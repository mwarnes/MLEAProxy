# JWKS to MarkLogic Integration Scripts

A complete solution for managing JWT public keys between JWKS endpoints and MarkLogic External Security profiles.

## 📁 Files Included

### Scripts
- **`extract-jwks-simple.sh`** - Extracts and uploads new keys from JWKS endpoints
- **`cleanup-obsolete-jwks-keys.sh`** - Analyzes and removes obsolete keys from MarkLogic

### Documentation
- **`JWKS-MarkLogic-Integration-Usage-Guide.md`** - Complete usage guide (📖 **START HERE**)
- **`JWKS-Quick-Reference.md`** - Quick reference card for common tasks
- **`JWKS-MARKLOGIC-INTEGRATION-COMPLETE.md`** - Implementation summary
- **`JWKS-DUPLICATE-DETECTION-ENHANCEMENT.md`** - Duplicate detection feature details

## 🚀 Quick Start

### 1. Make Scripts Executable
```bash
chmod +x extract-jwks-simple.sh cleanup-obsolete-jwks-keys.sh
```

### 2. Configure MarkLogic Settings
Edit the configuration variables in both scripts:
```bash
MARKLOGIC_HOST="your-marklogic-server.com"
MARKLOGIC_USER="admin"
MARKLOGIC_PASS="your-admin-password"
EXTERNAL_SECURITY_NAME="Your-External-Security-Profile"
```

### 3. First Time Setup
```bash
# Add all current keys to MarkLogic
./extract-jwks-simple.sh https://your-idp.com/jwks --upload-to-marklogic
```

### 4. After Key Rotation
```bash
# Add new keys (skips duplicates)
./extract-jwks-simple.sh https://your-idp.com/jwks --upload-to-marklogic

# Remove obsolete keys
./cleanup-obsolete-jwks-keys.sh https://your-idp.com/jwks --delete-keys
```

## 📋 Prerequisites

Install required tools:
```bash
# macOS
brew install curl jq

# Ubuntu/Debian  
sudo apt-get install curl jq openssl python3
```

## 🔧 Features

### Key Management
- ✅ **Automatic PEM Conversion** - Converts base64URL keys to proper PEM format
- ✅ **Duplicate Detection** - Prevents adding existing keys
- ✅ **Smart Cleanup** - Identifies and removes obsolete keys
- ✅ **Safety Confirmations** - Requires explicit approval for deletions

### Supported Identity Providers
- Keycloak
- Auth0  
- Azure AD
- Google OAuth
- Okta
- Any JWKS-compliant provider

### Security Features
- SSL certificate handling
- Comprehensive error checking
- Detailed audit logging
- Safe cancellation options

## 📚 Documentation

For complete instructions, troubleshooting, and best practices, see:
- **[Complete Usage Guide](JWKS-MarkLogic-Integration-Usage-Guide.md)** - Comprehensive documentation
- **[Quick Reference](JWKS-Quick-Reference.md)** - Command reference and examples

## 🛠️ Example Workflows

### Daily Maintenance
```bash
# Check for new keys and add them
./extract-jwks-simple.sh https://idp.company.com/jwks --upload-to-marklogic

# Analyze for obsolete keys  
./cleanup-obsolete-jwks-keys.sh https://idp.company.com/jwks
```

### Weekly Cleanup
```bash
# Remove obsolete keys after verification
./cleanup-obsolete-jwks-keys.sh https://idp.company.com/jwks --delete-keys
```

## ⚠️ Important Notes

- **Always test in development first**
- **Run analysis mode before deletion**
- **Consider grace periods for key rotation**
- **Monitor applications for JWT validation errors**

## 🏆 Benefits

- **Zero Manual Work** - Fully automated key synchronization
- **No Duplicates** - Intelligent duplicate detection  
- **Safe Operations** - Multiple confirmation layers
- **Production Ready** - Comprehensive error handling
- **Universal Compatibility** - Works with any JWKS endpoint

---

*For questions or issues, refer to the troubleshooting section in the [Complete Usage Guide](JWKS-MarkLogic-Integration-Usage-Guide.md).*