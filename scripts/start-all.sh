#!/bin/bash
# ================================================================
# MLEAProxy - Start All Protocols
# ================================================================
# Starts MLEAProxy with LDAP + OAuth + SAML + Kerberos enabled
# ================================================================

JAR_FILE="target/mlesproxy-2.0.0.jar"
LOG_FILE="mleaproxy-all.log"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0;m'

if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}Error: MLEAProxy JAR not found at $JAR_FILE${NC}"
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
     -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &

PID=$!
echo $PID > mleaproxy.pid
sleep 5

if ps -p $PID > /dev/null; then
    echo -e "${GREEN}MLEAProxy started successfully with all protocols!${NC}"
    echo "PID: $PID"
    echo ""
    echo "LDAP Endpoints:"
    echo "  - Proxy: ldap://localhost:10389"
    echo "  - In-memory: ldap://localhost:60389"
    echo ""
    echo "OAuth Endpoints:"
    echo "  - Token: http://localhost:8080/oauth/token"
    echo "  - JWKS: http://localhost:8080/oauth/jwks"
    echo ""
    echo "SAML Endpoints:"
    echo "  - Auth: http://localhost:8080/saml/auth"
    echo "  - Metadata: http://localhost:8080/saml/metadata"
    echo ""
    echo "Kerberos Endpoints:"
    echo "  - KDC: localhost:88"
    echo "  - Auth: http://localhost:8080/kerberos/auth"
    echo ""
    echo "Stop with: ./scripts/stop.sh"
    echo "View logs: tail -f $LOG_FILE"
else
    echo -e "${RED}Error: MLEAProxy failed to start${NC}"
    exit 1
fi
