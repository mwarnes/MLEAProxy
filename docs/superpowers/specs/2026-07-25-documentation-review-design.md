# User Documentation Review and Update Design

**Date:** 2026-07-25  
**Status:** Approved  
**Goal:** Comprehensive user documentation review and update following A→B→C priority order

---

## Motivation

Recent changes require documentation updates, and a comprehensive audit will ensure all user documentation is accurate, consistent, and well-structured:

**Recent Changes Needing Documentation:**
- New `/status` HTML endpoint
- Version update (2.0.2 → 2.0.3)
- Kerberos port standardization (→ 8088)
- Hostname detection improvements
- Consolidated startup output format

**Documentation Quality Goals:**
- All examples tested and verified
- Consistent structure across guides
- Self-contained but with good navigation
- Progressive complexity (basic → intermediate → MarkLogic integration)

**Benefits:**
- Users can find information quickly
- Examples actually work
- Reduced support burden
- Professional documentation quality

---

## Scope

### In Scope

**Documents to Update (8 primary files):**
1. `README.md` (main project overview)
2. `docs/user/README.md` (documentation index)
3. `docs/user/QUICKSTART_VERIFICATION.md` (working examples)
4. `docs/user/LDAP_GUIDE.md` (LDAP protocol guide)
5. `docs/user/OAUTH_GUIDE.md` (OAuth 2.0 guide)
6. `docs/user/SAML_GUIDE.md` (SAML 2.0 guide)
7. `docs/user/KERBEROS_GUIDE.md` (Kerberos guide)
8. `docs/user/CONFIGURATION_GUIDE.md` (configuration reference)

**Also Review:**
- `docs/user/JWKS-MarkLogic-Integration-Usage-Guide.md` (verify currency)

**Three-Phase Approach:**
- **Phase 1:** Update for Recent Changes (new features, version updates)
- **Phase 2:** Deep Audit (test all examples, document issues)
- **Phase 3:** Structure Improvements (navigation, consistency, example ordering)

### Out of Scope

- Developer documentation (`docs/developer/`)
- Code comments/Javadocs
- Test documentation
- Architecture diagrams (future enhancement)
- Video tutorials (future enhancement)

### Future Enhancements

- Screenshots of status page
- Architecture diagrams
- Video walkthroughs
- Dockerized examples
- Interactive tutorials

---

## Phase 1: Update for Recent Changes

### Priority: Highest (A)

**Goal:** Document all recent changes so users have accurate information immediately.

### 1.1 Version Updates (2.0.2 → 2.0.3)

**Files Affected:** All 8 documentation files

**Changes:**
- Replace all occurrences of `mlesproxy-2.0.2.jar` → `mlesproxy-2.0.3.jar`
- Update version badges in README.md
- Update "Last Updated" dates in affected docs

**Method:** Simple find/replace across all files

**Example:**
```bash
# Before
java -jar target/mlesproxy-2.0.2.jar

# After
java -jar target/mlesproxy-2.0.3.jar
```

### 1.2 Status Endpoint Documentation

**New Feature:** `/status` HTML endpoint showing server configuration and endpoints

**Documentation Locations:**

**README.md - Quick Start Section:**
```markdown
## Quick Start

### View Server Status

After starting MLEAProxy, visit the status page:

**Status Page:** http://localhost:8080/status

Features:
- 🔗 **Clickable endpoint URLs** - Test endpoints directly
- 📋 **Copy-to-clipboard buttons** - Copy example commands
- 🔄 **Real-time information** - Current hostname, user count
- ✅ **Conditional sections** - Only shows enabled protocols
```

**docs/user/README.md - Quick References Section:**
```markdown
### Quick References

- **[Status Page](http://localhost:8080/status)** - View all configured endpoints and server status
- **[DISCOVERY_ENDPOINTS_QUICK_REF.md](./DISCOVERY_ENDPOINTS_QUICK_REF.md)** - OAuth/SAML discovery endpoints
```

**QUICKSTART_VERIFICATION.md - New Section 0:**
```markdown
## 0. Status Page

The status page provides a web interface to view all server configuration and endpoints.

### Access

**URL:** http://localhost:8080/status (or http://<your-hostname>:8080/status)

### Features

- **Server Information** - Port, base URL, hostname
- **Protocol Endpoints** - OAuth, SAML, LDAP, Kerberos
- **Configured Users** - Username, roles, passwords (test credentials)
- **Clickable Links** - Click any URL to test the endpoint
- **Copy Buttons** - One-click copy for example commands

### Example

After starting MLEAProxy with `java -jar target/mlesproxy-2.0.3.jar`, visit:

http://localhost:8080/status

You'll see:
- OAuth 2.0 endpoints (token, JWKS, OpenID config)
- SAML 2.0 endpoints (auth, metadata, CA certs)
- Configured users from users.json
- Example curl commands with copy buttons

### When to Use

- Quick reference for endpoint URLs
- Verify server configuration
- Copy test commands
- Check which protocols are enabled
```

**Each Protocol Guide - Status Page Section:**

Add after "Quick Start" section in each guide:

```markdown
## Status Page

View [Protocol] configuration on the status page: http://localhost:8080/status

The status page displays:
- [Protocol] endpoint URLs (clickable)
- Example commands with copy-to-clipboard buttons
- Current configuration status

### [Protocol]-Specific Information

**OAuth Guide:**
- Token endpoint URL
- JWKS endpoint URL
- OpenID configuration URL
- Example curl command for token request

**SAML Guide:**
- Authentication URL
- IdP metadata URL
- CA certificates URL
- Configuration status (Loaded/Not configured)

**LDAP Guide:**
- In-memory server URLs
- Proxy listener URLs
- Example ldapsearch commands

**Kerberos Guide:**
- KDC host and port
- Realm information
- Example kinit commands
```

### 1.3 Kerberos Port Standardization (→ 8088)

**Files Affected:**
- `docs/user/KERBEROS_GUIDE.md`
- `docs/user/CONFIGURATION_GUIDE.md`

**Changes:**
- Replace all port references: 60088, 60089 → 8088
- Update krb5.conf examples to use port 8088
- Add rationale note

**Rationale Note:**
```markdown
> 💡 **Port 8088:** MLEAProxy uses port 8088 for the Kerberos KDC (not the standard port 88).
> Port 88 requires root/administrator privileges. Port 8088 is a non-privileged port suitable for
> development and testing environments.
```

**Example Updates:**

```properties
# Before
mleaproxy.kerberos.kdc-port=60088

# After
mleaproxy.kerberos.kdc-port=8088  # Non-privileged port (88 requires root)
```

```ini
# Before (krb5.conf)
[realms]
  MARKLOGIC.LOCAL = {
    kdc = localhost:60088
  }

# After (krb5.conf)
[realms]
  MARKLOGIC.LOCAL = {
    kdc = localhost:8088
  }
```

### 1.4 Startup Output Examples

**Files Affected:** All protocol guides showing startup output

**Update to Match New Format:**

```
================================================================================
MLEAProxy Server Started
================================================================================
Server Port: 8080
Base URL: http://Martins-Air.localdomain:8080
Status Page: http://Martins-Air.localdomain:8080/status
================================================================================

LDAP Endpoints:
--------------------------------------------------------------------------------
In-Memory Server 'marklogic':   ldap://Martins-Air.localdomain:60389
  Base DN: dc=marklogic,dc=local
LDAP Listener 'proxy':      ldap://Martins-Air.localdomain:10389
  Description: LDAP proxy/authentication

Example LDAP Search:
ldapsearch -H ldap://Martins-Air.localdomain:10389 \
  -D "cn=admin,ou=users,dc=marklogic,dc=local" \
  -w password \
  -b "ou=users,dc=marklogic,dc=local" \
  "(objectClass=*)"
================================================================================

Kerberos KDC:
--------------------------------------------------------------------------------
Realm:                    MARKLOGIC.LOCAL
KDC:                      Martins-Air.localdomain:8088
HTTP Endpoint:            http://Martins-Air.localdomain:8080/kerberos/auth

Test with kinit:
  export KRB5_CONFIG=./krb5.conf
  kinit mluser1@MARKLOGIC.LOCAL
  (password: password)
  klist
================================================================================

OAuth 2.0 Endpoints:
--------------------------------------------------------------------------------
Token Endpoint:           http://Martins-Air.localdomain:8080/oauth/token
JWKS Endpoint:            http://Martins-Air.localdomain:8080/oauth/jwks
OpenID Configuration:     http://Martins-Air.localdomain:8080/oauth/.well-known/config

Example Token Request:
curl -s -X POST http://Martins-Air.localdomain:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=password" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"
================================================================================

SAML 2.0 Endpoints:
--------------------------------------------------------------------------------
Authentication:           http://Martins-Air.localdomain:8080/saml/auth
IdP Metadata:             http://Martins-Air.localdomain:8080/saml/idp-metadata
CA Certificates:          http://Martins-Air.localdomain:8080/saml/ca

SAML Configuration: Loaded
================================================================================

Configured Users (from users.json):
--------------------------------------------------------------------------------
Username             Password             Roles                                   
--------------------------------------------------------------------------------
admin                password             admin, user                             
user1                password             user, reader                            
user2                password             user, writer                            
developer            dev123               developer, user                         
manager              password             (none)                                  
--------------------------------------------------------------------------------
Total users: 5

Example login: admin / password
================================================================================
```

**Key Changes:**
- Added "Status Page:" line after Base URL
- Consolidated LDAP and Kerberos sections
- Shows actual hostname (not "localhost")
- Includes example commands with actual hostname

### 1.5 Hostname Detection

**Files Affected:** All guides showing URLs

**Update Pattern:**
- Change generic `http://localhost:8080` to `http://<hostname>:8080` or actual example like `http://Martins-Air.localdomain:8080`
- Add note about hostname detection

**Hostname Detection Note:**
```markdown
> 🔧 **Hostname Detection:** MLEAProxy auto-detects your system's hostname (FQDN preferred).
> All displayed URLs will use your actual hostname instead of "localhost".
> Override with: `--mleaproxy.server.hostname=your-hostname.example.com`
```

**Example Updates:**

```markdown
# Before
Access the token endpoint:
http://localhost:8080/oauth/token

# After
Access the token endpoint:
http://<hostname>:8080/oauth/token

Example: http://Martins-Air.localdomain:8080/oauth/token
```

### 1.6 Phase 1 Deliverables

**Commits:**
1. `docs: update version 2.0.2 → 2.0.3 across all documentation`
2. `docs: add status endpoint documentation to all guides`
3. `docs: standardize Kerberos port to 8088`
4. `docs: update startup output examples to match new format`
5. `docs: update hostname examples to show auto-detection`

**Verification:**
- `grep -r "2.0.2" docs/` returns no results
- `grep -r "60088\|60089" docs/user/*.md` returns no results (only 8088)
- Status page documented in README, QUICKSTART, and all protocol guides
- Startup examples match actual output

---

## Phase 2: Deep Audit

### Priority: High (B)

**Goal:** Test every example, verify accuracy, document issues for fixing.

### 2.1 Audit Process

**For Each Documentation File:**

1. **Read through completely**
   - Note unclear sections
   - Mark outdated information
   - Flag missing content

2. **Test every command**
   - Execute all `mvn`, `java -jar`, `bash`, `curl`, `ldapsearch`, `kinit` commands
   - Verify exit codes (0 = success)
   - Compare actual output to documented output
   - Note any discrepancies

3. **Verify configuration examples**
   - Check all referenced files exist in `examples/`
   - Test each configuration actually works
   - Validate property names against current schema

4. **Check cross-references**
   - Click all internal links
   - Verify external URLs are accessible
   - Ensure referenced sections exist

5. **Validate file references**
   - Confirm all mentioned files exist
   - Check paths are correct
   - Verify example file contents

### 2.2 Issue Categorization

**Category 1: Simple Fixes** (no external dependencies)

Examples:
- Typos, grammar errors, formatting issues
- Wrong version numbers (`2.0.2` still present)
- Incorrect port numbers
- Broken internal links (`[text](./missing-file.md)`)
- References to non-existent files
- Outdated configuration property names
- Code blocks without syntax highlighting

**Documentation Format:**
```markdown
## Category 1: Simple Fixes

### QUICKSTART_VERIFICATION.md
- [ ] Line 42: Typo "authetication" → "authentication"
- [ ] Line 78: Wrong port 60088 → 8088
- [ ] Line 120: Version 2.0.2 → 2.0.3

### LDAP_GUIDE.md
- [ ] Line 156: Broken link to CONFIGURATION_GUIDE.md (wrong section)
- [ ] Line 234: Code block missing ```bash tag
- [ ] Line 345: References non-existent example file
```

**Category 2: Requires MLEAProxy Running**

Examples:
- Startup command verification
- Endpoint accessibility tests (curl commands)
- LDAP bind/search operations
- OAuth token generation and validation
- SAML metadata retrieval
- Kerberos ticket generation (kinit)
- Status page verification

**Testing Approach:**
1. Start MLEAProxy once with default/all protocols
2. Test all Category 2 commands in sequence
3. Document failures with actual vs expected output

**Documentation Format:**
```markdown
## Category 2: Requires MLEAProxy Running

### Setup
- Start: `java -jar target/mlesproxy-2.0.3.jar`
- Wait: 5 seconds for startup

### QUICKSTART_VERIFICATION.md
- [ ] Line 180: ldapsearch command - verify returns users
  - Command: `ldapsearch -H ldap://localhost:10389 ...`
  - Expected: 5 users returned
  - Actual: [Test result]

### OAUTH_GUIDE.md
- [ ] Line 220: curl token request - verify returns JWT
  - Command: `curl -s -X POST http://localhost:8080/oauth/token ...`
  - Expected: JSON with access_token
  - Actual: [Test result]
```

**Category 3: Requires External Services**

MarkLogic Server is the only external service.

Examples:
- MarkLogic External Security configuration
- MarkLogic App Server authentication testing
- REST API calls to configure MarkLogic
- End-to-end authentication flows with MarkLogic

**Note:** LDAP, SAML, and Kerberos examples use MLEAProxy's built-in services (Category 2, not Category 3)

**Documentation Format:**
```markdown
## Category 3: Requires External Services (MarkLogic)

### Prerequisites
- MarkLogic Server 10+ running
- Admin credentials available
- Network access to ML Server

### SAML_GUIDE.md
- [ ] Line 450: Configure ML External Security via REST API
  - Requires: MarkLogic Server on localhost:8001
  - Command: `curl -X POST http://localhost:8001/manage/v2/external-security ...`
  - Status: Needs ML Server

### OAUTH_GUIDE.md
- [ ] Line 520: Test OAuth with ML App Server
  - Requires: MarkLogic App Server configured with OAuth
  - End-to-end flow
  - Status: Needs ML Server
```

**Category 4: Structural Issues**

Examples:
- Missing table of contents
- Inconsistent formatting across guides
- No breadcrumb navigation
- Duplicated content (should reference canonical source)
- Unclear explanations
- Examples out of order (complex before basic)
- Missing "Related Docs" sections

**Documentation Format:**
```markdown
## Category 4: Structural Issues

### All Protocol Guides
- [ ] Add table of contents to each guide
- [ ] Add breadcrumb navigation (Home > User Docs > Guide)
- [ ] Add "Related Documentation" section at bottom
- [ ] Standardize section order

### LDAP_GUIDE.md
- [ ] Lines 100-150: Startup commands duplicated (should reference QUICKSTART)
- [ ] Lines 200-250: Examples out of order (MarkLogic before basic)
- [ ] Missing clear progression: basic → intermediate → ML integration

### OAUTH_GUIDE.md
- [ ] Inconsistent code block formatting (some missing language tags)
- [ ] No cross-references to related SAML/Kerberos OAuth integration
```

### 2.3 Testing Methodology

**Test Environment:**
- Clean MLEAProxy build (`mvn clean package`)
- Default configuration (`users.json` in project root)
- Fresh terminal (no cached Kerberos tickets)

**Testing Order:**
1. Category 1: Document all simple issues (read-through only)
2. Category 2: Start MLEAProxy, test all commands
3. Category 3: Document MarkLogic requirements
4. Category 4: Identify structural issues during review

**Test Documentation:**
For each tested command, record:
- File and line number
- Command executed
- Expected result
- Actual result
- Pass/Fail status

**Example Test Log:**
```markdown
### Test: LDAP Search (QUICKSTART_VERIFICATION.md:180)

**Command:**
```bash
ldapsearch -H ldap://localhost:10389 \
  -D "cn=admin,ou=users,dc=marklogic,dc=local" \
  -w password \
  -b "ou=users,dc=marklogic,dc=local" \
  "(objectClass=*)"
```

**Expected:** Returns 5 users

**Actual:** ❌ FAIL - Error: "Invalid search request blocked for security reasons"

**Issue:** LDAP injection prevention blocks `(objectClass=*)` pattern

**Fix Needed:** Update example to use valid search filter like `(sAMAccountName=admin)`
```

### 2.4 Phase 2 Deliverables

**Primary Deliverable:**
- `DOCUMENTATION_AUDIT_ISSUES.md` (issue list file)

**Structure:**
```markdown
# Documentation Audit Issues

Generated: 2026-07-25
Auditor: [Name]
MLEAProxy Version: 2.0.3

## Summary
- Total Issues: [count]
- Category 1 (Simple): [count]
- Category 2 (MLEAProxy): [count]
- Category 3 (MarkLogic): [count]
- Category 4 (Structural): [count]

## Category 1: Simple Fixes
[List of issues]

## Category 2: Requires MLEAProxy Running
[List of issues with test results]

## Category 3: Requires External Services
[List of MarkLogic-dependent examples]

## Category 4: Structural Issues
[List of structure/navigation improvements needed]

## Testing Notes
[Environment, approach, any blockers encountered]
```

**Commit:**
- `docs: add comprehensive documentation audit issue list`

**No Fixes in Phase 2:** Issues are documented only, fixes come in Phase 3 (or separate implementation).

---

## Phase 3: Structure Improvements

### Priority: Medium (C)

**Goal:** Improve navigation, consistency, and structure across all documentation.

### 3.1 Navigation Aids (Approach D)

**Add to Every Guide:**

**1. Breadcrumb Navigation (top of file)**
```markdown
[🏠 Home](../../README.md) > [📚 User Docs](./README.md) > LDAP Guide

# LDAP Guide
```

**2. Table of Contents (after title)**
```markdown
## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Status Page](#status-page)
- [Configuration](#configuration)
- [Examples](#examples)
  - [Basic Examples](#basic-examples)
  - [Intermediate Examples](#intermediate-examples)
  - [MarkLogic Integration](#marklogic-integration)
- [Verification & Testing](#verification--testing)
- [Troubleshooting](#troubleshooting)
- [Related Documentation](#related-documentation)
```

**3. Related Documentation (bottom of file)**
```markdown
---

## Related Documentation

### Quick References
- 📚 [Quick Start & Verification](./QUICKSTART_VERIFICATION.md)
- 🌐 [Status Page](http://localhost:8080/status)

### Configuration
- ⚙️ [Configuration Guide](./CONFIGURATION_GUIDE.md)
- 📝 [Configuration Reference - [Protocol] Section](./CONFIGURATION_GUIDE.md#[protocol]-configuration)

### Other Protocols
- 🔑 [OAuth 2.0 Guide](./OAUTH_GUIDE.md)
- 🛡️ [SAML 2.0 Guide](./SAML_GUIDE.md)
- 🎫 [Kerberos Guide](./KERBEROS_GUIDE.md)
- 📂 [LDAP Guide](./LDAP_GUIDE.md)

### Integration
- 🗄️ [MarkLogic Integration Guide](./JWKS-MarkLogic-Integration-Usage-Guide.md)

### Main Documentation
- 🏠 [User Documentation Index](./README.md)
- 📖 [Project README](../../README.md)
```

**4. Cross-Reference Links (inline)**

Pattern:
```markdown
> 💡 **Tip:** For detailed configuration options, see [Configuration Guide - OAuth Section](./CONFIGURATION_GUIDE.md#oauth-20-configuration)

> 📖 **Full Reference:** See [Quick Start Guide](./QUICKSTART_VERIFICATION.md#oauth-20) for working examples
```

### 3.2 Self-Contained with Links (Hybrid Approach C)

**Principle:** Each protocol guide should be **self-contained** for common operations but **link to canonical sources** for detailed information.

**Self-Contained Content:**
- Basic startup commands
- Essential configuration examples
- Simple verification commands
- Protocol-specific quick start

**Link to Canonical Sources:**
- Full configuration reference → CONFIGURATION_GUIDE.md
- Complete property list → specific section
- All startup options → QUICKSTART_VERIFICATION.md
- Complex integration scenarios → dedicated guides

**Example Pattern:**

```markdown
## Quick Start

### Start the Server

Start MLEAProxy with default configuration:

```bash
# Using the built JAR
java -jar target/mlesproxy-2.0.3.jar

# Or using Maven
mvn spring-boot:run
```

The server starts with all protocols enabled by default.

> 💡 **All startup options:** See [Quick Start Guide - Starting MLEAProxy](./QUICKSTART_VERIFICATION.md#starting-mleaproxy)

### Verify OAuth is Running

Test the token endpoint:

```bash
curl -s http://localhost:8080/oauth/token
```

Expected response: `{"error":"invalid_request"...}` (normal - we didn't provide credentials)

> 📖 **Complete verification:** See [Quick Start Guide - OAuth Verification](./QUICKSTART_VERIFICATION.md#2-oauth-protocol)

## Configuration

### Basic Configuration

Enable OAuth and set issuer:

```properties
# oauth.properties
mleaproxy.oauth.enabled=true
mleaproxy.oauth.issuer=http://localhost:8080
mleaproxy.oauth.jwt-expiration=3600
```

Load this configuration:

```bash
java -jar target/mlesproxy-2.0.3.jar \
  --spring.config.location=classpath:/application.properties,./oauth.properties
```

> ⚙️ **Full configuration reference:** See [Configuration Guide - OAuth 2.0](./CONFIGURATION_GUIDE.md#oauth-20-configuration)
```

**Benefits:**
- Users don't need to switch docs for basic operations
- Canonical sources remain single source of truth
- Links provide path to deeper information
- Reduces duplication while maintaining usability

### 3.3 Standardized Structure

**All Protocol Guides Follow Same Template:**

```markdown
[Breadcrumb]

# [Protocol] Guide

## Table of Contents
[Auto-generated links]

## Overview
- What is [Protocol]
- When to use
- MLEAProxy implementation details
- Supported features

## Quick Start
- Basic startup (self-contained)
- First verification command
- Link to full quick start guide

## Status Page
- How to view [Protocol] configuration
- What information is displayed
- Screenshot or example
- Link: http://localhost:8080/status

## Configuration
- Essential properties (self-contained examples)
- Configuration file examples
- Link to full configuration reference

## Examples

### Basic Examples
[Tier 1: Command line only, minimal setup]

#### Example 1: [Basic Operation]
#### Example 2: [Simple Verification]

### Intermediate Examples
[Tier 2: Multi-protocol, advanced configs]

#### Example 3: [Multi-Protocol Setup]
#### Example 4: [Custom Configuration]

### MarkLogic Integration Examples
[Tier 3: External service integration]

#### Example 5: [Configure External Security]
#### Example 6: [App Server Integration]

## Verification & Testing
- Test commands
- Expected output
- Common issues

## Troubleshooting
- Common errors
- Solutions
- Debug commands

## MarkLogic Integration
- Overview of ML integration
- Configuration steps
- Link to detailed guide

## Related Documentation
[Standard links section]
```

### 3.4 Example Ordering Principle

**Consistent Progression Across All Guides:**

**Tier 1: Basic Command Line** (standalone, no integration)
- Simple startup with default configuration
- Basic verification (curl, ldapsearch, kinit)
- Single-protocol examples
- File-based testing (users.json, local configs)
- No external dependencies (except MLEAProxy itself)

**Tier 2: Intermediate Multi-Protocol** (MLEAProxy features)
- Protocol combinations (OAuth + LDAP, SAML + Kerberos)
- Advanced configuration files
- Custom properties and listeners
- Status page usage
- Multiple configuration scenarios

**Tier 3: MarkLogic Integration** (external service)
- MarkLogic External Security configuration
- App Server setup using REST API
- End-to-end authentication flows
- Production-like deployment scenarios

**Example Structure:**

```markdown
## Examples

### Basic Examples

#### Example 1: Simple Token Request
**Tier:** Basic  
**Dependencies:** MLEAProxy running  
**Description:** Request an OAuth token using curl

```bash
curl -s -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=password" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"
```

**Expected Output:**
```json
{
  "access_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

#### Example 2: Verify Token with JWKS
**Tier:** Basic  
**Dependencies:** MLEAProxy running  
**Description:** Retrieve JWKS to verify token signature

```bash
curl -s http://localhost:8080/oauth/jwks
```

### Intermediate Examples

#### Example 3: OAuth + LDAP Integration
**Tier:** Intermediate  
**Dependencies:** MLEAProxy with OAuth and LDAP enabled  
**Description:** Use LDAP roles in OAuth tokens

[Configuration and commands]

#### Example 4: Custom JWT Claims
**Tier:** Intermediate  
**Dependencies:** Custom configuration file  
**Description:** Add custom claims to JWT tokens

[Configuration and commands]

### MarkLogic Integration Examples

#### Example 5: Configure ML External Security
**Tier:** MarkLogic Integration  
**Dependencies:** MarkLogic Server 10+ running  
**Description:** Create External Security configuration via REST API

[REST API commands]

#### Example 6: End-to-End ML Authentication
**Tier:** MarkLogic Integration  
**Dependencies:** MarkLogic Server with configured App Server  
**Description:** Complete authentication flow from client to MarkLogic

[Complete workflow]
```

### 3.5 Consistent Example Data

**Standardize Across All Documentation:**

**Test Users (from users.json):**
```
admin      / password    / Roles: admin, user
user1      / password    / Roles: user, reader
user2      / password    / Roles: user, writer
developer  / dev123      / Roles: developer, user
manager    / password    / Roles: (none)
```

**Network Configuration:**
```
HTTP Port:        8080
LDAP Proxy:       10389
LDAP In-Memory:   60389
Kerberos KDC:     8088
```

**Kerberos:**
```
Realm:            MARKLOGIC.LOCAL
KDC:              localhost:8088
Test Principal:   mluser1@MARKLOGIC.LOCAL / password
```

**LDAP:**
```
Base DN:          dc=marklogic,dc=local
Users DN:         ou=users,dc=marklogic,dc=local
Manager DN:       cn=manager,ou=users,dc=marklogic,dc=local
```

**OAuth:**
```
Client ID:        marklogic
Client Secret:    secret
Issuer:           http://localhost:8080
Grant Type:       password
```

**SAML:**
```
IdP Entity ID:    http://localhost:8080/saml/idp-metadata
SP Entity ID:     http://localhost:8002 (MarkLogic example)
```

### 3.6 Fix README Index

**Update docs/user/README.md:**

**Remove Non-Existent References:**
- `MarkLogic-SAML-configuration.md` (doesn't exist)
- `DISCOVERY_ENDPOINTS_QUICK_REF.md` (doesn't exist)

**Add Missing References:**
- Status Page (http://localhost:8080/status)
- JWKS Integration Guide (already exists, should be in index)

**Update Structure:**

```markdown
# MLEAProxy User Documentation

This folder contains all user-facing documentation for MLEAProxy.

## 📚 Documentation Index

### ⭐ Quick Start

- **[QUICKSTART_VERIFICATION.md](./QUICKSTART_VERIFICATION.md)** - **Start here!** Working examples for all protocols
- **[Status Page](http://localhost:8080/status)** - Web interface showing all endpoints and configuration

### 🎯 Protocol Guides

Choose your protocol:

- 📂 **[LDAP_GUIDE.md](./LDAP_GUIDE.md)** - LDAP/LDAPS proxy and in-memory server
- 🔑 **[OAUTH_GUIDE.md](./OAUTH_GUIDE.md)** - OAuth 2.0 JWT tokens and JWKS
- 🛡️ **[SAML_GUIDE.md](./SAML_GUIDE.md)** - SAML 2.0 Identity Provider
- 🎫 **[KERBEROS_GUIDE.md](./KERBEROS_GUIDE.md)** - Kerberos KDC and authentication

### ⚙️ Configuration & Integration

- **[CONFIGURATION_GUIDE.md](./CONFIGURATION_GUIDE.md)** - Complete configuration reference (all `mleaproxy.*` properties)
- **[JWKS-MarkLogic-Integration-Usage-Guide.md](./JWKS-MarkLogic-Integration-Usage-Guide.md)** - MarkLogic OAuth integration guide

### 🔗 Related Documentation

- **[Main README](../../README.md)** - Project overview and quick start
- **[Developer Docs](../developer/)** - Technical implementation details

---

## 🚀 Getting Started

1. **Quick Start:** Follow [QUICKSTART_VERIFICATION.md](./QUICKSTART_VERIFICATION.md)
2. **View Status:** Visit http://localhost:8080/status after starting MLEAProxy
3. **Choose Protocol:** Select your protocol guide from above
4. **Configure:** Review [CONFIGURATION_GUIDE.md](./CONFIGURATION_GUIDE.md) for all options

---

**Last Updated:** 2026-07-25
```

### 3.7 Consistency Improvements

**Code Block Formatting:**
- Standardize language tags: `bash`, `properties`, `json`, `xml`, `ini`
- Consistent indentation (2 spaces for properties, 4 for code)
- Add comments for clarity

**Before:**
````
```
java -jar target/mlesproxy-2.0.3.jar
```
````

**After:**
````
```bash
# Start MLEAProxy with default configuration
java -jar target/mlesproxy-2.0.3.jar
```
````

**Port References:**
- LDAP Proxy: Always 10389
- LDAP In-Memory: Always 60389
- Kerberos KDC: Always 8088
- HTTP Server: Always 8080

**URL Patterns:**
- Use `http://<hostname>:8080` for generic examples
- Use `http://Martins-Air.localdomain:8080` for specific examples
- Always mention "replace `<hostname>` with your actual hostname"

**Command Prompts:**
- Include `#` comments for clarity
- Show expected output with `# Output:` comments
- Use `\` for line continuation in long commands

**Example:**
```bash
# Generate OAuth token
curl -s -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=password" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"

# Output: JSON with access_token
```

### 3.8 Phase 3 Deliverables

**Commits:**
1. `docs: add navigation aids (TOC, breadcrumbs, related docs) to all guides`
2. `docs: standardize protocol guide structure across all docs`
3. `docs: reorder examples to basic → intermediate → MarkLogic`
4. `docs: fix docs/user/README.md index (remove broken links, add status page)`
5. `docs: standardize code formatting and example data`

**Verification:**
- All guides have TOC, breadcrumbs, related docs
- All guides follow same structure template
- Examples progress from basic to complex
- Consistent example data (users, ports, URLs)
- Code blocks have language tags

---

## Success Criteria

**Implementation complete when:**

### Phase 1 Complete
- ✅ No references to version 2.0.2 in documentation
- ✅ No Kerberos port references to 60088 or 60089 (only 8088)
- ✅ Status page documented in README, QUICKSTART, and all protocol guides
- ✅ Startup output examples match actual current output
- ✅ Hostname examples show auto-detection pattern

### Phase 2 Complete
- ✅ Comprehensive issue list created (`DOCUMENTATION_AUDIT_ISSUES.md`)
- ✅ All commands tested, results documented
- ✅ Issues categorized (Simple / MLEAProxy / MarkLogic / Structural)
- ✅ MarkLogic-dependent examples clearly identified
- ✅ Test environment and methodology documented

### Phase 3 Complete
- ✅ All guides have TOC, breadcrumbs, related docs sections
- ✅ All guides follow standardized structure template
- ✅ Examples ordered: Basic → Intermediate → MarkLogic
- ✅ Consistent example data across all docs
- ✅ Code blocks properly formatted with language tags
- ✅ docs/user/README.md index fixed (no broken links)
- ✅ Self-contained guides with links to canonical references

### Overall Quality
- ✅ User can start from any guide and find their way
- ✅ Examples work as documented (or marked as "requires MarkLogic")
- ✅ Consistent terminology and formatting
- ✅ Professional documentation quality

---

## Testing Strategy

### Phase 1 Verification

**Version Check:**
```bash
grep -r "2.0.2" docs/ README.md
# Expected: No matches
```

**Port Check:**
```bash
grep -E "60088|60089" docs/user/*.md
# Expected: No matches (only 8088)
```

**Status Page References:**
```bash
grep -l "status" README.md docs/user/README.md docs/user/QUICKSTART_VERIFICATION.md docs/user/*_GUIDE.md
# Expected: All files listed
```

### Phase 2 Verification

**Test Environment:**
```bash
# Build fresh
mvn clean package

# Start server
java -jar target/mlesproxy-2.0.3.jar

# Wait for startup
sleep 5

# Run test commands from audit
[Execute each Category 2 command]

# Document results
[Record pass/fail in issue list]
```

**Category 2 Test Example:**
```bash
# Test LDAP bind
ldapsearch -H ldap://localhost:10389 \
  -D "cn=admin,ou=users,dc=marklogic,dc=local" \
  -w password \
  -b "ou=users,dc=marklogic,dc=local" \
  "(sAMAccountName=admin)"

# Expected: Entry for admin user
# Actual: [Record result]
```

### Phase 3 Verification

**Structure Check:**
```bash
# Verify all guides have TOC
for file in docs/user/*_GUIDE.md; do
  grep -q "## Table of Contents" "$file" && echo "✓ $file" || echo "✗ $file"
done

# Verify all guides have breadcrumbs
for file in docs/user/*_GUIDE.md; do
  head -1 "$file" | grep -q "🏠 Home" && echo "✓ $file" || echo "✗ $file"
done

# Verify all guides have related docs
for file in docs/user/*_GUIDE.md; do
  grep -q "## Related Documentation" "$file" && echo "✓ $file" || echo "✗ $file"
done
```

**Example Ordering Check:**
- Manual review: Examples progress Basic → Intermediate → MarkLogic
- No MarkLogic examples before basic command-line examples

**Consistency Check:**
```bash
# Verify standard ports used
grep -n "10389\|60389\|8088\|8080" docs/user/*.md

# Verify standard users used
grep -n "admin\|user1\|user2\|developer\|manager" docs/user/*.md

# Check code block formatting
grep -n '```$' docs/user/*.md
# Expected: No matches (all blocks should have language tags)
```

---

## Rollback Plan

**If issues arise:**

Each phase is in separate commits, so rollback is per-phase:

```bash
# Rollback Phase 3
git revert <phase-3-commits>

# Rollback Phase 2 (just issue list)
git revert <phase-2-commit>

# Rollback Phase 1
git revert <phase-1-commits>
```

**No code changes, only documentation** - clean rollback with no build/test impact.

---

## Timeline Estimate

**Phase 1: Update for Recent Changes**
- Time: 2-3 hours
- Effort: Find/replace + new content writing

**Phase 2: Deep Audit**
- Time: 6-8 hours (most time-consuming)
- Effort: Read all docs, test all commands, document issues

**Phase 3: Structure Improvements**
- Time: 4-5 hours
- Effort: Add navigation, reorganize, standardize

**Total: 12-16 hours** for comprehensive documentation review and update.

---

## Future Enhancements

After this review is complete, consider:

1. **Screenshots** - Add status page screenshots
2. **Architecture Diagrams** - Visual system architecture
3. **Video Walkthroughs** - YouTube tutorials for each protocol
4. **Docker Examples** - Containerized deployment examples
5. **Interactive Tutorials** - Step-by-step guided tutorials
6. **PDF Generation** - Generate PDF documentation from markdown
7. **Versioned Docs** - Maintain docs for multiple versions
8. **Search Functionality** - If hosting docs as website

---

## Summary

**What we're building:**
- Comprehensive documentation review in 3 phases
- Update for recent features (status page, version, ports)
- Deep audit with tested examples
- Professional structure and navigation

**Implementation approach:**
- Phase 1: Update recent changes (highest priority)
- Phase 2: Audit and document issues (testing focus)
- Phase 3: Structure improvements (usability focus)
- Each phase deliverable and revertible

**User benefit:**
- Accurate, tested documentation
- Easy navigation and discovery
- Professional quality
- Examples that actually work
