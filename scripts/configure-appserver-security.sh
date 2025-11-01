#!/bin/bash

# ================================================================
# MarkLogic App Server Security Configuration Script
# ================================================================
# 
# This script configures MarkLogic app servers to use external security
# configurations (OAuth, SAML, LDAP, etc.) via the Management REST API
# 
# Features:
# - Updates app server external security configuration
# - Supports multiple app servers in one run
# - Validates external security configurations exist
# - Shows current app server configuration
# - Dry-run mode for testing
#
# Author: MLEAProxy Development Team
# Version: 1.0.0
# Date: October 2025
#
# Usage:
#   ./configure-appserver-security.sh [OPTIONS]
#
# Examples:
#   # Configure single app server
#   ./configure-appserver-security.sh --appserver App-Services --external-security MLEAProxy-OAuth
#
#   # Configure multiple app servers
#   ./configure-appserver-security.sh --appserver "App-Services,Documents" --external-security MLEAProxy-OAuth
#
#   # Show current configuration
#   ./configure-appserver-security.sh --appserver App-Services --show-current
#
#   # Remove external security (set to none)
#   ./configure-appserver-security.sh --appserver App-Services --remove-security
#
# ================================================================

set -euo pipefail

# ================================================================
# CONFIGURATION VARIABLES
# ================================================================

# Default values
MARKLOGIC_HOST="localhost"
MARKLOGIC_PORT="8002"
MARKLOGIC_USER="admin"
MARKLOGIC_PASS="admin"
APPSERVER_NAMES=""
EXTERNAL_SECURITY=""
SHOW_CURRENT="false"
REMOVE_SECURITY="false"
LIST_APPSERVERS="false"
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

log_verbose() {
    if [ "$VERBOSE" = "true" ]; then
        echo -e "${CYAN}[DEBUG]${NC} $1" >&2
    fi
}

show_usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Configures MarkLogic app servers to use external security configurations.

OPTIONS:
    --appserver NAMES             App server name(s) - comma-separated for multiple (required)
    --external-security NAME      External security configuration name
    --show-current                Show current app server security configuration
    --remove-security             Remove external security (set authentication to basic)
    --list-appservers             List all available app servers
    --marklogic-host HOST         MarkLogic host (default: localhost)
    --marklogic-port PORT         MarkLogic manage port (default: 8002)
    --marklogic-user USER         MarkLogic admin user (default: admin)
    --marklogic-pass PASS         MarkLogic admin password (default: admin)
    --verbose                     Enable verbose logging
    --dry-run                     Show what would be done without executing
    --help                        Show this help message

EXAMPLES:
    # Configure single app server with OAuth
    $0 --appserver App-Services --external-security MLEAProxy-OAuth

    # Configure multiple app servers
    $0 --appserver "App-Services,Documents,Admin" --external-security SAML-Config

    # Show current configuration
    $0 --appserver App-Services --show-current

    # Remove external security from app server
    $0 --appserver App-Services --remove-security

    # Dry run to see what would be changed
    $0 --appserver App-Services --external-security OAuth-Config --dry-run

ENVIRONMENT VARIABLES:
    MARKLOGIC_HOST               Override default MarkLogic host
    MARKLOGIC_USER               Override default MarkLogic user
    MARKLOGIC_PASS               Override default MarkLogic password

EOF
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
    
    local test_url="http://$MARKLOGIC_HOST:$MARKLOGIC_PORT"
    local response status_code
    
    response=$(curl -s -w "%{http_code}" -m 10 --connect-timeout 5 "$test_url" 2>/dev/null)
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
# APP SERVER CONFIGURATION FUNCTIONS
# ================================================================

# List available app servers
list_appservers() {
    log_info "Available app servers on $MARKLOGIC_HOST:"
    
    local servers_url="http://$MARKLOGIC_HOST:$MARKLOGIC_PORT/manage/v2/servers"
    local response status_code
    
    response=$(curl -s -w "%{http_code}" -m 30 --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
        -H "Accept: application/json" \
        "$servers_url" 2>/dev/null)
    
    status_code="${response: -3}"
    local servers_body="${response%???}"
    
    case "$status_code" in
        200)
            echo "$servers_body" | jq -r '.["server-default-list"]["list-items"]["list-item"][].nameref' 2>/dev/null | sort || {
                log_warning "Could not parse server list"
                echo "$servers_body"
            }
            ;;
        *)
            log_error "Failed to list app servers (HTTP $status_code)"
            echo "$servers_body" | jq . 2>/dev/null || echo "$servers_body" >&2
            ;;
    esac
}

# Get server ID from server name
get_server_id() {
    local appserver_name="$1"
    local servers_url="http://$MARKLOGIC_HOST:$MARKLOGIC_PORT/manage/v2/servers"
    
    log_verbose "Looking up server ID for: $appserver_name"
    
    local response status_code
    response=$(curl -s -w "%{http_code}" -m 30 --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
        -H "Accept: application/json" \
        "$servers_url" 2>/dev/null)
    
    status_code="${response: -3}"
    local servers_body="${response%???}"
    
    if [ "$status_code" = "200" ]; then
        local server_id
        server_id=$(echo "$servers_body" | jq -r --arg name "$appserver_name" \
            '.["server-default-list"]["list-items"]["list-item"][] | select(.nameref == $name) | .idref' 2>/dev/null)
        
        if [ "$server_id" != "" ] && [ "$server_id" != "null" ]; then
            echo "$server_id"
            return 0
        fi
    fi
    
    log_verbose "Could not find server ID for: $appserver_name"
    return 1
}

# Get current app server configuration
get_appserver_config() {
    local appserver_name="$1"
    local group_name="${2:-Default}"
    
    # MarkLogic Management API requires group-id parameter
    local appserver_url="http://$MARKLOGIC_HOST:$MARKLOGIC_PORT/manage/v2/servers/$appserver_name/properties?group-id=$group_name"
    log_verbose "Getting configuration for app server: $appserver_name (group: $group_name)"
    
    local response status_code
    response=$(curl -s -w "%{http_code}" -m 30 --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
        -H "Accept: application/json" \
        "$appserver_url" 2>/dev/null)
    
    status_code="${response: -3}"
    local config_body="${response%???}"
    
    log_verbose "App server get response code: $status_code"
    
    case "$status_code" in
        200)
            echo "$config_body"
            return 0
            ;;
        404)
            log_error "App server '$appserver_name' not found"
            return 1
            ;;
        000)
            log_error "Connection failed to MarkLogic management API"
            return 1
            ;;
        *)
            log_error "Failed to get app server configuration (HTTP $status_code)"
            if [ "$status_code" = "400" ]; then
                log_error "This usually means the app server name is incorrect or requires different parameters"
                log_error "Available app servers:"
                list_appservers
            fi
            echo "$config_body" | jq . 2>/dev/null || echo "$config_body" >&2
            return 1
            ;;
    esac
}

# Show current app server security configuration
show_appserver_security() {
    local appserver_name="$1"
    
    log_info "Current security configuration for app server: $appserver_name"
    
    local config
    config=$(get_appserver_config "$appserver_name") || return 1
    
    local authentication external_security
    authentication=$(echo "$config" | jq -r '.authentication // "basic"')
    external_security=$(echo "$config" | jq -r '."external-security" // "none"')
    
    echo
    echo "  Authentication: $authentication"
    echo "  External Security: $external_security"
    
    if [ "$external_security" != "none" ] && [ "$external_security" != "null" ]; then
        # Try to get details about the external security configuration
        local ext_sec_url="http://$MARKLOGIC_HOST:$MARKLOGIC_PORT/manage/v2/external-security/$external_security"
        local ext_response ext_status
        
        ext_response=$(curl -s -w "%{http_code}" -m 10 --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
            "$ext_sec_url" 2>/dev/null)
        ext_status="${ext_response: -3}"
        
        if [ "$ext_status" = "200" ]; then
            local ext_body="${ext_response%???}"
            local auth_type description
            auth_type=$(echo "$ext_body" | jq -r '.authentication // "unknown"')
            description=$(echo "$ext_body" | jq -r '.description // ""')
            
            echo "  External Security Type: $auth_type"
            if [ "$description" != "" ]; then
                echo "  Description: $description"
            fi
        fi
    fi
    echo
}

# Validate external security configuration exists
validate_external_security() {
    local config_name="$1"
    
    log_verbose "Validating external security configuration: $config_name"
    
    local ext_sec_url="http://$MARKLOGIC_HOST:$MARKLOGIC_PORT/manage/v2/external-security/$config_name"
    local response status_code
    
    response=$(curl -s -w "%{http_code}" -m 10 --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
        "$ext_sec_url" 2>/dev/null)
    status_code="${response: -3}"
    
    case "$status_code" in
        200)
            log_verbose "External security configuration '$config_name' exists"
            return 0
            ;;
        404)
            log_error "External security configuration '$config_name' not found"
            log_error "Please create the external security configuration first"
            return 1
            ;;
        *)
            log_error "Failed to validate external security configuration (HTTP $status_code)"
            return 1
            ;;
    esac
}

# Configure app server security
configure_appserver_security() {
    local appserver_name="$1"
    local config_name="$2"
    local remove_security="$3"
    
    local appserver_url="http://$MARKLOGIC_HOST:$MARKLOGIC_PORT/manage/v2/servers/$appserver_name/properties?group-id=Default"
    
    if [ "$remove_security" = "true" ]; then
        log_info "Removing external security from app server: $appserver_name"
    else
        log_info "Configuring app server '$appserver_name' to use external security '$config_name'"
    fi
    
    # Get current configuration first to preserve required fields
    log_verbose "Getting current app server configuration..."
    local current_config
    current_config=$(get_appserver_config "$appserver_name") || return 1
    
    # Extract required fields from current configuration
    local server_group server_name port
    server_group=$(echo "$current_config" | jq -r '."group-name" // "Default"')
    server_name=$(echo "$current_config" | jq -r '."server-name" // "'$appserver_name'"')
    port=$(echo "$current_config" | jq -r '.port // 8000')
    
    log_verbose "Current server group: $server_group"
    log_verbose "Current server name: $server_name"
    log_verbose "Current port: $port"
    
    if [ "$DRY_RUN" = "true" ]; then
        log_info "DRY RUN - App server configuration that would be executed:"
        echo
        if [ "$remove_security" = "true" ]; then
            cat << EOF
curl -X PUT \\
  --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \\
  -H "Content-Type: application/json" \\
  -d '@-' \\
  "$appserver_url" << 'JSON_PAYLOAD'
{
  "server-name": "$server_name",
  "group-name": "$server_group",
  "port": $port,
  "authentication": "basic",
  "external-security": null
}
JSON_PAYLOAD
EOF
        else
            cat << EOF
curl -X PUT \\
  --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \\
  -H "Content-Type: application/json" \\
  -d '@-' \\
  "$appserver_url" << 'JSON_PAYLOAD'
{
  "server-name": "$server_name",
  "group-name": "$server_group",
  "port": $port,
  "authentication": "oauth",
  "external-security": "$config_name"
}
JSON_PAYLOAD
EOF
        fi
        echo
        return 0
    fi
    
    # Validate external security exists (unless removing)
    if [ "$remove_security" != "true" ]; then
        validate_external_security "$config_name" || return 1
    fi
    
    # Prepare the update payload with required fields
    local update_payload
    if [ "$remove_security" = "true" ]; then
        update_payload=$(cat << EOF
{
  "server-name": "$server_name",
  "group-name": "$server_group",
  "port": $port,
  "authentication": "basic",
  "external-security": null
}
EOF
        )
    else
        update_payload=$(cat << EOF
{
  "server-name": "$server_name",
  "group-name": "$server_group",
  "port": $port,
  "authentication": "oauth",
  "external-security": "$config_name"
}
EOF
        )
    fi
    
    log_verbose "Updating app server security configuration..."
    log_verbose "Payload: $update_payload"
    
    local response status_code
    response=$(curl -s -w "%{http_code}" -m 30 --anyauth -u "$MARKLOGIC_USER:$MARKLOGIC_PASS" \
        -H "Content-Type: application/json" \
        -X PUT \
        -d "$update_payload" \
        "$appserver_url" 2>/dev/null)
    
    status_code="${response: -3}"
    local response_body="${response%???}"
    
    log_verbose "App server update response code: $status_code"
    log_verbose "App server update response body: $response_body"
    
    case "$status_code" in
        204|200)
            if [ "$remove_security" = "true" ]; then
                log_success "Removed external security from app server '$appserver_name'"
            else
                log_success "App server '$appserver_name' configured to use external security '$config_name'"
            fi
            return 0
            ;;
        400)
            log_error "Bad request - check app server name and external security configuration"
            echo "$response_body" | jq . 2>/dev/null || echo "$response_body" >&2
            return 1
            ;;
        404)
            log_error "App server '$appserver_name' not found"
            return 1
            ;;
        *)
            log_error "Failed to update app server configuration (HTTP $status_code)"
            echo "$response_body" | jq . 2>/dev/null || echo "$response_body" >&2
            return 1
            ;;
    esac
}

# Process multiple app servers
process_appservers() {
    local appserver_list="$1"
    local config_name="$2"
    local remove_security="$3"
    local show_current="$4"
    
    # Split comma-separated app server names
    IFS=',' read -ra APPSERVERS <<< "$appserver_list"
    
    local success_count=0
    local total_count=${#APPSERVERS[@]}
    
    for appserver in "${APPSERVERS[@]}"; do
        # Trim whitespace
        appserver=$(echo "$appserver" | xargs)
        
        if [ "$show_current" = "true" ]; then
            show_appserver_security "$appserver" || continue
        else
            configure_appserver_security "$appserver" "$config_name" "$remove_security" || continue
        fi
        
        ((success_count++))
        
        # Add separator between app servers (except for the last one)
        if [ $success_count -lt $total_count ]; then
            echo
        fi
    done
    
    if [ "$show_current" != "true" ]; then
        log_info "Successfully processed $success_count of $total_count app servers"
    fi
    
    return 0
}

# ================================================================
# MAIN SCRIPT LOGIC
# ================================================================

# Parse command line arguments
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --appserver)
                APPSERVER_NAMES="$2"
                shift 2
                ;;
            --external-security)
                EXTERNAL_SECURITY="$2"
                shift 2
                ;;
            --show-current)
                SHOW_CURRENT="true"
                shift
                ;;
            --remove-security)
                REMOVE_SECURITY="true"
                shift
                ;;
            --list-appservers)
                LIST_APPSERVERS="true"
                shift
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
}

# Main execution function
main() {
    log_info "=== MarkLogic App Server Security Configuration Script ==="
    log_info "Version 1.0.0 - MLEAProxy Development Team"
    echo
    
    # Handle list-appservers option
    if [ "$LIST_APPSERVERS" = "true" ]; then
        check_dependencies
        echo
        test_marklogic_connection || exit 1
        echo
        list_appservers
        exit 0
    fi
    
    # Validate required parameters
    if [ -z "$APPSERVER_NAMES" ]; then
        log_error "App server name(s) required (--appserver)"
        echo
        show_usage
        exit 1
    fi
    
    if [ "$SHOW_CURRENT" != "true" ] && [ "$REMOVE_SECURITY" != "true" ] && [ -z "$EXTERNAL_SECURITY" ]; then
        log_error "External security configuration name required (--external-security)"
        log_error "Or use --show-current or --remove-security"
        echo
        show_usage
        exit 1
    fi
    
    if [ "$REMOVE_SECURITY" = "true" ] && [ -n "$EXTERNAL_SECURITY" ]; then
        log_error "Cannot use --remove-security with --external-security"
        echo
        show_usage
        exit 1
    fi
    
    # Check dependencies
    check_dependencies
    echo
    
    # Test MarkLogic connectivity (skip in dry-run mode for show-current)
    if [ "$DRY_RUN" != "true" ] || [ "$SHOW_CURRENT" != "true" ]; then
        test_marklogic_connection || exit 1
        echo
    fi
    
    # Process app servers
    process_appservers "$APPSERVER_NAMES" "$EXTERNAL_SECURITY" "$REMOVE_SECURITY" "$SHOW_CURRENT"
    
    # Summary
    if [ "$SHOW_CURRENT" != "true" ]; then
        echo
        log_success "=== Configuration Complete ==="
        log_info "MarkLogic Host: $MARKLOGIC_HOST:$MARKLOGIC_PORT"
        log_info "App Server(s): $APPSERVER_NAMES"
        
        if [ "$REMOVE_SECURITY" = "true" ]; then
            log_info "Action: Removed external security"
        else
            log_info "External Security: $EXTERNAL_SECURITY"
        fi
        
        if [ "$DRY_RUN" = "true" ]; then
            log_warning "This was a DRY RUN - no changes were made"
        fi
        
        echo
        log_info "Next steps:"
        if [ "$REMOVE_SECURITY" = "true" ]; then
            log_info "1. App server(s) now use basic authentication"
            log_info "2. Users will need MarkLogic database credentials to authenticate"
        else
            log_info "1. Test authentication with your external security provider"
            log_info "2. Verify role mapping and user permissions"
            log_info "3. Check app server logs for any authentication issues"
        fi
    fi
}

# Script entry point
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    parse_arguments "$@"
    main
fi