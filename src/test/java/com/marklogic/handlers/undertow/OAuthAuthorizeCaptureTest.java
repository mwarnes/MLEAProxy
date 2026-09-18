package com.marklogic.handlers.undertow;

import com.marklogic.EphemeralServerPorts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Tests for the diagnostic capture behaviour of the OAuth 2.0 Authorization Endpoint, and
 * the discovery metadata that advertises it.
 *
 * <p>Once the Authorization Code flow was implemented, a complete authorize request renders
 * the login page instead of the capture page. The capture page is now reserved for requests
 * that are not usable authorization requests - no {@code response_type} or no
 * {@code client_id} - which is what makes the endpoint useful for profiling an unfamiliar
 * client. Request capture is still logged for every request either way.
 *
 * <p>The flow itself is covered by {@link OAuthAuthorizationCodeFlowTest}.
 */
@EphemeralServerPorts
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("OAuth Authorize Endpoint - Capture Stage Tests")
class OAuthAuthorizeCaptureTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should render the capture page when response_type is absent")
    void testCapturePageForIncompleteRequest() throws Exception {
        mockMvc.perform(get("/oauth/authorize")
                        .param("client_id", "marklogic-adminui")
                        .param("redirect_uri", "https://mlhost:8001/oauth/callback")
                        .param("state", "xyz123"))
                .andExpect(status().isOk())
                .andExpect(view().name("oauth-authorize-capture"))
                .andExpect(model().attributeExists("capture", "knownParams", "unknownParams"));
    }

    @Test
    @DisplayName("Should render the login page for a complete authorize request")
    void testLoginPageForCompleteRequest() throws Exception {
        mockMvc.perform(get("/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", "marklogic-adminui")
                        .param("redirect_uri", "https://mlhost:8001/oauth/callback")
                        .param("state", "xyz123"))
                .andExpect(status().isOk())
                .andExpect(view().name("oauth-authorize"));
    }

    @Test
    @DisplayName("Should detect PKCE when code_challenge is present")
    void testPkceDetected() throws Exception {
        mockMvc.perform(get("/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", "marklogic-adminui")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pkcePresent", true));
    }

    @Test
    @DisplayName("Should report PKCE absent when no code_challenge is sent")
    void testPkceAbsent() throws Exception {
        mockMvc.perform(get("/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", "qconsole"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pkcePresent", false));
    }

    @Test
    @DisplayName("Should separate unrecognised parameters from OAuth parameters")
    @SuppressWarnings("unchecked")
    void testUnrecognisedParametersCaptured() throws Exception {
        var result = mockMvc.perform(get("/oauth/authorize")
                        .param("client_id", "marklogic-adminui")
                        .param("ml_appserver", "Admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("oauth-authorize-capture"))
                .andReturn();

        var modelMap = result.getModelAndView().getModel();
        var known = (Map<String, List<String>>) modelMap.get("knownParams");
        var unknown = (Map<String, List<String>>) modelMap.get("unknownParams");

        assertTrue(known.containsKey("client_id"), "client_id should be recognised");
        assertTrue(unknown.containsKey("ml_appserver"), "ml_appserver should be unrecognised");
        assertFalse(known.containsKey("ml_appserver"), "ml_appserver must not be listed as known");
    }

    @Test
    @DisplayName("Should render the capture page when no parameters are supplied")
    void testNoParameters() throws Exception {
        mockMvc.perform(get("/oauth/authorize"))
                .andExpect(status().isOk())
                .andExpect(view().name("oauth-authorize-capture"))
                .andExpect(model().attribute("pkcePresent", false));
    }

    @Test
    @DisplayName("Should mask credential-bearing request headers")
    @SuppressWarnings("unchecked")
    void testSensitiveHeadersMasked() throws Exception {
        var result = mockMvc.perform(get("/oauth/authorize")
                        .param("client_id", "marklogic-adminui")
                        .header("Authorization", "Bearer supersecrettokenvalue"))
                .andExpect(status().isOk())
                .andReturn();

        var headers = (Map<String, List<String>>) result.getModelAndView().getModel().get("headers");
        String authorization = headers.get("Authorization").get(0);
        assertTrue(authorization.startsWith("<masked:"), "Authorization value should be masked");
        assertTrue(authorization.contains("scheme=Bearer"), "Masked value should retain the scheme");
        assertFalse(authorization.contains("supersecrettokenvalue"),
                "Masked value must not contain the credential");
    }

    @Test
    @DisplayName("Discovery should advertise the authorization endpoint and code flow")
    void testDiscoveryAdvertisesAuthorizationCodeFlow() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorization_endpoint").exists())
                .andExpect(jsonPath("$.authorization_endpoint").value(
                        org.hamcrest.Matchers.endsWith("/oauth/authorize")))
                .andExpect(jsonPath("$.response_types_supported").value(
                        org.hamcrest.Matchers.hasItem("code")))
                .andExpect(jsonPath("$.grant_types_supported").value(
                        org.hamcrest.Matchers.hasItem("authorization_code")))
                .andExpect(jsonPath("$.code_challenge_methods_supported").value(
                        org.hamcrest.Matchers.hasItem("S256")));
    }

    @Test
    @DisplayName("All three discovery paths should return identical metadata")
    void testDiscoveryPathsAgree() throws Exception {
        String standardOidc = mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String rfc8414 = mockMvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String legacy = mockMvc.perform(get("/oauth/.well-known/config"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertNotNull(standardOidc);
        assertEquals(standardOidc, rfc8414,
                "openid-configuration and oauth-authorization-server should agree");
        assertEquals(standardOidc, legacy,
                "the standard and MLEAProxy-specific discovery paths should agree");
    }

    @Test
    @DisplayName("Existing grant types should still be advertised")
    void testExistingGrantTypesPreserved() throws Exception {
        mockMvc.perform(get("/oauth/.well-known/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grant_types_supported").value(
                        org.hamcrest.Matchers.hasItems("password", "client_credentials")))
                .andExpect(jsonPath("$.token_endpoint").value(
                        org.hamcrest.Matchers.endsWith("/oauth/token")))
                .andExpect(jsonPath("$.jwks_uri").value(
                        org.hamcrest.Matchers.endsWith("/oauth/jwks")));
    }
}
