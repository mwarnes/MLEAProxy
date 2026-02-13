# MLEAProxy Startup Scripts

This directory contains convenience scripts for starting MLEAProxy in different modes.

---

## 📋 Available Scripts

| Script | Description |
|--------|-------------|
| **start-ldap.sh** | Start LDAP proxy mode with comprehensive server details |
| **start-oauth.sh** | Start OAuth 2.0 mode with comprehensive server details |
| **start-saml.sh** | Start SAML 2.0 mode with comprehensive server details |
| **start-kerberos.sh** | Start Kerberos mode with comprehensive server details |
| **start-all.sh** | Start all protocols (LDAP + OAuth + SAML + Kerberos) |
| **stop.sh** | Stop MLEAProxy (find and kill Java process) |
| **status.sh** | Check MLEAProxy status |

**Note**: All startup scripts display comprehensive server information including endpoints, credentials, available users, and testing commands upon successful launch.

---

## 🚀 Usage

### Start in Specific Mode

```bash
# LDAP mode
./scripts/start-ldap.sh

# OAuth mode
./scripts/start-oauth.sh

# SAML mode
./scripts/start-saml.sh

# Kerberos mode
./scripts/start-kerberos.sh

# All protocols
./scripts/start-all.sh
```

### Stop MLEAProxy

```bash
./scripts/stop.sh
```

### Check Status

```bash
./scripts/status.sh
```

---

## 🔧 Script Configuration

### Prerequisites

All scripts assume:
- MLEAProxy JAR is at: `target/mlesproxy-2.0.0.jar`
- Configuration files are in project root
- Java 21+ is installed and in PATH

### Custom JAR Location

Edit the scripts to change JAR path:

```bash
# Default
JAR_FILE="target/mlesproxy-2.0.0.jar"

# Custom
JAR_FILE="/path/to/mlesproxy.jar"
```

### Custom Configuration

Scripts use example configurations from `examples/` directory.
To use custom configuration:

```bash
# Copy example to root
cp examples/ldap/01-standalone-json-server.properties ldap.properties

# Then start
./scripts/start-ldap.sh
```

---

## 📖 Script Details

### start-ldap.sh

Starts MLEAProxy in LDAP proxy mode with detailed server information.

**Configuration Used**: `examples/ldap/01-standalone-json-server.properties`

**Features**:
- Standalone JSON LDAP server
- In-memory directory
- No backend required

**Endpoints**:
- LDAP Proxy: `ldap://localhost:10389` (INTERNAL mode, JSON authentication)
- In-Memory Server: `ldap://localhost:60389` (Base DN: dc=MarkLogic,dc=Local)

**Startup Display Includes**:

- LDAP proxy and in-memory server endpoints
- Bind DN and password for both servers
- Available users (admin, user1, user2, developer) with full details:
  - Distinguished Names (DNs)
  - Passwords
  - Roles
  - sAMAccountName
  - Email addresses
- Testing commands with ldapsearch examples
- MarkLogic integration configuration files

---

### start-oauth.sh

Starts MLEAProxy in OAuth 2.0 mode with detailed token configuration.

**Configuration Used**: `examples/oauth/01-oauth-basic.properties`

**Features**:
- JWT token generation with RS256
- JWKS endpoint for public keys
- OpenID Connect discovery endpoint

**Endpoints**:
- Token: `http://localhost:8080/oauth/token`
- JWKS: `http://localhost:8080/oauth/jwks`
- Discovery: `http://localhost:8080/.well-known/openid-configuration`

**Startup Display Includes**:

- All OAuth endpoints with descriptions
- Token configuration (validity periods, algorithm, key size)
- JWT claims (standard and custom)
- Available users (admin, user1, user2, developer) with passwords and roles
- OAuth client credentials (marklogic/secret)
- Testing commands for all grant types:
  - Password grant
  - Client credentials grant
  - Refresh token grant
- Example token response with decoded JWT structure

---

### start-saml.sh

Starts MLEAProxy in SAML 2.0 IdP mode with detailed assertion configuration.

**Configuration Used**: `examples/saml/01-saml-basic.properties`

**Features**:
- SAML 2.0 Identity Provider
- Digital signature generation
- Assertion generation with custom attributes

**Endpoints**:
- Auth: `http://localhost:8080/saml/auth`
- Metadata: `http://localhost:8080/saml/metadata`
- Wrap Assertion: `http://localhost:8080/saml/wrapassertion`
- CA Certificates: `http://localhost:8080/saml/cacerts`

**Startup Display Includes**:

- All SAML endpoints with descriptions
- Assertion validity configuration (300 seconds)
- Signature configuration (RSA-SHA256, 2048-bit)
- SAML assertion attributes (Subject, Issuer, Audience, custom attributes)
- Available users (admin, user1, user2, developer) with:
  - Usernames and passwords
  - Roles that appear in SAML assertions
  - Email addresses
  - Example SAML attribute statements
- Testing commands for authentication and metadata retrieval
- Example SAML assertion structure
- MarkLogic integration steps
- Role resolution priority order

---

### start-kerberos.sh

Starts MLEAProxy in Kerberos mode with embedded KDC and protocol bridges.

**Configuration Used**: `examples/kerberos/01-kerberos-basic.properties`

**Features**:
- Embedded Apache Kerby KDC
- SPNEGO authentication
- Protocol bridges (Kerberos → OAuth, Kerberos → SAML)
- In-memory keytab generation

**KDC**: `localhost:88` (Realm: EXAMPLE.COM)

**Endpoints**:
- Auth: `http://localhost:8080/kerberos/auth`
- OAuth Bridge: `http://localhost:8080/kerberos/oauth`
- SAML Bridge: `http://localhost:8080/kerberos/saml`

**Startup Display Includes**:

- KDC configuration (host, port, realm, encryption types)
- Ticket configuration (lifetime, renewal, supported mechanisms)
- Available principals with passwords:
  - `admin@EXAMPLE.COM` (password: password)
  - `user@EXAMPLE.COM` (password: password)
  - `HTTP/localhost@EXAMPLE.COM` (service principal)
- All Kerberos HTTP endpoints with descriptions
- Protocol bridge flow explanations (Kerberos → OAuth, Kerberos → SAML)
- Step-by-step testing commands:
  - kinit to obtain tickets
  - klist to verify tickets
  - curl commands for all endpoints
  - kdestroy to clean up
- Client configuration for `/etc/krb5.conf`
- MarkLogic integration options (3 different approaches)
- Troubleshooting guide with common issues and solutions

---

### start-all.sh

Starts MLEAProxy with all protocols enabled.

**Configuration Used**: Multiple property files combined

**Features**:
- LDAP proxy + standalone server
- OAuth 2.0 JWT tokens
- SAML 2.0 assertions
- Kerberos authentication

**All Endpoints Active**

---

### stop.sh

Stops running MLEAProxy instance.

**Features**:
- Finds Java process running mlesproxy
- Gracefully terminates (SIGTERM)
- Verifies shutdown

---

### status.sh

Checks MLEAProxy status.

**Displays**:
- Process ID (if running)
- Configuration in use
- Active endpoints
- Log file location

---

## 📺 Example Script Output

This section shows what you'll see when running each startup script.

### Example: start-ldap.sh

**Command:**

```bash
./scripts/start-ldap.sh
```

**Output:**

```ansi
[0;32mStarting MLEAProxy in LDAP mode...[0m
[0;32m╔════════════════════════════════════════════════════════════════════════════╗[0m
[0;32m║                    MLEAProxy Started Successfully!                         ║[0m
[0;32m╚════════════════════════════════════════════════════════════════════════════╝[0m

[0;36mProcess Information:[0m
  PID: 12345
  Log File: mleaproxy-ldap.log

[0;34m═══════════════════════════════════════════════════════════════════════════[0m
[0;34m                           LDAP Endpoints[0m
[0;34m═══════════════════════════════════════════════════════════════════════════[0m

[0;36m📡 LDAP Proxy Listener:[0m
  URL: ldap://localhost:10389
  Description: Proxies requests to backend or processes internally
  Mode: INTERNAL (standalone JSON authentication)

[0;36m📡 In-Memory LDAP Directory Server:[0m
  URL: ldap://localhost:60389
  Description: Standalone LDAP directory with test data
  Base DN: dc=MarkLogic,dc=Local

[0;34m═══════════════════════════════════════════════════════════════════════════[0m
[0;34m                         Authentication Details[0m
[0;34m═══════════════════════════════════════════════════════════════════════════[0m

[0;36m🔐 Bind Credentials (In-Memory Server):[0m
  Bind DN: cn=Directory Manager
  Password: password

[0;36m🔐 Bind Credentials (Proxy - JSON Auth):[0m
  Bind DN: cn=manager,ou=users,dc=marklogic,dc=local
  Password: password

[0;34m═══════════════════════════════════════════════════════════════════════════[0m
[0;34m                            Available Users[0m
[0;34m═══════════════════════════════════════════════════════════════════════════[0m

[0;36m👤 User: admin[0m
  DN: cn=admin,ou=users,dc=marklogic,dc=local
  Password: admin
  Roles: admin, marklogic-admin
  sAMAccountName: admin
  Email: admin@marklogic.local

[0;36m👤 User: user1[0m
  DN: cn=user1,ou=users,dc=marklogic,dc=local
  Password: password
  Roles: user, reader
  sAMAccountName: user1
  Email: user1@marklogic.local

[... additional users ...]

[0;34m═══════════════════════════════════════════════════════════════════════════[0m
[0;34m                          Testing Commands[0m
[0;34m═══════════════════════════════════════════════════════════════════════════[0m

[0;36m🧪 Test Proxy with JSON Auth (admin):[0m
  ldapsearch -H ldap://localhost:10389 \
    -D "cn=admin,ou=users,dc=marklogic,dc=local" \
    -w admin \
    -b "ou=users,dc=marklogic,dc=local" \
    "(sAMAccountName=admin)"
```

---

### Example: start-oauth.sh

**Command:**

```bash
./scripts/start-oauth.sh
```

**Output:**

```ansi
[0;32mStarting MLEAProxy in OAuth mode...[0m
[0;32m╔════════════════════════════════════════════════════════════════════════════╗[0m
[0;32m║                    MLEAProxy Started Successfully!                         ║[0m
[0;32m╚════════════════════════════════════════════════════════════════════════════╝[0m

[0;36mProcess Information:[0m
  PID: 12346
  Log File: mleaproxy-oauth.log

[0;34m═══════════════════════════════════════════════════════════════════════════[0m
[0;34m                         OAuth 2.0 Endpoints[0m
[0;34m═══════════════════════════════════════════════════════════════════════════[0m

[0;36m🔑 Token Endpoint:[0m
  URL: http://localhost:8080/oauth/token
  Methods: POST
  Description: Generate JWT access tokens
  Grant Types: password, client_credentials, refresh_token

[0;36m🔐 JWKS Endpoint (JSON Web Key Set):[0m
  URL: http://localhost:8080/oauth/jwks
  Methods: GET
  Description: Public keys for token verification
  Format: JWK (JSON Web Key)

[0;36m📋 Discovery Endpoint (OpenID Configuration):[0m
  URL: http://localhost:8080/.well-known/openid-configuration
  Methods: GET
  Description: OAuth/OIDC server metadata (RFC 8414)
  Contains: issuer, endpoints, grant types, algorithms

[0;34m═══════════════════════════════════════════════════════════════════════════[0m
[0;34m                         Token Configuration[0m
[0;34m═══════════════════════════════════════════════════════════════════════════[0m

[0;36m⏱️  Token Validity:[0m
  Access Token: 3600 seconds (1 hour)
  Refresh Token: 86400 seconds (1 day)
  Algorithm: RS256 (RSA + SHA-256)
  Key Size: 2048-bit RSA

[0;36m📝 JWT Token Claims (Standard):[0m
  iss (Issuer): http://localhost:8080
  sub (Subject): username
  aud (Audience): marklogic
  exp (Expiration): issued_at + validity
  iat (Issued At): current timestamp
  jti (JWT ID): unique token identifier

[0;36m📝 JWT Token Claims (Custom):[0m
  username: User's username
  roles: Array of user roles
  client_id: OAuth client identifier

[0;34m═══════════════════════════════════════════════════════════════════════════[0m
[0;34m                            Available Users[0m
[0;34m═══════════════════════════════════════════════════════════════════════════[0m

[0;36m👤 User: admin[0m
  Username: admin
  Password: admin
  Roles: ["admin", "marklogic-admin"]
  Claims: username=admin, roles=[admin,marklogic-admin]

[... additional users ...]

[0;34m═══════════════════════════════════════════════════════════════════════════[0m
[0;34m                          Testing Commands[0m
[0;34m═══════════════════════════════════════════════════════════════════════════[0m

[0;36m🧪 Get Access Token (Password Grant - admin):[0m
  curl -X POST http://localhost:8080/oauth/token \
    -d "grant_type=password" \
    -d "username=admin" \
    -d "password=admin" \
    -d "client_id=marklogic" \
    -d "client_secret=secret"
```

---

### Example: start-saml.sh

**Command:**

```bash
./scripts/start-saml.sh
```

**Output:**

```ansi
[0;32mStarting MLEAProxy in SAML mode...[0m
[0;32m╔════════════════════════════════════════════════════════════════════════════╗[0m
[0;32m║                    MLEAProxy Started Successfully!                         ║[0m
[0;32m╚════════════════════════════════════════════════════════════════════════════╝[0m

[0;36mProcess Information:[0m
  PID: 12347
  Log File: mleaproxy-saml.log

[0;34m═══════════════════════════════════════════════════════════════════════════[0m
[0;34m                         SAML 2.0 Endpoints[0m
[0;34m═══════════════════════════════════════════════════════════════════════════[0m

[0;36m🔐 Authentication Endpoint:[0m
  URL: http://localhost:8080/saml/auth
  Methods: POST
  Description: Authenticates users and generates SAML assertions
  Parameters: username, password, roles (optional)

[0;36m📄 Metadata Endpoint:[0m
  URL: http://localhost:8080/saml/metadata
  Methods: GET
  Description: SAML IdP metadata (EntityDescriptor)
  Format: XML (SAML 2.0 Metadata)
  Contains: Entity ID, SSO endpoints, certificates, supported bindings

[0;34m═══════════════════════════════════════════════════════════════════════════[0m
[0;34m                         Assertion Configuration[0m
[0;34m═══════════════════════════════════════════════════════════════════════════[0m

[0;36m⏱️  Assertion Validity:[0m
  Validity Duration: 300 seconds (5 minutes)
  NotBefore: Current timestamp
  NotOnOrAfter: Current timestamp + 300s

[0;36m🔐 Signature Configuration:[0m
  Algorithm: RSA-SHA256 (http://www.w3.org/2001/04/xmldsig-more#rsa-sha256)
  Digest Method: SHA-256
  Certificate: Bundled X.509 certificate (or custom if configured)
  Key Size: 2048-bit RSA

[0;34m═══════════════════════════════════════════════════════════════════════════[0m
[0;34m                            Available Users[0m
[0;34m═══════════════════════════════════════════════════════════════════════════[0m

[0;36m👤 User: admin[0m
  Username: admin
  Password: admin
  Roles: ["admin", "marklogic-admin"]
  Email: admin@marklogic.local
  SAML Attributes:
    <saml:Attribute Name="username"><saml:AttributeValue>admin</saml:AttributeValue></saml:Attribute>
    <saml:Attribute Name="roles"><saml:AttributeValue>admin,marklogic-admin</saml:AttributeValue></saml:Attribute>

[... additional users ...]

[0;34m═══════════════════════════════════════════════════════════════════════════[0m
[0;34m                          Testing Commands[0m
[0;34m═══════════════════════════════════════════════════════════════════════════[0m

[0;36m🧪 Authenticate and Get SAML Assertion (admin):[0m
  curl -X POST http://localhost:8080/saml/auth \
    -d "username=admin" \
    -d "password=admin" \
    -d "roles=admin,marklogic-admin"
```

---

### Example: start-kerberos.sh

**Command:**

```bash
./scripts/start-kerberos.sh
```

**Output:**

```ansi
[0;32mStarting MLEAProxy in Kerberos mode...[0m
[0;32m╔════════════════════════════════════════════════════════════════════════════╗[0m
[0;32m║                    MLEAProxy Started Successfully!                         ║[0m
[0;32m╚════════════════════════════════════════════════════════════════════════════╝[0m

[0;36mProcess Information:[0m
  PID: 12348
  Log File: mleaproxy-kerberos.log

[0;34m═══════════════════════════════════════════════════════════════════════════[0m
[0;34m                    Kerberos KDC (Key Distribution Center)[0m
[0;34m═══════════════════════════════════════════════════════════════════════════[0m

[0;36m🔐 KDC Configuration:[0m
  Host: localhost
  Port: 88 (standard Kerberos port)
  Realm: EXAMPLE.COM
  Type: Embedded Apache Kerby KDC
  Status: Running (embedded in MLEAProxy process)

[0;36m🎫 Ticket Configuration:[0m
  Ticket Lifetime: 86400 seconds (24 hours)
  Renewable Lifetime: 604800 seconds (7 days)
  Encryption Types: AES256-CTS-HMAC-SHA1-96, AES128-CTS-HMAC-SHA1-96
  Supported Auth Mechanisms: SPNEGO, Kerberos V5

[0;34m═══════════════════════════════════════════════════════════════════════════[0m
[0;34m                         Kerberos Principals[0m
[0;34m═══════════════════════════════════════════════════════════════════════════[0m

[0;36m👤 User Principal: admin@EXAMPLE.COM[0m
  Type: User Principal
  Password: password
  Description: Administrator user account
  Use Case: Testing administrative authentication
  Associated User: admin (from users.json)
  Roles: ["admin", "marklogic-admin"]

[0;36m👤 User Principal: user@EXAMPLE.COM[0m
  Type: User Principal
  Password: password
  Description: Standard user account
  Use Case: Testing standard user authentication
  Associated User: user1 (from users.json)
  Roles: ["user", "reader"]

[0;36m🔧 Service Principal: HTTP/localhost@EXAMPLE.COM[0m
  Type: Service Principal
  Password: (auto-generated)
  Description: Service principal for SPNEGO authentication
  Use Case: HTTP Negotiate authentication
  Keytab: Auto-generated and stored in memory

[0;34m═══════════════════════════════════════════════════════════════════════════[0m
[0;34m                       Kerberos HTTP Endpoints[0m
[0;34m═══════════════════════════════════════════════════════════════════════════[0m

[0;36m🔐 Kerberos Authentication Endpoint:[0m
  URL: http://localhost:8080/kerberos/auth
  Methods: POST
  Description: SPNEGO authentication using Kerberos tickets
  Auth Mechanism: HTTP Negotiate (SPNEGO)
  Response: Authentication success with user information

[0;36m🔄 Kerberos to OAuth Bridge:[0m
  URL: http://localhost:8080/kerberos/oauth
  Methods: POST
  Description: Converts Kerberos ticket to OAuth JWT token
  Auth Mechanism: HTTP Negotiate (SPNEGO)
  Response: OAuth 2.0 token response (access_token, token_type, expires_in)
  Use Case: Integrate Kerberos-authenticated users with OAuth-based APIs

[0;34m═══════════════════════════════════════════════════════════════════════════[0m
[0;34m                          Testing Commands[0m
[0;34m═══════════════════════════════════════════════════════════════════════════[0m

[0;36m🧪 Step 1: Obtain Kerberos Ticket (admin):[0m
  kinit admin@EXAMPLE.COM
  # Enter password when prompted: password

[0;36m🧪 Step 2: Verify Ticket:[0m
  klist
  # Should show ticket for admin@EXAMPLE.COM
  # Valid starting: [current time]
  # Expires: [current time + 24 hours]

[0;36m🧪 Step 3a: Test Kerberos Authentication:[0m
  curl -X POST http://localhost:8080/kerberos/auth \
    --negotiate -u : \
    -v

  # Expected response:
  # {"status":"authenticated","principal":"admin@EXAMPLE.COM"}
```

---

### Example: Testing with curl

After starting the OAuth server, here's what a token request looks like:

**Command:**

```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" \
  -d "client_id=marklogic" \
  -d "client_secret=secret"
```

**Response:**

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAiLCJzdWIiOiJhZG1pbiIsImF1ZCI6Im1hcmtsb2dpYyIsImV4cCI6MTcwNzg0MDAwMCwiaWF0IjoxNzA3ODM2NDAwLCJqdGkiOiJ1bmlxdWUtand0LWlkIiwidXNlcm5hbWUiOiJhZG1pbiIsInJvbGVzIjpbImFkbWluIiwibWFya2xvZ2ljLWFkbWluIl0sImNsaWVudF9pZCI6Im1hcmtsb2dpYyJ9.signature",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "4f2c7b1a-8d3e-9f5a-1c2b-3d4e5f6a7b8c"
}
```

---

## 🛠️ Customization

### Make Scripts Executable

```bash
chmod +x scripts/*.sh
```

### Add to PATH

```bash
# Add to ~/.bashrc or ~/.zshrc
export PATH="$PATH:/path/to/MLEAProxy/scripts"

# Then use from anywhere
start-oauth.sh
```

### Background Execution

Scripts can be modified to run in background:

```bash
# Add to script
nohup java -jar $JAR_FILE ... > mleaproxy.log 2>&1 &
echo $! > mleaproxy.pid
```

---

## 🧪 Testing After Startup

### Test LDAP

```bash
./scripts/start-ldap.sh

# Test
ldapsearch -H ldap://localhost:10389 \
  -D "cn=manager,ou=users,dc=marklogic,dc=local" \
  -w password \
  -b "ou=users,dc=marklogic,dc=local"
```

### Test OAuth

```bash
./scripts/start-oauth.sh

# Test
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=password&username=admin&password=admin" \
  -d "client_id=marklogic&client_secret=secret"
```

### Test SAML

```bash
./scripts/start-saml.sh

# Test
curl -X POST http://localhost:8080/saml/auth \
  -d "username=admin&password=admin"
```

### Test Kerberos

```bash
./scripts/start-kerberos.sh

# Test
kinit admin@EXAMPLE.COM
curl -X POST http://localhost:8080/kerberos/auth --negotiate -u :
```

---

## 📚 Additional Resources

- **[LDAP Examples](../examples/ldap/)** - LDAP configuration examples
- **[OAuth Examples](../examples/oauth/)** - OAuth configuration examples
- **[SAML Examples](../examples/saml/)** - SAML configuration examples
- **[Kerberos Examples](../examples/kerberos/)** - Kerberos configuration examples

---

<div align="center">

**For more information, see the [Main README](../README.md)**

</div>
