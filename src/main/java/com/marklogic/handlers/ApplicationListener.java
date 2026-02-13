package com.marklogic.handlers;

import com.marklogic.beans.SamlBean;
import com.marklogic.configuration.ApplicationConfig;
import com.marklogic.service.*;
import org.aeonbits.owner.ConfigFactory;
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

    private KerberosKDCServer kerberosKdc;
    private ApplicationConfig applicationConfig;

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

        // Initialize configuration
        ApplicationConfig cfg = initializeConfiguration();
        this.applicationConfig = cfg;

        // Setup core components
        setupSecurityProviders();
        setupLDAPDebugging(cfg);
        logApplicationArguments(args);

        // Start services
        ldapServerService.startInMemoryServers(cfg);
        startKerberosKDC(cfg);
        ldapListenerService.startLDAPListeners(cfg);
        initializeSAMLConfiguration(cfg);

        // Display startup summary
        startupDisplayService.displayStartupSummary();

        logger.info("MLEAProxy initialization complete");
    }

    /**
     * Initializes the application configuration from properties files.
     * Sets the default properties file path if not specified on command line.
     * Uses Aeonbits Owner library with MERGE policy to load configuration from multiple sources.
     *
     * @return Loaded ApplicationConfig instance
     */
    private ApplicationConfig initializeConfiguration() {
        // Set mleaproxy.properties System Property if not passed on the commandline
        if (System.getProperty("mleaproxy.properties") == null) {
            System.setProperty("mleaproxy.properties", "./mleaproxy.properties");
        }

        ApplicationConfig cfg = ConfigFactory.create(ApplicationConfig.class);
        logger.debug("Configuration loaded: {}", cfg);

        return cfg;
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
     *
     * @param cfg Application configuration containing debug settings
     */
    private void setupLDAPDebugging(ApplicationConfig cfg) {
        logger.debug("LDAP debug flag: {}", cfg.ldapDebug());
        if (cfg.ldapDebug()) {
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
     * @param cfg Application configuration containing Kerberos settings
     * @throws Exception if KDC initialization fails
     */
    private void startKerberosKDC(ApplicationConfig cfg) throws Exception {
        try {
            com.marklogic.configuration.KerberosConfig krbCfg =
                ConfigFactory.create(com.marklogic.configuration.KerberosConfig.class);

            if (krbCfg.kerberosEnabled()) {
                logger.info("Kerberos KDC enabled, starting embedded KDC");
                kerberosKdc = new KerberosKDCServer(krbCfg);
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
     * Sets the ApplicationConfig on the SAML bean so Spring controllers can access it.
     *
     * Note: SAML endpoints are implemented as Spring Boot controllers (SAMLAuthHandler)
     * rather than Undertow handlers for better integration with Spring Security.
     *
     * @param cfg Application configuration to set on the SAML bean
     */
    private void initializeSAMLConfiguration(ApplicationConfig cfg) {
        saml.setCfg(cfg);
        logger.debug("SAML configuration initialized");
    }
}
