#!/bin/bash

# ================================================================
# MLEAProxy Kerberos Integration Test
# ================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/marklogic-utils.sh"

# Configuration
TEST_NAME="Kerberos"
APPSERVER_NAME="MLEAProxy-Kerberos-Test"
APPSERVER_PORT="9004"
EXTERNAL_SECURITY_NAME="MLEAProxy-Kerberos"
AUTH_TYPE="kerberos"
AUTHZ_SCHEME="internal"
KERBEROS_PRINCIPAL="HTTP/rocky@MARKLOGIC.LOCAL"
KERBEROS_KEYTAB="/path/to/http.keytab"
EXPECTED_USER_PATTERN="mluser1"

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
  <authentication>kerberos</authentication>
  <cache-timeout>300</cache-timeout>
  <authorization>internal</authorization>
  <kerberos-principal>'${KERBEROS_PRINCIPAL}'</kerberos-principal>
  <kerberos-keytab-path>'${KERBEROS_KEYTAB}'</kerberos-keytab-path>
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

check_prerequisites true

log_info "Setup:"

delete_external_security "$EXTERNAL_SECURITY_NAME" > /dev/null 2>&1 || true
log_success "Deleted existing external security '${EXTERNAL_SECURITY_NAME}'"

create_external_security "$EXTERNAL_SECURITY_NAME" "kerberos" "$EXTERNAL_SECURITY_XML"
log_success "Created external security '${EXTERNAL_SECURITY_NAME}'"

delete_test_appserver "$APPSERVER_NAME" > /dev/null 2>&1 || true
log_success "Deleted existing AppServer '${APPSERVER_NAME}'"

create_test_appserver "$APPSERVER_NAME" "$APPSERVER_PORT" "$AUTH_TYPE" "$AUTHZ_SCHEME" "$EXTERNAL_SECURITY_NAME"
log_success "Created AppServer '${APPSERVER_NAME}' on port ${APPSERVER_PORT}"

wait_for_appserver "$APPSERVER_PORT"

echo
log_info "Testing:"

TEST_URL="http://localhost:${APPSERVER_PORT}/manage/LATEST/?format=json"
log_step "Authenticating with Kerberos ticket against ${TEST_URL}"

RESPONSE=$(curl -sf --negotiate -u : "$TEST_URL" 2>/dev/null || echo "")

if [[ -z "$RESPONSE" ]]; then
    log_error "HTTP request failed"
    log_error "Verify Kerberos ticket: klist"
    echo
    echo "================================================================================"
    echo "❌ ${TEST_NAME} Integration Test: FAILED"
    echo "================================================================================"
    exit 1
fi

log_success "HTTP 200 OK"

if ! echo "$RESPONSE" | jq . > /dev/null 2>&1; then
    log_error "Invalid JSON response"
    exit 1
fi
log_success "Valid JSON response"

ACTUAL_USER=$(echo "$RESPONSE" | jq -r '."user-name" // empty')

if [[ -z "$ACTUAL_USER" ]]; then
    log_error "No user-name field in response"
    exit 1
fi

if echo "$ACTUAL_USER" | grep -q "$EXPECTED_USER_PATTERN"; then
    log_success "User: $ACTUAL_USER (Kerberos identity verified)"
else
    log_error "User mismatch: expected pattern '$EXPECTED_USER_PATTERN', got '$ACTUAL_USER'"
    exit 1
fi

log_success "Authentication succeeded (not anonymous)"

echo
echo "================================================================================"
echo "✅ ${TEST_NAME} Integration Test: PASSED"
echo "================================================================================"
exit 0
