# JWKS Key Extraction Scripts

This directory contains two bash scripts for extracting keys from JWKS (JSON Web Key Set) endpoints.

## Scripts

### 1. `extract-jwks-keys.sh` - Detailed Extractor
A comprehensive script that provides detailed information about each key found in a JWKS endpoint.

**Usage:**
```bash
./extract-jwks-keys.sh <JWKS_ENDPOINT_URL>
```

**Features:**
- Shows detailed information for each key
- Supports RSA, EC (Elliptic Curve), and symmetric keys
- Displays key type, algorithm, usage, and all key components
- Includes security warnings for private keys or symmetric keys
- Provides formatted output with summary

**Example Output:**
```
🔐 JWKS Key Extractor
====================
Endpoint: https://www.googleapis.com/oauth2/v3/certs

📡 Fetching JWKS data...
✅ Found 2 key(s) in JWKS

🔑 Key #1:
---------------
Key ID: c8ab71530972bba20b49f78a09c9852c43ff9118
Key Type: RSA
Algorithm: RS256
Use: sig
RSA Modulus (n): vG5pJE-wQNbH7tvZU3IgjdeHugdw2x5eXPe47vOP3dI...
RSA Exponent (e): AQAB
```

### 2. `extract-jwks-simple.sh` - Simple Extractor
A minimal script that outputs just the key ID and key data on separate lines.

**Usage:**
```bash
./extract-jwks-simple.sh <JWKS_ENDPOINT_URL>
```

**Features:**
- Simple output format
- Key ID on one line, key data on the next line
- Blank line between multiple keys
- Perfect for parsing or scripting

**Example Output:**
```
c8ab71530972bba20b49f78a09c9852c43ff9118
vG5pJE-wQNbH7tvZU3IgjdeHugdw2x5eXPe47vOP3dIy4d9HnCWSTroJLtPYA1SFkcl8FlgrgWspCGBzJ8gwMo81Tk-5hX2pWXsNKrOH8R01jFqIn_UBwhmqU-YDde1R4w9upLzwNyl9Je_VY65EKrMOZG9u4UYtzTkNFLf1taBe0gIM20VSAcClUhTGpE3MX9lXxQqN3Hoybja7C_SZ8ymcnB5h-20ynZGgQybZRU43KcZkIMK2YKkLd7Tn4UQeSRPbmlbm5a0zbs5GpcYB7MONYh7MD16FTS72-tYKX-kDh3NltO6HQsV9pfoOi7qJrFaYWP3AHd_h7mWTHIkNjQ

fb9f9371d5755f3e383a40ab3a172cd8baca517f
to2hcsFNHKquhCdUzXWdP8yxnGqxFWJlRT7sntBgp47HwxB9HFc-U_AB1JT8xe1hwDpWTheckoOfpLgo7_ROEsKpVJ_OXnotL_dgNwbprr-T_EFJV7qOEdHL0KmrnN-kFNLUUSqSChPYVh1aEjlPfXg92Yieaaz2AMMtiageZrKoYnrGC0z4yPNYFj21hO1x6mvGIjmpo6_fe91o-buZNzzkmYlGsFxdvUxYAvgk-5-7D10UTTLGh8bUv_BQT3aRFiVRS5d07dyCJ4wowzxYlPSM6lnfUlvHTWyPL4JysMGeu-tbPA-5QvwCdSGpfWFQbgMq9NznBtWb99r1UStpBQ
```

## Requirements

Both scripts require:
- `curl` - for fetching JWKS data
- `jq` - for JSON parsing

Install jq on macOS:
```bash
brew install jq
```

## Example JWKS Endpoints

**Public endpoints for testing:**
- Keycloak: `https://oauth.example.com:8443/realms/progress-marklogic/protocol/openid-connect/certs`
- Google: `https://www.googleapis.com/oauth2/v3/certs`
- Auth0: `https://YOUR_DOMAIN.auth0.com/.well-known/jwks.json`
- MLEAProxy: `http://localhost:8080/oauth/jwks` (when running locally)

## Key Types Supported

- **RSA Keys**: Extracts modulus (n) and exponent (e)
- **Elliptic Curve Keys**: Extracts curve, x and y coordinates
- **Symmetric Keys**: Extracts key value (k) - with security warning

## Error Handling

Both scripts include error handling for:
- Missing dependencies (curl, jq)
- Network connectivity issues
- Invalid JSON responses
- Empty or malformed JWKS responses
- Timeout protection (10s connect, 30s total)