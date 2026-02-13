package com.marklogic.service;

import com.marklogic.configuration.ApplicationConfig;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.util.io.pem.PemObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;

/**
 * Service for loading and configuring SSL/TLS certificates from various formats.
 * Supports PFX/PKCS12, PEM, and JKS certificate formats.
 *
 * This service is used by LDAP proxy listeners and directory servers to establish
 * secure TLS connections.
 */
@Service
public class CertificateService {

    private static final Logger logger = LoggerFactory.getLogger(CertificateService.class);

    /**
     * Expected sequence size for an RSA private key in ASN.1 format.
     * An RSA private key sequence contains: version, modulus, publicExponent,
     * privateExponent, prime1, prime2, exponent1, exponent2, and coefficient.
     */
    private static final int RSA_PRIVATE_KEY_SEQUENCE_SIZE = 9;

    /**
     * Creates a trust manager based on application configuration.
     *
     * @param config Application configuration
     * @return TrustAllTrustManager if certificate verification is disabled,
     *         system default trust manager otherwise
     */
    public TrustManager createConfiguredTrustManager(ApplicationConfig config) {
        if (!config.sslVerifyCertificates()) {
            logger.info("SSL certificate verification disabled - using TrustAllTrustManager (testing only)");
            return new TrustAllTrustManager();
        } else {
            logger.info("SSL certificate verification enabled - using system default trust manager");
            try {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init((KeyStore) null);
                TrustManager[] trustManagers = tmf.getTrustManagers();
                return trustManagers.length > 0 ? trustManagers[0] : new TrustAllTrustManager();
            } catch (Exception e) {
                logger.warn("Failed to initialize system trust manager, falling back to TrustAllTrustManager", e);
                return new TrustAllTrustManager();
            }
        }
    }

    /**
     * Creates an SSLUtil instance from a PFX/PKCS12 keystore file.
     *
     * @param pfxPath Path to the PFX/PKCS12 file
     * @param pfxPassword Password for the PFX file
     * @return SSLUtil configured with the keystore
     * @throws GeneralSecurityException if keystore loading fails
     * @throws IOException if file access fails
     */
    public SSLUtil getSslUtilFromPfx(String pfxPath, String pfxPassword)
            throws GeneralSecurityException, IOException {
        logger.debug("Loading PFX keystore from: {}", pfxPath);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(pfxPath)) {
            keyStore.load(fis, pfxPassword.toCharArray());
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, pfxPassword.toCharArray());

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        logger.info("Successfully loaded PFX certificate from: {}", pfxPath);
        return new SSLUtil(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers());
    }

    /**
     * Creates an SSLUtil instance from PEM-formatted certificate and key files.
     *
     * @param keyPath Path to the PEM private key file
     * @param certPath Path to the PEM certificate file
     * @param caPath Path to the PEM CA certificate file (optional, can be null)
     * @return SSLUtil configured with the PEM certificates
     * @throws Exception if certificate loading or parsing fails
     */
    public SSLUtil getSslUtilFromPem(String keyPath, String certPath, String caPath) throws Exception {
        logger.debug("Loading PEM certificates - key: {}, cert: {}, ca: {}", keyPath, certPath, caPath);

        // Parse RSA private key from PEM
        KeyPair keyPair;
        try (FileInputStream fis = new FileInputStream(keyPath);
             InputStreamReader isr = new InputStreamReader(fis);
             PEMParser pemParser = new PEMParser(isr)) {

            PemObject pemObject = pemParser.readPemObject();
            byte[] keyBytes = pemObject.getContent();

            // Parse ASN.1 sequence
            ASN1Sequence sequence = ASN1Sequence.getInstance(keyBytes);
            if (sequence.size() != RSA_PRIVATE_KEY_SEQUENCE_SIZE) {
                throw new IllegalArgumentException(
                    "Invalid RSA private key format - expected " + RSA_PRIVATE_KEY_SEQUENCE_SIZE +
                    " elements in ASN.1 sequence, got " + sequence.size()
                );
            }

            // Extract RSA key components from PKCS#1 format
            ASN1Integer modulus = (ASN1Integer) sequence.getObjectAt(1);
            ASN1Integer publicExponent = (ASN1Integer) sequence.getObjectAt(2);
            ASN1Integer privateExponent = (ASN1Integer) sequence.getObjectAt(3);
            ASN1Integer prime1 = (ASN1Integer) sequence.getObjectAt(4);
            ASN1Integer prime2 = (ASN1Integer) sequence.getObjectAt(5);
            ASN1Integer exponent1 = (ASN1Integer) sequence.getObjectAt(6);
            ASN1Integer exponent2 = (ASN1Integer) sequence.getObjectAt(7);
            ASN1Integer coefficient = (ASN1Integer) sequence.getObjectAt(8);

            // Create RSA key specs
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(
                modulus.getValue(),
                publicExponent.getValue()
            );
            RSAPrivateCrtKeySpec privateKeySpec = new RSAPrivateCrtKeySpec(
                modulus.getValue(),
                publicExponent.getValue(),
                privateExponent.getValue(),
                prime1.getValue(),
                prime2.getValue(),
                exponent1.getValue(),
                exponent2.getValue(),
                coefficient.getValue()
            );

            // Generate key pair
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
            keyPair = new KeyPair(publicKey, privateKey);
        }

        // Load X.509 certificate
        Certificate certificate;
        try (FileInputStream fis = new FileInputStream(certPath)) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            certificate = certFactory.generateCertificate(fis);
        }

        // Load CA certificate if provided
        Certificate caCertificate = null;
        if (caPath != null && !caPath.trim().isEmpty()) {
            try (FileInputStream fis = new FileInputStream(caPath)) {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                caCertificate = certFactory.generateCertificate(fis);
            }
        }

        // Create in-memory keystore
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);

        // Add certificate chain
        Certificate[] certChain;
        if (caCertificate != null) {
            certChain = new Certificate[]{certificate, caCertificate};
            keyStore.setCertificateEntry("ca", caCertificate);
        } else {
            certChain = new Certificate[]{certificate};
        }
        keyStore.setKeyEntry("key", keyPair.getPrivate(), "".toCharArray(), certChain);

        // Create SSL managers
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, "".toCharArray());

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        logger.info("Successfully loaded PEM certificates - key: {}, cert: {}", keyPath, certPath);
        return new SSLUtil(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers());
    }

    /**
     * Creates an SSLUtil instance from JKS keystore and truststore files.
     *
     * @param keystorePath Path to the JKS keystore file
     * @param keystorePassword Password for the keystore
     * @param truststorePath Path to the JKS truststore file (optional, can be null)
     * @param truststorePassword Password for the truststore
     * @return SSLUtil configured with the keystores
     * @throws GeneralSecurityException if keystore loading fails
     * @throws IOException if file access fails
     */
    public SSLUtil getSslUtilFromJks(String keystorePath, String keystorePassword,
                                     String truststorePath, String truststorePassword)
            throws GeneralSecurityException, IOException {
        logger.debug("Loading JKS keystore: {}, truststore: {}", keystorePath, truststorePath);

        // Load keystore
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, keystorePassword.toCharArray());
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keystorePassword.toCharArray());

        // Load truststore (optional)
        TrustManager[] trustManagers;
        if (truststorePath != null && !truststorePath.trim().isEmpty()) {
            KeyStore trustStore = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(truststorePath)) {
                trustStore.load(fis, truststorePassword.toCharArray());
            }

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            trustManagers = trustManagerFactory.getTrustManagers();
        } else {
            trustManagers = new TrustManager[]{new TrustAllTrustManager()};
        }

        logger.info("Successfully loaded JKS keystore from: {}", keystorePath);
        return new SSLUtil(keyManagerFactory.getKeyManagers(), trustManagers);
    }
}
