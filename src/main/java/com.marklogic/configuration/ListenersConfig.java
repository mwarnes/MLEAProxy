package com.marklogic.configuration;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.FIRST)
@Config.Sources({ "file:./mleaproxy.properties",
        "file:${HOME}/mleaproxy.properties",
        "file:/etc/mleaproxy.properties",
        "classpath:mleaproxy.properties" })
public interface ListenersConfig extends Config {

    @Key("listener.${listener}.ipaddress")
    @DefaultValue("0.0.0.0")
    String listenerIpAddress();

    @Key("listener.${listener}.port")
    int listenerPort();

    @Key("listener.${listener}.secure")
    @DefaultValue("false")
    boolean secureListener();

    @Key("listener.${listener}.debuglevel")
    @DefaultValue("INFO")
    String debugLevel();

    @Key("listener.${listener}.keystore")
    @DefaultValue("")
    String listenerKeyStore();

    @Key("listener.${listener}.keystorepasswd")
    @DefaultValue("")
    String listenerKeyStorePassword();

    /* Not currently implemented */
    @Key("listener.${listener}.truststore")
    @DefaultValue("")
    String listenerTrustStore();

    /* Not currently implemented */
    @Key("listener.${listener}.truststorepasswd")
    @DefaultValue("")
    String listenerTrustStorePassword();

    @Key("listener.${listener}.ldapset")
    @DefaultValue("")
    String[] listenerLDAPSet();

    @Key("listener.${listener}.ldapmode")
    @DefaultValue("")
    String listenerLDAPMode();

    @Key("listener.${listener}.authenticator")
    @DefaultValue("")
    String listenerAuthenticator();

    @Key("listener.${listener}.description")
    @DefaultValue("")
    String listenerDescription();

    @Key("listener.${listener}.requestHandler")
    String listenerRequestHandler();
}
