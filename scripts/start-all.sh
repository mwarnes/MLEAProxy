#!/bin/bash
# ================================================================
# MLEAProxy - Start All Protocols
# ================================================================
# Starts MLEAProxy with LDAP + OAuth + SAML + Kerberos enabled
# ================================================================

LOG_FILE="mleaproxy-all.log"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0;m'

# Detect hostname (prefer FQDN, fallback to simple hostname)
get_hostname() {
    local hostname=$(hostname -f 2>/dev/null || hostname 2>/dev/null || echo "localhost")
    # Filter out localhost variants
    if [[ "$hostname" == "localhost" ]] || [[ "$hostname" == "localhost.localdomain" ]] || [[ "$hostname" =~ ^127\. ]]; then
        hostname="localhost"
    fi
    echo "$hostname"
}

HOSTNAME=$(get_hostname)

# Find JAR file (check distribution location first)
if [ -f "mlesproxy-2.0.3.jar" ]; then
    JAR_FILE="mlesproxy-2.0.3.jar"
elif [ -f "target/mlesproxy-2.0.3.jar" ]; then
    JAR_FILE="target/mlesproxy-2.0.3.jar"
elif [ -f "release/mlesproxy-2.0.3.jar" ]; then
    JAR_FILE="release/mlesproxy-2.0.3.jar"
else
    echo -e "${RED}Error: MLEAProxy JAR not found${NC}"
    echo "Please build the project first: ./build.sh clean package"
    exit 1
fi

if pgrep -f "mlesproxy.*jar" > /dev/null; then
    echo -e "${YELLOW}Warning: MLEAProxy is already running${NC}"
    exit 1
fi

echo -e "${GREEN}Starting MLEAProxy with all protocols...${NC}"

# Copy all configuration files
cp examples/ldap/01-standalone-json-server.properties ldap.properties
cp examples/oauth/01-oauth-basic.properties oauth.properties
cp examples/saml/01-saml-basic.properties saml.properties
cp examples/kerberos/01-kerberos-basic.properties kerberos.properties

# Start with all configs
java -Dspring.config.location=classpath:/application.properties,./ldap.properties,./oauth.properties,./saml.properties,./kerberos.properties \
     -jar "$JAR_FILE" \
     --users-json=./users.json \
     > "$LOG_FILE" 2>&1 &

PID=$!
echo $PID > mleaproxy.pid
sleep 5

if ps -p $PID > /dev/null; then
    echo -e "${GREEN}MLEAProxy started successfully with all protocols!${NC}"
    echo "PID: $PID"
    echo ""
    echo "LDAP Endpoints:"
    echo "  - Proxy: ldap://$HOSTNAME:10389"
    echo "  - In-memory: ldap://$HOSTNAME:60389"
    echo ""
    echo "OAuth Endpoints:"
    echo "  - Token: http://$HOSTNAME:8080/oauth/token"
    echo "  - JWKS: http://$HOSTNAME:8080/oauth/jwks"
    echo ""
    echo "SAML Endpoints:"
    echo "  - Auth: http://$HOSTNAME:8080/saml/auth"
    echo "  - Metadata: http://$HOSTNAME:8080/saml/metadata"
    echo ""
    echo "Kerberos Endpoints:"
    echo "  - KDC: $HOSTNAME:8088"
    echo "  - Auth: http://$HOSTNAME:8080/kerberos/auth"
    echo ""
    echo "Status Page: http://$HOSTNAME:8080/status"
    echo ""
    echo "Stop with: ./scripts/stop.sh"
    echo "View logs: tail -f $LOG_FILE"
else
    echo -e "${RED}Error: MLEAProxy failed to start${NC}"
    exit 1
fi
