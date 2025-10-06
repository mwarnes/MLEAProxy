package com.marklogic.handlers.undertow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.zip.DataFormatException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Marshaller;
import org.opensaml.core.xml.io.MarshallerFactory;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.core.xml.schema.impl.XSStringBuilder;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AttributeValue;
import org.opensaml.saml.saml2.core.AuthnContext;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;
import org.opensaml.saml.saml2.core.impl.AssertionBuilder;
import org.opensaml.saml.saml2.core.impl.AttributeBuilder;
import org.opensaml.saml.saml2.core.impl.AttributeStatementBuilder;
import org.opensaml.saml.saml2.core.impl.AuthnContextBuilder;
import org.opensaml.saml.saml2.core.impl.AuthnContextClassRefBuilder;
import org.opensaml.saml.saml2.core.impl.AuthnStatementBuilder;
import org.opensaml.saml.saml2.core.impl.ConditionsBuilder;
import org.opensaml.saml.saml2.core.impl.IssuerBuilder;
import org.opensaml.saml.saml2.core.impl.NameIDBuilder;
import org.opensaml.saml.saml2.core.impl.ResponseBuilder;
import org.opensaml.saml.saml2.core.impl.StatusBuilder;
import org.opensaml.saml.saml2.core.impl.StatusCodeBuilder;
import org.opensaml.saml.saml2.core.impl.SubjectBuilder;
import org.opensaml.saml.saml2.core.impl.SubjectConfirmationBuilder;
import org.opensaml.saml.saml2.core.impl.SubjectConfirmationDataBuilder;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.saml.saml2.metadata.impl.EntityDescriptorBuilder;
import org.opensaml.saml.saml2.metadata.impl.IDPSSODescriptorBuilder;
import org.opensaml.saml.saml2.metadata.impl.KeyDescriptorBuilder;
import org.opensaml.saml.saml2.metadata.impl.SingleSignOnServiceBuilder;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.CredentialSupport;
import org.opensaml.security.credential.UsageType;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.X509Data;
import org.opensaml.xmlsec.signature.impl.KeyInfoBuilder;
import org.opensaml.xmlsec.signature.impl.X509CertificateBuilder;
import org.opensaml.xmlsec.signature.impl.X509DataBuilder;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.marklogic.Utils;
import com.marklogic.beans.SamlBean;
import com.marklogic.repository.XmlUserRepository;
import com.marklogic.repository.XmlUserRepository.UserInfo;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import jakarta.annotation.PostConstruct;

@org.springframework.stereotype.Controller
public class SAMLAuthHandler {

    private static final Logger logger = LoggerFactory.getLogger(SAMLAuthHandler.class);

    @Autowired
    private SamlBean saml;
    
    @Autowired
    private ResourceLoader resourceLoader;
    
    @Autowired(required = false)
    private XmlUserRepository xmlUserRepository;
    
    // Configurable certificate and key paths (works in production JARs)
    @Value("${saml.certificate.path:classpath:static/certificates/certificate.pem}")
    private String certificatePath;
    
    @Value("${saml.signing.key.path:classpath:static/certificates/privkey.pem}")
    private String signingKeyPath;
    
    // Configurable IdP entity ID and SSO endpoint
    @Value("${saml.idp.entity.id:http://localhost:8080/saml/idp}")
    private String idpEntityId;
    
    @Value("${saml.idp.sso.url:http://localhost:8080/saml/auth}")
    private String idpSsoUrl;
    
    // Cache loaded credentials
    private PrivateKey cachedPrivateKey;
    private X509Certificate cachedCertificate;
    private volatile boolean initialized = false;

    @PostConstruct
    public void init() {
        try {
            // Load private key
            Resource keyResource = resourceLoader.getResource(signingKeyPath);
            if (keyResource.exists()) {
                try (java.io.InputStream inputStream = keyResource.getInputStream()) {
                    this.cachedPrivateKey = loadPrivateKey(inputStream);
                    logger.info("SAML private key loaded successfully");
                }
            } else {
                logger.warn("SAML private key not found at: {}", signingKeyPath);
                this.cachedPrivateKey = null;
            }
            
            // Load certificate
            Resource certResource = resourceLoader.getResource(certificatePath);
            if (certResource.exists()) {
                try (java.io.InputStream inputStream = certResource.getInputStream()) {
                    this.cachedCertificate = loadCertificate(inputStream);
                    logger.info("SAML certificate loaded successfully");
                }
            } else {
                logger.warn("SAML certificate not found at: {}", certificatePath);
                this.cachedCertificate = null;
            }
            
            this.initialized = true;
            
        } catch (Exception e) {
            logger.error("Failed to initialize SAML handler", e);
            this.cachedPrivateKey = null;
            this.cachedCertificate = null;
            this.initialized = true; // Still allow SAML to work without signatures
        }
    }
    
    @GetMapping(value = "/saml/auth")
    public String authn(Model model, @RequestParam(value = "SAMLRequest") String req) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(SAMLAuthHandler.class).setLevel(saml.getCfg().samlDebug() ? Level.DEBUG : Level.INFO);
        model.addAttribute("saml", saml);
        
        try {
            logger.debug("Processing SAML Request: {}", req);
            
            // Initialize OpenSAML 4.x if not already done
            if (!isOpenSAMLInitialized()) {
                InitializationService.initialize();
                logger.info("OpenSAML 4.x initialized successfully");
            }
            
            // Decode the SAML request
            String decodedReq = Utils.decodeMessage(req);
            logger.debug("Decoded SAML Request: {}", decodedReq);
            
            // Parse the XML document
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = docBuilder.parse(new ByteArrayInputStream(decodedReq.getBytes(StandardCharsets.UTF_8)));
            Element element = document.getDocumentElement();
            
            // Unmarshal the SAML AuthnRequest using OpenSAML 4.x
            UnmarshallerFactory unmarshallerFactory = XMLObjectProviderRegistrySupport.getUnmarshallerFactory();
            Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(element);
            AuthnRequest authnRequest = (AuthnRequest) unmarshaller.unmarshall(element);
            
            // Extract SAML request details
            String samlId = authnRequest.getID();
            String assertionConsumerServiceURL = authnRequest.getAssertionConsumerServiceURL();
            
            logger.debug("SAML Request ID: {}", samlId);
            logger.debug("Assertion Consumer Service URL: {}", assertionConsumerServiceURL);
            
            // Set the SAML request data in the bean
            saml.setSamlRequest(req);
            saml.setSamlid(samlId);
            saml.setAssertionUrl(assertionConsumerServiceURL);
            
            // Set default dates (current time and +5 minutes)
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime notAfter = now.plusMinutes(saml.getCfg().SamlResponseDuration() / 60);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            
            saml.setNotbefore_date(now.format(formatter));
            saml.setNotafter_date(notAfter.format(formatter));
            
            logger.info("SAML Authentication request processed successfully for ID: {}", samlId);
            
        } catch (InitializationException e) {
            logger.error("Failed to initialize OpenSAML 4.x", e);
            throw new RuntimeException("OpenSAML initialization failed", e);
        } catch (IOException | ParserConfigurationException | SAXException | UnmarshallingException | DataFormatException e) {
            logger.error("Error processing SAML request", e);
            throw new RuntimeException("SAML request processing failed", e);
        }
        
        return "authn";
    }
    
    /**
     * Check if OpenSAML is already initialized to avoid double initialization
     */
    private boolean isOpenSAMLInitialized() {
        try {
            XMLObjectProviderRegistrySupport.getUnmarshallerFactory();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Load private key from PEM InputStream
     */
    private PrivateKey loadPrivateKey(java.io.InputStream inputStream) throws Exception {
        String key = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        
        // Remove PEM header/footer and whitespace
        key = key.replace("-----BEGIN PRIVATE KEY-----", "")
                 .replace("-----END PRIVATE KEY-----", "")
                 .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                 .replace("-----END RSA PRIVATE KEY-----", "")
                 .replaceAll("\\s", "");
        
        // Decode base64
        byte[] keyBytes = Base64.getDecoder().decode(key);
        
        // Try PKCS8 format first
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            // If PKCS8 fails, try converting from PKCS1 (traditional RSA format)
            logger.warn("Failed to load as PKCS8, attempting PKCS1 conversion", e);
            throw new Exception("Private key must be in PKCS8 format. Convert using: openssl pkcs8 -topk8 -nocrypt -in privkey.pem -out privkey_pkcs8.pem");
        }
    }
    
    /**
     * Load X509 certificate from PEM InputStream
     */
    private X509Certificate loadCertificate(java.io.InputStream inputStream) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(inputStream);
    }
    
    /**
     * Create signing credential from cached private key and certificate
     */
    private Credential createSigningCredential() throws Exception {
        if (cachedPrivateKey == null || cachedCertificate == null) {
            throw new Exception("Private key or certificate not available");
        }
        return CredentialSupport.getSimpleCredential(cachedCertificate, cachedPrivateKey);
    }
    
    /**
     * Generate SAML Response using OpenSAML 4.x API
     * This is a temporary local implementation until Utils.generateSAMLResponse is updated
     */
    private String generateSAMLResponseV4(SamlBean samlBean) throws Exception {
        logger.info("Generating SAML response using OpenSAML 4.x for user: {}", samlBean.getUserid());
        
        try {
            // Initialize OpenSAML if needed
            if (!isOpenSAMLInitialized()) {
                InitializationService.initialize();
            }
            
            // Get the XMLObject factory
            XMLObjectBuilderFactory builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory();
            
            // Create Response
            ResponseBuilder responseBuilder = (ResponseBuilder) builderFactory.getBuilder(Response.DEFAULT_ELEMENT_NAME);
            Response response = responseBuilder.buildObject();
            response.setVersion(SAMLVersion.VERSION_20);
            response.setIssueInstant(java.time.Instant.now());
            response.setID("ID_" + java.util.UUID.randomUUID().toString());
            response.setInResponseTo(samlBean.getSamlid());
            response.setDestination(samlBean.getAssertionUrl());
            
            // Create Status (Success)
            StatusBuilder statusBuilder = (StatusBuilder) builderFactory.getBuilder(Status.DEFAULT_ELEMENT_NAME);
            Status status = statusBuilder.buildObject();
            
            StatusCodeBuilder statusCodeBuilder = (StatusCodeBuilder) builderFactory.getBuilder(StatusCode.DEFAULT_ELEMENT_NAME);
            StatusCode statusCode = statusCodeBuilder.buildObject();
            
            if ("SUCCESS".equalsIgnoreCase(samlBean.getAuthnResult())) {
                statusCode.setValue(StatusCode.SUCCESS);
            } else {
                statusCode.setValue(StatusCode.AUTHN_FAILED);
            }
            
            status.setStatusCode(statusCode);
            response.setStatus(status);
            
            // Create Assertion
            AssertionBuilder assertionBuilder = (AssertionBuilder) builderFactory.getBuilder(Assertion.DEFAULT_ELEMENT_NAME);
            Assertion assertion = assertionBuilder.buildObject();
            assertion.setVersion(SAMLVersion.VERSION_20);
            assertion.setIssueInstant(java.time.Instant.now());
            assertion.setID("ID_" + java.util.UUID.randomUUID().toString());
            
            // Create Issuer
            IssuerBuilder issuerBuilder = (IssuerBuilder) builderFactory.getBuilder(Issuer.DEFAULT_ELEMENT_NAME);
            Issuer issuer = issuerBuilder.buildObject();
            issuer.setValue(samlBean.getAssertionUrl());
            assertion.setIssuer(issuer);
            
            // Create Subject
            SubjectBuilder subjectBuilder = (SubjectBuilder) builderFactory.getBuilder(Subject.DEFAULT_ELEMENT_NAME);
            Subject subject = subjectBuilder.buildObject();
            
            NameIDBuilder nameIDBuilder = (NameIDBuilder) builderFactory.getBuilder(NameID.DEFAULT_ELEMENT_NAME);
            NameID nameID = nameIDBuilder.buildObject();
            nameID.setFormat(NameIDType.TRANSIENT);
            nameID.setValue(samlBean.getUserid());
            subject.setNameID(nameID);
            
            // Create SubjectConfirmation
            SubjectConfirmationBuilder subjectConfirmationBuilder = (SubjectConfirmationBuilder) builderFactory.getBuilder(SubjectConfirmation.DEFAULT_ELEMENT_NAME);
            SubjectConfirmation subjectConfirmation = subjectConfirmationBuilder.buildObject();
            subjectConfirmation.setMethod(SubjectConfirmation.METHOD_BEARER);
            
            // Create SubjectConfirmationData with Recipient and NotOnOrAfter
            SubjectConfirmationDataBuilder subjectConfirmationDataBuilder = (SubjectConfirmationDataBuilder) builderFactory.getBuilder(SubjectConfirmationData.DEFAULT_ELEMENT_NAME);
            SubjectConfirmationData subjectConfirmationData = subjectConfirmationDataBuilder.buildObject();
            subjectConfirmationData.setRecipient(samlBean.getAssertionUrl());
            subjectConfirmationData.setInResponseTo(samlBean.getSamlid());
            
            // Parse NotOnOrAfter from the samlBean
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                LocalDateTime notAfterDateTime = LocalDateTime.parse(samlBean.getNotafter_date(), formatter);
                java.time.Instant notAfterInstant = notAfterDateTime.atZone(java.time.ZoneId.of("UTC")).toInstant();
                subjectConfirmationData.setNotOnOrAfter(notAfterInstant);
            } catch (Exception e) {
                logger.error("Failed to parse NotOnOrAfter date: {}", samlBean.getNotafter_date(), e);
                // Default to 5 minutes from now
                subjectConfirmationData.setNotOnOrAfter(java.time.Instant.now().plusSeconds(300));
            }
            
            subjectConfirmation.setSubjectConfirmationData(subjectConfirmationData);
            subject.getSubjectConfirmations().add(subjectConfirmation);
            
            assertion.setSubject(subject);
            
            // Create Conditions with NotBefore and NotOnOrAfter
            ConditionsBuilder conditionsBuilder = (ConditionsBuilder) builderFactory.getBuilder(Conditions.DEFAULT_ELEMENT_NAME);
            Conditions conditions = conditionsBuilder.buildObject();
            
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                LocalDateTime notBeforeDateTime = LocalDateTime.parse(samlBean.getNotbefore_date(), formatter);
                LocalDateTime notAfterDateTime = LocalDateTime.parse(samlBean.getNotafter_date(), formatter);
                
                java.time.Instant notBeforeInstant = notBeforeDateTime.atZone(java.time.ZoneId.of("UTC")).toInstant();
                java.time.Instant notAfterInstant = notAfterDateTime.atZone(java.time.ZoneId.of("UTC")).toInstant();
                
                conditions.setNotBefore(notBeforeInstant);
                conditions.setNotOnOrAfter(notAfterInstant);
            } catch (java.time.format.DateTimeParseException e) {
                logger.error("Failed to parse Conditions dates: notBefore={}, notAfter={}", 
                             samlBean.getNotbefore_date(), samlBean.getNotafter_date(), e);
                // Default to now and 5 minutes from now
                conditions.setNotBefore(java.time.Instant.now());
                conditions.setNotOnOrAfter(java.time.Instant.now().plusSeconds(300));
            }
            
            assertion.setConditions(conditions);
            
            // Create AuthnStatement
            AuthnStatementBuilder authnStatementBuilder = (AuthnStatementBuilder) builderFactory.getBuilder(AuthnStatement.DEFAULT_ELEMENT_NAME);
            AuthnStatement authnStatement = authnStatementBuilder.buildObject();
            authnStatement.setAuthnInstant(java.time.Instant.now());
            
            // Create AuthnContext
            AuthnContextBuilder authnContextBuilder = (AuthnContextBuilder) builderFactory.getBuilder(AuthnContext.DEFAULT_ELEMENT_NAME);
            AuthnContext authnContext = authnContextBuilder.buildObject();
            
            // Create AuthnContextClassRef
            AuthnContextClassRefBuilder authnContextClassRefBuilder = (AuthnContextClassRefBuilder) builderFactory.getBuilder(AuthnContextClassRef.DEFAULT_ELEMENT_NAME);
            AuthnContextClassRef authnContextClassRef = authnContextClassRefBuilder.buildObject();
            authnContextClassRef.setURI(AuthnContext.PASSWORD_AUTHN_CTX);
            
            authnContext.setAuthnContextClassRef(authnContextClassRef);
            authnStatement.setAuthnContext(authnContext);
            assertion.getAuthnStatements().add(authnStatement);
            
            // Create AttributeStatement with roles
            logger.debug("Processing roles for user {}: [{}]", samlBean.getUserid(), samlBean.getRoles());
            if (samlBean.getRoles() != null && !samlBean.getRoles().isEmpty()) {
                AttributeStatementBuilder attributeStatementBuilder = (AttributeStatementBuilder) builderFactory.getBuilder(AttributeStatement.DEFAULT_ELEMENT_NAME);
                AttributeStatement attributeStatement = attributeStatementBuilder.buildObject();
                
                // Create roles attribute
                AttributeBuilder attributeBuilder = (AttributeBuilder) builderFactory.getBuilder(Attribute.DEFAULT_ELEMENT_NAME);
                Attribute rolesAttribute = attributeBuilder.buildObject();
                rolesAttribute.setName("roles");
                rolesAttribute.setNameFormat("urn:oasis:names:tc:SAML:2.0:attrname-format:basic");
                
                // Parse comma-separated roles and add each as an attribute value
                String[] roles = samlBean.getRoles().split(",");
                XSStringBuilder stringBuilder = (XSStringBuilder) builderFactory.getBuilder(XSString.TYPE_NAME);
                
                for (String role : roles) {
                    String trimmedRole = role.trim();
                    if (!trimmedRole.isEmpty()) {
                        XSString roleValue = stringBuilder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
                        roleValue.setValue(trimmedRole);
                        rolesAttribute.getAttributeValues().add(roleValue);
                    }
                }
                
                attributeStatement.getAttributes().add(rolesAttribute);
                assertion.getAttributeStatements().add(attributeStatement);
                
                logger.info("Added {} role(s) to SAML assertion: {}", roles.length, samlBean.getRoles());
            } else {
                logger.warn("No roles to add to SAML assertion for user: {} (roles value: '{}')", samlBean.getUserid(), samlBean.getRoles());
            }
            
            // Sign the assertion if certificate and key are available
            Signature signature = null;
            try {
                // Use cached signing credential
                Credential signingCredential = createSigningCredential();
                
                // Create signature
                var sigBuilder = builderFactory.getBuilder(Signature.DEFAULT_ELEMENT_NAME);
                if (sigBuilder != null) {
                    signature = (Signature) sigBuilder.buildObject(Signature.DEFAULT_ELEMENT_NAME);
                    signature.setSigningCredential(signingCredential);
                    signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
                    signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
                    
                    // Add signature to assertion
                    assertion.setSignature(signature);
                    
                    logger.info("Assertion signature configured successfully");
                } else {
                    logger.error("Failed to get Signature builder from factory");
                }
            } catch (Exception e) {
                logger.error("Failed to sign assertion - certificate or key not found or invalid", e);
                // Continue without signature if signing fails
                signature = null;
            }
            
            // Add Assertion to Response
            response.getAssertions().add(assertion);
            
            // Marshal to XML
            MarshallerFactory marshallerFactory = XMLObjectProviderRegistrySupport.getMarshallerFactory();
            Marshaller marshaller = marshallerFactory.getMarshaller(response);
            if (marshaller == null) {
                throw new RuntimeException("No marshaller found for Response");
            }
            org.w3c.dom.Element element = marshaller.marshall(response);
            
            // Sign the assertion after marshalling (required by OpenSAML)
            if (signature != null) {
                try {
                    Signer.signObject(signature);
                    logger.info("Assertion signed successfully");
                } catch (Exception e) {
                    logger.error("Failed to sign assertion", e);
                    throw new RuntimeException("Assertion signing failed", e);
                }
            } else {
                logger.warn("No signature configured - assertion will not be signed");
            }
            
            // Convert to string
            javax.xml.transform.TransformerFactory transformerFactory = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            java.io.StringWriter stringWriter = new java.io.StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(element), 
                               new javax.xml.transform.stream.StreamResult(stringWriter));
            
            String samlResponse = stringWriter.toString();
            if (logger.isDebugEnabled()) {
                logger.debug("Generated SAML Response XML (length: {} chars):", samlResponse.length());
                logger.debug(samlResponse);
            }
            
            // Base64 encode the response
            return Utils.e(samlResponse);
            
        } catch (Exception ex) {
            logger.error("Failed to generate SAML response for user: {}", samlBean.getUserid(), ex);
            throw new RuntimeException("SAML response generation failed", ex);
        }
    }

    @PostMapping(value = "/saml/auth")
    public String authz(@ModelAttribute("saml") SamlBean saml,
                        @RequestParam(value = "userid", defaultValue = "") String userid,
                        @RequestParam(value = "roles", defaultValue = "") String roles,
                        @RequestParam(value = "authn", defaultValue = "") String authn,
                        @RequestParam(value = "notbefore_date", required = false) String notbefore_date,
                        @RequestParam(value = "notafter_date", required = false) String notafter_date,
                        @RequestParam(value = "samlid", required = false) String samlid,
                        @RequestParam(value = "assertionUrl", required = false) String assertionUrl) throws Exception {
        
        // Sanitize null bytes from all parameters
        userid = sanitizeNullBytes(userid);
        roles = sanitizeNullBytes(roles);
        authn = sanitizeNullBytes(authn);
        notbefore_date = sanitizeNullBytes(notbefore_date);
        notafter_date = sanitizeNullBytes(notafter_date);
        samlid = sanitizeNullBytes(samlid);
        assertionUrl = sanitizeNullBytes(assertionUrl);
        
        logger.info("Processing SAML authentication for user: {}", userid);
        
        try {
            // Determine roles to include in SAML assertion
            String finalRoles = roles;
            
            // If XML user repository is configured, look up user and get roles from memberOf
            if (xmlUserRepository != null && xmlUserRepository.isInitialized() && 
                userid != null && !userid.trim().isEmpty()) {
                
                logger.info("Looking up user '{}' in XML user repository for SAML", userid);
                UserInfo userInfo = xmlUserRepository.findByUsername(userid);
                
                if (userInfo != null) {
                    // Prioritize roles from request parameter if provided
                    if (roles != null && !roles.trim().isEmpty()) {
                        finalRoles = roles;
                        logger.info("Using roles from request parameter for user '{}': {}", userid, finalRoles);
                    } else {
                        // Fall back to XML roles if no roles in request
                        List<String> userRoles = userInfo.getRoles();
                        if (!userRoles.isEmpty()) {
                            finalRoles = String.join(",", userRoles);
                            logger.info("Using roles from XML for user '{}': {}", userid, finalRoles);
                        } else {
                            logger.info("User '{}' found in XML but has no roles assigned", userid);
                            finalRoles = "";
                        }
                    }
                } else {
                    logger.warn("User '{}' not found in XML user repository, using provided roles parameter", userid);
                    // Keep the roles parameter value if user not found
                }
            } else if (roles != null && !roles.trim().isEmpty()) {
                logger.debug("XML user repository not configured, using roles parameter: {}", roles);
            }
            
            // Set the authentication details in the SAML bean
            saml.setUserid(userid);
            saml.setRoles(finalRoles);
            saml.setAuthnResult(authn);
            
            // Use provided dates or current ones
            if (notbefore_date != null && !notbefore_date.isEmpty()) {
                saml.setNotbefore_date(notbefore_date);
            }
            if (notafter_date != null && !notafter_date.isEmpty()) {
                saml.setNotafter_date(notafter_date);
            }
            if (samlid != null && !samlid.isEmpty()) {
                saml.setSamlid(samlid);
            }
            if (assertionUrl != null && !assertionUrl.isEmpty()) {
                saml.setAssertionUrl(assertionUrl);
            }
            
            // Generate the SAML response using OpenSAML 4.x
            String response = generateSAMLResponseV4(saml);
            if (logger.isDebugEnabled()) {
                String decodedResponse = new String(Utils.b64d(response));
                logger.debug("Generated SAML Response (Base64 decoded, length: {} chars):", decodedResponse.length());
                logger.debug(decodedResponse);
            }
            saml.setSamlResponse(response);
            
            logger.info("SAML authentication successful for user: {} with roles: {}", userid, finalRoles);
            
        } catch (Exception e) {
            logger.error("Error generating SAML response for user: {}", userid, e);
            throw new RuntimeException("SAML response generation failed", e);
        }
        
        return "redirect";
    }
    
    /**
     * IdP Metadata Endpoint
     * 
     * Serves the SAML Identity Provider metadata XML document.
     * This endpoint allows Service Providers (SPs) to automatically discover
     * and configure their SAML integration with this IdP.
     * 
     * GET /saml/idp-metadata
     * 
     * @return ResponseEntity containing the IdP metadata XML
     */
    @GetMapping(value = "/saml/idp-metadata", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> idpMetadata() {
        try {
            logger.debug("IdP metadata endpoint called");
            
            // Check if handler is properly initialized
            if (cachedCertificate == null) {
                logger.error("IdP metadata endpoint - certificate not loaded");
                return ResponseEntity.status(500)
                    .body("<?xml version=\"1.0\"?><error>IdP metadata unavailable - certificate not configured</error>");
            }
            
            // Initialize OpenSAML if needed
            if (!isOpenSAMLInitialized()) {
                InitializationService.initialize();
                logger.info("OpenSAML 4.x initialized for IdP metadata generation");
            }
            
            // Generate IdP metadata XML
            String metadata = generateIdPMetadata();
            
            logger.info("IdP metadata served successfully (entity ID: {})", idpEntityId);
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(metadata);
                
        } catch (Exception e) {
            logger.error("Error generating IdP metadata", e);
            return ResponseEntity.status(500)
                .body("<?xml version=\"1.0\"?><error>Failed to generate IdP metadata</error>");
        }
    }
    
    /**
     * Generate SAML IdP metadata XML document.
     * Creates an EntityDescriptor with IDPSSODescriptor containing:
     * - Entity ID
     * - X.509 certificate for signature verification
     * - SingleSignOnService endpoint (HTTP-Redirect binding)
     * - Supported NameID formats
     * 
     * @return XML string representing the IdP metadata
     * @throws Exception if metadata generation fails
     */
    private String generateIdPMetadata() throws Exception {
        XMLObjectBuilderFactory builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory();
        
        // Create EntityDescriptor
        EntityDescriptorBuilder entityDescriptorBuilder = 
            (EntityDescriptorBuilder) builderFactory.getBuilder(EntityDescriptor.DEFAULT_ELEMENT_NAME);
        EntityDescriptor entityDescriptor = entityDescriptorBuilder.buildObject();
        entityDescriptor.setEntityID(idpEntityId);
        
        // Create IDPSSODescriptor
        IDPSSODescriptorBuilder idpDescriptorBuilder = 
            (IDPSSODescriptorBuilder) builderFactory.getBuilder(IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
        IDPSSODescriptor idpDescriptor = idpDescriptorBuilder.buildObject();
        idpDescriptor.setWantAuthnRequestsSigned(false);
        idpDescriptor.addSupportedProtocol("urn:oasis:names:tc:SAML:2.0:protocol");
        
        // Add signing key descriptor with certificate
        KeyDescriptorBuilder keyDescriptorBuilder = 
            (KeyDescriptorBuilder) builderFactory.getBuilder(KeyDescriptor.DEFAULT_ELEMENT_NAME);
        KeyDescriptor keyDescriptor = keyDescriptorBuilder.buildObject();
        keyDescriptor.setUse(UsageType.SIGNING);
        
        // Create KeyInfo with X509Data
        KeyInfoBuilder keyInfoBuilder = 
            (KeyInfoBuilder) builderFactory.getBuilder(KeyInfo.DEFAULT_ELEMENT_NAME);
        KeyInfo keyInfo = keyInfoBuilder.buildObject();
        
        X509DataBuilder x509DataBuilder = 
            (X509DataBuilder) builderFactory.getBuilder(X509Data.DEFAULT_ELEMENT_NAME);
        X509Data x509Data = x509DataBuilder.buildObject();
        
        X509CertificateBuilder x509CertBuilder = 
            (X509CertificateBuilder) builderFactory.getBuilder(org.opensaml.xmlsec.signature.X509Certificate.DEFAULT_ELEMENT_NAME);
        org.opensaml.xmlsec.signature.X509Certificate x509Cert = x509CertBuilder.buildObject();
        
        // Encode certificate as Base64 (without PEM headers)
        String certBase64 = Base64.getEncoder().encodeToString(cachedCertificate.getEncoded());
        x509Cert.setValue(certBase64);
        
        x509Data.getX509Certificates().add(x509Cert);
        keyInfo.getX509Datas().add(x509Data);
        keyDescriptor.setKeyInfo(keyInfo);
        
        idpDescriptor.getKeyDescriptors().add(keyDescriptor);
        
        // Add NameID formats supported
        idpDescriptor.getNameIDFormats().add(
            createNameIDFormat(builderFactory, NameIDType.UNSPECIFIED)
        );
        idpDescriptor.getNameIDFormats().add(
            createNameIDFormat(builderFactory, NameIDType.EMAIL)
        );
        idpDescriptor.getNameIDFormats().add(
            createNameIDFormat(builderFactory, NameIDType.PERSISTENT)
        );
        idpDescriptor.getNameIDFormats().add(
            createNameIDFormat(builderFactory, NameIDType.TRANSIENT)
        );
        
        // Add SingleSignOnService endpoint
        SingleSignOnServiceBuilder ssoServiceBuilder = 
            (SingleSignOnServiceBuilder) builderFactory.getBuilder(SingleSignOnService.DEFAULT_ELEMENT_NAME);
        SingleSignOnService ssoService = ssoServiceBuilder.buildObject();
        ssoService.setBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect");
        ssoService.setLocation(idpSsoUrl);
        
        idpDescriptor.getSingleSignOnServices().add(ssoService);
        
        // Add IDPSSODescriptor to EntityDescriptor
        entityDescriptor.getRoleDescriptors().add(idpDescriptor);
        
        // Marshal to XML
        MarshallerFactory marshallerFactory = XMLObjectProviderRegistrySupport.getMarshallerFactory();
        Marshaller marshaller = marshallerFactory.getMarshaller(entityDescriptor);
        Element element = marshaller.marshall(entityDescriptor);
        
        // Convert DOM Element to string
        StringWriter writer = new StringWriter();
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.transform(new DOMSource(element), new StreamResult(writer));
        
        return writer.toString();
    }
    
    /**
     * Helper method to create NameIDFormat element
     */
    private org.opensaml.saml.saml2.metadata.NameIDFormat createNameIDFormat(
            XMLObjectBuilderFactory builderFactory, String format) {
        org.opensaml.saml.saml2.metadata.impl.NameIDFormatBuilder nameIDFormatBuilder = 
            (org.opensaml.saml.saml2.metadata.impl.NameIDFormatBuilder) 
            builderFactory.getBuilder(org.opensaml.saml.saml2.metadata.NameIDFormat.DEFAULT_ELEMENT_NAME);
        org.opensaml.saml.saml2.metadata.NameIDFormat nameIDFormat = nameIDFormatBuilder.buildObject();
        nameIDFormat.setURI(format);
        return nameIDFormat;
    }
    
    /**
     * Sanitize null bytes from input strings to prevent XML parsing issues
     */
    private String sanitizeNullBytes(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("\u0000", "");
    }
}
