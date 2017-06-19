package com.marklogic.configuration;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.FIRST)
@Config.Sources({ "file:./mleaproxy.properties",
        "file:${HOME}/mleaproxy.properties",
        "file:/etc/mleaproxy.properties",
        "classpath:mleaproxy.properties" })
public interface DuoConfig extends Config {

    @Key("authenticator.${authenticator}.host")
    @DefaultValue("")
    String duoHost();

    @Key("authenticator.${authenticator}.ikey")
    @DefaultValue("")
    String duoIkey();

    @Key("authenticator.${authenticator}.skey")
    @DefaultValue("")
    String duoSkey();

    @Key("authenticator.${authenticator}.admin_dn")
    @DefaultValue("")
    String duoAdminDn();

    @Key("authenticator.${authenticator}.base_dn")
    @DefaultValue("")
    String duoBaseDn();

    @Key("authenticator.${authenticator}.userid_attribute")
    @DefaultValue("")
    String duoUseridAttribute();

    @Key("authenticator.${authenticator}.separator")
    @DefaultValue("")
    String duoSeparator();

    @Key("authenticator.${authenticator}.allow_if_down")
    @DefaultValue("false")
    boolean allowIfDown();

    @Key("authenticator.${authenticator}.enableCredentialsCheck")
    @DefaultValue("false")
    boolean enableCredentialsCheck();
}
