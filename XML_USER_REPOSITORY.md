# XML User Repository Integration

## Overview

MLEAProxy now supports loading users and their roles from an external XML file. When configured, both **OAuth 2.0** and **SAML 2.0** authentication handlers will automatically look up users by their `sAMAccountName` and extract roles from `memberOf` LDAP DN entries.

This feature enables:
- ✅ Centralized user management via XML file
- ✅ Dynamic role assignment without code changes
- ✅ Consistent user/role data across OAuth and SAML
- ✅ Password validation (optional)
- ✅ TOTP secret support (rfc6238code)

---

## Configuration

### Command Line Options

You can specify the users.xml file path using any of these command line arguments:

```bash
# Option 1: --users-xml argument
java -jar mleaproxy.jar --users-xml=/path/to/users.xml

# Option 2: --users argument
java -jar mleaproxy.jar --users=/path/to/users.xml

# Option 3: System property
java -Dusers.xml.path=/path/to/users.xml -jar mleaproxy.jar
```

### Application Properties

Add to `application.properties`:

```properties
# XML User Repository Configuration
users.xml.path=/path/to/users.xml
```

### Configuration Priority

The application checks for the users.xml path in this order:
1. Command line argument: `--users-xml`
2. Command line argument: `--users`
3. Application property: `users.xml.path`
4. System property: `users.xml.path`

---

## XML File Format

The users.xml file must follow this structure:

```xml
<?xml version="1.0"?>
<ldap>
    <users basedn="ou=users,dc=marklogic,dc=local">
        <!-- User with admin role -->
        <user dn="cn=admin">
            <sAMAccountName>admin</sAMAccountName>
            <memberOf>cn=admin,ou=groups,dc=marklogic,dc=local</memberOf>
            <userPassword>password</userPassword>
            <rfc6238code>IVKFK3B4OI4VQMDGENEVURT5GRYFIWTM</rfc6238code>
        </user>
        
        <!-- User with multiple roles -->
        <user dn="cn=user1">
            <sAMAccountName>user1</sAMAccountName>
            <memberOf>cn=appreader,ou=groups,dc=marklogic,dc=local</memberOf>
            <memberOf>cn=appwriter,ou=groups,dc=marklogic,dc=local</memberOf>
            <memberOf>cn=appadmin,ou=groups,dc=marklogic,dc=local</memberOf>
            <userPassword>password</userPassword>
            <rfc6238code>IVKFK3B4OI4VQMDGENEVURT5GRYFIWTM</rfc6238code>
        </user>
        
        <!-- User with no roles -->
        <user dn="cn=user2">
            <sAMAccountName>user2</sAMAccountName>
            <userPassword>password</userPassword>
        </user>
    </users>
</ldap>
```

### XML Elements

| Element | Required | Description |
|---------|----------|-------------|
| `<user dn="...">` | Yes | User container with LDAP DN |
| `<sAMAccountName>` | Yes | Username for authentication lookups |
| `<memberOf>` | No | LDAP group DN (role extracted from CN) |
| `<userPassword>` | No | Password for validation (OAuth only) |
| `<rfc6238code>` | No | TOTP secret for 2FA (future use) |

### Role Extraction

Roles are extracted from the `CN` (Common Name) component of the `memberOf` DN:

**Example:**
```xml
<memberOf>cn=admin,ou=groups,dc=marklogic,dc=local</memberOf>
```
**Extracted role:** `admin`

---

## OAuth 2.0 Integration

### Behavior When XML Repository is Configured

**Token Request:**
```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=user1" \
  -d "password=password" \
  -d "client_id=test-client" \
  -d "client_secret=secret"
```

**Process:**
1. ✅ Handler looks up `user1` in XML repository by `sAMAccountName`
2. ✅ If found, extracts roles from `memberOf` elements
3. ✅ Optionally validates password (if `<userPassword>` is present)
4. ✅ Generates JWT token with roles claim
5. ❌ Returns 401 error if user not found

**Generated JWT Token:**
```json
{
  "sub": "user1",
  "iss": "mleaproxy-oauth-server",
  "roles": ["appreader", "appwriter", "appadmin"],
  "roles_string": "appreader appwriter appadmin",
  "client_id": "test-client",
  "grant_type": "password",
  "exp": 1704070800,
  "iat": 1704067200
}
```

### Fallback Behavior (XML Not Configured)

If no XML file is configured, OAuth handler falls back to using the `roles` parameter:

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=user1" \
  -d "password=password" \
  -d "client_id=test-client" \
  -d "client_secret=secret" \
  -d "roles=custom-role,another-role"
```

### Password Validation

When both the XML file **and** `<userPassword>` are present:
- ✅ Password is validated against XML value
- ❌ Returns 401 if password doesn't match
- ⚠️  If `<userPassword>` is missing, no validation is performed

---

## SAML 2.0 Integration

### Behavior When XML Repository is Configured

**SAML Authentication Request:**
```bash
curl -X POST http://localhost:8080/saml/auth \
  -d "userid=user1" \
  -d "authn=success"
```

**Process:**
1. ✅ Handler looks up `user1` in XML repository by `sAMAccountName`
2. ✅ If found, extracts roles from `memberOf` elements
3. ✅ Generates SAML assertion with role attributes
4. ⚠️  If user not found, uses provided `roles` parameter (warning logged)

**Generated SAML Assertion:**
```xml
<saml:Assertion>
  <saml:AttributeStatement>
    <saml:Attribute Name="roles">
      <saml:AttributeValue>appreader</saml:AttributeValue>
      <saml:AttributeValue>appwriter</saml:AttributeValue>
      <saml:AttributeValue>appadmin</saml:AttributeValue>
    </saml:Attribute>
  </saml:AttributeStatement>
</saml:Assertion>
```

### Fallback Behavior (XML Not Configured)

If no XML file is configured, SAML handler uses the `roles` parameter:

```bash
curl -X POST http://localhost:8080/saml/auth \
  -d "userid=user1" \
  -d "roles=custom-role,another-role" \
  -d "authn=success"
```

---

## Usage Examples

### Example 1: Complete Setup with users.xml

**1. Create users.xml file:**
```xml
<?xml version="1.0"?>
<ldap>
    <users basedn="ou=users,dc=marklogic,dc=local">
        <user dn="cn=admin">
            <sAMAccountName>admin</sAMAccountName>
            <memberOf>cn=admin,ou=groups,dc=marklogic,dc=local</memberOf>
            <userPassword>admin123</userPassword>
        </user>
        <user dn="cn=developer">
            <sAMAccountName>developer</sAMAccountName>
            <memberOf>cn=developers,ou=groups,dc=marklogic,dc=local</memberOf>
            <memberOf>cn=users,ou=groups,dc=marklogic,dc=local</memberOf>
            <userPassword>dev123</userPassword>
        </user>
    </users>
</ldap>
```

**2. Start MLEAProxy:**
```bash
java -jar mleaproxy.jar --users-xml=/path/to/users.xml
```

**3. Console output:**
```
✅ XML user repository initialized successfully from: /path/to/users.xml
   Loaded 2 users with role mappings
```

**4. Request OAuth token:**
```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=developer" \
  -d "password=dev123" \
  -d "client_id=my-app" \
  -d "client_secret=secret"
```

**5. Log output:**
```
Looking up user 'developer' in XML user repository
Found user 'developer' with 2 roles from XML: developers,users
Successfully generated OAuth token for user: developer
```

**6. Token contains:**
```json
{
  "sub": "developer",
  "roles": ["developers", "users"]
}
```

### Example 2: SAML Authentication

**Request:**
```bash
curl -X POST http://localhost:8080/saml/auth \
  -d "userid=admin" \
  -d "authn=success"
```

**Log output:**
```
Looking up user 'admin' in XML user repository for SAML
Found user 'admin' with 1 roles from XML: admin
SAML authentication successful for user: admin with roles: admin
```

### Example 3: User Not Found (OAuth)

**Request:**
```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=nonexistent" \
  -d "password=test" \
  -d "client_id=app" \
  -d "client_secret=secret"
```

**Response:**
```json
{
  "error": "invalid_grant",
  "error_description": "Invalid username or password"
}
```

**HTTP Status:** 401 Unauthorized

### Example 4: User Not Found (SAML)

**Request:**
```bash
curl -X POST http://localhost:8080/saml/auth \
  -d "userid=nonexistent" \
  -d "roles=fallback-role" \
  -d "authn=success"
```

**Log output:**
```
⚠️  User 'nonexistent' not found in XML user repository, using provided roles parameter
SAML authentication successful for user: nonexistent with roles: fallback-role
```

**Note:** SAML is more lenient - it logs a warning but continues with the provided roles parameter.

---

## Security Considerations

### XML Security Features

The XML parser is configured with security hardening:
- ✅ External entities disabled (XXE protection)
- ✅ DOCTYPE declarations disabled
- ✅ External DTD loading disabled
- ✅ Entity expansion disabled

### Password Storage

**⚠️  IMPORTANT:** Passwords in the XML file are stored in plain text. This is suitable for:
- Development and testing environments
- Proof-of-concept implementations
- Isolated internal networks

**For production environments:**
- Consider using hashed passwords (requires code modification)
- Use external authentication providers (LDAP, Active Directory)
- Implement secure credential storage solutions

### File Permissions

Ensure proper file permissions on users.xml:

```bash
# Linux/macOS
chmod 600 /path/to/users.xml
chown mleaproxy-user:mleaproxy-group /path/to/users.xml

# Verify
ls -la /path/to/users.xml
# Should show: -rw------- 1 mleaproxy-user mleaproxy-group
```

---

## Troubleshooting

### Issue: Repository Not Initialized

**Log message:**
```
No users.xml file specified - OAuth and SAML will use parameter-based roles
To enable XML-based user lookup, use: --users-xml=/path/to/users.xml
```

**Solution:** Provide the users.xml path via command line or application properties.

---

### Issue: File Not Found

**Log message:**
```
⚠️  XML user file not found: /path/to/users.xml
```

**Solution:** 
- Verify the file path is correct
- Check file permissions
- Ensure file exists

---

### Issue: User Not Found During OAuth Request

**Log message:**
```
⚠️  User 'testuser' not found in XML user repository
```

**Response:** 401 Unauthorized

**Solution:**
- Verify username is correct (case-insensitive)
- Check `<sAMAccountName>` in XML file
- Ensure user exists in XML

---

### Issue: No Roles Returned

**Log message:**
```
ℹ️  User 'testuser' found in XML but has no roles assigned
```

**Solution:** Add `<memberOf>` elements to the user in XML file.

---

### Issue: Invalid XML Format

**Log message:**
```
❌ Failed to initialize XML user repository from: /path/to/users.xml
   [Detailed parsing error]
```

**Solution:**
- Validate XML syntax
- Ensure proper structure (see XML File Format section)
- Check for missing closing tags

---

## Testing

### Unit Tests

Run the XmlUserRepository tests:

```bash
mvn test -Dtest=XmlUserRepositoryTest
```

**Test coverage:**
- ✅ Loading users from XML
- ✅ Finding users by username (case-insensitive)
- ✅ Extracting roles from memberOf
- ✅ Password validation
- ✅ Multiple roles per user
- ✅ Users with no roles
- ✅ TOTP secret loading
- ✅ Error handling (file not found, invalid XML)

### Integration Test

**1. Create test users.xml:**
```bash
cat > /tmp/test-users.xml << 'EOF'
<?xml version="1.0"?>
<ldap>
    <users basedn="ou=users,dc=test,dc=local">
        <user dn="cn=testuser">
            <sAMAccountName>testuser</sAMAccountName>
            <memberOf>cn=testers,ou=groups,dc=test,dc=local</memberOf>
            <userPassword>testpass</userPassword>
        </user>
    </users>
</ldap>
EOF
```

**2. Start MLEAProxy:**
```bash
java -jar mleaproxy.jar --users-xml=/tmp/test-users.xml
```

**3. Test OAuth:**
```bash
curl -v -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=testuser" \
  -d "password=testpass" \
  -d "client_id=test" \
  -d "client_secret=secret"
```

**Expected:** HTTP 200 with JWT token containing `"roles": ["testers"]`

**4. Test SAML:**
```bash
curl -v -X POST http://localhost:8080/saml/auth \
  -d "userid=testuser" \
  -d "authn=success"
```

**Expected:** HTTP 302 redirect with SAML response containing testers role

---

## API Reference

### XmlUserRepository Class

**Package:** `com.marklogic.repository`

#### Methods

**`void initialize(String filePath)`**
- Loads users from the specified XML file
- Throws `Exception` if parsing fails

**`UserInfo findByUsername(String username)`**
- Finds user by sAMAccountName (case-insensitive)
- Returns `null` if not found

**`boolean validatePassword(String username, String password)`**
- Validates user password
- Returns `false` if user not found or password mismatch

**`List<String> getUserRoles(String username)`**
- Returns list of role names for user
- Returns empty list if user not found

**`boolean isInitialized()`**
- Returns `true` if repository is initialized

**`int getUserCount()`**
- Returns number of users loaded

### UserInfo Class

**Properties:**
- `String username` - sAMAccountName
- `String password` - User password (plain text)
- `List<String> roles` - List of role names
- `String dn` - LDAP Distinguished Name
- `String rfc6238code` - TOTP secret

---

## Migration Guide

### Migrating from Parameter-Based Roles

**Before (OAuth):**
```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "username=admin" \
  -d "roles=admin,user"
```

**After (XML-based):**
1. Create users.xml with admin user
2. Start with `--users-xml`
3. Remove `roles` parameter from requests:
```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "username=admin"
```

**Before (SAML):**
```bash
curl -X POST http://localhost:8080/saml/auth \
  -d "userid=admin" \
  -d "roles=admin,user"
```

**After (XML-based):**
```bash
curl -X POST http://localhost:8080/saml/auth \
  -d "userid=admin"
```

---

## Limitations

1. **Password Storage**: Plain text only (no hashing support)
2. **Dynamic Reload**: Changes to XML file require application restart
3. **Performance**: All users loaded into memory (not suitable for 1000+ users)
4. **Authentication**: No password validation for SAML (OAuth only)
5. **Group Nesting**: Nested groups not supported

---

## Future Enhancements

Potential improvements for future releases:

- [ ] Support for hashed passwords (bcrypt, SHA-256)
- [ ] Hot reload of XML file without restart
- [ ] Database backend support (JDBC)
- [ ] LDAP server integration (live queries)
- [ ] Group nesting and inheritance
- [ ] User attribute extensions (email, displayName, etc.)
- [ ] JSON format support (in addition to XML)
- [ ] REST API for user management

---

## Summary

The XML User Repository feature provides:

✅ **Centralized user management** via external XML file  
✅ **Automatic role extraction** from memberOf LDAP DNs  
✅ **OAuth 2.0 integration** with JWT role claims  
✅ **SAML 2.0 integration** with assertion attributes  
✅ **Password validation** for OAuth requests  
✅ **Flexible configuration** (command line, properties, system property)  
✅ **Backward compatible** (falls back to parameter-based roles)  
✅ **Secure XML parsing** (XXE protection)  
✅ **Comprehensive testing** (15+ unit tests)  

For questions or issues, refer to the [main README](./README.md) or open a GitHub issue.
