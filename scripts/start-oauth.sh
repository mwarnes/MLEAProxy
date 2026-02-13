#!/bin/bash
# ================================================================
# MLEAProxy - Start OAuth Mode
# ================================================================
# Starts MLEAProxy in OAuth 2.0 mode with JWT token generation
# ================================================================

JAR_FILE="target/mlesproxy-2.0.0.jar"
CONFIG_FILE="examples/oauth/01-oauth-basic.properties"
LOG_FILE="mleaproxy-oauth.log"

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

echo -e "${GREEN}Starting MLEAProxy in OAuth mode...${NC}"
cp "$CONFIG_FILE" oauth.properties

java -Dspring.config.location=classpath:/application.properties,./oauth.properties \
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
    echo -e "${BLUE}                         OAuth 2.0 Endpoints${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}🔑 Token Endpoint:${NC}"
    echo "  URL: http://localhost:8080/oauth/token"
    echo "  Methods: POST"
    echo "  Description: Generate JWT access tokens"
    echo "  Grant Types: password, client_credentials, refresh_token"
    echo ""

    echo -e "${CYAN}🔐 JWKS Endpoint (JSON Web Key Set):${NC}"
    echo "  URL: http://localhost:8080/oauth/jwks"
    echo "  Methods: GET"
    echo "  Description: Public keys for token verification"
    echo "  Format: JWK (JSON Web Key)"
    echo ""

    echo -e "${CYAN}📋 Discovery Endpoint (OpenID Configuration):${NC}"
    echo "  URL: http://localhost:8080/.well-known/openid-configuration"
    echo "  Methods: GET"
    echo "  Description: OAuth/OIDC server metadata (RFC 8414)"
    echo "  Contains: issuer, endpoints, grant types, algorithms"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                         Token Configuration${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}⏱️  Token Validity:${NC}"
    echo "  Access Token: 3600 seconds (1 hour)"
    echo "  Refresh Token: 86400 seconds (1 day)"
    echo "  Algorithm: RS256 (RSA + SHA-256)"
    echo "  Key Size: 2048-bit RSA"
    echo ""

    echo -e "${CYAN}📝 JWT Token Claims (Standard):${NC}"
    echo "  iss (Issuer): http://localhost:8080"
    echo "  sub (Subject): username"
    echo "  aud (Audience): marklogic"
    echo "  exp (Expiration): issued_at + validity"
    echo "  iat (Issued At): current timestamp"
    echo "  jti (JWT ID): unique token identifier"
    echo ""

    echo -e "${CYAN}📝 JWT Token Claims (Custom):${NC}"
    echo "  username: User's username"
    echo "  roles: Array of user roles"
    echo "  client_id: OAuth client identifier"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                            Available Users${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}👤 User: admin${NC}"
    echo "  Username: admin"
    echo "  Password: admin"
    echo "  Roles: [\"admin\", \"marklogic-admin\"]"
    echo "  Claims: username=admin, roles=[admin,marklogic-admin]"
    echo ""

    echo -e "${CYAN}👤 User: user1${NC}"
    echo "  Username: user1"
    echo "  Password: password"
    echo "  Roles: [\"user\", \"reader\"]"
    echo "  Claims: username=user1, roles=[user,reader]"
    echo ""

    echo -e "${CYAN}👤 User: user2${NC}"
    echo "  Username: user2"
    echo "  Password: password"
    echo "  Roles: [\"user\", \"writer\"]"
    echo "  Claims: username=user2, roles=[user,writer]"
    echo ""

    echo -e "${CYAN}👤 User: developer${NC}"
    echo "  Username: developer"
    echo "  Password: dev123"
    echo "  Roles: [\"developer\", \"user\"]"
    echo "  Claims: username=developer, roles=[developer,user]"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                         OAuth Client Credentials${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}🔐 Default Client:${NC}"
    echo "  Client ID: marklogic"
    echo "  Client Secret: secret"
    echo "  Allowed Grant Types: password, client_credentials, refresh_token"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                          Testing Commands${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}🧪 Get Access Token (Password Grant - admin):${NC}"
    echo "  curl -X POST http://localhost:8080/oauth/token \\"
    echo "    -d \"grant_type=password\" \\"
    echo "    -d \"username=admin\" \\"
    echo "    -d \"password=admin\" \\"
    echo "    -d \"client_id=marklogic\" \\"
    echo "    -d \"client_secret=secret\""
    echo ""

    echo -e "${CYAN}🧪 Get Access Token (Password Grant - user1):${NC}"
    echo "  curl -X POST http://localhost:8080/oauth/token \\"
    echo "    -d \"grant_type=password\" \\"
    echo "    -d \"username=user1\" \\"
    echo "    -d \"password=password\" \\"
    echo "    -d \"client_id=marklogic\" \\"
    echo "    -d \"client_secret=secret\""
    echo ""

    echo -e "${CYAN}🧪 Get Access Token with Custom Roles:${NC}"
    echo "  curl -X POST http://localhost:8080/oauth/token \\"
    echo "    -d \"grant_type=password\" \\"
    echo "    -d \"username=admin\" \\"
    echo "    -d \"password=admin\" \\"
    echo "    -d \"roles=superuser,admin\" \\"
    echo "    -d \"client_id=marklogic\" \\"
    echo "    -d \"client_secret=secret\""
    echo ""

    echo -e "${CYAN}🧪 Client Credentials Grant:${NC}"
    echo "  curl -X POST http://localhost:8080/oauth/token \\"
    echo "    -d \"grant_type=client_credentials\" \\"
    echo "    -d \"client_id=marklogic\" \\"
    echo "    -d \"client_secret=secret\""
    echo ""

    echo -e "${CYAN}🧪 Refresh Token Grant:${NC}"
    echo "  # First get a token with password grant (includes refresh_token)"
    echo "  # Then use the refresh_token:"
    echo "  curl -X POST http://localhost:8080/oauth/token \\"
    echo "    -d \"grant_type=refresh_token\" \\"
    echo "    -d \"refresh_token=YOUR_REFRESH_TOKEN\" \\"
    echo "    -d \"client_id=marklogic\" \\"
    echo "    -d \"client_secret=secret\""
    echo ""

    echo -e "${CYAN}🧪 Get JWKS (Public Keys):${NC}"
    echo "  curl http://localhost:8080/oauth/jwks | jq ."
    echo ""

    echo -e "${CYAN}🧪 Get OpenID Discovery:${NC}"
    echo "  curl http://localhost:8080/.well-known/openid-configuration | jq ."
    echo ""

    echo -e "${CYAN}🧪 Decode JWT Token:${NC}"
    echo "  # Get token first, then decode:"
    echo "  echo \"YOUR_TOKEN\" | cut -d. -f1-2 | sed 's/\\./\\n/' | \\"
    echo "    while read part; do echo \$part | base64 -d 2>/dev/null | jq .; done"
    echo ""

    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                        Example Token Response${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    echo -e "${CYAN}📄 Response Format:${NC}"
    echo "  {"
    echo "    \"access_token\": \"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...\","
    echo "    \"token_type\": \"Bearer\","
    echo "    \"expires_in\": 3600,"
    echo "    \"refresh_token\": \"4f2c7b1a-8d3e-9f5a-1c2b-3d4e5f6a7b8c\""
    echo "  }"
    echo ""

    echo -e "${CYAN}📄 Decoded Access Token (JWT):${NC}"
    echo "  Header:"
    echo "    {\"alg\": \"RS256\", \"typ\": \"JWT\"}"
    echo ""
    echo "  Payload:"
    echo "    {"
    echo "      \"iss\": \"http://localhost:8080\","
    echo "      \"sub\": \"admin\","
    echo "      \"aud\": \"marklogic\","
    echo "      \"exp\": 1676300000,"
    echo "      \"iat\": 1676296400,"
    echo "      \"jti\": \"unique-jwt-id\","
    echo "      \"username\": \"admin\","
    echo "      \"roles\": [\"admin\", \"marklogic-admin\"],"
    echo "      \"client_id\": \"marklogic\""
    echo "    }"
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
