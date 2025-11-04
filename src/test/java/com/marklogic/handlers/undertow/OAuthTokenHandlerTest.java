package com.marklogic.handlers.undertow;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marklogic.MLEAProxy;

/**
 * Comprehensive test suite for OAuth 2.0 Token Endpoint
 * Tests token generation, JWT structure, role inclusion, and error handling
 */
@SpringBootTest(
    classes = MLEAProxy.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, 
    properties = {
        "spring.profiles.active=test",
        "oauth.default.roles=user",
        "logging.level.com.marklogic.handlers.undertow.OAuthTokenHandler=DEBUG",
        "logging.level.root=INFO"
    }
)
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
                        .param("password", "password")
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
                        .param("password", "password")
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

        // Should have default role 'user' since no roles parameter was provided
        // and JSON user repository is not configured
        assertTrue(payloadJson.has("roles"));
        assertTrue(payloadJson.get("roles").isArray());
        assertEquals(1, payloadJson.get("roles").size());
        assertEquals("user", payloadJson.get("roles").get(0).asText());
    }

    @Test
    @DisplayName("Should handle single role")
    void testGenerateTokenWithSingleRole() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "test-secret")
                        .param("username", "testuser")
                        .param("password", "password")
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
                        .param("password", "password")
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
                        .param("password", "password")
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

        // Empty roles parameter should fallback to default role 'user'
        assertTrue(payloadJson.get("roles").isArray());
        assertEquals(1, payloadJson.get("roles").size());
        assertEquals("user", payloadJson.get("roles").get(0).asText());
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
                        .param("password", "password")
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
                        .param("password", "password")
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
        
        // JJWT creates audience as an array (per JWT RFC 7519), handle both formats
        JsonNode audNode = payloadJson.get("aud");
        if (audNode.isArray()) {
            assertEquals("test-client", audNode.get(0).asText());
        } else {
            assertEquals("test-client", audNode.asText());
        }
    }

    @Test
    @DisplayName("Should handle special characters in username")
    void testSpecialCharactersInUsername() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", "test-client")
                        .param("client_secret", "test-secret")
                        .param("username", "user@example.com")
                        .param("password", "password")
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
                        .param("password", "password")
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
    
    // ============================================================
    // JWKS Endpoint Tests
    // ============================================================
    
    @Test
    @DisplayName("Should return valid JWKS with RSA public key")
    void testJWKSEndpoint() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth/jwks")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].kid").exists())
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].n").exists())
                .andExpect(jsonPath("$.keys[0].e").exists())
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);
        
        // Validate the structure
        assertTrue(jsonResponse.has("keys"), "Response should have 'keys' field");
        JsonNode keys = jsonResponse.get("keys");
        assertTrue(keys.isArray(), "Keys should be an array");
        assertTrue(keys.size() > 0, "Keys array should not be empty");
        
        // Validate first key
        JsonNode firstKey = keys.get(0);
        assertEquals("RSA", firstKey.get("kty").asText(), "Key type should be RSA");
        assertEquals("sig", firstKey.get("use").asText(), "Use should be 'sig'");
        assertEquals("RS256", firstKey.get("alg").asText(), "Algorithm should be RS256");
        
        // Validate key ID exists and is not empty
        assertTrue(firstKey.has("kid"), "Key should have 'kid' field");
        String kid = firstKey.get("kid").asText();
        assertFalse(kid.isEmpty(), "Key ID should not be empty");
        
        // Validate modulus and exponent exist and are base64url encoded
        assertTrue(firstKey.has("n"), "Key should have modulus 'n'");
        assertTrue(firstKey.has("e"), "Key should have exponent 'e'");
        
        String n = firstKey.get("n").asText();
        String e = firstKey.get("e").asText();
        
        assertFalse(n.isEmpty(), "Modulus should not be empty");
        assertFalse(e.isEmpty(), "Exponent should not be empty");
        
        // Base64url should not contain + or / or =
        assertFalse(n.contains("+") || n.contains("/") || n.contains("="), 
            "Modulus should be base64url encoded (no +, /, or =)");
        assertFalse(e.contains("+") || e.contains("/") || e.contains("="), 
            "Exponent should be base64url encoded (no +, /, or =)");
    }
    
    @Test
    @DisplayName("Should return consistent key ID across requests")
    void testJWKSKeyIdConsistency() throws Exception {
        // First request
        MvcResult result1 = mockMvc.perform(get("/oauth/jwks")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody1 = result1.getResponse().getContentAsString();
        JsonNode jsonResponse1 = objectMapper.readTree(responseBody1);
        String kid1 = jsonResponse1.get("keys").get(0).get("kid").asText();
        
        // Second request
        MvcResult result2 = mockMvc.perform(get("/oauth/jwks")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody2 = result2.getResponse().getContentAsString();
        JsonNode jsonResponse2 = objectMapper.readTree(responseBody2);
        String kid2 = jsonResponse2.get("keys").get(0).get("kid").asText();
        
        // Key IDs should be consistent
        assertEquals(kid1, kid2, "Key ID should be consistent across requests");
    }
    
    @Test
    @DisplayName("Should use JWKS key ID in generated tokens")
    void testTokenUsesJWKSKeyId() throws Exception {
        // Get JWKS key ID
        MvcResult jwksResult = mockMvc.perform(get("/oauth/jwks")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        
        String jwksBody = jwksResult.getResponse().getContentAsString();
        JsonNode jwksJson = objectMapper.readTree(jwksBody);
        String jwksKeyId = jwksJson.get("keys").get(0).get("kid").asText();
        
        // Generate a token
        MvcResult tokenResult = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "client_credentials")
                        .param("client_id", "test-client")
                        .param("client_secret", "test-secret")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();
        
        String tokenBody = tokenResult.getResponse().getContentAsString();
        JsonNode tokenJson = objectMapper.readTree(tokenBody);
        String accessToken = tokenJson.get("access_token").asText();
        
        // Decode token header
        String[] parts = accessToken.split("\\.");
        String header = new String(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode headerJson = objectMapper.readTree(header);
        
        // Token should use the same key ID
        assertTrue(headerJson.has("kid"), "Token header should have 'kid' field");
        String tokenKeyId = headerJson.get("kid").asText();
        assertEquals(jwksKeyId, tokenKeyId, "Token should use the same key ID as JWKS");
    }
    
    // ============================================================
    // OAuth Well-Known Config Endpoint Tests
    // ============================================================
    
    @Test
    @DisplayName("Should return valid OAuth configuration")
    void testWellKnownConfigEndpoint() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth/.well-known/config")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.issuer").exists())
                .andExpect(jsonPath("$.token_endpoint").exists())
                .andExpect(jsonPath("$.jwks_uri").exists())
                .andExpect(jsonPath("$.grant_types_supported").isArray())
                .andExpect(jsonPath("$.response_types_supported").isArray())
                .andExpect(jsonPath("$.token_endpoint_auth_methods_supported").isArray())
                .andExpect(jsonPath("$.id_token_signing_alg_values_supported").isArray())
                .andExpect(jsonPath("$.claims_supported").isArray())
                .andExpect(jsonPath("$.scopes_supported").isArray())
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        JsonNode config = objectMapper.readTree(responseBody);
        
        // Validate issuer
        assertTrue(config.has("issuer"), "Config should have issuer");
        assertFalse(config.get("issuer").asText().isEmpty(), "Issuer should not be empty");
        
        // Validate endpoints
        assertTrue(config.has("token_endpoint"), "Config should have token_endpoint");
        assertTrue(config.get("token_endpoint").asText().contains("/oauth/token"), 
            "Token endpoint should contain /oauth/token");
        
        assertTrue(config.has("jwks_uri"), "Config should have jwks_uri");
        assertTrue(config.get("jwks_uri").asText().contains("/oauth/jwks"), 
            "JWKS URI should contain /oauth/jwks");
        
        // Validate grant types
        JsonNode grantTypes = config.get("grant_types_supported");
        assertTrue(grantTypes.isArray(), "Grant types should be an array");
        assertTrue(grantTypes.size() >= 2, "Should support at least 2 grant types");
        
        boolean hasPassword = false;
        boolean hasClientCredentials = false;
        for (JsonNode grantType : grantTypes) {
            String grant = grantType.asText();
            if ("password".equals(grant)) hasPassword = true;
            if ("client_credentials".equals(grant)) hasClientCredentials = true;
        }
        assertTrue(hasPassword, "Should support 'password' grant type");
        assertTrue(hasClientCredentials, "Should support 'client_credentials' grant type");
        
        // Validate signing algorithms
        JsonNode algValues = config.get("id_token_signing_alg_values_supported");
        assertTrue(algValues.isArray(), "Signing algorithms should be an array");
        assertTrue(algValues.size() > 0, "Should support at least one signing algorithm");
        
        boolean hasRS256 = false;
        for (JsonNode alg : algValues) {
            if ("RS256".equals(alg.asText())) {
                hasRS256 = true;
                break;
            }
        }
        assertTrue(hasRS256, "Should support RS256 signing algorithm");
        
        // Validate claims
        JsonNode claims = config.get("claims_supported");
        assertTrue(claims.isArray(), "Claims should be an array");
        assertTrue(claims.size() > 0, "Should support at least one claim");
        
        // Check for standard claims
        boolean hasIss = false;
        boolean hasSub = false;
        boolean hasExp = false;
        for (JsonNode claim : claims) {
            String claimName = claim.asText();
            if ("iss".equals(claimName)) hasIss = true;
            if ("sub".equals(claimName)) hasSub = true;
            if ("exp".equals(claimName)) hasExp = true;
        }
        assertTrue(hasIss, "Should support 'iss' claim");
        assertTrue(hasSub, "Should support 'sub' claim");
        assertTrue(hasExp, "Should support 'exp' claim");
    }
    
    @Test
    @DisplayName("Should have consistent issuer across config and tokens")
    void testIssuerConsistency() throws Exception {
        // Get issuer from config
        MvcResult configResult = mockMvc.perform(get("/oauth/.well-known/config")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        
        String configBody = configResult.getResponse().getContentAsString();
        JsonNode config = objectMapper.readTree(configBody);
        String configIssuer = config.get("issuer").asText();
        
        // Generate a token
        MvcResult tokenResult = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "client_credentials")
                        .param("client_id", "test-client")
                        .param("client_secret", "test-secret")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn();
        
        String tokenBody = tokenResult.getResponse().getContentAsString();
        JsonNode tokenJson = objectMapper.readTree(tokenBody);
        String accessToken = tokenJson.get("access_token").asText();
        
        // Decode token payload
        String[] parts = accessToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode payloadJson = objectMapper.readTree(payload);
        
        // Issuers should match
        assertTrue(payloadJson.has("iss"), "Token should have 'iss' claim");
        String tokenIssuer = payloadJson.get("iss").asText();
        assertEquals(configIssuer, tokenIssuer, "Config issuer and token issuer should match");
    }
}
