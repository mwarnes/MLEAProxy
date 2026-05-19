package com.marklogic.configuration;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Configuration;

import com.marklogic.repository.JsonUserRepository;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class for initializing the JSON User Repository.
 * 
 * This configuration checks for the users.json file path in the following order:
 * 1. Command line argument: --users-json=/path/to/users.json
 * 2. Command line argument: --users=/path/to/users.json
 * 3. Application property: users.json.path
 * 4. System property: users.json.path
 * 
 * If a users.json file is provided, the repository will be initialized and
 * used by OAuth and SAML handlers for user authentication and role lookup.
 * 
 * @since 2.0
 */
@Configuration
public class JsonUserRepositoryConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonUserRepositoryConfig.class);
    
    @Autowired
    private ApplicationArguments applicationArguments;
    
    @Autowired
    private JsonUserRepository jsonUserRepository;
    
    @Value("${users.json.path:}")
    private String usersJsonPathProperty;
    
    @PostConstruct
    public void initializeUserRepository() {
        String usersJsonPath = null;
        
        // Check command line arguments first
        if (applicationArguments.containsOption("users-json")) {
            List<String> values = applicationArguments.getOptionValues("users-json");
            if (values != null && !values.isEmpty()) {
                usersJsonPath = values.get(0);
                logger.info("Using users.json path from --users-json argument: {}", usersJsonPath);
            }
        } else if (applicationArguments.containsOption("users")) {
            List<String> values = applicationArguments.getOptionValues("users");
            if (values != null && !values.isEmpty()) {
                usersJsonPath = values.get(0);
                logger.info("Using users.json path from --users argument: {}", usersJsonPath);
            }
        }
        
        // Check application properties
        if (usersJsonPath == null && usersJsonPathProperty != null && !usersJsonPathProperty.trim().isEmpty()) {
            usersJsonPath = usersJsonPathProperty;
            logger.info("Using users.json path from application property: {}", usersJsonPath);
        }
        
        // Check system property
        if (usersJsonPath == null) {
            usersJsonPath = System.getProperty("users.json.path");
            if (usersJsonPath != null) {
                logger.info("Using users.json path from system property: {}", usersJsonPath);
            }
        }
        
        // Initialize repository if path is provided
        if (usersJsonPath != null && !usersJsonPath.trim().isEmpty()) {
            try {
                jsonUserRepository.initialize(usersJsonPath);
                if (jsonUserRepository.isInitialized()) {
                    logger.info("✅ JSON user repository initialized successfully from: {}", usersJsonPath);
                    logger.info("   Loaded {} users with role mappings", jsonUserRepository.getUserCount());
                } else {
                    logger.warn("⚠️  JSON user repository initialization completed but no users loaded");
                }
            } catch (Exception e) {
                logger.error("❌ Failed to initialize JSON user repository from: {}", usersJsonPath, e);
                logger.error("   OAuth and SAML authentication will fall back to parameter-based roles");
            }
        } else {
            logger.info("No users.json file specified - OAuth and SAML will use parameter-based roles");
            logger.info("To enable JSON-based user lookup, use: --users-json=/path/to/users.json");
        }
    }
}
