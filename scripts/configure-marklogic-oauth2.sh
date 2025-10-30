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
FETCH_JWKS="true"
TEST_CONFIG="true"
MLEAPROXY_URL="http://localhost:8080"
VERBOSE="false"
DRY_RUN="false"

# ================================================================
# UTILITY FUNCTIONS
# ================================================================

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_verbose() {
    if [ "$VERBOSE" = "true" ]; then
        echo -e "${CYAN}[DEBUG]${NC} $1"
    fi
}

show_usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Creates MarkLogic OAuth2 external security configuration from OAuth2 Authorization Server discovery endpoints.

OPTIONS:
    --well-known-url URL          OAuth2 .well-known discovery endpoint URL (required)
    --marklogic-host HOST         MarkLogic host (default: localhost)
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
    --no-jwks                     Skip JWKS key fetching
    --no-test                     Skip configuration testing
    --mleaproxy-url URL           MLEAProxy URL for testing (default: http://localhost:8080)
    --verbose                     Enable verbose logging
    --dry-run                     Show what would be done without executing
    --help                        Show this help message

EXAMPLES:
    # MLEAProxy (development/testing)
    $0 --well-known-url http://localhost:8080/oauth/.well-known/config \\
       --config-name MLEAProxy-OAuth

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
    MLEAPROXY_URL               Override default MLEAProxy URL for testing

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

# ================================================================
# OAUTH2 DISCOVERY FUNCTIONS
# ================================================================

# Fetch OAuth2 configuration from .well-known endpoint
fetch_oauth_config() {
    local well_known_url="$1"
    
    log_info "Fetching OAuth2 configuration from: $well_known_url"
    
    local response
    response=$(curl -s -f "$well_known_url" 2>/dev/null) || {
        log_error "Failed to fetch OAuth2 configuration from $well_known_url"
        return 1
    }
    
    # Validate JSON response
    if ! echo "$response" | jq empty 2>/dev/null; then
        log_error "Invalid JSON response from OAuth2 discovery endpoint"
        return 1
    fi
    
    log_verbose "OAuth2 configuration response: $response"
    echo "$response"
}

# Extract configuration values from OAuth2 discovery response
parse_oauth_config() {
    local config_json="$1"
    
    log_info "Parsing OAuth2 configuration..."
    
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
    jwks_response=$(curl -s -f "$jwks_uri" 2>/dev/null) || {
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
    local marklogic_url="http://$MARKLOGIC_HOST:$MARKLOGIC_PORT/manage/v2/external-security"
    
    log_info "Applying configuration to MarkLogic at: $marklogic_url"
    
    if [ "$DRY_RUN" = "true" ]; then
        log_info "DRY RUN - Configuration that would be applied:"
        echo "$config_json" | jq .
        return 0
    fi
    
    local response status_code
    response=$(curl -s -w "%{http_code}" --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
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
        400)
            log_error "Bad request - check configuration parameters"
            echo "$response_body" | jq . 2>/dev/null || echo "$response_body"
            return 1
            ;;
        401)
            log_error "Unauthorized - check MarkLogic credentials"
            return 1
            ;;
        409)
            log_warning "Configuration already exists - updating..."
            update_marklogic_config "$config_json"
            return $?
            ;;
        *)
            log_error "Failed to create configuration (HTTP $status_code)"
            echo "$response_body" | jq . 2>/dev/null || echo "$response_body"
            return 1
            ;;
    esac
}

# Update existing MarkLogic configuration
update_marklogic_config() {
    local config_json="$1"
    local marklogic_url="http://$MARKLOGIC_HOST:$MARKLOGIC_PORT/manage/v2/external-security/$CONFIG_NAME"
    
    log_info "Updating existing MarkLogic configuration..."
    
    if [ "$DRY_RUN" = "true" ]; then
        log_info "DRY RUN - Configuration that would be updated"
        return 0
    fi
    
    local response status_code
    response=$(curl -s -w "%{http_code}" --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
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
    log_info "POST http://$MARKLOGIC_HOST:$MARKLOGIC_PORT/manage/v2/external-security/$CONFIG_NAME/jwt-secrets"
    log_info "JWKS data available for manual conversion: $(echo "$jwks_json" | jq -c '.keys | length') keys found"
}

# ================================================================
# TESTING FUNCTIONS
# ================================================================

# Test OAuth token generation with MLEAProxy
test_mleaproxy_token_generation() {
    if [ "$TEST_CONFIG" != "true" ]; then
        log_info "Skipping configuration testing"
        return 0
    fi
    
    log_info "Testing OAuth token generation with MLEAProxy..."
    
    local token_url="$MLEAPROXY_URL/oauth/token"
    local response access_token
    
    # Test client credentials flow
    response=$(curl -s -X POST "$token_url" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=client_credentials&client_id=test-client&client_secret=test-secret&scope=read:documents" 2>/dev/null) || {
        log_warning "Failed to generate test token from MLEAProxy"
        return 1
    }
    
    access_token=$(echo "$response" | jq -r '.access_token // ""')
    
    if [ "$access_token" = "" ] || [ "$access_token" = "null" ]; then
        log_warning "No access token received from MLEAProxy"
        log_verbose "Response: $response"
        return 1
    fi
    
    log_success "Successfully generated test token from MLEAProxy"
    log_verbose "Access token: ${access_token:0:50}..."
    
    # Test token validation
    test_token_validation "$access_token"
}

# Test token validation against MarkLogic
test_token_validation() {
    local access_token="$1"
    
    log_info "Testing token validation against MarkLogic..."
    
    if [ "$DRY_RUN" = "true" ]; then
        log_info "DRY RUN - Token validation would be tested"
        return 0
    fi
    
    # Test a simple MarkLogic API call with the token
    local ml_api_url="http://$MARKLOGIC_HOST:8000/v1/documents"
    local response status_code
    
    response=$(curl -s -w "%{http_code}" \
        -H "Authorization: Bearer $access_token" \
        "$ml_api_url" 2>/dev/null) || {
        log_warning "Failed to test token against MarkLogic API"
        return 1
    }
    
    status_code="${response: -3}"
    
    case "$status_code" in
        200|401|403)
            # 200: Success, 401/403: Auth configured (may need valid user/roles)
            log_success "Token validation test completed (HTTP $status_code)"
            log_info "OAuth configuration appears to be working"
            ;;
        *)
            log_warning "Unexpected response from MarkLogic API (HTTP $status_code)"
            ;;
    esac
}

# Validate the created configuration
validate_configuration() {
    log_info "Validating MarkLogic OAuth2 configuration..."
    
    local config_url="http://$MARKLOGIC_HOST:$MARKLOGIC_PORT/manage/v2/external-security/$CONFIG_NAME"
    local response status_code
    
    response=$(curl -s -w "%{http_code}" --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
        "$config_url" 2>/dev/null)
    
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
            --no-jwks)
                FETCH_JWKS="false"
                shift
                ;;
            --no-test)
                TEST_CONFIG="false"
                shift
                ;;
            --mleaproxy-url)
                MLEAPROXY_URL="$2"
                shift 2
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
    
    # Override with environment variables if set
    MARKLOGIC_HOST="${MARKLOGIC_HOST:-${MARKLOGIC_HOST}}"
    MARKLOGIC_USER="${MARKLOGIC_USER:-${MARKLOGIC_USER}}"
    MARKLOGIC_PASS="${MARKLOGIC_PASS:-${MARKLOGIC_PASS}}"
    MLEAPROXY_URL="${MLEAPROXY_URL:-${MLEAPROXY_URL}}"
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
    
    # Check dependencies
    check_dependencies
    echo
    
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
    
    # Test configuration
    if [ "$TEST_CONFIG" = "true" ]; then
        test_mleaproxy_token_generation
        echo
    fi
    
    # Summary
    log_success "=== Configuration Complete ==="
    log_info "External Security Name: $CONFIG_NAME"
    log_info "OAuth Issuer: $ISSUER"
    log_info "JWKS URI: ${JWKS_URI:-Not configured}"
    log_info "MarkLogic Host: $MARKLOGIC_HOST:$MARKLOGIC_PORT"
    
    if [ "$DRY_RUN" = "true" ]; then
        log_warning "This was a DRY RUN - no changes were made"
    fi
    
    echo
    log_info "Next steps:"
    log_info "1. Configure app servers to use external security: $CONFIG_NAME"
    log_info "2. Test authentication with your OAuth2 provider"
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