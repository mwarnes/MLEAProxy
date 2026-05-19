package com.marklogic.service;

import com.marklogic.configuration.properties.DirectoryServerProperties;
import com.marklogic.configuration.properties.MleaProxyProperties;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldif.LDIFException;
import com.unboundid.ldif.LDIFReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing in-memory LDAP directory servers.
 * Creates and starts UnboundID in-memory LDAP servers populated with test data.
 */
@Service
public class LDAPServerService {

    private static final Logger logger = LoggerFactory.getLogger(LDAPServerService.class);

    @Autowired
    private MarkLogicConfigService markLogicConfigService;

    private final List<InMemoryDirectoryServer> runningServers = new ArrayList<>();

    /**
     * Starts all configured in-memory LDAP directory servers.
     *
     * @param config Application configuration
     * @throws Exception if server startup fails
     */
    public void startInMemoryServers(MleaProxyProperties config) throws Exception {
        var directoryServers = config.getDirectoryServers();
        if (directoryServers == null || directoryServers.isEmpty()) {
            logger.info("No in-memory LDAP directory servers configured");
            return;
        }
        
        logger.info("Starting {} in-memory LDAP directory server(s)", directoryServers.size());

        for (String serverName : directoryServers.keySet()) {
            DirectoryServerProperties dsConfig = directoryServers.get(serverName);
            startInMemoryServer(serverName, dsConfig);
        }

        logger.info("All in-memory LDAP servers started successfully");
    }

    /**
     * Starts a single in-memory LDAP directory server.
     *
     * @param serverName Name of the server to start
     * @param dsConfig Directory server configuration
     * @throws Exception if server startup fails
     */
    private void startInMemoryServer(String serverName, DirectoryServerProperties dsConfig) throws Exception {
        logger.info("Configuring in-memory LDAP server: {}", serverName);

        // Create directory server configuration
        DN baseDN = new DN(dsConfig.getBaseDn());
        InMemoryDirectoryServerConfig serverConfig = new InMemoryDirectoryServerConfig(baseDN);

        // Set admin credentials
        serverConfig.addAdditionalBindCredentials(dsConfig.getAdminDn(), dsConfig.getAdminPassword());

        // Configure listener
        String ipAddress = dsConfig.getIpAddress();
        int port = dsConfig.getPort();
        InetAddress listenAddress = InetAddress.getByName(ipAddress);
        InMemoryListenerConfig listenerConfig = InMemoryListenerConfig.createLDAPConfig(
            serverName,
            listenAddress,
            port,
            null  // No SSL for in-memory servers (testing tool)
        );
        serverConfig.setListenerConfigs(listenerConfig);

        // Create and start the server
        InMemoryDirectoryServer server = new InMemoryDirectoryServer(serverConfig);

        // Import LDIF data
        importLDIFData(server, dsConfig);

        // Start the server
        server.startListening();
        runningServers.add(server);

        logger.info("In-memory LDAP server '{}' started on {}:{} with base DN '{}'",
                   serverName, ipAddress, port, dsConfig.getBaseDn());

        // Generate MarkLogic configuration
        try {
            markLogicConfigService.generateConfigForInMemoryServer(
                serverName, ipAddress, port, dsConfig.getBaseDn()
            );
        } catch (IOException e) {
            logger.warn("Failed to generate MarkLogic config for server '{}': {}",
                       serverName, e.getMessage());
        }
    }

    /**
     * Imports LDIF data into the directory server.
     *
     * @param server Directory server instance
     * @param dsConfig Server configuration
     * @throws LDAPException if LDIF import fails
     * @throws IOException if LDIF file cannot be read
     */
    private void importLDIFData(InMemoryDirectoryServer server, DirectoryServerProperties dsConfig)
            throws LDAPException, IOException {
        String ldifPath = dsConfig.getLdifPath();

        if (ldifPath != null && !ldifPath.trim().isEmpty()) {
            // Load from file system
            logger.info("Loading LDIF from override path: {}", ldifPath);
            server.importFromLDIF(false, ldifPath);
            logger.info("Successfully imported LDIF from: {}", ldifPath);
        } else {
            // Load from classpath
            String classpathLdif = "/marklogic.ldif";
            logger.info("Loading LDIF from classpath: {}", classpathLdif);

            try (InputStream ldifStream = getClass().getResourceAsStream(classpathLdif)) {
                if (ldifStream == null) {
                    throw new IOException("LDIF file not found in classpath: " + classpathLdif);
                }

                try (LDIFReader ldifReader = new LDIFReader(ldifStream)) {
                    server.importFromLDIF(false, ldifReader);
                    logger.info("Successfully imported LDIF from classpath: {}", classpathLdif);
                }
            }
        }
    }

    /**
     * Shuts down all running in-memory LDAP servers.
     * Called during application shutdown.
     */
    public void shutdownAll() {
        logger.info("Shutting down {} in-memory LDAP server(s)", runningServers.size());
        for (InMemoryDirectoryServer server : runningServers) {
            try {
                server.shutDown(true);
            } catch (Exception e) {
                logger.warn("Error shutting down LDAP server: {}", e.getMessage());
            }
        }
        runningServers.clear();
        logger.info("All in-memory LDAP servers shut down");
    }
}
