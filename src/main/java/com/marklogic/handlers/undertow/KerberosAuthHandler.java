package com.marklogic.handlers.undertow;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST Controller for Kerberos SPNEGO authentication.
 * 
 * This controller handles HTTP requests with Kerberos/SPNEGO authentication
 * and returns JWT tokens for authenticated users.
 * 
 * Endpoints:
 * - GET /kerberos/auth - Authenticate with Kerberos ticket, receive JWT
 * - GET /kerberos/token - Same as /kerberos/auth (alias)
 * - GET /kerberos/whoami - Get current authenticated user info
 * 
 * Usage:
 * <pre>
 * # Get Kerberos ticket first
 * kinit mluser1@MARKLOGIC.LOCAL
 * 
 * # Authenticate and get JWT token
 * curl --negotiate -u : http://localhost:8080/kerberos/auth
 * 
 * # Use the JWT token
 * curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8080/some-endpoint
 * </pre>
 * 
 * @since 2.0.0
 */
@RestController
@RequestMapping("/kerberos")
public class KerberosAuthHandler {
    private static final Logger logger = LoggerFactory.getLogger(KerberosAuthHandler.class);

    @Value("${oauth.token.expiry:3600}")
    private int tokenExpiry;

    @Value("${oauth.token.audience:marklogic}")
    private String tokenAudience;

    @Value("${oauth.issuer:mleaproxy-kerberos-server}")
    private String issuer;

    // Secret key for JWT signing (should match OAuth configuration)
    private SecretKey jwtSigningKey;

    /**
     * Main authentication endpoint.
     * Accepts Kerberos/SPNEGO authentication and returns a JWT token.
     * 
     * The browser or curl client must present a valid Kerberos ticket.
     * 
     * @return JWT token with user principal and roles
     */
    @GetMapping("/auth")
    public ResponseEntity<Map<String, Object>> authenticate() {
        try {
            logger.debug("Kerberos authentication request received");
            
            // Get authenticated principal from Spring Security context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                logger.warn("No authentication found in security context");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "unauthorized", 
                                 "message", "No valid Kerberos ticket provided"));
            }
            
            String principal = authentication.getName();
            logger.info("Kerberos authentication successful for principal: {}", principal);
            
            // Extract roles/authorities
            List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
            
            logger.debug("Principal {} has roles: {}", principal, roles);
            
            // Generate JWT token
            String token = generateJwtToken(principal, roles);
            
            // Build response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("access_token", token);
            response.put("token_type", "Bearer");
            response.put("expires_in", tokenExpiry);
            response.put("principal", principal);
            response.put("roles", roles);
            response.put("issued_at", Instant.now().getEpochSecond());
            
            logger.info("JWT token issued for principal: {}", principal);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during Kerberos authentication", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "internal_error", 
                             "message", "Authentication failed: " + e.getMessage()));
        }
    }

    /**
     * Alias for /auth endpoint.
     * Provides backward compatibility and clearer naming.
     * 
     * @return JWT token with user principal and roles
     */
    @GetMapping("/token")
    public ResponseEntity<Map<String, Object>> getToken() {
        return authenticate();
    }

    /**
     * Get information about the currently authenticated user.
     * Does not issue a new token, just returns principal info.
     * 
     * @return User principal information
     */
    @GetMapping("/whoami")
    public ResponseEntity<Map<String, Object>> whoami() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("authenticated", false));
            }
            
            String principal = authentication.getName();
            List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("authenticated", true);
            response.put("principal", principal);
            response.put("roles", roles);
            response.put("authentication_type", "kerberos");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting current user info", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "internal_error"));
        }
    }

    /**
     * Generate a JWT token for the authenticated principal.
     * 
     * The token includes:
     * - iss: Issuer
     * - sub: Subject (principal name)
     * - aud: Audience
     * - exp: Expiration time
     * - iat: Issued at time
     * - jti: JWT ID (unique)
     * - roles: User roles
     * - auth_method: "kerberos"
     * 
     * @param principal The authenticated principal name
     * @param roles List of user roles
     * @return JWT token string
     */
    private String generateJwtToken(String principal, List<String> roles) {
        if (jwtSigningKey == null) {
            // Generate a signing key (in production, this should be loaded from configuration)
            String secret = "mleaproxy-kerberos-jwt-signing-key-change-in-production";
            jwtSigningKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(tokenExpiry);
        
        return Jwts.builder()
            .issuer(issuer)
            .subject(principal)
            .audience().add(tokenAudience).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .id(UUID.randomUUID().toString())
            .claim("roles", roles)
            .claim("roles_string", String.join(",", roles))
            .claim("auth_method", "kerberos")
            .signWith(jwtSigningKey)
            .compact();
    }

    /**
     * Health check endpoint for Kerberos authentication service.
     * 
     * @return Service status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("service", "kerberos-auth");
        health.put("status", "healthy");
        health.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.ok(health);
    }
}
