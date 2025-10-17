# JWKS-MarkLogic Integration - Quick Reference

## Scripts Overview
| Script | Purpose | Usage |
|--------|---------|-------|
| `extract-jwks-simple.sh` | Add new keys | `./extract-jwks-simple.sh <JWKS_URL> [--upload-to-marklogic]` |
| `cleanup-obsolete-jwks-keys.sh` | Remove old keys | `./cleanup-obsolete-jwks-keys.sh <JWKS_URL> [--delete-keys]` |

## Quick Start Workflow

### 1. First Time Setup
```bash
# Extract and upload all current keys
./extract-jwks-simple.sh https://your-idp.com/jwks --upload-to-marklogic
```

### 2. After Key Rotation
```bash
# Add new keys (skips duplicates)
./extract-jwks-simple.sh https://your-idp.com/jwks --upload-to-marklogic

# Check for obsolete keys
./cleanup-obsolete-jwks-keys.sh https://your-idp.com/jwks

# Remove obsolete keys (with confirmation)
./cleanup-obsolete-jwks-keys.sh https://your-idp.com/jwks --delete-keys
```

### 3. Regular Maintenance
```bash
# Weekly analysis (safe)
./cleanup-obsolete-jwks-keys.sh https://your-idp.com/jwks
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

## Configuration
Edit these variables in both scripts:
```bash
MARKLOGIC_HOST="your-marklogic-server.com"
MARKLOGIC_PORT="8002"
MARKLOGIC_USER="admin"
MARKLOGIC_PASS="your-admin-password"
EXTERNAL_SECURITY_NAME="Your-External-Security-Profile"
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
📋 To delete: ./cleanup-obsolete-jwks-keys.sh <URL> --delete-keys
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