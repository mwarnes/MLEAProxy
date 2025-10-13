# JJWT Migration Plan - Nimbus to JJWT

**Date:** 2025-10-04  
**Status:** 🔄 IN PROGRESS  
**Goal:** Migrate OAuth token handling from Nimbus JOSE JWT to JJWT (io.jsonwebtoken)

## Executive Summary

Migrate the OAuth 2.0 token endpoint from **Nimbus JOSE JWT** to **JJWT (io.jsonwebtoken)** for better Java integration, cleaner API, and improved maintainability.

### Benefits of JJWT

✅ **Better Java Integration** - More idiomatic Java API  
✅ **Fluent Builder API** - Cleaner token creation  
✅ **Automatic Type Conversion** - Better type safety  
✅ **Comprehensive Documentation** - Well-documented library  
✅ **Active Development** - Regular updates and security patches  
✅ **Smaller Footprint** - Fewer dependencies  
✅ **Better Jackson Integration** - Native JSON support  

---

## Current State (Nimbus JOSE JWT 9.37.3)

### Dependencies
```xml
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <version>9.37.3</version>
</dependency>
```

### Current Implementation
- **File**: `OAuthTokenHandler.java`
- **Algorithm**: RS256 (RSA-SHA256)
- **Key Format**: RSA Private Key (PKCS8)
- **Token Type**: JWT with custom claims
- **Expiration**: Configurable (default 1 hour)

### Current Nimbus Code Pattern
```java
// Build claims
JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
    .issuer(jwtIssuer)
    .subject(username)
    .audience(clientId)
    .issueTime(Date.from(now))
    .expirationTime(Date.from(expiration))
    .jwtID(UUID.randomUUID().toString())
    .claim("roles", roles);

JWTClaimsSet claims = claimsBuilder.build();

// Sign with RS256
JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
    .keyID(UUID.randomUUID().toString())
    .type(JOSEObjectType.JWT)
    .build();

SignedJWT signedJWT = new SignedJWT(header, claims);
JWSSigner signer = new RSASSASigner(privateKey);
signedJWT.sign(signer);

return signedJWT.serialize();
```

---

## Target State (JJWT 0.12.x)

### New Dependencies
```xml
<!-- JJWT API -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>

<!-- JJWT Implementation -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>

<!-- JJWT Jackson (JSON processing) -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

### New JJWT Code Pattern
```java
// Build and sign token in one fluent chain
String token = Jwts.builder()
    .issuer(jwtIssuer)
    .subject(username)
    .audience().add(clientId).and()
    .issuedAt(Date.from(now))
    .expiration(Date.from(expiration))
    .id(UUID.randomUUID().toString())
    .claim("client_id", clientId)
    .claim("grant_type", grantType)
    .claim("username", username)
    .claim("scope", scope)
    .claim("roles", roles)
    .header()
        .keyId(UUID.randomUUID().toString())
        .type("JWT")
    .and()
    .signWith(privateKey, Jwts.SIG.RS256)
    .compact();

return token;
```

---

## Migration Steps

### Phase 1: Dependency Management ✅

**Step 1.1:** Add JJWT dependencies to pom.xml
```xml
<!-- Add after OAuth2 dependencies -->
<!-- JJWT - Modern JWT library -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

**Step 1.2:** Remove Nimbus dependency (after migration complete)
```xml
<!-- REMOVE AFTER MIGRATION -->
<!--
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <version>9.37.3</version>
</dependency>
-->
```

### Phase 2: Code Migration ✅

**Step 2.1:** Update imports in `OAuthTokenHandler.java`

**Remove:**
```java
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
```

**Add:**
```java
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
```

**Step 2.2:** Update `generateAccessToken()` method

Replace Nimbus implementation with JJWT fluent API.

**Step 2.3:** Update exception handling

- `JOSEException` → Generic `Exception` or `RuntimeException`
- JJWT throws unchecked exceptions by default

**Step 2.4:** Update key loading (if needed)

JJWT works with standard `PrivateKey` interface - no changes needed.

### Phase 3: Testing ✅

**Step 3.1:** Run existing OAuth tests
```bash
mvn test -Dtest=OAuthTokenHandlerTest
```

**Step 3.2:** Verify token structure
- Decode generated tokens using jwt.io
- Verify all claims are present
- Check header contains correct algorithm (RS256)

**Step 3.3:** Integration testing
```bash
# Test all grant types
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "client_id=test" \
  -d "client_secret=test" \
  -d "username=martin" \
  -d "password=pass" \
  -d "roles=admin,user"
```

### Phase 4: Documentation Updates ✅

**Step 4.1:** Update `OAUTH_TOKEN_ENDPOINT.md`
- Update library references
- Update code examples

**Step 4.2:** Update README.md
- Update dependencies section
- Update OAuth documentation

**Step 4.3:** Create `JJWT_MIGRATION_COMPLETE.md`
- Document changes made
- Include before/after comparisons
- List benefits achieved

### Phase 5: Cleanup ✅

**Step 5.1:** Remove Nimbus dependency from pom.xml

**Step 5.2:** Update CODE_FIXES_SUMMARY_2025.md
- Add JJWT migration section
- Document improvements

**Step 5.3:** Verify no remaining Nimbus imports
```bash
grep -r "com.nimbusds" src/
```

---

## API Comparison

### Token Creation

**Nimbus (Before):**
```java
JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
    .issuer("issuer")
    .subject("subject")
    .audience("audience")
    .issueTime(new Date())
    .expirationTime(expDate)
    .jwtID(UUID.randomUUID().toString())
    .claim("custom", value);

JWTClaimsSet claims = claimsBuilder.build();

JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
    .keyID("key-id")
    .type(JOSEObjectType.JWT)
    .build();

SignedJWT signedJWT = new SignedJWT(header, claims);
JWSSigner signer = new RSASSASigner(privateKey);
signedJWT.sign(signer);
String token = signedJWT.serialize();
```

**JJWT (After):**
```java
String token = Jwts.builder()
    .issuer("issuer")
    .subject("subject")
    .audience().add("audience").and()
    .issuedAt(new Date())
    .expiration(expDate)
    .id(UUID.randomUUID().toString())
    .claim("custom", value)
    .header()
        .keyId("key-id")
        .type("JWT")
    .and()
    .signWith(privateKey, Jwts.SIG.RS256)
    .compact();
```

**Improvements:**
- ✅ Fluent API - single chain
- ✅ No separate signing step
- ✅ More readable
- ✅ Type-safe

### Token Parsing (For Future Use)

**Nimbus:**
```java
SignedJWT jwt = SignedJWT.parse(token);
JWTClaimsSet claims = jwt.getJWTClaimsSet();
String subject = claims.getSubject();
```

**JJWT:**
```java
Claims claims = Jwts.parser()
    .verifyWith(publicKey)
    .build()
    .parseSignedClaims(token)
    .getPayload();
String subject = claims.getSubject();
```

---

## Risk Assessment

### Low Risk Items ✅
- API is similar, straightforward mapping
- Same RSA key format supported
- Same JWT structure produced
- Existing tests validate behavior

### Medium Risk Items ⚠️
- Exception handling changes (checked → unchecked)
- Need to verify all claims transfer correctly
- Token format must remain compatible

### Mitigation Strategies
1. ✅ Keep both libraries during transition
2. ✅ Run comprehensive tests
3. ✅ Decode and compare tokens
4. ✅ Gradual rollout with fallback option

---

## Testing Checklist

### Unit Tests
- [ ] Test password grant type
- [ ] Test client_credentials grant type
- [ ] Test with roles
- [ ] Test without roles
- [ ] Test with scope
- [ ] Test without scope
- [ ] Test multiple roles
- [ ] Test error scenarios
- [ ] Test token expiration claim
- [ ] Test all custom claims

### Integration Tests
- [ ] Verify token structure with jwt.io
- [ ] Test with MarkLogic REST API
- [ ] Verify RS256 signature
- [ ] Check token expiration
- [ ] Validate all claims present
- [ ] Test special characters in username
- [ ] Test long role lists
- [ ] Test empty role arrays

### Performance Tests
- [ ] Token generation time
- [ ] Memory usage
- [ ] Throughput comparison

---

## Rollback Plan

If issues arise:

1. **Immediate:** Comment out JJWT code, uncomment Nimbus code
2. **Revert:** Git revert to previous commit
3. **Analysis:** Review logs and test failures
4. **Fix:** Address issues and re-attempt

---

## Timeline

| Phase | Duration | Status |
|-------|----------|--------|
| Dependency Management | 15 min | 🔄 Ready |
| Code Migration | 45 min | 🔄 Ready |
| Testing | 30 min | ⏳ Pending |
| Documentation | 20 min | ⏳ Pending |
| Cleanup | 10 min | ⏳ Pending |
| **Total** | **2 hours** | 🔄 **In Progress** |

---

## Success Criteria

✅ All 18 existing tests pass  
✅ OAuth token endpoint returns valid JWT tokens  
✅ Token structure matches previous implementation  
✅ RS256 signature verifies correctly  
✅ All custom claims present in token  
✅ No Nimbus dependencies remaining  
✅ Documentation updated  
✅ Clean build with no warnings  

---

## Additional OAuth Enhancements

While migrating, consider these enhancements:

### 1. Token Validation Endpoint
```java
@PostMapping("/oauth/introspect")
public ResponseEntity<Map<String, Object>> introspect(
    @RequestParam("token") String token) {
    // Validate and return token details
}
```

### 2. Public Key Endpoint (JWKS)
```java
@GetMapping("/.well-known/jwks.json")
public Map<String, Object> jwks() {
    // Return public key in JWKS format
}
```

### 3. Refresh Token Support
```java
// Add refresh token to response
response.put("refresh_token", generateRefreshToken());
response.put("token_type", "Bearer");
```

### 4. Token Revocation
```java
@PostMapping("/oauth/revoke")
public ResponseEntity<Void> revoke(
    @RequestParam("token") String token) {
    // Revoke token
}
```

### 5. Scope Validation
```java
private void validateScope(String scope) {
    // Validate against allowed scopes
}
```

---

## References

- **JJWT Documentation**: https://github.com/jwtk/jjwt
- **JJWT API Docs**: https://javadoc.io/doc/io.jsonwebtoken/jjwt-api
- **JWT RFC 7519**: https://tools.ietf.org/html/rfc7519
- **OAuth 2.0 RFC 6749**: https://tools.ietf.org/html/rfc6749

---

**Status**: Ready to begin migration  
**Next Step**: Add JJWT dependencies to pom.xml
