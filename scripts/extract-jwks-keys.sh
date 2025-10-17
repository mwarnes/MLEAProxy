#!/bin/bash

# JWKS Key Extractor Script
# Usage: ./extract-jwks-keys.sh <JWKS_ENDPOINT_URL>
# 
# This script fetches a JWKS endpoint and extracts the key ID (kid) and key data
# for any keys present in the response.

set -e  # Exit on any error

# Check if URL parameter is provided
if [ $# -eq 0 ]; then
    echo "Usage: $0 <JWKS_ENDPOINT_URL>"
    echo ""
    echo "Examples:"
    echo "  $0 https://your-idp.example.com/realms/your-realm/protocol/openid-connect/certs"
    echo "  $0 http://localhost:8080/oauth/jwks"
    exit 1
fi

JWKS_URL="$1"

echo "🔐 JWKS Key Extractor"
echo "===================="
echo "Endpoint: $JWKS_URL"
echo ""

# Check if required tools are available
command -v curl >/dev/null 2>&1 || { echo "❌ Error: curl is required but not installed." >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "❌ Error: jq is required but not installed. Install with: brew install jq" >&2; exit 1; }

# Fetch JWKS data
echo "📡 Fetching JWKS data..."
JWKS_DATA=$(curl -s -k --connect-timeout 10 --max-time 30 "$JWKS_URL")

# Check if curl was successful
if [ $? -ne 0 ]; then
    echo "❌ Error: Failed to fetch JWKS data from $JWKS_URL"
    exit 1
fi

# Check if response is valid JSON
echo "$JWKS_DATA" | jq . >/dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "❌ Error: Invalid JSON response from JWKS endpoint"
    echo "Response received:"
    echo "$JWKS_DATA"
    exit 1
fi

# Check if response has keys array
KEY_COUNT=$(echo "$JWKS_DATA" | jq '.keys | length' 2>/dev/null || echo "0")

if [ "$KEY_COUNT" -eq 0 ]; then
    echo "⚠️  No keys found in JWKS response"
    echo "Raw response:"
    echo "$JWKS_DATA" | jq .
    exit 0
fi

echo "✅ Found $KEY_COUNT key(s) in JWKS"
echo ""

# Extract and display each key's information
for i in $(seq 0 $((KEY_COUNT - 1))); do
    echo "🔑 Key #$((i + 1)):"
    echo "---------------"
    
    # Extract key ID (kid)
    KID=$(echo "$JWKS_DATA" | jq -r ".keys[$i].kid // \"(no kid)\"")
    echo "Key ID: $KID"
    
    # Extract key type
    KTY=$(echo "$JWKS_DATA" | jq -r ".keys[$i].kty // \"(no kty)\"")
    echo "Key Type: $KTY"
    
    # Extract algorithm if present
    ALG=$(echo "$JWKS_DATA" | jq -r ".keys[$i].alg // \"(no alg)\"")
    echo "Algorithm: $ALG"
    
    # Extract key use if present
    USE=$(echo "$JWKS_DATA" | jq -r ".keys[$i].use // \"(no use)\"")
    echo "Use: $USE"
    
    # Extract key data based on key type
    if [ "$KTY" = "RSA" ]; then
        # RSA keys have 'n' (modulus) and 'e' (exponent)
        MODULUS=$(echo "$JWKS_DATA" | jq -r ".keys[$i].n // \"(no modulus)\"")
        EXPONENT=$(echo "$JWKS_DATA" | jq -r ".keys[$i].e // \"(no exponent)\"")
        echo "RSA Modulus (n): $MODULUS"
        echo "RSA Exponent (e): $EXPONENT"
        
        # Also check for private key components (d, p, q) - should not be present in public JWKS
        PRIVATE_EXP=$(echo "$JWKS_DATA" | jq -r ".keys[$i].d // \"(not present)\"")
        if [ "$PRIVATE_EXP" != "(not present)" ]; then
            echo "⚠️  WARNING: Private key component detected! This should not be in a public JWKS!"
        fi
        
    elif [ "$KTY" = "EC" ]; then
        # Elliptic Curve keys have 'crv' (curve), 'x' and 'y' coordinates
        CURVE=$(echo "$JWKS_DATA" | jq -r ".keys[$i].crv // \"(no curve)\"")
        X_COORD=$(echo "$JWKS_DATA" | jq -r ".keys[$i].x // \"(no x)\"")
        Y_COORD=$(echo "$JWKS_DATA" | jq -r ".keys[$i].y // \"(no y)\"")
        echo "EC Curve: $CURVE"
        echo "X Coordinate: $X_COORD"
        echo "Y Coordinate: $Y_COORD"
        
    elif [ "$KTY" = "oct" ]; then
        # Symmetric keys have 'k' (key value)
        KEY_VALUE=$(echo "$JWKS_DATA" | jq -r ".keys[$i].k // \"(no key value)\"")
        echo "Symmetric Key: $KEY_VALUE"
        echo "⚠️  WARNING: Symmetric key found in JWKS - this is unusual for public endpoints!"
        
    else
        echo "⚠️  Unknown or unsupported key type: $KTY"
        echo "Raw key data:"
        echo "$JWKS_DATA" | jq ".keys[$i]"
    fi
    
    echo ""
done

echo "🏁 Extraction complete!"
echo ""
echo "📋 Summary:"
echo "  - Total keys found: $KEY_COUNT"
echo "  - JWKS endpoint: $JWKS_URL"
echo "  - Timestamp: $(date)"