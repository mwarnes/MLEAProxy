package com.marklogic.configuration;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.FIRST)
@Config.Sources({ "file:./mleaproxy.properties",
        "file:${HOME}/mleaproxy.properties",
        "file:/etc/mleaproxy.properties",
        "classpath:mleaproxy.properties" })
public interface MLGoogleConfig extends Config {

    @Key("authenticator.${authenticator}.mlhost")
    @DefaultValue("localhost")
    String mlHost();

    @Key("authenticator.${authenticator}.mlport")
    @DefaultValue("8000")
    int mlPort();

    @Key("authenticator.${authenticator}.mluser")
    @DefaultValue("")
    String mlUser();

    @Key("authenticator.${authenticator}.mlpassword")
    @DefaultValue("")
    String mlPassword();

    @Key("authenticator.${authenticator}.mldb")
    @DefaultValue("")
    String mlDB();

    @Key("authenticator.${authenticator}.mluri_format")
    @DefaultValue("")
    String mlUriFormat();

    @Key("authenticator.${authenticator}.admin_dn")
    @DefaultValue("")
    String mlAdminDn();

    @Key("authenticator.${authenticator}.base_dn")
    @DefaultValue("")
    String mlBaseDn();

    @Key("authenticator.${authenticator}.userid_attribute")
    @DefaultValue("")
    String mlUseridAttribute();

    @Key("authenticator.${authenticator}.separator")
    @DefaultValue("")
    String mlSeparator();

    @Key("authenticator.${authenticator}.allow_if_down")
    @DefaultValue("false")
    boolean allowIfDown();
}
