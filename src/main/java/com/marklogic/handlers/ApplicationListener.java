package com.marklogic.handlers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.marklogic.beans.saml;
import com.marklogic.configuration.*;
import com.unboundid.ldap.listener.*;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldif.LDIFReader;
import com.unboundid.util.Validator;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import io.undertow.Handlers;
import io.undertow.Undertow;
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

/**
 * Created by mwarnes on 29/01/2017.
 */
@Component
class Applicationlistener implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(Applicationlistener.class);

    @Autowired
    private saml saml;

    @Override
    public void run(ApplicationArguments args) throws Exception {

        // Set mleaproxy.properties System Property if not passed on the commandline.
        if (System.getProperty("mleaproxy.properties")==null) {
            System.setProperty("mleaproxy.properties", "./mleaproxy.properties");
        }
        ApplicationConfig cfg = ConfigFactory.create(ApplicationConfig.class);

        logger.debug("Cfg." + cfg.toString());

        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        logger.debug("ldap.debug flag: " + cfg.ldapDebug());
        if (cfg.ldapDebug()) {
            System.setProperty("com.unboundid.ldap.sdk.debug.enabled","true");
            System.setProperty("com.unboundid.ldap.sdk.debug.type","ldap");
        }

        logger.info("Application started with command-line arguments: {}", Arrays.toString(args.getSourceArgs()));
        logger.info("NonOptionArgs: {}", args.getNonOptionArgs());
        logger.info("OptionNames: {}", args.getOptionNames());


        // Start In memory Directory Server
        logger.debug("inMemory LDAP servers: " + Arrays.toString(cfg.directoryServers()));
        if (cfg.directoryServers()==null) {
            logger.info("No inMemory LDAP servers defined.");
        } else {
            logger.info("Starting inMemory LDAP servers.");
            for (String d : cfg.directoryServers()) {
                logger.debug("directoryServer: " + d);
                HashMap expVars;
                expVars = new HashMap();
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

                logger.debug("LDIF Path empty: " + dsCfg.dsLDIF().isEmpty());

                InMemoryDirectoryServer ds = new InMemoryDirectoryServer(config);

                if (dsCfg.dsLDIF().isEmpty()) {
                    logger.info("Using internal LDIF");
                    try (LDIFReader ldr = new LDIFReader(Objects.requireNonNull(ClassLoader.class.getResourceAsStream("/marklogic.ldif")))) {
                        ds.importFromLDIF(true, ldr);
                    }
                } else {
                    logger.info("LDIF file read from override path.");
                    ds.importFromLDIF(true, dsCfg.dsLDIF());
                }
                ds.startListening();
                logger.info("Directory Server listening on: " + addr + ":" + port + " ( " + dsCfg.dsName() + " )");

            }
        }

        // Start LDAP Listeners
        if (cfg.ldaplisteners()==null) {
            logger.info("No LDAP Listener configurations found.");
        } else {
            for (String l : cfg.ldaplisteners()) {
                logger.info("Starting LDAP listeners.");
                logger.debug("Listener: " + l);
                HashMap<String,String> expVars = new HashMap();
                expVars.put("listener", l);
                LDAPListenersConfig listenerCfg = ConfigFactory
                        .create(LDAPListenersConfig.class, expVars);

                LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
                context.getLogger(Applicationlistener.class).setLevel(Level.valueOf(listenerCfg.debugLevel()));

                logger.debug("IP Address: " + listenerCfg.listenerIpAddress());
                logger.debug("Port: " + listenerCfg.listenerPort());
                logger.debug("Request handler: " + listenerCfg.listenerRequestHandler());

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

                logger.info("Listening on: " + listenerCfg.listenerIpAddress() + ":" + listenerCfg.listenerPort() + " ( " + listenerCfg.listenerDescription() + " )");
            }
        }

//        // Start SAML Listeners
        saml.setCfg(cfg);
//        if (cfg.samllisteners()==null) {
//            logger.info("No SAML Listener configurations found.");
//        } else {
//            for (String l : cfg.samllisteners()) {
//                logger.info("Starting SAML listeners.");
//                logger.debug("Listener: " + l);
//                HashMap<String, String> expVars = new HashMap();
//                expVars.put("listener", l);
//                SAMLListenersConfig listenerCfg = ConfigFactory
//                        .create(SAMLListenersConfig.class, expVars);
//                saml.setListenerCfg(listenerCfg);
//
//                LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
//                context.getLogger(Applicationlistener.class).setLevel(Level.valueOf(listenerCfg.debugLevel()));
//
//
//                Undertow.builder().addHttpListener(listenerCfg.listenerPort(), listenerCfg.listenerIpAddress())
//                        .setHandler(Handlers.path()
//                                // REST API path
//                                .addPrefixPath("/saml", Handlers.routing()
//                                                .get("/auth", new SAMLAuthHandler(listenerCfg, logger))
////                                                .get("/ca", new SAMLCaCertsHandler(listenerCfg, logger))
////                                                .post("/wrapassertion",new SAMLWrapAssertionHandler(listenerCfg, logger))
////                                        .delete("/customers/{customerId}", exchange -> {...})
////                                        .setFallbackHandler(exchange -> {...})
//                                )
//                                // Redirect root path to /static to serve the index.html by default
////                                                .addExactPath("/saml/encode",new B64EncodeHandler(listenerCfg, logger))
////                                                .addExactPath("/saml/decode",new B64DecodeHandler(listenerCfg, logger))
////
////                                // Serve all static files from a folder
////                                .addPrefixPath("/static", new ResourceHandler(
////                                        new PathResourceManager(Paths.get("/path/to/www/"), 100))
////                                        .setWelcomeFiles("index.html"))
//                        ).build().start();
//            }
//        }

    }

    private ServerSet buildServerSet(String[] serverSetsList, String mode) throws GeneralSecurityException, IOException {
        logger.debug("Building server sets");

        ServerSet returnSet;
        ArrayList<ServerSet> sets = new ArrayList();

        for (String set : serverSetsList) {
            logger.debug("ServerSet: " + set);

            ServerSet ss= null;

            List<String> hostAddresses = new ArrayList<>();
            List<Integer> hostPorts = new ArrayList<>();

            HashMap setVars;
            setVars = new HashMap();
            setVars.put("serverSet", set);
            SetsConfig setsCfg = ConfigFactory
                    .create(SetsConfig.class, setVars);

            for (String server : setsCfg.servers()) {
                HashMap serverVars;
                serverVars = new HashMap();
                serverVars.put("server", server);
                ServersConfig serverCfg = ConfigFactory
                        .create(ServersConfig.class, serverVars);

                if (!mode.equalsIgnoreCase("INTERNAL")) {
                    logger.debug("LDAP Server host: " + serverCfg.serverHost());
                    logger.debug("LDAP Server Port: " + serverCfg.serverPort());
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
            }
            if (mode.equalsIgnoreCase("SINGLE")) {
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

    private ServerSocketFactory createServerSocketFactory(LDAPListenersConfig cfg) throws Exception {
        logger.debug("Creating Server Socket Factory.");

        if (cfg.listenerKeyStore().isEmpty() || cfg.listenerKeyStorePassword().isEmpty() ) {
            throw new Exception("Unable to create secure listener without keystore and password.");
        }

        SSLUtil sslUtil = getSslUtil(cfg.listenerKeyStore(),cfg.listenerKeyStorePassword(),cfg.listenerTrustStore(),cfg.listenerTrustStorePassword());

        return sslUtil.createSSLServerSocketFactory();

    }

    private SSLUtil getSslUtil(String pfxpath, String pfxpasswd) throws NoSuchProviderException, KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        logger.debug("Creating SSLUtil (Type=PFX).");
        SSLUtil sslUtil;

        KeyManager km = null;

        logger.debug("PFX path: " + pfxpath);
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

    private SSLUtil getSslUtil(String keypath, String certpath, String capath) throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        logger.debug("Creating SSLUtil (Type=PEM).");
        SSLUtil sslUtil;

        KeyManager km = null;

        logger.debug("Key path: " + keypath);
        logger.debug("Cert path: " + certpath);

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
                    if (seq.size() != 9) {
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
                    logger.error("Private Key file not of type RSA Private Key: " + keypath);
                }
            } catch (Exception e) {
                logger.error("Unable to parse Private key file: " + keypath);
                e.printStackTrace();
                System.exit(0);
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
                            logger.debug("Found certificate: " + ((X509Certificate) certificate).getSubjectDN().toString());
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Unable to parse Certificate key file: " + keypath);
                e.printStackTrace();
                System.exit(0);
            } finally {
                if (pemParser != null) {
                    pemParser.close();
                }
            }
        }

        // If KeyPair and Certificate are both available then create a KeyStore
        if (kp!=null & !certs.isEmpty()) {
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

        if (km!=null) {
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

        logger.debug("Keystore: " + keystore);
//        logger.debug("Keystore password: " + keystorepw);
        logger.debug("Truststore: " + truststore);
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

        if (km!=null & tm==null) {
            logger.debug("No Trust managers created using defined KeyManager & TrustAllTrustManager.");
            sslUtil = new SSLUtil(km,new TrustAllTrustManager());
        } else if (km!=null & tm!=null) {
            logger.debug("Using configured KeyManager & TrustManager.");
            sslUtil = new SSLUtil(km,tm);
        } else if (km==null & tm!=null) {
            logger.debug("Using configured TrustManager.");
            sslUtil = new SSLUtil(tm);
        } else {
            logger.debug("Using default TrustAllTrustManager.");
            sslUtil = new SSLUtil(new TrustAllTrustManager());
        }

        return sslUtil;
    }


}

