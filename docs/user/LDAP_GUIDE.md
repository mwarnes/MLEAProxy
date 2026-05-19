# MLEAProxy LDAP Guide

Complete guide for LDAP/LDAPS authentication proxy functionality in MLEAProxy.

---

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration Reference](#configuration-reference)
- [Usage Examples](#usage-examples)
- [In-Memory LDAP Server](#in-memory-ldap-server)
- [MarkLogic Integration](#marklogic-integration)
- [Security Features](#security-features)
- [Troubleshooting](#troubleshooting)

---

## Overview

MLEAProxy provides comprehensive LDAP/LDAPS proxy and server capabilities:

- **Diagnostic Tool**: Debug LDAP authentication issues with detailed logging
- **Proxy Mode**: Forward requests to backend LDAP/Active Directory servers
- **Load Balancing**: Support for multiple backend servers with various algorithms
- **Standalone Mode**: JSON-based LDAP server for testing without backend infrastructure
- **Security Hardening**: Built-in protection against LDAP injection attacks

### Default LDAP Endpoints

| Protocol | Default Port | Purpose |
|----------|--------------|---------|
| LDAP | 10389 | LDAP proxy (requires backend) |
| LDAP | 20389 | JSON LDAP server (standalone) |
| LDAP | 60389 | In-memory directory server |

---

## Prerequisites

- **Java 21** or later (JDK required for building, JRE sufficient for running)
- **Maven 3.9+** (for building from source)
- **ldapsearch** (optional, for testing - part of OpenLDAP client tools)

### Verify Java Installation

```bash
java -version
```

Expected output: `openjdk version "21.x.x"` or similar.

---

## Installation

### Option 1: Download Pre-built JAR

Download `mlesproxy-2.0.2.jar` from the releases page.

### Option 2: Build from Source

```bash
git clone https://github.com/marklogic/MLEAProxy.git
cd MLEAProxy
./build.sh clean package
```

The built JAR is located at `target/mlesproxy-2.0.2.jar`.

### Verify Installation

```bash
java -jar mlesproxy-2.0.2.jar --help
```

---

## Quick Start

### Minimal Configuration (Standalone JSON LDAP Server)

The simplest way to get started requires no backend LDAP server.

**1. Create `mleaproxy.properties`:**

```properties
# Minimal standalone LDAP server configuration
mleaproxy.ldap-debug=true

# LDAP listener
mleaproxy.ldap-listeners.ldapjson.port=20389
mleaproxy.ldap-listeners.ldapjson.ldap-mode=internal
mleaproxy.ldap-listeners.ldapjson.ldap-sets=internal
mleaproxy.ldap-listeners.ldapjson.request-processor=jsonauth

# Server set (required even for internal mode)
mleaproxy.ldap-sets.internal.servers=localhost

# Backend server reference
mleaproxy.ldap-servers.localhost.host=localhost
mleaproxy.ldap-servers.localhost.port=389

# Request processor
mleaproxy.request-processors.jsonauth.auth-class=com.marklogic.processors.JsonRequestProcessor
```

**2. Start MLEAProxy:**

```bash
java -jar mlesproxy-2.0.2.jar
```

**3. Verify with ldapsearch:**

```bash
ldapsearch -H ldap://localhost:20389 -x -D "cn=manager" -w password \
  -b "ou=users,dc=marklogic,dc=local" "(sAMAccountName=admin)"
```

Expected output:

```ldif
# admin, users, marklogic.local
dn: cn=admin,ou=users,dc=marklogic,dc=local
objectClass: inetOrgPerson
memberOf: admin
```

### Command-Line Property Overrides

Override any property via command line:

```bash
java -jar mlesproxy-2.0.2.jar --mleaproxy.ldap-debug=true --mleaproxy.ldap-listeners.ldapjson.port=30389
```

---

## Configuration Reference

MLEAProxy uses Spring Boot configuration properties with the `mleaproxy.*` prefix. Properties can be set via:

1. `application.properties` or `application.yml`
2. External properties files via `--spring.config.additional-location=./ldap.properties`
3. Command-line arguments: `--mleaproxy.ldap-debug=true`
4. Environment variables: `MLEAPROXY_LDAP_DEBUG=true`

### Architecture Components

```
LDAP Client --> Listener --> Request Processor --> Server Set --> Backend Servers
```

### Global Settings

| Property | Description | Default |
|----------|-------------|---------|
| `mleaproxy.ldap-debug` | Enable LDAP protocol debug logging | `false` |

### LDAP Listeners

Prefix: `mleaproxy.ldap-listeners.{name}.*`

| Property | Description | Default | Required |
|----------|-------------|---------|----------|
| `ip-address` | IP address to bind | `0.0.0.0` | No |
| `port` | Listening port | - | Yes |
| `secure` | Enable LDAPS | `false` | No |
| `debug-level` | Logging level: DEBUG, INFO, WARN, ERROR | `INFO` | No |
| `keystore` | Path to keystore for LDAPS | - | If secure=true |
| `keystore-password` | Keystore password | - | If keystore set |
| `truststore` | Path to truststore | - | No |
| `truststore-password` | Truststore password | - | If truststore set |
| `ldap-sets` | Comma-separated server set names | - | Yes |
| `ldap-mode` | Balancing mode (see below) | `internal` | No |
| `request-processor` | Request processor name | - | Yes |
| `description` | Human-readable description | - | No |

**LDAP Modes:**

| Mode | Description |
|------|-------------|
| `internal` | Standalone mode, no backend servers |
| `single` | Use only first server in set |
| `failover` | Primary server with automatic failover |
| `roundrobin` | Distribute requests evenly |
| `roundrobindns` | DNS-based round robin |
| `fewest` | Route to least-used server |
| `fastest` | Route to fastest-responding server |

### LDAP Server Sets

Prefix: `mleaproxy.ldap-sets.{name}.*`

| Property | Description | Default | Required |
|----------|-------------|---------|----------|
| `servers` | Comma-separated server names | - | Yes |
| `secure` | Use LDAPS to backend | `false` | No |
| `keystore` | Client keystore path | - | No |
| `keystore-password` | Keystore password | - | If keystore set |
| `truststore` | Truststore path for CA certs | - | No |
| `truststore-password` | Truststore password | - | If truststore set |

### LDAP Backend Servers

Prefix: `mleaproxy.ldap-servers.{name}.*`

| Property | Description | Default | Required |
|----------|-------------|---------|----------|
| `host` | Hostname or IP address | - | Yes |
| `port` | Port number | - | Yes |

### Request Processors

Prefix: `mleaproxy.request-processors.{name}.*`

| Property | Description | Default | Required |
|----------|-------------|---------|----------|
| `auth-class` | Fully qualified processor class | - | Yes |
| `debug-level` | Logging level | `INFO` | No |
| `params` | List of additional parameters | - | No |

**Built-in Processors:**

| Class | Description |
|-------|-------------|
| `com.marklogic.processors.JsonRequestProcessor` | JSON user repository (standalone) |
| `com.marklogic.processors.ProxyRequestProcessor` | Proxy to backend LDAP |
| `com.marklogic.processors.XmlRequestProcessor` | Legacy XML user repository |

### In-Memory Directory Servers

Prefix: `mleaproxy.directory-servers.{name}.*`

| Property | Description | Default | Required |
|----------|-------------|---------|----------|
| `name` | Display name | - | No |
| `ip-address` | Bind address | `0.0.0.0` | No |
| `port` | Listening port | `60389` | No |
| `base-dn` | Directory base DN | `dc=MarkLogic,dc=Local` | No |
| `admin-dn` | Administrator DN | `cn=Directory Manager` | No |
| `admin-password` | Administrator password | `password` | No |
| `ldif-path` | Custom LDIF file path | (uses built-in) | No |

---

## Usage Examples

### Example 1: Standalone JSON LDAP Server

No backend LDAP server required. Uses JSON user data.

**Configuration:**

```properties
# Enable debug logging
mleaproxy.ldap-debug=true

# LDAP Listener
mleaproxy.ldap-listeners.ldapjson.ip-address=0.0.0.0
mleaproxy.ldap-listeners.ldapjson.port=20389
mleaproxy.ldap-listeners.ldapjson.debug-level=DEBUG
mleaproxy.ldap-listeners.ldapjson.ldap-mode=internal
mleaproxy.ldap-listeners.ldapjson.ldap-sets=internal
mleaproxy.ldap-listeners.ldapjson.request-processor=jsonauthenticator
mleaproxy.ldap-listeners.ldapjson.description=Simple LDAP Server using JSON user store

# Server Set (required even for internal mode)
mleaproxy.ldap-sets.internal.servers=localhost
mleaproxy.ldap-sets.internal.secure=false

# Backend Server reference
mleaproxy.ldap-servers.localhost.host=localhost
mleaproxy.ldap-servers.localhost.port=389

# Request Processor
mleaproxy.request-processors.jsonauthenticator.auth-class=com.marklogic.processors.JsonRequestProcessor
mleaproxy.request-processors.jsonauthenticator.debug-level=DEBUG
```

**User Data (`users.json`):**

```json
{
  "baseDN": "ou=users,dc=marklogic,dc=local",
  "users": [
    {
      "dn": "cn=manager",
      "sAMAccountName": "manager",
      "userPassword": "password",
      "roles": ["admin"]
    },
    {
      "dn": "cn=user1",
      "sAMAccountName": "user1",
      "userPassword": "password",
      "memberOf": [
        "cn=appreader,ou=groups,dc=marklogic,dc=local",
        "cn=appwriter,ou=groups,dc=marklogic,dc=local"
      ],
      "roles": ["reader", "writer"]
    }
  ]
}
```

**Test:**

```bash
ldapsearch -H ldap://localhost:20389 -x -D "cn=manager" -w password \
  -b "ou=users,dc=marklogic,dc=local" "(sAMAccountName=user1)" memberOf
```

### Example 2: Simple LDAP Proxy

Forward LDAP requests to a backend server with logging.

**Configuration:**

```properties
mleaproxy.ldap-debug=true

# Listener
mleaproxy.ldap-listeners.proxy.ip-address=0.0.0.0
mleaproxy.ldap-listeners.proxy.port=10389
mleaproxy.ldap-listeners.proxy.debug-level=DEBUG
mleaproxy.ldap-listeners.proxy.ldap-sets=backend
mleaproxy.ldap-listeners.proxy.ldap-mode=single
mleaproxy.ldap-listeners.proxy.request-processor=ldapproxy
mleaproxy.ldap-listeners.proxy.description=LDAP Proxy server

# Processor
mleaproxy.request-processors.ldapproxy.auth-class=com.marklogic.processors.ProxyRequestProcessor
mleaproxy.request-processors.ldapproxy.debug-level=DEBUG

# Server Set
mleaproxy.ldap-sets.backend.servers=server1

# Backend Server
mleaproxy.ldap-servers.server1.host=ldap.company.com
mleaproxy.ldap-servers.server1.port=389
```

**Test:**

```bash
ldapsearch -H ldap://localhost:10389 -x \
  -D "cn=testuser,ou=users,dc=company,dc=com" -w password \
  -b "ou=users,dc=company,dc=com" "(uid=testuser)"
```

### Example 3: Secure LDAP Proxy (LDAPS Backend)

Proxy with unencrypted client connection but secure backend. Ideal for debugging LDAPS issues.

**Configuration:**

```properties
mleaproxy.ldap-debug=true

# Listener (clear LDAP for debugging visibility)
mleaproxy.ldap-listeners.proxy.ip-address=0.0.0.0
mleaproxy.ldap-listeners.proxy.port=30389
mleaproxy.ldap-listeners.proxy.debug-level=DEBUG
mleaproxy.ldap-listeners.proxy.secure=false
mleaproxy.ldap-listeners.proxy.ldap-sets=securebackend
mleaproxy.ldap-listeners.proxy.ldap-mode=single
mleaproxy.ldap-listeners.proxy.request-processor=ldapproxy
mleaproxy.ldap-listeners.proxy.description=LDAP proxy with LDAPS backend

# Processor
mleaproxy.request-processors.ldapproxy.auth-class=com.marklogic.processors.ProxyRequestProcessor
mleaproxy.request-processors.ldapproxy.debug-level=DEBUG

# Server Set (secure backend)
mleaproxy.ldap-sets.securebackend.servers=server1
mleaproxy.ldap-sets.securebackend.secure=true
mleaproxy.ldap-sets.securebackend.truststore=/path/to/truststore.jks
mleaproxy.ldap-sets.securebackend.truststore-password=changeit

# Backend Server (LDAPS)
mleaproxy.ldap-servers.server1.host=ldap.company.com
mleaproxy.ldap-servers.server1.port=636
```

**Test:**

```bash
ldapsearch -H ldap://localhost:30389 -x \
  -D "cn=testuser,ou=users,dc=company,dc=com" -w password \
  -b "ou=users,dc=company,dc=com" "(uid=testuser)"
```

### Example 4: Fully Secure LDAP Proxy (LDAPS Both Sides)

LDAPS on both client and backend connections.

**Configuration:**

```properties
mleaproxy.ldap-debug=true

# Listener (LDAPS)
mleaproxy.ldap-listeners.proxy.ip-address=0.0.0.0
mleaproxy.ldap-listeners.proxy.port=30636
mleaproxy.ldap-listeners.proxy.debug-level=DEBUG
mleaproxy.ldap-listeners.proxy.secure=true
mleaproxy.ldap-listeners.proxy.keystore=/path/to/server-keystore.jks
mleaproxy.ldap-listeners.proxy.keystore-password=password
mleaproxy.ldap-listeners.proxy.ldap-sets=securebackend
mleaproxy.ldap-listeners.proxy.ldap-mode=single
mleaproxy.ldap-listeners.proxy.request-processor=ldapproxy
mleaproxy.ldap-listeners.proxy.description=Fully secure LDAP proxy

# Processor
mleaproxy.request-processors.ldapproxy.auth-class=com.marklogic.processors.ProxyRequestProcessor
mleaproxy.request-processors.ldapproxy.debug-level=DEBUG

# Server Set (secure backend)
mleaproxy.ldap-sets.securebackend.servers=server1
mleaproxy.ldap-sets.securebackend.secure=true
mleaproxy.ldap-sets.securebackend.truststore=/path/to/truststore.jks
mleaproxy.ldap-sets.securebackend.truststore-password=changeit

# Backend Server
mleaproxy.ldap-servers.server1.host=ldap.company.com
mleaproxy.ldap-servers.server1.port=636
```

**Test:**

```bash
ldapsearch -H ldaps://localhost:30636 -x \
  -D "cn=testuser,ou=users,dc=company,dc=com" -w password \
  -b "ou=users,dc=company,dc=com" "(uid=testuser)"
```

### Example 5: Load Balancing (Round Robin)

Balance requests across multiple LDAP servers.

**Configuration:**

```properties
mleaproxy.ldap-debug=true

# Listener
mleaproxy.ldap-listeners.proxy.ip-address=0.0.0.0
mleaproxy.ldap-listeners.proxy.port=30389
mleaproxy.ldap-listeners.proxy.debug-level=DEBUG
mleaproxy.ldap-listeners.proxy.ldap-sets=cluster
mleaproxy.ldap-listeners.proxy.ldap-mode=roundrobin
mleaproxy.ldap-listeners.proxy.request-processor=ldapproxy
mleaproxy.ldap-listeners.proxy.description=Load balancing LDAP proxy

# Processor
mleaproxy.request-processors.ldapproxy.auth-class=com.marklogic.processors.ProxyRequestProcessor
mleaproxy.request-processors.ldapproxy.debug-level=DEBUG

# Server Set
mleaproxy.ldap-sets.cluster.servers=server1,server2,server3

# Backend Servers
mleaproxy.ldap-servers.server1.host=192.168.0.50
mleaproxy.ldap-servers.server1.port=389

mleaproxy.ldap-servers.server2.host=192.168.0.51
mleaproxy.ldap-servers.server2.port=389

mleaproxy.ldap-servers.server3.host=192.168.0.52
mleaproxy.ldap-servers.server3.port=389
```

### Example 6: Multi-Site Failover

Load balance within sites, with failover between sites.

**Configuration:**

```properties
mleaproxy.ldap-debug=true

# Listener (multiple server sets for failover)
mleaproxy.ldap-listeners.proxy.ip-address=0.0.0.0
mleaproxy.ldap-listeners.proxy.port=30389
mleaproxy.ldap-listeners.proxy.debug-level=DEBUG
mleaproxy.ldap-listeners.proxy.ldap-sets=primarysite,secondarysite
mleaproxy.ldap-listeners.proxy.ldap-mode=roundrobin
mleaproxy.ldap-listeners.proxy.request-processor=ldapproxy
mleaproxy.ldap-listeners.proxy.description=Multi-site load balancing with failover

# Processor
mleaproxy.request-processors.ldapproxy.auth-class=com.marklogic.processors.ProxyRequestProcessor
mleaproxy.request-processors.ldapproxy.debug-level=DEBUG

# Primary Site Server Set
mleaproxy.ldap-sets.primarysite.servers=server1,server2

# Secondary Site Server Set
mleaproxy.ldap-sets.secondarysite.servers=server3,server4

# Primary Site Servers
mleaproxy.ldap-servers.server1.host=192.168.0.50
mleaproxy.ldap-servers.server1.port=389

mleaproxy.ldap-servers.server2.host=192.168.0.51
mleaproxy.ldap-servers.server2.port=389

# Secondary Site Servers
mleaproxy.ldap-servers.server3.host=192.168.1.50
mleaproxy.ldap-servers.server3.port=389

mleaproxy.ldap-servers.server4.host=192.168.1.51
mleaproxy.ldap-servers.server4.port=389
```

---

## In-Memory LDAP Server

MLEAProxy includes a built-in in-memory LDAP directory server powered by UnboundID. Perfect for development, testing, and demos.

### Features

- UnboundID In-Memory Directory Server
- LDIF import at startup
- Multiple servers on different ports
- Zero external dependencies
- Fast startup (milliseconds)

### Quick Start

**Configuration:**

```properties
# In-memory directory server
mleaproxy.directory-servers.marklogic.name=MarkLogic Local
mleaproxy.directory-servers.marklogic.ip-address=0.0.0.0
mleaproxy.directory-servers.marklogic.port=60389
mleaproxy.directory-servers.marklogic.base-dn=dc=MarkLogic,dc=Local
mleaproxy.directory-servers.marklogic.admin-dn=cn=Directory Manager
mleaproxy.directory-servers.marklogic.admin-password=password
```

**Start:**

```bash
java -jar mlesproxy-2.0.2.jar
```

**Test:**

```bash
ldapsearch -H ldap://localhost:60389 -x -D "cn=Directory Manager" -w password \
  -b "dc=MarkLogic,dc=Local" "(objectClass=*)"
```

### Custom LDIF File

```properties
mleaproxy.directory-servers.custom.name=Custom Directory
mleaproxy.directory-servers.custom.port=60390
mleaproxy.directory-servers.custom.base-dn=dc=example,dc=com
mleaproxy.directory-servers.custom.ldif-path=/opt/ldap-data/custom.ldif
```

### Built-in Users

The default `marklogic.ldif` includes:

| Username | DN | Password |
|----------|-----|----------|
| mluser1 | `uid=mluser1,ou=Users,dc=MarkLogic,dc=Local` | password |
| mluser2 | `uid=mluser2,ou=Users,dc=MarkLogic,dc=Local` | password |
| appreader | `uid=appreader,ou=Users,dc=MarkLogic,dc=Local` | password |
| appwriter | `uid=appwriter,ou=Users,dc=MarkLogic,dc=Local` | password |
| appadmin | `uid=appadmin,ou=Users,dc=MarkLogic,dc=Local` | password |

### Multiple In-Memory Servers

```properties
# Server 1
mleaproxy.directory-servers.server1.name=MarkLogic-Test
mleaproxy.directory-servers.server1.port=60389
mleaproxy.directory-servers.server1.base-dn=dc=MarkLogic,dc=Local

# Server 2
mleaproxy.directory-servers.server2.name=Custom-Test
mleaproxy.directory-servers.server2.port=60390
mleaproxy.directory-servers.server2.base-dn=dc=example,dc=com
mleaproxy.directory-servers.server2.ldif-path=/opt/data/example.ldif
```

---

## MarkLogic Integration

### Automatic External Security Configuration

MLEAProxy automatically generates MarkLogic External Security configurations for each LDAP listener at startup.

**Example Startup Output:**

```
MarkLogic External Security Configuration Generated:
  Configuration file: marklogic-external-security-ldapjson.json
  LDAP URI: ldap://localhost:20389
  LDAP Base: ou=users,dc=marklogic,dc=local
  LDAP Attribute: sAMAccountName
  Apply with: curl -X POST --anyauth -u admin:admin \
    -H "Content-Type:application/json" \
    -d @marklogic-external-security-ldapjson.json \
    http://localhost:8002/manage/v2/external-security
```

### Apply Configuration

```bash
curl -X POST --anyauth -u admin:admin -H "Content-Type:application/json" \
  -d @marklogic-external-security-ldapjson.json \
  http://localhost:8002/manage/v2/external-security
```

### Generated Configuration Example

```json
{
  "external-security-name": "MLEAProxy-ldapjson",
  "description": "External security for MLEAProxy LDAP listener 'ldapjson'",
  "authentication": "ldap",
  "cache-timeout": "300",
  "authorization": "ldap",
  "ldap-server-uri": "ldap://localhost:20389",
  "ldap-base": "ou=users,dc=marklogic,dc=local",
  "ldap-attribute": "sAMAccountName",
  "ldap-default-user": "default",
  "ldap-password": "password",
  "ldap-bind-method": "simple"
}
```

### MarkLogic REST API Operations

```bash
# List external security configurations
curl --anyauth -u admin:admin http://localhost:8002/manage/v2/external-security

# Get specific configuration
curl --anyauth -u admin:admin \
  http://localhost:8002/manage/v2/external-security/MLEAProxy-ldapjson

# Delete configuration
curl -X DELETE --anyauth -u admin:admin \
  http://localhost:8002/manage/v2/external-security/MLEAProxy-ldapjson
```

---

## Security Features

### LDAP Injection Protection

MLEAProxy automatically sanitizes LDAP inputs:
- Escapes special characters: `*`, `(`, `)`, `\`, `NUL`
- Validates DN syntax
- Prevents filter injection

### TLS/SSL Configuration

**Server-side TLS (listener):**

```properties
mleaproxy.ldap-listeners.secure.secure=true
mleaproxy.ldap-listeners.secure.keystore=/path/to/keystore.jks
mleaproxy.ldap-listeners.secure.keystore-password=changeit
```

**Client-side TLS (backend):**

```properties
mleaproxy.ldap-sets.backend.secure=true
mleaproxy.ldap-sets.backend.truststore=/path/to/truststore.jks
mleaproxy.ldap-sets.backend.truststore-password=changeit
```

### JSON User Repository

MLEAProxy uses JSON format for user data:

```json
{
  "baseDN": "ou=users,dc=marklogic,dc=local",
  "users": [
    {
      "dn": "cn=manager",
      "sAMAccountName": "manager",
      "userPassword": "password",
      "roles": ["admin"]
    }
  ]
}
```

Features:
- Case-insensitive username lookup
- Bcrypt password hashing support
- Role-based access control

---

## Troubleshooting

### Enable Debug Logging

```bash
java -jar mlesproxy-2.0.2.jar --mleaproxy.ldap-debug=true
```

Or in configuration:

```properties
mleaproxy.ldap-debug=true
mleaproxy.ldap-listeners.proxy.debug-level=DEBUG
mleaproxy.request-processors.ldapproxy.debug-level=DEBUG
```

### Common Issues

#### Connection Refused

**Symptom:** `Connection refused` when connecting

**Solutions:**
1. Verify listener is running: `netstat -an | grep 10389`
2. Check bind address: `mleaproxy.ldap-listeners.proxy.ip-address=0.0.0.0`
3. Check firewall: `sudo ufw allow 10389/tcp`

#### Authentication Failed (Error 49)

**Symptom:** `INVALID_CREDENTIALS` error

**Solutions:**
1. Enable debug: `--mleaproxy.ldap-debug=true`
2. Verify user exists in `users.json`
3. Check password (case-sensitive)
4. Verify DN format: `cn=user,ou=users,dc=example,dc=com`

#### TLS/SSL Errors

**Symptom:** `SSLHandshakeException` or certificate errors

**Solutions:**
1. Verify certificate: `keytool -list -v -keystore keystore.jks`
2. Add CA cert to truststore: `keytool -import -file ca.crt -keystore truststore.jks`
3. Check hostname matches certificate CN

#### Backend Connection Failed

**Symptom:** Cannot connect to backend LDAP server

**Solutions:**
1. Test backend directly: `ldapsearch -H ldap://backend:389 -x`
2. Verify server set configuration
3. Check network connectivity: `telnet backend 389`

### LDAPS Troubleshooting Checklist

| Issue | Check | Solution |
|-------|-------|----------|
| Certificate Errors | Truststore has CA cert | `keytool -import -file ca.crt -keystore truststore.jks` |
| Hostname Mismatch | CN matches hostname | Update DNS or certificate |
| Connection Timeout | Port 636 accessible | `telnet ldap.company.com 636` |
| Protocol Errors | Using correct port | Port 636 (LDAPS) not 389 (LDAP) |

### Sample Debug Output

```log
2024-01-15 14:30:15.123 DEBUG --- Client Request: BindRequest(dn='cn=testuser,...')
2024-01-15 14:30:15.150 DEBUG --- Backend Connection: Connecting to ldap.company.com:389
2024-01-15 14:30:15.280 DEBUG --- Backend Response: BindResponse(resultCode=SUCCESS)
2024-01-15 14:30:15.281 DEBUG --- Client Response: BindResponse(resultCode=SUCCESS)
```

---

## Related Documentation

- [OAUTH_GUIDE.md](./OAUTH_GUIDE.md) - OAuth 2.0 functionality
- [SAML_GUIDE.md](./SAML_GUIDE.md) - SAML 2.0 functionality
- [KERBEROS_GUIDE.md](./KERBEROS_GUIDE.md) - Kerberos authentication
- [CONFIGURATION_GUIDE.md](./CONFIGURATION_GUIDE.md) - Complete configuration reference

---

## Standards References

- [RFC 4511](https://tools.ietf.org/html/rfc4511) - LDAP: The Protocol
- [RFC 4513](https://tools.ietf.org/html/rfc4513) - LDAP: Authentication Methods and Security
- [RFC 4516](https://tools.ietf.org/html/rfc4516) - LDAP: Uniform Resource Locator
