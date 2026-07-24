# Dependency Cleanup Design

**Date:** 2026-07-24  
**Status:** Approved  
**Goal:** Remove unused dependencies and migrate org.json to Jackson

---

## Motivation

Reduce JAR size, security surface area, and maintenance burden by removing 10 unused dependencies identified by ponytail audit. All targeted dependencies have zero imports in the codebase.

**Benefits:**
- ~150KB smaller JAR
- Fewer CVEs to track (10 fewer dependencies)
- Simpler dependency tree
- Faster builds and startup

---

## Scope

**In Scope:**
- Remove 10 unused dependencies from pom.xml
- Migrate JsonRequestProcessor.java from org.json to Jackson
- Verify build and tests pass

**Out of Scope:**
- Thymeleaf (kept - actively used by authn.html and redirect.html)
- Any new features or functionality changes
- Performance benchmarking (size reduction is sufficient metric)

---

## Dependency Removals

### Remove from pom.xml (0 imports found):

**Utility libraries (replaced by Java stdlib):**
1. `guava` (33.5.0-jre) → Java Collections API
2. `commons-lang3` (3.20.0) → java.lang.String methods
3. `threetenbp` (1.6.8) → java.time (since Java 8)
4. `httpclient` (4.5.14) → java.net.http.HttpClient (since Java 11)

**Unused feature dependencies:**
5. `spring-boot-starter-mail` → No email functionality (can re-add later if needed)
6. `googleauth` (1.5.0) → No TOTP/2FA implemented
7. `validation-api` (2.0.1.Final) → Spring Boot includes validation support
8. `hibernate-validator` (8.0.1.Final) → Spring Boot includes validator
9. `org-netbeans-libs-json_simple` (RELEASE270) → Dead dependency

**JSON library (migrating to Jackson):**
10. `org.json` (20251224) → Migrating to Jackson (see below)

### Keep (actively used):
- `spring-boot-starter-thymeleaf` → Used by authn.html and redirect.html templates

---

## Code Changes

### JsonRequestProcessor.java - Migrate to Jackson

**File:** `src/main/java/com/marklogic/processors/JsonRequestProcessor.java`

**Current state:**
- Uses `org.json.JSONObject` and `org.json.JSONArray`
- Parses user data from JSON files

**Changes:**

1. **Import replacements:**
   ```java
   // Remove:
   import org.json.JSONObject;
   import org.json.JSONArray;
   
   // Add:
   import com.fasterxml.jackson.databind.ObjectMapper;
   import com.fasterxml.jackson.databind.JsonNode;
   ```

2. **Instance variable:**
   ```java
   // Change:
   private JSONObject usersData;
   
   // To:
   private JsonNode usersData;
   private static final ObjectMapper objectMapper = new ObjectMapper();
   ```

3. **JSON parsing:**
   ```java
   // Change:
   new JSONObject(jsonContent)
   
   // To:
   objectMapper.readTree(jsonContent)
   ```

4. **JSON access patterns:**
   ```java
   // Change:
   jsonObject.getJSONArray("users")
   
   // To:
   jsonNode.get("users")
   ```

**Rationale:**
- Jackson is already present via Spring Boot (zero new dependencies)
- Standard JSON library for Spring applications
- Better performance and more features
- Consistent with rest of Spring ecosystem

---

## Testing Strategy

**Verification steps:**
1. Build with dependencies removed: `mvn clean package -DskipTests`
2. Verify JAR size reduction
3. Run full test suite: `./run-tests.sh all`
4. Specifically test JsonRequestProcessor functionality (LDAP authentication with JSON users)
5. Manual smoke test: Start server and verify JSON-based LDAP authentication works

**Success criteria:**
- Build succeeds
- All 107 tests pass
- JSON-based LDAP authentication works correctly
- No runtime errors or missing class exceptions

---

## Implementation Steps

1. Remove 10 dependencies from pom.xml
2. Update JsonRequestProcessor.java imports and code
3. Build and verify compilation
4. Run test suite
5. Manual verification of JSON authentication
6. Commit changes

---

## Rollback Plan

If issues arise:
- `git revert` to previous commit
- All changes are in a single atomic commit for easy rollback

---

## Risks & Mitigations

**Risk:** JsonRequestProcessor migration introduces bugs  
**Mitigation:** Existing tests cover JSON user authentication; manual testing verifies functionality

**Risk:** Hidden transitive dependency on removed libraries  
**Mitigation:** Ponytail verified 0 imports; build failure would catch this immediately

**Risk:** Runtime reflection or classpath scanning uses removed dependencies  
**Mitigation:** Test suite and manual testing will catch runtime issues

---

## Expected Outcome

- 10 fewer dependencies in pom.xml
- ~150KB smaller JAR file
- Simpler dependency tree (easier security scanning)
- JsonRequestProcessor using Jackson (Spring Boot standard)
- All tests passing
- No functional changes to end users
