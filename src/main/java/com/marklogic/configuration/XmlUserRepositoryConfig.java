package com.marklogic.configuration;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Configuration;

import com.marklogic.repository.XmlUserRepository;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class for initializing the XML User Repository.
 * 
 * This configuration checks for the users.xml file path in the following order:
 * 1. Command line argument: --users-xml=/path/to/users.xml
 * 2. Command line argument: --users=/path/to/users.xml
 * 3. Application property: users.xml.path
 * 4. System property: users.xml.path
 * 
 * If a users.xml file is provided, the repository will be initialized and
 * used by OAuth and SAML handlers for user authentication and role lookup.
 * 
 * @since 1.0
 */
@Configuration
public class XmlUserRepositoryConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(XmlUserRepositoryConfig.class);
    
    @Autowired
    private ApplicationArguments applicationArguments;
    
    @Autowired
    private XmlUserRepository xmlUserRepository;
    
    @Value("${users.xml.path:}")
    private String usersXmlPathProperty;
    
    @PostConstruct
    public void initializeUserRepository() {
        String usersXmlPath = null;
        
        // Check command line arguments first
        if (applicationArguments.containsOption("users-xml")) {
            List<String> values = applicationArguments.getOptionValues("users-xml");
            if (values != null && !values.isEmpty()) {
                usersXmlPath = values.get(0);
                logger.info("Using users.xml path from --users-xml argument: {}", usersXmlPath);
            }
        } else if (applicationArguments.containsOption("users")) {
            List<String> values = applicationArguments.getOptionValues("users");
            if (values != null && !values.isEmpty()) {
                usersXmlPath = values.get(0);
                logger.info("Using users.xml path from --users argument: {}", usersXmlPath);
            }
        }
        
        // Check application properties
        if (usersXmlPath == null && usersXmlPathProperty != null && !usersXmlPathProperty.trim().isEmpty()) {
            usersXmlPath = usersXmlPathProperty;
            logger.info("Using users.xml path from application property: {}", usersXmlPath);
        }
        
        // Check system property
        if (usersXmlPath == null) {
            usersXmlPath = System.getProperty("users.xml.path");
            if (usersXmlPath != null) {
                logger.info("Using users.xml path from system property: {}", usersXmlPath);
            }
        }
        
        // Initialize repository if path is provided
        if (usersXmlPath != null && !usersXmlPath.trim().isEmpty()) {
            try {
                xmlUserRepository.initialize(usersXmlPath);
                if (xmlUserRepository.isInitialized()) {
                    logger.info("✅ XML user repository initialized successfully from: {}", usersXmlPath);
                    logger.info("   Loaded {} users with role mappings", xmlUserRepository.getUserCount());
                } else {
                    logger.warn("⚠️  XML user repository initialization completed but no users loaded");
                }
            } catch (Exception e) {
                logger.error("❌ Failed to initialize XML user repository from: {}", usersXmlPath, e);
                logger.error("   OAuth and SAML authentication will fall back to parameter-based roles");
            }
        } else {
            logger.info("No users.xml file specified - OAuth and SAML will use parameter-based roles");
            logger.info("To enable XML-based user lookup, use: --users-xml=/path/to/users.xml");
        }
    }
}
