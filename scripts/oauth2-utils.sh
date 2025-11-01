#!/bin/bash

# ================================================================
# OAuth2 Utility Functions Library
# ================================================================
#
# This library provides utility functions for OAuth2 operations
# including JWT token manipulation, JWKS processing, and 
# MarkLogic API interactions.
#
# Author: MLEAProxy Development Team
# Version: 1.0.0
# Date: October 2025
#
# Usage:
#   source oauth2-utils.sh
#
# ================================================================

# Prevent multiple includes
# if [ "${OAUTH2_UTILS_LOADED:-}" = "true" ]; then
#     return 0
# fi
# export OAUTH2_UTILS_LOADED=true

# ================================================================
# CONSTANTS AND CONFIGURATION
# ================================================================

# Colors for output
readonly COLOR_RED='\033[0;31m'
readonly COLOR_GREEN='\033[0;32m'
readonly COLOR_YELLOW='\033[1;33m'
readonly COLOR_BLUE='\033[0;34m'
readonly COLOR_CYAN='\033[0;36m'
readonly COLOR_NC='\033[0m' # No Color

# Default timeout for HTTP requests
readonly DEFAULT_TIMEOUT=30

# ================================================================
# LOGGING FUNCTIONS
# ================================================================

oauth2_log_info() {
    echo -e "${COLOR_BLUE}[OAUTH2-INFO]${COLOR_NC} $1" >&2
}

oauth2_log_success() {
    echo -e "${COLOR_GREEN}[OAUTH2-SUCCESS]${COLOR_NC} $1" >&2
}

oauth2_log_warning() {
    echo -e "${COLOR_YELLOW}[OAUTH2-WARNING]${COLOR_NC} $1" >&2
}

oauth2_log_error() {
    echo -e "${COLOR_RED}[OAUTH2-ERROR]${COLOR_NC} $1" >&2
}

oauth2_log_debug() {
    if [ "${OAUTH2_DEBUG:-false}" = "true" ]; then
        echo -e "${COLOR_CYAN}[OAUTH2-DEBUG]${COLOR_NC} $1" >&2
    fi
}

# ================================================================
# VALIDATION FUNCTIONS  
# ================================================================

# Validate URL format
oauth2_validate_url() {
    local url="$1"
    local url_pattern='^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$'
    
    if [[ ! "$url" =~ $url_pattern ]]; then
        oauth2_log_error "Invalid URL format: $url"
        return 1
    fi
    
    return 0
}

# Validate JSON format
oauth2_validate_json() {
    local json_string="$1"
    
    if ! echo "$json_string" | jq empty 2>/dev/null; then
        oauth2_log_error "Invalid JSON format"
        return 1
    fi
    
    return 0
}

# Validate JWT token format (basic structure check)
oauth2_validate_jwt() {
    local token="$1"
    
    # JWT should have 3 parts separated by dots
    local part_count
    part_count=$(echo "$token" | tr '.' '\n' | wc -l)
    
    if [ "$part_count" -ne 3 ]; then
        oauth2_log_error "Invalid JWT format - should have 3 parts separated by dots"
        return 1
    fi
    
    return 0
}

# Check if required dependencies are available
oauth2_check_dependencies() {
    local required_commands=("curl" "jq" "base64")
    local missing_commands=()
    
    for cmd in "${required_commands[@]}"; do
        if ! command -v "$cmd" >/dev/null 2>&1; then
            missing_commands+=("$cmd")
        fi
    done
    
    if [ ${#missing_commands[@]} -ne 0 ]; then
        oauth2_log_error "Missing required commands: ${missing_commands[*]}"
        oauth2_log_error "Please install missing dependencies and try again"
        return 1
    fi
    
    oauth2_log_debug "All dependencies found: ${required_commands[*]}"
    return 0
}

# ================================================================
# HTTP CLIENT FUNCTIONS
# ================================================================

# Enhanced curl wrapper with timeout and error handling
oauth2_http_get() {
    local url="$1"
    local timeout="${2:-$DEFAULT_TIMEOUT}"
    local headers="${3:-}"
    local extra_flags="${4:-}"
    
    oauth2_validate_url "$url" || return 1
    
    oauth2_log_debug "HTTP GET: $url"
    
    local curl_cmd="curl -s -f --connect-timeout $timeout --max-time $timeout"
    
    if [ -n "$headers" ]; then
        curl_cmd="$curl_cmd $headers"
    fi
    
    if [ -n "$extra_flags" ]; then
        curl_cmd="$curl_cmd $extra_flags"
    fi
    
    local response
    response=$($curl_cmd "$url" 2>/dev/null) || {
        oauth2_log_error "HTTP GET failed: $url"
        return 1
    }
    
    echo "$response"
}

# HTTP POST with form data
oauth2_http_post_form() {
    local url="$1"
    local form_data="$2"
    local timeout="${3:-$DEFAULT_TIMEOUT}"
    local headers="${4:--H \"Content-Type: application/x-www-form-urlencoded\"}"
    local extra_flags="${5:-}"
    
    oauth2_validate_url "$url" || return 1
    
    oauth2_log_debug "HTTP POST: $url"
    oauth2_log_debug "Form data: $form_data"
    
    local response
    if [ -n "$extra_flags" ]; then
        response=$(curl -s -f --connect-timeout "$timeout" --max-time "$timeout" \
            -H "Content-Type: application/x-www-form-urlencoded" \
            $extra_flags \
            -d "$form_data" \
            "$url" 2>/dev/null) || {
            oauth2_log_error "HTTP POST failed: $url"
            return 1
        }
    else
        response=$(curl -s -f --connect-timeout "$timeout" --max-time "$timeout" \
            -H "Content-Type: application/x-www-form-urlencoded" \
            -d "$form_data" \
            "$url" 2>/dev/null) || {
            oauth2_log_error "HTTP POST failed: $url"
            return 1
        }
    fi
    
    echo "$response"
}

# HTTP POST with JSON data
oauth2_http_post_json() {
    local url="$1"
    local json_data="$2"
    local timeout="${3:-$DEFAULT_TIMEOUT}"
    local auth_header="${4:-}"
    
    oauth2_validate_url "$url" || return 1
    oauth2_validate_json "$json_data" || return 1
    
    oauth2_log_debug "HTTP POST JSON: $url"
    
    local curl_cmd="curl -s -w %{http_code} --connect-timeout $timeout --max-time $timeout -H \"Content-Type: application/json\""
    
    if [ -n "$auth_header" ]; then
        curl_cmd="$curl_cmd -H \"$auth_header\""
    fi
    
    local response
    response=$(eval "$curl_cmd -d '$json_data' '$url'" 2>/dev/null) || {
        oauth2_log_error "HTTP POST JSON failed: $url"
        return 1
    }
    
    echo "$response"
}

# ================================================================
# JWT TOKEN FUNCTIONS
# ================================================================

# Decode JWT header
oauth2_jwt_decode_header() {
    local token="$1"
    
    oauth2_validate_jwt "$token" || return 1
    
    local header
    header=$(echo "$token" | cut -d. -f1)
    
    # Add padding if needed for base64 decoding
    local padding=$((4 - ${#header} % 4))
    if [ $padding -ne 4 ]; then
        header="${header}$(printf '%*s' $padding | tr ' ' '=')"
    fi
    
    echo "$header" | base64 -d 2>/dev/null | jq . 2>/dev/null || {
        oauth2_log_error "Failed to decode JWT header"
        return 1
    }
}

# Decode JWT payload
oauth2_jwt_decode_payload() {
    local token="$1"
    
    oauth2_validate_jwt "$token" || return 1
    
    local payload
    payload=$(echo "$token" | cut -d. -f2)
    
    # Add padding if needed for base64 decoding
    local padding=$((4 - ${#payload} % 4))
    if [ $padding -ne 4 ]; then
        payload="${payload}$(printf '%*s' $padding | tr ' ' '=')"
    fi
    
    echo "$payload" | base64 -d 2>/dev/null | jq . 2>/dev/null || {
        oauth2_log_error "Failed to decode JWT payload"
        return 1
    }
}

# Extract JWT claim value
oauth2_jwt_get_claim() {
    local token="$1"
    local claim="$2"
    
    local payload
    payload=$(oauth2_jwt_decode_payload "$token") || return 1
    
    echo "$payload" | jq -r ".$claim // empty"
}

# Check if JWT token is expired
oauth2_jwt_is_expired() {
    local token="$1"
    
    local exp_claim current_time
    exp_claim=$(oauth2_jwt_get_claim "$token" "exp") || return 1
    current_time=$(date +%s)
    
    if [ -z "$exp_claim" ]; then
        oauth2_log_warning "No expiration claim found in JWT"
        return 1
    fi
    
    if [ "$current_time" -gt "$exp_claim" ]; then
        oauth2_log_debug "JWT token is expired (exp: $exp_claim, now: $current_time)"
        return 0 # Token is expired
    else
        oauth2_log_debug "JWT token is valid (exp: $exp_claim, now: $current_time)"
        return 1 # Token is not expired
    fi
}

# Get JWT token time until expiration
oauth2_jwt_time_to_expiry() {
    local token="$1"
    
    local exp_claim current_time
    exp_claim=$(oauth2_jwt_get_claim "$token" "exp") || return 1
    current_time=$(date +%s)
    
    if [ -z "$exp_claim" ]; then
        echo "unknown"
        return 1
    fi
    
    local time_diff=$((exp_claim - current_time))
    
    if [ $time_diff -le 0 ]; then
        echo "expired"
    else
        echo "$time_diff"
    fi
}

# ================================================================
# OAUTH2 DISCOVERY FUNCTIONS
# ================================================================

# Fetch OAuth2 well-known configuration
oauth2_fetch_well_known() {
    local base_url="$1"
    local endpoint_path="${2:-.well-known/openid_configuration}"
    
    # Remove trailing slash from base URL
    base_url="${base_url%/}"
    
    local well_known_url="$base_url/$endpoint_path"
    
    oauth2_log_debug "Fetching OAuth2 well-known config from: $well_known_url"
    
    local config
    config=$(oauth2_http_get "$well_known_url") || return 1
    
    oauth2_validate_json "$config" || return 1
    
    echo "$config"
}

# Extract specific endpoint from OAuth2 configuration
oauth2_get_endpoint() {
    local config="$1"
    local endpoint_name="$2"
    
    local endpoint_url
    endpoint_url=$(echo "$config" | jq -r ".$endpoint_name // empty")
    
    if [ -z "$endpoint_url" ]; then
        oauth2_log_warning "Endpoint '$endpoint_name' not found in OAuth2 configuration"
        return 1
    fi
    
    echo "$endpoint_url"
}

# Fetch JWKS from JWKS URI
oauth2_fetch_jwks() {
    local jwks_uri="$1"
    local extra_flags="${2:-}"
    
    oauth2_log_debug "Fetching JWKS from: $jwks_uri"
    
    local jwks
    jwks=$(oauth2_http_get "$jwks_uri" "$DEFAULT_TIMEOUT" "" "$extra_flags") || return 1
    
    oauth2_validate_json "$jwks" || return 1
    
    # Validate JWKS structure
    local key_count
    key_count=$(echo "$jwks" | jq '.keys | length' 2>/dev/null || echo "0")
    
    if [ "$key_count" = "0" ]; then
        oauth2_log_warning "No keys found in JWKS"
        return 1
    fi
    
    oauth2_log_debug "JWKS contains $key_count keys"
    echo "$jwks"
}

# ================================================================
# OAUTH2 TOKEN FUNCTIONS
# ================================================================

# Generate OAuth2 token using client credentials flow
oauth2_get_token_client_credentials() {
    local token_endpoint="$1"
    local client_id="$2"
    local client_secret="$3"
    local scope="${4:-}"
    local extra_flags="${5:-}"
    
    oauth2_log_debug "Requesting token using client credentials flow"
    
    local form_data="grant_type=client_credentials&client_id=$client_id&client_secret=$client_secret"
    
    if [ -n "$scope" ]; then
        form_data="$form_data&scope=$scope"
    fi
    
    local response
    response=$(oauth2_http_post_form "$token_endpoint" "$form_data" "$DEFAULT_TIMEOUT" "-H \"Content-Type: application/x-www-form-urlencoded\"" "$extra_flags") || return 1
    
    oauth2_validate_json "$response" || return 1
    
    # Extract access token
    local access_token
    access_token=$(echo "$response" | jq -r '.access_token // empty')
    
    if [ -z "$access_token" ]; then
        oauth2_log_error "No access token in response"
        oauth2_log_error "Response: $response"
        return 1
    fi
    
    echo "$access_token"
}

# Generate OAuth2 token using password flow
oauth2_get_token_password() {
    local token_endpoint="$1"
    local username="$2"
    local password="$3"
    local client_id="$4"
    local client_secret="${5:-}"
    local scope="${6:-}"
    local extra_flags="${7:-}"
    
    oauth2_log_debug "Requesting token using password flow for user: $username"
    
    local form_data="grant_type=password&username=$username&password=$password&client_id=$client_id"
    
    if [ -n "$client_secret" ]; then
        form_data="$form_data&client_secret=$client_secret"
    fi
    
    if [ -n "$scope" ]; then
        form_data="$form_data&scope=$scope"
    fi
    
    # Use curl directly to get both response and status code for better error handling
    local response status_code
    local temp_response
    temp_response=$(curl -s -w "\n%{http_code}" --connect-timeout "$DEFAULT_TIMEOUT" --max-time "$DEFAULT_TIMEOUT" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        $extra_flags \
        -d "$form_data" \
        "$token_endpoint" 2>/dev/null)
    
    if [ $? -ne 0 ]; then
        oauth2_log_error "Failed to connect to token endpoint: $token_endpoint"
        return 1
    fi
    
    # Split response and status code
    response=$(echo "$temp_response" | sed '$d')
    status_code=$(echo "$temp_response" | tail -n 1)
    
    oauth2_log_debug "🔍 Password flow response - Status: $status_code"
    oauth2_log_debug "🔍 Password flow response body: $(echo "$response" | head -c 200)..."
    
    # Check status code
    case "$status_code" in
        200)
            oauth2_log_debug "Password flow successful - extracting token"
            ;;
        400)
            oauth2_log_error "❌ Password flow failed - Bad Request (400)"
            local error_desc error_reason
            error_reason=$(echo "$response" | jq -r '.error // "unknown_error"' 2>/dev/null)
            error_desc=$(echo "$response" | jq -r '.error_description // "No description provided"' 2>/dev/null)
            oauth2_log_error "   Error: $error_reason"
            oauth2_log_error "   Description: $error_desc"
            
            case "$error_reason" in
                "invalid_grant"|"invalid_user_credentials")
                    oauth2_log_error "   → Username '$username' does not exist or password is incorrect"
                    ;;
                "invalid_client")
                    oauth2_log_error "   → Client ID '$client_id' is not configured or client secret is wrong"
                    ;;
                "unsupported_grant_type")
                    oauth2_log_error "   → Password flow is disabled for this client"
                    ;;
                "invalid_scope")
                    oauth2_log_error "   → Requested scope '$scope' is not available"
                    ;;
            esac
            return 1
            ;;
        401)
            oauth2_log_error "❌ Password flow failed - Unauthorized (401)"
            oauth2_log_error "   → Check username '$username' and password"
            return 1
            ;;
        403)
            oauth2_log_error "❌ Password flow failed - Forbidden (403)"
            oauth2_log_error "   → User '$username' may be disabled or password flow not allowed"
            return 1
            ;;
        *)
            oauth2_log_error "❌ Password flow failed - HTTP $status_code"
            oauth2_log_error "   Response: $response"
            return 1
            ;;
    esac
    
    # Validate JSON response
    if ! oauth2_validate_json "$response"; then
        oauth2_log_error "Invalid JSON response from token endpoint"
        return 1
    fi
    
    # Extract access token
    local access_token
    access_token=$(echo "$response" | jq -r '.access_token // empty')
    
    if [ -z "$access_token" ]; then
        oauth2_log_error "No access token in successful response"
        oauth2_log_error "Response: $response"
        return 1
    fi
    
    echo "$access_token"
}

# ================================================================
# MARKLOGIC INTEGRATION FUNCTIONS
# ================================================================

# Test MarkLogic external security configuration
oauth2_test_marklogic_config() {
    local marklogic_host="$1"
    local marklogic_port="$2"
    local config_name="$3"
    local username="$4"
    local password="$5"
    
    local config_url="http://$marklogic_host:$marklogic_port/manage/v2/external-security/$config_name"
    
    oauth2_log_debug "Testing MarkLogic config: $config_url"
    
    local response status_code
    response=$(curl -s -w "%{http_code}" --anyauth -u "$username:$password" "$config_url" 2>/dev/null)
    
    status_code="${response: -3}"
    response_body="${response%???}"
    
    case "$status_code" in
        200)
            oauth2_log_success "MarkLogic configuration exists and is accessible"
            return 0
            ;;
        404)
            oauth2_log_error "MarkLogic configuration not found"
            return 1
            ;;
        401)
            oauth2_log_error "MarkLogic authentication failed"
            return 1
            ;;
        *)
            oauth2_log_error "MarkLogic configuration test failed (HTTP $status_code)"
            oauth2_log_debug "Response: $response_body"
            return 1
            ;;
    esac
}

# Test OAuth token against MarkLogic API
oauth2_test_token_against_marklogic() {
    local token="$1"
    local marklogic_host="$2"  
    local marklogic_port="${3:-8000}"
    local endpoint="${4:-/v1/documents}"
    local custom_url="${5:-}"
    
    local api_url
    local protocol
    
    # If custom URL is provided, use it directly
    if [ -n "$custom_url" ]; then
        api_url="$custom_url"
        # Extract protocol from custom URL for SSL handling
        if [[ "$custom_url" =~ ^https:// ]]; then
            protocol="https"
        else
            protocol="http"
        fi
    else
        # Use proper protocol from parsed host or default to http
        protocol="${MARKLOGIC_PROTOCOL:-http}"
        api_url="$protocol://$marklogic_host:$marklogic_port$endpoint"
    fi
    
    # Get appropriate curl flags for HTTPS
    local curl_flags=""
    if [ "$protocol" = "https" ] || [ "${MARKLOGIC_IS_HTTPS:-false}" = "true" ]; then
        curl_flags="--insecure"
    fi
    
    oauth2_log_debug "Testing token against MarkLogic API: $api_url"
    
    # Debug: Show the curl command that will be executed
    oauth2_log_info "🔍 DEBUG: Curl command being sent to MarkLogic:"
    oauth2_log_info "curl -s -w \"%{http_code}\" \\"
    if [ -n "$curl_flags" ]; then
        oauth2_log_info "  $curl_flags \\"
    fi
    oauth2_log_info "  -H \"Authorization: Bearer ${token:0:20}...\" \\"
    oauth2_log_info "  \"$api_url\""
    
    local response status_code
    response=$(curl -s -w "%{http_code}" \
        $curl_flags \
        -H "Authorization: Bearer $token" \
        "$api_url" 2>/dev/null)
    
    status_code="${response: -3}"
    response_body="${response%???}"
    
    # Debug: Show response details
    oauth2_log_info "🔍 DEBUG: MarkLogic response:"
    oauth2_log_info "  Status Code: $status_code"
    if [ -n "$response_body" ] && [ ${#response_body} -lt 500 ]; then
        oauth2_log_info "  Response Body: $response_body"
    elif [ -n "$response_body" ]; then
        oauth2_log_info "  Response Body (first 200 chars): ${response_body:0:200}..."
    else
        oauth2_log_info "  Response Body: (empty)"
    fi
    
    case "$status_code" in
        200)
            oauth2_log_success "Token validated successfully by MarkLogic"
            return 0
            ;;
        302)
            oauth2_log_success "Token accepted by MarkLogic (HTTP 302 redirect - OAuth working)"
            return 0
            ;;
        401)
            oauth2_log_warning "Token rejected by MarkLogic (401 Unauthorized)"
            return 1
            ;;
        403)
            oauth2_log_warning "Token accepted but access denied (403 Forbidden)"
            return 2
            ;;
        *)
            oauth2_log_warning "Unexpected response from MarkLogic API (HTTP $status_code)"
            oauth2_log_debug "Response: $response_body"
            return 3
            ;;
    esac
}

# ================================================================
# JWKS PROCESSING FUNCTIONS
# ================================================================

# Extract RSA public key components from JWKS
oauth2_jwks_get_rsa_key() {
    local jwks="$1"
    local key_id="${2:-}"
    
    local key_filter='.keys[] | select(.kty == "RSA")'
    
    if [ -n "$key_id" ]; then
        key_filter="$key_filter | select(.kid == \"$key_id\")"
    fi
    
    local key_data
    key_data=$(echo "$jwks" | jq "$key_filter | select(. != null)" | head -n 1)
    
    if [ -z "$key_data" ] || [ "$key_data" = "null" ]; then
        oauth2_log_error "No matching RSA key found in JWKS"
        return 1
    fi
    
    echo "$key_data"
}

# List all key IDs in JWKS
oauth2_jwks_list_key_ids() {
    local jwks="$1"
    
    echo "$jwks" | jq -r '.keys[].kid // "no-kid"' 2>/dev/null || {
        oauth2_log_error "Failed to extract key IDs from JWKS"
        return 1
    }
}

# Get key count from JWKS
oauth2_jwks_key_count() {
    local jwks="$1"
    
    echo "$jwks" | jq '.keys | length' 2>/dev/null || echo "0"
}

# ================================================================
# UTILITY HELPER FUNCTIONS
# ================================================================

# Generate random string for testing
oauth2_generate_random_string() {
    local length="${1:-32}"
    
    head /dev/urandom | tr -dc 'a-zA-Z0-9' | head -c "$length"
}

# Convert seconds to human readable time
oauth2_seconds_to_human() {
    local seconds="$1"
    
    if [ "$seconds" -lt 60 ]; then
        echo "${seconds}s"
    elif [ "$seconds" -lt 3600 ]; then
        echo "$((seconds / 60))m $((seconds % 60))s"
    else
        echo "$((seconds / 3600))h $((seconds % 3600 / 60))m $((seconds % 60))s"
    fi
}

# Check if port is open
oauth2_check_port() {
    local host="$1"
    local port="$2"
    local timeout="${3:-5}"
    
    if command -v nc >/dev/null 2>&1; then
        nc -z -w "$timeout" "$host" "$port" 2>/dev/null
    elif command -v telnet >/dev/null 2>&1; then
        timeout "$timeout" telnet "$host" "$port" </dev/null >/dev/null 2>&1
    else
        # Fallback using curl
        curl -s --connect-timeout "$timeout" "http://$host:$port" >/dev/null 2>&1
    fi
}

# Wait for service to be available
oauth2_wait_for_service() {
    local host="$1"
    local port="$2"
    local max_attempts="${3:-30}"
    local delay="${4:-2}"
    
    oauth2_log_info "Waiting for service at $host:$port..."
    
    local attempt=1
    while [ $attempt -le $max_attempts ]; do
        if oauth2_check_port "$host" "$port"; then
            oauth2_log_success "Service is available at $host:$port"
            return 0
        fi
        
        oauth2_log_debug "Attempt $attempt/$max_attempts - service not ready"
        sleep "$delay"
        ((attempt++))
    done
    
    oauth2_log_error "Service at $host:$port did not become available within $((max_attempts * delay)) seconds"
    return 1
}

# ================================================================
# INITIALIZATION
# ================================================================

# Check dependencies when library is loaded
if ! oauth2_check_dependencies; then
    oauth2_log_error "OAuth2 utilities library initialization failed"
    return 1
fi

oauth2_log_debug "OAuth2 utilities library loaded successfully"