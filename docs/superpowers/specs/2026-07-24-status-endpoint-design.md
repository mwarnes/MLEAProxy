# Status Endpoint Design

**Date:** 2026-07-24  
**Status:** Approved  
**Goal:** Create `/status` HTML endpoint displaying server configuration and example commands with copy-to-clipboard functionality

---

## Motivation

Provide a user-friendly web interface showing the same information displayed in console logs at startup:
- Server configuration (ports, URLs, hostname)
- Protocol endpoints (LDAP, Kerberos, OAuth, SAML)
- Example commands for testing
- Configured users

**Benefits:**
- Easy reference without scrolling through console logs
- Clickable URLs for quick testing
- Copy-to-clipboard for commands
- Real-time data (user counts, current hostname)
- Foundation for future stats/metrics dashboard

---

## Scope

**In Scope:**
- New `GET /status` endpoint
- Thymeleaf HTML template with Bootstrap 4 styling
- Display server info, LDAP, Kerberos, OAuth, SAML endpoints
- Show configured users with passwords (development tool)
- Copy-to-clipboard buttons for command examples
- Clickable links for all URLs
- Hybrid real-time/static data (real-time: user count, hostname; static: endpoints)
- Optional meta refresh tag (commented out, easy to enable)

**Out of Scope:**
- Authentication/authorization (same as other endpoints)
- Detailed usage statistics (future enhancement)
- Connection metrics (future enhancement)
- Interactive testing forms (future enhancement)
- WebSocket/live updates (future enhancement)

**Future Enhancements:**
- Auto-refresh via meta tag or JavaScript polling
- Connection statistics (active connections, request counts)
- Usage metrics (requests per protocol, error rates)
- Health checks (service status indicators)

---

## Architecture & Components

### Component Overview

```
User Browser
    ↓
GET /status
    ↓
StatusHandler (@Controller)
    ↓
StartupDisplayService (modified)
    ↓
Returns structured data (Maps/Lists)
    ↓
StatusHandler builds Thymeleaf model
    ↓
status.html renders Bootstrap page
    ↓
HTML with clickable links + copy buttons
```

### Three Main Components

#### 1. StartupDisplayService (existing, modified)

**Changes:**

**Make existing private methods public:**
- `getBaseUrl()` → public
- `getServerHostname()` → public

**Add new public methods returning structured data:**

```java
public Map<String, String> getServerInfo()
// Returns: {port, baseUrl, hostname}

public List<Map<String, String>> getLDAPServers()
// Returns: [{name, url, baseDn, port}, ...]

public List<Map<String, String>> getLDAPListeners()
// Returns: [{name, url, description, port}, ...]

public Map<String, Object> getKerberosInfo()
// Returns: null if disabled, else {realm, kdcHost, kdcPort, httpEndpoint, testCommands}

public Map<String, String> getOAuthInfo()
// Returns: {tokenUrl, jwksUrl, configUrl, exampleCurl, curlFlags}

public Map<String, Object> getSAMLInfo()
// Returns: {authUrl, metadataUrl, caUrl, configured: boolean}

public List<Map<String, Object>> getConfiguredUsers()
// Returns: [{username, password, roles: [...]}, ...]
```

**Rationale:**
- Keeps existing console logging functionality intact
- New methods return data instead of logging it
- StatusHandler consumes these methods to build model
- No duplication - single source of truth for configuration

#### 2. StatusHandler.java (new)

**Location:** `src/main/java/com/marklogic/handlers/undertow/StatusHandler.java`

**Implementation:**

```java
@Controller
public class StatusHandler {
    
    @Autowired
    private StartupDisplayService startupDisplayService;
    
    @GetMapping("/status")
    public String getStatus(Model model) {
        // Server info
        model.addAttribute("serverInfo", startupDisplayService.getServerInfo());
        
        // LDAP
        model.addAttribute("ldapServers", startupDisplayService.getLDAPServers());
        model.addAttribute("ldapListeners", startupDisplayService.getLDAPListeners());
        
        // Kerberos (null if disabled)
        model.addAttribute("kerberosInfo", startupDisplayService.getKerberosInfo());
        
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

**Responsibilities:**
- Single endpoint: `GET /status`
- Orchestrate data gathering from `StartupDisplayService`
- Build Thymeleaf model
- Return template name

#### 3. status.html (new)

**Location:** `src/main/resources/templates/status.html`

**Structure:**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="utf-8">
    <title>MLEAProxy Status</title>
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.1.3/css/bootstrap.min.css">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.7.0/css/font-awesome.min.css">
    <!-- Optional auto-refresh (uncomment to enable):
    <meta http-equiv="refresh" content="30">
    -->
</head>
<body>
    <div class="container mt-4 mb-5">
        <h1 class="mb-4">MLEAProxy Status</h1>
        
        <!-- Server Info Section -->
        <div class="card mb-3">
            <div class="card-header bg-primary text-white">
                <h5 class="mb-0">Server Information</h5>
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
        
        <!-- LDAP Section (conditional) -->
        <div class="card mb-3" th:if="${ldapServers != null and !ldapServers.isEmpty()} or ${ldapListeners != null and !ldapListeners.isEmpty()}">
            <!-- LDAP servers table, listeners table, example command with copy button -->
        </div>
        
        <!-- Kerberos Section (conditional) -->
        <div class="card mb-3" th:if="${kerberosInfo != null}">
            <!-- KDC info, test commands with copy buttons -->
        </div>
        
        <!-- OAuth Section -->
        <div class="card mb-3">
            <!-- OAuth endpoints as clickable links, example curl with copy button -->
        </div>
        
        <!-- SAML Section -->
        <div class="card mb-3">
            <!-- SAML endpoints as clickable links, configuration status -->
        </div>
        
        <!-- Users Section -->
        <div class="card mb-3">
            <!-- Users table: username, password, roles -->
        </div>
    </div>
    
    <script>
        function copyToClipboard(elementId) {
            const element = document.getElementById(elementId);
            const text = element.textContent;
            
            navigator.clipboard.writeText(text).then(function() {
                // Success feedback
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
                // Fallback: select text
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

**Key features:**
- Bootstrap 4 cards for each section
- Conditional rendering with `th:if` (only show enabled protocols)
- Clickable URL links using `th:href`
- Code blocks with copy buttons
- Font Awesome icons
- Graceful degradation for missing data

---

## Data Flow & Model Structure

### Request Flow

1. User requests `GET /status`
2. `StatusHandler.getStatus()` invoked
3. Handler calls `StartupDisplayService` methods
4. Each method returns structured data (Map/List)
5. All data added to Thymeleaf model
6. Template renders Bootstrap HTML
7. Browser displays page with interactive elements

### Model Attributes

**Server Info:**
```java
Map<String, String> serverInfo = {
    "port": "8080",
    "baseUrl": "http://Martins-Air.localdomain:8080",
    "hostname": "Martins-Air.localdomain"
}
```

**LDAP Servers:**
```java
List<Map<String, String>> ldapServers = [
    {
        "name": "marklogic",
        "url": "ldap://Martins-Air.localdomain:61389",
        "baseDn": "dc=marklogic,dc=local",
        "port": "61389"
    }
]
```

**LDAP Listeners:**
```java
List<Map<String, String>> ldapListeners = [
    {
        "name": "default",
        "url": "ldap://Martins-Air.localdomain:10389",
        "description": "LDAP proxy/authentication",
        "port": "10389"
    }
]
```

**Kerberos Info (null if disabled):**
```java
Map<String, Object> kerberosInfo = {
    "realm": "MARKLOGIC.LOCAL",
    "kdcHost": "Martins-Air.localdomain",
    "kdcPort": "8088",
    "httpEndpoint": "http://Martins-Air.localdomain:8080/kerberos/auth",
    "testCommands": [
        "export KRB5_CONFIG=./krb5.conf",
        "kinit mluser1@MARKLOGIC.LOCAL",
        "klist"
    ]
}
```

**OAuth Info:**
```java
Map<String, String> oauthInfo = {
    "tokenUrl": "http://Martins-Air.localdomain:8080/oauth/token",
    "jwksUrl": "http://Martins-Air.localdomain:8080/oauth/jwks",
    "configUrl": "http://Martins-Air.localdomain:8080/oauth/.well-known/config",
    "exampleCurl": "curl -s -X POST http://.../oauth/token -d \"grant_type=password\" ...",
    "curlFlags": "-s" // or "-sk" for HTTPS
}
```

**SAML Info:**
```java
Map<String, Object> samlInfo = {
    "authUrl": "http://Martins-Air.localdomain:8080/saml/auth",
    "metadataUrl": "http://Martins-Air.localdomain:8080/saml/idp-metadata",
    "caUrl": "http://Martins-Air.localdomain:8080/saml/ca",
    "configured": true // or false
}
```

**Configured Users:**
```java
List<Map<String, Object>> users = [
    {
        "username": "admin",
        "password": "password",
        "roles": ["admin", "user"]
    },
    {
        "username": "user1",
        "password": "password",
        "roles": ["user"]
    }
]
```

### Real-time vs Static Data (Hybrid Approach)

**Real-time (queried on each `/status` request):**
- Current hostname (may change with DHCP/network changes)
- User count (total users configured)
- Service enabled status (Kerberos on/off, SAML configured)

**Static (from startup configuration):**
- Port numbers
- Base URLs
- Endpoint paths
- Example commands

**Future real-time additions:**
- Active connection count
- Request counts per protocol
- Error rates
- Uptime

---

## HTML Template Design

### Layout Structure

**Bootstrap 4 grid:**
- Container: `.container` with `.mt-4` (margin-top) and `.mb-5` (margin-bottom)
- Cards: Each section in a `.card` with `.mb-3` (margin-bottom for spacing)
- Card headers: `.card-header` with `.bg-primary` or `.bg-info` for color
- Card bodies: `.card-body` with content

**Section organization:**
1. Server Information (always shown)
2. LDAP Endpoints (conditional: `th:if` LDAP configured)
3. Kerberos KDC (conditional: `th:if` Kerberos enabled)
4. OAuth 2.0 Endpoints (always shown)
5. SAML 2.0 Endpoints (always shown)
6. Configured Users (conditional: `th:if` users exist)

### Copy-to-Clipboard Implementation

**HTML structure for command blocks:**

```html
<div class="position-relative">
    <button class="btn btn-sm btn-outline-secondary position-absolute" 
            style="top: 5px; right: 5px; z-index: 10;"
            onclick="copyToClipboard('ldapSearchCmd')">
        <i class="fa fa-copy"></i> Copy
    </button>
    <pre class="bg-light p-3 rounded"><code id="ldapSearchCmd">ldapsearch -H ldap://localhost:10389 \
  -D "cn=admin,ou=users,dc=marklogic,dc=local" \
  -w password \
  -b "ou=users,dc=marklogic,dc=local" \
  "(objectClass=*)"</code></pre>
</div>
```

**JavaScript function:**

```javascript
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
```

**Features:**
- Modern `navigator.clipboard` API
- Visual feedback (button turns green, shows checkmark)
- Fallback for older browsers (select text, show alert)
- Accessible (works with keyboard navigation)

### URL Rendering

**All URLs as clickable links:**

```html
<a th:href="${oauthInfo.tokenUrl}" th:text="${oauthInfo.tokenUrl}">
    http://localhost:8080/oauth/token
</a>
```

**Benefits:**
- One-click access to endpoints
- Opens in same tab (no `target="_blank"` to avoid tab clutter)
- Thymeleaf `@{...}` syntax handles URL encoding
- Fallback text for server-side rendering

### Styling Details

**Bootstrap classes used:**
- `.card`, `.card-header`, `.card-body` - Card components
- `.bg-primary`, `.bg-info`, `.text-white` - Header colors
- `.btn`, `.btn-sm`, `.btn-outline-secondary`, `.btn-success` - Buttons
- `.table`, `.table-sm`, `.table-striped` - Tables for users/servers
- `.bg-light`, `.p-3`, `.rounded` - Code block styling
- `.position-relative`, `.position-absolute` - Copy button positioning
- `.mt-4`, `.mb-3`, `.mb-5` - Spacing
- `.row`, `.col-sm-3`, `.col-sm-9` - Grid layout

**Font Awesome icons:**
- `fa fa-copy` - Copy button
- `fa fa-check` - Success feedback
- `fa fa-server` - Server info
- `fa fa-database` - LDAP section
- `fa fa-lock` - Kerberos section
- `fa fa-key` - OAuth section
- `fa fa-shield-alt` - SAML section
- `fa fa-users` - Users section

**Code block styling:**
```css
/* Inline styles in template */
pre {
    overflow-x: auto;
    white-space: pre-wrap;
    word-wrap: break-word;
}
```

### Auto-Refresh Meta Tag

**Commented out by default:**

```html
<!-- Optional auto-refresh (uncomment to enable):
<meta http-equiv="refresh" content="30">
-->
```

**To enable:** Remove comment markers, page refreshes every 30 seconds.

**Future enhancement:** JavaScript polling with `fetch()` for smoother updates without full page reload.

---

## Error Handling & Edge Cases

### 1. Service Not Available

**Scenario:** `jsonUserRepository == null`

**Handling:**
```html
<div class="card mb-3" th:if="${users != null and !users.isEmpty()}">
    <!-- Users table -->
</div>
<div class="card mb-3" th:if="${users == null or users.isEmpty()}">
    <div class="card-header bg-secondary text-white">
        <h5 class="mb-0">Configured Users</h5>
    </div>
    <div class="card-body">
        <p class="text-muted mb-0">User repository not configured</p>
    </div>
</div>
```

**Result:** Graceful message, no crash

### 2. Protocol Disabled

**Scenario:** Kerberos not enabled

**Handling:**
```html
<div class="card mb-3" th:if="${kerberosInfo != null}">
    <!-- Kerberos section -->
</div>
<!-- If kerberosInfo is null, section is not rendered -->
```

**Result:** Section hidden entirely

### 3. Empty User List

**Scenario:** No users configured in `users.json`

**Handling:**
```html
<div th:if="${users.isEmpty()}">
    <p class="text-muted mb-0">No users configured</p>
</div>
<table th:if="${!users.isEmpty()}" class="table table-sm table-striped">
    <!-- User rows -->
</table>
```

**Result:** Clear message instead of empty table

### 4. Clipboard API Not Available

**Scenario:** Older browser without `navigator.clipboard`

**Handling:**
```javascript
navigator.clipboard.writeText(text).then(...)
.catch(function(err) {
    // Fallback: select text for manual copy
    const range = document.createRange();
    range.selectNode(element);
    window.getSelection().removeAllRanges();
    window.getSelection().addRange(range);
    alert('Press Ctrl+C (or Cmd+C) to copy');
});
```

**Result:** Text selected, user can Cmd+C manually

### 5. Long Command Strings

**Scenario:** Very long curl commands

**Handling:**
```html
<pre class="bg-light p-3 rounded" style="overflow-x: auto; white-space: pre-wrap;">
    <code>...</code>
</pre>
```

**Result:** Horizontal scrollbar if needed, or word wrap

### 6. Network/Hostname Changes

**Scenario:** Hostname changes after startup (DHCP renewal)

**Handling:**
- `getServerHostname()` is called fresh on each `/status` request
- Real-time hostname displayed (not cached from startup)

**Result:** Always shows current hostname

### 7. Missing Environment/Properties

**Scenario:** `environment == null` or properties not loaded

**Handling:**
```java
public Map<String, String> getServerInfo() {
    if (environment == null) {
        return Map.of(
            "port", "8080",
            "baseUrl", "http://localhost:8080",
            "hostname", "localhost"
        );
    }
    // Normal flow
}
```

**Result:** Safe defaults returned

---

## Security Considerations

**Development/Testing Tool:**
- Per `README.md`: "⚠️ IMPORTANT: Testing & Diagnostic Tool Only"
- Not intended for production use
- Passwords shown are test credentials

**No Authentication Required:**
- `/status` endpoint is publicly accessible (same as `/oauth/token`, `/saml/metadata`)
- Consistent with project's purpose as a development tool
- Users can add authentication later if needed

**Password Visibility:**
- Passwords shown in clear text (Option A approved)
- Matches console output behavior
- Necessary for testing/development workflow
- Users are test accounts only

**Future Security Options (if needed):**
- Basic authentication on `/status` endpoint
- IP whitelist configuration
- Hide passwords toggle (show/hide button)
- Session-based access control

**No XSS Risk:**
- Thymeleaf auto-escapes all variables
- URLs use `th:href` (safe URL encoding)
- No user input processed (read-only display)

---

## Testing Strategy

### Manual Verification

1. **Start server with default configuration:**
   ```bash
   scripts/start-all.sh
   ```

2. **Visit `/status` endpoint:**
   ```
   http://localhost:8080/status
   ```

3. **Verify displayed information:**
   - Server info (port, base URL, hostname)
   - LDAP servers and listeners
   - Kerberos KDC info (if enabled)
   - OAuth endpoints
   - SAML endpoints
   - Configured users table

4. **Test interactive elements:**
   - Click URL links → should open endpoints
   - Click copy buttons → should copy commands to clipboard
   - Paste commands → should be valid and executable

### Configuration Testing

**Test with different protocol combinations:**

1. **LDAP only:**
   - Disable Kerberos, OAuth, SAML
   - Verify only LDAP section shown

2. **OAuth only:**
   - Disable LDAP, Kerberos, SAML
   - Verify only OAuth section shown

3. **All protocols enabled:**
   - Enable LDAP, Kerberos, OAuth, SAML
   - Verify all sections shown

4. **No users configured:**
   - Empty `users.json`
   - Verify "No users configured" message

### Browser Compatibility Testing

**Test copy-to-clipboard in:**
- Chrome (latest)
- Firefox (latest)
- Safari (latest)
- Edge (latest)

**Verify:**
- Copy button works in all browsers
- Success feedback displays correctly
- Fallback works in older browsers (if testing with older browser)

### Visual Testing

**Verify Bootstrap layout:**
- Cards display correctly
- Spacing is consistent
- Colors match existing templates
- Responsive on different screen sizes (mobile, tablet, desktop)

### Link Testing

**Click all displayed URLs:**
- LDAP URLs (should show LDAP server info or error)
- OAuth endpoints (should return JSON or 401)
- SAML endpoints (should return XML metadata or HTML)
- Kerberos endpoint (should show auth form or 401)

**Verify URLs are correct:**
- Hostname matches current server hostname
- Ports match configuration
- Protocols (http/https) match SSL settings

### Command Testing

**Copy and execute example commands:**

1. **LDAP search:**
   - Copy command from /status page
   - Paste and execute in terminal
   - Verify returns user data

2. **OAuth token request:**
   - Copy curl command
   - Execute and verify returns JWT token

3. **Kerberos kinit:**
   - Copy kinit commands
   - Execute and verify ticket created

### Edge Case Testing

1. **Very long hostname:** 
   - Set hostname to very long FQDN
   - Verify layout doesn't break

2. **Many users:**
   - Configure 50+ users in users.json
   - Verify table scrolls properly

3. **Special characters in configuration:**
   - Usernames/passwords with symbols
   - Verify Thymeleaf escaping works

---

## Implementation Notes

### File Locations

```
src/main/java/com/marklogic/
├── handlers/undertow/
│   └── StatusHandler.java (NEW)
└── service/
    └── StartupDisplayService.java (MODIFIED)

src/main/resources/
└── templates/
    └── status.html (NEW)
```

### Dependencies

**No new dependencies needed:**
- Thymeleaf: Already present (spring-boot-starter-thymeleaf)
- Bootstrap 4: CDN link (same as existing templates)
- Font Awesome: CDN link (same as existing templates)
- Jackson: Already present for JSON serialization (if needed later)

### Compatibility

**Versions:**
- Java 21
- Spring Boot 3.3.5
- Thymeleaf 3.1.2
- Bootstrap 4.1.3 (via CDN)
- Font Awesome 4.7.0 (via CDN)

**Browser requirements:**
- Modern browsers (Chrome, Firefox, Safari, Edge)
- Clipboard API support (Chrome 63+, Firefox 53+, Safari 13.1+)
- Fallback for older browsers (manual copy)

---

## Success Criteria

**Implementation complete when:**

1. ✅ `GET /status` endpoint accessible
2. ✅ Page displays all configured protocols
3. ✅ URLs are clickable and correct
4. ✅ Copy buttons copy commands to clipboard
5. ✅ Visual feedback on successful copy
6. ✅ Bootstrap styling matches existing templates
7. ✅ Sections conditionally shown based on configuration
8. ✅ Users table displays username, password, roles
9. ✅ No errors in console logs
10. ✅ Page works on Chrome, Firefox, Safari

**Verification command:**
```bash
# Start server
scripts/start-all.sh

# Visit in browser
open http://localhost:8080/status

# Test copy button, click links, verify display
```

---

## Future Enhancements

**Potential additions (not in current scope):**

1. **Auto-refresh:**
   - Uncomment meta refresh tag, or
   - Add JavaScript polling with `fetch()`

2. **Statistics dashboard:**
   - Active connections count
   - Request counts per protocol
   - Error rates and response times
   - Uptime display

3. **Health checks:**
   - Service status indicators (green/yellow/red)
   - LDAP connection test
   - OAuth key validation
   - SAML metadata validation

4. **Interactive testing:**
   - Inline forms for testing endpoints
   - Token validation tool
   - SAML assertion decoder

5. **Configuration export:**
   - Download MarkLogic external security XML
   - Export current configuration as JSON

6. **Dark mode:**
   - Toggle between light/dark themes
   - Save preference in localStorage

7. **WebSocket updates:**
   - Real-time stats without polling
   - Live connection monitoring

---

## Rollback Plan

**If issues arise:**

1. **Remove endpoint:**
   - Delete `StatusHandler.java`
   - Delete `status.html`
   - Revert changes to `StartupDisplayService.java`

2. **Git revert:**
   ```bash
   git revert HEAD
   mvn clean package
   ```

**No database changes, no configuration changes - clean rollback.**

---

## Summary

**What we're building:**
- Single HTML status page at `/status`
- Displays server configuration and endpoints
- Clickable links and copy-to-clipboard buttons
- Matches existing Thymeleaf/Bootstrap styling
- Hybrid real-time/static data
- Foundation for future stats dashboard

**Implementation approach:**
- Modify `StartupDisplayService` to return structured data
- Create `StatusHandler` to orchestrate and render
- Create `status.html` Thymeleaf template
- No new dependencies
- Single commit, easy to rollback

**User benefit:**
- Quick reference without console logs
- Easy testing with copy/paste commands
- Professional web interface
- Future-ready for metrics/stats
