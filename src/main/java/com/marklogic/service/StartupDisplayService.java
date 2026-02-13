package com.marklogic.service;

import com.marklogic.beans.SamlBean;
import com.marklogic.repository.JsonUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

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
     * Displays server port and base URL information.
     */
    private void displayServerInfo() {
        if (environment == null) {
            logger.warn("Spring Environment not available, skipping server info display");
            return;
        }

        String port = environment.getProperty("local.server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String baseUrl = "http://localhost:" + port + contextPath;

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

        String port = environment.getProperty("local.server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String baseUrl = "http://localhost:" + port + contextPath;

        logger.info("");
        logger.info("OAuth 2.0 Endpoints:");
        logger.info("--------------------------------------------------------------------------------");
        logger.info("Token Endpoint:           {}/oauth/token", baseUrl);
        logger.info("JWKS Endpoint:            {}/oauth/jwks", baseUrl);
        logger.info("OpenID Configuration:     {}/.well-known/openid-configuration", baseUrl);
        logger.info("");
        logger.info("Example Token Request:");
        logger.info("curl -X POST {}/oauth/token \\", baseUrl);
        logger.info("  -H \"Content-Type: application/x-www-form-urlencoded\" \\");
        logger.info("  -d \"grant_type=password&username=admin&password=password&client_id=test-client\"");
        logger.info("================================================================================");
    }

    /**
     * Displays SAML 2.0 endpoints and configuration status.
     */
    private void displaySAMLEndpoints() {
        if (environment == null) {
            return;
        }

        String port = environment.getProperty("local.server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String baseUrl = "http://localhost:" + port + contextPath;

        logger.info("");
        logger.info("SAML 2.0 Endpoints:");
        logger.info("--------------------------------------------------------------------------------");
        logger.info("Authentication:           {}/saml/auth", baseUrl);
        logger.info("Metadata:                 {}/saml/metadata", baseUrl);
        logger.info("Wrap Assertion:           {}/saml/wrapassertion", baseUrl);
        logger.info("CA Certificates:          {}/saml/cacerts", baseUrl);
        logger.info("");

        if (samlBean != null && samlBean.getCfg() != null) {
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
