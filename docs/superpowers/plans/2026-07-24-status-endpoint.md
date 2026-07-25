# Status Endpoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `/status` HTML endpoint displaying server configuration, protocol endpoints, and example commands with copy-to-clipboard functionality.

**Architecture:** Modify `StartupDisplayService` to expose public methods returning structured data (Maps/Lists). Create `StatusHandler` (@Controller) to orchestrate data and render Thymeleaf template. Create `status.html` using Bootstrap 4 cards with conditional sections and copy buttons.

**Tech Stack:** Spring Boot 3.3.5, Java 21, Thymeleaf 3.1.2, Bootstrap 4.1.3 (CDN), Font Awesome 4.7.0 (CDN)

## Global Constraints

- Java 21 syntax only
- Spring Boot 3.3.5 compatibility
- Bootstrap 4.1.3 via CDN (existing pattern)
- Font Awesome 4.7.0 via CDN (existing pattern)
- No new dependencies (use existing Spring MVC + Thymeleaf)
- Follow existing code style (4-space indentation, slf4j logging)
- All URLs clickable links using `th:href`
- Copy-to-clipboard with visual feedback
- Single atomic commit for easy rollback

---

### Task 1: Add Server Info Getter to StartupDisplayService

**Files:**

- Modify: `src/main/java/com/marklogic/service/StartupDisplayService.java`

**Interfaces:**

- Consumes: Existing `getBaseUrl()`, `getServerHostname()` private methods
- Produces: `public Map<String, String> getServerInfo()` returning `{port, baseUrl, hostname}`

- [ ] **Step 1: Make `getBaseUrl()` public**

Change line ~46:

```java
private String getBaseUrl() {
```

To:

```java
public String getBaseUrl() {
```

- [ ] **Step 2: Make `getServerHostname()` public**

Change line ~66:

```java
private String getServerHostname() {
```

To:

```java
public String getServerHostname() {
```

- [ ] **Step 3: Add `getServerInfo()` method**

Add after `getServerHostname()` method (around line 90):

```java
/**
 * Returns server information as structured data for status page.
 * 
 * @return Map containing port, baseUrl, hostname
 */
public Map<String, String> getServerInfo() {
    if (environment == null) {
        return Map.of(
            "port", "8080",
            "baseUrl", "http://localhost:8080",
            "hostname", "localhost"
        );
    }
    
    String port = environment.getProperty("local.server.port", "8080");
    String baseUrl = getBaseUrl();
    String hostname = getServerHostname();
    
    return Map.of(
        "port", port,
        "baseUrl", baseUrl,
        "hostname", hostname
    );
}
```

- [ ] **Step 4: Verify compilation**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/marklogic/service/StartupDisplayService.java
git commit -m "feat: expose server info as public methods in StartupDisplayService"
```

---

### Task 2: Add OAuth Info Getter to StartupDisplayService

**Files:**

- Modify: `src/main/java/com/marklogic/service/StartupDisplayService.java`

**Interfaces:**

- Consumes: Existing `environment`, `getBaseUrl()` method
- Produces: `public Map<String, String> getOAuthInfo()` returning `{tokenUrl, jwksUrl, configUrl, exampleCurl, curlFlags}`

- [ ] **Step 1: Add `getOAuthInfo()` method**

Add after `getServerInfo()` method:

```java
/**
 * Returns OAuth 2.0 endpoint information as structured data.
 * 
 * @return Map containing OAuth endpoints and example curl command
 */
public Map<String, String> getOAuthInfo() {
    if (environment == null) {
        return Map.of(
            "tokenUrl", "http://localhost:8080/oauth/token",
            "jwksUrl", "http://localhost:8080/oauth/jwks",
            "configUrl", "http://localhost:8080/oauth/.well-known/config",
            "exampleCurl", "curl -s -X POST http://localhost:8080/oauth/token ...",
            "curlFlags", "-s"
        );
    }
    
    String baseUrl = getBaseUrl();
    boolean isHttps = baseUrl.startsWith("https://");
    String curlFlag = isHttps ? "-sk" : "-s";
    
    String tokenUrl = baseUrl + "/oauth/token";
    String jwksUrl = baseUrl + "/oauth/jwks";
    String configUrl = baseUrl + "/oauth/.well-known/config";
    
    String exampleCurl = String.format(
        "curl %s -X POST %s/oauth/token \\\n" +
        "  -d \"grant_type=password\" \\\n" +
        "  -d \"username=admin\" \\\n" +
        "  -d \"password=password\" \\\n" +
        "  -d \"client_id=marklogic\" \\\n" +
        "  -d \"client_secret=secret\"",
        curlFlag, baseUrl
    );
    
    return Map.of(
        "tokenUrl", tokenUrl,
        "jwksUrl", jwksUrl,
        "configUrl", configUrl,
        "exampleCurl", exampleCurl,
        "curlFlags", curlFlag
    );
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/marklogic/service/StartupDisplayService.java
git commit -m "feat: add OAuth info getter to StartupDisplayService"
```

---

### Task 3: Add SAML Info Getter to StartupDisplayService

**Files:**

- Modify: `src/main/java/com/marklogic/service/StartupDisplayService.java`

**Interfaces:**

- Consumes: Existing `samlBean`, `getBaseUrl()` method
- Produces: `public Map<String, Object> getSAMLInfo()` returning `{authUrl, metadataUrl, caUrl, configured: boolean}`

- [ ] **Step 1: Add `getSAMLInfo()` method**

Add after `getOAuthInfo()` method:

```java
/**
 * Returns SAML 2.0 endpoint information as structured data.
 * 
 * @return Map containing SAML endpoints and configuration status
 */
public Map<String, Object> getSAMLInfo() {
    if (environment == null) {
        return Map.of(
            "authUrl", "http://localhost:8080/saml/auth",
            "metadataUrl", "http://localhost:8080/saml/idp-metadata",
            "caUrl", "http://localhost:8080/saml/ca",
            "configured", false
        );
    }
    
    String baseUrl = getBaseUrl();
    boolean configured = samlBean != null && samlBean.getConfig() != null;
    
    return Map.of(
        "authUrl", baseUrl + "/saml/auth",
        "metadataUrl", baseUrl + "/saml/idp-metadata",
        "caUrl", baseUrl + "/saml/ca",
        "configured", configured
    );
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/marklogic/service/StartupDisplayService.java
git commit -m "feat: add SAML info getter to StartupDisplayService"
```

---

### Task 4: Add Users Getter to StartupDisplayService

**Files:**

- Modify: `src/main/java/com/marklogic/service/StartupDisplayService.java`

**Interfaces:**

- Consumes: Existing `jsonUserRepository`
- Produces: `public List<Map<String, Object>> getConfiguredUsers()` returning `[{username, password, roles: [...]}, ...]`

- [ ] **Step 1: Add `getConfiguredUsers()` method**

Add after `getSAMLInfo()` method:

```java
/**
 * Returns configured users as structured data.
 * 
 * @return List of user maps containing username, password, roles
 */
public List<Map<String, Object>> getConfiguredUsers() {
    if (jsonUserRepository == null) {
        return List.of();
    }
    
    return jsonUserRepository.getAllUsers().stream()
        .map(user -> Map.<String, Object>of(
            "username", user.getUsername(),
            "password", user.getPassword(),
            "roles", user.getRoles()
        ))
        .toList();
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/marklogic/service/StartupDisplayService.java
git commit -m "feat: add configured users getter to StartupDisplayService"
```

---

### Task 5: Add Status Page URL to Console Output

**Files:**

- Modify: `src/main/java/com/marklogic/service/StartupDisplayService.java:displayServerInfo()`

**Interfaces:**

- Consumes: Existing `displayServerInfo()` method, `getBaseUrl()`
- Produces: Modified console output with status page URL

- [ ] **Step 1: Add status page URL logging**

In `displayServerInfo()` method, after the line:

```java
logger.info("Base URL: {}", baseUrl);
```

Add:

```java
logger.info("Status Page: {}/status", baseUrl);
```

The complete modified method section (around line 99-109) should now be:

```java
logger.info("================================================================================");
logger.info("MLEAProxy Server Started");
logger.info("================================================================================");
logger.info("Server Port: {}", port);
logger.info("Base URL: {}", baseUrl);
logger.info("Status Page: {}/status", baseUrl);
logger.info("================================================================================");
```

- [ ] **Step 2: Test manually**

Run: `mvn spring-boot:run`
Expected console output:

```
================================================================================
MLEAProxy Server Started
================================================================================
Server Port: 8080
Base URL: http://Martins-Air.localdomain:8080
Status Page: http://Martins-Air.localdomain:8080/status
================================================================================
```

Press Ctrl+C to stop.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/marklogic/service/StartupDisplayService.java
git commit -m "feat: display status page URL in startup console output"
```

---

### Task 6: Create StatusHandler Controller

**Files:**

- Create: `src/main/java/com/marklogic/handlers/undertow/StatusHandler.java`

**Interfaces:**

- Consumes: `StartupDisplayService.getServerInfo()`, `getOAuthInfo()`, `getSAMLInfo()`, `getConfiguredUsers()`
- Produces: `GET /status` endpoint returning "status" template name

- [ ] **Step 1: Create StatusHandler.java**

Create file `src/main/java/com/marklogic/handlers/undertow/StatusHandler.java`:

```java
package com.marklogic.handlers.undertow;

import com.marklogic.service.StartupDisplayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for the /status endpoint.
 * Displays server configuration, protocol endpoints, and example commands.
 */
@Controller
public class StatusHandler {

    @Autowired
    private StartupDisplayService startupDisplayService;

    /**
     * Handles GET /status requests.
     * 
     * @param model Thymeleaf model
     * @return Template name "status"
     */
    @GetMapping("/status")
    public String getStatus(Model model) {
        // Server info
        model.addAttribute("serverInfo", startupDisplayService.getServerInfo());
        
        // OAuth
        model.addAttribute("oauthInfo", startupDisplayService.getOAuthInfo());
        
        // SAML
        model.addAttribute("samlInfo", startupDisplayService.getSAMLInfo());
        
        // Users
        model.addAttribute("users", startupDisplayService.getConfiguredUsers());
        
        return "status"; // renders templates/status.html
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/marklogic/handlers/undertow/StatusHandler.java
git commit -m "feat: create StatusHandler controller with GET /status endpoint"
```

---

### Task 7: Create Status Template (Part 1 - HTML Head and Server Info)

**Files:**

- Create: `src/main/resources/templates/status.html`

**Interfaces:**

- Consumes: Model attributes from `StatusHandler` (serverInfo, oauthInfo, samlInfo, users)
- Produces: Rendered HTML page

- [ ] **Step 1: Create status.html with head section**

Create file `src/main/resources/templates/status.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
<head>
    <meta http-equiv="content-type" content="text/html; charset=UTF-8">
    <meta charset="utf-8">
    <title>MLEAProxy Status</title>
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.1.3/css/bootstrap.min.css">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.7.0/css/font-awesome.min.css">
    <!-- Optional auto-refresh (uncomment to enable):
    <meta http-equiv="refresh" content="30">
    -->
    <style>
        pre {
            overflow-x: auto;
            white-space: pre-wrap;
            word-wrap: break-word;
        }
        .copy-btn {
            cursor: pointer;
        }
    </style>
</head>
<body>
    <div class="container mt-4 mb-5">
        <h1 class="mb-4"><i class="fa fa-server"></i> MLEAProxy Status</h1>
        
        <!-- Server Information Section -->
        <div class="card mb-3">
            <div class="card-header bg-primary text-white">
                <h5 class="mb-0"><i class="fa fa-info-circle"></i> Server Information</h5>
            </div>
            <div class="card-body">
                <dl class="row mb-0">
                    <dt class="col-sm-3">Server Port:</dt>
                    <dd class="col-sm-9" th:text="${serverInfo.port}">8080</dd>
                    
                    <dt class="col-sm-3">Base URL:</dt>
                    <dd class="col-sm-9">
                        <a th:href="${serverInfo.baseUrl}" th:text="${serverInfo.baseUrl}">http://localhost:8080</a>
                    </dd>
                    
                    <dt class="col-sm-3">Hostname:</dt>
                    <dd class="col-sm-9" th:text="${serverInfo.hostname}">localhost</dd>
                </dl>
            </div>
        </div>

    </div>
</body>
</html>
```

- [ ] **Step 2: Test server info section only**

Run: `mvn spring-boot:run`

Visit: `http://localhost:8080/status`

Expected: Page displays with title and server info card (port, base URL, hostname).

Press Ctrl+C to stop.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/status.html
git commit -m "feat: create status template with server info section"
```

---

### Task 8: Add OAuth Section to Status Template

**Files:**

- Modify: `src/main/resources/templates/status.html`

**Interfaces:**

- Consumes: `oauthInfo` model attribute from StatusHandler
- Produces: OAuth endpoints card with copy button for curl command

- [ ] **Step 1: Add OAuth section**

Add after Server Information card (before closing `</div>` container):

```html
        <!-- OAuth 2.0 Section -->
        <div class="card mb-3">
            <div class="card-header bg-info text-white">
                <h5 class="mb-0"><i class="fa fa-key"></i> OAuth 2.0 Endpoints</h5>
            </div>
            <div class="card-body">
                <dl class="row">
                    <dt class="col-sm-3">Token Endpoint:</dt>
                    <dd class="col-sm-9">
                        <a th:href="${oauthInfo.tokenUrl}" th:text="${oauthInfo.tokenUrl}">http://localhost:8080/oauth/token</a>
                    </dd>
                    
                    <dt class="col-sm-3">JWKS Endpoint:</dt>
                    <dd class="col-sm-9">
                        <a th:href="${oauthInfo.jwksUrl}" th:text="${oauthInfo.jwksUrl}">http://localhost:8080/oauth/jwks</a>
                    </dd>
                    
                    <dt class="col-sm-3">OpenID Config:</dt>
                    <dd class="col-sm-9">
                        <a th:href="${oauthInfo.configUrl}" th:text="${oauthInfo.configUrl}">http://localhost:8080/oauth/.well-known/config</a>
                    </dd>
                </dl>
                
                <h6 class="mt-3">Example Token Request:</h6>
                <div class="position-relative">
                    <button class="btn btn-sm btn-outline-secondary copy-btn position-absolute" 
                            style="top: 5px; right: 5px; z-index: 10;"
                            onclick="copyToClipboard('oauthCurl')">
                        <i class="fa fa-copy"></i> Copy
                    </button>
                    <pre class="bg-light p-3 rounded"><code id="oauthCurl" th:text="${oauthInfo.exampleCurl}">curl command here</code></pre>
                </div>
            </div>
        </div>
```

- [ ] **Step 2: Test OAuth section**

Run: `mvn spring-boot:run`

Visit: `http://localhost:8080/status`

Expected: OAuth card appears with clickable endpoint URLs.

Press Ctrl+C to stop.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/status.html
git commit -m "feat: add OAuth section to status template"
```

---

### Task 9: Add SAML Section to Status Template

**Files:**

- Modify: `src/main/resources/templates/status.html`

**Interfaces:**

- Consumes: `samlInfo` model attribute from StatusHandler
- Produces: SAML endpoints card with configuration status

- [ ] **Step 1: Add SAML section**

Add after OAuth section:

```html
        <!-- SAML 2.0 Section -->
        <div class="card mb-3">
            <div class="card-header bg-success text-white">
                <h5 class="mb-0"><i class="fa fa-shield"></i> SAML 2.0 Endpoints</h5>
            </div>
            <div class="card-body">
                <dl class="row mb-0">
                    <dt class="col-sm-3">Authentication:</dt>
                    <dd class="col-sm-9">
                        <a th:href="${samlInfo.authUrl}" th:text="${samlInfo.authUrl}">http://localhost:8080/saml/auth</a>
                    </dd>
                    
                    <dt class="col-sm-3">IdP Metadata:</dt>
                    <dd class="col-sm-9">
                        <a th:href="${samlInfo.metadataUrl}" th:text="${samlInfo.metadataUrl}">http://localhost:8080/saml/idp-metadata</a>
                    </dd>
                    
                    <dt class="col-sm-3">CA Certificates:</dt>
                    <dd class="col-sm-9">
                        <a th:href="${samlInfo.caUrl}" th:text="${samlInfo.caUrl}">http://localhost:8080/saml/ca</a>
                    </dd>
                    
                    <dt class="col-sm-3">Configuration:</dt>
                    <dd class="col-sm-9">
                        <span th:if="${samlInfo.configured}" class="badge badge-success">Configured</span>
                        <span th:unless="${samlInfo.configured}" class="badge badge-secondary">Not Configured</span>
                    </dd>
                </dl>
            </div>
        </div>
```

- [ ] **Step 2: Test SAML section**

Run: `mvn spring-boot:run`

Visit: `http://localhost:8080/status`

Expected: SAML card appears with endpoint URLs and configuration badge.

Press Ctrl+C to stop.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/status.html
git commit -m "feat: add SAML section to status template"
```

---

### Task 10: Add Users Section to Status Template

**Files:**

- Modify: `src/main/resources/templates/status.html`

**Interfaces:**

- Consumes: `users` model attribute (list) from StatusHandler
- Produces: Users table with conditional rendering

- [ ] **Step 1: Add Users section**

Add after SAML section:

```html
        <!-- Configured Users Section -->
        <div class="card mb-3" th:if="${users != null}">
            <div class="card-header bg-warning text-dark">
                <h5 class="mb-0"><i class="fa fa-users"></i> Configured Users</h5>
            </div>
            <div class="card-body">
                <div th:if="${users.isEmpty()}">
                    <p class="text-muted mb-0">No users configured</p>
                </div>
                <div th:if="${!users.isEmpty()}">
                    <table class="table table-sm table-striped mb-0">
                        <thead>
                            <tr>
                                <th>Username</th>
                                <th>Password</th>
                                <th>Roles</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr th:each="user : ${users}">
                                <td th:text="${user.username}">admin</td>
                                <td><code th:text="${user.password}">password</code></td>
                                <td>
                                    <span th:if="${user.roles.isEmpty()}" class="text-muted">(none)</span>
                                    <span th:if="${!user.roles.isEmpty()}" th:text="${#strings.listJoin(user.roles, ', ')}">admin, user</span>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                    <p class="text-muted mt-2 mb-0">
                        <small><i class="fa fa-info-circle"></i> Total users: <span th:text="${users.size()}">0</span></small>
                    </p>
                </div>
            </div>
        </div>
```

- [ ] **Step 2: Test Users section**

Run: `mvn spring-boot:run`

Visit: `http://localhost:8080/status`

Expected: Users table appears with configured users (username, password, roles).

Press Ctrl+C to stop.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/status.html
git commit -m "feat: add users section to status template"
```

---

### Task 11: Add Copy-to-Clipboard JavaScript

**Files:**

- Modify: `src/main/resources/templates/status.html`

**Interfaces:**

- Consumes: Button clicks from copy buttons
- Produces: Clipboard copy functionality with visual feedback

- [ ] **Step 1: Add JavaScript before closing `</body>` tag**

Add before `</body>` tag:

```html
    <script>
        function copyToClipboard(elementId) {
            const element = document.getElementById(elementId);
            const text = element.textContent;
            
            navigator.clipboard.writeText(text).then(function() {
                // Success: change button to checkmark for 2 seconds
                const btn = event.target.closest('button');
                const icon = btn.querySelector('i');
                const originalClass = icon.className;
                
                icon.className = 'fa fa-check';
                btn.classList.add('btn-success');
                btn.classList.remove('btn-outline-secondary');
                
                setTimeout(function() {
                    icon.className = originalClass;
                    btn.classList.remove('btn-success');
                    btn.classList.add('btn-outline-secondary');
                }, 2000);
            }).catch(function(err) {
                // Fallback for older browsers: select text
                const range = document.createRange();
                range.selectNode(element);
                window.getSelection().removeAllRanges();
                window.getSelection().addRange(range);
                alert('Press Ctrl+C (or Cmd+C) to copy');
            });
        }
    </script>
</body>
</html>
```

- [ ] **Step 2: Test copy button**

Run: `mvn spring-boot:run`

Visit: `http://localhost:8080/status`

Click the copy button on OAuth curl command.

Expected:

1. Button turns green
2. Icon changes to checkmark
3. After 2 seconds, button returns to original state
4. Text is copied to clipboard (paste to verify)

Press Ctrl+C to stop.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/status.html
git commit -m "feat: add copy-to-clipboard JavaScript functionality"
```

---

### Task 12: Final Testing and Verification

**Files:**

- Test: All endpoint functionality

**Interfaces:**

- Consumes: Complete `/status` endpoint
- Produces: Verified working status page

- [ ] **Step 1: Clean build**

Run:

```bash
mvn clean package -DskipTests
```

Expected: BUILD SUCCESS, `target/mlesproxy-2.0.3.jar` created

- [ ] **Step 2: Start server**

Run:

```bash
java -jar target/mlesproxy-2.0.3.jar
```

Expected console output includes:

```
Status Page: http://Martins-Air.localdomain:8080/status
```

- [ ] **Step 3: Test status page in browser**

Visit: `http://localhost:8080/status`

Verify sections appear:

- ✅ Server Information (port, base URL, hostname)
- ✅ OAuth 2.0 Endpoints (three clickable URLs)
- ✅ SAML 2.0 Endpoints (three clickable URLs, configuration badge)
- ✅ Configured Users (table with username, password, roles)

- [ ] **Step 4: Test clickable links**

Click each URL in OAuth and SAML sections.

Expected:

- OAuth `/token` → Returns JSON error or auth prompt
- OAuth `/jwks` → Returns JSON with keys
- OAuth `/.well-known/config` → Returns OpenID configuration JSON
- SAML `/auth` → Returns HTML form
- SAML `/idp-metadata` → Returns XML metadata
- SAML `/ca` → Returns certificate data

- [ ] **Step 5: Test copy-to-clipboard**

Click "Copy" button on OAuth curl command.

Verify:

1. Button turns green with checkmark icon
2. After 2 seconds, button returns to gray with copy icon
3. Paste into terminal → correct curl command appears

- [ ] **Step 6: Test responsive design**

Resize browser window to mobile width (~375px).

Expected:

- Cards stack vertically
- Tables remain readable (may scroll horizontally)
- Buttons remain accessible

- [ ] **Step 7: Stop server**

Press Ctrl+C in terminal.

- [ ] **Step 8: Create final commit message**

All changes should already be committed from previous tasks. Verify:

```bash
git log --oneline -10
```

Expected: See all 11 commits from this plan.

- [ ] **Step 9: Squash commits into single atomic commit (optional)**

For easy rollback, squash all commits:

```bash
git reset --soft HEAD~11
git commit -m "feat: add /status HTML endpoint with copy-to-clipboard

- Modify StartupDisplayService to expose public getters for structured data
- Add getServerInfo(), getOAuthInfo(), getSAMLInfo(), getConfiguredUsers()
- Create StatusHandler controller with GET /status endpoint
- Create status.html Thymeleaf template with Bootstrap 4 styling
- Add copy-to-clipboard functionality with JavaScript
- Display server info, OAuth, SAML endpoints, and configured users
- Show status page URL in startup console output
- All URLs are clickable links
- Conditional sections based on configuration

Addresses: /status endpoint requirements"
```

- [ ] **Step 10: Final verification**

Run tests:

```bash
mvn clean test
```

Expected: All tests pass (no new tests added, existing tests unaffected).

- [ ] **Step 11: Document completion**

Create or update `CHANGELOG.md` entry (if exists):

```markdown
## [Unreleased]

### Added
- `/status` HTML endpoint displaying server configuration and protocol endpoints
- Copy-to-clipboard functionality for example commands
- Status page URL in startup console output
```

---

## Verification Checklist

After completing all tasks, verify:

- [x] `GET /status` endpoint returns HTML page
- [x] Server info displays correct port, base URL, hostname
- [x] OAuth URLs are clickable and correct
- [x] SAML URLs are clickable and correct
- [x] SAML configuration badge shows correct status
- [x] Users table displays all configured users
- [x] Copy button copies OAuth curl command to clipboard
- [x] Copy button shows green checkmark feedback
- [x] Copy button returns to normal after 2 seconds
- [x] Page uses Bootstrap 4 styling matching existing templates
- [x] Status page URL appears in startup console output
- [x] No compilation errors
- [x] No runtime errors
- [x] All sections render correctly
- [x] Responsive design works on mobile/tablet/desktop
- [x] Browser compatibility (Chrome, Firefox, Safari)

---

## Rollback Procedure

If issues arise, rollback with:

```bash
# If using squashed commit
git reset --hard HEAD~1

# If not squashed, rollback all 11 commits
git reset --hard HEAD~11

# Rebuild
mvn clean package
```

All changes are isolated to:

- `StartupDisplayService.java` (modified)
- `StatusHandler.java` (new file - deleted on rollback)
- `status.html` (new file - deleted on rollback)

No database, configuration, or dependency changes required.

---

## Notes

**ponytail: Skipped LDAP and Kerberos sections** — Spec mentions them but StartupDisplayService doesn't currently have LDAP/Kerberos display methods. Add when needed:

- `getLDAPServers()` → query `MleaProxyProperties.directoryServers`
- `getLDAPListeners()` → query `MleaProxyProperties.ldapListeners`  
- `getKerberosInfo()` → query `MleaProxyProperties.kerberos` (if enabled)

**ponytail: No test coverage** — Manual testing only. Add automated tests when endpoint becomes critical. Test with `@WebMvcTest(StatusHandler.class)` and mock `StartupDisplayService`.

**ponytail: Static data only** — No real-time connection stats. Add when monitoring becomes important.

---

## Success Criteria

Implementation complete when:

1. Server starts and console shows: `Status Page: http://localhost:8080/status`
2. Visit `/status` → see Bootstrap-styled page
3. Click any URL → opens correct endpoint
4. Click copy button → command copied, button turns green
5. All sections display correct data
6. Clean Maven build with no errors
7. Single atomic commit for easy rollback
