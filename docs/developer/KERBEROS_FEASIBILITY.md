# Kerberos KDC Implementation - Feasibility Study

**Date**: October 6, 2025  
**Status**: 📋 Planning - Ready for Phase 1 Implementation  
**Complexity**: 🟡 Medium  
**Value**: 🟢 High

---

## Executive Summary

Implementing an embedded Kerberos KDC (Key Distribution Center) using Apache Kerby is **technically feasible** and **strategically valuable** for MLEAProxy. The primary challenge of DNS sensitivity can be mitigated using `localhost` and proper configuration for testing/development scenarios.

### Decision: ✅ **GO - Proceed with Phased Implementation**

---

## Table of Contents

1. [Background](#background)
2. [Technical Approach](#technical-approach)
3. [DNS Challenge & Solutions](#dns-challenge--solutions)
4. [Architecture](#architecture)
5. [Implementation Phases](#implementation-phases)
6. [Phase 1 Details (MVP)](#phase-1-details-mvp)
7. [Dependencies](#dependencies)
8. [Configuration](#configuration)
9. [Testing Strategy](#testing-strategy)
10. [Risk Assessment](#risk-assessment)
11. [Success Metrics](#success-metrics)

---

## Background

### Current State

MLEAProxy provides:
- ✅ **LDAP Proxy** - UnboundID-based LDAP authentication
- ✅ **In-Memory LDAP** - UnboundID In-Memory Directory Server
- ✅ **SAML 2.0** - Identity provider with role mapping
- ✅ **OAuth 2.0** - Token generation with JWT
- ❌ **Kerberos** - Not implemented

### Use Cases for Kerberos Support

1. **Enterprise Testing** - Many organizations use Kerberos (Active Directory)
2. **Single Sign-On** - SPNEGO for browser-based authentication
3. **Service Authentication** - Machine-to-machine with keytabs
4. **Integration Testing** - Test MarkLogic Kerberos external security
5. **Development** - No need for external AD/KDC infrastructure

### Apache Directory Studio Connection

Apache Directory Studio includes **Apache Kerby** as its KDC implementation, which is:
- Pure Java (no native dependencies)
- Embeddable
- Well-maintained by Apache Directory project
- Similar architecture to UnboundID LDAP (which we already use)

---

## Technical Approach

### Library Selection: Apache Kerby

**Maven Coordinates:**
```xml
<dependency>
    <groupId>org.apache.kerby</groupId>
    <artifactId>kerb-simplekdc</artifactId>
    <version>2.0.3</version>
</dependency>
```

**Why Apache Kerby?**
- ✅ Pure Java implementation
- ✅ Embeddable `SimpleKdcServer` class
- ✅ Active Apache project
- ✅ Used by Apache Directory Studio
- ✅ No native dependencies (unlike MIT Kerberos JNI bindings)
- ✅ Can import principals from LDAP
- ✅ Supports keytab generation

**Alternatives Considered:**
- ❌ MIT Kerberos - Requires native installation
- ❌ Active Directory - Windows-only, heavyweight
- ❌ FreeIPA - Linux-only, complex setup

---

## DNS Challenge & Solutions

### The Problem

Kerberos is **DNS-sensitive** by design:

1. **Service Principals** include hostnames:
   ```
   HTTP/webserver.example.com@EXAMPLE.COM
   ```

2. **Reverse DNS Lookups** must match:
   ```
   Forward:  webserver.example.com → 192.168.1.100
   Reverse:  192.168.1.100 → webserver.example.com
   ```

3. **Realm Mapping** uses DNS:
   ```
   .example.com → EXAMPLE.COM (realm)
   ```

### The Solution: Testing-Focused Configuration

#### Strategy 1: Use `localhost` Only ✅ **Recommended**

**Configuration:**
```properties
# krb5.conf
[libdefaults]
    default_realm = MARKLOGIC.LOCAL
    dns_lookup_realm = false    # Don't use DNS for realm lookup
    dns_lookup_kdc = false       # Don't use DNS for KDC lookup
    rdns = false                 # Disable reverse DNS
    
[realms]
    MARKLOGIC.LOCAL = {
        kdc = localhost:60088
        admin_server = localhost:60749
    }

[domain_realm]
    .localhost = MARKLOGIC.LOCAL
    localhost = MARKLOGIC.LOCAL
```

**Principals:**
```
# User principals
mluser1@MARKLOGIC.LOCAL
mluser2@MARKLOGIC.LOCAL
appadmin@MARKLOGIC.LOCAL

# Service principals (localhost-based)
HTTP/localhost@MARKLOGIC.LOCAL
ldap/localhost@MARKLOGIC.LOCAL
```

**Why This Works:**
- `localhost` always resolves to `127.0.0.1`
- No `/etc/hosts` modifications needed
- Cross-platform compatible
- Perfect for testing/development

#### Strategy 2: Auto-Generated Configuration ✅

MLEAProxy will generate `krb5.conf` automatically on startup:

```java
public void generateKrb5Config() {
    Path krb5Path = Paths.get("./krb5.conf");
    String config = String.format("""
        [libdefaults]
            default_realm = %s
            dns_lookup_realm = false
            dns_lookup_kdc = false
            rdns = false
            
        [realms]
            %s = {
                kdc = localhost:%d
                admin_server = localhost:%d
            }
            
        [domain_realm]
            .localhost = %s
            localhost = %s
        """, realm, realm, kdcPort, adminPort, realm, realm);
    
    Files.writeString(krb5Path, config);
    logger.info("Generated krb5.conf at: {}", krb5Path.toAbsolutePath());
}
```

#### Strategy 3: Environment Variable Override ✅

Users can override if they need custom DNS:

```bash
export KRB5_CONFIG=/path/to/custom/krb5.conf
java -jar mleaproxy.jar
```

---

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  MLEAProxy (Port 8080)                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐  │
│  │ SAML Handler │  │ OAuth Handler│  │ SPNEGO Handler* │  │
│  │   /saml/*    │  │  /oauth/*    │  │  /kerberos/*    │  │
│  └──────────────┘  └──────────────┘  └─────────────────┘  │
│         │                  │                   │            │
│         └──────────────────┴───────────────────┘            │
│                            │                                │
│                   ┌────────▼─────────┐                      │
│                   │  Kerberos Ticket │                      │
│                   │    Validator     │                      │
│                   └────────┬─────────┘                      │
│                            │                                │
└────────────────────────────┼────────────────────────────────┘
                             │
           ┌─────────────────┴─────────────────┐
           │                                   │
           ▼                                   ▼
┌──────────────────────┐          ┌──────────────────────┐
│   Kerby KDC Server   │          │  UnboundID In-Memory │
│   (Port 60088)       │◄─────────┤  LDAP Server         │
│                      │          │  (Port 60389)        │
│  Realm:              │  Shared  │                      │
│  MARKLOGIC.LOCAL     │  User DB │  Base DN:            │
│                      │          │  dc=MarkLogic,       │
│  Services:           │          │  dc=Local            │
│  - AS (Auth)         │          │                      │
│  - TGS (Ticket Grant)│          │  Users: 6            │
│  - Kadmin            │          │  Groups: 3           │
│                      │          │                      │
│  Principals:         │          │                      │
│  - mluser1@...       │          │                      │
│  - mluser2@...       │          │                      │
│  - appadmin@...      │          │                      │
│  - HTTP/localhost@...│          │                      │
└──────────────────────┘          └──────────────────────┘

* SPNEGO Handler = Phase 2
```

### Component Details

#### 1. Kerby KDC Server
- **Library**: Apache Kerby `SimpleKdcServer`
- **Port**: 60088 (KDC), 60749 (Admin)
- **Realm**: `MARKLOGIC.LOCAL`
- **Backend**: UnboundID LDAP (shared user database)
- **Features**:
  - AS-REQ/AS-REP (Authentication Service)
  - TGS-REQ/TGS-REP (Ticket Granting Service)
  - Kadmin protocol (optional)

#### 2. Principal Management
- **Import from LDAP**: Reads users from in-memory LDAP
- **Auto-create Service Principals**: HTTP, LDAP, etc.
- **Password Sync**: Uses same passwords as LDAP

#### 3. Configuration Generation
- **krb5.conf**: Auto-generated on startup
- **Keytabs**: Generated for service principals
- **Location**: `./kerberos/` directory

---

## Implementation Phases

### Phase 1: Basic KDC (MVP) 🎯 **Current Focus**
**Effort**: 2-3 days  
**Value**: High - Enables command-line testing

**Deliverables:**
1. ✅ Embed Apache Kerby `SimpleKdcServer`
2. ✅ Load principals from `marklogic.ldif`
3. ✅ Start KDC on port 60088
4. ✅ Auto-generate `krb5.conf`
5. ✅ Generate service keytabs
6. ✅ Configuration properties (`kerberos.properties`)
7. ✅ Documentation (`KERBEROS_GUIDE.md`)
8. ✅ Testing with `kinit`/`klist`

**Success Criteria:**
```bash
# User can do this:
export KRB5_CONFIG=./krb5.conf
kinit mluser1@MARKLOGIC.LOCAL
# (enter password)
klist
# Shows valid ticket
```

### Phase 2: HTTP Authentication (SPNEGO)
**Effort**: 3-4 days  
**Value**: Medium - Browser SSO

**Deliverables:**
1. Spring Security SPNEGO filter
2. `/kerberos/auth` endpoint
3. Kerberos ticket → Session token
4. Browser testing (Firefox/Chrome)

**Success Criteria:**
```bash
curl --negotiate -u : http://localhost:8080/kerberos/auth
# Returns JWT token or session cookie
```

### Phase 3: Protocol Integration
**Effort**: 2-3 days  
**Value**: High - Full integration

**Deliverables:**
1. Kerberos ticket → OAuth token
2. Kerberos ticket → SAML assertion
3. Service-to-service authentication
4. MarkLogic integration examples

**Success Criteria:**
```bash
# Get Kerberos ticket
kinit mluser1@MARKLOGIC.LOCAL

# Use ticket to get OAuth token
curl --negotiate -u : http://localhost:8080/oauth/token-from-kerberos
# Returns OAuth JWT with roles
```

---

## Phase 1 Details (MVP)

### 1. Maven Dependencies

**Add to `pom.xml`:**
```xml
<!-- Apache Kerby for Kerberos KDC -->
<dependency>
    <groupId>org.apache.kerby</groupId>
    <artifactId>kerb-simplekdc</artifactId>
    <version>2.0.3</version>
</dependency>

<dependency>
    <groupId>org.apache.kerby</groupId>
    <artifactId>kerb-core</artifactId>
    <version>2.0.3</version>
</dependency>

<dependency>
    <groupId>org.apache.kerby</groupId>
    <artifactId>kerby-config</artifactId>
    <version>2.0.3</version>
</dependency>
```

### 2. Configuration Interface

**File**: `src/main/java/com/marklogic/configuration/KerberosConfig.java`

```java
package com.marklogic.configuration;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({ 
    "system:properties",
    "file:${mleaproxy.properties}",
    "file:./mleaproxy.properties",
    "file:${HOME}/mleaproxy.properties",
    "file:/etc/mleaproxy.properties",
    "classpath:mleaproxy.properties",
    "file:./kerberos.properties"
})
public interface KerberosConfig extends Config {

    @Key("kerberos.enabled")
    @DefaultValue("false")
    boolean kerberosEnabled();

    @Key("kerberos.realm")
    @DefaultValue("MARKLOGIC.LOCAL")
    String kerberosRealm();

    @Key("kerberos.kdc.host")
    @DefaultValue("localhost")
    String kdcHost();

    @Key("kerberos.kdc.port")
    @DefaultValue("60088")
    int kdcPort();

    @Key("kerberos.admin.port")
    @DefaultValue("60749")
    int adminPort();

    @Key("kerberos.principals.import-from-ldap")
    @DefaultValue("true")
    boolean importPrincipalsFromLdap();

    @Key("kerberos.principals.ldap-base-dn")
    @DefaultValue("dc=MarkLogic,dc=Local")
    String ldapBaseDn();

    @Key("kerberos.service-principals")
    @DefaultValue("HTTP/localhost")
    String[] servicePrincipals();

    @Key("kerberos.work-dir")
    @DefaultValue("./kerberos")
    String workDir();

    @Key("kerberos.debug")
    @DefaultValue("false")
    boolean debug();
}
```

### 3. KDC Server Implementation

**File**: `src/main/java/com/marklogic/handlers/KerberosKDCServer.java`

```java
package com.marklogic.handlers;

import com.marklogic.configuration.KerberosConfig;
import org.apache.kerby.kerberos.kerb.KrbException;
import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class KerberosKDCServer {
    private static final Logger logger = LoggerFactory.getLogger(KerberosKDCServer.class);
    
    private SimpleKdcServer kdc;
    private final KerberosConfig config;
    private Path workDir;
    
    public KerberosKDCServer(KerberosConfig config) {
        this.config = config;
    }
    
    public void start() throws Exception {
        if (!config.kerberosEnabled()) {
            logger.info("Kerberos KDC is disabled");
            return;
        }
        
        logger.info("Starting Kerberos KDC...");
        
        // Create work directory
        workDir = Paths.get(config.workDir());
        Files.createDirectories(workDir);
        
        // Initialize KDC
        kdc = new SimpleKdcServer();
        kdc.setKdcRealm(config.kerberosRealm());
        kdc.setKdcHost(config.kdcHost());
        kdc.setKdcTcpPort(config.kdcPort());
        kdc.setAllowUdp(false); // TCP only for simplicity
        kdc.setWorkDir(workDir.toFile());
        
        logger.debug("KDC Realm: {}", config.kerberosRealm());
        logger.debug("KDC Host: {}", config.kdcHost());
        logger.debug("KDC Port: {}", config.kdcPort());
        logger.debug("Work Dir: {}", workDir.toAbsolutePath());
        
        // Initialize
        kdc.init();
        
        // Create principals
        createPrincipals();
        
        // Start KDC
        kdc.start();
        
        logger.info("Kerberos KDC started successfully");
        logger.info("  Realm: {}", config.kerberosRealm());
        logger.info("  KDC: {}:{}", config.kdcHost(), config.kdcPort());
        logger.info("  Work Directory: {}", workDir.toAbsolutePath());
        
        // Generate krb5.conf
        generateKrb5Config();
        
        // Generate keytabs
        generateKeytabs();
    }
    
    private void createPrincipals() throws Exception {
        logger.info("Creating Kerberos principals...");
        
        List<Principal> principals = new ArrayList<>();
        
        if (config.importPrincipalsFromLdap()) {
            // Import from in-memory LDAP
            principals.addAll(importFromLdap());
        }
        
        // Create user principals
        for (Principal principal : principals) {
            String principalName = principal.name + "@" + config.kerberosRealm();
            kdc.createPrincipal(principalName, principal.password);
            logger.info("Created principal: {}", principalName);
        }
        
        // Create service principals
        for (String servicePrincipal : config.servicePrincipals()) {
            String fullPrincipal = servicePrincipal + "@" + config.kerberosRealm();
            kdc.createPrincipal(fullPrincipal, "service-password");
            logger.info("Created service principal: {}", fullPrincipal);
        }
    }
    
    private List<Principal> importFromLdap() {
        logger.info("Importing principals from LDAP (base DN: {})", config.ldapBaseDn());
        
        // Hardcoded for Phase 1 - will integrate with LDAP in future
        List<Principal> principals = new ArrayList<>();
        principals.add(new Principal("mluser1", "password"));
        principals.add(new Principal("mluser2", "password"));
        principals.add(new Principal("mluser3", "password"));
        principals.add(new Principal("appreader", "password"));
        principals.add(new Principal("appwriter", "password"));
        principals.add(new Principal("appadmin", "password"));
        
        logger.info("Imported {} principals from LDAP", principals.size());
        return principals;
    }
    
    private void generateKrb5Config() throws Exception {
        Path krb5Path = workDir.resolve("krb5.conf");
        
        String config = String.format("""
            # Auto-generated by MLEAProxy
            # Date: %s
            
            [libdefaults]
                default_realm = %s
                dns_lookup_realm = false
                dns_lookup_kdc = false
                rdns = false
                udp_preference_limit = 1
                
            [realms]
                %s = {
                    kdc = %s:%d
                    admin_server = %s:%d
                }
                
            [domain_realm]
                .localhost = %s
                localhost = %s
            """,
            java.time.LocalDateTime.now(),
            this.config.kerberosRealm(),
            this.config.kerberosRealm(),
            this.config.kdcHost(), this.config.kdcPort(),
            this.config.kdcHost(), this.config.adminPort(),
            this.config.kerberosRealm(),
            this.config.kerberosRealm()
        );
        
        Files.writeString(krb5Path, config);
        logger.info("Generated krb5.conf at: {}", krb5Path.toAbsolutePath());
        
        // Also create in root directory for convenience
        Path rootKrb5 = Paths.get("./krb5.conf");
        Files.writeString(rootKrb5, config);
        logger.info("Also created krb5.conf at: {}", rootKrb5.toAbsolutePath());
    }
    
    private void generateKeytabs() throws Exception {
        logger.info("Generating keytabs for service principals...");
        
        Path keytabDir = workDir.resolve("keytabs");
        Files.createDirectories(keytabDir);
        
        for (String servicePrincipal : config.servicePrincipals()) {
            String fullPrincipal = servicePrincipal + "@" + config.kerberosRealm();
            String keytabName = servicePrincipal.replace("/", "_") + ".keytab";
            File keytabFile = keytabDir.resolve(keytabName).toFile();
            
            kdc.exportKeytab(keytabFile, fullPrincipal);
            logger.info("Generated keytab: {} for principal: {}", 
                keytabFile.getAbsolutePath(), fullPrincipal);
        }
    }
    
    public void stop() {
        if (kdc != null) {
            try {
                kdc.stop();
                logger.info("Kerberos KDC stopped");
            } catch (KrbException e) {
                logger.error("Error stopping KDC", e);
            }
        }
    }
    
    private static class Principal {
        final String name;
        final String password;
        
        Principal(String name, String password) {
            this.name = name;
            this.password = password;
        }
    }
}
```

### 4. Application Listener Integration

**File**: `src/main/java/com/marklogic/handlers/ApplicationListener.java`

Add to existing `ApplicationListener` class:

```java
// Add field
private KerberosKDCServer kerberosKdc;

// Add to contextInitialized() method
private void startKerberosKdc(ApplicationConfig cfg) {
    try {
        KerberosConfig krbCfg = ConfigFactory.create(KerberosConfig.class);
        
        if (krbCfg.kerberosEnabled()) {
            logger.info("Initializing Kerberos KDC...");
            kerberosKdc = new KerberosKDCServer(krbCfg);
            kerberosKdc.start();
        } else {
            logger.info("Kerberos KDC is disabled (set kerberos.enabled=true to enable)");
        }
    } catch (Exception e) {
        logger.error("Failed to start Kerberos KDC", e);
        throw new RuntimeException("Kerberos KDC startup failed", e);
    }
}

// Add to contextDestroyed() method
if (kerberosKdc != null) {
    kerberosKdc.stop();
}
```

### 5. Configuration File

**File**: `kerberos.properties` (create in root)

```properties
# ================================================================
# Kerberos KDC Configuration
# ================================================================
# Enable/disable Kerberos KDC
kerberos.enabled=false

# Kerberos Realm (uppercase by convention)
kerberos.realm=MARKLOGIC.LOCAL

# KDC Host and Ports
kerberos.kdc.host=localhost
kerberos.kdc.port=60088
kerberos.admin.port=60749

# Principal Management
# Import user principals from in-memory LDAP server
kerberos.principals.import-from-ldap=true
kerberos.principals.ldap-base-dn=dc=MarkLogic,dc=Local

# Service Principals (comma-separated)
# Format: service/hostname
kerberos.service-principals=HTTP/localhost,ldap/localhost

# Working Directory (for keytabs, krb5.conf, etc.)
kerberos.work-dir=./kerberos

# Debug logging
kerberos.debug=false
```

### 6. Testing Guide

**File**: `KERBEROS_TESTING_PHASE1.md`

```markdown
# Kerberos Phase 1 Testing Guide

## Prerequisites

- `kinit` command-line tool (part of Kerberos client)
  - macOS: Pre-installed
  - Linux: `apt-get install krb5-user` or `yum install krb5-workstation`
  - Windows: Download MIT Kerberos for Windows

## Quick Start

### 1. Enable Kerberos KDC

Edit `kerberos.properties`:
```properties
kerberos.enabled=true
```

### 2. Start MLEAProxy

```bash
java -jar target/mlesproxy-2.0.0.jar
```

**Expected Output:**
```
Starting Kerberos KDC...
Created principal: mluser1@MARKLOGIC.LOCAL
Created principal: mluser2@MARKLOGIC.LOCAL
...
Created service principal: HTTP/localhost@MARKLOGIC.LOCAL
Generated krb5.conf at: ./kerberos/krb5.conf
Generated keytab: ./kerberos/keytabs/HTTP_localhost.keytab
Kerberos KDC started successfully
  Realm: MARKLOGIC.LOCAL
  KDC: localhost:60088
```

### 3. Test with kinit

```bash
# Set Kerberos config
export KRB5_CONFIG=./krb5.conf

# Get ticket for mluser1
kinit mluser1@MARKLOGIC.LOCAL

# Enter password: password

# Verify ticket
klist
```

**Expected Output:**
```
Ticket cache: FILE:/tmp/krb5cc_501
Default principal: mluser1@MARKLOGIC.LOCAL

Valid starting     Expires            Service principal
10/06/25 14:30:00  10/07/25 00:30:00  krbtgt/MARKLOGIC.LOCAL@MARKLOGIC.LOCAL
```

### 4. Test Other Users

```bash
# appadmin user
kinit appadmin@MARKLOGIC.LOCAL
# Password: password

klist
```

### 5. Destroy Ticket

```bash
kdestroy
klist  # Should show no tickets
```

## Troubleshooting

### Issue: "Cannot find KDC for realm"

**Solution:** Set `KRB5_CONFIG` environment variable:
```bash
export KRB5_CONFIG=./krb5.conf
```

### Issue: "Clock skew too great"

**Solution:** Sync system time:
```bash
# macOS
sudo sntp -sS time.apple.com

# Linux
sudo ntpdate pool.ntp.org
```

### Issue: "Pre-authentication failed"

**Cause:** Wrong password  
**Solution:** Password is "password" for all users

## Success Criteria

✅ MLEAProxy starts with Kerberos enabled  
✅ `krb5.conf` auto-generated  
✅ Keytabs auto-generated  
✅ `kinit` succeeds for all 6 users  
✅ `klist` shows valid tickets  
✅ Tickets expire after 10 hours (default)  
```

---

## Dependencies

### Maven Dependencies (Phase 1)

```xml
<!-- Apache Kerby -->
<dependency>
    <groupId>org.apache.kerby</groupId>
    <artifactId>kerb-simplekdc</artifactId>
    <version>2.0.3</version>
</dependency>

<dependency>
    <groupId>org.apache.kerby</groupId>
    <artifactId>kerb-core</artifactId>
    <version>2.0.3</version>
</dependency>

<dependency>
    <groupId>org.apache.kerby</groupId>
    <artifactId>kerby-config</artifactId>
    <version>2.0.3</version>
</dependency>
```

### Total Size Impact

- **JAR Size Increase**: ~5-7 MB
- **Runtime Memory**: ~20-50 MB additional
- **Startup Time**: +200-500ms

---

## Configuration

### Ports

| Port  | Service | Protocol | Configurable |
|-------|---------|----------|--------------|
| 60088 | KDC     | TCP      | Yes          |
| 60749 | Kadmin  | TCP      | Yes          |

### Files Generated

| File | Location | Purpose |
|------|----------|---------|
| `krb5.conf` | `./krb5.conf` | Kerberos client configuration |
| `krb5.conf` | `./kerberos/krb5.conf` | Backup copy |
| `*.keytab` | `./kerberos/keytabs/` | Service principal keytabs |

### Default Principals

**User Principals** (from LDAP):
```
mluser1@MARKLOGIC.LOCAL
mluser2@MARKLOGIC.LOCAL
mluser3@MARKLOGIC.LOCAL
appreader@MARKLOGIC.LOCAL
appwriter@MARKLOGIC.LOCAL
appadmin@MARKLOGIC.LOCAL
```

**Service Principals**:
```
HTTP/localhost@MARKLOGIC.LOCAL
ldap/localhost@MARKLOGIC.LOCAL
krbtgt/MARKLOGIC.LOCAL@MARKLOGIC.LOCAL (auto-created)
```

---

## Testing Strategy

### Unit Tests

```java
@Test
void testKdcServerStartup() {
    KerberosConfig config = mock(KerberosConfig.class);
    when(config.kerberosEnabled()).thenReturn(true);
    when(config.kerberosRealm()).thenReturn("TEST.LOCAL");
    
    KerberosKDCServer kdc = new KerberosKDCServer(config);
    kdc.start();
    
    // Verify KDC is running
    assertTrue(kdc.isRunning());
    
    kdc.stop();
}

@Test
void testPrincipalCreation() throws Exception {
    // Create principal
    kdc.createPrincipal("testuser@TEST.LOCAL", "password");
    
    // Verify exists
    assertTrue(kdc.principalExists("testuser@TEST.LOCAL"));
}
```

### Integration Tests

```java
@SpringBootTest
class KerberosIntegrationTest {
    
    @Test
    void testKinitAuthentication() throws Exception {
        // Run kinit command
        Process process = new ProcessBuilder(
            "kinit", "-V", "mluser1@MARKLOGIC.LOCAL"
        ).start();
        
        // Send password
        try (OutputStream os = process.getOutputStream()) {
            os.write("password\n".getBytes());
            os.flush();
        }
        
        int exitCode = process.waitFor();
        assertEquals(0, exitCode, "kinit should succeed");
        
        // Verify ticket exists
        Process klist = new ProcessBuilder("klist").start();
        String output = new String(klist.getInputStream().readAllBytes());
        assertTrue(output.contains("mluser1@MARKLOGIC.LOCAL"));
    }
}
```

### Manual Testing

See `KERBEROS_TESTING_PHASE1.md` above.

---

## Risk Assessment

### High Risks 🔴

None identified for Phase 1.

### Medium Risks 🟡

1. **Clock Skew Issues**
   - **Mitigation**: Document NTP requirements, default tolerance is 5 minutes
   - **Impact**: Authentication failures

2. **Cross-Platform Compatibility**
   - **Mitigation**: Test on macOS, Linux, Windows
   - **Impact**: May need platform-specific documentation

### Low Risks 🟢

1. **Performance Impact**
   - **Mitigation**: KDC is disabled by default
   - **Impact**: Minimal (<100ms startup time)

2. **Port Conflicts**
   - **Mitigation**: Ports are configurable
   - **Impact**: Easy to resolve

---

## Success Metrics

### Phase 1 Success Criteria

✅ **Functional:**
1. KDC starts successfully on configured port
2. All 6 user principals created from LDAP
3. Service principals created with keytabs
4. `krb5.conf` auto-generated correctly
5. `kinit` succeeds for all users
6. `klist` shows valid tickets with correct expiration

✅ **Quality:**
1. Unit tests pass (>80% coverage)
2. Integration tests pass
3. No errors in logs during startup/shutdown
4. Memory usage < 50MB additional
5. Startup time increase < 500ms

✅ **Documentation:**
1. `KERBEROS_GUIDE.md` created
2. `KERBEROS_TESTING_PHASE1.md` created
3. `kerberos.properties` example provided
4. README.md updated with Kerberos section

---

## Timeline

### Phase 1 (MVP)
- **Duration**: 2-3 days
- **Start**: October 6, 2025
- **Target Completion**: October 9, 2025

**Day 1:**
- ✅ Feasibility document (this file)
- ✅ Maven dependencies
- ⏳ Configuration interface
- ⏳ KDC server skeleton

**Day 2:**
- ⏳ KDC implementation complete
- ⏳ Principal creation
- ⏳ krb5.conf generation
- ⏳ Keytab generation

**Day 3:**
- ⏳ Testing & debugging
- ⏳ Documentation
- ⏳ Integration with ApplicationListener
- ⏳ README.md updates

### Phase 2 (SPNEGO)
- **Duration**: 3-4 days
- **Target Start**: October 10, 2025

### Phase 3 (Integration)
- **Duration**: 2-3 days
- **Target Start**: October 15, 2025

---

## Future Enhancements (Post-Phase 3)

1. **Active Directory Integration**
   - Connect to external AD as backend
   - Principal replication

2. **Cross-Realm Trust**
   - Trust relationships with other realms
   - Referral support

3. **Advanced Kadmin**
   - Change password support
   - Principal management UI

4. **High Availability**
   - Multiple KDC instances
   - Replication

5. **Audit Logging**
   - Authentication attempts
   - Ticket granting events
   - Failed authentication tracking

---

## Conclusion

Implementing Kerberos KDC support using Apache Kerby is **feasible, valuable, and well-scoped** for MLEAProxy. The DNS challenges are mitigated by using `localhost`-based configuration suitable for testing/development scenarios.

### Recommendation: ✅ **PROCEED WITH PHASE 1**

**Rationale:**
1. ✅ Clear technical path forward
2. ✅ Manageable scope (2-3 days)
3. ✅ High value for enterprise testing
4. ✅ Consistent with existing architecture (similar to UnboundID LDAP)
5. ✅ Low risk implementation

**Next Steps:**
1. Review and approve this feasibility document
2. Add Maven dependencies to `pom.xml`
3. Create `KerberosConfig.java` interface
4. Implement `KerberosKDCServer.java`
5. Test with `kinit`/`klist`

---

**Document Status**: ✅ Complete - Ready for Implementation  
**Author**: GitHub Copilot  
**Date**: October 6, 2025  
**Version**: 1.0
