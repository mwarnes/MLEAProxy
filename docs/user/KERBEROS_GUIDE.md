# MLEAProxy Kerberos Guide

Complete guide for Kerberos authentication, SPNEGO, and OAuth/SAML protocol bridges in MLEAProxy.

---

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Configuration Reference](#configuration-reference)
- [KDC Setup](#kdc-setup)
- [Client Configuration (krb5.conf)](#client-configuration-krb5conf)
- [Testing Examples](#testing-examples)
- [Bridge Endpoints](#bridge-endpoints)
- [MarkLogic Integration](#marklogic-integration)
- [Troubleshooting](#troubleshooting)
- [Related Documentation](#related-documentation)

---

## Overview

MLEAProxy includes an embedded Kerberos Key Distribution Center (KDC) for testing and development. It enables SPNEGO HTTP authentication and bridges between Kerberos, OAuth, and SAML protocols.

### Key Features

- **Embedded Kerberos KDC**: Apache Kerby 2.0.3 for development and testing
- **SPNEGO Authentication**: HTTP Negotiate authentication (RFC 4559)
- **OAuth Bridge**: Exchange Kerberos tickets for OAuth JWT tokens
- **SAML Bridge**: Exchange Kerberos tickets for SAML 2.0 assertions
- **Automatic Principal Import**: Import users from in-memory LDAP
- **Auto-generated krb5.conf**: Client configuration generated at startup

### Endpoints Summary

| Endpoint | Method | Authentication | Description |
|----------|--------|----------------|-------------|
| `/kerberos/auth` | GET | SPNEGO | Get JWT token |
| `/kerberos/token` | GET | SPNEGO | Alias for /auth |
| `/kerberos/whoami` | GET | SPNEGO | Get user info |
| `/kerberos/health` | GET | None | Health check |
| `/kerberos/oauth` | POST | SPNEGO | Exchange for OAuth token |
| `/kerberos/saml` | POST | SPNEGO | Exchange for SAML assertion |

---

## Prerequisites

### Required Software

- **Java 21+** (OpenJDK LTS recommended)
- **MIT Kerberos or Heimdal** (kinit, klist, kdestroy commands)

### macOS Installation

```bash
# MIT Kerberos is included in macOS by default
# Verify installation:
which kinit klist kdestroy
```

### Linux Installation (Debian/Ubuntu)

```bash
sudo apt-get update
sudo apt-get install -y krb5-user krb5-config
```

### Linux Installation (RHEL/CentOS)

```bash
sudo yum install -y krb5-workstation krb5-libs
```

### Windows

Use MIT Kerberos for Windows or Windows native Kerberos with a properly configured domain.

---

## Quick Start

### 1. Start MLEAProxy with Kerberos Enabled

```bash
# Start with Kerberos enabled
java -Dmleaproxy.kerberos.enabled=true \
     -jar target/mlesproxy-2.0.2.jar
```

Or use a properties file:

```bash
# Create minimal config
cat > kerberos-test.properties << 'EOF'
mleaproxy.kerberos.enabled=true
mleaproxy.kerberos.realm=MARKLOGIC.LOCAL
mleaproxy.kerberos.kdc-host=localhost
mleaproxy.kerberos.kdc-port=60088
EOF

# Start MLEAProxy
java -Dmleaproxy.properties=./kerberos-test.properties \
     -jar target/mlesproxy-2.0.2.jar
```

### 2. Configure Kerberos Client

```bash
# Set the KRB5_CONFIG environment variable to the auto-generated config
export KRB5_CONFIG=./kerberos/krb5.conf
```

### 3. Get a Kerberos Ticket

```bash
# Authenticate as mluser1 (default password: password)
kinit mluser1@MARKLOGIC.LOCAL
```

When prompted, enter: `password`

### 4. Verify the Ticket

```bash
klist
```

Expected output:
```
Ticket cache: FILE:/tmp/krb5cc_501
Default principal: mluser1@MARKLOGIC.LOCAL

Valid starting       Expires              Service principal
05/18/2026 10:00:00  05/18/2026 20:00:00  krbtgt/MARKLOGIC.LOCAL@MARKLOGIC.LOCAL
```

### 5. Test SPNEGO Authentication

```bash
# Get JWT token via SPNEGO
curl --negotiate -u : http://localhost:8080/kerberos/auth
```

Expected response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "principal": "mluser1@MARKLOGIC.LOCAL",
  "roles": ["ROLE_USER"],
  "expiration": "2026-05-18T20:00:00Z"
}
```

### 6. Clean Up

```bash
# Destroy tickets when done
kdestroy
```

---

## Configuration Reference

### Core Kerberos Properties

All Kerberos properties use the `mleaproxy.kerberos.*` prefix.

| Property | Default | Description |
|----------|---------|-------------|
| `mleaproxy.kerberos.enabled` | `false` | Enable/disable embedded KDC |
| `mleaproxy.kerberos.realm` | `MARKLOGIC.LOCAL` | Kerberos realm (UPPERCASE) |
| `mleaproxy.kerberos.kdc-host` | `localhost` | KDC hostname |
| `mleaproxy.kerberos.kdc-port` | `60088` | KDC port (standard is 88) |
| `mleaproxy.kerberos.admin-port` | `60749` | Kadmin port (standard is 749) |
| `mleaproxy.kerberos.work-dir` | `./kerberos` | Working directory for KDC files |
| `mleaproxy.kerberos.debug` | `false` | Enable debug logging |

### Principal Management Properties

| Property | Default | Description |
|----------|---------|-------------|
| `mleaproxy.kerberos.principals-import-from-ldap` | `true` | Import users from LDAP |
| `mleaproxy.kerberos.principals-ldap-base-dn` | `dc=MarkLogic,dc=Local` | LDAP base DN for import |
| `mleaproxy.kerberos.service-principals` | `HTTP/localhost,ldap/localhost` | Service principals to create |

### Advanced Properties

| Property | Default | Description |
|----------|---------|-------------|
| `mleaproxy.kerberos.ticket-lifetime` | `36000` | Ticket lifetime (seconds, 10 hours) |
| `mleaproxy.kerberos.ticket-renewable-lifetime` | `604800` | Renewable lifetime (seconds, 7 days) |
| `mleaproxy.kerberos.clock-skew` | `300` | Clock skew tolerance (seconds, 5 minutes) |

### Complete Configuration Example

```properties
# mleaproxy.properties - Kerberos Configuration

# ================================================================
# Core Kerberos Settings
# ================================================================
mleaproxy.kerberos.enabled=true
mleaproxy.kerberos.realm=MARKLOGIC.LOCAL
mleaproxy.kerberos.kdc-host=localhost
mleaproxy.kerberos.kdc-port=60088
mleaproxy.kerberos.admin-port=60749
mleaproxy.kerberos.work-dir=./kerberos
mleaproxy.kerberos.debug=false

# ================================================================
# Principal Management
# ================================================================
# Import user principals from in-memory LDAP
mleaproxy.kerberos.principals-import-from-ldap=true
mleaproxy.kerberos.principals-ldap-base-dn=dc=MarkLogic,dc=Local

# Service principals (comma-separated, realm auto-appended)
mleaproxy.kerberos.service-principals=HTTP/localhost,ldap/localhost

# ================================================================
# Advanced Settings (Optional)
# ================================================================
#mleaproxy.kerberos.ticket-lifetime=36000
#mleaproxy.kerberos.ticket-renewable-lifetime=604800
#mleaproxy.kerberos.clock-skew=300
```

---

## KDC Setup

### Automatic Setup

When `mleaproxy.kerberos.enabled=true`, MLEAProxy automatically:

1. Creates the KDC working directory (`./kerberos/`)
2. Initializes the embedded Apache Kerby KDC
3. Imports user principals from in-memory LDAP
4. Creates service principals
5. Generates keytab files in `./kerberos/keytabs/`
6. Creates `krb5.conf` for clients

### Default Principals

When importing from LDAP (`principals-import-from-ldap=true`), these principals are created:

**User Principals** (password: `password`):
- `mluser1@MARKLOGIC.LOCAL`
- `mluser2@MARKLOGIC.LOCAL`
- `mluser3@MARKLOGIC.LOCAL`
- `appreader@MARKLOGIC.LOCAL`
- `appwriter@MARKLOGIC.LOCAL`
- `appadmin@MARKLOGIC.LOCAL`

**Service Principals**:
- `HTTP/localhost@MARKLOGIC.LOCAL`
- `ldap/localhost@MARKLOGIC.LOCAL`
- `krbtgt/MARKLOGIC.LOCAL@MARKLOGIC.LOCAL` (auto-created)

### Generated Files

After startup, the `./kerberos/` directory contains:

```
kerberos/
├── krb5.conf              # Client configuration
├── keytabs/
│   ├── HTTP_localhost.keytab
│   └── ldap_localhost.keytab
└── [KDC database files]
```

---

## Client Configuration (krb5.conf)

### Auto-Generated Configuration

MLEAProxy generates `krb5.conf` automatically. Set the environment variable:

```bash
export KRB5_CONFIG=./kerberos/krb5.conf
```

### Manual krb5.conf Example

If you need to create `krb5.conf` manually:

```ini
[libdefaults]
    default_realm = MARKLOGIC.LOCAL
    dns_lookup_realm = false
    dns_lookup_kdc = false
    ticket_lifetime = 10h
    renew_lifetime = 7d
    forwardable = true
    udp_preference_limit = 1

[realms]
    MARKLOGIC.LOCAL = {
        kdc = localhost:60088
        admin_server = localhost:60749
    }

[domain_realm]
    .marklogic.local = MARKLOGIC.LOCAL
    marklogic.local = MARKLOGIC.LOCAL
    localhost = MARKLOGIC.LOCAL
    .localhost = MARKLOGIC.LOCAL
```

### macOS-Specific Configuration

On macOS, you may also need to update `/etc/krb5.conf` or set the environment variable in your shell profile:

```bash
# Add to ~/.zshrc or ~/.bash_profile
export KRB5_CONFIG=/path/to/your/kerberos/krb5.conf
```

---

## Testing Examples

### Basic SPNEGO Authentication

```bash
# Set Kerberos config
export KRB5_CONFIG=./kerberos/krb5.conf

# Get ticket
kinit mluser1@MARKLOGIC.LOCAL
# Enter password: password

# Test SPNEGO endpoint
curl --negotiate -u : http://localhost:8080/kerberos/auth

# View current user
curl --negotiate -u : http://localhost:8080/kerberos/whoami

# Health check (no auth required)
curl http://localhost:8080/kerberos/health
```

### Complete Test Script

```bash
#!/bin/bash
# test-kerberos.sh - Complete Kerberos test workflow

set -e

# Configuration
export KRB5_CONFIG=./kerberos/krb5.conf
REALM="MARKLOGIC.LOCAL"
USER="mluser1"
HOST="localhost:8080"

echo "=== MLEAProxy Kerberos Test ==="

# Step 1: Clean any existing tickets
echo "[1/5] Cleaning existing tickets..."
kdestroy 2>/dev/null || true

# Step 2: Get Kerberos ticket
echo "[2/5] Getting Kerberos ticket for ${USER}@${REALM}..."
echo "password" | kinit "${USER}@${REALM}"

# Step 3: Verify ticket
echo "[3/5] Verifying ticket..."
klist

# Step 4: Test SPNEGO authentication
echo "[4/5] Testing SPNEGO authentication..."
RESPONSE=$(curl -s --negotiate -u : "http://${HOST}/kerberos/auth")
echo "Response: $RESPONSE"

# Step 5: Test whoami
echo "[5/5] Testing whoami endpoint..."
curl -s --negotiate -u : "http://${HOST}/kerberos/whoami" | jq .

# Cleanup
echo ""
echo "=== Test Complete ==="
kdestroy
```

### Testing with Different Users

```bash
# Test as mluser1
kinit mluser1@MARKLOGIC.LOCAL  # password: password
curl --negotiate -u : http://localhost:8080/kerberos/whoami
kdestroy

# Test as appadmin
kinit appadmin@MARKLOGIC.LOCAL  # password: password
curl --negotiate -u : http://localhost:8080/kerberos/whoami
kdestroy
```

---

## Bridge Endpoints

MLEAProxy provides bridge endpoints to convert Kerberos authentication to OAuth tokens or SAML assertions.

### Kerberos to OAuth Bridge

**POST /kerberos/oauth**

Exchange a Kerberos ticket for an OAuth JWT access token.

```bash
# Get Kerberos ticket first
kinit mluser1@MARKLOGIC.LOCAL

# Exchange for OAuth token
curl --negotiate -u : -X POST http://localhost:8080/kerberos/oauth
```

**Response:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjE3Y2UwNDc0MjgyYmEwMDEifQ...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "app-reader app-writer",
  "principal": "mluser1",
  "kerberos_principal": "mluser1@MARKLOGIC.LOCAL",
  "roles": ["app-reader", "app-writer"],
  "auth_method": "kerberos-oauth-bridge"
}
```

### Kerberos to SAML Bridge

**POST /kerberos/saml**

Exchange a Kerberos ticket for a SAML 2.0 assertion.

```bash
# Get Kerberos ticket first
kinit mluser1@MARKLOGIC.LOCAL

# Exchange for SAML assertion
curl --negotiate -u : -X POST http://localhost:8080/kerberos/saml
```

**Response:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                ID="_a1b2c3d4-e5f6-7890-abcd-ef1234567890"
                IssueInstant="2026-05-18T14:30:00Z"
                Version="2.0">
  <saml:Issuer>mleaproxy-saml-idp</saml:Issuer>
  <saml:Subject>
    <saml:NameID>mluser1@MARKLOGIC.LOCAL</saml:NameID>
  </saml:Subject>
  <saml:AttributeStatement>
    <saml:Attribute Name="username">
      <saml:AttributeValue>mluser1</saml:AttributeValue>
    </saml:Attribute>
    <saml:Attribute Name="roles">
      <saml:AttributeValue>app-reader</saml:AttributeValue>
      <saml:AttributeValue>app-writer</saml:AttributeValue>
    </saml:Attribute>
  </saml:AttributeStatement>
</saml:Assertion>
```

### Using the OAuth Token

```bash
# Get OAuth token from Kerberos
TOKEN=$(curl -s --negotiate -u : -X POST http://localhost:8080/kerberos/oauth | jq -r .access_token)

# Use the token for API calls
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/protected
```

---

## MarkLogic Integration

### Configure MarkLogic for Kerberos

1. **Enable External Security** in MarkLogic Admin UI
2. **Configure the External Security** to point to MLEAProxy
3. **Map users** between Kerberos principals and MarkLogic users

### Example MarkLogic External Security Configuration

```xml
<external-security xmlns="http://marklogic.com/manage">
  <external-security-name>mleaproxy-kerberos</external-security-name>
  <authentication>kerberos</authentication>
  <cache-timeout>300</cache-timeout>
  <authorization>internal</authorization>
  <ldap-server-uri>ldap://localhost:10389</ldap-server-uri>
</external-security>
```

### Using Kerberos-OAuth Bridge with MarkLogic

```bash
# Get OAuth token via Kerberos
kinit mluser1@MARKLOGIC.LOCAL
TOKEN=$(curl -s --negotiate -u : -X POST http://localhost:8080/kerberos/oauth | jq -r .access_token)

# Use token with MarkLogic REST API
curl -H "Authorization: Bearer $TOKEN" \
     "http://localhost:8002/v1/documents?uri=/example.json"
```

---

## Troubleshooting

### Common Issues

#### "Cannot find KDC for realm MARKLOGIC.LOCAL"

**Cause:** `KRB5_CONFIG` not set or points to wrong file.

**Solution:**
```bash
export KRB5_CONFIG=./kerberos/krb5.conf
# Verify the file exists
cat $KRB5_CONFIG
```

#### "kinit: Client not found in Kerberos database"

**Cause:** User principal does not exist in KDC.

**Solution:**
```bash
# Check if Kerberos is enabled and users imported from LDAP
# Verify in startup logs:
grep "Created principal" logs/mleaproxy.log

# Default users: mluser1, mluser2, mluser3, appreader, appwriter, appadmin
```

#### "Clock skew too great"

**Cause:** Time difference between client and KDC exceeds tolerance (default 5 minutes).

**Solution:**
```bash
# Sync system time
# macOS:
sudo sntp -sS time.apple.com

# Linux:
sudo ntpdate pool.ntp.org

# Or increase clock skew tolerance:
# mleaproxy.kerberos.clock-skew=600
```

#### "Pre-authentication failed"

**Cause:** Wrong password.

**Solution:**
```bash
# Default password for all test users is: password
kinit mluser1@MARKLOGIC.LOCAL
# Enter: password
```

#### Port 60088 already in use

**Cause:** Another KDC or process using the port.

**Solution:**
```bash
# Check what's using the port
lsof -i :60088

# Use a different port
mleaproxy.kerberos.kdc-port=60089
```

#### curl --negotiate returns 401

**Cause:** No valid Kerberos ticket or SPNEGO not working.

**Solution:**
```bash
# Verify you have a valid ticket
klist

# If no ticket, get one
kinit mluser1@MARKLOGIC.LOCAL

# Verify curl supports SPNEGO
curl --version | grep -i spnego

# Try verbose mode
curl -v --negotiate -u : http://localhost:8080/kerberos/auth
```

### Debug Logging

Enable debug logging to troubleshoot issues:

```properties
# Enable Kerberos debug
mleaproxy.kerberos.debug=true

# Java Kerberos debug
-Dsun.security.krb5.debug=true

# Spring logging
logging.level.com.marklogic.handlers.KerberosKDCServer=DEBUG
logging.level.com.marklogic.handlers.undertow.KerberosAuthHandler=DEBUG
logging.level.com.marklogic.security.KerberosAuthenticationFilter=DEBUG
```

Run with debug:
```bash
java -Dsun.security.krb5.debug=true \
     -Dmleaproxy.kerberos.debug=true \
     -jar target/mlesproxy-2.0.2.jar
```

### Verify KDC is Running

```bash
# Check if KDC port is listening
nc -zv localhost 60088

# Or with netstat
netstat -an | grep 60088
```

### View Keytab Contents

```bash
# List principals in keytab
klist -kt ./kerberos/keytabs/HTTP_localhost.keytab
```

---

## Related Documentation

### Protocol Guides

- [OAUTH_GUIDE.md](./OAUTH_GUIDE.md) - OAuth 2.0 implementation
- [SAML_GUIDE.md](./SAML_GUIDE.md) - SAML 2.0 implementation
- [LDAP_GUIDE.md](./LDAP_GUIDE.md) - LDAP authentication and directory

### Configuration

- [CONFIGURATION_GUIDE.md](./CONFIGURATION_GUIDE.md) - Complete configuration reference

### Developer Documentation

- [../developer/KERBEROS_PHASE1_COMPLETE.md](../developer/KERBEROS_PHASE1_COMPLETE.md) - Embedded KDC implementation
- [../developer/KERBEROS_PHASE2_COMPLETE.md](../developer/KERBEROS_PHASE2_COMPLETE.md) - SPNEGO implementation
- [../developer/KERBEROS_PHASE3_COMPLETE.md](../developer/KERBEROS_PHASE3_COMPLETE.md) - OAuth/SAML bridges

### Standards

- [RFC 4120](https://tools.ietf.org/html/rfc4120) - Kerberos V5 Protocol
- [RFC 4559](https://tools.ietf.org/html/rfc4559) - SPNEGO HTTP Authentication
- [RFC 6749](https://tools.ietf.org/html/rfc6749) - OAuth 2.0
- [OASIS SAML 2.0](http://docs.oasis-open.org/security/saml/v2.0/) - SAML Specification
