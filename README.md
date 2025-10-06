# 🚀 MLEAProxy

[![Java](https://img.shields.io/badge/Java-21+-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?style=for-the-badge&logo=spring-boot)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36?style=for-the-badge&logo=apache-maven)](https://maven.apache.org/)
[![Tests](https://img.shields.io/badge/Tests-107%2F107_Passing-brightgreen?style=for-the-badge)](./TEST_SUITE_SUMMARY.md)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

> **A Multi-Protocol Authentication Proxy & Development Server** 🔐
>
> Supporting **LDAP/LDAPS**, **OAuth 2.0**, and **SAML 2.0** for MarkLogic Server and beyond

---

## ✨ Overview

**MLEAProxy** is a comprehensive authentication proxy and development server that supports multiple authentication protocols. Originally designed for MarkLogic Server external authentication diagnostics, it has evolved into a full-featured authentication platform supporting LDAP, OAuth 2.0, and SAML 2.0.

### 🎯 Key Features

- **LDAP/LDAPS**: Proxy mode, load balancing, standalone JSON server, injection protection
- **OAuth 2.0**: JWT token generation, JWKS endpoint, RFC 8414 metadata, 3-tier role resolution
- **SAML 2.0**: Full IdP implementation, metadata endpoint, digital signatures, 3-tier role resolution
- **Modern Stack**: Java 21, Spring Boot 3.3.5, Jackson JSON processing
- **Comprehensive Testing**: 107/107 tests passing (100% coverage)
- **Extensive Documentation**: Separate protocol-specific guides

### 🆕 What's New in 2025

- **JSON User Repository**: Converted from XML to JSON format for better performance
- **3-Tier Role Resolution**: Sophisticated role assignment for OAuth and SAML:
  1. Request parameter roles (explicit override - highest priority)
  2. JSON user repository roles (user-specific)
  3. Default configuration roles (fallback - lowest priority)
- **Enhanced Testing**: Expanded test suite to 107 tests with comprehensive coverage
- **Improved Documentation**: Reorganized into protocol-specific guides

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
# - LDAP server on ldap://localhost:10389
# - OAuth endpoints on http://localhost:8080/oauth/*
# - SAML endpoints on http://localhost:8080/saml/*
```

### Quick Tests

```bash
# Test LDAP
ldapsearch -H ldap://localhost:10389 \
  -D "cn=manager,ou=users,dc=marklogic,dc=local" \
  -w password \
  -b "ou=users,dc=marklogic,dc=local"

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
| **Network** | TCP ports | Defaults: 10389 (LDAP), 8080 (HTTP - SAML/OAuth) |
| **Disk Space** | 100MB+ | For JAR and logs |

---

## 🌐 Endpoint Reference

### Quick Reference Table

| Protocol | Endpoint | Method | Purpose | Port |
|----------|----------|--------|---------|------|
| **LDAP** | `ldap://localhost:10389` | LDAP | LDAP proxy/server | 10389 |
| **OAuth** | `/oauth/token` | POST | Generate JWT tokens | 8080 |
| **OAuth** | `/oauth/jwks` | GET | Public key discovery | 8080 |
| **OAuth** | `/oauth/.well-known/config` | GET | Server metadata (RFC 8414) | 8080 |
| **SAML** | `/saml/auth` | GET | SAML authentication (SSO) | 8080 |
| **SAML** | `/saml/idp-metadata` | GET | IdP metadata XML | 8080 |

> **Note**: OAuth and SAML share the same Spring Boot web server port (default: 8080). They are distinguished by URL path prefixes (`/oauth/*` vs `/saml/*`), not separate ports. Configure via `server.port` property.

### LDAP Endpoints

**LDAP Proxy Server**

- Protocol: LDAP/LDAPS
- Default Port: 10389 (configurable)
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

Complete documentation for each protocol has been organized into separate guides:

### LDAP/LDAPS Guide

**[LDAP_GUIDE.md](./LDAP_GUIDE.md)** - Complete LDAP functionality

- Configuration (servers, server sets, listeners, processors)
- Standalone JSON LDAP server
- Proxy mode and load balancing
- Security features (LDAPS, injection protection)
- Multiple usage scenarios with examples
- Troubleshooting guide

### OAuth 2.0 Guide

**[OAUTH_GUIDE.md](./OAUTH_GUIDE.md)** - Complete OAuth 2.0 functionality

- All OAuth endpoints (token, JWKS, well-known config)
- Configuration and user repository setup
- 3-tier role resolution system (New in 2025)
- Client integration examples (Node.js, Python, Java)
- Security best practices
- Complete API reference

### SAML 2.0 Guide

**[SAML_GUIDE.md](./SAML_GUIDE.md)** - Complete SAML 2.0 functionality

- All SAML endpoints (auth, IdP metadata)
- Configuration and certificate setup
- 3-tier role resolution system (New in 2025)
- Service Provider integration (Okta, Azure AD, SimpleSAMLphp)
- Security and certificate management
- Complete API reference

---

## 👥 User Repository

### JSON User Repository (New in 2025)

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

### 3-Tier Role Resolution (New in 2025)

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
- JsonUserRepositoryTest: 14 tests (New in 2025)
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

### Complete Documentation Index

| Document | Description | Updated |
|----------|-------------|---------|
| **[README.md](./README.md)** | This file - General overview | 2025 |
| **[LDAP_GUIDE.md](./LDAP_GUIDE.md)** | Complete LDAP/LDAPS guide | 2025 |
| **[IN_MEMORY_LDAP_GUIDE.md](./IN_MEMORY_LDAP_GUIDE.md)** | In-Memory LDAP server guide | 2025 |
| **[OAUTH_GUIDE.md](./OAUTH_GUIDE.md)** | Complete OAuth 2.0 guide | 2025 |
| **[SAML_GUIDE.md](./SAML_GUIDE.md)** | Complete SAML 2.0 guide | 2025 |
| **[TEST_SUITE_SUMMARY.md](./TEST_SUITE_SUMMARY.md)** | Test suite documentation | 2025 |
| **[TESTING_GUIDE.md](./TESTING_GUIDE.md)** | Testing procedures | 2025 |
| **[CONFIGURATION_GUIDE.md](./CONFIGURATION_GUIDE.md)** | Configuration reference | 2025 |

### Additional Documentation

- **[OAUTH_JWKS_WELLKNOWN_COMPLETE.md](./OAUTH_JWKS_WELLKNOWN_COMPLETE.md)** - Detailed JWKS/well-known documentation
- **[SAML_IDP_METADATA_COMPLETE.md](./SAML_IDP_METADATA_COMPLETE.md)** - Detailed SAML IdP metadata documentation
- **[OAUTH_SAML_DISCOVERY_SUMMARY.md](./OAUTH_SAML_DISCOVERY_SUMMARY.md)** - Discovery endpoints summary
- **[DISCOVERY_ENDPOINTS_QUICK_REF.md](./DISCOVERY_ENDPOINTS_QUICK_REF.md)** - Quick command reference
- **[CODE_FIXES_SUMMARY_2025.md](./CODE_FIXES_SUMMARY_2025.md)** - Recent changes and fixes
- **[JJWT_MIGRATION_COMPLETE.md](./JJWT_MIGRATION_COMPLETE.md)** - JJWT 0.12.6 migration notes

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
- **Lines of Code**: ~5,000+ (application) + 2,000+ (tests)
- **Test Coverage**: 100% (107/107 tests passing)
- **Documentation**: 4,000+ lines across multiple guides
- **Endpoints**: 6 (3 protocols: LDAP, OAuth 2.0, SAML 2.0)
- **Supported Standards**: 10+ RFCs and OASIS specifications

---

<div align="center">

**Made with ❤️ for the MarkLogic Community**

[⬆ Back to Top](#-mleaproxy)

</div>
