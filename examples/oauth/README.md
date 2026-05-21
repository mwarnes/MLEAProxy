# OAuth 2.0 Configuration Examples

This directory contains example OAuth 2.0 configuration files for various deployment scenarios.

---

## Available Examples

| File | Scenario | Description |
|------|----------|-------------|
| **01-oauth-basic.properties** | Basic OAuth | Simple OAuth 2.0 JWT token generation |
| **02-oauth-custom-validity.properties** | Custom Token Validity | Configure token expiration times |
| **03-oauth-custom-keys.properties** | Custom Keys | Use custom RSA keys for signing |
| **04-oauth-with-refresh.properties** | Refresh Tokens | Enable OAuth refresh token support |

---

## Quick Start

### Using an Example

1. **Choose an example** from the list above
2. **Copy to root directory**:
   ```bash
   cp examples/oauth/01-oauth-basic.properties mleaproxy.properties
   ```
3. **Edit configuration** (update paths, validity as needed)
4. **Run MLEAProxy**:
   ```bash
   java -jar target/mlesproxy-2.0.0.jar
   ```

### Expected Startup Output

When OAuth is configured, you should see these endpoints registered at startup:

```
============================================================
                   MLEAProxy Startup Summary
============================================================
Version: 2.0.0
Started: 2024-01-15 10:30:45

------------------------------------------------------------
                    Web Server Endpoints
------------------------------------------------------------
Port: 8080

OAuth Endpoints:
  POST /oauth/token                    - Get access/refresh tokens
  GET  /oauth/jwks                     - JSON Web Key Set
  GET  /.well-known/openid-configuration - Discovery endpoint

Utility Endpoints:
  GET  /b64encode                      - Base64 encode
  GET  /b64decode                      - Base64 decode
============================================================
```

---

## Comprehensive Testing

### 1. Token Generation (Password Grant)

```bash
curl -s -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" \
  -d "client_id=marklogic" \
  -d "client_secret=secret" | jq .
```

**Expected Response:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsImlzcyI6Im1sZWFwcm94eS1vYXV0aC1zZXJ2ZXIiLCJpYXQiOjE3MDUzMTIwMDAsImV4cCI6MTcwNTMxNTYwMCwicm9sZXMiOlsiYWRtaW4iXX0.signature...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

### 2. Token Generation (Client Credentials Grant)

```bash
curl -s -X POST http://localhost:8080/oauth/token \
  -d "grant_type=client_credentials" \
  -d "client_id=marklogic" \
  -d "client_secret=secret" | jq .
```

**Expected Response:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### 3. Refresh Token Grant

```bash
# Use the refresh_token from step 1
curl -s -X POST http://localhost:8080/oauth/token \
  -d "grant_type=refresh_token" \
  -d "refresh_token=a1b2c3d4-e5f6-7890-abcd-ef1234567890" \
  -d "client_id=marklogic" \
  -d "client_secret=secret" | jq .
```

**Expected Response:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "new-refresh-token-uuid"
}
```

### 4. JWKS Endpoint

```bash
curl -s http://localhost:8080/oauth/jwks | jq .
```

**Expected Response:**
```json
{
  "keys": [
    {
      "kty": "RSA",
      "n": "vN9W7KqVb5J3H...(base64url-encoded modulus)...",
      "e": "AQAB",
      "alg": "RS256",
      "use": "sig",
      "kid": "mleaproxy-signing-key"
    }
  ]
}
```

### 5. Discovery Endpoint

```bash
curl -s http://localhost:8080/.well-known/openid-configuration | jq .
```

**Expected Response:**
```json
{
  "issuer": "mleaproxy-oauth-server",
  "authorization_endpoint": "http://localhost:8080/oauth/authorize",
  "token_endpoint": "http://localhost:8080/oauth/token",
  "jwks_uri": "http://localhost:8080/oauth/jwks",
  "response_types_supported": ["code", "token"],
  "grant_types_supported": ["password", "client_credentials", "refresh_token"],
  "subject_types_supported": ["public"],
  "id_token_signing_alg_values_supported": ["RS256"],
  "token_endpoint_auth_methods_supported": ["client_secret_post", "client_secret_basic"]
}
```

### 6. Decode JWT Token

Decode the JWT to inspect its contents:

```bash
# Extract and decode header
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsImlzcyI6Im1sZWFwcm94eS1vYXV0aC1zZXJ2ZXIiLCJpYXQiOjE3MDUzMTIwMDAsImV4cCI6MTcwNTMxNTYwMCwicm9sZXMiOlsiYWRtaW4iXX0.signature"

# Decode header
echo $TOKEN | cut -d. -f1 | base64 -d 2>/dev/null | jq .

# Decode payload
echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq .
```

**Expected Header:**
```json
{
  "alg": "RS256",
  "typ": "JWT"
}
```

**Expected Payload:**
```json
{
  "sub": "admin",
  "iss": "mleaproxy-oauth-server",
  "iat": 1705312000,
  "exp": 1705315600,
  "roles": ["admin"]
}
```

### 7. Test with Custom Roles

```bash
curl -s -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" \
  -d "client_id=marklogic" \
  -d "client_secret=secret" \
  -d "roles=custom-role,another-role" | jq .
```

---

## Example Details

### 1. Basic OAuth Configuration

**File**: `01-oauth-basic.properties`

**Use Case**:
- Development and testing
- Quick OAuth 2.0 JWT token generation
- Default settings

**Key Features**:
- Uses bundled RSA private key
- 1-hour token validity (3600 seconds)
- Default role assignment

**Endpoints**:
- Token: `http://localhost:8080/oauth/token`
- JWKS: `http://localhost:8080/oauth/jwks`
- Discovery: `http://localhost:8080/.well-known/openid-configuration`

**Requirements**:
- `users.json` file for user lookup

---

### 2. Custom Token Validity

**File**: `02-oauth-custom-validity.properties`

**Use Case**:
- Custom token expiration times
- Short-lived tokens for security
- Long-lived tokens for development

**Key Features**:
- Configurable access token validity (default: 3600 seconds)
- Configurable refresh token validity (default: 2592000 seconds / 30 days)
- Per-environment token lifetimes

**Configuration Options**:
```properties
# Short-lived (15 minutes)
oauth.token.expiration.seconds=900

# Medium-lived (1 hour)
oauth.token.expiration.seconds=3600

# Long-lived (8 hours)
oauth.token.expiration.seconds=28800

# Refresh tokens (7 days)
oauth.refresh.token.expiry.seconds=604800
```

---

### 3. Custom RSA Keys

**File**: `03-oauth-custom-keys.properties`

**Use Case**:
- Production environments
- Custom key management
- Key rotation

**Key Features**:
- Custom RSA private key path
- PEM format support
- Automatic public key derivation

**Configuration Required**:
- RSA private key in PEM format (2048-bit or 4096-bit)

**Generate Custom Keys**:
```bash
# Generate 2048-bit RSA key pair
openssl genrsa -out privkey.pem 2048

# Extract public key (for verification)
openssl rsa -in privkey.pem -pubout -out pubkey.pem

# Configure MLEAProxy
oauth.signing.key.path=/path/to/privkey.pem
```

---

### 4. OAuth with Refresh Tokens

**File**: `04-oauth-with-refresh.properties`

**Use Case**:
- Long-lived sessions
- Token rotation
- Enhanced security

**Key Features**:
- Refresh token grant type
- Configurable refresh token validity
- Automatic token rotation
- Periodic cleanup of expired tokens

**Grant Types Supported**:
- `password` - Resource Owner Password Credentials
- `client_credentials` - Client Credentials
- `refresh_token` - Refresh Token

**Example Flow**:
```bash
# 1. Get initial access token
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password&username=admin&password=admin" \
  -d "client_id=marklogic&client_secret=secret"

# Response includes refresh_token

# 2. Use refresh token to get new access token
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=refresh_token&refresh_token=YOUR_REFRESH_TOKEN" \
  -d "client_id=marklogic&client_secret=secret"
```

---

## Configuration Reference

### All OAuth Properties

```properties
# Access token validity in seconds (default: 3600)
oauth.token.expiration.seconds=3600

# Refresh token validity in seconds (default: 2592000 = 30 days)
oauth.refresh.token.expiry.seconds=2592000

# Enable/disable refresh tokens (default: true)
oauth.refresh.token.enabled=true

# Cleanup interval for expired refresh tokens (default: 3600)
oauth.refresh.token.cleanup.interval.seconds=3600

# RSA private key path (default: bundled key)
oauth.signing.key.path=classpath:static/certificates/privkey.pem

# JWT issuer claim (default: mleaproxy-oauth-server)
oauth.jwt.issuer=mleaproxy-oauth-server

# Base URL for discovery endpoint (default: http://localhost:8080)
oauth.server.base.url=http://localhost:8080

# Default roles when user not found (default: user)
oauth.default.roles=user
```

### Role Resolution

OAuth role assignment follows a 3-tier resolution system:

1. **Request Parameter Roles** (highest priority)
   ```bash
   curl -X POST http://localhost:8080/oauth/token \
     -d "roles=admin,marklogic-admin"
   ```

2. **JSON User Repository Roles** (user-specific)
   - Configured in `users.json`

3. **Default Configuration Roles** (fallback)
   ```properties
   oauth.default.roles=user
   ```

---

## Security Considerations

### Token Validity

**Short-Lived Tokens (Recommended for Production)**:
```properties
# 15 minutes for access tokens
oauth.token.expiration.seconds=900

# 1 day for refresh tokens
oauth.refresh.token.expiry.seconds=86400
```

**Development/Testing**:
```properties
# 8 hours for access tokens
oauth.token.expiration.seconds=28800

# 7 days for refresh tokens
oauth.refresh.token.expiry.seconds=604800
```

### Key Management

**Development**:
- Use bundled keys for convenience
- No custom configuration needed

**Production**:
- Generate dedicated RSA key pairs
- Minimum 2048-bit keys (4096-bit recommended)
- Store keys securely (encrypted filesystem, HSM)
- Implement key rotation policies

---

## Troubleshooting

### Token Generation Failed

```bash
# Enable debug logging
logging.level.com.marklogic.handlers.undertow.OAuthTokenHandler=DEBUG
logging.level.com.marklogic.service.RefreshTokenService=DEBUG
```

### Invalid User Credentials

```bash
# Check users.json file exists and is valid JSON
cat users.json | jq .
```

### JWKS Endpoint Not Working

```bash
# Verify RSA key is accessible
oauth.signing.key.path=classpath:static/certificates/privkey.pem

# Or absolute path
oauth.signing.key.path=/path/to/privkey.pem
```

### Token Expired Too Quickly

```bash
# Increase token validity
oauth.token.expiration.seconds=7200  # 2 hours
```

### Refresh Token Not Returned

```bash
# Ensure refresh tokens are enabled
oauth.refresh.token.enabled=true

# Refresh tokens only returned for password grant, not client_credentials
```

---

## Additional Resources

- **[OAUTH_GUIDE.md](../../docs/user/OAUTH_GUIDE.md)** - Complete OAuth documentation
- **[README.md](../../README.md)** - General application overview
- **[JWT.io](https://jwt.io)** - JWT token decoder and debugger

---

**For more information, see the [OAuth Guide](../../docs/user/OAUTH_GUIDE.md)**
