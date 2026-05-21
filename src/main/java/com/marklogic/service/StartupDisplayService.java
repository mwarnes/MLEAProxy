package com.marklogic.service;

import com.marklogic.beans.SamlBean;
import com.marklogic.repository.JsonUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Service for displaying startup information and endpoint summaries.
 * Shows configured users, OAuth endpoints, SAML endpoints, and example curl commands.
 */
@Service
public class StartupDisplayService {

    private static final Logger logger = LoggerFactory.getLogger(StartupDisplayService.class);

    @Autowired(required = false)
    private Environment environment;

    @Autowired(required = false)
    private JsonUserRepository jsonUserRepository;

    @Autowired(required = false)
    private SamlBean samlBean;

    /**
     * Displays comprehensive startup summary including server info, endpoints, and users.
     */
    public void displayStartupSummary() {
        displayServerInfo();
        displayOAuthEndpoints();
        displaySAMLEndpoints();
        displayConfiguredUsers();
    }

    /**
     * Gets the base URL for endpoints.
     * Priority:
     * 1. oauth.server.base.url property (if explicitly configured)
     * 2. Auto-detect from server hostname, port, and SSL settings
     */
    private String getBaseUrl() {
        if (environment == null) {
            return "http://localhost:8080";
        }
        
        // Check for explicitly configured base URL first (override)
        String configuredBaseUrl = environment.getProperty("oauth.server.base.url");
        if (configuredBaseUrl != null && !configuredBaseUrl.isEmpty()) {
            return configuredBaseUrl;
        }
        
        // Auto-detect from server settings
        String port = environment.getProperty("local.server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        boolean sslEnabled = Boolean.parseBoolean(environment.getProperty("server.ssl.enabled", "false"));
        String protocol = sslEnabled ? "https" : "http";
        
        // Get the server's hostname
        String hostname = getServerHostname();
        
        return protocol + "://" + hostname + ":" + port + contextPath;
    }
    
    /**
     * Gets the server's hostname, preferring the canonical hostname (FQDN).
     */
    private String getServerHostname() {
        try {
            // Try to get the fully qualified domain name
            String canonicalHostname = InetAddress.getLocalHost().getCanonicalHostName();
            if (canonicalHostname != null && !canonicalHostname.isEmpty() 
                    && !canonicalHostname.equals("localhost")
                    && !canonicalHostname.startsWith("127.")) {
                return canonicalHostname;
            }
            
            // Fall back to simple hostname
            String hostname = InetAddress.getLocalHost().getHostName();
            if (hostname != null && !hostname.isEmpty()) {
                return hostname;
            }
        } catch (UnknownHostException e) {
            logger.debug("Could not determine server hostname: {}", e.getMessage());
        }
        
        // Final fallback
        return "localhost";
    }

    /**
     * Displays server port and base URL information.
     */
    private void displayServerInfo() {
        if (environment == null) {
            logger.warn("Spring Environment not available, skipping server info display");
            return;
        }

        String port = environment.getProperty("local.server.port", "8080");
        String baseUrl = getBaseUrl();

        logger.info("================================================================================");
        logger.info("MLEAProxy Server Started");
        logger.info("================================================================================");
        logger.info("Server Port: {}", port);
        logger.info("Base URL: {}", baseUrl);
        logger.info("================================================================================");
    }

    /**
     * Displays OAuth 2.0 endpoints and example curl commands.
     */
    private void displayOAuthEndpoints() {
        if (environment == null) {
            return;
        }

        String baseUrl = getBaseUrl();
        boolean isHttps = baseUrl.startsWith("https://");
        String curlFlag = isHttps ? "-sk" : "-s";

        logger.info("");
        logger.info("OAuth 2.0 Endpoints:");
        logger.info("--------------------------------------------------------------------------------");
        logger.info("Token Endpoint:           {}/oauth/token", baseUrl);
        logger.info("JWKS Endpoint:            {}/oauth/jwks", baseUrl);
        logger.info("OpenID Configuration:     {}/oauth/.well-known/config", baseUrl);
        logger.info("");
        logger.info("Example Token Request:");
        logger.info("curl {} -X POST {}/oauth/token \\", curlFlag, baseUrl);
        logger.info("  -d \"grant_type=password\" \\");
        logger.info("  -d \"username=admin\" \\");
        logger.info("  -d \"password=password\" \\");
        logger.info("  -d \"client_id=marklogic\" \\");
        logger.info("  -d \"client_secret=secret\"");
        logger.info("================================================================================");
    }

    /**
     * Displays SAML 2.0 endpoints and configuration status.
     */
    private void displaySAMLEndpoints() {
        if (environment == null) {
            return;
        }

        String baseUrl = getBaseUrl();

        logger.info("");
        logger.info("SAML 2.0 Endpoints:");
        logger.info("--------------------------------------------------------------------------------");
        logger.info("Authentication:           {}/saml/auth", baseUrl);
        logger.info("IdP Metadata:             {}/saml/idp-metadata", baseUrl);
        logger.info("CA Certificates:          {}/saml/ca", baseUrl);
        logger.info("");

        if (samlBean != null && samlBean.getConfig() != null) {
            logger.info("SAML Configuration: Loaded");
        } else {
            logger.info("SAML Configuration: Not configured");
        }

        logger.info("================================================================================");
    }

    /**
     * Displays configured users from the JSON user repository.
     */
    private void displayConfiguredUsers() {
        if (jsonUserRepository == null) {
            logger.info("");
            logger.info("User Repository: Not available");
            logger.info("================================================================================");
            return;
        }

        logger.info("");
        logger.info("Configured Users (from users.json):");
        logger.info("--------------------------------------------------------------------------------");

        var users = jsonUserRepository.getAllUsers();
        if (users.isEmpty()) {
            logger.info("No users configured");
        } else {
            // Display header
            logger.info(String.format("%-20s %-20s %-40s", "Username", "Password", "Roles"));
            logger.info("--------------------------------------------------------------------------------");

            // Display each user
            for (var user : users) {
                String rolesStr = user.getRoles().isEmpty() ? "(none)" : String.join(", ", user.getRoles());
                logger.info(String.format("%-20s %-20s %-40s",
                    user.getUsername(),
                    user.getPassword(),
                    rolesStr
                ));
            }

            logger.info("--------------------------------------------------------------------------------");
            logger.info("Total users: {}", users.size());
            logger.info("");
            logger.info("Example login: admin / password");
        }

        logger.info("================================================================================");
    }
}
