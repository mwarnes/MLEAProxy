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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive test suite for OAuth 2.0 Token Endpoint
 * Tests token generation, JWT structure, role inclusion, and error handling
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("OAuth Token Endpoint Tests")
class OAuthTokenHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should generate valid access token with all parameters")
    void testGenerateTokenWithAllParameters() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "test-secret")
                        .param("username", "testuser")
                        .param("password", "testpass")
                        .param("scope", "read write")
                        .param("roles", "admin,user,developer")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600))
                .andExpect(jsonPath("$.scope").value("read write"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);
        String accessToken = jsonResponse.get("access_token").asText();

        // Validate JWT structure
        assertJWTStructure(accessToken);

        // Decode and validate JWT payload
        String[] parts = accessToken.split("\\.");
        assertEquals(3, parts.length, "JWT should have 3 parts");

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode payloadJson = objectMapper.readTree(payload);

        // Validate claims
        assertEquals("testuser", payloadJson.get("sub").asText());
        assertEquals("test-client", payloadJson.get("client_id").asText());
        assertEquals("read write", payloadJson.get("scope").asText());
        assertTrue(payloadJson.has("roles"));
        
        JsonNode rolesArray = payloadJson.get("roles");
        assertTrue(rolesArray.isArray());
        assertEquals(3, rolesArray.size());
        assertEquals("admin", rolesArray.get(0).asText());
        assertEquals("user", rolesArray.get(1).asText());
        assertEquals("developer", rolesArray.get(2).asText());
    }

    @Test
    @DisplayName("Should generate token without roles parameter")
    void testGenerateTokenWithoutRoles() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "test-secret")
                        .param("username", "testuser")
                        .param("password", "testpass")
                        .param("scope", "read")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);
        String accessToken = jsonResponse.get("access_token").asText();

        // Decode JWT payload
        String[] parts = accessToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode payloadJson = objectMapper.readTree(payload);

        // Should have empty roles array
        assertTrue(payloadJson.has("roles"));
        assertTrue(payloadJson.get("roles").isArray());
        assertEquals(0, payloadJson.get("roles").size());
    }

    @Test
    @DisplayName("Should handle single role")
    void testGenerateTokenWithSingleRole() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "test-secret")
                        .param("username", "testuser")
                        .param("password", "testpass")
                        .param("roles", "admin")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);
        String accessToken = jsonResponse.get("access_token").asText();

        String[] parts = accessToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode payloadJson = objectMapper.readTree(payload);

        JsonNode rolesArray = payloadJson.get("roles");
        assertEquals(1, rolesArray.size());
        assertEquals("admin", rolesArray.get(0).asText());
    }

    @Test
    @DisplayName("Should trim whitespace from roles")
    void testRoleWhitespaceTrimming() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "test-secret")
                        .param("username", "testuser")
                        .param("password", "testpass")
                        .param("roles", " admin , user , developer ")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);
        String accessToken = jsonResponse.get("access_token").asText();

        String[] parts = accessToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode payloadJson = objectMapper.readTree(payload);

        JsonNode rolesArray = payloadJson.get("roles");
        assertEquals("admin", rolesArray.get(0).asText());
        assertEquals("user", rolesArray.get(1).asText());
        assertEquals("developer", rolesArray.get(2).asText());
    }

    @Test
    @DisplayName("Should handle empty roles parameter")
    void testEmptyRolesParameter() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "test-secret")
                        .param("username", "testuser")
                        .param("password", "testpass")
                        .param("roles", "")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);
        String accessToken = jsonResponse.get("access_token").asText();

        String[] parts = accessToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode payloadJson = objectMapper.readTree(payload);

        assertTrue(payloadJson.get("roles").isArray());
        assertEquals(0, payloadJson.get("roles").size());
    }

    @Test
    @DisplayName("Should handle missing required parameters")
    void testMissingRequiredParameters() throws Exception {
        mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest()); // Handler correctly returns 400 for missing client_id
    }

    @Test
    @DisplayName("Should validate JWT expiration time")
    void testJWTExpirationTime() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "test-secret")
                        .param("username", "testuser")
                        .param("password", "testpass")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);
        String accessToken = jsonResponse.get("access_token").asText();

        String[] parts = accessToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode payloadJson = objectMapper.readTree(payload);

        long iat = payloadJson.get("iat").asLong();
        long exp = payloadJson.get("exp").asLong();

        // Should expire in 3600 seconds (1 hour)
        assertEquals(3600, exp - iat);
    }

    @Test
    @DisplayName("Should validate JWT issuer and audience")
    void testJWTIssuerAndAudience() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "test-secret")
                        .param("username", "testuser")
                        .param("password", "testpass")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);
        String accessToken = jsonResponse.get("access_token").asText();

        String[] parts = accessToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode payloadJson = objectMapper.readTree(payload);

        assertEquals("mleaproxy-oauth-server", payloadJson.get("iss").asText());
        assertEquals("test-client", payloadJson.get("aud").asText());
    }

    @Test
    @DisplayName("Should handle special characters in username")
    void testSpecialCharactersInUsername() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "test-secret")
                        .param("username", "user@example.com")
                        .param("password", "testpass")
                        .param("roles", "admin")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);
        String accessToken = jsonResponse.get("access_token").asText();

        String[] parts = accessToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode payloadJson = objectMapper.readTree(payload);

        assertEquals("user@example.com", payloadJson.get("sub").asText());
    }

    @Test
    @DisplayName("Should handle multiple scopes")
    void testMultipleScopes() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "test-secret")
                        .param("username", "testuser")
                        .param("password", "testpass")
                        .param("scope", "read write admin profile")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("read write admin profile"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);
        String accessToken = jsonResponse.get("access_token").asText();

        String[] parts = accessToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode payloadJson = objectMapper.readTree(payload);

        assertEquals("read write admin profile", payloadJson.get("scope").asText());
    }

    /**
     * Helper method to validate JWT structure
     */
    private void assertJWTStructure(String jwt) {
        assertNotNull(jwt);
        assertFalse(jwt.isEmpty());
        
        // JWT should have format: header.payload.signature
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length, "JWT should have exactly 3 parts separated by dots");

        // Each part should be base64url encoded
        for (String part : parts) {
            assertTrue(part.matches("[A-Za-z0-9_-]+"), 
                "JWT part should be base64url encoded: " + part);
        }
    }
}
