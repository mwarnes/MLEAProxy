# Kerberos Phase 4: Production Enhancements - IN PROGRESS

## Status: Phase 4 Implementation Started

**Date**: January 2025  
**Phase**: Phase 4 - Optional Production Enhancements  
**Previous**: [Phase 3 Complete](KERBEROS_PHASE3_COMPLETE.md)

---

## Overview

Phase 4 enhances the Kerberos OAuth/SAML bridges with production-ready features requested by the user:

1. **SAML Assertion Signing** - Add XMLSignature support for trust
2. **LDAP Role Integration** - Query in-memory LDAP for group-based roles  
3. **Token Refresh** - OAuth 2.0 refresh token mechanism
4. **Configurable Attributes** - Dynamic SAML attribute mapping

---

## ✅ Completed Components

### 1. LDAPRoleService (✅ COMPLETE)

**File**: `src/main/java/com/marklogic/service/LDAPRoleService.java`

**Purpose**: Query in-memory LDAP server for user group memberships and map to application roles.

**Features**:
- Connects to in-memory LDAP (localhost:60389 by default)
- Queries user entries for `memberOf` attributes
- Maps LDAP group DNs to application roles
- Connection pooling (3-10 connections)
- Configurable group-to-role mapping
- Graceful degradation if LDAP unavailable

**Configuration**:
```properties
# Enable LDAP role queries
ldap.role.query.enabled=true
ldap.role.query.host=localhost
ldap.role.query.port=60389
ldap.role.query.base-dn=dc=MarkLogic,dc=Local
ldap.role.query.bind-dn=cn=Directory Manager
ldap.role.query.bind-password=password
ldap.role.query.user-filter=(uid={0})
ldap.role.query.group-attribute=memberOf

# Optional: explicit group-to-role mapping
# Format: ldapGroupDN=appRole,ldapGroupDN2=appRole2
ldap.role.mapping=cn=app-readers,ou=groups,dc=marklogic,dc=local=reader,cn=admins,ou=groups,dc=marklogic,dc=local=admin
```

**Key Methods**:
- `getUserRoles(String username)` - Returns list of roles from LDAP groups
- `getUserAttributes(String username, String... attrs)` - Get arbitrary LDAP attributes
- `isInitialized()` - Check if LDAP connection successful

**Default Behavior**: 
- If no explicit mapping configured, extracts CN from group DN as role name
- Example: `cn=app-readers,ou=groups,...` → role name "app-readers"

---

### 2. RefreshTokenService (✅ COMPLETE)

**File**: `src/main/java/com/marklogic/service/RefreshTokenService.java`

**Purpose**: Manage OAuth 2.0 refresh tokens with single-use token rotation.

**Features**:
- Cryptographically secure token generation (UUID-based)
- In-memory storage (ConcurrentHashMap)
- Single-use tokens (consumed on refresh)
- Token rotation (old token invalidated, new issued)
- Automatic expiration cleanup (periodic timer)
- Configurable token lifetime

**Configuration**:
```properties
# Enable refresh tokens
oauth.refresh.token.enabled=true

# Token lifetime (default 30 days = 2592000 seconds)
oauth.refresh.token.expiry.seconds=2592000

# Cleanup interval (default 1 hour = 3600 seconds)
oauth.refresh.token.cleanup.interval.seconds=3600
```

**Key Methods**:
- `generateRefreshToken(username, scope)` - Create new refresh token
- `validateAndConsumeToken(token)` - Validate and consume (single-use)
- `revokeToken(token)` - Revoke specific token
- `revokeAllUserTokens(username)` - Revoke all tokens for user
- `cleanupExpiredTokens()` - Remove expired tokens

**Security Model**:
- Tokens are 72-character UUIDs (very high entropy)
- Single-use: consumed immediately on refresh
- Token rotation: old token invalidated, new token issued
- Automatic expiration prevents long-lived tokens

**Note**: Current implementation uses in-memory storage. For production at scale, consider:
- Redis for distributed caching
- Database for persistence across restarts
- Encrypted storage for additional security

---

### 3. KerberosOAuthBridgeHandler Enhanced (✅ COMPLETE)

**File**: `src/main/java/com/marklogic/handlers/undertow/KerberosOAuthBridgeHandler.java`

**Changes**:
1. **LDAP Integration**: Added fallback to LDAP role queries
2. **Refresh Tokens**: Generate refresh tokens in token response
3. **New Endpoint**: POST /oauth/refresh for token refresh

**Updated Role Resolution** (Multi-tier fallback):
```
1. Try JsonUserRepository (users.json) first
   ↓ (if user not found)
2. Try LDAPRoleService (query in-memory LDAP)
   ↓ (if LDAP fails or user not found)
3. Use default roles from configuration
```

**Enhanced Token Response**:
```json
{
  "access_token": "eyJhbGc...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "read write admin",
  "refresh_token": "a1b2c3d4-...-xyz789",  ← NEW (Phase 4)
  "principal": "mluser1",
  "kerberos_principal": "mluser1@MARKLOGIC.LOCAL",
  "roles": ["read", "write", "admin"],
  "auth_method": "kerberos-oauth-bridge"
}
```

**New Refresh Endpoint** (✅ IMPLEMENTED):
```bash
# Endpoint: POST /oauth/refresh
# Parameters: grant_type=refresh_token, refresh_token=<token>

curl -X POST http://localhost:8080/oauth/refresh \
  -d "grant_type=refresh_token" \
  -d "refresh_token=a1b2c3d4-...-xyz789"

# Response: New access_token and refresh_token (token rotation)
{
  "access_token": "eyJhbGc...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "read write admin",
  "refresh_token": "new-token-...",  ← New token issued
  "principal": "mluser1",
  "auth_method": "refresh_token"
}
```

**Token Rotation Flow**:
1. Client sends refresh_token to /oauth/refresh
2. Server validates token (checks expiration, not consumed)
3. Server marks old token as consumed and removes from store
4. Server generates new access_token and new refresh_token
5. Client must use new refresh_token for next refresh

---

## ⏳ Remaining Work

### 4. KerberosSAMLBridgeHandler Enhancements (IN PROGRESS)

**File**: `src/main/java/com/marklogic/handlers/undertow/KerberosSAMLBridgeHandler.java`

**Required Changes**:

#### A. Add SAML Assertion Signing (PRIORITY 1)

**Current State**: SAML assertions are unsigned (Phase 3 implementation uses DOM API)

**Discovery**: SAMLAuthHandler already has complete OpenSAML signature implementation (lines 480-490)

**Plan**:
1. Add OpenSAML 4.x imports (from SAMLAuthHandler.java)
2. Refactor `generateSAMLAssertion()` from DOM to OpenSAML builders
3. Add private key and certificate loading (PostConstruct)
4. Implement `signAssertion()` method (adapt from SAMLAuthHandler)
5. Add configuration properties for signing key/certificate paths

**Code Reference** (from SAMLAuthHandler.java lines 480-490):
```java
// Create signature
Signature signature = null;
if (cachedPrivateKey != null && cachedCertificate != null) {
    var sigBuilder = builderFactory.getBuilder(Signature.DEFAULT_ELEMENT_NAME);
    signature = (Signature) sigBuilder.buildObject(Signature.DEFAULT_ELEMENT_NAME);
    signature.setSigningCredential(signingCredential);
    signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
    
    // Add KeyInfo with X509Data
    KeyInfoBuilder keyInfoBuilder = (KeyInfoBuilder) builderFactory.getBuilder(KeyInfo.DEFAULT_ELEMENT_NAME);
    KeyInfo keyInfo = keyInfoBuilder.buildObject();
    X509DataBuilder x509DataBuilder = (X509DataBuilder) builderFactory.getBuilder(X509Data.DEFAULT_ELEMENT_NAME);
    X509Data x509Data = x509DataBuilder.buildObject();
    X509CertificateBuilder certBuilder = (X509CertificateBuilder) builderFactory.getBuilder(X509Certificate.DEFAULT_ELEMENT_NAME);
    X509Certificate cert = certBuilder.buildObject();
    cert.setValue(Base64.getEncoder().encodeToString(cachedCertificate.getEncoded()));
    x509Data.getX509Certificates().add(cert);
    keyInfo.getX509Datas().add(x509Data);
    signature.setKeyInfo(keyInfo);
    
    response.setSignature(signature);
    Marshaller.marshall(response);
    Signer.signObject(signature);
}
```

**Configuration to Add**:
```properties
# SAML assertion signing
saml.signing.enabled=true
saml.signing.key.path=classpath:static/certificates/privkey.pem
saml.signing.certificate.path=classpath:static/certificates/cert.pem
saml.signing.algorithm=RSA-SHA256
```

#### B. Add LDAP Attribute Integration (PRIORITY 2)

**Plan**:
1. Inject LDAPRoleService into handler
2. Update `loadUserAttributes()` to query LDAP
3. Add fallback: JsonUserRepository → LDAP → defaults
4. Query for common attributes: mail, displayName, memberOf, etc.

**Example Code**:
```java
private Map<String, List<String>> loadUserAttributes(String username) {
    // Try JsonUserRepository first
    // ...
    
    // Fallback to LDAP (Phase 4)
    if (ldapRoleService != null && ldapRoleService.isInitialized()) {
        Map<String, List<String>> ldapAttrs = ldapRoleService.getUserAttributes(
            username, 
            "mail", "displayName", "givenName", "sn", "telephoneNumber"
        );
        if (!ldapAttrs.isEmpty()) {
            return ldapAttrs;
        }
    }
    
    // Defaults
    return Map.of("email", List.of(username + "@example.com"));
}
```

#### C. Add Configurable SAML Attributes (PRIORITY 3)

**Plan**:
1. Create AttributeMappingConfig interface
2. Add properties for attribute name customization
3. Support both simple and NameFormat attributes
4. Document configuration format

**Configuration Format**:
```properties
# SAML attribute mapping
# Format: internalName=samlAttributeName:nameFormat

saml.attribute.email=email:urn:oasis:names:tc:SAML:2.0:attrname-format:basic
saml.attribute.displayName=DisplayName:urn:oasis:names:tc:SAML:2.0:attrname-format:basic
saml.attribute.roles=Roles:urn:oasis:names:tc:SAML:2.0:attrname-format:basic

# Or simple format (defaults to basic name format)
saml.attribute.mail=email
saml.attribute.name=displayName
```

**Example Code**:
```java
@Configuration
public class AttributeMappingConfig {
    @Value("${saml.attribute.email:email}")
    private String emailAttributeName;
    
    @Value("${saml.attribute.displayName:displayName}")
    private String displayNameAttributeName;
    
    // ... getters ...
}
```

---

## Testing Strategy

### Unit Tests Needed

1. **LDAPRoleService Tests**:
   - Test LDAP connection
   - Test group-to-role mapping
   - Test fallback when LDAP unavailable
   - Test attribute queries

2. **RefreshTokenService Tests**:
   - Test token generation
   - Test token validation
   - Test token consumption (single-use)
   - Test token expiration
   - Test token rotation
   - Test revocation

3. **OAuth Bridge Tests**:
   - Test LDAP fallback role loading
   - Test refresh token generation
   - Test /oauth/refresh endpoint
   - Test token rotation flow

4. **SAML Bridge Tests** (after completion):
   - Test SAML signature validation
   - Test LDAP attribute loading
   - Test attribute mapping configuration

### Integration Tests

**Test 1: LDAP Role Integration**
```bash
# Setup: User "testuser" exists in LDAP but not users.json
# LDAP groups: cn=app-readers,ou=groups,dc=marklogic,dc=local

kinit testuser@MARKLOGIC.LOCAL
TOKEN_RESPONSE=$(curl -s --negotiate -u : -X POST \
  http://localhost:8080/oauth/token-from-kerberos)

echo "$TOKEN_RESPONSE" | jq .roles
# Expected: ["app-readers"] (from LDAP)
```

**Test 2: Refresh Token Flow**
```bash
# Get initial tokens
RESPONSE=$(curl -s --negotiate -u : -X POST \
  http://localhost:8080/oauth/token-from-kerberos)

ACCESS_TOKEN=$(echo "$RESPONSE" | jq -r .access_token)
REFRESH_TOKEN=$(echo "$RESPONSE" | jq -r .refresh_token)

echo "Initial access token: ${ACCESS_TOKEN:0:50}..."
echo "Refresh token: ${REFRESH_TOKEN:0:50}..."

# Wait a bit (optional, to see different token)
sleep 2

# Use refresh token to get new tokens
NEW_RESPONSE=$(curl -s -X POST http://localhost:8080/oauth/refresh \
  -d "grant_type=refresh_token" \
  -d "refresh_token=$REFRESH_TOKEN")

NEW_ACCESS_TOKEN=$(echo "$NEW_RESPONSE" | jq -r .access_token)
NEW_REFRESH_TOKEN=$(echo "$NEW_RESPONSE" | jq -r .refresh_token)

echo "New access token: ${NEW_ACCESS_TOKEN:0:50}..."
echo "New refresh token: ${NEW_REFRESH_TOKEN:0:50}..."

# Verify old refresh token cannot be reused (token rotation)
OLD_REUSE=$(curl -s -X POST http://localhost:8080/oauth/refresh \
  -d "grant_type=refresh_token" \
  -d "refresh_token=$REFRESH_TOKEN")

echo "$OLD_REUSE" | jq .error
# Expected: "invalid_grant" with "Token already used" message
```

**Test 3: SAML Signed Assertions** (after implementation)
```bash
# Get signed SAML assertion
SAML=$(curl -s --negotiate -u : -X POST \
  http://localhost:8080/saml/assertion-from-kerberos)

# Check signature presence
echo "$SAML" | grep -o "<Signature" && echo "✓ Signature found"

# Validate signature (using xmlsec1 or Java validator)
echo "$SAML" | xmlsec1 --verify --trusted-pem cert.pem
# Expected: Signature verification successful
```

**Test 4: LDAP SAML Attributes** (after implementation)
```bash
# User has LDAP attributes: mail=test@example.com, displayName=Test User

kinit testuser@MARKLOGIC.LOCAL
SAML=$(curl -s --negotiate -u : -X POST \
  http://localhost:8080/saml/assertion-from-kerberos)

# Check attributes from LDAP
echo "$SAML" | grep -o 'Name="mail"' && echo "✓ Email attribute found"
echo "$SAML" | grep -o 'test@example.com' && echo "✓ Email value correct"
```

---

## Build and Run

### Compile Phase 4 Code

```bash
cd /Users/martin/Documents/Projects/MLEAProxy
mvn clean compile

# Expected: BUILD SUCCESS
# New classes:
#   - LDAPRoleService.class
#   - RefreshTokenService.class
#   - Enhanced KerberosOAuthBridgeHandler.class
```

### Run Application

```bash
# Configure LDAP role queries
export LDAP_ROLE_QUERY_ENABLED=true

# Configure refresh tokens
export OAUTH_REFRESH_TOKEN_ENABLED=true
export OAUTH_REFRESH_TOKEN_EXPIRY_SECONDS=2592000  # 30 days

# Start application
mvn spring-boot:run
```

### Verify Services

```bash
# Check logs for initialization
tail -f target/logs/mleaproxy.log | grep -E "(LDAP|Refresh)"

# Expected output:
# LDAPRoleService - LDAP connection successful
# RefreshTokenService - Refresh token service initialized
# KerberosOAuthBridgeHandler - Kerberos-OAuth bridge initialized
```

---

## Known Issues and Limitations

### Current Limitations

1. **In-Memory Refresh Tokens**:
   - Tokens lost on application restart
   - Not suitable for distributed deployments
   - **Recommendation**: For production, use Redis or database storage

2. **LDAP Connection Not Pooled at Startup**:
   - Connection pool created in @PostConstruct
   - If LDAP unavailable at startup, remains disabled
   - **Recommendation**: Add health check and auto-reconnect

3. **No Token Introspection Endpoint**:
   - Cannot check token validity without using it
   - **Recommendation**: Add /oauth/introspect endpoint (OAuth 2.0 RFC 7662)

4. **SAML Signing Not Yet Implemented**:
   - Assertions still unsigned (Phase 3 state)
   - **Status**: Work in progress

### Future Enhancements

1. **Persistent Token Storage**:
   - Add Redis support for refresh tokens
   - Add database support for audit trail
   - Support distributed deployments

2. **Advanced LDAP Features**:
   - LDAP caching for performance
   - Multiple LDAP servers (failover)
   - Nested group resolution

3. **OAuth 2.0 Compliance**:
   - Token revocation endpoint (RFC 7009)
   - Token introspection endpoint (RFC 7662)
   - JWKS endpoint for public key distribution

4. **SAML Enhancements**:
   - Encrypted assertions (in addition to signing)
   - Support for multiple SPs (metadata registry)
   - Dynamic SP configuration

---

## Configuration Reference

### Complete Phase 4 Properties

```properties
# ===================================================================
# PHASE 4: KERBEROS OAUTH/SAML PRODUCTION ENHANCEMENTS
# ===================================================================

# ----- LDAP Role Integration -----
ldap.role.query.enabled=true
ldap.role.query.host=localhost
ldap.role.query.port=60389
ldap.role.query.base-dn=dc=MarkLogic,dc=Local
ldap.role.query.bind-dn=cn=Directory Manager
ldap.role.query.bind-password=password
ldap.role.query.user-filter=(uid={0})
ldap.role.query.group-attribute=memberOf

# Optional: explicit group-to-role mapping
ldap.role.mapping=cn=app-readers,ou=groups,dc=marklogic,dc=local=reader

# ----- OAuth Refresh Tokens -----
oauth.refresh.token.enabled=true
oauth.refresh.token.expiry.seconds=2592000  # 30 days
oauth.refresh.token.cleanup.interval.seconds=3600  # 1 hour

# ----- SAML Assertion Signing (TODO: implement) -----
saml.signing.enabled=true
saml.signing.key.path=classpath:static/certificates/privkey.pem
saml.signing.certificate.path=classpath:static/certificates/cert.pem
saml.signing.algorithm=RSA-SHA256

# ----- SAML Attribute Mapping (TODO: implement) -----
saml.attribute.email=email
saml.attribute.displayName=displayName
saml.attribute.roles=Roles
```

---

## File Inventory

### New Files (Phase 4)

1. **LDAPRoleService.java** (353 lines) ✅
   - Path: `src/main/java/com/marklogic/service/LDAPRoleService.java`
   - Purpose: Query LDAP for group memberships and map to roles
   - Status: Complete, compiles

2. **RefreshTokenService.java** (379 lines) ✅
   - Path: `src/main/java/com/marklogic/service/RefreshTokenService.java`
   - Purpose: Manage OAuth 2.0 refresh tokens with rotation
   - Status: Complete, compiles (minor unused field warnings)

3. **KERBEROS_PHASE4_IN_PROGRESS.md** (this file) ✅
   - Path: `/Users/martin/Documents/Projects/MLEAProxy/KERBEROS_PHASE4_IN_PROGRESS.md`
   - Purpose: Phase 4 implementation documentation
   - Status: Complete

### Modified Files (Phase 4)

1. **KerberosOAuthBridgeHandler.java** (enhanced) ✅
   - Path: `src/main/java/com/marklogic/handlers/undertow/KerberosOAuthBridgeHandler.java`
   - Changes:
     - Added LDAP role fallback in `loadUserRoles()`
     - Added refresh token generation in `exchangeKerberosForOAuth()`
     - Added new endpoint: `POST /oauth/refresh`
   - Status: Complete, compiles

2. **KerberosSAMLBridgeHandler.java** (pending) ⏳
   - Path: `src/main/java/com/marklogic/handlers/undertow/KerberosSAMLBridgeHandler.java`
   - Planned Changes:
     - Refactor to OpenSAML builders (from DOM)
     - Add SAML assertion signing
     - Add LDAP attribute integration
     - Add configurable attribute mapping
   - Status: Not started (Phase 3 code unchanged)

### Compilation Status

```bash
mvn clean compile
```

**Expected Issues**:
- RefreshTokenService.java: 3 unused field warnings (non-blocking)
- All code compiles successfully
- No breaking changes

---

## Next Steps

### Immediate Tasks

1. **Complete SAML Bridge Enhancement** (2-3 hours):
   - Refactor KerberosSAMLBridgeHandler to use OpenSAML builders
   - Add signature support (adapt from SAMLAuthHandler)
   - Add LDAP attribute integration
   - Add configurable attribute mapping

2. **Test Complete Phase 4 Flow** (1-2 hours):
   - Test OAuth with LDAP roles
   - Test refresh token flow
   - Test SAML signed assertions
   - Test SAML LDAP attributes

3. **Documentation Update** (1 hour):
   - Complete Phase 4 completion document
   - Update README.md with Phase 4 features
   - Create Phase 4 quick reference guide

### Long-term Improvements

1. **Persistent Token Storage**:
   - Implement Redis backend for RefreshTokenService
   - Add token audit trail to database

2. **Advanced LDAP**:
   - Add connection health monitoring
   - Implement auto-reconnect
   - Add nested group support

3. **OAuth Compliance**:
   - Add token introspection endpoint
   - Add token revocation endpoint
   - Publish JWKS endpoint

4. **SAML Advanced Features**:
   - Add assertion encryption
   - Support multiple signing algorithms
   - Add metadata registry

---

## Summary

**Phase 4 Progress**: 60% Complete

**✅ Complete**:
- LDAPRoleService (LDAP group queries)
- RefreshTokenService (OAuth refresh tokens)
- KerberosOAuthBridgeHandler enhanced (LDAP + refresh)
- OAuth /refresh endpoint implemented

**⏳ In Progress**:
- KerberosSAMLBridgeHandler enhancements (SAML signing + LDAP + attributes)

**📋 Remaining**:
- SAML assertion signing implementation
- SAML LDAP attribute integration
- Configurable SAML attributes
- Comprehensive testing
- Final documentation

**Build Status**: ✅ Compiles successfully (minor warnings only)

**Ready For**: Completing SAML bridge enhancements, then full integration testing

---

## Related Documentation

- [Phase 1: Kerberos KDC](KERBEROS_PHASE1_COMPLETE.md)
- [Phase 2: SPNEGO Authentication](KERBEROS_PHASE2_COMPLETE.md)
- [Phase 3: OAuth/SAML Bridges](KERBEROS_PHASE3_COMPLETE.md)
- [Phase 3 Quick Reference](KERBEROS_PHASE3_QUICK_REF.md)
- [Testing Guide](TESTING_GUIDE.md)
- [Configuration Guide](CONFIGURATION_GUIDE.md)
