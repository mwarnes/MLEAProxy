# OAuth 2.0 Configuration Examples

This directory contains example OAuth 2.0 configuration files for various deployment scenarios.

---

## 📋 Available Examples

| File | Scenario | Description |
|------|----------|-------------|
| **01-oauth-basic.properties** | Basic OAuth | Simple OAuth 2.0 JWT token generation |
| **02-oauth-custom-validity.properties** | Custom Token Validity | Configure token expiration times |
| **03-oauth-custom-keys.properties** | Custom Keys | Use custom RSA keys for signing |
| **04-oauth-with-refresh.properties** | Refresh Tokens | Enable OAuth refresh token support |

---

## 🚀 Quick Start

### Using an Example

1. **Choose an example** from the list above
2. **Copy to root directory**:
   ```bash
   cp examples/oauth/01-oauth-basic.properties oauth.properties
   ```
3. **Edit configuration** (update paths, validity as needed)
4. **Run MLEAProxy**:
   ```bash
   java -jar target/mlesproxy-2.0.0.jar
   ```

### Testing Your Configuration

```bash
# Test OAuth token generation
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"

# Test JWKS endpoint
curl http://localhost:8080/oauth/jwks

# Test discovery endpoint
curl http://localhost:8080/.well-known/openid-configuration
```

---

## 📖 Example Details

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
- Configurable refresh token validity (default: 86400 seconds)
- Per-environment token lifetimes

**Configuration Options**:
```properties
# Short-lived (15 minutes)
oauth.token.validity=900

# Medium-lived (1 hour)
oauth.token.validity=3600

# Long-lived (8 hours)
oauth.token.validity=28800

# Refresh tokens (1 day)
oauth.refresh.token.validity=86400
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
oauth.keypath=/path/to/privkey.pem
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

## 🔧 Configuration Reference

### Common Properties

```properties
# OAuth token validity (seconds)
oauth.token.validity=3600

# Refresh token validity (seconds)
oauth.refresh.token.validity=86400

# RSA private key path
oauth.keypath=classpath:static/certificates/privkey.pem

# Default roles when user not found
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

## 🔒 Security Considerations

### Token Validity

**Short-Lived Tokens (Recommended for Production)**:
```properties
# 15 minutes for access tokens
oauth.token.validity=900

# 1 day for refresh tokens
oauth.refresh.token.validity=86400
```

**Development/Testing**:
```properties
# 8 hours for access tokens
oauth.token.validity=28800

# 7 days for refresh tokens
oauth.refresh.token.validity=604800
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

## 🧪 Testing Examples

### Test Password Grant

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"
```

### Test Client Credentials Grant

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=client_credentials" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"
```

### Test Refresh Token Grant

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=refresh_token" \
  -d "refresh_token=YOUR_REFRESH_TOKEN" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"
```

### Verify JWT Token

```bash
# Decode JWT token (header and payload)
echo "YOUR_TOKEN" | cut -d. -f1-2 | sed 's/\./\n/' | while read part; do echo $part | base64 -d 2>/dev/null | jq .; done
```

### Test JWKS Endpoint

```bash
# Get public keys for verification
curl http://localhost:8080/oauth/jwks | jq .
```

### Test Discovery Endpoint

```bash
# Get OAuth/OIDC configuration
curl http://localhost:8080/.well-known/openid-configuration | jq .
```

---

## 📚 Additional Resources

- **[OAUTH_GUIDE.md](../../docs/user/OAUTH_GUIDE.md)** - Complete OAuth documentation
- **[README.md](../../README.md)** - General application overview
- **[JWT.io](https://jwt.io)** - JWT token decoder and debugger

---

## 🆘 Troubleshooting

### Token Generation Failed

```bash
# Enable debug logging
logging.level.com.marklogic.handlers.undertow.OAuthTokenHandler=DEBUG
```

### Invalid User Credentials

```bash
# Check users.json file exists and is valid JSON
cat users.json | jq .
```

### JWKS Endpoint Not Working

```bash
# Verify RSA key is accessible
oauth.keypath=classpath:static/certificates/privkey.pem

# Or absolute path
oauth.keypath=/path/to/privkey.pem
```

### Token Expired Too Quickly

```bash
# Increase token validity
oauth.token.validity=7200  # 2 hours
```

---

<div align="center">

**For more information, see the [OAuth Guide](../../docs/user/OAUTH_GUIDE.md)**

</div>
