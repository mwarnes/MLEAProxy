#!/bin/bash

# ================================================================
# MLEAProxy MarkLogic Integration Test Utilities
# ================================================================
#
# Shared utility functions for MarkLogic integration tests.
# This file is sourced by protocol-specific test scripts.
#
# Author: Martin Warnes
# Version: 1.0.0
# Date: July 2026
#
# ================================================================

set -euo pipefail

# ================================================================
# Configuration
# ================================================================

MARKLOGIC_HOST="${MARKLOGIC_HOST:-localhost}"
MARKLOGIC_PORT="${MARKLOGIC_PORT:-8002}"
MARKLOGIC_ADMIN_USER="${MARKLOGIC_ADMIN_USER:-admin}"
MARKLOGIC_ADMIN_PASS="${MARKLOGIC_ADMIN_PASS:-admin}"

MLEAPROXY_HOST="${MLEAPROXY_HOST:-localhost}"
MLEAPROXY_PORT="${MLEAPROXY_PORT:-8080}"

MARKLOGIC_BASE="http://${MARKLOGIC_HOST}:${MARKLOGIC_PORT}"
MLEAPROXY_BASE="http://${MLEAPROXY_HOST}:${MLEAPROXY_PORT}"

# ================================================================
# Utility Functions
# ================================================================

log_info() {
    echo "  $1"
}

log_success() {
    echo "  ✓ $1"
}

log_error() {
    echo "  ✗ $1"
}

log_step() {
    echo "  → $1"
}

# ================================================================
# Prerequisites Check
# ================================================================

check_prerequisites() {
    local prereq_kerberos="${1:-false}"
    
    log_info "Prerequisites:"
    
    # Check MarkLogic Server
    if curl -sf -u "${MARKLOGIC_ADMIN_USER}:${MARKLOGIC_ADMIN_PASS}" \
        "${MARKLOGIC_BASE}/manage/LATEST" > /dev/null 2>&1; then
        log_success "MarkLogic Server accessible (${MARKLOGIC_BASE})"
    else
        log_error "MarkLogic Server not accessible at ${MARKLOGIC_BASE}"
        exit 3
    fi
    
    # Check MLEAProxy
    if curl -sf "${MLEAPROXY_BASE}/status" > /dev/null 2>&1; then
        log_success "MLEAProxy running (${MLEAPROXY_BASE})"
    else
        log_error "MLEAProxy not running at ${MLEAPROXY_BASE}"
        log_error "Start MLEAProxy: java -jar target/mlesproxy-2.0.3.jar"
        exit 3
    fi
    
    # Check Manage AppServer template exists
    if curl -sf -u "${MARKLOGIC_ADMIN_USER}:${MARKLOGIC_ADMIN_PASS}" \
        "${MARKLOGIC_BASE}/manage/LATEST/servers/Manage?format=json" > /dev/null 2>&1; then
        log_success "Manage template available (port 8002)"
    else
        log_error "Manage AppServer not found - cannot use as template"
        exit 3
    fi
    
    # Check for Kerberos ticket if required
    if [[ "$prereq_kerberos" == "true" ]]; then
        if klist -s 2>/dev/null; then
            log_success "Kerberos ticket available"
        else
            log_error "No Kerberos ticket found"
            log_error "Run: kinit mluser1@MARKLOGIC.LOCAL"
            exit 3
        fi
    fi
    
    echo
}

# ================================================================
# AppServer Management
# ================================================================

get_manage_config() {
    curl -sf -u "${MARKLOGIC_ADMIN_USER}:${MARKLOGIC_ADMIN_PASS}" \
        "${MARKLOGIC_BASE}/manage/LATEST/servers/Manage?format=json"
}

create_test_appserver() {
    local name="$1"
    local port="$2"
    local auth_type="$3"
    local authz_scheme="$4"
    local external_security="$5"
    
    # Delete existing AppServer first (idempotent)
    delete_test_appserver "$name" > /dev/null 2>&1 || true
    
    # Get Manage config as template
    local manage_config
    manage_config=$(get_manage_config)
    
    # Modify config for test AppServer
    local test_config
    test_config=$(echo "$manage_config" | jq \
        --arg name "$name" \
        --arg port "$port" \
        --arg auth "$auth_type" \
        --arg authz "$authz_scheme" \
        --arg external "$external_security" \
        '
        .["server-name"] = $name |
        .port = ($port | tonumber) |
        .authentication = $auth |
        .authorization = $authz |
        .["external-security"] = $external |
        del(.["server-id"])
        ')
    
    # Create AppServer
    echo "$test_config" | curl -sf -u "${MARKLOGIC_ADMIN_USER}:${MARKLOGIC_ADMIN_PASS}" \
        -X POST \
        -H "Content-Type: application/json" \
        -d @- \
        "${MARKLOGIC_BASE}/manage/LATEST/servers" > /dev/null
}

delete_test_appserver() {
    local name="$1"
    curl -sf -u "${MARKLOGIC_ADMIN_USER}:${MARKLOGIC_ADMIN_PASS}" \
        -X DELETE \
        "${MARKLOGIC_BASE}/manage/LATEST/servers/${name}?group-id=Default" > /dev/null 2>&1 || true
}

wait_for_appserver() {
    local port="$1"
    local max_wait="${2:-10}"
    local waited=0
    
    log_step "Waiting for AppServer to be ready..."
    
    while [ $waited -lt $max_wait ]; do
        if curl -sf "http://localhost:${port}" > /dev/null 2>&1; then
            log_success "AppServer ready (${waited}s)"
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done
    
    log_error "AppServer not ready after ${max_wait}s"
    return 1
}

# ================================================================
# External Security Management
# ================================================================

create_external_security() {
    local name="$1"
    local type="$2"
    local xml_config="$3"
    
    # Delete existing config first (idempotent)
    delete_external_security "$name" > /dev/null 2>&1 || true
    
    # Create external security
    echo "$xml_config" | curl -sf -u "${MARKLOGIC_ADMIN_USER}:${MARKLOGIC_ADMIN_PASS}" \
        -X POST \
        -H "Content-Type: application/xml" \
        -d @- \
        "${MARKLOGIC_BASE}/manage/LATEST/external-security" > /dev/null
}

delete_external_security() {
    local name="$1"
    curl -sf -u "${MARKLOGIC_ADMIN_USER}:${MARKLOGIC_ADMIN_PASS}" \
        -X DELETE \
        "${MARKLOGIC_BASE}/manage/LATEST/external-security/${name}" > /dev/null 2>&1 || true
}

# ================================================================
# Response Verification
# ================================================================

verify_json_response() {
    local response_body="$1"
    local expected_user="$2"
    shift 2
    local expected_roles=("$@")
    
    # Verify valid JSON
    if ! echo "$response_body" | jq . > /dev/null 2>&1; then
        log_error "Invalid JSON response"
        return 1
    fi
    log_success "Valid JSON response"
    
    # Extract user-name
    local actual_user
    actual_user=$(echo "$response_body" | jq -r '."user-name" // empty')
    
    if [[ -z "$actual_user" ]]; then
        log_error "No user-name field in response"
        return 1
    fi
    
    if [[ "$actual_user" == "$expected_user" ]]; then
        log_success "User: $actual_user"
    else
        log_error "User mismatch: expected '$expected_user', got '$actual_user'"
        return 1
    fi
    
    # Extract roles array
    local actual_roles
    actual_roles=$(echo "$response_body" | jq -r '.roles[]?' 2>/dev/null || echo "")
    
    # Verify expected roles present (subset check)
    local all_found=true
    for expected_role in "${expected_roles[@]}"; do
        if echo "$response_body" | jq -e --arg role "$expected_role" '.roles[]? | select(. == $role)' > /dev/null 2>&1; then
            continue
        else
            log_error "Role '$expected_role' not found in response"
            all_found=false
        fi
    done
    
    if [[ "$all_found" == "true" ]]; then
        log_success "Roles: [${expected_roles[*]}]"
        return 0
    else
        return 1
    fi
}
