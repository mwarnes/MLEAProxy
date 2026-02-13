# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MLEAProxy (MarkLogic External Authentication Proxy) is a multi-protocol authentication proxy and development server supporting LDAP/LDAPS, OAuth 2.0, SAML 2.0, and Kerberos. Built with Java 21 and Spring Boot 3.3.5, using Undertow as the servlet container.

## Build & Run Commands

```bash
# Build (filters Maven/Guice warnings for clean output)
./build.sh clean package

# Run the application
java -Dmleaproxy.properties=./mleaproxy.properties -jar target/mlesproxy-2.0.0.jar

# Run in dev mode
mvn spring-boot:run

# Run all tests
./run-tests.sh all          # or: mvn clean test

# Run specific test suites
./run-tests.sh oauth        # OAuthTokenHandlerTest
./run-tests.sh saml         # SAMLAuthHandlerTest
./run-tests.sh ldap         # LDAPRequestHandlerTest
./run-tests.sh integration  # MLEAProxyIntegrationTest

# Run a single test class
./run-tests.sh single OAuthTokenHandlerTest
mvn test -Dtest=OAuthTokenHandlerTest

# Tests with coverage
mvn clean test jacoco:report    # Report at target/site/jacoco/index.html
```

The build artifact is `target/mlesproxy-2.0.0.jar` (also copied to `release/`).

## Architecture

### Entry Point & Startup

`MLEAProxy.java` is the Spring Boot main class. `ApplicationListener` (implements `ApplicationRunner`) is a lightweight orchestrator (~183 lines) that delegates to specialized service classes:
1. Loads configuration via `ApplicationConfig` (Aeonbits Owner pattern)
2. Registers BouncyCastle security provider
3. Delegates to `LDAPServerService` to start in-memory LDAP directory servers
4. Starts Kerberos KDC if enabled via `KerberosKDCServer`
5. Delegates to `LDAPListenerService` to start LDAP proxy listeners
6. Initializes SAML configuration via `SamlBean`
7. Delegates to `StartupDisplayService` to display startup summary

The startup flow follows the Single Responsibility Principle with focused service classes handling specific concerns.

### Configuration System

Uses the **Aeonbits Owner** library with MERGE load policy. `ApplicationConfig` is the central config interface. Properties are loaded in priority order (highest first):
1. System properties (`-D` flags)
2. `${mleaproxy.properties}` system variable path
3. `./mleaproxy.properties` (working directory)
4. `${HOME}/mleaproxy.properties`
5. `/etc/mleaproxy.properties`
6. `classpath:mleaproxy.properties`

Plus protocol-specific files: `ldap.properties`, `saml.properties`, `oauth.properties`, `directory.properties`

### Key Packages

- **`handlers/undertow/`** - HTTP request handlers: `OAuthTokenHandler` (JWT/JWKS/discovery), `SAMLAuthHandler` (SAML IdP), `KerberosAuthHandler` (SPNEGO), bridge handlers for cross-protocol auth
- **`handlers/`** - `LDAPRequestHandler` (LDAP proxy), `ApplicationListener` (startup orchestrator, ~183 lines), `KerberosKDCServer` (embedded KDC)
- **`service/`** - Core services following Single Responsibility Principle:
  - `LDAPServerService` - Manages in-memory LDAP directory servers (UnboundID)
  - `LDAPListenerService` - Manages LDAP proxy listeners with 7 ServerSet modes (INTERNAL, SINGLE, ROUNDROBIN, FAILOVER, FASTEST, FEWEST, ROUNDROBINDNS)
  - `MarkLogicConfigService` - Generates MarkLogic External Security configuration files
  - `CertificateService` - SSL/TLS certificate loading (PFX, PEM, JKS formats)
  - `StartupDisplayService` - Startup information and endpoint summaries
  - `RefreshTokenService` - OAuth refresh token management
  - `LDAPRoleService` - LDAP group-based role resolution
- **`processors/`** - LDAP request processing: `ProxyRequestProcessor`, `JsonRequestProcessor`, `XMLRequestProcessor` (all implement `IRequestProcessor`)
- **`repository/`** - User storage: `JsonUserRepository` (primary, from `users.json`) and `XmlUserRepository` (legacy, from `users.xml`)
- **`configuration/`** - Spring config classes: `LDAPListenersConfig`, `ProcessorConfig`, `SetsConfig`, `ServersConfig`, `DSConfig`, `SAMLListenersConfig`, `KerberosConfig`

### Protocol Endpoints

All HTTP endpoints share the web server port (default 8080):
- **OAuth**: `/oauth/token`, `/oauth/jwks`, `/.well-known/openid-configuration`
- **SAML**: `/saml/auth`, `/saml/metadata`, `/saml/wrapassertion`, `/saml/cacerts`
- **Kerberos**: `/kerberos/auth`, `/kerberos/oauth`, `/kerberos/saml`
- **Utility**: `/b64encode`, `/b64decode`

LDAP listeners run on separate ports (default: 10389 proxy, 61389 in-memory).

### Key Libraries

- **UnboundID LDAP SDK 7.0.4** - LDAP protocol implementation
- **OpenSAML 4.3.2** (Shibboleth) - SAML 2.0 processing
- **JJWT 0.13.0** - JWT token creation/validation
- **BouncyCastle 1.83** - Cryptography (bcprov, bcpkix, bcutil)
- **Apache Kerby 2.0.3** - Embedded Kerberos KDC
- **Aeonbits Owner 1.0.12** - Type-safe configuration
- **Guava 33.5.0-jre** - Core utilities and caching
- **Commons Lang3 3.20.0** - String utilities

### Testing

9 test classes with 107 tests total (JUnit 5, Spring Boot Test). Tests use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `MockMvc`. Test classes mirror the main source structure under `src/test/java/com/marklogic/`.

## Documentation

Protocol-specific guides are in `docs/user/`: `LDAP_GUIDE.md`, `OAUTH_GUIDE.md`, `SAML_GUIDE.md`, `KERBEROS_GUIDE.md`, `CONFIGURATION_GUIDE.md`. Example configurations are in `examples/`. REST client test files for manual testing are in `http_client/`.
