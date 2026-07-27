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
echo -e "${BLUE}                  Kerberos Keytab Generation Wizard${NC}"
echo -e "${BLUE}================================================================================${NC}"
echo ""
echo "This wizard will help you create a Kerberos keytab file."
echo ""
echo "What is a keytab?"
echo "  A keytab is a file containing Kerberos principals and their encrypted keys."
echo "  MLEAProxy uses it to authenticate as a service without interactive login."
echo ""

if ! command -v ktutil &> /dev/null; then
    echo -e "${RED}✗ ktutil not found${NC}"
    echo ""
    echo "ktutil is required to generate keytabs."
    echo "Install Kerberos tools:"
    echo "  • macOS: brew install krb5"
    echo "  • Linux: apt-get install krb5-user (Debian/Ubuntu)"
    echo "          yum install krb5-workstation (RHEL/CentOS)"
    exit 2
fi

echo -e "${GREEN}✓ ktutil found${NC}"
echo ""

echo "Step 1: Kerberos Realm"
echo "  The realm is your Kerberos domain (usually uppercase)"
echo "  Example: MARKLOGIC.LOCAL"
echo ""
read -p "Enter realm [MARKLOGIC.LOCAL]: " REALM
REALM=${REALM:-MARKLOGIC.LOCAL}

if ! [[ "$REALM" =~ ^[A-Z0-9.]+$ ]]; then
    echo -e "${RED}✗ Invalid realm format${NC}"
    echo "  Realm must be uppercase letters, numbers, and dots"
    exit 1
fi

echo -e "${GREEN}✓ Realm: $REALM${NC}"
echo ""

echo "Step 2: Service Principal"
echo "  The principal identifies the service"
echo "  Format: service/hostname@REALM"
echo "  Example: HTTP/rocky@MARKLOGIC.LOCAL"
echo ""

SUGGESTED_HOSTNAME=$(hostname -f 2>/dev/null || hostname)
SUGGESTED_PRINCIPAL="HTTP/${SUGGESTED_HOSTNAME}@${REALM}"

read -p "Enter principal [$SUGGESTED_PRINCIPAL]: " PRINCIPAL
PRINCIPAL=${PRINCIPAL:-$SUGGESTED_PRINCIPAL}

if ! [[ "$PRINCIPAL" =~ ^[A-Za-z0-9_-]+/[A-Za-z0-9._-]+@[A-Z0-9.]+$ ]]; then
    echo -e "${RED}✗ Invalid principal format${NC}"
    echo "  Format: service/hostname@REALM"
    exit 1
fi

echo -e "${GREEN}✓ Principal: $PRINCIPAL${NC}"
echo ""

echo "Step 3: Password"
echo "  Enter the password for this principal"
echo ""
read -s -p "Password: " PASSWORD
echo ""

if [ -z "$PASSWORD" ]; then
    echo -e "${RED}✗ Password cannot be empty${NC}"
    exit 1
fi

echo ""

mkdir -p "$PROJECT_ROOT/kerberos/keytabs"

KEYTAB_FILENAME=$(echo "$PRINCIPAL" | sed 's/\//-/g' | sed 's/@/-/g').keytab
KEYTAB_PATH="$PROJECT_ROOT/kerberos/keytabs/$KEYTAB_FILENAME"

if [ -f "$KEYTAB_PATH" ]; then
    echo -e "${YELLOW}⚠ Keytab already exists: $KEYTAB_FILENAME${NC}"
    read -p "Overwrite? [y/N]: " overwrite
    if ! [[ "$overwrite" =~ ^[Yy]$ ]]; then
        echo "Cancelled"
        exit 0
    fi
    rm "$KEYTAB_PATH"
fi

echo "Step 4: Generating keytab..."
echo ""

(
cat <<KTUTIL
addent -password -p $PRINCIPAL -k 1 -e aes256-cts-hmac-sha1-96
$PASSWORD
wkt $KEYTAB_PATH
quit
KTUTIL
) | ktutil

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Failed to generate keytab${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Keytab generated: $KEYTAB_FILENAME${NC}"
echo ""

echo "Step 5: Verifying keytab..."
if klist -k -t "$KEYTAB_PATH" &> /dev/null; then
    echo -e "${GREEN}✓ Keytab verified${NC}"
    echo ""
    klist -k -t "$KEYTAB_PATH"
    echo ""
else
    echo -e "${RED}✗ Keytab verification failed${NC}"
    exit 1
fi

echo "Step 6: Updating kerberos.properties..."

KERBEROS_PROPS="$PROJECT_ROOT/kerberos.properties"

if [ -f "$KERBEROS_PROPS" ]; then
    cp "$KERBEROS_PROPS" "$KERBEROS_PROPS.bak"
    echo -e "${GREEN}✓ Backed up to kerberos.properties.bak${NC}"
fi

RELATIVE_KEYTAB_PATH="kerberos/keytabs/$KEYTAB_FILENAME"

if [ -f "$KERBEROS_PROPS" ]; then
    sed -i.tmp "s|^mleaproxy.kerberos.principal=.*|mleaproxy.kerberos.principal=$PRINCIPAL|" "$KERBEROS_PROPS"
    sed -i.tmp "s|^mleaproxy.kerberos.keytab=.*|mleaproxy.kerberos.keytab=$RELATIVE_KEYTAB_PATH|" "$KERBEROS_PROPS"
    rm "$KERBEROS_PROPS.tmp"
else
    cat > "$KERBEROS_PROPS" << PROPEOF
# Kerberos Configuration
mleaproxy.kerberos.principal=$PRINCIPAL
mleaproxy.kerberos.keytab=$RELATIVE_KEYTAB_PATH
mleaproxy.kerberos.realm=$REALM
PROPEOF
fi

echo -e "${GREEN}✓ Updated kerberos.properties${NC}"
echo ""

echo -e "${GREEN}================================================================================${NC}"
echo -e "${GREEN}Keytab Generation Complete!${NC}"
echo -e "${GREEN}================================================================================${NC}"
echo ""
echo "Generated:"
echo "  • Keytab: $RELATIVE_KEYTAB_PATH"
echo "  • Principal: $PRINCIPAL"
echo "  • Realm: $REALM"
echo ""
echo "Next steps:"
echo "  1. Start MLEAProxy with Kerberos:"
echo "     ./scripts/start-kerberos.sh"
echo ""
echo "  2. Or use the interactive launcher:"
echo "     ./scripts/start.sh"
echo ""
