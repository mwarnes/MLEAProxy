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
    
    if [ ! -f "$PROJECT_ROOT/mlesproxy-2.0.3.jar" ]; then
        echo -e "${RED}✗ JAR file not found: mlesproxy-2.0.3.jar${NC}"
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

if [ "$KERBEROS_AVAILABLE" = true ]; then
    echo "  1) All protocols (LDAP, OAuth, SAML, Kerberos)"
else
    echo "  1) All protocols (LDAP, OAuth, SAML, Kerberos) [DISABLED - no keytab]"
fi

echo "  2) LDAP only"
echo "  3) OAuth only"
echo "  4) SAML only"

if [ "$KERBEROS_AVAILABLE" = true ]; then
    echo "  5) Kerberos only"
else
    echo "  5) Kerberos only [DISABLED - no keytab]"
fi

echo "  6) LDAP, OAuth, SAML (no Kerberos)"
echo "  7) Exit"
echo ""
read -p "Enter choice [1-7]: " choice

case $choice in
    1)
        if [ "$KERBEROS_AVAILABLE" = false ]; then
            echo ""
            echo -e "${RED}✗ Kerberos keytab required${NC}"
            echo ""
            echo "Create a keytab first:"
            echo "  ./scripts/create-keytab.sh"
            echo ""
            echo "Or choose option 6 to start without Kerberos"
            exit 1
        fi
        echo ""
        echo -e "${BLUE}Starting all protocols...${NC}"
        "$SCRIPT_DIR/start-all.sh"
        ;;
    2)
        echo ""
        echo -e "${BLUE}Starting LDAP...${NC}"
        "$SCRIPT_DIR/start-ldap.sh"
        ;;
    3)
        echo ""
        echo -e "${BLUE}Starting OAuth...${NC}"
        "$SCRIPT_DIR/start-oauth.sh"
        ;;
    4)
        echo ""
        echo -e "${BLUE}Starting SAML...${NC}"
        "$SCRIPT_DIR/start-saml.sh"
        ;;
    5)
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
        ;;
    6)
        echo ""
        echo -e "${BLUE}Starting LDAP, OAuth, SAML (without Kerberos)...${NC}"
        echo ""
        "$SCRIPT_DIR/start-ldap.sh" &
        LDAP_PID=$!
        sleep 2
        "$SCRIPT_DIR/start-oauth.sh" &
        OAUTH_PID=$!
        sleep 2
        "$SCRIPT_DIR/start-saml.sh" &
        SAML_PID=$!
        
        # Wait for all to start
        wait $LDAP_PID $OAUTH_PID $SAML_PID 2>/dev/null || true
        ;;
    7)
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

echo ""
echo -e "${GREEN}================================================================================${NC}"
echo -e "${GREEN}Server started successfully!${NC}"
echo -e "${GREEN}================================================================================${NC}"
echo ""
echo "Next steps:"
echo "  • Check status:  ./scripts/status.sh"
echo "  • Web interface: http://localhost:8080/status"
echo "  • Stop server:   ./scripts/stop.sh"
echo ""
