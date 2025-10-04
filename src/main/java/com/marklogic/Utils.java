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

import com.marklogic.beans.SamlBean;

import ch.qos.logback.classic.LoggerContext;

public final class Utils {

    private static final Logger LOG = LoggerFactory.getLogger(Utils.class);
    private static final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    
    /**
     * Expected sequence size for an RSA private key in ASN.1 format.
     * An RSA private key sequence contains: version, modulus, publicExponent,
     * privateExponent, prime1, prime2, exponent1, exponent2, and coefficient.
     */
    private static final int RSA_PRIVATE_KEY_SEQUENCE_SIZE = 9;
    
    // Security constants for XML processing
    private static final int MAX_XML_SIZE = 10 * 1024 * 1024; // 10MB max XML size
    private static final int MAX_ENTITY_EXPANSION_LIMIT = 64000;
    private static final String[] DANGEROUS_XML_PATTERNS = {
        "<!DOCTYPE", "<!ENTITY", "&lt;!DOCTYPE", "&lt;!ENTITY",
        "SYSTEM", "PUBLIC", "file://", "http://", "ftp://"
    };

    /**
     * Loads a classpath resource as a string.
     * Reads the entire resource file into memory and converts it to a UTF-8 string.
     * 
     * @param path Classpath resource path (e.g., "templates/authn.html")
     * @return Complete resource content as a UTF-8 string
     * @throws IOException if resource cannot be read or does not exist
     * @throws IllegalArgumentException if path is null or empty
     */
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

    /**
     * Decodes a Base64-encoded string to bytes.
     * Supports both standard and URL-safe Base64 encoding with character substitution.
     * Performs validation on the input string format.
     * 
     * @param s Base64-encoded string (supports standard and URL-safe formats)
     * @return Decoded byte array
     * @throws IllegalArgumentException if string is null, empty, or invalid Base64
     */
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

    /**
     * Encodes a string to Base64 format.
     * Converts the input string to UTF-8 bytes and applies Base64 encoding.
     * 
     * @param decoded String to encode
     * @return Base64-encoded string
     * @throws IllegalArgumentException if input is null
     * @throws RuntimeException if encoding operation fails
     */
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

    /**
     * Decodes and decompresses a SAML message.
     * Performs Base64 decoding followed by DEFLATE decompression.
     * Enforces security limits on decompressed size (10MB maximum).
     * Validates the decompression completes successfully.
     * 
     * @param message Base64-encoded compressed SAML message
     * @return Decompressed XML message string in UTF-8 format
     * @throws IOException if decompression fails or resource handling errors occur
     * @throws DataFormatException if the compressed data format is invalid
     * @throws SecurityException if decompressed size exceeds 10MB security limit
     * @throws IllegalArgumentException if message is null, empty, or invalid Base64
     */
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

    /**
     * Parses an X.509 certificate from PEM-formatted string.
     * Extracts the certificate from PEM format and converts it to X509Certificate object.
     * Validates that the PEM object contains a certificate.
     * 
     * @param cert PEM-formatted certificate string (including BEGIN/END CERTIFICATE markers)
     * @return Parsed X509Certificate object
     * @throws IOException if PEM parsing fails
     * @throws CertificateException if certificate format is invalid or parsing fails
     */
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

    /**
     * Retrieves the CA certificate from configuration or default location.
     * If no custom CA certificate path is specified, uses the default certificate from resources.
     * Returns the certificate in PEM-encoded string format.
     * 
     * @param samlBean SAML configuration bean containing certificate paths
     * @return PEM-encoded CA certificate string (with BEGIN/END CERTIFICATE markers)
     * @throws CertificateException if certificate parsing fails
     * @throws IOException if certificate file cannot be read
     */
    public static String getCaCertificate(SamlBean samlBean) throws CertificateException, IOException {
        String content;
        if (samlBean.getCfg().SamlCaPath() == null || samlBean.getCfg().SamlCaPath().isEmpty()) {
            content = Utils.resourceToString("static/certificates/certificate.pem");
        } else {
            content = Files.readString(Paths.get(samlBean.getCfg().SamlCaPath()));
        }
        X509Certificate X509cert = getX509Certificate(content);
        return printPEMstring("CERTIFICATE", X509cert.getEncoded());
    }

    /**
     * Retrieves the CA private key from configuration or default location.
     * If no custom private key path is specified, uses the default key from resources.
     * Returns the private key in PEM format.
     * 
     * @param samlBean SAML configuration bean containing private key paths
     * @return PEM-formatted private key string (including BEGIN/END markers)
     * @throws CertificateException if key processing fails
     * @throws IOException if key file cannot be read
     */
    public static String getCaPrivateKey(SamlBean samlBean) throws CertificateException, IOException {
        String content;
        if (samlBean.getCfg().SamlKeyPath() == null || samlBean.getCfg().SamlKeyPath().isEmpty()) {
            content = Utils.resourceToString("static/certificates/rsakey.pem");
        } else {
            content = Files.readString(Paths.get(samlBean.getCfg().SamlKeyPath()));
        }
        return content;
    }

    /**
     * Parses an RSA key pair from PEM-formatted private key string.
     * Extracts and validates the RSA private key using ASN.1 sequence parsing.
     * Validates the sequence contains exactly 9 elements for a valid RSA private key.
     * Uses BouncyCastle provider for RSA key pair generation.
     * 
     * @param key PEM-formatted RSA private key string (PKCS#1 format)
     * @return RSA KeyPair containing public and private keys, or null if parsing fails
     * @throws IOException if PEM parsing fails
     * @throws NoSuchAlgorithmException if RSA algorithm is unavailable
     * @throws NoSuchProviderException if BouncyCastle provider is unavailable
     * @throws InvalidKeySpecException if key specification is invalid
     */
    public static KeyPair getKeyPair(String key) throws IOException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
        String pem = key;
        PemObject o = parsePEM(pem);
        KeyPair kp = null;
        LOG.debug("Private Key type: {}", o.getType());
        if (o.getType().equalsIgnoreCase("RSA PRIVATE KEY")) {
            ASN1Sequence seq = (ASN1Sequence) ASN1Sequence.fromByteArray(o.getContent());
            if (seq.size() != RSA_PRIVATE_KEY_SEQUENCE_SIZE) {
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

    /**
     * Parses a PEM-formatted string into a PemObject.
     * Extracts the PEM type and content from standard PEM format (BEGIN/END markers).
     * 
     * @param pem PEM-formatted string (e.g., certificate, private key, etc.)
     * @return Parsed PemObject containing type and binary content
     * @throws IOException if PEM parsing fails or format is invalid
     */
    public static PemObject parsePEM(String pem) throws IOException {
        Reader r = new StringReader(pem);
        PEMParser pp = new PEMParser(r);
        PemObject o = pp.readPemObject();
        LOG.debug("PEM type: {}", o.getType());
        return o;
    }

    /**
     * Generates a PEM-formatted string from binary data.
     * Creates a PEM object with specified type and converts it to standard PEM string format.
     * Properly closes all resources after writing.
     * 
     * @param type PEM type identifier (e.g., "CERTIFICATE", "RSA PRIVATE KEY")
     * @param data Binary data to encode in PEM format
     * @return PEM-formatted string with BEGIN/END markers
     * @throws IOException if PEM writing fails
     */
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
    public static String generateSAMLResponse(SamlBean samlBean) throws Exception {
        LOG.warn("SAML Response generation temporarily disabled during OpenSAML 4.x migration");
        throw new UnsupportedOperationException(
            "SAML Response generation is temporarily disabled during OpenSAML 4.x migration. " +
            "This method requires complete rewrite for modern OpenSAML API compatibility. " +
            "Please use OAuth2/OIDC authentication or wait for SAML modernization completion.");
    }
}
