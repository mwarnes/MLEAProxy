#!/bin/bash

# JWKS Key Extractor with MarkLogic Integration
# Usage: ./extract-jwks-simple.sh <JWKS_ENDPOINT_URL> [--upload-to-marklogic]
# 
# This script fetches a JWKS endpoint, extracts the key ID (kid), converts
# RSA key data to PEM format, and optionally uploads to MarkLogic External Security.
# 
# Requirements: curl, jq, openssl, python3
#
# ⚠️  DISCLAIMER: This software is NOT an official Progress MarkLogic product.
# This integration toolset is provided "AS IS" without any warranties or guarantees.
# Usage is solely at your own risk. No support will be provided by Progress MarkLogic
# for these scripts. Users are responsible for testing and validating functionality
# in their environment. Always test in a development environment before using in production.
# By using this script, you acknowledge and accept full responsibility for any consequences.

set -e  # Exit on any error

# Check if URL parameter is provided
if [ $# -eq 0 ]; then
    echo "Usage: $0 <JWKS_ENDPOINT_URL> [--upload-to-marklogic]"
    echo ""
    echo "Examples:"
    echo "  $0 https://your-idp.example.com/realms/your-realm/protocol/openid-connect/certs"
    echo "  $0 https://your-idp.example.com/realms/your-realm/protocol/openid-connect/certs --upload-to-marklogic"
    echo "  $0 http://localhost:8080/oauth/jwks"
    exit 1
fi

JWKS_URL="$1"
UPLOAD_TO_MARKLOGIC=false

# Check for upload flag
if [ "$2" = "--upload-to-marklogic" ]; then
    UPLOAD_TO_MARKLOGIC=true
fi

# Array to store key data for MarkLogic upload
KEY_DATA=()

# MarkLogic configuration (customize these settings for your environment)
MARKLOGIC_HOST="your-marklogic-server.com"
MARKLOGIC_PORT="8002"
MARKLOGIC_USER="admin"
MARKLOGIC_PASS="your-admin-password"
EXTERNAL_SECURITY_NAME="Your-External-Security-Profile"

# Check if required tools are available
command -v curl >/dev/null 2>&1 || { echo "Error: curl is required but not installed." >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "Error: jq is required but not installed. Install with: brew install jq" >&2; exit 1; }
command -v openssl >/dev/null 2>&1 || { echo "Warning: openssl not found. RSA keys will be output in base64url format instead of PEM." >&2; }
command -v python3 >/dev/null 2>&1 || { echo "Warning: python3 not found. RSA keys will be output in base64url format instead of PEM." >&2; }

# Function to get existing key IDs from MarkLogic
get_existing_key_ids() {
    echo "🔍 Checking existing keys in MarkLogic External Security profile '$EXTERNAL_SECURITY_NAME'..."
    
    # Build the URL for debugging
    MARKLOGIC_URL="http://$MARKLOGIC_HOST:$MARKLOGIC_PORT/manage/v2/external-security/$EXTERNAL_SECURITY_NAME/properties"
    echo "🔗 API URL: $MARKLOGIC_URL"
    
    # Query MarkLogic for existing external security configuration (properties endpoint for detailed config)
    EXISTING_CONFIG=$(curl -X GET --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
        -H "Accept: application/json" \
        -s \
        --connect-timeout 10 \
        --max-time 30 \
        "$MARKLOGIC_URL" 2>&1)
    
    # Check if query was successful
    if [ $? -ne 0 ]; then
        echo "⚠️  Warning: Could not retrieve existing MarkLogic configuration"
        echo "   Proceeding with all keys (they may be duplicates)"
        return 1
    fi
    
    # Check if response contains valid JSON
    echo "$EXISTING_CONFIG" | jq . >/dev/null 2>&1
    if [ $? -ne 0 ]; then
        echo "⚠️  Warning: Invalid JSON response from MarkLogic"
        echo "   Response: $EXISTING_CONFIG"
        echo "   Proceeding with all keys (they may be duplicates)"
        return 1
    fi
    
    # Debug: Show the structure of the response to help with JSON path debugging
    echo "🔍 MarkLogic configuration structure (first level keys):"
    echo "$EXISTING_CONFIG" | jq -r 'keys[]' 2>/dev/null | head -10 | sed 's/^/   - /'
    
    # Debug: Show oauth-server structure if it exists
    if echo "$EXISTING_CONFIG" | jq -e '.["oauth-server"]' >/dev/null 2>&1; then
        echo "🔍 Found oauth-server structure"
        if echo "$EXISTING_CONFIG" | jq -e '.["oauth-server"]["oauth-jwt-secrets"]' >/dev/null 2>&1; then
            echo "🔍 Found oauth-jwt-secrets structure"
            SECRET_COUNT=$(echo "$EXISTING_CONFIG" | jq '.["oauth-server"]["oauth-jwt-secrets"]["oauth-jwt-secret"] | length' 2>/dev/null || echo "0")
            echo "🔍 Found $SECRET_COUNT existing secrets"
        fi
    fi
    
    # Extract existing key IDs from the oauth-jwt-secrets.oauth-jwt-secret array
    # Based on the MarkLogic API properties response structure
    EXISTING_KEY_IDS=$(echo "$EXISTING_CONFIG" | jq -r '.["oauth-server"]["oauth-jwt-secrets"]["oauth-jwt-secret"][]? | .["oauth-jwt-key-id"] // empty' 2>/dev/null)
    
    if [ -z "$EXISTING_KEY_IDS" ]; then
        echo "📭 No existing JWT secrets found in MarkLogic"
        return 0
    fi
    
    # Count existing keys
    EXISTING_COUNT=$(echo "$EXISTING_KEY_IDS" | wc -l | tr -d ' ')
    echo "📊 Found $EXISTING_COUNT existing key(s) in MarkLogic:"
    
    # Display existing key IDs
    while IFS= read -r key_id; do
        [ -n "$key_id" ] && echo "   - $key_id"
    done <<< "$EXISTING_KEY_IDS"
    
    return 0
}

# Function to check if a key ID already exists in MarkLogic
key_exists_in_marklogic() {
    local kid="$1"
    
    # If we couldn't get existing keys, assume it doesn't exist
    if [ -z "$EXISTING_KEY_IDS" ]; then
        return 1
    fi
    
    # Check if the key ID is in the list of existing keys
    echo "$EXISTING_KEY_IDS" | grep -Fxq "$kid"
    return $?
}

# Function to upload keys to MarkLogic
upload_keys_to_marklogic() {
    if [ ${#KEY_DATA[@]} -eq 0 ]; then
        echo ""
        echo "❌ No new RSA keys found to upload to MarkLogic."
        echo "   All keys may already exist in the External Security profile."
        return 1
    fi
    
    echo ""
    echo "🔄 Preparing to upload ${#KEY_DATA[@]} new key(s) to MarkLogic..."
    
    # Create JSON payload
    PAYLOAD_FILE="JWTSecretsPayload.json"
    
    # Start building JSON
    echo "{" > "$PAYLOAD_FILE"
    echo "  \"oauth-server\": {" >> "$PAYLOAD_FILE"
    echo "    \"oauth-jwt-secret\": [" >> "$PAYLOAD_FILE"
    
    # Add each key to the JSON
    for i in "${!KEY_DATA[@]}"; do
        echo "      ${KEY_DATA[$i]}" >> "$PAYLOAD_FILE"
        # Add comma if not the last element
        if [ $i -lt $((${#KEY_DATA[@]} - 1)) ]; then
            echo "," >> "$PAYLOAD_FILE"
        else
            echo "" >> "$PAYLOAD_FILE"
        fi
    done
    
    # Close JSON structure
    echo "    ]" >> "$PAYLOAD_FILE"
    echo "  }" >> "$PAYLOAD_FILE"
    echo "}" >> "$PAYLOAD_FILE"
    
    echo "📝 Created payload file: $PAYLOAD_FILE"
    echo "📄 Payload content:"
    cat "$PAYLOAD_FILE" | jq . 2>/dev/null || cat "$PAYLOAD_FILE"
    echo ""
    
    # Upload to MarkLogic using the hardcoded curl command
    echo "🚀 Uploading new keys to MarkLogic External Security profile..."
    echo "   External Security Profile: $EXTERNAL_SECURITY_NAME"
    echo "   MarkLogic Server: http://$MARKLOGIC_HOST:$MARKLOGIC_PORT"
    echo "   New keys to add: ${#KEY_DATA[@]}"
    echo ""
    
    CURL_RESPONSE=$(curl -X POST --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
        -H "Content-Type:application/json" \
        -d @"$PAYLOAD_FILE" \
        -w "\nHTTP_CODE:%{http_code}" \
        -s \
        "http://$MARKLOGIC_HOST:$MARKLOGIC_PORT/manage/v2/external-security/$EXTERNAL_SECURITY_NAME/jwt-secrets" 2>&1)
    
    # Parse response
    HTTP_CODE=$(echo "$CURL_RESPONSE" | grep "HTTP_CODE:" | cut -d: -f2)
    RESPONSE_BODY=$(echo "$CURL_RESPONSE" | sed '/HTTP_CODE:/d')
    
    if [ "$HTTP_CODE" = "204" ] || [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
        echo "✅ Successfully uploaded JWT secrets to MarkLogic!"
        echo "   HTTP Status: $HTTP_CODE"
        echo "   Keys added: ${#KEY_DATA[@]}"
    else
        echo "❌ Failed to upload JWT secrets to MarkLogic"
        echo "   HTTP Status: $HTTP_CODE"
        echo "   Response: $RESPONSE_BODY"
        echo ""
        echo "💡 Troubleshooting tips:"
        echo "   - Verify MarkLogic is running on $MARKLOGIC_HOST:$MARKLOGIC_PORT"
        echo "   - Check external security profile '$EXTERNAL_SECURITY_NAME' exists"
        echo "   - Verify admin credentials are correct"
        echo "   - Ensure the external security profile is configured for JWT tokens"
    fi
    
    # # Keep or remove payload file
    # echo ""
    # read -p "Keep payload file '$PAYLOAD_FILE' for reference? (y/n): " -n 1 -r
    # echo ""
    # if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    #     rm -f "$PAYLOAD_FILE"
    #     echo "🗑️  Payload file removed"
    # else
    #     echo "📁 Payload file kept: $PAYLOAD_FILE"
    # fi
}

# Fetch JWKS data (with SSL options for self-signed certificates)
JWKS_DATA=$(curl -s -k --connect-timeout 10 --max-time 30 "$JWKS_URL")

# Check if curl was successful
if [ $? -ne 0 ]; then
    echo "Error: Failed to fetch JWKS data from $JWKS_URL"
    exit 1
fi

# Check if response is valid JSON
echo "$JWKS_DATA" | jq . >/dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "Error: Invalid JSON response from JWKS endpoint"
    exit 1
fi

# Check if response has keys array
KEY_COUNT=$(echo "$JWKS_DATA" | jq '.keys | length' 2>/dev/null || echo "0")

if [ "$KEY_COUNT" -eq 0 ]; then
    echo "No keys found in JWKS response"
    exit 0
fi

# Initialize existing key IDs variable and get existing keys if upload is requested
EXISTING_KEY_IDS=""
if [ "$UPLOAD_TO_MARKLOGIC" = true ]; then
    get_existing_key_ids
fi

# Extract and display each key's information (simple format)
for i in $(seq 0 $((KEY_COUNT - 1))); do
    # Extract key ID (kid)
    KID=$(echo "$JWKS_DATA" | jq -r ".keys[$i].kid // \"(no kid)\"")
    echo "$KID"
    
    # Extract key data based on key type
    KTY=$(echo "$JWKS_DATA" | jq -r ".keys[$i].kty // \"(no kty)\"")
    
    if [ "$KTY" = "RSA" ]; then
        # RSA keys - convert to PEM format
        MODULUS=$(echo "$JWKS_DATA" | jq -r ".keys[$i].n // \"(no modulus)\"")
        EXPONENT=$(echo "$JWKS_DATA" | jq -r ".keys[$i].e // \"(no exponent)\"")
        
        # Create RSA public key in PEM format using python3 and openssl
        PEM_KEY_CONTENT=""
        if command -v openssl >/dev/null 2>&1 && command -v python3 >/dev/null 2>&1 && [ "$MODULUS" != "(no modulus)" ] && [ "$EXPONENT" != "(no exponent)" ]; then
            # Create temporary file for RSA key generation
            TEMP_FILE=$(mktemp)
            
            # Use Python to create proper RSA public key in PEM format
            python3 -c "
import base64
import sys

def base64url_decode(data):
    # Add padding if needed
    missing_padding = len(data) % 4
    if missing_padding:
        data += '=' * (4 - missing_padding)
    return base64.urlsafe_b64decode(data)

def der_encode_length(length):
    if length < 0x80:
        return bytes([length])
    else:
        length_bytes = []
        while length > 0:
            length_bytes.insert(0, length & 0xff)
            length >>= 8
        return bytes([0x80 | len(length_bytes)]) + bytes(length_bytes)

def der_encode_integer(value):
    # Remove leading zeros except when needed to prevent negative interpretation
    while len(value) > 1 and value[0] == 0 and (value[1] & 0x80) == 0:
        value = value[1:]
    # Add leading zero if first bit is set (to prevent negative interpretation)
    if value[0] & 0x80:
        value = b'\x00' + value
    return b'\x02' + der_encode_length(len(value)) + value

try:
    # Decode modulus and exponent from base64url
    n = base64url_decode('$MODULUS')
    e = base64url_decode('$EXPONENT')

    # Create DER encoded integers
    n_der = der_encode_integer(n)
    e_der = der_encode_integer(e)

    # Create SEQUENCE containing n and e
    seq_content = n_der + e_der
    rsa_seq = b'\x30' + der_encode_length(len(seq_content)) + seq_content

    # Create SubjectPublicKeyInfo structure
    # RSA OID: 1.2.840.113549.1.1.1 with NULL parameters
    rsa_oid = bytes.fromhex('300d06092a864886f70d0101010500')
    
    # BIT STRING containing the RSA sequence
    bit_string = b'\x03' + der_encode_length(len(rsa_seq) + 1) + b'\x00' + rsa_seq
    
    # Final SubjectPublicKeyInfo SEQUENCE
    spki_content = rsa_oid + bit_string
    spki = b'\x30' + der_encode_length(len(spki_content)) + spki_content

    # Write DER to temporary file
    with open('$TEMP_FILE', 'wb') as f:
        f.write(spki)
        
except Exception as e:
    print(f'Error creating RSA key: {e}', file=sys.stderr)
    sys.exit(1)
" 2>/dev/null
            
            # Convert DER to PEM using openssl
            if [ -f "$TEMP_FILE" ] && [ -s "$TEMP_FILE" ]; then
                PEM_OUTPUT=$(openssl rsa -pubin -inform DER -in "$TEMP_FILE" -outform PEM 2>/dev/null)
                if [ $? -eq 0 ] && [ -n "$PEM_OUTPUT" ]; then
                    echo "$PEM_OUTPUT"
                    # Store PEM content for MarkLogic upload (escape newlines for JSON)
                    PEM_KEY_CONTENT=$(echo "$PEM_OUTPUT" | awk '{printf "%s\\n", $0}' | sed 's/\\n$//')
                else
                    echo "Error: Could not convert to PEM format. Raw modulus:"
                    echo "$MODULUS"
                fi
            else
                echo "Error: Could not generate RSA key structure. Raw modulus:"
                echo "$MODULUS"
            fi
            
            # Clean up
            rm -f "$TEMP_FILE" 2>/dev/null
        else
            # Fallback to base64url modulus if dependencies not available
            echo "$MODULUS"
        fi
        
    elif [ "$KTY" = "EC" ]; then
        # Elliptic Curve keys - output x coordinate (main key data)
        X_COORD=$(echo "$JWKS_DATA" | jq -r ".keys[$i].x // \"(no x)\"")
        echo "$X_COORD"
        
    elif [ "$KTY" = "oct" ]; then
        # Symmetric keys - output key value
        KEY_VALUE=$(echo "$JWKS_DATA" | jq -r ".keys[$i].k // \"(no key value)\"")
        echo "$KEY_VALUE"
        
    else
        # Unknown key type - output raw JSON
        echo "$JWKS_DATA" | jq -c ".keys[$i]"
    fi
    
    # Store key data for MarkLogic upload if requested
    if [ "$UPLOAD_TO_MARKLOGIC" = true ]; then
        if [ "$KTY" = "RSA" ] && [ "$PEM_KEY_CONTENT" != "" ]; then
            # Check if this key already exists in MarkLogic
            if key_exists_in_marklogic "$KID"; then
                echo "⏭️  Key '$KID' already exists in MarkLogic - skipping"
            else
                echo "✅ Key '$KID' is new - will be added to MarkLogic"
                # Store kid and PEM key for later processing
                KEY_DATA+=("{\"oauth-jwt-key-id\": \"$KID\", \"oauth-jwt-secret-value\": \"$PEM_KEY_CONTENT\"}")
            fi
        fi
    fi
    
    # Add blank line between keys (except for last key)
    if [ $i -lt $((KEY_COUNT - 1)) ]; then
        echo ""
    fi
done

# Upload to MarkLogic if requested
if [ "$UPLOAD_TO_MARKLOGIC" = true ]; then
    upload_keys_to_marklogic
fi