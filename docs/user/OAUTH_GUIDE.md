# 🔑 MLEAProxy OAuth 2.0 Guide

Complete guide for OAuth 2.0 token generation and JWT verification in MLEAProxy.

---

## 📋 Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Endpoints](#endpoints)
- [Configuration](#configuration)
- [Role Resolution](#role-resolution)
- [Usage Examples](#usage-examples)
- [Client Integration](#client-integration)
- [Security](#security)
- [API Reference](#api-reference)

---

## Overview

MLEAProxy provides complete OAuth 2.0 authorization server functionality with JWT token generation and verification support.

### 🎯 Key Features

- 🔐 **Token Generation**: JWT access token generation with RSA signatures
- 🔑 **JWKS Endpoint**: Public key discovery for JWT verification (RFC 7517)
- 📋 **Server Metadata**: OAuth 2.0 authorization server metadata (RFC 8414)
- 🔐 **Multiple Grant Types**: Password and client credentials flows
- 👥 **Role Support**: Custom claims for user roles and permissions
- 🎭 **3-Tier Role Resolution**: Flexible role assignment with fallback (New in 2025)

### 📊 OAuth Endpoints

| Endpoint | Method | Purpose | RFC |
|----------|--------|---------|-----|
| `/oauth/token` | POST | Generate JWT access tokens | RFC 6749 |
| `/oauth/jwks` | GET | Public key discovery (JWKS) | RFC 7517 |
| `/oauth/.well-known/config` | GET | Server metadata | RFC 8414 |

---

## Quick Start

### Generate Your First Token

```bash
# Start MLEAProxy
java -jar mleaproxy.jar

# Generate OAuth token
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"
```

**Response:**

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6Im1sZWFwcm94eS1rZXkifQ...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### Verify Token with JWKS

```bash
# Get public keys for verification
curl http://localhost:8080/oauth/jwks
```

**Response:**

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "kid": "mleaproxy-key",
      "n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx...",
      "e": "AQAB",
      "alg": "RS256"
    }
  ]
}
```

---

## Endpoints

### 1️⃣ Token Endpoint (`/oauth/token`)

Generate JWT access tokens with user roles and permissions.

#### Request

**Method:** `POST`  
**Content-Type:** `application/x-www-form-urlencoded`

**Parameters:**

| Parameter | Required | Description | Example |
|-----------|----------|-------------|---------|
| `grant_type` | Yes | OAuth grant type | `password` or `client_credentials` |
| `username` | Yes* | Username for authentication | `admin` |
| `password` | Yes* | User password | `secret` |
| `client_id` | Yes | Client identifier | `marklogic` |
| `client_secret` | Yes | Client secret | `secret` |
| `roles` | No | Comma-separated roles (override) | `admin,user` |
| `scope` | No | OAuth scopes | `read write` |

*Required for `password` grant type

#### Response

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6Im1sZWFwcm94eS1rZXkifQ...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

#### Token Payload

```json
{
  "sub": "admin",
  "iss": "http://localhost:8080",
  "iat": 1704067200,
  "exp": 1704070800,
  "roles": ["admin", "user"],
  "scope": "read write",
  "client_id": "marklogic"
}
```

#### Examples

**Password Grant:**

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"
```

**With Custom Roles:**

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" \
  -d "client_id=marklogic" \
  -d "client_secret=secret" \
  -d "roles=admin,developer,analyst"
```

**Client Credentials Grant:**

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=client_credentials" \
  -d "client_id=service-account" \
  -d "client_secret=service-secret"
```

**With Basic Authentication:**

```bash
curl -X POST http://localhost:8080/oauth/token \
  -u "marklogic:secret" \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin"
```

### 2️⃣ JWKS Endpoint (`/oauth/jwks`)

Retrieve public keys for JWT signature verification.

#### Request

**Method:** `GET`  
**No parameters required**

#### Response

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "kid": "mleaproxy-key",
      "n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx...",
      "e": "AQAB",
      "alg": "RS256"
    }
  ]
}
```

#### Usage

```bash
curl http://localhost:8080/oauth/jwks
```

**Purpose:**
- Resource servers use this to validate JWT signatures
- API gateways verify token authenticity
- Microservices authenticate requests

### 3️⃣ Well-Known Config Endpoint (`/oauth/.well-known/config`)

OAuth 2.0 authorization server metadata for automated discovery.

#### Request

**Method:** `GET`  
**No parameters required**

#### Response

```json
{
  "issuer": "http://localhost:8080",
  "token_endpoint": "http://localhost:8080/oauth/token",
  "jwks_uri": "http://localhost:8080/oauth/jwks",
  "grant_types_supported": ["password", "client_credentials"],
  "response_types_supported": ["token"],
  "token_endpoint_auth_methods_supported": ["client_secret_post", "client_secret_basic"]
}
```

#### Usage

```bash
curl http://localhost:8080/oauth/.well-known/config
```

**Purpose:**
- Automated client configuration
- OpenID Connect compatibility
- Service discovery

---

## Configuration

### OAuth Properties (`oauth.properties`)

```properties
# ==========================================
# OAuth Issuer Configuration
# ==========================================
oauth.issuer=http://localhost:8080

# ==========================================
# Token Configuration
# ==========================================
oauth.token.expiry=3600              # Token lifetime in seconds (1 hour)
oauth.token.audience=marklogic       # Token audience claim
oauth.token.refresh-enabled=false    # Refresh token support (not implemented)

# ==========================================
# RSA Key Configuration
# ==========================================
oauth.rsa.key-size=2048              # RSA key size (2048 or 4096)
oauth.rsa.key-id=mleaproxy-key       # Key ID in JWKS
oauth.rsa.algorithm=RS256            # Signing algorithm

# ==========================================
# Grant Types
# ==========================================
oauth.grant-types.password.enabled=true
oauth.grant-types.client-credentials.enabled=true
oauth.grant-types.authorization-code.enabled=false
oauth.grant-types.refresh-token.enabled=false

# ==========================================
# Client Configuration
# ==========================================
oauth.client.id=marklogic
oauth.client.secret=secret
oauth.client.require-secret=true

# ==========================================
# Default Roles (New in 2025)
# ==========================================
# Default roles to assign when user is not found in users.json
# and no roles are specified in the request
# Use comma-separated list of roles
oauth.default.roles=user
```

### User Repository Configuration

MLEAProxy uses a JSON-based user repository for storing user credentials and roles.

**Location:** `src/main/resources/users.json`

**Format:**

```json
{
  "baseDN": "ou=users,dc=marklogic,dc=local",
  "users": [
    {
      "dn": "cn=manager",
      "sAMAccountName": "manager",
      "userPassword": "password",
      "roles": ["admin", "manager"]
    },
    {
      "dn": "cn=admin",
      "sAMAccountName": "admin",
      "userPassword": "admin",
      "roles": ["admin", "user"]
    },
    {
      "dn": "cn=testuser",
      "sAMAccountName": "testuser",
      "userPassword": "password",
      "roles": ["user"]
    }
  ]
}
```

**Custom Location:**

Specify custom user repository location:

```properties
# In application.properties
users.json.path=/path/to/custom/users.json
```

**Command Line Override:**

```bash
# Override users.json path at startup (recommended - shorter parameter)
java -jar mleaproxy.jar --users=/path/to/custom/users.json

# Alternative syntax (also supported)
java -jar mleaproxy.jar --users-json=/path/to/custom/users.json

# Or use relative path
java -jar mleaproxy.jar --users=src/main/resources/users.json
```

---

## Role Resolution

### 🎯 3-Tier Role Resolution System (New in 2025)

MLEAProxy implements a sophisticated 3-tier priority system for role assignment:

```
┌─────────────────────────────────────────┐
│  Priority 1: Request Parameter Roles    │  ← Highest Priority
│  (Explicit override via 'roles' param)  │
└─────────────────────────────────────────┘
              ↓ (if not provided)
┌─────────────────────────────────────────┐
│  Priority 2: JSON User Repository       │  ← Medium Priority
│  (User's assigned roles in users.json)  │
└─────────────────────────────────────────┘
              ↓ (if user not found)
┌─────────────────────────────────────────┐
│  Priority 3: Default Configuration      │  ← Lowest Priority
│  (oauth.default.roles property)         │
└─────────────────────────────────────────┘
```

### How It Works

#### Priority 1: Request Parameter Roles

When `roles` parameter is provided in the token request, it **always overrides** other sources:

```bash
# Explicitly request specific roles
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" \
  -d "client_id=marklogic" \
  -d "client_secret=secret" \
  -d "roles=admin,developer"

# Token will contain: "roles": ["admin", "developer"]
# Even if users.json has different roles for this user
```

**Use Cases:**
- Testing with specific roles
- Temporary privilege elevation
- Role-based testing scenarios

#### Priority 2: JSON User Repository

When no `roles` parameter is provided and user exists in `users.json`:

```json
{
  "users": [
    {
      "sAMAccountName": "admin",
      "userPassword": "admin",
      "roles": ["admin", "user", "developer"]
    }
  ]
}
```

```bash
# Request without roles parameter
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"

# Token will contain: "roles": ["admin", "user", "developer"]
# From users.json
```

**Use Cases:**
- Normal authentication flow
- User-specific role assignment
- Production scenarios

#### Priority 3: Default Configuration

When user is **not found** in `users.json` and no `roles` parameter provided:

```properties
# oauth.properties
oauth.default.roles=user,guest
```

```bash
# Authenticate with unknown user
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=unknown_user" \
  -d "password=password" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"

# Token will contain: "roles": ["user", "guest"]
# From oauth.default.roles property
```

**Use Cases:**
- Guest access
- Default minimal permissions
- Fallback for new users

### Configuration Examples

#### Example 1: Minimal Access by Default

```properties
oauth.default.roles=guest,readonly
```

All unknown users get read-only guest access.

#### Example 2: Standard User Access

```properties
oauth.default.roles=user
```

Unknown users get standard user role.

#### Example 3: No Default Access

```properties
oauth.default.roles=
```

Unknown users get no roles (empty array).

#### Example 4: Multiple Default Roles

```properties
oauth.default.roles=user,viewer,reporter
```

Unknown users get multiple default roles.

### Logging

Role resolution decisions are logged for troubleshooting:

```log
# Priority 1: Request parameter roles
INFO  - Using roles from request parameter for user 'admin': [developer, tester]

# Priority 2: JSON user roles
INFO  - Using roles from JSON for user 'admin': [admin, user, developer]

# Priority 3: Default roles
INFO  - User 'unknown' not found in JSON, using default roles: user
```

---

## Usage Examples

### Scenario 1: Basic Authentication

Simple password grant with default roles:

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=testuser" \
  -d "password=password" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"
```

**Result:**
- User `testuser` found in users.json
- Roles from users.json: `["user"]`
- Token includes user's assigned roles

### Scenario 2: Role Override

Override user's default roles for testing:

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=testuser" \
  -d "password=password" \
  -d "client_id=marklogic" \
  -d "client_secret=secret" \
  -d "roles=admin,developer"
```

**Result:**
- Request parameter roles take precedence
- Token includes: `["admin", "developer"]`
- Original user roles ignored

### Scenario 3: Unknown User

Authenticate user not in repository:

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=new_user" \
  -d "password=password" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"
```

**Result:**
- User `new_user` not found in users.json
- Default roles applied: `["user"]` (from oauth.default.roles)
- Token includes default roles

### Scenario 4: Service Account

Client credentials grant (no user):

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=client_credentials" \
  -d "client_id=service-account" \
  -d "client_secret=service-secret"
```

**Result:**
- No user context
- Default roles applied: `["user"]`
- Token subject is client_id

### Scenario 5: Parse and Verify Token

```bash
# Generate token
TOKEN=$(curl -s -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password&username=admin&password=admin" \
  -d "client_id=marklogic&client_secret=secret" \
  | jq -r .access_token)

# Decode JWT header and payload (without verification)
echo $TOKEN | cut -d. -f1 | base64 -d | jq .
echo $TOKEN | cut -d. -f2 | base64 -d | jq .

# Verify signature using JWKS (requires jose CLI or similar)
```

---

## Client Integration

### JavaScript/Node.js

**Using `jose` library:**

```javascript
const jose = require('jose');
const axios = require('axios');

// Create JWKS client
const JWKS = jose.createRemoteJWKSet(
  new URL('http://localhost:8080/oauth/jwks')
);

// Verify token
async function verifyToken(token) {
  try {
    const { payload } = await jose.jwtVerify(token, JWKS, {
      issuer: 'http://localhost:8080',
      audience: 'marklogic'
    });
    
    console.log('Valid token:', payload);
    console.log('User:', payload.sub);
    console.log('Roles:', payload.roles);
    return payload;
  } catch (err) {
    console.error('Invalid token:', err.message);
    throw err;
  }
}

// Generate token
async function generateToken(username, password) {
  const response = await axios.post('http://localhost:8080/oauth/token', 
    new URLSearchParams({
      grant_type: 'password',
      username: username,
      password: password,
      client_id: 'marklogic',
      client_secret: 'secret'
    })
  );
  
  return response.data.access_token;
}

// Usage
(async () => {
  const token = await generateToken('admin', 'admin');
  const payload = await verifyToken(token);
})();
```

### Python

**Using `python-jose` library:**

```python
from jose import jwt, jwk
import requests

# Get JWKS
def get_jwks():
    response = requests.get('http://localhost:8080/oauth/jwks')
    return response.json()

# Verify token
def verify_token(token):
    jwks = get_jwks()
    public_key = jwk.construct(jwks['keys'][0])
    
    try:
        claims = jwt.decode(
            token, 
            public_key, 
            algorithms=['RS256'],
            issuer='http://localhost:8080',
            audience='marklogic'
        )
        print(f"Valid token for user: {claims['sub']}")
        print(f"Roles: {claims['roles']}")
        return claims
    except jwt.JWTError as e:
        print(f"Invalid token: {e}")
        raise

# Generate token
def generate_token(username, password):
    response = requests.post('http://localhost:8080/oauth/token',
        data={
            'grant_type': 'password',
            'username': username,
            'password': password,
            'client_id': 'marklogic',
            'client_secret': 'secret'
        }
    )
    return response.json()['access_token']

# Usage
if __name__ == '__main__':
    token = generate_token('admin', 'admin')
    claims = verify_token(token)
```

### Java/Spring Security

**Resource Server Configuration:**

```java
@Configuration
@EnableWebSecurity
public class ResourceServerConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasAuthority("SCOPE_admin")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwkSetUri("http://localhost:8080/oauth/jwks")
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );
        return http.build();
    }
    
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        converter.setAuthoritiesClaimName("roles");
        converter.setAuthorityPrefix("ROLE_");
        
        JwtAuthenticationConverter authConverter = new JwtAuthenticationConverter();
        authConverter.setJwtGrantedAuthoritiesConverter(converter);
        return authConverter;
    }
}
```

**Client Configuration:**

```yaml
# application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080
          jwk-set-uri: http://localhost:8080/oauth/jwks
```

### cURL Examples

**Generate token and use it:**

```bash
# 1. Generate token
TOKEN=$(curl -s -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" \
  -d "client_id=marklogic" \
  -d "client_secret=secret" \
  | jq -r .access_token)

# 2. Use token to access protected resource
curl -H "Authorization: Bearer $TOKEN" \
  http://your-api-server:8080/api/protected/resource

# 3. Inspect token payload
echo $TOKEN | cut -d. -f2 | base64 -d | jq .
```

---

## Security

### 1. JWT Token Security

**RSA-256 Signatures:**

```properties
# Strong key configuration
oauth.rsa.key-size=4096
oauth.rsa.key-id=mleaproxy-key-$(date +%Y%m)
oauth.rsa.algorithm=RS256

# Token security
oauth.token.expiry=1800        # 30 minutes (shorter is more secure)
oauth.token.audience=marklogic  # Audience validation
```

**Token Claims:**

```json
{
  "iss": "http://localhost:8080",    // Validated issuer
  "sub": "admin",                      // Subject (username)
  "aud": "marklogic",                  // Audience validation
  "exp": 1704070800,                   // Expiration timestamp
  "iat": 1704067200,                   // Issued at timestamp
  "nbf": 1704067200,                   // Not before timestamp
  "jti": "unique-token-id",            // JWT ID (prevent replay)
  "roles": ["admin", "user"],          // Authorization roles
  "kid": "mleaproxy-key"               // Key ID for rotation
}
```

### 2. Client Authentication

**Supported Methods:**

```bash
# Method 1: Client Secret in POST body
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"

# Method 2: HTTP Basic Authentication
curl -X POST http://localhost:8080/oauth/token \
  -u "marklogic:secret" \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin"
```

### 3. Token Validation

**Resource servers should validate:**

- ✅ Signature (using JWKS public key)
- ✅ Issuer (`iss` claim)
- ✅ Audience (`aud` claim)
- ✅ Expiration (`exp` claim)
- ✅ Not before (`nbf` claim)
- ✅ Token ID (`jti` claim for replay prevention)

### 4. Best Practices

**Production Checklist:**

- [ ] Use HTTPS for all OAuth endpoints
- [ ] Implement rate limiting on token endpoint
- [ ] Rotate RSA keys regularly (every 90 days)
- [ ] Use short token expiry times (15-30 minutes)
- [ ] Implement token revocation if needed
- [ ] Monitor for unusual token generation patterns
- [ ] Store client secrets securely (environment variables)
- [ ] Enable audit logging for token generation
- [ ] Validate all token claims on resource server
- [ ] Use strong client secrets (minimum 32 characters)

**Secure Configuration:**

```properties
# Use environment variables for secrets
oauth.client.secret=${OAUTH_CLIENT_SECRET}

# Short token lifetime
oauth.token.expiry=1800

# Strong RSA keys
oauth.rsa.key-size=4096

# Audience validation
oauth.token.audience=your-application-name
```

---

## API Reference

### Configuration Properties

```properties
# ==========================================
# OAuth Issuer
# ==========================================
oauth.issuer=http://localhost:8080

# ==========================================
# Token Configuration
# ==========================================
oauth.token.expiry=3600                    # Default: 3600 seconds
oauth.token.audience=marklogic             # Default: marklogic
oauth.token.refresh-enabled=false          # Default: false

# ==========================================
# RSA Configuration
# ==========================================
oauth.rsa.key-size=2048                    # Default: 2048 (2048 or 4096)
oauth.rsa.key-id=mleaproxy-key             # Default: mleaproxy-key
oauth.rsa.algorithm=RS256                  # Default: RS256

# ==========================================
# Grant Types
# ==========================================
oauth.grant-types.password.enabled=true
oauth.grant-types.client-credentials.enabled=true
oauth.grant-types.authorization-code.enabled=false
oauth.grant-types.refresh-token.enabled=false

# ==========================================
# Client Configuration
# ==========================================
oauth.client.id=marklogic
oauth.client.secret=secret
oauth.client.require-secret=true

# ==========================================
# Default Roles (New in 2025)
# ==========================================
oauth.default.roles=user                   # Comma-separated list
```

### HTTP Status Codes

| Code | Meaning | Scenario |
|------|---------|----------|
| 200 | OK | Token generated successfully |
| 400 | Bad Request | Missing required parameters |
| 401 | Unauthorized | Invalid credentials |
| 405 | Method Not Allowed | Wrong HTTP method |
| 500 | Internal Server Error | Server error |

### Error Responses

```json
{
  "error": "invalid_grant",
  "error_description": "Invalid username or password"
}
```

**Common Errors:**

- `invalid_request` - Missing required parameters
- `invalid_client` - Invalid client credentials
- `invalid_grant` - Invalid username/password
- `unsupported_grant_type` - Grant type not enabled
- `server_error` - Internal server error

---

## Standards References

- [RFC 6749](https://tools.ietf.org/html/rfc6749) - OAuth 2.0 Authorization Framework
- [RFC 7517](https://tools.ietf.org/html/rfc7517) - JSON Web Key (JWK)
- [RFC 7519](https://tools.ietf.org/html/rfc7519) - JSON Web Token (JWT)
- [RFC 8414](https://tools.ietf.org/html/rfc8414) - OAuth 2.0 Authorization Server Metadata

---

## Related Documentation

- **[README.md](./README.md)** - General application overview
- **[LDAP_GUIDE.md](./LDAP_GUIDE.md)** - LDAP functionality
- **[SAML_GUIDE.md](./SAML_GUIDE.md)** - SAML 2.0 functionality
- **[OAUTH_JWKS_WELLKNOWN_COMPLETE.md](./OAUTH_JWKS_WELLKNOWN_COMPLETE.md)** - Detailed JWKS documentation
- **[TESTING_GUIDE.md](./TESTING_GUIDE.md)** - Testing procedures

---

<div align="center">

**[⬆ Back to Top](#-mleaproxy-oauth-20-guide)**

</div>
