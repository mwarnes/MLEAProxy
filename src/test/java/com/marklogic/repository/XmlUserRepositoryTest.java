package com.marklogic.repository;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for XmlUserRepository.
 */
class XmlUserRepositoryTest {
    
    private XmlUserRepository repository;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        repository = new XmlUserRepository();
    }
    
    @Test
    void testLoadUsersFromXml() throws Exception {
        File xmlFile = createTestUsersXml();
        
        repository.initialize(xmlFile.getAbsolutePath());
        
        assertTrue(repository.isInitialized());
        assertEquals(4, repository.getUserCount());
    }
    
    @Test
    void testFindUserByUsername() throws Exception {
        File xmlFile = createTestUsersXml();
        repository.initialize(xmlFile.getAbsolutePath());
        
        XmlUserRepository.UserInfo user = repository.findByUsername("admin");
        
        assertNotNull(user);
        assertEquals("admin", user.getUsername());
        assertEquals("password", user.getPassword());
        assertEquals(1, user.getRoles().size());
        assertTrue(user.getRoles().contains("admin"));
    }
    
    @Test
    void testFindUserCaseInsensitive() throws Exception {
        File xmlFile = createTestUsersXml();
        repository.initialize(xmlFile.getAbsolutePath());
        
        XmlUserRepository.UserInfo user1 = repository.findByUsername("ADMIN");
        XmlUserRepository.UserInfo user2 = repository.findByUsername("admin");
        XmlUserRepository.UserInfo user3 = repository.findByUsername("Admin");
        
        assertNotNull(user1);
        assertNotNull(user2);
        assertNotNull(user3);
        assertEquals(user1.getUsername(), user2.getUsername());
        assertEquals(user2.getUsername(), user3.getUsername());
    }
    
    @Test
    void testGetUserRoles() throws Exception {
        File xmlFile = createTestUsersXml();
        repository.initialize(xmlFile.getAbsolutePath());
        
        List<String> roles = repository.getUserRoles("user1");
        
        assertEquals(3, roles.size());
        assertTrue(roles.contains("appreader"));
        assertTrue(roles.contains("appwriter"));
        assertTrue(roles.contains("appadmin"));
    }
    
    @Test
    void testGetUserRolesForNonExistentUser() throws Exception {
        File xmlFile = createTestUsersXml();
        repository.initialize(xmlFile.getAbsolutePath());
        
        List<String> roles = repository.getUserRoles("nonexistent");
        
        assertTrue(roles.isEmpty());
    }
    
    @Test
    void testUserWithNoRoles() throws Exception {
        File xmlFile = createTestUsersXml();
        repository.initialize(xmlFile.getAbsolutePath());
        
        List<String> roles = repository.getUserRoles("manager");
        
        assertTrue(roles.isEmpty());
    }
    
    @Test
    void testValidatePassword() throws Exception {
        File xmlFile = createTestUsersXml();
        repository.initialize(xmlFile.getAbsolutePath());
        
        assertTrue(repository.validatePassword("admin", "password"));
        assertFalse(repository.validatePassword("admin", "wrongpassword"));
        assertFalse(repository.validatePassword("nonexistent", "password"));
    }
    
    @Test
    void testInitializeWithNullPath() throws Exception {
        repository.initialize(null);
        
        assertFalse(repository.isInitialized());
        assertEquals(0, repository.getUserCount());
    }
    
    @Test
    void testInitializeWithEmptyPath() throws Exception {
        repository.initialize("");
        
        assertFalse(repository.isInitialized());
        assertEquals(0, repository.getUserCount());
    }
    
    @Test
    void testInitializeWithNonExistentFile() throws Exception {
        repository.initialize("/nonexistent/path/users.xml");
        
        assertFalse(repository.isInitialized());
        assertEquals(0, repository.getUserCount());
    }
    
    @Test
    void testRfc6238CodeIsLoaded() throws Exception {
        File xmlFile = createTestUsersXml();
        repository.initialize(xmlFile.getAbsolutePath());
        
        XmlUserRepository.UserInfo user = repository.findByUsername("user1");
        
        assertNotNull(user);
        assertEquals("IVKFK3B4OI4VQMDGENEVURT5GRYFIWTM", user.getRfc6238code());
    }
    
    @Test
    void testUserDnIsLoaded() throws Exception {
        File xmlFile = createTestUsersXml();
        repository.initialize(xmlFile.getAbsolutePath());
        
        XmlUserRepository.UserInfo user = repository.findByUsername("admin");
        
        assertNotNull(user);
        assertEquals("cn=admin", user.getDn());
    }
    
    @Test
    void testMultipleRolesForUser() throws Exception {
        File xmlFile = createTestUsersXml();
        repository.initialize(xmlFile.getAbsolutePath());
        
        XmlUserRepository.UserInfo user = repository.findByUsername("user2");
        
        assertNotNull(user);
        assertEquals(2, user.getRoles().size());
        assertTrue(user.getRoles().contains("appreader"));
        assertTrue(user.getRoles().contains("appwriter"));
    }
    
    @Test
    void testGetXmlFilePath() throws Exception {
        File xmlFile = createTestUsersXml();
        String xmlPath = xmlFile.getAbsolutePath();
        
        repository.initialize(xmlPath);
        
        assertEquals(xmlPath, repository.getXmlFilePath());
    }
    
    private File createTestUsersXml() throws Exception {
        File xmlFile = tempDir.resolve("users.xml").toFile();
        
        try (FileWriter writer = new FileWriter(xmlFile)) {
            writer.write("<?xml version=\"1.0\"?>\n");
            writer.write("<ldap>\n");
            writer.write("  <users basedn=\"ou=users,dc=marklogic,dc=local\">\n");
            writer.write("    <user dn=\"cn=manager\">\n");
            writer.write("      <sAMAccountName>manager</sAMAccountName>\n");
            writer.write("      <userPassword>password</userPassword>\n");
            writer.write("    </user>\n");
            writer.write("    <user dn=\"cn=admin\">\n");
            writer.write("      <sAMAccountName>admin</sAMAccountName>\n");
            writer.write("      <memberOf>cn=admin,ou=groups,dc=marklogic,dc=local</memberOf>\n");
            writer.write("      <userPassword>password</userPassword>\n");
            writer.write("      <rfc6238code>IVKFK3B4OI4VQMDGENEVURT5GRYFIWTM</rfc6238code>\n");
            writer.write("    </user>\n");
            writer.write("    <user dn=\"cn=user1\">\n");
            writer.write("      <sAMAccountName>user1</sAMAccountName>\n");
            writer.write("      <memberOf>cn=appreader,ou=groups,dc=marklogic,dc=local</memberOf>\n");
            writer.write("      <memberOf>cn=appwriter,ou=groups,dc=marklogic,dc=local</memberOf>\n");
            writer.write("      <memberOf>cn=appadmin,ou=groups,dc=marklogic,dc=local</memberOf>\n");
            writer.write("      <userPassword>password</userPassword>\n");
            writer.write("      <rfc6238code>IVKFK3B4OI4VQMDGENEVURT5GRYFIWTM</rfc6238code>\n");
            writer.write("    </user>\n");
            writer.write("    <user dn=\"cn=user2\">\n");
            writer.write("      <sAMAccountName>user2</sAMAccountName>\n");
            writer.write("      <memberOf>cn=appreader,ou=groups,dc=marklogic,dc=local</memberOf>\n");
            writer.write("      <memberOf>cn=appwriter,ou=groups,dc=marklogic,dc=local</memberOf>\n");
            writer.write("      <userPassword>password</userPassword>\n");
            writer.write("    </user>\n");
            writer.write("  </users>\n");
            writer.write("</ldap>\n");
        }
        
        return xmlFile;
    }
}
