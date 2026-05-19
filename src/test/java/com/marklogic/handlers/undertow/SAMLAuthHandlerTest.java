package com.marklogic.handlers.undertow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test suite for SAML authentication handler
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.profiles.active=test")
@AutoConfigureMockMvc
public class SAMLAuthHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should generate SAML response with signature")
    void testSAMLResponseGeneration() throws Exception {
        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-saml-id-123")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00Z")
                        .param("notafter_date", "2025-10-04T11:00:00Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "admin,user"))
                .andExpect(status().isOk())
                .andExpect(view().name("redirect"))
                .andExpect(model().attributeExists("saml"))
                .andReturn();

        // Extract SAML response from the rendered HTML body
        String htmlBody = result.getResponse().getContentAsString();
        String samlResponse = extractSAMLResponseFromHTML(htmlBody);
        assertNotNull(samlResponse, "SAML Response should be present in HTML");
        assertFalse(samlResponse.isEmpty());

        // Decode and validate SAML Response XML
        byte[] decodedBytes = Base64.getDecoder().decode(samlResponse);

        // Parse XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(decodedBytes));

        // Validate Response element
        Element root = doc.getDocumentElement();
        assertEquals("Response", root.getLocalName());
        assertEquals("urn:oasis:names:tc:SAML:2.0:protocol", root.getNamespaceURI());

        // Validate Assertion exists
        NodeList assertions = root.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Assertion");
        assertTrue(assertions.getLength() > 0, "SAML Response should contain an Assertion");

        Element assertion = (Element) assertions.item(0);

        // Validate Signature exists
        NodeList signatures = assertion.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature");
        assertTrue(signatures.getLength() > 0, "Assertion should contain a digital signature");

        // Validate signature components
        Element signature = (Element) signatures.item(0);
        NodeList signedInfo = signature.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "SignedInfo");
        assertTrue(signedInfo.getLength() > 0, "Signature should contain SignedInfo");

        NodeList signatureValue = signature.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "SignatureValue");
        assertTrue(signatureValue.getLength() > 0, "Signature should contain SignatureValue");

        // Validate RSA-SHA256 signature method
        NodeList signatureMethod = signature.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "SignatureMethod");
        assertTrue(signatureMethod.getLength() > 0, "Signature should specify signature method");
        Element sigMethod = (Element) signatureMethod.item(0);
        assertTrue(sigMethod.getAttribute("Algorithm").contains("rsa-sha256"), 
                  "Should use RSA-SHA256 signature algorithm");
    }

    @Test
    @DisplayName("Should include roles in SAML assertion")
    void testSAMLRoleInclusion() throws Exception {
        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-roles-id")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "admin,user,developer"))
                .andExpect(status().isOk())
                .andReturn();

        String htmlBody = result.getResponse().getContentAsString();
        String samlResponse = extractSAMLResponseFromHTML(htmlBody);
        assertNotNull(samlResponse);
        byte[] decodedBytes = Base64.getDecoder().decode(samlResponse);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(decodedBytes));

        // Validate AttributeStatement exists
        NodeList attrStatements = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "AttributeStatement");
        assertTrue(attrStatements.getLength() > 0, "Should contain AttributeStatement");

        // Validate roles attribute
        NodeList attributes = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Attribute");
        boolean foundRoles = false;
        for (int i = 0; i < attributes.getLength(); i++) {
            Element attr = (Element) attributes.item(i);
            if ("roles".equals(attr.getAttribute("Name"))) {
                foundRoles = true;
                NodeList attrValues = attr.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "AttributeValue");
                assertEquals(3, attrValues.getLength(), "Should have 3 role values");
                
                assertTrue(attrValues.item(0).getTextContent().contains("admin"));
                assertTrue(attrValues.item(1).getTextContent().contains("user"));
                assertTrue(attrValues.item(2).getTextContent().contains("developer"));
            }
        }
        assertTrue(foundRoles, "Should contain roles attribute");
    }

    @Test
    @DisplayName("Should handle single role")
    void testSAMLSingleRole() throws Exception {
        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-single-role")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "admin"))
                .andExpect(status().isOk())
                .andReturn();

        String htmlBody = result.getResponse().getContentAsString();
        String samlResponse = extractSAMLResponseFromHTML(htmlBody);
        assertNotNull(samlResponse);
        byte[] decodedBytes = Base64.getDecoder().decode(samlResponse);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(decodedBytes));

        NodeList attributes = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Attribute");
        for (int i = 0; i < attributes.getLength(); i++) {
            Element attr = (Element) attributes.item(i);
            if ("roles".equals(attr.getAttribute("Name"))) {
                NodeList attrValues = attr.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "AttributeValue");
                assertEquals(1, attrValues.getLength());
                assertTrue(attrValues.item(0).getTextContent().contains("admin"));
            }
        }
    }

    @Test
    @DisplayName("Should set correct SubjectConfirmationData")
    void testSubjectConfirmationData() throws Exception {
        String testSamlId = "test-subject-confirmation-id";
        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", testSamlId)
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00Z")
                        .param("notafter_date", "2025-10-04T11:00:00Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "user"))
                .andExpect(status().isOk())
                .andReturn();

        String htmlBody = result.getResponse().getContentAsString();
        String samlResponse = extractSAMLResponseFromHTML(htmlBody);
        assertNotNull(samlResponse);
        byte[] decodedBytes = Base64.getDecoder().decode(samlResponse);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(decodedBytes));

        // Validate SubjectConfirmationData
        NodeList confirmationData = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "SubjectConfirmationData");
        assertTrue(confirmationData.getLength() > 0);
        
        Element confirmData = (Element) confirmationData.item(0);
        
        // Should have InResponseTo matching the samlid
        assertEquals(testSamlId, confirmData.getAttribute("InResponseTo"));
        
        // Should have NotOnOrAfter timestamp
        String notOnOrAfter = confirmData.getAttribute("NotOnOrAfter");
        assertNotNull(notOnOrAfter);
        assertFalse(notOnOrAfter.isEmpty());
    }

    @Test
    @DisplayName("Should set correct Conditions element")
    void testConditionsElement() throws Exception {
        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-conditions-id")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "user"))
                .andExpect(status().isOk())
                .andReturn();

        String htmlBody = result.getResponse().getContentAsString();
        String samlResponse = extractSAMLResponseFromHTML(htmlBody);
        assertNotNull(samlResponse);
        byte[] decodedBytes = Base64.getDecoder().decode(samlResponse);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(decodedBytes));

        // Validate Conditions element
        NodeList conditions = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Conditions");
        assertTrue(conditions.getLength() > 0);
        
        Element condition = (Element) conditions.item(0);
        
        // Should have NotBefore attribute
        String notBefore = condition.getAttribute("NotBefore");
        assertNotNull(notBefore);
        assertFalse(notBefore.isEmpty());
        
        // Should have NotOnOrAfter attribute
        String notOnOrAfter = condition.getAttribute("NotOnOrAfter");
        assertNotNull(notOnOrAfter);
        assertFalse(notOnOrAfter.isEmpty());
        
        // NotOnOrAfter should be after NotBefore
        assertTrue(notOnOrAfter.compareTo(notBefore) > 0);
    }

    @Test
    @DisplayName("Should include AuthnStatement with Password context")
    void testAuthnStatement() throws Exception {
        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-authn-id")
                        .param("userid", "authnuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "user"))
                .andExpect(status().isOk())
                .andReturn();

        String htmlBody = result.getResponse().getContentAsString();
        String samlResponse = extractSAMLResponseFromHTML(htmlBody);
        assertNotNull(samlResponse);
        byte[] decodedBytes = Base64.getDecoder().decode(samlResponse);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(decodedBytes));

        // Validate AuthnStatement
        NodeList authnStatements = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "AuthnStatement");
        assertTrue(authnStatements.getLength() > 0);
        
        Element authnStatement = (Element) authnStatements.item(0);
        
        // Should have AuthnInstant
        String authnInstant = authnStatement.getAttribute("AuthnInstant");
        assertNotNull(authnInstant);
        assertFalse(authnInstant.isEmpty());
        
        // Validate AuthnContext with Password class
        NodeList authnContextClassRef = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "AuthnContextClassRef");
        assertTrue(authnContextClassRef.getLength() > 0);
        
        String contextClass = authnContextClassRef.item(0).getTextContent();
        assertEquals("urn:oasis:names:tc:SAML:2.0:ac:classes:Password", contextClass);
    }

    @Test
    @DisplayName("Should handle empty roles gracefully")
    void testEmptyRoles() throws Exception {
        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-empty-roles")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", ""))
                .andExpect(status().isOk())
                .andReturn();

        String htmlBody = result.getResponse().getContentAsString();
        String samlResponse = extractSAMLResponseFromHTML(htmlBody);
        assertNotNull(samlResponse);
        byte[] decodedBytes = Base64.getDecoder().decode(samlResponse);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(decodedBytes));

        // Should still have valid SAML response
        Element root = doc.getDocumentElement();
        assertEquals("Response", root.getLocalName());
    }

    @Test
    @DisplayName("Should trim whitespace from roles")
    void testRoleWhitespaceTrimming() throws Exception {
        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-whitespace-roles")
                        .param("userid", "whitespaceuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", " admin , user , developer "))
                .andExpect(status().isOk())
                .andReturn();

        String htmlBody = result.getResponse().getContentAsString();
        String samlResponse = extractSAMLResponseFromHTML(htmlBody);
        assertNotNull(samlResponse);
        byte[] decodedBytes = Base64.getDecoder().decode(samlResponse);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(decodedBytes));

        NodeList attributes = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Attribute");
        for (int i = 0; i < attributes.getLength(); i++) {
            Element attr = (Element) attributes.item(i);
            if ("roles".equals(attr.getAttribute("Name"))) {
                NodeList attrValues = attr.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "AttributeValue");
                // Roles should be trimmed
                assertEquals("admin", attrValues.item(0).getTextContent().trim());
                assertEquals("user", attrValues.item(1).getTextContent().trim());
                assertEquals("developer", attrValues.item(2).getTextContent().trim());
            }
        }
    }

    /**
     * Helper method to extract SAML response from HTML body
     * Looks for <input type="hidden" id="SAMLResponse" name="SAMLResponse" value="...">
     * or <input ... name="samlResponse" value="...">
     */
    private String extractSAMLResponseFromHTML(String html) {
        // Try to find SAMLResponse or samlResponse input
        String[] patterns = {"id=\"SAMLResponse\"", "name=\"SAMLResponse\"", "name=\"samlResponse\""};
        
        for (String pattern : patterns) {
            int startIndex = html.indexOf(pattern);
            if (startIndex == -1) {
                continue;
            }
            
            // Find the value attribute
            int valueStart = html.indexOf("value=\"", startIndex);
            if (valueStart == -1) {
                continue;
            }
            
            valueStart += "value=\"".length();
            int valueEnd = html.indexOf("\"", valueStart);
            if (valueEnd == -1) {
                continue;
            }
            
            return html.substring(valueStart, valueEnd);
        }
        
        return null;
    }
}
