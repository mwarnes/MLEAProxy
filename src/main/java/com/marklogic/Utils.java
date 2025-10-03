package com.marklogic;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import com.marklogic.beans.saml;

import ch.qos.logback.classic.LoggerContext;

public final class Utils {

    private static final Logger LOG = LoggerFactory.getLogger(Utils.class);
    private static final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    
    // Security constants for XML processing
    private static final int MAX_XML_SIZE = 10 * 1024 * 1024; // 10MB max XML size
    private static final int MAX_ENTITY_EXPANSION_LIMIT = 64000;
    private static final String[] DANGEROUS_XML_PATTERNS = {
        "<!DOCTYPE", "<!ENTITY", "&lt;!DOCTYPE", "&lt;!ENTITY",
        "SYSTEM", "PUBLIC", "file://", "http://", "ftp://"
    };

    public static String resourceToString(String path) throws IOException {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        
        InputStream is = Objects.requireNonNull(new ClassPathResource(path).getInputStream());
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            int c;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }
        return textBuilder.toString();
    }

    public static byte[] b64d(final String s) {
        if (s == null || s.trim().isEmpty()) {
            throw new IllegalArgumentException("Base64 string cannot be null or empty");
        }
        
        try {
            String normalized = s.replace('-', '+').replace('_', '/');
            return java.util.Base64.getDecoder().decode(normalized.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            LOG.error("Invalid base64 string provided: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid base64 encoding", e);
        }
    }

    public static final String e(final String decoded) {
        if (decoded == null) {
            throw new IllegalArgumentException("Input string cannot be null");
        }
        
        try {
            final byte[] bytes = decoded.getBytes(StandardCharsets.UTF_8);
            final byte[] encoded = java.util.Base64.getEncoder().encode(bytes);
            return new String(encoded, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            LOG.error("Error encoding string: {}", ex.getMessage(), ex);
            throw new RuntimeException("Encoding failed", ex);
        }
    }

    public static String decodeMessage(String message) throws IOException, DataFormatException {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be null or empty");
        }
        
        try {
            byte[] xmlBytes = message.getBytes(StandardCharsets.UTF_8);
            byte[] base64DecodedByteArray = java.util.Base64.getDecoder().decode(xmlBytes);
            Inflater inflater = new Inflater(true);
            inflater.setInput(base64DecodedByteArray);
            
            // Use dynamic buffer size with security limits
            byte[] xmlMessageBytes = new byte[Math.min(base64DecodedByteArray.length * 10, MAX_XML_SIZE)];
            int resultLength = inflater.inflate(xmlMessageBytes);
            
            if (!inflater.finished()) {
                inflater.end();
                throw new SecurityException("Decompressed content exceeds security limits");
            }
            
            inflater.end();
            String decodedXML = new String(xmlMessageBytes, 0, resultLength, StandardCharsets.UTF_8);

            return decodedXML;
            
        } catch (IllegalArgumentException e) {
            LOG.error("Invalid base64 content in message: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid message format", e);
        } catch (DataFormatException e) {
            LOG.error("Data format error during decompression: {}", e.getMessage());
            throw e;
        }
    }

    public static X509Certificate getX509Certificate(String cert) throws IOException, CertificateException {
        String pem = cert;
        PemObject o = parsePEM(pem);
        X509Certificate X509cert = null;
        if (o.getType().equalsIgnoreCase("CERTIFICATE")) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            for (Certificate certificate : cf
                    .generateCertificates(new ByteArrayInputStream(o.getContent()))) {
                X509cert = (X509Certificate) certificate;
                LOG.debug("Found certificate: " + X509cert.getSubjectDN().toString());
            }
        }
        return X509cert;
    }

    public static String getCaCertificate() throws CertificateException, IOException {
        String content;
        if (Objects.isNull(saml.getCfg().SamlCaPath()) || saml.getCfg().SamlCaPath().isEmpty()) {
            content = Utils.resourceToString("static/certificates/certificate.pem");
        } else {
            content = Files.readString(Paths.get(saml.getCfg().SamlCaPath()));
        }
        X509Certificate X509cert = getX509Certificate(content);
        return printPEMstring("CERTIFICATE", X509cert.getEncoded());
    }

    public static String getCaPrivateKey() throws CertificateException, IOException {
        String content;
        if (Objects.isNull(saml.getCfg().SamlKeyPath()) || saml.getCfg().SamlKeyPath().isEmpty()) {
            content = Utils.resourceToString("static/certificates/rsakey.pem");
        } else {
            content = Files.readString(Paths.get(saml.getCfg().SamlKeyPath()));
        }
        return content;
    }

    public static KeyPair getKeyPair(String key) throws IOException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
        String pem = key;
        PemObject o = parsePEM(pem);
        KeyPair kp = null;
        LOG.debug("Private Key type: {}", o.getType());
        if (o.getType().equalsIgnoreCase("RSA PRIVATE KEY")) {
            ASN1Sequence seq = (ASN1Sequence) ASN1Sequence.fromByteArray(o.getContent());
            if (seq.size() != 9) {
                LOG.debug("Malformed sequence in RSA private key");
            } else {
                ASN1Integer mod = (ASN1Integer) seq.getObjectAt(1);
                ASN1Integer pubExp = (ASN1Integer) seq.getObjectAt(2);
                ASN1Integer privExp = (ASN1Integer) seq.getObjectAt(3);
                ASN1Integer p1 = (ASN1Integer) seq.getObjectAt(4);
                ASN1Integer p2 = (ASN1Integer) seq.getObjectAt(5);
                ASN1Integer exp1 = (ASN1Integer) seq.getObjectAt(6);
                ASN1Integer exp2 = (ASN1Integer) seq.getObjectAt(7);
                ASN1Integer crtCoef = (ASN1Integer) seq.getObjectAt(8);
                RSAPublicKeySpec pubSpec = new RSAPublicKeySpec(mod.getValue(), pubExp.getValue());
                RSAPrivateCrtKeySpec privSpec = new RSAPrivateCrtKeySpec(mod.getValue(), pubExp.getValue(),
                        privExp.getValue(), p1.getValue(), p2.getValue(), exp1.getValue(), exp2.getValue(),
                        crtCoef.getValue());
                KeyFactory fact = KeyFactory.getInstance("RSA", "BC");
                kp = new KeyPair(fact.generatePublic(pubSpec), fact.generatePrivate(privSpec));
            }
        }
        return kp;
    }

    public static PemObject parsePEM(String pem) throws IOException {
        Reader r = new StringReader(pem);
        PEMParser pp = new PEMParser(r);
        PemObject o = pp.readPemObject();
        LOG.debug("PEM type: {}", o.getType());
        return o;
    }

    public static String printPEMstring(String type, byte[] data) throws IOException {
        PemObject pemObject = new PemObject(type, data);
        StringWriter pemString = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(pemString);
        pemWriter.writeObject(pemObject);
        pemWriter.close();
        pemString.close();
        return pemString.toString();
    }

    /**
     * Generates SAML response using modern OpenSAML 4.x API
     * @deprecated This method is temporarily disabled during OpenSAML 4.x migration
     * TODO: Complete migration from OpenSAML 2.x to 4.x API
     */
    @Deprecated
    public static String generateSAMLResponse(saml samlBean) throws Exception {
        LOG.warn("SAML Response generation temporarily disabled during OpenSAML 4.x migration");
        throw new UnsupportedOperationException(
            "SAML Response generation is temporarily disabled during OpenSAML 4.x migration. " +
            "This method requires complete rewrite for modern OpenSAML API compatibility. " +
            "Please use OAuth2/OIDC authentication or wait for SAML modernization completion.");
    }
}
