package com.marklogic.configuration;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.FIRST)
@Config.Sources({ "file:${mleaproxy.properties}",
        "file:./mleaproxy.properties",
        "file:${HOME}/mleaproxy.properties",
        "file:/etc/mleaproxy.properties",
        "classpath:mleaproxy.properties" })
public interface DSConfig extends Config {

    @Key("ds.${directoryServer}.name")
    String dsName();

    @Key("ds.${directoryServer}.ipaddress")
    @DefaultValue("0.0.0.0")
    String dsIpAddress();

    @Key("ds.${directoryServer}.port")
    @DefaultValue("60389")
    int dsPort();

    @Key("ds.${directoryServer}.basedn")
    @DefaultValue("dc=MarkLogic,dc=Local")
    String dsBaseDN();

    @Key("ds.${directoryServer}.admindn")
    @DefaultValue("cn=Directory Manager")
    String dsAdminDN();

    @Key("ds.${directoryServer}.adminpw")
    @DefaultValue("password")
    String dsAdminPW();

    @Key("ds.${directoryServer}.ldifpath")
    @DefaultValue("")
    String dsLDIF();
}

