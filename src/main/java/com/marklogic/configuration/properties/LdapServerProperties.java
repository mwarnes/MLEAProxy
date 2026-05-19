package com.marklogic.configuration.properties;

/**
 * Configuration properties for a backend LDAP server.
 */
public class LdapServerProperties {

    private String host;
    private int port = 389;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
