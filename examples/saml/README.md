# SAML 2.0 Configuration Examples

This directory contains example SAML 2.0 configuration files for various deployment scenarios.

---

## 📋 Available Examples

| File | Scenario | Description |
|------|----------|-------------|
| **01-saml-basic.properties** | Basic SAML IdP | Simple SAML 2.0 Identity Provider with default settings |
| **02-saml-custom-certs.properties** | Custom Certificates | SAML with custom signing certificates |
| **03-saml-custom-validity.properties** | Custom Validity | Configure assertion expiration times |
| **04-saml-with-roles.properties** | Role-Based Access | SAML with role attributes and mappings |

---

## 🚀 Quick Start

### Using an Example

1. **Choose an example** from the list above
2. **Copy to root directory**:
   ```bash
   cp examples/saml/01-saml-basic.properties saml.properties
   ```
3. **Edit configuration** (update paths, validity as needed)
4. **Run MLEAProxy**:
   ```bash
   java -jar target/mlesproxy-2.0.0.jar
   ```

### Testing Your Configuration

```bash
# Test SAML authentication
curl -X POST http://localhost:8080/saml/auth \
  -d "username=admin&password=admin"

# Get SAML IdP metadata
curl http://localhost:8080/saml/metadata

# Wrap SAML assertion
curl -X POST http://localhost:8080/saml/wrapassertion \
  -d "assertion=<saml2:Assertion>...</saml2:Assertion>"

# Get CA certificates
curl http://localhost:8080/saml/cacerts
```

---

## 📖 Example Details

### 1. Basic SAML Configuration

**File**: `01-saml-basic.properties`

**Use Case**:
- Development and testing
- Quick SAML IdP setup
- Default certificates and settings

**Key Features**:
- Uses bundled certificates
- 5-minute assertion validity
- Default role assignment

**Endpoints**:
- Authentication: `http://localhost:8080/saml/auth`
- Metadata: `http://localhost:8080/saml/metadata`
- Wrap Assertion: `http://localhost:8080/saml/wrapassertion`
- CA Certificates: `http://localhost:8080/saml/cacerts`

---

### 2. Custom SAML Certificates

**File**: `02-saml-custom-certs.properties`

**Use Case**:
- Production environments
- Custom certificate management
- Certificate rotation

**Key Features**:
- Custom RSA private key
- Custom X.509 certificate
- PEM format support

**Configuration Required**:
```properties
saml.keypath=/path/to/rsakey.pem
saml.capath=/path/to/certificate.pem
```

**Generate Custom Certificates**:
```bash
# Generate private key
openssl genrsa -out rsakey.pem 2048

# Generate self-signed certificate
openssl req -new -x509 -key rsakey.pem -out certificate.pem -days 365

# Or use existing CA-signed certificate
```

---

### 3. Custom Assertion Validity

**File**: `03-saml-custom-validity.properties`

**Use Case**:
- Control assertion lifetime
- Security policies
- Session management

**Key Features**:
- Configurable assertion validity
- Short-lived for security
- Long-lived for convenience

**Validity Options**:
```properties
# Short-lived (5 minutes)
saml.response.validity=300

# Medium-lived (15 minutes)
saml.response.validity=900

# Long-lived (1 hour)
saml.response.validity=3600
```

---

### 4. SAML with Role-Based Access

**File**: `04-saml-with-roles.properties`

**Use Case**:
- Authorization integration
- Role-based access control
- Group membership

**Key Features**:
- Role attributes in assertions
- Default role configuration
- User repository integration

**Role Resolution**:
1. Request parameter roles (highest priority)
2. JSON user repository roles
3. Default configuration roles (fallback)

---

## 🔧 Configuration Reference

### Common Properties

```properties
# Enable SAML debug logging
saml.debug=true

# SAML assertion validity (seconds)
saml.response.validity=300

# Certificate paths
saml.capath=/path/to/certificate.pem
saml.keypath=/path/to/rsakey.pem

# Default roles
saml.default.roles=user
```

### Assertion Structure

Generated SAML assertions include:
- **Subject** - User identifier
- **Conditions** - NotBefore/NotOnOrAfter timestamps
- **AttributeStatement** - User attributes and roles
- **Signature** - Digital signature (if certificates configured)

---

## 🔒 Security Considerations

### Assertion Validity

**Production (Recommended)**:
```properties
# 5-10 minutes
saml.response.validity=300
```

**Development/Testing**:
```properties
# 15-30 minutes
saml.response.validity=1800
```

### Certificate Management

**Development**:
- Use bundled self-signed certificates
- Simple for testing

**Production**:
- Generate dedicated certificates
- Minimum 2048-bit RSA keys
- CA-signed certificates preferred
- Implement certificate rotation
- Store keys securely

---

## 🧪 Testing Examples

### Test SAML Authentication

```bash
curl -X POST http://localhost:8080/saml/auth \
  -d "username=admin&password=admin&roles=admin,marklogic-admin"
```

### Get SAML IdP Metadata

```bash
curl http://localhost:8080/saml/metadata
```

### Wrap Existing SAML Assertion

```bash
curl -X POST http://localhost:8080/saml/wrapassertion \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "assertion=<saml2:Assertion>...</saml2:Assertion>"
```

### Get CA Certificates (PEM)

```bash
curl http://localhost:8080/saml/cacerts
```

### Validate SAML Assertion

```bash
# Decode base64-encoded assertion
echo "BASE64_ASSERTION" | base64 -d | xmllint --format -
```

---

## 📚 Additional Resources

- **[SAML_GUIDE.md](../../docs/user/SAML_GUIDE.md)** - Complete SAML documentation
- **[README.md](../../README.md)** - General application overview
- **[SAML 2.0 Specification](http://docs.oasis-open.org/security/saml/v2.0/)** - Official SAML spec

---

## 🆘 Troubleshooting

### Assertion Generation Failed

```bash
# Enable debug logging
saml.debug=true
logging.level.com.marklogic.handlers.undertow.SAMLAuthHandler=DEBUG
```

### Certificate Errors

```bash
# Verify certificate format
openssl x509 -in certificate.pem -text -noout

# Verify private key format
openssl rsa -in rsakey.pem -check

# Check key-certificate pair match
openssl x509 -noout -modulus -in certificate.pem | openssl md5
openssl rsa -noout -modulus -in rsakey.pem | openssl md5
```

### Invalid Assertion

```bash
# Check assertion validity period
saml.response.validity=300

# Verify system time synchronization
date
```

### Role Assignment Issues

```bash
# Check role resolution order
# 1. Request parameters (highest)
# 2. users.json
# 3. saml.default.roles (fallback)

# Verify users.json format
cat users.json | jq .
```

---

<div align="center">

**For more information, see the [SAML Guide](../../docs/user/SAML_GUIDE.md)**

</div>
