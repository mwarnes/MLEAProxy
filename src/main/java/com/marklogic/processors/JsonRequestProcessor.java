package com.marklogic.processors;

import com.marklogic.configuration.properties.RequestProcessorProperties;
import com.unboundid.ldap.listener.LDAPListenerClientConnection;
import com.unboundid.ldap.protocol.*;
import com.unboundid.ldap.sdk.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JSON-based LDAP Request Processor
 * Handles LDAP authentication and search operations using a JSON user repository
 */
public class JsonRequestProcessor implements IRequestProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JsonRequestProcessor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private JsonNode usersData;
    private RequestProcessorProperties cfg;

    @Override
    public void initialize(RequestProcessorProperties cfg) throws Exception {
        logger.info("Initializing JsonRequestProcessor");
        this.cfg = cfg;
        
        // Get the JSON user data file path from configuration (using parm1)
        String userDataPath = this.cfg.getParm1();
        if (userDataPath == null || userDataPath.isEmpty()) {
            logger.info("JSON file path missing using default JSON configuration instead.");
            // Load from classpath
            loadJsonUserDataFromClasspath();
        } else {
            File f = new File(userDataPath);
            if (f.exists() && !f.isDirectory()) {
                logger.info("Using custom JSON configuration from: {}", userDataPath);
                loadJsonUserDataFromFile(userDataPath);
            } else {
                logger.error("Custom JSON configuration file not found: {}", userDataPath);
                throw new Exception(userDataPath + " missing or invalid.");
            }
        }
        
        logger.info("JsonRequestProcessor initialized successfully");
    }

    private void loadJsonUserDataFromFile(String userDataPath) throws IOException {
        Path path = Paths.get(userDataPath);
        if (!Files.exists(path)) {
            throw new IOException("JSON user data file not found: " + userDataPath);
        }
        
        String jsonContent = Files.readString(path);
        this.usersData = objectMapper.readTree(jsonContent);
        
        JsonNode users = usersData.get("users");
        logger.info("Loaded {} users from JSON file", users.size());
    }

    private void loadJsonUserDataFromClasspath() throws IOException {
        try {
            String jsonContent = new String(
                getClass().getClassLoader().getResourceAsStream("users.json").readAllBytes()
            );
            this.usersData = objectMapper.readTree(jsonContent);
            
            JsonNode users = usersData.get("users");
            logger.info("Loaded {} users from JSON classpath", users.size());
        } catch (Exception e) {
            throw new IOException("Failed to load users.json from classpath", e);
        }
    }

    @Override
    public LDAPMessage processBindRequest(int messageID, BindRequestProtocolOp request, 
            List<Control> controls, LDAPConnection ldapConnection, 
            LDAPListenerClientConnection listenerConnection) {
        
        logger.debug("Processing bind request for DN: {}", request.getBindDN());
        
        try {
            String bindDN = request.getBindDN();
            String password = request.getSimplePassword().stringValue();
            
            // Find user by DN
            JsonNode user = findUserByDN(bindDN);
            if (user == null) {
                logger.warn("User not found for DN: {}", bindDN);
                LDAPResult bindResult = new LDAPResult(messageID, ResultCode.INVALID_CREDENTIALS);
                BindResponseProtocolOp bindResponseProtocolOp = new BindResponseProtocolOp(
                    bindResult.getResultCode().intValue(),
                    bindResult.getMatchedDN(), 
                    bindResult.getDiagnosticMessage(),
                    Arrays.asList(bindResult.getReferralURLs()), 
                    null);
                return new LDAPMessage(messageID, bindResponseProtocolOp, 
                    Arrays.asList(bindResult.getResponseControls()));
            }
            
            // Verify password
            String expectedPassword = user.get("password").asText();
            if (!password.equals(expectedPassword)) {
                logger.warn("Invalid password for user: {}", user.get("username").asText());
                LDAPResult bindResult = new LDAPResult(messageID, ResultCode.INVALID_CREDENTIALS);
                BindResponseProtocolOp bindResponseProtocolOp = new BindResponseProtocolOp(
                    bindResult.getResultCode().intValue(),
                    bindResult.getMatchedDN(), 
                    bindResult.getDiagnosticMessage(),
                    Arrays.asList(bindResult.getReferralURLs()), 
                    null);
                return new LDAPMessage(messageID, bindResponseProtocolOp, 
                    Arrays.asList(bindResult.getResponseControls()));
            }
            
            logger.info("Successful bind for user: {}", user.get("username").asText());
            LDAPResult bindResult = new LDAPResult(messageID, ResultCode.SUCCESS);
            BindResponseProtocolOp bindResponseProtocolOp = new BindResponseProtocolOp(
                bindResult.getResultCode().intValue(),
                bindResult.getMatchedDN(), 
                bindResult.getDiagnosticMessage(),
                Arrays.asList(bindResult.getReferralURLs()), 
                null);
            return new LDAPMessage(messageID, bindResponseProtocolOp, 
                Arrays.asList(bindResult.getResponseControls()));
                    
        } catch (Exception e) {
            logger.error("Error processing bind request", e);
            LDAPResult bindResult = new LDAPResult(messageID, ResultCode.OTHER);
            BindResponseProtocolOp bindResponseProtocolOp = new BindResponseProtocolOp(
                bindResult.getResultCode().intValue(),
                bindResult.getMatchedDN(), 
                bindResult.getDiagnosticMessage(),
                Arrays.asList(bindResult.getReferralURLs()), 
                null);
            return new LDAPMessage(messageID, bindResponseProtocolOp, 
                Arrays.asList(bindResult.getResponseControls()));
        }
    }

    @Override
    public LDAPMessage processSearchRequest(int messageID, SearchRequestProtocolOp request, 
            List<Control> controls, LDAPConnection ldapConnection, 
            LDAPListenerClientConnection listenerConnection) {
        
        logger.debug("Processing search request - Base DN: {}, Scope: {}, Filter: {}", 
            request.getBaseDN(), request.getScope(), request.getFilter());
        
        try {
            List<SearchResultEntry> entries = new ArrayList<>();
            
            JsonNode users = usersData.get("users");
            
            // Process each user and check if it matches the search criteria
            for (int i = 0; i < users.size(); i++) {
                JsonNode user = users.get(i);
                SearchResultEntry entry = createSearchResultEntry(user, request);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            
            // Send all matching entries
            for (SearchResultEntry entry : entries) {
                SearchResultEntryProtocolOp searchResultEntryProtocolOp = 
                    new SearchResultEntryProtocolOp(entry);
                try {
                    listenerConnection.sendSearchResultEntry(messageID, searchResultEntryProtocolOp, new Control[0]);
                } catch (Exception e) {
                    logger.error("Error sending search result entry", e);
                }
            }
            
            // Send search result done
            logger.debug("Search completed, returned {} entries", entries.size());
            LDAPResult searchResult = new LDAPResult(messageID, ResultCode.SUCCESS);
            return new LDAPMessage(messageID, 
                new SearchResultDoneProtocolOp(searchResult.getResultCode().intValue(), 
                    searchResult.getMatchedDN(), searchResult.getDiagnosticMessage(), 
                    Arrays.asList(searchResult.getReferralURLs())));
                    
        } catch (Exception e) {
            logger.error("Error processing search request", e);
            LDAPResult searchResult = new LDAPResult(messageID, ResultCode.OTHER);
            return new LDAPMessage(messageID, 
                new SearchResultDoneProtocolOp(searchResult.getResultCode().intValue(), 
                    searchResult.getMatchedDN(), searchResult.getDiagnosticMessage(), 
                    Arrays.asList(searchResult.getReferralURLs())));
        }
    }

    private JsonNode findUserByDN(String dn) {
        try {
            JsonNode users = usersData.get("users");
            for (int i = 0; i < users.size(); i++) {
                JsonNode user = users.get(i);
                if (dn.equals(user.get("dn").asText())) {
                    return user;
                }
            }
        } catch (Exception e) {
            logger.error("Error finding user by DN: {}", dn, e);
        }
        return null;
    }

    private SearchResultEntry createSearchResultEntry(JsonNode user, SearchRequestProtocolOp request) {
        try {
            String userDN = user.get("dn").asText();
            String username = user.get("username").asText();
            
            // Basic DN matching - for simplicity, we'll return all users for now
            // In a full implementation, you'd want to properly parse and match the search base and filter
            
            List<Attribute> attributes = new ArrayList<>();
            
            // Add common LDAP attributes
            attributes.add(new Attribute("cn", username));
            attributes.add(new Attribute("uid", username));
            attributes.add(new Attribute("userPrincipalName", username));
            attributes.add(new Attribute("objectClass", "person", "organizationalPerson", "inetOrgPerson"));
            
            // Add roles if present
            if (user.has("roles")) {
                JsonNode roles = user.get("roles");
                List<String> roleStrings = new ArrayList<>();
                for (int i = 0; i < roles.size(); i++) {
                    roleStrings.add(roles.get(i).asText());
                }
                if (!roleStrings.isEmpty()) {
                    attributes.add(new Attribute("memberOf", roleStrings.toArray(new String[0])));
                }
            }
            
            return new SearchResultEntry(userDN, attributes);
            
        } catch (Exception e) {
            logger.error("Error creating search result entry for user", e);
            return null;
        }
    }

    @Override
    public LDAPMessage processAddRequest(int messageID, AddRequestProtocolOp request, 
            List<Control> controls, LDAPConnection ldapConnection, 
            LDAPListenerClientConnection listenerConnection) {
        
        logger.debug("Add request not supported in JSON processor");
        LDAPResult result = new LDAPResult(messageID, ResultCode.UNWILLING_TO_PERFORM);
        return new LDAPMessage(messageID, 
            new AddResponseProtocolOp(result.getResultCode().intValue(), 
                result.getMatchedDN(), result.getDiagnosticMessage(), 
                Arrays.asList(result.getReferralURLs())));
    }

    @Override
    public LDAPMessage processCompareRequest(int messageID, CompareRequestProtocolOp request, 
            List<Control> controls, LDAPConnection ldapConnection, 
            LDAPListenerClientConnection listenerConnection) {
        
        logger.debug("Compare request not supported in JSON processor");
        LDAPResult result = new LDAPResult(messageID, ResultCode.UNWILLING_TO_PERFORM);
        return new LDAPMessage(messageID, 
            new CompareResponseProtocolOp(result.getResultCode().intValue(), 
                result.getMatchedDN(), result.getDiagnosticMessage(), 
                Arrays.asList(result.getReferralURLs())));
    }

    @Override
    public LDAPMessage processDeleteRequest(int messageID, DeleteRequestProtocolOp request, 
            List<Control> controls, LDAPConnection ldapConnection, 
            LDAPListenerClientConnection listenerConnection) {
        
        logger.debug("Delete request not supported in JSON processor");
        LDAPResult result = new LDAPResult(messageID, ResultCode.UNWILLING_TO_PERFORM);
        return new LDAPMessage(messageID, 
            new DeleteResponseProtocolOp(result.getResultCode().intValue(), 
                result.getMatchedDN(), result.getDiagnosticMessage(), 
                Arrays.asList(result.getReferralURLs())));
    }

    @Override
    public LDAPMessage processExtendedRequest(int messageID, ExtendedRequestProtocolOp request, 
            List<Control> controls, LDAPConnection ldapConnection, 
            LDAPListenerClientConnection listenerConnection) {
        
        logger.debug("Extended request not supported in JSON processor");
        final ExtendedResponseProtocolOp extendedResponseProtocolOp =
                new ExtendedResponseProtocolOp(ResultCode.UNWILLING_TO_PERFORM_INT_VALUE,
                        null, null, null, null, null);
        return new LDAPMessage(messageID, extendedResponseProtocolOp, controls);
    }

    @Override
    public LDAPMessage processModifyRequest(int messageID, ModifyRequestProtocolOp request, 
            List<Control> controls, LDAPConnection ldapConnection, 
            LDAPListenerClientConnection listenerConnection) {
        
        logger.debug("Modify request not supported in JSON processor");
        LDAPResult result = new LDAPResult(messageID, ResultCode.UNWILLING_TO_PERFORM);
        return new LDAPMessage(messageID, 
            new ModifyResponseProtocolOp(result.getResultCode().intValue(), 
                result.getMatchedDN(), result.getDiagnosticMessage(), 
                Arrays.asList(result.getReferralURLs())));
    }

    @Override
    public LDAPMessage processModifyDNRequest(int messageID, ModifyDNRequestProtocolOp request, 
            List<Control> controls, LDAPConnection ldapConnection, 
            LDAPListenerClientConnection listenerConnection) {
        
        logger.debug("ModifyDN request not supported in JSON processor");
        LDAPResult result = new LDAPResult(messageID, ResultCode.UNWILLING_TO_PERFORM);
        return new LDAPMessage(messageID, 
            new ModifyDNResponseProtocolOp(result.getResultCode().intValue(), 
                result.getMatchedDN(), result.getDiagnosticMessage(), 
                Arrays.asList(result.getReferralURLs())));
    }
}