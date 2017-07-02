package com.marklogic.configuration;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.FIRST)
@Config.Sources({ "file:${mleaproxy.properties}",
        "file:./mleaproxy.properties",
        "file:${HOME}/mleaproxy.properties",
        "file:/etc/mleaproxy.properties",
        "classpath:mleaproxy.properties" })
public interface ProcessorConfig extends Config {

    @Key("requestProcessor.${requestProcessor}.authclass")
    @DefaultValue("com.marklogic.processors.XMLRequestProcessorg")
    String requestProcessorClass();

    @Key("requestProcessor.${requestProcessor}.debuglevel")
    @DefaultValue("INFO")
    String debugLevel();

    @Key("requestProcessor.${requestProcessor}.parm1")
    @DefaultValue("")
    String parm1();

    @Key("requestProcessor.${requestProcessor}.parm2")
    @DefaultValue("")
    String parm2();

    @Key("requestProcessor.${requestProcessor}.parm3")
    @DefaultValue("")
    String parm3();

    @Key("requestProcessor.${requestProcessor}.parm4")
    @DefaultValue("")
    String parm4();

    @Key("requestProcessor.${requestProcessor}.parm5")
    @DefaultValue("")
    String parm5();

    @Key("requestProcessor.${requestProcessor}.parm6")
    @DefaultValue("")
    String parm6();

    @Key("requestProcessor.${requestProcessor}.parm7")
    @DefaultValue("")
    String parm7();

    @Key("requestProcessor.${requestProcessor}.parm8")
    @DefaultValue("")
    String parm8();

    @Key("requestProcessor.${requestProcessor}.parm9")
    @DefaultValue("")
    String parm9();

    @Key("requestProcessor.${requestProcessor}.parm10")
    @DefaultValue("")
    String parm10();

    @Key("requestProcessor.${requestProcessor}.parm11")
    @DefaultValue("")
    String parm11();

    @Key("requestProcessor.${requestProcessor}.parm12")
    @DefaultValue("")
    String parm12();

    @Key("requestProcessor.${requestProcessor}.parm13")
    @DefaultValue("")
    String parm13();

    @Key("requestProcessor.${requestProcessor}.parm14")
    @DefaultValue("")
    String parm14();

    @Key("requestProcessor.${requestProcessor}.parm15")
    @DefaultValue("")
    String parm15();

    @Key("requestProcessor.${requestProcessor}.parm16")
    @DefaultValue("")
    String parm16();

    @Key("requestProcessor.${requestProcessor}.parm17")
    @DefaultValue("")
    String parm17();

    @Key("requestProcessor.${requestProcessor}.parm18")
    @DefaultValue("")
    String parm18();

    @Key("requestProcessor.${requestProcessor}.parm19")
    @DefaultValue("")
    String parm19();

    @Key("requestProcessor.${requestProcessor}.parm20")
    @DefaultValue("")
    String parm20();
}

