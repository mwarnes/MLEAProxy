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
source "$SCRIPT_DIR/oauth2-utils.sh" || {
    echo "ERROR: Could not load oauth2-utils.sh" >&2
    exit 1
}

# ================================================================
# CONFIGURATION
# ================================================================

# Default values
OAUTH_SERVER_URL=""
MARKLOGIC_HOST="localhost"
MARKLOGIC_MANAGE_PORT="8002"
MARKLOGIC_API_PORT="8000"
MARKLOGIC_USER="admin"
MARKLOGIC_PASS="admin"
CONFIG_NAME=""
CLIENT_ID="marklogic"
CLIENT_SECRET="secret"
TEST_USERNAME="admin"
TEST_PASSWORD="admin"
VERBOSE="false"
PERFORMANCE_TEST="false"
DETAILED_OUTPUT="false"

# Test results tracking
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
WARNINGS=0

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
    --oauth-server-url URL        OAuth2 server base URL (required)
    --marklogic-host HOST         MarkLogic host (default: localhost)
    --marklogic-manage-port PORT  MarkLogic manage port (default: 8002)
    --marklogic-api-port PORT     MarkLogic API port (default: 8000)
    --marklogic-user USER         MarkLogic admin user (default: admin)
    --marklogic-pass PASS         MarkLogic admin password (default: admin)
    --config-name NAME            External security configuration name (required)
    --client-id ID                OAuth client ID (default: marklogic)
    --client-secret SECRET        OAuth client secret (default: secret)
    --test-username USER          Username for password grant test (default: admin)
    --test-password PASS          Password for password grant test (default: admin)
    --performance                 Run performance tests
    --detailed                    Show detailed test output
    --verbose                     Enable verbose logging
    --help                        Show this help message

EXAMPLES:
    # Validate MLEAProxy configuration
    $0 --oauth-server-url http://localhost:8080 --config-name MLEAProxy-OAuth

    # Validate production OAuth2 setup
    $0 --oauth-server-url https://auth.example.com \\
       --config-name Production-OAuth \\
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
    
    # Test basic connectivity
    if oauth2_check_port "$(echo "$OAUTH_SERVER_URL" | sed 's#.*://##' | cut -d: -f1)" \
                        "$(echo "$OAUTH_SERVER_URL" | sed 's#.*:##' | cut -d/ -f1)"; then
        log_test_pass "OAuth2 server is reachable"
    else
        log_test_fail "OAuth2 server is not reachable"
        return 1
    fi
    
    # Test HTTP response
    local response
    if response=$(oauth2_http_get "$OAUTH_SERVER_URL" 10); then
        log_test_pass "OAuth2 server responds to HTTP requests"
        if [ "$VERBOSE" = "true" ]; then
            oauth2_log_debug "Server response preview: $(echo "$response" | head -c 200)..."
        fi
    else
        log_test_warning "OAuth2 server base URL does not respond (this may be normal)"
    fi
}

# Test 2: OAuth2 Discovery Endpoints
test_oauth_discovery_endpoints() {
    log_test_start "OAuth2 Discovery Endpoints"
    
    # Test well-known configuration endpoints
    local config_endpoints=(
        ".well-known/openid_configuration"
        ".well-known/config"
        ".well-known/oauth-authorization-server"
    )
    
    local config_found=false
    local working_endpoint=""
    
    for endpoint in "${config_endpoints[@]}"; do
        oauth2_log_info "Testing endpoint: $endpoint"
        
        if config=$(oauth2_fetch_well_known "$OAUTH_SERVER_URL" "$endpoint" 2>/dev/null); then
            log_test_pass "OAuth2 configuration found at $endpoint"
            config_found=true
            working_endpoint="$endpoint"
            
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
            
            break
        fi
    done
    
    if [ "$config_found" = "false" ]; then
        log_test_fail "No OAuth2 discovery endpoint found"
        return 1
    fi
    
    # Test JWKS endpoint if available
    if [ -n "$OAUTH_JWKS_URI" ]; then
        oauth2_log_info "Testing JWKS endpoint..."
        
        if jwks=$(oauth2_fetch_jwks "$OAUTH_JWKS_URI"); then
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
    
    if access_token=$(oauth2_get_token_client_credentials "$OAUTH_TOKEN_ENDPOINT" "$CLIENT_ID" "$CLIENT_SECRET" "read:documents"); then
        log_test_pass "Client credentials flow successful"
        
        # Validate token structure
        if oauth2_validate_jwt "$access_token"; then
            log_test_pass "Generated token has valid JWT structure"
            
            # Decode and display token information
            local header payload
            header=$(oauth2_jwt_decode_header "$access_token")
            payload=$(oauth2_jwt_decode_payload "$access_token")
            
            oauth2_log_info "Token algorithm: $(echo "$header" | jq -r '.alg // "unknown"')"
            oauth2_log_info "Token issuer: $(echo "$payload" | jq -r '.iss // "unknown"')"
            oauth2_log_info "Token subject: $(echo "$payload" | jq -r '.sub // "unknown"')"
            oauth2_log_info "Token audience: $(echo "$payload" | jq -r '.aud // "unknown"')"
            
            # Check expiration
            local time_to_expiry
            time_to_expiry=$(oauth2_jwt_time_to_expiry "$access_token")
            
            if [ "$time_to_expiry" = "expired" ]; then
                log_test_warning "Generated token is already expired"
            elif [ "$time_to_expiry" = "unknown" ]; then
                log_test_warning "Token expiration time is unknown"
            else
                oauth2_log_info "Token expires in: $(oauth2_seconds_to_human "$time_to_expiry")"
            fi
            
            if [ "$DETAILED_OUTPUT" = "true" ]; then
                oauth2_log_info "JWT Header:"
                echo "$header" | jq .
                oauth2_log_info "JWT Payload:"
                echo "$payload" | jq .
            fi
            
            export TEST_ACCESS_TOKEN="$access_token"
        else
            log_test_fail "Generated token has invalid JWT structure"
        fi
    else
        log_test_fail "Client credentials flow failed"
    fi
    
    # Test password flow (optional)
    oauth2_log_info "Testing password flow..."
    
    if password_token=$(oauth2_get_token_password "$OAUTH_TOKEN_ENDPOINT" "$TEST_USERNAME" "$TEST_PASSWORD" "$CLIENT_ID" "$CLIENT_SECRET" "read:documents" 2>/dev/null); then
        log_test_pass "Password flow successful"
        
        if [ "$VERBOSE" = "true" ]; then
            local username_claim
            username_claim=$(oauth2_jwt_get_claim "$password_token" "username")
            oauth2_log_info "Password token username claim: $username_claim"
        fi
    else
        log_test_warning "Password flow failed (this may be expected if user is not configured)"
    fi
}

# Test 4: MarkLogic Configuration
test_marklogic_configuration() {
    log_test_start "MarkLogic Configuration"
    
    if [ -z "$CONFIG_NAME" ]; then
        log_test_fail "No configuration name provided for MarkLogic testing"
        return 1
    fi
    
    # Test MarkLogic connectivity
    oauth2_log_info "Testing MarkLogic connectivity..."
    
    if oauth2_check_port "$MARKLOGIC_HOST" "$MARKLOGIC_MANAGE_PORT"; then
        log_test_pass "MarkLogic manage port is reachable"
    else
        log_test_fail "MarkLogic manage port is not reachable"
        return 1
    fi
    
    # Test external security configuration
    oauth2_log_info "Testing external security configuration..."
    
    if oauth2_test_marklogic_config "$MARKLOGIC_HOST" "$MARKLOGIC_MANAGE_PORT" "$CONFIG_NAME" "$MARKLOGIC_USER" "$MARKLOGIC_PASS"; then
        log_test_pass "MarkLogic external security configuration exists"
        
        # Get configuration details
        local config_url="http://$MARKLOGIC_HOST:$MARKLOGIC_MANAGE_PORT/manage/v2/external-security/$CONFIG_NAME"
        local config_response
        
        if config_response=$(curl -s --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" "$config_url" 2>/dev/null); then
            local auth_method cache_timeout
            auth_method=$(echo "$config_response" | jq -r '.["external-security-config"] | .authentication // "unknown"')
            cache_timeout=$(echo "$config_response" | jq -r '.["external-security-config"] | .["cache-timeout"] // "unknown"')
            
            oauth2_log_info "Authentication method: $auth_method"
            oauth2_log_info "Cache timeout: $cache_timeout seconds"
            
            if [ "$DETAILED_OUTPUT" = "true" ]; then
                oauth2_log_info "Full configuration:"
                echo "$config_response" | jq .
            fi
        fi
    else
        log_test_fail "MarkLogic external security configuration not accessible"
        return 1
    fi
    
    # Test MarkLogic API port
    oauth2_log_info "Testing MarkLogic API port..."
    
    if oauth2_check_port "$MARKLOGIC_HOST" "$MARKLOGIC_API_PORT"; then
        log_test_pass "MarkLogic API port is reachable"
    else
        log_test_warning "MarkLogic API port is not reachable (may affect token testing)"
    fi
}

# Test 5: End-to-End Token Validation
test_end_to_end_validation() {
    log_test_start "End-to-End Token Validation"
    
    if [ -z "$TEST_ACCESS_TOKEN" ]; then
        log_test_fail "No access token available for end-to-end testing"
        return 1
    fi
    
    # Test token against MarkLogic API
    oauth2_log_info "Testing token against MarkLogic API..."
    
    local result
    oauth2_test_token_against_marklogic "$TEST_ACCESS_TOKEN" "$MARKLOGIC_HOST" "$MARKLOGIC_API_PORT" "/v1/documents"
    result=$?
    
    case $result in
        0)
            log_test_pass "Token validated successfully by MarkLogic"
            ;;
        1)
            log_test_fail "Token rejected by MarkLogic - check external security configuration"
            ;;
        2)
            log_test_warning "Token accepted but access denied - check user roles and permissions"
            ;;
        3)
            log_test_warning "Unexpected response from MarkLogic API"
            ;;
    esac
    
    # Test different API endpoints
    if [ "$PERFORMANCE_TEST" = "true" ]; then
        oauth2_log_info "Testing multiple API endpoints..."
        
        local endpoints=("/v1/documents" "/v1/search" "/v1/config/query/default")
        local successful_endpoints=0
        
        for endpoint in "${endpoints[@]}"; do
            if oauth2_test_token_against_marklogic "$TEST_ACCESS_TOKEN" "$MARKLOGIC_HOST" "$MARKLOGIC_API_PORT" "$endpoint" >/dev/null 2>&1; then
                ((successful_endpoints++))
            fi
        done
        
        oauth2_log_info "Token worked with $successful_endpoints/${#endpoints[@]} endpoints"
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
    
    for ((i=1; i<=iterations; i++)); do
        oauth2_get_token_client_credentials "$OAUTH_TOKEN_ENDPOINT" "$CLIENT_ID" "$CLIENT_SECRET" "read:documents" >/dev/null 2>&1 || {
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
    if [ -n "$TEST_ACCESS_TOKEN" ]; then
        oauth2_log_info "Testing concurrent API requests..."
        
        local concurrent_requests=5
        local pids=()
        
        start_time=$(date +%s.%N)
        
        for ((i=1; i<=concurrent_requests; i++)); do
            (oauth2_test_token_against_marklogic "$TEST_ACCESS_TOKEN" "$MARKLOGIC_HOST" "$MARKLOGIC_API_PORT" "/v1/documents" >/dev/null 2>&1) &
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
    if [[ "$OAUTH_SERVER_URL" =~ ^https:// ]]; then
        log_test_pass "OAuth2 server uses HTTPS"
    else
        log_test_warning "OAuth2 server uses HTTP (not recommended for production)"
    fi
    
    if [[ "http://$MARKLOGIC_HOST:$MARKLOGIC_API_PORT" =~ ^https:// ]]; then
        log_test_pass "MarkLogic API uses HTTPS"
    else
        log_test_warning "MarkLogic API uses HTTP (not recommended for production)"
    fi
    
    # Test token expiration
    if [ -n "$TEST_ACCESS_TOKEN" ]; then
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
    if [ -n "$TEST_ACCESS_TOKEN" ]; then
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
            --oauth-server-url)
                OAUTH_SERVER_URL="$2"
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
            --marklogic-api-port)
                MARKLOGIC_API_PORT="$2"
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
            --performance)
                PERFORMANCE_TEST="true"
                shift
                ;;
            --detailed)
                DETAILED_OUTPUT="true"
                shift
                ;;
            --verbose)
                VERBOSE="true"
                export OAUTH2_DEBUG="true"
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
    if [ -z "$OAUTH_SERVER_URL" ]; then
        oauth2_log_error "OAuth2 server URL is required"
        echo
        show_usage
        exit 1
    fi
    
    if [ -z "$CONFIG_NAME" ]; then
        oauth2_log_error "MarkLogic configuration name is required"
        echo
        show_usage
        exit 1
    fi
    
    oauth2_validate_url "$OAUTH_SERVER_URL" || exit 1
    
    oauth2_log_info "Configuration:"
    oauth2_log_info "• OAuth2 Server: $OAUTH_SERVER_URL"
    oauth2_log_info "• MarkLogic Host: $MARKLOGIC_HOST"
    oauth2_log_info "• Configuration Name: $CONFIG_NAME"
    oauth2_log_info "• Performance Testing: $PERFORMANCE_TEST"
    oauth2_log_info "• Detailed Output: $DETAILED_OUTPUT"
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