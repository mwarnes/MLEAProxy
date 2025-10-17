# JWKS-MarkLogic Integration - Quick Reference

## Scripts Overview
| Script | Purpose | Usage |
|--------|---------|-------|
| `scripts/extract-jwks-keys.sh` | Add new keys | `./scripts/extract-jwks-keys.sh <JWKS_URL> [--upload-to-marklogic] [OPTIONS]` |
| `scripts/cleanup-obsolete-jwks-keys.sh` | Remove old keys | `./scripts/cleanup-obsolete-jwks-keys.sh <JWKS_URL> [--delete-keys] [OPTIONS]` |

## Quick Start Workflow

### 1. First Time Setup
```bash
# Extract and upload all current keys with custom configuration
./scripts/extract-jwks-keys.sh https://your-idp.com/jwks \
  --upload-to-marklogic \
  --marklogic-host ml.company.com \
  --external-security OAuth2-Production
```

### 2. After Key Rotation
```bash
# Add new keys (skips duplicates)
./scripts/extract-jwks-keys.sh https://your-idp.com/jwks \
  --upload-to-marklogic \
  --marklogic-host ml.company.com \
  --external-security OAuth2-Production

# Check for obsolete keys
./scripts/cleanup-obsolete-jwks-keys.sh https://your-idp.com/jwks \
  --marklogic-host ml.company.com \
  --external-security OAuth2-Production

# Remove obsolete keys (with confirmation)
./scripts/cleanup-obsolete-jwks-keys.sh https://your-idp.com/jwks \
  --delete-keys \
  --marklogic-host ml.company.com \
  --external-security OAuth2-Production
```

### 3. Regular Maintenance
```bash
# Weekly analysis (safe)
./scripts/cleanup-obsolete-jwks-keys.sh https://your-idp.com/jwks \
  --marklogic-host ml.company.com \
  --external-security OAuth2-Production
```

## Common JWKS URLs
- **Keycloak**: `https://your-keycloak.example.com/realms/{realm}/protocol/openid-connect/certs`
- **Auth0**: `https://domain.auth0.com/.well-known/jwks.json`
- **Azure AD**: `https://login.microsoftonline.com/{tenant}/discovery/v2.0/keys`
- **Google**: `https://www.googleapis.com/oauth2/v3/certs`

## Prerequisites
```bash
# Install required tools
brew install curl jq                    # macOS
sudo apt-get install curl jq openssl    # Ubuntu/Debian
```

## Configuration Options

### Command-Line Parameters (Recommended)
```bash
--marklogic-host HOST      # MarkLogic server hostname
--marklogic-port PORT      # Management API port (default: 8002)
--marklogic-user USER      # Admin username
--marklogic-pass PASS      # Admin password
--external-security NAME   # External Security profile name
```

### Environment Variables (Most Secure)
```bash
export ML_HOST="your-server.com"
export ML_USER="admin"
export ML_PASS="secure-password"
export ML_PROFILE="OAuth2-Production"

./scripts/extract-jwks-keys.sh https://idp.com/jwks \
  --upload-to-marklogic \
  --marklogic-host "$ML_HOST" \
  --marklogic-user "$ML_USER" \
  --marklogic-pass "$ML_PASS" \
  --external-security "$ML_PROFILE"
```

## Safety Features
- ✅ Duplicate detection (no duplicate keys added)
- ✅ Confirmation prompts (type "yes" to delete)
- ✅ Analysis mode first (always check before deleting)
- ✅ Detailed feedback (shows exactly what will happen)

## Output Examples

### Successful Key Addition
```
✅ Key 'NewKeyId123' is new - will be added to MarkLogic
🔄 Preparing to upload 1 new key(s) to MarkLogic...
✅ Successfully uploaded JWT secrets to MarkLogic!
```

### Duplicate Detection
```
⏭️  Key 'ExistingKeyId' already exists in MarkLogic - skipping
❌ No new RSA keys found to upload to MarkLogic.
```

### Obsolete Key Cleanup
```
🗑️  OBSOLETE KEYS: 1
   🔴 OldKeyId789
📋 To delete: ./scripts/scripts/cleanup-obsolete-jwks-keys.sh <URL> --delete-keys
```

## Troubleshooting
| Issue | Solution |
|-------|----------|
| `Failed to fetch JWKS` | Check URL and network connectivity |
| `Invalid JSON response` | Verify JWKS endpoint returns valid JSON |
| `Authentication failed` | Check MarkLogic credentials and permissions |
| `Permission denied` | Run `chmod +x *.sh` to make scripts executable |

## Security Best Practices
1. **Test first**: Always run analysis mode before deletion
2. **Grace period**: Wait for old tokens to expire before deleting keys
3. **Backup**: Consider backing up MarkLogic config before changes
4. **Monitor**: Check application logs for JWT validation errors
5. **Credentials**: Use secure credential management in production

---
*For complete documentation, see JWKS-MarkLogic-Integration-Usage-Guide.md*