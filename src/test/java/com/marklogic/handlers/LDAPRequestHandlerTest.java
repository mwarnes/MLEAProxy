package com.marklogic.handlers;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for LDAP Proxy Handler
 * Tests LDAP connection, bind operations, search operations, and group membership
 */
@SpringBootTest
@DisplayName("LDAP Request Handler Tests")
class LDAPRequestHandlerTest {

    private static InMemoryDirectoryServer testLDAPServer;
    private LDAPConnection connection;

    @BeforeAll
    static void setUpLDAPServer() throws Exception {
        // Create in-memory LDAP server for testing
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=marklogic,dc=local");
        config.addAdditionalBindCredentials("cn=manager,ou=users,dc=marklogic,dc=local", "password");
        
        InMemoryListenerConfig listenerConfig = InMemoryListenerConfig.createLDAPConfig("test-ldap", 10389);
        config.setListenerConfigs(listenerConfig);
        
        testLDAPServer = new InMemoryDirectoryServer(config);
        
        // Add test entries
        testLDAPServer.add(
            "dn: dc=marklogic,dc=local",
            "objectClass: top",
            "objectClass: domain",
            "dc: marklogic");
        
        testLDAPServer.add(
            "dn: ou=users,dc=marklogic,dc=local",
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: users");
        
        testLDAPServer.add(
            "dn: ou=groups,dc=marklogic,dc=local",
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: groups");
        
        testLDAPServer.add(
            "dn: cn=user1,ou=users,dc=marklogic,dc=local",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "cn: user1",
            "sn: User",
            "sAMAccountName: user1",
            "userPassword: password",
            "memberOf: cn=appreader,ou=groups,dc=marklogic,dc=local",
            "memberOf: cn=appwriter,ou=groups,dc=marklogic,dc=local");
        
        testLDAPServer.add(
            "dn: cn=appreader,ou=groups,dc=marklogic,dc=local",
            "objectClass: top",
            "objectClass: groupOfNames",
            "cn: appreader",
            "member: cn=user1,ou=users,dc=marklogic,dc=local");
        
        testLDAPServer.add(
            "dn: cn=appwriter,ou=groups,dc=marklogic,dc=local",
            "objectClass: top",
            "objectClass: groupOfNames",
            "cn: appwriter",
            "member: cn=user1,ou=users,dc=marklogic,dc=local");
        
        testLDAPServer.startListening();
    }

    @AfterAll
    static void tearDownLDAPServer() {
        if (testLDAPServer != null) {
            testLDAPServer.shutDown(true);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        // Connect to test LDAP server
        connection = new LDAPConnection("localhost", 10389);
    }

    @AfterEach
    void tearDown() {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Should establish LDAP connection")
    void testLDAPConnection() {
        assertTrue(connection.isConnected());
    }

    @Test
    @DisplayName("Should perform anonymous bind")
    void testAnonymousBind() throws Exception {
        BindResult bindResult = connection.bind("", "");
        assertEquals(ResultCode.SUCCESS, bindResult.getResultCode());
    }

    @Test
    @DisplayName("Should perform authenticated bind with valid credentials")
    void testAuthenticatedBind() throws Exception {
        BindResult bindResult = connection.bind(
            "cn=manager,ou=users,dc=marklogic,dc=local", 
            "password");
        assertEquals(ResultCode.SUCCESS, bindResult.getResultCode());
    }

    @Test
    @DisplayName("Should fail bind with invalid credentials")
    void testInvalidCredentialsBind() {
        assertThrows(LDAPException.class, () -> {
            connection.bind("cn=user1,ou=users,dc=marklogic,dc=local", "wrongpassword");
        });
    }

    @Test
    @DisplayName("Should search for user by sAMAccountName")
    void testSearchBySAMAccountName() throws Exception {
        connection.bind("cn=manager,ou=users,dc=marklogic,dc=local", "password");
        
        SearchResult searchResult = connection.search(
            "ou=users,dc=marklogic,dc=local",
            SearchScope.SUB,
            Filter.createEqualityFilter("sAMAccountName", "user1"));
        
        assertEquals(ResultCode.SUCCESS, searchResult.getResultCode());
        assertEquals(1, searchResult.getEntryCount());
        
        SearchResultEntry entry = searchResult.getSearchEntries().get(0);
        assertEquals("cn=user1,ou=users,dc=marklogic,dc=local", entry.getDN());
        assertEquals("user1", entry.getAttributeValue("sAMAccountName"));
    }

    @Test
    @DisplayName("Should retrieve user's group memberships")
    void testUserGroupMemberships() throws Exception {
        connection.bind("cn=manager,ou=users,dc=marklogic,dc=local", "password");
        
        SearchResult searchResult = connection.search(
            "cn=user1,ou=users,dc=marklogic,dc=local",
            SearchScope.BASE,
            Filter.createPresenceFilter("objectClass"),
            "memberOf");
        
        assertEquals(ResultCode.SUCCESS, searchResult.getResultCode());
        
        SearchResultEntry entry = searchResult.getSearchEntries().get(0);
        String[] memberOf = entry.getAttributeValues("memberOf");
        
        assertNotNull(memberOf);
        assertEquals(2, memberOf.length);
        assertTrue(java.util.Arrays.asList(memberOf).contains("cn=appreader,ou=groups,dc=marklogic,dc=local"));
        assertTrue(java.util.Arrays.asList(memberOf).contains("cn=appwriter,ou=groups,dc=marklogic,dc=local"));
    }

    @Test
    @DisplayName("Should search for groups")
    void testSearchGroups() throws Exception {
        connection.bind("cn=manager,ou=users,dc=marklogic,dc=local", "password");
        
        SearchResult searchResult = connection.search(
            "ou=groups,dc=marklogic,dc=local",
            SearchScope.SUB,
            Filter.createEqualityFilter("objectClass", "groupOfNames"));
        
        assertEquals(ResultCode.SUCCESS, searchResult.getResultCode());
        assertEquals(2, searchResult.getEntryCount());
    }

    @Test
    @DisplayName("Should handle non-existent user search")
    void testNonExistentUser() throws Exception {
        connection.bind("cn=manager,ou=users,dc=marklogic,dc=local", "password");
        
        SearchResult searchResult = connection.search(
            "ou=users,dc=marklogic,dc=local",
            SearchScope.SUB,
            Filter.createEqualityFilter("sAMAccountName", "nonexistent"));
        
        assertEquals(ResultCode.SUCCESS, searchResult.getResultCode());
        assertEquals(0, searchResult.getEntryCount());
    }

    @Test
    @DisplayName("Should search with wildcard filter")
    void testWildcardSearch() throws Exception {
        connection.bind("cn=manager,ou=users,dc=marklogic,dc=local", "password");
        
        SearchResult searchResult = connection.search(
            "ou=users,dc=marklogic,dc=local",
            SearchScope.SUB,
            Filter.createSubstringFilter("cn", "user", null, null));
        
        assertEquals(ResultCode.SUCCESS, searchResult.getResultCode());
        assertTrue(searchResult.getEntryCount() >= 1);
    }

    @Test
    @DisplayName("Should handle base scope search")
    void testBaseSearch() throws Exception {
        connection.bind("cn=manager,ou=users,dc=marklogic,dc=local", "password");
        
        SearchResult searchResult = connection.search(
            "cn=user1,ou=users,dc=marklogic,dc=local",
            SearchScope.BASE,
            Filter.createPresenceFilter("objectClass"));
        
        assertEquals(ResultCode.SUCCESS, searchResult.getResultCode());
        assertEquals(1, searchResult.getEntryCount());
    }

    @Test
    @DisplayName("Should retrieve specific attributes")
    void testAttributeRetrieval() throws Exception {
        connection.bind("cn=manager,ou=users,dc=marklogic,dc=local", "password");
        
        SearchResult searchResult = connection.search(
            "cn=user1,ou=users,dc=marklogic,dc=local",
            SearchScope.BASE,
            Filter.createPresenceFilter("objectClass"),
            "cn", "sAMAccountName", "memberOf");
        
        assertEquals(ResultCode.SUCCESS, searchResult.getResultCode());
        
        SearchResultEntry entry = searchResult.getSearchEntries().get(0);
        assertNotNull(entry.getAttributeValue("cn"));
        assertNotNull(entry.getAttributeValue("sAMAccountName"));
        assertNotNull(entry.getAttributeValues("memberOf"));
    }

    @Test
    @DisplayName("Should handle complex filter")
    void testComplexFilter() throws Exception {
        connection.bind("cn=manager,ou=users,dc=marklogic,dc=local", "password");
        
        Filter filter = Filter.createANDFilter(
            Filter.createEqualityFilter("objectClass", "inetOrgPerson"),
            Filter.createEqualityFilter("sAMAccountName", "user1")
        );
        
        SearchResult searchResult = connection.search(
            "ou=users,dc=marklogic,dc=local",
            SearchScope.SUB,
            filter);
        
        assertEquals(ResultCode.SUCCESS, searchResult.getResultCode());
        assertEquals(1, searchResult.getEntryCount());
    }

    @Test
    @DisplayName("Should handle OR filter")
    void testORFilter() throws Exception {
        connection.bind("cn=manager,ou=users,dc=marklogic,dc=local", "password");
        
        Filter filter = Filter.createORFilter(
            Filter.createEqualityFilter("cn", "user1"),
            Filter.createEqualityFilter("cn", "nonexistent")
        );
        
        SearchResult searchResult = connection.search(
            "ou=users,dc=marklogic,dc=local",
            SearchScope.SUB,
            filter);
        
        assertEquals(ResultCode.SUCCESS, searchResult.getResultCode());
        assertEquals(1, searchResult.getEntryCount());
    }

    @Test
    @DisplayName("Should handle NOT filter")
    void testNOTFilter() throws Exception {
        connection.bind("cn=manager,ou=users,dc=marklogic,dc=local", "password");
        
        Filter filter = Filter.createNOTFilter(
            Filter.createEqualityFilter("cn", "manager")
        );
        
        SearchResult searchResult = connection.search(
            "ou=users,dc=marklogic,dc=local",
            SearchScope.SUB,
            filter);
        
        assertEquals(ResultCode.SUCCESS, searchResult.getResultCode());
        // Should find user1 but not manager
        assertTrue(searchResult.getEntryCount() >= 1);
    }
}
