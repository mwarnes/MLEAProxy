#!/bin/bash

# ================================================================
# MLEAProxy OAuth Integration Test
# ================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/marklogic-utils.sh"

# Configuration
TEST_NAME="OAuth"
APPSERVER_NAME="MLEAProxy-OAuth-Test"
APPSERVER_PORT="9006"
EXTERNAL_SECURITY_NAME="MLEAProxy-OAuth"
AUTH_TYPE="oauth2"
AUTHZ_SCHEME="internal"
TEST_USERNAME="admin"
TEST_PASSWORD="password"
CLIENT_ID="marklogic"
CLIENT_SECRET="secret"
EXPECTED_USER="admin"
EXPECTED_ROLES=("admin")

VERBOSE=false
NO_CLEANUP=false

for arg in "$@"; do
    case $arg in
        --verbose) VERBOSE=true ;;
        --no-cleanup) NO_CLEANUP=true ;;
    esac
done

# External Security XML
EXTERNAL_SECURITY_XML='<external-security xmlns="http://marklogic.com/manage">
  <external-security-name>'${EXTERNAL_SECURITY_NAME}'</external-security-name>
  <authentication>oauth2</authentication>
  <cache-timeout>300</cache-timeout>
  <authorization>internal</authorization>
  <oauth-authorization-endpoint>http://localhost:8080/oauth/authorize</oauth-authorization-endpoint>
  <oauth-token-endpoint>http://localhost:8080/oauth/token</oauth-token-endpoint>
  <oauth-jwks-uri>http://localhost:8080/oauth/jwks</oauth-jwks-uri>
</external-security>'

# Cleanup
cleanup() {
    if [[ "$NO_CLEANUP" == "true" ]]; then
        log_info "Skipping cleanup (--no-cleanup flag set)"
        return 0
    fi
    
    echo
    log_info "Cleanup:"
    delete_test_appserver "$APPSERVER_NAME"
    log_success "Deleted AppServer '${APPSERVER_NAME}'"
    delete_external_security "$EXTERNAL_SECURITY_NAME"
    log_success "Deleted external security '${EXTERNAL_SECURITY_NAME}'"
    echo
}

trap cleanup EXIT

# Main Test
echo "🔧 MLEAProxy ${TEST_NAME} Integration Test"
echo "================================================================================"
echo

check_prerequisites false

log_info "Setup:"

delete_external_security "$EXTERNAL_SECURITY_NAME" > /dev/null 2>&1 || true
log_success "Deleted existing external security '${EXTERNAL_SECURITY_NAME}'"

create_external_security "$EXTERNAL_SECURITY_NAME" "oauth2" "$EXTERNAL_SECURITY_XML"
log_success "Created external security '${EXTERNAL_SECURITY_NAME}'"

delete_test_appserver "$APPSERVER_NAME" > /dev/null 2>&1 || true
log_success "Deleted existing AppServer '${APPSERVER_NAME}'"

create_test_appserver "$APPSERVER_NAME" "$APPSERVER_PORT" "$AUTH_TYPE" "$AUTHZ_SCHEME" "$EXTERNAL_SECURITY_NAME"
log_success "Created AppServer '${APPSERVER_NAME}' on port ${APPSERVER_PORT}"

wait_for_appserver "$APPSERVER_PORT"

echo
log_info "Testing:"

log_step "Step 1: Obtaining JWT token from MLEAProxy"

TOKEN_RESPONSE=$(curl -sf -X POST "${MLEAPROXY_BASE}/oauth/token" \
    -d "grant_type=password" \
    -d "username=${TEST_USERNAME}" \
    -d "password=${TEST_PASSWORD}" \
    -d "client_id=${CLIENT_ID}" \
    -d "client_secret=${CLIENT_SECRET}" 2>/dev/null || echo "")

if [[ -z "$TOKEN_RESPONSE" ]]; then
    log_error "Failed to obtain token from MLEAProxy"
    exit 1
fi

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token // empty')

if [[ -z "$ACCESS_TOKEN" ]]; then
    log_error "No access_token in response"
    exit 1
fi

log_success "Token retrieved successfully"

log_step "Step 2: Testing endpoint with Bearer token"

TEST_URL="http://localhost:${APPSERVER_PORT}/manage/LATEST/?format=json"

RESPONSE=$(curl -sf -H "Authorization: Bearer ${ACCESS_TOKEN}" "$TEST_URL" 2>/dev/null || echo "")

if [[ -z "$RESPONSE" ]]; then
    log_error "HTTP request failed"
    echo
    echo "================================================================================"
    echo "❌ ${TEST_NAME} Integration Test: FAILED"
    echo "================================================================================"
    exit 1
fi

log_success "HTTP 200 OK"

if verify_json_response "$RESPONSE" "$EXPECTED_USER" "${EXPECTED_ROLES[@]}"; then
    echo
    echo "================================================================================"
    echo "✅ ${TEST_NAME} Integration Test: PASSED"
    echo "================================================================================"
    exit 0
else
    echo
    echo "================================================================================"
    echo "❌ ${TEST_NAME} Integration Test: FAILED"
    echo "================================================================================"
    exit 1
fi
