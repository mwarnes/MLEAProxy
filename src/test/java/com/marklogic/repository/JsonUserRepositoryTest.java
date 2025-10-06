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
 * Unit tests for JsonUserRepository.
 */
class JsonUserRepositoryTest {
    
    private JsonUserRepository repository;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        repository = new JsonUserRepository();
    }
    
    @Test
    void testLoadUsersFromJson() throws Exception {
        File jsonFile = createTestUsersJson();
        
        repository.initialize(jsonFile.getAbsolutePath());
        
        assertTrue(repository.isInitialized());
        assertEquals(4, repository.getUserCount());
    }
    
    @Test
    void testFindUserByUsername() throws Exception {
        File jsonFile = createTestUsersJson();
        repository.initialize(jsonFile.getAbsolutePath());
        
        JsonUserRepository.UserInfo user = repository.findByUsername("admin");
        
        assertNotNull(user);
        assertEquals("admin", user.getUsername());
        assertEquals("password", user.getPassword());
        assertEquals(1, user.getRoles().size());
        assertTrue(user.getRoles().contains("admin"));
    }
    
    @Test
    void testFindUserCaseInsensitive() throws Exception {
        File jsonFile = createTestUsersJson();
        repository.initialize(jsonFile.getAbsolutePath());
        
        JsonUserRepository.UserInfo user1 = repository.findByUsername("ADMIN");
        JsonUserRepository.UserInfo user2 = repository.findByUsername("admin");
        JsonUserRepository.UserInfo user3 = repository.findByUsername("Admin");
        
        assertNotNull(user1);
        assertNotNull(user2);
        assertNotNull(user3);
        assertEquals(user1.getUsername(), user2.getUsername());
        assertEquals(user2.getUsername(), user3.getUsername());
    }
    
    @Test
    void testGetUserRoles() throws Exception {
        File jsonFile = createTestUsersJson();
        repository.initialize(jsonFile.getAbsolutePath());
        
        List<String> roles = repository.getUserRoles("user1");
        
        assertEquals(3, roles.size());
        assertTrue(roles.contains("appreader"));
        assertTrue(roles.contains("appwriter"));
        assertTrue(roles.contains("appadmin"));
    }
    
    @Test
    void testGetUserRolesForNonExistentUser() throws Exception {
        File jsonFile = createTestUsersJson();
        repository.initialize(jsonFile.getAbsolutePath());
        
        List<String> roles = repository.getUserRoles("nonexistent");
        
        assertTrue(roles.isEmpty());
    }
    
    @Test
    void testUserWithNoRoles() throws Exception {
        File jsonFile = createTestUsersJson();
        repository.initialize(jsonFile.getAbsolutePath());
        
        List<String> roles = repository.getUserRoles("manager");
        
        assertTrue(roles.isEmpty());
    }
    
    @Test
    void testValidatePassword() throws Exception {
        File jsonFile = createTestUsersJson();
        repository.initialize(jsonFile.getAbsolutePath());
        
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
        repository.initialize("/nonexistent/path/users.json");
        
        assertFalse(repository.isInitialized());
        assertEquals(0, repository.getUserCount());
    }
    
    @Test
    void testRfc6238CodeIsLoaded() throws Exception {
        File jsonFile = createTestUsersJson();
        repository.initialize(jsonFile.getAbsolutePath());
        
        JsonUserRepository.UserInfo user = repository.findByUsername("user1");
        
        assertNotNull(user);
        assertEquals("IVKFK3B4OI4VQMDGENEVURT5GRYFIWTM", user.getRfc6238code());
    }
    
    @Test
    void testUserDnIsLoaded() throws Exception {
        File jsonFile = createTestUsersJson();
        repository.initialize(jsonFile.getAbsolutePath());
        
        JsonUserRepository.UserInfo user = repository.findByUsername("admin");
        
        assertNotNull(user);
        assertEquals("cn=admin", user.getDn());
    }
    
    @Test
    void testMultipleRolesForUser() throws Exception {
        File jsonFile = createTestUsersJson();
        repository.initialize(jsonFile.getAbsolutePath());
        
        JsonUserRepository.UserInfo user = repository.findByUsername("user2");
        
        assertNotNull(user);
        assertEquals(2, user.getRoles().size());
        assertTrue(user.getRoles().contains("appreader"));
        assertTrue(user.getRoles().contains("appwriter"));
    }
    
    @Test
    void testGetJsonFilePath() throws Exception {
        File jsonFile = createTestUsersJson();
        String jsonPath = jsonFile.getAbsolutePath();
        
        repository.initialize(jsonPath);
        
        assertEquals(jsonPath, repository.getJsonFilePath());
    }
    
    private File createTestUsersJson() throws Exception {
        File jsonFile = tempDir.resolve("users.json").toFile();
        
        try (FileWriter writer = new FileWriter(jsonFile)) {
            writer.write("{\n");
            writer.write("  \"users\": [\n");
            writer.write("    {\n");
            writer.write("      \"username\": \"manager\",\n");
            writer.write("      \"password\": \"password\",\n");
            writer.write("      \"dn\": \"cn=manager\",\n");
            writer.write("      \"roles\": []\n");
            writer.write("    },\n");
            writer.write("    {\n");
            writer.write("      \"username\": \"admin\",\n");
            writer.write("      \"password\": \"password\",\n");
            writer.write("      \"dn\": \"cn=admin\",\n");
            writer.write("      \"roles\": [\"admin\"],\n");
            writer.write("      \"rfc6238code\": \"IVKFK3B4OI4VQMDGENEVURT5GRYFIWTM\"\n");
            writer.write("    },\n");
            writer.write("    {\n");
            writer.write("      \"username\": \"user1\",\n");
            writer.write("      \"password\": \"password\",\n");
            writer.write("      \"dn\": \"cn=user1\",\n");
            writer.write("      \"roles\": [\"appreader\", \"appwriter\", \"appadmin\"],\n");
            writer.write("      \"rfc6238code\": \"IVKFK3B4OI4VQMDGENEVURT5GRYFIWTM\"\n");
            writer.write("    },\n");
            writer.write("    {\n");
            writer.write("      \"username\": \"user2\",\n");
            writer.write("      \"password\": \"password\",\n");
            writer.write("      \"dn\": \"cn=user2\",\n");
            writer.write("      \"roles\": [\"appreader\", \"appwriter\"]\n");
            writer.write("    }\n");
            writer.write("  ]\n");
            writer.write("}\n");
        }
        
        return jsonFile;
    }
}
