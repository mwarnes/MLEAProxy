# System Properties Support - October 6, 2025

## Overview

Added support for command-line system property overrides using `-D` flags. System properties now have the **highest priority** in the configuration hierarchy.

## Problem

When running the application with system property overrides:
```bash
java -Dldap.debug=true -Dldaplistener.proxy.port=20389 -jar target/mlesproxy-2.0.0.jar
```

The properties were being ignored and the application used values from property files instead.

## Root Cause

The `@Config.Sources` annotation in all configuration interfaces did not include `system:properties` as a source. The `aeonbits.owner` configuration library requires explicit declaration of system properties as a source to enable command-line overrides.

## Solution

Added `"system:properties"` as the **first source** (highest priority) in all configuration interface files:

### Files Modified

1. `ApplicationConfig.java`
2. `LDAPListenersConfig.java`
3. `SAMLListenersConfig.java`
4. `ProcessorConfig.java`
5. `ServersConfig.java`
6. `DSConfig.java`
7. `SetsConfig.java`

### Change Applied

```java
// Before:
@Config.Sources({ 
        "file:${mleaproxy.properties}",
        "file:./mleaproxy.properties",
        // ... other sources
})

// After:
@Config.Sources({ 
        "system:properties",              // <- Added (highest priority)
        "file:${mleaproxy.properties}",
        "file:./mleaproxy.properties",
        // ... other sources
})
```

## Configuration Priority (After Fix)

Properties are now loaded with this priority order (highest to lowest):

1. **System properties** via `-D` flags ← **NEW (Highest)**
2. `./directory.properties`
3. `./oauth.properties`
4. `./saml.properties`
5. `./ldap.properties`
6. `file:${mleaproxy.properties}` (system property path)
7. `./mleaproxy.properties` (current directory)
8. `${HOME}/mleaproxy.properties` (user home)
9. `/etc/mleaproxy.properties` (system-wide)
10. `classpath:mleaproxy.properties` (bundled in JAR) ← **Lowest**

## Verification

Tested with the original command:

```bash
java -Dldap.debug=true -Dldaplistener.proxy.port=20389 -jar target/mlesproxy-2.0.0.jar
```

**Result**: ✅ SUCCESS

Application log confirms:
```
Port: 20389
Listening on: 0.0.0.0:20389 (LDAP proxy with LDAPS connection to back-end Active Directory server.)
```

The port override works correctly!

## Usage Examples

### Override LDAP Listener Port
```bash
java -Dldaplistener.proxy.port=20389 -jar target/mlesproxy-2.0.0.jar
```

### Enable Debug Logging
```bash
java -Dldap.debug=true -Dsaml.debug=true -jar target/mlesproxy-2.0.0.jar
```

### Change Backend LDAP Server
```bash
java -Dldapserver.server1.host=ldap.newserver.com \
     -Dldapserver.server1.port=636 \
     -jar target/mlesproxy-2.0.0.jar
```

### Multiple Overrides
```bash
java -Dldap.debug=true \
     -Dldaplistener.proxy.port=20389 \
     -Dldaplistener.proxy.ipaddress=127.0.0.1 \
     -Dldapserver.server1.host=192.168.1.100 \
     -jar target/mlesproxy-2.0.0.jar
```

### Common Use Cases

1. **Quick Testing**: Override settings without editing configuration files
2. **Container Deployments**: Pass environment-specific settings via Docker/Kubernetes environment variables
3. **CI/CD Pipelines**: Configure per-environment without maintaining multiple property files
4. **Development**: Rapidly test different configurations
5. **Troubleshooting**: Enable debug logging temporarily

## Documentation Updated

Updated `CONFIGURATION_GUIDE.md` to reflect:
- System properties as highest priority source
- Examples of command-line overrides
- Use cases for system property overrides

## Benefits

✅ **No file editing needed** for temporary configuration changes
✅ **Environment-specific overrides** in containerized deployments
✅ **Debugging flexibility** - enable/disable debug logging on-the-fly
✅ **CI/CD friendly** - configure via environment variables
✅ **Backward compatible** - existing property files still work
✅ **Developer-friendly** - quick experimentation with different settings

## Testing

All tests still pass after this change:
```
Tests run: 93, Failures: 0, Errors: 0, Skipped: 6
BUILD SUCCESS
```

The change only affects the configuration loading mechanism and does not alter application logic.
