package com.marklogic.handlers.undertow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the CA certificate download endpoint.
 *
 * <p>The HTTPS listener is enabled here so that a CA is generated, written to the
 * temporary directory rather than the working tree. {@code @EphemeralServerPorts} is not
 * used because it disables HTTPS outright.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "mleaproxy.directory-servers.marklogic.port=0",
                "mleaproxy.ldap-listeners.proxy.port=0",
                "mleaproxy.https.enabled=true",
                "mleaproxy.https.port=18444",
                "mleaproxy.https.certificate=${java.io.tmpdir}/mleaproxy-catest-cert.pem",
                "mleaproxy.https.private-key=${java.io.tmpdir}/mleaproxy-catest-key.pem",
                "mleaproxy.https.ca-certificate=${java.io.tmpdir}/mleaproxy-catest-ca.pem",
                "mleaproxy.https.ca-private-key=${java.io.tmpdir}/mleaproxy-catest-cakey.pem"
        })
@AutoConfigureMockMvc
@DisplayName("TLS CA Certificate Endpoint Tests")
class TlsCaCertificateHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should serve the CA certificate as PEM")
    void testServesCaCertificate() throws Exception {
        String body = mockMvc.perform(get("/tls/ca"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("-----BEGIN CERTIFICATE-----"),
                "response should be a PEM certificate");
        assertTrue(body.contains("-----END CERTIFICATE-----"),
                "response should be a complete PEM certificate");
    }

    @Test
    @DisplayName("Should offer the CA certificate as a download")
    void testServedAsAttachment() throws Exception {
        mockMvc.perform(get("/tls/ca"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"mleaproxy-ca-certificate.pem\""))
                .andExpect(content().contentTypeCompatibleWith("text/plain"));
    }

    @Test
    @DisplayName("Should never expose a private key")
    void testNoPrivateKeyExposed() throws Exception {
        String body = mockMvc.perform(get("/tls/ca"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("PRIVATE KEY"),
                "the CA endpoint must not expose any private key");
    }

    @Test
    @DisplayName("Status page should surface the CA details and download link")
    void testStatusPageShowsCa() throws Exception {
        String body = mockMvc.perform(get("/status"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("CA Certificate"), "status page should mention the CA");
        assertTrue(body.contains("/tls/ca"), "status page should link to the download");
        assertTrue(body.contains("Certificate Authorities"),
                "status page should say where to import it in MarkLogic");
        assertFalse(body.contains("PRIVATE KEY"),
                "status page must not render any private key");
    }
}
