# MLEAProxy SAML 2.0 Guide

Complete guide for SAML 2.0 Identity Provider functionality in MLEAProxy.

---

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration Reference](#configuration-reference)
- [SAML Endpoints](#saml-endpoints)
- [Role Resolution](#role-resolution)
- [Examples](#examples)
- [MarkLogic Integration](#marklogic-integration)
- [Troubleshooting](#troubleshooting)

---

## Overview

MLEAProxy provides complete SAML 2.0 Identity Provider (IdP) functionality for Single Sign-On (SSO) authentication.

### Key Features

- **IdP Functionality**: Full SAML 2.0 Identity Provider implementation
- **IdP Metadata**: Automated metadata endpoint for SP configuration
- **Digital Signatures**: XML signature support for assertions (RSA-SHA256)
- **Attribute Statements**: Flexible role and attribute mapping
- **HTTP-Redirect Binding**: Standard SAML protocol binding support
- **3-Tier Role Resolution**: Flexible role assignment with fallback

### SAML Endpoints Summary

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/saml/auth` | GET/POST | SAML authentication (SSO) |
| `/saml/idp-metadata` | GET | IdP metadata XML |
| `/saml/ca` | GET | CA certificates (PEM format) |

---

## Prerequisites

- Java 21 or later
- Network access to port 8080 (configurable)
- MarkLogic Server (for SP integration)

---

## Installation

### Download and Extract

```bash
# Download the latest release
wget https://github.com/marklogic/mleaproxy/releases/download/v2.0.2/mlesproxy-2.0.2.jar

# Or build from source
git clone https://github.com/marklogic/mleaproxy.git
cd mleaproxy
./build.sh clean package
```

### Verify Installation

```bash
# Check Java version
java -version

# Run MLEAProxy
java -jar mlesproxy-2.0.2.jar

# Verify SAML endpoints are available
curl -s http://localhost:8080/saml/idp-metadata | head -5
```

---

## Quick Start

### Step 1: Start MLEAProxy

```bash
java -jar mlesproxy-2.0.2.jar
```

### Step 2: Get IdP Metadata

```bash
# Fetch and display formatted IdP metadata
curl -s http://localhost:8080/saml/idp-metadata | xmllint --format -
```

**Expected output:**

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
    <md:SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"
                            Location="http://localhost:8080/saml/auth"/>
  </md:IDPSSODescriptor>
</md:EntityDescriptor>
```

### Step 3: Save Metadata for MarkLogic

```bash
# Save metadata to file
curl -s http://localhost:8080/saml/idp-metadata > mleaproxy-idp-metadata.xml

# Extract certificate for MarkLogic configuration
curl -s http://localhost:8080/saml/idp-metadata | \
  xmllint --xpath "//*[local-name()='X509Certificate']/text()" - 2>/dev/null | \
  fold -w 64 | \
  { echo "-----BEGIN CERTIFICATE-----"; cat; echo "-----END CERTIFICATE-----"; } > idp-cert.pem

# Verify certificate
openssl x509 -in idp-cert.pem -text -noout | head -15
```

### Step 4: Test SAML Authentication

```bash
# Create a minimal SAML AuthnRequest
AUTHN_REQUEST='<?xml version="1.0"?>
<samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                    ID="_test123"
                    Version="2.0"
                    IssueInstant="2025-01-01T00:00:00Z"
                    AssertionConsumerServiceURL="http://localhost:8000/saml/acs">
  <saml:Issuer xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">marklogic-sp</saml:Issuer>
</samlp:AuthnRequest>'

# Base64 encode and URL encode the request
SAML_REQUEST=$(echo -n "$AUTHN_REQUEST" | gzip -c | base64 | tr -d '\n' | python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.stdin.read()))")

# Open in browser (will show login form)
echo "Open: http://localhost:8080/saml/auth?SAMLRequest=$SAML_REQUEST"
```

---

## Configuration Reference

### Property File Location

Create `saml.properties` or add to `mleaproxy.properties`:

```bash
java -jar mlesproxy-2.0.2.jar --spring.config.additional-location=./saml.properties
```

### SAML Properties

| Property | Default | Description |
|----------|---------|-------------|
| `mleaproxy.saml-debug` | `false` | Enable debug logging for SAML operations |
| `mleaproxy.saml-ca-path` | (bundled) | Path to CA certificate file (PEM format) |
| `mleaproxy.saml-key-path` | (bundled) | Path to private key file (PEM format) |
| `mleaproxy.saml-response-validity` | `300` | SAML assertion validity in seconds |
| `mleaproxy.saml-default-roles` | `user` | Default roles when user not in repository |

### Additional Properties

| Property | Default | Description |
|----------|---------|-------------|
| `saml.idp.entity.id` | `http://localhost:8080/saml/idp` | IdP Entity ID |
| `saml.idp.sso.url` | `http://localhost:8080/saml/auth` | SSO endpoint URL |
| `saml.certificate.path` | (bundled) | Certificate path for metadata |
| `saml.signing.key.path` | (bundled) | Signing key path |
| `saml.default.roles` | `user` | Fallback roles for unknown users |

### Complete Configuration Example

```properties
# ================================================================
# SAML 2.0 Configuration
# ================================================================

# --- Debug Settings ---
mleaproxy.saml-debug=true

# --- Assertion Settings ---
# Validity period in seconds (5 minutes)
mleaproxy.saml-response-validity=300

# --- Certificate Settings (optional - uses bundled if not set) ---
#mleaproxy.saml-ca-path=/path/to/certificate.pem
#mleaproxy.saml-key-path=/path/to/private-key.pem

# --- Role Configuration ---
mleaproxy.saml-default-roles=user

# --- User Repository ---
# Enable JSON-based user lookup
users.json.path=./users.json

# --- Server Settings ---
server.port=8080
```

### users.json Format

```json
{
  "users": [
    {
      "username": "admin",
      "password": "password",
      "dn": "cn=admin",
      "roles": ["admin", "marklogic-admin"]
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

---

## SAML Endpoints

### 1. Authentication Endpoint (`/saml/auth`)

SAML 2.0 Identity Provider authentication endpoint for SSO.

**GET Request** - Display login form:

```bash
curl -G "http://localhost:8080/saml/auth" \
  --data-urlencode "SAMLRequest=<base64-encoded-request>"
```

**POST Request** - Process authentication:

| Parameter | Required | Description |
|-----------|----------|-------------|
| `SAMLRequest` | Yes | Base64-encoded SAML AuthnRequest |
| `RelayState` | No | Application state to preserve |
| `userid` | Yes | Username for authentication |
| `password` | Yes | User password |
| `roles` | No | Comma-separated roles (override) |

**Response**: HTTP 302 redirect with auto-submitting form containing:
- `SAMLResponse` - Base64-encoded signed SAML Response
- `RelayState` - Original relay state (if provided)

### 2. IdP Metadata Endpoint (`/saml/idp-metadata`)

Returns SAML 2.0 Identity Provider metadata for Service Provider configuration.

**Request:**

```bash
# Get formatted metadata
curl -s http://localhost:8080/saml/idp-metadata | xmllint --format -

# Save to file
curl -s http://localhost:8080/saml/idp-metadata > idp-metadata.xml
```

**Response:** EntityDescriptor XML containing:
- Entity ID
- Signing certificate (X.509)
- SingleSignOnService endpoint
- Supported NameID formats

### 3. CA Certificates Endpoint (`/saml/ca`)

Returns the IdP signing certificate in PEM format.

**Request:**

```bash
# Get certificate
curl -s http://localhost:8080/saml/ca

# Save to file
curl -s http://localhost:8080/saml/ca > idp-signing-cert.pem

# Verify certificate
curl -s http://localhost:8080/saml/ca | openssl x509 -text -noout
```

---

## Role Resolution

### 3-Tier Priority System

MLEAProxy uses a sophisticated role resolution system:

```
Priority 1: Request Parameter Roles    <- Highest
    (Explicit 'roles' parameter)
              |
              v (if not provided)
Priority 2: JSON User Repository       <- Medium
    (User's roles from users.json)
              |
              v (if user not found)
Priority 3: Default Configuration      <- Lowest
    (mleaproxy.saml-default-roles)
```

### Priority 1: Request Parameter Roles

Override roles via the `roles` parameter:

```bash
curl -X POST "http://localhost:8080/saml/auth" \
  -d "SAMLRequest=..." \
  -d "userid=admin" \
  -d "password=password" \
  -d "roles=admin,developer"
```

Result: Assertion contains `roles=["admin", "developer"]`

### Priority 2: JSON User Repository

When no `roles` parameter and user exists in `users.json`:

```json
{
  "users": [{
    "username": "admin",
    "password": "password",
    "roles": ["admin", "user"]
  }]
}
```

Result: Assertion contains `roles=["admin", "user"]`

### Priority 3: Default Configuration

When user not found in repository:

```properties
mleaproxy.saml-default-roles=user,guest
```

Result: Assertion contains `roles=["user", "guest"]`

---

## Examples

### Example 1: Get and Inspect IdP Metadata

```bash
# Fetch metadata
curl -s http://localhost:8080/saml/idp-metadata > metadata.xml

# View formatted
xmllint --format metadata.xml

# Extract Entity ID
xmllint --xpath "string(//*[local-name()='EntityDescriptor']/@entityID)" metadata.xml

# Extract SSO URL
xmllint --xpath "string(//*[local-name()='SingleSignOnService']/@Location)" metadata.xml

# Extract certificate
xmllint --xpath "//*[local-name()='X509Certificate']/text()" metadata.xml 2>/dev/null
```

### Example 2: Extract Certificate for MarkLogic

```bash
# One-liner to create PEM certificate
curl -s http://localhost:8080/saml/idp-metadata | \
  xmllint --xpath "//*[local-name()='X509Certificate']/text()" - 2>/dev/null | \
  fold -w 64 | \
  { echo "-----BEGIN CERTIFICATE-----"; cat; echo "-----END CERTIFICATE-----"; } > idp-cert.pem

# Verify certificate details
openssl x509 -in idp-cert.pem -text -noout | grep -E "(Subject|Issuer|Not Before|Not After)"
```

### Example 3: Decode SAML Response

After authentication, decode the SAMLResponse:

```bash
# If you captured a SAMLResponse from browser
SAML_RESPONSE="PD94bWwgdmVyc2lvbj0i..."

# Decode and format
echo "$SAML_RESPONSE" | base64 -d | xmllint --format -

# Extract subject/username
echo "$SAML_RESPONSE" | base64 -d | \
  xmllint --xpath "string(//*[local-name()='NameID'])" -

# Extract roles
echo "$SAML_RESPONSE" | base64 -d | \
  xmllint --xpath "//*[local-name()='Attribute'][@Name='roles']//*[local-name()='AttributeValue']/text()" - 2>/dev/null
```

### Example 4: Test with Custom Certificates

```bash
# Generate self-signed certificate
openssl req -x509 -newkey rsa:2048 \
  -keyout saml-key.pem \
  -out saml-cert.pem \
  -days 365 -nodes \
  -subj "/CN=mleaproxy.example.com/O=Example/C=US"

# Start with custom certificates
java -jar mlesproxy-2.0.2.jar \
  --mleaproxy.saml-ca-path=./saml-cert.pem \
  --mleaproxy.saml-key-path=./saml-key.pem
```

### Example 5: Enable Debug Logging

```bash
# Via command line
java -jar mlesproxy-2.0.2.jar --mleaproxy.saml-debug=true

# Via properties file
cat > saml.properties << 'EOF'
mleaproxy.saml-debug=true
logging.level.com.marklogic.handlers.undertow.SAMLAuthHandler=DEBUG
EOF

java -jar mlesproxy-2.0.2.jar --spring.config.additional-location=./saml.properties
```

---

## MarkLogic Integration

### Configuration Overview

```
User -> MarkLogic (SP) -> MLEAProxy (IdP) -> Authenticate -> SAML Response -> MarkLogic
```

### Step 1: Get IdP Metadata

```bash
curl -s http://localhost:8080/saml/idp-metadata > mleaproxy-idp-metadata.xml
```

### Step 2: Extract Certificate

```bash
# Extract and format certificate
curl -s http://localhost:8080/saml/idp-metadata | \
  xmllint --xpath "//*[local-name()='X509Certificate']/text()" - 2>/dev/null | \
  fold -w 64 | \
  { echo "-----BEGIN CERTIFICATE-----"; cat; echo "-----END CERTIFICATE-----"; } > idp-cert.pem

# Verify certificate
openssl x509 -in idp-cert.pem -noout -dates
```

### Step 3: Create External Security (Admin Console)

Navigate to: **Security > External Security > Create**

| Setting | Value |
|---------|-------|
| external security name | `mleaproxy-saml` |
| authentication | `saml` |
| authorization | `saml` |
| cache timeout | `300` |
| SAML entity ID | `http://localhost:8080/saml/idp` |
| SAML destination | `http://localhost:8080/saml/auth` |
| SAML issuer | `marklogic-server` |
| SAML assertion host | `localhost:8000` |
| SAML IDP certificate | *(paste contents of idp-cert.pem)* |

### Step 4: Create External Security (REST API)

```bash
# Read certificate content
CERT_CONTENT=$(cat idp-cert.pem)

# Create external security configuration
curl -X POST "http://localhost:8002/manage/v2/external-security" \
  -H "Content-Type: application/json" \
  -u admin:admin \
  -d "{
    \"external-security-name\": \"mleaproxy-saml\",
    \"description\": \"SAML authentication via MLEAProxy\",
    \"authentication\": \"saml\",
    \"cache-timeout\": 300,
    \"authorization\": \"saml\",
    \"saml-entity-id\": \"http://localhost:8080/saml/idp\",
    \"saml-destination\": \"http://localhost:8080/saml/auth\",
    \"saml-issuer\": \"marklogic-server\",
    \"saml-assertion-host\": \"localhost:8000\",
    \"saml-idp-certificate\": \"$CERT_CONTENT\"
  }"
```

### Step 5: Configure App Server

```bash
curl -X PUT "http://localhost:8002/manage/v2/servers/App-Services/properties?group-id=Default" \
  -H "Content-Type: application/json" \
  -u admin:admin \
  -d '{
    "authentication": "application-level",
    "internal-security": false,
    "external-security": ["mleaproxy-saml"]
  }'
```

### Step 6: Test Authentication

```bash
# Access MarkLogic - should redirect to MLEAProxy
open http://localhost:8000/

# After login, verify user in MarkLogic
curl -X POST "http://localhost:8000/v1/eval" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --negotiate -u : \
  -d "xquery=xdmp:get-current-user()"
```

---

## Troubleshooting

### Common Issues

#### 1. "Certificate not loaded" Error

**Symptom:** IdP metadata returns error about certificate not configured

**Solution:**
```bash
# Verify bundled certificates exist
java -jar mlesproxy-2.0.2.jar --mleaproxy.saml-debug=true 2>&1 | grep -i certificate

# Or provide custom certificates
java -jar mlesproxy-2.0.2.jar \
  --mleaproxy.saml-ca-path=/path/to/cert.pem \
  --mleaproxy.saml-key-path=/path/to/key.pem
```

#### 2. Invalid Signature in MarkLogic

**Symptom:** "SAML response signature validation failed"

**Solution:**
```bash
# Verify certificate matches
openssl x509 -in idp-cert.pem -noout -fingerprint -sha256

# Re-extract certificate from metadata
curl -s http://localhost:8080/saml/idp-metadata | \
  xmllint --xpath "//*[local-name()='X509Certificate']/text()" - 2>/dev/null | \
  fold -w 64 | \
  { echo "-----BEGIN CERTIFICATE-----"; cat; echo "-----END CERTIFICATE-----"; } > idp-cert-new.pem

# Compare certificates
diff idp-cert.pem idp-cert-new.pem
```

#### 3. User Has No Roles

**Symptom:** Authentication succeeds but user has no permissions

**Solution:**
```bash
# Check users.json configuration
cat users.json | jq '.users[] | {username, roles}'

# Set default roles
java -jar mlesproxy-2.0.2.jar --mleaproxy.saml-default-roles=user,reader
```

#### 4. Redirect Loop

**Symptom:** Browser keeps redirecting between MarkLogic and MLEAProxy

**Solution:**
- Verify `SAML assertion host` matches MarkLogic app server host:port exactly
- Check cookies are enabled in browser
- Ensure clock sync between servers (SAML assertions are time-sensitive)

### Debug Commands

```bash
# Enable verbose logging
java -jar mlesproxy-2.0.2.jar \
  --mleaproxy.saml-debug=true \
  --logging.level.com.marklogic=DEBUG

# Test metadata endpoint
curl -v http://localhost:8080/saml/idp-metadata 2>&1 | head -30

# Check certificate validity
curl -s http://localhost:8080/saml/ca | openssl x509 -noout -dates

# Verify MLEAProxy is running
curl -s http://localhost:8080/actuator/health 2>/dev/null || echo "Health endpoint not available"
```

### Log Messages Reference

| Log Message | Meaning |
|-------------|---------|
| `SAML private key loaded successfully` | Certificate configuration OK |
| `Using roles from request parameter` | Priority 1 role resolution |
| `Using roles from JSON for user` | Priority 2 role resolution |
| `User not found in JSON, using default roles` | Priority 3 role resolution |
| `IdP metadata served successfully` | Metadata endpoint working |

---

## Related Documentation

- [LDAP_GUIDE.md](./LDAP_GUIDE.md) - LDAP authentication
- [OAUTH_GUIDE.md](./OAUTH_GUIDE.md) - OAuth 2.0 tokens
- [KERBEROS_GUIDE.md](./KERBEROS_GUIDE.md) - Kerberos/SPNEGO
- [CONFIGURATION_GUIDE.md](./CONFIGURATION_GUIDE.md) - Full configuration reference

---

## Standards References

- [OASIS SAML 2.0 Core](http://docs.oasis-open.org/security/saml/v2.0/saml-core-2.0-os.pdf)
- [SAML 2.0 Metadata](http://docs.oasis-open.org/security/saml/v2.0/saml-metadata-2.0-os.pdf)
- [SAML 2.0 Bindings](http://docs.oasis-open.org/security/saml/v2.0/saml-bindings-2.0-os.pdf)
