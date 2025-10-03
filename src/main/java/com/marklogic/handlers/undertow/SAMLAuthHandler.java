package com.marklogic.handlers.undertow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.DataFormatException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Marshaller;
import org.opensaml.core.xml.io.MarshallerFactory;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.impl.AssertionBuilder;
import org.opensaml.saml.saml2.core.impl.IssuerBuilder;
import org.opensaml.saml.saml2.core.impl.NameIDBuilder;
import org.opensaml.saml.saml2.core.impl.ResponseBuilder;
import org.opensaml.saml.saml2.core.impl.StatusBuilder;
import org.opensaml.saml.saml2.core.impl.StatusCodeBuilder;
import org.opensaml.saml.saml2.core.impl.SubjectBuilder;
import org.opensaml.saml.saml2.core.impl.SubjectConfirmationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.marklogic.Utils;
import com.marklogic.beans.saml;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

@Controller
public class SAMLAuthHandler {

    private static final Logger logger = LoggerFactory.getLogger(SAMLAuthHandler.class);

    @Autowired
    private saml saml;

    @GetMapping(value = "/saml/auth")
    public String authn(Model model, @RequestParam(value = "SAMLRequest") String req) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(SAMLAuthHandler.class).setLevel(saml.getCfg().samlDebug() ? Level.DEBUG : Level.INFO);
        model.addAttribute(saml);
        
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
     * Generate SAML Response using OpenSAML 4.x API
     * This is a temporary local implementation until Utils.generateSAMLResponse is updated
     */
    private String generateSAMLResponseV4(saml samlBean) throws Exception {
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
            
            assertion.setSubject(subject);
            
            // Add Assertion to Response
            response.getAssertions().add(assertion);
            
            // Marshal to XML
            MarshallerFactory marshallerFactory = XMLObjectProviderRegistrySupport.getMarshallerFactory();
            Marshaller marshaller = marshallerFactory.getMarshaller(response);
            if (marshaller == null) {
                throw new RuntimeException("No marshaller found for Response");
            }
            org.w3c.dom.Element element = marshaller.marshall(response);
            
            // Convert to string
            javax.xml.transform.TransformerFactory transformerFactory = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            java.io.StringWriter stringWriter = new java.io.StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(element), 
                               new javax.xml.transform.stream.StreamResult(stringWriter));
            
            String samlResponse = stringWriter.toString();
            logger.debug("Generated SAML Response XML: {}", samlResponse);
            
            // Base64 encode the response
            return Utils.e(samlResponse);
            
        } catch (Exception ex) {
            logger.error("Failed to generate SAML response for user: {}", samlBean.getUserid(), ex);
            throw new RuntimeException("SAML response generation failed", ex);
        }
    }

    @PostMapping(value = "/saml/auth")
    public String authz(@ModelAttribute saml saml,
                        @RequestParam(value = "userid", defaultValue = "") String userid,
                        @RequestParam(value = "roles", defaultValue = "") String roles,
                        @RequestParam(value = "authn", defaultValue = "") String authn,
                        @RequestParam(value = "notbefore_date", required = false) String notbefore_date,
                        @RequestParam(value = "notafter_date", required = false) String notafter_date,
                        @RequestParam(value = "samlid", required = false) String samlid,
                        @RequestParam(value = "assertionUrl", required = false) String assertionUrl) throws Exception {
        
        logger.info("Processing SAML authentication for user: {}", userid);
        
        try {
            // Set the authentication details in the SAML bean
            saml.setUserid(userid);
            saml.setRoles(roles);
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
            logger.debug("Generated SAML Response: {}", new String(Utils.b64d(response)));
            saml.setSamlResponse(response);
            
            logger.info("SAML authentication successful for user: {} with roles: {}", userid, roles);
            
        } catch (Exception e) {
            logger.error("Error generating SAML response for user: {}", userid, e);
            throw new RuntimeException("SAML response generation failed", e);
        }
        
        return "redirect";
    }
}
