#!/bin/bash
set -euo pipefail

VERSION="2.0.3"
DIST_DIR="dist-temp"
PACKAGE_NAME="mleaproxy-${VERSION}"
ZIP_NAME="${PACKAGE_NAME}-distribution.zip"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}================================================================================${NC}"
echo -e "${BLUE}          MLEAProxy Distribution Package Creator v${VERSION}${NC}"
echo -e "${BLUE}================================================================================${NC}"
echo ""

# Step 1: Validate JAR exists
echo "Step 1: Validating prerequisites..."

JAR_PATH=""
if [ -f "release/mlesproxy-${VERSION}.jar" ]; then
    JAR_PATH="release/mlesproxy-${VERSION}.jar"
    echo -e "${GREEN}✓ Found JAR: release/mlesproxy-${VERSION}.jar${NC}"
elif [ -f "target/mlesproxy-${VERSION}.jar" ]; then
    JAR_PATH="target/mlesproxy-${VERSION}.jar"
    echo -e "${GREEN}✓ Found JAR: target/mlesproxy-${VERSION}.jar${NC}"
else
    echo -e "${RED}✗ JAR file not found${NC}"
    echo ""
    echo "Build the JAR first:"
    echo "  ./build.sh clean package"
    exit 1
fi

JAR_SIZE=$(du -h "$JAR_PATH" | awk '{print $1}')
echo "  Size: $JAR_SIZE"
echo ""

# Step 2: Portability Validation
validate_portability() {
    local issues=0
    
    echo "Checking for hardcoded paths..."
    
    if grep -r "/Users/" scripts/*.sh 2>/dev/null | grep -v "^#" | grep -v "^Binary"; then
        echo -e "${RED}ERROR: Found /Users/ paths in scripts/${NC}"
        issues=1
    fi
    
    if grep -r "/home/" scripts/*.sh 2>/dev/null | grep -v "^#" | grep -v "^Binary"; then
        echo -e "${RED}ERROR: Found /home/ paths in scripts/${NC}"
        issues=1
    fi
    
    if grep -r "/opt/" scripts/*.sh 2>/dev/null | grep -v "^#" | grep -v "^Binary"; then
        echo -e "${RED}ERROR: Found /opt/ paths in scripts/${NC}"
        issues=1
    fi
    
    if grep -r "Martins-Air" scripts/*.sh *.properties examples/ 2>/dev/null | grep -v "^#" | grep -v "^Binary"; then
        echo -e "${RED}ERROR: Found machine-specific hostname${NC}"
        issues=1
    fi
    
    if grep -E "/Users/|/home/|/opt/" *.properties *.json 2>/dev/null | grep -v "^#"; then
        echo -e "${RED}ERROR: Found absolute paths in configs${NC}"
        issues=1
    fi
    
    if [ $issues -eq 0 ]; then
        echo -e "${GREEN}✓ All files are portable${NC}"
        echo ""
        return 0
    else
        echo ""
        echo -e "${RED}Fix hardcoded paths before creating distribution${NC}"
        return 1
    fi
}

validate_portability

# Step 3: Create distribution directory
echo "Step 3: Creating distribution directory..."

if [ -d "$DIST_DIR" ]; then
    rm -rf "$DIST_DIR"
fi

mkdir -p "$DIST_DIR/$PACKAGE_NAME"
echo -e "${GREEN}✓ Created $DIST_DIR/$PACKAGE_NAME/${NC}"
echo ""

# Step 4: Copy files
echo "Step 4: Copying files..."

cp "$JAR_PATH" "$DIST_DIR/$PACKAGE_NAME/mlesproxy-${VERSION}.jar"
echo -e "${GREEN}✓ JAR${NC}"

cp *.properties "$DIST_DIR/$PACKAGE_NAME/" 2>/dev/null || true
cp *.json "$DIST_DIR/$PACKAGE_NAME/" 2>/dev/null || true
echo -e "${GREEN}✓ Configuration files${NC}"

mkdir -p "$DIST_DIR/$PACKAGE_NAME/docs"
cp -r docs/user/* "$DIST_DIR/$PACKAGE_NAME/docs/"
echo -e "${GREEN}✓ Documentation${NC}"

cp -r examples "$DIST_DIR/$PACKAGE_NAME/"
echo -e "${GREEN}✓ Examples${NC}"

mkdir -p "$DIST_DIR/$PACKAGE_NAME/scripts"
cp scripts/start-all.sh "$DIST_DIR/$PACKAGE_NAME/scripts/"
cp scripts/start-ldap.sh "$DIST_DIR/$PACKAGE_NAME/scripts/"
cp scripts/start-oauth.sh "$DIST_DIR/$PACKAGE_NAME/scripts/"
cp scripts/start-saml.sh "$DIST_DIR/$PACKAGE_NAME/scripts/"
cp scripts/start-kerberos.sh "$DIST_DIR/$PACKAGE_NAME/scripts/"
cp scripts/stop.sh "$DIST_DIR/$PACKAGE_NAME/scripts/"
cp scripts/status.sh "$DIST_DIR/$PACKAGE_NAME/scripts/"
cp scripts/start.sh "$DIST_DIR/$PACKAGE_NAME/scripts/"
cp scripts/create-keytab.sh "$DIST_DIR/$PACKAGE_NAME/scripts/"
echo -e "${GREEN}✓ Scripts${NC}"

mkdir -p "$DIST_DIR/$PACKAGE_NAME/kerberos/keytabs"
if [ -f "examples/kerberos/krb5.conf" ]; then
    cp examples/kerberos/krb5.conf "$DIST_DIR/$PACKAGE_NAME/kerberos/"
fi
echo -e "${GREEN}✓ Kerberos runtime directory${NC}"

echo ""

# Step 5: Generate README.txt
echo "Step 5: Generating README.txt..."

cat > "$DIST_DIR/$PACKAGE_NAME/README.txt" << 'READMEEOF'
MLEAProxy 2.0.3 - Quick Start Guide
====================================

PREREQUISITES
- Java 21 or higher
- Ports available: 8080 (web), 10389 (LDAP), 8088 (Kerberos KDC)
- For MarkLogic integration: MarkLogic Server running on port 8002

FIRST RUN
1. Start the server:
   ./scripts/start.sh
   
   This interactive launcher will ask which protocol(s) to start.

2. Check status:
   ./scripts/status.sh

3. Stop the server:
   ./scripts/stop.sh

4. Access web interface:
   http://localhost:8080/status

ALTERNATIVE: Individual Protocol Scripts
- All protocols:   ./scripts/start-all.sh
- LDAP only:       ./scripts/start-ldap.sh
- OAuth only:      ./scripts/start-oauth.sh
- SAML only:       ./scripts/start-saml.sh
- Kerberos only:   ./scripts/start-kerberos.sh

KERBEROS SETUP
Before starting Kerberos for the first time:
   ./scripts/create-keytab.sh

This wizard will generate the required keytab file.

FULL DOCUMENTATION
See docs/README.md for complete guides:
- QUICKSTART_VERIFICATION.md - Working examples for all protocols
- CONFIGURATION_GUIDE.md - Complete property reference
- Protocol guides: LDAP_GUIDE.md, OAUTH_GUIDE.md, SAML_GUIDE.md, KERBEROS_GUIDE.md

MARKLOGIC INTEGRATION
See examples/marklogic/README.md for MarkLogic integration tests.

CONFIGURATION
- users.json - User database (default password: "password")
- *.properties - Protocol-specific configuration
- examples/ - Example configurations for each protocol

DEFAULT USERS
All users have password "password":
- admin - Administrator role
- user1, user2, user3 - Standard users

PORTS
- 8080 - Web server and REST API
- 10389 - LDAP proxy
- 8088 - Kerberos KDC
- 9003-9006 - MarkLogic integration test AppServers

SUPPORT
For issues or questions, see documentation in docs/

VERSION
2.0.3
READMEEOF

echo -e "${GREEN}✓ README.txt created${NC}"
echo ""

# Step 6: Set permissions
echo "Step 6: Setting permissions..."

chmod +x "$DIST_DIR/$PACKAGE_NAME/scripts"/*.sh
chmod +x "$DIST_DIR/$PACKAGE_NAME/examples/marklogic"/*.sh 2>/dev/null || true

echo -e "${GREEN}✓ All scripts executable${NC}"
echo ""

# Step 7: Create zip archive
echo "Step 7: Creating zip archive..."

cd "$DIST_DIR"
zip -r "../$ZIP_NAME" "$PACKAGE_NAME/" > /dev/null
cd ..

ZIP_SIZE=$(du -h "$ZIP_NAME" | awk '{print $1}')

echo -e "${GREEN}✓ Created $ZIP_NAME ($ZIP_SIZE)${NC}"
echo ""

# Step 8: Cleanup
echo "Step 8: Cleanup..."

rm -rf "$DIST_DIR"

echo -e "${GREEN}✓ Removed temporary directory${NC}"
echo ""

echo -e "${GREEN}================================================================================${NC}"
echo -e "${GREEN}Distribution Package Created Successfully!${NC}"
echo -e "${GREEN}================================================================================${NC}"
echo ""
echo "Package: $ZIP_NAME"
echo "Size:    $ZIP_SIZE"
echo ""
echo "To test:"
echo "  unzip $ZIP_NAME"
echo "  cd $PACKAGE_NAME"
echo "  ./scripts/start.sh"
echo ""
echo "Ready to upload to company portal."
echo ""
