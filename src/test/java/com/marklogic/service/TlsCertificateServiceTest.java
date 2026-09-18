package com.marklogic.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the HTTPS listener's TLS certificate handling.
 *
 * <p>These assertions matter because the bundled SAML signing certificate is unusable for
 * TLS: it has no subjectAltName and no serverAuth extended key usage. A generated
 * certificate must not repeat those omissions, or browsers and MarkLogic will reject it.
 */
@DisplayName("TLS Certificate Service Tests")
class TlsCertificateServiceTest {

    private static final String SAN_OID = "2.5.29.17";
    private static final String SERVER_AUTH_OID = "1.3.6.1.5.5.7.3.1";
    private static final int SAN_TYPE_DNS = 2;
    private static final int SAN_TYPE_IP = 7;

    private final TlsCertificateService service = new TlsCertificateService();

    @Test
    @DisplayName("Should generate a certificate and key when none exist")
    void testGeneratesWhenAbsent(@TempDir Path dir) throws Exception {
        Path cert = dir.resolve("tls-certificate.pem");
        Path key = dir.resolve("tls-privkey.pem");
        Path caCert = dir.resolve("ca-certificate.pem");
        Path caKey = dir.resolve("ca-privkey.pem");

        boolean generated = service.ensureCertificate(caCert, caKey, cert, key, Collections.emptyList());

        assertTrue(generated, "a certificate should have been generated");
        assertTrue(Files.exists(cert), "certificate file should exist");
        assertTrue(Files.exists(key), "private key file should exist");
        assertTrue(Files.exists(caCert), "CA certificate file should exist");
        assertTrue(Files.exists(caKey), "CA private key file should exist");
        assertTrue(Files.readString(cert).startsWith("-----BEGIN CERTIFICATE-----"));
        assertTrue(Files.readString(key).startsWith("-----BEGIN PRIVATE KEY-----"));
    }

    @Test
    @DisplayName("Should keep an existing certificate rather than regenerating it")
    void testKeepsExisting(@TempDir Path dir) throws Exception {
        Path cert = dir.resolve("tls-certificate.pem");
        Path key = dir.resolve("tls-privkey.pem");
        Path caCert = dir.resolve("ca-certificate.pem");
        Path caKey = dir.resolve("ca-privkey.pem");

        service.ensureCertificate(caCert, caKey, cert, key, Collections.emptyList());
        String firstCert = Files.readString(cert);

        boolean generated = service.ensureCertificate(caCert, caKey, cert, key, Collections.emptyList());

        assertFalse(generated, "an existing certificate should be reused");
        assertEquals(firstCert, Files.readString(cert), "certificate should be unchanged");
    }

    @Test
    @DisplayName("Should always include localhost and loopback subject alternative names")
    void testDefaultSubjectAltNames(@TempDir Path dir) throws Exception {
        Path cert = dir.resolve("tls-certificate.pem");
        Path key = dir.resolve("tls-privkey.pem");
        Path caCert = dir.resolve("ca-certificate.pem");
        Path caKey = dir.resolve("ca-privkey.pem");
        service.ensureCertificate(caCert, caKey, cert, key, Collections.emptyList());

        Collection<List<?>> sans = loadCertificate(cert).getSubjectAlternativeNames();
        assertNotNull(sans, "certificate must carry a subjectAltName extension");

        List<String> values = sans.stream().map(entry -> String.valueOf(entry.get(1))).toList();
        assertTrue(values.contains("localhost"), "SANs should include localhost, got " + values);
        assertTrue(values.contains("127.0.0.1"), "SANs should include 127.0.0.1, got " + values);
    }

    @Test
    @DisplayName("Should include extra subject alternative names, typed by kind")
    void testExtraSubjectAltNames(@TempDir Path dir) throws Exception {
        Path cert = dir.resolve("tls-certificate.pem");
        Path key = dir.resolve("tls-privkey.pem");
        Path caCert = dir.resolve("ca-certificate.pem");
        Path caKey = dir.resolve("ca-privkey.pem");
        service.ensureCertificate(caCert, caKey, cert, key, List.of("marklogic.example.com", "192.168.1.50"));

        Collection<List<?>> sans = loadCertificate(cert).getSubjectAlternativeNames();
        // An IP address placed in a dNSName entry is ignored by TLS clients, so the
        // distinction between the two SAN types has to be correct.
        boolean dnsPresent = sans.stream().anyMatch(
                e -> ((Integer) e.get(0)) == SAN_TYPE_DNS && "marklogic.example.com".equals(e.get(1)));
        boolean ipPresent = sans.stream().anyMatch(
                e -> ((Integer) e.get(0)) == SAN_TYPE_IP && "192.168.1.50".equals(e.get(1)));

        assertTrue(dnsPresent, "hostname should be a dNSName entry, got " + sans);
        assertTrue(ipPresent, "IP address should be an iPAddress entry, got " + sans);
    }

    @Test
    @DisplayName("Should mark the certificate for TLS server authentication")
    void testServerAuthExtensions(@TempDir Path dir) throws Exception {
        Path cert = dir.resolve("tls-certificate.pem");
        Path key = dir.resolve("tls-privkey.pem");
        Path caCert = dir.resolve("ca-certificate.pem");
        Path caKey = dir.resolve("ca-privkey.pem");
        service.ensureCertificate(caCert, caKey, cert, key, Collections.emptyList());

        X509Certificate certificate = loadCertificate(cert);

        assertTrue(certificate.getExtendedKeyUsage().contains(SERVER_AUTH_OID),
                "certificate needs the serverAuth extended key usage");
        assertNotNull(certificate.getKeyUsage(), "certificate should declare a key usage");
        assertTrue(certificate.getKeyUsage()[0], "digitalSignature should be set");
        assertTrue(certificate.getKeyUsage()[2], "keyEncipherment should be set");
        assertEquals(-1, certificate.getBasicConstraints(), "certificate must not be a CA");
        assertTrue(certificate.getCriticalExtensionOIDs().contains(SAN_OID)
                        || certificate.getNonCriticalExtensionOIDs().contains(SAN_OID),
                "certificate should carry a subjectAltName extension");
    }

    @Test
    @DisplayName("Should build a usable SSLContext from the generated PEM files")
    void testCreateSslContext(@TempDir Path dir) throws Exception {
        Path cert = dir.resolve("tls-certificate.pem");
        Path key = dir.resolve("tls-privkey.pem");
        Path caCert = dir.resolve("ca-certificate.pem");
        Path caKey = dir.resolve("ca-privkey.pem");
        service.ensureCertificate(caCert, caKey, cert, key, Collections.emptyList());

        SSLContext sslContext = service.createSslContext(cert, key, caCert);

        assertNotNull(sslContext, "an SSLContext should be returned");
        assertNotNull(sslContext.getSocketFactory(), "the SSLContext should be initialised");
    }

    private X509Certificate loadCertificate(Path path) throws Exception {
        try (var in = Files.newInputStream(path)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    @Test
    @DisplayName("Should generate a CA certificate usable as a trust anchor")
    void testCaIsAUsableTrustAnchor(@TempDir Path dir) throws Exception {
        Path cert = dir.resolve("tls-certificate.pem");
        Path key = dir.resolve("tls-privkey.pem");
        Path caCert = dir.resolve("ca-certificate.pem");
        Path caKey = dir.resolve("ca-privkey.pem");
        service.ensureCertificate(caCert, caKey, cert, key, Collections.emptyList());

        X509Certificate ca = loadCertificate(caCert);

        // MarkLogic's Certificate Authorities store needs CA:TRUE; a leaf will not do.
        assertTrue(ca.getBasicConstraints() >= 0, "CA must assert basicConstraints CA:TRUE");
        assertNotNull(ca.getKeyUsage(), "CA should declare a key usage");
        assertTrue(ca.getKeyUsage()[5], "CA must assert keyCertSign");
        assertEquals(ca.getSubjectX500Principal(), ca.getIssuerX500Principal(),
                "CA should be self-signed");
    }

    @Test
    @DisplayName("Should sign the server certificate with the CA")
    void testServerCertificateIsSignedByCa(@TempDir Path dir) throws Exception {
        Path cert = dir.resolve("tls-certificate.pem");
        Path key = dir.resolve("tls-privkey.pem");
        Path caCert = dir.resolve("ca-certificate.pem");
        Path caKey = dir.resolve("ca-privkey.pem");
        service.ensureCertificate(caCert, caKey, cert, key, Collections.emptyList());

        X509Certificate ca = loadCertificate(caCert);
        X509Certificate server = loadCertificate(cert);

        assertEquals(ca.getSubjectX500Principal(), server.getIssuerX500Principal(),
                "server certificate should be issued by the CA");
        // Throws if the signature does not verify, which is the property that makes
        // importing the CA into MarkLogic sufficient.
        server.verify(ca.getPublicKey());
        assertEquals(-1, server.getBasicConstraints(), "server certificate must not be a CA");
    }

    @Test
    @DisplayName("Should regenerate the whole chain if the CA is missing")
    void testRegeneratesWhenCaMissing(@TempDir Path dir) throws Exception {
        Path cert = dir.resolve("tls-certificate.pem");
        Path key = dir.resolve("tls-privkey.pem");
        Path caCert = dir.resolve("ca-certificate.pem");
        Path caKey = dir.resolve("ca-privkey.pem");
        service.ensureCertificate(caCert, caKey, cert, key, Collections.emptyList());

        // A server certificate left over from an earlier single-certificate install must
        // not be kept, or it would not verify against the newly generated CA.
        Files.delete(caCert);
        String staleServerCert = Files.readString(cert);

        boolean generated = service.ensureCertificate(caCert, caKey, cert, key, Collections.emptyList());

        assertTrue(generated, "chain should be regenerated when the CA is absent");
        assertTrue(Files.exists(caCert), "CA certificate should be recreated");
        assertNotEquals(staleServerCert, Files.readString(cert),
                "server certificate should be reissued by the new CA");
        loadCertificate(cert).verify(loadCertificate(caCert).getPublicKey());
    }
}
