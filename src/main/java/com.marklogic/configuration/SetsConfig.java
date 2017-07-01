package com.marklogic.configuration;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.FIRST)
@Config.Sources({ "file:./mleaproxy.properties",
        "file:${HOME}/mleaproxy.properties",
        "file:/etc/mleaproxy.properties",
        "classpath:mleaproxy.properties" })
public interface SetsConfig extends Config {

    @Key("ldapset.${serverSet}.servers")
    @DefaultValue("dummy")
    public String[] servers();

    @Key("ldapset.${serverSet}.mode")
    String serverSetMode();

    @Key("ldapset.${serverSet}.secure")
    @DefaultValue("false")
    boolean serverSetSecure();

    /* Required for servers that support Client Authentication */
    @Key("ldapset.${serverSet}.keystore")
    @DefaultValue("")
    String serverSetKeyStore();

    /* Required for servers that support Client Authentication */
    @Key("ldapset.${serverSet}.keystorepasswd")
    @DefaultValue("")
    String serverSetKeyStorePassword();

    @Key("ldapset.${serverSet}.truststore")
    @DefaultValue("")
    String serverSetTrustStore();

    @Key("ldapset.${serverSet}.truststorepasswd")
    @DefaultValue("")
    String serverSetTrustStorePassword();

}

