# JWKS to MarkLogic Integration - Complete Usage Guide

## ⚠️ DISCLAIMER

**IMPORTANT: This software is NOT an official Progress MarkLogic product.**

- This integration toolset is provided "AS IS" without any warranties or guarantees
- Usage is solely at your own risk
- No support will be provided by Progress MarkLogic for these scripts
- Users are responsible for testing and validating functionality in their environment
- Always test in a development environment before using in production
- Review and understand the code before executing in your infrastructure

By using these scripts, you acknowledge and accept full responsibility for any consequences resulting from their use.

---

## Overview

This guide covers two powerful scripts designed to manage JWT public keys between JWKS (JSON Web Key Set) endpoints and MarkLogic External Security profiles. These tools automate the synchronization of cryptographic keys used for JWT token validation.

### Scripts Included
1. **`extract-jwks-simple.sh`** - Extracts and uploads new keys from JWKS endpoints to MarkLogic
2. **`cleanup-obsolete-jwks-keys.sh`** - Analyzes and removes obsolete keys from MarkLogic

---

## Prerequisites

### System Requirements
- **Operating System**: macOS, Linux, or Unix-like system
- **Shell**: bash (version 4.0 or later)
- **Network Access**: Connectivity to both JWKS endpoints and MarkLogic server

### Required Tools
All tools are commonly available and can be installed via package managers:

```bash
# macOS (using Homebrew)
brew install curl jq python3

# CentOS/RHEL
sudo yum install curl jq openssl python3
```

**Tool Descriptions:**
- **curl** - HTTP client for API requests
- **jq** - JSON processor for parsing responses
- **openssl** - Cryptographic toolkit for PEM conversion
- **python3** - For ASN.1 DER encoding (RSA key structure creation)

### MarkLogic Configuration
- MarkLogic server running and accessible
- External Security profile configured for OAuth/JWT
- Admin credentials with permission to manage External Security

---

## Script 1: extract-jwks-simple.sh

### Purpose
Extracts RSA public keys from JWKS endpoints, converts them to PEM format, and uploads them to MarkLogic External Security profiles. Includes intelligent duplicate detection to prevent adding existing keys.

### Usage Syntax
```bash
./extract-jwks-simple.sh <JWKS_ENDPOINT_URL> [--upload-to-marklogic]
```

### Parameters
- **`<JWKS_ENDPOINT_URL>`** (required) - The HTTPS/HTTP URL of the JWKS endpoint
- **`--upload-to-marklogic`** (optional) - Uploads keys to MarkLogic (analysis only without this flag)

### Examples

#### Basic Key Extraction (Analysis Only)
```bash
# Extract and display keys without uploading
./extract-jwks-simple.sh https://auth.example.com/.well-known/jwks.json
```

**Output:**
```
DCYjkxqf7xLskUc_9tlaJ8-2QC4Vx-G1nPC1qPQro1Q
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuekhsx8JZydBcsINa2Gt...
-----END PUBLIC KEY-----

9xJihMI2qDH9Aon014odONQLByhF553_8Wu_zcM2StM
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA8I5S+2YnzUeYsV0K0b8A...
-----END PUBLIC KEY-----
```

#### Upload New Keys to MarkLogic
```bash
# Extract and upload new keys (skips duplicates)
./extract-jwks-simple.sh https://auth.example.com/.well-known/jwks.json --upload-to-marklogic
```

**Output with Duplicate Detection:**
```
🔍 Checking existing keys in MarkLogic External Security profile 'OAUTH2-Keycloak'...
📊 Found 2 existing key(s) in MarkLogic:
   - DCYjkxqf7xLskUc_9tlaJ8-2QC4Vx-G1nPC1qPQro1Q
   - 9xJihMI2qDH9Aon014odONQLByhF553_8Wu_zcM2StM

⏭️  Key 'DCYjkxqf7xLskUc_9tlaJ8-2QC4Vx-G1nPC1qPQro1Q' already exists in MarkLogic - skipping
✅ Key 'NewKeyId123' is new - will be added to MarkLogic

🔄 Preparing to upload 1 new key(s) to MarkLogic...
✅ Successfully uploaded JWT secrets to MarkLogic!
```

### Common JWKS Endpoints
- **Keycloak**: `https://your-keycloak.example.com/realms/{realm}/protocol/openid-connect/certs`
- **Auth0**: `https://your-domain.auth0.com/.well-known/jwks.json`
- **Azure AD**: `https://login.microsoftonline.com/{tenant}/discovery/v2.0/keys`
- **Google**: `https://www.googleapis.com/oauth2/v3/certs`
- **Okta**: `https://your-domain.okta.com/oauth2/default/v1/keys`

### Key Features
- **PEM Conversion**: Automatically converts base64URL keys to proper PEM format
- **Duplicate Detection**: Prevents adding keys that already exist in MarkLogic
- **SSL Support**: Handles self-signed certificates with `-k` flag
- **Error Handling**: Comprehensive validation and error reporting
- **Multiple Key Types**: Supports RSA, EC, and symmetric keys (uploads RSA only)

---

## Script 2: cleanup-obsolete-jwks-keys.sh

### Purpose
Compares keys in MarkLogic External Security profiles with current JWKS endpoints to identify and optionally remove obsolete keys that are no longer active.

### Usage Syntax
```bash
./cleanup-obsolete-jwks-keys.sh <JWKS_ENDPOINT_URL> [--delete-keys]
```

### Parameters
- **`<JWKS_ENDPOINT_URL>`** (required) - The HTTPS/HTTP URL of the JWKS endpoint
- **`--delete-keys`** (optional) - Actually removes obsolete keys (analysis only without this flag)

### Two-Step Workflow

#### Step 1: Analysis Mode (Safe)
```bash
# Identify obsolete keys without making changes
./cleanup-obsolete-jwks-keys.sh https://auth.example.com/.well-known/jwks.json
```

**Sample Output:**
```
🔍 JWKS Key Cleanup Analysis
=================================
JWKS Endpoint: https://auth.example.com/.well-known/jwks.json
MarkLogic External Security Profile: OAUTH2-Keycloak
Mode: ANALYSIS MODE - Will identify obsolete keys only

✅ Found 2 key(s) in current JWKS:
   - NewKeyId123
   - AnotherKeyId456

✅ Found 3 key(s) in MarkLogic:
   - OldKeyId789
   - NewKeyId123  
   - AnotherKeyId456

🔄 SYNCHRONIZED KEYS: 2
🆕 MISSING KEYS: 0
🗑️  OBSOLETE KEYS: 1

🗑️  OBSOLETE KEYS (Present in MarkLogic but not in current JWKS):
   Count: 1
   🔴 OldKeyId789

🧹 Cleanup Options:
   4. To delete obsolete keys, run:
      ./cleanup-obsolete-jwks-keys.sh https://auth.example.com/.well-known/jwks.json --delete-keys
```

#### Step 2: Deletion Mode (Destructive)
```bash
# Remove obsolete keys with confirmation
./cleanup-obsolete-jwks-keys.sh https://auth.example.com/.well-known/jwks.json --delete-keys
```

**Interactive Deletion Process:**
```
⚠️  DELETE MODE ENABLED - Obsolete keys will be removed from MarkLogic!

🗑️  DELETING OBSOLETE KEYS
==========================
About to delete 1 obsolete key(s) from MarkLogic...

⚠️  WARNING: This action cannot be undone!
Keys to be deleted:
   🔴 OldKeyId789

Are you sure you want to delete these keys? (yes/no): yes

🗑️  Deleting key: OldKeyId789
   ✅ Successfully deleted key: OldKeyId789

📊 DELETION SUMMARY
===================
✅ Successfully deleted: 1 key(s)
❌ Failed to delete: 0 key(s)
🎉 All obsolete keys have been successfully removed from MarkLogic!
```

### Safety Features
- **Confirmation Required**: Must type "yes" to proceed with deletion
- **Analysis First**: Encourages running analysis mode before deletion  
- **Detailed Warnings**: Shows exactly which keys will be affected
- **Cancellation Support**: Easy to abort the process
- **Error Reporting**: Clear feedback on successes and failures

---

## Configuration

### MarkLogic Settings
Both scripts use hardcoded MarkLogic configuration. Modify these variables in the scripts if needed:

```bash
# MarkLogic configuration (edit in both scripts)
MARKLOGIC_HOST="your-marklogic-server.com"
MARKLOGIC_PORT="8002"
MARKLOGIC_USER="admin"
MARKLOGIC_PASS="your-admin-password"
EXTERNAL_SECURITY_NAME="Your-External-Security-Profile"
```

**Configuration Parameters:**
- **MARKLOGIC_HOST**: MarkLogic server hostname or IP address
- **MARKLOGIC_PORT**: Management API port (typically 8002)
- **MARKLOGIC_USER**: Admin username with External Security permissions
- **MARKLOGIC_PASS**: Admin password
- **EXTERNAL_SECURITY_NAME**: Name of the External Security profile

### SSL Certificate Handling
Both scripts include SSL certificate bypass for development environments with self-signed certificates. The `-k` flag is used with curl commands.

**For Production**: Remove the `-k` flag and ensure proper SSL certificates are configured.

---

## Workflows and Best Practices

### Complete Key Lifecycle Management

#### 1. Initial Setup
```bash
# First time: Extract and upload all current keys
./extract-jwks-simple.sh https://your-idp.com/jwks --upload-to-marklogic
```

#### 2. Key Rotation Handling
```bash
# After IdP key rotation: Add new keys (automatic duplicate detection)
./extract-jwks-simple.sh https://your-idp.com/jwks --upload-to-marklogic

# Clean up old keys: Analyze first
./cleanup-obsolete-jwks-keys.sh https://your-idp.com/jwks

# Clean up old keys: Delete after verification
./cleanup-obsolete-jwks-keys.sh https://your-idp.com/jwks --delete-keys
```

#### 3. Scheduled Maintenance
```bash
#!/bin/bash
# Example maintenance script (maintenance.sh)
JWKS_URL="https://your-idp.com/jwks"

echo "=== Key Synchronization Maintenance ==="
echo "Adding new keys..."
./extract-jwks-simple.sh "$JWKS_URL" --upload-to-marklogic

echo "Analyzing obsolete keys..."
./cleanup-obsolete-jwks-keys.sh "$JWKS_URL"

echo "=== Maintenance Complete ==="
```

### Key Rotation Best Practices

#### Grace Period Considerations
When rotating keys, consider the following timeline:
1. **T+0**: New key published in JWKS
2. **T+0**: Add new key to MarkLogic (using extract script)
3. **T+24h**: Applications begin using new key for signing
4. **T+72h**: Analyze for obsolete keys (using cleanup script)
5. **T+168h (1 week)**: Remove obsolete keys after ensuring no old tokens remain

#### Verification Steps
Before deleting obsolete keys:
1. **Check Token Expiry**: Ensure all tokens signed with old keys have expired
2. **Application Testing**: Verify applications work with current key set
3. **Monitoring**: Check for JWT validation errors in application logs
4. **Backup**: Consider backing up MarkLogic configuration before deletion

---

## Troubleshooting

### Common Issues and Solutions

#### 1. Connection Errors
**Problem**: `Failed to fetch JWKS data from endpoint`
```bash
# Check network connectivity
curl -I https://your-idp.com/jwks

# Test with verbose output
curl -v https://your-idp.com/jwks
```

**Solutions:**
- Verify URL is correct and accessible
- Check firewall/proxy settings
- Ensure SSL certificates are valid (or use -k for testing)

#### 2. MarkLogic Authentication Errors
**Problem**: `XDMP-OAUTH: Access token provided is empty`
```bash
# Test MarkLogic connectivity
curl -u admin:password http://marklogic-host:8002/manage/v2/external-security
```

**Solutions:**
- Verify MarkLogic credentials in script configuration
- Ensure admin user has External Security permissions
- Check MarkLogic server is running and accessible

#### 3. JSON Parsing Errors
**Problem**: `Invalid JSON response from JWKS endpoint`
```bash
# Validate JWKS response manually
curl https://your-idp.com/jwks | jq .
```

**Solutions:**
- Verify JWKS endpoint returns valid JSON
- Check for HTML error pages instead of JSON
- Ensure endpoint is the correct JWKS URL

#### 4. Key Format Issues
**Problem**: Keys not converting to PEM format
```bash
# Check if required tools are available
which openssl python3
```

**Solutions:**
- Install missing dependencies (openssl, python3)
- Verify RSA key parameters (n, e) are present in JWKS
- Check for unsupported key types (only RSA keys are uploaded)

#### 5. Permission Issues
**Problem**: Script execution permission denied
```bash
# Make scripts executable
chmod +x extract-jwks-simple.sh
chmod +x cleanup-obsolete-jwks-keys.sh
```

### Debug Mode
For troubleshooting, you can modify the scripts to add debug output:

```bash
# Add to beginning of script for verbose output
set -x  # Enable debug mode
```

---

## Security Considerations

### Key Management Security
1. **Principle of Least Privilege**: Use dedicated service accounts with minimal required permissions
2. **Credential Protection**: Store MarkLogic credentials securely (consider environment variables)
3. **Audit Logging**: Monitor all key addition/deletion operations
4. **Network Security**: Use HTTPS for all communications
5. **Access Control**: Restrict script execution to authorized personnel

### Production Deployment Security
```bash
# Example: Using environment variables for credentials
export MARKLOGIC_USER="service-account"
export MARKLOGIC_PASS="secure-password"
export MARKLOGIC_HOST="your-marklogic-server.com"

# Modify script to use environment variables
MARKLOGIC_USER="${MARKLOGIC_USER:-admin}"
MARKLOGIC_PASS="${MARKLOGIC_PASS:-admin}"
```

### Key Validation
Before deleting keys, always verify:
- No active JWT tokens are signed with the key
- Applications have been updated to use new keys
- Sufficient grace period has passed for token expiration

---

## Automation and Integration

### Cron Job Example
```bash
# /etc/crontab entry for daily key synchronization
0 2 * * * /path/to/extract-jwks-simple.sh https://idp.company.com/jwks --upload-to-marklogic >> /var/log/jwks-sync.log 2>&1
```

### CI/CD Integration
```yaml
# Example GitHub Actions workflow
name: JWKS Synchronization
on:
  schedule:
    - cron: '0 2 * * *'  # Daily at 2 AM
  workflow_dispatch:

jobs:
  sync-keys:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v2
      
      - name: Install dependencies
        run: |
          sudo apt-get update
          sudo apt-get install -y curl jq openssl python3
      
      - name: Sync JWKS keys
        env:
          MARKLOGIC_USER: ${{ secrets.MARKLOGIC_USER }}
          MARKLOGIC_PASS: ${{ secrets.MARKLOGIC_PASS }}
        run: |
          ./extract-jwks-simple.sh ${{ vars.JWKS_URL }} --upload-to-marklogic
```

### Monitoring Integration
```bash
# Example monitoring script
#!/bin/bash
JWKS_URL="https://idp.company.com/jwks"
WEBHOOK_URL="https://monitoring.company.com/webhook"

# Run analysis
RESULT=$(./cleanup-obsolete-jwks-keys.sh "$JWKS_URL" 2>&1)

# Check for obsolete keys
if echo "$RESULT" | grep -q "Obsolete keys: [1-9]"; then
    # Send alert
    curl -X POST "$WEBHOOK_URL" \
         -H "Content-Type: application/json" \
         -d '{"message": "Obsolete JWKS keys detected in MarkLogic", "details": "'"$RESULT"'"}'
fi
```

---

## API Reference

### MarkLogic Management API Endpoints Used

#### Get External Security Configuration
```http
GET /manage/v2/external-security/{name}/properties
Authorization: Basic {credentials}
Accept: application/json
```

#### Add JWT Secrets
```http
POST /manage/v2/external-security/{name}/jwt-secrets
Authorization: Basic {credentials}
Content-Type: application/json

{
  "oauth-server": {
    "oauth-jwt-secret": [
      {
        "oauth-jwt-key-id": "key-id",
        "oauth-jwt-secret-value": "-----BEGIN PUBLIC KEY-----\n..."
      }
    ]
  }
}
```

#### Delete JWT Secret
```http
DELETE /manage/v2/external-security/{name}/jwt-secrets/{key-id}
Authorization: Basic {credentials}
```

### JWKS Format Reference
```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "key-identifier",
      "use": "sig",
      "n": "base64url-encoded-modulus",
      "e": "base64url-encoded-exponent"
    }
  ]
}
```

---

## Version History and Updates

### Script Versions
- **v1.0**: Basic JWKS extraction and upload
- **v1.1**: Added duplicate detection
- **v1.2**: Enhanced error handling and SSL support  
- **v2.0**: Added cleanup script with deletion capability
- **v2.1**: Improved safety features and user feedback

### Compatibility
- **MarkLogic**: Tested with versions 11.x and 12.x
- **Operating Systems**: macOS, Linux 
- **Shell**: bash 4.0+

---

## Maintenance

### Help
1. **Check Prerequisites**: Ensure all required tools are installed
2. **Review Logs**: Check script output for specific error messages
3. **Test Connectivity**: Verify network access to both JWKS and MarkLogic
4. **Validate Configuration**: Confirm MarkLogic settings and credentials

### Regular Maintenance Tasks
- **Weekly**: Review obsolete keys analysis
- **Monthly**: Verify key synchronization accuracy
- **Quarterly**: Update scripts if MarkLogic or IdP configurations change
- **Annually**: Review and update security configurations

### Script Updates
To update the scripts:
1. Test new versions in development environment
2. Backup current working versions
3. Update configuration variables as needed
4. Run analysis mode first before enabling destructive operations

---

## Conclusion

These JWKS integration scripts provide a complete solution for managing JWT public keys between Identity Providers and MarkLogic External Security profiles. The combination of automated duplicate detection, comprehensive analysis, and safe deletion processes ensures reliable and secure key lifecycle management.

**Key Benefits:**
- **Automation**: Reduces manual key management overhead
- **Safety**: Multiple confirmation layers prevent accidental deletions
- **Reliability**: Comprehensive error handling and validation
- **Flexibility**: Works with any JWKS-compliant Identity Provider
- **Security**: Maintains clean, synchronized key sets

For production deployments, always test in development environments first and follow security best practices for credential management and access control.

---