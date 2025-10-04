package com.marklogic.handlers.undertow;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.*;

/**
 * OAuth 2.0 Token Endpoint Handler
 * 
 * Provides a simple OAuth 2.0 token endpoint at /oauth/token
 * Accepts client credentials and user credentials to generate JWT access tokens
 * with custom roles included in the token claims.
 * 
 * Endpoint: POST /oauth/token
 * 
 * Parameters:
 * - grant_type: "password" or "client_credentials"
 * - client_id: Client identifier (required)
 * - client_secret: Client secret (required)
 * - username: User's username (required for password grant)
 * - password: User's password (required for password grant)
 * - scope: OAuth scope (optional)
 * - roles: Comma-separated list of roles to include in token (optional)
 * 
 * Response: JSON with access_token, token_type, expires_in, and scope
 */
@Controller
public class OAuthTokenHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuthTokenHandler.class);
    
    // Token expiration time in seconds (default: 1 hour)
    private static final long TOKEN_EXPIRATION_SECONDS = 3600;
    
    // JWT issuer
    private static final String JWT_ISSUER = "mleaproxy-oauth-server";
    
    // Private key for signing tokens
    private RSAPrivateKey privateKey;
    
    public OAuthTokenHandler() {
        try {
            // Load the same private key used for SAML signing
            String keyPath = System.getProperty("user.dir") + "/src/main/resources/static/certificates/privkey.pem";
            this.privateKey = loadPrivateKey(keyPath);
            logger.info("OAuth Token Handler initialized with RSA signing key");
        } catch (Exception e) {
            logger.error("Failed to initialize OAuth Token Handler", e);
            throw new RuntimeException("Failed to load private key for OAuth token signing", e);
        }
    }

    @PostMapping(value = "/oauth/token", produces = "application/json")
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "scope", defaultValue = "") String scope,
            @RequestParam(value = "roles", defaultValue = "") String rolesParam) {

        try {
            logger.info("OAuth token request - grant_type: {}, client_id: {}, username: {}, scope: {}, roles: {}", 
                       grantType, clientId, username, scope, rolesParam);

            // Validate required parameters
            if (grantType == null || grantType.isEmpty()) {
                return createErrorResponse("invalid_request", "grant_type is required", HttpStatus.BAD_REQUEST);
            }

            if (clientId == null || clientId.isEmpty()) {
                return createErrorResponse("invalid_client", "client_id is required", HttpStatus.BAD_REQUEST);
            }

            if (clientSecret == null || clientSecret.isEmpty()) {
                return createErrorResponse("invalid_client", "client_secret is required", HttpStatus.BAD_REQUEST);
            }

            // Validate grant type
            if (!grantType.equals("password") && !grantType.equals("client_credentials")) {
                return createErrorResponse("unsupported_grant_type", 
                                "Only 'password' and 'client_credentials' grant types are supported", 
                                HttpStatus.BAD_REQUEST);
            }

            // For password grant, validate username and password
            if (grantType.equals("password")) {
                if (username == null || username.isEmpty()) {
                    return createErrorResponse("invalid_request", "username is required for password grant", HttpStatus.BAD_REQUEST);
                }
                if (password == null || password.isEmpty()) {
                    return createErrorResponse("invalid_request", "password is required for password grant", HttpStatus.BAD_REQUEST);
                }
            }

            // Parse roles
            List<String> roles = parseRoles(rolesParam);
            logger.debug("Parsed roles: {}", roles);

            // Generate JWT access token
            String accessToken = generateAccessToken(clientId, username, scope, roles, grantType);
            
            // Create success response
            Map<String, Object> response = new HashMap<>();
            response.put("access_token", accessToken);
            response.put("token_type", "Bearer");
            response.put("expires_in", TOKEN_EXPIRATION_SECONDS);
            
            if (scope != null && !scope.isEmpty()) {
                response.put("scope", scope);
            }
            
            logger.info("OAuth token generated successfully for client: {}, user: {}, roles: {}", 
                       clientId, username, String.join(",", roles));
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing OAuth token request", e);
            return createErrorResponse("server_error", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Generate a JWT access token with the specified claims
     */
    private String generateAccessToken(String clientId, String username, String scope, List<String> roles, String grantType) 
            throws JOSEException {
        
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(TOKEN_EXPIRATION_SECONDS);
        
        // Build JWT claims
        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
            .issuer(JWT_ISSUER)
            .subject(username != null ? username : clientId)
            .audience(clientId)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expiration))
            .jwtID(UUID.randomUUID().toString())
            .claim("client_id", clientId)
            .claim("grant_type", grantType);
        
        // Add username if present
        if (username != null && !username.isEmpty()) {
            claimsBuilder.claim("username", username);
        }
        
        // Add scope if present
        if (scope != null && !scope.isEmpty()) {
            claimsBuilder.claim("scope", scope);
        }
        
        // Always add roles claim (even if empty array)
        claimsBuilder.claim("roles", roles);
        if (!roles.isEmpty()) {
            claimsBuilder.claim("roles_string", String.join(" ", roles));
        }
        
        JWTClaimsSet claims = claimsBuilder.build();
        
        // Sign the JWT with RS256
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(UUID.randomUUID().toString())
            .type(com.nimbusds.jose.JOSEObjectType.JWT)
            .build();
        
        SignedJWT signedJWT = new SignedJWT(header, claims);
        JWSSigner signer = new RSASSASigner(privateKey);
        signedJWT.sign(signer);
        
        return signedJWT.serialize();
    }

    /**
     * Parse comma-separated roles string into a list
     */
    private List<String> parseRoles(String rolesParam) {
        List<String> roles = new ArrayList<>();
        if (rolesParam != null && !rolesParam.trim().isEmpty()) {
            String[] roleArray = rolesParam.split(",");
            for (String role : roleArray) {
                String trimmedRole = role.trim();
                if (!trimmedRole.isEmpty()) {
                    roles.add(trimmedRole);
                }
            }
        }
        return roles;
    }

    /**
     * Load RSA private key from PEM file
     */
    private RSAPrivateKey loadPrivateKey(String keyPath) throws Exception {
        logger.debug("Loading private key from: {}", keyPath);
        
        try (FileInputStream fis = new FileInputStream(keyPath)) {
            String keyContent = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
            
            // Remove PEM headers and whitespace
            keyContent = keyContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
            
            // Decode base64
            byte[] keyBytes = Base64.getDecoder().decode(keyContent);
            
            // Create private key
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(spec);
            
            logger.info("Private key loaded successfully from: {}", keyPath);
            return (RSAPrivateKey) privateKey;
        }
    }

    /**
     * Create OAuth error response
     */
    private ResponseEntity<Map<String, Object>> createErrorResponse(String error, String errorDescription, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", error);
        response.put("error_description", errorDescription);
        
        logger.warn("OAuth error response: {} - {}", error, errorDescription);
        return ResponseEntity.status(status).body(response);
    }
}
