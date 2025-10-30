# 🔐 OAuth2 MarkLogic Configuration Scripts

## Overview

This collection of scripts provides comprehensive tools for configuring OAuth2 authentication with MarkLogic using OAuth2 Authorization Server discovery endpoints. The scripts are designed to work with any OAuth2-compliant authorization server, with special integration for MLEAProxy development and testing.

## 📁 Script Collection

### 🛠️ Main Scripts

| Script | Purpose | Use Case |
|--------|---------|----------|
| **`configure-marklogic-oauth2.sh`** | Creates MarkLogic OAuth2 external security configuration from .well-known endpoints | Production setup, automated deployment |
| **`test-oauth2-integration.sh`** | Comprehensive integration testing with MLEAProxy | Development, CI/CD testing |
| **`validate-oauth2-config.sh`** | Validates existing OAuth2 configurations | Production monitoring, troubleshooting |
| **`oauth2-utils.sh`** | Utility functions library | Support library for other scripts |

### 🎯 Key Features

- **🔍 Auto-Discovery**: Automatically discovers OAuth2 endpoints from .well-known configuration
- **🧪 Comprehensive Testing**: End-to-end testing of OAuth2 flows and MarkLogic integration
- **📊 Validation**: Thorough validation of configurations and security settings
- **🔧 MLEAProxy Integration**: Special support for MLEAProxy OAuth server
- **📈 Performance Testing**: Load and performance testing capabilities
- **🛡️ Security Analysis**: Security best practices validation

---

## 🚀 Quick Start

### Prerequisites

Ensure you have the following tools installed:

```bash
# Required tools
curl --version    # HTTP client
```

### 1️⃣ Basic Setup with MLEAProxy

```bash
# Start MLEAProxy OAuth server
cd /path/to/MLEAProxy
mvn spring-boot:run

# Configure MarkLogic with MLEAProxy OAuth
./scripts/configure-marklogic-oauth2.sh \
    --well-known-url http://localhost:8080/oauth/.well-known/config \
    --config-name MLEAProxy-OAuth \
    --marklogic-host localhost
```

### 2️⃣ Production Setup with Keycloak

```bash
# Configure MarkLogic with Keycloak OAuth
./scripts/configure-marklogic-oauth2.sh \
    --well-known-url https://keycloak.example.com/auth/realms/marklogic/.well-known/openid_configuration \
    --config-name Production-Keycloak-OAuth \
    --marklogic-host production.marklogic.com \
    --client-id marklogic-api \
    --username-attribute preferred_username \
    --role-attribute marklogic_roles
```


### 3️⃣ Azure AD Setup

```bash
# Configure MarkLogic with Azure AD OAuth
./scripts/configure-marklogic-oauth2.sh \
    --well-known-url https://login.microsoftonline.com/YOUR-TENANT-ID/v2.0/.well-known/openid_configuration \
    --config-name AzureAD-OAuth \
    --marklogic-host marklogic.azure.com \
    --username-attribute upn \
    --role-attribute roles \
    --client-id your-application-id
```

---

## 📖 Detailed Usage

### 🔧 Configuration Script (`configure-marklogic-oauth2.sh`)

Creates MarkLogic OAuth2 external security configuration based on OAuth2 discovery endpoints.

#### Basic Syntax

```bash
./configure-marklogic-oauth2.sh --well-known-url URL --config-name NAME [OPTIONS]
```

#### Common Options

```bash
# Required Parameters
--well-known-url URL          # OAuth2 .well-known discovery endpoint
--config-name NAME           # MarkLogic external security configuration name

# MarkLogic Connection  
--marklogic-host HOST        # MarkLogic server hostname (default: localhost)
--marklogic-port PORT        # MarkLogic manage port (default: 8002)
--marklogic-user USER        # MarkLogic admin username (default: admin)
--marklogic-pass PASS        # MarkLogic admin password (default: admin)

# OAuth Configuration
--client-id ID               # OAuth client identifier (default: marklogic)
--username-attribute ATTR    # JWT claim for username (default: preferred_username)
--role-attribute ATTR        # JWT claim for roles (default: marklogic-roles)
--cache-timeout SECONDS      # Token cache timeout (default: 300)

# Behavior Control
--no-jwks                    # Skip automatic JWKS key fetching
--no-test                    # Skip configuration testing
--dry-run                    # Show what would be done without executing
--verbose                    # Enable detailed logging
```

#### Example Workflows

**Development with MLEAProxy:**
```bash
# 1. Start MLEAProxy
cd /path/to/MLEAProxy && mvn spring-boot:run &

# 2. Configure MarkLogic (dry run first)
./configure-marklogic-oauth2.sh \
    --well-known-url http://localhost:8080/oauth/.well-known/config \
    --config-name MLEAProxy-Dev \
    --dry-run --verbose

# 3. Apply configuration
./configure-marklogic-oauth2.sh \
    --well-known-url http://localhost:8080/oauth/.well-known/config \
    --config-name MLEAProxy-Dev
```

**Production with Okta:**
```bash
./configure-marklogic-oauth2.sh \
    --well-known-url https://dev-123456.okta.com/.well-known/openid_configuration \
    --config-name Okta-Production \
    --marklogic-host ml-cluster.company.com \
    --marklogic-user ml-admin \
    --marklogic-pass 'SecurePassword123!' \
    --client-id marklogic-production \
    --username-attribute preferred_username \
    --role-attribute groups \
    --cache-timeout 600
```

### 🧪 Integration Testing (`test-oauth2-integration.sh`)

Comprehensive testing script specifically designed for MLEAProxy integration.

#### Features

- **Automatic MLEAProxy startup** (if needed)
- **OAuth2 discovery endpoint testing**
- **Token generation testing** (client credentials and password flows)
- **MarkLogic configuration script testing**
- **End-to-end authentication flow validation**

#### Usage

```bash
# Run complete integration test
./test-oauth2-integration.sh

# The script will:
# 1. Check if MLEAProxy is running (start if needed)
# 2. Test OAuth2 endpoints
# 3. Test token generation
# 4. Run configuration script (with user confirmation)
# 5. Test end-to-end flow
# 6. Provide cleanup options
```

#### Interactive Flow

```
=== MLEAProxy OAuth2 Integration Test ===

This script will test the complete OAuth2 integration flow:
1. Start MLEAProxy OAuth2 server (if needed)
2. Test OAuth2 discovery endpoints  
3. Test token generation
4. Run the MarkLogic configuration script
5. Test end-to-end flow

Continue with OAuth2 integration test? (Y/n): Y

[INFO] Checking dependencies...
[SUCCESS] All dependencies found

[INFO] Checking MLEAProxy status...
[SUCCESS] MLEAProxy is running and responding

[TEST] Testing OAuth2 discovery endpoints...
[INFO] Testing endpoint: .well-known/config
[SUCCESS] OAuth2 configuration found at .well-known/config
[INFO] Issuer: mleaproxy-oauth-server
[INFO] Token Endpoint: http://localhost:8080/oauth/token
[INFO] JWKS URI: http://localhost:8080/oauth/jwks

# ... continues with comprehensive testing
```

### 📊 Configuration Validation (`validate-oauth2-config.sh`)

Validates existing OAuth2 configurations and provides comprehensive analysis.

#### Key Validation Areas

1. **🔗 Connectivity Testing**
   - OAuth2 server reachability
   - MarkLogic server connectivity
   - Network port availability

2. **🔍 Discovery Endpoint Validation**
   - .well-known configuration accessibility
   - JWKS endpoint functionality
   - Required OAuth2 metadata presence

3. **🎫 Token Generation Testing**
   - Client credentials flow
   - Password flow (if configured)
   - JWT token structure validation

4. **⚙️ MarkLogic Configuration**
   - External security configuration existence
   - Configuration parameter validation
   - API endpoint accessibility

5. **🔄 End-to-End Flow Testing**
   - Token validation against MarkLogic APIs
   - Multiple endpoint testing
   - Authentication flow verification

6. **🚀 Performance Analysis** (optional)
   - Token generation performance
   - Concurrent request handling
   - Response time analysis

7. **🛡️ Security Assessment**
   - HTTPS usage validation
   - Token expiration policy review
   - JWT signing algorithm verification

#### Usage Examples

**Basic Validation:**
```bash
./validate-oauth2-config.sh \
    --oauth-server-url http://localhost:8080 \
    --config-name MLEAProxy-OAuth
```

**Comprehensive Production Validation:**
```bash
./validate-oauth2-config.sh \
    --oauth-server-url https://auth.company.com \
    --config-name Production-OAuth \
    --marklogic-host ml-prod-cluster.company.com \
    --marklogic-manage-port 8002 \
    --marklogic-api-port 8443 \
    --performance \
    --detailed \
    --verbose
```

#### Sample Validation Report

```
=== VALIDATION REPORT ===

📊 Test Summary:
   Total Tests: 7
   Passed: 6
   Failed: 0
   Warnings: 2
   Success Rate: 86%

✅ GOOD - Configuration is working with minor issues

📋 Recommendations:
• Address security warnings for production use
• Consider using HTTPS for all communications
• Monitor token generation and validation performance
• Test with real user accounts and applications
```

---

## 🔧 Advanced Configuration

### Environment Variables

Control script behavior using environment variables:

```bash
# Debug and logging
export OAUTH2_DEBUG=true          # Enable detailed debug logging
export OAUTH2_VERBOSE=true        # Enable verbose output

# Default connection settings
export MARKLOGIC_HOST=ml-server.company.com
export MARKLOGIC_USER=admin
export MARKLOGIC_PASS=secure-password
export MLEAPROXY_URL=http://oauth-dev.company.com:8080

# Run configuration script with environment defaults
./configure-marklogic-oauth2.sh \
    --well-known-url https://auth.company.com/.well-known/openid_configuration \
    --config-name Production-OAuth
```

### Custom OAuth2 Providers

#### Auth0 Configuration
```bash
./configure-marklogic-oauth2.sh \
    --well-known-url https://YOUR-DOMAIN.auth0.com/.well-known/openid_configuration \
    --config-name Auth0-Production \
    --client-id YOUR-AUTH0-CLIENT-ID \
    --username-attribute email \
    --role-attribute https://marklogic.company.com/roles \
    --marklogic-host production.marklogic.com
```

#### Google Identity Platform
```bash
./configure-marklogic-oauth2.sh \
    --well-known-url https://accounts.google.com/.well-known/openid_configuration \
    --config-name Google-OAuth \
    --client-id YOUR-GOOGLE-CLIENT-ID.apps.googleusercontent.com \
    --username-attribute email \
    --role-attribute groups \
    --marklogic-host marklogic.company.com
```

#### AWS Cognito
```bash
./configure-marklogic-oauth2.sh \
    --well-known-url https://cognito-idp.REGION.amazonaws.com/USER-POOL-ID/.well-known/openid_configuration \
    --config-name Cognito-OAuth \
    --client-id YOUR-COGNITO-CLIENT-ID \
    --username-attribute cognito:username \
    --role-attribute cognito:groups \
    --marklogic-host marklogic.aws.company.com
```

### JWT Secret Management

For environments that don't support JWKS endpoints or require manual key management:

```bash
# Skip JWKS auto-fetch
./configure-marklogic-oauth2.sh \
    --well-known-url https://custom-auth.company.com/.well-known/config \
    --config-name Custom-OAuth \
    --no-jwks

# Then manually add JWT secrets using MarkLogic Management API:
curl -X POST --anyauth -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "oauth-server": {
      "oauth-jwt-secret": [
        {
          "oauth-jwt-key-id": "key-1",
          "oauth-jwt-secret-value": "-----BEGIN PUBLIC KEY-----\nYOUR-PEM-KEY-HERE\n-----END PUBLIC KEY-----"
        }
      ]
    }
  }' \
  "http://marklogic-host:8002/manage/v2/external-security/Custom-OAuth/jwt-secrets"
```

---

## 🛠️ Integration with CI/CD

### GitLab CI Example

```yaml
# .gitlab-ci.yml
test-oauth2-integration:
  stage: test
  image: ubuntu:latest
  services:
    - name: marklogic/marklogic-server:latest
      alias: marklogic
  before_script:
    - apt-get update && apt-get install -y curl jq maven openjdk-11-jdk
  script:
    - cd MLEAProxy && mvn spring-boot:run &
    - sleep 30  # Wait for MLEAProxy startup
    - ./scripts/test-oauth2-integration.sh
  after_script:
    - pkill java  # Stop MLEAProxy
  only:
    - main
    - develop
```

### GitHub Actions Example

```yaml
# .github/workflows/oauth2-integration.yml
name: OAuth2 Integration Test

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    
    services:
      marklogic:
        image: marklogic/marklogic-server:latest
        ports:
          - 8000:8000
          - 8002:8002
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 11
      uses: actions/setup-java@v3
      with:
        java-version: '11'
        distribution: 'temurin'
    
    - name: Install dependencies
      run: |
        sudo apt-get update
        sudo apt-get install -y curl jq bc
    
    - name: Start MLEAProxy
      run: |
        cd MLEAProxy
        mvn spring-boot:run &
        sleep 30
    
    - name: Run OAuth2 Integration Tests
      run: |
        ./scripts/test-oauth2-integration.sh
    
    - name: Validate Configuration
      run: |
        ./scripts/validate-oauth2-config.sh \
          --oauth-server-url http://localhost:8080 \
          --config-name MLEAProxy-CI-Test \
          --marklogic-host marklogic \
          --performance
```

### Jenkins Pipeline Example

```groovy
// Jenkinsfile
pipeline {
    agent any
    
    stages {
        stage('Setup') {
            steps {
                sh 'docker run -d --name marklogic -p 8000:8000 -p 8002:8002 marklogic/marklogic-server:latest'
                sh 'sleep 60'  // Wait for MarkLogic startup
            }
        }
        
        stage('Start MLEAProxy') {
            steps {
                dir('MLEAProxy') {
                    sh 'mvn spring-boot:run &'
                    sh 'sleep 30'
                }
            }
        }
        
        stage('Integration Tests') {
            steps {
                sh './scripts/test-oauth2-integration.sh'
            }
        }
        
        stage('Validation') {
            steps {
                sh '''
                ./scripts/validate-oauth2-config.sh \
                    --oauth-server-url http://localhost:8080 \
                    --config-name Jenkins-Test \
                    --marklogic-host localhost \
                    --performance \
                    --detailed
                '''
            }
        }
    }
    
    post {
        always {
            sh 'docker stop marklogic && docker rm marklogic'
            sh 'pkill -f spring-boot:run || true'
        }
    }
}
```

---

## 🚨 Troubleshooting

### Common Issues and Solutions

#### 1. **Connection Refused Errors**

```bash
# Error: curl: (7) Failed to connect to localhost port 8080: Connection refused

# Solution: Check if OAuth2 server is running
curl http://localhost:8080/oauth/.well-known/config

# For MLEAProxy:
cd MLEAProxy && mvn spring-boot:run

# Check port availability
netstat -tlnp | grep 8080
```

#### 2. **Invalid JSON Response**

```bash
# Error: Invalid JSON response from OAuth2 discovery endpoint

# Solution: Verify endpoint URL and format
curl -v http://localhost:8080/oauth/.well-known/config | jq .

# Check different endpoint paths
curl http://localhost:8080/.well-known/openid_configuration | jq .
```

#### 3. **MarkLogic Authentication Failures**

```bash
# Error: 401 Unauthorized from MarkLogic

# Solution: Verify MarkLogic credentials and network access
curl --anyauth -u admin:admin http://marklogic-host:8002/manage/v2/external-security

# Test network connectivity
telnet marklogic-host 8002
```

#### 4. **Token Validation Failures**

```bash
# Error: Token rejected by MarkLogic (401 Unauthorized)

# Solution: Check external security configuration
curl --anyauth -u admin:admin \
  http://marklogic-host:8002/manage/v2/external-security/CONFIG-NAME | jq .

# Verify token issuer matches configuration
./scripts/validate-oauth2-config.sh \
  --oauth-server-url http://oauth-server \
  --config-name CONFIG-NAME \
  --detailed
```

#### 5. **JWT Signing Key Issues**

```bash
# Error: JWT signature verification failed

# Solution: Check JWKS endpoint and key configuration
curl http://oauth-server/oauth/jwks | jq .

# Manually verify JWT secrets in MarkLogic
curl --anyauth -u admin:admin \
  http://marklogic-host:8002/manage/v2/external-security/CONFIG-NAME/jwt-secrets | jq .
```

### Debug Mode

Enable comprehensive debugging for troubleshooting:

```bash
# Enable debug mode for all scripts
export OAUTH2_DEBUG=true

# Run with maximum verbosity
./configure-marklogic-oauth2.sh \
    --well-known-url http://localhost:8080/oauth/.well-known/config \
    --config-name Debug-Test \
    --verbose \
    --dry-run

# Check utility functions
source scripts/oauth2-utils.sh
oauth2_validate_url "http://invalid-url"
oauth2_jwt_decode_payload "invalid.jwt.token"
```

### Log Analysis

Scripts generate detailed logs for troubleshooting:

```bash
# Redirect output to log file
./test-oauth2-integration.sh > oauth2-test.log 2>&1

# Monitor logs in real-time
tail -f oauth2-test.log

# Search for specific issues
grep -i "error\|fail" oauth2-test.log
grep -i "warning" oauth2-test.log
```

---

## 📋 Best Practices

### Security Best Practices

1. **Use HTTPS in Production**
   ```bash
   # Always use HTTPS URLs for production
   --oauth-server-url https://auth.company.com
   --marklogic-host https://marklogic.company.com
   ```

2. **Secure Credential Management**
   ```bash
   # Use environment variables for sensitive data
   export MARKLOGIC_PASS="$(vault kv get -field=password secret/marklogic)"
   export CLIENT_SECRET="$(vault kv get -field=secret secret/oauth-client)"
   ```

3. **Token Lifetime Management**
   ```bash
   # Configure appropriate cache timeout
   --cache-timeout 300  # 5 minutes for high security
   --cache-timeout 900  # 15 minutes for balance
   ```

4. **Regular Validation**
   ```bash
   # Schedule regular validation checks
   ./validate-oauth2-config.sh \
     --oauth-server-url https://auth.company.com \
     --config-name Production-OAuth \
     --performance >> /var/log/oauth2-validation.log
   ```

### Performance Optimization

1. **Appropriate Caching**
   ```bash
   # Balance security and performance
   --cache-timeout 600  # 10 minutes for production
   ```

2. **Connection Pooling**
   - Configure MarkLogic connection pools appropriately
   - Monitor concurrent request handling

3. **Monitoring**
   ```bash
   # Regular performance testing
   ./validate-oauth2-config.sh \
     --oauth-server-url https://auth.company.com \
     --config-name Production-OAuth \
     --performance
   ```

### Operational Best Practices

1. **Version Control**
   - Store configuration scripts in version control
   - Tag releases for configuration changes

2. **Documentation**
   - Document OAuth2 provider-specific configurations
   - Maintain runbooks for common issues

3. **Testing**
   - Test configurations in staging before production
   - Automate integration testing in CI/CD

4. **Monitoring**
   - Set up alerts for OAuth2 endpoint availability
   - Monitor token validation error rates

---

## 📚 References and Resources

### OAuth2 and JWT Standards

- **[RFC 6749](https://tools.ietf.org/html/rfc6749)** - The OAuth 2.0 Authorization Framework
- **[RFC 7517](https://tools.ietf.org/html/rfc7517)** - JSON Web Key (JWK)
- **[RFC 7519](https://tools.ietf.org/html/rfc7519)** - JSON Web Token (JWT)
- **[RFC 8414](https://tools.ietf.org/html/rfc8414)** - OAuth 2.0 Authorization Server Metadata

### MarkLogic Documentation

- **[MarkLogic Security Guide](https://docs.marklogic.com/guide/security)** - Complete security documentation
- **[External Security Configuration](https://docs.marklogic.com/REST/POST/manage/v2/external-security)** - Management API reference
- **[OAuth Configuration Examples](https://docs.marklogic.com/guide/security/external-auth#id_79072)** - OAuth setup examples

### MLEAProxy Resources

- **[MLEAProxy OAuth Guide](../user/OAUTH_GUIDE.md)** - Complete OAuth implementation guide
- **[OAuth Discovery Endpoints](../developer/OAUTH_JWKS_WELLKNOWN_COMPLETE.md)** - Technical implementation details
- **[JWKS Integration Guide](../user/JWKS-MarkLogic-Integration-Usage-Guide.md)** - JWKS setup and configuration

### OAuth2 Provider Documentation

- **[Auth0 Documentation](https://auth0.com/docs/api/authentication#oauth2)** - Auth0 OAuth2 implementation
- **[Azure AD OAuth](https://docs.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-auth-code-flow)** - Microsoft Identity Platform
- **[Google Identity](https://developers.google.com/identity/protocols/oauth2)** - Google OAuth2 implementation
- **[AWS Cognito](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-with-identity-providers.html)** - AWS Cognito OAuth2

---

## 🤝 Contributing

### Reporting Issues

When reporting issues, please include:

1. **Script version and command used**
2. **OAuth2 provider details** (without sensitive information)
3. **MarkLogic version and configuration**
4. **Complete error messages and logs**
5. **Environment information** (OS, shell, dependency versions)

### Improvement Suggestions

We welcome improvements for:

- Additional OAuth2 provider support
- Enhanced error handling and user experience
- Performance optimizations
- Security enhancements
- Documentation improvements

### Testing Changes

Before submitting changes:

```bash
# Run full test suite
./test-oauth2-integration.sh

# Validate with different configurations
./validate-oauth2-config.sh --oauth-server-url URL --config-name TEST

# Test with MLEAProxy
cd MLEAProxy && mvn test
```

---

*This documentation is part of the MLEAProxy project. For the latest updates and additional resources, visit the [MLEAProxy repository](https://github.com/marklogic/MLEAProxy).*