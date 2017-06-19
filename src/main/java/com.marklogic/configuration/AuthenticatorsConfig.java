package com.marklogic.configuration;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.FIRST)
@Config.Sources({ "file:./mleaproxy.properties",
        "file:${HOME}/mleaproxy.properties",
        "file:/etc/mleaproxy.properties",
        "classpath:mleaproxy.properties" })
public interface AuthenticatorsConfig extends Config {

    @Key("authenticator.${authenticator}.authclass")
    String authenticatorClass();

    @Key("authenticator.${authenticator}.configclass")
    String authenticatorConfigClass();

    @Key("authenticator.${authenticator}.separator")
    @DefaultValue(",")
    String authenticatorSeperator();
}

