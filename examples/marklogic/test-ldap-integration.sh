#!/bin/bash

# ================================================================
# MLEAProxy LDAP Integration Test
# ================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/marklogic-utils.sh"

# Configuration
TEST_NAME="LDAP"
APPSERVER_NAME="MLEAProxy-LDAP-Test"
APPSERVER_PORT="9003"
EXTERNAL_SECURITY_NAME="MLEAProxy-LDAP"
AUTH_TYPE="ldap"
AUTHZ_SCHEME="internal"
TEST_USERNAME="admin"
TEST_PASSWORD="password"
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
  <authentication>ldap</authentication>
  <cache-timeout>300</cache-timeout>
  <authorization>internal</authorization>
  <ldap-server-uri>ldap://localhost:10389</ldap-server-uri>
  <ldap-base>dc=marklogic,dc=local</ldap-base>
  <ldap-attribute>uid</ldap-attribute>
  <ldap-bind-method>simple</ldap-bind-method>
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

create_external_security "$EXTERNAL_SECURITY_NAME" "ldap" "$EXTERNAL_SECURITY_XML"
log_success "Created external security '${EXTERNAL_SECURITY_NAME}' (ldap://localhost:10389)"

delete_test_appserver "$APPSERVER_NAME" > /dev/null 2>&1 || true
log_success "Deleted existing AppServer '${APPSERVER_NAME}'"

create_test_appserver "$APPSERVER_NAME" "$APPSERVER_PORT" "$AUTH_TYPE" "$AUTHZ_SCHEME" "$EXTERNAL_SECURITY_NAME"
log_success "Created AppServer '${APPSERVER_NAME}' on port ${APPSERVER_PORT}"

wait_for_appserver "$APPSERVER_PORT"

echo
log_info "Testing:"

TEST_URL="http://localhost:${APPSERVER_PORT}/manage/LATEST/?format=json"
log_step "Authenticating as '${TEST_USERNAME}' against ${TEST_URL}"

RESPONSE=$(curl -sf -u "${TEST_USERNAME}:${TEST_PASSWORD}" "$TEST_URL" 2>/dev/null || echo "")

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
