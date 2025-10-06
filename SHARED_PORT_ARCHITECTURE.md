# Shared Port Architecture - SAML & OAuth

**Date**: October 6, 2025  
**Status**: ✅ Documentation Complete

## Overview

MLEAProxy uses a **shared port architecture** for SAML and OAuth protocols. Both services run on the same Spring Boot embedded web server and share a single port (default: 8080). They are distinguished by **URL path prefixes**, not separate ports.

## Architecture Summary

```
┌─────────────────────────────────────────────────────────┐
│         Spring Boot Embedded Web Server (Port 8080)     │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌────────────────────┐      ┌──────────────────────┐  │
│  │   SAML Endpoints   │      │   OAuth Endpoints    │  │
│  │   Path: /saml/*    │      │   Path: /oauth/*     │  │
│  ├────────────────────┤      ├──────────────────────┤  │
│  │ /saml/auth         │      │ /oauth/token         │  │
│  │ /saml/idp-metadata │      │ /oauth/jwks          │  │
│  │ /saml/wrapassertion│      │ /oauth/.well-known/* │  │
│  └────────────────────┘      └──────────────────────┘  │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

## Protocol Comparison

| Aspect | LDAP | SAML/OAuth |
|--------|------|------------|
| **Architecture** | Separate listener threads | Shared Spring Boot web server |
| **Port Configuration** | Multiple ports via `ldaplistener.*.port` | Single port via `server.port` |
| **URL Distinction** | N/A (binary protocol) | Path-based routing (`/saml/*`, `/oauth/*`) |
| **Concurrent Listeners** | Yes (multiple ports) | No (single web server) |
| **Default Port** | 10389+ | 8080 |

## Configuration

### Single Port for Both SAML and OAuth

**application.properties** or **command-line**:
```properties
# This single property affects BOTH SAML and OAuth
server.port=8080
```

**Result**:
- SAML endpoints: `http://localhost:8080/saml/*`
- OAuth endpoints: `http://localhost:8080/oauth/*`

### SAML Configuration (saml.properties)

```properties
# ================================================================
# Web Server Configuration (Shared with OAuth)
# ================================================================
# SAML and OAuth both run on the Spring Boot embedded web server
# They share the same port but use different URL paths:
#   - SAML endpoints: http://localhost:8080/saml/*
#   - OAuth endpoints: http://localhost:8080/oauth/*
#
# Configure the server port (default: 8080)
# Note: This affects BOTH SAML and OAuth endpoints
#
#server.port=8080

# SAML-specific settings
saml.debug=true
saml.default.roles=user
```

### OAuth Configuration (oauth.properties)

```properties
# ================================================================
# Web Server Configuration (Shared with SAML)
# ================================================================
# OAuth and SAML both run on the Spring Boot embedded web server
# They share the same port but use different URL paths:
#   - OAuth endpoints: http://localhost:8080/oauth/*
#   - SAML endpoints: http://localhost:8080/saml/*
#
# Configure the server port (default: 8080)
# Note: This affects BOTH OAuth and SAML endpoints
#
#server.port=8080

# OAuth-specific settings
oauth.default.roles=user
```

## All Available Endpoints

### SAML Endpoints (Port 8080)

| Endpoint | Method | Purpose | URL |
|----------|--------|---------|-----|
| Authentication | GET | SAML SSO login | `http://localhost:8080/saml/auth` |
| IdP Metadata | GET | Service Provider configuration | `http://localhost:8080/saml/idp-metadata` |
| Wrap Assertion | POST | Direct assertion generation | `http://localhost:8080/saml/wrapassertion` |

### OAuth Endpoints (Port 8080)

| Endpoint | Method | Purpose | URL |
|----------|--------|---------|-----|
| Token Generation | POST | Get JWT access token | `http://localhost:8080/oauth/token` |
| JWKS | GET | Public key discovery | `http://localhost:8080/oauth/jwks` |
| Server Config | GET | OAuth server metadata (RFC 8414) | `http://localhost:8080/oauth/.well-known/config` |

## Why Shared Port Architecture?

### Industry Standard
- **Keycloak**: All protocols on same port (default: 8080)
- **Auth0**: All endpoints on same domain/port
- **Okta**: Single authentication server, path-based routing
- **Azure AD**: All OAuth/SAML endpoints share base URL

### Technical Benefits
1. **Simpler Configuration**: Single port to configure and manage
2. **Firewall Management**: Only one port to open
3. **Load Balancing**: Single target for load balancers
4. **TLS/SSL**: Single certificate for all authentication endpoints
5. **Reverse Proxy**: Easier to configure with single backend
6. **Resource Efficiency**: Single web server instance

### Spring Boot Design
Spring Boot applications typically run a **single embedded web server** (Undertow, Tomcat, or Jetty). Multiple protocols are handled through:
- **Controller Mapping**: `@GetMapping("/saml/auth")` vs `@GetMapping("/oauth/token")`
- **Path-Based Routing**: Spring MVC routes requests by URL path
- **Bean Separation**: Different `@Controller` beans handle different paths

## Changing the Port

### Option 1: Application Properties
**File**: `application.properties`
```properties
server.port=9443
```

### Option 2: Command Line
```bash
java -jar mlesproxy-2.0.0.jar --server.port=9443
```

### Option 3: Environment Variable
```bash
export SERVER_PORT=9443
java -jar mlesproxy-2.0.0.jar
```

### Option 4: System Property
```bash
java -Dserver.port=9443 -jar mlesproxy-2.0.0.jar
```

**Result**: All endpoints (SAML and OAuth) will use port 9443

## Testing

### Verify Web Server Port
```bash
# Check Spring Boot startup log
grep "Undertow started on port" mleaproxy.log

# Expected output:
# Undertow started on port 8080 (http) with context path '/'
```

### Test SAML Endpoints
```bash
# Get IdP metadata
curl http://localhost:8080/saml/idp-metadata

# Test authentication endpoint (with valid SAMLRequest)
curl -G http://localhost:8080/saml/auth \
  --data-urlencode "SAMLRequest=<base64-encoded-request>"
```

### Test OAuth Endpoints
```bash
# Get JWKS
curl http://localhost:8080/oauth/jwks

# Get server configuration
curl http://localhost:8080/oauth/.well-known/config

# Generate token
curl -X POST http://localhost:8080/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&username=user1&password=pass&client_id=test"
```

### Verify Port Listening
```bash
# macOS/Linux
lsof -i :8080
netstat -an | grep 8080

# Expected: Java process listening on port 8080
```

## Migration Notes

### If You Previously Used Port 30389

All documentation and configuration examples have been updated from port **30389** to **8080**:

**Old URLs** (Incorrect):
- ❌ `http://localhost:30389/saml/auth`
- ❌ `http://localhost:30389/oauth/token`

**New URLs** (Correct):
- ✅ `http://localhost:8080/saml/auth`
- ✅ `http://localhost:8080/oauth/token`

**Client Applications**: Update any hardcoded URLs or configuration pointing to port 30389.

**Service Provider Configuration**: Re-import IdP metadata from the correct endpoint:
```bash
curl http://localhost:8080/saml/idp-metadata > mleaproxy-idp-metadata.xml
```

## FAQ

### Q: Can I run SAML and OAuth on different ports?
**A**: Not with the current architecture. This would require running two separate Spring Boot web servers, which is a major architectural change. The current design follows industry standards where identity providers run multiple protocols on the same port.

### Q: Why not use separate ports like LDAP does?
**A**: LDAP uses separate listener threads because it's a binary protocol with its own connection handling. SAML and OAuth are HTTP-based protocols that naturally fit into Spring Boot's web server architecture with path-based routing.

### Q: How do I secure the web server with HTTPS?
**A**: Configure Spring Boot's TLS settings in `application.properties`:
```properties
server.port=8443
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=changeit
server.ssl.key-store-type=PKCS12
```

This will secure **both** SAML and OAuth endpoints.

### Q: Can I disable SAML or OAuth individually?
**A**: The controllers are always active, but you can control access through:
- Spring Security configuration
- Reverse proxy rules
- Firewall rules

### Q: What if I need different TLS certificates for SAML and OAuth?
**A**: Use a reverse proxy (nginx, Apache, HAProxy) to terminate TLS with different certificates:
```
Client → Reverse Proxy (HTTPS, cert per path) → MLEAProxy (HTTP, single port)
```

## Related Documentation

- **SAML_GUIDE.md** - Complete SAML configuration and usage
- **OAUTH_GUIDE.md** - Complete OAuth configuration and usage
- **SAML_PORT_CONFIGURATION_UPDATE.md** - Port configuration fix details
- **saml.properties** - SAML configuration file
- **oauth.properties** - OAuth configuration file

## Implementation Details

### Spring Boot Controllers

**SAML**: `SAMLAuthHandler.java`
```java
@Controller
public class SAMLAuthHandler {
    @GetMapping("/saml/auth")
    public String authn(...) { ... }
    
    @GetMapping("/saml/idp-metadata")
    public ResponseEntity<String> idpMetadata() { ... }
}
```

**OAuth**: `OAuthTokenHandler.java`
```java
@RestController
public class OAuthTokenHandler {
    @PostMapping("/oauth/token")
    public ResponseEntity<Map<String, Object>> token(...) { ... }
    
    @GetMapping("/oauth/jwks")
    public ResponseEntity<Map<String, Object>> jwks() { ... }
}
```

Both controllers are registered with the same `DispatcherServlet` on the same port.

## Summary

✅ **One Port**: `server.port=8080` (default)  
✅ **Two Protocols**: SAML (`/saml/*`) and OAuth (`/oauth/*`)  
✅ **Path-Based Routing**: Spring MVC handles request routing  
✅ **Industry Standard**: Same approach as Keycloak, Auth0, Okta  
✅ **Simpler Management**: Single port, single TLS certificate, single configuration  

**Configuration Rule**: When you change `server.port`, you change it for **both** SAML and OAuth.
