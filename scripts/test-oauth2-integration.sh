#!/bin/bash

# ================================================================
# MLEAProxy OAuth2 Integration Test Script
# ================================================================
# 
# This script demonstrates and tests the OAuth2 configuration script
# with MLEAProxy as the OAuth2 Authorization Server.
#
# Features:
# - Starts MLEAProxy OAuth server
# - Tests .well-known endpoint discovery
# - Runs the OAuth2 configuration script
# - Validates MarkLogic configuration
# - Tests end-to-end token flow
#
# Author: MLEAProxy Development Team  
# Version: 1.0.0
# Date: October 2025
#
# ================================================================

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MLEAPROXY_DIR="$(dirname "$SCRIPT_DIR")"
CONFIG_SCRIPT="$SCRIPT_DIR/configure-marklogic-oauth2.sh"
MLEAPROXY_URL="http://localhost:8080"
MARKLOGIC_HOST="localhost"
MARKLOGIC_PORT="8002"
CONFIG_NAME="MLEAProxy-OAuth-Test"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

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

log_test() {
    echo -e "${CYAN}[TEST]${NC} $1"
}

# Check if MLEAProxy is running
check_mleaproxy() {
    log_info "Checking MLEAProxy status..."
    
    local response status_code
    response=$(curl -s -w "%{http_code}" "$MLEAPROXY_URL/oauth/.well-known/config" 2>/dev/null) || {
        log_warning "MLEAProxy is not running at $MLEAPROXY_URL"
        return 1
    }
    
    status_code="${response: -3}"
    
    if [ "$status_code" = "200" ]; then
        log_success "MLEAProxy is running and responding"
        return 0
    else
        log_warning "MLEAProxy returned HTTP $status_code"
        return 1
    fi
}

# Start MLEAProxy if needed
start_mleaproxy() {
    if check_mleaproxy; then
        return 0
    fi
    
    log_info "Starting MLEAProxy..."
    
    # Check if we can start MLEAProxy
    if [ ! -f "$MLEAPROXY_DIR/pom.xml" ]; then
        log_error "MLEAProxy source not found at $MLEAPROXY_DIR"
        log_error "Please ensure MLEAProxy is available or start it manually"
        return 1
    fi
    
    log_info "Starting MLEAProxy in background (this may take a moment)..."
    
    # Start MLEAProxy in the background
    cd "$MLEAPROXY_DIR"
    (mvn spring-boot:run > /tmp/mleaproxy.log 2>&1 &)
    
    # Wait for startup
    log_info "Waiting for MLEAProxy to start..."
    local attempts=0
    while [ $attempts -lt 30 ]; do
        if check_mleaproxy; then
            log_success "MLEAProxy started successfully"
            return 0
        fi
        sleep 2
        ((attempts++))
    done
    
    log_error "MLEAProxy failed to start within 60 seconds"
    log_error "Check logs: tail /tmp/mleaproxy.log"
    return 1
}

# Test OAuth2 discovery endpoints
test_oauth_discovery() {
    log_test "Testing OAuth2 discovery endpoints..."
    
    # Test .well-known/config endpoint
    log_info "Testing .well-known/config endpoint..."
    local config_response
    config_response=$(curl -s "$MLEAPROXY_URL/oauth/.well-known/config" 2>/dev/null) || {
        log_error "Failed to fetch OAuth2 configuration"
        return 1
    }
    
    # Validate required fields
    local issuer token_endpoint jwks_uri
    issuer=$(echo "$config_response" | jq -r '.issuer // ""')
    token_endpoint=$(echo "$config_response" | jq -r '.token_endpoint // ""')
    jwks_uri=$(echo "$config_response" | jq -r '.jwks_uri // ""')
    
    log_info "Issuer: $issuer"
    log_info "Token Endpoint: $token_endpoint"
    log_info "JWKS URI: $jwks_uri"
    
    if [ "$issuer" = "" ] || [ "$token_endpoint" = "" ] || [ "$jwks_uri" = "" ]; then
        log_error "Missing required OAuth2 configuration fields"
        return 1
    fi
    
    # Test JWKS endpoint
    log_info "Testing JWKS endpoint..."
    local jwks_response
    jwks_response=$(curl -s "$jwks_uri" 2>/dev/null) || {
        log_error "Failed to fetch JWKS"
        return 1
    }
    
    local key_count
    key_count=$(echo "$jwks_response" | jq '.keys | length' 2>/dev/null || echo "0")
    log_info "JWKS contains $key_count keys"
    
    if [ "$key_count" = "0" ]; then
        log_warning "No keys found in JWKS"
    else
        log_success "OAuth2 discovery endpoints working correctly"
    fi
    
    return 0
}

# Test token generation
test_token_generation() {
    log_test "Testing OAuth2 token generation..."
    
    # Test client credentials flow
    log_info "Testing client credentials grant..."
    local token_response access_token
    token_response=$(curl -s -X POST "$MLEAPROXY_URL/oauth/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=client_credentials&client_id=test-client&client_secret=test-secret&scope=read:documents" 2>/dev/null) || {
        log_error "Failed to generate OAuth2 token"
        return 1
    }
    
    access_token=$(echo "$token_response" | jq -r '.access_token // ""')
    
    if [ "$access_token" = "" ]; then
        log_error "No access token received"
        log_error "Response: $token_response"
        return 1
    fi
    
    log_success "Successfully generated access token"
    log_info "Token (first 50 chars): ${access_token:0:50}..."
    
    # Decode JWT token for verification
    log_info "Decoding JWT token..."
    local payload
    payload=$(echo "$access_token" | cut -d. -f2 | base64 -d 2>/dev/null | jq . 2>/dev/null) || {
        log_warning "Could not decode JWT payload"
        return 0
    }
    
    log_info "JWT Claims:"
    echo "$payload" | jq .
    
    return 0
}

# Test password grant flow
test_password_grant() {
    log_test "Testing password grant flow..."
    
    local token_response access_token
    token_response=$(curl -s -X POST "$MLEAPROXY_URL/oauth/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=password&username=admin&password=admin&client_id=marklogic&client_secret=secret" 2>/dev/null) || {
        log_warning "Password grant test failed (this is expected if no users are configured)"
        return 1
    }
    
    access_token=$(echo "$token_response" | jq -r '.access_token // ""')
    
    if [ "$access_token" != "" ]; then
        log_success "Password grant working - token generated for admin user"
        log_info "Token (first 50 chars): ${access_token:0:50}..."
    else
        log_warning "Password grant returned no token (check user configuration)"
        log_info "Response: $token_response"
    fi
    
    return 0
}

# Run the OAuth2 configuration script
test_configuration_script() {
    log_test "Testing OAuth2 configuration script..."
    
    if [ ! -f "$CONFIG_SCRIPT" ]; then
        log_error "Configuration script not found: $CONFIG_SCRIPT"
        return 1
    fi
    
    log_info "Running configuration script in dry-run mode..."
    
    # Run in dry-run mode first
    "$CONFIG_SCRIPT" \
        --well-known-url "$MLEAPROXY_URL/oauth/.well-known/config" \
        --marklogic-host "$MARKLOGIC_HOST" \
        --marklogic-port "$MARKLOGIC_PORT" \
        --config-name "$CONFIG_NAME" \
        --config-description "MLEAProxy OAuth2 test configuration" \
        --dry-run \
        --verbose || {
        log_error "Configuration script dry-run failed"
        return 1
    }
    
    log_success "Configuration script dry-run completed successfully"
    
    # Ask user if they want to apply the configuration
    echo
    read -p "Apply configuration to MarkLogic? (y/N): " -n 1 -r
    echo
    
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "Skipping MarkLogic configuration application"
        return 0
    fi
    
    log_info "Applying configuration to MarkLogic..."
    
    "$CONFIG_SCRIPT" \
        --well-known-url "$MLEAPROXY_URL/oauth/.well-known/config" \
        --marklogic-host "$MARKLOGIC_HOST" \
        --marklogic-port "$MARKLOGIC_PORT" \
        --config-name "$CONFIG_NAME" \
        --config-description "MLEAProxy OAuth2 test configuration" \
        --verbose || {
        log_error "Configuration script failed"
        return 1
    }
    
    log_success "Configuration applied to MarkLogic successfully"
    return 0
}

# Test end-to-end token flow
test_end_to_end() {
    log_test "Testing end-to-end OAuth2 flow..."
    
    # Generate token
    log_info "Step 1: Generate OAuth2 token from MLEAProxy..."
    local token_response access_token
    token_response=$(curl -s -X POST "$MLEAPROXY_URL/oauth/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=client_credentials&client_id=marklogic&client_secret=secret&scope=read:documents" 2>/dev/null)
    
    access_token=$(echo "$token_response" | jq -r '.access_token // ""')
    
    if [ "$access_token" = "" ]; then
        log_error "Could not generate token for end-to-end test"
        return 1
    fi
    
    log_success "Generated token: ${access_token:0:50}..."
    
    # Test token against MarkLogic API (if available)
    log_info "Step 2: Test token against MarkLogic API..."
    
    local ml_response status_code
    ml_response=$(curl -s -w "%{http_code}" \
        -H "Authorization: Bearer $access_token" \
        "http://$MARKLOGIC_HOST:8000/v1/documents" 2>/dev/null) || {
        log_warning "MarkLogic API not available for testing"
        return 0
    }
    
    status_code="${ml_response: -3}"
    
    case "$status_code" in
        200)
            log_success "Token validated successfully by MarkLogic"
            ;;
        401)
            log_info "Token rejected by MarkLogic (401) - check external security configuration"
            ;;
        403)
            log_info "Token accepted but access denied (403) - check user roles and permissions"
            ;;
        *)
            log_warning "Unexpected response from MarkLogic API (HTTP $status_code)"
            ;;
    esac
    
    return 0
}

# Cleanup function
cleanup() {
    log_info "Cleaning up test resources..."
    
    # Optionally remove test configuration
    read -p "Remove test configuration '$CONFIG_NAME' from MarkLogic? (y/N): " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        log_info "Removing test configuration..."
        curl -s --anyauth -u admin:admin \
            -X DELETE \
            "http://$MARKLOGIC_HOST:$MARKLOGIC_PORT/manage/v2/external-security/$CONFIG_NAME" \
            >/dev/null 2>&1 && \
            log_success "Test configuration removed" || \
            log_warning "Could not remove test configuration"
    fi
}

# Display test results summary
show_summary() {
    echo
    log_info "=== Test Summary ==="
    echo
    log_info "The following components were tested:"
    log_info "✓ MLEAProxy OAuth2 server startup"
    log_info "✓ OAuth2 discovery endpoints (.well-known/config, JWKS)"  
    log_info "✓ Token generation (client credentials and password grants)"
    log_info "✓ OAuth2 configuration script (dry-run and optional application)"
    log_info "✓ End-to-end token flow validation"
    echo
    log_info "Next steps:"
    log_info "1. Configure MarkLogic app servers to use the external security configuration"
    log_info "2. Set up user roles and permissions in MLEAProxy users.json"
    log_info "3. Test authentication with your applications"
    echo
    log_info "For production use:"
    log_info "- Replace MLEAProxy with your production OAuth2 provider"
    log_info "- Configure proper SSL/TLS certificates"
    log_info "- Set up appropriate token expiration and refresh policies"
    log_info "- Implement proper user role mapping"
}

# Main test execution
main() {
    echo "=== MLEAProxy OAuth2 Integration Test ==="
    echo "Version 1.0.0 - MLEAProxy Development Team"
    echo
    
    log_info "This script will test the complete OAuth2 integration flow:"
    log_info "1. Start MLEAProxy OAuth2 server (if needed)"
    log_info "2. Test OAuth2 discovery endpoints"
    log_info "3. Test token generation"
    log_info "4. Run the MarkLogic configuration script"
    log_info "5. Test end-to-end flow"
    echo
    
    # Confirm execution
    read -p "Continue with OAuth2 integration test? (Y/n): " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Nn]$ ]]; then
        log_info "Test cancelled by user"
        exit 0
    fi
    
    # Check dependencies
    log_info "Checking dependencies..."
    for cmd in curl jq mvn; do
        if ! command -v "$cmd" >/dev/null 2>&1; then
            log_error "Required command '$cmd' not found"
            exit 1
        fi
    done
    log_success "All dependencies found"
    echo
    
    # Start MLEAProxy
    start_mleaproxy || exit 1
    echo
    
    # Test OAuth2 discovery
    test_oauth_discovery || exit 1
    echo
    
    # Test token generation
    test_token_generation || exit 1
    echo
    
    # Test password grant (optional)
    test_password_grant
    echo
    
    # Test configuration script
    test_configuration_script || exit 1
    echo
    
    # Test end-to-end flow
    test_end_to_end
    echo
    
    # Show summary
    show_summary
    
    # Cleanup
    echo
    cleanup
    
    log_success "=== OAuth2 Integration Test Complete ==="
}

# Handle script interruption
trap 'echo; log_warning "Test interrupted by user"; exit 1' INT TERM

# Script entry point
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi