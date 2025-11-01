# 🔐 OAuth2 MarkLogic Configuration Scripts

## Overview

This collection of scripts provides comprehensive tools for configuring OAuth2 authentication with MarkLogic using OAuth2 Authorization Server discovery endpoints. The scripts are designed to work with any OAuth2-compliant authorization server, with special integration for MLEAProxy development and testing.

## 📁 Script Collection

### 🛠️ Main Scripts

| Script | Purpose | Use Case |
|--------|---------|----------|
| **`configure-marklogic-oauth2.sh`** | Creates MarkLogic OAuth2 external security configuration from .well-known endpoints | Production setup, automated deployment |
| **`configure-appserver-security.sh`** | Configures MarkLogic app servers to use external security configurations | Apply OAuth2/SAML/LDAP to app servers |
| **`validate-oauth2-config.sh`** | Validates existing OAuth2 configurations and tests authentication flows | Production monitoring, troubleshooting |
| **`extract-jwks-keys.sh`** | Extracts JWT signing keys from JWKS endpoints and uploads to MarkLogic | JWT key management, security updates |
| **`cleanup-obsolete-jwks-keys.sh`** | Identifies and removes obsolete JWT keys from MarkLogic configurations | Security maintenance, key rotation |
| **`oauth2-examples.sh`** | Interactive examples and demonstrations of OAuth2 configuration workflows | Learning, documentation, training |
| **`oauth2-utils.sh`** | Core utility functions library for OAuth2 operations | Support library for other scripts |

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

### 🎯 App Server Security Configuration (`configure-appserver-security.sh`)

Configures MarkLogic app servers to use external security configurations (OAuth, SAML, LDAP).

#### Key Features

- **Multi-Server Support**: Configure multiple app servers in a single command
- **External Security Integration**: Works with any MarkLogic external security configuration
- **Configuration Validation**: Verifies external security profiles exist before applying
- **Status Display**: Shows current app server security settings
- **Dry-Run Support**: Preview changes before applying

#### Basic Usage

```bash
# Configure single app server
./configure-appserver-security.sh \
    --appserver App-Services \
    --external-security MLEAProxy-OAuth

# Configure multiple app servers
./configure-appserver-security.sh \
    --appserver "App-Services,Documents,Manage" \
    --external-security Production-OAuth \
    --marklogic-host ml-prod.company.com
```

#### Advanced Examples

**Production Deployment with Verification:**
```bash
# Show current configuration first
./configure-appserver-security.sh \
    --appserver App-Services \
    --show-config \
    --marklogic-host production.marklogic.com

# Apply configuration with dry-run
./configure-appserver-security.sh \
    --appserver "App-Services,Documents" \
    --external-security Company-OAuth2 \
    --marklogic-host production.marklogic.com \
    --marklogic-user ml-admin \
    --marklogic-pass "$ML_ADMIN_PASS" \
    --dry-run

# Apply actual configuration
./configure-appserver-security.sh \
    --appserver "App-Services,Documents" \
    --external-security Company-OAuth2 \
    --marklogic-host production.marklogic.com \
    --marklogic-user ml-admin \
    --marklogic-pass "$ML_ADMIN_PASS" \
    --verbose
```

**HTTPS with Custom Ports:**
```bash
./configure-appserver-security.sh \
    --appserver "Custom-App-8080,API-Server-9000" \
    --external-security Azure-SAML-Config \
    --marklogic-host https://secure-ml.company.com:8443 \
    --insecure \
    --verbose
```

### 🔑 JWKS Key Management (`extract-jwks-keys.sh`)

Extracts JWT signing keys from JWKS endpoints and manages them in MarkLogic external security configurations.

#### Key Features

- **Automatic Key Discovery**: Fetches keys from JWKS endpoints
- **PEM Conversion**: Converts JWK format to PEM for MarkLogic compatibility
- **Batch Upload**: Uploads multiple keys to MarkLogic in one operation
- **Key Validation**: Verifies key format and compatibility
- **Multiple Key Types**: Supports RSA, EC, and other key types

#### Basic Usage

```bash
# Extract and display keys
./extract-jwks-keys.sh https://auth.company.com/.well-known/jwks.json

# Extract and upload to MarkLogic
./extract-jwks-keys.sh \
    https://keycloak.company.com/auth/realms/production/protocol/openid_connect/certs \
    --upload-to-marklogic \
    --external-security-name Production-OAuth \
    --marklogic-host ml-prod.company.com
```

#### Advanced Examples

**Multi-Environment Key Management:**
```bash
# Development environment
./extract-jwks-keys.sh \
    http://localhost:8080/oauth/jwks \
    --upload-to-marklogic \
    --external-security-name MLEAProxy-Dev \
    --marklogic-host localhost \
    --marklogic-user admin \
    --marklogic-pass admin

# Staging environment
./extract-jwks-keys.sh \
    https://auth-staging.company.com/.well-known/jwks.json \
    --upload-to-marklogic \
    --external-security-name Company-OAuth-Staging \
    --marklogic-host ml-staging.company.com \
    --marklogic-user "$STAGING_USER" \
    --marklogic-pass "$STAGING_PASS" \
    --verbose

# Production environment
./extract-jwks-keys.sh \
    https://auth.company.com/.well-known/jwks.json \
    --upload-to-marklogic \
    --external-security-name Company-OAuth-Production \
    --marklogic-host https://ml-prod.company.com:8443 \
    --marklogic-user "$PROD_USER" \
    --marklogic-pass "$PROD_PASS" \
    --insecure \
    --verbose
```

**Automated Key Rotation Pipeline:**
```bash
#!/bin/bash
# key-rotation.sh - Automated key rotation script

ENVIRONMENTS=("dev" "staging" "production")
JWKS_ENDPOINTS=(
    "http://localhost:8080/oauth/jwks"
    "https://auth-staging.company.com/.well-known/jwks.json"
    "https://auth.company.com/.well-known/jwks.json"
)
ML_HOSTS=(
    "localhost"
    "ml-staging.company.com"
    "https://ml-prod.company.com:8443"
)

for i in "${!ENVIRONMENTS[@]}"; do
    env="${ENVIRONMENTS[$i]}"
    jwks="${JWKS_ENDPOINTS[$i]}"
    host="${ML_HOSTS[$i]}"
    
    echo "Updating keys for $env environment..."
    
    ./extract-jwks-keys.sh "$jwks" \
        --upload-to-marklogic \
        --external-security-name "Company-OAuth-${env^}" \
        --marklogic-host "$host" \
        --marklogic-user "${env^^}_USER" \
        --marklogic-pass "${env^^}_PASS" \
        --verbose
done
```

### 🧹 JWKS Key Cleanup (`cleanup-obsolete-jwks-keys.sh`)

Identifies and removes obsolete JWT keys from MarkLogic external security configurations.

#### Key Features

- **Key Comparison**: Compares MarkLogic keys with current JWKS endpoint
- **Obsolete Detection**: Identifies keys no longer present in JWKS
- **Safe Removal**: Interactive confirmation before deletion
- **Backup Support**: Optional backup of removed keys
- **Audit Trail**: Detailed logging of cleanup operations

#### Basic Usage

```bash
# Analyze obsolete keys (read-only)
./cleanup-obsolete-jwks-keys.sh \
    https://auth.company.com/.well-known/jwks.json \
    --external-security-name Production-OAuth \
    --marklogic-host ml-prod.company.com

# Remove obsolete keys (with confirmation)
./cleanup-obsolete-jwks-keys.sh \
    https://auth.company.com/.well-known/jwks.json \
    --delete-keys \
    --external-security-name Production-OAuth \
    --marklogic-host ml-prod.company.com
```

#### Advanced Examples

**Automated Cleanup with Safeguards:**
```bash
# Production cleanup with backup
./cleanup-obsolete-jwks-keys.sh \
    https://auth.company.com/.well-known/jwks.json \
    --delete-keys \
    --external-security-name Company-OAuth-Production \
    --marklogic-host https://ml-prod.company.com:8443 \
    --marklogic-user "$PROD_USER" \
    --marklogic-pass "$PROD_PASS" \
    --backup-directory "/backup/jwks-keys/$(date +%Y%m%d)" \
    --force \
    --verbose

# Batch cleanup across environments
for env in dev staging production; do
    echo "Cleaning up $env environment..."
    
    ./cleanup-obsolete-jwks-keys.sh \
        "https://auth-${env}.company.com/.well-known/jwks.json" \
        --delete-keys \
        --external-security-name "Company-OAuth-${env^}" \
        --marklogic-host "ml-${env}.company.com" \
        --marklogic-user "${env^^}_USER" \
        --marklogic-pass "${env^^}_PASS" \
        --backup-directory "/backup/jwks-cleanup-$(date +%Y%m%d)" \
        --verbose
done
```

**Scheduled Maintenance Script:**
```bash
#!/bin/bash
# scheduled-jwks-cleanup.sh - Run via cron

LOG_FILE="/var/log/jwks-cleanup-$(date +%Y%m%d).log"
BACKUP_DIR="/backup/jwks/$(date +%Y%m%d-%H%M%S)"

{
    echo "=== JWKS Cleanup Starting at $(date) ==="
    
    # Production cleanup
    ./cleanup-obsolete-jwks-keys.sh \
        https://auth.company.com/.well-known/jwks.json \
        --delete-keys \
        --external-security-name Production-OAuth \
        --marklogic-host ml-prod.company.com \
        --marklogic-user "$PROD_ML_USER" \
        --marklogic-pass "$PROD_ML_PASS" \
        --backup-directory "$BACKUP_DIR" \
        --force \
        --verbose
    
    echo "=== JWKS Cleanup Completed at $(date) ==="
} >> "$LOG_FILE" 2>&1
```

### 📚 Interactive Examples (`oauth2-examples.sh`)

Provides interactive demonstrations and examples of OAuth2 configuration workflows.

#### Key Features

- **Interactive Learning**: Step-by-step guided examples
- **Multiple Scenarios**: Development, staging, and production examples
- **Copy-Paste Ready**: All examples are executable commands
- **Best Practices**: Demonstrates recommended configuration patterns
- **Troubleshooting**: Common issues and solutions

#### Usage

```bash
# Run interactive examples
./oauth2-examples.sh

# Display specific example
./oauth2-examples.sh --example 1

# Show all examples without interaction
./oauth2-examples.sh --all --non-interactive
```

#### Example Scenarios Included

1. **MLEAProxy Development Setup**
2. **Keycloak Integration**
3. **Azure AD Configuration**
4. **Configuration Validation**
5. **CI/CD Pipeline Integration**

### 🔧 Utility Functions (`oauth2-utils.sh`)

Core utility library providing OAuth2 functions for other scripts.

#### Key Functions

- **Token Operations**: `oauth2_get_token_*`, `oauth2_jwt_decode_*`
- **MarkLogic Integration**: `oauth2_test_token_against_marklogic`
- **Configuration Validation**: `oauth2_validate_*`
- **Logging**: `log_info`, `log_error`, `log_success`
- **HTTP Operations**: Specialized curl functions with proper error handling

#### Usage in Scripts

```bash
#!/bin/bash
# Example script using oauth2-utils.sh

# Source the utilities
source "$(dirname "$0")/oauth2-utils.sh"

# Get a token using client credentials
token=$(oauth2_get_token_client_credentials \
    "https://auth.company.com/oauth/token" \
    "my-client-id" \
    "my-client-secret" \
    "openid profile")

# Test token against MarkLogic
if oauth2_test_token_against_marklogic "$token" "http://localhost:8000" "Documents"; then
    log_success "Token authentication successful"
else
    log_error "Token authentication failed"
fi

# Decode JWT payload
username=$(oauth2_jwt_get_claim "$token" "preferred_username")
log_info "Authenticated user: $username"
```

---

## � Complete Workflow Examples

### Scenario 1: New Environment Setup

Complete OAuth2 setup for a new MarkLogic environment:

```bash
#!/bin/bash
# complete-oauth2-setup.sh

OAUTH_SERVER="https://auth.company.com"
WELL_KNOWN_URL="$OAUTH_SERVER/.well-known/openid_configuration"
MARKLOGIC_HOST="https://ml-prod.company.com:8443"
CONFIG_NAME="Company-OAuth2-Production"

echo "🔧 Setting up OAuth2 for new environment..."

# Step 1: Create OAuth2 external security configuration
echo "Step 1: Creating external security configuration..."
./configure-marklogic-oauth2.sh \
    --well-known-url "$WELL_KNOWN_URL" \
    --config-name "$CONFIG_NAME" \
    --marklogic-host "$MARKLOGIC_HOST" \
    --client-id marklogic-production \
    --username-attribute preferred_username \
    --role-attribute groups \
    --fetch-jwks-keys \
    --verbose

# Step 2: Extract and upload JWKS keys
echo "Step 2: Managing JWT signing keys..."
./extract-jwks-keys.sh \
    "$OAUTH_SERVER/.well-known/jwks.json" \
    --upload-to-marklogic \
    --external-security-name "$CONFIG_NAME" \
    --marklogic-host "$MARKLOGIC_HOST" \
    --verbose

# Step 3: Configure app servers
echo "Step 3: Configuring app servers..."
./configure-appserver-security.sh \
    --appserver "App-Services,Documents,Manage" \
    --external-security "$CONFIG_NAME" \
    --marklogic-host "$MARKLOGIC_HOST" \
    --verbose

# Step 4: Validate complete setup
echo "Step 4: Validating configuration..."
./validate-oauth2-config.sh \
    --oauth-server-url "$OAUTH_SERVER" \
    --config-name "$CONFIG_NAME" \
    --marklogic-host "$MARKLOGIC_HOST" \
    --performance \
    --detailed

echo "✅ OAuth2 setup complete!"
```

### Scenario 2: Key Rotation Maintenance

Monthly key rotation and cleanup:

```bash
#!/bin/bash
# monthly-key-rotation.sh

ENVIRONMENTS=("staging" "production")
BASE_OAUTH_URL="https://auth.company.com"
BASE_ML_HOST="ml-prod.company.com"

for env in "${ENVIRONMENTS[@]}"; do
    echo "🔄 Processing $env environment key rotation..."
    
    OAUTH_URL="$BASE_OAUTH_URL"
    ML_HOST="$BASE_ML_HOST"
    CONFIG_NAME="Company-OAuth2-${env^}"
    
    if [ "$env" = "staging" ]; then
        OAUTH_URL="https://auth-staging.company.com"
        ML_HOST="ml-staging.company.com"
    fi
    
    # Step 1: Backup current keys
    echo "  📋 Creating backup..."
    mkdir -p "/backup/jwks-rotation-$(date +%Y%m%d)/$env"
    
    # Step 2: Extract new keys
    echo "  🔑 Extracting new keys..."
    ./extract-jwks-keys.sh \
        "$OAUTH_URL/.well-known/jwks.json" \
        --upload-to-marklogic \
        --external-security-name "$CONFIG_NAME" \
        --marklogic-host "$ML_HOST" \
        --verbose
    
    # Step 3: Clean up obsolete keys
    echo "  🧹 Cleaning up obsolete keys..."
    ./cleanup-obsolete-jwks-keys.sh \
        "$OAUTH_URL/.well-known/jwks.json" \
        --delete-keys \
        --external-security-name "$CONFIG_NAME" \
        --marklogic-host "$ML_HOST" \
        --backup-directory "/backup/jwks-rotation-$(date +%Y%m%d)/$env" \
        --force \
        --verbose
    
    # Step 4: Validate after rotation
    echo "  ✅ Validating configuration..."
    ./validate-oauth2-config.sh \
        --oauth-server-url "$OAUTH_URL" \
        --config-name "$CONFIG_NAME" \
        --marklogic-host "$ML_HOST" \
        --verbose
    
    echo "✅ Key rotation complete for $env"
done
```

### Scenario 3: Multi-Tenant Setup

Configure OAuth2 for multiple tenants:

```bash
#!/bin/bash
# multi-tenant-oauth2-setup.sh

TENANTS=("acme-corp" "widget-inc" "global-tech")
MARKLOGIC_HOST="https://shared-ml.company.com:8443"

for tenant in "${TENANTS[@]}"; do
    echo "🏢 Configuring OAuth2 for tenant: $tenant"
    
    TENANT_OAUTH_URL="https://${tenant}-auth.company.com"
    CONFIG_NAME="${tenant}-OAuth2"
    APP_SERVER="${tenant}-app-server"
    CLIENT_ID="${tenant}-marklogic"
    
    # Create tenant-specific external security
    ./configure-marklogic-oauth2.sh \
        --well-known-url "$TENANT_OAUTH_URL/.well-known/openid_configuration" \
        --config-name "$CONFIG_NAME" \
        --marklogic-host "$MARKLOGIC_HOST" \
        --client-id "$CLIENT_ID" \
        --username-attribute preferred_username \
        --role-attribute "${tenant}_roles" \
        --cache-timeout 300 \
        --fetch-jwks-keys \
        --verbose
    
    # Configure tenant app server
    ./configure-appserver-security.sh \
        --appserver "$APP_SERVER" \
        --external-security "$CONFIG_NAME" \
        --marklogic-host "$MARKLOGIC_HOST" \
        --verbose
    
    # Validate tenant setup
    ./validate-oauth2-config.sh \
        --oauth-server-url "$TENANT_OAUTH_URL" \
        --config-name "$CONFIG_NAME" \
        --marklogic-host "$MARKLOGIC_HOST" \
        --verbose
    
    echo "✅ Tenant $tenant configured successfully"
done
```

### Scenario 4: Development to Production Migration

Migrate OAuth2 configuration from dev to production:

```bash
#!/bin/bash
# dev-to-prod-migration.sh

# Source environment
DEV_OAUTH_URL="http://localhost:8080"
DEV_ML_HOST="localhost"
DEV_CONFIG="MLEAProxy-Dev"

# Target environment  
PROD_OAUTH_URL="https://auth.company.com"
PROD_ML_HOST="https://ml-prod.company.com:8443"
PROD_CONFIG="Company-OAuth2-Production"

echo "🚀 Migrating OAuth2 configuration from dev to production..."

# Step 1: Validate development setup
echo "Step 1: Validating development configuration..."
./validate-oauth2-config.sh \
    --oauth-server-url "$DEV_OAUTH_URL" \
    --config-name "$DEV_CONFIG" \
    --marklogic-host "$DEV_ML_HOST" \
    --verbose

if [ $? -ne 0 ]; then
    echo "❌ Development validation failed. Fix issues before migration."
    exit 1
fi

# Step 2: Create production configuration
echo "Step 2: Creating production external security configuration..."
./configure-marklogic-oauth2.sh \
    --well-known-url "$PROD_OAUTH_URL/.well-known/openid_configuration" \
    --config-name "$PROD_CONFIG" \
    --marklogic-host "$PROD_ML_HOST" \
    --client-id marklogic-production \
    --username-attribute preferred_username \
    --role-attribute groups \
    --cache-timeout 600 \
    --fetch-jwks-keys \
    --verbose

# Step 3: Configure production app servers
echo "Step 3: Configuring production app servers..."
./configure-appserver-security.sh \
    --appserver "App-Services,Documents" \
    --external-security "$PROD_CONFIG" \
    --marklogic-host "$PROD_ML_HOST" \
    --verbose

# Step 4: Validate production setup
echo "Step 4: Validating production configuration..."
./validate-oauth2-config.sh \
    --oauth-server-url "$PROD_OAUTH_URL" \
    --config-name "$PROD_CONFIG" \
    --marklogic-host "$PROD_ML_HOST" \
    --performance \
    --detailed \
    --verbose

echo "✅ Migration to production complete!"
```

---

## �🔧 Advanced Configuration

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
# Redirect validation output to log file
./scripts/validate-oauth2-config.sh --verbose > oauth2-validation.log 2>&1

# Monitor validation logs in real-time
tail -f oauth2-validation.log

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
# Validate configuration
./scripts/validate-oauth2-config.sh --verbose

# Validate with different configurations
./validate-oauth2-config.sh --oauth-server-url URL --config-name TEST

# Test with MLEAProxy
cd MLEAProxy && mvn test
```

---

*This documentation is part of the MLEAProxy project. For the latest updates and additional resources, visit the [MLEAProxy repository](https://github.com/marklogic/MLEAProxy).*