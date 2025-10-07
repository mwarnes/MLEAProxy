package com.marklogic.handlers.undertow;

import com.marklogic.repository.JsonUserRepository;
import com.marklogic.repository.JsonUserRepository.UserInfo;
import org.ietf.jgss.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Bridge handler for generating SAML assertions from Kerberos tickets.
 * 
 * This handler enables integration between Kerberos authentication and SAML-based
 * systems by accepting a Kerberos ticket and returning a SAML 2.0 assertion with
 * user attributes and roles from the user repository.
 * 
 * Endpoints:
 * - POST /saml/assertion-from-kerberos - Exchange Kerberos ticket for SAML assertion
 * 
 * Usage:
 * <pre>
 * # Get Kerberos ticket
 * kinit mluser1@MARKLOGIC.LOCAL
 * 
 * # Exchange for SAML assertion
 * curl --negotiate -u : -X POST http://localhost:8080/saml/assertion-from-kerberos
 * 
 * # Response is XML SAML 2.0 assertion
 * &lt;saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"&gt;
 *   &lt;saml:Subject&gt;
 *     &lt;saml:NameID Format="..."&gt;mluser1&lt;/saml:NameID&gt;
 *   &lt;/saml:Subject&gt;
 *   &lt;saml:AttributeStatement&gt;
 *     &lt;saml:Attribute Name="roles"&gt;
 *       &lt;saml:AttributeValue&gt;app-reader&lt;/saml:AttributeValue&gt;
 *     &lt;/saml:Attribute&gt;
 *   &lt;/saml:AttributeStatement&gt;
 * &lt;/saml:Assertion&gt;
 * </pre>
 * 
 * Phase 3 Enhancement:
 * - Validates Kerberos ticket using JAAS/GSS-API
 * - Extracts principal from ticket
 * - Queries JsonUserRepository for user attributes
 * - Generates SAML 2.0 assertion with user data
 * - Returns XML assertion (unsigned - can be enhanced)
 * 
 * @since 2.0.0 (Phase 3)
 */
@RestController
@RequestMapping("/saml")
public class KerberosSAMLBridgeHandler {
    private static final Logger logger = LoggerFactory.getLogger(KerberosSAMLBridgeHandler.class);

    @Autowired(required = false)
    private JsonUserRepository jsonUserRepository;

    @Value("${kerberos.enabled:false}")
    private boolean kerberosEnabled;

    @Value("${kerberos.service-principal:HTTP/localhost@MARKLOGIC.LOCAL}")
    private String servicePrincipal;

    @Value("${kerberos.keytab-location:./kerberos/keytabs/service.keytab}")
    private String keytabLocation;

    @Value("${kerberos.debug:false}")
    private boolean debug;

    @Value("${saml.sp.issuer:mleaproxy-saml-bridge}")
    private String samlIssuer;

    @Value("${saml.assertion.validity.seconds:300}")
    private long assertionValiditySeconds;

    private static final DateTimeFormatter SAML_DATE_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC"));

    /**
     * Exchange a Kerberos ticket for a SAML 2.0 assertion.
     * 
     * This endpoint accepts a Kerberos ticket via the Authorization: Negotiate header,
     * validates it, extracts the principal, queries the user repository for attributes,
     * and returns a SAML 2.0 assertion as XML.
     * 
     * @param authHeader Authorization header with Negotiate token
     * @return SAML assertion XML or error response
     */
    @PostMapping(value = "/assertion-from-kerberos", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> exchangeKerberosForSAML(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        if (!kerberosEnabled) {
            logger.warn("Kerberos is disabled but /saml/assertion-from-kerberos was called");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.TEXT_PLAIN)
                .body("Kerberos authentication is not enabled");
        }

        // Validate Authorization header
        if (authHeader == null || !authHeader.startsWith("Negotiate ")) {
            logger.debug("No Kerberos ticket in Authorization header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("WWW-Authenticate", "Negotiate")
                .contentType(MediaType.TEXT_PLAIN)
                .body("Kerberos ticket required in Authorization: Negotiate header");
        }

        try {
            // Extract and validate Kerberos ticket
            String base64Token = authHeader.substring("Negotiate ".length());
            byte[] kerberosTicket = Base64.getDecoder().decode(base64Token);
            
            logger.debug("Received Kerberos ticket for SAML assertion, size: {} bytes", kerberosTicket.length);

            // Validate ticket and extract principal
            String fullPrincipal = validateTicketAndGetPrincipal(kerberosTicket);
            
            if (fullPrincipal == null) {
                logger.warn("Kerberos ticket validation failed");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Negotiate")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Kerberos ticket validation failed");
            }

            logger.info("Kerberos ticket validated successfully for principal: {}", fullPrincipal);

            // Extract username from principal (e.g., "mluser1@MARKLOGIC.LOCAL" -> "mluser1")
            String username = extractUsername(fullPrincipal);
            
            // Load user attributes
            Map<String, List<String>> attributes = loadUserAttributes(username);
            
            logger.debug("Generating SAML assertion for principal: {} (username: {})", fullPrincipal, username);

            // Generate SAML assertion
            String samlAssertion = generateSAMLAssertion(username, fullPrincipal, attributes);

            logger.info("SAML assertion generated for Kerberos principal: {} (username: {})", fullPrincipal, username);

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(samlAssertion);

        } catch (Exception e) {
            logger.error("Error exchanging Kerberos ticket for SAML assertion", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_PLAIN)
                .body("Failed to process Kerberos ticket: " + e.getMessage());
        }
    }

    /**
     * Validate Kerberos ticket using GSS-API and extract principal name.
     * 
     * @param kerberosTicket Base64-decoded Kerberos ticket
     * @return Principal name (e.g., "mluser1@MARKLOGIC.LOCAL") or null if validation fails
     */
    private String validateTicketAndGetPrincipal(byte[] kerberosTicket) {
        try {
            // Create JAAS configuration
            javax.security.auth.login.Configuration jaasConfig = createJaasConfiguration();
            javax.security.auth.login.Configuration.setConfiguration(jaasConfig);

            // Login as service principal using keytab
            LoginContext loginContext = new LoginContext("KerberosService");
            loginContext.login();
            
            Subject serviceSubject = loginContext.getSubject();
            
            if (logger.isDebugEnabled()) {
                logger.debug("Service login successful, principals: {}", serviceSubject.getPrincipals());
            }

            // Validate ticket using GSS-API within privileged context
            String principal;
            try {
                principal = Subject.callAs(serviceSubject, () -> {
                    // Create GSS context
                    GSSManager manager = GSSManager.getInstance();
                    GSSContext context = manager.createContext((GSSCredential) null);

                    // Accept security context (validate ticket)
                    context.acceptSecContext(kerberosTicket, 0, kerberosTicket.length);

                    // Extract client principal
                    String clientPrincipal = context.getSrcName().toString();
                    
                    if (logger.isDebugEnabled()) {
                        logger.debug("Ticket validated successfully");
                        logger.debug("  Client principal: {}", clientPrincipal);
                    }

                    // Clean up
                    context.dispose();
                    
                    return clientPrincipal;
                });
            } catch (Exception e) {
                logger.error("Error validating Kerberos ticket", e);
                principal = null;
            }

            // Logout
            loginContext.logout();
            
            return principal;
            
        } catch (LoginException e) {
            logger.error("JAAS login failed for service principal: {}", servicePrincipal, e);
            return null;
        }
    }

    /**
     * Create JAAS configuration for service principal authentication.
     * 
     * @return JAAS configuration
     */
    private javax.security.auth.login.Configuration createJaasConfiguration() {
        return new javax.security.auth.login.Configuration() {
            @Override
            public javax.security.auth.login.AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                Map<String, String> options = new HashMap<>();
                options.put("useKeyTab", "true");
                options.put("storeKey", "true");
                options.put("doNotPrompt", "true");
                options.put("isInitiator", "false");
                options.put("principal", servicePrincipal);
                options.put("keyTab", new File(keytabLocation).getAbsolutePath());
                
                if (debug) {
                    options.put("debug", "true");
                }

                return new javax.security.auth.login.AppConfigurationEntry[] {
                    new javax.security.auth.login.AppConfigurationEntry(
                        "com.sun.security.auth.module.Krb5LoginModule",
                        javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                        options
                    )
                };
            }
        };
    }

    /**
     * Extract username from Kerberos principal.
     * 
     * @param principal Full Kerberos principal
     * @return Username
     */
    private String extractUsername(String principal) {
        if (principal == null) {
            return null;
        }
        
        int atIndex = principal.indexOf('@');
        if (atIndex > 0) {
            return principal.substring(0, atIndex);
        }
        
        return principal;
    }

    /**
     * Load user attributes from JSON user repository.
     * 
     * @param username Username (without realm)
     * @return Map of attribute names to values
     */
    private Map<String, List<String>> loadUserAttributes(String username) {
        Map<String, List<String>> attributes = new HashMap<>();

        if (jsonUserRepository == null) {
            logger.warn("JsonUserRepository not available, returning minimal attributes");
            attributes.put("uid", Arrays.asList(username));
            return attributes;
        }

        try {
            UserInfo userInfo = jsonUserRepository.findByUsername(username);
            if (userInfo != null) {
                // Add standard attributes
                attributes.put("uid", Arrays.asList(username));
                
                if (userInfo.getDn() != null) {
                    attributes.put("dn", Arrays.asList(userInfo.getDn()));
                }
                
                if (userInfo.getRoles() != null && !userInfo.getRoles().isEmpty()) {
                    attributes.put("roles", new ArrayList<>(userInfo.getRoles()));
                }
                
                logger.debug("Loaded {} attributes for user: {}", attributes.size(), username);
            } else {
                logger.debug("User {} not found in repository, using minimal attributes", username);
                attributes.put("uid", Arrays.asList(username));
            }
        } catch (Exception e) {
            logger.error("Error loading attributes for user: {}", username, e);
            attributes.put("uid", Arrays.asList(username));
        }

        return attributes;
    }

    /**
     * Generate a SAML 2.0 assertion with user attributes.
     * 
     * Creates a basic SAML 2.0 assertion including:
     * - Issuer
     * - Subject with NameID
     * - Conditions (NotBefore, NotOnOrAfter)
     * - AuthnStatement
     * - AttributeStatement with user attributes
     * 
     * Note: This assertion is NOT signed. For production use, implement
     * signature generation using XMLSignature API.
     * 
     * @param username Username
     * @param fullPrincipal Full Kerberos principal
     * @param attributes User attributes
     * @return SAML assertion XML string
     * @throws Exception if assertion generation fails
     */
    private String generateSAMLAssertion(String username, String fullPrincipal, 
                                         Map<String, List<String>> attributes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        // SAML namespaces
        String samlNS = "urn:oasis:names:tc:SAML:2.0:assertion";
        String xsiNS = "http://www.w3.org/2001/XMLSchema-instance";
        String xsNS = "http://www.w3.org/2001/XMLSchema";

        // Create Assertion element
        Element assertion = doc.createElementNS(samlNS, "saml:Assertion");
        assertion.setAttribute("xmlns:saml", samlNS);
        assertion.setAttribute("xmlns:xsi", xsiNS);
        assertion.setAttribute("xmlns:xs", xsNS);
        assertion.setAttribute("ID", "_" + UUID.randomUUID().toString());
        assertion.setAttribute("Version", "2.0");
        assertion.setAttribute("IssueInstant", SAML_DATE_FORMAT.format(Instant.now()));
        doc.appendChild(assertion);

        // Issuer
        Element issuer = doc.createElementNS(samlNS, "saml:Issuer");
        issuer.setTextContent(samlIssuer);
        assertion.appendChild(issuer);

        // Subject
        Element subject = doc.createElementNS(samlNS, "saml:Subject");
        Element nameID = doc.createElementNS(samlNS, "saml:NameID");
        nameID.setAttribute("Format", "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified");
        nameID.setTextContent(username);
        subject.appendChild(nameID);
        assertion.appendChild(subject);

        // Conditions
        Instant now = Instant.now();
        Instant notOnOrAfter = now.plusSeconds(assertionValiditySeconds);
        Element conditions = doc.createElementNS(samlNS, "saml:Conditions");
        conditions.setAttribute("NotBefore", SAML_DATE_FORMAT.format(now));
        conditions.setAttribute("NotOnOrAfter", SAML_DATE_FORMAT.format(notOnOrAfter));
        assertion.appendChild(conditions);

        // AuthnStatement
        Element authnStatement = doc.createElementNS(samlNS, "saml:AuthnStatement");
        authnStatement.setAttribute("AuthnInstant", SAML_DATE_FORMAT.format(now));
        Element authnContext = doc.createElementNS(samlNS, "saml:AuthnContext");
        Element authnContextClassRef = doc.createElementNS(samlNS, "saml:AuthnContextClassRef");
        authnContextClassRef.setTextContent("urn:oasis:names:tc:SAML:2.0:ac:classes:Kerberos");
        authnContext.appendChild(authnContextClassRef);
        authnStatement.appendChild(authnContext);
        assertion.appendChild(authnStatement);

        // AttributeStatement
        Element attributeStatement = doc.createElementNS(samlNS, "saml:AttributeStatement");
        
        // Add Kerberos principal as attribute
        addAttribute(doc, attributeStatement, "kerberosPrincipal", Arrays.asList(fullPrincipal), samlNS, xsNS);
        
        // Add all user attributes
        for (Map.Entry<String, List<String>> entry : attributes.entrySet()) {
            addAttribute(doc, attributeStatement, entry.getKey(), entry.getValue(), samlNS, xsNS);
        }
        
        // Add authentication method
        addAttribute(doc, attributeStatement, "authenticationMethod", Arrays.asList("kerberos"), samlNS, xsNS);
        
        assertion.appendChild(attributeStatement);

        // Convert to XML string
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        
        return writer.toString();
    }

    /**
     * Add a SAML attribute to the attribute statement.
     * 
     * @param doc XML document
     * @param attributeStatement Attribute statement element
     * @param name Attribute name
     * @param values Attribute values
     * @param samlNS SAML namespace
     * @param xsNS XML Schema namespace
     */
    private void addAttribute(Document doc, Element attributeStatement, String name, 
                             List<String> values, String samlNS, String xsNS) {
        Element attribute = doc.createElementNS(samlNS, "saml:Attribute");
        attribute.setAttribute("Name", name);
        attribute.setAttribute("NameFormat", "urn:oasis:names:tc:SAML:2.0:attrname-format:basic");
        
        for (String value : values) {
            Element attributeValue = doc.createElementNS(samlNS, "saml:AttributeValue");
            attributeValue.setAttribute("xsi:type", "xs:string");
            attributeValue.setTextContent(value);
            attribute.appendChild(attributeValue);
        }
        
        attributeStatement.appendChild(attribute);
    }
}
