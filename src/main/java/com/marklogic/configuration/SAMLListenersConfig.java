package com.marklogic.configuration;

import org.aeonbits.owner.Config;
import org.springframework.stereotype.Component;

@Config.LoadPolicy(Config.LoadType.FIRST)
@Config.Sources({ "file:${mleaproxy.properties}",
        "file:./mleaproxy.properties",
        "file:${HOME}/mleaproxy.properties",
        "file:/etc/mleaproxy.properties",
        "classpath:mleaproxy.properties" })
public interface SAMLListenersConfig extends Config {

    @Key("samllistener.${listener}.ipaddress")
    @DefaultValue("0.0.0.0")
    String listenerIpAddress();

    @Key("samllistener.${listener}.port")
    int listenerPort();

//    @Key("samllistener.${listener}.secure")
//    @DefaultValue("false")
//    boolean secureListener();

    @Key("samllistener.${listener}.debuglevel")
    @DefaultValue("INFO")
    String debugLevel();

//    @Key("samllistener.${listener}.keystore")
//    @DefaultValue("")
//    String listenerKeyStore();
//
//    @Key("samllistener.${listener}.keystorepasswd")
//    @DefaultValue("")
//    String listenerKeyStorePassword();
//
//    /* Not currently implemented */
//    @Key("samllistener.${listener}.truststore")
//    @DefaultValue("")
//    String listenerTrustStore();
//
//    /* Not currently implemented */
//    @Key("samllistener.${listener}.truststorepasswd")
//    @DefaultValue("")
//    String listenerTrustStorePassword();
//
//    @Key("samllistener.${listener}.requestProcessor")
//    @DefaultValue("")
//    String listenerRequestProcessor();
//
    @Key("samllistener.${listener}.description")
    @DefaultValue("")
    String listenerDescription();
//
//    @Key("samllistener.${listener}.requestHandler")
//    @DefaultValue("com.marklogic.handlers.SAMLRequestHandler")
//    String listenerRequestHandler();
}
