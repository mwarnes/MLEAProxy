package com.marklogic.configuration.properties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the embedded Kerberos KDC.
 */
public class KerberosProperties {

    private boolean enabled = false;
    private String realm = "MARKLOGIC.LOCAL";
    private String kdcHost = "localhost";
    private int kdcPort = 60088;
    private int adminPort = 60749;
    private String workDir = "./kerberos";
    private boolean debug = false;
    
    // LDAP integration
    private boolean importPrincipalsFromLdap = true;
    private String ldapBaseDn;
    
    // Ticket configuration
    private long ticketLifetime = 86400000; // 24 hours in ms
    private long renewableLifetime = 604800000; // 7 days in ms
    private long clockSkew = 300000; // 5 minutes in ms
    
    // Service principals
    private List<String> servicePrincipals = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getKdcHost() {
        return kdcHost;
    }

    public void setKdcHost(String kdcHost) {
        this.kdcHost = kdcHost;
    }

    public int getKdcPort() {
        return kdcPort;
    }

    public void setKdcPort(int kdcPort) {
        this.kdcPort = kdcPort;
    }

    public int getAdminPort() {
        return adminPort;
    }

    public void setAdminPort(int adminPort) {
        this.adminPort = adminPort;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isImportPrincipalsFromLdap() {
        return importPrincipalsFromLdap;
    }

    public void setImportPrincipalsFromLdap(boolean importPrincipalsFromLdap) {
        this.importPrincipalsFromLdap = importPrincipalsFromLdap;
    }

    public String getLdapBaseDn() {
        return ldapBaseDn;
    }

    public void setLdapBaseDn(String ldapBaseDn) {
        this.ldapBaseDn = ldapBaseDn;
    }

    public long getTicketLifetime() {
        return ticketLifetime;
    }

    public void setTicketLifetime(long ticketLifetime) {
        this.ticketLifetime = ticketLifetime;
    }

    public long getRenewableLifetime() {
        return renewableLifetime;
    }

    public void setRenewableLifetime(long renewableLifetime) {
        this.renewableLifetime = renewableLifetime;
    }

    public long getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(long clockSkew) {
        this.clockSkew = clockSkew;
    }

    public List<String> getServicePrincipals() {
        return servicePrincipals;
    }

    public void setServicePrincipals(List<String> servicePrincipals) {
        this.servicePrincipals = servicePrincipals;
    }
}
