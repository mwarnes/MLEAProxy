package com.marklogic.handlers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

import com.marklogic.beans.SamlBean;
import com.marklogic.configuration.*;
import com.unboundid.ldap.listener.*;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldif.LDIFReader;
import com.unboundid.util.Validator;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;

import org.aeonbits.owner.ConfigFactory;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.util.io.pem.PemObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.*;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;
import org.springframework.context.annotation.Profile;

// SAML handler imports removed - using lambda handlers instead for Undertow
// import com.marklogic.handlers.undertow.B64DecodeHandler;
// import com.marklogic.handlers.undertow.B64EncodeHandler;
// import com.marklogic.handlers.undertow.SAMLAuthHandler;
// import com.marklogic.handlers.undertow.SAMLCaCertsHandler;
// import com.marklogic.handlers.undertow.SAMLWrapAssertionHandler;

/**
 * Created by mwarnes on 29/01/2017.
 */
@Component
@Profile("!test")
class Applicationlistener implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(Applicationlistener.class);

    /**
     * Expected sequence size for an RSA private key in ASN.1 format.
     * An RSA private key sequence contains: version, modulus, publicExponent,
     * privateExponent, prime1, prime2, exponent1, exponent2, and coefficient.
     */
    private static final int RSA_PRIVATE_KEY_SEQUENCE_SIZE = 9;

    @Autowired
    private SamlBean saml;

    /**
     * Main entry point for the application runner.
     * Orchestrates the initialization and startup of all application components.
     * 
     * @param args Command-line arguments passed to the application
     * @throws Exception if any initialization step fails
     */
    private KerberosKDCServer kerberosKdc;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        ApplicationConfig cfg = initializeConfiguration();
        setupSecurityProviders();
        setupLDAPDebugging(cfg);
        logApplicationArguments(args);
        startInMemoryDirectoryServers(cfg);
        startKerberosKDC(cfg);
        startLDAPListeners(cfg);
        initializeSAMLConfiguration(cfg);
    }

    /**
     * Initializes the application configuration from properties file.
     * Sets the default properties file path if not specified on command line.
     * 
     * @return Loaded ApplicationConfig instance
     */
    private ApplicationConfig initializeConfiguration() {
        // Set mleaproxy.properties System Property if not passed on the commandline.
        if (System.getProperty("mleaproxy.properties") == null) {
            System.setProperty("mleaproxy.properties", "./mleaproxy.properties");
        }
        
        ApplicationConfig cfg = ConfigFactory.create(ApplicationConfig.class);
        logger.debug("Cfg: {}", cfg);
        
        return cfg;
    }

    /**
     * Sets up security providers required for cryptographic operations.
     * Adds BouncyCastle provider for PEM file parsing and SSL operations.
     */
    private void setupSecurityProviders() {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    /**
     * Configures LDAP debugging based on application configuration.
     * Enables UnboundID LDAP SDK debug output when configured.
     * 
     * @param cfg Application configuration containing debug settings
     */
    private void setupLDAPDebugging(ApplicationConfig cfg) {
        logger.debug("ldap.debug flag: {}", cfg.ldapDebug());
        if (cfg.ldapDebug()) {
            System.setProperty("com.unboundid.ldap.sdk.debug.enabled", "true");
            System.setProperty("com.unboundid.ldap.sdk.debug.type", "ldap");
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
        logger.info("NonOptionArgs: {}", args.getNonOptionArgs());
        logger.info("OptionNames: {}", args.getOptionNames());
    }

    /**
     * Starts in-memory LDAP directory servers as configured.
     * Each server can have custom schema, LDIF data, and custom listeners.
     * 
     * @param cfg Application configuration containing directory server definitions
     * @throws Exception if server initialization or startup fails
     */
    private void startInMemoryDirectoryServers(ApplicationConfig cfg) throws Exception {
        // Start In memory Directory Server
        logger.debug("inMemory LDAP servers: {}", Arrays.toString(cfg.directoryServers()));
        if (cfg.directoryServers() == null) {
            logger.info("No inMemory LDAP servers defined.");
        } else {
            logger.info("Starting inMemory LDAP servers.");
            for (String d : cfg.directoryServers()) {
                logger.debug("directoryServer: {}", d);
                HashMap<String, String> expVars;
                expVars = new HashMap<>();
                expVars.put("directoryServer", d);
                DSConfig dsCfg = ConfigFactory
                        .create(DSConfig.class, expVars);

                InMemoryDirectoryServerConfig config =
                        new InMemoryDirectoryServerConfig(dsCfg.dsBaseDN());
                config.addAdditionalBindCredentials(dsCfg.dsAdminDN(), dsCfg.dsAdminPW());

                InetAddress addr = InetAddress.getByName(dsCfg.dsIpAddress());
                int port = dsCfg.dsPort();

                InMemoryListenerConfig dsListener = new InMemoryListenerConfig(dsCfg.dsName(), addr, port, null, null, null);

                config.setListenerConfigs(dsListener);

                logger.debug("LDIF Path empty: {}", dsCfg.dsLDIF().isEmpty());

                InMemoryDirectoryServer ds = new InMemoryDirectoryServer(config);

                if (dsCfg.dsLDIF().isEmpty()) {
                    logger.info("Using internal LDIF");
                    try (LDIFReader ldr = new LDIFReader(Objects.requireNonNull(getClass().getResourceAsStream("/marklogic.ldif")))) {
                        ds.importFromLDIF(true, ldr);
                    }
                } else {
                    logger.info("LDIF file read from override path.");
                    ds.importFromLDIF(true, dsCfg.dsLDIF());
                }
                ds.startListening();
                logger.info("Directory Server listening on: {}:{} ({})", addr, port, dsCfg.dsName());

            }
        }
    }

    /**
     * Starts the embedded Kerberos KDC (Key Distribution Center).
     * The KDC provides Kerberos authentication for testing and development.
     * 
     * Features:
     * - Auto-imports principals from in-memory LDAP
     * - Generates krb5.conf configuration file
     * - Creates service principal keytabs
     * - Localhost-optimized (no DNS requirements)
     * 
     * @param cfg Application configuration (not directly used, KerberosConfig loaded separately)
     * @throws Exception if KDC initialization or startup fails
     */
    private void startKerberosKDC(ApplicationConfig cfg) throws Exception {
        try {
            KerberosConfig krbCfg = ConfigFactory.create(KerberosConfig.class);
            
            if (krbCfg.kerberosEnabled()) {
                logger.info("Initializing Kerberos KDC...");
                kerberosKdc = new KerberosKDCServer(krbCfg);
                kerberosKdc.start();
            } else {
                logger.debug("Kerberos KDC is disabled (set kerberos.enabled=true in kerberos.properties to enable)");
            }
        } catch (Exception e) {
            logger.error("Failed to start Kerberos KDC", e);
            // Don't fail application startup if Kerberos fails
            logger.warn("Application will continue without Kerberos support");
        }
    }

    /**
     * Starts LDAP proxy listeners that forward requests to backend LDAP servers.
     * Configures both secure (TLS) and non-secure listeners with custom request handlers.
     * 
     * @param cfg Application configuration containing listener definitions
     * @throws Exception if listener initialization or startup fails
     */
    private void startLDAPListeners(ApplicationConfig cfg) throws Exception {
        // Start LDAP Listeners
        if (cfg.ldaplisteners() == null) {
            logger.info("No LDAP Listener configurations found.");
        } else {
            for (String l : cfg.ldaplisteners()) {
                logger.info("Starting LDAP listeners.");
                logger.debug("Listener: {}", l);
                HashMap<String, String> expVars = new HashMap<>();
                expVars.put("listener", l);
                LDAPListenersConfig listenerCfg = ConfigFactory
                        .create(LDAPListenersConfig.class, expVars);

                LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
                context.getLogger(Applicationlistener.class).setLevel(Level.valueOf(listenerCfg.debugLevel()));

                logger.debug("IP Address: {}", listenerCfg.listenerIpAddress());
                logger.debug("Port: {}", listenerCfg.listenerPort());
                logger.debug("Request handler: {}", listenerCfg.listenerRequestHandler());

                ServerSet serverSet = buildServerSet(listenerCfg.listenerLDAPSet(), listenerCfg.listenerLDAPMode());

                logger.debug(serverSet.toString());

                if (listenerCfg.secureListener()) {
                    Constructor<?> c = Class.forName(listenerCfg.listenerRequestHandler()).getDeclaredConstructor(ServerSet.class, String.class);
                    LDAPListenerRequestHandler mlh = (LDAPListenerRequestHandler) c.newInstance(serverSet, listenerCfg.listenerRequestProcessor());
                    LDAPListenerConfig listenerConfig = new LDAPListenerConfig(listenerCfg.listenerPort(), mlh);
                    ServerSocketFactory ssf = createServerSocketFactory(listenerCfg);
                    listenerConfig.setServerSocketFactory(ssf);
                    LDAPListener listener = new LDAPListener(listenerConfig);
                    listener.startListening();
                } else {
                    Constructor<?> c = Class.forName(listenerCfg.listenerRequestHandler()).getDeclaredConstructor(ServerSet.class, String.class);
                    LDAPListenerRequestHandler mlh = (LDAPListenerRequestHandler) c.newInstance(serverSet, listenerCfg.listenerRequestProcessor());
                    LDAPListenerConfig listenerConfig = new LDAPListenerConfig(listenerCfg.listenerPort(), mlh);
                    LDAPListener listener = new LDAPListener(listenerConfig);
                    listener.startListening();
                }

                logger.info("Listening on: {}:{} ({})", listenerCfg.listenerIpAddress(), listenerCfg.listenerPort(), listenerCfg.listenerDescription());
            }
        }
    }

    /**
     * Initializes SAML configuration for Spring Boot SAML authentication handlers.
     * Note: SAML listeners now use Spring Boot controllers (SAMLAuthHandler)
     * instead of Undertow handlers. See SAMLAuthHandler.java for implementation.
     * 
     * @param cfg Application configuration to set on the SAML bean
     */
    private void initializeSAMLConfiguration(ApplicationConfig cfg) {
        // Start SAML Listeners
        // Note: SAML listeners now use Spring Boot controllers (SAMLAuthHandler)
        // instead of Undertow handlers. See SAMLAuthHandler.java for implementation.
        saml.setCfg(cfg);
    }

    /**
     * Builds a ServerSet configuration for LDAP backend connections.
     * Supports multiple server set types including SINGLE, ROUNDROBIN, FAILOVER, FASTEST, FEWEST, and ROUNDROBINDNS.
     * Can create secure (SSL/TLS) or non-secure connections based on configuration.
     * 
     * @param serverSetsList Array of server set names to configure
     * @param mode Connection mode (INTERNAL, SINGLE, ROUNDROBIN, FAILOVER, FASTEST, FEWEST, ROUNDROBINDNS)
     * @return Configured ServerSet for LDAP connections
     * @throws GeneralSecurityException if SSL/TLS configuration fails
     * @throws IOException if server configuration cannot be read
     */
    private ServerSet buildServerSet(String[] serverSetsList, String mode) throws GeneralSecurityException, IOException {
        logger.debug("Building server sets");

        ServerSet returnSet;
        ArrayList<ServerSet> sets = new ArrayList<>();

        for (String set : serverSetsList) {
            logger.debug("ServerSet: {}", set);

            ServerSet ss= null;

            List<String> hostAddresses = new ArrayList<>();
            List<Integer> hostPorts = new ArrayList<>();

            HashMap<String, Object> setVars;
            setVars = new HashMap<>();
            setVars.put("serverSet", set);
            SetsConfig setsCfg = ConfigFactory
                    .create(SetsConfig.class, setVars);

            for (String server : setsCfg.servers()) {
                HashMap<String, String> serverVars;
                serverVars = new HashMap<>();
                serverVars.put("server", server);
                ServersConfig serverCfg = ConfigFactory
                        .create(ServersConfig.class, serverVars);

                if (!mode.equalsIgnoreCase("INTERNAL")) {
                    logger.debug("LDAP Server host: {}", serverCfg.serverHost());
                    logger.debug("LDAP Server Port: {}", serverCfg.serverPort());
                    hostAddresses.add(serverCfg.serverHost());
                    hostPorts.add(serverCfg.serverPort());
                }

            }

            // Convert Addresses and Port List to Array
            int[] intPorts = new int[hostPorts.size()];
            for (int i = 0; i < intPorts.length; i++)
                intPorts[i] = hostPorts.get(i);
            String[] strAddresses = new String[hostAddresses.size()];
            for (int i = 0; i < strAddresses.length; i++)
                strAddresses[i] = hostAddresses.get(i);

            if (mode.equalsIgnoreCase("INTERNAL")) {
                ss = new NullServerSet();
            } else if (mode.equalsIgnoreCase("SINGLE")) {
                if (setsCfg.serverSetSecure()) {
                    ss = new SingleServerSet(hostAddresses.get(0), hostPorts.get(0), createSecureSocketFactory(setsCfg));
                } else {
                    ss = new SingleServerSet(hostAddresses.get(0), hostPorts.get(0));
                }
            } else if (mode.equalsIgnoreCase("ROUNDROBIN")) {
                if (setsCfg.serverSetSecure()) {
                    ss = new RoundRobinServerSet(strAddresses, intPorts, createSecureSocketFactory(setsCfg));
                } else {
                    ss = new RoundRobinServerSet(strAddresses, intPorts);
                }
            } else if (mode.equalsIgnoreCase("FAILOVER")) {
                if (setsCfg.serverSetSecure()) {
                    ss = new FailoverServerSet(strAddresses, intPorts, createSecureSocketFactory(setsCfg));
                } else {
                    ss = new FailoverServerSet(strAddresses, intPorts);
                }
            } else if (mode.equalsIgnoreCase("FASTEST")) {
                if (setsCfg.serverSetSecure()) {
                    ss = new FastestConnectServerSet(strAddresses, intPorts, createSecureSocketFactory(setsCfg));
                } else {
                    ss = new FastestConnectServerSet(strAddresses, intPorts);
                }
            } else if (mode.equalsIgnoreCase("FEWEST")) {
                if (setsCfg.serverSetSecure()) {
                    ss = new FewestConnectionsServerSet(strAddresses, intPorts, createSecureSocketFactory(setsCfg));
                } else {
                    ss = new FewestConnectionsServerSet(strAddresses, intPorts);
                }
            } else if (mode.equalsIgnoreCase("ROUNDROBINDNS")) {
                RoundRobinDNSServerSet.AddressSelectionMode selectionMode =
                        RoundRobinDNSServerSet.AddressSelectionMode.ROUND_ROBIN;
                long cacheTimeoutMillis = 3600000L; // 1 hour
                String providerURL = "dns:";
                SocketFactory socketFactory = null;
                LDAPConnectionOptions connectionOptions = null;
                if (setsCfg.serverSetSecure()) {
                    ss = new RoundRobinDNSServerSet(hostAddresses.get(0),
                            hostPorts.get(0), selectionMode, cacheTimeoutMillis, providerURL, createSecureSocketFactory(setsCfg),
                            connectionOptions);
                } else {
                    ss = new RoundRobinDNSServerSet(hostAddresses.get(0),
                            hostPorts.get(0), selectionMode, cacheTimeoutMillis, providerURL, socketFactory,
                            connectionOptions);
                }
            }

            sets.add(ss);

        }

        Validator.ensureNotNull(sets);

        // If there is more than one set then create a FailoverServerSet from the list.
        // This will allow a user to configure a mixed set to allow greater connectivity options
        // including the ability to have secure and insecure back end connections.
        if (sets.size()>1) {
            returnSet = new FailoverServerSet(sets.toArray(new ServerSet[sets.size()]));
        } else {
            returnSet=sets.get(0);
        }

        return returnSet;
    }

    /**
     * Creates an SSL socket factory for secure LDAP connections.
     * Configures SSL/TLS settings based on the provided configuration including certificates, keys, and trust stores.
     * 
     * @param cfg Server set configuration containing SSL/TLS settings
     * @return Configured SSLSocketFactory for secure connections
     * @throws GeneralSecurityException if SSL/TLS setup fails
     * @throws IOException if certificate or key files cannot be read
     */
    private SSLSocketFactory createSecureSocketFactory(SetsConfig cfg) throws GeneralSecurityException, IOException {
        logger.debug("Creating SSL Socket Factory.");

        SSLUtil sslUtil;
        if (cfg.serverSetStoreType().equalsIgnoreCase("PEM")) {
            sslUtil = getSslUtil(cfg.serverSetKeyPath(), cfg.serverSetCertPath(), cfg.serverSetCAPath());
        } else if (cfg.serverSetStoreType().equalsIgnoreCase("PFX")) {
            sslUtil = getSslUtil(cfg.serverSetPfxPath(), cfg.serverSetPfxPassword());
        } else {
            sslUtil = getSslUtil(cfg.serverSetKeyStore(), cfg.serverSetKeyStorePassword(), cfg.serverSetTrustStore(), cfg.serverSetTrustStorePassword());
        }

        return sslUtil.createSSLSocketFactory();

    }

    /**
     * Creates a server socket factory for secure LDAP listener connections.
     * Configures SSL/TLS settings for incoming connections based on the listener configuration.
     * Requires keystore and password to be configured.
     * 
     * @param cfg LDAP listener configuration containing SSL/TLS settings
     * @return Configured ServerSocketFactory for secure server sockets
     * @throws Exception if keystore or password is not configured, or if SSL/TLS configuration fails
     */
    private ServerSocketFactory createServerSocketFactory(LDAPListenersConfig cfg) throws Exception {
        logger.debug("Creating Server Socket Factory.");

        if (cfg.listenerKeyStore().isEmpty() || cfg.listenerKeyStorePassword().isEmpty() ) {
            throw new Exception("Unable to create secure listener without keystore and password.");
        }

        SSLUtil sslUtil = getSslUtil(cfg.listenerKeyStore(),cfg.listenerKeyStorePassword(),cfg.listenerTrustStore(),cfg.listenerTrustStorePassword());

        return sslUtil.createSSLServerSocketFactory();

    }

    /**
     * Creates an SSLUtil instance from a PFX/PKCS12 keystore file.
     * Loads the keystore and configures key manager for SSL/TLS connections.
     * Uses BouncyCastle provider for PKCS12 keystore operations.
     * 
     * @param pfxpath Path to the PFX/PKCS12 keystore file
     * @param pfxpasswd Password for the PFX keystore
     * @return Configured SSLUtil instance with key manager
     * @throws NoSuchProviderException if BouncyCastle provider is unavailable
     * @throws KeyStoreException if keystore operations fail
     * @throws IOException if keystore file cannot be read
     * @throws CertificateException if certificate parsing fails
     * @throws NoSuchAlgorithmException if required algorithm is unavailable
     * @throws UnrecoverableKeyException if private key cannot be recovered
     */
    private SSLUtil getSslUtil(String pfxpath, String pfxpasswd) throws NoSuchProviderException, KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        logger.debug("Creating SSLUtil (Type=PFX).");
        SSLUtil sslUtil;

        KeyManager km = null;

        logger.debug("PFX path: {}", pfxpath);
//        logger.debug("PFX password: " + pfxpasswd);

        if (!pfxpath.isEmpty() && !pfxpasswd.isEmpty()) {
            KeyStore ks = KeyStore.getInstance("PKCS12", "BC");
            ks.load(new FileInputStream(pfxpath), pfxpasswd.toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, pfxpasswd.toCharArray());
            km = kmf.getKeyManagers()[0];
        }

        if (km!=null) {
            logger.debug("Using supplied PKCS#12 Store & TrustAllTrustManager.");
            sslUtil = new SSLUtil(km,new TrustAllTrustManager());
        } else {
            logger.debug("Using default TrustAllTrustManager.");
            sslUtil = new SSLUtil(new TrustAllTrustManager());
        }

        return sslUtil;
    }

    /**
     * Creates an SSLUtil instance from separate PEM-formatted files.
     * Parses PEM-formatted private key, certificate, and CA certificate files.
     * Supports RSA private keys with PKCS#1 format and ASN.1 sequence validation.
     * Can optionally include CA certificate for trust chain validation.
     * 
     * @param keypath Path to PEM-formatted private key file
     * @param certpath Path to PEM-formatted certificate file
     * @param capath Path to PEM-formatted CA certificate file (may be empty)
     * @return Configured SSLUtil instance with key and trust managers
     * @throws IOException if PEM files cannot be read or parsed
     * @throws KeyStoreException if keystore operations fail
     * @throws CertificateException if certificate parsing fails
     * @throws NoSuchAlgorithmException if required algorithm is unavailable
     * @throws UnrecoverableKeyException if private key cannot be recovered
     */
    private SSLUtil getSslUtil(String keypath, String certpath, String capath) throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        logger.debug("Creating SSLUtil (Type=PEM).");
        SSLUtil sslUtil;

        KeyManager km = null;

        logger.debug("Key path: {}", keypath);
        logger.debug("Cert path: {}", certpath);

        PEMParser pemParser;
        PemObject pemObject;
        KeyPair kp = null;
        ArrayList<X509Certificate> certs = new ArrayList<>();

        if (!keypath.isEmpty()) {
            InputStreamReader isr1 = new InputStreamReader(new FileInputStream(keypath));
            pemParser = new PEMParser(isr1);
            try {
                pemObject = pemParser.readPemObject();
                if (pemObject.getType().equalsIgnoreCase("RSA PRIVATE KEY")) {
                    logger.debug("Parsing RSA Private Key");
                    ASN1Sequence seq = (ASN1Sequence) ASN1Sequence.fromByteArray(pemObject.getContent());
                    if (seq.size() != RSA_PRIVATE_KEY_SEQUENCE_SIZE) {
                        logger.debug("Malformed sequence in RSA private key");
                    } else {
                        ASN1Integer mod = (ASN1Integer) seq.getObjectAt(1);
                        ASN1Integer pubExp = (ASN1Integer) seq.getObjectAt(2);
                        ASN1Integer privExp = (ASN1Integer) seq.getObjectAt(3);
                        ASN1Integer p1 = (ASN1Integer) seq.getObjectAt(4);
                        ASN1Integer p2 = (ASN1Integer) seq.getObjectAt(5);
                        ASN1Integer exp1 = (ASN1Integer) seq.getObjectAt(6);
                        ASN1Integer exp2 = (ASN1Integer) seq.getObjectAt(7);
                        ASN1Integer crtCoef = (ASN1Integer) seq.getObjectAt(8);
                        RSAPublicKeySpec pubSpec = new RSAPublicKeySpec(mod.getValue(), pubExp.getValue());
                        RSAPrivateCrtKeySpec privSpec = new RSAPrivateCrtKeySpec(mod.getValue(), pubExp.getValue(),
                                privExp.getValue(), p1.getValue(), p2.getValue(), exp1.getValue(), exp2.getValue(),
                                crtCoef.getValue());
                        KeyFactory fact = KeyFactory.getInstance("RSA", "BC");
                        kp = new KeyPair(fact.generatePublic(pubSpec), fact.generatePrivate(privSpec));
                    }
                } else {
                    logger.error("Private Key file not of type RSA Private Key: {}", keypath);
                }
            } catch (Exception e) {
                logger.error("Unable to parse Private key file: {}", keypath, e);
                throw new IllegalStateException("Failed to load private key from: " + keypath, e);
            } finally {
                if (pemParser != null) {
                    pemParser.close();
                }
            }
        }

        if (!certpath.isEmpty()) {
            InputStreamReader isr1 = new InputStreamReader(new FileInputStream(certpath));
            pemParser = new PEMParser(isr1);
            try {
                logger.debug("Parsing Certificate(s)");
                while ((pemObject = pemParser.readPemObject()) != null) {
                    if (pemObject.getType().equalsIgnoreCase("CERTIFICATE")) {
                        CertificateFactory cf = CertificateFactory.getInstance("X.509");
                        for (Certificate certificate : cf
                                .generateCertificates(new ByteArrayInputStream(pemObject.getContent()))) {
                            certs.add((X509Certificate) certificate);
                            logger.debug("Found certificate: {}", ((X509Certificate) certificate).getSubjectDN());
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Unable to parse Certificate file: {}", certpath, e);
                throw new IllegalStateException("Failed to load certificate from: " + certpath, e);
            } finally {
                if (pemParser != null) {
                    pemParser.close();
                }
            }
        }

        // If KeyPair and Certificate are both available then create a KeyStore
        if (kp != null && !certs.isEmpty()) {
            logger.debug("Creating Keystore Manager.");
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(null, null);
            ks.setKeyEntry("main", kp.getPrivate(), "654321".toCharArray(), new Certificate[] {certs.get(0)});
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, "654321".toCharArray());
            km = kmf.getKeyManagers()[0];
//            FileOutputStream JKSkeyStoreOut = new FileOutputStream("/Users/mwarnes/test.jks");
//            ks.store(JKSkeyStoreOut, "Sterling123".toCharArray());
//            JKSkeyStoreOut.close();
        }

        if (km != null) {
            logger.debug("Using supplied Certificate and Private Key & TrustAllTrustManager.");
            sslUtil = new SSLUtil(km,new TrustAllTrustManager());
        } else {
            logger.debug("Using default TrustAllTrustManager.");
            sslUtil = new SSLUtil(new TrustAllTrustManager());
        }

        return sslUtil;
    }

    private SSLUtil getSslUtil(String keystore, String keystorepw, String truststore, String truststorepw) throws GeneralSecurityException, IOException {
        logger.debug("Creating SSLUtil (Type=JKS).");
        SSLUtil sslUtil;

        KeyManager km = null;
        TrustManager tm = null;

        logger.debug("Keystore: {}", keystore);
//        logger.debug("Keystore password: " + keystorepw);
        logger.debug("Truststore: {}", truststore);
//        logger.debug("Truststore password: " + truststorepw);

        if (!keystore.isEmpty() && !keystorepw.isEmpty()) {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream(keystore), keystorepw.toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, keystorepw.toCharArray());
            km = kmf.getKeyManagers()[0];
        }

        if (!truststore.isEmpty() && !truststorepw.isEmpty()) {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream(truststore), truststorepw.toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);
            tm = tmf.getTrustManagers()[0];
        }

        if (km != null && tm == null) {
            logger.debug("No Trust managers created using defined KeyManager & TrustAllTrustManager.");
            sslUtil = new SSLUtil(km, new TrustAllTrustManager());
        } else if (km != null && tm != null) {
            logger.debug("Using configured KeyManager & TrustManager.");
            sslUtil = new SSLUtil(km, tm);
        } else if (km == null && tm != null) {
            logger.debug("Using configured TrustManager.");
            sslUtil = new SSLUtil(tm);
        } else {
            logger.debug("Using default TrustAllTrustManager.");
            sslUtil = new SSLUtil(new TrustAllTrustManager());
        }

        return sslUtil;
    }


}

