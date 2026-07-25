# MLEAProxy MarkLogic Integration Tests

Automated integration tests to verify MLEAProxy authentication protocols work with MarkLogic Server.

## Overview

These scripts test MLEAProxy integration with MarkLogic Server across 4 authentication protocols:

| Protocol | Authentication | Authorization | Test Port | Test User |
|----------|----------------|---------------|-----------|-----------|
| **LDAP** | ldap | internal | 9003 | admin/password |
| **Kerberos** | kerberos | internal | 9004 | mluser1@MARKLOGIC.LOCAL |
| **SAML** | saml | internal | 9005 | admin/password |
| **OAuth** | oauth2 | internal | 9006 | admin/password |

Each test creates a MarkLogic AppServer configured with MLEAProxy external security, performs authentication, and verifies the user and roles are correct.

## Prerequisites

### 1. MarkLogic Server Running

- Accessible at `http://localhost:8002`
- Manage AppServer available (used as template, never modified)
- Admin credentials: `admin` / `admin`

### 2. MLEAProxy Running

- Built and started: `java -jar target/mlesproxy-2.0.3.jar`
- Accessible at `http://localhost:8080`
- Test users available in `users.json` (admin, user1, user2, etc.)
- Default password: `password`

### 3. Required Commands

All scripts require:
- `curl` - For HTTP requests
- `jq` - For JSON parsing and verification
- `bash` - For running scripts

### 4. For Kerberos Test Only

Valid Kerberos ticket required:

```bash
export KRB5_CONFIG=./kerberos/krb5.conf
kinit mluser1@MARKLOGIC.LOCAL
# Password: password
klist  # Verify ticket
```

## Usage

### Run All Tests

```bash
cd examples/marklogic
for test in test-*.sh; do ./$test; done
```

### Run Individual Test

```bash
./test-ldap-integration.sh
./test-oauth-integration.sh
./test-saml-integration.sh
./test-kerberos-integration.sh
```

### Command-Line Options

```bash
./test-oauth-integration.sh --verbose      # Show detailed HTTP traffic
./test-oauth-integration.sh --no-cleanup   # Leave resources for inspection
```

## Test Details

### LDAP Integration Test

**Port:** 9003  
**External Security:** Points to `ldap://localhost:10389` (MLEAProxy LDAP proxy)  
**Test User:** admin/password  
**Expected:** User `admin` with role `admin`

**What it tests:**
1. Creates external security config for LDAP
2. Creates test AppServer using Manage as template
3. Authenticates via Basic auth (admin/password)
4. MarkLogic queries MLEAProxy LDAP proxy
5. Verifies JSON response shows correct user and roles

**Run:**
```bash
./test-ldap-integration.sh
```

### OAuth Integration Test

**Port:** 9006  
**External Security:** Points to `http://localhost:8080/oauth/jwks`  
**Test User:** admin/password  
**Expected:** User `admin` with role `admin`

**What it tests:**
1. Obtains JWT token from MLEAProxy `/oauth/token`
2. Creates external security config for OAuth
3. Creates test AppServer
4. Authenticates with Bearer token
5. MarkLogic validates token via JWKS
6. Verifies JSON response shows user from token claims

**Run:**
```bash
./test-oauth-integration.sh
```

### SAML Integration Test

**Port:** 9005  
**External Security:** Points to `http://localhost:8080/saml/idp-metadata`  
**Test User:** admin/password  
**Expected:** Configuration verified (automated testing if available)

**What it tests:**
1. Creates external security config for SAML
2. Creates test AppServer
3. Verifies IdP metadata is accessible
4. Attempts automated assertion generation
5. Falls back to manual testing documentation if automated approach unavailable

**Run:**
```bash
./test-saml-integration.sh
```

**Manual Testing (if automated fails):**
1. Run the test script to create configuration
2. Open browser to `http://localhost:9005`
3. Follow SAML redirect to MLEAProxy IdP
4. Enter credentials: admin/password
5. Verify successful login and access to `/manage/LATEST/`

### Kerberos Integration Test

**Port:** 9004  
**External Security:** Kerberos principal and keytab  
**Test User:** Kerberos ticket for mluser1@MARKLOGIC.LOCAL  
**Expected:** Kerberos identity verified

**Prerequisites:**
```bash
export KRB5_CONFIG=./kerberos/krb5.conf
kinit mluser1@MARKLOGIC.LOCAL
# Password: password
```

**What it tests:**
1. Verifies Kerberos ticket exists
2. Creates external security config for Kerberos
3. Creates test AppServer
4. Authenticates with SPNEGO (curl --negotiate)
5. Verifies Kerberos identity in response

**Run:**
```bash
./test-kerberos-integration.sh
```

## Troubleshooting

### MarkLogic Not Accessible

```
✗ MarkLogic Server not accessible at http://localhost:8002
```

**Solution:** Verify MarkLogic is running and accessible on port 8002.

### MLEAProxy Not Running

```
✗ MLEAProxy not running at http://localhost:8080
```

**Solution:** Start MLEAProxy:
```bash
cd /path/to/MLEAProxy
java -jar target/mlesproxy-2.0.3.jar
```

### No Kerberos Ticket (Kerberos test)

```
✗ No Kerberos ticket found
Run: kinit mluser1@MARKLOGIC.LOCAL
```

**Solution:** Obtain ticket:
```bash
export KRB5_CONFIG=./kerberos/krb5.conf
kinit mluser1@MARKLOGIC.LOCAL
# Password: password
```

### Authentication Failed

```
✗ HTTP request failed
```

**Solutions:**
- Check MLEAProxy logs for errors
- Verify test user exists in `users.json`
- For LDAP: Check LDAP proxy is running (port 10389)
- For OAuth: Verify JWKS endpoint is accessible
- For SAML: Verify IdP metadata is accessible
- For Kerberos: Verify ticket is valid (klist)

### JSON Parsing Error

```
✗ Invalid JSON response
```

**Solution:** Run with `--verbose` to see full response:
```bash
./test-oauth-integration.sh --verbose
```

## Cleanup

Each test automatically cleans up resources (AppServer and external security) on exit.

To leave resources for manual inspection:
```bash
./test-ldap-integration.sh --no-cleanup
```

Then manually clean up:
```bash
# Delete AppServer
curl -X DELETE -u admin:admin \
  "http://localhost:8002/manage/LATEST/servers/MLEAProxy-LDAP-Test?group-id=Default"

# Delete external security
curl -X DELETE -u admin:admin \
  "http://localhost:8002/manage/LATEST/external-security/MLEAProxy-LDAP"
```

## Exit Codes

- `0` - Test passed
- `1` - Test failed (authentication or verification error)
- `2` - Setup failed (couldn't create AppServer or external security)
- `3` - Prerequisites missing (MarkLogic, MLEAProxy, or Kerberos ticket)

## Files

```
examples/marklogic/
├── README.md                      # This file
├── marklogic-utils.sh             # Shared utility functions
├── test-ldap-integration.sh       # LDAP test
├── test-kerberos-integration.sh   # Kerberos test
├── test-saml-integration.sh       # SAML test
├── test-oauth-integration.sh      # OAuth test
└── configs/                       # Optional: saved external security configs
```

## Architecture

**Script Structure:**
- Each test is standalone and idempotent
- Shared utilities in `marklogic-utils.sh`
- Template: Clones Manage AppServer (port 8002) configuration
- Test AppServers on ports 9003-9006
- Automatic cleanup on exit (unless `--no-cleanup`)

**Test Flow:**
1. Check prerequisites (MarkLogic, MLEAProxy running)
2. Delete existing resources (idempotent start)
3. Create external security configuration
4. Create test AppServer from Manage template
5. Wait for AppServer to be ready
6. Run protocol-specific authentication test
7. Verify JSON response (user, roles)
8. Display pass/fail results
9. Cleanup resources

## Notes

- **Template Source:** Uses Manage AppServer (port 8002) as template
- **Never Modified:** Ports 8000, 8001, 8002 are never modified (read-only template)
- **Test Ports:** 9003-9006 are used for test AppServers
- **Credentials:** MarkLogic admin/admin is used for management API only
- **Test Users:** MLEAProxy users (admin, user1, etc.) are used for authentication tests
- **Idempotent:** Safe to run tests multiple times
- **Cleanup:** Resources are automatically deleted unless `--no-cleanup` is used
