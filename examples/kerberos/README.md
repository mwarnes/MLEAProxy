# Kerberos Configuration Examples

This directory contains example Kerberos configuration files for various deployment scenarios.

---

## 📋 Available Examples

| File | Scenario | Description |
|------|----------|-------------|
| **01-kerberos-basic.properties** | Basic Kerberos | Embedded KDC with default principals |
| **02-kerberos-custom-realm.properties** | Custom Realm | Configure custom Kerberos realm and principals |
| **03-kerberos-with-oauth.properties** | OAuth Bridge | Kerberos authentication + OAuth JWT tokens |
| **04-kerberos-with-saml.properties** | SAML Bridge | Kerberos authentication + SAML assertions |
| **05-kerberos-full-stack.properties** | Full Integration | Kerberos + OAuth + SAML + LDAP roles |

---

## 🚀 Quick Start

### Using an Example

1. **Choose an example** from the list above
2. **Copy to root directory**:
   ```bash
   cp examples/kerberos/01-kerberos-basic.properties kerberos.properties
   ```
3. **Edit configuration** (update realm, principals as needed)
4. **Run MLEAProxy**:
   ```bash
   java -jar target/mlesproxy-2.0.0.jar
   ```

### Testing Your Configuration

```bash
# Test Kerberos authentication
curl -X POST http://localhost:8080/kerberos/auth \
  --negotiate -u : \
  -v

# Test Kerberos to OAuth bridge
curl -X POST http://localhost:8080/kerberos/oauth \
  --negotiate -u : \
  -v

# Test Kerberos to SAML bridge
curl -X POST http://localhost:8080/kerberos/saml \
  --negotiate -u : \
  -v
```

---

## 📖 Example Details

### 1. Basic Kerberos Configuration

**File**: `01-kerberos-basic.properties`

**Use Case**:
- Development and testing
- Embedded Kerberos KDC
- Default realm and principals

**Key Features**:
- Embedded KDC using Apache Kerby
- Pre-configured principals (admin, user, service)
- SPNEGO HTTP authentication

**Endpoints**:
- Authentication: `http://localhost:8080/kerberos/auth`

**Default Configuration**:
- Realm: `EXAMPLE.COM`
- KDC Port: 88
- Principals: `admin@EXAMPLE.COM`, `user@EXAMPLE.COM`, `HTTP/localhost@EXAMPLE.COM`

---

### 2. Custom Kerberos Realm

**File**: `02-kerberos-custom-realm.properties`

**Use Case**:
- Custom realm configuration
- Integration with existing infrastructure
- Multiple principals

**Key Features**:
- Configurable realm name
- Custom principal list
- Custom keytab paths

**Configuration Options**:
```properties
# Custom realm
kerberos.realm=MYCOMPANY.COM

# Custom KDC port
kerberos.kdc.port=8888

# Custom principals (comma-separated)
kerberos.principals=admin@MYCOMPANY.COM,user1@MYCOMPANY.COM,user2@MYCOMPANY.COM,HTTP/myserver@MYCOMPANY.COM
```

---

### 3. Kerberos with OAuth Bridge

**File**: `03-kerberos-with-oauth.properties`

**Use Case**:
- Kerberos authentication
- Generate OAuth JWT tokens
- Bridge legacy Kerberos to modern OAuth

**Key Features**:
- SPNEGO authentication
- Automatic JWT token generation
- Token includes Kerberos principal and groups

**Endpoint**: `/kerberos/oauth`

**Flow**:
```
1. Client authenticates with Kerberos ticket
2. MLEAProxy validates ticket
3. MLEAProxy generates OAuth JWT token
4. Client receives JWT for API access
```

**Response**:
```json
{
  "access_token": "eyJhbGc...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "principal": "admin@EXAMPLE.COM"
}
```

---

### 4. Kerberos with SAML Bridge

**File**: `04-kerberos-with-saml.properties`

**Use Case**:
- Kerberos authentication
- Generate SAML assertions
- Bridge Kerberos to SAML-based systems

**Key Features**:
- SPNEGO authentication
- Automatic SAML assertion generation
- Assertion includes Kerberos principal and groups

**Endpoint**: `/kerberos/saml`

**Flow**:
```
1. Client authenticates with Kerberos ticket
2. MLEAProxy validates ticket
3. MLEAProxy generates SAML assertion
4. Client receives SAML for application access
```

---

### 5. Full Kerberos Integration

**File**: `05-kerberos-full-stack.properties`

**Use Case**:
- Complete authentication solution
- Kerberos + OAuth + SAML + LDAP roles
- Enterprise integration

**Key Features**:
- Kerberos authentication
- LDAP group-based role resolution
- OAuth JWT token generation
- SAML assertion generation
- All protocol bridges enabled

**Endpoints**:
- `/kerberos/auth` - SPNEGO authentication
- `/kerberos/oauth` - Generate OAuth JWT
- `/kerberos/saml` - Generate SAML assertion

**Configuration**:
- Kerberos realm and principals
- LDAP connection for group lookup
- OAuth token settings
- SAML assertion settings

---

## 🔧 Configuration Reference

### Common Properties

```properties
# Enable/disable Kerberos KDC
kerberos.enabled=true

# Kerberos realm
kerberos.realm=EXAMPLE.COM

# KDC bind address and port
kerberos.kdc.host=localhost
kerberos.kdc.port=88

# Principals (comma-separated)
kerberos.principals=admin@EXAMPLE.COM,user@EXAMPLE.COM,HTTP/localhost@EXAMPLE.COM

# Service principal for SPNEGO
kerberos.service.principal=HTTP/localhost@EXAMPLE.COM

# Keytab path (optional)
#kerberos.keytab.path=/path/to/service.keytab
```

### LDAP Role Integration

```properties
# LDAP server for group lookup
ldap.role.server=ldap://localhost:10389
ldap.role.base=ou=groups,dc=example,dc=com
ldap.role.attribute=memberOf
```

---

## 🔒 Security Considerations

### Embedded KDC

**Development**:
- Use embedded KDC for testing and development
- Pre-configured principals with simple passwords
- No external infrastructure required

**Production**:
- **DO NOT use embedded KDC in production**
- Connect to existing Kerberos infrastructure
- Use proper keytabs and service principals
- Implement key rotation policies

### Service Principal

**Configuration**:
```properties
# Service principal should match hostname
kerberos.service.principal=HTTP/your-hostname@YOUR-REALM

# Use dedicated keytab
kerberos.keytab.path=/etc/security/keytabs/service.keytab
```

**Generate Keytab**:
```bash
# On KDC server
kadmin.local -q "addprinc -randkey HTTP/your-hostname@YOUR-REALM"
kadmin.local -q "ktadd -k /tmp/service.keytab HTTP/your-hostname@YOUR-REALM"

# Copy to application server
scp /tmp/service.keytab app-server:/etc/security/keytabs/
chmod 600 /etc/security/keytabs/service.keytab
```

---

## 🧪 Testing Examples

### Test Basic Kerberos Authentication

```bash
# Obtain Kerberos ticket
kinit admin@EXAMPLE.COM

# Authenticate with ticket
curl -X POST http://localhost:8080/kerberos/auth \
  --negotiate -u : \
  -v
```

### Test Kerberos to OAuth Bridge

```bash
# Get OAuth JWT token using Kerberos ticket
curl -X POST http://localhost:8080/kerberos/oauth \
  --negotiate -u : \
  -H "Content-Type: application/x-www-form-urlencoded"

# Response includes JWT token
```

### Test Kerberos to SAML Bridge

```bash
# Get SAML assertion using Kerberos ticket
curl -X POST http://localhost:8080/kerberos/saml \
  --negotiate -u : \
  -H "Content-Type: application/x-www-form-urlencoded"

# Response includes SAML assertion
```

### Verify Kerberos Ticket

```bash
# List current tickets
klist

# Verbose ticket information
klist -v
```

### Test with Different Principals

```bash
# Authenticate as different user
kinit user@EXAMPLE.COM

# Test authentication
curl -X POST http://localhost:8080/kerberos/auth --negotiate -u :

# Destroy ticket
kdestroy
```

---

## 📚 Additional Resources

- **[KERBEROS_GUIDE.md](../../docs/user/KERBEROS_GUIDE.md)** - Complete Kerberos documentation
- **[README.md](../../README.md)** - General application overview
- **[MIT Kerberos Documentation](https://web.mit.edu/kerberos/krb5-latest/doc/)** - Official Kerberos documentation

---

## 🆘 Troubleshooting

### KDC Not Starting

```bash
# Enable debug logging
logging.level.com.marklogic.handlers.KerberosKDCServer=DEBUG

# Check port availability
netstat -an | grep 88

# Verify configuration
kerberos.enabled=true
kerberos.kdc.port=88
```

### SPNEGO Authentication Failed

```bash
# Verify service principal
kerberos.service.principal=HTTP/localhost@EXAMPLE.COM

# Check keytab (if using external KDC)
klist -k /path/to/service.keytab

# Enable Kerberos debug output
-Dsun.security.krb5.debug=true
```

### No Kerberos Ticket

```bash
# Obtain ticket
kinit admin@EXAMPLE.COM

# Verify ticket
klist

# Check ticket expiration
klist -v
```

### Clock Skew Error

```bash
# Kerberos requires time synchronization (within 5 minutes)
# Sync system clock
sudo ntpdate -u time.nist.gov

# Or use systemd-timesyncd
sudo timedatectl set-ntp true
```

---

<div align="center">

**For more information, see the [Kerberos Guide](../../docs/user/KERBEROS_GUIDE.md)**

</div>
