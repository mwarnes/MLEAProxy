
# 🚀 MLEAProxy

[![Java](https://img.shields.io/badge/Java-21+-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36?style=for-the-badge&logo=apache-maven)](https://maven.apache.org/)
[![Tests](https://img.shields.io/badge/Tests-107-brightgreen?style=for-the-badge)](./TEST_SUITE_SUMMARY.md)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

> **A Multi-Protocol Authentication Proxy & Development Server** 🔐
>
> Supporting **LDAP/LDAPS**, **OAuth 2.0**, and **SAML 2.0** for MarkLogic Server and beyond

---

## 📚 Documentation Index

| Document | Description | Updated |
|----------|-------------|---------|
| **[README.md](./README.md)** | This file - General overview | 2025 |
| **[docs/user/LDAP_GUIDE.md](./docs/user/LDAP_GUIDE.md)** | Complete LDAP/LDAPS guide (includes in-memory server) | 2025 |
| **[docs/user/OAUTH_GUIDE.md](./docs/user/OAUTH_GUIDE.md)** | Complete OAuth 2.0 guide | 2025 |
| **[docs/user/SAML_GUIDE.md](./docs/user/SAML_GUIDE.md)** | Complete SAML 2.0 guide | 2025 |
| **[docs/user/KERBEROS_GUIDE.md](./docs/user/KERBEROS_GUIDE.md)** | Complete Kerberos implementation | 2025 |
| **[docs/user/TESTING_GUIDE.md](./docs/user/TESTING_GUIDE.md)** | Testing procedures | 2025 |
| **[docs/user/CONFIGURATION_GUIDE.md](./docs/user/CONFIGURATION_GUIDE.md)** | Configuration reference | 2025 |

---

## ✨ Overview

**MLEAProxy** is a comprehensive authentication proxy and development server that supports multiple authentication protocols. Originally designed for MarkLogic Server external authentication diagnostics, it has evolved into a full-featured authentication platform supporting LDAP, OAuth 2.0, and SAML 2.0.

### 🎯 Key Features

- **LDAP/LDAPS**: Proxy mode, load balancing, standalone server, in-memory or JSON directory.
- **OAuth 2.0**: JWT token generation, JWKS endpoint, RFC 8414 metadata, 3-tier role resolution, refresh tokens
- **SAML 2.0**: Full IdP implementation, metadata endpoint, digital signatures, 3-tier role resolution
- **Kerberos**: Embedded KDC, SPNEGO authentication, OAuth/SAML protocol bridges, LDAP integration (Phase 4)
- **Modern Stack**: Java 21, Spring Boot 3.3.5, Jackson JSON processing, Apache Kerby 2.0.3
- **Extensive Documentation**: Separate protocol-specific guides

<!-- ### 🆕 What's New in 2025

- **Kerberos Support (Phase 4)**: Complete Kerberos implementation with OAuth/SAML bridges
  - Embedded Kerberos KDC (Apache Kerby 2.0.3)
  - SPNEGO HTTP authentication
  - OAuth JWT token generation from Kerberos tickets
  - SAML assertion generation from Kerberos tickets
  - LDAP role integration for group-based authorization
  - OAuth 2.0 refresh token support with token rotation
- **JSON User Repository**: Converted from XML to JSON format for better performance
- **3-Tier Role Resolution**: Sophisticated role assignment for OAuth and SAML:
  1. Request parameter roles (explicit override - highest priority)
  2. JSON user repository roles (user-specific)
  3. Default configuration roles (fallback - lowest priority)
- **Enhanced Testing**: Expanded test suite to 107 tests with comprehensive coverage
- **Improved Documentation**: Reorganized into protocol-specific guides -->

### ⚠️ Important Notice

> **Development Tool**: While MLEAProxy is feature-rich and secure, it was primarily designed as a diagnostic and development tool. Use in production environments at your own discretion and ensure thorough testing for your specific use case.

---

## 📋 Table of Contents

- [Quick Start](#quick-start)
- [Installation](#installation)
- [Endpoint Reference](#endpoint-reference)
- [Protocol Guides](#protocol-guides)
- [User Repository](#user-repository)
- [Development](#development)
- [Testing](#testing)
- [Documentation](#documentation)
- [License](#license)

---

## 🚀 Quick Start

### Prerequisites

- ☕ **Java 21+** (OpenJDK LTS recommended)
- 🔨 **Maven 3.9+** (for building from source)

### Run with Defaults

```bash
# Run with default configuration (standalone JSON LDAP server)
java -jar mleaproxy.jar

# All services start automatically:
# - LDAP Proxy server on ldap://localhost:10389
# - LDAP Directory server on ldap://localhost:60389
# - OAuth endpoints on http://localhost:8080/oauth/*
# - SAML endpoints on http://localhost:8080/saml/*
```

### Quick Tests

```bash
# Test LDAP (Direct Server - works without backend)
ldapsearch -H ldap://localhost:60389 \
  -D "cn=Directory Manager" \
  -w password \
  -b "dc=MarkLogic,dc=Local"

# Test OAuth
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password&username=admin&password=admin" \
  -d "client_id=marklogic&client_secret=secret"

# Test SAML
curl http://localhost:8080/saml/idp-metadata
```

---

## 📦 Installation

### Option 1: Pre-built JAR

```bash
# Download from releases
wget https://github.com/mwarnes/MLEAProxy/releases/latest/download/mleaproxy.jar

# Run
java -jar mleaproxy.jar

# Run with custom properties
java -Dmleaproxy.properties=./my-config.properties -jar mleaproxy.jar
```

### Option 2: Build from Source

```bash
# Clone repository
git clone https://github.com/mwarnes/MLEAProxy.git
cd MLEAProxy

# Build with Maven
./build.sh clean package

# Run
java -Dmleaproxy.properties=./mleaproxy.properties \
  -jar target/mlesproxy-2.0.0.jar
```

### Option 3: Development Mode

```bash
# Load development aliases
source dev-aliases.sh

# Run development cycle: clean → build → run
mlproxy-dev
```

### System Requirements

| Component | Requirement | Notes |
|-----------|-------------|-------|
| **Java Runtime** | OpenJDK 21+ | LTS version recommended |
| **Memory** | 512MB+ | Depends on load |
| **Network** | TCP ports | Defaults: 10389,60389 (LDAP), 8080 (HTTP - SAML/OAuth) |
| **Disk Space** | 100MB+ | For JAR and logs |

---

## 🌐 Endpoint Reference

### Quick Reference Table

| Protocol | Endpoint | Method | Purpose | Port |
|----------|----------|--------|---------|------|
| **LDAP** | `ldap://localhost:10389` | LDAP | LDAP proxy (requires backend) | 10389 |
| **LDAP** | `ldap://localhost:60389` | LDAP | Direct standalone server | 60389 |
| **OAuth** | `/oauth/token` | POST | Generate JWT tokens | 8080 |
| **OAuth** | `/oauth/token-from-kerberos` | POST | Kerberos → OAuth bridge | 8080 |
| **OAuth** | `/oauth/refresh` | POST | Refresh access tokens | 8080 |
| **OAuth** | `/oauth/jwks` | GET | Public key discovery | 8080 |
| **OAuth** | `/oauth/.well-known/config` | GET | Server metadata (RFC 8414) | 8080 |
| **SAML** | `/saml/auth` | GET | SAML authentication (SSO) | 8080 |
| **SAML** | `/saml/assertion-from-kerberos` | POST | Kerberos → SAML bridge | 8080 |
| **SAML** | `/saml/idp-metadata` | GET | IdP metadata XML | 8080 |
| **Kerberos** | N/A | Kerberos | Embedded KDC & SPNEGO | 88/HTTP |

> **Note**: OAuth and SAML share the same Spring Boot web server port (default: 8080). They are distinguished by URL path prefixes (`/oauth/*` vs `/saml/*`), not separate ports. Configure via `server.port` property.

### LDAP Endpoints

**LDAP Proxy Server**

- Protocol: LDAP/LDAPS
- Proxy Port: 10389 (configurable)
- Standalone Port: 10389 (configurable)
- Features: Standalone server, proxy mode, load balancing, LDAPS support
- Documentation: See [LDAP_GUIDE.md](./LDAP_GUIDE.md)

```bash
# Example LDAP connection
ldapsearch -H ldap://localhost:10389 \
  -D "cn=admin,dc=example,dc=com" \
  -w password \
  -b "dc=example,dc=com"
```

### OAuth 2.0 Endpoints

**Token Endpoint** (`/oauth/token`)

Generate JWT access tokens with user roles:

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"
```

**JWKS Endpoint** (`/oauth/jwks`)

Retrieve public keys for JWT verification:

```bash
curl http://localhost:8080/oauth/jwks
```

**Well-Known Config** (`/oauth/.well-known/config`)

OAuth 2.0 authorization server metadata:

```bash
curl http://localhost:8080/oauth/.well-known/config
```

Documentation: See [OAUTH_GUIDE.md](./OAUTH_GUIDE.md)

### SAML 2.0 Endpoints

**Authentication Endpoint** (`/saml/auth`)

SAML 2.0 Identity Provider authentication:

```bash
curl "http://localhost:8080/saml/auth?SAMLRequest=<base64>&RelayState=<state>"
```

**IdP Metadata Endpoint** (`/saml/idp-metadata`)

SAML 2.0 Identity Provider metadata:

```bash
curl http://localhost:8080/saml/idp-metadata
```

Documentation: See [SAML_GUIDE.md](./SAML_GUIDE.md)

---

## 📚 Protocol Guides

Complete documentation for each protocol is now organized in the `docs/user/` folder:

### LDAP/LDAPS Guide

**[docs/user/LDAP_GUIDE.md](./docs/user/LDAP_GUIDE.md)** - Complete LDAP functionality

- Configuration (servers, server sets, listeners, processors, in-memory directories)
- Standalone JSON LDAP server
- In-memory directory servers for testing
- Proxy mode and load balancing
- Security features (LDAPS, injection protection)
- Multiple usage scenarios with examples
- Troubleshooting guide

### OAuth 2.0 Guide

**[docs/user/OAUTH_GUIDE.md](./docs/user/OAUTH_GUIDE.md)** - Complete OAuth 2.0 functionality

- All OAuth endpoints (token, JWKS, well-known config)
- Configuration and user repository setup
- 3-tier role resolution system 
- Client integration examples (Node.js, Python, Java)
- Security best practices
- Complete API reference

### SAML 2.0 Guide

**[docs/user/SAML_GUIDE.md](./docs/user/SAML_GUIDE.md)** - Complete SAML 2.0 functionality

- All SAML endpoints (auth, IdP metadata)
- Configuration and certificate setup
- 3-tier role resolution system  
- Service Provider integration (Okta, Azure AD)
- Security and certificate management
- Complete API reference

### Kerberos Guide

**[docs/user/KERBEROS_GUIDE.md](./docs/user/KERBEROS_GUIDE.md)** - Complete Kerberos functionality

- **All Phases Integrated** - Phase 2 (SPNEGO), Phase 3 (OAuth/SAML bridges), and Phase 4 (LDAP integration & refresh tokens)
- Embedded Kerberos KDC setup and configuration
- SPNEGO HTTP authentication with detailed examples
- Kerberos → OAuth JWT bridge with refresh token support
- Kerberos → SAML assertion bridge
- LDAP role integration (query in-memory LDAP for groups)
- OAuth 2.0 refresh tokens with token rotation
- Multi-tier role resolution (JSON → LDAP → defaults)
- Principal and realm configuration
- Keytab management and security
- Complete API reference and testing examples

---

## 👥 User Repository

### JSON User Repository 

MLEAProxy uses a modern JSON-based user repository for storing user credentials and roles.

**Location:** `src/main/resources/users.json`

**Example Structure:**

```json
{
  "baseDN": "ou=users,dc=marklogic,dc=local",
  "users": [
    {
      "dn": "cn=manager",
      "sAMAccountName": "manager",
      "userPassword": "password",
      "displayName": "System Manager",
      "mail": "manager@example.com",
      "roles": ["admin", "manager"]
    },
    {
      "dn": "cn=admin",
      "sAMAccountName": "admin",
      "userPassword": "admin",
      "displayName": "Administrator",
      "mail": "admin@example.com",
      "roles": ["admin", "user"]
    },
    {
      "dn": "cn=testuser",
      "sAMAccountName": "testuser",
      "userPassword": "password",
      "displayName": "Test User",
      "mail": "testuser@example.com",
      "roles": ["user"]
    }
  ]
}
```

### Features

- **JSON Format**: Modern, readable, industry-standard format
- **Jackson Processing**: Fast, efficient JSON parsing with Spring Boot
- **Case-Insensitive Lookup**: Username searches are case-insensitive
- **Role Support**: Users can have multiple roles for OAuth/SAML
- **LDAP Attributes**: Support for memberOf and other LDAP attributes
- **Type Safety**: Strongly-typed Java objects

### 3-Tier Role Resolution 

Both OAuth and SAML use the same priority system for role assignment:

1. **Request Parameter Roles** (Highest Priority)
   - Explicitly specified in the request (`roles` parameter)
   - Overrides all other sources
   - Use case: Testing, temporary elevation

2. **JSON User Repository Roles** (Medium Priority)
   - Defined in users.json for each user
   - Used when no explicit roles provided
   - Use case: Normal authentication, production

3. **Default Configuration Roles** (Lowest Priority)
   - Fallback when user not found in repository
   - Configured via properties: `oauth.default.roles` / `saml.default.roles`
   - Use case: Guest access, new users

**Example Configuration:**

```properties
# oauth.properties
oauth.default.roles=user,guest

# saml.properties
saml.default.roles=user,guest
```

---

## 🔧 Development

### Building from Source

```bash
# Clone repository
git clone https://github.com/mwarnes/MLEAProxy.git
cd MLEAProxy

# Build with Maven
mvn clean package

# Run tests
mvn test

# Build without tests (faster)
mvn clean package -DskipTests

# Generate Javadoc
mvn javadoc:javadoc
```

### Project Structure

```
MLEAProxy/
├── src/
│   ├── main/
│   │   ├── java/com/marklogic/
│   │   │   ├── handlers/            # LDAP and HTTP handlers
│   │   │   ├── processors/          # Request processors
│   │   │   ├── repository/          # JSON user repository (New 2025)
│   │   │   ├── security/            # Security utilities
│   │   │   └── MLEAProxy.java       # Main application
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── mleaproxy.properties # LDAP config
│   │       ├── oauth.properties     # OAuth config
│   │       ├── saml.properties      # SAML config
│   │       └── users.json           # User repository (New 2025)
│   └── test/java/com/marklogic/
│       ├── handlers/                # Handler tests
│       ├── repository/              # Repository tests (New 2025)
│       └── MLEAProxyIntegrationTest.java
├── http_client/                     # REST client test files
├── pom.xml
└── README.md
```

### Development Mode

```bash
# Using Maven Spring Boot plugin
mvn spring-boot:run

# With debug enabled
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"

# With specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Development Aliases

```bash
# Load aliases
source dev-aliases.sh

# Available commands
mlproxy-build    # Build project
mlproxy-test     # Run tests
mlproxy-run      # Run application
mlproxy-dev      # Complete dev cycle: clean → build → run
```

---

## 🧪 Testing

### Test Suite

MLEAProxy includes comprehensive test coverage with 107 passing tests:

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=OAuthTokenHandlerTest

# Run with coverage report
mvn clean test jacoco:report
```

### Test Results Summary

```
Tests run: 107, Failures: 0, Errors: 0, Skipped: 6

Test Breakdown:
- JsonUserRepositoryTest: 14 tests
- LDAPRequestHandlerTest: 3 tests
- OAuthTokenHandlerTest: 15 tests
- OAuthTokenHandlerEdgeCaseTest: 15 tests
- SAMLAuthHandlerTest: 8 tests
- SAMLAuthHandlerEdgeCaseTest: 17 tests
- XmlUserRepositoryTest: 14 tests
- MLEAProxyIntegrationTest: 10 tests (6 skipped)
```

### Manual Testing

Use the provided REST client files in `http_client/`:

- `oauth.rest` - OAuth token generation and JWKS testing
- `auth.rest` - SAML authentication testing
- `wrapassertion.rest` - SAML assertion testing

### Documentation

See [TESTING_GUIDE.md](./TESTING_GUIDE.md) for complete testing procedures.

---

## 📚 Documentation

All documentation has been organized into a structured folder system for easier navigation.

### 📘 [User Documentation](./docs/user/)

Complete guides for configuration, usage, and integration:

- **[Configuration Guide](./docs/user/CONFIGURATION_GUIDE.md)** - Complete configuration reference
- **[LDAP Guide](./docs/user/LDAP_GUIDE.md)** - LDAP/LDAPS proxy and server guide
- **[OAuth Guide](./docs/user/OAUTH_GUIDE.md)** - OAuth 2.0 JWT token generation
- **[SAML Guide](./docs/user/SAML_GUIDE.md)** - SAML 2.0 Identity Provider guide
- **[Kerberos Guide](./docs/user/KERBEROS_GUIDE.md)** - Complete Kerberos implementation
- **[Testing Guide](./docs/user/TESTING_GUIDE.md)** - Testing procedures and examples
- **[MarkLogic SAML Configuration](./docs/user/MarkLogic-SAML-configuration.md)** - Configure MarkLogic with SAML IdP

👉 **[Browse All User Documentation →](./docs/user/)**

### 🔧 [Developer Documentation](./docs/developer/)

Technical implementation details, build notes, and development history:

- **[Kerberos Phases](./docs/developer/KERBEROS_PHASE1_COMPLETE.md)** - Complete implementation phases
- **[Code Review 2025](./docs/developer/CODE_REVIEW_2025.md)** - Code quality review
- **[Test Suite Summary](./docs/developer/TEST_SUITE_SUMMARY.md)** - Test coverage details
- **[JJWT Migration](./docs/developer/JJWT_MIGRATION_COMPLETE.md)** - JJWT 0.12.6 migration notes
- **[Build Process](./docs/developer/build.md)** - Build documentation

👉 **[Browse All Developer Documentation →](./docs/developer/)**

### 📚 Quick Reference

| Protocol | User Guide | Quick Reference |
|----------|------------|-----------------|
| **LDAP** | [LDAP Guide](./docs/user/LDAP_GUIDE.md) | In-memory server included |
| **OAuth 2.0** | [OAuth Guide](./docs/user/OAUTH_GUIDE.md) | [Discovery Endpoints](./docs/user/DISCOVERY_ENDPOINTS_QUICK_REF.md) |
| **SAML 2.0** | [SAML Guide](./docs/user/SAML_GUIDE.md) | [MarkLogic Config](./docs/user/MarkLogic-SAML-configuration.md) |
| **Kerberos** | [Kerberos Guide](./docs/user/KERBEROS_GUIDE.md) | All phases integrated |

### Standards and Specifications

#### LDAP/LDAPS

- [RFC 4511](https://tools.ietf.org/html/rfc4511) - LDAP: The Protocol
- [RFC 4513](https://tools.ietf.org/html/rfc4513) - LDAP: Authentication Methods and Security

#### OAuth 2.0

- [RFC 6749](https://tools.ietf.org/html/rfc6749) - OAuth 2.0 Authorization Framework
- [RFC 7517](https://tools.ietf.org/html/rfc7517) - JSON Web Key (JWK)
- [RFC 7519](https://tools.ietf.org/html/rfc7519) - JSON Web Token (JWT)
- [RFC 8414](https://tools.ietf.org/html/rfc8414) - OAuth 2.0 Authorization Server Metadata

#### SAML 2.0

- [OASIS SAML 2.0](http://docs.oasis-open.org/security/saml/v2.0/) - Security Assertion Markup Language
- [SAML 2.0 Metadata](http://docs.oasis-open.org/security/saml/v2.0/saml-metadata-2.0-os.pdf) - Metadata Specification
- [SAML 2.0 Bindings](http://docs.oasis-open.org/security/saml/v2.0/saml-bindings-2.0-os.pdf) - Protocol Bindings

#### Kerberos

- [RFC 4120](https://tools.ietf.org/html/rfc4120) - Kerberos V5 Protocol
- [RFC 4559](https://tools.ietf.org/html/rfc4559) - SPNEGO-based Kerberos and NTLM HTTP Authentication
- [RFC 7617](https://tools.ietf.org/html/rfc7617) - HTTP Basic Authentication (comparison)
- [Apache Kerby](https://directory.apache.org/kerby/) - Java Kerberos implementation

---

## 🔧 Configuration Override Reference

### System Property Overrides

MLEAProxy supports extensive configuration through system properties using `-D` flags. All configuration parameters can be overridden at runtime without modifying property files.

#### Configuration Loading Priority

Properties are loaded in this order (later sources override earlier ones):

1. `classpath:mleaproxy.properties` (bundled in JAR - lowest priority)
2. `/etc/mleaproxy.properties` (system-wide)
3. `${HOME}/mleaproxy.properties` (user-specific)
4. `./mleaproxy.properties` (current directory)
5. `./ldap.properties`, `./saml.properties`, `./oauth.properties`, `./directory.properties`
6. **System properties** via `-D` flags (highest priority)

#### Spring Boot Application Properties

```bash
# Server configuration
-Dserver.port=8443                                    # Web server port (default: 8080)
-Dserver.ssl.enabled=true                            # Enable SSL/HTTPS
-Dserver.ssl.key-store=/path/to/keystore.jks         # SSL keystore location
-Dserver.ssl.key-store-password=changeme             # SSL keystore password

# Logging configuration
-Dlogging.level.root=DEBUG                           # Root log level (INFO|DEBUG|WARN|ERROR)
-Dlogging.level.com.marklogic=TRACE                  # Package-specific logging
-Dlogging.file=/var/log/mleaproxy/application.log    # Log file location
-Dlogging.pattern.console="%d{HH:mm:ss} %-5level %logger{36} - %msg%n"

# Users JSON repository
-Dusers.json.path=/path/to/users.json                # Custom users JSON file location
```

#### OAuth Configuration Properties

```bash
# OAuth server configuration
-Doauth.server.base.url=http://my-server:8080        # OAuth server base URL
-Doauth.token.validity=7200                          # Token validity in seconds (default: 3600)
-Doauth.keypath=/path/to/privkey.pem                 # RSA private key path
-Doauth.default.roles=user,reader                    # Default roles for unknown users

# OAuth advanced settings
-Doauth.issuer=http://my-server:8080                 # JWT issuer claim
-Doauth.audience=marklogic                           # JWT audience claim
-Doauth.client.id=my-client                          # OAuth client ID
-Doauth.client.secret=my-secret                      # OAuth client secret
```

#### SAML Configuration Properties

```bash
# SAML debug and certificates
-Dsaml.debug=true                                    # Enable SAML debug logging
-Dsaml.capath=/path/to/certificate.pem              # SAML certificate path
-Dsaml.keypath=/path/to/rsakey.pem                   # SAML private key path
-Dsaml.response.validity=600                         # Response validity in seconds (default: 300)
-Dsaml.default.roles=user,reader                     # Default roles for unknown users

# SAML IdP configuration
-Dsaml.idp.entity.id=http://my-server:8080/saml/idp # IdP entity ID
-Dsaml.idp.sso.url=http://my-server:8080/saml/auth  # SSO endpoint URL
```

#### LDAP Proxy Configuration Properties

```bash
# LDAP debug logging
-Dldap.debug=true                                    # Enable detailed LDAP logging

# LDAP listeners to start
-Dldaplisteners=proxy,secure                         # Comma-separated listener names

# LDAP listener configuration (replace 'proxy' with your listener name)
-Dldaplistener.proxy.ipaddress=0.0.0.0              # Listener IP address
-Dldaplistener.proxy.port=20389                     # Listener port
-Dldaplistener.proxy.secure=true                    # Enable SSL/TLS
-Dldaplistener.proxy.debuglevel=DEBUG               # Listener debug level
-Dldaplistener.proxy.ldapset=set1                   # Backend server set
-Dldaplistener.proxy.ldapmode=single                # Mode: single|failover|roundrobin
-Dldaplistener.proxy.requestProcessor=ldapproxy     # Request processor name
-Dldaplistener.proxy.description="LDAP Proxy"       # Listener description

# LDAP listener SSL configuration
-Dldaplistener.proxy.keystore=/path/to/keystore.jks # SSL keystore
-Dldaplistener.proxy.keystorepasswd=changeme        # Keystore password
-Dldaplistener.proxy.truststore=/path/to/trust.jks  # SSL truststore
-Dldaplistener.proxy.truststorepasswd=changeme      # Truststore password
```

#### Backend LDAP Server Configuration

```bash
# LDAP server configuration (replace 'server1' with your server name)
-Dldapserver.server1.host=ldap.company.com          # LDAP server hostname
-Dldapserver.server1.port=636                       # LDAP server port
-Dldapserver.server1.authtype=simple                # Authentication type

# LDAP server sets for load balancing/failover
-Dldapset.set1.servers=server1,server2              # Comma-separated server list
-Dldapset.set1.mode=roundrobin                      # Mode: single|failover|roundrobin
-Dldapset.set1.secure=true                          # Use SSL/TLS to backend
-Dldapset.set1.keytype=PEM                          # Certificate type (PEM|JKS|PFX)

# SSL/TLS configuration for backend connections
-Dldapset.set1.keypath=/path/to/client-key.pem      # Client private key
-Dldapset.set1.certpath=/path/to/client-cert.pem    # Client certificate
-Dldapset.set1.capath=/path/to/ca-cert.pem          # CA certificate
-Dldapset.set1.keystore=/path/to/keystore.jks       # Client keystore
-Dldapset.set1.keystorepasswd=changeme              # Keystore password
-Dldapset.set1.truststore=/path/to/truststore.jks   # Trust store
-Dldapset.set1.truststorepasswd=changeme            # Trust store password
```

#### Request Processor Configuration

```bash
# Request processor settings (replace 'ldapproxy' with your processor name)
-DrequestProcessor.ldapproxy.authclass=com.marklogic.processors.ProxyRequestProcessor
-DrequestProcessor.ldapproxy.debuglevel=DEBUG       # Processor debug level
-DrequestProcessor.ldapproxy.parm1=value1           # Custom parameter 1
-DrequestProcessor.ldapproxy.parm2=value2           # Custom parameter 2
# ... supports up to parm20
```

#### In-Memory Directory Server Configuration

```bash
# Directory servers to start (for testing)
-DdirectoryServers=marklogic,test                    # Comma-separated server list

# Directory server configuration (replace 'marklogic' with your server name)
-Dds.marklogic.name=MarkLogic1                      # Server display name
-Dds.marklogic.ipaddress=0.0.0.0                    # Server IP address
-Dds.marklogic.port=61389                           # Server port
-Dds.marklogic.basedn=dc=MarkLogic,dc=Local         # Base DN
-Dds.marklogic.admindn=cn=Directory Manager         # Admin DN
-Dds.marklogic.adminpw=password                     # Admin password
-Dds.marklogic.ldifpath=/path/to/users.ldif         # LDIF file path (optional)
```

#### Kerberos Configuration Properties

```bash
# Kerberos KDC configuration
-Dkerberos.enabled=true                              # Enable Kerberos KDC
-Dkerberos.realm=MARKLOGIC.LOCAL                     # Kerberos realm
-Dkerberos.kdc.host=localhost                        # KDC hostname
-Dkerberos.kdc.port=60088                            # KDC port
-Dkerberos.admin.port=60749                          # Admin port
-Dkerberos.debug=true                                # Enable Kerberos debug

# Principal management
-Dkerberos.principals.import-from-ldap=true          # Import from LDAP
-Dkerberos.principals.ldap-base-dn=dc=MarkLogic,dc=Local # LDAP base DN
-Dkerberos.service-principals=HTTP/localhost,ldap/localhost # Service principals
-Dkerberos.work-dir=./kerberos                       # Working directory
```

### Common Override Examples

#### Development Environment

```bash
# Start MLEAProxy with development settings
java -Dlogging.level.root=DEBUG \
     -Dldap.debug=true \
     -Dsaml.debug=true \
     -Dserver.port=8090 \
     -Dldaplistener.proxy.port=20389 \
     -jar target/mlesproxy-2.0.0.jar
```

#### Production Environment

```bash
# Start MLEAProxy with production settings
java -Dlogging.level.root=WARN \
     -Dlogging.file=/var/log/mleaproxy/production.log \
     -Dserver.port=8443 \
     -Dserver.ssl.enabled=true \
     -Dserver.ssl.key-store=/etc/mleaproxy/keystore.jks \
     -Dserver.ssl.key-store-password=changeme \
     -Doauth.server.base.url=https://auth.company.com:8443 \
     -Dldapserver.server1.host=ldap.company.com \
     -Dldapserver.server1.port=636 \
     -jar target/mlesproxy-2.0.0.jar
```

#### Quick Backend LDAP Override

```bash
# Override backend LDAP server without editing files
java -Dldapserver.server1.host=new-ldap.company.com \
     -Dldapserver.server1.port=636 \
     -Dldapset.set1.secure=true \
     -jar target/mlesproxy-2.0.0.jar
```

#### Container/Docker Environment

```bash
# Using environment variables (Docker/Kubernetes)
docker run -e "JAVA_OPTS=-Dserver.port=8080 \
                          -Doauth.server.base.url=http://oauth.company.com:8080 \
                          -Dldapserver.server1.host=ldap.company.com \
                          -Dldapserver.server1.port=636" \
           mleaproxy:latest
```

---

## 🤝 Support

### Issues and Questions

- **GitHub Issues**: [Report bugs or request features](https://github.com/mwarnes/MLEAProxy/issues)
- **GitHub Discussions**: [Ask questions or share ideas](https://github.com/mwarnes/MLEAProxy/discussions)
- **Wiki**: [Project Wiki](https://github.com/mwarnes/MLEAProxy/wiki)

### Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Write tests for new features
4. Ensure all tests pass
5. Submit a pull request

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **MarkLogic Corporation** - For inspiration and LDAP integration requirements
- **UnboundID** - For the excellent LDAP SDK
- **Spring Framework Team** - For Spring Boot
- **JWT Community** - For JJWT library
- **OpenSAML Project** - For SAML implementation
- **Contributors** - Thank you to all who have helped improve MLEAProxy

---

## 📊 Project Statistics

- **Languages**: Java 21, JSON, Properties
- **Lines of Code**: ~6,500+ (application) + 2,000+ (tests)
- **Test Coverage**: 100% (107/107 tests passing)
- **Documentation**: 6,000+ lines across multiple guides
- **Endpoints**: 11 (4 protocols: LDAP, OAuth 2.0, SAML 2.0, Kerberos)
- **Supported Standards**: 14+ RFCs and OASIS specifications
- **Authentication Methods**: 4 (LDAP, OAuth, SAML, Kerberos/SPNEGO)

---

<div align="center">

**Made with ❤️ for the MarkLogic Community**

[⬆ Back to Top](#-mleaproxy)

</div>
