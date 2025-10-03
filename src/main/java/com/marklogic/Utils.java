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
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.xml.namespace.QName;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;
import org.joda.time.DateTime;
import org.opensaml.DefaultBootstrap;
import org.opensaml.common.SAMLVersion;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.AttributeValue;
import org.opensaml.saml2.core.Audience;
import org.opensaml.saml2.core.AudienceRestriction;
import org.opensaml.saml2.core.Conditions;
import org.opensaml.saml2.core.Issuer;
import org.opensaml.saml2.core.NameID;
import org.opensaml.saml2.core.Status;
import org.opensaml.saml2.core.StatusCode;
import org.opensaml.saml2.core.Subject;
import org.opensaml.saml2.core.SubjectConfirmation;
import org.opensaml.saml2.core.SubjectConfirmationData;
import org.opensaml.saml2.core.impl.AssertionBuilder;
import org.opensaml.saml2.core.impl.AttributeBuilder;
import org.opensaml.saml2.core.impl.AttributeStatementBuilder;
import org.opensaml.saml2.core.impl.AudienceBuilder;
import org.opensaml.saml2.core.impl.AudienceRestrictionBuilder;
import org.opensaml.saml2.core.impl.ConditionsBuilder;
import org.opensaml.saml2.core.impl.IssuerBuilder;
import org.opensaml.saml2.core.impl.NameIDBuilder;
import org.opensaml.saml2.core.impl.ResponseBuilder;
import org.opensaml.saml2.core.impl.ResponseMarshaller;
import org.opensaml.saml2.core.impl.StatusBuilder;
import org.opensaml.saml2.core.impl.StatusCodeBuilder;
import org.opensaml.saml2.core.impl.SubjectBuilder;
import org.opensaml.saml2.core.impl.SubjectConfirmationBuilder;
import org.opensaml.saml2.core.impl.SubjectConfirmationDataBuilder;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.XMLObjectBuilder;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.schema.XSString;
import org.opensaml.xml.schema.impl.XSStringBuilder;
import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.signature.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Element;

import com.marklogic.beans.saml;
import com.marklogic.handlers.undertow.SAMLAuthHandler;

import ch.qos.logback.classic.Level;
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

    /**
     * Validates XML content for security threats before processing
     */
    private static void validateXMLSecurity(String xmlContent) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            throw new IllegalArgumentException("XML content cannot be null or empty");
        }
        
        if (xmlContent.length() > MAX_XML_SIZE) {
            throw new SecurityException("XML content exceeds maximum allowed size");
        }
        
        // Check for dangerous XML patterns
        String upperContent = xmlContent.toUpperCase();
        for (String pattern : DANGEROUS_XML_PATTERNS) {
            if (upperContent.contains(pattern.toUpperCase())) {
                LOG.warn("Potential XML security threat detected: {}", pattern);
                throw new SecurityException("Potentially dangerous XML content detected");
            }
        }
    }

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
            
            // Validate the decoded XML for security
            validateXMLSecurity(decodedXML);
            
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

    public static String generateSAMLResponse(saml saml) throws CertificateException, IOException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException, MarshallingException, TransformerException, ConfigurationException, SignatureException {
        context.getLogger(SAMLAuthHandler .class).setLevel(Level.DEBUG);
        org.opensaml.saml2.core.Response response = new ResponseBuilder().buildObject();
        response.setVersion(SAMLVersion.VERSION_20);
        LocalDateTime instant = LocalDateTime.now();
        String instant_date = instant.toString();
        response.setIssueInstant(new DateTime(instant_date));
        Status stat = new StatusBuilder().buildObject();
        StatusCode statCode = new StatusCodeBuilder().buildObject();
        if (saml.getAuthnResult().equalsIgnoreCase("SUCCESS")) {
            statCode.setValue("urn:oasis:names:tc:SAML:2.0:status:Success");
        } else {
            statCode.setValue("urn:oasis:names:tc:SAML:2.0:status:AuthnFailed");
        }
        response.setInResponseTo(saml.getSamlid());
        stat.setStatusCode(statCode);
        response.setStatus(stat);
        response.setDestination(saml.getAssertionUrl());
        Assertion assertion = new AssertionBuilder().buildObject();
        assertion.setVersion(SAMLVersion.VERSION_20);
        assertion.setIssueInstant(new DateTime(instant_date));
        response.setID("ID_" + UUID.randomUUID());
        assertion.setID("ID_" + UUID.randomUUID());
        Issuer issuer = new IssuerBuilder().buildObject();
        issuer.setValue(saml.getAssertionUrl());
        assertion.setIssuer(issuer);
        NameID nameid = new NameIDBuilder().buildObject();
        nameid.setSPNameQualifier("http://sp.example.com/demo1/metadata.php");
        nameid.setFormat("urn:oasis:names:tc:SAML:2.0:nameid-format:transient");
        nameid.setValue(saml.getUserid());
        SubjectConfirmation subjectConfirmation = new SubjectConfirmationBuilder().buildObject();
        subjectConfirmation.setMethod("urn:oasis:names:tc:SAML:2.0:cm:bearer");
        SubjectConfirmationData scd = new SubjectConfirmationDataBuilder().buildObject();
        scd.setInResponseTo(saml.getSamlid());
        scd.setNotBefore(new DateTime(saml.getNotbefore_date()));
        scd.setNotOnOrAfter(new DateTime(saml.getNotafter_date()));
        scd.setRecipient(saml.getAssertionUrl());
        subjectConfirmation.setSubjectConfirmationData(scd);
        Subject subject = new SubjectBuilder().buildObject();
        subject.setNameID(nameid);
        subject.getSubjectConfirmations().add(subjectConfirmation);
        assertion.setSubject(subject);
        Conditions conditions = new ConditionsBuilder().buildObject();
        conditions.setNotBefore(new DateTime(saml.getNotbefore_date()));
        conditions.setNotOnOrAfter(new DateTime(saml.getNotafter_date()));
        AudienceRestriction ar = new AudienceRestrictionBuilder().buildObject();
        Audience audience = new AudienceBuilder().buildObject();
        audience.setAudienceURI(saml.getAssertionUrl());
        ar.getAudiences().add(audience);
        conditions.getAudienceRestrictions().add(ar);
        assertion.setConditions(conditions);
        AttributeStatement attributeStatement = new AttributeStatementBuilder().buildObject();
        Attribute attribute = new AttributeBuilder().buildObject();
        attribute.setName("Roles");

        List<String> roles = Arrays.asList(saml.getRoles().split(","));
        Iterator it = roles.iterator();
        while (it.hasNext()) {
            XSString attributeValue = buildAttributeValue(new XSStringBuilder(), XSString.TYPE_NAME);
            attributeValue.setValue((String) it.next());
            attribute.getAttributeValues().add(attributeValue);
        }

        attributeStatement.getAttributes().add(attribute);
        assertion.getAttributeStatements().add(attributeStatement);

        X509Certificate signingcert = Utils.getX509Certificate(Utils.getCaCertificate());
        PrivateKey signingkey = Utils.getKeyPair(Utils.getCaPrivateKey()).getPrivate();
        BasicX509Credential bc = new BasicX509Credential();
        bc.setEntityCertificate(signingcert);
        bc.setPrivateKey(signingkey);

        DefaultBootstrap.bootstrap();
        Assertion as = AssertionSigner.createWithCredential(bc).signAssertion(assertion);
        response.getAssertions().add(as);

        ResponseMarshaller rMarsh = new ResponseMarshaller();
        Element plain = rMarsh.marshall(response);
        TransformerFactory transFactory = TransformerFactory.newInstance();
        Transformer transformer = transFactory.newTransformer();
        StringWriter buffer = new StringWriter();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(new DOMSource(plain), new StreamResult(buffer));
        String samlResponse = buffer.toString();
        return Utils.e(samlResponse);
    }

    private static <T extends XMLObject> T buildAttributeValue(XMLObjectBuilder<T> builder, QName typeName) {
        return builder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, typeName);
    }
}
