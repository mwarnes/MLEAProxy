# Design: Migrate from Aeonbits Owner to Spring ConfigurationProperties

**Date:** 2026-05-18  
**Status:** Draft  
**Author:** Claude (with Martin)

## Problem Statement

MLEAProxy uses two parallel configuration systems that don't integrate:

1. **Aeonbits Owner library** - 8 Config interfaces for LDAP, Kerberos, SAML settings
2. **Spring Boot properties** - `@Value` annotations for some services

The Owner library only reads Java system properties (`-D` flags), ignoring Spring Boot's `--property=value` command line arguments. This causes configuration overrides to be silently ignored, confusing users.

## Solution

Replace all Aeonbits Owner Config interfaces with Spring `@ConfigurationProperties` classes. This unifies configuration under Spring Boot's property system, which properly supports:

- `application.properties` / `application.yml`
- Command line arguments (`--property=value`)
- Environment variables
- System properties (`-D`)
- Profile-specific configuration

## Scope

### In Scope
- Migrate 8 Owner Config interfaces to Spring `@ConfigurationProperties`
- Update all services that use `ConfigFactory.create()`
- New Spring-style property key format
- Property validation with Bean Validation (JSR-380)
- Remove Aeonbits Owner dependency

### Out of Scope
- Backward compatibility with old property keys
- Documentation updates (separate task after migration)
- Example file updates (separate task after migration)

---

## Architecture

### New Package Structure

```
src/main/java/com/marklogic/configuration/
├── properties/
│   ├── MleaProxyProperties.java           # Root @ConfigurationProperties class
│   ├── DirectoryServerProperties.java     # In-memory LDAP server config
│   ├── KerberosProperties.java            # Kerberos KDC config
│   ├── LdapListenerProperties.java        # LDAP listener config
│   ├── SamlListenerProperties.java        # SAML listener config
│   ├── ServerSetProperties.java           # Backend server set config
│   ├── LdapServerProperties.java          # Backend LDAP server config
│   └── RequestProcessorProperties.java    # Request processor config
├── MleaProxyPropertiesConfig.java         # PropertySource configuration
└── (existing Spring @Configuration classes remain)
```

### Property Binding Model

```
MleaProxyProperties (prefix: "mleaproxy")
├── directoryServers: Map<String, DirectoryServerProperties>
├── ldapListeners: Map<String, LdapListenerProperties>
├── samlListeners: Map<String, SamlListenerProperties>
├── ldapSets: Map<String, ServerSetProperties>
├── ldapServers: Map<String, LdapServerProperties>
├── requestProcessors: Map<String, RequestProcessorProperties>
├── kerberos: KerberosProperties
├── ldapDebug: boolean
├── samlDebug: boolean
├── samlCaPath: String
├── samlKeyPath: String
├── samlResponseValidity: int
└── sslVerifyCertificates: boolean
```

---

## Property Key Mapping

### Root Application Properties

| Old Key | New Key | Type | Default |
|---------|---------|------|---------|
| `directoryServers` | *(removed - derived from map keys)* | - | - |
| `ldaplisteners` | *(removed - derived from map keys)* | - | - |
| `samllisteners` | *(removed - derived from map keys)* | - | - |
| `ldap.debug` | `mleaproxy.ldap-debug` | boolean | `false` |
| `saml.debug` | `mleaproxy.saml-debug` | boolean | `false` |
| `saml.capath` | `mleaproxy.saml-ca-path` | String | - |
| `saml.keypath` | `mleaproxy.saml-key-path` | String | - |
| `saml.response.validity` | `mleaproxy.saml-response-validity` | int | `300` |
| `ssl.verify.certificates` | `mleaproxy.ssl-verify-certificates` | boolean | `false` |

### Directory Server Properties (DSConfig)

| Old Key | New Key | Type | Default |
|---------|---------|------|---------|
| `ds.${name}.name` | `mleaproxy.directory-servers.${name}.name` | String | - |
| `ds.${name}.ipaddress` | `mleaproxy.directory-servers.${name}.ip-address` | String | `0.0.0.0` |
| `ds.${name}.port` | `mleaproxy.directory-servers.${name}.port` | int | `60389` |
| `ds.${name}.basedn` | `mleaproxy.directory-servers.${name}.base-dn` | String | `dc=MarkLogic,dc=Local` |
| `ds.${name}.admindn` | `mleaproxy.directory-servers.${name}.admin-dn` | String | `cn=Directory Manager` |
| `ds.${name}.adminpw` | `mleaproxy.directory-servers.${name}.admin-password` | String | `password` |
| `ds.${name}.ldifpath` | `mleaproxy.directory-servers.${name}.ldif-path` | String | - |

### Kerberos Properties (KerberosConfig)

| Old Key | New Key | Type | Default |
|---------|---------|------|---------|
| `kerberos.enabled` | `mleaproxy.kerberos.enabled` | boolean | `false` |
| `kerberos.realm` | `mleaproxy.kerberos.realm` | String | `MARKLOGIC.LOCAL` |
| `kerberos.kdc.host` | `mleaproxy.kerberos.kdc-host` | String | `localhost` |
| `kerberos.kdc.port` | `mleaproxy.kerberos.kdc-port` | int | `60088` |
| `kerberos.admin.port` | `mleaproxy.kerberos.admin-port` | int | `60749` |
| `kerberos.principals.import-from-ldap` | `mleaproxy.kerberos.import-principals-from-ldap` | boolean | `true` |
| `kerberos.principals.ldap-base-dn` | `mleaproxy.kerberos.ldap-base-dn` | String | `dc=MarkLogic,dc=Local` |
| `kerberos.service-principals` | `mleaproxy.kerberos.service-principals` | List<String> | `HTTP/localhost,ldap/localhost` |
| `kerberos.work-dir` | `mleaproxy.kerberos.work-dir` | String | `./kerberos` |
| `kerberos.debug` | `mleaproxy.kerberos.debug` | boolean | `false` |
| `kerberos.ticket.lifetime` | `mleaproxy.kerberos.ticket-lifetime` | int | `36000` |
| `kerberos.ticket.renewable-lifetime` | `mleaproxy.kerberos.renewable-lifetime` | int | `604800` |
| `kerberos.clock-skew` | `mleaproxy.kerberos.clock-skew` | int | `300` |

### LDAP Listener Properties (LDAPListenersConfig)

| Old Key | New Key | Type | Default |
|---------|---------|------|---------|
| `ldaplistener.${name}.ipaddress` | `mleaproxy.ldap-listeners.${name}.ip-address` | String | `0.0.0.0` |
| `ldaplistener.${name}.port` | `mleaproxy.ldap-listeners.${name}.port` | int | *(required)* |
| `ldaplistener.${name}.secure` | `mleaproxy.ldap-listeners.${name}.secure` | boolean | `false` |
| `ldaplistener.${name}.debuglevel` | `mleaproxy.ldap-listeners.${name}.debug-level` | String | `INFO` |
| `ldaplistener.${name}.keystore` | `mleaproxy.ldap-listeners.${name}.keystore` | String | - |
| `ldaplistener.${name}.keystorepasswd` | `mleaproxy.ldap-listeners.${name}.keystore-password` | String | - |
| `ldaplistener.${name}.truststore` | `mleaproxy.ldap-listeners.${name}.truststore` | String | - |
| `ldaplistener.${name}.truststorepasswd` | `mleaproxy.ldap-listeners.${name}.truststore-password` | String | - |
| `ldaplistener.${name}.ldapset` | `mleaproxy.ldap-listeners.${name}.ldap-sets` | List<String> | - |
| `ldaplistener.${name}.ldapmode` | `mleaproxy.ldap-listeners.${name}.ldap-mode` | String | `internal` |
| `ldaplistener.${name}.requestProcessor` | `mleaproxy.ldap-listeners.${name}.request-processor` | String | - |
| `ldaplistener.${name}.description` | `mleaproxy.ldap-listeners.${name}.description` | String | - |
| `ldaplistener.${name}.requestHandler` | `mleaproxy.ldap-listeners.${name}.request-handler` | String | `com.marklogic.handlers.LDAPRequestHandler` |

### SAML Listener Properties (SAMLListenersConfig)

| Old Key | New Key | Type | Default |
|---------|---------|------|---------|
| `samllistener.${name}.ipaddress` | `mleaproxy.saml-listeners.${name}.ip-address` | String | `0.0.0.0` |
| `samllistener.${name}.port` | `mleaproxy.saml-listeners.${name}.port` | int | *(required)* |
| `samllistener.${name}.debuglevel` | `mleaproxy.saml-listeners.${name}.debug-level` | String | `INFO` |
| `samllistener.${name}.description` | `mleaproxy.saml-listeners.${name}.description` | String | - |

### Server Set Properties (SetsConfig)

| Old Key | New Key | Type | Default |
|---------|---------|------|---------|
| `ldapset.${name}.servers` | `mleaproxy.ldap-sets.${name}.servers` | List<String> | - |
| `ldapset.${name}.mode` | `mleaproxy.ldap-sets.${name}.mode` | String | - |
| `ldapset.${name}.secure` | `mleaproxy.ldap-sets.${name}.secure` | boolean | `false` |
| `ldapset.${name}.keytype` | `mleaproxy.ldap-sets.${name}.key-type` | String | `JKS` |
| `ldapset.${name}.keypath` | `mleaproxy.ldap-sets.${name}.key-path` | String | - |
| `ldapset.${name}.certpath` | `mleaproxy.ldap-sets.${name}.cert-path` | String | - |
| `ldapset.${name}.capath` | `mleaproxy.ldap-sets.${name}.ca-path` | String | - |
| `ldapset.${name}.keystore` | `mleaproxy.ldap-sets.${name}.keystore` | String | - |
| `ldapset.${name}.keystorepasswd` | `mleaproxy.ldap-sets.${name}.keystore-password` | String | - |
| `ldapset.${name}.pfxpath` | `mleaproxy.ldap-sets.${name}.pfx-path` | String | - |
| `ldapset.${name}.pfxpasswd` | `mleaproxy.ldap-sets.${name}.pfx-password` | String | - |
| `ldapset.${name}.truststore` | `mleaproxy.ldap-sets.${name}.truststore` | String | - |
| `ldapset.${name}.truststorepasswd` | `mleaproxy.ldap-sets.${name}.truststore-password` | String | - |

### Backend Server Properties (ServersConfig)

| Old Key | New Key | Type | Default |
|---------|---------|------|---------|
| `ldapserver.${name}.host` | `mleaproxy.ldap-servers.${name}.host` | String | - |
| `ldapserver.${name}.port` | `mleaproxy.ldap-servers.${name}.port` | int | *(required)* |
| `ldapserver.${name}.authtype` | `mleaproxy.ldap-servers.${name}.auth-type` | String | `simple` |

### Request Processor Properties (ProcessorConfig)

| Old Key | New Key | Type | Default |
|---------|---------|------|---------|
| `requestProcessor.${name}.authclass` | `mleaproxy.request-processors.${name}.auth-class` | String | - |
| `requestProcessor.${name}.debuglevel` | `mleaproxy.request-processors.${name}.debug-level` | String | `INFO` |
| `requestProcessor.${name}.parm1` | `mleaproxy.request-processors.${name}.params[0]` | String | - |
| `requestProcessor.${name}.parm2` | `mleaproxy.request-processors.${name}.params[1]` | String | - |
| ... | ... | ... | ... |
| `requestProcessor.${name}.parm20` | `mleaproxy.request-processors.${name}.params[19]` | String | - |

---

## Class Designs

### MleaProxyProperties (Root)

```java
@Configuration
@ConfigurationProperties(prefix = "mleaproxy")
@Validated
public class MleaProxyProperties {
    
    private Map<String, DirectoryServerProperties> directoryServers = new LinkedHashMap<>();
    private Map<String, LdapListenerProperties> ldapListeners = new LinkedHashMap<>();
    private Map<String, SamlListenerProperties> samlListeners = new LinkedHashMap<>();
    private Map<String, ServerSetProperties> ldapSets = new LinkedHashMap<>();
    private Map<String, LdapServerProperties> ldapServers = new LinkedHashMap<>();
    private Map<String, RequestProcessorProperties> requestProcessors = new LinkedHashMap<>();
    
    @NestedConfigurationProperty
    private KerberosProperties kerberos = new KerberosProperties();
    
    private boolean ldapDebug = false;
    private boolean samlDebug = false;
    private String samlCaPath;
    private String samlKeyPath;
    private int samlResponseValidity = 300;
    private boolean sslVerifyCertificates = false;
    
    // Getters and setters...
}
```

### DirectoryServerProperties

```java
public class DirectoryServerProperties {
    private String name;
    private String ipAddress = "0.0.0.0";
    private int port = 60389;
    private String baseDn = "dc=MarkLogic,dc=Local";
    private String adminDn = "cn=Directory Manager";
    private String adminPassword = "password";
    private String ldifPath;
    
    // Getters and setters...
}
```

### LdapListenerProperties

```java
public class LdapListenerProperties {
    private String ipAddress = "0.0.0.0";
    
    @NotNull(message = "LDAP listener port is required")
    private Integer port;
    
    private boolean secure = false;
    private String debugLevel = "INFO";
    private String keystore;
    private String keystorePassword;
    private String truststore;
    private String truststorePassword;
    private List<String> ldapSets = new ArrayList<>();
    private String ldapMode = "internal";
    private String requestProcessor;
    private String description;
    private String requestHandler = "com.marklogic.handlers.LDAPRequestHandler";
    
    // Getters and setters...
}
```

### KerberosProperties

```java
public class KerberosProperties {
    private boolean enabled = false;
    private String realm = "MARKLOGIC.LOCAL";
    private String kdcHost = "localhost";
    private int kdcPort = 60088;
    private int adminPort = 60749;
    private boolean importPrincipalsFromLdap = true;
    private String ldapBaseDn = "dc=MarkLogic,dc=Local";
    private List<String> servicePrincipals = List.of("HTTP/localhost", "ldap/localhost");
    private String workDir = "./kerberos";
    private boolean debug = false;
    private int ticketLifetime = 36000;
    private int renewableLifetime = 604800;
    private int clockSkew = 300;
    
    // Getters and setters...
}
```

### ServerSetProperties

```java
public class ServerSetProperties {
    private List<String> servers = new ArrayList<>();
    private String mode;
    private boolean secure = false;
    private String keyType = "JKS";
    private String keyPath;
    private String certPath;
    private String caPath;
    private String keystore;
    private String keystorePassword;
    private String pfxPath;
    private String pfxPassword;
    private String truststore;
    private String truststorePassword;
    
    // Getters and setters...
}
```

### LdapServerProperties

```java
public class LdapServerProperties {
    private String host;
    
    @NotNull(message = "LDAP server port is required")
    private Integer port;
    
    private String authType = "simple";
    
    // Getters and setters...
}
```

### RequestProcessorProperties

```java
public class RequestProcessorProperties {
    private String authClass;
    private String debugLevel = "INFO";
    private List<String> params = new ArrayList<>();
    
    // Getters and setters...
    
    // Helper for legacy parm1-parm20 access
    public String getParam(int index) {
        return index < params.size() ? params.get(index) : "";
    }
}
```

---

## Property Source Configuration

```java
@Configuration
@EnableConfigurationProperties(MleaProxyProperties.class)
@PropertySources({
    // Lowest priority first (Spring merges in order, last wins)
    @PropertySource(value = "classpath:mleaproxy.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:/etc/mleaproxy.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:${user.home}/mleaproxy.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:./mleaproxy.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:./ldap.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:./saml.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:./oauth.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:./directory.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:./kerberos.properties", ignoreResourceNotFound = true)
})
public class MleaProxyPropertiesConfig {
    // Spring Boot command line args (--property=value) have highest priority automatically
}
```

---

## Service Changes

### Before (LDAPServerService)

```java
Map<String, String> expVars = new HashMap<>();
expVars.put("directoryServer", serverName);
DSConfig dsConfig = ConfigFactory.create(DSConfig.class, expVars);
int port = dsConfig.dsPort();
```

### After (LDAPServerService)

```java
@Autowired
private MleaProxyProperties config;

// In method:
DirectoryServerProperties dsConfig = config.getDirectoryServers().get(serverName);
int port = dsConfig.getPort();
```

### Before (LDAPListenerService)

```java
Map<String, String> expVars = new HashMap<>();
expVars.put("listener", listenerName);
LDAPListenersConfig listenerCfg = ConfigFactory.create(LDAPListenersConfig.class, expVars);

Map<String, Object> setVars = new HashMap<>();
setVars.put("serverSet", setName);
SetsConfig setsCfg = ConfigFactory.create(SetsConfig.class, setVars);
```

### After (LDAPListenerService)

```java
@Autowired
private MleaProxyProperties config;

// In method:
LdapListenerProperties listenerCfg = config.getLdapListeners().get(listenerName);
ServerSetProperties setsCfg = config.getLdapSets().get(setName);
```

---

## Files to Create

| File | Description |
|------|-------------|
| `src/main/java/com/marklogic/configuration/properties/MleaProxyProperties.java` | Root configuration class |
| `src/main/java/com/marklogic/configuration/properties/DirectoryServerProperties.java` | Directory server POJO |
| `src/main/java/com/marklogic/configuration/properties/KerberosProperties.java` | Kerberos POJO |
| `src/main/java/com/marklogic/configuration/properties/LdapListenerProperties.java` | LDAP listener POJO |
| `src/main/java/com/marklogic/configuration/properties/SamlListenerProperties.java` | SAML listener POJO |
| `src/main/java/com/marklogic/configuration/properties/ServerSetProperties.java` | Server set POJO |
| `src/main/java/com/marklogic/configuration/properties/LdapServerProperties.java` | Backend server POJO |
| `src/main/java/com/marklogic/configuration/properties/RequestProcessorProperties.java` | Request processor POJO |
| `src/main/java/com/marklogic/configuration/MleaProxyPropertiesConfig.java` | PropertySource setup |
| `src/main/resources/mleaproxy.properties` | Default config with new keys |

## Files to Modify

| File | Changes |
|------|---------|
| `src/main/java/com/marklogic/handlers/ApplicationListener.java` | Inject `MleaProxyProperties`, remove `ConfigFactory` usage |
| `src/main/java/com/marklogic/service/LDAPServerService.java` | Inject `MleaProxyProperties`, remove `ConfigFactory` usage |
| `src/main/java/com/marklogic/service/LDAPListenerService.java` | Inject `MleaProxyProperties`, remove `ConfigFactory` usage |
| `src/main/java/com/marklogic/handlers/LDAPRequestHandler.java` | Inject `MleaProxyProperties`, remove `ConfigFactory` usage |
| `src/main/java/com/marklogic/handlers/KerberosKDCServer.java` | Accept `KerberosProperties` instead of `KerberosConfig` |
| `src/main/java/com/marklogic/beans/SamlBean.java` | Use `MleaProxyProperties` instead of `ApplicationConfig` |
| `pom.xml` | Remove `org.aeonbits.owner:owner` dependency |

## Files to Delete

| File | Reason |
|------|--------|
| `src/main/java/com/marklogic/configuration/ApplicationConfig.java` | Replaced by `MleaProxyProperties` |
| `src/main/java/com/marklogic/configuration/DSConfig.java` | Replaced by `DirectoryServerProperties` |
| `src/main/java/com/marklogic/configuration/KerberosConfig.java` | Replaced by `KerberosProperties` |
| `src/main/java/com/marklogic/configuration/LDAPListenersConfig.java` | Replaced by `LdapListenerProperties` |
| `src/main/java/com/marklogic/configuration/SAMLListenersConfig.java` | Replaced by `SamlListenerProperties` |
| `src/main/java/com/marklogic/configuration/SetsConfig.java` | Replaced by `ServerSetProperties` |
| `src/main/java/com/marklogic/configuration/ServersConfig.java` | Replaced by `LdapServerProperties` |
| `src/main/java/com/marklogic/configuration/ProcessorConfig.java` | Replaced by `RequestProcessorProperties` |

---

## Testing Strategy

1. **Unit Tests**: Create tests for each Properties class with various configurations
2. **Integration Tests**: Update existing tests to use new property format
3. **Command Line Override Test**: Verify `--mleaproxy.ldap-debug=true` works
4. **Property File Priority Test**: Verify file loading order is correct
5. **Validation Test**: Verify required fields fail fast with clear errors

---

## Example Configuration (New Format)

```properties
# ===========================================
# MLEAProxy Configuration (Spring Boot Style)
# ===========================================

# --- Global Settings ---
mleaproxy.ldap-debug=false
mleaproxy.saml-debug=false
mleaproxy.ssl-verify-certificates=false

# --- In-Memory Directory Server ---
mleaproxy.directory-servers.marklogic.name=MarkLogic Local
mleaproxy.directory-servers.marklogic.ip-address=0.0.0.0
mleaproxy.directory-servers.marklogic.port=60389
mleaproxy.directory-servers.marklogic.base-dn=dc=MarkLogic,dc=Local
mleaproxy.directory-servers.marklogic.admin-dn=cn=Directory Manager
mleaproxy.directory-servers.marklogic.admin-password=password

# --- LDAP Listeners ---
mleaproxy.ldap-listeners.proxy.ip-address=0.0.0.0
mleaproxy.ldap-listeners.proxy.port=10389
mleaproxy.ldap-listeners.proxy.ldap-mode=internal
mleaproxy.ldap-listeners.proxy.ldap-sets=internal
mleaproxy.ldap-listeners.proxy.request-processor=jsonauth
mleaproxy.ldap-listeners.proxy.description=JSON Authentication Proxy

# --- Server Sets ---
mleaproxy.ldap-sets.internal.servers=localhost
mleaproxy.ldap-sets.internal.mode=internal

# --- Backend Servers ---
mleaproxy.ldap-servers.localhost.host=localhost
mleaproxy.ldap-servers.localhost.port=60389

# --- Request Processors ---
mleaproxy.request-processors.jsonauth.auth-class=com.marklogic.processors.JsonRequestProcessor
mleaproxy.request-processors.jsonauth.debug-level=INFO

# --- Kerberos (Optional) ---
mleaproxy.kerberos.enabled=false
mleaproxy.kerberos.realm=MARKLOGIC.LOCAL
mleaproxy.kerberos.kdc-port=60088
```

---

## Migration Checklist

- [ ] Create all Properties POJO classes
- [ ] Create MleaProxyPropertiesConfig with PropertySources
- [ ] Update ApplicationListener to use MleaProxyProperties
- [ ] Update LDAPServerService to use MleaProxyProperties
- [ ] Update LDAPListenerService to use MleaProxyProperties
- [ ] Update LDAPRequestHandler to use MleaProxyProperties
- [ ] Update KerberosKDCServer to accept KerberosProperties
- [ ] Update SamlBean to use MleaProxyProperties
- [ ] Create new default mleaproxy.properties with new keys
- [ ] Update all existing tests
- [ ] Remove Owner dependency from pom.xml
- [ ] Delete old Owner Config interfaces
- [ ] Verify command line overrides work
- [ ] Verify all tests pass

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Breaking existing deployments | Clear documentation of property key changes; version bump to 3.0.0 |
| Missing property mappings | Comprehensive mapping table above; thorough testing |
| Spring binding edge cases | Use `@NestedConfigurationProperty` and explicit types |
| Validation too strict | Start with `@NotNull` only on truly required fields |

---

## Success Criteria

1. All tests pass with new configuration system
2. Command line `--mleaproxy.property=value` overrides work correctly
3. Property file priority order is preserved
4. Application starts and functions identically to before
5. No Aeonbits Owner code remains in codebase
