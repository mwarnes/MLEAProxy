#!/bin/bash

# ================================================================
# OAuth2 Configuration Validation Script
# ================================================================
#
# This script validates OAuth2 configurations for MarkLogic
# and provides comprehensive testing of the authentication flow.
#
# Features:
# - Validate OAuth2 discovery endpoints
# - Test token generation and validation
# - Verify MarkLogic external security configuration
# - End-to-end authentication flow testing
# - Performance and reliability testing
#
# Author: MLEAProxy Development Team
# Version: 1.0.0
# Date: October 2025
#
# ================================================================

set -euo pipefail

# Load utility functions
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "DEBUG: SCRIPT_DIR=$SCRIPT_DIR" >&2
echo "DEBUG: Attempting to source $SCRIPT_DIR/oauth2-utils.sh" >&2
ls -la "$SCRIPT_DIR/oauth2-utils.sh" >&2 || echo "DEBUG: File not found!" >&2
source "$SCRIPT_DIR/oauth2-utils.sh" || {
    echo "ERROR: Could not load oauth2-utils.sh from $SCRIPT_DIR" >&2
    echo "ERROR: Current working directory: $(pwd)" >&2
    echo "ERROR: BASH_SOURCE[0]: ${BASH_SOURCE[0]}" >&2
    exit 1
}
echo "DEBUG: oauth2-utils.sh loaded successfully" >&2

# ================================================================
# CONFIGURATION
# ================================================================

# Default values
OAUTH_SERVER_URL=""
WELL_KNOWN_URL=""
MARKLOGIC_HOST="http://localhost"
MARKLOGIC_MANAGE_PORT="8002"
MARKLOGIC_USER="admin"
MARKLOGIC_PASS="admin"
APP_SERVER=""
CLIENT_ID="marklogic-oauth"
CLIENT_SECRET="4UZyJkjWsGV5JtpsWfgkL1qW5vZ5hhmv"
TEST_USERNAME="martin"
TEST_PASSWORD="L1tespeed1!?kc"
API_ENDPOINT_URL=""
VERBOSE="false"
PERFORMANCE_TEST="false"  
DETAILED_OUTPUT="false"
DECODE_TOKENS="true"
INSECURE="false"

# Test results tracking
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
WARNINGS=0
API_ENDPOINT_RESPONSE=""

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
    
    oauth2_log_debug "Parsed MarkLogic URL: protocol=$protocol, host=$host, port=$port, is_https=$MARKLOGIC_IS_HTTPS"
}

# Get curl flags for SSL handling
get_curl_flags() {
    local flags=""
    if [ "$INSECURE" = "true" ]; then
        flags="$flags --insecure"
    fi
    echo "$flags"
}

# Get curl flags for MarkLogic requests (includes HTTPS handling)
get_marklogic_curl_flags() {
    local flags=""
    if [ "$INSECURE" = "true" ]; then
        flags="$flags --insecure"
    elif [ "$MARKLOGIC_IS_HTTPS" = "true" ]; then
        # Automatically add -k flag for HTTPS MarkLogic hosts
        flags="$flags --insecure"
    fi
    echo "$flags"
}

# Decode and display token information
decode_and_display_token() {
    local token="$1"
    local token_type="${2:-Access Token}"
    
    if [ -z "$token" ]; then
        oauth2_log_warning "No token provided for decoding"
        return 1
    fi
    
    # Skip decoding if disabled
    if [ "$DECODE_TOKENS" != "true" ]; then
        oauth2_log_info "$token_type received (decoding disabled)"
        return 0
    fi
    
    oauth2_log_info "=== $token_type Details ==="
    
    # Validate JWT structure
    if ! oauth2_validate_jwt "$token"; then
        oauth2_log_error "Invalid JWT structure"
        return 1
    fi
    
    # Decode header and payload
    local header payload
    header=$(oauth2_jwt_decode_header "$token")
    payload=$(oauth2_jwt_decode_payload "$token")
    
    if [ -z "$header" ] || [ -z "$payload" ]; then
        oauth2_log_error "Failed to decode JWT"
        return 1
    fi
    
    # Display header information
    oauth2_log_info "📋 JWT Header:"
    local alg typ kid
    alg=$(echo "$header" | jq -r '.alg // "unknown"')
    typ=$(echo "$header" | jq -r '.typ // "unknown"')
    kid=$(echo "$header" | jq -r '.kid // "unknown"')
    
    oauth2_log_info "  • Algorithm: $alg"
    oauth2_log_info "  • Type: $typ"
    if [ "$kid" != "unknown" ]; then
        oauth2_log_info "  • Key ID: $kid"
    fi
    
    # Display payload information
    oauth2_log_info "📋 JWT Payload:"
    local iss sub aud exp iat nbf jti scope client_id username preferred_username email realm_access resource_access roles
    iss=$(echo "$payload" | jq -r '.iss // "unknown"')
    sub=$(echo "$payload" | jq -r '.sub // "unknown"')
    aud=$(echo "$payload" | jq -r '.aud // "unknown"')
    exp=$(echo "$payload" | jq -r '.exp // "unknown"')
    iat=$(echo "$payload" | jq -r '.iat // "unknown"')
    nbf=$(echo "$payload" | jq -r '.nbf // "unknown"')
    jti=$(echo "$payload" | jq -r '.jti // "unknown"')
    scope=$(echo "$payload" | jq -r '.scope // "unknown"')
    client_id=$(echo "$payload" | jq -r '.client_id // .clientId // "unknown"')
    username=$(echo "$payload" | jq -r '.username // "unknown"')
    preferred_username=$(echo "$payload" | jq -r '.preferred_username // "unknown"')
    email=$(echo "$payload" | jq -r '.email // "unknown"')
    realm_access=$(echo "$payload" | jq -r '.realm_access.roles // empty' 2>/dev/null)
    resource_access=$(echo "$payload" | jq -r '.resource_access // empty' 2>/dev/null)
    roles=$(echo "$payload" | jq -r '.roles // empty' 2>/dev/null)
    marklogic_roles=$(echo "$payload" | jq -r '."marklogic-roles" // empty' 2>/dev/null)
    
    oauth2_log_info "  • Issuer: $iss"
    oauth2_log_info "  • Subject: $sub"
    oauth2_log_info "  • Audience: $aud"
    
    if [ "$client_id" != "unknown" ]; then
        oauth2_log_info "  • Client ID: $client_id"
    fi
    
    if [ "$scope" != "unknown" ]; then
        oauth2_log_info "  • Scope: $scope"
    fi
    
    if [ "$username" != "unknown" ]; then
        oauth2_log_info "  • Username: $username"
    fi
    
    if [ "$preferred_username" != "unknown" ]; then
        oauth2_log_info "  • Preferred Username: $preferred_username"
    fi
    
    if [ "$email" != "unknown" ]; then
        oauth2_log_info "  • Email: $email"
    fi
    
    if [ "$jti" != "unknown" ]; then
        oauth2_log_info "  • JWT ID: $jti"
    fi
    
    # Display role information
    if [ -n "$realm_access" ]; then
        oauth2_log_info "🔐 Realm Roles:"
        echo "$realm_access" | jq -r '.[]' 2>/dev/null | sed 's/^/    • /' || oauth2_log_info "    • None"
    fi
    
    if [ -n "$resource_access" ]; then
        oauth2_log_info "🔐 Resource Roles:"
        echo "$resource_access" | jq -r 'to_entries[] | "  \(.key): \(.value.roles | join(", "))"' 2>/dev/null | sed 's/^/    • /' || oauth2_log_info "    • None"
    fi
    
    if [ -n "$marklogic_roles" ]; then
        oauth2_log_info "🔐 MarkLogic Roles:"
        echo "$marklogic_roles" | jq -r '.[]' 2>/dev/null | sed 's/^/    • /' || oauth2_log_info "    • None"
    elif [ -n "$roles" ]; then
        oauth2_log_info "🔐 Custom Roles:"
        echo "$roles" | jq -r '.[]' 2>/dev/null | sed 's/^/    • /' || oauth2_log_info "    • None"
    fi
    
    # Display time-related claims
    oauth2_log_info "📅 Time Claims:"
    local current_time
    current_time=$(date +%s)
    
    if [ "$iat" != "unknown" ]; then
        local iat_date
        iat_date=$(date -r "$iat" 2>/dev/null || echo "invalid")
        oauth2_log_info "  • Issued At: $iat_date ($iat)"
    fi
    
    if [ "$nbf" != "unknown" ]; then
        local nbf_date
        nbf_date=$(date -r "$nbf" 2>/dev/null || echo "invalid")
        oauth2_log_info "  • Not Before: $nbf_date ($nbf)"
    fi
    
    if [ "$exp" != "unknown" ]; then
        local exp_date time_to_expiry
        exp_date=$(date -r "$exp" 2>/dev/null || echo "invalid")
        time_to_expiry=$((exp - current_time))
        
        oauth2_log_info "  • Expires At: $exp_date ($exp)"
        
        if [ $time_to_expiry -lt 0 ]; then
            oauth2_log_warning "  • Status: EXPIRED ($((-time_to_expiry)) seconds ago)"
        else
            oauth2_log_info "  • Status: Valid (expires in $(oauth2_seconds_to_human "$time_to_expiry"))"
        fi
    fi
    
    # Display custom claims if verbose mode
    if [ "$VERBOSE" = "true" ]; then
        oauth2_log_info "📋 All Claims:"
        echo "$payload" | jq . | sed 's/^/    /'
    fi
    
    echo
}

# ================================================================
# LOGGING AND REPORTING
# ================================================================

log_test_start() {
    ((TOTAL_TESTS++))
    oauth2_log_info "🧪 TEST $TOTAL_TESTS: $1"
}

log_test_pass() {
    ((PASSED_TESTS++))
    oauth2_log_success "✅ PASS: $1"
}

log_test_fail() {
    ((FAILED_TESTS++))
    oauth2_log_error "❌ FAIL: $1"
}

log_test_warning() {
    ((WARNINGS++))
    oauth2_log_warning "⚠️  WARNING: $1"
}

show_usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Validates OAuth2 configuration for MarkLogic integration.

OPTIONS:
    --well-known-url URL          OAuth2 .well-known discovery endpoint URL (required)
    --marklogic-host URL          MarkLogic host URL (default: http://localhost)
    --marklogic-manage-port PORT  MarkLogic manage port (default: 8002)
    --marklogic-user USER         MarkLogic admin user (default: admin)
    --marklogic-pass PASS         MarkLogic admin password (default: admin)
    --app-server NAME             App server name to test OAuth against (required)
    --client-id ID                OAuth client ID (default: marklogic)
    --client-secret SECRET        OAuth client secret (default: secret)
    --test-username USER          Username for password grant test (default: admin)
    --test-password PASS          Password for password grant test (default: admin)
    --api-endpoint-url URL        Custom API endpoint URL for authentication testing
    --performance                 Run performance tests
    --detailed                    Show detailed test output
    --decode-tokens               Decode and display token contents (default: enabled)
    --no-decode-tokens            Skip token decoding and display
    --verbose                     Enable verbose logging
    --insecure                    Skip SSL certificate verification
    --help                        Show this help message

EXAMPLES:
    # Validate OAuth-configured app server
    $0 --well-known-url http://localhost:8080/oauth/.well-known/config --app-server Manage2

    # Validate production OAuth2 setup
    $0 --well-known-url https://auth.example.com/.well-known/openid_configuration \\
       --app-server ProductionApp \\
       --marklogic-host marklogic.example.com \\
       --performance --detailed

ENVIRONMENT VARIABLES:
    OAUTH2_DEBUG                  Enable debug logging
    MARKLOGIC_HOST               Override MarkLogic host
    MARKLOGIC_USER               Override MarkLogic user
    MARKLOGIC_PASS               Override MarkLogic password

EOF
}

# ================================================================
# VALIDATION TESTS
# ================================================================

# Test 1: OAuth2 Server Connectivity
test_oauth_server_connectivity() {
    log_test_start "OAuth2 Server Connectivity"
    
    # Extract server info from well-known URL
    local server_host server_port
    server_host=$(echo "$WELL_KNOWN_URL" | sed 's#.*://##' | cut -d: -f1 | cut -d/ -f1)
    server_port=$(echo "$WELL_KNOWN_URL" | sed 's#.*://##' | cut -d: -f2 | cut -d/ -f1)
    
    # If no port specified, use default (80 for HTTP, 443 for HTTPS)
    if [ "$server_port" = "$server_host" ]; then
        if [[ "$WELL_KNOWN_URL" =~ ^https:// ]]; then
            server_port="443"
        else
            server_port="80"
        fi
    fi
    
    # Test basic connectivity
    if oauth2_check_port "$server_host" "$server_port"; then
        log_test_pass "OAuth2 server is reachable ($server_host:$server_port)"
    else
        log_test_fail "OAuth2 server is not reachable ($server_host:$server_port)"
        return 1
    fi
    
    # Test well-known endpoint directly
    local response
    local curl_flags
    curl_flags=$(get_curl_flags)
    if response=$(curl -s -m 10 $curl_flags "$WELL_KNOWN_URL" 2>/dev/null); then
        log_test_pass "OAuth2 well-known endpoint responds"
        if [ "$VERBOSE" = "true" ]; then
            oauth2_log_debug "Endpoint response preview: $(echo "$response" | head -c 200)..."
        fi
    else
        log_test_fail "OAuth2 well-known endpoint does not respond"
        return 1
    fi
}

# Test 2: OAuth2 Discovery Endpoint
test_oauth_discovery_endpoints() {
    log_test_start "OAuth2 Discovery Endpoint"
    
    # Test the explicitly provided well-known endpoint
    oauth2_log_info "Testing well-known endpoint: $WELL_KNOWN_URL"
    
    local curl_flags
    curl_flags=$(get_curl_flags)
    if config=$(curl -s -m 10 $curl_flags "$WELL_KNOWN_URL" 2>/dev/null); then
        # Validate it's valid JSON
        if echo "$config" | jq . >/dev/null 2>&1; then
            log_test_pass "OAuth2 configuration retrieved successfully"
            
            # Validate configuration structure
            local issuer token_endpoint jwks_uri
            issuer=$(echo "$config" | jq -r '.issuer // empty')
            token_endpoint=$(echo "$config" | jq -r '.token_endpoint // empty')
            jwks_uri=$(echo "$config" | jq -r '.jwks_uri // empty')
            
            oauth2_log_info "Issuer: $issuer"
            oauth2_log_info "Token Endpoint: $token_endpoint"
            oauth2_log_info "JWKS URI: $jwks_uri"
            
            if [ "$DETAILED_OUTPUT" = "true" ]; then
                oauth2_log_info "Full configuration:"
                echo "$config" | jq .
            fi
            
            # Store for later tests
            export OAUTH_CONFIG="$config"
            export OAUTH_ISSUER="$issuer"
            export OAUTH_TOKEN_ENDPOINT="$token_endpoint"
            export OAUTH_JWKS_URI="$jwks_uri"
        else
            log_test_fail "OAuth2 endpoint returned invalid JSON"
            return 1
        fi
    else
        log_test_fail "Failed to retrieve OAuth2 configuration from $WELL_KNOWN_URL"
        return 1
    fi
    
    # Test JWKS endpoint if available
    if [ -n "$OAUTH_JWKS_URI" ]; then
        oauth2_log_info "Testing JWKS endpoint..."
        
        # Pass insecure flag to oauth2_fetch_jwks if needed
        local jwks_flags=""
        if [ "$INSECURE" = "true" ]; then
            jwks_flags="--insecure"
        fi
        if jwks=$(oauth2_fetch_jwks "$OAUTH_JWKS_URI" "$jwks_flags"); then
            log_test_pass "JWKS endpoint is accessible"
            
            local key_count
            key_count=$(oauth2_jwks_key_count "$jwks")
            oauth2_log_info "JWKS contains $key_count keys"
            
            # List key IDs
            if [ "$VERBOSE" = "true" ]; then
                oauth2_log_info "Key IDs:"
                oauth2_jwks_list_key_ids "$jwks"
            fi
            
            export OAUTH_JWKS="$jwks"
        else
            log_test_warning "JWKS endpoint is not accessible"
        fi
    fi
}

# Test 3: Token Generation
test_token_generation() {
    log_test_start "OAuth2 Token Generation"
    
    if [ -z "$OAUTH_TOKEN_ENDPOINT" ]; then
        log_test_fail "No token endpoint available for testing"
        return 1
    fi
    
    # Test client credentials flow
    oauth2_log_info "Testing client credentials flow..."
    
    local token_flags=""
    if [ "$INSECURE" = "true" ]; then
        token_flags="--insecure"
    fi
    
    if access_token=$(oauth2_get_token_client_credentials "$OAUTH_TOKEN_ENDPOINT" "$CLIENT_ID" "$CLIENT_SECRET" "openid" "$token_flags"); then
        log_test_pass "Client credentials flow successful"
        
        # Validate token structure
        if oauth2_validate_jwt "$access_token"; then
            log_test_pass "Generated token has valid JWT structure"
            
            # Decode and display comprehensive token information
            decode_and_display_token "$access_token" "Client Credentials Token"
            
            export TEST_ACCESS_TOKEN="$access_token"
        else
            log_test_fail "Generated token has invalid JWT structure"
        fi
    else
        log_test_fail "Client credentials flow failed"
    fi
    
    # Test password flow (optional)
    oauth2_log_info "Testing password flow..."
    oauth2_log_info "Using username: '$TEST_USERNAME' (configure with --test-username)"
    
    if password_token=$(oauth2_get_token_password "$OAUTH_TOKEN_ENDPOINT" "$TEST_USERNAME" "$TEST_PASSWORD" "$CLIENT_ID" "$CLIENT_SECRET" "openid" "$token_flags"); then
        log_test_pass "Password flow successful"
        
        # Decode and display password token information
        decode_and_display_token "$password_token" "Password Grant Token"
        
        # Store password token for end-to-end testing
        export TEST_PASSWORD_TOKEN="$password_token"
    else
        log_test_warning "Password flow failed for user '$TEST_USERNAME'"
        oauth2_log_info "💡 To fix this:"
        oauth2_log_info "   1. Verify user '$TEST_USERNAME' exists in your OAuth server (Keycloak)"
        oauth2_log_info "   2. Check the password is correct"
        oauth2_log_info "   3. Ensure password flow is enabled for client '$CLIENT_ID'"
        oauth2_log_info "   4. Verify user account is enabled and not locked"
        oauth2_log_info "   5. Use different credentials: --test-username USER --test-password PASS"
    fi
}

# Test 4: MarkLogic Configuration
test_marklogic_configuration() {
    log_test_start "MarkLogic Configuration"
    
    if [ -z "$APP_SERVER" ]; then
        log_test_fail "No app server provided for MarkLogic testing"
        return 1
    fi
    
    # MarkLogic host already parsed in main()
    # Test MarkLogic connectivity
    oauth2_log_info "Testing MarkLogic connectivity..."
    
    local manage_port_reachable=false
    if oauth2_check_port "$MARKLOGIC_HOST_ONLY" "$MARKLOGIC_MANAGE_PORT"; then
        log_test_pass "MarkLogic manage port is reachable"
        manage_port_reachable=true
    else
        log_test_warning "MarkLogic manage port is not reachable - some tests will be limited"
        log_test_warning "This may be expected in environments where manage port is not accessible"
    fi
    
    # Initialize MARKLOGIC_API_PORT (will be set from app server config or default)
    MARKLOGIC_API_PORT=""
    
    # Only try to get app server configuration if manage port is reachable
    if [ "$manage_port_reachable" = "true" ]; then
        # Get app server configuration to determine port and authentication settings
        oauth2_log_info "Getting app server configuration to determine port..."
        
        # First check if app server exists
        local app_server_url="$MARKLOGIC_PROTOCOL://$MARKLOGIC_HOST_ONLY:$MARKLOGIC_MANAGE_PORT/manage/v2/servers/$APP_SERVER"
        local server_response
        local marklogic_curl_flags
        marklogic_curl_flags=$(get_marklogic_curl_flags)
        
        if server_response=$(curl -s --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" -H "Accept: application/json" $marklogic_curl_flags "$app_server_url?group-id=Default" 2>/dev/null); then
        log_test_pass "App server '$APP_SERVER' exists"
        
        # Get detailed properties to find port and authentication settings
        local properties_url="$MARKLOGIC_PROTOCOL://$MARKLOGIC_HOST_ONLY:$MARKLOGIC_MANAGE_PORT/manage/v2/servers/$APP_SERVER/properties"
        local properties_response
        
        if properties_response=$(curl -s --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" -H "Accept: application/json" $marklogic_curl_flags "$properties_url?group-id=Default" 2>/dev/null); then
            # Get the actual port from the properties
            MARKLOGIC_API_PORT=$(echo "$properties_response" | jq -r '.port // "unknown"')
            
            if [ "$MARKLOGIC_API_PORT" != "unknown" ] && [ "$MARKLOGIC_API_PORT" != "null" ]; then
                oauth2_log_info "App server port: $MARKLOGIC_API_PORT"
                export MARKLOGIC_API_PORT
            else
                log_test_fail "Could not determine app server port from configuration"
                return 1
            fi
                
                # Check if OAuth is configured
                local auth_method external_auth
                auth_method=$(echo "$properties_response" | jq -r '.authentication // "unknown"')
                external_auth=$(echo "$properties_response" | jq -r '.["external-security"] // "none"')
                
                oauth2_log_info "App server authentication: $auth_method"
                oauth2_log_info "External security: $external_auth"
                
                if [ "$auth_method" = "oauth" ] || ([ "$auth_method" = "application-level" ] && [ "$external_auth" != "none" ]); then
                    log_test_pass "App server is configured for OAuth authentication"
                    
                    # Extract external security configuration name from app server config
                    if [ "$external_auth" != "none" ] && [ "$external_auth" != "null" ]; then
                        local config_name
                        config_name=$(echo "$external_auth" | jq -r '.[0] // empty' 2>/dev/null || echo "$external_auth")
                        if [ -n "$config_name" ] && [ "$config_name" != "null" ]; then
                            oauth2_log_info "External security configuration: $config_name"
                            
                            # Test the external security configuration
                            oauth2_log_info "Testing external security configuration..."
                            
                            if oauth2_test_marklogic_config "$MARKLOGIC_HOST" "$MARKLOGIC_MANAGE_PORT" "$config_name" "$MARKLOGIC_USER" "$MARKLOGIC_PASS"; then
                                log_test_pass "MarkLogic external security configuration exists"
                                
                                # Get configuration details
                                local config_url="$MARKLOGIC_PROTOCOL://$MARKLOGIC_HOST_ONLY:$MARKLOGIC_MANAGE_PORT/manage/v2/external-security/$config_name"
                                local config_response
                                
                                if config_response=$(curl -s --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" -H "Accept: application/json" $marklogic_curl_flags "$config_url" 2>/dev/null); then
                                    local ext_auth_method cache_timeout
                                    ext_auth_method=$(echo "$config_response" | jq -r '.["external-security-config"] | .authentication // "unknown"')
                                    cache_timeout=$(echo "$config_response" | jq -r '.["external-security-config"] | .["cache-timeout"] // "unknown"')
                                    
                                    oauth2_log_info "Authentication method: $ext_auth_method"
                                    oauth2_log_info "Cache timeout: $cache_timeout seconds"
                                    
                                    if [ "$DETAILED_OUTPUT" = "true" ]; then
                                        oauth2_log_info "Full external security configuration:"
                                        echo "$config_response" | jq .
                                    fi
                                fi
                            else
                                log_test_fail "MarkLogic external security configuration not accessible"
                            fi
                        fi
                    fi
                elif [ "$auth_method" = "application-level" ]; then
                    log_test_warning "App server uses application-level auth but no external security configured"
                else
                    log_test_warning "App server may not be properly configured for OAuth (auth: $auth_method, external: $external_auth)"
                fi
                
                if [ "$DETAILED_OUTPUT" = "true" ]; then
                    oauth2_log_info "Full app server properties:"
                    echo "$properties_response" | jq .
                fi
            else
                log_test_fail "Could not retrieve app server properties"
                return 1
            fi
        else
            log_test_fail "App server '$APP_SERVER' not accessible"
            return 1
        fi
    else
        # Manage port not reachable - use default port for testing
        log_test_warning "Manage port not accessible - using default app server port for testing"
        
        # Try common MarkLogic app server ports
        local default_ports=("8000" "8080" "8010" "8020")
        local port_found=false
        
        for port in "${default_ports[@]}"; do
            if oauth2_check_port "$MARKLOGIC_HOST_ONLY" "$port"; then
                MARKLOGIC_API_PORT="$port"
                oauth2_log_info "Found accessible port: $port (assuming this is the OAuth-configured app server)"
                export MARKLOGIC_API_PORT
                port_found=true
                break
            fi
        done
        
        if [ "$port_found" = "false" ]; then
            log_test_warning "No common app server ports are reachable - end-to-end testing may not work"
            MARKLOGIC_API_PORT="8000"  # Default fallback
            export MARKLOGIC_API_PORT
        fi
    fi
    
    # Test MarkLogic OAuth-configured app server port
    oauth2_log_info "Testing MarkLogic OAuth app server port..."
    
    if oauth2_check_port "$MARKLOGIC_HOST_ONLY" "$MARKLOGIC_API_PORT"; then
        log_test_pass "MarkLogic OAuth app server port is reachable (port $MARKLOGIC_API_PORT)"
    else
        log_test_warning "MarkLogic OAuth app server port is not reachable (may affect token testing)"
    fi
}

# Helper function to test custom API endpoint and capture response
test_custom_api_endpoint() {
    local token="$1"
    local endpoint_url="$2"
    
    if [ -z "$endpoint_url" ]; then
        return 1
    fi
    
    oauth2_log_info "🌐 Testing custom API endpoint: $endpoint_url"
    
    # Get appropriate curl flags for HTTPS
    local curl_flags=""
    if [[ "$endpoint_url" =~ ^https:// ]]; then
        curl_flags="--insecure"
    fi
    
    # Debug: Show the curl command
    oauth2_log_info "🔍 DEBUG: Custom endpoint curl command:"
    oauth2_log_info "curl -s -w \"%{http_code}\" \\"
    if [ -n "$curl_flags" ]; then
        oauth2_log_info "  $curl_flags \\"
    fi
    oauth2_log_info "  -H \"Authorization: Bearer ${token:0:20}...\" \\"
    oauth2_log_info "  \"$endpoint_url\""
    
    local response status_code response_body
    response=$(curl -s -w "%{http_code}" \
        $curl_flags \
        -H "Authorization: Bearer $token" \
        "$endpoint_url" 2>/dev/null)
    
    status_code="${response: -3}"
    response_body="${response%???}"
    
    # Store response for final report
    export API_ENDPOINT_RESPONSE="$response_body"
    
    # Debug: Show response details
    oauth2_log_info "🔍 DEBUG: Custom endpoint response:"
    oauth2_log_info "  Status Code: $status_code"
    if [ -n "$response_body" ]; then
        if [ ${#response_body} -lt 500 ]; then
            oauth2_log_info "  Response Body: $response_body"
        else
            oauth2_log_info "  Response Body (first 200 chars): ${response_body:0:200}..."
        fi
    else
        oauth2_log_info "  Response Body: (empty)"
    fi
    
    case "$status_code" in
        200)
            oauth2_log_success "✅ Custom API endpoint test successful (HTTP 200)"
            return 0
            ;;
        302)
            oauth2_log_success "✅ Custom API endpoint test successful (HTTP 302 redirect)"
            return 0
            ;;
        401)
            oauth2_log_warning "❌ Custom API endpoint rejected token (HTTP 401)"
            return 1
            ;;
        403)
            oauth2_log_warning "⚠️  Custom API endpoint accepted token but access denied (HTTP 403)"
            return 2
            ;;
        *)
            oauth2_log_warning "⚠️  Custom API endpoint returned unexpected response (HTTP $status_code)"
            return 3
            ;;
    esac
}

# Test 5: End-to-End Token Validation
test_end_to_end_validation() {
    log_test_start "End-to-End Token Validation"
    
    # Test password flow token first (preferred for end-to-end testing)
    if [ -n "${TEST_PASSWORD_TOKEN:-}" ]; then
        oauth2_log_info "🎯 Testing with password flow token (represents real user authentication)"
        # Get the username and roles from the password token
        local password_token_user password_token_roles
        password_token_user=$(oauth2_jwt_get_claim "$TEST_PASSWORD_TOKEN" "preferred_username" 2>/dev/null || oauth2_jwt_get_claim "$TEST_PASSWORD_TOKEN" "username" 2>/dev/null || oauth2_jwt_get_claim "$TEST_PASSWORD_TOKEN" "sub" 2>/dev/null || echo "unknown")
        # Extract roles - check marklogic-roles first, then fall back to other role claims
        local marklogic_roles_claim standard_roles_claim realm_roles_claim
        marklogic_roles_claim=$(oauth2_jwt_decode_payload "$TEST_PASSWORD_TOKEN" | jq -r '."marklogic-roles" // empty' 2>/dev/null)
        standard_roles_claim=$(oauth2_jwt_get_claim "$TEST_PASSWORD_TOKEN" "roles" 2>/dev/null)
        realm_roles_claim=$(oauth2_jwt_get_claim "$TEST_PASSWORD_TOKEN" "realm_access.roles" 2>/dev/null)
        
        # Use the first non-empty roles claim
        if [ -n "$marklogic_roles_claim" ] && [ "$marklogic_roles_claim" != "null" ]; then
            password_token_roles="$marklogic_roles_claim"
        elif [ -n "$standard_roles_claim" ] && [ "$standard_roles_claim" != "null" ]; then
            password_token_roles="$standard_roles_claim"
        elif [ -n "$realm_roles_claim" ] && [ "$realm_roles_claim" != "null" ]; then
            password_token_roles="$realm_roles_claim"
        else
            password_token_roles=""
        fi
        
        oauth2_log_info "Testing password flow token against MarkLogic OAuth-configured app server (port $MARKLOGIC_API_PORT)..."
        
        # Decode and display the JWT token like jwt.io
        oauth2_log_info "=== JWT Token Decoded (jwt.io style) ==="
        
        # Decode header
        local jwt_header jwt_header_formatted
        jwt_header=$(oauth2_jwt_decode_header "$TEST_PASSWORD_TOKEN")
        if [ -n "$jwt_header" ]; then
            oauth2_log_info "📋 JWT Header:"
            jwt_header_formatted=$(echo "$jwt_header" | jq . 2>/dev/null || echo "$jwt_header")
            while IFS= read -r line; do
                oauth2_log_info "$line"
            done <<< "$jwt_header_formatted"
        fi
        
        # Decode payload  
        local jwt_payload jwt_payload_formatted
        jwt_payload=$(oauth2_jwt_decode_payload "$TEST_PASSWORD_TOKEN")
        if [ -n "$jwt_payload" ]; then
            oauth2_log_info "📋 JWT Payload:"
            jwt_payload_formatted=$(echo "$jwt_payload" | jq . 2>/dev/null || echo "$jwt_payload")
            while IFS= read -r line; do
                oauth2_log_info "$line"
            done <<< "$jwt_payload_formatted"
        fi
        
        oauth2_log_info "Token user: $password_token_user"
        
        # Display roles if available
        if [ -n "$password_token_roles" ] && [ "$password_token_roles" != "null" ] && [ "$password_token_roles" != "" ]; then
            # Format roles nicely if it's a JSON array
            if echo "$password_token_roles" | jq -e 'type == "array"' >/dev/null 2>&1; then
                oauth2_log_info "Token roles:"
                local role_list
                role_list=$(echo "$password_token_roles" | jq -r '.[]' 2>/dev/null | sed 's/^/    • /')
                echo "$role_list"
            else
                oauth2_log_info "Token roles: $password_token_roles"
            fi
        else
            oauth2_log_info "Token roles: none found (may be in custom claims or external system)"
        fi
        
        local result
        oauth2_test_token_against_marklogic "$TEST_PASSWORD_TOKEN" "$MARKLOGIC_HOST_ONLY" "$MARKLOGIC_API_PORT" "/" ""
        result=$?
        
        case $result in
            0)
                log_test_pass "Password flow token validated successfully by MarkLogic (user: $password_token_user)"
                ;;
            1)
                log_test_fail "Password flow token rejected by MarkLogic - check external security configuration"
                ;;
            2)
                log_test_pass "OAuth integration working - password token accepted by MarkLogic!"
                log_test_warning "User '$password_token_user' authenticated but lacks sufficient permissions (HTTP 403)"
                oauth2_log_info "✅ This confirms OAuth2 external security is working correctly"
                oauth2_log_info "💡 To fix permissions: assign roles to user '$password_token_user' in MarkLogic"
                ;;
            3)
                log_test_warning "Unexpected response from MarkLogic API with password flow token"
                ;;
        esac
        
        # Test custom API endpoint if provided
        if [ -n "$API_ENDPOINT_URL" ]; then
            oauth2_log_info ""
            oauth2_log_info "🔗 Testing custom API endpoint with password flow token..."
            
            local api_result
            test_custom_api_endpoint "$TEST_PASSWORD_TOKEN" "$API_ENDPOINT_URL"
            api_result=$?
            
            case $api_result in
                0)
                    log_test_pass "Custom API endpoint test successful with password flow token"
                    ;;
                1)
                    log_test_fail "Custom API endpoint rejected password flow token"
                    ;;
                2)
                    log_test_pass "Custom API endpoint accepted password flow token but access denied"
                    oauth2_log_info "✅ This confirms OAuth2 authentication is working for the custom endpoint"
                    ;;
                3)
                    log_test_warning "Custom API endpoint returned unexpected response"
                    ;;
            esac
        fi
    else
        oauth2_log_warning "No password flow token available - falling back to client credentials token"
        
        # Test client credentials token against MarkLogic API as fallback
        if [ -n "${TEST_ACCESS_TOKEN:-}" ]; then
            oauth2_log_info "🎯 Testing with client credentials token (service account)"
            
            local client_token_user
            client_token_user=$(oauth2_jwt_get_claim "$TEST_ACCESS_TOKEN" "preferred_username" 2>/dev/null || oauth2_jwt_get_claim "$TEST_ACCESS_TOKEN" "sub" 2>/dev/null || echo "service-account")
            oauth2_log_info "Token user: $client_token_user"
            
            local result
            oauth2_test_token_against_marklogic "$TEST_ACCESS_TOKEN" "$MARKLOGIC_HOST_ONLY" "$MARKLOGIC_API_PORT" "/" ""
            result=$?
            
            case $result in
                0)
                    log_test_pass "Client credentials token validated successfully by MarkLogic (user: $client_token_user)"
                    oauth2_log_info "ℹ️  Note: This validates OAuth integration but not user-specific authentication"
                    ;;
                1)
                    log_test_fail "Client credentials token rejected by MarkLogic - check external security configuration"
                    ;;
                2)
                    log_test_pass "OAuth integration working - token accepted by MarkLogic!"
                    log_test_warning "Service account '$client_token_user' authenticated but lacks sufficient permissions (HTTP 403)"
                    oauth2_log_info "✅ This confirms OAuth2 external security is working correctly"
                    oauth2_log_info "💡 To fix permissions: assign roles to the service account in MarkLogic"
                    ;;
                3)
                    log_test_warning "Unexpected response from MarkLogic API with client credentials token"
                    ;;
            esac
            
            # Test custom API endpoint if provided
            if [ -n "$API_ENDPOINT_URL" ]; then
                oauth2_log_info ""
                oauth2_log_info "🔗 Testing custom API endpoint with client credentials token..."
                
                local api_result
                test_custom_api_endpoint "$TEST_ACCESS_TOKEN" "$API_ENDPOINT_URL"
                api_result=$?
                
                case $api_result in
                    0)
                        log_test_pass "Custom API endpoint test successful with client credentials token"
                        ;;
                    1)
                        log_test_fail "Custom API endpoint rejected client credentials token"
                        ;;
                    2)
                        log_test_pass "Custom API endpoint accepted client credentials token but access denied"
                        oauth2_log_info "✅ This confirms OAuth2 authentication is working for the custom endpoint"
                        ;;
                    3)
                        log_test_warning "Custom API endpoint returned unexpected response"
                        ;;
                esac
            fi
        else
            log_test_fail "No tokens available for end-to-end testing"
            log_test_fail "Both password flow and client credentials flow failed"
            return 1
        fi
    fi
    
    # Test different API endpoints with password flow token
    if [ "$PERFORMANCE_TEST" = "true" ]; then
        if [ -n "${TEST_PASSWORD_TOKEN:-}" ]; then
            oauth2_log_info "Testing multiple endpoints on OAuth-configured app server with password flow token..."
            
            local endpoints=("/" "/error-handler.xqy" "/rewriter.xml")
            local successful_endpoints=0
            
            for endpoint in "${endpoints[@]}"; do
                if oauth2_test_token_against_marklogic "$TEST_PASSWORD_TOKEN" "$MARKLOGIC_HOST_ONLY" "$MARKLOGIC_API_PORT" "$endpoint" "" >/dev/null 2>&1; then
                    ((successful_endpoints++))
                fi
            done
            
            oauth2_log_info "Password flow token worked with $successful_endpoints/${#endpoints[@]} endpoints"
        else
            oauth2_log_info "Skipping endpoint testing - no password flow token available"
        fi
    fi
}

# Test 6: Performance Testing
test_performance() {
    if [ "$PERFORMANCE_TEST" != "true" ]; then
        return 0
    fi
    
    log_test_start "Performance Testing"
    
    # Test token generation performance
    oauth2_log_info "Testing token generation performance..."
    
    local iterations=10
    local start_time end_time total_time
    start_time=$(date +%s.%N)
    
    local perf_token_flags=""
    if [ "$INSECURE" = "true" ]; then
        perf_token_flags="--insecure"
    fi
    
    for ((i=1; i<=iterations; i++)); do
        oauth2_get_token_client_credentials "$OAUTH_TOKEN_ENDPOINT" "$CLIENT_ID" "$CLIENT_SECRET" "openid" "$perf_token_flags" >/dev/null 2>&1 || {
            log_test_warning "Token generation failed on iteration $i"
        }
    done
    
    end_time=$(date +%s.%N)
    total_time=$(echo "$end_time - $start_time" | bc -l)
    avg_time=$(echo "scale=3; $total_time / $iterations" | bc -l)
    
    oauth2_log_info "Generated $iterations tokens in ${total_time%.*} seconds"
    oauth2_log_info "Average time per token: ${avg_time} seconds"
    
    if (( $(echo "$avg_time < 1.0" | bc -l) )); then
        log_test_pass "Token generation performance is good (<1s per token)"
    else
        log_test_warning "Token generation is slow (>1s per token)"
    fi
    
    # Test concurrent token validation
    if [ -n "${TEST_ACCESS_TOKEN:-}" ]; then
        oauth2_log_info "Testing concurrent API requests..."
        
        local concurrent_requests=5
        local pids=()
        
        start_time=$(date +%s.%N)
        
        for ((i=1; i<=concurrent_requests; i++)); do
            (oauth2_test_token_against_marklogic "$TEST_ACCESS_TOKEN" "$MARKLOGIC_HOST_ONLY" "$MARKLOGIC_API_PORT" "/" "" >/dev/null 2>&1) &
            pids+=($!)
        done
        
        # Wait for all requests to complete
        for pid in "${pids[@]}"; do
            wait "$pid"
        done
        
        end_time=$(date +%s.%N)
        total_time=$(echo "$end_time - $start_time" | bc -l)
        
        oauth2_log_info "Completed $concurrent_requests concurrent requests in ${total_time%.*} seconds"
        
        if (( $(echo "$total_time < 5.0" | bc -l) )); then
            log_test_pass "Concurrent request performance is good (<5s for $concurrent_requests requests)"
        else
            log_test_warning "Concurrent requests are slow (>5s for $concurrent_requests requests)"
        fi
    fi
}

# Test 7: Security Validation
test_security_validation() {
    log_test_start "Security Validation"
    
    # Test HTTPS usage (if applicable)
    if [[ "$WELL_KNOWN_URL" =~ ^https:// ]]; then
        log_test_pass "OAuth2 server uses HTTPS"
    else
        log_test_warning "OAuth2 server uses HTTP (not recommended for production)"
    fi
    
    if [ "${MARKLOGIC_PROTOCOL:-http}" = "https" ]; then
        log_test_pass "MarkLogic API uses HTTPS"
    else
        log_test_warning "MarkLogic API uses HTTP (not recommended for production)"
    fi
    
    # Test token expiration
    if [ -n "${TEST_ACCESS_TOKEN:-}" ]; then
        local exp_claim
        exp_claim=$(oauth2_jwt_get_claim "$TEST_ACCESS_TOKEN" "exp")
        
        if [ -n "$exp_claim" ]; then
            log_test_pass "Access token has expiration claim"
            
            local token_lifetime current_time
            current_time=$(date +%s)
            token_lifetime=$((exp_claim - current_time))
            
            if [ $token_lifetime -lt 3600 ]; then
                log_test_pass "Token lifetime is reasonable (<1 hour)"
            elif [ $token_lifetime -lt 86400 ]; then
                log_test_warning "Token lifetime is long (<24 hours)"
            else
                log_test_warning "Token lifetime is very long (>24 hours)"
            fi
        else
            log_test_warning "Access token has no expiration claim"
        fi
    fi
    
    # Test JWT algorithm
    if [ -n "${TEST_ACCESS_TOKEN:-}" ]; then
        local alg
        alg=$(oauth2_jwt_get_claim "$TEST_ACCESS_TOKEN" "alg" 2>/dev/null) || {
            local header
            header=$(oauth2_jwt_decode_header "$TEST_ACCESS_TOKEN")
            alg=$(echo "$header" | jq -r '.alg // "unknown"')
        }
        
        case "$alg" in
            "RS256"|"RS384"|"RS512"|"ES256"|"ES384"|"ES512")
                log_test_pass "JWT uses secure signing algorithm: $alg"
                ;;
            "HS256"|"HS384"|"HS512")
                log_test_warning "JWT uses HMAC algorithm: $alg (RSA/ECDSA preferred)"
                ;;
            "none")
                log_test_fail "JWT uses no signature algorithm (security risk)"
                ;;
            *)
                log_test_warning "JWT uses unknown algorithm: $alg"
                ;;
        esac
    fi
}

# ================================================================
# MAIN EXECUTION
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
            --marklogic-manage-port)
                MARKLOGIC_MANAGE_PORT="$2"
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
            --app-server)
                APP_SERVER="$2"
                shift 2
                ;;
            --client-id)
                CLIENT_ID="$2"
                shift 2
                ;;
            --client-secret)
                CLIENT_SECRET="$2"
                shift 2
                ;;
            --test-username)
                TEST_USERNAME="$2"
                shift 2
                ;;
            --test-password)
                TEST_PASSWORD="$2"
                shift 2
                ;;
            --api-endpoint-url)
                API_ENDPOINT_URL="$2"
                shift 2
                ;;
            --performance)
                PERFORMANCE_TEST="true"
                shift
                ;;
            --detailed)
                DETAILED_OUTPUT="true"
                shift
                ;;
            --decode-tokens)
                DECODE_TOKENS="true"
                shift
                ;;
            --no-decode-tokens)
                DECODE_TOKENS="false"
                shift
                ;;
            --verbose)
                VERBOSE="true"
                export OAUTH2_DEBUG="true"
                shift
                ;;
            --insecure)
                INSECURE="true"
                shift
                ;;
            --help)
                show_usage
                exit 0
                ;;
            *)
                oauth2_log_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done
    
    # Override with environment variables if set
    MARKLOGIC_HOST="${MARKLOGIC_HOST:-${MARKLOGIC_HOST}}"
    MARKLOGIC_USER="${MARKLOGIC_USER:-${MARKLOGIC_USER}}"
    MARKLOGIC_PASS="${MARKLOGIC_PASS:-${MARKLOGIC_PASS}}"
}

# Generate test report
generate_report() {
    echo
    oauth2_log_info "=== VALIDATION REPORT ==="
    echo
    
    # Test summary
    local success_rate
    if [ $TOTAL_TESTS -gt 0 ]; then
        success_rate=$((PASSED_TESTS * 100 / TOTAL_TESTS))
    else
        success_rate=0
    fi
    
    oauth2_log_info "📊 Test Summary:"
    oauth2_log_info "   Total Tests: $TOTAL_TESTS"
    oauth2_log_info "   Passed: $PASSED_TESTS"
    oauth2_log_info "   Failed: $FAILED_TESTS"
    oauth2_log_info "   Warnings: $WARNINGS"
    oauth2_log_info "   Success Rate: $success_rate%"
    echo
    
    # Display API endpoint response if available
    if [ -n "$API_ENDPOINT_URL" ] && [ -n "$API_ENDPOINT_RESPONSE" ]; then
        oauth2_log_info "🌐 Custom API Endpoint Response:"
        oauth2_log_info "   Endpoint: $API_ENDPOINT_URL"
        echo
        
        # Format and display the response
        if echo "$API_ENDPOINT_RESPONSE" | jq . >/dev/null 2>&1; then
            # It's valid JSON, pretty print it
            oauth2_log_info "   📄 Response (JSON formatted):"
            echo "$API_ENDPOINT_RESPONSE" | jq . | sed 's/^/      /'
        else
            # It's not JSON, display as-is
            oauth2_log_info "   📄 Response (raw):"
            echo "$API_ENDPOINT_RESPONSE" | sed 's/^/      /'
        fi
        echo
    fi
    
    # Overall assessment
    if [ $FAILED_TESTS -eq 0 ] && [ $WARNINGS -le 2 ]; then
        oauth2_log_success "🎉 EXCELLENT - Configuration is working well"
    elif [ $FAILED_TESTS -eq 0 ]; then
        oauth2_log_success "✅ GOOD - Configuration is working with minor issues"
    elif [ $FAILED_TESTS -le 2 ]; then
        oauth2_log_warning "⚠️  NEEDS ATTENTION - Configuration has some issues"
    else
        oauth2_log_error "❌ CRITICAL - Configuration has major issues"
    fi
    
    echo
    oauth2_log_info "📋 Recommendations:"
    
    if [ $FAILED_TESTS -gt 0 ]; then
        oauth2_log_info "• Review and fix failing tests"
        oauth2_log_info "• Check OAuth2 server and MarkLogic connectivity"
        oauth2_log_info "• Verify external security configuration"
    fi
    
    if [ $WARNINGS -gt 3 ]; then
        oauth2_log_info "• Address security warnings for production use"
        oauth2_log_info "• Consider using HTTPS for all communications"
        oauth2_log_info "• Review token expiration policies"
    fi
    
    if [ "$PERFORMANCE_TEST" = "true" ]; then
        oauth2_log_info "• Monitor token generation and validation performance"
        oauth2_log_info "• Consider implementing token caching strategies"
    fi
    
    oauth2_log_info "• Test with real user accounts and applications"
    oauth2_log_info "• Implement monitoring and alerting for production use"
}

# Main validation function
main() {
    oauth2_log_info "=== OAuth2 Configuration Validation ==="
    oauth2_log_info "Version 1.0.0 - MLEAProxy Development Team"
    echo
    
    # Validate required parameters
    if [ -z "$WELL_KNOWN_URL" ]; then
        oauth2_log_error "OAuth2 well-known URL is required"
        echo
        show_usage
        exit 1
    fi
    
    if [ -z "$APP_SERVER" ]; then
        oauth2_log_error "--app-server is required"
        echo
        show_usage
        exit 1
    fi
    
    oauth2_validate_url "$WELL_KNOWN_URL" || exit 1
    
    # Parse MarkLogic host early to show proper URL in config
    parse_marklogic_host "$MARKLOGIC_HOST"
    local display_host_url="$MARKLOGIC_PROTOCOL://$MARKLOGIC_HOST_ONLY"
    if [ -n "$MARKLOGIC_PORT_FROM_URL" ]; then
        display_host_url="$display_host_url:$MARKLOGIC_PORT_FROM_URL"
    fi
    
    oauth2_log_info "Configuration:"
    oauth2_log_info "• OAuth2 Well-Known URL: $WELL_KNOWN_URL"
    oauth2_log_info "• MarkLogic Host URL: $display_host_url"
    oauth2_log_info "• MarkLogic Manage Port: $MARKLOGIC_MANAGE_PORT"
    oauth2_log_info "• App Server: $APP_SERVER"
    if [ -n "$API_ENDPOINT_URL" ]; then
        oauth2_log_info "• API Endpoint URL: $API_ENDPOINT_URL"
    fi
    oauth2_log_info "• Performance Testing: $PERFORMANCE_TEST"
    oauth2_log_info "• Detailed Output: $DETAILED_OUTPUT"
    oauth2_log_info "• Token Decoding: $DECODE_TOKENS"
    echo
    
    # Run validation tests
    test_oauth_server_connectivity
    test_oauth_discovery_endpoints
    test_token_generation
    test_marklogic_configuration
    test_end_to_end_validation
    test_performance
    test_security_validation
    
    # Generate final report
    generate_report
    
    # Exit with appropriate code
    if [ $FAILED_TESTS -eq 0 ]; then
        exit 0
    else
        exit 1
    fi
}

# Script entry point
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    parse_arguments "$@"
    main
fi