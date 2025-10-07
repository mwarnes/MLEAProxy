# Kerberos Phase 2 - Quick Reference

## Status: ✅ COMPLETE

Phase 2 implementation is complete and compiles successfully!

## What Was Built

**Custom SPNEGO Authentication** using Java JAAS and GSS-API (Spring Boot 3.x compatible):

1. **KerberosAuthenticationFilter.java** - Custom filter that validates Kerberos tickets
2. **KerberosSecurityConfig.java** - Spring Security configuration
3. **KerberosAuthHandler.java** - REST endpoints for authentication

## Quick Start

### 1. Enable Kerberos

```properties
# kerberos.properties
kerberos.enabled=true
```

### 2. Start Application

```bash
mvn spring-boot:run
```

The KDC will start automatically and generate:
- `./krb5.conf` - Client configuration
- `./kerberos/keytabs/service.keytab` - Service keytab

### 3. Get Kerberos Ticket

```bash
export KRB5_CONFIG=./krb5.conf
kinit mluser1
# Password: password
klist
```

### 4. Test Authentication

```bash
curl --negotiate -u : http://localhost:8080/kerberos/auth
```

**Expected**: JSON response with JWT token

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "principal": "mluser1@MARKLOGIC.LOCAL",
  "roles": ["ROLE_USER"],
  ...
}
```

## Available Endpoints

| Endpoint | Auth Required | Description |
|----------|---------------|-------------|
| `/kerberos/auth` | Yes | Get JWT token |
| `/kerberos/token` | Yes | Alias for /auth |
| `/kerberos/whoami` | Yes | Get user info |
| `/kerberos/health` | No | Health check |

## Key Features

✅ Browser-based Kerberos SSO (Firefox/Chrome)  
✅ Command-line authentication (curl --negotiate)  
✅ JWT token generation  
✅ Spring Boot 3.x compatible  
✅ No deprecated dependencies  
✅ Custom JAAS/GSS-API implementation  

## Architecture

```
Browser/curl → HTTP request
              ↓
KerberosAuthenticationFilter
  ├─ Extract ticket from Authorization header
  ├─ JAAS login (service principal + keytab)
  ├─ GSS-API validation
  └─ Set Spring Security context
              ↓
KerberosAuthHandler
  ├─ Read authenticated principal
  ├─ Generate JWT token
  └─ Return token
```

## Configuration

### kerberos.properties

```properties
kerberos.enabled=true
kerberos.service-principal=HTTP/localhost@MARKLOGIC.LOCAL
kerberos.keytab-location=./kerberos/keytabs/service.keytab
kerberos.debug=false
```

### JWT Settings (oauth.properties)

```properties
oauth.token.expiry=3600
oauth.token.audience=marklogic-api
oauth.issuer=mleaproxy-kerberos
```

## Testing

### Test 1: Basic Authentication
```bash
curl --negotiate -u : http://localhost:8080/kerberos/auth
```

### Test 2: Use JWT Token
```bash
TOKEN=$(curl -s --negotiate -u : http://localhost:8080/kerberos/auth | jq -r .token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/endpoint
```

### Test 3: User Info
```bash
curl --negotiate -u : http://localhost:8080/kerberos/whoami
```

### Test 4: Health Check (no auth)
```bash
curl http://localhost:8080/kerberos/health
```

## Browser Testing (Firefox)

1. Open `about:config`
2. Set `network.negotiate-auth.trusted-uris` = `localhost`
3. Restart Firefox
4. Navigate to: `http://localhost:8080/kerberos/auth`
5. JWT token displayed as JSON

## Available Test Users

From Phase 1 KDC:

| Username | Password | Roles |
|----------|----------|-------|
| mluser1 | password | ROLE_USER |
| mluser2 | password | ROLE_USER |
| mluser3 | password | ROLE_USER |
| appreader | password | ROLE_USER |
| appwriter | password | ROLE_USER |
| appadmin | password | ROLE_USER |

## Build Status

```bash
mvn clean compile
```

**Result**: ✅ BUILD SUCCESS (no compilation errors)

## Documentation

- **Complete Guide**: `KERBEROS_PHASE2_COMPLETE.md` (548 lines)
- **Feasibility Study**: `KERBEROS_FEASIBILITY.md`
- **Phase 1 Summary**: `KERBEROS_PHASE1_COMPLETE.md`

## Next Steps (Phase 3)

Phase 3 will integrate Kerberos with OAuth and SAML:

1. **OAuth Integration**
   - `/oauth/token-from-kerberos` endpoint
   - Exchange Kerberos ticket for OAuth JWT

2. **SAML Integration**
   - Generate SAML assertion from Kerberos principal
   - Include attributes from LDAP

3. **LDAP Role Mapping**
   - Query LDAP for user's group memberships
   - Map groups to Spring Security roles
   - Include in JWT claims

## Troubleshooting

### Problem: 401 Unauthorized
**Solution**: Check `kerberos.enabled=true` and KDC is running

### Problem: JAAS Login Failed
**Solution**: Verify keytab exists: `ls -la ./kerberos/keytabs/service.keytab`

### Problem: No Ticket
**Solution**: 
```bash
kinit mluser1
klist
```

### Problem: Ticket Expired
**Solution**:
```bash
kdestroy
kinit mluser1
```

## Files Created

```
src/main/java/com/marklogic/security/
  └── KerberosAuthenticationFilter.java     ← NEW (254 lines)

src/main/java/com/marklogic/configuration/
  └── KerberosSecurityConfig.java           ← MODIFIED (102 lines)

src/main/java/com/marklogic/controller/
  └── KerberosAuthHandler.java              ← NEW (222 lines)

KERBEROS_PHASE2_COMPLETE.md                 ← NEW (548 lines)
KERBEROS_PHASE2_QUICK_REF.md               ← THIS FILE
```

---

**Phase 2 Status**: ✅ COMPLETE  
**Build Status**: ✅ SUCCESS  
**Ready for**: Browser testing, Phase 3 integration
