#!/bin/bash
# ================================================================
# MLEAProxy - Start SAML Mode
# ================================================================
# Starts MLEAProxy in SAML 2.0 IdP mode
# ================================================================

JAR_FILE="target/mlesproxy-2.0.0.jar"
CONFIG_FILE="examples/saml/01-saml-basic.properties"
LOG_FILE="mleaproxy-saml.log"

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

echo -e "${GREEN}Starting MLEAProxy in SAML mode...${NC}"
cp "$CONFIG_FILE" saml.properties

java -Dspring.config.location=classpath:/application.properties,./saml.properties \
     -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &

PID=$!
echo $PID > mleaproxy.pid
sleep 3

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
    echo -e "${BLUE}                         SAML 2.0 Endpoints${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}🔐 Authentication Endpoint:${NC}"
    echo "  URL: http://localhost:8080/saml/auth"
    echo "  Methods: POST"
    echo "  Description: Authenticates users and generates SAML assertions"
    echo "  Parameters: username, password, roles (optional)"
    echo ""

    echo -e "${CYAN}📄 Metadata Endpoint:${NC}"
    echo "  URL: http://localhost:8080/saml/metadata"
    echo "  Methods: GET"
    echo "  Description: SAML IdP metadata (EntityDescriptor)"
    echo "  Format: XML (SAML 2.0 Metadata)"
    echo "  Contains: Entity ID, SSO endpoints, certificates, supported bindings"
    echo ""

    echo -e "${CYAN}📦 Wrap Assertion Endpoint:${NC}"
    echo "  URL: http://localhost:8080/saml/wrapassertion"
    echo "  Methods: POST"
    echo "  Description: Wraps existing SAML assertion in Response"
    echo "  Use Case: Converting assertions between formats"
    echo ""

    echo -e "${CYAN}🔒 CA Certificates Endpoint:${NC}"
    echo "  URL: http://localhost:8080/saml/cacerts"
    echo "  Methods: GET"
    echo "  Description: Retrieves IdP CA certificates"
    echo "  Format: PEM-encoded X.509 certificates"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                         Assertion Configuration${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}⏱️  Assertion Validity:${NC}"
    echo "  Validity Duration: 300 seconds (5 minutes)"
    echo "  NotBefore: Current timestamp"
    echo "  NotOnOrAfter: Current timestamp + 300s"
    echo ""

    echo -e "${CYAN}🔐 Signature Configuration:${NC}"
    echo "  Algorithm: RSA-SHA256 (http://www.w3.org/2001/04/xmldsig-more#rsa-sha256)"
    echo "  Digest Method: SHA-256"
    echo "  Certificate: Bundled X.509 certificate (or custom if configured)"
    echo "  Key Size: 2048-bit RSA"
    echo ""

    echo -e "${CYAN}📝 SAML Assertion Attributes:${NC}"
    echo "  Standard Attributes:"
    echo "    - Subject (NameID): User's username"
    echo "    - Issuer: http://localhost:8080"
    echo "    - Audience: Configured SP entity ID"
    echo "    - AuthnInstant: Authentication timestamp"
    echo ""
    echo "  Custom Attributes:"
    echo "    - username: User's username"
    echo "    - roles: Comma-separated list of roles"
    echo "    - email: User's email address (if available)"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                            Available Users${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}👤 User: admin${NC}"
    echo "  Username: admin"
    echo "  Password: admin"
    echo "  Roles: [\"admin\", \"marklogic-admin\"]"
    echo "  Email: admin@marklogic.local"
    echo "  SAML Attributes:"
    echo "    <saml:Attribute Name=\"username\"><saml:AttributeValue>admin</saml:AttributeValue></saml:Attribute>"
    echo "    <saml:Attribute Name=\"roles\"><saml:AttributeValue>admin,marklogic-admin</saml:AttributeValue></saml:Attribute>"
    echo ""

    echo -e "${CYAN}👤 User: user1${NC}"
    echo "  Username: user1"
    echo "  Password: password"
    echo "  Roles: [\"user\", \"reader\"]"
    echo "  Email: user1@marklogic.local"
    echo "  SAML Attributes:"
    echo "    <saml:Attribute Name=\"username\"><saml:AttributeValue>user1</saml:AttributeValue></saml:Attribute>"
    echo "    <saml:Attribute Name=\"roles\"><saml:AttributeValue>user,reader</saml:AttributeValue></saml:Attribute>"
    echo ""

    echo -e "${CYAN}👤 User: user2${NC}"
    echo "  Username: user2"
    echo "  Password: password"
    echo "  Roles: [\"user\", \"writer\"]"
    echo "  Email: user2@marklogic.local"
    echo "  SAML Attributes:"
    echo "    <saml:Attribute Name=\"username\"><saml:AttributeValue>user2</saml:AttributeValue></saml:Attribute>"
    echo "    <saml:Attribute Name=\"roles\"><saml:AttributeValue>user,writer</saml:AttributeValue></saml:Attribute>"
    echo ""

    echo -e "${CYAN}👤 User: developer${NC}"
    echo "  Username: developer"
    echo "  Password: dev123"
    echo "  Roles: [\"developer\", \"user\"]"
    echo "  Email: dev@marklogic.local"
    echo "  SAML Attributes:"
    echo "    <saml:Attribute Name=\"username\"><saml:AttributeValue>developer</saml:AttributeValue></saml:Attribute>"
    echo "    <saml:Attribute Name=\"roles\"><saml:AttributeValue>developer,user</saml:AttributeValue></saml:Attribute>"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                          Testing Commands${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}🧪 Authenticate and Get SAML Assertion (admin):${NC}"
    echo "  curl -X POST http://localhost:8080/saml/auth \\\\"
    echo "    -d \"username=admin\" \\\\"
    echo "    -d \"password=admin\" \\\\"
    echo "    -d \"roles=admin,marklogic-admin\""
    echo ""

    echo -e "${CYAN}🧪 Authenticate with Default Roles (user1):${NC}"
    echo "  curl -X POST http://localhost:8080/saml/auth \\\\"
    echo "    -d \"username=user1\" \\\\"
    echo "    -d \"password=password\""
    echo ""

    echo -e "${CYAN}🧪 Get SAML IdP Metadata:${NC}"
    echo "  curl http://localhost:8080/saml/metadata | xmllint --format -"
    echo ""

    echo -e "${CYAN}🧪 Get CA Certificates:${NC}"
    echo "  curl http://localhost:8080/saml/cacerts"
    echo ""

    echo -e "${CYAN}🧪 Wrap Existing SAML Assertion:${NC}"
    echo "  curl -X POST http://localhost:8080/saml/wrapassertion \\\\"
    echo "    -H \"Content-Type: application/xml\" \\\\"
    echo "    -d @assertion.xml"
    echo ""

    echo -e "${CYAN}🧪 Decode SAML Assertion (Base64):${NC}"
    echo "  # If assertion is Base64 encoded:"
    echo "  echo \"BASE64_ASSERTION\" | base64 -d | xmllint --format -"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                        Example SAML Assertion${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}📄 Assertion Structure:${NC}"
    echo "  <saml:Assertion xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\">"
    echo "    <saml:Issuer>http://localhost:8080</saml:Issuer>"
    echo "    <saml:Subject>"
    echo "      <saml:NameID Format=\"urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified\">"
    echo "        admin"
    echo "      </saml:NameID>"
    echo "      <saml:SubjectConfirmation Method=\"urn:oasis:names:tc:SAML:2.0:cm:bearer\">"
    echo "        <saml:SubjectConfirmationData NotOnOrAfter=\"2026-02-13T12:05:00Z\"/>"
    echo "      </saml:SubjectConfirmation>"
    echo "    </saml:Subject>"
    echo "    <saml:Conditions NotBefore=\"2026-02-13T12:00:00Z\""
    echo "                     NotOnOrAfter=\"2026-02-13T12:05:00Z\">"
    echo "      <saml:AudienceRestriction>"
    echo "        <saml:Audience>marklogic</saml:Audience>"
    echo "      </saml:AudienceRestriction>"
    echo "    </saml:Conditions>"
    echo "    <saml:AuthnStatement AuthnInstant=\"2026-02-13T12:00:00Z\">"
    echo "      <saml:AuthnContext>"
    echo "        <saml:AuthnContextClassRef>"
    echo "          urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport"
    echo "        </saml:AuthnContextClassRef>"
    echo "      </saml:AuthnContext>"
    echo "    </saml:AuthnStatement>"
    echo "    <saml:AttributeStatement>"
    echo "      <saml:Attribute Name=\"username\">"
    echo "        <saml:AttributeValue>admin</saml:AttributeValue>"
    echo "      </saml:Attribute>"
    echo "      <saml:Attribute Name=\"roles\">"
    echo "        <saml:AttributeValue>admin,marklogic-admin</saml:AttributeValue>"
    echo "      </saml:Attribute>"
    echo "    </saml:AttributeStatement>"
    echo "  </saml:Assertion>"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                         MarkLogic Integration${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}📋 Integration Steps:${NC}"
    echo "  1. Download IdP metadata:"
    echo "     curl http://localhost:8080/saml/metadata > idp-metadata.xml"
    echo ""
    echo "  2. Configure MarkLogic External Security:"
    echo "     - Create SAML configuration in MarkLogic Admin UI"
    echo "     - Upload idp-metadata.xml"
    echo "     - Configure attribute mapping (username → roles)"
    echo ""
    echo "  3. Test SAML authentication with MarkLogic:"
    echo "     - Access MarkLogic application"
    echo "     - Redirect to http://localhost:8080/saml/auth"
    echo "     - Authenticate with admin/admin"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                         Role Mapping${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}🔄 Role Resolution (Priority Order):${NC}"
    echo "  1. Request Parameter: roles specified in POST request"
    echo "  2. User Repository: roles from users.json"
    echo "  3. Default Roles: saml.default.roles property (default: user)"
    echo ""
    echo "  Example with custom roles:"
    echo "    curl -X POST http://localhost:8080/saml/auth \\\\"
    echo "      -d \"username=user1\" \\\\"
    echo "      -d \"password=password\" \\\\"
    echo "      -d \"roles=superuser,admin\""
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
    exit 1
fi
