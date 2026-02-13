#!/bin/bash
# ================================================================
# MLEAProxy - Start Kerberos Mode
# ================================================================
# Starts MLEAProxy in Kerberos mode with embedded KDC
# ================================================================

JAR_FILE="target/mlesproxy-2.0.0.jar"
CONFIG_FILE="examples/kerberos/01-kerberos-basic.properties"
LOG_FILE="mleaproxy-kerberos.log"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}Error: MLEAProxy JAR not found at $JAR_FILE${NC}"
    exit 1
fi

if [ ! -f "$CONFIG_FILE" ]; then
    echo -e "${RED}Error: Configuration file not found at $CONFIG_FILE${NC}"
    exit 1
fi

if pgrep -f "mlesproxy.*jar" > /dev/null; then
    echo -e "${YELLOW}Warning: MLEAProxy is already running${NC}"
    exit 1
fi

echo -e "${GREEN}Starting MLEAProxy in Kerberos mode...${NC}"
cp "$CONFIG_FILE" kerberos.properties

java -Dspring.config.location=classpath:/application.properties,./kerberos.properties \
     -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &

PID=$!
echo $PID > mleaproxy.pid
sleep 5  # KDC takes longer to start

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
    echo -e "${BLUE}                    Kerberos KDC (Key Distribution Center)${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}🔐 KDC Configuration:${NC}"
    echo "  Host: localhost"
    echo "  Port: 88 (standard Kerberos port)"
    echo "  Realm: EXAMPLE.COM"
    echo "  Type: Embedded Apache Kerby KDC"
    echo "  Status: Running (embedded in MLEAProxy process)"
    echo ""

    echo -e "${CYAN}🎫 Ticket Configuration:${NC}"
    echo "  Ticket Lifetime: 86400 seconds (24 hours)"
    echo "  Renewable Lifetime: 604800 seconds (7 days)"
    echo "  Encryption Types: AES256-CTS-HMAC-SHA1-96, AES128-CTS-HMAC-SHA1-96"
    echo "  Supported Auth Mechanisms: SPNEGO, Kerberos V5"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                         Kerberos Principals${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}👤 User Principal: admin@EXAMPLE.COM${NC}"
    echo "  Type: User Principal"
    echo "  Password: password"
    echo "  Description: Administrator user account"
    echo "  Use Case: Testing administrative authentication"
    echo "  Associated User: admin (from users.json)"
    echo "  Roles: [\"admin\", \"marklogic-admin\"]"
    echo ""

    echo -e "${CYAN}👤 User Principal: user@EXAMPLE.COM${NC}"
    echo "  Type: User Principal"
    echo "  Password: password"
    echo "  Description: Standard user account"
    echo "  Use Case: Testing standard user authentication"
    echo "  Associated User: user1 (from users.json)"
    echo "  Roles: [\"user\", \"reader\"]"
    echo ""

    echo -e "${CYAN}🔧 Service Principal: HTTP/localhost@EXAMPLE.COM${NC}"
    echo "  Type: Service Principal"
    echo "  Password: (auto-generated)"
    echo "  Description: Service principal for SPNEGO authentication"
    echo "  Use Case: HTTP Negotiate authentication"
    echo "  Keytab: Auto-generated and stored in memory"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                       Kerberos HTTP Endpoints${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}🔐 Kerberos Authentication Endpoint:${NC}"
    echo "  URL: http://localhost:8080/kerberos/auth"
    echo "  Methods: POST"
    echo "  Description: SPNEGO authentication using Kerberos tickets"
    echo "  Auth Mechanism: HTTP Negotiate (SPNEGO)"
    echo "  Response: Authentication success with user information"
    echo ""

    echo -e "${CYAN}🔄 Kerberos to OAuth Bridge:${NC}"
    echo "  URL: http://localhost:8080/kerberos/oauth"
    echo "  Methods: POST"
    echo "  Description: Converts Kerberos ticket to OAuth JWT token"
    echo "  Auth Mechanism: HTTP Negotiate (SPNEGO)"
    echo "  Response: OAuth 2.0 token response (access_token, token_type, expires_in)"
    echo "  Use Case: Integrate Kerberos-authenticated users with OAuth-based APIs"
    echo ""

    echo -e "${CYAN}🔄 Kerberos to SAML Bridge:${NC}"
    echo "  URL: http://localhost:8080/kerberos/saml"
    echo "  Methods: POST"
    echo "  Description: Converts Kerberos ticket to SAML assertion"
    echo "  Auth Mechanism: HTTP Negotiate (SPNEGO)"
    echo "  Response: SAML 2.0 assertion XML"
    echo "  Use Case: Integrate Kerberos-authenticated users with SAML-based systems"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                       Protocol Bridge Details${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}🌉 Kerberos → OAuth Bridge Flow:${NC}"
    echo "  1. Client obtains Kerberos ticket (kinit)"
    echo "  2. Client sends SPNEGO token to /kerberos/oauth"
    echo "  3. MLEAProxy validates Kerberos ticket"
    echo "  4. MLEAProxy extracts principal (e.g., admin@EXAMPLE.COM)"
    echo "  5. MLEAProxy looks up user in users.json"
    echo "  6. MLEAProxy generates OAuth JWT token with user's roles"
    echo "  7. Client receives JWT token for API access"
    echo ""

    echo -e "${CYAN}🌉 Kerberos → SAML Bridge Flow:${NC}"
    echo "  1. Client obtains Kerberos ticket (kinit)"
    echo "  2. Client sends SPNEGO token to /kerberos/saml"
    echo "  3. MLEAProxy validates Kerberos ticket"
    echo "  4. MLEAProxy extracts principal and looks up user"
    echo "  5. MLEAProxy generates SAML assertion with user attributes"
    echo "  6. Client receives SAML assertion for SSO"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                          Testing Commands${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}🧪 Step 1: Obtain Kerberos Ticket (admin):${NC}"
    echo "  kinit admin@EXAMPLE.COM"
    echo "  # Enter password when prompted: password"
    echo ""

    echo -e "${CYAN}🧪 Step 2: Verify Ticket:${NC}"
    echo "  klist"
    echo "  # Should show ticket for admin@EXAMPLE.COM"
    echo "  # Valid starting: [current time]"
    echo "  # Expires: [current time + 24 hours]"
    echo ""

    echo -e "${CYAN}🧪 Step 3a: Test Kerberos Authentication:${NC}"
    echo "  curl -X POST http://localhost:8080/kerberos/auth \\\\"
    echo "    --negotiate -u : \\\\"
    echo "    -v"
    echo ""
    echo "  # Expected response:"
    echo "  # {\"status\":\"authenticated\",\"principal\":\"admin@EXAMPLE.COM\"}"
    echo ""

    echo -e "${CYAN}🧪 Step 3b: Test Kerberos to OAuth Bridge:${NC}"
    echo "  curl -X POST http://localhost:8080/kerberos/oauth \\\\"
    echo "    --negotiate -u : \\\\"
    echo "    -v"
    echo ""
    echo "  # Expected response:"
    echo "  # {"
    echo "  #   \"access_token\": \"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...\","
    echo "  #   \"token_type\": \"Bearer\","
    echo "  #   \"expires_in\": 3600"
    echo "  # }"
    echo ""

    echo -e "${CYAN}🧪 Step 3c: Test Kerberos to SAML Bridge:${NC}"
    echo "  curl -X POST http://localhost:8080/kerberos/saml \\\\"
    echo "    --negotiate -u : \\\\"
    echo "    -v"
    echo ""
    echo "  # Expected response: SAML assertion XML"
    echo ""

    echo -e "${CYAN}🧪 Step 4: Use Generated OAuth Token:${NC}"
    echo "  # Extract token from Step 3b response, then:"
    echo "  TOKEN=\"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...\""
    echo "  curl -H \"Authorization: Bearer \$TOKEN\" \\\\"
    echo "    http://your-api-endpoint"
    echo ""

    echo -e "${CYAN}🧪 Step 5: Cleanup (Destroy Ticket):${NC}"
    echo "  kdestroy"
    echo "  # Verify ticket is destroyed:"
    echo "  klist"
    echo "  # Should show: \"klist: No credentials cache found\""
    echo ""

    echo -e "${CYAN}🧪 Alternative: Test with User Principal:${NC}"
    echo "  kinit user@EXAMPLE.COM"
    echo "  # Enter password: password"
    echo "  curl -X POST http://localhost:8080/kerberos/auth --negotiate -u : -v"
    echo "  kdestroy"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                         Configuration Files${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}📄 Kerberos Configuration:${NC}"
    echo "  Location: ./kerberos.properties"
    echo "  Realm: EXAMPLE.COM"
    echo "  KDC Host: localhost:88"
    echo "  Principals: admin@EXAMPLE.COM, user@EXAMPLE.COM, HTTP/localhost@EXAMPLE.COM"
    echo "  Service Principal: HTTP/localhost@EXAMPLE.COM"
    echo ""

    echo -e "${CYAN}📄 Client Configuration (/etc/krb5.conf):${NC}"
    echo "  # Add this to your /etc/krb5.conf for testing:"
    echo "  [libdefaults]"
    echo "      default_realm = EXAMPLE.COM"
    echo "      dns_lookup_realm = false"
    echo "      dns_lookup_kdc = false"
    echo ""
    echo "  [realms]"
    echo "      EXAMPLE.COM = {"
    echo "          kdc = localhost:88"
    echo "          admin_server = localhost:88"
    echo "      }"
    echo ""
    echo "  [domain_realm]"
    echo "      .example.com = EXAMPLE.COM"
    echo "      example.com = EXAMPLE.COM"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                         MarkLogic Integration${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}🔄 Integration Option 1: Kerberos with LDAP Roles${NC}"
    echo "  1. Configure MarkLogic for Kerberos authentication"
    echo "  2. Point MarkLogic LDAP external security to MLEAProxy LDAP proxy"
    echo "  3. MLEAProxy resolves Kerberos principal → LDAP user → roles"
    echo "  4. MarkLogic receives roles via LDAP group membership"
    echo ""
    echo "  Example configuration file: examples/kerberos/05-kerberos-full-stack.properties"
    echo ""

    echo -e "${CYAN}🔄 Integration Option 2: Kerberos to OAuth Bridge${NC}"
    echo "  1. Users authenticate via Kerberos (kinit)"
    echo "  2. Application calls /kerberos/oauth to get JWT token"
    echo "  3. Application uses JWT token with MarkLogic REST API"
    echo "  4. MarkLogic validates JWT via OAuth external security"
    echo ""
    echo "  Example configuration file: examples/kerberos/03-kerberos-with-oauth.properties"
    echo ""

    echo -e "${CYAN}🔄 Integration Option 3: Kerberos to SAML Bridge${NC}"
    echo "  1. Users authenticate via Kerberos (kinit)"
    echo "  2. Application calls /kerberos/saml to get SAML assertion"
    echo "  3. Application uses SAML assertion with MarkLogic"
    echo "  4. MarkLogic validates SAML via SAML external security"
    echo ""
    echo "  Example configuration file: examples/kerberos/04-kerberos-with-saml.properties"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                         Troubleshooting${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}❌ Common Issues:${NC}"
    echo ""
    echo "  Issue: \"kinit: Cannot find KDC for realm\""
    echo "  Solution: Check /etc/krb5.conf configuration (see above)"
    echo ""
    echo "  Issue: \"kinit: Password incorrect\""
    echo "  Solution: Verify password is 'password' for test principals"
    echo ""
    echo "  Issue: \"curl: No credentials cache found\""
    echo "  Solution: Run kinit before curl (Step 1 above)"
    echo ""
    echo "  Issue: \"HTTP 401 Unauthorized\""
    echo "  Solution: Ensure --negotiate -u : flags are used with curl"
    echo ""
    echo "  Issue: \"KDC not starting\""
    echo "  Solution: Check if port 88 is available (requires root on Linux/macOS)"
    echo "            Alternative: Change kerberos.kdc.port to 10088 in config"
    echo ""

    echo -e "${CYAN}🔍 Debug Commands:${NC}"
    echo "  # Check if KDC is listening:"
    echo "  netstat -an | grep :88"
    echo ""
    echo "  # View KDC logs:"
    echo "  tail -f $LOG_FILE | grep KDC"
    echo ""
    echo "  # Verbose kinit:"
    echo "  KRB5_TRACE=/dev/stdout kinit admin@EXAMPLE.COM"
    echo ""
    echo "  # Test with curl verbose:"
    echo "  curl -X POST http://localhost:8080/kerberos/auth \\\\"
    echo "    --negotiate -u : -v 2>&1 | grep -i negotiate"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                           Management${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo "  Stop Server: ./scripts/stop.sh"
    echo "  View Logs: tail -f $LOG_FILE"
    echo "  Check Status: ./scripts/status.sh"
    echo "  Destroy Tickets: kdestroy (clean up Kerberos credentials)"
    echo ""
    echo -e "${GREEN}═══════════════════════════════════════════════════════════════════════════${NC}"
else
    echo -e "${RED}Error: MLEAProxy failed to start${NC}"
    echo "Check log file: $LOG_FILE"
    exit 1
fi
