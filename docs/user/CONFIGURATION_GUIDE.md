# Configuration Guide

## Overview

MLEAProxy configuration has been reorganized into modular property files for better maintainability and clarity. Configuration is now split across multiple files based on functionality.

## Configuration Files

### Core Configuration Files

| File | Purpose | Default Location | Override Locations |
|------|---------|------------------|-------------------|
| `application.properties` | Spring Boot settings, logging | `src/main/resources/` | `/etc/`, `${HOME}/`, `./` |
| `ldap.properties` | LDAP proxy configuration | Project root | `/etc/`, `${HOME}/`, `./` |
| `saml.properties` | SAML authentication configuration | Project root | `/etc/`, `${HOME}/`, `./` |
| `oauth.properties` | OAuth token configuration | Project root | `/etc/`, `${HOME}/`, `./` |
| `directory.properties` | In-memory directory servers (testing) | Project root | `/etc/`, `${HOME}/`, `./` |

### Legacy Support

| File | Status | Notes |
|------|--------|-------|
| `mleaproxy.properties` | **Supported** | Legacy monolithic configuration file still works |

## Property Loading Priority

The application uses a **MERGE** strategy to load properties from multiple sources. Properties are loaded in this order (later sources override earlier ones):

1. `classpath:mleaproxy.properties` (bundled in JAR - lowest priority)
2. `/etc/mleaproxy.properties` (system-wide)
3. `${HOME}/mleaproxy.properties` (user-specific)
4. `./mleaproxy.properties` (current directory)
5. `${mleaproxy.properties}` (system property path)
6. `classpath:application.properties` (bundled Spring Boot config)
7. `/etc/application.properties` (system-wide Spring Boot config)
8. `${HOME}/application.properties` (user-specific Spring Boot config)
9. `./application.properties` (current directory Spring Boot config)
10. `./ldap.properties`
11. `./saml.properties`
12. `./oauth.properties`
13. `./directory.properties`
14. **System properties** via `-D` flags (highest priority)

This means you can override any configuration property at runtime using command-line arguments. For example:

```bash
java -Dldaplistener.proxy.port=20389 -Dldap.debug=true -jar target/mlesproxy-2.0.0.jar
```

## Configuration File Details

### ldap.properties

Configures the LDAP proxy functionality:

```properties
# Debug logging
ldap.debug=true

# LDAP listeners to start
ldaplisteners=proxy

# Listener configuration
ldaplistener.proxy.ipaddress=0.0.0.0
ldaplistener.proxy.port=10389
ldaplistener.proxy.secure=false
ldaplistener.proxy.ldapset=set1

# Backend LDAP/AD servers
ldapserver.server1.host=192.168.0.60
ldapserver.server1.port=636
```

**Key Settings:**
- `ldaplisteners` - Comma-separated list of listeners to enable
- `ldaplistener.<name>.*` - Configuration for each listener
- `ldapserver.<name>.*` - Backend LDAP/AD server details
- `ldapset.<name>.*` - Server sets for load balancing/failover

### saml.properties

Configures SAML authentication:

```properties
# Debug logging
saml.debug=true

# Certificate paths (optional)
#saml.capath=/path/to/certificate.pem
#saml.keypath=/path/to/rsakey.pem

# Response validity (seconds)
#saml.response.validity=300

# SAML listeners (optional - uses Spring Boot port if not configured)
#samllisteners=auth1
```

**Key Settings:**
- `saml.debug` - Enable detailed SAML logging
- `saml.capath` - Path to SAML certificate (default: bundled cert)
- `saml.keypath` - Path to private key (default: bundled key)
- `saml.response.validity` - Token validity in seconds (default: 300)

### oauth.properties

Configures OAuth token generation:

```properties
# OAuth token validity (seconds)
#oauth.token.validity=3600

# Private key path
#oauth.keypath=classpath:static/certificates/privkey.pem
```

**Key Settings:**
- `oauth.token.validity` - JWT token expiration time
- `oauth.keypath` - RSA private key for signing tokens

### directory.properties

Configures in-memory LDAP servers (primarily for testing):

```properties
# Directory servers to start
#directoryServers=marklogic

# Server configuration
ds.marklogic.name=MarkLogic1
ds.marklogic.ipaddress=0.0.0.0
ds.marklogic.port=60389
ds.marklogic.basedn=dc=MarkLogic,dc=Local
```

**Note:** This is typically disabled in production environments.

### application.properties

Spring Boot and application-wide settings. This file can now be overridden using the same hierarchy as other configuration files:

**Default (bundled in JAR):**
```properties
# Logging
logging.level.root=INFO
logging.file=mleaproxy.log

# XML User Repository (optional)
users.xml.path=/path/to/users.xml
```

**Override Examples:**

**Local override (./application.properties):**
```properties
# Override logging level for development
logging.level.root=DEBUG
logging.level.com.marklogic=TRACE

# Custom log file location
logging.file=/var/log/mleaproxy/application.log

# Development-specific settings
spring.profiles.active=development
```

**System-wide override (/etc/application.properties):**
```properties
# Production logging configuration
logging.level.root=WARN
logging.file=/var/log/mleaproxy/production.log
logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n

# Production security settings
server.ssl.enabled=true
server.port=8443
```

**Key Settings:**
- `logging.*` - Logging configuration (levels, file paths, patterns)
- `server.*` - Spring Boot server settings (port, SSL, etc.)
- `spring.*` - Spring Framework settings
- `users.xml.path` - Custom XML user repository location

## Running the Application

### Using Modular Configuration (Recommended)

Place property files in the project root directory:

```bash
java -jar target/mlesproxy-2.0.0.jar
```

The application automatically loads:
- `./application.properties` (Spring Boot overrides)
- `./ldap.properties`
- `./saml.properties`
- `./oauth.properties`
- `./directory.properties`

### Using Legacy Configuration

You can still use the monolithic `mleaproxy.properties`:

```bash
java -Dmleaproxy.properties=./mleaproxy.properties -jar target/mlesproxy-2.0.0.jar
```

### Using Both

You can use a combination - modular files for organized configuration, with a legacy file for overrides:

```bash
java -Dmleaproxy.properties=./overrides.properties -jar target/mlesproxy-2.0.0.jar
```

## Migration from Legacy Configuration

### Option 1: Use Modular Files (Recommended)

1. **Copy the new property files** to your deployment directory:
   ```bash
   cp ldap.properties /path/to/deployment/
   cp saml.properties /path/to/deployment/
   cp oauth.properties /path/to/deployment/
   cp directory.properties /path/to/deployment/
   ```

2. **Customize each file** for your environment

3. **Remove the `-Dmleaproxy.properties` parameter** from your startup script

### Option 2: Keep Legacy File

Continue using your existing `mleaproxy.properties` - it still works perfectly!

### Option 3: Hybrid Approach

Use modular files for base configuration and keep environment-specific overrides in a separate file:

```bash
# Base configuration in modular files
./ldap.properties
./saml.properties
./oauth.properties

# Environment-specific overrides
java -Dmleaproxy.properties=./prod-overrides.properties -jar target/mlesproxy-2.0.0.jar
```

## Configuration Examples

### Example 1: Development Environment

**ldap.properties:**
```properties
ldap.debug=true
ldaplistener.proxy.port=10389
ldapserver.server1.host=localhost
ldapserver.server1.port=389
```

**saml.properties:**
```properties
saml.debug=true
```

### Example 2: Production Environment

**application.properties:**
```properties
# Production logging
logging.level.root=WARN
logging.file=/var/log/mleaproxy/production.log
logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n

# SSL configuration
server.ssl.enabled=true
server.port=8443
server.ssl.key-store=/etc/mleaproxy/keystore.jks
server.ssl.key-store-password=changeme
```

**ldap.properties:**
```properties
ldap.debug=false
ldaplistener.proxy.port=10389
ldaplistener.proxy.secure=true
ldapserver.server1.host=ad.corp.com
ldapserver.server1.port=636
```

**saml.properties:**
```properties
saml.debug=false
saml.capath=/etc/mleaproxy/certs/certificate.pem
saml.keypath=/etc/mleaproxy/certs/rsakey.pem
saml.response.validity=300
```

### Example 3: Development with Custom Spring Boot Settings

**application.properties:**
```properties
# Development logging
logging.level.root=DEBUG
logging.level.com.marklogic=TRACE
logging.pattern.console=%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n

# Development server settings
server.port=8080
management.endpoints.web.exposure.include=health,info,metrics
```

## Troubleshooting

### Properties Not Loading

Check the application startup logs for property source information:

```
Loading configuration from:
  - classpath:mleaproxy.properties
  - ./ldap.properties
  - ./saml.properties
  ...
```

### Property Precedence Issues

Remember the merge order - files loaded later override earlier values. Use debug logging to see which values are active:

```bash
java -Dlogging.level.org.aeonbits.owner=DEBUG -jar target/mlesproxy-2.0.0.jar
```

### Missing Property Files

If optional property files don't exist, they're silently skipped. This is normal and expected.

## Best Practices

1. ✅ **Use modular files** for new deployments
2. ✅ **Keep sensitive data** (passwords, keys) in separate files with restricted permissions
3. ✅ **Use comments** to document non-obvious settings
4. ✅ **Version control** your base configurations, but exclude environment-specific overrides
5. ✅ **Test configuration changes** in development before deploying to production

## Command-Line Property Override

**System properties have the highest priority** and will override any file-based configuration. You can pass properties using `-D` flags:

### Examples

**Override LDAP listener port:**
```bash
java -Dldaplistener.proxy.port=20389 -jar target/mlesproxy-2.0.0.jar
```

**Enable debug logging:**
```bash
java -Dldap.debug=true -Dsaml.debug=true -jar target/mlesproxy-2.0.0.jar
```

**Multiple overrides:**
```bash
java -Dldap.debug=true \
     -Dldaplistener.proxy.port=20389 \
     -Dldaplistener.proxy.ipaddress=127.0.0.1 \
     -jar target/mlesproxy-2.0.0.jar
```

**Override backend LDAP server:**
```bash
java -Dldapserver.server1.host=ldap.example.com \
     -Dldapserver.server1.port=636 \
     -jar target/mlesproxy-2.0.0.jar
```

**Override Spring Boot application settings:**
```bash
java -Dlogging.level.root=DEBUG \
     -Dserver.port=8443 \
     -Dlogging.file=/tmp/mleaproxy-debug.log \
     -jar target/mlesproxy-2.0.0.jar
```

**Combined protocol and application overrides:**
```bash
java -Dldap.debug=true \
     -Dsaml.debug=true \
     -Dlogging.level.com.marklogic=TRACE \
     -Dserver.port=9090 \
     -jar target/mlesproxy-2.0.0.jar
```

### Use Cases

System property overrides are particularly useful for:

- **Quick testing** - Change settings without editing files
- **Container deployments** - Pass environment-specific settings via Docker/Kubernetes env vars
- **CI/CD pipelines** - Configure per-environment without maintaining multiple files
- **Development** - Test different configurations rapidly
- **Troubleshooting** - Enable debug logging temporarily without file changes
