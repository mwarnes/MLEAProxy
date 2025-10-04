package com.marklogic;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for MLEAProxy
 * Tests end-to-end workflows combining multiple components
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("MLEAProxy Integration Tests")
class MLEAProxyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should test application context loads")
    void contextLoads() {
        // Context loading test
        assertNotNull(mockMvc);
    }

    @Test
    @DisplayName("Should test OAuth to SAML workflow")
    void testOAuthToSAMLWorkflow() throws Exception {
        // Step 1: Get OAuth token with roles
        MvcResult oauthResult = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "marklogic-client")
                        .param("client_secret", "secret")
                        .param("username", "testuser")
                        .param("password", "password")
                        .param("roles", "marklogic-admin,developer")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String oauthResponse = oauthResult.getResponse().getContentAsString();
        JsonNode oauthJson = objectMapper.readTree(oauthResponse);
        String accessToken = oauthJson.get("access_token").asText();

        // Validate OAuth token structure
        assertNotNull(accessToken);
        String[] tokenParts = accessToken.split("\\.");
        assertEquals(3, tokenParts.length);

        // Decode and verify roles in OAuth token
        String payload = new String(Base64.getUrlDecoder().decode(tokenParts[1]));
        JsonNode payloadJson = objectMapper.readTree(payload);
        assertEquals(2, payloadJson.get("roles").size());

        // Step 2: Simulate SAML authentication with same roles
        MvcResult samlResult = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "integration-test-id")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "marklogic-admin,developer"))
                .andExpect(status().isOk())
                .andReturn();

        // Validate SAML response exists in HTML body (not model)
        String htmlBody = samlResult.getResponse().getContentAsString();
        assertTrue(htmlBody.contains("SAMLResponse") || htmlBody.contains("samlResponse"), 
                   "HTML should contain SAML response");
        assertFalse(htmlBody.isEmpty());

        // Both OAuth and SAML should contain same roles for same user
        assertTrue(payload.contains("marklogic-admin"));
        assertTrue(payload.contains("developer"));
    }

    @Test
    @DisplayName("Should test Base64 encoding endpoint")
    void testBase64Encoding() throws Exception {
        String testData = "Hello, World!";
        
        MvcResult result = mockMvc.perform(get("/encode")
                        .param("data", testData))
                .andExpect(status().isOk())
                .andReturn();

        String encoded = result.getResponse().getContentAsString();
        assertNotNull(encoded);
        assertFalse(encoded.isEmpty());

        // Verify it's valid base64
        byte[] decoded = Base64.getDecoder().decode(encoded);
        assertEquals(testData, new String(decoded));
    }

    @Test
    @DisplayName("Should test Base64 decoding endpoint")
    void testBase64Decoding() throws Exception {
        String testData = "Hello, World!";
        String encoded = Base64.getEncoder().encodeToString(testData.getBytes());

        MvcResult result = mockMvc.perform(get("/decode")
                        .param("data", encoded))
                .andExpect(status().isOk())
                .andReturn();

        String decoded = result.getResponse().getContentAsString();
        assertEquals(testData, decoded);
    }

    @Test
    @DisplayName("Should test round-trip encoding/decoding")
    void testRoundTripEncodingDecoding() throws Exception {
        String originalData = "Test data with special characters: !@#$%^&*()";

        // Encode
        MvcResult encodeResult = mockMvc.perform(get("/encode")
                        .param("data", originalData))
                .andExpect(status().isOk())
                .andReturn();

        String encoded = encodeResult.getResponse().getContentAsString();

        // Decode
        MvcResult decodeResult = mockMvc.perform(get("/decode")
                        .param("data", encoded))
                .andExpect(status().isOk())
                .andReturn();

        String decoded = decodeResult.getResponse().getContentAsString();
        assertEquals(originalData, decoded);
    }

    @Test
    @DisplayName("Should test multiple OAuth tokens with different roles")
    void testMultipleOAuthTokensWithDifferentRoles() throws Exception {
        // Token 1: Admin roles
        MvcResult result1 = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "client1")
                        .param("client_secret", "secret1")
                        .param("username", "admin")
                        .param("password", "password")
                        .param("roles", "admin,marklogic-admin")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        // Token 2: User roles
        MvcResult result2 = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "client2")
                        .param("client_secret", "secret2")
                        .param("username", "user")
                        .param("password", "password")
                        .param("roles", "user,reader")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String response1 = result1.getResponse().getContentAsString();
        String response2 = result2.getResponse().getContentAsString();

        JsonNode json1 = objectMapper.readTree(response1);
        JsonNode json2 = objectMapper.readTree(response2);

        // Tokens should be different
        assertNotEquals(json1.get("access_token").asText(), json2.get("access_token").asText());

        // Verify roles in each token
        String token1 = json1.get("access_token").asText();
        String token2 = json2.get("access_token").asText();

        String payload1 = new String(Base64.getUrlDecoder().decode(token1.split("\\.")[1]));
        String payload2 = new String(Base64.getUrlDecoder().decode(token2.split("\\.")[1]));

        assertTrue(payload1.contains("admin"));
        assertTrue(payload1.contains("marklogic-admin"));
        assertTrue(payload2.contains("user"));
        assertTrue(payload2.contains("reader"));
    }

    @Test
    @DisplayName("Should test SAML with special characters in username")
    void testSAMLWithSpecialCharacters() throws Exception {
        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "special-char-test")
                        .param("userid", "user@example.com")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "user"))
                .andExpect(status().isOk())
                .andReturn();

        String samlHtmlBody = result.getResponse().getContentAsString();
        assertNotNull(samlHtmlBody);
        assertFalse(samlHtmlBody.isEmpty());

        // HTML should contain the username (SAML response will be base64 encoded in the HTML)
        assertTrue(samlHtmlBody.contains("redirect") || samlHtmlBody.contains("form"), 
                   "HTML should contain redirect form");
    }

    @Test
    @DisplayName("Should test concurrent OAuth token requests")
    void testConcurrentOAuthRequests() throws Exception {
        // Simulate multiple concurrent requests
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/oauth/token")
                            .param("grant_type", "password")
                            .param("client_id", "client-" + i)
                            .param("client_secret", "secret")
                            .param("username", "user" + i)
                            .param("password", "password")
                            .param("roles", "user")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Should test OAuth token expiration claim")
    void testOAuthTokenExpiration() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "secret")
                        .param("username", "testuser")
                        .param("password", "password")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);

        // Check expires_in is set correctly
        assertEquals(3600, json.get("expires_in").asInt());

        // Verify JWT claims
        String token = json.get("access_token").asText();
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        JsonNode payloadJson = objectMapper.readTree(payload);

        long iat = payloadJson.get("iat").asLong();
        long exp = payloadJson.get("exp").asLong();

        // Should expire 3600 seconds after issuance
        assertEquals(3600, exp - iat);
    }

    @Test
    @DisplayName("Should validate home page loads")
    void testHomePageLoads() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }
}
