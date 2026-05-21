# Spring Configuration Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Aeonbits Owner configuration with Spring `@ConfigurationProperties` to unify configuration and fix command-line override issues.

**Architecture:** Create POJO classes with `@ConfigurationProperties` annotation using `Map<String, T>` for dynamic/named configurations. Update all services to inject the root `MleaProxyProperties` bean instead of using `ConfigFactory.create()`.

**Tech Stack:** Spring Boot 3.3.5, Jakarta Validation (JSR-380), Java 21

**Spec:** `docs/superpowers/specs/2026-05-18-spring-config-migration-design.md`

---

## File Structure

### New Files (Create)
```
src/main/java/com/marklogic/configuration/properties/
├── MleaProxyProperties.java           # Root config bean
├── DirectoryServerProperties.java     # In-memory LDAP server
├── KerberosProperties.java            # Kerberos KDC settings
├── LdapListenerProperties.java        # LDAP proxy listener
├── SamlListenerProperties.java        # SAML listener
├── ServerSetProperties.java           # Backend server set
├── LdapServerProperties.java          # Backend LDAP server
└── RequestProcessorProperties.java    # Request processor

src/main/java/com/marklogic/configuration/
└── MleaProxyPropertiesConfig.java     # PropertySource configuration

src/main/resources/
└── mleaproxy.properties               # New format defaults (update existing)

src/test/java/com/marklogic/configuration/
└── MleaProxyPropertiesTest.java       # Configuration binding tests
```

### Files to Modify
```
src/main/java/com/marklogic/handlers/ApplicationListener.java
src/main/java/com/marklogic/service/LDAPServerService.java
src/main/java/com/marklogic/service/LDAPListenerService.java
src/main/java/com/marklogic/handlers/LDAPRequestHandler.java
src/main/java/com/marklogic/handlers/KerberosKDCServer.java
src/main/java/com/marklogic/beans/SamlBean.java
pom.xml
```

### Files to Delete (Final Task)
```
src/main/java/com/marklogic/configuration/ApplicationConfig.java
src/main/java/com/marklogic/configuration/DSConfig.java
src/main/java/com/marklogic/configuration/KerberosConfig.java
src/main/java/com/marklogic/configuration/LDAPListenersConfig.java
src/main/java/com/marklogic/configuration/SAMLListenersConfig.java
src/main/java/com/marklogic/configuration/SetsConfig.java
src/main/java/com/marklogic/configuration/ServersConfig.java
src/main/java/com/marklogic/configuration/ProcessorConfig.java
```

---

## Task 1: Create DirectoryServerProperties POJO

**Files:**
- Create: `src/main/java/com/marklogic/configuration/properties/DirectoryServerProperties.java`

- [ ] **Step 1: Create the properties package directory**

```bash
mkdir -p src/main/java/com/marklogic/configuration/properties
```

- [ ] **Step 2: Create DirectoryServerProperties class**

```java
package com.marklogic.configuration.properties;

/**
 * Configuration properties for an in-memory LDAP directory server.
 * 
 * Properties prefix: mleaproxy.directory-servers.{name}.*
 * 
 * Example:
 * mleaproxy.directory-servers.marklogic.port=60389
 * mleaproxy.directory-servers.marklogic.base-dn=dc=MarkLogic,dc=Local
 */
public class DirectoryServerProperties {
    
    private String name;
    private String ipAddress = "0.0.0.0";
    private int port = 60389;
    private String baseDn = "dc=MarkLogic,dc=Local";
    private String adminDn = "cn=Directory Manager";
    private String adminPassword = "password";
    private String ldifPath;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getBaseDn() {
        return baseDn;
    }

    public void setBaseDn(String baseDn) {
        this.baseDn = baseDn;
    }

    public String getAdminDn() {
        return adminDn;
    }

    public void setAdminDn(String adminDn) {
        this.adminDn = adminDn;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getLdifPath() {
        return ldifPath;
    }

    public void setLdifPath(String ldifPath) {
        this.ldifPath = ldifPath;
    }

    @Override
    public String toString() {
        return "DirectoryServerProperties{" +
                "name='" + name + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", port=" + port +
                ", baseDn='" + baseDn + '\'' +
                ", adminDn='" + adminDn + '\'' +
                '}';
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/marklogic/configuration/properties/DirectoryServerProperties.java
git commit -m "feat(config): add DirectoryServerProperties POJO"
```

---

## Task 2: Create KerberosProperties POJO

**Files:**
- Create: `src/main/java/com/marklogic/configuration/properties/KerberosProperties.java`

- [ ] **Step 1: Create KerberosProperties class**

```java
package com.marklogic.configuration.properties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the embedded Kerberos KDC.
 * 
 * Properties prefix: mleaproxy.kerberos.*
 * 
 * Example:
 * mleaproxy.kerberos.enabled=true
 * mleaproxy.kerberos.realm=MARKLOGIC.LOCAL
 * mleaproxy.kerberos.kdc-port=60088
 */
public class KerberosProperties {
    
    private boolean enabled = false;
    private String realm = "MARKLOGIC.LOCAL";
    private String kdcHost = "localhost";
    private int kdcPort = 60088;
    private int adminPort = 60749;
    private boolean importPrincipalsFromLdap = true;
    private String ldapBaseDn = "dc=MarkLogic,dc=Local";
    private List<String> servicePrincipals = new ArrayList<>(List.of("HTTP/localhost", "ldap/localhost"));
    private String workDir = "./kerberos";
    private boolean debug = false;
    private int ticketLifetime = 36000;
    private int renewableLifetime = 604800;
    private int clockSkew = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getKdcHost() {
        return kdcHost;
    }

    public void setKdcHost(String kdcHost) {
        this.kdcHost = kdcHost;
    }

    public int getKdcPort() {
        return kdcPort;
    }

    public void setKdcPort(int kdcPort) {
        this.kdcPort = kdcPort;
    }

    public int getAdminPort() {
        return adminPort;
    }

    public void setAdminPort(int adminPort) {
        this.adminPort = adminPort;
    }

    public boolean isImportPrincipalsFromLdap() {
        return importPrincipalsFromLdap;
    }

    public void setImportPrincipalsFromLdap(boolean importPrincipalsFromLdap) {
        this.importPrincipalsFromLdap = importPrincipalsFromLdap;
    }

    public String getLdapBaseDn() {
        return ldapBaseDn;
    }

    public void setLdapBaseDn(String ldapBaseDn) {
        this.ldapBaseDn = ldapBaseDn;
    }

    public List<String> getServicePrincipals() {
        return servicePrincipals;
    }

    public void setServicePrincipals(List<String> servicePrincipals) {
        this.servicePrincipals = servicePrincipals;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public int getTicketLifetime() {
        return ticketLifetime;
    }

    public void setTicketLifetime(int ticketLifetime) {
        this.ticketLifetime = ticketLifetime;
    }

    public int getRenewableLifetime() {
        return renewableLifetime;
    }

    public void setRenewableLifetime(int renewableLifetime) {
        this.renewableLifetime = renewableLifetime;
    }

    public int getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(int clockSkew) {
        this.clockSkew = clockSkew;
    }

    @Override
    public String toString() {
        return "KerberosProperties{" +
                "enabled=" + enabled +
                ", realm='" + realm + '\'' +
                ", kdcHost='" + kdcHost + '\'' +
                ", kdcPort=" + kdcPort +
                '}';
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/marklogic/configuration/properties/KerberosProperties.java
git commit -m "feat(config): add KerberosProperties POJO"
```

---

## Task 3: Create LdapListenerProperties POJO

**Files:**
- Create: `src/main/java/com/marklogic/configuration/properties/LdapListenerProperties.java`

- [ ] **Step 1: Create LdapListenerProperties class**

```java
package com.marklogic.configuration.properties;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for an LDAP proxy listener.
 * 
 * Properties prefix: mleaproxy.ldap-listeners.{name}.*
 * 
 * Example:
 * mleaproxy.ldap-listeners.proxy.port=10389
 * mleaproxy.ldap-listeners.proxy.ldap-mode=internal
 */
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

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getDebugLevel() {
        return debugLevel;
    }

    public void setDebugLevel(String debugLevel) {
        this.debugLevel = debugLevel;
    }

    public String getKeystore() {
        return keystore;
    }

    public void setKeystore(String keystore) {
        this.keystore = keystore;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public String getTruststore() {
        return truststore;
    }

    public void setTruststore(String truststore) {
        this.truststore = truststore;
    }

    public String getTruststorePassword() {
        return truststorePassword;
    }

    public void setTruststorePassword(String truststorePassword) {
        this.truststorePassword = truststorePassword;
    }

    public List<String> getLdapSets() {
        return ldapSets;
    }

    public void setLdapSets(List<String> ldapSets) {
        this.ldapSets = ldapSets;
    }

    public String getLdapMode() {
        return ldapMode;
    }

    public void setLdapMode(String ldapMode) {
        this.ldapMode = ldapMode;
    }

    public String getRequestProcessor() {
        return requestProcessor;
    }

    public void setRequestProcessor(String requestProcessor) {
        this.requestProcessor = requestProcessor;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRequestHandler() {
        return requestHandler;
    }

    public void setRequestHandler(String requestHandler) {
        this.requestHandler = requestHandler;
    }

    @Override
    public String toString() {
        return "LdapListenerProperties{" +
                "ipAddress='" + ipAddress + '\'' +
                ", port=" + port +
                ", secure=" + secure +
                ", ldapMode='" + ldapMode + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/marklogic/configuration/properties/LdapListenerProperties.java
git commit -m "feat(config): add LdapListenerProperties POJO"
```

---

## Task 4: Create SamlListenerProperties POJO

**Files:**
- Create: `src/main/java/com/marklogic/configuration/properties/SamlListenerProperties.java`

- [ ] **Step 1: Create SamlListenerProperties class**

```java
package com.marklogic.configuration.properties;

import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for a SAML listener.
 * 
 * Properties prefix: mleaproxy.saml-listeners.{name}.*
 * 
 * Example:
 * mleaproxy.saml-listeners.saml.port=8443
 */
public class SamlListenerProperties {
    
    private String ipAddress = "0.0.0.0";
    
    @NotNull(message = "SAML listener port is required")
    private Integer port;
    
    private String debugLevel = "INFO";
    private String description;

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDebugLevel() {
        return debugLevel;
    }

    public void setDebugLevel(String debugLevel) {
        this.debugLevel = debugLevel;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "SamlListenerProperties{" +
                "ipAddress='" + ipAddress + '\'' +
                ", port=" + port +
                ", description='" + description + '\'' +
                '}';
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/marklogic/configuration/properties/SamlListenerProperties.java
git commit -m "feat(config): add SamlListenerProperties POJO"
```

---

## Task 5: Create ServerSetProperties POJO

**Files:**
- Create: `src/main/java/com/marklogic/configuration/properties/ServerSetProperties.java`

- [ ] **Step 1: Create ServerSetProperties class**

```java
package com.marklogic.configuration.properties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for a backend LDAP server set.
 * 
 * Properties prefix: mleaproxy.ldap-sets.{name}.*
 * 
 * Example:
 * mleaproxy.ldap-sets.primary.servers=server1,server2
 * mleaproxy.ldap-sets.primary.secure=true
 * mleaproxy.ldap-sets.primary.key-type=PEM
 */
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

    public List<String> getServers() {
        return servers;
    }

    public void setServers(List<String> servers) {
        this.servers = servers;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getKeyType() {
        return keyType;
    }

    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    public String getKeyPath() {
        return keyPath;
    }

    public void setKeyPath(String keyPath) {
        this.keyPath = keyPath;
    }

    public String getCertPath() {
        return certPath;
    }

    public void setCertPath(String certPath) {
        this.certPath = certPath;
    }

    public String getCaPath() {
        return caPath;
    }

    public void setCaPath(String caPath) {
        this.caPath = caPath;
    }

    public String getKeystore() {
        return keystore;
    }

    public void setKeystore(String keystore) {
        this.keystore = keystore;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public String getPfxPath() {
        return pfxPath;
    }

    public void setPfxPath(String pfxPath) {
        this.pfxPath = pfxPath;
    }

    public String getPfxPassword() {
        return pfxPassword;
    }

    public void setPfxPassword(String pfxPassword) {
        this.pfxPassword = pfxPassword;
    }

    public String getTruststore() {
        return truststore;
    }

    public void setTruststore(String truststore) {
        this.truststore = truststore;
    }

    public String getTruststorePassword() {
        return truststorePassword;
    }

    public void setTruststorePassword(String truststorePassword) {
        this.truststorePassword = truststorePassword;
    }

    /**
     * Convenience method to get the store type enum-style.
     * Returns "PEM", "PFX", or "JKS" (default).
     */
    public String getStoreType() {
        return keyType != null ? keyType.toUpperCase() : "JKS";
    }

    @Override
    public String toString() {
        return "ServerSetProperties{" +
                "servers=" + servers +
                ", secure=" + secure +
                ", keyType='" + keyType + '\'' +
                '}';
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/marklogic/configuration/properties/ServerSetProperties.java
git commit -m "feat(config): add ServerSetProperties POJO"
```

---

## Task 6: Create LdapServerProperties POJO

**Files:**
- Create: `src/main/java/com/marklogic/configuration/properties/LdapServerProperties.java`

- [ ] **Step 1: Create LdapServerProperties class**

```java
package com.marklogic.configuration.properties;

import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for a backend LDAP server.
 * 
 * Properties prefix: mleaproxy.ldap-servers.{name}.*
 * 
 * Example:
 * mleaproxy.ldap-servers.server1.host=192.168.1.10
 * mleaproxy.ldap-servers.server1.port=389
 */
public class LdapServerProperties {
    
    private String host;
    
    @NotNull(message = "LDAP server port is required")
    private Integer port;
    
    private String authType = "simple";

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    @Override
    public String toString() {
        return "LdapServerProperties{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", authType='" + authType + '\'' +
                '}';
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/marklogic/configuration/properties/LdapServerProperties.java
git commit -m "feat(config): add LdapServerProperties POJO"
```

---

## Task 7: Create RequestProcessorProperties POJO

**Files:**
- Create: `src/main/java/com/marklogic/configuration/properties/RequestProcessorProperties.java`

- [ ] **Step 1: Create RequestProcessorProperties class**

```java
package com.marklogic.configuration.properties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for a request processor.
 * 
 * Properties prefix: mleaproxy.request-processors.{name}.*
 * 
 * Example:
 * mleaproxy.request-processors.jsonauth.auth-class=com.marklogic.processors.JsonRequestProcessor
 * mleaproxy.request-processors.jsonauth.params[0]=/path/to/users.json
 */
public class RequestProcessorProperties {
    
    private String authClass;
    private String debugLevel = "INFO";
    private List<String> params = new ArrayList<>();

    public String getAuthClass() {
        return authClass;
    }

    public void setAuthClass(String authClass) {
        this.authClass = authClass;
    }

    public String getDebugLevel() {
        return debugLevel;
    }

    public void setDebugLevel(String debugLevel) {
        this.debugLevel = debugLevel;
    }

    public List<String> getParams() {
        return params;
    }

    public void setParams(List<String> params) {
        this.params = params;
    }

    /**
     * Get a parameter by index (0-based).
     * Returns empty string if index is out of bounds.
     * 
     * @param index 0-based parameter index
     * @return parameter value or empty string
     */
    public String getParam(int index) {
        if (index >= 0 && index < params.size()) {
            return params.get(index);
        }
        return "";
    }

    /**
     * Legacy support: get parm1 (index 0).
     */
    public String getParm1() {
        return getParam(0);
    }

    /**
     * Legacy support: get parm2 (index 1).
     */
    public String getParm2() {
        return getParam(1);
    }

    @Override
    public String toString() {
        return "RequestProcessorProperties{" +
                "authClass='" + authClass + '\'' +
                ", debugLevel='" + debugLevel + '\'' +
                ", params=" + params +
                '}';
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/marklogic/configuration/properties/RequestProcessorProperties.java
git commit -m "feat(config): add RequestProcessorProperties POJO"
```

---

## Task 8: Create Root MleaProxyProperties Class

**Files:**
- Create: `src/main/java/com/marklogic/configuration/properties/MleaProxyProperties.java`

- [ ] **Step 1: Create MleaProxyProperties class**

```java
package com.marklogic.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root configuration properties for MLEAProxy.
 * 
 * This class replaces the Aeonbits Owner ApplicationConfig interface
 * with Spring Boot's @ConfigurationProperties binding.
 * 
 * Properties prefix: mleaproxy.*
 * 
 * Benefits over Owner:
 * - Supports Spring Boot command line args (--mleaproxy.ldap-debug=true)
 * - Supports environment variables (MLEAPROXY_LDAP_DEBUG=true)
 * - Supports YAML configuration
 * - Integrates with Spring's property resolution
 * - Bean Validation support
 */
@ConfigurationProperties(prefix = "mleaproxy")
@Validated
public class MleaProxyProperties {
    
    /**
     * In-memory LDAP directory servers.
     * Map key is the server name (e.g., "marklogic").
     */
    private Map<String, DirectoryServerProperties> directoryServers = new LinkedHashMap<>();
    
    /**
     * LDAP proxy listeners.
     * Map key is the listener name (e.g., "proxy").
     */
    private Map<String, LdapListenerProperties> ldapListeners = new LinkedHashMap<>();
    
    /**
     * SAML listeners.
     * Map key is the listener name (e.g., "saml").
     */
    private Map<String, SamlListenerProperties> samlListeners = new LinkedHashMap<>();
    
    /**
     * Backend LDAP server sets (for load balancing/failover).
     * Map key is the set name (e.g., "primary").
     */
    private Map<String, ServerSetProperties> ldapSets = new LinkedHashMap<>();
    
    /**
     * Backend LDAP servers.
     * Map key is the server name (e.g., "server1").
     */
    private Map<String, LdapServerProperties> ldapServers = new LinkedHashMap<>();
    
    /**
     * Request processors for LDAP operations.
     * Map key is the processor name (e.g., "jsonauth").
     */
    private Map<String, RequestProcessorProperties> requestProcessors = new LinkedHashMap<>();
    
    /**
     * Kerberos KDC configuration.
     */
    @NestedConfigurationProperty
    private KerberosProperties kerberos = new KerberosProperties();
    
    /**
     * Enable LDAP SDK debug output.
     */
    private boolean ldapDebug = false;
    
    /**
     * Enable SAML debug logging.
     */
    private boolean samlDebug = false;
    
    /**
     * Path to SAML CA certificate file.
     */
    private String samlCaPath;
    
    /**
     * Path to SAML private key file.
     */
    private String samlKeyPath;
    
    /**
     * SAML assertion validity duration in seconds.
     */
    private int samlResponseValidity = 300;
    
    /**
     * Enable SSL certificate verification for backend connections.
     * Default is false (trust all) for testing environments.
     */
    private boolean sslVerifyCertificates = false;

    // Getters and Setters

    public Map<String, DirectoryServerProperties> getDirectoryServers() {
        return directoryServers;
    }

    public void setDirectoryServers(Map<String, DirectoryServerProperties> directoryServers) {
        this.directoryServers = directoryServers;
    }

    public Map<String, LdapListenerProperties> getLdapListeners() {
        return ldapListeners;
    }

    public void setLdapListeners(Map<String, LdapListenerProperties> ldapListeners) {
        this.ldapListeners = ldapListeners;
    }

    public Map<String, SamlListenerProperties> getSamlListeners() {
        return samlListeners;
    }

    public void setSamlListeners(Map<String, SamlListenerProperties> samlListeners) {
        this.samlListeners = samlListeners;
    }

    public Map<String, ServerSetProperties> getLdapSets() {
        return ldapSets;
    }

    public void setLdapSets(Map<String, ServerSetProperties> ldapSets) {
        this.ldapSets = ldapSets;
    }

    public Map<String, LdapServerProperties> getLdapServers() {
        return ldapServers;
    }

    public void setLdapServers(Map<String, LdapServerProperties> ldapServers) {
        this.ldapServers = ldapServers;
    }

    public Map<String, RequestProcessorProperties> getRequestProcessors() {
        return requestProcessors;
    }

    public void setRequestProcessors(Map<String, RequestProcessorProperties> requestProcessors) {
        this.requestProcessors = requestProcessors;
    }

    public KerberosProperties getKerberos() {
        return kerberos;
    }

    public void setKerberos(KerberosProperties kerberos) {
        this.kerberos = kerberos;
    }

    public boolean isLdapDebug() {
        return ldapDebug;
    }

    public void setLdapDebug(boolean ldapDebug) {
        this.ldapDebug = ldapDebug;
    }

    public boolean isSamlDebug() {
        return samlDebug;
    }

    public void setSamlDebug(boolean samlDebug) {
        this.samlDebug = samlDebug;
    }

    public String getSamlCaPath() {
        return samlCaPath;
    }

    public void setSamlCaPath(String samlCaPath) {
        this.samlCaPath = samlCaPath;
    }

    public String getSamlKeyPath() {
        return samlKeyPath;
    }

    public void setSamlKeyPath(String samlKeyPath) {
        this.samlKeyPath = samlKeyPath;
    }

    public int getSamlResponseValidity() {
        return samlResponseValidity;
    }

    public void setSamlResponseValidity(int samlResponseValidity) {
        this.samlResponseValidity = samlResponseValidity;
    }

    public boolean isSslVerifyCertificates() {
        return sslVerifyCertificates;
    }

    public void setSslVerifyCertificates(boolean sslVerifyCertificates) {
        this.sslVerifyCertificates = sslVerifyCertificates;
    }

    @Override
    public String toString() {
        return "MleaProxyProperties{" +
                "directoryServers=" + directoryServers.keySet() +
                ", ldapListeners=" + ldapListeners.keySet() +
                ", samlListeners=" + samlListeners.keySet() +
                ", ldapSets=" + ldapSets.keySet() +
                ", ldapServers=" + ldapServers.keySet() +
                ", ldapDebug=" + ldapDebug +
                ", kerberos.enabled=" + kerberos.isEnabled() +
                '}';
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/marklogic/configuration/properties/MleaProxyProperties.java
git commit -m "feat(config): add MleaProxyProperties root configuration class"
```

---

## Task 9: Create MleaProxyPropertiesConfig

**Files:**
- Create: `src/main/java/com/marklogic/configuration/MleaProxyPropertiesConfig.java`

- [ ] **Step 1: Create the PropertySource configuration class**

```java
package com.marklogic.configuration;

import com.marklogic.configuration.properties.MleaProxyProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

/**
 * Configuration class that enables MleaProxyProperties and configures
 * property file loading order.
 * 
 * Property sources are loaded in order, with later sources overriding earlier ones.
 * Spring Boot command line args (--property=value) have highest priority automatically.
 * 
 * Load order (lowest to highest priority):
 * 1. classpath:mleaproxy.properties (bundled defaults)
 * 2. /etc/mleaproxy.properties (system-wide)
 * 3. ${user.home}/mleaproxy.properties (user home)
 * 4. ./mleaproxy.properties (current directory)
 * 5. Protocol-specific files (ldap.properties, saml.properties, etc.)
 * 6. System properties (-D flags)
 * 7. Command line args (--property=value) - HIGHEST PRIORITY
 */
@Configuration
@EnableConfigurationProperties(MleaProxyProperties.class)
@PropertySources({
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
    // Spring Boot handles property binding automatically
    // Command line args (--mleaproxy.property=value) have highest priority
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/marklogic/configuration/MleaProxyPropertiesConfig.java
git commit -m "feat(config): add MleaProxyPropertiesConfig for property source configuration"
```

---

## Task 10: Create Default mleaproxy.properties with New Keys

**Files:**
- Create: `src/main/resources/mleaproxy.properties`

- [ ] **Step 1: Create default configuration file**

```properties
# ================================================================
# MLEAProxy Default Configuration
# ================================================================
# This file contains sensible defaults for development/testing.
# Override these values in your own mleaproxy.properties file.
#
# Property sources (lowest to highest priority):
# 1. This file (classpath:mleaproxy.properties)
# 2. /etc/mleaproxy.properties
# 3. ${HOME}/mleaproxy.properties
# 4. ./mleaproxy.properties
# 5. Command line args (--mleaproxy.property=value)
# ================================================================

# ----------------------------------------------------------------
# Global Settings
# ----------------------------------------------------------------
mleaproxy.ldap-debug=false
mleaproxy.saml-debug=false
mleaproxy.ssl-verify-certificates=false
mleaproxy.saml-response-validity=300

# ----------------------------------------------------------------
# In-Memory Directory Server: marklogic
# ----------------------------------------------------------------
mleaproxy.directory-servers.marklogic.name=MarkLogic Local Directory
mleaproxy.directory-servers.marklogic.ip-address=0.0.0.0
mleaproxy.directory-servers.marklogic.port=60389
mleaproxy.directory-servers.marklogic.base-dn=dc=MarkLogic,dc=Local
mleaproxy.directory-servers.marklogic.admin-dn=cn=Directory Manager
mleaproxy.directory-servers.marklogic.admin-password=password

# ----------------------------------------------------------------
# LDAP Listener: proxy
# ----------------------------------------------------------------
mleaproxy.ldap-listeners.proxy.ip-address=0.0.0.0
mleaproxy.ldap-listeners.proxy.port=10389
mleaproxy.ldap-listeners.proxy.secure=false
mleaproxy.ldap-listeners.proxy.debug-level=INFO
mleaproxy.ldap-listeners.proxy.ldap-mode=internal
mleaproxy.ldap-listeners.proxy.ldap-sets=internal
mleaproxy.ldap-listeners.proxy.request-processor=jsonauth
mleaproxy.ldap-listeners.proxy.description=LDAP Authentication Proxy

# ----------------------------------------------------------------
# Server Set: internal (connects to in-memory server)
# ----------------------------------------------------------------
mleaproxy.ldap-sets.internal.servers=localhost
mleaproxy.ldap-sets.internal.secure=false

# ----------------------------------------------------------------
# Backend Server: localhost (the in-memory server)
# ----------------------------------------------------------------
mleaproxy.ldap-servers.localhost.host=localhost
mleaproxy.ldap-servers.localhost.port=60389

# ----------------------------------------------------------------
# Request Processor: jsonauth (JSON-based authentication)
# ----------------------------------------------------------------
mleaproxy.request-processors.jsonauth.auth-class=com.marklogic.processors.JsonRequestProcessor
mleaproxy.request-processors.jsonauth.debug-level=INFO

# ----------------------------------------------------------------
# Kerberos KDC (disabled by default)
# ----------------------------------------------------------------
mleaproxy.kerberos.enabled=false
mleaproxy.kerberos.realm=MARKLOGIC.LOCAL
mleaproxy.kerberos.kdc-host=localhost
mleaproxy.kerberos.kdc-port=60088
mleaproxy.kerberos.admin-port=60749
mleaproxy.kerberos.work-dir=./kerberos
mleaproxy.kerberos.debug=false
```

- [ ] **Step 2: Verify the file is readable**

```bash
cat src/main/resources/mleaproxy.properties | head -20
```

Expected: First 20 lines of the file displayed

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/mleaproxy.properties
git commit -m "feat(config): add default mleaproxy.properties with Spring-style keys"
```

---

## Task 11: Update ApplicationListener to Use MleaProxyProperties

**Files:**
- Modify: `src/main/java/com/marklogic/handlers/ApplicationListener.java`

- [ ] **Step 1: Update imports and add MleaProxyProperties injection**

Replace the imports and class declaration section. Change from:

```java
package com.marklogic.handlers;

import com.marklogic.beans.SamlBean;
import com.marklogic.configuration.ApplicationConfig;
import com.marklogic.service.*;
import org.aeonbits.owner.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.Security;
import java.util.Arrays;
```

To:

```java
package com.marklogic.handlers;

import com.marklogic.beans.SamlBean;
import com.marklogic.configuration.properties.MleaProxyProperties;
import com.marklogic.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.Security;
import java.util.Arrays;
```

- [ ] **Step 2: Update class fields**

Replace the field declarations. Change from:

```java
    private KerberosKDCServer kerberosKdc;
    private ApplicationConfig applicationConfig;
```

To:

```java
    @Autowired
    private MleaProxyProperties config;

    private KerberosKDCServer kerberosKdc;
```

- [ ] **Step 3: Update the run() method**

Replace the run method. Change from:

```java
    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("Starting MLEAProxy initialization");

        // Initialize configuration
        ApplicationConfig cfg = initializeConfiguration();
        this.applicationConfig = cfg;

        // Setup core components
        setupSecurityProviders();
        setupLDAPDebugging(cfg);
        logApplicationArguments(args);

        // Start services
        ldapServerService.startInMemoryServers(cfg);
        startKerberosKDC(cfg);
        ldapListenerService.startLDAPListeners(cfg);
        initializeSAMLConfiguration(cfg);

        // Display startup summary
        startupDisplayService.displayStartupSummary();

        logger.info("MLEAProxy initialization complete");
    }
```

To:

```java
    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("Starting MLEAProxy initialization");

        // Setup core components
        setupSecurityProviders();
        setupLDAPDebugging();
        logApplicationArguments(args);

        // Start services
        ldapServerService.startInMemoryServers(config);
        startKerberosKDC();
        ldapListenerService.startLDAPListeners(config);
        initializeSAMLConfiguration();

        // Display startup summary
        startupDisplayService.displayStartupSummary();

        logger.info("MLEAProxy initialization complete");
    }
```

- [ ] **Step 4: Remove initializeConfiguration() method entirely**

Delete this method (no longer needed - Spring handles it):

```java
    /**
     * Initializes the application configuration from properties files.
     * ...
     */
    private ApplicationConfig initializeConfiguration() {
        // Set mleaproxy.properties System Property if not passed on the commandline
        if (System.getProperty("mleaproxy.properties") == null) {
            System.setProperty("mleaproxy.properties", "./mleaproxy.properties");
        }

        ApplicationConfig cfg = ConfigFactory.create(ApplicationConfig.class);
        logger.debug("Configuration loaded: {}", cfg);

        return cfg;
    }
```

- [ ] **Step 5: Update setupLDAPDebugging() method**

Change from:

```java
    private void setupLDAPDebugging(ApplicationConfig cfg) {
        logger.debug("LDAP debug flag: {}", cfg.ldapDebug());
        if (cfg.ldapDebug()) {
            System.setProperty("com.unboundid.ldap.sdk.debug.enabled", "true");
            System.setProperty("com.unboundid.ldap.sdk.debug.type", "ldap");
            logger.info("LDAP SDK debugging enabled");
        }
    }
```

To:

```java
    private void setupLDAPDebugging() {
        logger.debug("LDAP debug flag: {}", config.isLdapDebug());
        if (config.isLdapDebug()) {
            System.setProperty("com.unboundid.ldap.sdk.debug.enabled", "true");
            System.setProperty("com.unboundid.ldap.sdk.debug.type", "ldap");
            logger.info("LDAP SDK debugging enabled");
        }
    }
```

- [ ] **Step 6: Update startKerberosKDC() method**

Change from:

```java
    private void startKerberosKDC(ApplicationConfig cfg) throws Exception {
        try {
            com.marklogic.configuration.KerberosConfig krbCfg =
                ConfigFactory.create(com.marklogic.configuration.KerberosConfig.class);

            if (krbCfg.kerberosEnabled()) {
                logger.info("Kerberos KDC enabled, starting embedded KDC");
                kerberosKdc = new KerberosKDCServer(krbCfg);
                kerberosKdc.start();
                logger.info("Kerberos KDC started successfully");
            } else {
                logger.debug("Kerberos KDC disabled in configuration (set kerberos.enabled=true to enable)");
            }
        } catch (Exception e) {
            logger.error("Failed to start Kerberos KDC: {}", e.getMessage(), e);
            throw e;
        }
    }
```

To:

```java
    private void startKerberosKDC() throws Exception {
        try {
            if (config.getKerberos().isEnabled()) {
                logger.info("Kerberos KDC enabled, starting embedded KDC");
                kerberosKdc = new KerberosKDCServer(config.getKerberos());
                kerberosKdc.start();
                logger.info("Kerberos KDC started successfully");
            } else {
                logger.debug("Kerberos KDC disabled in configuration (set mleaproxy.kerberos.enabled=true to enable)");
            }
        } catch (Exception e) {
            logger.error("Failed to start Kerberos KDC: {}", e.getMessage(), e);
            throw e;
        }
    }
```

- [ ] **Step 7: Update initializeSAMLConfiguration() method**

Change from:

```java
    private void initializeSAMLConfiguration(ApplicationConfig cfg) {
        saml.setCfg(cfg);
        logger.debug("SAML configuration initialized");
    }
```

To:

```java
    private void initializeSAMLConfiguration() {
        saml.setConfig(config);
        logger.debug("SAML configuration initialized");
    }
```

- [ ] **Step 8: Verify compilation**

```bash
mvn compile -q 2>&1 | head -20
```

Expected: Compilation errors about KerberosKDCServer and SamlBean (we'll fix these in subsequent tasks)

- [ ] **Step 9: Commit work in progress**

```bash
git add src/main/java/com/marklogic/handlers/ApplicationListener.java
git commit -m "refactor(config): update ApplicationListener to use MleaProxyProperties

WIP: KerberosKDCServer and SamlBean need updating"
```

---

## Task 12: Update LDAPServerService

**Files:**
- Modify: `src/main/java/com/marklogic/service/LDAPServerService.java`

- [ ] **Step 1: Update imports**

Change from:

```java
import com.marklogic.configuration.ApplicationConfig;
import com.marklogic.configuration.DSConfig;
```

To:

```java
import com.marklogic.configuration.properties.DirectoryServerProperties;
import com.marklogic.configuration.properties.MleaProxyProperties;
```

Remove:

```java
import org.aeonbits.owner.ConfigFactory;
import java.util.HashMap;
import java.util.Map;
```

- [ ] **Step 2: Update startInMemoryServers() method signature and body**

Change from:

```java
    public void startInMemoryServers(ApplicationConfig config) throws Exception {
        String[] directoryServers = config.directoryServers();
        logger.info("Starting {} in-memory LDAP directory server(s)", directoryServers.length);

        for (String directoryServer : directoryServers) {
            startInMemoryServer(config, directoryServer);
        }

        logger.info("All in-memory LDAP servers started successfully");
    }
```

To:

```java
    public void startInMemoryServers(MleaProxyProperties config) throws Exception {
        var directoryServers = config.getDirectoryServers();
        if (directoryServers.isEmpty()) {
            logger.info("No in-memory LDAP directory servers configured");
            return;
        }
        
        logger.info("Starting {} in-memory LDAP directory server(s)", directoryServers.size());

        for (String serverName : directoryServers.keySet()) {
            DirectoryServerProperties dsConfig = directoryServers.get(serverName);
            startInMemoryServer(serverName, dsConfig);
        }

        logger.info("All in-memory LDAP servers started successfully");
    }
```

- [ ] **Step 3: Update startInMemoryServer() method**

Change from:

```java
    private void startInMemoryServer(ApplicationConfig config, String serverName) throws Exception {
        logger.info("Configuring in-memory LDAP server: {}", serverName);

        // Load server configuration with variable expansion
        Map<String, String> expVars = new HashMap<>();
        expVars.put("directoryServer", serverName);
        DSConfig dsConfig = ConfigFactory.create(DSConfig.class, expVars);

        // Create directory server configuration
        DN baseDN = new DN(dsConfig.dsBaseDN());
        InMemoryDirectoryServerConfig serverConfig = new InMemoryDirectoryServerConfig(baseDN);

        // Set admin credentials
        serverConfig.addAdditionalBindCredentials(dsConfig.dsAdminDN(), dsConfig.dsAdminPW());

        // Configure listener
        String ipAddress = dsConfig.dsIpAddress();
        int port = dsConfig.dsPort();
```

To:

```java
    private void startInMemoryServer(String serverName, DirectoryServerProperties dsConfig) throws Exception {
        logger.info("Configuring in-memory LDAP server: {}", serverName);

        // Create directory server configuration
        DN baseDN = new DN(dsConfig.getBaseDn());
        InMemoryDirectoryServerConfig serverConfig = new InMemoryDirectoryServerConfig(baseDN);

        // Set admin credentials
        serverConfig.addAdditionalBindCredentials(dsConfig.getAdminDn(), dsConfig.getAdminPassword());

        // Configure listener
        String ipAddress = dsConfig.getIpAddress();
        int port = dsConfig.getPort();
```

- [ ] **Step 4: Update the rest of startInMemoryServer() - LDIF import and logging**

The remaining code uses `dsConfig.dsBaseDN()` etc. Update all references:

Change:
- `dsConfig.dsBaseDN()` → `dsConfig.getBaseDn()`
- `dsConfig.dsLDIF()` → `dsConfig.getLdifPath()`

In the logging statement at the end, change from:

```java
        logger.info("In-memory LDAP server '{}' started on {}:{} with base DN '{}'",
                   serverName, ipAddress, port, dsConfig.dsBaseDN());
```

To:

```java
        logger.info("In-memory LDAP server '{}' started on {}:{} with base DN '{}'",
                   serverName, ipAddress, port, dsConfig.getBaseDn());
```

- [ ] **Step 5: Update importLDIFData() method signature**

Change from:

```java
    private void importLDIFData(InMemoryDirectoryServer server, DSConfig dsConfig)
```

To:

```java
    private void importLDIFData(InMemoryDirectoryServer server, DirectoryServerProperties dsConfig)
```

And update the body to use `dsConfig.getLdifPath()` instead of `dsConfig.dsLDIF()`.

- [ ] **Step 6: Verify compilation**

```bash
mvn compile -q 2>&1 | head -20
```

Expected: Possible errors from other classes, but LDAPServerService should compile

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/marklogic/service/LDAPServerService.java
git commit -m "refactor(config): update LDAPServerService to use MleaProxyProperties"
```

---

## Task 13: Update LDAPListenerService

**Files:**
- Modify: `src/main/java/com/marklogic/service/LDAPListenerService.java`

- [ ] **Step 1: Update imports**

Remove:

```java
import com.marklogic.configuration.*;
import org.aeonbits.owner.ConfigFactory;
```

Add:

```java
import com.marklogic.configuration.properties.*;
```

Keep existing imports for UnboundID and other dependencies.

- [ ] **Step 2: Add MleaProxyProperties field**

Add after the existing `@Autowired` fields:

```java
    @Autowired
    private MleaProxyProperties mleaProxyProperties;
```

- [ ] **Step 3: Update startLDAPListeners() method**

Change from:

```java
    public void startLDAPListeners(ApplicationConfig config) throws Exception {
        String[] listeners = config.ldaplisteners();
        if (listeners == null || listeners.length == 0) {
            logger.info("No LDAP listener configurations found");
            return;
        }

        logger.info("Starting {} LDAP listener(s)", listeners.length);

        for (String listenerName : listeners) {
            startListener(config, listenerName);
        }

        logger.info("All LDAP listeners started successfully");
    }
```

To:

```java
    public void startLDAPListeners(MleaProxyProperties config) throws Exception {
        var listeners = config.getLdapListeners();
        if (listeners.isEmpty()) {
            logger.info("No LDAP listener configurations found");
            return;
        }

        logger.info("Starting {} LDAP listener(s)", listeners.size());

        for (String listenerName : listeners.keySet()) {
            LdapListenerProperties listenerCfg = listeners.get(listenerName);
            startListener(listenerName, listenerCfg);
        }

        logger.info("All LDAP listeners started successfully");
    }
```

- [ ] **Step 4: Update startListener() method signature and config loading**

Change from:

```java
    private void startListener(ApplicationConfig config, String listenerName) throws Exception {
        logger.info("Starting LDAP listener: {}", listenerName);

        // Load listener configuration with variable expansion
        Map<String, String> expVars = new HashMap<>();
        expVars.put("listener", listenerName);
        LDAPListenersConfig listenerCfg = ConfigFactory.create(LDAPListenersConfig.class, expVars);
```

To:

```java
    private void startListener(String listenerName, LdapListenerProperties listenerCfg) throws Exception {
        logger.info("Starting LDAP listener: {}", listenerName);
```

- [ ] **Step 5: Update property access in startListener()**

Throughout the method, replace Owner-style calls with POJO getters:

- `listenerCfg.debugLevel()` → `listenerCfg.getDebugLevel()`
- `listenerCfg.listenerIpAddress()` → `listenerCfg.getIpAddress()`
- `listenerCfg.listenerPort()` → `listenerCfg.getPort()`
- `listenerCfg.listenerRequestHandler()` → `listenerCfg.getRequestHandler()`
- `listenerCfg.listenerLDAPSet()` → `listenerCfg.getLdapSets().toArray(new String[0])`
- `listenerCfg.listenerLDAPMode()` → `listenerCfg.getLdapMode()`
- `listenerCfg.secureListener()` → `listenerCfg.isSecure()`
- `listenerCfg.listenerRequestProcessor()` → `listenerCfg.getRequestProcessor()`
- `listenerCfg.listenerDescription()` → `listenerCfg.getDescription()`
- `listenerCfg.listenerKeyStore()` → `listenerCfg.getKeystore()`
- `listenerCfg.listenerKeyStorePassword()` → `listenerCfg.getKeystorePassword()`
- `listenerCfg.listenerTrustStore()` → `listenerCfg.getTruststore()`
- `listenerCfg.listenerTrustStorePassword()` → `listenerCfg.getTruststorePassword()`

- [ ] **Step 6: Update buildServerSet() method**

Change from:

```java
    private ServerSet buildServerSet(String[] serverSetsList, String mode)
            throws Exception {
        logger.debug("Building server sets with mode: {}", mode);

        ArrayList<ServerSet> sets = new ArrayList<>();

        for (String setName : serverSetsList) {
            logger.debug("Configuring ServerSet: {}", setName);

            // Load server set configuration
            Map<String, Object> setVars = new HashMap<>();
            setVars.put("serverSet", setName);
            SetsConfig setsCfg = ConfigFactory.create(SetsConfig.class, setVars);
```

To:

```java
    private ServerSet buildServerSet(String[] serverSetsList, String mode)
            throws Exception {
        logger.debug("Building server sets with mode: {}", mode);

        ArrayList<ServerSet> sets = new ArrayList<>();

        for (String setName : serverSetsList) {
            logger.debug("Configuring ServerSet: {}", setName);

            // Load server set configuration from injected properties
            ServerSetProperties setsCfg = mleaProxyProperties.getLdapSets().get(setName);
            if (setsCfg == null) {
                throw new IllegalArgumentException("Server set not found: " + setName);
            }
```

- [ ] **Step 7: Update server iteration in buildServerSet()**

Change from:

```java
            for (String serverName : setsCfg.servers()) {
                Map<String, String> serverVars = new HashMap<>();
                serverVars.put("server", serverName);
                ServersConfig serverCfg = ConfigFactory.create(ServersConfig.class, serverVars);

                if (!"INTERNAL".equalsIgnoreCase(mode)) {
                    logger.debug("Backend LDAP server host: {}, port: {}",
                               serverCfg.serverHost(), serverCfg.serverPort());
                    hostAddresses.add(serverCfg.serverHost());
                    hostPorts.add(serverCfg.serverPort());
                }
            }
```

To:

```java
            for (String serverName : setsCfg.getServers()) {
                LdapServerProperties serverCfg = mleaProxyProperties.getLdapServers().get(serverName);
                if (serverCfg == null) {
                    throw new IllegalArgumentException("LDAP server not found: " + serverName);
                }

                if (!"INTERNAL".equalsIgnoreCase(mode)) {
                    logger.debug("Backend LDAP server host: {}, port: {}",
                               serverCfg.getHost(), serverCfg.getPort());
                    hostAddresses.add(serverCfg.getHost());
                    hostPorts.add(serverCfg.getPort());
                }
            }
```

- [ ] **Step 8: Update SetsConfig references to ServerSetProperties**

Throughout buildServerSet() and createServerSetForMode(), change:

- `setsCfg.serverSetSecure()` → `setsCfg.isSecure()`

- [ ] **Step 9: Update createSecureSocketFactory() method signature**

Change from:

```java
    private SSLSocketFactory createSecureSocketFactory(SetsConfig cfg)
```

To:

```java
    private SSLSocketFactory createSecureSocketFactory(ServerSetProperties cfg)
```

And update the body:
- `cfg.serverSetStoreType()` → `cfg.getStoreType()`
- `cfg.serverSetKeyPath()` → `cfg.getKeyPath()`
- `cfg.serverSetCertPath()` → `cfg.getCertPath()`
- `cfg.serverSetCAPath()` → `cfg.getCaPath()`
- `cfg.serverSetPfxPath()` → `cfg.getPfxPath()`
- `cfg.serverSetPfxPassword()` → `cfg.getPfxPassword()`
- `cfg.serverSetKeyStore()` → `cfg.getKeystore()`
- `cfg.serverSetKeyStorePassword()` → `cfg.getKeystorePassword()`
- `cfg.serverSetTrustStore()` → `cfg.getTruststore()`
- `cfg.serverSetTrustStorePassword()` → `cfg.getTruststorePassword()`

- [ ] **Step 10: Update createServerSocketFactory() method signature**

Change from:

```java
    private ServerSocketFactory createServerSocketFactory(ApplicationConfig appConfig, LDAPListenersConfig cfg)
```

To:

```java
    private ServerSocketFactory createServerSocketFactory(LdapListenerProperties cfg)
```

And update the body to use:
- `cfg.getKeystore()` instead of `cfg.listenerKeyStore()`
- `cfg.getKeystorePassword()` instead of `cfg.listenerKeyStorePassword()`
- etc.

- [ ] **Step 11: Remove unused imports**

Remove any remaining references to old config classes and HashMap/Map imports that are no longer used.

- [ ] **Step 12: Verify compilation**

```bash
mvn compile -q 2>&1 | head -30
```

- [ ] **Step 13: Commit**

```bash
git add src/main/java/com/marklogic/service/LDAPListenerService.java
git commit -m "refactor(config): update LDAPListenerService to use MleaProxyProperties"
```

---

## Task 14: Update LDAPRequestHandler

**Files:**
- Modify: `src/main/java/com/marklogic/handlers/LDAPRequestHandler.java`

- [ ] **Step 1: Update imports**

Remove:

```java
import com.marklogic.configuration.ProcessorConfig;
import org.aeonbits.owner.ConfigFactory;
```

Add:

```java
import com.marklogic.configuration.properties.MleaProxyProperties;
import com.marklogic.configuration.properties.RequestProcessorProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
```

- [ ] **Step 2: Add Spring component annotation and config injection**

The LDAPRequestHandler is instantiated via reflection, so we need a different approach. Add a static holder for the config:

```java
/**
 * Static holder for MleaProxyProperties since this handler is instantiated via reflection.
 */
private static MleaProxyProperties staticConfig;

public static void setStaticConfig(MleaProxyProperties config) {
    staticConfig = config;
}
```

- [ ] **Step 3: Update getProcessor() method**

Change from:

```java
    private IRequestProcessor getProcessor(String requestProcessor) {
        // ... existing caching logic ...
        
        Map<String, Object> appVars = new HashMap<>();
        appVars.put("requestProcessor", requestProcessor);
        ProcessorConfig processorCfg = ConfigFactory.create(ProcessorConfig.class, appVars);
        
        String processorClass = processorCfg.requestProcessorClass();
```

To:

```java
    private IRequestProcessor getProcessor(String requestProcessor) {
        // ... existing caching logic ...
        
        if (staticConfig == null) {
            throw new IllegalStateException("MleaProxyProperties not initialized. Call LDAPRequestHandler.setStaticConfig() first.");
        }
        
        RequestProcessorProperties processorCfg = staticConfig.getRequestProcessors().get(requestProcessor);
        if (processorCfg == null) {
            throw new IllegalArgumentException("Request processor not found: " + requestProcessor);
        }
        
        String processorClass = processorCfg.getAuthClass();
```

- [ ] **Step 4: Update processor parameter access**

Change from:

```java
        processorCfg.parm1()
        processorCfg.parm2()
        // etc.
```

To:

```java
        processorCfg.getParam(0)
        processorCfg.getParam(1)
        // etc.
```

- [ ] **Step 5: Verify compilation**

```bash
mvn compile -q 2>&1 | head -20
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/marklogic/handlers/LDAPRequestHandler.java
git commit -m "refactor(config): update LDAPRequestHandler to use MleaProxyProperties"
```

---

## Task 15: Update KerberosKDCServer

**Files:**
- Modify: `src/main/java/com/marklogic/handlers/KerberosKDCServer.java`

- [ ] **Step 1: Update imports**

Change from:

```java
import com.marklogic.configuration.KerberosConfig;
```

To:

```java
import com.marklogic.configuration.properties.KerberosProperties;
```

- [ ] **Step 2: Update constructor and field**

Change the field type and constructor parameter from `KerberosConfig` to `KerberosProperties`:

```java
    private final KerberosProperties config;

    public KerberosKDCServer(KerberosProperties config) {
        this.config = config;
    }
```

- [ ] **Step 3: Update all config access methods**

Throughout the class, change Owner-style calls to POJO getters:

- `config.kerberosEnabled()` → `config.isEnabled()`
- `config.kerberosRealm()` → `config.getRealm()`
- `config.kdcHost()` → `config.getKdcHost()`
- `config.kdcPort()` → `config.getKdcPort()`
- `config.adminPort()` → `config.getAdminPort()`
- `config.importPrincipalsFromLdap()` → `config.isImportPrincipalsFromLdap()`
- `config.ldapBaseDn()` → `config.getLdapBaseDn()`
- `config.servicePrincipals()` → `config.getServicePrincipals().toArray(new String[0])`
- `config.workDir()` → `config.getWorkDir()`
- `config.debug()` → `config.isDebug()`
- `config.ticketLifetime()` → `config.getTicketLifetime()`
- `config.renewableLifetime()` → `config.getRenewableLifetime()`
- `config.clockSkew()` → `config.getClockSkew()`

- [ ] **Step 4: Verify compilation**

```bash
mvn compile -q 2>&1 | head -20
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/marklogic/handlers/KerberosKDCServer.java
git commit -m "refactor(config): update KerberosKDCServer to use KerberosProperties"
```

---

## Task 16: Update SamlBean

**Files:**
- Modify: `src/main/java/com/marklogic/beans/SamlBean.java`

- [ ] **Step 1: Update imports**

Change from:

```java
import com.marklogic.configuration.ApplicationConfig;
```

To:

```java
import com.marklogic.configuration.properties.MleaProxyProperties;
```

- [ ] **Step 2: Update field and setter**

Change from:

```java
    private ApplicationConfig cfg;

    public void setCfg(ApplicationConfig cfg) {
        this.cfg = cfg;
    }

    public ApplicationConfig getCfg() {
        return cfg;
    }
```

To:

```java
    private MleaProxyProperties config;

    public void setConfig(MleaProxyProperties config) {
        this.config = config;
    }

    public MleaProxyProperties getConfig() {
        return config;
    }
```

- [ ] **Step 3: Update any property access**

If there are references to `cfg.SamlCaPath()` etc., update to:
- `config.getSamlCaPath()`
- `config.getSamlKeyPath()`
- `config.getSamlResponseValidity()`
- `config.isSamlDebug()`

- [ ] **Step 4: Verify compilation**

```bash
mvn compile -q 2>&1 | head -20
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/marklogic/beans/SamlBean.java
git commit -m "refactor(config): update SamlBean to use MleaProxyProperties"
```

---

## Task 17: Initialize Static Config in ApplicationListener

**Files:**
- Modify: `src/main/java/com/marklogic/handlers/ApplicationListener.java`

- [ ] **Step 1: Add static config initialization in run() method**

Add after the logging setup and before starting services:

```java
        // Initialize static config for reflection-based handlers
        LDAPRequestHandler.setStaticConfig(config);
```

- [ ] **Step 2: Add import if not already present**

```java
import com.marklogic.handlers.LDAPRequestHandler;
```

- [ ] **Step 3: Verify compilation**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/marklogic/handlers/ApplicationListener.java
git commit -m "refactor(config): initialize LDAPRequestHandler static config"
```

---

## Task 18: Update pom.xml - Remove Owner Dependency

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Remove Aeonbits Owner dependency**

Find and remove this dependency block:

```xml
        <dependency>
            <groupId>org.aeonbits.owner</groupId>
            <artifactId>owner</artifactId>
            <version>1.0.12</version>
        </dependency>
```

- [ ] **Step 2: Verify build still works**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore(deps): remove Aeonbits Owner dependency"
```

---

## Task 19: Delete Old Owner Config Interfaces

**Files:**
- Delete: `src/main/java/com/marklogic/configuration/ApplicationConfig.java`
- Delete: `src/main/java/com/marklogic/configuration/DSConfig.java`
- Delete: `src/main/java/com/marklogic/configuration/KerberosConfig.java`
- Delete: `src/main/java/com/marklogic/configuration/LDAPListenersConfig.java`
- Delete: `src/main/java/com/marklogic/configuration/SAMLListenersConfig.java`
- Delete: `src/main/java/com/marklogic/configuration/SetsConfig.java`
- Delete: `src/main/java/com/marklogic/configuration/ServersConfig.java`
- Delete: `src/main/java/com/marklogic/configuration/ProcessorConfig.java`

- [ ] **Step 1: Delete all old config interfaces**

```bash
rm -f src/main/java/com/marklogic/configuration/ApplicationConfig.java
rm -f src/main/java/com/marklogic/configuration/DSConfig.java
rm -f src/main/java/com/marklogic/configuration/KerberosConfig.java
rm -f src/main/java/com/marklogic/configuration/LDAPListenersConfig.java
rm -f src/main/java/com/marklogic/configuration/SAMLListenersConfig.java
rm -f src/main/java/com/marklogic/configuration/SetsConfig.java
rm -f src/main/java/com/marklogic/configuration/ServersConfig.java
rm -f src/main/java/com/marklogic/configuration/ProcessorConfig.java
```

- [ ] **Step 2: Verify build still works**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS (no more references to deleted files)

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: remove old Aeonbits Owner config interfaces

Replaced by Spring @ConfigurationProperties classes in
com.marklogic.configuration.properties package."
```

---

## Task 20: Run Full Test Suite

**Files:**
- All test files

- [ ] **Step 1: Run all tests**

```bash
./run-tests.sh all
```

Expected: Tests may fail due to configuration format changes

- [ ] **Step 2: Document failing tests**

Note which tests fail and why - likely due to old property format in test configurations.

- [ ] **Step 3: Commit current state**

```bash
git add -A
git commit -m "test: run test suite after config migration

Some tests may need property format updates."
```

---

## Task 21: Update Test Configurations

**Files:**
- Modify: `src/test/resources/application.properties` (if exists)
- Modify: Any test-specific property files

- [ ] **Step 1: Check for test property files**

```bash
find src/test -name "*.properties" -o -name "*.yml"
```

- [ ] **Step 2: Update test properties to new format**

For each test property file, convert old keys to new format following the mapping in the spec.

Example conversion:
```properties
# Old format
ldaplistener.proxy.port=10389

# New format
mleaproxy.ldap-listeners.proxy.port=10389
```

- [ ] **Step 3: Run tests again**

```bash
./run-tests.sh all
```

Expected: More tests should pass now

- [ ] **Step 4: Commit**

```bash
git add src/test/
git commit -m "test: update test configurations to new property format"
```

---

## Task 22: Create Configuration Binding Test

**Files:**
- Create: `src/test/java/com/marklogic/configuration/MleaProxyPropertiesTest.java`

- [ ] **Step 1: Create test class**

```java
package com.marklogic.configuration;

import com.marklogic.configuration.properties.MleaProxyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "mleaproxy.ldap-debug=true",
    "mleaproxy.directory-servers.test.port=12345",
    "mleaproxy.directory-servers.test.base-dn=dc=Test,dc=Local",
    "mleaproxy.ldap-listeners.testlistener.port=10389",
    "mleaproxy.ldap-listeners.testlistener.ldap-mode=internal",
    "mleaproxy.kerberos.enabled=true",
    "mleaproxy.kerberos.realm=TEST.REALM"
})
class MleaProxyPropertiesTest {

    @Autowired
    private MleaProxyProperties config;

    @Test
    void shouldBindGlobalProperties() {
        assertTrue(config.isLdapDebug());
    }

    @Test
    void shouldBindDirectoryServerProperties() {
        var ds = config.getDirectoryServers().get("test");
        assertNotNull(ds);
        assertEquals(12345, ds.getPort());
        assertEquals("dc=Test,dc=Local", ds.getBaseDn());
    }

    @Test
    void shouldBindLdapListenerProperties() {
        var listener = config.getLdapListeners().get("testlistener");
        assertNotNull(listener);
        assertEquals(10389, listener.getPort());
        assertEquals("internal", listener.getLdapMode());
    }

    @Test
    void shouldBindKerberosProperties() {
        assertTrue(config.getKerberos().isEnabled());
        assertEquals("TEST.REALM", config.getKerberos().getRealm());
    }

    @Test
    void shouldHaveDefaultValues() {
        // Defaults from KerberosProperties
        assertEquals(60088, config.getKerberos().getKdcPort());
        assertEquals(300, config.getKerberos().getClockSkew());
    }
}
```

- [ ] **Step 2: Run the new test**

```bash
mvn test -Dtest=MleaProxyPropertiesTest -q
```

Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/marklogic/configuration/MleaProxyPropertiesTest.java
git commit -m "test: add MleaProxyProperties binding tests"
```

---

## Task 23: Verify Command Line Override Works

**Files:**
- None (manual verification)

- [ ] **Step 1: Build the application**

```bash
./build.sh clean package
```

- [ ] **Step 2: Test command line override**

```bash
java -jar target/mlesproxy-2.0.0.jar --mleaproxy.ldap-debug=true 2>&1 | head -30
```

Expected: Should see "LDAP SDK debugging enabled" in the output

- [ ] **Step 3: Document verification**

Create a note or update AGENTS.md that command line overrides now work:

```bash
echo "
## Configuration Override Verification ($(date))
Command line override verified working:
java -jar target/mlesproxy-2.0.0.jar --mleaproxy.ldap-debug=true
" >> docs/superpowers/plans/2026-05-18-spring-config-migration.md
```

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat(config): complete migration to Spring ConfigurationProperties

- All 8 Owner Config interfaces replaced with Spring POJOs
- Command line overrides (--property=value) now work correctly
- Property file loading order preserved
- All tests passing

BREAKING CHANGE: Property keys changed to Spring-style format.
See docs/superpowers/specs/2026-05-18-spring-config-migration-design.md
for complete key mapping."
```

---

## Task 24: Run Full Verification

- [ ] **Step 1: Clean build**

```bash
mvn clean package -q
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Run all tests**

```bash
./run-tests.sh all
```

Expected: All tests pass

- [ ] **Step 3: Quick smoke test**

```bash
timeout 10 java -jar target/mlesproxy-2.0.0.jar || true
```

Expected: Application starts without errors (will timeout after 10s)

---

## Summary

This plan migrates MLEAProxy from Aeonbits Owner to Spring ConfigurationProperties in 24 tasks:

1. **Tasks 1-8**: Create property POJO classes
2. **Task 9**: Create PropertySource configuration
3. **Task 10**: Create default properties file
4. **Tasks 11-17**: Update services to use new config
5. **Task 18**: Remove Owner dependency
6. **Task 19**: Delete old config interfaces
7. **Tasks 20-22**: Update and verify tests
8. **Tasks 23-24**: Final verification

Total estimated time: 2-4 hours for experienced developer
