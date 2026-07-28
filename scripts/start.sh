#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}================================================================================${NC}"
echo -e "${BLUE}                    MLEAProxy Interactive Launcher${NC}"
echo -e "${BLUE}================================================================================${NC}"
echo ""

check_prerequisites() {
    echo "Checking prerequisites..."
    
    if ! command -v java &> /dev/null; then
        echo -e "${RED}✗ Java not found${NC}"
        echo "  Install Java 21 or higher"
        exit 2
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}')
    JAVA_MAJOR=$(echo "$JAVA_VERSION" | cut -d'.' -f1)
    
    if [ "$JAVA_MAJOR" -lt 21 ]; then
        echo -e "${RED}✗ Java $JAVA_VERSION found, but Java 21+ required${NC}"
        exit 2
    fi
    
    echo -e "${GREEN}✓ Java $JAVA_VERSION${NC}"
    
    # Check for JAR in distribution or build locations
    JAR_FILE=""
    if [ -f "$PROJECT_ROOT/mlesproxy-2.0.3.jar" ]; then
        JAR_FILE="$PROJECT_ROOT/mlesproxy-2.0.3.jar"
    elif [ -f "$PROJECT_ROOT/target/mlesproxy-2.0.3.jar" ]; then
        JAR_FILE="$PROJECT_ROOT/target/mlesproxy-2.0.3.jar"
    elif [ -f "$PROJECT_ROOT/release/mlesproxy-2.0.3.jar" ]; then
        JAR_FILE="$PROJECT_ROOT/release/mlesproxy-2.0.3.jar"
    fi
    
    if [ -z "$JAR_FILE" ]; then
        echo -e "${RED}✗ JAR file not found${NC}"
        echo "  Expected: mlesproxy-2.0.3.jar"
        exit 2
    fi
    
    echo -e "${GREEN}✓ JAR file found${NC}"
    
    if [ ! -f "$PROJECT_ROOT/users.json" ]; then
        echo -e "${YELLOW}⚠ users.json not found (using defaults)${NC}"
    else
        echo -e "${GREEN}✓ users.json found${NC}"
    fi
    
    # Check for Kerberos keytab
    if ls "$PROJECT_ROOT/kerberos/keytabs"/*.keytab &> /dev/null; then
        echo -e "${GREEN}✓ Kerberos keytab found${NC}"
        KERBEROS_AVAILABLE=true
    else
        echo -e "${YELLOW}⚠ Kerberos keytab not found${NC}"
        echo -e "  ${YELLOW}To enable Kerberos: ./scripts/create-keytab.sh${NC}"
        KERBEROS_AVAILABLE=false
    fi
    
    echo ""
}

check_prerequisites

echo "Which protocol(s) would you like to start?"
echo ""
echo "  1) LDAP, OAuth, SAML (no Kerberos) - Recommended"
echo "  2) OAuth, SAML only (no LDAP, no Kerberos)"

if [ "$KERBEROS_AVAILABLE" = true ]; then
    echo "  3) All protocols (LDAP, OAuth, SAML, Kerberos)"
else
    echo "  3) All protocols (LDAP, OAuth, SAML, Kerberos) [DISABLED - no keytab]"
fi

echo "  4) LDAP only"
echo "  5) OAuth only"
echo "  6) SAML only"

if [ "$KERBEROS_AVAILABLE" = true ]; then
    echo "  7) Kerberos only"
else
    echo "  7) Kerberos only [DISABLED - no keytab]"
fi

echo "  8) Exit"
echo ""
read -p "Enter choice [1-8]: " choice

# Detect hostname for display
get_hostname() {
    local hostname=$(hostname -f 2>/dev/null || hostname 2>/dev/null || echo "localhost")
    if [[ "$hostname" == "localhost" ]] || [[ "$hostname" == "localhost.localdomain" ]]; then
        hostname="localhost"
    fi
    echo "$hostname"
}

HOSTNAME=$(get_hostname)

# Helper function to start server with specific configs
start_with_configs() {
    local configs="$1"
    local description="$2"
    
    cd "$PROJECT_ROOT"
    
    # Check if already running
    if pgrep -f "mlesproxy.*jar" > /dev/null; then
        echo -e "${YELLOW}Warning: MLEAProxy is already running${NC}"
        exit 1
    fi
    
    # Copy example configs if they exist
    if [[ "$configs" == *"ldap"* ]] && [ -d "examples/ldap" ] && [ -f "examples/ldap/01-standalone-json-server.properties" ]; then
        cp examples/ldap/01-standalone-json-server.properties ldap.properties
    fi
    if [[ "$configs" == *"oauth"* ]] && [ -d "examples/oauth" ] && [ -f "examples/oauth/01-oauth-basic.properties" ]; then
        cp examples/oauth/01-oauth-basic.properties oauth.properties
    fi
    if [[ "$configs" == *"saml"* ]] && [ -d "examples/saml" ] && [ -f "examples/saml/01-saml-basic.properties" ]; then
        cp examples/saml/01-saml-basic.properties saml.properties
    fi
    
    # Start with specified configs
    java -Dspring.config.location=classpath:/application.properties,$configs \
         -jar "$JAR_FILE" \
         --users-json=./users.json \
         > mleaproxy.log 2>&1 &
    
    PID=$!
    echo $PID > mleaproxy.pid
    sleep 5
    
    if ps -p $PID > /dev/null; then
        echo -e "${GREEN}MLEAProxy started successfully!${NC}"
        echo "PID: $PID"
        echo ""
        
        # Display relevant endpoints based on what's configured
        if [[ "$configs" == *"ldap"* ]]; then
            echo "LDAP Endpoints:"
            echo "  - Proxy: ldap://$HOSTNAME:10389"
            echo "  - In-memory: ldap://$HOSTNAME:60389"
            echo ""
        fi
        
        if [[ "$configs" == *"oauth"* ]]; then
            echo "OAuth Endpoints:"
            echo "  - Token: http://$HOSTNAME:8080/oauth/token"
            echo "  - JWKS: http://$HOSTNAME:8080/oauth/jwks"
            echo ""
        fi
        
        if [[ "$configs" == *"saml"* ]]; then
            echo "SAML Endpoints:"
            echo "  - Auth: http://$HOSTNAME:8080/saml/auth"
            echo "  - Metadata: http://$HOSTNAME:8080/saml/metadata"
            echo ""
        fi
        
        echo "Status Page: http://$HOSTNAME:8080/status"
        echo ""
        echo "Stop with: ./scripts/stop.sh"
        echo "View logs: tail -f mleaproxy.log"
    else
        echo -e "${RED}Error: MLEAProxy failed to start${NC}"
        echo "Check logs: cat mleaproxy.log"
        exit 1
    fi
}

case $choice in
    1)
        echo ""
        echo -e "${BLUE}Starting LDAP, OAuth, SAML (without Kerberos)...${NC}"
        start_with_configs "./ldap.properties,./oauth.properties,./saml.properties" "LDAP, OAuth, SAML"
        ;;
    2)
        echo ""
        echo -e "${BLUE}Starting OAuth, SAML (without LDAP or Kerberos)...${NC}"
        start_with_configs "./oauth.properties,./saml.properties" "OAuth, SAML"
        ;;
    3)
        if [ "$KERBEROS_AVAILABLE" = false ]; then
            echo ""
            echo -e "${RED}✗ Kerberos keytab required${NC}"
            echo ""
            echo "Create a keytab first:"
            echo "  ./scripts/create-keytab.sh"
            echo ""
            echo "Or choose option 1 or 2 to start without Kerberos"
            exit 1
        fi
        echo ""
        echo -e "${BLUE}Starting all protocols...${NC}"
        "$SCRIPT_DIR/start-all.sh"
        exit 0
        ;;
    4)
        echo ""
        echo -e "${BLUE}Starting LDAP...${NC}"
        "$SCRIPT_DIR/start-ldap.sh"
        exit 0
        ;;
    5)
        echo ""
        echo -e "${BLUE}Starting OAuth...${NC}"
        "$SCRIPT_DIR/start-oauth.sh"
        exit 0
        ;;
    6)
        echo ""
        echo -e "${BLUE}Starting SAML...${NC}"
        "$SCRIPT_DIR/start-saml.sh"
        exit 0
        ;;
    7)
        if [ "$KERBEROS_AVAILABLE" = false ]; then
            echo ""
            echo -e "${RED}✗ Kerberos keytab required${NC}"
            echo ""
            echo "Create a keytab first:"
            echo "  ./scripts/create-keytab.sh"
            exit 1
        fi
        echo ""
        echo -e "${BLUE}Starting Kerberos...${NC}"
        "$SCRIPT_DIR/start-kerberos.sh"
        exit 0
        ;;
    8)
        echo ""
        echo "Exiting..."
        exit 0
        ;;
    *)
        echo ""
        echo -e "${RED}Invalid choice${NC}"
        exit 1
        ;;
esac
