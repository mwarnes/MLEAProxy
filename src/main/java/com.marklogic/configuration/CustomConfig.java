package com.marklogic.configuration;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.FIRST)
@Config.Sources({ "file:./mleaproxy.properties",
        "file:${HOME}/mleaproxy.properties",
        "file:/etc/mleaproxy.properties",
        "classpath:mleaproxy.properties" })
public interface CustomConfig extends Config {

    @Key("authenticator.${authenticator}.parm1")
    @DefaultValue("")
    String parm1();

    @Key("authenticator.${authenticator}.parm2")
    @DefaultValue("")
    String parm2();

    @Key("authenticator.${authenticator}.parm3")
    @DefaultValue("")
    String parm3();

    @Key("authenticator.${authenticator}.parm4")
    @DefaultValue("")
    String parm4();

    @Key("authenticator.${authenticator}.parm5")
    @DefaultValue("")
    String parm5();

    @Key("authenticator.${authenticator}.parm6")
    @DefaultValue("")
    String parm6();

    @Key("authenticator.${authenticator}.parm7")
    @DefaultValue("")
    String parm7();

    @Key("authenticator.${authenticator}.parm8")
    @DefaultValue("")
    String parm8();

    @Key("authenticator.${authenticator}.parm9")
    @DefaultValue("")
    String parm9();

    @Key("authenticator.${authenticator}.parm610")
    @DefaultValue("")
    String parm10();

}
