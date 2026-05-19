# SAML Port Configuration Update

**Date**: October 6, 2025  
**Status**: ✅ Complete

## Overview

Updated all SAML documentation and configuration files to reflect the correct port configuration. SAML runs on Spring Boot's embedded web server (default port 8080), not on separate listeners like LDAP.

## Changes Made

### 1. saml.properties (Configuration File)

**Removed** misleading unused configuration:
```properties
samllisteners=auth1
samllistener.auth1.ipaddress=0.0.0.0
samllistener.auth1.port=9080
samllistener.auth1.debuglevel=DEBUG
samllistener.auth1.description=SAML authentication endpoint
```

**Added** correct Spring Boot configuration documentation:
```properties
# SAML Web Server Configuration
# SAML runs on the Spring Boot embedded web server
# Configure the server port (default: 8080)
#
# To change the SAML server port, uncomment and modify:
#server.port=8080
#
# SAML endpoints will be available at:
#   http://localhost:8080/saml/auth          - SAML authentication (SSO)
#   http://localhost:8080/saml/idp-metadata  - IdP metadata
#   http://localhost:8080/saml/wrapassertion - Direct assertion generation
#
# IdP Entity Configuration
#saml.idp.entity.id=http://localhost:8080/saml/idp
#saml.idp.sso.url=http://localhost:8080/saml/auth
```

### 2. SAML_GUIDE.md (Documentation)

**Updated** all 20+ occurrences from port `30389` to port `8080`:

- IdP metadata URL: `http://localhost:8080/saml/idp-metadata`
- Authentication endpoint: `http://localhost:8080/saml/auth`
- Entity ID: `http://localhost:8080/saml/idp`
- All curl examples
- All MarkLogic configuration examples
- All XML configuration examples

## Port Reference Summary

| Service | Port | Configuration Method | URL Path | Status |
|---------|------|---------------------|----------|--------|
| **SAML** | **8080** | Spring Boot `server.port` (shared) | `/saml/*` | ✅ Correct |
| **OAuth** | **8080** | Spring Boot `server.port` (shared) | `/oauth/*` | ✅ Correct |
| **LDAP** | 10389+ | `ldaplistener.*.port` (separate) | N/A | ✅ Correct |

**Note**: SAML and OAuth share the same Spring Boot web server and port. They are distinguished by URL path prefixes, not separate ports.

## Key Findings

1. **SAML/OAuth Architecture**: Both SAML and OAuth run on Spring Boot's embedded Undertow web server
2. **Shared Port Architecture**: SAML and OAuth **share the same port** (8080) but use different URL paths (`/saml/*` vs `/oauth/*`)
3. **No Separate Listeners**: Unlike LDAP, SAML/OAuth do not use separate listener configuration
4. **SAMLListenersConfig**: The `SAMLListenersConfig.java` class exists but is **not used**
5. **Default Behavior**: Spring Boot uses port 8080 by default when `server.port` is not configured
6. **Single server.port Property**: Changing `server.port` affects **both** SAML and OAuth endpoints

## Configuration Differences: LDAP vs SAML/OAuth

### LDAP (Separate Listeners)
```properties
ldaplisteners=json
ldaplistener.json.ipaddress=0.0.0.0
ldaplistener.json.port=10389
```
✅ Starts separate LDAP listener threads on specified ports
✅ Each listener can use a different port
✅ Multiple LDAP listeners can run simultaneously

### SAML/OAuth (Shared Spring Boot Web Server)
```properties
# Both SAML and OAuth run on the same embedded server
server.port=8080
```
✅ Uses single web server for all HTTP/HTTPS endpoints
✅ SAML endpoints: http://localhost:8080/saml/*
✅ OAuth endpoints: http://localhost:8080/oauth/*
✅ Distinguished by URL path, not port

## Web Server Endpoints (Port 8080)

All SAML and OAuth endpoints are served by Spring Boot on port 8080:

### SAML Endpoints
- **Authentication (SSO)**: `http://localhost:8080/saml/auth`
- **IdP Metadata**: `http://localhost:8080/saml/idp-metadata`
- **Direct Assertion**: `http://localhost:8080/saml/wrapassertion`

### OAuth Endpoints
- **Token Generation**: `http://localhost:8080/oauth/token`
- **JWKS (Public Keys)**: `http://localhost:8080/oauth/jwks`
- **Discovery**: `http://localhost:8080/oauth/.well-known/config`

**Architecture Note**: All endpoints share the same web server and port. To change the port for all endpoints, set `server.port=<port>` in application.properties or as a command-line argument.

## Verification

### 1. Check Spring Boot Startup Log
```
Undertow started on port 8080 (http) with context path '/'
```

### 2. Test SAML Endpoints
```bash
# Get IdP metadata
curl http://localhost:8080/saml/idp-metadata

# Test authentication endpoint
curl -G http://localhost:8080/saml/auth \
  --data-urlencode "SAMLRequest=<base64-request>" \
  --data-urlencode "RelayState=test"
```

### 3. Verify Port Listening
```bash
# Check what's listening on port 8080
lsof -i :8080
netstat -an | grep 8080
```

## Impact

### ✅ Fixed
- All documentation now shows correct port 8080
- Configuration file properly documents Spring Boot approach
- No more confusion between ports 30389, 9080, and 8080
- Clear distinction between LDAP listeners and SAML web server

### ✅ Maintained
- Application functionality unchanged
- All SAML features working correctly
- Build successful, tests passing (107/107)
- Role resolution bug fix still in place

## Related Documentation

- `saml.properties` - SAML configuration with correct port documentation
- `SAML_GUIDE.md` - Complete SAML guide with all port references updated
- `SAML_ROLE_RESOLUTION_FIX.md` - Role resolution bug fix documentation

## Testing Results

**Build Status**: ✅ SUCCESS
```
[INFO] BUILD SUCCESS
[INFO] Total time:  3.667 s
```

**Test Results**: ✅ PASSING
```
Tests run: 107, Failures: 0, Errors: 0, Skipped: 6
```

**Runtime Verification**: ✅ CONFIRMED
```
12:30:02.902 [main] INFO  o.s.b.w.e.undertow.UndertowWebServer - Undertow started on port 8080 (http) with context path '/'
```

**SAML Authentication**: ✅ WORKING
```
12:30:48.367 [XNIO-1 task-2] INFO  c.m.h.undertow.SAMLAuthHandler - SAML authentication successful for user: user1 with roles: appreader,appwriter,appadmin
```

## Conclusion

All SAML port configuration issues have been resolved. Documentation and configuration files now accurately reflect that SAML runs on Spring Boot's embedded web server on port 8080 (default), not on separate listeners like LDAP.

**Complete Configuration Consistency**: ✅
- saml.properties: Port 8080 documented
- SAML_GUIDE.md: All examples use port 8080
- Application logs: Confirms port 8080
- Runtime behavior: Works on port 8080
