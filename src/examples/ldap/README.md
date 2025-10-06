# LDAP Configuration Examples

This directory contains example LDAP configuration files for various deployment scenarios.

---

## 📋 Available Examples

| File | Scenario | Description |
|------|----------|-------------|
| **01-standalone-json-server.properties** | Standalone JSON Server | Simple LDAP server using JSON user data (no backend required) |
| **02-simple-proxy.properties** | Simple Proxy | Forward requests to a backend LDAP server |
| **03-secure-proxy-ldaps-backend.properties** | Secure Backend | LDAP client → MLEAProxy → LDAPS backend |
| **04-fully-secure-proxy-ldaps-both-sides.properties** | Fully Secure | LDAPS client → MLEAProxy → LDAPS backend |
| **05-load-balancing-roundrobin.properties** | Load Balancing | Distribute requests across multiple backend servers |
| **06-multi-site-failover.properties** | Multi-Site Failover | Load balance within sites with inter-site failover |

---

## 🚀 Quick Start

### Using an Example

1. **Choose an example** from the list above
2. **Copy to root directory**:
   ```bash
   cp src/examples/ldap/01-standalone-json-server.properties mleaproxy.properties
   ```
3. **Edit configuration** (update hosts, ports, paths as needed)
4. **Run MLEAProxy**:
   ```bash
   java -jar mleaproxy.jar
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

## 📖 Example Details

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

**Port**: 10389

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
- Attribute mapping support (e.g., memberOf → isMemberOf)
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
  ├─ server1 (192.168.0.50:10389)
  └─ server2 (192.168.0.51:10389)

Secondary Site (set2):
  ├─ server3 (192.168.0.52:10389)
  └─ server4 (192.168.0.53:10389)
```

---

## 🔧 Configuration Reference

### Common Properties

```properties
# Enable LDAP protocol debugging
ldap.debug=true

# Active listeners
listeners=listener1,listener2

# Listener configuration
listener.<name>.ipaddress=0.0.0.0      # Bind address
listener.<name>.port=10389              # Listen port
listener.<name>.debuglevel=DEBUG        # Log level
listener.<name>.ldapmode=single         # Load balancing mode
listener.<name>.ldapset=set1            # Server set
listener.<name>.requestProcessor=name   # Request processor

# Server sets
ldapset.<name>.servers=server1,server2  # Server list
ldapset.<name>.secure=true              # Enable LDAPS

# Backend servers
ldapserver.<name>.host=hostname         # LDAP server host
ldapserver.<name>.port=389              # LDAP server port
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

## 🔒 Security Configuration

### LDAPS (Client-Facing)

```properties
listener.proxy.secure=true
listener.proxy.keystore=/path/to/keystore.jks
listener.proxy.keystorepasswd=password
```

### LDAPS (Backend)

```properties
ldapset.set1.secure=true
ldapset.set1.truststore=/path/to/truststore.jks
ldapset.set1.truststorepasswd=password
```

### TLS Client Authentication (Backend)

```properties
ldapset.set1.keystore=/path/to/client-keystore.jks
ldapset.set1.keystorepasswd=password
```

---

## 📝 Customization Tips

### Attribute Mapping

Map LDAP attributes between client and backend:

```properties
requestProcessor.ldapproxy.parm1=memberOf:isMemberOf
```

Useful for MarkLogic 8 compatibility.

### Custom User Repository Path

Specify custom path to users.json:

```properties
requestProcessor.jsonauthenticator.parm1=/path/to/users.json
```

### Multiple Listeners

Run multiple LDAP listeners simultaneously:

```properties
listeners=json,proxy,secure

listener.json.port=10389
listener.json.ldapmode=internal

listener.proxy.port=20389
listener.proxy.ldapmode=single

listener.secure.port=30636
listener.secure.secure=true
```

---

## 🧪 Testing Examples

### Test Standalone Server

```bash
ldapsearch -H ldap://localhost:10389 \
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
ldapsearch -H ldap://localhost:10389 \
  -D "cn=manager,ou=users,dc=marklogic,dc=local" \
  -w password \
  -b "ou=users,dc=marklogic,dc=local" \
  -s sub -a always -z 1000 \
  "(sAMAccountName=user1)" \
  "memberOf" "mail" "displayName"
```

---

## 📚 Additional Resources

- **[LDAP_GUIDE.md](../../../LDAP_GUIDE.md)** - Complete LDAP documentation
- **[README.md](../../../README.md)** - General application overview
- **[TESTING_GUIDE.md](../../../TESTING_GUIDE.md)** - Testing procedures

---

## 🆘 Troubleshooting

### Connection Refused

```bash
# Check if listener is running
netstat -an | grep 10389

# Verify bind address
listener.proxy.ipaddress=0.0.0.0
```

### Authentication Failed

```bash
# Enable debug logging
ldap.debug=true
listener.proxy.debuglevel=DEBUG
requestProcessor.ldapproxy.debuglevel=DEBUG
```

### TLS/SSL Errors

```bash
# Verify certificate
keytool -list -v -keystore mlproxy.jks -storepass password

# Check certificate validity
keytool -list -v -keystore mlproxy.jks | grep Valid
```

---

<div align="center">

**For more information, see the [LDAP Guide](../../../LDAP_GUIDE.md)**

</div>
