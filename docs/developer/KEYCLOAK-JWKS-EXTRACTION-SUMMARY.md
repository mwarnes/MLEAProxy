# JWKS Key Extraction - Updated for Keycloak

## Summary

Both JWKS extraction scripts have been updated to work with your Keycloak endpoint:

**Endpoint**: `https://oauth.warnesnet.com:8443/realms/progress-marklogic/protocol/openid-connect/certs`

## Changes Made

1. **SSL Certificate Handling**: Added `-k` flag to curl commands to handle self-signed certificates
2. **Updated Examples**: Changed example URLs to use your Keycloak endpoint as the primary example
3. **Updated Documentation**: Added your Keycloak endpoint to the list of test endpoints

## Current Key Information

Your Keycloak realm currently has **2 RSA keys**:

### Key #1 (Encryption)
- **Key ID**: `DCYjkxqf7xLskUc_9tlaJ8-2QC4Vx-G1nPC1qPQro1Q`
- **Algorithm**: RSA-OAEP
- **Use**: Encryption (`enc`)
- **Key Data**: `uekhsx8JZydBcsINa2GtTiA6f2qNVlJoCXktjIIuOKpD4sTa8J8DsMQTBxS7kCk1yuqj...`

### Key #2 (Signing)
- **Key ID**: `9xJihMI2qDH9Aon014odONQLByhF553_8Wu_zcM2StM`
- **Algorithm**: RS256
- **Use**: Signing (`sig`)
- **Key Data**: `8I5S-2YnzUeYsV0K0b8A_XQ4kBWuhZtojzp6ccIb6_pPXNoUaWwr6NK-NyOZTRhOXA2m...`

## Usage

### Simple Format (Key ID and Key Data on separate lines)
```bash
./scripts/extract-jwks-keys.sh https://oauth.warnesnet.com:8443/realms/progress-marklogic/protocol/openid-connect/certs
```

### Detailed Format (Full information)
```bash
./extract-jwks-keys.sh https://oauth.warnesnet.com:8443/realms/progress-marklogic/protocol/openid-connect/certs
```

## Notes

- Both scripts now include SSL certificate bypass (`-k` flag) for self-signed certificates
- The signing key (RS256) is typically used for JWT token signature validation in MarkLogic
- The encryption key (RSA-OAEP) would be used for JWT encryption if needed
- Scripts work with the current SSL configuration of your Keycloak server