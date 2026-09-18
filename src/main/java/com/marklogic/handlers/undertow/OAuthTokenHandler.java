package com.marklogic.handlers.undertow;

import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
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
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marklogic.service.AuthorizationCodeStore;
import com.marklogic.service.AuthorizationCodeStore.AuthorizationCode;
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
    
    @Autowired
    private Environment environment;
    
    // Configurable token expiration time in seconds (default: 1 hour)
    @Autowired
    private AuthorizationCodeStore codeStore;

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
    
    // Base URL for OAuth endpoints - computed at startup
    private String baseUrl;
    
    // Explicitly configured base URL (optional override)
    @Value("${oauth.server.base.url:}")
    private String configuredBaseUrl;

    // Explicitly configured base URL for the authorization endpoint (optional override)
    @Value("${oauth.authorize.base.url:}")
    private String configuredAuthorizeBaseUrl;

    // HTTPS listener settings, used to advertise the authorization endpoint
    @Value("${mleaproxy.https.enabled:true}")
    private boolean httpsEnabled;

    @Value("${mleaproxy.https.port:8443}")
    private int httpsPort;
    
    /**
     * Initialize the OAuth handler after Spring context is ready.
     * Uses @PostConstruct to ensure proper Spring lifecycle management.
     */
    @PostConstruct
    public void init() {
        // Initialize base URL
        initializeBaseUrl();
        
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
                logger.info("OAuth Base URL: {}", baseUrl);
            }
        } catch (Exception e) {
            logger.error("Failed to initialize OAuth Token Handler", e);
            this.privateKey = null;
            this.publicKey = null;
            this.keyId = null;
            this.initialized = false;
        }
    }
    
    /**
     * Initialize the base URL for OAuth endpoints.
     *
     * MarkLogic 12.1 requires the token and JWKS URIs to be HTTPS, so the HTTPS listener
     * is preferred whenever it is enabled. The endpoints are served on both listeners -
     * only the advertised URL differs - so an explicit oauth.server.base.url can still
     * point at plain HTTP for a Resource Server setup that needs it.
     *
     * Priority:
     * 1. Explicitly configured oauth.server.base.url
     * 2. https://&lt;hostname&gt;:&lt;mleaproxy.https.port&gt; when the HTTPS listener is enabled
     * 3. Auto-detect from server hostname, port, and SSL settings
     */
    private void initializeBaseUrl() {
        // Check for explicitly configured base URL
        if (configuredBaseUrl != null && !configuredBaseUrl.isEmpty()) {
            this.baseUrl = configuredBaseUrl;
            logger.info("Using configured OAuth base URL: {}", baseUrl);
            return;
        }

        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String hostname = getServerHostname();

        // Prefer the HTTPS listener: MarkLogic rejects http:// token and JWKS URIs.
        if (httpsEnabled && httpsPort > 0) {
            this.baseUrl = "https://" + hostname + ":" + httpsPort + contextPath;
            logger.info("Auto-detected OAuth base URL (HTTPS listener): {}", baseUrl);
            return;
        }

        // Fall back to the primary connector
        String port = environment.getProperty("server.port", "8080");
        boolean sslEnabled = Boolean.parseBoolean(environment.getProperty("server.ssl.enabled", "false"));
        String protocol = sslEnabled ? "https" : "http";

        this.baseUrl = protocol + "://" + hostname + ":" + port + contextPath;
        logger.info("Auto-detected OAuth base URL: {}", baseUrl);
    }
    
    /**
     * Gets the server's hostname, preferring the canonical hostname (FQDN).
     */
    private String getServerHostname() {
        try {
            // Try to get the fully qualified domain name
            String canonicalHostname = InetAddress.getLocalHost().getCanonicalHostName();
            if (canonicalHostname != null && !canonicalHostname.isEmpty() 
                    && !canonicalHostname.equals("localhost")
                    && !canonicalHostname.startsWith("127.")) {
                return canonicalHostname;
            }
            
            // Fall back to simple hostname
            String hostname = InetAddress.getLocalHost().getHostName();
            if (hostname != null && !hostname.isEmpty()) {
                return hostname;
            }
        } catch (UnknownHostException e) {
            logger.debug("Could not determine server hostname: {}", e.getMessage());
        }
        
        // Final fallback
        return "localhost";
    }

    @PostMapping(value = "/oauth/token", produces = "application/json")
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "scope", defaultValue = "") String scope,
            @RequestParam(value = "roles", defaultValue = "") String rolesParam,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "code_verifier", required = false) String codeVerifier,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

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

            // Accept client_secret_basic as well as client_secret_post. MarkLogic's
            // external security configuration only says "Client secret", so which of the
            // two it uses cannot be assumed.
            String[] basicCredentials = parseBasicAuthorization(authorization);
            if (basicCredentials != null) {
                if (clientId == null || clientId.isEmpty()) {
                    clientId = basicCredentials[0];
                }
                if (clientSecret == null || clientSecret.isEmpty()) {
                    clientSecret = basicCredentials[1];
                }
            }

            if (clientId == null || clientId.isEmpty()) {
                return createErrorResponse("invalid_client", "client_id is required", HttpStatus.BAD_REQUEST);
            }

            if (clientSecret == null || clientSecret.isEmpty()) {
                return createErrorResponse("invalid_client", "client_secret is required", HttpStatus.BAD_REQUEST);
            }

            // Validate grant type
            if (!grantType.equals("password") && !grantType.equals("client_credentials")
                    && !grantType.equals("authorization_code")) {
                return createErrorResponse("unsupported_grant_type", 
                                "Only 'password', 'client_credentials' and 'authorization_code' "
                                + "grant types are supported", 
                                HttpStatus.BAD_REQUEST);
            }

            // The Authorization Code grant is handled separately: the username and roles
            // were already decided at the authorize step, so the role resolution below
            // does not apply to it.
            if (grantType.equals("authorization_code")) {
                return handleAuthorizationCodeGrant(code, redirectUri, clientId, codeVerifier);
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
                        List<String> userRoles = userInfo.getRoles();
                        if (!userRoles.isEmpty()) {
                            roles = userRoles;
                            logger.info("Priority 2: Using roles from JSON for user '{}': {}", 
                                       username, String.join(",", roles));
                        } else {
                            // User has empty roles in JSON, use default roles
                            roles = parseRoles(defaultRoles);
                            logger.info("Priority 3: User '{}' found in JSON but has no roles assigned, using default roles: {}", 
                                       username, defaultRoles);
                        }
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
     * Completes the Authorization Code grant.
     *
     * Redeems the code, checks it against the authorize request it was issued for, verifies
     * PKCE, and issues the access token using the username and roles chosen at the login
     * page.
     *
     * @param code         the authorization code presented by the client
     * @param redirectUri  redirect URI sent with the exchange, checked against the original
     * @param clientId     client performing the exchange, checked against the original
     * @param codeVerifier PKCE verifier
     */
    private ResponseEntity<Map<String, Object>> handleAuthorizationCodeGrant(
            String code, String redirectUri, String clientId, String codeVerifier) {

        if (code == null || code.isEmpty()) {
            return createErrorResponse("invalid_request",
                "code is required for the authorization_code grant", HttpStatus.BAD_REQUEST);
        }

        // Redeeming removes the code, so a replay of the same code fails even if this
        // exchange goes on to fail for another reason.
        var redeemed = codeStore.redeem(code);
        if (redeemed.isEmpty()) {
            return createErrorResponse("invalid_grant",
                "Authorization code is invalid, expired or has already been used",
                HttpStatus.BAD_REQUEST);
        }
        AuthorizationCode details = redeemed.get();

        if (details.clientId() != null && !details.clientId().isEmpty()
                && clientId != null && !clientId.isEmpty()
                && !details.clientId().equals(clientId)) {
            logger.warn("Authorization code rejected: issued to client '{}' but presented by '{}'",
                        details.clientId(), clientId);
            return createErrorResponse("invalid_grant",
                "Authorization code was issued to a different client", HttpStatus.BAD_REQUEST);
        }

        if (details.redirectUri() != null && redirectUri != null && !redirectUri.isEmpty()
                && !details.redirectUri().equals(redirectUri)) {
            logger.warn("Authorization code rejected: redirect_uri mismatch (issued for '{}', "
                        + "presented '{}')", details.redirectUri(), redirectUri);
            return createErrorResponse("invalid_grant",
                "redirect_uri does not match the authorization request", HttpStatus.BAD_REQUEST);
        }

        if (details.codeChallenge() != null && !details.codeChallenge().isEmpty()
                && !verifyPkce(details.codeChallenge(), details.codeChallengeMethod(), codeVerifier)) {
            return createErrorResponse("invalid_grant", "PKCE verification failed",
                HttpStatus.BAD_REQUEST);
        }

        long lifetime = details.tokenLifetimeSeconds() != null
            ? details.tokenLifetimeSeconds()
            : tokenExpirationSeconds;

        String accessToken = generateAccessToken(clientId, details.username(), details.scope(),
            details.roles(), "authorization_code", lifetime);

        Map<String, Object> response = new HashMap<>();
        response.put("access_token", accessToken);
        response.put("token_type", "Bearer");
        response.put("expires_in", lifetime);
        if (details.scope() != null && !details.scope().isEmpty()) {
            response.put("scope", details.scope());
        }

        logger.info("Authorization code exchanged successfully for client: {}, user: {}, roles: {}",
                    clientId, details.username(), String.join(",", details.roles()));

        return ResponseEntity.ok(response);
    }

    /**
     * Verifies a PKCE code_verifier against the stored challenge (RFC 7636).
     *
     * MarkLogic 12.1 always sends code_challenge_method=S256, which is the case that
     * matters; "plain" is accepted for completeness.
     */
    private boolean verifyPkce(String challenge, String method, String verifier) {
        if (verifier == null || verifier.isEmpty()) {
            logger.warn("PKCE verification failed: code_verifier was not supplied but the "
                        + "authorization request included a code_challenge");
            return false;
        }

        String computed;
        if (method == null || method.isEmpty() || method.equalsIgnoreCase("plain")) {
            computed = verifier;
        } else if (method.equalsIgnoreCase("S256")) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
                computed = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            } catch (Exception e) {
                logger.error("PKCE verification failed: could not compute the S256 challenge", e);
                return false;
            }
        } else {
            logger.warn("PKCE verification failed: unsupported code_challenge_method '{}'", method);
            return false;
        }

        // Constant-time comparison; the challenge is not secret but the habit is cheap.
        boolean matches = MessageDigest.isEqual(
            computed.getBytes(StandardCharsets.US_ASCII),
            challenge.getBytes(StandardCharsets.US_ASCII));

        if (!matches) {
            logger.warn("PKCE verification failed: code_verifier does not match the challenge");
        }
        return matches;
    }

    /**
     * Decodes an HTTP Basic Authorization header into client id and secret.
     *
     * @return a two element array, or null when the header is absent or not Basic
     */
    private String[] parseBasicAuthorization(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String decoded = new String(
                Base64.getDecoder().decode(authorization.substring(6).trim()),
                StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator < 0) {
                return null;
            }
            return new String[]{decoded.substring(0, separator), decoded.substring(separator + 1)};
        } catch (Exception e) {
            logger.warn("Could not decode the Basic Authorization header", e);
            return null;
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
        return generateAccessToken(clientId, username, scope, roles, grantType, tokenExpirationSeconds);
    }

    /**
     * Generate a JWT access token with an explicit lifetime.
     *
     * The Authorization Code flow carries a lifetime chosen at the login page, which lets
     * an operator deliberately mint an already-expired token to see how MarkLogic reports
     * the rejection.
     *
     * @param lifetimeSeconds token lifetime in seconds; may be negative to produce an
     *                        already-expired token
     */
    private String generateAccessToken(String clientId, String username, String scope,
                                       List<String> roles, String grantType, long lifetimeSeconds) {
        
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(lifetimeSeconds);
        
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
        return discoveryResponse("/oauth/.well-known/config");
    }

    /**
     * OpenID Connect discovery document, served at the standard location.
     *
     * Clients conventionally look for metadata here rather than at the MLEAProxy-specific
     * /oauth/.well-known/config path, so both are served with identical content.
     *
     * GET /.well-known/openid-configuration
     */
    @GetMapping(value = "/.well-known/openid-configuration", produces = "application/json")
    public ResponseEntity<Map<String, Object>> openidConfiguration() {
        return discoveryResponse("/.well-known/openid-configuration");
    }

    /**
     * OAuth 2.0 Authorization Server Metadata (RFC 8414).
     *
     * GET /.well-known/oauth-authorization-server
     */
    @GetMapping(value = "/.well-known/oauth-authorization-server", produces = "application/json")
    public ResponseEntity<Map<String, Object>> oauthAuthorizationServerMetadata() {
        return discoveryResponse("/.well-known/oauth-authorization-server");
    }

    private ResponseEntity<Map<String, Object>> discoveryResponse(String path) {
        try {
            logger.debug("OAuth discovery endpoint called: {}", path);
            Map<String, Object> config = buildDiscoveryDocument();
            logger.info("OAuth configuration discovery served successfully from {}", path);
            return ResponseEntity.ok(config);
        } catch (Exception ex) {
            logger.error("Error generating OAuth configuration", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Builds the discovery metadata shared by all three discovery endpoints.
     *
     * Note on authorization_code: the grant is advertised so that MarkLogic will initiate
     * the Authorization Code flow and its authorize request can be captured. The
     * /oauth/authorize endpoint is currently in capture mode and does not yet issue codes,
     * so a code exchange at /oauth/token will still be rejected.
     */
    private Map<String, Object> buildDiscoveryDocument() {
        Map<String, Object> config = new LinkedHashMap<>();

        // OAuth 2.0 Authorization Server Metadata fields
        config.put("issuer", jwtIssuer);
        config.put("authorization_endpoint", authorizeBaseUrl() + "/oauth/authorize");
        config.put("token_endpoint", baseUrl + "/oauth/token");
        config.put("jwks_uri", baseUrl + "/oauth/jwks");

        // Supported grant types
        config.put("grant_types_supported",
            List.of("password", "client_credentials", "authorization_code"));

        // Supported response types
        config.put("response_types_supported", List.of("token", "code"));

        // PKCE (RFC 7636) challenge methods
        config.put("code_challenge_methods_supported", List.of("S256", "plain"));

        // Token endpoint authentication methods
        config.put("token_endpoint_auth_methods_supported",
            List.of("client_secret_post", "client_secret_basic"));

        // Supported signing algorithms
        config.put("id_token_signing_alg_values_supported", List.of("RS256"));

        // Additional claims we support
        config.put("claims_supported", List.of(
            "iss", "sub", "aud", "exp", "iat", "jti",
            "client_id", "grant_type", "username", "scope", "roles", "roles_string"
        ));

        // Scopes supported (extensible)
        config.put("scopes_supported", List.of("openid", "profile", "email"));

        return config;
    }

    /**
     * Base URL for the authorization endpoint.
     *
     * The Authorization Code flow redirects a browser to a login page, which is served by
     * the HTTPS listener rather than the primary HTTP port. The token and JWKS endpoints
     * keep using {@link #baseUrl} so that existing Resource Server configurations pointing
     * at HTTP continue to work unchanged.
     *
     * Priority:
     * 1. Explicitly configured oauth.authorize.base.url
     * 2. https://<hostname>:<mleaproxy.https.port> when the HTTPS listener is enabled
     * 3. The primary base URL
     */
    private String authorizeBaseUrl() {
        if (configuredAuthorizeBaseUrl != null && !configuredAuthorizeBaseUrl.isEmpty()) {
            return configuredAuthorizeBaseUrl;
        }
        if (httpsEnabled && httpsPort > 0) {
            return "https://" + getServerHostname() + ":" + httpsPort;
        }
        return baseUrl;
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
