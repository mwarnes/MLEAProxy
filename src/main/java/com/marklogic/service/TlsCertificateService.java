package com.marklogic.service;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the TLS material used by the HTTPS listener.
 *
 * <p>MLEAProxy's OAuth 2.0 Authorization Code flow needs HTTPS in two directions: a
 * browser is redirected to the login page, and MarkLogic itself fetches JWKS and performs
 * the code exchange server-to-server. Both have to trust the certificate.
 *
 * <p>This service therefore generates a small two-tier chain rather than a single
 * self-signed certificate:
 *
 * <pre>
 *   ca-certificate.pem    self-signed CA  (CA:TRUE)  -&gt; import into MarkLogic
 *   tls-certificate.pem   server cert, signed by the CA, with subjectAltName
 * </pre>
 *
 * <p>The two-tier arrangement matters for usability. MarkLogic's Certificate Authorities
 * store expects a CA certificate, and a leaf marked {@code CA:FALSE} is not a valid trust
 * anchor. Separating them also means the CA can be imported once and the server
 * certificate reissued later - for a new hostname, say - without touching MarkLogic again.
 *
 * <p>The bundled {@code static/certificates/certificate.pem} is unsuitable for any of
 * this: it is a SAML <em>signing</em> certificate ({@code CN=SAML Signing Certificate})
 * with no subjectAltName, which TLS clients reject for server authentication.
 */
@Service
public class TlsCertificateService {

    private static final Logger logger = LoggerFactory.getLogger(TlsCertificateService.class);

    /** Password for the in-memory keystore. Never persisted, so the value is irrelevant. */
    private static final char[] KEYSTORE_PASSWORD = "mleaproxy".toCharArray();

    private static final int KEY_SIZE = 2048;
    private static final int CA_VALIDITY_DAYS = 3650;
    private static final int SERVER_VALIDITY_DAYS = 825;
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    /**
     * Ensures a complete set of TLS material exists, generating it if anything is missing.
     *
     * <p>All four files are treated as one unit: if any is absent the whole chain is
     * regenerated, because a server certificate and CA from different generations would
     * not verify against each other.
     *
     * @param caCertPath  destination for the CA certificate (import this into MarkLogic)
     * @param caKeyPath   destination for the CA private key, retained to reissue certs
     * @param certPath    destination for the server certificate
     * @param keyPath     destination for the server private key
     * @param extraSans   additional subjectAltName values (hostnames or IP addresses)
     * @return true if new material was generated, false if existing material was kept
     * @throws Exception if generation or file writing fails
     */
    public boolean ensureCertificate(Path caCertPath, Path caKeyPath,
                                     Path certPath, Path keyPath,
                                     List<String> extraSans) throws Exception {

        boolean complete = Files.exists(caCertPath) && Files.exists(caKeyPath)
                && Files.exists(certPath) && Files.exists(keyPath);

        if (complete) {
            logger.info("Using existing TLS certificate: {}", certPath.toAbsolutePath());
            logCertificateDetails("Server", loadCertificate(certPath));
            logCertificateDetails("CA", loadCertificate(caCertPath));
            logImportInstruction(caCertPath);
            return false;
        }

        logger.info("Generating TLS material (CA plus server certificate) under {}",
                certPath.toAbsolutePath().getParent());
        generateChain(caCertPath, caKeyPath, certPath, keyPath, extraSans);
        return true;
    }

    /**
     * Builds an {@link SSLContext} for the HTTPS listener.
     *
     * <p>The CA certificate is included in the chain presented to clients, so a client
     * that trusts the CA can validate the server certificate without having seen it.
     *
     * @param certPath   server certificate
     * @param keyPath    server PKCS#8 private key
     * @param caCertPath CA certificate, or null to present the server certificate alone
     */
    public SSLContext createSslContext(Path certPath, Path keyPath, Path caCertPath) throws Exception {
        X509Certificate serverCertificate = loadCertificate(certPath);
        PrivateKey privateKey = loadPrivateKey(keyPath);

        List<Certificate> chain = new ArrayList<>();
        chain.add(serverCertificate);
        if (caCertPath != null && Files.exists(caCertPath)) {
            chain.add(loadCertificate(caCertPath));
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("mleaproxy-tls", privateKey, KEYSTORE_PASSWORD,
                chain.toArray(new Certificate[0]));

        KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());
        return sslContext;
    }

    /**
     * Generates a self-signed CA and a server certificate signed by it.
     *
     * <p>subjectAltName always covers {@code localhost}, {@code 127.0.0.1}, {@code ::1}
     * and the detected hostname, since MarkLogic may reach MLEAProxy by any of them.
     */
    private void generateChain(Path caCertPath, Path caKeyPath,
                               Path certPath, Path keyPath,
                               List<String> extraSans) throws Exception {

        String primaryHost = detectHostname();
        JcaX509ExtensionUtils extensionUtils = new JcaX509ExtensionUtils();
        Instant now = Instant.now();
        Date notBefore = Date.from(now.minus(1, ChronoUnit.DAYS));

        // ---- Certificate authority -------------------------------------------------
        KeyPair caKeyPair = generateKeyPair();
        X500Name caSubject = new X500Name(
                "CN=MLEAProxy Development CA"
                        + ", OU=MLEAProxy HTTPS Listener"
                        + ", O=Progress MarkLogic External Security Proxy");

        JcaX509v3CertificateBuilder caBuilder = new JcaX509v3CertificateBuilder(
                caSubject, randomSerial(), notBefore,
                Date.from(now.plus(CA_VALIDITY_DAYS, ChronoUnit.DAYS)),
                caSubject, caKeyPair.getPublic());

        caBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        caBuilder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature));
        caBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                extensionUtils.createSubjectKeyIdentifier(caKeyPair.getPublic()));

        ContentSigner caSigner = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .build(caKeyPair.getPrivate());
        X509Certificate caCertificate = new JcaX509CertificateConverter()
                .getCertificate(caBuilder.build(caSigner));

        // ---- Server certificate, signed by the CA ----------------------------------
        Set<String> sans = new LinkedHashSet<>();
        sans.add(primaryHost);
        sans.add("localhost");
        sans.add("127.0.0.1");
        sans.add("::1");
        if (extraSans != null) {
            extraSans.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .forEach(sans::add);
        }

        KeyPair serverKeyPair = generateKeyPair();
        X500Name serverSubject = new X500Name(
                "CN=" + primaryHost
                        + ", OU=MLEAProxy HTTPS Listener"
                        + ", O=Progress MarkLogic External Security Proxy");

        JcaX509v3CertificateBuilder serverBuilder = new JcaX509v3CertificateBuilder(
                caSubject, randomSerial(), notBefore,
                Date.from(now.plus(SERVER_VALIDITY_DAYS, ChronoUnit.DAYS)),
                serverSubject, serverKeyPair.getPublic());

        serverBuilder.addExtension(Extension.subjectAlternativeName, false, buildSubjectAltNames(sans));
        serverBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        serverBuilder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        serverBuilder.addExtension(Extension.extendedKeyUsage, false,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
        serverBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                extensionUtils.createSubjectKeyIdentifier(serverKeyPair.getPublic()));
        serverBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                extensionUtils.createAuthorityKeyIdentifier(caCertificate));

        // Signed with the CA key, which is what makes the CA a usable trust anchor.
        ContentSigner serverSigner = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .build(caKeyPair.getPrivate());
        X509Certificate serverCertificate = new JcaX509CertificateConverter()
                .getCertificate(serverBuilder.build(serverSigner));

        // Fail loudly here rather than at TLS handshake time if the chain is broken.
        serverCertificate.verify(caCertificate.getPublicKey());

        for (Path path : List.of(caCertPath, caKeyPath, certPath, keyPath)) {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
        }
        writePem(caCertPath, "CERTIFICATE", caCertificate.getEncoded());
        writePem(caKeyPath, "PRIVATE KEY", caKeyPair.getPrivate().getEncoded());
        writePem(certPath, "CERTIFICATE", serverCertificate.getEncoded());
        writePem(keyPath, "PRIVATE KEY", serverKeyPair.getPrivate().getEncoded());
        restrictPermissions(caKeyPath);
        restrictPermissions(keyPath);

        logger.info("Generated CA certificate:     {}", caCertPath.toAbsolutePath());
        logger.info("Generated server certificate: {}", certPath.toAbsolutePath());
        logger.info("Subject alternative names: {}", String.join(", ", sans));
        logCertificateDetails("CA", caCertificate);
        logCertificateDetails("Server", serverCertificate);
        logImportInstruction(caCertPath);
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(KEY_SIZE, new SecureRandom());
        return generator.generateKeyPair();
    }

    private BigInteger randomSerial() {
        return new BigInteger(160, new SecureRandom());
    }

    private GeneralNames buildSubjectAltNames(Set<String> sans) {
        List<GeneralName> names = new ArrayList<>();
        for (String san : sans) {
            if (isIpAddress(san)) {
                names.add(new GeneralName(GeneralName.iPAddress, san));
            } else {
                names.add(new GeneralName(GeneralName.dNSName, san));
            }
        }
        return new GeneralNames(names.toArray(new GeneralName[0]));
    }

    /**
     * Distinguishes an IP literal from a hostname so the right subjectAltName type is
     * used. A dNSName entry holding an IP address is ignored by TLS clients.
     */
    private boolean isIpAddress(String value) {
        if (value.contains(":")) {
            return true;
        }
        String[] octets = value.split("\\.");
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            try {
                int parsed = Integer.parseInt(octet);
                if (parsed < 0 || parsed > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private X509Certificate loadCertificate(Path certPath) throws Exception {
        byte[] pem = Files.readAllBytes(certPath);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(pem));
    }

    private PrivateKey loadPrivateKey(Path keyPath) throws Exception {
        String content = Files.readString(keyPath, StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(content);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private void writePem(Path path, String type, byte[] der) throws Exception {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(der);
        String pem = "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
        Files.writeString(path, pem, StandardCharsets.UTF_8);
    }

    /** Best-effort tightening of private key permissions; not supported on every filesystem. */
    private void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, java.util.Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        } catch (Exception e) {
            logger.debug("Could not restrict permissions on {}", path, e);
        }
    }

    /**
     * Logs the fingerprint and validity window, which is what an administrator needs in
     * order to identify the certificate in MarkLogic.
     */
    private void logCertificateDetails(String label, X509Certificate certificate) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            StringBuilder fingerprint = new StringBuilder();
            for (byte b : digest) {
                if (fingerprint.length() > 0) {
                    fingerprint.append(':');
                }
                fingerprint.append(String.format("%02X", b));
            }
            logger.info("{} certificate subject:     {}", label, certificate.getSubjectX500Principal());
            logger.info("{} certificate valid until: {}", label, certificate.getNotAfter());
            logger.info("{} certificate SHA-256:     {}", label, fingerprint);
        } catch (Exception e) {
            logger.debug("Could not compute certificate fingerprint", e);
        }
    }

    private void logImportInstruction(Path caCertPath) {
        logger.warn("The HTTPS listener uses a private CA. Import {} into the MarkLogic "
                        + "Certificate Authorities store, otherwise MarkLogic will reject the "
                        + "JWKS and token endpoints over HTTPS.",
                caCertPath.toAbsolutePath());
    }

    private String detectHostname() {
        try {
            String canonical = InetAddress.getLocalHost().getCanonicalHostName();
            if (canonical != null && !canonical.isEmpty()
                    && !canonical.equals("localhost")
                    && !canonical.startsWith("127.")) {
                return canonical;
            }
            String hostname = InetAddress.getLocalHost().getHostName();
            if (hostname != null && !hostname.isEmpty()) {
                return hostname;
            }
        } catch (Exception e) {
            logger.debug("Could not determine hostname for certificate subject", e);
        }
        return "localhost";
    }
}
