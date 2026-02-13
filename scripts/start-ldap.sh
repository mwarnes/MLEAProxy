#!/bin/bash
# ================================================================
# MLEAProxy - Start LDAP Mode
# ================================================================
# Starts MLEAProxy in LDAP proxy mode with standalone JSON server
# ================================================================

# Configuration
JAR_FILE="target/mlesproxy-2.0.0.jar"
CONFIG_FILE="examples/ldap/01-standalone-json-server.properties"
LOG_FILE="mleaproxy-ldap.log"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}Error: MLEAProxy JAR not found at $JAR_FILE${NC}"
    echo "Please build the project first: ./build.sh clean package"
    exit 1
fi

# Check if config exists
if [ ! -f "$CONFIG_FILE" ]; then
    echo -e "${RED}Error: Configuration file not found at $CONFIG_FILE${NC}"
    exit 1
fi

# Check if already running
if pgrep -f "mlesproxy.*jar" > /dev/null; then
    echo -e "${YELLOW}Warning: MLEAProxy is already running${NC}"
    echo "Stop it first with: ./scripts/stop.sh"
    exit 1
fi

# Start MLEAProxy
echo -e "${GREEN}Starting MLEAProxy in LDAP mode...${NC}"
echo "Configuration: $CONFIG_FILE"
echo "Log file: $LOG_FILE"
echo ""

# Copy config to root as ldap.properties
cp "$CONFIG_FILE" ldap.properties

# Start the application
java -Dspring.config.location=classpath:/application.properties,./ldap.properties \
     -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &

# Save PID
PID=$!
echo $PID > mleaproxy.pid

# Wait a moment for startup
sleep 3

# Check if still running
if ps -p $PID > /dev/null; then
    echo -e "${GREEN}╔════════════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                    MLEAProxy Started Successfully!                         ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${CYAN}Process Information:${NC}"
    echo "  PID: $PID"
    echo "  Log File: $LOG_FILE"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                           LDAP Endpoints${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}📡 LDAP Proxy Listener:${NC}"
    echo "  URL: ldap://localhost:10389"
    echo "  Description: Proxies requests to backend or processes internally"
    echo "  Mode: INTERNAL (standalone JSON authentication)"
    echo ""

    echo -e "${CYAN}📡 In-Memory LDAP Directory Server:${NC}"
    echo "  URL: ldap://localhost:60389"
    echo "  Description: Standalone LDAP directory with test data"
    echo "  Base DN: dc=MarkLogic,dc=Local"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                         Authentication Details${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}🔐 Bind Credentials (In-Memory Server):${NC}"
    echo "  Bind DN: cn=Directory Manager"
    echo "  Password: password"
    echo ""

    echo -e "${CYAN}🔐 Bind Credentials (Proxy - JSON Auth):${NC}"
    echo "  Bind DN: cn=manager,ou=users,dc=marklogic,dc=local"
    echo "  Password: password"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                            Available Users${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}👤 User: admin${NC}"
    echo "  DN: cn=admin,ou=users,dc=marklogic,dc=local"
    echo "  Password: admin"
    echo "  Roles: admin, marklogic-admin"
    echo "  sAMAccountName: admin"
    echo "  Email: admin@marklogic.local"
    echo ""

    echo -e "${CYAN}👤 User: user1${NC}"
    echo "  DN: cn=user1,ou=users,dc=marklogic,dc=local"
    echo "  Password: password"
    echo "  Roles: user, reader"
    echo "  sAMAccountName: user1"
    echo "  Email: user1@marklogic.local"
    echo ""

    echo -e "${CYAN}👤 User: user2${NC}"
    echo "  DN: cn=user2,ou=users,dc=marklogic,dc=local"
    echo "  Password: password"
    echo "  Roles: user, writer"
    echo "  sAMAccountName: user2"
    echo "  Email: user2@marklogic.local"
    echo ""

    echo -e "${CYAN}👤 User: developer${NC}"
    echo "  DN: cn=developer,ou=users,dc=marklogic,dc=local"
    echo "  Password: dev123"
    echo "  Roles: developer, user"
    echo "  sAMAccountName: developer"
    echo "  Email: dev@marklogic.local"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                          Testing Commands${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}🧪 Test In-Memory Server:${NC}"
    echo "  ldapsearch -H ldap://localhost:60389 \\"
    echo "    -D \"cn=Directory Manager\" \\"
    echo "    -w password \\"
    echo "    -b \"dc=MarkLogic,dc=Local\" \\"
    echo "    \"(objectClass=*)\""
    echo ""

    echo -e "${CYAN}🧪 Test Proxy with JSON Auth (admin):${NC}"
    echo "  ldapsearch -H ldap://localhost:10389 \\"
    echo "    -D \"cn=admin,ou=users,dc=marklogic,dc=local\" \\"
    echo "    -w admin \\"
    echo "    -b \"ou=users,dc=marklogic,dc=local\" \\"
    echo "    \"(sAMAccountName=admin)\""
    echo ""

    echo -e "${CYAN}🧪 Test Proxy with JSON Auth (user1):${NC}"
    echo "  ldapsearch -H ldap://localhost:10389 \\"
    echo "    -D \"cn=user1,ou=users,dc=marklogic,dc=local\" \\"
    echo "    -w password \\"
    echo "    -b \"ou=users,dc=marklogic,dc=local\" \\"
    echo "    \"(sAMAccountName=user1)\""
    echo ""

    echo -e "${CYAN}🧪 Search for All Users:${NC}"
    echo "  ldapsearch -H ldap://localhost:10389 \\"
    echo "    -D \"cn=manager,ou=users,dc=marklogic,dc=local\" \\"
    echo "    -w password \\"
    echo "    -b \"ou=users,dc=marklogic,dc=local\" \\"
    echo "    \"(objectClass=person)\" cn sAMAccountName mail memberOf"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                         MarkLogic Integration${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}📄 Configuration Files Generated:${NC}"
    echo "  marklogic-external-security-proxy.json"
    echo "  marklogic-external-security-proxy-instructions.txt"
    echo "  marklogic-external-security-marklogic.json"
    echo "  marklogic-external-security-marklogic-instructions.txt"
    echo ""
    echo "  Use these files to configure MarkLogic External Security"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                           Management${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo "  Stop Server: ./scripts/stop.sh"
    echo "  View Logs: tail -f $LOG_FILE"
    echo "  Check Status: ./scripts/status.sh"
    echo ""
    echo -e "${GREEN}═══════════════════════════════════════════════════════════════════════════${NC}"
else
    echo -e "${RED}Error: MLEAProxy failed to start${NC}"
    echo "Check log file: $LOG_FILE"
    exit 1
fi
