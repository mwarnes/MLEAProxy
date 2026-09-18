package com.marklogic.handlers.undertow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marklogic.EphemeralServerPorts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for the shape of the claims in an issued access token.
 *
 * <p>MarkLogic 12.1 fails with {@code XDMP-INTERNAL: Internal error: std::bad_cast} while
 * extracting a token whose {@code aud} claim is a JSON array. RFC 7519 section 4.1.3 permits
 * either a single string or an array, so this is MarkLogic being strict rather than MLEAProxy
 * being wrong - but a tool whose purpose is to exercise MarkLogic has to emit the form
 * MarkLogic accepts.
 *
 * <p>Isolated by signing token variants and presenting each to a MarkLogic app server: every
 * variant with a string {@code aud} succeeded, every variant with an array failed, and the
 * {@code roles}, {@code iss} and {@code scope} claims made no difference.
 */
@EphemeralServerPorts
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Access Token Claim Shape Tests")
class OAuthTokenClaimShapeTest {

    private static final String CLIENT_ID = "marklogic-qconsole";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Decodes the payload of an issued access token. */
    private JsonNode payloadOf(String responseBody) throws Exception {
        String token = objectMapper.readTree(responseBody).get("access_token").asText();
        String payload = token.split("\\.")[1];
        byte[] decoded = Base64.getUrlDecoder().decode(
                payload + "=".repeat((4 - payload.length() % 4) % 4));
        return objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
    }

    private JsonNode passwordGrantPayload() throws Exception {
        String body = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", CLIENT_ID)
                        .param("client_secret", "secret")
                        .param("username", "user1")
                        .param("password", "password")
                        .param("roles", "appreader,appwriter"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return payloadOf(body);
    }

    @Test
    @DisplayName("aud must be a single string, not an array")
    void testAudienceIsAString() throws Exception {
        JsonNode aud = passwordGrantPayload().get("aud");

        assertFalse(aud.isArray(),
                "aud must not be an array: MarkLogic 12.1 fails with std::bad_cast");
        assertTrue(aud.isTextual(), "aud should be a JSON string");
        assertEquals(CLIENT_ID, aud.asText());
    }

    @Test
    @DisplayName("aud must be a single string for the client_credentials grant too")
    void testAudienceIsAStringForClientCredentials() throws Exception {
        String body = mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "client_credentials")
                        .param("client_id", CLIENT_ID)
                        .param("client_secret", "secret"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode aud = payloadOf(body).get("aud");
        assertFalse(aud.isArray(), "aud must not be an array");
        assertEquals(CLIENT_ID, aud.asText());
    }

    @Test
    @DisplayName("roles should remain an array, which MarkLogic accepts")
    void testRolesRemainsAnArray() throws Exception {
        JsonNode payload = passwordGrantPayload();

        assertTrue(payload.get("roles").isArray(), "roles is expected to be an array");
        assertEquals(2, payload.get("roles").size());
        // roles_string is retained alongside it for consumers that want a flat value.
        assertEquals("appreader appwriter", payload.get("roles_string").asText());
    }

    @Test
    @DisplayName("Standard claims should be present with the expected types")
    void testStandardClaimTypes() throws Exception {
        JsonNode payload = passwordGrantPayload();

        assertTrue(payload.get("iss").isTextual(), "iss should be a string");
        assertTrue(payload.get("sub").isTextual(), "sub should be a string");
        assertTrue(payload.get("jti").isTextual(), "jti should be a string");
        assertTrue(payload.get("username").isTextual(), "username should be a string");
        assertTrue(payload.get("iat").isNumber(), "iat should be numeric");
        assertTrue(payload.get("exp").isNumber(), "exp should be numeric");
    }
}
