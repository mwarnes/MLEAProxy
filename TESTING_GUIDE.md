# MLEAProxy Test Suite Documentation

## Overview

This comprehensive test suite validates all major functionality of the MLEAProxy application including:
- **OAuth 2.0 Token Endpoint** - JWT token generation with roles
- **SAML 2.0 Authentication** - Signed assertions with role attributes
- **LDAP Proxy** - Directory operations and group membership
- **Integration Tests** - End-to-end workflows

## Test Structure

```
src/test/java/com/marklogic/
├── MLEAProxyTests.java                    # Basic Spring context test
├── MLEAProxyIntegrationTest.java          # Integration tests
└── handlers/
    ├── LDAPRequestHandlerTest.java        # LDAP proxy tests
    └── undertow/
        ├── OAuthTokenHandlerTest.java     # OAuth endpoint tests
        └── SAMLAuthHandlerTest.java       # SAML authentication tests
```

## Running Tests

### Run All Tests
```bash
mvn test
```

### Run Specific Test Class
```bash
mvn test -Dtest=OAuthTokenHandlerTest
mvn test -Dtest=SAMLAuthHandlerTest
mvn test -Dtest=LDAPRequestHandlerTest
mvn test -Dtest=MLEAProxyIntegrationTest
```

### Run Specific Test Method
```bash
mvn test -Dtest=OAuthTokenHandlerTest#testGenerateTokenWithAllParameters
mvn test -Dtest=SAMLAuthHandlerTest#testSAMLResponseGeneration
```

### Run Tests with Coverage
```bash
mvn test jacoco:report
```

### Skip Tests During Build
```bash
mvn clean package -DskipTests
```

## Test Categories

### 1. OAuth Token Endpoint Tests (`OAuthTokenHandlerTest`)

**Test Coverage:**
- ✅ Token generation with all parameters
- ✅ Token generation without roles
- ✅ Single role handling
- ✅ Multiple roles handling
- ✅ Role whitespace trimming
- ✅ Empty roles parameter
- ✅ Missing required parameters
- ✅ JWT structure validation
- ✅ JWT expiration time validation
- ✅ JWT issuer and audience validation
- ✅ Special characters in username
- ✅ Multiple scopes handling

**Key Test Methods:**
```java
testGenerateTokenWithAllParameters()    // Full OAuth flow with roles
testGenerateTokenWithoutRoles()         // Token without roles
testRoleWhitespaceTrimming()            // Trim spaces from roles
testJWTExpirationTime()                 // Validate 3600s expiration
testSpecialCharactersInUsername()       // Handle user@example.com
```

**Sample Test Execution:**
```bash
mvn test -Dtest=OAuthTokenHandlerTest#testGenerateTokenWithAllParameters
```

### 2. SAML Authentication Tests (`SAMLAuthHandlerTest`)

**Test Coverage:**
- ✅ SAML authentication form display
- ✅ SAML response generation with signature
- ✅ Role inclusion in AttributeStatement
- ✅ Single role handling
- ✅ Multiple roles handling
- ✅ SubjectConfirmationData validation
- ✅ Conditions element validation
- ✅ AuthnStatement validation
- ✅ Empty roles handling
- ✅ Role whitespace trimming
- ✅ Digital signature validation

**Key Test Methods:**
```java
testSAMLResponseGeneration()            // Generate signed SAML response
testSAMLRoleInclusion()                 // Verify roles in assertion
testSubjectConfirmationData()           // Validate recipient/timestamps
testConditionsElement()                 // Validate validity period
testAuthnStatement()                    // Validate authentication context
```

**Sample Test Execution:**
```bash
mvn test -Dtest=SAMLAuthHandlerTest#testSAMLRoleInclusion
```

### 3. LDAP Proxy Tests (`LDAPRequestHandlerTest`)

**Test Coverage:**
- ✅ LDAP connection establishment
- ✅ Anonymous bind
- ✅ Authenticated bind with valid credentials
- ✅ Invalid credentials handling
- ✅ User search by sAMAccountName
- ✅ Group membership retrieval
- ✅ Group searches
- ✅ Non-existent user handling
- ✅ Wildcard searches
- ✅ Base scope searches
- ✅ Attribute retrieval
- ✅ Complex filter (AND)
- ✅ OR filter
- ✅ NOT filter

**Key Test Methods:**
```java
testLDAPConnection()                    // Establish connection
testAuthenticatedBind()                 // Valid credentials bind
testSearchBySAMAccountName()            // Find user by username
testUserGroupMemberships()              // Get user's groups
testComplexFilter()                     // AND/OR/NOT filters
```

**Sample Test Execution:**
```bash
mvn test -Dtest=LDAPRequestHandlerTest#testUserGroupMemberships
```

**Note:** LDAP tests use an in-memory UnboundID LDAP server for testing.

### 4. Integration Tests (`MLEAProxyIntegrationTest`)

**Test Coverage:**
- ✅ Application context loading
- ✅ OAuth to SAML workflow integration
- ✅ Base64 encoding endpoint
- ✅ Base64 decoding endpoint
- ✅ Round-trip encoding/decoding
- ✅ Multiple OAuth tokens with different roles
- ✅ SAML with special characters
- ✅ Concurrent OAuth requests
- ✅ Token expiration validation
- ✅ Home page loading

**Key Test Methods:**
```java
testOAuthToSAMLWorkflow()               // End-to-end OAuth + SAML
testBase64Encoding()                    // Test /encode endpoint
testRoundTripEncodingDecoding()         // Encode then decode
testMultipleOAuthTokensWithDifferentRoles() // Concurrent tokens
```

**Sample Test Execution:**
```bash
mvn test -Dtest=MLEAProxyIntegrationTest#testOAuthToSAMLWorkflow
```

## Test Data

### OAuth Test Parameters
```
client_id: test-client, marklogic-client
client_secret: test-secret, secret
username: testuser, admin, user
password: testpass, password
scope: read, write, read write admin profile
roles: admin,user,developer, marklogic-admin
```

### SAML Test Parameters
```
samlid: test-saml-id-123, integration-test-id
userid: testuser, roleuser, user@example.com
authnresult: success
notbefore: 2025-10-04T10:00:00Z
notonorafter: 2025-10-04T11:00:00Z
assertionurl: http://localhost:9002/consumer
roles: admin,user,developer, marklogic-admin
```

### LDAP Test Data
```
Base DN: dc=marklogic,dc=local
Users OU: ou=users,dc=marklogic,dc=local
Groups OU: ou=groups,dc=marklogic,dc=local

Test User:
  DN: cn=user1,ou=users,dc=marklogic,dc=local
  sAMAccountName: user1
  Password: password
  Groups: appreader, appwriter

Manager:
  DN: cn=manager,ou=users,dc=marklogic,dc=local
  Password: password
```

## Assertions Used

### OAuth Tests
```java
assertEquals(3, parts.length)                          // JWT has 3 parts
assertEquals("testuser", payloadJson.get("sub"))       // Subject claim
assertEquals(3600, exp - iat)                          // 1 hour expiration
assertTrue(rolesArray.isArray())                       // Roles is array
assertEquals("admin", rolesArray.get(0).asText())      // Role value
```

### SAML Tests
```java
assertEquals("Response", root.getLocalName())          // Root element
assertEquals("Success", statusCode.getAttribute())     // Success status
assertTrue(signatures.getLength() > 0)                 // Signature exists
assertEquals("testuser", nameIds.item(0))              // NameID
assertEquals(3, attrValues.getLength())                // 3 role values
```

### LDAP Tests
```java
assertEquals(ResultCode.SUCCESS, bindResult)           // Successful bind
assertEquals(1, searchResult.getEntryCount())          // Found 1 entry
assertNotNull(memberOf)                                // Has groups
assertTrue(Arrays.asList(memberOf).contains())         // Has specific group
```

## Test Utilities

### JWT Validation Helper
```java
private void assertJWTStructure(String jwt) {
    String[] parts = jwt.split("\\.");
    assertEquals(3, parts.length, "JWT should have 3 parts");
    for (String part : parts) {
        assertTrue(part.matches("[A-Za-z0-9_-]+"));
    }
}
```

### SAML XML Parsing
```java
DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
factory.setNamespaceAware(true);
DocumentBuilder builder = factory.newDocumentBuilder();
Document doc = builder.parse(new ByteArrayInputStream(decodedBytes));
```

## Continuous Integration

### GitHub Actions Example
```yaml
name: Test Suite
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK 21
        uses: actions/setup-java@v2
        with:
          java-version: '21'
      - name: Run tests
        run: mvn test
      - name: Generate coverage
        run: mvn jacoco:report
```

## Test Coverage Goals

| Component | Coverage Target | Current Status |
|-----------|----------------|----------------|
| OAuth Handler | 90%+ | ✅ Comprehensive |
| SAML Handler | 90%+ | ✅ Comprehensive |
| LDAP Handler | 80%+ | ✅ Core functionality |
| Integration | 85%+ | ✅ Key workflows |
| Overall | 85%+ | 🎯 Target |

## Troubleshooting

### Tests Fail with Port Already in Use
```bash
# Kill existing processes
killall java

# Or use different port in application-test.properties
server.port=0  # Random port
```

### LDAP Tests Fail
```bash
# Ensure UnboundID LDAP SDK is in dependencies
mvn dependency:tree | grep unboundid

# Check test LDAP server port 10389 is available
lsof -i :10389
```

### SAML Signature Validation Fails
```bash
# Verify certificate files exist
ls -la src/main/resources/static/certificates/

# Check certificate validity
openssl x509 -in certificate.pem -text -noout
```

### OAuth JWT Parsing Fails
```bash
# Verify JWT structure manually
echo "JWT_TOKEN" | cut -d'.' -f2 | base64 -d

# Check RSA key format
openssl rsa -in privkey.pem -text -noout
```

## Best Practices

1. **Isolation** - Each test should be independent
2. **Cleanup** - Use @AfterEach for cleanup operations
3. **Descriptive Names** - Use @DisplayName for clear test descriptions
4. **Assertions** - Use specific assertions (assertEquals vs assertTrue)
5. **Test Data** - Use constants or fixtures for test data
6. **Mocking** - Mock external dependencies when appropriate
7. **Performance** - Keep tests fast (< 5 seconds each)
8. **Documentation** - Add comments for complex test logic

## Next Steps

### Planned Enhancements
- [ ] Add performance tests for concurrent requests
- [ ] Add security tests for injection vulnerabilities
- [ ] Add load tests for LDAP proxy under high load
- [ ] Add end-to-end tests with real MarkLogic server
- [ ] Add mutation testing with PITest
- [ ] Add contract tests for API consumers

### Additional Test Scenarios
- [ ] OAuth token refresh flow
- [ ] SAML logout requests
- [ ] LDAP connection pool exhaustion
- [ ] Certificate expiration handling
- [ ] Rate limiting tests
- [ ] Error response validation

## Resources

- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [MockMvc Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/testing.html#spring-mvc-test-framework)
- [UnboundID LDAP SDK](https://docs.ldap.com/ldap-sdk/)
- [OAuth 2.0 RFC 6749](https://tools.ietf.org/html/rfc6749)
- [SAML 2.0 Specification](https://docs.oasis-open.org/security/saml/v2.0/)

## Support

For test-related questions or issues:
1. Check this guide first
2. Review test logs: `target/surefire-reports/`
3. Enable debug logging: `logging.level.com.marklogic=DEBUG`
4. Review application logs during test execution

---

**Last Updated:** October 4, 2025  
**Test Framework:** JUnit 5.9.3  
**Spring Boot:** 3.3.5  
**Java:** 21.0.4
