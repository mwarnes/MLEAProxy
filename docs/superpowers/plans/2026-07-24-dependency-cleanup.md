# Implementation Plan: Dependency Cleanup

**Created:** 2026-07-24  
**Spec:** docs/superpowers/specs/2026-07-24-dependency-cleanup-design.md  
**Branch:** master (atomic commit with git checkpoint)

---

## Context

Removing 10 unused dependencies (all with 0 imports) and migrating JsonRequestProcessor from org.json to Jackson. All changes in a single atomic commit for easy rollback.

**Pre-flight verification:**
- All 10 dependencies have 0 imports (verified by ponytail audit)
- Jackson ObjectMapper already available via Spring Boot
- Users confirmed Thymeleaf templates are actively used (keeping spring-boot-starter-thymeleaf)

---

## Global Constraints

- **Single atomic commit** - all changes in one commit for easy rollback
- **Zero functional changes** - behavior identical before/after
- **All 107 tests must pass**
- **Build must succeed** - `mvn clean package`
- **No new dependencies** - Jackson already present via Spring Boot

---

## File Changes

**Files to modify:**
1. `pom.xml` - Remove 10 dependency declarations
2. `src/main/java/com/marklogic/processors/JsonRequestProcessor.java` - Migrate from org.json to Jackson

**Files to verify (no changes):**
- All test files should pass unchanged
- `users.json` format unchanged
- LDAP authentication behavior unchanged

---

## Tasks

### Task 1: Remove unused dependencies from pom.xml

**Objective:** Remove 10 unused dependencies with 0 imports

**Steps:**

1. **Record baseline**
   - Note current JAR size: `ls -lh target/mlesproxy-2.0.3.jar`
   - Record for comparison after changes

2. **Remove utility library dependencies**
   - Delete `<dependency>` block for `guava` (lines ~124-127)
   - Delete `<dependency>` block for `commons-lang3` (lines ~128-131)
   - Delete `<dependency>` block for `threetenbp` (lines ~241-244)
   - Delete `<dependency>` block for `httpclient` (lines ~120-123)

3. **Remove unused feature dependencies**
   - Delete `<dependency>` block for `spring-boot-starter-mail` (lines ~71-73)
   - Delete `<dependency>` block for `googleauth` (lines ~132-135)
   - Delete `<dependency>` block for `validation-api` (lines ~137-140)
   - Delete `<dependency>` block for `hibernate-validator` (lines ~142-145)
   - Delete `<dependency>` block for `org-netbeans-libs-json_simple` (lines ~113-116)

4. **Remove org.json dependency**
   - Delete `<dependency>` block for `org.json` (migrating to Jackson)

5. **Verify pom.xml**
   - Ensure no accidental deletions
   - Verify `spring-boot-starter-thymeleaf` is STILL PRESENT
   - Check XML syntax is valid

6. **Verify build compiles**
   - Run: `mvn clean compile`
   - Should succeed (JsonRequestProcessor will have compile errors - addressed in Task 2)
   - Expect compilation failure in JsonRequestProcessor (org.json classes missing)

**Success criteria:**
- 10 dependencies removed from pom.xml
- spring-boot-starter-thymeleaf still present
- pom.xml is valid XML
- Note: Compilation will fail at this point (expected - fixed in Task 2)

---

### Task 2: Migrate JsonRequestProcessor to Jackson

**Objective:** Replace org.json with Jackson ObjectMapper in JsonRequestProcessor.java

**Background:**
- File: `src/main/java/com/marklogic/processors/JsonRequestProcessor.java`
- Currently uses `org.json.JSONObject` and `org.json.JSONArray`
- Jackson ObjectMapper is already available via Spring Boot

**Steps:**

1. **Replace imports**
   - Remove: `import org.json.JSONObject;`
   - Remove: `import org.json.JSONArray;`
   - Add: `import com.fasterxml.jackson.databind.ObjectMapper;`
   - Add: `import com.fasterxml.jackson.databind.JsonNode;`

2. **Add ObjectMapper instance**
   - Add static instance: `private static final ObjectMapper objectMapper = new ObjectMapper();`
   - Place near the top with other static fields

3. **Update instance variable**
   - Change: `private JSONObject usersData;`
   - To: `private JsonNode usersData;`

4. **Update JSON parsing code**
   - Find all uses of `new JSONObject(jsonContent)`
   - Replace with `objectMapper.readTree(jsonContent)`

5. **Update JSON access patterns**
   - Find all uses of `.getJSONArray(...)`, `.getJSONObject(...)`, `.getString(...)`, etc.
   - Replace with Jackson equivalents:
     - `jsonObject.getJSONArray("key")` → `jsonNode.get("key")`
     - `jsonObject.getString("key")` → `jsonNode.get("key").asText()`
     - `jsonArray.getJSONObject(i)` → `jsonArray.get(i)`
   - Verify each access pattern preserves null-safety

6. **Verify no org.json references remain**
   - Search file for `JSONObject`, `JSONArray`, `org.json`
   - All should be removed

7. **Build and verify compilation**
   - Run: `mvn clean compile`
   - Should succeed with no errors
   - JsonRequestProcessor should compile cleanly

**Success criteria:**
- No org.json imports in JsonRequestProcessor.java
- All JSON operations use Jackson APIs
- File compiles without errors
- Logic unchanged (behavior preserved)

---

### Task 3: Build and verify JAR

**Objective:** Verify the build produces a working JAR with reduced size

**Steps:**

1. **Clean build**
   - Run: `mvn clean package -DskipTests`
   - Should succeed

2. **Check JAR size**
   - Run: `ls -lh target/mlesproxy-2.0.3.jar`
   - Compare to baseline from Task 1
   - Should be ~150KB smaller

3. **Verify JAR contents**
   - List dependencies in JAR: `unzip -l target/mlesproxy-2.0.3.jar | grep -E "guava|commons-lang|org.json" | wc -l`
   - Should show 0 (none of the removed deps present)

4. **Verify spring-boot-starter-thymeleaf is present**
   - Check for Thymeleaf: `unzip -l target/mlesproxy-2.0.3.jar | grep thymeleaf | head -5`
   - Should show Thymeleaf JARs present

**Success criteria:**
- Build completes successfully
- JAR is ~150KB smaller
- Removed dependencies not in JAR
- Thymeleaf still present in JAR

---

### Task 4: Run test suite

**Objective:** Verify all 107 tests pass with the changes

**Steps:**

1. **Run full test suite**
   - Run: `./run-tests.sh all`
   - Or: `mvn clean test`
   - All 107 tests should pass

2. **Check for JsonRequestProcessor-related tests**
   - Look for test failures related to JSON parsing
   - Look for test failures related to LDAP authentication
   - All should pass (behavior unchanged)

3. **Check test output for warnings**
   - Look for dependency-related warnings
   - Look for ClassNotFoundException for removed deps
   - Should be clean

**Success criteria:**
- All 107 tests pass
- No warnings about missing classes
- No dependency-related errors

---

### Task 5: Manual verification

**Objective:** Verify JSON-based LDAP authentication works in running server

**Steps:**

1. **Start the server**
   - Run: `scripts/start-all.sh`
   - Server should start without errors

2. **Check startup logs**
   - Check for ClassNotFoundException or NoClassDefFoundError
   - Check for dependency loading errors
   - Logs should be clean

3. **Test JSON-based LDAP authentication**
   - Run an LDAP search using JSON user credentials
   - Example: `ldapsearch -H ldap://localhost:10389 -D "cn=admin,ou=users,dc=marklogic,dc=local" -w password -b "ou=users,dc=marklogic,dc=local" "(objectClass=*)"`
   - Should succeed and return users

4. **Verify other protocols**
   - Quick smoke test OAuth endpoint: `curl http://localhost:8080/oauth/jwks`
   - Quick smoke test SAML endpoint: `curl http://localhost:8080/saml/metadata`
   - Both should respond normally

5. **Stop the server**
   - Run: `scripts/stop.sh`

**Success criteria:**
- Server starts cleanly
- No ClassNotFoundException errors
- JSON-based LDAP authentication works
- Other protocol endpoints respond

---

### Task 6: Commit changes

**Objective:** Create single atomic commit with all changes

**Steps:**

1. **Review changes**
   - Run: `git status`
   - Should show: pom.xml, JsonRequestProcessor.java

2. **Review diff**
   - Run: `git diff pom.xml | grep "^-.*<dependency>" | wc -l`
   - Should show ~30 lines (10 dependencies × 3 lines each)
   - Run: `git diff src/main/java/com/marklogic/processors/JsonRequestProcessor.java`
   - Verify org.json removed, Jackson added

3. **Stage changes**
   - Run: `git add pom.xml src/main/java/com/marklogic/processors/JsonRequestProcessor.java`

4. **Commit**
   - Run: `git commit -m "Remove 10 unused dependencies and migrate to Jackson

Removed dependencies (all with 0 imports):
- guava (33.5.0-jre)
- commons-lang3 (3.20.0)
- httpclient (4.5.14)
- googleauth (1.5.0)
- validation-api (2.0.1.Final)
- hibernate-validator (8.0.1.Final)
- threetenbp (1.6.8)
- org-netbeans-libs-json_simple (RELEASE270)
- spring-boot-starter-mail
- org.json (20251224)

Migrated JsonRequestProcessor from org.json to Jackson:
- Jackson ObjectMapper already available via Spring Boot
- Behavior unchanged, all 107 tests pass

Result: ~150KB smaller JAR, fewer dependencies to maintain

Kept: spring-boot-starter-thymeleaf (actively used)"`

5. **Verify commit**
   - Run: `git log -1 --stat`
   - Should show the commit with 2 files changed

**Success criteria:**
- Single atomic commit created
- Commit message describes all changes
- Changes can be reverted with `git revert HEAD` if needed

---

## Verification Checklist

Before marking complete:

- [ ] 10 dependencies removed from pom.xml
- [ ] spring-boot-starter-thymeleaf still present
- [ ] JsonRequestProcessor migrated to Jackson
- [ ] No org.json references remain
- [ ] Build succeeds: `mvn clean package`
- [ ] JAR ~150KB smaller
- [ ] All 107 tests pass
- [ ] Server starts without errors
- [ ] JSON-based LDAP authentication works
- [ ] Single atomic commit created
- [ ] Changes pushed to GitHub (if desired)

---

## Rollback

If any issues:
```bash
git revert HEAD
mvn clean package
```

All changes in one commit = one command to rollback.
