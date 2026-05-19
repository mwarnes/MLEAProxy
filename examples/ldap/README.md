# LDAP Configuration Examples

This directory contains example LDAP configuration files for various deployment scenarios.

---

## Available Examples

| File | Scenario | Description |
|------|----------|-------------|
| **01-standalone-json-server.properties** | Standalone JSON Server | Simple LDAP server using JSON user data (no backend required) |
| **02-simple-proxy.properties** | Simple Proxy | Forward requests to a backend LDAP server |
| **03-secure-proxy-ldaps-backend.properties** | Secure Backend | LDAP client -> MLEAProxy -> LDAPS backend |
| **04-fully-secure-proxy-ldaps-both-sides.properties** | Fully Secure | LDAPS client -> MLEAProxy -> LDAPS backend |
| **05-load-balancing-roundrobin.properties** | Load Balancing | Distribute requests across multiple backend servers |
| **06-multi-site-failover.properties** | Multi-Site Failover | Load balance within sites with inter-site failover |

---

## Quick Start

### Using an Example

1. **Choose an example** from the list above
2. **Copy to root directory**:
   ```bash
   cp examples/ldap/01-standalone-json-server.properties mleaproxy.properties
   ```
3. **Edit configuration** (update hosts, ports, paths as needed)
4. **Run MLEAProxy**:
   ```bash
   java -jar mleaproxy.jar
   ```

### Command-Line Overrides

Override any property via command line using `--mleaproxy.*` syntax:

```bash
# Override LDAP debug setting
java -jar mleaproxy.jar --mleaproxy.ldap-debug=true

# Override listener port
java -jar mleaproxy.jar --mleaproxy.ldap-listeners.proxy.port=10389

# Override backend server host
java -jar mleaproxy.jar --mleaproxy.ldap-servers.server1.host=ldap.example.com

# Multiple overrides
java -jar mleaproxy.jar \
  --mleaproxy.ldap-debug=true \
  --mleaproxy.ldap-listeners.proxy.port=20389 \
  --mleaproxy.ldap-servers.server1.host=192.168.1.100
```

### Testing Your Configuration

```bash
# Test LDAP connection
ldapsearch -H ldap://localhost:10389 \
  -D "cn=manager,ou=users,dc=marklogic,dc=local" \
  -w password \
  -b "ou=users,dc=marklogic,dc=local" \
  "(sAMAccountName=user1)"
```

---

## Expected Startup Output

When MLEAProxy starts successfully with LDAP configured, you should see output similar to:

```
  __  __ _     ___   _   ___
 |  \/  | |   | __| /_\ | _ \_ _ _____ ___  _
 | |\/| | |__ | _| / _ \|  _/ '_/ _ \ \ / || |
 |_|  |_|____||___/_/ \_\_| |_| \___/_\_\_, |
                                        |__/
 MLEAProxy - MarkLogic External Authentication Proxy
 Version: 2.0.0

2024-01-15 10:30:45.123 INFO  --- Starting MLEAProxy...
2024-01-15 10:30:45.456 INFO  --- LDAP Listener 'proxy' started on 0.0.0.0:20389
2024-01-15 10:30:45.457 INFO  --- LDAP mode: single
2024-01-15 10:30:45.458 INFO  --- Backend servers: server1 (ldap.example.com:389)
2024-01-15 10:30:45.789 INFO  --- MLEAProxy started successfully
```

---

## Verification Commands

### Test Standalone JSON Server (Example 01)

```bash
# Search for a user
ldapsearch -H ldap://localhost:20389 \
  -D "cn=manager,ou=users,dc=marklogic,dc=local" \
  -w password \
  -b "ou=users,dc=marklogic,dc=local" \
  "(sAMAccountName=admin)"

# Verify authentication
ldapsearch -H ldap://localhost:20389 \
  -D "cn=admin,ou=users,dc=marklogic,dc=local" \
  -w admin \
  -b "ou=users,dc=marklogic,dc=local" \
  "(objectClass=*)"
```

### Test Proxy Server (Examples 02-06)

```bash
# Basic search through proxy
ldapsearch -H ldap://localhost:20389 \
  -D "cn=admin,dc=example,dc=com" \
  -w password \
  -b "dc=example,dc=com" \
  "(uid=user1)"

# Search with specific attributes
ldapsearch -H ldap://localhost:20389 \
  -D "cn=admin,dc=example,dc=com" \
  -w password \
  -b "dc=example,dc=com" \
  "(uid=user1)" \
  cn mail memberOf
```

### Test LDAPS Connection (Example 04)

```bash
# LDAPS connection (may need to trust certificate)
ldapsearch -H ldaps://localhost:30636 \
  -D "cn=admin,dc=example,dc=com" \
  -w password \
  -b "dc=example,dc=com" \
  "(uid=user1)"

# With certificate verification disabled (testing only)
LDAPTLS_REQCERT=never ldapsearch -H ldaps://localhost:30636 \
  -D "cn=admin,dc=example,dc=com" \
  -w password \
  -b "dc=example,dc=com" \
  "(uid=user1)"
```

---

## Example Details

### 1. Standalone JSON LDAP Server

**File**: `01-standalone-json-server.properties`

**Use Case**: 
- Development and testing
- No backend LDAP infrastructure required
- Quick proof-of-concept

**Key Features**:
- Uses `users.json` for user data
- No external dependencies
- Perfect for local development

**Port**: 20389

**Requirements**:
- `users.json` file in root directory

---

### 2. Simple LDAP Proxy

**File**: `02-simple-proxy.properties`

**Use Case**:
- Debug LDAP authentication issues
- Monitor LDAP traffic
- Add logging to existing LDAP infrastructure

**Key Features**:
- Detailed debug logging
- Attribute mapping support (e.g., memberOf -> isMemberOf)
- Single backend server

**Port**: 20389

**Configuration Required**:
- Backend LDAP server host and port

---

### 3. Secure LDAP Proxy (LDAPS Backend)

**File**: `03-secure-proxy-ldaps-backend.properties`

**Use Case**:
- Secure connection to backend LDAP/AD
- Client uses plain LDAP (simpler configuration)
- Backend requires LDAPS

**Key Features**:
- LDAPS connection to backend (port 636)
- Optional TLS client authentication
- CA certificate validation

**Port**: 30389

**Configuration Required**:
- Backend LDAPS server host
- Optional: Truststore with CA certificate
- Optional: Keystore for client authentication

---

### 4. Fully Secure LDAP Proxy (LDAPS Both Sides)

**File**: `04-fully-secure-proxy-ldaps-both-sides.properties`

**Use Case**:
- Maximum security requirements
- End-to-end encryption
- Production environments with strict security policies

**Key Features**:
- LDAPS on client side (port 30636)
- LDAPS on backend side (port 636)
- Complete encryption in transit

**Port**: 30636

**Configuration Required**:
- Server certificate keystore for client-facing LDAPS
- Backend LDAPS server host
- Optional: Backend CA certificate truststore

**Certificate Generation**:
```bash
keytool -genkeypair -alias mleaproxy -keyalg RSA -keysize 2048 \
        -validity 365 -keystore mlproxy.jks -storepass password \
        -dname "CN=localhost,OU=IT,O=Company,L=City,ST=State,C=US"
```

---

### 5. Load Balancing (Round Robin)

**File**: `05-load-balancing-roundrobin.properties`

**Use Case**:
- Distribute load across multiple servers
- Improve performance and availability
- Scale LDAP infrastructure

**Key Features**:
- Round-robin load distribution
- 3+ backend servers supported
- Automatic failover if server unavailable

**Port**: 30389

**Configuration Required**:
- Multiple backend LDAP server hosts and ports

**Load Balancing Modes**:
- `single` - Only first server (dev/test)
- `failover` - Primary with backup (HA)
- `roundrobin` - Distribute evenly (recommended)
- `roundrobindns` - DNS-based (cloud)
- `fewest` - Least connections
- `fastest` - Fastest response

---

### 6. Multi-Site Failover

**File**: `06-multi-site-failover.properties`

**Use Case**:
- Geographic redundancy
- Data center failover
- Disaster recovery

**Key Features**:
- Load balancing within each site
- Automatic failover between sites
- Supports 2+ sites with 2+ servers each

**Port**: 30389

**Configuration Required**:
- Primary site servers (server1, server2)
- Secondary site servers (server3, server4)

**Architecture**:
```
Primary Site (set1):
  +-- server1 (192.168.0.50:10389)
  +-- server2 (192.168.0.51:10389)

Secondary Site (set2):
  +-- server3 (192.168.0.52:10389)
  +-- server4 (192.168.0.53:10389)
```

---

## Configuration Reference

### Common Properties

```properties
# Enable LDAP protocol debugging
mleaproxy.ldap-debug=true

# Listener configuration (map structure - no explicit list needed)
mleaproxy.ldap-listeners.<name>.ip-address=0.0.0.0
mleaproxy.ldap-listeners.<name>.port=10389
mleaproxy.ldap-listeners.<name>.debug-level=DEBUG
mleaproxy.ldap-listeners.<name>.ldap-mode=single
mleaproxy.ldap-listeners.<name>.ldap-sets=set1
mleaproxy.ldap-listeners.<name>.request-processor=processorName

# Request processors
mleaproxy.request-processors.<name>.auth-class=com.marklogic.processors.ProxyRequestProcessor
mleaproxy.request-processors.<name>.debug-level=DEBUG
mleaproxy.request-processors.<name>.params[0]=value

# Server sets
mleaproxy.ldap-sets.<name>.servers=server1,server2
mleaproxy.ldap-sets.<name>.secure=true

# Backend servers
mleaproxy.ldap-servers.<name>.host=hostname
mleaproxy.ldap-servers.<name>.port=389
```

### Load Balancing Modes

| Mode | Behavior | Use Case |
|------|----------|----------|
| `internal` | No backend servers | Standalone mode |
| `single` | First server only | Development/testing |
| `failover` | Primary with backup | High availability |
| `roundrobin` | Even distribution | Load balancing |
| `roundrobindns` | DNS round robin | Cloud environments |
| `fewest` | Least connections | Variable loads |
| `fastest` | Fastest response | Mixed server specs |

---

## Security Configuration

### LDAPS (Client-Facing)

```properties
mleaproxy.ldap-listeners.proxy.secure=true
mleaproxy.ldap-listeners.proxy.keystore=/path/to/keystore.jks
mleaproxy.ldap-listeners.proxy.keystore-password=password
```

### LDAPS (Backend)

```properties
mleaproxy.ldap-sets.set1.secure=true
mleaproxy.ldap-sets.set1.truststore=/path/to/truststore.jks
mleaproxy.ldap-sets.set1.truststore-password=password
```

### TLS Client Authentication (Backend)

```properties
mleaproxy.ldap-sets.set1.keystore=/path/to/client-keystore.jks
mleaproxy.ldap-sets.set1.keystore-password=password
```

---

## Customization Tips

### Attribute Mapping

Map LDAP attributes between client and backend:

```properties
mleaproxy.request-processors.ldapproxy.params[0]=memberOf:isMemberOf
```

Useful for MarkLogic 8 compatibility.

### Custom User Repository Path

Specify custom path to users.json:

```properties
mleaproxy.request-processors.jsonauthenticator.params[0]=/path/to/users.json
```

### Multiple Listeners

Run multiple LDAP listeners simultaneously:

```properties
# JSON-based standalone server
mleaproxy.ldap-listeners.json.port=10389
mleaproxy.ldap-listeners.json.ldap-mode=internal
mleaproxy.ldap-listeners.json.request-processor=jsonauthenticator

# Proxy server
mleaproxy.ldap-listeners.proxy.port=20389
mleaproxy.ldap-listeners.proxy.ldap-mode=single
mleaproxy.ldap-listeners.proxy.ldap-sets=set1
mleaproxy.ldap-listeners.proxy.request-processor=ldapproxy

# Secure proxy
mleaproxy.ldap-listeners.secure.port=30636
mleaproxy.ldap-listeners.secure.secure=true
mleaproxy.ldap-listeners.secure.keystore=/path/to/keystore.jks
mleaproxy.ldap-listeners.secure.keystore-password=password
mleaproxy.ldap-listeners.secure.ldap-sets=set1
mleaproxy.ldap-listeners.secure.request-processor=ldapproxy
```

---

## Testing Examples

### Test Standalone Server

```bash
ldapsearch -H ldap://localhost:20389 \
  -D "cn=manager,ou=users,dc=marklogic,dc=local" \
  -w password \
  -b "ou=users,dc=marklogic,dc=local" \
  "(sAMAccountName=user1)"
```

### Test LDAPS Connection

```bash
ldapsearch -H ldaps://localhost:30636 \
  -D "cn=admin,dc=example,dc=com" \
  -w password \
  -b "dc=example,dc=com" \
  "(uid=user1)"
```

### Test with Specific Attributes

```bash
ldapsearch -H ldap://localhost:20389 \
  -D "cn=manager,ou=users,dc=marklogic,dc=local" \
  -w password \
  -b "ou=users,dc=marklogic,dc=local" \
  -s sub -a always -z 1000 \
  "(sAMAccountName=user1)" \
  "memberOf" "mail" "displayName"
```

---

## Additional Resources

- **[LDAP_GUIDE.md](../../docs/user/LDAP_GUIDE.md)** - Complete LDAP documentation
- **[README.md](../../README.md)** - General application overview
- **[CONFIGURATION_GUIDE.md](../../docs/user/CONFIGURATION_GUIDE.md)** - Configuration reference

---

## Troubleshooting

### Connection Refused

```bash
# Check if listener is running
netstat -an | grep 10389

# Verify bind address (use 0.0.0.0 for all interfaces)
# In properties:
mleaproxy.ldap-listeners.proxy.ip-address=0.0.0.0
```

### Authentication Failed

```bash
# Enable debug logging via command line
java -jar mleaproxy.jar --mleaproxy.ldap-debug=true

# Or in properties:
mleaproxy.ldap-debug=true
mleaproxy.ldap-listeners.proxy.debug-level=DEBUG
mleaproxy.request-processors.ldapproxy.debug-level=DEBUG
```

### TLS/SSL Errors

```bash
# Verify certificate
keytool -list -v -keystore mlproxy.jks -storepass password

# Check certificate validity
keytool -list -v -keystore mlproxy.jks | grep Valid
```

---

**For more information, see the [LDAP Guide](../../docs/user/LDAP_GUIDE.md)**
