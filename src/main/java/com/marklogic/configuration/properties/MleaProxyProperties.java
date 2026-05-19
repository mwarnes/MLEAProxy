package com.marklogic.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Root configuration properties for MLEAProxy.
 * All properties use the "mleaproxy" prefix.
 */
@ConfigurationProperties(prefix = "mleaproxy")
public class MleaProxyProperties {

    // Global settings
    private boolean ldapDebug = false;
    private boolean samlDebug = false;
    private boolean sslVerifyCertificates = false;
    private int samlResponseValidity = 300;
    private String samlCaPath;
    private String samlKeyPath;

    // Named configuration maps
    private Map<String, DirectoryServerProperties> directoryServers = new HashMap<>();
    private Map<String, LdapListenerProperties> ldapListeners = new HashMap<>();
    private Map<String, ServerSetProperties> ldapSets = new HashMap<>();
    private Map<String, LdapServerProperties> ldapServers = new HashMap<>();
    private Map<String, RequestProcessorProperties> requestProcessors = new HashMap<>();
    private Map<String, SamlListenerProperties> samlListeners = new HashMap<>();

    // Kerberos settings
    private KerberosProperties kerberos = new KerberosProperties();

    // Getters and Setters

    public boolean isLdapDebug() {
        return ldapDebug;
    }

    public void setLdapDebug(boolean ldapDebug) {
        this.ldapDebug = ldapDebug;
    }

    public boolean isSamlDebug() {
        return samlDebug;
    }

    public void setSamlDebug(boolean samlDebug) {
        this.samlDebug = samlDebug;
    }

    public boolean isSslVerifyCertificates() {
        return sslVerifyCertificates;
    }

    public void setSslVerifyCertificates(boolean sslVerifyCertificates) {
        this.sslVerifyCertificates = sslVerifyCertificates;
    }

    public int getSamlResponseValidity() {
        return samlResponseValidity;
    }

    public void setSamlResponseValidity(int samlResponseValidity) {
        this.samlResponseValidity = samlResponseValidity;
    }

    public String getSamlCaPath() {
        return samlCaPath;
    }

    public void setSamlCaPath(String samlCaPath) {
        this.samlCaPath = samlCaPath;
    }

    public String getSamlKeyPath() {
        return samlKeyPath;
    }

    public void setSamlKeyPath(String samlKeyPath) {
        this.samlKeyPath = samlKeyPath;
    }

    public Map<String, DirectoryServerProperties> getDirectoryServers() {
        return directoryServers;
    }

    public void setDirectoryServers(Map<String, DirectoryServerProperties> directoryServers) {
        this.directoryServers = directoryServers;
    }

    public Map<String, LdapListenerProperties> getLdapListeners() {
        return ldapListeners;
    }

    public void setLdapListeners(Map<String, LdapListenerProperties> ldapListeners) {
        this.ldapListeners = ldapListeners;
    }

    public Map<String, ServerSetProperties> getLdapSets() {
        return ldapSets;
    }

    public void setLdapSets(Map<String, ServerSetProperties> ldapSets) {
        this.ldapSets = ldapSets;
    }

    public Map<String, LdapServerProperties> getLdapServers() {
        return ldapServers;
    }

    public void setLdapServers(Map<String, LdapServerProperties> ldapServers) {
        this.ldapServers = ldapServers;
    }

    public Map<String, RequestProcessorProperties> getRequestProcessors() {
        return requestProcessors;
    }

    public void setRequestProcessors(Map<String, RequestProcessorProperties> requestProcessors) {
        this.requestProcessors = requestProcessors;
    }

    public Map<String, SamlListenerProperties> getSamlListeners() {
        return samlListeners;
    }

    public void setSamlListeners(Map<String, SamlListenerProperties> samlListeners) {
        this.samlListeners = samlListeners;
    }

    public KerberosProperties getKerberos() {
        return kerberos;
    }

    public void setKerberos(KerberosProperties kerberos) {
        this.kerberos = kerberos;
    }
}
