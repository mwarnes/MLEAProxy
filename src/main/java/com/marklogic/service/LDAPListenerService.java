package com.marklogic.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.marklogic.configuration.properties.*;
import com.marklogic.handlers.NullServerSet;
import com.unboundid.ldap.listener.*;
import com.unboundid.ldap.sdk.*;
import com.unboundid.util.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.*;

/**
 * Service for managing LDAP proxy listeners.
 * Creates and configures LDAP listeners that proxy requests to backend LDAP servers,
 * supporting various server set topologies and secure connections.
 */
@Service
public class LDAPListenerService {

    private static final Logger logger = LoggerFactory.getLogger(LDAPListenerService.class);

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private MarkLogicConfigService markLogicConfigService;

    @Autowired
    private MleaProxyProperties mleaProxyProperties;

    private final List<LDAPListener> runningListeners = new ArrayList<>();

    /**
     * Starts all configured LDAP proxy listeners.
     *
     * @param config Application configuration
     * @throws Exception if listener startup fails
     */
    public void startLDAPListeners(MleaProxyProperties config) throws Exception {
        var listeners = config.getLdapListeners();
        if (listeners == null || listeners.isEmpty()) {
            logger.info("No LDAP listener configurations found");
            return;
        }

        logger.info("Starting {} LDAP listener(s)", listeners.size());

        for (String listenerName : listeners.keySet()) {
            LdapListenerProperties listenerCfg = listeners.get(listenerName);
            startListener(listenerName, listenerCfg);
        }

        logger.info("All LDAP listeners started successfully");
    }

    /**
     * Starts a single LDAP proxy listener.
     *
     * @param listenerName Name of the listener to start
     * @param listenerCfg Listener configuration properties
     * @throws Exception if listener startup fails
     */
    private void startListener(String listenerName, LdapListenerProperties listenerCfg) throws Exception {
        logger.info("Starting LDAP listener: {}", listenerName);

        // Set log level for this listener
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(LDAPListenerService.class).setLevel(Level.valueOf(listenerCfg.getDebugLevel()));

        logger.debug("IP Address: {}", listenerCfg.getIpAddress());
        logger.debug("Port: {}", listenerCfg.getPort());
        logger.debug("Request handler: {}", listenerCfg.getRequestHandler());

        // Build backend server set
        ServerSet serverSet = buildServerSet(listenerCfg.getLdapSets().toArray(new String[0]), listenerCfg.getLdapMode());
        logger.debug("ServerSet: {}", serverSet);

        // Create request handler
        LDAPListenerRequestHandler handler = createRequestHandler(
            listenerCfg.getRequestHandler(),
            serverSet,
            listenerCfg.getRequestProcessor()
        );

        // Create and start listener
        LDAPListener listener;
        if (listenerCfg.isSecure()) {
            ServerSocketFactory ssf = createServerSocketFactory(listenerCfg);
            LDAPListenerConfig listenerConfig = new LDAPListenerConfig(listenerCfg.getPort(), handler);
            listenerConfig.setServerSocketFactory(ssf);
            listener = new LDAPListener(listenerConfig);
        } else {
            LDAPListenerConfig listenerConfig = new LDAPListenerConfig(listenerCfg.getPort(), handler);
            listener = new LDAPListener(listenerConfig);
        }

        listener.startListening();
        runningListeners.add(listener);

        logger.info("LDAP listener '{}' started on {}:{} ({})",
                   listenerName, listenerCfg.getIpAddress(), listenerCfg.getPort(),
                   listenerCfg.getDescription());

        // Generate MarkLogic configuration
        try {
            markLogicConfigService.generateConfigForListener(listenerName, listenerCfg);
        } catch (IOException e) {
            logger.warn("Failed to generate MarkLogic config for listener '{}': {}",
                       listenerName, e.getMessage());
        }
    }

    /**
     * Builds a ServerSet for backend LDAP connections.
     * Supports multiple topologies: INTERNAL, SINGLE, ROUNDROBIN, FAILOVER, FASTEST, FEWEST, ROUNDROBINDNS.
     *
     * @param serverSetsList Array of server set names
     * @param mode Connection mode/topology
     * @return Configured ServerSet
     * @throws Exception if server set configuration or SSL setup fails
     */
    private ServerSet buildServerSet(String[] serverSetsList, String mode)
            throws Exception {
        logger.debug("Building server sets with mode: {}", mode);

        ArrayList<ServerSet> sets = new ArrayList<>();

        for (String setName : serverSetsList) {
            logger.debug("Configuring ServerSet: {}", setName);

            // Load server set configuration
            ServerSetProperties setsCfg = mleaProxyProperties.getLdapSets().get(setName);
            if (setsCfg == null) {
                throw new IllegalArgumentException("Server set not found: " + setName);
            }

            // Collect server addresses and ports
            List<String> hostAddresses = new ArrayList<>();
            List<Integer> hostPorts = new ArrayList<>();

            for (String serverName : setsCfg.getServers()) {
                LdapServerProperties serverCfg = mleaProxyProperties.getLdapServers().get(serverName);
                if (serverCfg == null) {
                    throw new IllegalArgumentException("LDAP server not found: " + serverName);
                }

                if (!"INTERNAL".equalsIgnoreCase(mode)) {
                    logger.debug("Backend LDAP server host: {}, port: {}",
                               serverCfg.getHost(), serverCfg.getPort());
                    hostAddresses.add(serverCfg.getHost());
                    hostPorts.add(serverCfg.getPort());
                }
            }

            // Convert lists to arrays
            String[] addresses = hostAddresses.toArray(new String[0]);
            int[] ports = hostPorts.stream().mapToInt(Integer::intValue).toArray();

            // Create ServerSet based on mode
            ServerSet serverSet = createServerSetForMode(mode, addresses, ports, setsCfg);
            sets.add(serverSet);
        }

        Validator.ensureNotNull(sets);

        // If multiple sets, wrap in FailoverServerSet for mixed connectivity
        if (sets.size() > 1) {
            return new FailoverServerSet(sets.toArray(new ServerSet[0]));
        } else {
            return sets.get(0);
        }
    }

    /**
     * Creates a ServerSet instance for the specified mode.
     */
    private ServerSet createServerSetForMode(String mode, String[] addresses, int[] ports, ServerSetProperties setsCfg)
            throws Exception {

        SSLSocketFactory sslSocketFactory = null;
        if (setsCfg.isSecure()) {
            sslSocketFactory = createSecureSocketFactory(setsCfg);
        }

        switch (mode.toUpperCase()) {
            case "INTERNAL":
                return new NullServerSet();

            case "SINGLE":
                if (sslSocketFactory != null) {
                    return new SingleServerSet(addresses[0], ports[0], sslSocketFactory);
                } else {
                    return new SingleServerSet(addresses[0], ports[0]);
                }

            case "ROUNDROBIN":
                if (sslSocketFactory != null) {
                    return new RoundRobinServerSet(addresses, ports, sslSocketFactory);
                } else {
                    return new RoundRobinServerSet(addresses, ports);
                }

            case "FAILOVER":
                if (sslSocketFactory != null) {
                    return new FailoverServerSet(addresses, ports, sslSocketFactory);
                } else {
                    return new FailoverServerSet(addresses, ports);
                }

            case "FASTEST":
                if (sslSocketFactory != null) {
                    return new FastestConnectServerSet(addresses, ports, sslSocketFactory);
                } else {
                    return new FastestConnectServerSet(addresses, ports);
                }

            case "FEWEST":
                if (sslSocketFactory != null) {
                    return new FewestConnectionsServerSet(addresses, ports, sslSocketFactory);
                } else {
                    return new FewestConnectionsServerSet(addresses, ports);
                }

            case "ROUNDROBINDNS":
                RoundRobinDNSServerSet.AddressSelectionMode selectionMode =
                    RoundRobinDNSServerSet.AddressSelectionMode.ROUND_ROBIN;
                long cacheTimeoutMillis = 3600000L; // 1 hour
                String providerURL = "dns:";

                if (sslSocketFactory != null) {
                    return new RoundRobinDNSServerSet(addresses[0], ports[0],
                        selectionMode, cacheTimeoutMillis, providerURL,
                        sslSocketFactory, null);
                } else {
                    return new RoundRobinDNSServerSet(addresses[0], ports[0],
                        selectionMode, cacheTimeoutMillis, providerURL,
                        (SocketFactory) null, null);
                }

            default:
                throw new IllegalArgumentException("Unknown server set mode: " + mode);
        }
    }

    /**
     * Creates an SSL socket factory for secure backend connections.
     */
    private SSLSocketFactory createSecureSocketFactory(ServerSetProperties cfg)
            throws Exception {
        logger.debug("Creating SSL socket factory for backend connection");

        String storeType = cfg.getStoreType();

        if ("PEM".equalsIgnoreCase(storeType)) {
            return certificateService.getSslUtilFromPem(
                cfg.getKeyPath(),
                cfg.getCertPath(),
                cfg.getCaPath()
            ).createSSLSocketFactory();
        } else if ("PFX".equalsIgnoreCase(storeType)) {
            return certificateService.getSslUtilFromPfx(
                cfg.getPfxPath(),
                cfg.getPfxPassword()
            ).createSSLSocketFactory();
        } else {
            // Default to JKS
            return certificateService.getSslUtilFromJks(
                cfg.getKeystore(),
                cfg.getKeystorePassword(),
                cfg.getTruststore(),
                cfg.getTruststorePassword()
            ).createSSLSocketFactory();
        }
    }

    /**
     * Creates a server socket factory for secure listener connections.
     */
    private ServerSocketFactory createServerSocketFactory(LdapListenerProperties cfg)
            throws Exception {
        logger.debug("Creating server socket factory for listener");

        if (cfg.getKeystore() == null || cfg.getKeystore().isEmpty() ||
            cfg.getKeystorePassword() == null || cfg.getKeystorePassword().isEmpty()) {
            throw new Exception("Unable to create secure listener without keystore and password");
        }

        return certificateService.getSslUtilFromJks(
            cfg.getKeystore(),
            cfg.getKeystorePassword(),
            cfg.getTruststore(),
            cfg.getTruststorePassword()
        ).createSSLServerSocketFactory();
    }

    /**
     * Creates a request handler instance using reflection with type validation.
     *
     * @param handlerClassName Fully qualified class name of the handler
     * @param serverSet Backend server set
     * @param processorName Name of the request processor
     * @return Instantiated request handler
     * @throws Exception if handler cannot be created
     */
    private LDAPListenerRequestHandler createRequestHandler(String handlerClassName,
                                                            ServerSet serverSet,
                                                            String processorName) throws Exception {
        try {
            // Load the class
            Class<?> clazz = Class.forName(handlerClassName);

            // Validate that it implements LDAPListenerRequestHandler interface
            if (!LDAPListenerRequestHandler.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException(
                    String.format("Class %s does not extend LDAPListenerRequestHandler", handlerClassName)
                );
            }

            // Safe cast now that we've validated the type
            Class<? extends LDAPListenerRequestHandler> handlerClass =
                clazz.asSubclass(LDAPListenerRequestHandler.class);

            // Get constructor with ServerSet and String parameters
            Constructor<? extends LDAPListenerRequestHandler> constructor =
                handlerClass.getDeclaredConstructor(ServerSet.class, String.class);

            // Create and return the instance
            LDAPListenerRequestHandler handler = constructor.newInstance(serverSet, processorName);
            logger.debug("Successfully created request handler: {}", handlerClassName);

            return handler;

        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Request handler class not found: " + handlerClassName, e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                "Request handler " + handlerClassName + " missing required constructor(ServerSet, String)", e
            );
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Failed to instantiate request handler " + handlerClassName + ": " + e.getMessage(), e
            );
        }
    }

    /**
     * Shuts down all running LDAP listeners.
     * Called during application shutdown.
     */
    public void shutdownAll() {
        logger.info("Shutting down {} LDAP listener(s)", runningListeners.size());
        for (LDAPListener listener : runningListeners) {
            try {
                listener.shutDown(true);
            } catch (Exception e) {
                logger.warn("Error shutting down LDAP listener: {}", e.getMessage());
            }
        }
        runningListeners.clear();
        logger.info("All LDAP listeners shut down");
    }
}
