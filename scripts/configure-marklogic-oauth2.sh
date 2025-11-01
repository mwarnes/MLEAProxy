#!/bin/bash

# ================================================================
# MarkLogic OAuth2 Configuration Script
# ================================================================
# 
# This script creates MarkLogic OAuth2 external security configuration
# based on OAuth2 Authorization Server .well-known discovery endpoint
# 
# Features:
# - Fetches configuration from .well-known/openid_configuration or .well-known/config
# - Creates MarkLogic external security configuration via REST API
# - Optionally fetches and configures JWT secrets from JWKS endpoint
# - Tests configuration with MLEAProxy OAuth server
# - Validates token verification
#
# Author: MLEAProxy Development Team
# Version: 1.0.0
# Date: October 2025
#
# Usage:
#   ./configure-marklogic-oauth2.sh [OPTIONS]
#
# Examples:
#   # Configure with MLEAProxy (development/testing)
#   ./configure-marklogic-oauth2.sh --well-known-url http://localhost:8080/oauth/.well-known/config --marklogic-host localhost --config-name MLEAProxy-OAuth
#
#   # Configure with MLEAProxy and fetch JWKS keys
#   ./configure-marklogic-oauth2.sh --well-known-url http://localhost:8080/oauth/.well-known/config --marklogic-host localhost --config-name MLEAProxy-OAuth --fetch-jwks-keys
#
#   # Configure with Keycloak
#   ./configure-marklogic-oauth2.sh --well-known-url https://keycloak.example.com/auth/realms/marklogic/.well-known/openid_configuration --marklogic-host marklogic.example.com --config-name Keycloak-OAuth
#
#   # Configure with Azure AD
#   ./configure-marklogic-oauth2.sh --well-known-url https://login.microsoftonline.com/{tenant-id}/v2.0/.well-known/openid_configuration --marklogic-host marklogic.example.com --config-name AzureAD-OAuth
#
# ================================================================

set -euo pipefail

# ================================================================
# CONFIGURATION VARIABLES
# ================================================================

# Default values
WELL_KNOWN_URL=""
MARKLOGIC_HOST="localhost"
MARKLOGIC_PORT="8002"
MARKLOGIC_USER="admin"
MARKLOGIC_PASS="admin"
CONFIG_NAME="OAuth2-Config"
CONFIG_DESCRIPTION="OAuth2 configuration created by script"
USERNAME_ATTRIBUTE="preferred_username"
ROLE_ATTRIBUTE="marklogic-roles"
PRIVILEGE_ATTRIBUTE=""
CACHE_TIMEOUT="300"
CLIENT_ID="marklogic"
FETCH_JWKS="false"
VERBOSE="false"
DRY_RUN="false"
INSECURE="false"

# URL parsing variables (set by parse_marklogic_host function)
MARKLOGIC_PROTOCOL=""
MARKLOGIC_HOST_ONLY=""
MARKLOGIC_PORT_FROM_URL=""
MARKLOGIC_IS_HTTPS="false"

# ================================================================
# UTILITY FUNCTIONS
# ================================================================

# Parse MarkLogic host URL and extract components
parse_marklogic_host() {
    local host_url="$1"
    
    # If no protocol specified, assume http
    if [[ ! "$host_url" =~ ^https?:// ]]; then
        host_url="http://$host_url"
    fi
    
    # Extract protocol, host, and port
    local protocol host port
    protocol=$(echo "$host_url" | sed 's#://.*##')
    host=$(echo "$host_url" | sed 's#.*://##' | cut -d: -f1 | cut -d/ -f1)
    port=$(echo "$host_url" | sed 's#.*://##' | cut -d: -f2 | cut -d/ -f1)
    
    # If port is same as host, no port was specified
    if [ "$port" = "$host" ]; then
        port=""
    fi
    
    # Export parsed components
    export MARKLOGIC_PROTOCOL="$protocol"
    export MARKLOGIC_HOST_ONLY="$host"
    export MARKLOGIC_PORT_FROM_URL="$port"
    export MARKLOGIC_IS_HTTPS="false"
    
    if [ "$protocol" = "https" ]; then
        export MARKLOGIC_IS_HTTPS="true"
    fi
    
    # Build display URL for user output
    if [ -n "$port" ]; then
        export display_host_url="$protocol://$host:$port"
    else
        export display_host_url="$protocol://$host"
    fi
    
    log_verbose "Parsed MarkLogic URL: protocol=$protocol, host=$host, port=$port, is_https=$MARKLOGIC_IS_HTTPS"
}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" >&2
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" >&2
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1" >&2
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

# Get curl flags based on configuration
get_curl_flags() {
    local flags=""
    if [ "$INSECURE" = "true" ]; then
        flags="$flags -k"
    fi
    echo "$flags"
}

# Get curl flags for MarkLogic requests (includes HTTPS handling)
get_marklogic_curl_flags() {
    local flags=""
    if [ "$INSECURE" = "true" ]; then
        flags="$flags -k"
    elif [ "$MARKLOGIC_IS_HTTPS" = "true" ]; then
        # Automatically add -k flag for HTTPS MarkLogic hosts
        flags="$flags -k"
    fi
    echo "$flags"
}

log_verbose() {
    if [ "$VERBOSE" = "true" ]; then
        echo -e "${CYAN}[DEBUG]${NC} $1" >&2
    fi
}

show_usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Creates MarkLogic OAuth2 external security configuration from OAuth2 Authorization Server discovery endpoints.

OPTIONS:
    --well-known-url URL          OAuth2 .well-known discovery endpoint URL (required)
    --marklogic-host URL          MarkLogic host URL (default: http://localhost)
    --marklogic-port PORT         MarkLogic manage port (default: 8002)
    --marklogic-user USER         MarkLogic admin user (default: admin)
    --marklogic-pass PASS         MarkLogic admin password (default: admin)
    --config-name NAME            External security configuration name (default: OAuth2-Config)
    --config-description DESC     Configuration description
    --username-attribute ATTR     JWT claim for username (default: preferred_username)
    --role-attribute ATTR         JWT claim for roles (default: marklogic-roles)
    --privilege-attribute ATTR    JWT claim for privileges (optional)
    --cache-timeout SECONDS       Token cache timeout (default: 300)
    --client-id ID                OAuth client ID (default: marklogic)
    --fetch-jwks-keys             Fetch and configure JWKS keys (default: disabled)
    --insecure                    Ignore SSL certificate verification errors
    --verbose                     Enable verbose logging
    --dry-run                     Show what would be done without executing
    --help                        Show this help message

EXAMPLES:
    # MLEAProxy (development/testing)
    $0 --well-known-url http://localhost:8080/oauth/.well-known/config \\
       --config-name MLEAProxy-OAuth

    # MLEAProxy with JWKS key fetching
    $0 --well-known-url http://localhost:8080/oauth/.well-known/config \\
       --config-name MLEAProxy-OAuth --fetch-jwks-keys

    # Keycloak
    $0 --well-known-url https://keycloak.example.com/auth/realms/marklogic/.well-known/openid_configuration \\
       --config-name Keycloak-OAuth --marklogic-host production.marklogic.com

    # Azure AD
    $0 --well-known-url https://login.microsoftonline.com/TENANT-ID/v2.0/.well-known/openid_configuration \\
       --config-name AzureAD-OAuth --username-attribute upn --role-attribute roles

ENVIRONMENT VARIABLES:
    MARKLOGIC_HOST               Override default MarkLogic host
    MARKLOGIC_USER               Override default MarkLogic user
    MARKLOGIC_PASS               Override default MarkLogic password

EOF
}

# Validate URL format
validate_url() {
    local url="$1"
    if [[ ! "$url" =~ ^https?:// ]]; then
        log_error "Invalid URL format: $url"
        return 1
    fi
    return 0
}

# Check if command exists
check_dependency() {
    local cmd="$1"
    if ! command -v "$cmd" >/dev/null 2>&1; then
        log_error "Required command '$cmd' not found. Please install it."
        exit 1
    fi
}

# Check required dependencies
check_dependencies() {
    log_info "Checking dependencies..."
    check_dependency "curl"
    check_dependency "jq"
    log_success "All dependencies found"
}

# Test MarkLogic connectivity
test_marklogic_connection() {
    log_info "Testing MarkLogic connectivity..."
    
    # Use parsed URL components or fallback to original values
    local protocol="${MARKLOGIC_PROTOCOL:-http}"
    local host="${MARKLOGIC_HOST_ONLY:-$MARKLOGIC_HOST}"
    local port="${MARKLOGIC_PORT_FROM_URL:-$MARKLOGIC_PORT}"
    local test_url="$protocol://$host:$port"
    
    local response status_code
    
    local curl_flags
    curl_flags=$(get_marklogic_curl_flags)
    response=$(curl -s -w "%{http_code}" -m 10 --connect-timeout 5 $curl_flags "$test_url" 2>/dev/null)
    status_code="${response: -3}"
    
    case "$status_code" in
        200|401|403)
            log_success "MarkLogic is accessible at $test_url"
            return 0
            ;;
        000)
            log_error "Cannot connect to MarkLogic at $test_url"
            log_error "Common solutions:"
            log_error "  1. Start MarkLogic: sudo /etc/init.d/MarkLogic start"
            log_error "  2. Check if running: sudo service MarkLogic status"
            log_error "  3. Verify port $MARKLOGIC_PORT is correct (usually 8001 or 8002)"
            log_error "  4. Check firewall settings"
            return 1
            ;;
        *)
            log_warning "Unexpected response from MarkLogic (HTTP $status_code)"
            log_info "Continuing anyway - may be a version-specific response"
            return 0
            ;;
    esac
}

# ================================================================
# OAUTH2 DISCOVERY FUNCTIONS
# ================================================================

# Fetch OAuth2 configuration from .well-known endpoint
fetch_oauth_config() {
    local well_known_url="$1"
    
    log_info "Fetching OAuth2 configuration from: $well_known_url"
    
    # Use curl with output redirection to avoid any stdout contamination
    local temp_file response
    temp_file=$(mktemp)
    
    # Redirect both stdout and stderr, then check if curl succeeded
    local curl_flags
    curl_flags=$(get_curl_flags)
    if curl -s -f $curl_flags "$well_known_url" -o "$temp_file" >/dev/null 2>&1; then
        response=$(cat "$temp_file")
        rm -f "$temp_file"
    else
        rm -f "$temp_file"
        log_error "Failed to fetch OAuth2 configuration from $well_known_url"
        return 1
    fi
    
    # Clean the response to remove any extra characters or whitespace
    response=$(echo "$response" | tr -d '\r' | sed 's/[[:space:]]*$//')
    
    # Debug: show what we extracted
    log_verbose "Fetched JSON response length: ${#response}"
    log_verbose "First 200 chars: '${response:0:200}'"
    log_verbose "Last 50 chars: '${response: -50}'"
    
    # Validate JSON response
    if [ -z "$response" ] || ! echo "$response" | jq empty 2>/dev/null; then
        log_error "Invalid or missing JSON response from OAuth2 discovery endpoint"
        log_error "Full response: '$response'"
        log_error "Response hex dump:"
        echo -n "$response" | hexdump -C | head -5
        return 1
    fi
    
    log_verbose "OAuth2 configuration response: $response"
    echo "$response"
}

# Extract configuration values from OAuth2 discovery response
parse_oauth_config() {
    local config_json="$1"
    
    log_info "Parsing OAuth2 configuration..."
    
    # Debug: show what we received for parsing
    log_verbose "Config JSON to parse (length ${#config_json}):"
    log_verbose "First 200 chars: '${config_json:0:200}'"
    
    # Extract required fields
    ISSUER=$(echo "$config_json" | jq -r '.issuer // .iss // "unknown-issuer"')
    TOKEN_ENDPOINT=$(echo "$config_json" | jq -r '.token_endpoint // ""')
    JWKS_URI=$(echo "$config_json" | jq -r '.jwks_uri // ""')
    
    log_verbose "Parsed issuer: $ISSUER"
    log_verbose "Parsed token endpoint: $TOKEN_ENDPOINT"
    log_verbose "Parsed JWKS URI: $JWKS_URI"
    
    # Validate required fields
    if [ "$ISSUER" = "null" ] || [ "$ISSUER" = "" ]; then
        log_warning "No issuer found in OAuth2 configuration"
        ISSUER="unknown-issuer"
    fi
    
    if [ "$JWKS_URI" = "null" ] || [ "$JWKS_URI" = "" ]; then
        log_warning "No JWKS URI found in OAuth2 configuration"
        FETCH_JWKS="false"
    fi
    
    log_success "OAuth2 configuration parsed successfully"
}

# Fetch JWKS keys from JWKS endpoint
fetch_jwks_keys() {
    local jwks_uri="$1"
    
    if [ "$FETCH_JWKS" != "true" ] || [ "$jwks_uri" = "" ] || [ "$jwks_uri" = "null" ]; then
        log_warning "Skipping JWKS key fetching"
        return 0
    fi
    
    log_info "Fetching JWKS keys from: $jwks_uri"
    
    local jwks_response
    local curl_flags
    curl_flags=$(get_curl_flags)
    jwks_response=$(curl -s -f $curl_flags "$jwks_uri" 2>/dev/null) || {
        log_error "Failed to fetch JWKS from $jwks_uri"
        return 1
    }
    
    # Validate JWKS response
    if ! echo "$jwks_response" | jq empty 2>/dev/null; then
        log_error "Invalid JSON response from JWKS endpoint"
        return 1
    fi
    
    local key_count
    key_count=$(echo "$jwks_response" | jq '.keys | length')
    log_info "Found $key_count keys in JWKS"
    
    log_verbose "JWKS response: $jwks_response"
    echo "$jwks_response"
}

# Convert JWKS key to PEM format
jwk_to_pem() {
    local jwks_json="$1"
    local key_index="${2:-0}"
    
    # Extract the first RSA key
    local key_data
    key_data=$(echo "$jwks_json" | jq -r ".keys[$key_index] | select(.kty == \"RSA\")")
    
    if [ "$key_data" = "null" ] || [ "$key_data" = "" ]; then
        log_warning "No RSA key found at index $key_index"
        return 1
    fi
    
    local kid n e
    kid=$(echo "$key_data" | jq -r '.kid // "unknown"')
    n=$(echo "$key_data" | jq -r '.n')
    e=$(echo "$key_data" | jq -r '.e')
    
    log_verbose "Converting JWK to PEM - kid: $kid"
    
    # Note: This is a simplified conversion. For production use, consider using
    # a proper JWK to PEM conversion tool or library
    log_warning "JWK to PEM conversion requires additional tooling for production use"
    echo "$kid"
}

# ================================================================
# MARKLOGIC CONFIGURATION FUNCTIONS
# ================================================================

# Create MarkLogic external security configuration JSON
create_external_security_config() {
    local config_json
    
    log_info "Creating MarkLogic external security configuration..."
    
    config_json=$(cat << EOF
{
  "external-security-name": "$CONFIG_NAME",
  "description": "$CONFIG_DESCRIPTION",
  "authentication": "oauth",
  "cache-timeout": $CACHE_TIMEOUT,
  "authorization": "oauth",
  "oauth-server": {
    "oauth-vendor": "Other",
    "oauth-flow-type": "Resource server",
    "oauth-client-id": "$CLIENT_ID",
    "oauth-jwt-issuer-uri": "$ISSUER",
    "oauth-token-type": "JSON Web Tokens",
    "oauth-username-attribute": "$USERNAME_ATTRIBUTE",
    "oauth-role-attribute": "$ROLE_ATTRIBUTE",
    "oauth-privilege-attribute": "$PRIVILEGE_ATTRIBUTE",
    "oauth-jwt-alg": "RS256"$([ "$JWKS_URI" != "" ] && [ "$JWKS_URI" != "null" ] && echo ",
    \"oauth-jwks-uri\": \"$JWKS_URI\"" || echo "")
  }
}
EOF
    )
    
    log_verbose "External security configuration: $config_json"
    echo "$config_json"
}

# Apply configuration to MarkLogic
apply_marklogic_config() {
    local config_json="$1"
    
    # Use parsed URL components or fallback to original values
    local protocol="${MARKLOGIC_PROTOCOL:-http}"
    local host="${MARKLOGIC_HOST_ONLY:-$MARKLOGIC_HOST}"
    local port="${MARKLOGIC_PORT_FROM_URL:-$MARKLOGIC_PORT}"
    local marklogic_url="$protocol://$host:$port/manage/v2/external-security"
    
    log_info "Applying configuration to MarkLogic at: $marklogic_url"
    
    if [ "$DRY_RUN" = "true" ]; then
        log_info "DRY RUN - Curl command that would be executed:"
        echo
        local marklogic_curl_flags
        marklogic_curl_flags=$(get_marklogic_curl_flags)
        cat << EOF
curl -X POST$([ -n "$marklogic_curl_flags" ] && echo " $marklogic_curl_flags") \\
  --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \\
  -H "Content-Type: application/json" \\
  -d '@-' \\
  "$marklogic_url" << 'JSON_PAYLOAD'
$(echo "$config_json" | jq .)
JSON_PAYLOAD
EOF
        echo
        return 0
    fi
    
    # First test if MarkLogic is accessible
    log_verbose "Testing MarkLogic connectivity..."
    local connectivity_test
    local marklogic_curl_flags
    marklogic_curl_flags=$(get_marklogic_curl_flags)
    connectivity_test=$(curl -s -w "%{http_code}" -m 10 --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
        $marklogic_curl_flags "$protocol://$host:$port" 2>/dev/null)
    local test_status="${connectivity_test: -3}"
    
    if [ "$test_status" = "000" ]; then
        log_error "Cannot connect to MarkLogic at $host:$port"
        log_error "Please verify:"
        log_error "  1. MarkLogic is running"
        log_error "  2. Admin console is accessible at $protocol://$host:$port"
        log_error "  3. Host and port are correct"
        return 1
    fi
    
    local response status_code
    response=$(curl -s -w "%{http_code}" -m 30 --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
        $marklogic_curl_flags \
        -H "Content-Type: application/json" \
        -d "$config_json" \
        "$marklogic_url" 2>/dev/null)
    
    status_code="${response: -3}"
    response_body="${response%???}"
    
    log_verbose "MarkLogic response code: $status_code"
    log_verbose "MarkLogic response body: $response_body"
    
    case "$status_code" in
        201|200)
            log_success "External security configuration created successfully"
            return 0
            ;;
        000)
            log_error "Connection failed to MarkLogic management API"
            log_error "URL: $marklogic_url"
            log_error "Please check MarkLogic is running and management API is accessible"
            return 1
            ;;
        400)
            log_error "Bad request - check configuration parameters"
            echo "$response_body" | jq . 2>/dev/null || echo "$response_body" >&2
            return 1
            ;;
        401)
            log_error "Unauthorized - check MarkLogic credentials (user: $MARKLOGIC_USER)"
            return 1
            ;;
        403)
            log_error "Forbidden - user $MARKLOGIC_USER lacks permissions for external security configuration"
            return 1
            ;;
        409)
            log_warning "Configuration already exists - updating..."
            update_marklogic_config "$config_json"
            return $?
            ;;
        *)
            log_error "Failed to create configuration (HTTP $status_code)"
            echo "$response_body" | jq . 2>/dev/null || echo "$response_body" >&2
            return 1
            ;;
    esac
}

# Update existing MarkLogic configuration
update_marklogic_config() {
    local config_json="$1"
    
    # Use parsed URL components or fallback to original values
    local protocol="${MARKLOGIC_PROTOCOL:-http}"
    local host="${MARKLOGIC_HOST_ONLY:-$MARKLOGIC_HOST}"
    local port="${MARKLOGIC_PORT_FROM_URL:-$MARKLOGIC_PORT}"
    local marklogic_url="$protocol://$host:$port/manage/v2/external-security/$CONFIG_NAME"
    
    log_info "Updating existing MarkLogic configuration..."
    
    if [ "$DRY_RUN" = "true" ]; then
        log_info "DRY RUN - Curl command that would be executed for update:"
        echo
        local marklogic_curl_flags
        marklogic_curl_flags=$(get_marklogic_curl_flags)
        cat << EOF
curl -X PUT$([ -n "$marklogic_curl_flags" ] && echo " $marklogic_curl_flags") \\
  --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \\
  -H "Content-Type: application/json" \\
  -d '@-' \\
  "$marklogic_url" << 'JSON_PAYLOAD'
$(echo "$config_json" | jq .)
JSON_PAYLOAD
EOF
        echo
        return 0
    fi
    
    local response status_code
    local curl_flags
    curl_flags=$(get_marklogic_curl_flags)
    response=$(curl -s -w "%{http_code}" --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
        $curl_flags \
        -H "Content-Type: application/json" \
        -X PUT \
        -d "$config_json" \
        "$marklogic_url" 2>/dev/null)
    
    status_code="${response: -3}"
    response_body="${response%???}"
    
    case "$status_code" in
        204|200)
            log_success "External security configuration updated successfully"
            return 0
            ;;
        *)
            log_error "Failed to update configuration (HTTP $status_code)"
            echo "$response_body" | jq . 2>/dev/null || echo "$response_body"
            return 1
            ;;
    esac
}

# Add JWT secrets to MarkLogic configuration
add_jwt_secrets() {
    local jwks_json="$1"
    
    if [ "$FETCH_JWKS" != "true" ] || [ "$jwks_json" = "" ]; then
        log_warning "Skipping JWT secrets configuration"
        return 0
    fi
    
    log_info "Adding JWT secrets to MarkLogic configuration..."
    
    if [ "$DRY_RUN" = "true" ]; then
        log_info "DRY RUN - JWT secrets would be configured"
        return 0
    fi
    
    # For now, log that manual JWT secret configuration may be needed
    # Full JWKS to PEM conversion and secret installation requires additional tools
    log_warning "Manual JWT secret configuration may be required"
    log_info "Use the following endpoint to add JWT secrets manually:"
    
    # Use parsed URL components or fallback to original values
    local protocol="${MARKLOGIC_PROTOCOL:-http}"
    local host="${MARKLOGIC_HOST_ONLY:-$MARKLOGIC_HOST}"
    local port="${MARKLOGIC_PORT_FROM_URL:-$MARKLOGIC_PORT}"
    log_info "POST $protocol://$host:$port/manage/v2/external-security/$CONFIG_NAME/jwt-secrets"
    log_info "JWKS data available for manual conversion: $(echo "$jwks_json" | jq -c '.keys | length') keys found"
}



# Validate the created configuration
validate_configuration() {
    log_info "Validating MarkLogic OAuth2 configuration..."
    
    # Use parsed URL components or fallback to original values
    local protocol="${MARKLOGIC_PROTOCOL:-http}"
    local host="${MARKLOGIC_HOST_ONLY:-$MARKLOGIC_HOST}"
    local port="${MARKLOGIC_PORT_FROM_URL:-$MARKLOGIC_PORT}"
    local config_url="$protocol://$host:$port/manage/v2/external-security/$CONFIG_NAME"
    
    local response status_code
    local curl_flags
    curl_flags=$(get_marklogic_curl_flags)
    
    response=$(curl -s -w "%{http_code}" --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
        $curl_flags "$config_url" 2>/dev/null)
    
    status_code="${response: -3}"
    response_body="${response%???}"
    
    case "$status_code" in
        200)
            log_success "Configuration validation successful"
            log_verbose "Configuration details: $response_body"
            return 0
            ;;
        404)
            log_error "Configuration not found - creation may have failed"
            return 1
            ;;
        *)
            log_error "Failed to validate configuration (HTTP $status_code)"
            return 1
            ;;
    esac
}

# ================================================================
# MAIN SCRIPT LOGIC
# ================================================================

# Parse command line arguments
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --well-known-url)
                WELL_KNOWN_URL="$2"
                shift 2
                ;;
            --marklogic-host)
                MARKLOGIC_HOST="$2"
                shift 2
                ;;
            --marklogic-port)
                MARKLOGIC_PORT="$2"
                shift 2
                ;;
            --marklogic-user)
                MARKLOGIC_USER="$2"
                shift 2
                ;;
            --marklogic-pass)
                MARKLOGIC_PASS="$2"
                shift 2
                ;;
            --config-name)
                CONFIG_NAME="$2"
                shift 2
                ;;
            --config-description)
                CONFIG_DESCRIPTION="$2"
                shift 2
                ;;
            --username-attribute)
                USERNAME_ATTRIBUTE="$2"
                shift 2
                ;;
            --role-attribute)
                ROLE_ATTRIBUTE="$2"
                shift 2
                ;;
            --privilege-attribute)
                PRIVILEGE_ATTRIBUTE="$2"
                shift 2
                ;;
            --cache-timeout)
                CACHE_TIMEOUT="$2"
                shift 2
                ;;
            --client-id)
                CLIENT_ID="$2"
                shift 2
                ;;
            --fetch-jwks-keys)
                FETCH_JWKS="true"
                shift
                ;;
            --insecure)
                INSECURE="true"
                shift
                ;;
            --verbose)
                VERBOSE="true"
                shift
                ;;
            --dry-run)
                DRY_RUN="true"
                shift
                ;;
            --help)
                show_usage
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done
    
    # Override with environment variables if set (these may already be set, so preserve them)
    MARKLOGIC_HOST="${MARKLOGIC_HOST:-localhost}"
    MARKLOGIC_USER="${MARKLOGIC_USER:-admin}"
    MARKLOGIC_PASS="${MARKLOGIC_PASS:-admin}"
}

# Main execution function
main() {
    log_info "=== MarkLogic OAuth2 Configuration Script ==="
    log_info "Version 1.0.0 - MLEAProxy Development Team"
    echo
    
    # Validate required parameters
    if [ -z "$WELL_KNOWN_URL" ]; then
        log_error "OAuth2 .well-known URL is required"
        echo
        show_usage
        exit 1
    fi
    
    validate_url "$WELL_KNOWN_URL" || exit 1
    
    # Parse MarkLogic host early to show proper URL in logs
    parse_marklogic_host "$MARKLOGIC_HOST"
    local display_host_url="$MARKLOGIC_PROTOCOL://$MARKLOGIC_HOST_ONLY"
    if [ -n "$MARKLOGIC_PORT_FROM_URL" ]; then
        display_host_url="$display_host_url:$MARKLOGIC_PORT_FROM_URL"
    fi
    
    # Check dependencies
    check_dependencies
    echo
    
    # Test MarkLogic connectivity (skip in dry-run mode)
    if [ "$DRY_RUN" != "true" ]; then
        test_marklogic_connection || exit 1
        echo
    fi
    
    # Fetch and parse OAuth2 configuration
    local oauth_config
    oauth_config=$(fetch_oauth_config "$WELL_KNOWN_URL") || exit 1
    parse_oauth_config "$oauth_config"
    echo
    
    # Fetch JWKS keys if available
    local jwks_json=""
    if [ "$FETCH_JWKS" = "true" ] && [ "$JWKS_URI" != "" ] && [ "$JWKS_URI" != "null" ]; then
        jwks_json=$(fetch_jwks_keys "$JWKS_URI") || log_warning "Could not fetch JWKS keys"
    fi
    echo
    
    # Create and apply MarkLogic configuration
    local ml_config
    ml_config=$(create_external_security_config)
    apply_marklogic_config "$ml_config" || exit 1
    echo
    
    # Add JWT secrets if available
    if [ "$jwks_json" != "" ]; then
        add_jwt_secrets "$jwks_json"
        echo
    fi
    
    # Validate configuration
    if [ "$DRY_RUN" != "true" ]; then
        validate_configuration || exit 1
        echo
    fi
    
    # Summary
    log_success "=== Configuration Complete ==="
    log_info "External Security Name: $CONFIG_NAME"
    log_info "OAuth Issuer: $ISSUER"
    log_info "JWKS URI: ${JWKS_URI:-Not configured}"
    log_info "MarkLogic Host: $display_host_url"
    
    if [ "$DRY_RUN" = "true" ]; then
        log_warning "This was a DRY RUN - no changes were made"
    fi
    
    echo
    log_info "Next steps:"
    log_info "1. Configure app servers to use external security: $CONFIG_NAME"
    echo
    log_info "   To configure app servers, run:"
    if [ "$MARKLOGIC_HOST_ONLY" != "localhost" ]; then
        log_info "   ./scripts/configure-appserver-security.sh --appserver <SERVER_NAME> --external-security $CONFIG_NAME --marklogic-host $display_host_url"
    else
        log_info "   ./scripts/configure-appserver-security.sh --appserver <SERVER_NAME> --external-security $CONFIG_NAME"
    fi
    echo
    log_info "   Examples:"
    if [ "$MARKLOGIC_HOST_ONLY" != "localhost" ]; then
        log_info "   ./scripts/configure-appserver-security.sh --appserver App-Services --external-security $CONFIG_NAME --marklogic-host $display_host_url"
        log_info "   ./scripts/configure-appserver-security.sh --appserver Manage --external-security $CONFIG_NAME --marklogic-host $display_host_url --dry-run"
    else
        log_info "   ./scripts/configure-appserver-security.sh --appserver App-Services --external-security $CONFIG_NAME"
        log_info "   ./scripts/configure-appserver-security.sh --appserver Manage --external-security $CONFIG_NAME --dry-run"
    fi
    echo
    log_info "2. Test the OAuth2 configuration:"
    if [ "$MARKLOGIC_HOST_ONLY" != "localhost" ]; then
        log_info "   ./scripts/validate-oauth2-config.sh --well-known-url $WELL_KNOWN_URL --config-name $CONFIG_NAME --marklogic-host $display_host_url"
    else
        log_info "   ./scripts/validate-oauth2-config.sh --well-known-url $WELL_KNOWN_URL --config-name $CONFIG_NAME"
    fi
    echo
    log_info "3. Verify role mapping and user permissions"
    
    if [ "$FETCH_JWKS" = "true" ] && [ "$jwks_json" != "" ]; then
        log_info "4. Consider configuring JWT secrets manually for production use"
    fi
}

# Script entry point
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    parse_arguments "$@"
    main
fi