package com.marklogic.configuration;

import org.aeonbits.owner.Config;

/**
 * Configuration interface for Kerberos KDC (Key Distribution Center).
 * 
 * This configuration controls the embedded Apache Kerby KDC server that provides
 * Kerberos authentication for testing and development purposes.
 * 
 * Configuration sources (in order of precedence):
 * 1. System properties (-Dkerberos.enabled=true)
 * 2. ${mleaproxy.properties} system property file
 * 3. ./mleaproxy.properties
 * 4. ${HOME}/mleaproxy.properties
 * 5. /etc/mleaproxy.properties
 * 6. classpath:mleaproxy.properties
 * 7. ./kerberos.properties
 * 
 * @see org.apache.kerby.kerberos.kerb.server.SimpleKdcServer
 * @since 2.0.0
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({ 
    "system:properties",
    "file:${mleaproxy.properties}",
    "file:./mleaproxy.properties",
    "file:${HOME}/mleaproxy.properties",
    "file:/etc/mleaproxy.properties",
    "classpath:mleaproxy.properties",
    "file:./kerberos.properties"
})
public interface KerberosConfig extends Config {

    /**
     * Enable or disable the Kerberos KDC server.
     * 
     * When enabled, the KDC will start on application startup and create
     * principals from the in-memory LDAP server.
     * 
     * @return true to enable KDC, false to disable
     */
    @Key("kerberos.enabled")
    @DefaultValue("false")
    boolean kerberosEnabled();

    /**
     * The Kerberos realm name (typically UPPERCASE by convention).
     * 
     * All principals will be created in this realm.
     * Example: MARKLOGIC.LOCAL
     * 
     * @return the Kerberos realm name
     */
    @Key("kerberos.realm")
    @DefaultValue("MARKLOGIC.LOCAL")
    String kerberosRealm();

    /**
     * The hostname or IP address for the KDC to bind to.
     * 
     * Use "localhost" for local testing, or "0.0.0.0" to bind to all interfaces.
     * 
     * @return the KDC host
     */
    @Key("kerberos.kdc.host")
    @DefaultValue("localhost")
    String kdcHost();

    /**
     * The port number for the KDC to listen on.
     * 
     * Standard KDC port is 88, but we use 60088 to avoid requiring root/admin privileges.
     * 
     * @return the KDC port number
     */
    @Key("kerberos.kdc.port")
    @DefaultValue("60088")
    int kdcPort();

    /**
     * The port number for Kadmin (administrative operations).
     * 
     * Standard kadmin port is 749, but we use 60749 to avoid requiring root/admin privileges.
     * 
     * @return the kadmin port number
     */
    @Key("kerberos.admin.port")
    @DefaultValue("60749")
    int adminPort();

    /**
     * Import user principals from the in-memory LDAP server.
     * 
     * When enabled, all users in the LDAP directory will be created as Kerberos principals
     * with synchronized passwords.
     * 
     * @return true to import from LDAP, false to skip
     */
    @Key("kerberos.principals.import-from-ldap")
    @DefaultValue("true")
    boolean importPrincipalsFromLdap();

    /**
     * The LDAP base DN to search for user principals.
     * 
     * This should match the base DN of your in-memory LDAP server.
     * 
     * @return the LDAP base DN
     */
    @Key("kerberos.principals.ldap-base-dn")
    @DefaultValue("dc=MarkLogic,dc=Local")
    String ldapBaseDn();

    /**
     * Array of service principals to create (comma-separated).
     * 
     * Format: service/hostname (realm will be appended automatically)
     * Example: HTTP/localhost,ldap/localhost
     * 
     * Keytab files will be generated for each service principal.
     * 
     * @return array of service principal names
     */
    @Key("kerberos.service-principals")
    @DefaultValue("HTTP/localhost,ldap/localhost")
    String[] servicePrincipals();

    /**
     * Working directory for Kerberos files.
     * 
     * The following files will be created in this directory:
     * - krb5.conf (Kerberos client configuration)
     * - keytabs/*.keytab (Service principal keytab files)
     * - Backend database files
     * 
     * @return the working directory path
     */
    @Key("kerberos.work-dir")
    @DefaultValue("./kerberos")
    String workDir();

    /**
     * Enable detailed debug logging for Kerberos operations.
     * 
     * Useful for troubleshooting authentication issues.
     * 
     * @return true to enable debug logging, false for normal logging
     */
    @Key("kerberos.debug")
    @DefaultValue("false")
    boolean debug();

    /**
     * Ticket lifetime in seconds.
     * 
     * How long tickets remain valid after being issued.
     * Default: 36000 seconds (10 hours)
     * 
     * @return ticket lifetime in seconds
     */
    @Key("kerberos.ticket.lifetime")
    @DefaultValue("36000")
    int ticketLifetime();

    /**
     * Renewable ticket lifetime in seconds.
     * 
     * How long tickets can be renewed before requiring re-authentication.
     * Default: 604800 seconds (7 days)
     * 
     * @return renewable lifetime in seconds
     */
    @Key("kerberos.ticket.renewable-lifetime")
    @DefaultValue("604800")
    int renewableLifetime();

    /**
     * Clock skew tolerance in seconds.
     * 
     * Maximum acceptable time difference between client and KDC.
     * Default: 300 seconds (5 minutes)
     * 
     * @return clock skew tolerance in seconds
     */
    @Key("kerberos.clock-skew")
    @DefaultValue("300")
    int clockSkew();
}
