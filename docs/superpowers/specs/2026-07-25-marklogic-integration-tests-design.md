# MarkLogic Integration Test Scripts Design

**Date:** 2026-07-25  
**Status:** Approved  
**Goal:** Create minimal MarkLogic integration test scripts for MLEAProxy to validate authentication works with MarkLogic Server

---

## Motivation

MLEAProxy provides authentication for MarkLogic Server across 4 protocols (LDAP, Kerberos, SAML, OAuth), but currently lacks automated integration tests to verify these configurations work end-to-end with MarkLogic.

**Current state:**
- MLEAProxy has example configurations in `examples/` directory
- Documentation exists in protocol guides
- No automated way to verify MarkLogic integration actually works

**Benefits of integration tests:**
- Validate MLEAProxy → MarkLogic integration for each protocol
- Provide working examples for users to reference
- Enable regression testing when changes are made
- Reduce support burden with proven working configurations

---

## Scope

### In Scope

**Test Coverage:**
- 4 protocol tests (one representative configuration each):
  - LDAP: `ldap` authentication + `internal` authorization
  - Kerberos: `kerberos` authentication + `internal` authorization
  - SAML: Exclusive configuration
  - OAuth: Exclusive configuration

**Functionality:**
- Create MarkLogic test AppServers with MLEAProxy external security
- Authenticate against test endpoints using protocol-specific methods
- Verify JSON responses show correct user and roles
- Cleanup test resources (AppServers, external security configs)

**Deliverables:**
- Modular bash scripts with shared utilities
- 4 protocol-specific test scripts
- Documentation (README) with usage instructions
- Optional: Saved external security XML configurations

### Out of Scope

**Not included in minimal version:**
- Multiple authentication/authorization combinations (e.g., ldap+ldap authz, kerberos+ldap authz)
- Comprehensive test suites with multiple user scenarios
- Validation scripts for configuration correctness
- Key rotation or maintenance scripts
- Security audit capabilities
- Performance testing
- Multi-server/cluster testing

**Future enhancements:**
- Expand to all authentication/authorization combinations
- Add validation scripts (similar to github.io examples)
- Include utility functions for common operations
- Add cleanup scripts for orphaned resources

---

## Architecture

### File Structure

**Location:** `examples/marklogic/` in MLEAProxy repository

```
examples/marklogic/
├── README.md                      # Usage documentation and manual testing notes
├── marklogic-utils.sh             # Shared utility functions
├── test-ldap-integration.sh       # LDAP integration test
├── test-kerberos-integration.sh   # Kerberos integration test
├── test-saml-integration.sh       # SAML integration test
├── test-oauth-integration.sh      # OAuth integration test
└── configs/                       # Optional: external security XML templates
    ├── ldap-external-security.xml
    ├── kerberos-external-security.xml
    ├── saml-external-security.xml
    └── oauth-external-security.xml
```

### Execution Model

**Standalone scripts:**
- Each test script is independent and idempotent
- Can run individually: `./test-oauth-integration.sh`
- Can run all: `for script in test-*.sh; do ./$script; done`
- Each script sources `marklogic-utils.sh` for common operations

**Port assignments:**
- LDAP Test: Port 9003
- Kerberos Test: Port 9004
- SAML Test: Port 9005
- OAuth Test: Port 9006
- Template source: Manage AppServer (port 8002)

**Design principle:**
- Scripts designed to run on same server as MarkLogic (typically `rocky`)
- Assumes both MarkLogic and MLEAProxy are accessible as `localhost`
- No remote server configuration complexity

---

## Deployment Environment

### Server Architecture

**MarkLogic Server (rocky):**
- Hostname: `rocky` (Linux server)
- MarkLogic Management API: `http://localhost:8002`
- Test Management AppServer: `http://localhost:8002` (Manage - used as template)
- Admin credentials: `admin` / `admin`

**MLEAProxy Server:**
- Runs on same server as MarkLogic (`rocky`)
- Accessible at: `http://localhost:8080`
- Test users from `users.json`: admin, user1, user2, user3, etc.
- Default passwords: `password`

### Developer Workflow

**Development (macOS):**
1. Edit scripts in `/Users/martin/Projects/MLEAProxy/examples/marklogic/`
2. Commit and push to GitHub

**Testing (rocky server):**
1. SSH to rocky: `ssh rocky`
2. Pull latest: `cd ~/MLEAProxy && git pull`
3. Build if needed: `mvn clean package`
4. Start MLEAProxy: `java -jar target/mlesproxy-2.0.3.jar &`
5. Run tests: `cd examples/marklogic && ./test-oauth-integration.sh`

**Rationale:**
- rocky cannot access MLEAProxy on macOS due to firewall
- Running MLEAProxy on rocky solves the access issue
- Most real deployments also run MLEAProxy on same server as MarkLogic
- Scripts assume local access to both services

---

## Shared Utilities (marklogic-utils.sh)

### Configuration Management Functions

**get_manage_config()**
```bash
# Retrieves Manage AppServer configuration as JSON template
# Returns: Full AppServer configuration JSON
# Used as: Base template for creating test AppServers
# IMPORTANT: Never modifies the Manage AppServer - read-only operation

GET http://localhost:8002/manage/LATEST/servers/Manage?format=json
```

**create_test_appserver(name, port, auth_type, authz_scheme, external_security)**
```bash
# Creates test AppServer by cloning Manage configuration
# Parameters:
#   name: AppServer name (e.g., "MLEAProxy-OAuth-Test")
#   port: Port number (e.g., 9006)
#   auth_type: Authentication protocol (ldap|kerberos|saml|oauth2)
#   authz_scheme: Authorization scheme (internal|ldap)
#   external_security: External security config name
# Process:
#   1. Get Manage config as JSON template
#   2. Modify only: server-name, port, authentication, authorization, external-security
#   3. Keep all other Manage settings unchanged
#   4. POST to http://localhost:8002/manage/LATEST/servers
# Returns: Success/failure status
# Idempotent: Deletes existing AppServer with same name first
```

**delete_test_appserver(name)**
```bash
# Deletes test AppServer
# Idempotent: Succeeds even if AppServer doesn't exist
# DELETE http://localhost:8002/manage/LATEST/servers/{name}
```

### External Security Management Functions

**create_external_security(name, type, config_xml)**
```bash
# Creates external security configuration
# Parameters:
#   name: External security config name
#   type: Protocol type (ldap|kerberos|saml|oauth)
#   config_xml: XML configuration payload
# POST http://localhost:8002/manage/LATEST/external-security
# Returns: Success/failure status
# Idempotent: Deletes existing config with same name first
```

**delete_external_security(name)**
```bash
# Deletes external security configuration
# DELETE http://localhost:8002/manage/LATEST/external-security/{name}
# Idempotent: Succeeds even if config doesn't exist
```

### Testing & Verification Functions

**test_endpoint(url, username, password, expected_user, expected_roles)**
```bash
# Tests authenticated endpoint access with verification
# Parameters:
#   url: Full endpoint URL (with ?format=json)
#   username: Test username
#   password: Test password
#   expected_user: Expected username in response
#   expected_roles: Array of expected roles (subset check)
# Process:
#   1. Make authenticated GET request
#   2. Verify HTTP 200 status
#   3. Parse JSON response
#   4. Extract and verify user-name field
#   5. Extract and verify roles array (expected roles are present)
# Returns: Pass/fail with diagnostic output
# Displays: ✓/✗ for each verification step
```

**wait_for_appserver(port, max_wait_seconds)**
```bash
# Waits for AppServer to be ready to accept requests
# Parameters:
#   port: AppServer port number
#   max_wait_seconds: Maximum time to wait (default: 10)
# Process:
#   1. Poll http://localhost:{port} every 1 second
#   2. Return success when responds with any HTTP status
#   3. Timeout after max_wait_seconds
# Used after: AppServer creation to ensure it's ready before testing
```

**check_prerequisites()**
```bash
# Verifies required services are available before running tests
# Checks:
#   - MarkLogic accessible: GET http://localhost:8002
#   - MLEAProxy running: GET http://localhost:8080/status
#   - Manage template exists: GET http://localhost:8002/manage/LATEST/servers/Manage
# For Kerberos: Also checks for valid Kerberos ticket (klist)
# Exits with clear error message if any prerequisite missing
# Returns: 0 on success, exits with code 3 on failure
```

### Credential Configuration

**MarkLogic credentials:**
```bash
MARKLOGIC_ADMIN_USER="admin"
MARKLOGIC_ADMIN_PASS="admin"
MARKLOGIC_HOST="localhost"
MARKLOGIC_PORT="8002"
```

**MLEAProxy configuration:**
```bash
MLEAPROXY_HOST="localhost"
MLEAPROXY_PORT="8080"
# Test users from users.json: admin, user1, user2, user3
# Default password: "password"
```

---

## Protocol-Specific Test Scripts

### Common Test Flow

Each test script follows this pattern:

```bash
1. Source marklogic-utils.sh
2. Parse command-line arguments (--verbose, --no-cleanup)
3. Display test header with protocol name
4. Check prerequisites (MarkLogic, MLEAProxy running)
5. Cleanup existing resources (idempotent start)
6. Create external security configuration
7. Create test AppServer from Manage template
8. Wait for AppServer to be ready
9. Run protocol-specific authentication tests
10. Verify JSON response (user, roles)
11. Display results (pass/fail summary)
12. Cleanup resources (unless --no-cleanup)
13. Exit with appropriate status code
```

### Exit Codes

```
0 - All tests passed
1 - Test failed (authentication or verification error)
2 - Setup failed (couldn't create AppServer or external security)
3 - Prerequisites missing (MLEAProxy not running, MarkLogic unavailable)
```

### test-ldap-integration.sh

**Configuration:**
- AppServer name: `MLEAProxy-LDAP-Test`
- Port: 9003
- Authentication: `ldap`
- Authorization: `internal`
- External security name: `MLEAProxy-LDAP`

**External Security Config:**
```xml
<external-security xmlns="http://marklogic.com/manage">
  <external-security-name>MLEAProxy-LDAP</external-security-name>
  <authentication>ldap</authentication>
  <cache-timeout>300</cache-timeout>
  <authorization>internal</authorization>
  <ldap-server-uri>ldap://localhost:10389</ldap-server-uri>
  <ldap-base>dc=marklogic,dc=local</ldap-base>
  <ldap-attribute>uid</ldap-attribute>
  <ldap-bind-method>simple</ldap-bind-method>
</external-security>
```

**Test execution:**
```bash
Request: GET http://localhost:9003/manage/LATEST/?format=json
Auth: Basic authentication (username: admin, password: password)

Flow:
  1. MarkLogic receives request with Basic auth
  2. MarkLogic connects to ldap://localhost:10389 (MLEAProxy LDAP proxy)
  3. MLEAProxy authenticates against users.json
  4. MLEAProxy returns user DN and attributes
  5. MarkLogic uses internal authorization (maps to MarkLogic user "admin")
  6. MarkLogic returns response with user and roles

Verify:
  ✓ HTTP 200 status
  ✓ Valid JSON response
  ✓ user-name = "admin"
  ✓ roles array contains "admin"
```

### test-kerberos-integration.sh

**Configuration:**
- AppServer name: `MLEAProxy-Kerberos-Test`
- Port: 9004
- Authentication: `kerberos`
- Authorization: `internal`
- External security name: `MLEAProxy-Kerberos`

**External Security Config:**
```xml
<external-security xmlns="http://marklogic.com/manage">
  <external-security-name>MLEAProxy-Kerberos</external-security-name>
  <authentication>kerberos</authentication>
  <cache-timeout>300</cache-timeout>
  <authorization>internal</authorization>
  <kerberos-principal>HTTP/rocky@MARKLOGIC.LOCAL</kerberos-principal>
  <kerberos-keytab-path>/path/to/http.keytab</kerberos-keytab-path>
</external-security>
```

**Prerequisites:**
```bash
# User must obtain Kerberos ticket before running test
kinit mluser1@MARKLOGIC.LOCAL
# Password: password
# Verify ticket: klist
```

**Test execution:**
```bash
Request: GET http://localhost:9004/manage/LATEST/?format=json
Auth: SPNEGO (Kerberos ticket via curl --negotiate -u :)

Flow:
  1. curl sends Kerberos ticket in Authorization header
  2. MarkLogic validates ticket via SPNEGO
  3. MarkLogic extracts Kerberos principal (mluser1@MARKLOGIC.LOCAL)
  4. MarkLogic maps to internal user "krbuser1" (or creates temp user)
  5. MarkLogic returns response with user identity

Verify:
  ✓ HTTP 200 status
  ✓ Valid JSON response
  ✓ user-name contains Kerberos identity
  ✓ Authentication succeeded (user not anonymous)
```

**Note:** Kerberos test requires valid ticket; script checks for active ticket before proceeding.

### test-oauth-integration.sh

**Configuration:**
- AppServer name: `MLEAProxy-OAuth-Test`
- Port: 9006
- Authentication: `oauth2`
- Authorization: `internal`
- External security name: `MLEAProxy-OAuth`

**External Security Config:**
```xml
<external-security xmlns="http://marklogic.com/manage">
  <external-security-name>MLEAProxy-OAuth</external-security-name>
  <authentication>oauth2</authentication>
  <cache-timeout>300</cache-timeout>
  <authorization>internal</authorization>
  <oauth-authorization-endpoint>http://localhost:8080/oauth/authorize</oauth-authorization-endpoint>
  <oauth-token-endpoint>http://localhost:8080/oauth/token</oauth-token-endpoint>
  <oauth-jwks-uri>http://localhost:8080/oauth/jwks</oauth-jwks-uri>
</external-security>
```

**Test execution:**
```bash
Step 1: Obtain JWT token from MLEAProxy
  POST http://localhost:8080/oauth/token
  Body: grant_type=password&username=admin&password=password&client_id=marklogic&client_secret=secret
  Extract: access_token from JSON response

Step 2: Test endpoint with Bearer token
  GET http://localhost:9006/manage/LATEST/?format=json
  Header: Authorization: Bearer {access_token}

Flow:
  1. MarkLogic receives Bearer token
  2. MarkLogic fetches JWKS from http://localhost:8080/oauth/jwks
  3. MarkLogic validates token signature using public key
  4. MarkLogic extracts claims (sub, roles, etc.)
  5. MarkLogic maps to internal user from 'sub' claim
  6. MarkLogic applies roles from token claims

Verify:
  ✓ Token generation succeeded
  ✓ HTTP 200 status with Bearer token
  ✓ Valid JSON response
  ✓ user-name matches token 'sub' claim
  ✓ roles array contains expected roles from token
```

### test-saml-integration.sh

**Configuration:**
- AppServer name: `MLEAProxy-SAML-Test`
- Port: 9005
- Authentication: `saml`
- Authorization: `internal`
- External security name: `MLEAProxy-SAML`

**External Security Config:**
```xml
<external-security xmlns="http://marklogic.com/manage">
  <external-security-name>MLEAProxy-SAML</external-security-name>
  <authentication>saml</authentication>
  <cache-timeout>300</cache-timeout>
  <authorization>internal</authorization>
  <saml-idp-metadata-url>http://localhost:8080/saml/idp-metadata</saml-idp-metadata-url>
  <saml-sp-entity-id>http://localhost:9005</saml-sp-entity-id>
  <saml-attribute-names>
    <saml-attribute-name>uid</saml-attribute-name>
    <saml-attribute-name>roles</saml-attribute-name>
  </saml-attribute-names>
</external-security>
```

**Test execution (Approach B - Automated):**
```bash
Step 1: Generate SAML assertion using MLEAProxy
  POST http://localhost:8080/saml/wrapassertion
  Body: userid=admin&password=password&roles=admin
  Extract: Base64-encoded SAML assertion from response

Step 2: Test endpoint with SAML assertion
  GET http://localhost:9005/manage/LATEST/?format=json
  Cookie or Header: SAML assertion (MarkLogic-specific format)

Flow:
  1. Script generates SAML assertion via /saml/wrapassertion
  2. MarkLogic receives assertion in HTTP header/cookie
  3. MarkLogic validates assertion against IdP metadata
  4. MarkLogic verifies signature using IdP certificate
  5. MarkLogic extracts user and roles from assertion attributes
  6. MarkLogic maps to internal user

Verify:
  ✓ Assertion generation succeeded
  ✓ HTTP 200 status with SAML assertion
  ✓ Valid JSON response
  ✓ user-name from assertion attribute
  ✓ roles from assertion attributes
```

**Fallback (Approach C - if automated fails):**
```bash
If programmatic SAML assertion testing proves too complex:
  1. Create external security configuration
  2. Create AppServer configuration
  3. Verify IdP metadata is accessible:
     GET http://localhost:8080/saml/idp-metadata
  4. Document manual testing procedure in README:
     - Open browser to http://localhost:9005
     - Follow SAML redirect to MLEAProxy IdP
     - Enter credentials (admin/password)
     - Verify successful login and access to /manage/LATEST/
  5. Mark test as "Configuration Verified - Manual Testing Required"
  6. Exit with success (configuration is correct even if automation incomplete)
```

---

## Error Handling

### Prerequisites Check

**Before each test:**
```bash
check_prerequisites():
  1. Check MarkLogic Server accessible
     GET http://localhost:8002
     Fail if: Connection refused, timeout, or non-2xx response
     
  2. Check MLEAProxy running
     GET http://localhost:8080/status
     Fail if: Connection refused, timeout, or non-2xx response
     
  3. Check Manage template exists
     GET http://localhost:8002/manage/LATEST/servers/Manage?format=json
     Fail if: 404 or error response
     
  4. For Kerberos test only: Check for valid ticket
     klist
     Fail if: No credentials cache found or ticket expired
     Prompt: "Run: kinit mluser1@MARKLOGIC.LOCAL"

Exit with code 3 and clear error message if any check fails
```

### Cleanup on Error

**Error handling strategy:**
```bash
trap cleanup_on_error EXIT ERR

cleanup_on_error():
  1. Log error message with context
  2. Attempt to delete test AppServer (if partially created)
  3. Attempt to delete external security config (if partially created)
  4. Report what was cleaned up
  5. Exit with appropriate status code

Idempotent operations:
  - All create operations delete existing resources first
  - All delete operations succeed even if resource doesn't exist
  - Safe to re-run any test script multiple times
  - No orphaned resources left behind on failure
```

### Failure Modes

**Setup failures (exit code 2):**
- Cannot retrieve Manage configuration template
- Cannot create external security configuration (invalid XML, API error)
- Cannot create test AppServer (port in use, configuration error)
- AppServer doesn't become ready within timeout period

**Test failures (exit code 1):**
- Authentication fails (HTTP 401/403)
- Unexpected HTTP status (500, 404, etc.)
- Invalid JSON response (parse error)
- User mismatch (got different user than expected)
- Missing expected roles (role mapping failed)

**Prerequisite failures (exit code 3):**
- MarkLogic Server not running
- MLEAProxy not running
- Manage AppServer not found
- (Kerberos only) No valid Kerberos ticket

---

## Output Format

### Console Output

**Standard output (non-verbose):**
```
🔧 MLEAProxy OAuth Integration Test
================================================================================

Prerequisites:
  ✓ MarkLogic Server accessible (http://localhost:8002)
  ✓ MLEAProxy running (http://localhost:8080)
  ✓ Manage template available (port 8002)

Setup:
  ✓ Deleted existing external security 'MLEAProxy-OAuth'
  ✓ Created external security 'MLEAProxy-OAuth'
  ✓ Deleted existing AppServer 'MLEAProxy-OAuth-Test'
  ✓ Created AppServer 'MLEAProxy-OAuth-Test' on port 9006
  ⏳ Waiting for AppServer to be ready... (2s)

Testing:
  → Step 1: Obtaining JWT token from MLEAProxy
    ✓ Token retrieved successfully
  → Step 2: Testing endpoint with Bearer token
    ✓ HTTP 200 OK
    ✓ Valid JSON response
    ✓ User: admin
    ✓ Roles: ["admin"]

Cleanup:
  ✓ Deleted AppServer 'MLEAProxy-OAuth-Test'
  ✓ Deleted external security 'MLEAProxy-OAuth'

================================================================================
✅ OAuth Integration Test: PASSED
================================================================================
```

**Failure output example:**
```
Testing:
  → Authenticating as 'admin' against http://localhost:9003/manage/LATEST/?format=json
    ✗ HTTP 401 Unauthorized
    ✗ Authentication failed

Cleanup:
  ✓ Deleted AppServer 'MLEAProxy-LDAP-Test'
  ✓ Deleted external security 'MLEAProxy-LDAP'

================================================================================
❌ LDAP Integration Test: FAILED
================================================================================
Error: Authentication failed - check LDAP configuration and MLEAProxy logs
```

### Verbose Mode

**Enable with --verbose flag:**
```bash
./test-oauth-integration.sh --verbose

Additional output:
  - Full HTTP request headers and body
  - Full HTTP response headers and body
  - Detailed JSON parsing steps
  - External security XML configuration shown
  - AppServer JSON configuration shown
  - Timing information for each step
```

### No-Cleanup Mode

**Enable with --no-cleanup flag:**
```bash
./test-ldap-integration.sh --no-cleanup

Behavior:
  - Runs test as normal
  - Skips cleanup section at end
  - Leaves AppServer and external security in place
  - Useful for: Manual inspection, debugging, iterative testing
  - Warning: Must manually clean up before re-running test
```

---

## JSON Response Verification

### Expected Response Structure

**MarkLogic Management API response:**
```json
{
  "user-name": "admin",
  "roles": ["admin", "manage-user"],
  // ... other fields (server info, etc.)
}
```

### Verification Logic

**verify_json_response function:**
```bash
verify_json_response(response_body, expected_user, expected_roles)

Steps:
  1. Parse JSON using jq
     Fail if: Invalid JSON syntax
     
  2. Extract user-name field
     Command: echo "$response_body" | jq -r '."user-name"'
     Fail if: Field missing or null
     
  3. Verify user matches expected
     Compare: extracted user == expected_user
     Fail if: Mismatch
     
  4. Extract roles array
     Command: echo "$response_body" | jq -r '.roles[]'
     Fail if: Field missing or not an array
     
  5. Verify expected roles present (subset check)
     For each expected_role:
       Check if role in roles array
     Note: Don't require exact match - MarkLogic adds system roles
     Fail if: Any expected role missing

Returns:
  - 0 on success
  - 1 on failure with diagnostic message

Output:
  ✓ Valid JSON response
  ✓ User: admin
  ✓ Roles: ["admin"] (subset of actual roles)
```

**Example role verification:**
```bash
Expected roles: ["admin"]
Actual roles: ["admin", "manage-user", "security"]
Result: ✓ Pass (expected roles are subset of actual)

Expected roles: ["admin", "custom-role"]
Actual roles: ["admin", "manage-user"]
Result: ✗ Fail (custom-role missing)
```

---

## Testing Strategy

### Manual Testing Procedure

**Before committing scripts:**

1. **Test each protocol individually:**
   ```bash
   # On rocky server
   ssh rocky
   cd ~/MLEAProxy
   git pull
   java -jar target/mlesproxy-2.0.3.jar &
   
   cd examples/marklogic
   ./test-ldap-integration.sh
   ./test-oauth-integration.sh
   ./test-saml-integration.sh
   ./test-kerberos-integration.sh
   ```

2. **Test failure modes:**
   - Stop MLEAProxy: Verify prerequisite check catches it
   - Use wrong credentials: Verify authentication failure is caught
   - Use port already in use: Verify setup failure is caught

3. **Test idempotency:**
   - Run same test twice: Should succeed both times
   - Run with --no-cleanup then again: Should clean up existing resources

4. **Test cleanup:**
   - Run test with --no-cleanup
   - Manually verify AppServer exists: `curl http://localhost:9006/manage/LATEST/?format=json`
   - Run test without --no-cleanup
   - Verify AppServer is deleted

### Automated Testing (Future)

**Could add to MLEAProxy CI/CD:**
```yaml
# GitHub Actions workflow (future enhancement)
marklogic-integration-tests:
  runs-on: ubuntu-latest
  services:
    marklogic:
      image: marklogic/marklogic-server:latest
      ports:
        - 8002:8002
  steps:
    - name: Start MLEAProxy
      run: java -jar target/mlesproxy-2.0.3.jar &
    - name: Run integration tests
      run: |
        cd examples/marklogic
        for test in test-*.sh; do
          ./$test || exit 1
        done
```

---

## Documentation (README.md)

### README Structure

**examples/marklogic/README.md:**

```markdown
# MLEAProxy MarkLogic Integration Tests

Automated integration tests to verify MLEAProxy authentication protocols work with MarkLogic Server.

## Overview

These scripts test MLEAProxy integration with MarkLogic Server across 4 authentication protocols:
- LDAP (ldap auth + internal authz)
- Kerberos (kerberos auth + internal authz)
- SAML (exclusive)
- OAuth 2.0 (exclusive)

Each test creates a MarkLogic AppServer configured with MLEAProxy external security, 
performs authentication, and verifies the user and roles are correct.

## Prerequisites

1. **MarkLogic Server running** on localhost:8002
   - Manage AppServer available on port 8002 (used as template, never modified)
   - Admin credentials: admin/admin

2. **MLEAProxy running** on localhost:8080
   - Built and started: `java -jar target/mlesproxy-2.0.3.jar`
   - Test users available in users.json

3. **For Kerberos test only:**
   - Valid Kerberos ticket: `kinit mluser1@MARKLOGIC.LOCAL`
   - Password: password

## Usage

### Run all tests
```bash
for test in test-*.sh; do ./$test; done
```

### Run individual test
```bash
./test-oauth-integration.sh
./test-ldap-integration.sh
./test-kerberos-integration.sh
./test-saml-integration.sh
```

### Options
```bash
./test-oauth-integration.sh --verbose      # Show detailed HTTP traffic
./test-oauth-integration.sh --no-cleanup   # Leave resources for inspection
```

## Test Details

[Document each test's configuration and expected behavior]

## SAML Manual Testing

[If SAML automation falls back to manual testing, document procedure here]

## Troubleshooting

[Common issues and solutions]
```

---

## Implementation Notes

### Dependencies

**Required commands:**
- `curl` - For HTTP requests
- `jq` - For JSON parsing and verification
- `base64` - For encoding/decoding (SAML assertions)
- `kinit`, `klist` - For Kerberos ticket management (Kerberos test only)

**Check at script start:**
```bash
for cmd in curl jq base64; do
  command -v $cmd >/dev/null 2>&1 || {
    echo "Error: $cmd is required but not installed"
    exit 3
  }
done
```

### MarkLogic REST API Usage

**Key endpoints:**

```bash
# List all servers
GET http://localhost:8002/manage/LATEST/servers?format=json

# Get specific server config
GET http://localhost:8002/manage/LATEST/servers/Manage?format=json

# Create server
POST http://localhost:8002/manage/LATEST/servers
Content-Type: application/json
Body: {server configuration JSON}

# Delete server
DELETE http://localhost:8002/manage/LATEST/servers/{name}

# List external security configs
GET http://localhost:8002/manage/LATEST/external-security?format=json

# Create external security
POST http://localhost:8002/manage/LATEST/external-security
Content-Type: application/xml
Body: {external security XML}

# Delete external security
DELETE http://localhost:8002/manage/LATEST/external-security/{name}
```

**Authentication for management API:**
```bash
# All management API calls use MarkLogic admin credentials
curl -u admin:admin ...
```

### Manage Template Cloning

**Properties to preserve from Manage:**
- `group-name`
- `modules-database`
- `content-database`
- `root`
- `url-rewriter`
- `error-handler`
- All advanced settings (timeout, threads, etc.)

**Properties to customize:**
- `server-name` - Set to test AppServer name
- `port` - Set to test port (9003-9006)
- `authentication` - Set to protocol (ldap|kerberos|saml|oauth2)
- `authorization` - Set to internal (or ldap for future combinations)
- `external-security` - Set to external security config name

**Properties to remove/unset:**
- `server-id` - Let MarkLogic assign new ID
- Any Manage-specific settings that shouldn't be cloned

---

## Success Criteria

**Implementation complete when:**

1. ✅ All 4 protocol test scripts exist and are executable
2. ✅ Shared utilities (`marklogic-utils.sh`) provides all common functions
3. ✅ Each test can run independently and succeed
4. ✅ All tests can run sequentially without interference
5. ✅ Tests are idempotent (can run multiple times safely)
6. ✅ Cleanup removes all test resources
7. ✅ README documents usage and troubleshooting
8. ✅ Tests verify JSON response with correct user and roles
9. ✅ Error handling catches all failure modes with clear messages
10. ✅ Prerequisites are checked before tests run

**Per-protocol success:**
- LDAP: Authenticates admin user via MLEAProxy LDAP proxy, returns admin role
- Kerberos: Accepts SPNEGO ticket, returns Kerberos principal identity
- OAuth: Accepts Bearer token, validates via JWKS, returns token subject and roles
- SAML: Either automated (generates/validates assertions) or documented manual procedure

---

## Rollback Plan

**If tests fail or cause issues:**

1. **Manual cleanup script:**
   ```bash
   # Delete all test AppServers
   for port in 9003 9004 9005 9006; do
     curl -X DELETE -u admin:admin \
       "http://localhost:8002/manage/LATEST/servers/MLEAProxy-*-Test"
   done
   
   # Delete all test external security configs
   for name in MLEAProxy-LDAP MLEAProxy-Kerberos MLEAProxy-SAML MLEAProxy-OAuth; do
     curl -X DELETE -u admin:admin \
       "http://localhost:8002/manage/LATEST/external-security/$name"
   done
   ```

2. **Script rollback:**
   - Remove `examples/marklogic/` directory
   - No impact on MLEAProxy code or existing examples
   - No impact on MarkLogic (only test resources created/deleted)

**Safe to rollback:** Tests only create/delete test resources, never modify:
- Existing AppServers (ports 8000, 8001, 8002)
- Production external security configurations
- User data or databases

---

## Future Enhancements

**After minimal version (scope A) is working:**

1. **Additional authentication/authorization combinations:**
   - LDAP auth + LDAP authz
   - Kerberos auth + LDAP authz

2. **Validation scripts:**
   - Similar to github.io examples: `validate-oauth2-config.sh`
   - Verify external security configuration is correct
   - Check for common misconfigurations

3. **Multi-user testing:**
   - Test with different MLEAProxy users (user1, user2, user3)
   - Verify role mapping for different privilege levels
   - Test unauthorized access (wrong password, missing roles)

4. **Comprehensive test suite:**
   - Positive tests (valid authentication)
   - Negative tests (invalid credentials, expired tokens)
   - Edge cases (special characters in usernames, long role lists)

5. **Utility functions:**
   - Key rotation scripts (similar to rotate-oauth-keys.sh)
   - Security audit scripts
   - Cleanup scripts for orphaned resources

6. **CI/CD integration:**
   - GitHub Actions workflow
   - Automated testing on each commit
   - Report integration test results

---

## Summary

**What we're building:**
- Minimal MarkLogic integration test scripts for MLEAProxy
- 4 protocol tests (LDAP, Kerberos, SAML, OAuth)
- Modular bash scripts with shared utilities
- Designed to run on same server as MarkLogic (typically rocky)

**Implementation approach:**
- Clone Manage AppServer configuration as template
- Customize only security settings per protocol
- Create external security configurations pointing to MLEAProxy
- Test authentication and verify JSON responses
- Clean up test resources when done

**User benefit:**
- Automated verification that MLEAProxy → MarkLogic integration works
- Working examples for each protocol
- Foundation for expanded testing and validation
- Reduces manual testing burden
