# MLEAProxy Quick Start & Verification Guide

This guide provides working examples for all protocols with configuration, expected startup output, and verification commands.

## Prerequisites

```bash
# Build the application
./build.sh clean package

# Verify the JAR was created
ls -la target/mlesproxy-2.0.2.jar

# Set JAR variable for examples
JAR=target/mlesproxy-2.0.2.jar
```

---

## 1. LDAP Protocol

### 1.1 Default Configuration

MLEAProxy includes a working default configuration in `src/main/resources/mleaproxy.properties`. The defaults start:

- **In-memory LDAP directory server** on port 60389
- **LDAP proxy listener** on port 10389 (with JSON user authentication)
- **Additional LDAP listener** on port 20389 (JSON user store)

### 1.2 Start the Server

```bash
java -jar target/mlesproxy-2.0.2.jar
```

### 1.3 Expected Startup Output

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v3.3.5)

... [startup logs] ...

INFO  c.m.service.LDAPServerService - In-memory LDAP server 'marklogic' started on 0.0.0.0:60389 with base DN 'dc=MarkLogic,dc=Local'
INFO  c.m.service.LDAPListenerService - LDAP listener 'ldapjson' started on 0.0.0.0:20389 (Simple LDAP Server using JSON user store)
INFO  c.m.service.LDAPListenerService - LDAP listener 'proxy' started on 0.0.0.0:10389 (LDAP Authentication Proxy)
INFO  c.m.service.StartupDisplayService - ================================================================================
INFO  c.m.service.StartupDisplayService - MLEAProxy Server Started
INFO  c.m.service.StartupDisplayService - ================================================================================
INFO  c.m.service.StartupDisplayService - Server Port: 8080
INFO  c.m.service.StartupDisplayService - Base URL: http://localhost:8080
INFO  c.m.service.StartupDisplayService - ================================================================================
INFO  c.m.service.StartupDisplayService - 
INFO  c.m.service.StartupDisplayService - OAuth 2.0 Endpoints:
INFO  c.m.service.StartupDisplayService - --------------------------------------------------------------------------------
INFO  c.m.service.StartupDisplayService - Token Endpoint:           http://localhost:8080/oauth/token
INFO  c.m.service.StartupDisplayService - JWKS Endpoint:            http://localhost:8080/oauth/jwks
INFO  c.m.service.StartupDisplayService - OpenID Configuration:     http://localhost:8080/oauth/.well-known/config
INFO  c.m.service.StartupDisplayService - ================================================================================
INFO  c.m.service.StartupDisplayService - 
INFO  c.m.service.StartupDisplayService - Configured Users (from users.json):
INFO  c.m.service.StartupDisplayService - --------------------------------------------------------------------------------
INFO  c.m.service.StartupDisplayService - Username             Password             Roles
INFO  c.m.service.StartupDisplayService - --------------------------------------------------------------------------------
INFO  c.m.service.StartupDisplayService - admin                password             admin
INFO  c.m.service.StartupDisplayService - user1                password             appreader, appwriter, appadmin
INFO  c.m.service.StartupDisplayService - user2                password             appreader, appwriter
INFO  c.m.service.StartupDisplayService - user3                password             appreader
INFO  c.m.service.StartupDisplayService - manager              password             (none)
INFO  c.m.service.StartupDisplayService - --------------------------------------------------------------------------------
INFO  c.m.handlers.Applicationlistener - MLEAProxy initialization complete
```

### 1.4 Verification Commands

#### Test 1: Search the in-memory directory server directly

```bash
ldapsearch -H ldap://localhost:60389 \
  -D "cn=Directory Manager" \
  -w password \
  -b "dc=MarkLogic,dc=Local" \
  "(objectClass=organization)" \
  -LLL
```

**Expected output:**
```
dn: dc=MarkLogic,dc=Local
objectClass: organization
objectClass: dcObject
dc: MarkLogic
o: MarkLogic
```

#### Test 2: List users in the directory

```bash
ldapsearch -H ldap://localhost:60389 \
  -D "cn=Directory Manager" \
  -w password \
  -b "ou=Users,dc=MarkLogic,dc=Local" \
  "(objectClass=inetOrgPerson)" \
  uid cn \
  -LLL
```

**Expected output:**
```
dn: uid=mluser1,ou=Users,dc=MarkLogic,dc=Local
uid: mluser1
cn: mluser1

dn: uid=mluser2,ou=Users,dc=MarkLogic,dc=Local
uid: mluser2
cn: mluser2
...
```

#### Test 3: Authenticate via JSON user store (proxy listener)

```bash
ldapsearch -H ldap://localhost:10389 \
  -D "cn=admin" \
  -w password \
  -b "dc=MarkLogic,dc=Local" \
  "(objectClass=*)" \
  -LLL
```

**Expected output:** Returns user info for authenticated user (admin).

---

## 2. OAuth 2.0 Protocol

OAuth 2.0 endpoints are enabled by default on the HTTP server (port 8080).

### 2.1 Default Configuration

OAuth works out of the box with these defaults:

```properties
# Token validity (seconds) - default: 3600
oauth.token.expiration.seconds=3600

# JWT issuer - default: mleaproxy-oauth-server
oauth.jwt.issuer=mleaproxy-oauth-server
```

### 2.2 Verification Commands

#### Get Access Token (Password Grant)

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=password" \
  -d "client_id=test-client" \
  -d "client_secret=secret"
```

**Expected output:**
```json
{
  "access_token": "eyJraWQiOiI...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

#### Get Access Token with Custom Roles

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=password" \
  -d "client_id=test-client" \
  -d "client_secret=secret" \
  -d "roles=admin,custom-role"
```

#### Get JWKS (JSON Web Key Set)

```bash
curl -s http://localhost:8080/oauth/jwks | jq .
```

**Expected output:**
```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "kid": "...",
      "alg": "RS256",
      "n": "ALRwjq4lwRs2D-2kQU...",
      "e": "AQAB"
    }
  ]
}
```

#### Get OpenID Connect Discovery Document

```bash
curl -s http://localhost:8080/oauth/.well-known/config | jq .
```

**Expected output:**
```json
{
  "issuer": "mleaproxy-oauth-server",
  "token_endpoint": "http://localhost:8080/oauth/token",
  "jwks_uri": "http://localhost:8080/oauth/jwks",
  "grant_types_supported": ["password", "client_credentials"],
  "response_types_supported": ["token"],
  "token_endpoint_auth_methods_supported": ["client_secret_post"],
  "id_token_signing_alg_values_supported": ["RS256"],
  "claims_supported": ["iss", "sub", "aud", "exp", "iat", "jti", "client_id", "grant_type", "username", "scope", "roles", "roles_string"],
  "scopes_supported": ["openid", "profile", "email"]
}
```

#### Decode JWT Token Payload

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=password" \
  -d "client_id=test-client" \
  -d "client_secret=secret" | jq -r '.access_token')

echo $TOKEN | cut -d'.' -f2 | base64 -d 2>/dev/null | jq .
```

**Expected payload:**
```json
{
  "iss": "mleaproxy-oauth-server",
  "sub": "admin",
  "aud": ["test-client"],
  "iat": 1699996399,
  "exp": 1699999999,
  "jti": "...",
  "client_id": "test-client",
  "grant_type": "password",
  "username": "admin",
  "roles": ["admin"],
  "roles_string": "admin"
}
```

---

## 3. SAML 2.0 Protocol

### 3.1 Configuration

SAML is enabled by default:

```properties
# SAML debug logging
mleaproxy.saml-debug=false

# SAML assertion validity (seconds)
mleaproxy.saml-response-validity=300

# Default roles for users not in JSON repository
saml.default.roles=user
```

### 3.2 Verification Commands

#### Get SAML IdP Metadata

```bash
curl -s http://localhost:8080/saml/idp-metadata
```

**Expected output (formatted):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata"
                     entityID="http://localhost:8080/saml/idp">
  <md:IDPSSODescriptor WantAuthnRequestsSigned="false"
                       protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
    <md:KeyDescriptor use="signing">
      <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
        <ds:X509Data>
          <ds:X509Certificate>MIIDyz...</ds:X509Certificate>
        </ds:X509Data>
      </ds:KeyInfo>
    </md:KeyDescriptor>
    <md:NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified</md:NameIDFormat>
    <md:SingleSignOnService
        Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"
        Location="http://localhost:8080/saml/auth"/>
  </md:IDPSSODescriptor>
</md:EntityDescriptor>
```

#### Get SAML Signing Certificate

```bash
curl -s http://localhost:8080/saml/ca
```

**Expected output:**
```
-----BEGIN CERTIFICATE-----
MIIDyzCCArOgAwIBAgIUVfpV56K9w6BsaPh9Wd6nRzF4zB0wDQYJKoZIhvcNAQEL
BQAwdTELMAkGA1UEBhMCVVMxDjAMBgNVBAgMBVN0YXRlMQ0wCwYDVQQHDARDaXR5
...
-----END CERTIFICATE-----
```

#### Verify Certificate Details

```bash
curl -s http://localhost:8080/saml/ca | openssl x509 -text -noout | head -20
```

**Expected output:**
```
Certificate:
    Data:
        Version: 3 (0x2)
        Serial Number: ...
        Signature Algorithm: sha256WithRSAEncryption
        Issuer: C = US, ST = State, L = City, O = Organization, OU = Unit, CN = SAML Signing Certificate
        Validity
            Not Before: Oct  3 12:59:01 2025 GMT
            Not After : Oct  1 12:59:01 2035 GMT
        Subject: C = US, ST = State, L = City, O = Organization, OU = Unit, CN = SAML Signing Certificate
```

---

## 4. Kerberos Protocol

### 4.1 Configuration

Kerberos is disabled by default. Enable it in your properties file:

```properties
# Enable embedded Kerberos KDC
mleaproxy.kerberos.enabled=true
mleaproxy.kerberos.realm=MARKLOGIC.LOCAL
mleaproxy.kerberos.kdc-host=localhost
mleaproxy.kerberos.kdc-port=60088
mleaproxy.kerberos.admin-port=60749
mleaproxy.kerberos.work-dir=./kerberos
mleaproxy.kerberos.debug=false

# Service principals
mleaproxy.kerberos.service-principals=HTTP/localhost,ldap/localhost

# Import principals from LDAP
mleaproxy.kerberos.import-principals-from-ldap=true
mleaproxy.kerberos.ldap-base-dn=dc=MarkLogic,dc=Local
```

### 4.2 Start with Kerberos Enabled

```bash
java -jar target/mlesproxy-2.0.2.jar \
  --mleaproxy.kerberos.enabled=true
```

### 4.3 Expected Kerberos Startup Output

```
INFO  c.m.handlers.KerberosKDCServer - Kerberos KDC enabled, starting embedded KDC
INFO  c.m.handlers.KerberosKDCServer - KDC Configuration:
INFO  c.m.handlers.KerberosKDCServer -   Realm: MARKLOGIC.LOCAL
INFO  c.m.handlers.KerberosKDCServer -   KDC Host: localhost
INFO  c.m.handlers.KerberosKDCServer -   KDC Port: 60088
INFO  c.m.handlers.KerberosKDCServer - Created service principal: HTTP/localhost@MARKLOGIC.LOCAL
INFO  c.m.handlers.KerberosKDCServer - Kerberos KDC started successfully
```

### 4.4 Kerberos Client Setup

```bash
# Create krb5.conf for the embedded KDC
cat > /tmp/krb5.conf << 'EOF'
[libdefaults]
    default_realm = MARKLOGIC.LOCAL
    dns_lookup_realm = false
    dns_lookup_kdc = false

[realms]
    MARKLOGIC.LOCAL = {
        kdc = localhost:60088
        admin_server = localhost:60749
    }

[domain_realm]
    .marklogic.local = MARKLOGIC.LOCAL
    marklogic.local = MARKLOGIC.LOCAL
EOF

export KRB5_CONFIG=/tmp/krb5.conf
```

### 4.5 Verification Commands

```bash
# Authenticate (password: password)
kinit admin@MARKLOGIC.LOCAL

# Verify ticket
klist

# Test SPNEGO authentication (requires curl with GSSAPI support)
curl --negotiate -u : http://localhost:8080/kerberos/auth

# Clean up
kdestroy
```

---

## 5. Quick Verification Script

Run all basic verification tests:

```bash
#!/bin/bash
# Save as verify-mleaproxy.sh

echo "=== Starting MLEAProxy ==="
java -jar target/mlesproxy-2.0.2.jar &
PID=$!
sleep 6

echo ""
echo "=== Test 1: LDAP Directory Server ==="
ldapsearch -H ldap://localhost:60389 \
  -D "cn=Directory Manager" \
  -w password \
  -b "dc=MarkLogic,dc=Local" \
  "(objectClass=organization)" dc -LLL

echo ""
echo "=== Test 2: OAuth JWKS ==="
curl -s http://localhost:8080/oauth/jwks | jq '.keys | length'

echo ""
echo "=== Test 3: OAuth Token ==="
curl -s -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=password" \
  -d "client_id=test" \
  -d "client_secret=secret" | jq '.access_token | length'

echo ""
echo "=== Test 4: SAML Metadata ==="
curl -s http://localhost:8080/saml/idp-metadata | grep -o 'entityID="[^"]*"'

echo ""
echo "=== Test 5: SAML Certificate ==="
curl -s http://localhost:8080/saml/ca | head -1

echo ""
echo "=== Stopping MLEAProxy ==="
kill $PID 2>/dev/null

echo ""
echo "=== All tests complete ==="
```

---

## 6. Command-Line Overrides

Override any property at runtime:

```bash
# Change HTTP port
java -jar target/mlesproxy-2.0.2.jar --server.port=9090

# Change LDAP listener port
java -jar target/mlesproxy-2.0.2.jar \
  --mleaproxy.ldap-listeners.proxy.port=20389

# Enable debug logging
java -jar target/mlesproxy-2.0.2.jar \
  --mleaproxy.ldap-debug=true \
  --mleaproxy.saml-debug=true

# Enable Kerberos
java -jar target/mlesproxy-2.0.2.jar \
  --mleaproxy.kerberos.enabled=true \
  --mleaproxy.kerberos.realm=MYCOMPANY.COM

# Multiple overrides
java -jar target/mlesproxy-2.0.2.jar \
  --server.port=9090 \
  --mleaproxy.ldap-listeners.proxy.port=20389 \
  --mleaproxy.kerberos.enabled=true
```

---

## 7. Troubleshooting

### Port Already in Use

```bash
# Find process using port 8080
lsof -i :8080

# Kill process
kill -9 <PID>
```

### LDAP Connection Refused

```bash
# Check LDAP listener is running
netstat -an | grep -E "10389|60389"

# Test with verbose output
ldapsearch -H ldap://localhost:60389 -x -b "" -s base -v
```

### OAuth Token Invalid

```bash
# Check JWKS endpoint
curl -v http://localhost:8080/oauth/jwks

# Decode and verify token at jwt.io
```

### View Application Logs

```bash
# Run with debug logging
java -jar target/mlesproxy-2.0.2.jar \
  --logging.level.com.marklogic=DEBUG
```

---

## Endpoint Reference

| Protocol | Endpoint | Method | Description |
|----------|----------|--------|-------------|
| OAuth | `/oauth/token` | POST | Get access token |
| OAuth | `/oauth/jwks` | GET | Get JSON Web Key Set |
| OAuth | `/oauth/.well-known/config` | GET | OpenID Connect discovery |
| SAML | `/saml/idp-metadata` | GET | IdP metadata XML |
| SAML | `/saml/ca` | GET | Signing certificate (PEM) |
| SAML | `/saml/auth` | POST | Generate SAML assertion |
| Kerberos | `/kerberos/auth` | GET | SPNEGO authentication |
| Kerberos | `/kerberos/token` | GET | Get service token |
| Utility | `/b64encode` | GET/POST | Base64 encode |
| Utility | `/b64decode` | GET/POST | Base64 decode |

---

## Default Ports

| Service | Port | Description |
|---------|------|-------------|
| HTTP | 8080 | OAuth, SAML, Kerberos endpoints |
| LDAP Proxy | 10389 | LDAP authentication proxy |
| LDAP JSON | 20389 | LDAP server with JSON user store |
| LDAP Directory | 60389 | In-memory LDAP directory |
| Kerberos KDC | 60088 | Kerberos Key Distribution Center |
| Kerberos Admin | 60749 | Kerberos admin server |
