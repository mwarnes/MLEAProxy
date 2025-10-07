package com.marklogic.handlers.undertow;

import com.marklogic.repository.JsonUserRepository;
import com.marklogic.repository.JsonUserRepository.UserInfo;
import com.marklogic.service.LDAPRoleService;
import com.marklogic.service.RefreshTokenService;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.ietf.jgss.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.MultiValueMap;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.*;

/**
 * Bridge handler for exchanging Kerberos tickets for OAuth JWT tokens.
 * 
 * This handler enables seamless integration between Kerberos authentication
 * and the OAuth 2.0 infrastructure by accepting a Kerberos ticket and
 * returning an OAuth-compatible JWT token with roles loaded from the
 * user repository.
 * 
 * Endpoints:
 * - POST /oauth/token-from-kerberos - Exchange Kerberos ticket for OAuth JWT
 * 
 * Usage:
 * <pre>
 * # Get Kerberos ticket
 * kinit mluser1@MARKLOGIC.LOCAL
 * 
 * # Exchange for OAuth token
 * curl --negotiate -u : -X POST http://localhost:8080/oauth/token-from-kerberos
 * 
 * # Response includes OAuth JWT with roles from user repository
 * {
 *   "access_token": "eyJhbGc...",
 *   "token_type": "Bearer",
 *   "expires_in": 3600,
 *   "scope": "read write",
 *   "principal": "mluser1",
 *   "roles": ["app-reader", "data-reader"]
 * }
 * </pre>
 * 
 * Phase 3 Enhancement:
 * - Validates Kerberos ticket using JAAS/GSS-API
 * - Extracts principal from ticket
 * - Queries JsonUserRepository for user roles
 * - Generates OAuth-compatible JWT with RSA signing
 * - Returns token in OAuth 2.0 format
 * 
 * @since 2.0.0 (Phase 3)
 */
@RestController
@RequestMapping("/oauth")
public class KerberosOAuthBridgeHandler {
    private static final Logger logger = LoggerFactory.getLogger(KerberosOAuthBridgeHandler.class);

    @Autowired(required = false)
    private JsonUserRepository jsonUserRepository;

    @Autowired(required = false)
    private LDAPRoleService ldapRoleService;

    @Autowired(required = false)
    private RefreshTokenService refreshTokenService;

    @Autowired
    private ResourceLoader resourceLoader;

    @Value("${kerberos.enabled:false}")
    private boolean kerberosEnabled;

    @Value("${kerberos.service-principal:HTTP/localhost@MARKLOGIC.LOCAL}")
    private String servicePrincipal;

    @Value("${kerberos.keytab-location:./kerberos/keytabs/service.keytab}")
    private String keytabLocation;

    @Value("${kerberos.debug:false}")
    private boolean debug;

    @Value("${oauth.token.expiration.seconds:3600}")
    private long tokenExpirationSeconds;

    @Value("${oauth.jwt.issuer:mleaproxy-oauth-server}")
    private String jwtIssuer;

    @Value("${oauth.signing.key.path:classpath:static/certificates/privkey.pem}")
    private String keyPath;

    @Value("${oauth.default.roles:user}")
    private String defaultRoles;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private String keyId;
    private volatile boolean initialized = false;

    @PostConstruct
    public void init() {
        try {
            // Load RSA private key for OAuth JWT signing
            Resource keyResource = resourceLoader.getResource(keyPath);
            if (keyResource.exists()) {
                try (InputStream inputStream = keyResource.getInputStream()) {
                    this.privateKey = loadPrivateKey(inputStream);
                    
                    // Derive public key from private key (requires RSAPrivateCrtKey)
                    if (privateKey instanceof RSAPrivateCrtKey) {
                        RSAPrivateCrtKey crtKey = (RSAPrivateCrtKey) privateKey;
                        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(
                            crtKey.getModulus(),
                            crtKey.getPublicExponent()
                        );
                        this.publicKey = (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
                        
                        // Generate consistent key ID
                        this.keyId = generateKeyId(publicKey);
                    } else {
                        logger.warn("Private key is not RSAPrivateCrtKey, cannot derive public key");
                        this.publicKey = null;
                    }                    logger.info("Kerberos-OAuth bridge initialized with RSA signing key");
                    this.initialized = true;
                }
            } else {
                logger.warn("OAuth signing key not found at: {}", keyPath);
                logger.warn("Kerberos-OAuth bridge will not be available");
                this.initialized = false;
            }
        } catch (Exception e) {
            logger.error("Failed to initialize Kerberos-OAuth bridge", e);
            this.initialized = false;
        }
    }

    /**
     * Exchange a Kerberos ticket for an OAuth JWT token.
     * 
     * This endpoint accepts a Kerberos ticket via the Authorization: Negotiate header,
     * validates it, extracts the principal, queries the user repository for roles,
     * and returns an OAuth-compatible JWT token signed with RSA.
     * 
     * @param authHeader Authorization header with Negotiate token
     * @return OAuth token response with JWT
     */
    @PostMapping("/token-from-kerberos")
    public ResponseEntity<Map<String, Object>> exchangeKerberosForOAuth(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        if (!initialized) {
            logger.error("Kerberos-OAuth bridge not initialized (RSA key not loaded)");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "error", "service_unavailable",
                    "error_description", "Kerberos-OAuth bridge is not configured"
                ));
        }

        if (!kerberosEnabled) {
            logger.warn("Kerberos is disabled but /oauth/token-from-kerberos was called");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "error", "service_unavailable",
                    "error_description", "Kerberos authentication is not enabled"
                ));
        }

        // Validate Authorization header
        if (authHeader == null || !authHeader.startsWith("Negotiate ")) {
            logger.debug("No Kerberos ticket in Authorization header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("WWW-Authenticate", "Negotiate")
                .body(Map.of(
                    "error", "invalid_request",
                    "error_description", "Kerberos ticket required in Authorization: Negotiate header"
                ));
        }

        try {
            // Extract and validate Kerberos ticket
            String base64Token = authHeader.substring("Negotiate ".length());
            byte[] kerberosTicket = Base64.getDecoder().decode(base64Token);
            
            logger.debug("Received Kerberos ticket for OAuth exchange, size: {} bytes", kerberosTicket.length);

            // Validate ticket and extract principal
            String fullPrincipal = validateTicketAndGetPrincipal(kerberosTicket);
            
            if (fullPrincipal == null) {
                logger.warn("Kerberos ticket validation failed");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Negotiate")
                    .body(Map.of(
                        "error", "invalid_grant",
                        "error_description", "Kerberos ticket validation failed"
                    ));
            }

            logger.info("Kerberos ticket validated successfully for principal: {}", fullPrincipal);

            // Extract username from principal (e.g., "mluser1@MARKLOGIC.LOCAL" -> "mluser1")
            String username = extractUsername(fullPrincipal);
            
            // Query user repository for roles
            List<String> roles = loadUserRoles(username);
            
            logger.debug("Principal {} has roles: {}", username, roles);

            // Generate OAuth JWT token
            String accessToken = generateOAuthToken(username, roles);

            // Generate refresh token (Phase 4 enhancement)
            String refreshToken = null;
            if (refreshTokenService != null && refreshTokenService.isEnabled()) {
                refreshToken = refreshTokenService.generateRefreshToken(username, String.join(" ", roles));
                logger.debug("Generated refresh token for user: {}", username);
            }

            // Build OAuth 2.0 response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("access_token", accessToken);
            response.put("token_type", "Bearer");
            response.put("expires_in", tokenExpirationSeconds);
            response.put("scope", String.join(" ", roles));
            response.put("principal", username);
            response.put("kerberos_principal", fullPrincipal);
            response.put("roles", roles);
            response.put("auth_method", "kerberos-oauth-bridge");
            
            // Add refresh token if available
            if (refreshToken != null) {
                response.put("refresh_token", refreshToken);
            }

            logger.info("OAuth token issued for Kerberos principal: {} (username: {}, has_refresh: {})", 
                fullPrincipal, username, refreshToken != null);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error exchanging Kerberos ticket for OAuth token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "server_error",
                    "error_description", "Failed to process Kerberos ticket: " + e.getMessage()
                ));
        }
    }

    /**
     * Validate Kerberos ticket using GSS-API and extract principal name.
     * 
     * @param kerberosTicket Base64-decoded Kerberos ticket
     * @return Principal name (e.g., "mluser1@MARKLOGIC.LOCAL") or null if validation fails
     */
    private String validateTicketAndGetPrincipal(byte[] kerberosTicket) {
        try {
            // Create JAAS configuration
            javax.security.auth.login.Configuration jaasConfig = createJaasConfiguration();
            javax.security.auth.login.Configuration.setConfiguration(jaasConfig);

            // Login as service principal using keytab
            LoginContext loginContext = new LoginContext("KerberosService");
            loginContext.login();
            
            Subject serviceSubject = loginContext.getSubject();
            
            if (logger.isDebugEnabled()) {
                logger.debug("Service login successful, principals: {}", serviceSubject.getPrincipals());
            }

            // Validate ticket using GSS-API within privileged context
            String principal;
            try {
                principal = Subject.callAs(serviceSubject, () -> {
                    // Create GSS context
                    GSSManager manager = GSSManager.getInstance();
                    GSSContext context = manager.createContext((GSSCredential) null);

                    // Accept security context (validate ticket)
                    context.acceptSecContext(kerberosTicket, 0, kerberosTicket.length);

                    // Extract client principal
                    String clientPrincipal = context.getSrcName().toString();
                    
                    if (logger.isDebugEnabled()) {
                        logger.debug("Ticket validated successfully");
                        logger.debug("  Client principal: {}", clientPrincipal);
                        logger.debug("  Context established: {}", context.isEstablished());
                    }

                    // Clean up
                    context.dispose();
                    
                    return clientPrincipal;
                });
            } catch (Exception e) {
                logger.error("Error validating Kerberos ticket", e);
                principal = null;
            }

            // Logout
            loginContext.logout();
            
            return principal;
            
        } catch (LoginException e) {
            logger.error("JAAS login failed for service principal: {}", servicePrincipal, e);
            return null;
        }
    }

    /**
     * Create JAAS configuration for service principal authentication.
     * 
     * @return JAAS configuration
     */
    private javax.security.auth.login.Configuration createJaasConfiguration() {
        return new javax.security.auth.login.Configuration() {
            @Override
            public javax.security.auth.login.AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                Map<String, String> options = new HashMap<>();
                options.put("useKeyTab", "true");
                options.put("storeKey", "true");
                options.put("doNotPrompt", "true");
                options.put("isInitiator", "false");  // We're the acceptor (server)
                options.put("principal", servicePrincipal);
                options.put("keyTab", new File(keytabLocation).getAbsolutePath());
                
                if (debug) {
                    options.put("debug", "true");
                }

                return new javax.security.auth.login.AppConfigurationEntry[] {
                    new javax.security.auth.login.AppConfigurationEntry(
                        "com.sun.security.auth.module.Krb5LoginModule",
                        javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                        options
                    )
                };
            }
        };
    }

    /**
     * Extract username from Kerberos principal.
     * 
     * Examples:
     * - "mluser1@MARKLOGIC.LOCAL" -> "mluser1"
     * - "mluser1" -> "mluser1"
     * 
     * @param principal Full Kerberos principal
     * @return Username
     */
    private String extractUsername(String principal) {
        if (principal == null) {
            return null;
        }
        
        int atIndex = principal.indexOf('@');
        if (atIndex > 0) {
            return principal.substring(0, atIndex);
        }
        
        return principal;
    }

    /**
     * Load user roles from JSON user repository with LDAP fallback (Phase 4 enhancement).
     * 
     * Resolution order:
     * 1. Try JsonUserRepository first (users.json)
     * 2. If not found, try LDAP role service
     * 3. If both fail, use default roles
     * 
     * @param username Username (without realm)
     * @return List of roles
     */
    private List<String> loadUserRoles(String username) {
        // Try JSON repository first
        if (jsonUserRepository != null) {
            try {
                UserInfo userInfo = jsonUserRepository.findByUsername(username);
                if (userInfo != null && userInfo.getRoles() != null) {
                    logger.debug("Loaded {} roles from JSON repository for user: {}", 
                        userInfo.getRoles().size(), username);
                    return new ArrayList<>(userInfo.getRoles());
                }
            } catch (Exception e) {
                logger.error("Error loading roles from JSON repository for user: {}", username, e);
            }
        }

        // Fallback to LDAP (Phase 4 enhancement)
        if (ldapRoleService != null && ldapRoleService.isInitialized()) {
            try {
                List<String> ldapRoles = ldapRoleService.getUserRoles(username);
                if (!ldapRoles.isEmpty()) {
                    logger.info("Loaded {} roles from LDAP for user: {}", ldapRoles.size(), username);
                    return ldapRoles;
                }
            } catch (Exception e) {
                logger.error("Error loading roles from LDAP for user: {}", username, e);
            }
        }

        // Ultimate fallback: default roles
        logger.debug("User {} not found in JSON or LDAP, using default roles", username);
        return Arrays.asList(defaultRoles.split(","));
    }

    /**
     * Generate OAuth-compatible JWT token with RSA signing.
     * 
     * @param username Username
     * @param roles User roles
     * @return JWT token string
     */
    private String generateOAuthToken(String username, List<String> roles) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(tokenExpirationSeconds);
        
        return Jwts.builder()
            .header()
                .keyId(keyId)
                .and()
            .issuer(jwtIssuer)
            .subject(username)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .id(UUID.randomUUID().toString())
            .claim("scope", String.join(" ", roles))
            .claim("roles", roles)
            .claim("auth_method", "kerberos")
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
    }

    /**
     * Load RSA private key from PEM file.
     * 
     * @param inputStream Key input stream
     * @return RSA private key
     * @throws Exception if key loading fails
     */
    private RSAPrivateKey loadPrivateKey(InputStream inputStream) throws Exception {
        String keyString = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        
        byte[] keyBytes = Base64.getDecoder().decode(keyString);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        
        return (RSAPrivateKey) keyFactory.generatePrivate(spec);
    }

    /**
     * Generate consistent key ID from public key.
     * 
     * @param publicKey RSA public key
     * @return Key ID (hex string)
     */
    private String generateKeyId(RSAPublicKey publicKey) {
        BigInteger modulus = publicKey.getModulus();
        String modulusHex = modulus.toString(16);
        return modulusHex.substring(0, Math.min(16, modulusHex.length()));
    }

    /**
     * Refresh OAuth token using refresh token (Phase 4 enhancement).
     * 
     * This endpoint implements OAuth 2.0 refresh token flow.
     * It accepts a refresh token, validates it, and issues a new access token.
     * 
     * Token rotation: old refresh token is consumed and a new one is issued.
     * 
     * @param grantType Must be "refresh_token"
     * @param refreshToken The refresh token to exchange
     * @return New OAuth token response with new access and refresh tokens
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "refresh_token", required = false) String refreshToken) {
        
        if (!initialized) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "error", "service_unavailable",
                    "error_description", "OAuth service is not configured"
                ));
        }

        if (refreshTokenService == null || !refreshTokenService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "error", "unsupported_grant_type",
                    "error_description", "Refresh tokens are not enabled"
                ));
        }

        // Validate grant_type
        if (!"refresh_token".equals(grantType)) {
            logger.debug("Invalid grant_type: {}", grantType);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "error", "invalid_request",
                    "error_description", "grant_type must be 'refresh_token'"
                ));
        }

        // Validate refresh token presence
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            logger.debug("Missing refresh_token parameter");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "error", "invalid_request",
                    "error_description", "refresh_token is required"
                ));
        }

        try {
            // Validate and consume refresh token (single-use)
            RefreshTokenService.TokenValidationResult result = 
                refreshTokenService.validateAndConsumeToken(refreshToken);
            
            if (!result.isValid()) {
                logger.warn("Invalid refresh token: {}", result.getErrorMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                        "error", "invalid_grant",
                        "error_description", result.getErrorMessage()
                    ));
            }

            String username = result.getUsername();
            String scope = result.getScope();
            
            logger.info("Refresh token validated for user: {}", username);

            // Parse scope back to roles list
            List<String> roles = Arrays.asList(scope.split(" "));

            // Generate new access token
            String newAccessToken = generateOAuthToken(username, roles);

            // Generate new refresh token (token rotation)
            String newRefreshToken = refreshTokenService.generateRefreshToken(username, scope);

            // Build OAuth 2.0 response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("access_token", newAccessToken);
            response.put("token_type", "Bearer");
            response.put("expires_in", tokenExpirationSeconds);
            response.put("scope", scope);
            response.put("refresh_token", newRefreshToken);
            response.put("principal", username);
            response.put("auth_method", "refresh_token");

            logger.info("New tokens issued via refresh for user: {}", username);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error refreshing token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "server_error",
                    "error_description", "Failed to refresh token: " + e.getMessage()
                ));
        }
    }
}
