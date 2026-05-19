package com.marklogic.handlers;

import com.marklogic.beans.SamlBean;
import com.marklogic.configuration.properties.MleaProxyProperties;
import com.marklogic.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.Security;
import java.util.Arrays;

/**
 * Application startup orchestrator.
 * Coordinates initialization of all MLEAProxy components including:
 * - Configuration loading
 * - Security providers
 * - In-memory LDAP directory servers
 * - LDAP proxy listeners
 * - Kerberos KDC
 * - SAML configuration
 * - Startup information display
 *
 * This class has been refactored to delegate complex operations to focused service classes,
 * following the Single Responsibility Principle. The original 1304-line "God Object" has been
 * decomposed into specialized services while maintaining the same functionality.
 *
 * @author mwarnes
 * @since 29/01/2017
 */
@Component
@Profile("!test")
class Applicationlistener implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(Applicationlistener.class);

    @Autowired
    private SamlBean saml;

    @Autowired
    private LDAPServerService ldapServerService;

    @Autowired
    private LDAPListenerService ldapListenerService;

    @Autowired
    private StartupDisplayService startupDisplayService;

    @Autowired
    private MleaProxyProperties config;

    private KerberosKDCServer kerberosKdc;

    /**
     * Main entry point for the application runner.
     * Orchestrates the initialization and startup of all application components in the correct order.
     *
     * @param args Command-line arguments passed to the application
     * @throws Exception if any initialization step fails
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("Starting MLEAProxy initialization");

        // Setup core components
        setupSecurityProviders();
        setupLDAPDebugging();
        logApplicationArguments(args);

        // Initialize static config for reflection-based handlers
        LDAPRequestHandler.setStaticConfig(config);

        // Start services
        ldapServerService.startInMemoryServers(config);
        startKerberosKDC();
        ldapListenerService.startLDAPListeners(config);
        initializeSAMLConfiguration();

        // Display startup summary
        startupDisplayService.displayStartupSummary();

        logger.info("MLEAProxy initialization complete");
    }

    /**
     * Sets up security providers required for cryptographic operations.
     * Adds BouncyCastle provider for PEM file parsing and SSL/TLS operations.
     */
    private void setupSecurityProviders() {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        logger.debug("BouncyCastle security provider registered");
    }

    /**
     * Configures LDAP SDK debugging based on application configuration.
     * Enables UnboundID LDAP SDK debug output when configured.
     */
    private void setupLDAPDebugging() {
        logger.debug("LDAP debug flag: {}", config.isLdapDebug());
        if (config.isLdapDebug()) {
            System.setProperty("com.unboundid.ldap.sdk.debug.enabled", "true");
            System.setProperty("com.unboundid.ldap.sdk.debug.type", "ldap");
            logger.info("LDAP SDK debugging enabled");
        }
    }

    /**
     * Logs command-line arguments passed to the application.
     * Useful for debugging application startup and configuration.
     *
     * @param args Command-line arguments to log
     */
    private void logApplicationArguments(ApplicationArguments args) {
        logger.info("Application started with command-line arguments: {}", Arrays.toString(args.getSourceArgs()));
        logger.debug("NonOptionArgs: {}", args.getNonOptionArgs());
        logger.debug("OptionNames: {}", args.getOptionNames());
    }

    /**
     * Starts the embedded Kerberos KDC (Key Distribution Center) if enabled.
     * The KDC provides Kerberos authentication services for testing/development.
     *
     * @throws Exception if KDC initialization fails
     */
    private void startKerberosKDC() throws Exception {
        try {
            if (config.getKerberos().isEnabled()) {
                logger.info("Kerberos KDC enabled, starting embedded KDC");
                kerberosKdc = new KerberosKDCServer(config.getKerberos());
                kerberosKdc.start();
                logger.info("Kerberos KDC started successfully");
            } else {
                logger.debug("Kerberos KDC disabled in configuration (set kerberos.enabled=true to enable)");
            }
        } catch (Exception e) {
            logger.error("Failed to start Kerberos KDC: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Initializes SAML configuration for Spring Boot SAML authentication handlers.
     * Sets the MleaProxyProperties on the SAML bean so Spring controllers can access it.
     *
     * Note: SAML endpoints are implemented as Spring Boot controllers (SAMLAuthHandler)
     * rather than Undertow handlers for better integration with Spring Security.
     */
    private void initializeSAMLConfiguration() {
        saml.setConfig(config);
        logger.debug("SAML configuration initialized");
    }
}
