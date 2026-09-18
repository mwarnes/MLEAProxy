package com.marklogic.handlers.undertow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the discovery metadata advertises HTTPS URLs when the HTTPS listener is
 * enabled.
 *
 * <p>MarkLogic 12.1 rejects {@code http://} token and JWKS URIs in an External Security
 * configuration, so advertising the HTTPS listener is required for the Authorization Code
 * flow rather than merely preferable.
 *
 * <p>This class deliberately does not use {@code @EphemeralServerPorts}: that annotation
 * disables the HTTPS listener, and {@code @TestPropertySource} would take precedence over
 * the properties set here. The LDAP ports are therefore made ephemeral inline, the HTTPS
 * port is one unlikely to collide, and the generated certificate is written to the
 * temporary directory rather than the working tree.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "mleaproxy.directory-servers.marklogic.port=0",
                "mleaproxy.ldap-listeners.proxy.port=0",
                "mleaproxy.https.enabled=true",
                "mleaproxy.https.port=18443",
                "mleaproxy.https.certificate=${java.io.tmpdir}/mleaproxy-test-tls-cert.pem",
                "mleaproxy.https.private-key=${java.io.tmpdir}/mleaproxy-test-tls-key.pem"
        })
@AutoConfigureMockMvc
@DisplayName("OAuth Discovery - HTTPS Advertising Tests")
class OAuthDiscoveryHttpsTest {

    private static final String HTTPS_PREFIX = "https://";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Token endpoint should be advertised over HTTPS")
    void testTokenEndpointIsHttps() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_endpoint").value(startsWith(HTTPS_PREFIX)));
    }

    @Test
    @DisplayName("JWKS URI should be advertised over HTTPS")
    void testJwksUriIsHttps() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jwks_uri").value(startsWith(HTTPS_PREFIX)));
    }

    @Test
    @DisplayName("Authorization endpoint should be advertised over HTTPS")
    void testAuthorizationEndpointIsHttps() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorization_endpoint").value(startsWith(HTTPS_PREFIX)));
    }

    @Test
    @DisplayName("Advertised URLs should use the configured HTTPS port")
    void testAdvertisedPortIsHttpsListenerPort() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_endpoint").value(
                        org.hamcrest.Matchers.containsString(":18443")))
                .andExpect(jsonPath("$.jwks_uri").value(
                        org.hamcrest.Matchers.containsString(":18443")))
                .andExpect(jsonPath("$.authorization_endpoint").value(
                        org.hamcrest.Matchers.containsString(":18443")));
    }
}
