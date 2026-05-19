package com.marklogic.repository;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Repository for loading and querying users from an XML file.
 * 
 * <p>This repository reads user data from an XML file in the format:
 * <pre>{@code
 * <ldap>
 *   <users basedn="ou=users,dc=marklogic,dc=local">
 *     <user dn="cn=username">
 *       <sAMAccountName>username</sAMAccountName>
 *       <memberOf>cn=group1,ou=groups,dc=marklogic,dc=local</memberOf>
 *       <memberOf>cn=group2,ou=groups,dc=marklogic,dc=local</memberOf>
 *       <userPassword>password</userPassword>
 *       <rfc6238code>IVKFK3B4OI4VQMDGENEVURT5GRYFIWTM</rfc6238code>
 *     </user>
 *   </users>
 * </ldap>
 * }</pre>
 * 
 * @since 1.0
 */
@Component
public class XmlUserRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(XmlUserRepository.class);
    
    private Map<String, UserInfo> users = new HashMap<>();
    private boolean initialized = false;
    private String xmlFilePath;
    
    /**
     * Represents user information loaded from XML.
     */
    public static class UserInfo {
        private String username;
        private String password;
        private List<String> roles;
        private String dn;
        private String rfc6238code;
        
        public UserInfo(String username, String dn) {
            this.username = username;
            this.dn = dn;
            this.roles = new ArrayList<>();
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
        
        public List<String> getRoles() {
            return roles;
        }
        
        public void addRole(String role) {
            this.roles.add(role);
        }
        
        public String getDn() {
            return dn;
        }
        
        public String getRfc6238code() {
            return rfc6238code;
        }
        
        public void setRfc6238code(String rfc6238code) {
            this.rfc6238code = rfc6238code;
        }
    }
    
    /**
     * Initializes the repository by loading users from the specified XML file.
     * 
     * @param filePath Path to the users.xml file
     * @throws Exception if the file cannot be read or parsed
     */
    public void initialize(String filePath) throws Exception {
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.info("No XML user file specified, repository not initialized");
            return;
        }
        
        this.xmlFilePath = filePath;
        File file = new File(filePath);
        
        if (!file.exists()) {
            logger.warn("XML user file not found: {}", filePath);
            return;
        }
        
        logger.info("Loading users from XML file: {}", filePath);
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Security: Disable external entities to prevent XXE attacks
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file);
        doc.getDocumentElement().normalize();
        
        NodeList userNodes = doc.getElementsByTagName("user");
        int userCount = 0;
        
        for (int i = 0; i < userNodes.getLength(); i++) {
            Element userElement = (Element) userNodes.item(i);
            
            // Get sAMAccountName
            NodeList samNodes = userElement.getElementsByTagName("sAMAccountName");
            if (samNodes.getLength() == 0) {
                logger.warn("User element missing sAMAccountName, skipping");
                continue;
            }
            
            String username = samNodes.item(0).getTextContent();
            String dn = userElement.getAttribute("dn");
            
            UserInfo userInfo = new UserInfo(username, dn);
            
            // Get password
            NodeList passwordNodes = userElement.getElementsByTagName("userPassword");
            if (passwordNodes.getLength() > 0) {
                userInfo.setPassword(passwordNodes.item(0).getTextContent());
            }
            
            // Get RFC6238 code (for TOTP)
            NodeList totpNodes = userElement.getElementsByTagName("rfc6238code");
            if (totpNodes.getLength() > 0) {
                userInfo.setRfc6238code(totpNodes.item(0).getTextContent());
            }
            
            // Get memberOf (roles/groups)
            NodeList memberOfNodes = userElement.getElementsByTagName("memberOf");
            for (int j = 0; j < memberOfNodes.getLength(); j++) {
                String memberOfDn = memberOfNodes.item(j).getTextContent();
                // Extract group name from DN (e.g., "cn=admin,ou=groups,..." -> "admin")
                String groupName = extractGroupName(memberOfDn);
                if (groupName != null) {
                    userInfo.addRole(groupName);
                }
            }
            
            users.put(username.toLowerCase(), userInfo);
            userCount++;
            
            logger.debug("Loaded user: {} with {} roles: {}", username, userInfo.getRoles().size(), 
                        String.join(",", userInfo.getRoles()));
        }
        
        initialized = true;
        logger.info("Successfully loaded {} users from XML file", userCount);
    }
    
    /**
     * Extracts the group name from an LDAP DN string.
     * 
     * @param memberOfDn LDAP DN string (e.g., "cn=admin,ou=groups,dc=marklogic,dc=local")
     * @return Group name (e.g., "admin") or null if cannot be extracted
     */
    private String extractGroupName(String memberOfDn) {
        if (memberOfDn == null || memberOfDn.trim().isEmpty()) {
            return null;
        }
        
        // Extract CN value from DN
        String[] parts = memberOfDn.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.toLowerCase().startsWith("cn=")) {
                return part.substring(3);
            }
        }
        
        return null;
    }
    
    /**
     * Finds a user by their sAMAccountName.
     * 
     * @param username The sAMAccountName to search for (case-insensitive)
     * @return UserInfo object if found, null otherwise
     */
    public UserInfo findByUsername(String username) {
        if (!initialized || username == null) {
            return null;
        }
        return users.get(username.toLowerCase());
    }
    
    /**
     * Validates a user's password.
     * 
     * @param username The username to validate
     * @param password The password to check
     * @return true if username exists and password matches, false otherwise
     */
    public boolean validatePassword(String username, String password) {
        UserInfo user = findByUsername(username);
        if (user == null || user.getPassword() == null) {
            return false;
        }
        return user.getPassword().equals(password);
    }
    
    /**
     * Gets the roles for a user.
     * 
     * @param username The username to get roles for
     * @return List of role names, or empty list if user not found
     */
    public List<String> getUserRoles(String username) {
        UserInfo user = findByUsername(username);
        if (user == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(user.getRoles());
    }
    
    /**
     * Checks if the repository is initialized and has users loaded.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Gets the number of users loaded.
     * 
     * @return Number of users in the repository
     */
    public int getUserCount() {
        return users.size();
    }
    
    /**
     * Gets the XML file path used to initialize this repository.
     * 
     * @return XML file path, or null if not initialized
     */
    public String getXmlFilePath() {
        return xmlFilePath;
    }
}
