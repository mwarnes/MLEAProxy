package com.marklogic.configuration;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.FIRST)
@Config.Sources({ "file:${mleaproxy.properties}",
        "file:./mleaproxy.properties",
        "file:${HOME}/mleaproxy.properties",
        "file:/etc/mleaproxy.properties",
        "classpath:mleaproxy.properties" })
public interface LDAPListenersConfig extends Config {

    @Key("ldaplistener.${listener}.ipaddress")
    @DefaultValue("0.0.0.0")
    String listenerIpAddress();

    @Key("ldaplistener.${listener}.port")
    int listenerPort();

    @Key("ldaplistener.${listener}.secure")
    @DefaultValue("false")
    boolean secureListener();

    @Key("ldaplistener.${listener}.debuglevel")
    @DefaultValue("INFO")
    String debugLevel();

    @Key("ldaplistener.${listener}.keystore")
    @DefaultValue("")
    String listenerKeyStore();

    @Key("ldaplistener.${listener}.keystorepasswd")
    @DefaultValue("")
    String listenerKeyStorePassword();

    /* Not currently implemented */
    @Key("ldaplistener.${listener}.truststore")
    @DefaultValue("")
    String listenerTrustStore();

    /* Not currently implemented */
    @Key("ldaplistener.${listener}.truststorepasswd")
    @DefaultValue("")
    String listenerTrustStorePassword();

    @Key("ldaplistener.${listener}.ldapset")
    @DefaultValue("null")
    String[] listenerLDAPSet();

    @Key("ldaplistener.${listener}.ldapmode")
    @DefaultValue("internal")
    String listenerLDAPMode();

    @Key("ldaplistener.${listener}.requestProcessor")
    @DefaultValue("")
    String listenerRequestProcessor();

    @Key("ldaplistener.${listener}.description")
    @DefaultValue("")
    String listenerDescription();

    @Key("ldaplistener.${listener}.requestHandler")
    @DefaultValue("com.marklogic.handlers.LDAPRequestHandler")
    String listenerRequestHandler();
}
