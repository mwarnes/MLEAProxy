package com.marklogic.configuration.properties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for an LDAP proxy listener.
 */
public class LdapListenerProperties {

    private String ipAddress = "0.0.0.0";
    private int port = 10389;
    private boolean secure = false;
    private String debugLevel = "INFO";
    private String ldapMode = "internal";
    private List<String> ldapSets = new ArrayList<>();
    private String requestProcessor;
    private String requestHandler;
    private String description;
    
    // Keystore-based SSL
    private String keystore;
    private String keystorePath;
    private String keystorePassword;
    private String keystoreType = "PKCS12";
    private String truststore;
    private String truststorePath;
    private String truststorePassword;

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getDebugLevel() {
        return debugLevel;
    }

    public void setDebugLevel(String debugLevel) {
        this.debugLevel = debugLevel;
    }

    public String getLdapMode() {
        return ldapMode;
    }

    public void setLdapMode(String ldapMode) {
        this.ldapMode = ldapMode;
    }

    public List<String> getLdapSets() {
        return ldapSets;
    }

    public void setLdapSets(List<String> ldapSets) {
        this.ldapSets = ldapSets;
    }

    public String getRequestProcessor() {
        return requestProcessor;
    }

    public void setRequestProcessor(String requestProcessor) {
        this.requestProcessor = requestProcessor;
    }

    public String getRequestHandler() {
        return requestHandler;
    }

    public void setRequestHandler(String requestHandler) {
        this.requestHandler = requestHandler;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getKeystore() {
        return keystore;
    }

    public void setKeystore(String keystore) {
        this.keystore = keystore;
    }

    public String getKeystorePath() {
        return keystorePath;
    }

    public void setKeystorePath(String keystorePath) {
        this.keystorePath = keystorePath;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public String getKeystoreType() {
        return keystoreType;
    }

    public void setKeystoreType(String keystoreType) {
        this.keystoreType = keystoreType;
    }

    public String getTruststore() {
        return truststore;
    }

    public void setTruststore(String truststore) {
        this.truststore = truststore;
    }

    public String getTruststorePath() {
        return truststorePath;
    }

    public void setTruststorePath(String truststorePath) {
        this.truststorePath = truststorePath;
    }

    public String getTruststorePassword() {
        return truststorePassword;
    }

    public void setTruststorePassword(String truststorePassword) {
        this.truststorePassword = truststorePassword;
    }
}
