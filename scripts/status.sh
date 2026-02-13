#!/bin/bash
# ================================================================
# MLEAProxy - Status Script
# ================================================================
# Checks status of running MLEAProxy instance
# ================================================================

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0;m'

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BLUE}                         MLEAProxy Status Check${NC}"
echo -e "${BLUE}==============================================================================${NC}"
echo ""

# Check by PID file
if [ -f "mleaproxy.pid" ]; then
    PID=$(cat mleaproxy.pid)

    if ps -p $PID > /dev/null 2>&1; then
        echo -e "${GREEN}Status: RUNNING${NC}"
        echo "PID: $PID"

        # Get memory usage
        MEM=$(ps -p $PID -o rss= | awk '{printf "%.1f MB", $1/1024}')
        echo "Memory: $MEM"

        # Get CPU usage
        CPU=$(ps -p $PID -o %cpu= | awk '{printf "%.1f%%", $1}')
        echo "CPU: $CPU"

        # Get uptime
        START_TIME=$(ps -p $PID -o lstart=)
        echo "Started: $START_TIME"
    else
        echo -e "${RED}Status: NOT RUNNING${NC}"
        echo "PID file exists but process not found"
        echo "(PID: $PID)"
    fi
else
    # Try to find by process name
    PID=$(pgrep -f "mlesproxy.*jar")

    if [ -n "$PID" ]; then
        echo -e "${YELLOW}Status: RUNNING (no PID file)${NC}"
        echo "PID: $PID"
        MEM=$(ps -p $PID -o rss= | awk '{printf "%.1f MB", $1/1024}')
        echo "Memory: $MEM"
    else
        echo -e "${RED}Status: NOT RUNNING${NC}"
    fi
fi

echo ""
echo -e "${BLUE}------------------------------------------------------------------------------${NC}"
echo -e "${BLUE}Configuration Files:${NC}"
echo ""

# Check for config files
for config in ldap.properties oauth.properties saml.properties kerberos.properties; do
    if [ -f "$config" ]; then
        echo -e "  ${GREEN}✓${NC} $config"
    else
        echo -e "  ${RED}✗${NC} $config"
    fi
done

echo ""
echo -e "${BLUE}------------------------------------------------------------------------------${NC}"
echo -e "${BLUE}Log Files:${NC}"
echo ""

# Check for log files
for log in mleaproxy-ldap.log mleaproxy-oauth.log mleaproxy-saml.log mleaproxy-kerberos.log mleaproxy-all.log mleaproxy.log; do
    if [ -f "$log" ]; then
        SIZE=$(ls -lh "$log" | awk '{print $5}')
        MODIFIED=$(ls -l "$log" | awk '{print $6, $7, $8}')
        echo "  $log ($SIZE, modified: $MODIFIED)"
    fi
done

echo ""
echo -e "${BLUE}------------------------------------------------------------------------------${NC}"
echo -e "${BLUE}Endpoints (if running):${NC}"
echo ""
echo "  LDAP:"
echo "    - ldap://localhost:10389 (proxy)"
echo "    - ldap://localhost:60389 (in-memory)"
echo ""
echo "  HTTP (port 8080):"
echo "    - http://localhost:8080/oauth/token"
echo "    - http://localhost:8080/oauth/jwks"
echo "    - http://localhost:8080/saml/auth"
echo "    - http://localhost:8080/saml/metadata"
echo "    - http://localhost:8080/kerberos/auth"
echo ""
echo "  Kerberos KDC:"
echo "    - localhost:88"

echo ""
echo -e "${BLUE}==============================================================================${NC}"
