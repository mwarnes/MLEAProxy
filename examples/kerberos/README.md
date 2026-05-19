# Kerberos Configuration Examples

This directory contains example Kerberos configuration files for various deployment scenarios.

---

## Available Examples

| File | Scenario | Description |
|------|----------|-------------|
| **01-kerberos-basic.properties** | Basic Kerberos | Embedded KDC with default principals |
| **02-kerberos-custom-realm.properties** | Custom Realm | Configure custom Kerberos realm and principals |
| **03-kerberos-with-oauth.properties** | OAuth Bridge | Kerberos authentication + OAuth JWT tokens |
| **04-kerberos-with-saml.properties** | SAML Bridge | Kerberos authentication + SAML assertions |
| **05-kerberos-full-stack.properties** | Full Integration | Kerberos + OAuth + SAML + LDAP roles |

---

## Quick Start

### Using an Example

1. **Choose an example** from the list above
2. **Run MLEAProxy with the example**:
   ```bash
   java -Dmleaproxy.properties=examples/kerberos/01-kerberos-basic.properties \
        -jar target/mlesproxy-2.0.0.jar
   ```
3. **Or copy and customize**:
   ```bash
   cp examples/kerberos/01-kerberos-basic.properties mleaproxy.properties
   # Edit mleaproxy.properties as needed
   java -jar target/mlesproxy-2.0.0.jar
   ```

### Expected Startup Output

When Kerberos KDC starts successfully, you'll see:

```
  __  __ _     ___   _   ___
 |  \/  | |   | __| /_\ | _ \_ _ _____ ___  _
 | |\/| | |__ | _| / _ \|  _/ '_/ _ \ \ / || |
 |_|  |_|____|___/_/ \_\_| |_| \___/_\_\_, |
                                       |__/
 :: MLEAProxy ::                    (v2.0.0)

INFO  --- Starting MLEAProxy...
INFO  --- Kerberos KDC Configuration:
INFO  ---   Realm: EXAMPLE.COM
INFO  ---   KDC Host: localhost
INFO  ---   KDC Port: 88
INFO  --- Starting embedded Kerberos KDC...
INFO  --- Kerberos KDC started successfully
INFO  --- Created principal: admin@EXAMPLE.COM
INFO  --- Created principal: user@EXAMPLE.COM
INFO  --- Created principal: HTTP/localhost@EXAMPLE.COM
INFO  --- Kerberos endpoints available:
INFO  ---   POST /kerberos/auth  - SPNEGO authentication
INFO  ---   POST /kerberos/oauth - Auth returning OAuth token
INFO  ---   POST /kerberos/saml  - Auth returning SAML assertion
```

---

## Testing Kerberos Authentication

### Prerequisites

Ensure you have Kerberos client tools installed:
- **macOS**: `brew install krb5`
- **Ubuntu/Debian**: `apt install krb5-user`
- **RHEL/CentOS**: `yum install krb5-workstation`

### Step 1: Obtain a Kerberos Ticket

```bash
# Authenticate as admin (password: password)
kinit admin@EXAMPLE.COM

# Or authenticate as user
kinit user@EXAMPLE.COM
```

### Step 2: Verify Your Ticket

```bash
# List current tickets
klist

# Expected output:
# Ticket cache: FILE:/tmp/krb5cc_501
# Default principal: admin@EXAMPLE.COM
#
# Valid starting       Expires              Service principal
# 05/18/2026 10:00:00  05/19/2026 10:00:00  krbtgt/EXAMPLE.COM@EXAMPLE.COM
```

### Step 3: Test Authentication Endpoints

```bash
# Test basic SPNEGO authentication
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

### Step 4: Cleanup

```bash
# Destroy your Kerberos ticket
kdestroy

# Verify ticket is destroyed
klist
# Should show: klist: No credentials cache found
```

---

## Example Details

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
mleaproxy.kerberos.realm=MYCOMPANY.COM

# Custom KDC port
mleaproxy.kerberos.kdc-port=8888

# Custom principals (comma-separated)
mleaproxy.kerberos.service-principals=admin@MYCOMPANY.COM,user1@MYCOMPANY.COM,user2@MYCOMPANY.COM,HTTP/myserver@MYCOMPANY.COM
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

## Configuration Reference

### Common Properties

```properties
# Enable/disable Kerberos KDC
mleaproxy.kerberos.enabled=true

# Kerberos realm
mleaproxy.kerberos.realm=EXAMPLE.COM

# KDC bind address and port
mleaproxy.kerberos.kdc-host=localhost
mleaproxy.kerberos.kdc-port=88

# Service principals (comma-separated)
mleaproxy.kerberos.service-principals=admin@EXAMPLE.COM,user@EXAMPLE.COM,HTTP/localhost@EXAMPLE.COM

# Keytab path (optional, for external KDC)
#mleaproxy.kerberos.keytab-path=/path/to/service.keytab

# Working directory for KDC data
#mleaproxy.kerberos.work-dir=/tmp/kdc

# Enable debug logging
#mleaproxy.kerberos.debug=true
```

### Ticket Configuration

```properties
# Ticket lifetime in seconds (default: 86400 = 24 hours)
mleaproxy.kerberos.ticket-lifetime=86400

# Renewable lifetime in seconds (default: 604800 = 7 days)
mleaproxy.kerberos.renewable-lifetime=604800

# Clock skew tolerance in seconds (default: 300 = 5 minutes)
mleaproxy.kerberos.clock-skew=300
```

### LDAP Principal Import

```properties
# Import principals from LDAP directory
mleaproxy.kerberos.import-principals-from-ldap=true

# LDAP base DN for principal search
mleaproxy.kerberos.ldap-base-dn=ou=users,dc=example,dc=com
```

### LDAP Role Integration

```properties
# LDAP server for group lookup
mleaproxy.ldap.role-server=ldap://localhost:10389
mleaproxy.ldap.role-base=ou=groups,dc=example,dc=com
mleaproxy.ldap.role-attribute=memberOf
```

---

## Security Considerations

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
mleaproxy.kerberos.service-principals=HTTP/your-hostname@YOUR-REALM

# Use dedicated keytab for external KDC
mleaproxy.kerberos.keytab-path=/etc/security/keytabs/service.keytab
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

## Complete Testing Session

Here's a complete testing session demonstrating all Kerberos endpoints:

```bash
# Start MLEAProxy with Kerberos enabled
java -Dmleaproxy.properties=examples/kerberos/05-kerberos-full-stack.properties \
     -jar target/mlesproxy-2.0.0.jar &

# Wait for startup
sleep 5

# Obtain Kerberos ticket
kinit admin@EXAMPLE.COM
# Enter password: password

# Verify ticket obtained
klist
# Ticket cache: FILE:/tmp/krb5cc_501
# Default principal: admin@EXAMPLE.COM
# Valid starting       Expires              Service principal
# 05/18/2026 10:00:00  05/19/2026 10:00:00  krbtgt/EXAMPLE.COM@EXAMPLE.COM

# Test SPNEGO authentication
curl -X POST http://localhost:8080/kerberos/auth --negotiate -u :
# Response: {"authenticated":true,"principal":"admin@EXAMPLE.COM"}

# Get OAuth JWT token
curl -X POST http://localhost:8080/kerberos/oauth --negotiate -u :
# Response: {"access_token":"eyJ...","token_type":"Bearer","expires_in":3600}

# Get SAML assertion
curl -X POST http://localhost:8080/kerberos/saml --negotiate -u :
# Response: <saml2:Assertion>...</saml2:Assertion>

# Test with different principal
kdestroy
kinit user@EXAMPLE.COM
curl -X POST http://localhost:8080/kerberos/auth --negotiate -u :
# Response: {"authenticated":true,"principal":"user@EXAMPLE.COM"}

# Cleanup
kdestroy
```

---

## Troubleshooting

### KDC Not Starting

```bash
# Enable debug logging
logging.level.com.marklogic.handlers.KerberosKDCServer=DEBUG

# Check port availability
netstat -an | grep 88

# Verify configuration
mleaproxy.kerberos.enabled=true
mleaproxy.kerberos.kdc-port=88
```

### SPNEGO Authentication Failed

```bash
# Verify service principal is configured
mleaproxy.kerberos.service-principals=HTTP/localhost@EXAMPLE.COM

# Check keytab (if using external KDC)
klist -k /path/to/service.keytab

# Enable Kerberos debug output
java -Dsun.security.krb5.debug=true -jar target/mlesproxy-2.0.0.jar
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

# Adjust clock skew tolerance if needed
mleaproxy.kerberos.clock-skew=600
```

### curl negotiate not working

```bash
# Ensure curl was built with SPNEGO support
curl --version | grep -i spnego

# On macOS, use Homebrew curl
brew install curl
/opt/homebrew/opt/curl/bin/curl -X POST http://localhost:8080/kerberos/auth --negotiate -u :
```

---

## Additional Resources

- **[KERBEROS_GUIDE.md](../../docs/user/KERBEROS_GUIDE.md)** - Complete Kerberos documentation
- **[README.md](../../README.md)** - General application overview
- **[MIT Kerberos Documentation](https://web.mit.edu/kerberos/krb5-latest/doc/)** - Official Kerberos documentation

---

<div align="center">

**For more information, see the [Kerberos Guide](../../docs/user/KERBEROS_GUIDE.md)**

</div>
