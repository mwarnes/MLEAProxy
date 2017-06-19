package com.marklogic.configuration;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.FIRST)
@Config.Sources({ "file:./mleaproxy.properties",
        "file:${HOME}/mleaproxy.properties",
        "file:/etc/mleaproxy.properties",
        "classpath:mleaproxy.properties" })
public interface ApplicationConfig extends Config {

    @Key("listeners")
    public String[] listeners();

    @Key("ldap.debug")
    @DefaultValue("false")
    boolean ldapDebug();

}

