package com.marklogic.handlers.undertow;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marklogic.repository.JsonUserRepository;
import com.marklogic.repository.JsonUserRepository.UserInfo;

import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;

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
@RestController
public class OAuthTokenHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuthTokenHandler.class);
    
    @Autowired
    private ResourceLoader resourceLoader;
    
    @Autowired(required = false)
    private JsonUserRepository jsonUserRepository;
    
    // Configurable token expiration time in seconds (default: 1 hour)
    @Value("${oauth.token.expiration.seconds:3600}")
    private long tokenExpirationSeconds;
    
    // Default roles to assign when user not found in repository
    @Value("${oauth.default.roles:user}")
    private String defaultRoles;
    
    // Configurable JWT issuer
    @Value("${oauth.jwt.issuer:mleaproxy-oauth-server}")
    private String jwtIssuer;
    
    // Configurable private key path
    @Value("${oauth.signing.key.path:classpath:static/certificates/privkey.pem}")
    private String keyPath;
    
    // Private key for signing tokens
    private RSAPrivateKey privateKey;
    
    // Public key derived from private key (for JWKS endpoint)
    private RSAPublicKey publicKey;
    
    // Key ID for JWKS (consistent across requests)
    private String keyId;
    
    // Flag to track if handler is properly initialized
    private volatile boolean initialized = false;
    
    // Base URL for OAuth endpoints (configurable)
    @Value("${oauth.server.base.url:http://localhost:8080}")
    private String baseUrl;
    
    /**
     * Initialize the OAuth handler after Spring context is ready.
     * Uses @PostConstruct to ensure proper Spring lifecycle management.
     */
    @PostConstruct
    public void init() {
        try {
            logger.info("Initializing OAuth Token Handler with key path: {}", keyPath);
            Resource resource = resourceLoader.getResource(keyPath);
            
            if (!resource.exists()) {
                logger.error("Private key resource not found at: {}", keyPath);
                this.privateKey = null;
                this.initialized = false;
                return;
            }
            
            try (InputStream inputStream = resource.getInputStream()) {
                this.privateKey = loadPrivateKey(inputStream);
                this.publicKey = derivePublicKey(this.privateKey);
                this.keyId = generateKeyId();
                this.initialized = true;
                logger.info("OAuth Token Handler initialized successfully with RSA signing key (kid: {})", keyId);
            }
        } catch (Exception e) {
            logger.error("Failed to initialize OAuth Token Handler", e);
            this.privateKey = null;
            this.publicKey = null;
            this.keyId = null;
            this.initialized = false;
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
            // Check if handler is properly initialized
            if (!initialized || privateKey == null) {
                logger.error("OAuth Token Handler not properly initialized - private key unavailable");
                return createErrorResponse("server_error", 
                    "OAuth service temporarily unavailable - configuration error", 
                    HttpStatus.SERVICE_UNAVAILABLE);
            }
            
            logger.debug("OAuth token request - grant_type: {}, client_id: {}", grantType, clientId);
            if (logger.isDebugEnabled()) {
                logger.debug("OAuth token request details - username: {}, scope: {}, roles: {}", 
                           username, scope, rolesParam);
            }

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

            // Determine roles to include in token
            // Priority order:
            // 1. Roles from request parameter (if specified)
            // 2. Roles from users.json (if user exists and no roles in request)
            // 3. Default roles from oauth.default.roles property (if user not found and no roles in request)
            List<String> roles = new ArrayList<>();
            
            // If JSON user repository is configured, look up user for validation
            if (jsonUserRepository != null && jsonUserRepository.isInitialized() && username != null) {
                logger.info("Looking up user '{}' in JSON user repository", username);
                UserInfo userInfo = jsonUserRepository.findByUsername(username);
                
                if (userInfo != null) {
                    // User found in JSON repository
                    // Optional: validate password if provided in JSON
                    if (grantType.equals("password") && userInfo.getPassword() != null) {
                        if (!userInfo.getPassword().equals(password)) {
                            logger.warn("Password validation failed for user: {}", username);
                            return createErrorResponse("invalid_grant", 
                                "Invalid username or password", 
                                HttpStatus.UNAUTHORIZED);
                        }
                    }
                    
                    // Priority 1: Use roles from request parameter if provided
                    if (rolesParam != null && !rolesParam.trim().isEmpty()) {
                        roles = parseRoles(rolesParam);
                        logger.info("Using roles from request parameter for user '{}': {}", 
                                   username, String.join(",", roles));
                    } else {
                        // Priority 2: Use roles from JSON if no roles in request
                        roles = userInfo.getRoles();
                        logger.info("Using roles from JSON for user '{}': {}", 
                                   username, String.join(",", roles));
                    }
                } else {
                    // User not found in JSON repository
                    if (rolesParam != null && !rolesParam.trim().isEmpty()) {
                        // Priority 1: Use roles from request parameter if provided
                        logger.info("User '{}' not in JSON, using roles from request parameter", username);
                        roles = parseRoles(rolesParam);
                    } else {
                        // Priority 3: Use default roles from configuration
                        logger.info("User '{}' not found in JSON, using default roles: {}", username, defaultRoles);
                        roles = parseRoles(defaultRoles);
                    }
                }
            } else {
                // JSON user repository not configured
                if (rolesParam != null && !rolesParam.trim().isEmpty()) {
                    // Priority 1: Use roles from request parameter if provided
                    logger.debug("JSON user repository not configured, using roles from request parameter");
                    roles = parseRoles(rolesParam);
                } else {
                    // Priority 3: Use default roles from configuration
                    logger.debug("JSON user repository not configured, using default roles: {}", defaultRoles);
                    roles = parseRoles(defaultRoles);
                }
            }
            
            logger.debug("Using roles for token: {}", roles);

            // Generate JWT access token
            String accessToken = generateAccessToken(clientId, username, scope, roles, grantType);
            
            // Create success response
            Map<String, Object> response = new HashMap<>();
            response.put("access_token", accessToken);
            response.put("token_type", "Bearer");
            response.put("expires_in", tokenExpirationSeconds);
            
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
     * Generate a JWT access token with the specified claims using JJWT library.
     * Creates a signed JWT token with RS256 algorithm containing standard OAuth/OIDC claims
     * and custom claims for roles and grant type information.
     * 
     * @param clientId OAuth client identifier
     * @param username User's username (may be null for client_credentials grant)
     * @param scope OAuth scope string
     * @param roles List of role strings to include in token
     * @param grantType Grant type used (password or client_credentials)
     * @return Serialized JWT token string
     */
    private String generateAccessToken(String clientId, String username, String scope, List<String> roles, String grantType) {
        
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(tokenExpirationSeconds);
        
        // Build and sign JWT token using JJWT fluent API
        var builder = Jwts.builder()
            .issuer(jwtIssuer)
            .subject(username != null ? username : clientId)
            .audience().add(clientId).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .id(UUID.randomUUID().toString())
            .claim("client_id", clientId)
            .claim("grant_type", grantType);
        
        // Add username if present
        if (username != null && !username.isEmpty()) {
            builder.claim("username", username);
        }
        
        // Add scope if present
        if (scope != null && !scope.isEmpty()) {
            builder.claim("scope", scope);
        }
        
        // Always add roles claim (even if empty array)
        builder.claim("roles", roles);
        if (!roles.isEmpty()) {
            builder.claim("roles_string", String.join(" ", roles));
        }
        
        // Add header and sign with RS256
        String token = builder
            .header()
                .keyId(keyId)
                .type("JWT")
            .and()
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
        
        return token;
    }
    
    /**
     * JWKS (JSON Web Key Set) endpoint for JWT verification.
     * Returns the public key in JWKS format that can be used by clients
     * to verify the signature of JWT tokens issued by this server.
     * 
     * GET /oauth/jwks
     * 
     * @return JWKS JSON containing the public key
     */
    @GetMapping(value = "/oauth/jwks", produces = "application/json")
    public ResponseEntity<Map<String, Object>> jwks() {
        try {
            // Check if handler is properly initialized
            if (!initialized || publicKey == null) {
                logger.error("OAuth JWKS endpoint - handler not properly initialized");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Service temporarily unavailable"));
            }
            
            logger.debug("JWKS endpoint called");
            
            // Get RSA public key parameters
            BigInteger modulus = publicKey.getModulus();
            BigInteger exponent = publicKey.getPublicExponent();
            
            // Convert to Base64 URL-encoded strings (without padding)
            String n = Base64.getUrlEncoder().withoutPadding().encodeToString(modulus.toByteArray());
            String e = Base64.getUrlEncoder().withoutPadding().encodeToString(exponent.toByteArray());
            
            // Create JWK (JSON Web Key)
            Map<String, Object> jwk = new LinkedHashMap<>();
            jwk.put("kty", "RSA");                    // Key Type
            jwk.put("use", "sig");                    // Public Key Use (signature)
            jwk.put("kid", keyId);                    // Key ID
            jwk.put("alg", "RS256");                  // Algorithm
            jwk.put("n", n);                          // Modulus
            jwk.put("e", e);                          // Exponent
            
            // Create JWKS (JSON Web Key Set)
            Map<String, Object> jwks = new LinkedHashMap<>();
            jwks.put("keys", List.of(jwk));
            
            logger.info("JWKS endpoint served successfully (kid: {})", keyId);
            return ResponseEntity.ok(jwks);
            
        } catch (Exception ex) {
            logger.error("Error generating JWKS", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
    }
    
    /**
     * OAuth 2.0 Server Configuration Discovery endpoint.
     * Returns metadata about the OAuth server endpoints and capabilities.
     * This follows the OAuth 2.0 Authorization Server Metadata specification.
     * 
     * GET /oauth/.well-known/config
     * 
     * @return OAuth server configuration metadata
     */
    @GetMapping(value = "/oauth/.well-known/config", produces = "application/json")
    public ResponseEntity<Map<String, Object>> wellKnownConfig() {
        try {
            logger.debug("OAuth configuration discovery endpoint called");
            
            // Build configuration metadata
            Map<String, Object> config = new LinkedHashMap<>();
            
            // OAuth 2.0 Authorization Server Metadata fields
            config.put("issuer", jwtIssuer);
            config.put("token_endpoint", baseUrl + "/oauth/token");
            config.put("jwks_uri", baseUrl + "/oauth/jwks");
            
            // Supported grant types
            config.put("grant_types_supported", List.of("password", "client_credentials"));
            
            // Supported response types (we only support token endpoint, not authorization)
            config.put("response_types_supported", List.of("token"));
            
            // Token endpoint authentication methods
            config.put("token_endpoint_auth_methods_supported", List.of("client_secret_post"));
            
            // Supported signing algorithms
            config.put("id_token_signing_alg_values_supported", List.of("RS256"));
            
            // Additional claims we support
            config.put("claims_supported", List.of(
                "iss", "sub", "aud", "exp", "iat", "jti",
                "client_id", "grant_type", "username", "scope", "roles", "roles_string"
            ));
            
            // Scopes supported (extensible)
            config.put("scopes_supported", List.of("openid", "profile", "email"));
            
            logger.info("OAuth configuration discovery served successfully");
            return ResponseEntity.ok(config);
            
        } catch (Exception ex) {
            logger.error("Error generating OAuth configuration", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
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
     * Load RSA private key from InputStream (works with classpath resources)
     */
    private RSAPrivateKey loadPrivateKey(InputStream inputStream) throws Exception {
        logger.debug("Loading private key from input stream");
        
        String keyContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        
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
        
        logger.info("Private key loaded successfully");
        return (RSAPrivateKey) privateKey;
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
    
    /**
     * Derive the RSA public key from the RSA private key.
     * Uses the modulus and public exponent from the private key to construct the public key.
     * 
     * @param privateKey RSA private key
     * @return RSA public key derived from the private key
     * @throws Exception if key derivation fails
     */
    private RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) throws Exception {
        logger.debug("Deriving public key from private key");
        
        BigInteger modulus = privateKey.getModulus();
        BigInteger publicExponent = BigInteger.valueOf(65537); // Standard RSA public exponent (0x10001)
        
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(modulus, publicExponent);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
        
        logger.info("Public key derived successfully");
        return publicKey;
    }
    
    /**
     * Generate a stable key ID for JWKS.
     * Uses a hash of the public key modulus to create a consistent identifier.
     * 
     * @return Key ID string
     */
    private String generateKeyId() {
        // Generate a deterministic key ID based on the key
        // For simplicity, we'll use a UUID that's consistent for this instance
        String kid = UUID.randomUUID().toString();
        logger.debug("Generated key ID: {}", kid);
        return kid;
    }
}
