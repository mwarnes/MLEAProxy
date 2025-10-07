package com.marklogic.handlers;

import com.marklogic.configuration.KerberosConfig;
import org.apache.kerby.kerberos.kerb.KrbException;
import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Embedded Kerberos KDC (Key Distribution Center) Server using Apache Kerby.
 * 
 * This class manages an in-memory Kerberos KDC for testing and development purposes.
 * It provides the same user-friendly approach as the UnboundID in-memory LDAP server.
 * 
 * Features:
 * - Automatic principal creation from LDAP users
 * - Auto-generated krb5.conf for clients
 * - Service principal keytab generation
 * - Localhost-optimized (no DNS requirements)
 * 
 * Usage:
 * <pre>
 * KerberosConfig config = ConfigFactory.create(KerberosConfig.class);
 * KerberosKDCServer kdc = new KerberosKDCServer(config);
 * kdc.start();
 * </pre>
 * 
 * @see org.apache.kerby.kerberos.kerb.server.SimpleKdcServer
 * @since 2.0.0
 */
public class KerberosKDCServer {
    private static final Logger logger = LoggerFactory.getLogger(KerberosKDCServer.class);
    
    private SimpleKdcServer kdc;
    private final KerberosConfig config;
    private Path workDir;
    private boolean running = false;
    
    /**
     * Create a new KDC server with the given configuration.
     * 
     * @param config Kerberos configuration
     */
    public KerberosKDCServer(KerberosConfig config) {
        this.config = config;
    }
    
    /**
     * Start the KDC server.
     * 
     * This will:
     * 1. Create the working directory
     * 2. Initialize the KDC
     * 3. Create user principals (from LDAP if configured)
     * 4. Create service principals
     * 5. Start the KDC listener
     * 6. Generate krb5.conf
     * 7. Generate service keytabs
     * 
     * @throws Exception if startup fails
     */
    public void start() throws Exception {
        if (!config.kerberosEnabled()) {
            logger.info("Kerberos KDC is disabled (set kerberos.enabled=true to enable)");
            return;
        }
        
        logger.info("Starting Kerberos KDC...");
        
        // Create work directory
        workDir = Paths.get(config.workDir());
        Files.createDirectories(workDir);
        logger.debug("Work directory: {}", workDir.toAbsolutePath());
        
        // Initialize KDC
        kdc = new SimpleKdcServer();
        kdc.setKdcRealm(config.kerberosRealm());
        kdc.setKdcHost(config.kdcHost());
        kdc.setKdcTcpPort(config.kdcPort());
        kdc.setAllowUdp(false); // TCP only for simplicity
        kdc.setWorkDir(workDir.toFile());
        
        if (config.debug()) {
            logger.debug("Kerberos debug logging enabled");
        }
        
        logger.debug("KDC Configuration:");
        logger.debug("  Realm: {}", config.kerberosRealm());
        logger.debug("  Host: {}", config.kdcHost());
        logger.debug("  Port: {}", config.kdcPort());
        logger.debug("  Admin Port: {}", config.adminPort());
        logger.debug("  Work Dir: {}", workDir.toAbsolutePath());
        
        // Initialize KDC
        kdc.init();
        logger.debug("KDC initialized");
        
        // Create principals
        createPrincipals();
        
        // Start KDC
        kdc.start();
        running = true;
        
        logger.info("✓ Kerberos KDC started successfully");
        logger.info("  Realm: {}", config.kerberosRealm());
        logger.info("  KDC: {}:{}", config.kdcHost(), config.kdcPort());
        logger.info("  Work Directory: {}", workDir.toAbsolutePath());
        
        // Generate krb5.conf
        generateKrb5Config();
        
        // Generate keytabs
        generateKeytabs();
        
        // Print usage instructions
        printUsageInstructions();
    }
    
    /**
     * Create user and service principals.
     * 
     * @throws Exception if principal creation fails
     */
    private void createPrincipals() throws Exception {
        logger.info("Creating Kerberos principals...");
        
        List<Principal> userPrincipals = new ArrayList<>();
        
        // Import from LDAP if configured
        if (config.importPrincipalsFromLdap()) {
            userPrincipals.addAll(importFromLdap());
        }
        
        // Create user principals
        int userCount = 0;
        for (Principal principal : userPrincipals) {
            String principalName = principal.name + "@" + config.kerberosRealm();
            try {
                kdc.createPrincipal(principalName, principal.password);
                logger.debug("  Created user principal: {}", principalName);
                userCount++;
            } catch (Exception e) {
                logger.error("Failed to create principal: {}", principalName, e);
            }
        }
        logger.info("  Created {} user principals", userCount);
        
        // Create service principals
        int serviceCount = 0;
        String[] servicePrincipals = config.servicePrincipals();
        if (servicePrincipals != null && servicePrincipals.length > 0) {
            for (String servicePrincipal : servicePrincipals) {
                String fullPrincipal = servicePrincipal + "@" + config.kerberosRealm();
                try {
                    kdc.createPrincipal(fullPrincipal, "service-password");
                    logger.debug("  Created service principal: {}", fullPrincipal);
                    serviceCount++;
                } catch (Exception e) {
                    logger.error("Failed to create service principal: {}", fullPrincipal, e);
                }
            }
        }
        logger.info("  Created {} service principals", serviceCount);
        
        logger.info("✓ Principal creation complete ({} users, {} services)", userCount, serviceCount);
    }
    
    /**
     * Import principals from the in-memory LDAP server.
     * 
     * Phase 1 Implementation: Hardcoded users from marklogic.ldif
     * Future: Will integrate with actual LDAP server
     * 
     * @return list of principals to create
     */
    private List<Principal> importFromLdap() {
        logger.info("Importing principals from LDAP (base DN: {})", config.ldapBaseDn());
        
        // Phase 1: Hardcoded principals matching marklogic.ldif
        // Future enhancement: Query actual LDAP server
        List<Principal> principals = new ArrayList<>();
        principals.add(new Principal("mluser1", "password"));
        principals.add(new Principal("mluser2", "password"));
        principals.add(new Principal("mluser3", "password"));
        principals.add(new Principal("appreader", "password"));
        principals.add(new Principal("appwriter", "password"));
        principals.add(new Principal("appadmin", "password"));
        
        logger.debug("Imported {} principals from LDAP", principals.size());
        return principals;
    }
    
    /**
     * Generate krb5.conf file for Kerberos clients.
     * 
     * Creates two copies:
     * 1. ./krb5.conf (root directory for convenience)
     * 2. ./kerberos/krb5.conf (work directory)
     * 
     * The configuration is optimized for localhost testing with no DNS requirements.
     * 
     * @throws Exception if file generation fails
     */
    private void generateKrb5Config() throws Exception {
        logger.info("Generating krb5.conf...");
        
        String krb5Content = String.format("""
            # Auto-generated by MLEAProxy Kerberos KDC
            # Date: %s
            # Realm: %s
            # KDC: %s:%d
            #
            # Usage:
            #   export KRB5_CONFIG=./krb5.conf
            #   kinit mluser1@%s
            #   klist
            
            [libdefaults]
                default_realm = %s
                dns_lookup_realm = false
                dns_lookup_kdc = false
                rdns = false
                udp_preference_limit = 1
                ticket_lifetime = %d
                renew_lifetime = %d
                clockskew = %d
                forwardable = true
                
            [realms]
                %s = {
                    kdc = %s:%d
                    admin_server = %s:%d
                    default_domain = localhost
                }
                
            [domain_realm]
                .localhost = %s
                localhost = %s
                
            [logging]
                default = FILE:/tmp/krb5libs.log
                kdc = FILE:/tmp/krb5kdc.log
                admin_server = FILE:/tmp/kadmind.log
            """,
            LocalDateTime.now(),
            config.kerberosRealm(),
            config.kdcHost(), config.kdcPort(),
            config.kerberosRealm(),
            config.kerberosRealm(),
            config.ticketLifetime(),
            config.renewableLifetime(),
            config.clockSkew(),
            config.kerberosRealm(),
            config.kdcHost(), config.kdcPort(),
            config.kdcHost(), config.adminPort(),
            config.kerberosRealm(),
            config.kerberosRealm()
        );
        
        // Write to work directory
        Path krb5WorkPath = workDir.resolve("krb5.conf");
        Files.writeString(krb5WorkPath, krb5Content);
        logger.info("  Created: {}", krb5WorkPath.toAbsolutePath());
        
        // Also create in root directory for convenience
        Path krb5RootPath = Paths.get("./krb5.conf");
        Files.writeString(krb5RootPath, krb5Content);
        logger.info("  Created: {}", krb5RootPath.toAbsolutePath());
        
        logger.info("✓ krb5.conf generated successfully");
    }
    
    /**
     * Generate keytab files for service principals.
     * 
     * Each service principal will get its own keytab file in the keytabs/ directory.
     * 
     * Note: Apache Kerby's SimpleKdcServer has limited keytab export capabilities.
     * For Phase 1, we'll document the keytab location and manual export process.
     * 
     * @throws Exception if keytab generation fails
     */
    private void generateKeytabs() throws Exception {
        logger.info("Preparing keytab directory for service principals...");
        
        Path keytabDir = workDir.resolve("keytabs");
        Files.createDirectories(keytabDir);
        
        String[] servicePrincipals = config.servicePrincipals();
        if (servicePrincipals == null || servicePrincipals.length == 0) {
            logger.info("  No service principals configured");
            return;
        }
        
        // Apache Kerby's SimpleKdcServer exports all principals to a single keytab
        // We'll create one keytab with all service principals
        File keytabFile = keytabDir.resolve("service.keytab").toFile();
        
        try {
            // Export all principals to keytab (SimpleKdcServer API)
            kdc.exportPrincipals(keytabFile);
            logger.info("✓ Generated keytab: {}", keytabFile.getAbsolutePath());
            logger.info("  Contains all principals including:");
            for (String servicePrincipal : servicePrincipals) {
                logger.info("    - {}@{}", servicePrincipal, config.kerberosRealm());
            }
        } catch (Exception e) {
            logger.warn("Could not auto-generate keytab file: {}", e.getMessage());
            logger.info("  You can manually create keytabs using:");
            for (String servicePrincipal : servicePrincipals) {
                String fullPrincipal = servicePrincipal + "@" + config.kerberosRealm();
                String keytabName = servicePrincipal.replace("/", "_") + ".keytab";
                logger.info("    ktutil -k {} add -p {} -e aes256-cts -V 1", keytabName, fullPrincipal);
            }
        }
    }
    
    /**
     * Print usage instructions to the console.
     */
    private void printUsageInstructions() {
        logger.info("");
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("Kerberos KDC Ready!");
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("");
        logger.info("Test with kinit:");
        logger.info("  export KRB5_CONFIG=./krb5.conf");
        logger.info("  kinit mluser1@{}", config.kerberosRealm());
        logger.info("  (password: password)");
        logger.info("  klist");
        logger.info("");
        logger.info("Available user principals:");
        logger.info("  mluser1@{}   (password: password)", config.kerberosRealm());
        logger.info("  mluser2@{}   (password: password)", config.kerberosRealm());
        logger.info("  mluser3@{}   (password: password)", config.kerberosRealm());
        logger.info("  appreader@{}  (password: password)", config.kerberosRealm());
        logger.info("  appwriter@{}  (password: password)", config.kerberosRealm());
        logger.info("  appadmin@{}   (password: password)", config.kerberosRealm());
        logger.info("");
        logger.info("Service principals:");
        String[] servicePrincipals = config.servicePrincipals();
        if (servicePrincipals != null && servicePrincipals.length > 0) {
            for (String sp : servicePrincipals) {
                logger.info("  {}@{}", sp, config.kerberosRealm());
            }
        }
        logger.info("");
        logger.info("Keytabs location: {}", workDir.resolve("keytabs").toAbsolutePath());
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("");
    }
    
    /**
     * Stop the KDC server.
     */
    public void stop() {
        if (kdc != null && running) {
            try {
                kdc.stop();
                running = false;
                logger.info("Kerberos KDC stopped");
            } catch (KrbException e) {
                logger.error("Error stopping KDC", e);
            }
        }
    }
    
    /**
     * Check if the KDC is running.
     * 
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Get the KDC realm.
     * 
     * @return the realm name
     */
    public String getRealm() {
        return config.kerberosRealm();
    }
    
    /**
     * Get the KDC port.
     * 
     * @return the port number
     */
    public int getPort() {
        return config.kdcPort();
    }
    
    /**
     * Get the working directory.
     * 
     * @return the working directory path
     */
    public Path getWorkDir() {
        return workDir;
    }
    
    /**
     * Internal class representing a Kerberos principal.
     */
    private static class Principal {
        final String name;
        final String password;
        
        Principal(String name, String password) {
            this.name = name;
            this.password = password;
        }
    }
}
