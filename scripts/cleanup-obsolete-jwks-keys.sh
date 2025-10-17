#!/bin/bash

# JWKS Key Cleanup Analysis Script
# Usage: ./cleanup-obsolete-jwks-keys.sh <JWKS_ENDPOINT_URL> [--delete-keys] [OPTIONS]
# 
# This script compares keys in MarkLogic External Security profile with
# keys currently available in the JWKS endpoint and identifies obsolete
# keys that can be safely removed from MarkLogic.
# 
# Requirements: curl, jq
#
# ⚠️  DISCLAIMER: This software is NOT an official Progress MarkLogic product.
# This integration toolset is provided "AS IS" without any warranties or guarantees.
# Usage is solely at your own risk. No support will be provided by Progress MarkLogic
# for these scripts. Users are responsible for testing and validating functionality
# in their environment. Always test in a development environment before using in production.
# By using this script, you acknowledge and accept full responsibility for any consequences.

set -e  # Exit on any error

# Default configuration values
DEFAULT_MARKLOGIC_HOST="your-marklogic-server.com"
DEFAULT_MARKLOGIC_PORT="8002"
DEFAULT_MARKLOGIC_USER="admin"
DEFAULT_MARKLOGIC_PASS="your-admin-password"
DEFAULT_EXTERNAL_SECURITY_NAME="Your-External-Security-Profile"

# Function to show usage
show_usage() {
    echo "Usage: $0 <JWKS_ENDPOINT_URL> [--delete-keys] [OPTIONS]"
    echo ""
    echo "Required:"
    echo "  <JWKS_ENDPOINT_URL>        The HTTPS/HTTP URL of the JWKS endpoint"
    echo ""
    echo "Modes:"
    echo "  (default)                  Analysis mode - identifies obsolete keys but doesn't delete them"
    echo "  --delete-keys              Delete mode - actually removes obsolete keys from MarkLogic"
    echo ""
    echo "MarkLogic Configuration Options:"
    echo "  --marklogic-host HOST      MarkLogic server hostname (default: $DEFAULT_MARKLOGIC_HOST)"
    echo "  --marklogic-port PORT      MarkLogic Management API port (default: $DEFAULT_MARKLOGIC_PORT)"
    echo "  --marklogic-user USER      MarkLogic admin username (default: $DEFAULT_MARKLOGIC_USER)"
    echo "  --marklogic-pass PASS      MarkLogic admin password (default: $DEFAULT_MARKLOGIC_PASS)"
    echo "  --external-security NAME   External Security profile name (default: $DEFAULT_EXTERNAL_SECURITY_NAME)"
    echo ""
    echo "Examples:"
    echo "  # Analyze obsolete keys (safe mode)"
    echo "  $0 https://your-idp.example.com/realms/your-realm/protocol/openid-connect/certs"
    echo ""
    echo "  # Delete obsolete keys with default settings"
    echo "  $0 https://your-idp.example.com/jwks --delete-keys"
    echo ""
    echo "  # Analyze with custom MarkLogic configuration"
    echo "  $0 https://your-idp.example.com/jwks \\"
    echo "     --marklogic-host ml.company.com --external-security OAuth2-Production"
    echo ""
    echo "  # Delete with environment variables for sensitive data"
    echo "  $0 https://your-idp.example.com/jwks --delete-keys \\"
    echo "     --marklogic-user \"\$ML_USER\" --marklogic-pass \"\$ML_PASS\""
    echo ""
    echo "This script analyzes key differences between MarkLogic and JWKS endpoint"
    echo "and identifies obsolete keys that are no longer in the JWKS."
    exit 1
}

# Initialize variables with defaults
JWKS_URL=""
DELETE_KEYS=false
MARKLOGIC_HOST="$DEFAULT_MARKLOGIC_HOST"
MARKLOGIC_PORT="$DEFAULT_MARKLOGIC_PORT"
MARKLOGIC_USER="$DEFAULT_MARKLOGIC_USER"
MARKLOGIC_PASS="$DEFAULT_MARKLOGIC_PASS"
EXTERNAL_SECURITY_NAME="$DEFAULT_EXTERNAL_SECURITY_NAME"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --delete-keys)
            DELETE_KEYS=true
            shift
            ;;
        --marklogic-host)
            if [[ -z "$2" || "$2" == --* ]]; then
                echo "Error: --marklogic-host requires a value"
                show_usage
            fi
            MARKLOGIC_HOST="$2"
            shift 2
            ;;
        --marklogic-port)
            if [[ -z "$2" || "$2" == --* ]]; then
                echo "Error: --marklogic-port requires a value"
                show_usage
            fi
            if ! [[ "$2" =~ ^[0-9]+$ ]] || [ "$2" -lt 1 ] || [ "$2" -gt 65535 ]; then
                echo "Error: --marklogic-port must be a valid port number (1-65535)"
                show_usage
            fi
            MARKLOGIC_PORT="$2"
            shift 2
            ;;
        --marklogic-user)
            if [[ -z "$2" || "$2" == --* ]]; then
                echo "Error: --marklogic-user requires a value"
                show_usage
            fi
            MARKLOGIC_USER="$2"
            shift 2
            ;;
        --marklogic-pass)
            if [[ -z "$2" || "$2" == --* ]]; then
                echo "Error: --marklogic-pass requires a value"
                show_usage
            fi
            MARKLOGIC_PASS="$2"
            shift 2
            ;;
        --external-security)
            if [[ -z "$2" || "$2" == --* ]]; then
                echo "Error: --external-security requires a value"
                show_usage
            fi
            EXTERNAL_SECURITY_NAME="$2"
            shift 2
            ;;
        --help|-h)
            show_usage
            ;;
        --*)
            echo "Error: Unknown option $1"
            show_usage
            ;;
        *)
            if [[ -z "$JWKS_URL" ]]; then
                JWKS_URL="$1"
            else
                echo "Error: Unexpected argument $1"
                show_usage
            fi
            shift
            ;;
    esac
done

# Check if JWKS URL is provided
if [[ -z "$JWKS_URL" ]]; then
    echo "Error: JWKS endpoint URL is required"
    show_usage
fi

# Validate JWKS URL format
if ! [[ "$JWKS_URL" =~ ^https?:// ]]; then
    echo "Error: JWKS URL must start with http:// or https://"
    show_usage
fi

# Show delete mode warning if enabled
if [ "$DELETE_KEYS" = true ]; then
    echo "⚠️  DELETE MODE ENABLED - Obsolete keys will be removed from MarkLogic!"
    echo ""
fi

# Check if required tools are available
command -v curl >/dev/null 2>&1 || { echo "Error: curl is required but not installed." >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "Error: jq is required but not installed. Install with: brew install jq" >&2; exit 1; }

if [ "$DELETE_KEYS" = true ]; then
    echo "�️  JWKS Key Cleanup & Deletion"
else
    echo "�🔍 JWKS Key Cleanup Analysis"
fi
echo "================================="
echo "JWKS Endpoint: $JWKS_URL"
echo "MarkLogic External Security Profile: $EXTERNAL_SECURITY_NAME"
if [ "$DELETE_KEYS" = true ]; then
    echo "Mode: DELETE MODE - Will remove obsolete keys"
else
    echo "Mode: ANALYSIS MODE - Will identify obsolete keys only"
fi
echo ""

# Function to get current JWKS key IDs
get_current_jwks_keys() {
    echo "📡 Fetching current keys from JWKS endpoint..."
    
    # Fetch JWKS data (with SSL options for self-signed certificates)
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
        exit 1
    fi
    
    # Extract key IDs from JWKS
    CURRENT_JWKS_KEYS=$(echo "$JWKS_DATA" | jq -r '.keys[]?.kid // empty' 2>/dev/null | sort)
    
    if [ -z "$CURRENT_JWKS_KEYS" ]; then
        echo "⚠️  Warning: No key IDs found in JWKS response"
        CURRENT_JWKS_KEYS=""
    else
        KEY_COUNT=$(echo "$CURRENT_JWKS_KEYS" | wc -l | tr -d ' ')
        echo "✅ Found $KEY_COUNT key(s) in current JWKS:"
        while IFS= read -r key_id; do
            [ -n "$key_id" ] && echo "   - $key_id"
        done <<< "$CURRENT_JWKS_KEYS"
    fi
    
    echo ""
}

# Function to get existing MarkLogic key IDs
get_marklogic_keys() {
    echo "🔍 Fetching existing keys from MarkLogic External Security profile..."
    
    # Query MarkLogic for existing external security configuration
    EXISTING_CONFIG=$(curl -X GET --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
        -H "Accept: application/json" \
        -s \
        "http://$MARKLOGIC_HOST:$MARKLOGIC_PORT/manage/v2/external-security/$EXTERNAL_SECURITY_NAME/properties" 2>&1)
    
    # Check if query was successful
    if [ $? -ne 0 ]; then
        echo "❌ Error: Could not retrieve MarkLogic configuration"
        echo "   Response: $EXISTING_CONFIG"
        exit 1
    fi
    
    # Check if response contains valid JSON
    echo "$EXISTING_CONFIG" | jq . >/dev/null 2>&1
    if [ $? -ne 0 ]; then
        echo "❌ Error: Invalid JSON response from MarkLogic"
        echo "   Response: $EXISTING_CONFIG"
        exit 1
    fi
    
    # Extract existing key IDs from MarkLogic
    MARKLOGIC_KEYS=$(echo "$EXISTING_CONFIG" | jq -r '.["oauth-server"]["oauth-jwt-secrets"]["oauth-jwt-secret"][]? | .["oauth-jwt-key-id"] // empty' 2>/dev/null | sort)
    
    if [ -z "$MARKLOGIC_KEYS" ]; then
        echo "📭 No JWT secrets found in MarkLogic External Security profile"
        MARKLOGIC_KEYS=""
    else
        KEY_COUNT=$(echo "$MARKLOGIC_KEYS" | wc -l | tr -d ' ')
        echo "✅ Found $KEY_COUNT key(s) in MarkLogic:"
        while IFS= read -r key_id; do
            [ -n "$key_id" ] && echo "   - $key_id"
        done <<< "$MARKLOGIC_KEYS"
    fi
    
    echo ""
}

# Function to analyze key differences
analyze_key_differences() {
    echo "📊 Analyzing key differences..."
    echo ""
    
    # Find keys that exist in MarkLogic but not in current JWKS (obsolete keys)
    OBSOLETE_KEYS=""
    if [ -n "$MARKLOGIC_KEYS" ]; then
        while IFS= read -r ml_key; do
            if [ -n "$ml_key" ]; then
                # Check if this MarkLogic key exists in current JWKS
                if [ -z "$CURRENT_JWKS_KEYS" ] || ! echo "$CURRENT_JWKS_KEYS" | grep -Fxq "$ml_key"; then
                    if [ -z "$OBSOLETE_KEYS" ]; then
                        OBSOLETE_KEYS="$ml_key"
                    else
                        OBSOLETE_KEYS="$OBSOLETE_KEYS"$'\n'"$ml_key"
                    fi
                fi
            fi
        done <<< "$MARKLOGIC_KEYS"
    fi
    
    # Find keys that exist in current JWKS but not in MarkLogic (missing keys)
    MISSING_KEYS=""
    if [ -n "$CURRENT_JWKS_KEYS" ]; then
        while IFS= read -r jwks_key; do
            if [ -n "$jwks_key" ]; then
                # Check if this JWKS key exists in MarkLogic
                if [ -z "$MARKLOGIC_KEYS" ] || ! echo "$MARKLOGIC_KEYS" | grep -Fxq "$jwks_key"; then
                    if [ -z "$MISSING_KEYS" ]; then
                        MISSING_KEYS="$jwks_key"
                    else
                        MISSING_KEYS="$MISSING_KEYS"$'\n'"$jwks_key"
                    fi
                fi
            fi
        done <<< "$CURRENT_JWKS_KEYS"
    fi
    
    # Find keys that exist in both (synchronized keys)
    SYNCHRONIZED_KEYS=""
    if [ -n "$MARKLOGIC_KEYS" ] && [ -n "$CURRENT_JWKS_KEYS" ]; then
        while IFS= read -r ml_key; do
            if [ -n "$ml_key" ]; then
                if echo "$CURRENT_JWKS_KEYS" | grep -Fxq "$ml_key"; then
                    if [ -z "$SYNCHRONIZED_KEYS" ]; then
                        SYNCHRONIZED_KEYS="$ml_key"
                    else
                        SYNCHRONIZED_KEYS="$SYNCHRONIZED_KEYS"$'\n'"$ml_key"
                    fi
                fi
            fi
        done <<< "$MARKLOGIC_KEYS"
    fi
    
    # Display results
    echo "🔄 SYNCHRONIZED KEYS (Present in both MarkLogic and JWKS):"
    if [ -n "$SYNCHRONIZED_KEYS" ]; then
        SYNC_COUNT=$(echo "$SYNCHRONIZED_KEYS" | wc -l | tr -d ' ')
        echo "   Count: $SYNC_COUNT"
        while IFS= read -r key_id; do
            [ -n "$key_id" ] && echo "   ✅ $key_id"
        done <<< "$SYNCHRONIZED_KEYS"
    else
        echo "   Count: 0"
        echo "   ⚠️  No keys are synchronized between MarkLogic and JWKS"
    fi
    echo ""
    
    echo "🆕 MISSING KEYS (Present in JWKS but not in MarkLogic):"
    if [ -n "$MISSING_KEYS" ]; then
        MISSING_COUNT=$(echo "$MISSING_KEYS" | wc -l | tr -d ' ')
        echo "   Count: $MISSING_COUNT"
        while IFS= read -r key_id; do
            [ -n "$key_id" ] && echo "   ➕ $key_id"
        done <<< "$MISSING_KEYS"
        echo "   💡 Use scripts/extract-jwks-keys.sh --upload-to-marklogic to add these keys"
    else
        echo "   Count: 0"
        echo "   ✅ All JWKS keys are already in MarkLogic"
    fi
    echo ""
    
    echo "🗑️  OBSOLETE KEYS (Present in MarkLogic but not in current JWKS):"
    if [ -n "$OBSOLETE_KEYS" ]; then
        OBSOLETE_COUNT=$(echo "$OBSOLETE_KEYS" | wc -l | tr -d ' ')
        echo "   Count: $OBSOLETE_COUNT"
        echo "   ⚠️  These keys can potentially be removed from MarkLogic:"
        while IFS= read -r key_id; do
            [ -n "$key_id" ] && echo "   🔴 $key_id"
        done <<< "$OBSOLETE_KEYS"
        echo ""
        echo "   📋 Key IDs that can be deleted (copy/paste ready):"
        while IFS= read -r key_id; do
            [ -n "$key_id" ] && echo "      $key_id"
        done <<< "$OBSOLETE_KEYS"
    else
        echo "   Count: 0"
        echo "   ✅ No obsolete keys found - MarkLogic is clean"
    fi
    echo ""
}

# Function to delete obsolete keys from MarkLogic
delete_obsolete_keys() {
    if [ -z "$OBSOLETE_KEYS" ]; then
        echo "✅ No obsolete keys to delete"
        return 0
    fi
    
    OBSOLETE_COUNT=$(echo "$OBSOLETE_KEYS" | wc -l | tr -d ' ')
    echo "🗑️  DELETING OBSOLETE KEYS"
    echo "=========================="
    echo "About to delete $OBSOLETE_COUNT obsolete key(s) from MarkLogic..."
    echo ""
    
    # Confirmation prompt
    echo "⚠️  WARNING: This action cannot be undone!"
    echo "Keys to be deleted:"
    while IFS= read -r key_id; do
        [ -n "$key_id" ] && echo "   🔴 $key_id"
    done <<< "$OBSOLETE_KEYS"
    echo ""
    read -p "Are you sure you want to delete these keys? (yes/no): " -r
    echo ""
    
    if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
        echo "❌ Deletion cancelled by user"
        return 1
    fi
    
    # Delete each obsolete key
    DELETED_COUNT=0
    FAILED_COUNT=0
    
    while IFS= read -r key_id; do
        if [ -n "$key_id" ]; then
            echo "🗑️  Deleting key: $key_id"
            
            # Delete the key using MarkLogic Management API
            DELETE_RESPONSE=$(curl -X DELETE --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
                -w "\nHTTP_CODE:%{http_code}" \
                -s \
                "http://$MARKLOGIC_HOST:$MARKLOGIC_PORT/manage/v2/external-security/$EXTERNAL_SECURITY_NAME/jwt-secrets/$key_id" 2>&1)
            
            # Parse response
            HTTP_CODE=$(echo "$DELETE_RESPONSE" | grep "HTTP_CODE:" | cut -d: -f2)
            RESPONSE_BODY=$(echo "$DELETE_RESPONSE" | sed '/HTTP_CODE:/d')
            
            if [ "$HTTP_CODE" = "204" ] || [ "$HTTP_CODE" = "200" ]; then
                echo "   ✅ Successfully deleted key: $key_id"
                DELETED_COUNT=$((DELETED_COUNT + 1))
            else
                echo "   ❌ Failed to delete key: $key_id"
                echo "      HTTP Status: $HTTP_CODE"
                echo "      Response: $RESPONSE_BODY"
                FAILED_COUNT=$((FAILED_COUNT + 1))
            fi
            echo ""
        fi
    done <<< "$OBSOLETE_KEYS"
    
    # Summary of deletion results
    echo "📊 DELETION SUMMARY"
    echo "==================="
    echo "✅ Successfully deleted: $DELETED_COUNT key(s)"
    echo "❌ Failed to delete: $FAILED_COUNT key(s)"
    
    if [ $FAILED_COUNT -eq 0 ]; then
        echo "🎉 All obsolete keys have been successfully removed from MarkLogic!"
    else
        echo "⚠️  Some keys could not be deleted. Please check the errors above."
    fi
    echo ""
}

# Function to display summary and recommendations
display_summary() {
    echo "📈 SUMMARY & RECOMMENDATIONS"
    echo "============================="
    
    SYNC_COUNT=0
    MISSING_COUNT=0
    OBSOLETE_COUNT=0
    
    [ -n "$SYNCHRONIZED_KEYS" ] && SYNC_COUNT=$(echo "$SYNCHRONIZED_KEYS" | wc -l | tr -d ' ')
    [ -n "$MISSING_KEYS" ] && MISSING_COUNT=$(echo "$MISSING_KEYS" | wc -l | tr -d ' ')
    [ -n "$OBSOLETE_KEYS" ] && OBSOLETE_COUNT=$(echo "$OBSOLETE_KEYS" | wc -l | tr -d ' ')
    
    echo "🔄 Synchronized keys: $SYNC_COUNT"
    echo "🆕 Missing keys: $MISSING_COUNT"
    echo "🗑️  Obsolete keys: $OBSOLETE_COUNT"
    echo ""
    
    if [ $MISSING_COUNT -gt 0 ]; then
        echo "📝 Next Actions:"
        echo "   1. Add missing keys:"
        echo "      ./scripts/extract-jwks-keys.sh $JWKS_URL --upload-to-marklogic"
        echo ""
    fi
    
    if [ $OBSOLETE_COUNT -gt 0 ] && [ "$DELETE_KEYS" = false ]; then
        echo "🧹 Cleanup Options:"
        echo "   1. Review obsolete keys before deletion"
        echo "   2. Ensure no applications are using old tokens signed with these keys"
        echo "   3. Consider grace period for key rotation"
        echo "   4. To delete obsolete keys, run:"
        echo "      $0 $JWKS_URL --delete-keys"
        echo ""
        echo "⚠️  IMPORTANT: Only delete keys if you're certain they're no longer needed!"
    elif [ $OBSOLETE_COUNT -eq 0 ]; then
        echo "✅ No cleanup needed - all keys are current"
    fi
}

# Main execution
get_current_jwks_keys
get_marklogic_keys
analyze_key_differences

# Delete obsolete keys if requested
if [ "$DELETE_KEYS" = true ]; then
    delete_obsolete_keys
fi

display_summary

if [ "$DELETE_KEYS" = true ]; then
    echo "🏁 Cleanup and deletion complete!"
else
    echo "🏁 Analysis complete!"
fi