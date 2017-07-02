package com.marklogic.configuration;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.FIRST)
@Config.Sources({ "file:${mleaproxy.properties}",
        "file:./mleaproxy.properties",
        "file:${HOME}/mleaproxy.properties",
        "file:/etc/mleaproxy.properties",
        "classpath:mleaproxy.properties" })
public interface ServersConfig extends Config {

    @Key("ldapserver.${server}.host")
    @DefaultValue("")
    String serverHost();

    @Key("ldapserver.${server}.port")
    int serverPort();

    @Key("ldapserver.${server}.authtype")
    @DefaultValue("simple")
    int serverAuthType();

}

