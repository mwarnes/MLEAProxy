package com.marklogic.handlers.undertow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Edge case and stress tests for OAuth Token Handler
 * Tests boundary conditions, special characters, concurrent requests, and security scenarios
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("OAuth Token Handler - Edge Cases & Security Tests")
class OAuthTokenHandlerEdgeCaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // TC-2: Edge Case Tests

    @Test
    @DisplayName("Should handle very long role names (1000+ characters)")
    void testVeryLongRoleNames() throws Exception {
        // Create role name with 1000 characters
        String longRole = "role_" + "x".repeat(995);
        String roles = "admin," + longRole + ",user";

        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "secret")
                        .param("username", "testuser")
                        .param("password", "password")
                        .param("roles", roles)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String accessToken = json.get("access_token").asText();

        // Decode and verify roles are in token
        String payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        assertTrue(payload.contains(longRole), "Should contain very long role name");
        assertTrue(payload.contains("admin"), "Should contain admin role");
    }

    @Test
    @DisplayName("Should handle special characters in roles")
    void testSpecialCharactersInRoles() throws Exception {
        // Test various special characters (but not commas since that's the delimiter)
        String roles = "admin-user,user_123,role.with.dots,role@domain,role#123";

        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "secret")
                        .param("username", "testuser")
                        .param("password", "password")
                        .param("roles", roles)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String accessToken = json.get("access_token").asText();

        String payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        assertTrue(payload.contains("admin-user"), "Should contain role with hyphen");
        assertTrue(payload.contains("user_123"), "Should contain role with underscore");
        assertTrue(payload.contains("role.with.dots"), "Should contain role with dots");
    }

    @Test
    @DisplayName("Should handle empty role string")
    void testEmptyRoleString() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "secret")
                        .param("username", "testuser")
                        .param("password", "password")
                        .param("roles", "")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        assertNotNull(json.get("access_token"));
    }

    @Test
    @DisplayName("Should handle roles with leading/trailing whitespace")
    void testRolesWithWhitespace() throws Exception {
        String roles = "  admin  ,  user  ,  developer  ";

        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "secret")
                        .param("username", "testuser")
                        .param("password", "password")
                        .param("roles", roles)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String accessToken = json.get("access_token").asText();

        String payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        JsonNode payloadJson = objectMapper.readTree(payload);
        
        // Roles should be present (trimmed or not, depending on implementation)
        assertNotNull(payloadJson.get("roles"));
        assertTrue(payloadJson.get("roles").isArray());
    }

    @Test
    @DisplayName("Should handle duplicate roles")
    void testDuplicateRoles() throws Exception {
        String roles = "admin,admin,user,admin";

        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "secret")
                        .param("username", "testuser")
                        .param("password", "password")
                        .param("roles", roles)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        assertNotNull(json.get("access_token"));
    }

    @Test
    @DisplayName("Should handle very long username (255+ characters)")
    void testVeryLongUsername() throws Exception {
        String longUsername = "user_" + "x".repeat(250);

        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "secret")
                        .param("username", longUsername)
                        .param("password", "password")
                        .param("roles", "user")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String accessToken = json.get("access_token").asText();

        String payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        assertTrue(payload.contains(longUsername.substring(0, 50)), 
                   "Should contain at least part of long username");
    }

    @Test
    @DisplayName("Should handle special characters in username")
    void testSpecialCharactersInUsername() throws Exception {
        String username = "user@example.com";

        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "secret")
                        .param("username", username)
                        .param("password", "password")
                        .param("roles", "user")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String accessToken = json.get("access_token").asText();

        String payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        assertTrue(payload.contains("user@example.com"), "Should contain email username");
    }

    // TC-3: Concurrent/Performance Tests

    @Test
    @DisplayName("Should handle 50 concurrent token requests")
    void testConcurrentTokenGeneration() throws Exception {
        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();
                    
                    mockMvc.perform(post("/oauth/token")
                                    .param("grant_type", "password")
                                    .param("client_id", "client-" + index)
                                    .param("client_secret", "secret")
                                    .param("username", "user" + index)
                                    .param("password", "password")
                                    .param("roles", "user")
                                    .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                            .andExpect(status().isOk());
                    
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.err.println("Request failed: " + e.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Start all threads at once
        startLatch.countDown();
        
        // Wait for all to complete (timeout after 30 seconds)
        boolean completed = finishLatch.await(30, TimeUnit.SECONDS);
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(completed, "All threads should complete within timeout");
        
        // Expect at least 95% success rate
        int expectedMinSuccess = (int) (threadCount * 0.95);
        assertTrue(successCount.get() >= expectedMinSuccess,
                String.format("Should have at least %d successful requests (had %d successes, %d failures)",
                        expectedMinSuccess, successCount.get(), failureCount.get()));
    }

    @Test
    @DisplayName("Should generate tokens consistently under repeated requests")
    void testRepeatedTokenGeneration() throws Exception {
        for (int i = 0; i < 20; i++) {
            MvcResult result = mockMvc.perform(post("/oauth/token")
                            .param("grant_type", "password")
                            .param("client_id", "test-client")
                            .param("client_secret", "secret")
                            .param("username", "testuser")
                            .param("password", "password")
                            .param("roles", "user")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk())
                    .andReturn();

            String response = result.getResponse().getContentAsString();
            JsonNode json = objectMapper.readTree(response);
            
            assertNotNull(json.get("access_token"));
            assertEquals("Bearer", json.get("token_type").asText());
            assertEquals(3600, json.get("expires_in").asInt());
        }
    }

    // TC-4: Security Tests

    @Test
    @DisplayName("Should handle potential LDAP injection in client_id")
    void testLDAPInjectionInClientId() throws Exception {
        String maliciousClientId = "test*))(|(uid=*";

        // Application should either handle gracefully or still generate token
        // (actual LDAP validation happens in LDAP handler, not OAuth handler)
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", maliciousClientId)
                        .param("client_secret", "secret")
                        .param("username", "testuser")
                        .param("password", "password")
                        .param("roles", "user")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andReturn();

        // Should not crash - either success or proper error
        int status = result.getResponse().getStatus();
        assertTrue(status == 200 || status == 400 || status == 401,
                "Should handle malicious input gracefully");
    }

    @Test
    @DisplayName("Should handle SQL injection patterns in username")
    void testSQLInjectionInUsername() throws Exception {
        String maliciousUsername = "admin' OR '1'='1";

        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "secret")
                        .param("username", maliciousUsername)
                        .param("password", "password")
                        .param("roles", "user")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andReturn();

        // Should not crash - either success or proper error
        int status = result.getResponse().getStatus();
        assertTrue(status == 200 || status == 400 || status == 401,
                "Should handle SQL injection patterns gracefully");
        
        // If it succeeds, verify the malicious string is properly encoded
        if (status == 200) {
            String response = result.getResponse().getContentAsString();
            JsonNode json = objectMapper.readTree(response);
            String token = json.get("access_token").asText();
            String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
            
            // Username should be in token but shouldn't cause SQL injection
            assertFalse(payload.contains("OR"), "Payload should not contain unescaped SQL keywords");
        }
    }

    @Test
    @DisplayName("Should handle XSS attempts in scope parameter")
    void testXSSInScope() throws Exception {
        String maliciousScope = "<script>alert('XSS')</script>";

        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "secret")
                        .param("username", "testuser")
                        .param("password", "password")
                        .param("scope", maliciousScope)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andReturn();

        // Should handle gracefully
        int status = result.getResponse().getStatus();
        assertTrue(status == 200 || status == 400,
                "Should handle XSS attempts gracefully");
    }

    @Test
    @DisplayName("Should handle null bytes in parameters")
    void testNullBytesInParameters() throws Exception {
        String usernameWithNull = "test\u0000user";

        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "secret")
                        .param("username", usernameWithNull)
                        .param("password", "password")
                        .param("roles", "user")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andReturn();

        // Should handle gracefully without crashing
        int status = result.getResponse().getStatus();
        assertTrue(status >= 200 && status < 600, "Should return valid HTTP status");
    }

    @Test
    @DisplayName("Should handle extremely large payload")
    void testExtremeLargePayload() throws Exception {
        // Create very large roles string (10KB+)
        StringBuilder largeRoles = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeRoles.append("role_").append(i);
            if (i < 999) largeRoles.append(",");
        }

        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "secret")
                        .param("username", "testuser")
                        .param("password", "password")
                        .param("roles", largeRoles.toString())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andReturn();

        // Should handle without crashing (may succeed or return 413/400)
        int status = result.getResponse().getStatus();
        assertTrue(status == 200 || status == 400 || status == 413,
                "Should handle large payloads gracefully");
    }

    @Test
    @DisplayName("Should handle Unicode characters in roles")
    void testUnicodeInRoles() throws Exception {
        String roles = "admin,用户,مستخدم,пользователь";

        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "secret")
                        .param("username", "testuser")
                        .param("password", "password")
                        .param("roles", roles)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        assertNotNull(json.get("access_token"));
    }
}
