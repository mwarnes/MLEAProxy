package com.marklogic.repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Repository for loading and querying users from a JSON file.
 * 
 * <p>This repository reads user data from a JSON file in the format:
 * <pre>{@code
 * {
 *   "users": [
 *     {
 *       "username": "admin",
 *       "password": "password",
 *       "dn": "cn=admin",
 *       "roles": ["admin", "appreader"],
 *       "rfc6238code": "IVKFK3B4OI4VQMDGENEVURT5GRYFIWTM"
 *     }
 *   ]
 * }
 * }</pre>
 * 
 * @since 2.0
 */
@Component
public class JsonUserRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonUserRepository.class);
    
    private Map<String, UserInfo> users = new HashMap<>();
    private boolean initialized = false;
    private String jsonFilePath;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Represents user information loaded from JSON.
     */
    public static class UserInfo {
        @JsonProperty("username")
        private String username;
        
        @JsonProperty("password")
        private String password;
        
        @JsonProperty("roles")
        private List<String> roles = new ArrayList<>();
        
        @JsonProperty("dn")
        private String dn;
        
        @JsonProperty("rfc6238code")
        private String rfc6238code;
        
        // Default constructor for Jackson
        public UserInfo() {
        }
        
        public UserInfo(String username, String dn) {
            this.username = username;
            this.dn = dn;
            this.roles = new ArrayList<>();
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
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
        
        public void setRoles(List<String> roles) {
            this.roles = roles != null ? roles : new ArrayList<>();
        }
        
        public void addRole(String role) {
            this.roles.add(role);
        }
        
        public String getDn() {
            return dn;
        }
        
        public void setDn(String dn) {
            this.dn = dn;
        }
        
        public String getRfc6238code() {
            return rfc6238code;
        }
        
        public void setRfc6238code(String rfc6238code) {
            this.rfc6238code = rfc6238code;
        }
    }
    
    /**
     * Internal wrapper class for JSON deserialization.
     */
    private static class UsersWrapper {
        @JsonProperty("users")
        private List<UserInfo> users;
        
        public List<UserInfo> getUsers() {
            return users;
        }
        
        public void setUsers(List<UserInfo> users) {
            this.users = users;
        }
    }
    
    /**
     * Initializes the repository by loading users from the specified JSON file.
     * 
     * @param filePath Path to the users.json file
     * @throws Exception if the file cannot be read or parsed
     */
    public void initialize(String filePath) throws Exception {
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.info("No JSON user file specified, repository not initialized");
            return;
        }
        
        this.jsonFilePath = filePath;
        File file = new File(filePath);
        
        if (!file.exists()) {
            logger.warn("JSON user file not found: {}", filePath);
            return;
        }
        
        logger.info("Loading users from JSON file: {}", filePath);
        
        try {
            UsersWrapper wrapper = objectMapper.readValue(file, UsersWrapper.class);
            
            if (wrapper == null || wrapper.getUsers() == null) {
                logger.warn("No users found in JSON file");
                return;
            }
            
            int userCount = 0;
            for (UserInfo userInfo : wrapper.getUsers()) {
                if (userInfo.getUsername() == null || userInfo.getUsername().trim().isEmpty()) {
                    logger.warn("User entry missing username, skipping");
                    continue;
                }
                
                // Ensure roles list is not null
                if (userInfo.getRoles() == null) {
                    userInfo.setRoles(new ArrayList<>());
                }
                
                users.put(userInfo.getUsername().toLowerCase(), userInfo);
                userCount++;
                
                logger.debug("Loaded user: {} with {} roles: {}", 
                            userInfo.getUsername(), 
                            userInfo.getRoles().size(), 
                            String.join(",", userInfo.getRoles()));
            }
            
            initialized = true;
            logger.info("Successfully loaded {} users from JSON file", userCount);
            
        } catch (IOException e) {
            logger.error("Failed to parse JSON user file: {}", filePath, e);
            throw new Exception("Failed to parse JSON user file", e);
        }
    }
    
    /**
     * Finds a user by their username.
     * 
     * @param username The username to search for (case-insensitive)
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
     * Gets the JSON file path used to initialize this repository.
     * 
     * @return JSON file path, or null if not initialized
     */
    public String getJsonFilePath() {
        return jsonFilePath;
    }
}
