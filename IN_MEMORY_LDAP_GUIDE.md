# In-Memory LDAP Server Guide

**Last Updated**: October 6, 2025  
**Status**: ✅ Feature Active and Available

## Overview

MLEAProxy includes a built-in **in-memory LDAP directory server** powered by **UnboundID In-Memory Directory Server**. This feature provides a lightweight, embedded LDAP server that loads data from an LDIF file at startup.

**Use Cases**:
- **Testing**: Test LDAP authentication without external directory servers
- **Development**: Rapid prototyping with predefined user data
- **Demos**: Self-contained demonstrations
- **CI/CD**: Automated testing with consistent test data
- **Training**: Learning LDAP concepts without infrastructure setup

## Key Features

✅ **UnboundID In-Memory Directory Server** (not Apache Directory Studio server)  
✅ **Compatible with Apache Directory Studio** as a client for management  
✅ **LDIF Import**: Loads data from `marklogic.ldif` or custom LDIF files  
✅ **Configurable**: Port, base DN, admin credentials all configurable  
✅ **Multiple Servers**: Can run multiple in-memory servers simultaneously  
✅ **Embedded**: Runs in the same JVM as MLEAProxy  
✅ **Fast Startup**: Loads in milliseconds  

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    MLEAProxy JVM                         │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │   In-Memory LDAP Server (UnboundID)              │  │
│  │   Port: 60389 (default)                          │  │
│  ├──────────────────────────────────────────────────┤  │
│  │   Data loaded from:                              │  │
│  │   - marklogic.ldif (bundled)                     │  │
│  │   - OR custom LDIF file                          │  │
│  └──────────────────────────────────────────────────┘  │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │   LDAP Proxy Listeners                           │  │
│  │   Port: 10389+ (proxies to real LDAP/in-memory) │  │
│  └──────────────────────────────────────────────────┘  │
│                                                          │
└─────────────────────────────────────────────────────────┘
           ↑                              ↑
           │                              │
    LDAP Clients              Apache Directory Studio
   (MarkLogic,               (for management/browsing)
    ldapsearch, etc.)
```

## Quick Start

### 1. Enable In-Memory Server

**Edit `directory.properties`**:
```properties
# Enable in-memory LDAP server
directoryServers=marklogic

# Server configuration
ds.marklogic.name=MarkLogic1
ds.marklogic.ipaddress=0.0.0.0
ds.marklogic.port=60389
ds.marklogic.basedn=dc=MarkLogic,dc=Local
ds.marklogic.admindn=cn=Directory Manager
ds.marklogic.adminpw=password
```

### 2. Start MLEAProxy

```bash
java -jar mlesproxy-2.0.0.jar
```

**Expected Log Output**:
```
Starting inMemory LDAP servers.
Using internal LDIF
Directory Server listening on: /0.0.0.0:60389 (MarkLogic1)
```

### 3. Test Connection

```bash
# Test bind and search
ldapsearch -H ldap://localhost:60389 \
  -D "cn=Directory Manager" \
  -w password \
  -b "dc=MarkLogic,dc=Local" \
  "(objectClass=*)"
```

## Configuration

### Configuration File: `directory.properties`

**Location**: Root directory or classpath

**Full Configuration**:
```properties
# ================================================================
# Directory Server Configuration
# ================================================================
# In-memory directory servers for testing
# Comma-separated list of directory servers to start
directoryServers=marklogic

# ----------------------------------------------------------------
# Directory Server: marklogic
# ----------------------------------------------------------------
ds.marklogic.name=MarkLogic1
ds.marklogic.ipaddress=0.0.0.0
ds.marklogic.port=60389
ds.marklogic.basedn=dc=MarkLogic,dc=Local
ds.marklogic.admindn=cn=Directory Manager
ds.marklogic.adminpw=password

# Optional: Custom LDIF file path
# If not specified, uses built-in marklogic.ldif
#ds.marklogic.ldifpath=/path/to/custom.ldif
```

### Configuration Properties

| Property | Description | Default | Required |
|----------|-------------|---------|----------|
| `directoryServers` | Comma-separated list of servers | (none) | Yes |
| `ds.<name>.name` | Display name for the server | - | Yes |
| `ds.<name>.ipaddress` | Bind address (0.0.0.0 = all) | `0.0.0.0` | No |
| `ds.<name>.port` | LDAP port | `60389` | No |
| `ds.<name>.basedn` | Directory base DN | `dc=MarkLogic,dc=Local` | No |
| `ds.<name>.admindn` | Administrator DN | `cn=Directory Manager` | No |
| `ds.<name>.adminpw` | Administrator password | `password` | No |
| `ds.<name>.ldifpath` | Custom LDIF file path | (uses built-in) | No |

## Built-in LDIF File: `marklogic.ldif`

**Location**: `src/main/resources/marklogic.ldif`

**Contents**:
```ldif
version: 1

dn: dc=MarkLogic,dc=Local
objectClass: organization
objectClass: dcObject
dc: MarkLogic
o: MarkLogic

dn: ou=Users,dc=MarkLogic,dc=Local
objectClass: organizationalUnit
ou: Users

dn: ou=Groups,dc=MarkLogic,dc=Local
objectClass: organizationalUnit
ou: Groups
```

### Users in Built-in LDIF

| Username | DN | Password | Roles/Groups |
|----------|-----|----------|--------------|
| mluser1 | `uid=mluser1,ou=Users,dc=MarkLogic,dc=Local` | password | AppWriter, AppReader |
| mluser2 | `uid=mluser2,ou=Users,dc=MarkLogic,dc=Local` | password | (none) |
| mluser3 | `uid=mluser3,ou=Users,dc=MarkLogic,dc=Local` | password | (none) |
| appreader | `uid=appreader,ou=Users,dc=MarkLogic,dc=Local` | password | AppReader |
| appwriter | `uid=appwriter,ou=Users,dc=MarkLogic,dc=Local` | password | AppWriter |
| appadmin | `uid=appadmin,ou=Users,dc=MarkLogic,dc=Local` | password | AppAdmin |

**Note**: All passwords are "password" (Base64 encoded in LDIF: `cGFzc3dvcmQ=`)

### Groups in Built-in LDIF

| Group | DN | Members |
|-------|-----|---------|
| AppReader | `cn=AppReader,ou=Groups,dc=MarkLogic,dc=Local` | appreader |
| AppWriter | `cn=AppWriter,ou=Groups,dc=MarkLogic,dc=Local` | appwriter |
| AppAdmin | `cn=AppAdmin,ou=Groups,dc=MarkLogic,dc=Local` | appadmin |

## Using Custom LDIF Files

### Option 1: Specify in Configuration

```properties
directoryServers=custom

ds.custom.name=CustomDirectory
ds.custom.port=60390
ds.custom.basedn=dc=example,dc=com
ds.custom.ldifpath=/opt/ldap-data/custom.ldif
```

### Option 2: Create Custom LDIF

**Example: `/opt/ldap-data/custom.ldif`**:
```ldif
version: 1

dn: dc=example,dc=com
objectClass: top
objectClass: domain
dc: example

dn: ou=People,dc=example,dc=com
objectClass: organizationalUnit
ou: People

dn: uid=testuser,ou=People,dc=example,dc=com
objectClass: inetOrgPerson
objectClass: person
objectClass: top
uid: testuser
cn: Test User
sn: User
userPassword: testpass
```

## Multiple In-Memory Servers

You can run multiple in-memory servers on different ports:

```properties
# Run two in-memory servers
directoryServers=server1,server2

# Server 1: MarkLogic test data
ds.server1.name=MarkLogic-Test
ds.server1.port=60389
ds.server1.basedn=dc=MarkLogic,dc=Local

# Server 2: Custom test data
ds.server2.name=Custom-Test
ds.server2.port=60390
ds.server2.basedn=dc=example,dc=com
ds.server2.ldifpath=/opt/data/example.ldif
```

## Using Apache Directory Studio

**Apache Directory Studio** is an LDAP client that can connect to and manage the in-memory server.

### 1. Download Apache Directory Studio

Download from: https://directory.apache.org/studio/

### 2. Create Connection

1. **Open Apache Directory Studio**
2. **New Connection** → LDAP Browser → New Connection
3. **Connection Settings**:
   - **Connection name**: MLEAProxy In-Memory
   - **Hostname**: localhost
   - **Port**: 60389
   - **Encryption method**: No encryption

4. **Authentication**:
   - **Bind DN or user**: `cn=Directory Manager`
   - **Bind password**: `password`

5. **Click "Check Authentication"** → Should succeed
6. **Click "Finish"**

### 3. Browse and Edit

Once connected, you can:
- ✅ Browse the directory tree
- ✅ View all entries and attributes
- ✅ Add new users and groups
- ✅ Modify existing entries
- ✅ Delete entries
- ✅ Export to LDIF
- ✅ Import from LDIF

**Note**: Changes are **in-memory only** and lost on restart unless you export to LDIF.

### 4. Export Modified Data

**To preserve changes**:
1. **Right-click** on base DN (e.g., `dc=MarkLogic,dc=Local`)
2. **Export** → LDIF Export
3. **Save** to file (e.g., `/opt/data/modified.ldif`)
4. **Update** `directory.properties`:
   ```properties
   ds.marklogic.ldifpath=/opt/data/modified.ldif
   ```
5. **Restart** MLEAProxy to load modified data

## Testing

### Test Authentication

```bash
# Simple bind test
ldapsearch -H ldap://localhost:60389 \
  -D "uid=mluser1,ou=Users,dc=MarkLogic,dc=Local" \
  -w password \
  -b "dc=MarkLogic,dc=Local" \
  "(uid=mluser1)"
```

### Search for All Users

```bash
ldapsearch -H ldap://localhost:60389 \
  -D "cn=Directory Manager" \
  -w password \
  -b "ou=Users,dc=MarkLogic,dc=Local" \
  "(objectClass=inetOrgPerson)"
```

### Search for Groups

```bash
ldapsearch -H ldap://localhost:60389 \
  -D "cn=Directory Manager" \
  -w password \
  -b "ou=Groups,dc=MarkLogic,dc=Local" \
  "(objectClass=groupOfNames)"
```

### Using Python ldap3

```python
from ldap3 import Server, Connection, ALL

# Connect to in-memory server
server = Server('localhost', port=60389, get_info=ALL)
conn = Connection(server, 
                  'cn=Directory Manager', 
                  'password', 
                  auto_bind=True)

# Search for users
conn.search('dc=MarkLogic,dc=Local', 
            '(objectClass=inetOrgPerson)',
            attributes=['uid', 'cn', 'businessCategory'])

for entry in conn.entries:
    print(f"User: {entry.uid}, Groups: {entry.businessCategory}")

conn.unbind()
```

## Integration with LDAP Proxy

The in-memory server can be used as a backend for LDAP proxy listeners:

**Example: Proxy to In-Memory Server**:

```properties
# In-memory server
directoryServers=marklogic
ds.marklogic.port=60389
ds.marklogic.basedn=dc=MarkLogic,dc=Local

# LDAP proxy listener
ldaplisteners=proxy
ldaplistener.proxy.port=10389
ldaplistener.proxy.serversets=inmemory

# Backend: In-memory server
ldapserverset.inmemory.servers=localhost
ldapserver.localhost.ipaddress=localhost
ldapserver.localhost.port=60389
ldapserver.localhost.type=SINGLE
```

**Result**: Clients connect to port 10389, which proxies to in-memory server on 60389

## Troubleshooting

### Server Not Starting

**Symptom**: No "Directory Server listening" message in logs

**Check**:
```bash
# Verify directoryServers is uncommented
grep "^directoryServers=" directory.properties

# Expected: directoryServers=marklogic
```

**Solution**: Uncomment the line in `directory.properties`

### Port Already in Use

**Symptom**: Error about port binding

**Solution**: Change port in configuration:
```properties
ds.marklogic.port=60390  # Use different port
```

### LDIF Import Error

**Symptom**: Error loading LDIF file

**Check LDIF syntax**:
```bash
# Validate LDIF file
ldapadd -H ldap://localhost:60389 \
  -D "cn=Directory Manager" \
  -w password \
  -f /path/to/custom.ldif -c
```

**Common Issues**:
- Missing `version: 1` header
- Incorrect DN syntax
- Missing required attributes
- Duplicate entries

### Authentication Failed

**Symptom**: Bind fails with error 49

**Check credentials**:
```bash
# Test with correct admin credentials
ldapsearch -H ldap://localhost:60389 \
  -D "cn=Directory Manager" \
  -w password \
  -b "dc=MarkLogic,dc=Local" \
  "(objectClass=*)"
```

**Verify admin DN matches configuration**:
```properties
ds.marklogic.admindn=cn=Directory Manager
ds.marklogic.adminpw=password
```

## Advanced Usage

### Dynamic User Addition

**Via Apache Directory Studio**:
1. Connect to in-memory server
2. Right-click on `ou=Users,dc=MarkLogic,dc=Local`
3. **New** → **New Entry**
4. Select object classes: `inetOrgPerson`, `person`, `top`
5. Fill in required attributes:
   - `uid`: username
   - `cn`: common name
   - `sn`: surname
   - `userPassword`: password

### Role-Based Access Control

The built-in LDIF uses `businessCategory` attribute for group membership:

```ldif
dn: uid=mluser1,ou=Users,dc=MarkLogic,dc=Local
...
businessCategory: cn=AppWriter,ou=Groups,dc=MarkLogic,dc=Local
businessCategory: cn=AppReader,ou=Groups,dc=MarkLogic,dc=Local
```

**LDAP Proxy** can map these to MarkLogic roles.

### Schema Customization

UnboundID In-Memory Directory Server supports standard LDAP schema. You can:
- Add custom object classes
- Define custom attributes
- Extend existing schemas

**Note**: Schema modifications require code changes (not just LDIF).

## Performance Considerations

### Memory Usage

In-memory servers load all data into RAM:
- **Small datasets** (<1000 entries): Negligible overhead
- **Medium datasets** (1000-10000 entries): ~10-50 MB
- **Large datasets** (>10000 entries): Consider external LDAP server

### Startup Time

- **Built-in marklogic.ldif** (6 users, 3 groups): <100ms
- **Custom LDIF** (1000 entries): <500ms
- **Large LDIF** (10000 entries): ~2-5 seconds

### Concurrent Connections

UnboundID In-Memory Directory Server handles:
- **100+ concurrent connections** easily
- **1000+ operations/second** for simple queries
- Suitable for most testing and development scenarios

## Use Cases

### 1. CI/CD Testing

```yaml
# .github/workflows/test.yml
- name: Run LDAP Integration Tests
  run: |
    # Start MLEAProxy with in-memory LDAP
    java -jar mlesproxy-2.0.0.jar &
    sleep 5
    
    # Run tests
    mvn test -Dldap.url=ldap://localhost:60389
```

### 2. Development Environment

Developers can test LDAP authentication locally without:
- Installing OpenLDAP or Active Directory
- Configuring external servers
- Network connectivity to production LDAP

### 3. Demo Environments

Portable demo with self-contained authentication:
```bash
# Single command starts everything
java -jar mlesproxy-2.0.0.jar
```

### 4. Training

Students can learn LDAP concepts using Apache Directory Studio connected to in-memory server.

## Comparison: In-Memory vs External LDAP

| Feature | In-Memory Server | External LDAP Server |
|---------|------------------|----------------------|
| **Setup** | Zero configuration | Requires installation |
| **Startup** | Instant (<1s) | Minutes to hours |
| **Persistence** | In-memory (lost on restart) | Persistent storage |
| **Performance** | Very fast (RAM-based) | Network + disk latency |
| **Scalability** | Limited by RAM | Enterprise scale |
| **HA/Replication** | No | Yes (production feature) |
| **Best For** | Dev/test/demo | Production environments |

## Related Documentation

- **LDAP_GUIDE.md** - Complete LDAP proxy functionality guide
- **directory.properties** - In-memory server configuration
- **src/main/resources/marklogic.ldif** - Built-in test data
- **ApplicationListener.java** - Server startup implementation (lines 155-197)

## Source Code References

### Implementation

**File**: `src/main/java/com/marklogic/handlers/ApplicationListener.java`

**Method**: `startInMemoryDirectoryServers()` (lines 155-197)

**Key Libraries**:
- UnboundID LDAP SDK: `com.unboundid.ldap.listener.InMemoryDirectoryServer`
- LDIF Reader: `com.unboundid.ldif.LDIFReader`

### Configuration Interface

**File**: `src/main/java/com/marklogic/configuration/DSConfig.java`

Defines all configuration properties for directory servers.

### Test Implementation

**File**: `src/test/java/com/marklogic/handlers/LDAPRequestHandlerTest.java`

Shows how tests use in-memory servers.

## Summary

✅ **Feature Active**: In-memory LDAP server is fully functional  
✅ **UnboundID Powered**: Uses UnboundID In-Memory Directory Server  
✅ **Apache Directory Studio Compatible**: Works as LDAP client for management  
✅ **LDIF Import**: Loads from built-in `marklogic.ldif` or custom files  
✅ **Easy Configuration**: Simple `directory.properties` setup  
✅ **Multiple Servers**: Can run several in-memory servers simultaneously  
✅ **Development Ready**: Perfect for testing, development, and demos  

---

**Documentation Complete**: October 6, 2025 ✅
