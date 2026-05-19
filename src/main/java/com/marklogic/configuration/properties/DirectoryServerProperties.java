package com.marklogic.configuration.properties;

/**
 * Configuration properties for an in-memory LDAP directory server.
 */
public class DirectoryServerProperties {

    private String name;
    private String ipAddress = "0.0.0.0";
    private int port = 60389;
    private String baseDn;
    private String adminDn;
    private String adminPassword;
    private String ldifFile;
    private String ldifPath;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

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

    public String getBaseDn() {
        return baseDn;
    }

    public void setBaseDn(String baseDn) {
        this.baseDn = baseDn;
    }

    public String getAdminDn() {
        return adminDn;
    }

    public void setAdminDn(String adminDn) {
        this.adminDn = adminDn;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getLdifFile() {
        return ldifFile;
    }

    public void setLdifFile(String ldifFile) {
        this.ldifFile = ldifFile;
    }

    public String getLdifPath() {
        return ldifPath;
    }

    public void setLdifPath(String ldifPath) {
        this.ldifPath = ldifPath;
    }
}
