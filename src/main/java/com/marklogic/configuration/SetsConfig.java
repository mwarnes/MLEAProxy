package com.marklogic.configuration;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.FIRST)
@Config.Sources({ "file:${mleaproxy.properties}",
        "file:./mleaproxy.properties",
        "file:${HOME}/mleaproxy.properties",
        "file:/etc/mleaproxy.properties",
        "classpath:mleaproxy.properties" })
public interface SetsConfig extends Config {

    @Key("ldapset.${serverSet}.servers")
    @DefaultValue("null")
    public String[] servers();

    @Key("ldapset.${serverSet}.mode")
    String serverSetMode();

    @Key("ldapset.${serverSet}.secure")
    @DefaultValue("false")
    boolean serverSetSecure();

    @Key("ldapset.${serverSet}.keytype")
    @DefaultValue("PEM")
    String serverSetStoreType();

    /* Required for servers that support Client Authentication */
    @Key("ldapset.${serverSet}.keypath")
    @DefaultValue("")
    String serverSetKeyPath();

    /* Required for servers that support Client Authentication */
    @Key("ldapset.${serverSet}.certpath")
    @DefaultValue("")
    String serverSetCertPath();

    @Key("ldapset.${serverSet}.capath")
    @DefaultValue("")
    String serverSetCAPath();

    /* Required for servers that support Client Authentication */
    @Key("ldapset.${serverSet}.keystore")
    @DefaultValue("")
    String serverSetKeyStore();

    /* Required for servers that support Client Authentication */
    @Key("ldapset.${serverSet}.keystorepasswd")
    @DefaultValue("")
    String serverSetKeyStorePassword();

    /* Required for servers that support Client Authentication */
    @Key("ldapset.${serverSet}.pfxpath")
    @DefaultValue("")
    String serverSetPfxPath();

    /* Required for servers that support Client Authentication */
    @Key("ldapset.${serverSet}.pfxpasswd")
    @DefaultValue("")
    String serverSetPfxPassword();

    @Key("ldapset.${serverSet}.truststore")
    @DefaultValue("")
    String serverSetTrustStore();

    @Key("ldapset.${serverSet}.truststorepasswd")
    @DefaultValue("")
    String serverSetTrustStorePassword();

}

