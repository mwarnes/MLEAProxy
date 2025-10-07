# Kerberos Phase 4: Quick Reference Guide

## What's New in Phase 4

Phase 4 adds production-ready enhancements to the Kerberos OAuth/SAML bridges:

✅ **LDAP Role Integration** - Query LDAP for group-based roles  
✅ **OAuth Refresh Tokens** - Token refresh without re-authentication  
✅ **Enhanced OAuth Bridge** - Multi-tier role resolution  
⏳ **SAML Signing** - Coming soon  
⏳ **Configurable Attributes** - Coming soon  

---

## Quick Start

### 1. Enable Phase 4 Features

Add to your properties file:

```properties
# Enable LDAP role queries
ldap.role.query.enabled=true
ldap.role.query.host=localhost
ldap.role.query.port=60389

# Enable refresh tokens
oauth.refresh.token.enabled=true
oauth.refresh.token.expiry.seconds=2592000  # 30 days
```

### 2. Get OAuth Token with Refresh Token

```bash
# Authenticate with Kerberos
kinit mluser1@MARKLOGIC.LOCAL

# Exchange for OAuth token (now includes refresh_token)
TOKEN_RESPONSE=$(curl -s --negotiate -u : -X POST \
  http://localhost:8080/oauth/token-from-kerberos)

echo "$TOKEN_RESPONSE" | jq .

# Response includes refresh_token:
# {
#   "access_token": "eyJhbGc...",
#   "token_type": "Bearer",
#   "expires_in": 3600,
#   "refresh_token": "a1b2c3d4-e5f6-...",  ← NEW!
#   "principal": "mluser1",
#   "roles": ["app-reader", "app-writer"]
# }
```

### 3. Use Refresh Token

```bash
# Extract refresh token
REFRESH_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r .refresh_token)

# Get new access token using refresh token (no Kerberos needed!)
curl -X POST http://localhost:8080/oauth/refresh \
  -d "grant_type=refresh_token" \
  -d "refresh_token=$REFRESH_TOKEN" | jq .

# Response includes new access_token and new refresh_token
# {
#   "access_token": "eyJhbGc...",  ← New token!
#   "refresh_token": "x9y8z7w6-...",  ← New refresh token!
#   "token_type": "Bearer",
#   "expires_in": 3600,
#   "principal": "mluser1"
# }
```

---

## LDAP Role Integration

### How It Works

**Multi-Tier Role Resolution**:

```
User requests OAuth token
          ↓
1. Check users.json (JsonUserRepository)
   ↓ (if not found)
2. Query LDAP for group memberships
   ↓ (if not found)
3. Use default roles from config
```

### Example: User in LDAP but not users.json

```bash
# User "bob" exists in LDAP with groups:
# - cn=app-readers,ou=groups,dc=marklogic,dc=local
# - cn=developers,ou=groups,dc=marklogic,dc=local

kinit bob@MARKLOGIC.LOCAL
curl -s --negotiate -u : -X POST \
  http://localhost:8080/oauth/token-from-kerberos | jq .roles

# Output: ["app-readers", "developers"]
# (roles automatically extracted from LDAP groups!)
```

### Configure Group-to-Role Mapping

```properties
# Map LDAP groups to application roles
ldap.role.mapping=\
  cn=app-readers,ou=groups,dc=marklogic,dc=local=reader,\
  cn=developers,ou=groups,dc=marklogic,dc=local=developer,\
  cn=admins,ou=groups,dc=marklogic,dc=local=admin
```

---

## Refresh Token Flow

### Standard Flow

```
1. User gets Kerberos ticket (kinit)
2. Exchange for OAuth token (includes refresh_token)
3. Use access_token for API calls
4. When access_token expires:
   - Send refresh_token to /oauth/refresh
   - Get new access_token and refresh_token
5. Repeat step 4 until refresh_token expires (30 days default)
```

### Token Rotation Security

**Old refresh token is consumed**:
- After successful refresh, old token cannot be reused
- New refresh token is issued
- Prevents replay attacks

```bash
# Try to reuse old refresh token
curl -X POST http://localhost:8080/oauth/refresh \
  -d "grant_type=refresh_token" \
  -d "refresh_token=$OLD_TOKEN" | jq .

# Response:
# {
#   "error": "invalid_grant",
#   "error_description": "Token already used"
# }
```

### Configure Token Lifetime

```properties
# Access token lifetime (default 1 hour)
oauth.token.expiration.seconds=3600

# Refresh token lifetime (default 30 days)
oauth.refresh.token.expiry.seconds=2592000
```

---

## API Reference

### POST /oauth/token-from-kerberos (Enhanced)

**Description**: Exchange Kerberos ticket for OAuth JWT (now with refresh token)

**Request**:
```bash
curl --negotiate -u : -X POST http://localhost:8080/oauth/token-from-kerberos
```

**Response** (Phase 4 Enhanced):
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjE3Y2UwNDc0MjgyYmEwMDEifQ...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "app-reader app-writer",
  "refresh_token": "a1b2c3d4-e5f6-7890-abcd-ef1234567890-x9y8z7w6-v5u4-t3s2-r1q0-p9o8n7m6l5k4",
  "principal": "mluser1",
  "kerberos_principal": "mluser1@MARKLOGIC.LOCAL",
  "roles": ["app-reader", "app-writer"],
  "auth_method": "kerberos-oauth-bridge"
}
```

**New Field**:
- `refresh_token` - Long-lived token for refreshing access token

---

### POST /oauth/refresh (NEW in Phase 4)

**Description**: Refresh OAuth access token using refresh token

**Request**:
```bash
curl -X POST http://localhost:8080/oauth/refresh \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=refresh_token" \
  -d "refresh_token=YOUR_REFRESH_TOKEN_HERE"
```

**Parameters**:
- `grant_type` (required) - Must be "refresh_token"
- `refresh_token` (required) - The refresh token from previous token response

**Success Response** (200 OK):
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjE3Y2UwNDc0MjgyYmEwMDEifQ...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "app-reader app-writer",
  "refresh_token": "NEW_REFRESH_TOKEN_HERE",
  "principal": "mluser1",
  "auth_method": "refresh_token"
}
```

**Error Responses**:

Invalid grant type:
```json
{
  "error": "invalid_request",
  "error_description": "grant_type must be 'refresh_token'"
}
```

Token expired or invalid:
```json
{
  "error": "invalid_grant",
  "error_description": "Token expired"
}
```

Token already used:
```json
{
  "error": "invalid_grant",
  "error_description": "Token already used"
}
```

Refresh tokens disabled:
```json
{
  "error": "unsupported_grant_type",
  "error_description": "Refresh tokens are not enabled"
}
```

---

## Complete Usage Example

### Scenario: Long-Running Application

```bash
#!/bin/bash
# Phase 4 OAuth refresh token example

# 1. Initial authentication with Kerberos
echo "Step 1: Getting Kerberos ticket..."
kinit mluser1@MARKLOGIC.LOCAL

# 2. Exchange for OAuth tokens
echo "Step 2: Exchanging for OAuth tokens..."
TOKENS=$(curl -s --negotiate -u : -X POST \
  http://localhost:8080/oauth/token-from-kerberos)

ACCESS_TOKEN=$(echo "$TOKENS" | jq -r .access_token)
REFRESH_TOKEN=$(echo "$TOKENS" | jq -r .refresh_token)

echo "Got access token: ${ACCESS_TOKEN:0:50}..."
echo "Got refresh token: ${REFRESH_TOKEN:0:50}..."

# 3. Use access token for API calls
echo "Step 3: Making API calls with access token..."
for i in {1..5}; do
  curl -s -H "Authorization: Bearer $ACCESS_TOKEN" \
    http://localhost:8080/api/some-endpoint
  sleep 1
done

# 4. Simulate access token expiration (wait or just refresh immediately)
echo "Step 4: Access token will expire, refreshing..."
sleep 2

# 5. Refresh access token using refresh token (no Kerberos needed!)
NEW_TOKENS=$(curl -s -X POST http://localhost:8080/oauth/refresh \
  -d "grant_type=refresh_token" \
  -d "refresh_token=$REFRESH_TOKEN")

NEW_ACCESS_TOKEN=$(echo "$NEW_TOKENS" | jq -r .access_token)
NEW_REFRESH_TOKEN=$(echo "$NEW_TOKENS" | jq -r .refresh_token)

echo "Got new access token: ${NEW_ACCESS_TOKEN:0:50}..."
echo "Got new refresh token: ${NEW_REFRESH_TOKEN:0:50}..."

# 6. Continue using new tokens
echo "Step 5: Making API calls with new access token..."
for i in {1..5}; do
  curl -s -H "Authorization: Bearer $NEW_ACCESS_TOKEN" \
    http://localhost:8080/api/some-endpoint
  sleep 1
done

echo "Done! Application ran for 15 seconds without re-authentication."
```

---

## Configuration Reference

### Minimal Configuration

```properties
# Enable Phase 4 features
ldap.role.query.enabled=true
oauth.refresh.token.enabled=true
```

### Production Configuration

```properties
# ===== LDAP Role Integration =====
ldap.role.query.enabled=true
ldap.role.query.host=localhost
ldap.role.query.port=60389
ldap.role.query.base-dn=dc=MarkLogic,dc=Local
ldap.role.query.bind-dn=cn=Directory Manager
ldap.role.query.bind-password=password
ldap.role.query.user-filter=(uid={0})
ldap.role.query.group-attribute=memberOf

# Map LDAP groups to application roles
ldap.role.mapping=\
  cn=app-readers,ou=groups,dc=marklogic,dc=local=reader,\
  cn=app-writers,ou=groups,dc=marklogic,dc=local=writer,\
  cn=admins,ou=groups,dc=marklogic,dc=local=admin

# ===== OAuth Refresh Tokens =====
oauth.refresh.token.enabled=true
oauth.refresh.token.expiry.seconds=2592000  # 30 days
oauth.refresh.token.cleanup.interval.seconds=3600  # 1 hour

# ===== OAuth Access Tokens =====
oauth.token.expiration.seconds=3600  # 1 hour
oauth.jwt.issuer=mleaproxy-oauth-server
oauth.signing.key.path=classpath:static/certificates/privkey.pem
oauth.default.roles=user
```

---

## Testing

### Test LDAP Role Resolution

```bash
# Create test user in LDAP (or use existing)
# User should NOT exist in users.json

kinit testuser@MARKLOGIC.LOCAL
TOKEN_RESPONSE=$(curl -s --negotiate -u : -X POST \
  http://localhost:8080/oauth/token-from-kerberos)

# Check if roles came from LDAP
echo "$TOKEN_RESPONSE" | jq .roles
# Expected: roles from LDAP groups

# Check logs for confirmation
tail -f logs/mleaproxy.log | grep "LDAP"
# Expected: "Loaded X roles from LDAP for user: testuser"
```

### Test Refresh Token Flow

```bash
# 1. Get initial tokens
RESPONSE=$(curl -s --negotiate -u : -X POST \
  http://localhost:8080/oauth/token-from-kerberos)

ACCESS_TOKEN=$(echo "$RESPONSE" | jq -r .access_token)
REFRESH_TOKEN=$(echo "$RESPONSE" | jq -r .refresh_token)

echo "Initial tokens received"
echo "Access token: ${ACCESS_TOKEN:0:30}..."
echo "Refresh token: ${REFRESH_TOKEN:0:30}..."

# 2. Use refresh token
NEW_RESPONSE=$(curl -s -X POST http://localhost:8080/oauth/refresh \
  -d "grant_type=refresh_token" \
  -d "refresh_token=$REFRESH_TOKEN")

NEW_ACCESS_TOKEN=$(echo "$NEW_RESPONSE" | jq -r .access_token)
NEW_REFRESH_TOKEN=$(echo "$NEW_RESPONSE" | jq -r .refresh_token)

echo "New tokens received"
echo "New access token: ${NEW_ACCESS_TOKEN:0:30}..."
echo "New refresh token: ${NEW_REFRESH_TOKEN:0:30}..."

# 3. Verify old refresh token is consumed
OLD_RETRY=$(curl -s -X POST http://localhost:8080/oauth/refresh \
  -d "grant_type=refresh_token" \
  -d "refresh_token=$REFRESH_TOKEN")

echo "Attempting to reuse old token..."
echo "$OLD_RETRY" | jq .
# Expected: {"error":"invalid_grant","error_description":"Token already used"}

echo "✅ Token rotation working correctly"
```

### Test Token Expiration

```bash
# Configure short expiration for testing
# oauth.refresh.token.expiry.seconds=10

# Get token
RESPONSE=$(curl -s --negotiate -u : -X POST \
  http://localhost:8080/oauth/token-from-kerberos)
REFRESH_TOKEN=$(echo "$RESPONSE" | jq -r .refresh_token)

# Wait for expiration
echo "Waiting for token to expire..."
sleep 15

# Try to use expired token
curl -s -X POST http://localhost:8080/oauth/refresh \
  -d "grant_type=refresh_token" \
  -d "refresh_token=$REFRESH_TOKEN" | jq .

# Expected: {"error":"invalid_grant","error_description":"Token expired"}
```

---

## Troubleshooting

### LDAP Connection Issues

**Problem**: "LDAP role service not initialized"

**Solution**:
1. Check LDAP server is running:
   ```bash
   netstat -an | grep 60389
   ```
2. Verify LDAP credentials in config
3. Check firewall allows connection to LDAP port

**Check Logs**:
```bash
tail -f logs/mleaproxy.log | grep "LDAPRoleService"
```

### Refresh Token Issues

**Problem**: "Refresh tokens are not enabled"

**Solution**:
```properties
oauth.refresh.token.enabled=true
```

**Problem**: "Token not found"

**Cause**: Token expired or server restarted (in-memory storage)

**Solution**:
- Get new token with Kerberos authentication
- For production: use Redis or database storage

**Problem**: "Token already used"

**Cause**: Attempting to reuse consumed token (token rotation)

**Solution**: This is expected behavior. Use the new refresh token from the last refresh response.

### Role Resolution Issues

**Problem**: User gets default roles instead of LDAP roles

**Debug**:
```bash
# Check user exists in LDAP
ldapsearch -H ldap://localhost:60389 \
  -D "cn=Directory Manager" \
  -w password \
  -b "dc=MarkLogic,dc=Local" \
  "(uid=username)"

# Check user has group memberships
ldapsearch -H ldap://localhost:60389 \
  -D "cn=Directory Manager" \
  -w password \
  -b "dc=MarkLogic,dc=Local" \
  "(uid=username)" memberOf

# Check logs for role resolution
tail -f logs/mleaproxy.log | grep "roles"
```

---

## Performance Considerations

### LDAP Connection Pooling

LDAPRoleService uses connection pool (3-10 connections):
- Minimum: 3 connections
- Maximum: 10 connections
- Automatic connection lifecycle management

### Refresh Token Storage

**Current Implementation**: In-memory (ConcurrentHashMap)
- ✅ Fast lookups
- ✅ No external dependencies
- ⚠️ Tokens lost on restart
- ⚠️ Not suitable for distributed deployments

**Production Recommendation**: Use Redis
- Persistent across restarts
- Supports distributed systems
- Automatic expiration (TTL)
- High performance

### Refresh Token Cleanup

**Automatic cleanup**:
- Runs every 1 hour (default)
- Removes expired tokens
- Configurable interval:
  ```properties
  oauth.refresh.token.cleanup.interval.seconds=3600
  ```

---

## Migration from Phase 3

### Changes from Phase 3

1. **OAuth Token Response**: Now includes `refresh_token` field
2. **New Endpoint**: POST /oauth/refresh
3. **Role Resolution**: Now checks LDAP if user not in users.json
4. **Dependencies**: Added LDAPRoleService, RefreshTokenService

### Backward Compatibility

✅ **Fully backward compatible**:
- Existing OAuth clients work without changes
- `refresh_token` is optional (can be ignored)
- LDAP integration is optional (disabled by default)
- Phase 3 endpoints unchanged

### Upgrading

```bash
# 1. Pull Phase 4 code
git pull origin main

# 2. Compile
mvn clean compile

# 3. Add configuration (optional)
cat >> application.properties << EOF
ldap.role.query.enabled=true
oauth.refresh.token.enabled=true
EOF

# 4. Restart application
mvn spring-boot:run
```

---

## Security Best Practices

### Refresh Token Security

1. **Store refresh tokens securely**: Never log or expose them
2. **Use HTTPS**: Always use TLS for token transmission
3. **Token rotation**: Use new refresh token from each refresh response
4. **Monitor usage**: Log token refresh events
5. **Revoke on logout**: Call revoke endpoint when user logs out

### LDAP Security

1. **Use LDAPS**: Enable TLS for LDAP connections in production
2. **Least privilege**: Use read-only LDAP account
3. **Validate DNs**: LDAPRoleService validates LDAP DNs
4. **Connection pooling**: Limits concurrent LDAP connections

### OAuth Security

1. **Short-lived access tokens**: 1 hour default
2. **Long-lived refresh tokens**: 30 days default
3. **Token introspection**: Validate tokens before use
4. **Audience validation**: Verify token issuer

---

## Next Steps

### Phase 4 Completion

Remaining enhancements:
- [ ] SAML assertion signing (OpenSAML signature support)
- [ ] LDAP attributes in SAML assertions
- [ ] Configurable SAML attribute mapping
- [ ] Comprehensive testing

### Beyond Phase 4

Future improvements:
- [ ] Persistent refresh token storage (Redis/Database)
- [ ] Token introspection endpoint (RFC 7662)
- [ ] Token revocation endpoint (RFC 7009)
- [ ] JWKS endpoint for public key distribution
- [ ] Advanced LDAP features (nested groups, caching)

---

## Related Documentation

- [Phase 4 In Progress](KERBEROS_PHASE4_IN_PROGRESS.md) - Detailed implementation notes
- [Phase 3 Complete](KERBEROS_PHASE3_COMPLETE.md) - OAuth/SAML bridge fundamentals
- [Phase 3 Quick Reference](KERBEROS_PHASE3_QUICK_REF.md) - Phase 3 API reference
- [Configuration Guide](CONFIGURATION_GUIDE.md) - Complete configuration reference
- [Testing Guide](TESTING_GUIDE.md) - Comprehensive testing guide

---

**Phase 4 Status**: 60% Complete ✅  
**Build Status**: Compiles Successfully ✅  
**Ready For**: Testing and SAML enhancements
