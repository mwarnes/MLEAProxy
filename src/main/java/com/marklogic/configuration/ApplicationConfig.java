package com.marklogic.configuration;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.FIRST)
@Config.Sources({ "file:${mleaproxy.properties}",
        "file:./mleaproxy.properties",
        "file:${HOME}/mleaproxy.properties",
        "file:/etc/mleaproxy.properties",
        "classpath:mleaproxy.properties" })
public interface ApplicationConfig extends Config {

    @Key("directoryServers")
    public String[] directoryServers();

    @Key("ldaplisteners")
    public String[] ldaplisteners();

    @Key("samllisteners")
    public String[] samllisteners();

    @Key("ldap.debug")
    @DefaultValue("false")
    boolean ldapDebug();

    @Key("saml.debug")
    @DefaultValue("false")
    boolean samlDebug();

    @Key("saml.capath")
    String SamlCaPath();

    @Key("saml.keypath")
    String SamlKeyPath();

    @Key("saml.response.validity")
    @DefaultValue("300")
    int SamlResponseDuration();

}

