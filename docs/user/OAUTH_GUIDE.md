# MLEAProxy OAuth 2.0 Guide

Complete guide for OAuth 2.0 token generation and JWT verification in MLEAProxy.

---

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Authorization Code Flow](#authorization-code-flow)
- [HTTPS and the CA Certificate](#https-and-the-ca-certificate)
- [Configuration Reference](#configuration-reference)
- [Token Generation Examples](#token-generation-examples)
- [JWKS and Discovery Endpoints](#jwks-and-discovery-endpoints)
- [Role Resolution](#role-resolution)
- [Client Integration](#client-integration)
- [Security Best Practices](#security-best-practices)
- [Troubleshooting](#troubleshooting)
- [MarkLogic Integration](#marklogic-integration)

---

## Overview

MLEAProxy provides OAuth 2.0 authorization server functionality with JWT token generation and JWKS-based verification support.

### Key Features

- **Token Generation**: JWT access tokens with RSA-256 signatures
- **JWKS Endpoint**: Public key discovery for JWT verification (RFC 7517)
- **Server Metadata**: OAuth 2.0 authorization server metadata (RFC 8414)
- **Grant Types**: Password, client credentials and authorization code flows
- **Authorization Code Flow**: Browser login page for MarkLogic 12.1 Admin UI and Query Console
- **PKCE**: `S256` and `plain` code challenge verification (RFC 7636)
- **HTTPS Listener**: Additional TLS listener with a generated private CA
- **Role Support**: Custom claims for user roles and permissions
- **Refresh Tokens**: Optional refresh token support with configurable expiry

### Endpoints

| Endpoint | Method | Purpose | RFC |
| ---------- | -------- | --------- | ----- |
| `/oauth/authorize` | GET, POST | Login page and authorization code issuance | RFC 6749 |
| `/oauth/token` | POST | Generate JWT access tokens | RFC 6749 |
| `/oauth/jwks` | GET | Public key discovery (JWKS) | RFC 7517 |
| `/.well-known/openid-configuration` | GET | Server metadata (standard path) | OIDC Discovery |
| `/.well-known/oauth-authorization-server` | GET | Server metadata (standard path) | RFC 8414 |
| `/oauth/.well-known/config` | GET | Server metadata (MLEAProxy path) | RFC 8414 |
| `/tls/ca` | GET | Download the HTTPS listener's CA certificate | - |

All three discovery paths return identical content. `/oauth/authorize` is served by the
HTTPS listener; see [HTTPS and the CA Certificate](#https-and-the-ca-certificate).

---

## Prerequisites

### Requirements

- Java 21 or later
- MLEAProxy 2.0.4 JAR file
- (Optional) `jq` for JSON parsing in examples
- (Optional) Custom RSA private key for production

### Installation

```bash
# Download the release JAR
# Place mlesproxy-2.0.4.jar in your working directory

# Verify Java version
java -version
# Expected: openjdk version "21.x.x" or higher
```

---

## Quick Start

### 1. Start MLEAProxy

```bash
java -jar mlesproxy-2.0.4.jar
```

### 2. Generate a Token

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=password" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"
```

**Expected Response:**

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Im1sZWFwcm94eS1rZXkifQ.eyJzdWIiOiJhZG1pbiIsImlzcyI6Im1sZWFwcm94eS1vYXV0aC1zZXJ2ZXIiLCJpYXQiOjE3MTYwNDgwMDAsImV4cCI6MTcxNjA1MTYwMCwicm9sZXMiOlsiYWRtaW4iXX0.signature",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### 3. Verify the JWKS Endpoint

```bash
curl http://localhost:8080/oauth/jwks
```

**Expected Response:**

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "kid": "mleaproxy-key",
      "alg": "RS256",
      "n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx...",
      "e": "AQAB"
    }
  ]
}
```

### 4. Decode and Inspect the Token

```bash
# Store token in a variable
TOKEN=$(curl -s -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=password" \
  -d "client_id=marklogic" \
  -d "client_secret=secret" | jq -r '.access_token')

# Decode the JWT payload (second part, between the dots)
echo "$TOKEN" | cut -d'.' -f2 | base64 -d 2>/dev/null | jq .
```

**Expected Output:**

```json
{
  "sub": "admin",
  "iss": "mleaproxy-oauth-server",
  "iat": 1716048000,
  "exp": 1716051600,
  "roles": ["admin"]
}
```

---

## Authorization Code Flow

> ### ⚠️ MarkLogic 12.1 or later only
>
> The Authorization Code flow exists to serve **MarkLogic 12.1+**, which can redirect
> Admin UI and Query Console users to an Authorization Server to sign in. Earlier
> MarkLogic releases support OAuth 2.0 only as a **Resource Server**, where a JWT obtained
> elsewhere is presented as a `Bearer` token. On MarkLogic 12.0 or earlier this flow is of
> no use - use the password or client credentials grants instead.
>
> For step-by-step MarkLogic configuration, see the
> **[MarkLogic Authorization Code Flow Guide](./MARKLOGIC_AUTHORIZATION_CODE_GUIDE.md)**.

### The login page

`GET /oauth/authorize` renders a login page in the same spirit as the SAML authentication
page: **credentials are not verified**. You enter the username and roles you want the
issued token to represent, which is what makes MLEAProxy useful for exercising an
application's authorization behaviour without maintaining real accounts.

| Field | Purpose |
| ------- | --------- |
| Userid | Becomes the `sub` and `username` claims |
| Roles | Comma separated; becomes the `roles` claim. Blank falls back to `users.json`, then `oauth.default.roles` |
| Token lifetime | Seconds. A **negative** value mints an already-expired token, to demonstrate rejection |
| Outcome | Approve issues a code; Deny returns `error=access_denied` |

A request that is not a usable authorization request - no `response_type` or no
`client_id` - renders a **diagnostic capture page** instead, listing every parameter and
header received, split into those defined by OAuth 2.0/OIDC and those that are not. This
is the quickest way to profile an unfamiliar client.

Every authorize request is also logged twice: as a readable block, and as a single JSON
line prefixed `OAUTH_AUTHORIZE_CAPTURE`:

```bash
grep OAUTH_AUTHORIZE_CAPTURE mleaproxy.log | tail -1
```

### Authorization codes

Codes are opaque, **single use**, and expire after 60 seconds by default
(`oauth.authorization-code.ttl-seconds`). A replayed code is rejected with
`invalid_grant`, which makes replay protection demonstrable rather than theoretical.

### Response modes

| `response_mode` | Behaviour |
| ----------------- | ----------- |
| `form_post` | The response is delivered as a self-submitting HTML form POST to the redirect URI. **MarkLogic 12.1 requires this.** |
| omitted or anything else | A 302 redirect with the code appended as query parameters |

`state` is always returned verbatim.

### PKCE

PKCE (RFC 7636) is verified when the authorize request includes a `code_challenge`:

- `S256` - the `code_verifier` is SHA-256 hashed and base64url compared
- `plain` - compared directly
- absent - no verification, and the exchange needs no `code_verifier`

MarkLogic 12.1 always sends `S256`.

### Exchanging the code

```bash
curl -s --cacert ca-certificate.pem \
  -X POST https://mleaproxy.example.com:8443/oauth/token \
  -d "grant_type=authorization_code" \
  -d "code=<code>" \
  -d "redirect_uri=https://app.example.com:8000" \
  -d "client_id=marklogic-qconsole" \
  -d "client_secret=secret" \
  -d "code_verifier=<verifier>"
```

The exchange is rejected with `invalid_grant` if the code is unknown, expired or already
used, if `redirect_uri` does not match the authorization request, if `client_id` differs
from the one the code was issued to, or if PKCE verification fails.

Both `client_secret_post` and `client_secret_basic` are accepted. Client secrets are
**not validated by value** - MLEAProxy has no client registry. See
[Security Best Practices](#security-best-practices).

### The issued token

```json
{
  "iss": "mleaproxy-oauth-server",
  "sub": "martin",
  "aud": "marklogic-qconsole",
  "iat": 1789741594,
  "exp": 1789745194,
  "jti": "32bb1056-982f-4315-bbe6-cbd34d359b2a",
  "client_id": "marklogic-qconsole",
  "grant_type": "authorization_code",
  "username": "martin",
  "roles": ["marklogic-admin"],
  "roles_string": "marklogic-admin"
}
```

`aud` is emitted as a **single string**, not an array. RFC 7519 §4.1.3 permits either, but
MarkLogic 12.1 fails with `XDMP-INTERNAL: Internal error: std::bad_cast` on an array.
`roles` remains an array, with `roles_string` alongside for consumers wanting a flat value.

---

## HTTPS and the CA Certificate

The Authorization Code flow redirects a browser to the login page, and MarkLogic requires
HTTPS for the token and JWKS endpoints. MLEAProxy therefore runs an HTTPS listener
**in addition to** the plain HTTP listener, so existing Resource Server setups are
unaffected.

```properties
mleaproxy.https.enabled=true
mleaproxy.https.port=8443
mleaproxy.https.address=0.0.0.0
mleaproxy.https.subject-alt-names=marklogic.example.com,192.168.1.50

mleaproxy.https.certificate=./certificates/tls-certificate.pem
mleaproxy.https.private-key=./certificates/tls-privkey.pem
mleaproxy.https.ca-certificate=./certificates/ca-certificate.pem
mleaproxy.https.ca-private-key=./certificates/ca-privkey.pem
```

Set `mleaproxy.https.enabled=false`, or the port to `0`, to disable it. A TLS configuration
failure is logged but **not fatal**: HTTP and the LDAP, SAML and Kerberos protocols
continue to work.

### Generated certificates

If any of the four files is missing, MLEAProxy generates a **private CA** plus a server
certificate signed by it. The two-tier arrangement matters: a trust store such as
MarkLogic's Certificate Authorities needs a `CA:TRUE` anchor, and a self-signed leaf will
not do. Importing the CA once also means the server certificate can be reissued later -
for a new hostname, say - without re-importing anything.

`subjectAltName` always covers the detected hostname, `localhost`, `127.0.0.1` and `::1`,
plus anything in `subject-alt-names`. Private keys are written `0600`.

> The SAML signing certificate at `static/certificates/certificate.pem` is **not** usable
> for TLS: it has no `subjectAltName` and no `serverAuth` extended key usage.

### Downloading the CA

Any client that must trust MLEAProxy over HTTPS needs the CA:

```bash
# Over plain HTTP, avoiding the need to trust the CA in order to fetch it
curl -O http://mleaproxy.example.com:9080/tls/ca
```

The status page at `/status` shows the CA's subject, SHA-256 fingerprint, expiry, path and
full PEM with a copy button. Only the CA is ever served; no private key is exposed.

---

## Configuration Reference

Create `oauth.properties` in your working directory to customize OAuth behavior.

### Core Properties

| Property | Description | Default |
| ---------- | ------------- | --------- |
| `oauth.token.expiration.seconds` | Access token validity in seconds | `3600` |
| `oauth.refresh.token.expiry.seconds` | Refresh token validity in seconds | `2592000` (30 days) |
| `oauth.signing.key.path` | Path to RSA private key (PEM format) | Built-in key |
| `oauth.server.base.url` | Base URL for discovery endpoints | `http://localhost:8080` |
| `oauth.jwt.issuer` | JWT issuer claim value | `mleaproxy-oauth-server` |
| `oauth.default.roles` | Default roles when user not found | `user` |
| `oauth.refresh.token.enabled` | Enable refresh token generation | `true` |
| `oauth.refresh.token.cleanup.interval.seconds` | Cleanup interval for expired tokens | `3600` |

### Example Configuration

```properties
# oauth.properties

# Token validity (15 minutes for production)
oauth.token.expiration.seconds=900

# Refresh token validity (7 days)
oauth.refresh.token.expiry.seconds=604800

# Custom RSA private key (optional - uses built-in key if not specified)
#oauth.signing.key.path=/etc/mleaproxy/keys/privkey.pem

# Base URL (must be reachable by MarkLogic for JWKS validation)
oauth.server.base.url=http://mleaproxy.example.com:8080

# JWT issuer claim
oauth.jwt.issuer=mleaproxy-oauth-server

# Default roles for users not in users.json
oauth.default.roles=user

# Enable refresh tokens
oauth.refresh.token.enabled=true
```

### Command-Line Overrides

Override any property at startup:

```bash
java -jar mlesproxy-2.0.4.jar \
  --oauth.token.expiration.seconds=7200 \
  --oauth.jwt.issuer=my-custom-issuer \
  --oauth.default.roles=guest,readonly
```

---

## Token Generation Examples

### Password Grant (User Authentication)

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=password" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"
```

### Password Grant with Custom Roles

Override the user's default roles:

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=password" \
  -d "client_id=marklogic" \
  -d "client_secret=secret" \
  -d "roles=admin,developer,analyst"
```

### Client Credentials Grant (Service-to-Service)

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=client_credentials" \
  -d "client_id=service-account" \
  -d "client_secret=service-secret"
```

### Using HTTP Basic Authentication

```bash
curl -X POST http://localhost:8080/oauth/token \
  -u "marklogic:secret" \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=password"
```

### Refresh Token Flow

```bash
# Initial token request returns refresh_token when enabled
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=password" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"

# Use refresh token to get new access token
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=refresh_token" \
  -d "refresh_token=<your-refresh-token>" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"
```

### Request Parameters

| Parameter | Required | Description | Example |
| ----------- | ---------- | ------------- | --------- |
| `grant_type` | Yes | OAuth grant type | `password`, `client_credentials`, `refresh_token` |
| `username` | Yes* | Username for authentication | `admin` |
| `password` | Yes* | User password | `password` |
| `client_id` | Yes | Client identifier | `marklogic` |
| `client_secret` | Yes | Client secret | `secret` |
| `roles` | No | Comma-separated roles (override) | `admin,user` |
| `scope` | No | OAuth scopes | `read write` |
| `refresh_token` | Yes** | Refresh token for renewal | `<token>` |

*Required for `password` grant type  
**Required for `refresh_token` grant type

---

## JWKS and Discovery Endpoints

### JWKS Endpoint

Retrieve public keys for JWT signature verification:

```bash
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
      "alg": "RS256",
      "n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
      "e": "AQAB"
    }
  ]
}
```

### Discovery Endpoint

OAuth 2.0 authorization server metadata:

```bash
curl http://localhost:8080/oauth/.well-known/config
```

**Response:**

```json
{
  "issuer": "mleaproxy-oauth-server",
  "token_endpoint": "http://localhost:8080/oauth/token",
  "jwks_uri": "http://localhost:8080/oauth/jwks",
  "grant_types_supported": ["password", "client_credentials", "refresh_token"],
  "response_types_supported": ["token"],
  "token_endpoint_auth_methods_supported": ["client_secret_post", "client_secret_basic"],
  "scopes_supported": ["openid", "profile", "email"]
}
```

---

## Role Resolution

MLEAProxy uses a 3-tier priority system for role assignment:

### Priority Order

1. **Request Parameter Roles** (Highest Priority)  
   Roles specified in the `roles` parameter override all other sources.

2. **User Repository Roles**  
   Roles defined in `users.json` for the authenticated user.

3. **Default Configuration Roles** (Lowest Priority)  
   Roles from `oauth.default.roles` when user is not found.

### User Repository Format

Users are defined in `users.json`:

```json
{
  "users": [
    {
      "username": "admin",
      "password": "password",
      "dn": "cn=admin",
      "roles": ["admin"]
    },
    {
      "username": "user1",
      "password": "password",
      "dn": "cn=user1",
      "roles": ["appreader", "appwriter"]
    }
  ]
}
```

### Custom User Repository Location

```bash
java -jar mlesproxy-2.0.4.jar --users=/path/to/custom/users.json
```

---

## Client Integration

### JavaScript/Node.js

```javascript
const jose = require('jose');
const axios = require('axios');

// Create JWKS client
const JWKS = jose.createRemoteJWKSet(
  new URL('http://localhost:8080/oauth/jwks')
);

// Verify token
async function verifyToken(token) {
  const { payload } = await jose.jwtVerify(token, JWKS, {
    issuer: 'mleaproxy-oauth-server'
  });
  console.log('User:', payload.sub);
  console.log('Roles:', payload.roles);
  return payload;
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
```

### Python

```python
from jose import jwt, jwk
import requests

def get_jwks():
    response = requests.get('http://localhost:8080/oauth/jwks')
    return response.json()

def verify_token(token):
    jwks = get_jwks()
    public_key = jwk.construct(jwks['keys'][0])
    claims = jwt.decode(
        token, 
        public_key, 
        algorithms=['RS256'],
        issuer='mleaproxy-oauth-server'
    )
    return claims

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
```

### Java/Spring Security

```java
@Configuration
@EnableWebSecurity
public class ResourceServerConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/admin/**").hasAuthority("ROLE_admin")
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

---

## Security Best Practices

### Production Configuration

```properties
# Use short token expiry (15 minutes)
oauth.token.expiration.seconds=900

# Use custom RSA key (generate with: openssl genrsa -out privkey.pem 4096)
oauth.signing.key.path=/etc/mleaproxy/keys/privkey.pem

# Use externally accessible URL
oauth.server.base.url=https://auth.example.com
```

### What MLEAProxy does not enforce

MLEAProxy is a **test and teaching tool**, and is permissive by design so that any client
can point at it without reconfiguration. It is not an Authorization Server implementation
to build on:

| Not enforced | Consequence |
| -------------- | ------------- |
| **Client secrets** | `client_id` and `client_secret` must be *present* but are never compared against anything. Any value is accepted. |
| **Client registration** | There is no client registry, so any `client_id` works. |
| **Redirect URI whitelisting** | The `redirect_uri` supplied by the client is used as given. A real Authorization Server validates it against a registered list; without that it is an open redirect. |
| **User credentials** | The login page does not verify passwords. Whoever reaches it can issue a token for any username and roles. |

Treat an MLEAProxy instance as granting anyone who can reach it the ability to mint a token
for any identity. Run it on a trusted network, never as production authentication.

### Production Checklist

- [ ] Use HTTPS for all OAuth endpoints
- [ ] Use custom RSA keys (4096-bit recommended)
- [ ] Set short token expiry times (15-30 minutes)
- [ ] Rotate RSA keys regularly (every 90 days)
- [ ] Store client secrets securely (environment variables)
- [ ] Implement rate limiting on token endpoint
- [ ] Monitor for unusual token generation patterns
- [ ] Validate all token claims on resource servers

### Token Validation Checklist

Resource servers should validate:

- Signature (using JWKS public key)
- Issuer (`iss` claim)
- Expiration (`exp` claim)
- Not before (`nbf` claim)
- Audience (`aud` claim) if applicable

---

## Troubleshooting

### Common Errors

| Error | Cause | Solution |
| ------- | ------- | ---------- |
| `invalid_grant` | Invalid username/password | Verify credentials in users.json |
| `invalid_client` | Invalid client_id/client_secret | Check client credentials |
| `invalid_request` | Missing required parameter | Include all required parameters |
| `unsupported_grant_type` | Grant type not enabled | Use `password` or `client_credentials` |

### Error Response Format

```json
{
  "error": "invalid_grant",
  "error_description": "Invalid username or password"
}
```

### Debugging Tips

1. **Check startup logs** for OAuth endpoint registration:

   ```
   Token Endpoint:           http://localhost:8080/oauth/token
   JWKS Endpoint:            http://localhost:8080/oauth/jwks
   ```

2. **Verify JWKS is accessible**:

   ```bash
   curl -v http://localhost:8080/oauth/jwks
   ```

3. **Decode token to inspect claims**:

   ```bash
   echo "$TOKEN" | cut -d'.' -f2 | base64 -d 2>/dev/null | jq .
   ```

4. **Enable debug logging**:

   ```properties
   logging.level.com.marklogic.handlers.undertow.OAuthTokenHandler=DEBUG
   ```

### HTTP Status Codes

| Code | Meaning | Scenario |
| ------ | --------- | ---------- |
| 200 | OK | Token generated successfully |
| 400 | Bad Request | Missing required parameters |
| 401 | Unauthorized | Invalid credentials |
| 405 | Method Not Allowed | Wrong HTTP method (use POST for /oauth/token) |
| 500 | Internal Server Error | Server configuration error |

---

## MarkLogic Integration

MarkLogic can use MLEAProxy in two distinct ways, and which one applies depends on the
MarkLogic version:

| MarkLogic acts as | Version | Flow | Covered in |
| ------------------- | --------- | ------ | ------------ |
| **Resource Server** | Any | A JWT obtained elsewhere is presented as a `Bearer` token; MarkLogic verifies it via JWKS | This section |
| **OAuth client** | **12.1+** | A browser with no JWT is redirected to MLEAProxy to sign in, for Admin UI and Query Console | **[MarkLogic Authorization Code Flow Guide](./MARKLOGIC_AUTHORIZATION_CODE_GUIDE.md)** |

> The Authorization Code flow is **only available from MarkLogic 12.1**. On earlier
> releases, only the Resource Server configuration below applies.
>
> That guide also carries a troubleshooting matrix for MarkLogic's OAuth errors, several of
> which report something other than the actual fault - for example
> `SVC-SOCCONN: Certificate verify failed` most often means an HTTPS URI pointing at a
> plain-HTTP port rather than a missing CA.

### Configure MarkLogic External Security

1. **Ensure JWKS URL is accessible from MarkLogic**

   ```bash
   # Test from MarkLogic server
   curl http://mleaproxy-host:8080/oauth/jwks
   ```

2. **Create External Security Configuration**

   Use MarkLogic Admin UI or REST API to create an external security profile:
   - Authentication: External
   - Authorization: External
   - JWKS URI: `http://mleaproxy-host:8080/oauth/jwks`

3. **Configure App Server**

   Set the App Server to use the external security profile for JWT authentication.

### Using Tokens with MarkLogic

```bash
# Generate token
TOKEN=$(curl -s -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=password" \
  -d "client_id=marklogic" \
  -d "client_secret=secret" | jq -r '.access_token')

# Use token with MarkLogic REST API
curl -H "Authorization: Bearer $TOKEN" \
  http://marklogic-host:8000/v1/documents?uri=/test.json
```

### JWKS Key Management Scripts

MLEAProxy includes scripts for managing JWKS keys in MarkLogic:

```bash
# Extract and upload keys to MarkLogic
./scripts/extract-jwks-keys.sh http://localhost:8080/oauth/jwks \
  --upload-to-marklogic \
  --marklogic-host ml.example.com \
  --marklogic-user admin \
  --marklogic-pass admin \
  --external-security OAuth2-Profile

# Clean up obsolete keys
./scripts/cleanup-obsolete-jwks-keys.sh http://localhost:8080/oauth/jwks \
  --marklogic-host ml.example.com \
  --external-security OAuth2-Profile
```

See `docs/developer/README-JWKS-Integration.md` for complete documentation.

---

## Standards References

- [RFC 6749](https://tools.ietf.org/html/rfc6749) - OAuth 2.0 Authorization Framework
- [RFC 7517](https://tools.ietf.org/html/rfc7517) - JSON Web Key (JWK)
- [RFC 7519](https://tools.ietf.org/html/rfc7519) - JSON Web Token (JWT)
- [RFC 8414](https://tools.ietf.org/html/rfc8414) - OAuth 2.0 Authorization Server Metadata

---

## Related Documentation

- **[LDAP_GUIDE.md](./LDAP_GUIDE.md)** - LDAP functionality
- **[SAML_GUIDE.md](./SAML_GUIDE.md)** - SAML 2.0 functionality
- **[KERBEROS_GUIDE.md](./KERBEROS_GUIDE.md)** - Kerberos authentication
- **[CONFIGURATION_GUIDE.md](./CONFIGURATION_GUIDE.md)** - Full configuration reference
