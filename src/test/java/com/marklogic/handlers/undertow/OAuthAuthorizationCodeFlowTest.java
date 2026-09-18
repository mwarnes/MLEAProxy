package com.marklogic.handlers.undertow;

import com.marklogic.EphemeralServerPorts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * End-to-end tests for the OAuth 2.0 Authorization Code flow.
 *
 * <p>The request shapes here mirror what MarkLogic 12.1 actually sends, captured from a
 * Query Console login: PKCE with S256, {@code response_mode=form_post}, an empty scope,
 * and a stray {@code grant_type} parameter on the authorize request.
 */
@EphemeralServerPorts
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("OAuth Authorization Code Flow Tests")
class OAuthAuthorizationCodeFlowTest {

    private static final String CLIENT_ID = "marklogic-qconsole";
    private static final String REDIRECT_URI = "https://rocky.warnesnet.com:8000";
    private static final String STATE = "ZC9sz_l7k2l5QIs0aVDLcv9OLWP2G-GQlqHBDjeB_Z4";
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    @Autowired
    private MockMvc mockMvc;

    private static String s256(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    /** Performs the authorize GET exactly as MarkLogic does. */
    private MvcResult authorizeGet() throws Exception {
        return mockMvc.perform(get("/oauth/authorize")
                        .param("grant_type", "authorization_code")
                        .param("state", STATE)
                        .param("response_type", "code")
                        .param("response_mode", "form_post")
                        .param("client_id", CLIENT_ID)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("scope", "")
                        .param("code_challenge", s256(VERIFIER))
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** Approves the login form and returns the issued code. */
    @SuppressWarnings("unchecked")
    private String approveAndExtractCode(String username, String roles) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/authorize")
                        .param("username", username)
                        .param("roles", roles)
                        .param("outcome", "approve")
                        .param("client_id", CLIENT_ID)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("state", STATE)
                        .param("scope", "")
                        .param("response_mode", "form_post")
                        .param("code_challenge", s256(VERIFIER))
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isOk())
                .andExpect(view().name("oauth-authorize-post"))
                .andReturn();

        Map<String, String> fields =
                (Map<String, String>) result.getModelAndView().getModel().get("formFields");
        assertNotNull(fields, "form fields should be present");
        return fields.get("code");
    }

    @Test
    @DisplayName("Should render the login page for a MarkLogic authorize request")
    void testLoginPageRendered() throws Exception {
        MvcResult result = authorizeGet();
        assertEquals("oauth-authorize", result.getModelAndView().getViewName());
        assertEquals(Boolean.TRUE, result.getModelAndView().getModel().get("pkcePresent"));
        assertEquals(REDIRECT_URI, result.getModelAndView().getModel().get("redirectUri"));
        assertEquals(STATE, result.getModelAndView().getModel().get("state"));
    }

    @Test
    @DisplayName("Should deliver the code by form POST when response_mode=form_post")
    @SuppressWarnings("unchecked")
    void testFormPostDelivery() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/authorize")
                        .param("username", "user1")
                        .param("outcome", "approve")
                        .param("client_id", CLIENT_ID)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("state", STATE)
                        .param("response_mode", "form_post"))
                .andExpect(status().isOk())
                .andExpect(view().name("oauth-authorize-post"))
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        assertEquals(REDIRECT_URI, model.get("formAction"), "form should POST to the redirect URI");

        Map<String, String> fields = (Map<String, String>) model.get("formFields");
        assertNotNull(fields.get("code"), "an authorization code should be issued");
        assertEquals(STATE, fields.get("state"), "state must be returned verbatim");
    }

    @Test
    @DisplayName("Should redirect with query parameters when no response_mode is given")
    void testQueryDelivery() throws Exception {
        mockMvc.perform(post("/oauth/authorize")
                        .param("username", "user1")
                        .param("outcome", "approve")
                        .param("client_id", CLIENT_ID)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("state", STATE))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Should return access_denied when the operator denies the request")
    @SuppressWarnings("unchecked")
    void testDeny() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/authorize")
                        .param("username", "user1")
                        .param("outcome", "deny")
                        .param("client_id", CLIENT_ID)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("state", STATE)
                        .param("response_mode", "form_post"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, String> fields = (Map<String, String>)
                result.getModelAndView().getModel().get("formFields");
        assertEquals("access_denied", fields.get("error"));
        assertEquals(STATE, fields.get("state"));
        assertTrue(fields.get("code") == null, "no code should be issued on denial");
    }

    @Test
    @DisplayName("Should show an error page when the client supplied no redirect_uri")
    void testMissingRedirectUri() throws Exception {
        mockMvc.perform(post("/oauth/authorize")
                        .param("username", "user1")
                        .param("outcome", "approve")
                        .param("client_id", CLIENT_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("oauth-authorize-error"));
    }

    @Test
    @DisplayName("Should exchange a code for an access token with the chosen roles")
    void testCodeExchange() throws Exception {
        String code = approveAndExtractCode("user1", "appreader,appwriter");

        mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("client_secret", "secret")
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    @Test
    @DisplayName("Should reject a replayed authorization code")
    void testCodeReplayRejected() throws Exception {
        String code = approveAndExtractCode("user1", "appreader");

        mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("client_secret", "secret")
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isOk());

        // Second use of the same code must fail: codes are single use.
        mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("client_secret", "secret")
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    @DisplayName("Should reject an exchange with the wrong PKCE verifier")
    void testPkceMismatchRejected() throws Exception {
        String code = approveAndExtractCode("user1", "appreader");

        mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("client_secret", "secret")
                        .param("code_verifier", "this-is-not-the-right-verifier"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    @DisplayName("Should reject an exchange with a missing PKCE verifier")
    void testPkceMissingRejected() throws Exception {
        String code = approveAndExtractCode("user1", "appreader");

        mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("client_secret", "secret"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    @DisplayName("Should reject an exchange with a mismatched redirect_uri")
    void testRedirectUriMismatchRejected() throws Exception {
        String code = approveAndExtractCode("user1", "appreader");

        mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", "https://attacker.example.com/")
                        .param("client_id", CLIENT_ID)
                        .param("client_secret", "secret")
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    @DisplayName("Should reject an exchange from a different client")
    void testClientMismatchRejected() throws Exception {
        String code = approveAndExtractCode("user1", "appreader");

        mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", "some-other-client")
                        .param("client_secret", "secret")
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    @DisplayName("Should reject an unknown authorization code")
    void testUnknownCodeRejected() throws Exception {
        mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("code", "not-a-real-code")
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("client_secret", "secret")
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    @DisplayName("Should require a code for the authorization_code grant")
    void testMissingCodeRejected() throws Exception {
        mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("client_id", CLIENT_ID)
                        .param("client_secret", "secret"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    @DisplayName("Should accept client credentials via HTTP Basic authentication")
    void testClientSecretBasicAccepted() throws Exception {
        String code = approveAndExtractCode("user1", "appreader");
        String basic = Base64.getEncoder().encodeToString(
                (CLIENT_ID + ":secret").getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/oauth/token")
                        .header("Authorization", "Basic " + basic)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists());
    }

    @Test
    @DisplayName("Existing password grant should be unaffected")
    void testPasswordGrantStillWorks() throws Exception {
        mockMvc.perform(post("/oauth/token")
                        .param("grant_type", "password")
                        .param("client_id", CLIENT_ID)
                        .param("client_secret", "secret")
                        .param("username", "user1")
                        .param("password", "password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists());
    }
}
