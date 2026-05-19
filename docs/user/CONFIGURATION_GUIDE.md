# Configuration Guide

## Overview

MLEAProxy uses Spring Boot's configuration system with all mleaproxy-specific properties under the `mleaproxy.*` namespace. Configuration supports kebab-case, camelCase, and SCREAMING_SNAKE_CASE naming conventions. Configuration can be organized into modular property files or consolidated into a single file.

## Prerequisites

- **Java 21** or later (Eclipse Temurin recommended)
- **Maven 3.8+** for building from source

## Installation

### Build from Source

```bash
# Clone and build
git clone https://github.com/marklogic/MLEAProxy.git
cd MLEAProxy
./build.sh clean package

# Verify build
ls -la target/mlesproxy-2.0.2.jar
```

### Run the Application

```bash
# Basic startup (uses ./application.properties or ./mleaproxy.properties)
java -jar target/mlesproxy-2.0.2.jar

# With Spring Boot dev mode
mvn spring-boot:run
```

## Property Loading Priority

Properties are loaded from multiple sources. Later sources override earlier ones:

1. `classpath:mleaproxy.properties` (bundled in JAR - lowest priority)
2. `/etc/mleaproxy.properties` (system-wide)
3. `${HOME}/mleaproxy.properties` (user-specific)
4. `./mleaproxy.properties` (current directory)
5. `./ldap.properties` (LDAP-specific configuration)
6. `./saml.properties` (SAML-specific configuration)
7. `./oauth.properties` (OAuth-specific configuration)
8. `./directory.properties` (directory server configuration)
9. `./kerberos.properties` (Kerberos KDC configuration)
10. **Environment variables** (e.g., `MLEAPROXY_LDAP_DEBUG=true`)
11. **System properties** via `-D` flags (e.g., `-Dmleaproxy.ldap-debug=true`)
12. **Command-line arguments** via `--` syntax (highest priority)

### Configuration Files

| File | Purpose |
|------|---------|
| `mleaproxy.properties` | Main configuration (all settings) |
| `ldap.properties` | LDAP listeners, server sets, backend servers |
| `saml.properties` | SAML IdP settings |
| `oauth.properties` | OAuth/JWT token settings |
| `directory.properties` | In-memory LDAP directory servers |
| `kerberos.properties` | Embedded Kerberos KDC |

## Property Reference

### Global Settings

```properties
# Debug logging for LDAP operations
mleaproxy.ldap-debug=true

# Debug logging for SAML operations
mleaproxy.saml-debug=true

# SSL certificate verification (disable for self-signed certs)
mleaproxy.ssl-verify-certificates=false

# SAML certificate paths (optional - uses bundled certs if not set)
mleaproxy.saml-ca-path=/path/to/certificate.pem
mleaproxy.saml-key-path=/path/to/rsakey.pem

# SAML response validity in seconds (default: 300)
mleaproxy.saml-response-validity=300
```

### Directory Servers (In-Memory LDAP)

In-memory LDAP servers for testing and development. Define servers using a map structure:

```properties
# Directory server named "marklogic"
mleaproxy.directory-servers.marklogic.ip-address=0.0.0.0
mleaproxy.directory-servers.marklogic.port=60389
mleaproxy.directory-servers.marklogic.base-dn=dc=MarkLogic,dc=Local
mleaproxy.directory-servers.marklogic.admin-dn=cn=Directory Manager
mleaproxy.directory-servers.marklogic.admin-password=password
mleaproxy.directory-servers.marklogic.ldif-path=/path/to/users.ldif

# Another directory server named "test"
mleaproxy.directory-servers.test.ip-address=0.0.0.0
mleaproxy.directory-servers.test.port=60390
mleaproxy.directory-servers.test.base-dn=dc=Test,dc=Local
mleaproxy.directory-servers.test.admin-dn=cn=admin
mleaproxy.directory-servers.test.admin-password=secret
```

**Directory Server Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ip-address` | String | `0.0.0.0` | IP address to bind to |
| `port` | Integer | `60389` | Port number |
| `base-dn` | String | `dc=MarkLogic,dc=Local` | Base distinguished name |
| `admin-dn` | String | `cn=Directory Manager` | Administrator DN |
| `admin-password` | String | `password` | Administrator password |
| `ldif-path` | String | (none) | Path to LDIF file to load on startup |

### LDAP Listeners

LDAP proxy listeners that accept client connections:

```properties
# Listener named "proxy"
mleaproxy.ldap-listeners.proxy.ip-address=0.0.0.0
mleaproxy.ldap-listeners.proxy.port=10389
mleaproxy.ldap-listeners.proxy.secure=false
mleaproxy.ldap-listeners.proxy.ldap-mode=single
mleaproxy.ldap-listeners.proxy.ldap-sets=backend
mleaproxy.ldap-listeners.proxy.request-processor=jsonauth
mleaproxy.ldap-listeners.proxy.debug-level=INFO
mleaproxy.ldap-listeners.proxy.description=Main LDAP proxy

# Secure listener named "secure-proxy" with SSL/TLS
mleaproxy.ldap-listeners.secure-proxy.ip-address=0.0.0.0
mleaproxy.ldap-listeners.secure-proxy.port=10636
mleaproxy.ldap-listeners.secure-proxy.secure=true
mleaproxy.ldap-listeners.secure-proxy.ldap-mode=failover
mleaproxy.ldap-listeners.secure-proxy.ldap-sets=primary,secondary
mleaproxy.ldap-listeners.secure-proxy.request-processor=jsonauth
mleaproxy.ldap-listeners.secure-proxy.keystore=/path/to/keystore.jks
mleaproxy.ldap-listeners.secure-proxy.keystore-password=changeit
mleaproxy.ldap-listeners.secure-proxy.truststore=/path/to/truststore.jks
mleaproxy.ldap-listeners.secure-proxy.truststore-password=changeit
```

**LDAP Listener Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ip-address` | String | `0.0.0.0` | IP address to bind to |
| `port` | Integer | (required) | Port number |
| `secure` | Boolean | `false` | Enable LDAPS |
| `ldap-mode` | String | `internal` | Server set mode |
| `ldap-sets` | List | (empty) | Comma-separated server set names |
| `request-processor` | String | (none) | Request processor name |
| `debug-level` | String | `INFO` | Logging level |
| `keystore` | String | (none) | Path to SSL keystore |
| `keystore-password` | String | (none) | Keystore password |
| `truststore` | String | (none) | Path to SSL truststore |
| `truststore-password` | String | (none) | Truststore password |
| `description` | String | (none) | Description for logging |
| `request-handler` | String | `com.marklogic.handlers.LDAPRequestHandler` | Handler class |

**LDAP Modes:**
- `internal` - Route to in-memory directory server
- `single` - Connect to a single backend server
- `roundrobin` - Round-robin load balancing
- `failover` - Automatic failover
- `fastest` - Connect to fastest responding server
- `fewest` - Connect to server with fewest connections
- `roundrobindns` - DNS-based round-robin

### Server Sets

Groups of backend servers for load balancing or failover:

```properties
# Server set named "backend" with two servers
mleaproxy.ldap-sets.backend.servers=server1,server2
mleaproxy.ldap-sets.backend.mode=roundrobin
mleaproxy.ldap-sets.backend.secure=false

# Secure server set with JKS keystore
mleaproxy.ldap-sets.secure-backend.servers=adserver1,adserver2
mleaproxy.ldap-sets.secure-backend.mode=failover
mleaproxy.ldap-sets.secure-backend.secure=true
mleaproxy.ldap-sets.secure-backend.key-type=JKS
mleaproxy.ldap-sets.secure-backend.keystore=/path/to/keystore.jks
mleaproxy.ldap-sets.secure-backend.keystore-password=changeit

# Secure server set with PEM certificates
mleaproxy.ldap-sets.pem-backend.servers=server1
mleaproxy.ldap-sets.pem-backend.secure=true
mleaproxy.ldap-sets.pem-backend.key-type=PEM
mleaproxy.ldap-sets.pem-backend.key-path=/path/to/key.pem
mleaproxy.ldap-sets.pem-backend.cert-path=/path/to/cert.pem
mleaproxy.ldap-sets.pem-backend.ca-path=/path/to/ca.pem
```

**Server Set Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `servers` | List | (empty) | Comma-separated server names |
| `mode` | String | (none) | SINGLE, ROUNDROBIN, FAILOVER, FASTEST, FEWEST, ROUNDROBINDNS |
| `secure` | Boolean | `false` | Enable SSL/TLS |
| `key-type` | String | `JKS` | Certificate format: JKS, PKCS12, PEM |
| `keystore` | String | (none) | Path to JKS keystore |
| `keystore-password` | String | (none) | JKS keystore password |
| `pfx-path` | String | (none) | Path to PKCS12/PFX file |
| `pfx-password` | String | (none) | PKCS12/PFX password |
| `key-path` | String | (none) | Path to PEM private key |
| `cert-path` | String | (none) | Path to PEM certificate |
| `ca-path` | String | (none) | Path to PEM CA certificate |
| `truststore` | String | (none) | Path to truststore |
| `truststore-password` | String | (none) | Truststore password |

### Backend Servers

Backend LDAP/Active Directory server definitions:

```properties
# Backend server "server1"
mleaproxy.ldap-servers.server1.host=ldap.example.com
mleaproxy.ldap-servers.server1.port=389
mleaproxy.ldap-servers.server1.auth-type=simple

# Backend server "server2"
mleaproxy.ldap-servers.server2.host=ldap2.example.com
mleaproxy.ldap-servers.server2.port=389

# Active Directory servers
mleaproxy.ldap-servers.adserver1.host=ad.corp.com
mleaproxy.ldap-servers.adserver1.port=636

mleaproxy.ldap-servers.adserver2.host=ad2.corp.com
mleaproxy.ldap-servers.adserver2.port=636
```

**Backend Server Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `host` | String | (none) | Server hostname or IP |
| `port` | Integer | (required) | Server port |
| `auth-type` | String | `simple` | Authentication type |

### Request Processors

Custom authentication processors:

```properties
# JSON-based authentication processor
mleaproxy.request-processors.jsonauth.auth-class=com.marklogic.processors.JsonRequestProcessor
mleaproxy.request-processors.jsonauth.debug-level=INFO

# XML-based authentication processor
mleaproxy.request-processors.xmlauth.auth-class=com.marklogic.processors.XMLRequestProcessor
mleaproxy.request-processors.xmlauth.debug-level=DEBUG

# Proxy passthrough processor (no local auth)
mleaproxy.request-processors.passthrough.auth-class=com.marklogic.processors.ProxyRequestProcessor
mleaproxy.request-processors.passthrough.debug-level=WARN
```

**Request Processor Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `auth-class` | String | (none) | Fully qualified processor class name |
| `debug-level` | String | `INFO` | Logging level |
| `params` | List | (empty) | Additional processor parameters |

### Kerberos Configuration

Embedded Apache Kerby KDC settings:

```properties
# Enable Kerberos KDC
mleaproxy.kerberos.enabled=true
mleaproxy.kerberos.realm=CORP.EXAMPLE.COM
mleaproxy.kerberos.kdc-host=0.0.0.0
mleaproxy.kerberos.kdc-port=60088
mleaproxy.kerberos.admin-port=60749

# Principal import from LDAP directory
mleaproxy.kerberos.import-principals-from-ldap=true
mleaproxy.kerberos.ldap-base-dn=dc=MarkLogic,dc=Local

# Service principals (comma-separated)
mleaproxy.kerberos.service-principals=HTTP/localhost,ldap/localhost

# KDC settings
mleaproxy.kerberos.work-dir=./kerberos
mleaproxy.kerberos.debug=false
mleaproxy.kerberos.ticket-lifetime=36000
mleaproxy.kerberos.renewable-lifetime=604800
mleaproxy.kerberos.clock-skew=300
```

**Kerberos Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | Boolean | `false` | Enable embedded KDC |
| `realm` | String | `MARKLOGIC.LOCAL` | Kerberos realm (uppercase) |
| `kdc-host` | String | `localhost` | KDC bind address |
| `kdc-port` | Integer | `60088` | KDC port |
| `admin-port` | Integer | `60749` | KDC admin port |
| `import-principals-from-ldap` | Boolean | `true` | Import users from LDAP directory |
| `ldap-base-dn` | String | `dc=MarkLogic,dc=Local` | LDAP base DN for import |
| `service-principals` | List | `HTTP/localhost,ldap/localhost` | Service principals to create |
| `work-dir` | String | `./kerberos` | KDC data directory |
| `debug` | Boolean | `false` | Enable Kerberos debug output |
| `ticket-lifetime` | Integer | `36000` | Ticket lifetime (seconds) |
| `renewable-lifetime` | Integer | `604800` | Renewable lifetime (seconds) |
| `clock-skew` | Integer | `300` | Allowed clock skew (seconds) |

### SAML Listeners

SAML Identity Provider endpoint configurations:

```properties
# SAML listener named "default"
mleaproxy.saml-listeners.default.ip-address=0.0.0.0
mleaproxy.saml-listeners.default.port=8080
mleaproxy.saml-listeners.default.debug-level=INFO
mleaproxy.saml-listeners.default.description=SAML IdP endpoint
```

**SAML Listener Properties:**

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ip-address` | String | `0.0.0.0` | IP address to bind to |
| `port` | Integer | (required) | Port number |
| `debug-level` | String | `INFO` | Logging level |
| `description` | String | (none) | Description for logging |

### Spring Boot Settings

Standard Spring Boot configuration (no `mleaproxy.` prefix):

```properties
# Server port for HTTP endpoints (OAuth, SAML, Kerberos)
server.port=8080

# Logging configuration
logging.level.root=INFO
logging.level.com.marklogic=DEBUG
logging.file.name=mleaproxy.log

# SSL configuration for HTTPS
server.ssl.enabled=true
server.ssl.key-store=/path/to/keystore.jks
server.ssl.key-store-password=changeit
```

## Environment Variables

Spring Boot's relaxed binding supports environment variables. Convert property names to SCREAMING_SNAKE_CASE:

| Property | Environment Variable |
|----------|---------------------|
| `mleaproxy.ldap-debug` | `MLEAPROXY_LDAP_DEBUG` |
| `mleaproxy.saml-debug` | `MLEAPROXY_SAML_DEBUG` |
| `mleaproxy.ssl-verify-certificates` | `MLEAPROXY_SSL_VERIFY_CERTIFICATES` |
| `mleaproxy.saml-response-validity` | `MLEAPROXY_SAML_RESPONSE_VALIDITY` |
| `mleaproxy.kerberos.enabled` | `MLEAPROXY_KERBEROS_ENABLED` |
| `mleaproxy.kerberos.realm` | `MLEAPROXY_KERBEROS_REALM` |

**Example:**
```bash
export MLEAPROXY_LDAP_DEBUG=true
export MLEAPROXY_KERBEROS_ENABLED=true
export MLEAPROXY_KERBEROS_REALM=CORP.EXAMPLE.COM
java -jar target/mlesproxy-2.0.2.jar
```

## Command-Line Overrides

Spring Boot supports two methods for runtime property overrides:

### Using `--` Arguments (Recommended)

```bash
java -jar target/mlesproxy-2.0.2.jar --mleaproxy.ldap-debug=true
```

The `--` syntax is preferred because:
- Arguments appear after the JAR, making them easier to read
- They have the highest priority (override `-D` flags and environment variables)
- Better compatibility with container orchestration tools

### Using `-D` System Properties

```bash
java -Dmleaproxy.ldap-debug=true -jar target/mlesproxy-2.0.2.jar
```

### Combining Both

```bash
# -- takes precedence over -D
java -Dmleaproxy.ldap-debug=false \
     -jar target/mlesproxy-2.0.2.jar \
     --mleaproxy.ldap-debug=true
# Result: ldap-debug will be true
```

### Common Override Examples

**Enable debug logging:**
```bash
java -jar target/mlesproxy-2.0.2.jar \
    --mleaproxy.ldap-debug=true \
    --mleaproxy.saml-debug=true \
    --logging.level.com.marklogic=TRACE
```

**Change listener port:**
```bash
java -jar target/mlesproxy-2.0.2.jar \
    --mleaproxy.ldap-listeners.proxy.port=20389
```

**Switch backend server:**
```bash
java -jar target/mlesproxy-2.0.2.jar \
    --mleaproxy.ldap-servers.server1.host=backup-ldap.example.com
```

**Override web server port:**
```bash
java -jar target/mlesproxy-2.0.2.jar \
    --server.port=9090
```

**Multiple overrides:**
```bash
java -jar target/mlesproxy-2.0.2.jar \
    --mleaproxy.ldap-debug=true \
    --mleaproxy.ldap-listeners.proxy.port=20389 \
    --mleaproxy.ldap-listeners.proxy.ip-address=127.0.0.1 \
    --mleaproxy.ldap-servers.server1.host=ldap.example.com \
    --server.port=8443 \
    --logging.level.root=DEBUG
```

## Complete Configuration Examples

### Example 1: Development Environment

**mleaproxy.properties:**
```properties
# === Global Debug Settings ===
mleaproxy.ldap-debug=true
mleaproxy.saml-debug=true

# === In-Memory Directory Server ===
mleaproxy.directory-servers.dev.ip-address=0.0.0.0
mleaproxy.directory-servers.dev.port=60389
mleaproxy.directory-servers.dev.base-dn=dc=MarkLogic,dc=Local
mleaproxy.directory-servers.dev.admin-dn=cn=Directory Manager
mleaproxy.directory-servers.dev.admin-password=password

# === LDAP Listener (internal mode) ===
mleaproxy.ldap-listeners.proxy.ip-address=0.0.0.0
mleaproxy.ldap-listeners.proxy.port=10389
mleaproxy.ldap-listeners.proxy.secure=false
mleaproxy.ldap-listeners.proxy.ldap-mode=internal
mleaproxy.ldap-listeners.proxy.request-processor=jsonauth

# === Request Processor ===
mleaproxy.request-processors.jsonauth.auth-class=com.marklogic.processors.JsonRequestProcessor
mleaproxy.request-processors.jsonauth.debug-level=DEBUG

# === Development Logging ===
logging.level.root=DEBUG
logging.level.com.marklogic=TRACE
```

### Example 2: Production with Active Directory

**mleaproxy.properties:**
```properties
# === Global Settings ===
mleaproxy.ldap-debug=false
mleaproxy.saml-debug=false
mleaproxy.ssl-verify-certificates=true

# === Custom SAML Certificates ===
mleaproxy.saml-ca-path=/etc/mleaproxy/certs/saml-cert.pem
mleaproxy.saml-key-path=/etc/mleaproxy/certs/saml-key.pem
mleaproxy.saml-response-validity=300

# === LDAP Listener with Failover ===
mleaproxy.ldap-listeners.proxy.ip-address=0.0.0.0
mleaproxy.ldap-listeners.proxy.port=10636
mleaproxy.ldap-listeners.proxy.secure=true
mleaproxy.ldap-listeners.proxy.ldap-mode=failover
mleaproxy.ldap-listeners.proxy.ldap-sets=ad-servers
mleaproxy.ldap-listeners.proxy.request-processor=jsonauth
mleaproxy.ldap-listeners.proxy.keystore=/etc/mleaproxy/certs/listener.jks
mleaproxy.ldap-listeners.proxy.keystore-password=changeit

# === AD Server Set ===
mleaproxy.ldap-sets.ad-servers.servers=ad1,ad2
mleaproxy.ldap-sets.ad-servers.mode=failover
mleaproxy.ldap-sets.ad-servers.secure=true
mleaproxy.ldap-sets.ad-servers.key-type=JKS
mleaproxy.ldap-sets.ad-servers.keystore=/etc/mleaproxy/certs/ad-client.jks
mleaproxy.ldap-sets.ad-servers.keystore-password=changeit

# === Active Directory Servers ===
mleaproxy.ldap-servers.ad1.host=ad1.corp.example.com
mleaproxy.ldap-servers.ad1.port=636

mleaproxy.ldap-servers.ad2.host=ad2.corp.example.com
mleaproxy.ldap-servers.ad2.port=636

# === Request Processor ===
mleaproxy.request-processors.jsonauth.auth-class=com.marklogic.processors.JsonRequestProcessor
mleaproxy.request-processors.jsonauth.debug-level=WARN

# === Production Logging ===
logging.level.root=WARN
logging.file.name=/var/log/mleaproxy/application.log
server.port=8443
server.ssl.enabled=true
```

### Example 3: Load-Balanced LDAP Cluster

**mleaproxy.properties:**
```properties
# === Debug ===
mleaproxy.ldap-debug=true

# === Round-Robin Listener ===
mleaproxy.ldap-listeners.balanced.ip-address=0.0.0.0
mleaproxy.ldap-listeners.balanced.port=10389
mleaproxy.ldap-listeners.balanced.secure=false
mleaproxy.ldap-listeners.balanced.ldap-mode=roundrobin
mleaproxy.ldap-listeners.balanced.ldap-sets=cluster
mleaproxy.ldap-listeners.balanced.request-processor=passthrough

# === Server Cluster ===
mleaproxy.ldap-sets.cluster.servers=node1,node2,node3
mleaproxy.ldap-sets.cluster.mode=roundrobin
mleaproxy.ldap-sets.cluster.secure=false

# === Cluster Nodes ===
mleaproxy.ldap-servers.node1.host=ldap-node1.example.com
mleaproxy.ldap-servers.node1.port=389

mleaproxy.ldap-servers.node2.host=ldap-node2.example.com
mleaproxy.ldap-servers.node2.port=389

mleaproxy.ldap-servers.node3.host=ldap-node3.example.com
mleaproxy.ldap-servers.node3.port=389

# === Passthrough Processor ===
mleaproxy.request-processors.passthrough.auth-class=com.marklogic.processors.ProxyRequestProcessor
mleaproxy.request-processors.passthrough.debug-level=INFO
```

### Example 4: Kerberos with LDAP Backend

**mleaproxy.properties:**
```properties
# === Kerberos KDC ===
mleaproxy.kerberos.enabled=true
mleaproxy.kerberos.realm=CORP.EXAMPLE.COM
mleaproxy.kerberos.kdc-host=0.0.0.0
mleaproxy.kerberos.kdc-port=60088
mleaproxy.kerberos.admin-port=60749
mleaproxy.kerberos.import-principals-from-ldap=true
mleaproxy.kerberos.ldap-base-dn=dc=MarkLogic,dc=Local
mleaproxy.kerberos.service-principals=HTTP/localhost,ldap/localhost
mleaproxy.kerberos.debug=false

# === In-Memory Directory Server ===
mleaproxy.directory-servers.dev.ip-address=0.0.0.0
mleaproxy.directory-servers.dev.port=60389
mleaproxy.directory-servers.dev.base-dn=dc=MarkLogic,dc=Local
mleaproxy.directory-servers.dev.admin-dn=cn=Directory Manager
mleaproxy.directory-servers.dev.admin-password=password

# === LDAP Listener ===
mleaproxy.ldap-listeners.krb.ip-address=0.0.0.0
mleaproxy.ldap-listeners.krb.port=10389
mleaproxy.ldap-listeners.krb.secure=false
mleaproxy.ldap-listeners.krb.ldap-mode=single
mleaproxy.ldap-listeners.krb.ldap-sets=backend
mleaproxy.ldap-listeners.krb.request-processor=jsonauth

# === Backend Server Set ===
mleaproxy.ldap-sets.backend.servers=ldap1
mleaproxy.ldap-sets.backend.mode=single
mleaproxy.ldap-sets.backend.secure=false

# === Backend Server ===
mleaproxy.ldap-servers.ldap1.host=ldap.corp.example.com
mleaproxy.ldap-servers.ldap1.port=389

# === Request Processor ===
mleaproxy.request-processors.jsonauth.auth-class=com.marklogic.processors.JsonRequestProcessor
mleaproxy.request-processors.jsonauth.debug-level=INFO
```

## Using Modular Configuration Files

Split configuration across multiple files for better organization:

**ldap.properties:**
```properties
mleaproxy.ldap-debug=true

mleaproxy.ldap-listeners.proxy.ip-address=0.0.0.0
mleaproxy.ldap-listeners.proxy.port=10389
mleaproxy.ldap-listeners.proxy.secure=false
mleaproxy.ldap-listeners.proxy.ldap-mode=single
mleaproxy.ldap-listeners.proxy.ldap-sets=backend
mleaproxy.ldap-listeners.proxy.request-processor=jsonauth

mleaproxy.ldap-sets.backend.servers=server1
mleaproxy.ldap-sets.backend.mode=single
mleaproxy.ldap-sets.backend.secure=false

mleaproxy.ldap-servers.server1.host=ldap.example.com
mleaproxy.ldap-servers.server1.port=389

mleaproxy.request-processors.jsonauth.auth-class=com.marklogic.processors.JsonRequestProcessor
mleaproxy.request-processors.jsonauth.debug-level=INFO
```

**saml.properties:**
```properties
mleaproxy.saml-debug=true
mleaproxy.saml-ca-path=/etc/mleaproxy/certs/saml-cert.pem
mleaproxy.saml-key-path=/etc/mleaproxy/certs/saml-key.pem
mleaproxy.saml-response-validity=300
```

**directory.properties:**
```properties
mleaproxy.directory-servers.dev.ip-address=0.0.0.0
mleaproxy.directory-servers.dev.port=60389
mleaproxy.directory-servers.dev.base-dn=dc=MarkLogic,dc=Local
mleaproxy.directory-servers.dev.admin-dn=cn=Directory Manager
mleaproxy.directory-servers.dev.admin-password=password
```

**kerberos.properties:**
```properties
mleaproxy.kerberos.enabled=true
mleaproxy.kerberos.realm=CORP.EXAMPLE.COM
mleaproxy.kerberos.kdc-host=0.0.0.0
mleaproxy.kerberos.kdc-port=60088
```

## Container Deployment

### Docker

**Dockerfile:**
```dockerfile
FROM eclipse-temurin:21-jre
COPY target/mlesproxy-2.0.2.jar /app/mlesproxy.jar
WORKDIR /app
ENTRYPOINT ["java", "-jar", "mlesproxy.jar"]
```

**Run with environment variables:**
```bash
docker run -p 8080:8080 -p 10389:10389 \
    -e MLEAPROXY_LDAP_DEBUG=true \
    -e MLEAPROXY_LDAP_LISTENERS_PROXY_PORT=10389 \
    mleaproxy
```

**Run with command-line arguments:**
```bash
docker run -p 8080:8080 -p 10389:10389 mleaproxy \
    --mleaproxy.ldap-debug=true \
    --mleaproxy.ldap-listeners.proxy.port=10389 \
    --mleaproxy.ldap-servers.server1.host=ldap.example.com
```

### Kubernetes

**ConfigMap:**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: mleaproxy-config
data:
  application.properties: |
    mleaproxy.ldap-debug=false
    mleaproxy.ldap-listeners.proxy.ip-address=0.0.0.0
    mleaproxy.ldap-listeners.proxy.port=10389
    mleaproxy.ldap-listeners.proxy.ldap-mode=failover
    mleaproxy.ldap-listeners.proxy.ldap-sets=backend
    mleaproxy.ldap-sets.backend.servers=ldap1,ldap2
    mleaproxy.ldap-sets.backend.mode=failover
    mleaproxy.ldap-servers.ldap1.host=ldap1.example.com
    mleaproxy.ldap-servers.ldap1.port=389
    mleaproxy.ldap-servers.ldap2.host=ldap2.example.com
    mleaproxy.ldap-servers.ldap2.port=389
```

**Deployment:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mleaproxy
spec:
  replicas: 2
  selector:
    matchLabels:
      app: mleaproxy
  template:
    metadata:
      labels:
        app: mleaproxy
    spec:
      containers:
      - name: mleaproxy
        image: mleaproxy:2.0.2
        ports:
        - containerPort: 8080
        - containerPort: 10389
        volumeMounts:
        - name: config
          mountPath: /app/application.properties
          subPath: application.properties
      volumes:
      - name: config
        configMap:
          name: mleaproxy-config
```

## Troubleshooting

### View Loaded Configuration

Enable debug logging to see configuration values:

```bash
java -jar target/mlesproxy-2.0.2.jar \
    --logging.level.org.springframework.boot.context.properties=DEBUG
```

### Property Not Taking Effect

1. **Check property name spelling** - use kebab-case: `ldap-debug` not `ldapDebug`
2. **Verify the `mleaproxy.` prefix** is present for all mleaproxy properties
3. **Check loading priority** - later sources override earlier ones
4. **Use `--` arguments** for guaranteed highest priority
5. **Check for typos in map keys** - `ldap-listeners.proxy.port` vs `ldap-listeners.proxey.port`

### SSL Certificate Issues

```bash
java -jar target/mlesproxy-2.0.2.jar \
    --mleaproxy.ssl-verify-certificates=false \
    --mleaproxy.ldap-debug=true
```

### Connection Issues

Enable debug logging and check backend server configuration:

```bash
java -jar target/mlesproxy-2.0.2.jar \
    --mleaproxy.ldap-debug=true \
    --logging.level.com.marklogic=TRACE
```

### Verify Configuration is Loaded

Check startup logs for configuration summary:

```bash
java -jar target/mlesproxy-2.0.2.jar 2>&1 | grep -i "listener\|server\|directory"
```

## Best Practices

1. **Use kebab-case** for all property names (`ldap-debug` not `ldapDebug`)
2. **Use `--` arguments** for runtime overrides (highest priority, cleaner syntax)
3. **Keep sensitive data** in separate files with restricted permissions
4. **Use modular files** for organized, maintainable configuration
5. **Version control base configs** but exclude environment-specific overrides
6. **Test configuration** in development before production deployment
7. **Use descriptive names** for listeners, sets, and servers
8. **Enable debug logging** when troubleshooting connectivity issues
9. **Use environment variables** in containerized deployments
10. **Document your configuration** with comments explaining each section
