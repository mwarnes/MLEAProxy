#!/bin/bash

# MLEAProxy Test Runner Script
# Provides convenient commands for running different test suites

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}MLEAProxy Test Suite Runner${NC}"
echo "=============================="

# Function to display usage
usage() {
    echo "Usage: $0 [option]"
    echo ""
    echo "Options:"
    echo "  all           - Run all tests"
    echo "  oauth         - Run OAuth token endpoint tests"
    echo "  saml          - Run SAML authentication tests"
    echo "  ldap          - Run LDAP proxy tests"
    echo "  integration   - Run integration tests"
    echo "  coverage      - Run tests with coverage report"
    echo "  quick         - Run tests without LDAP (faster)"
    echo "  single <test> - Run a single test class"
    echo ""
    echo "Examples:"
    echo "  $0 all"
    echo "  $0 oauth"
    echo "  $0 single OAuthTokenHandlerTest"
    exit 1
}

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven is not installed${NC}"
    exit 1
fi

# Parse command line arguments
case "${1:-all}" in
    all)
        echo -e "${GREEN}Running all tests...${NC}"
        mvn clean test
        ;;
    oauth)
        echo -e "${GREEN}Running OAuth token endpoint tests...${NC}"
        mvn test -Dtest=OAuthTokenHandlerTest
        ;;
    saml)
        echo -e "${GREEN}Running SAML authentication tests...${NC}"
        mvn test -Dtest=SAMLAuthHandlerTest
        ;;
    ldap)
        echo -e "${GREEN}Running LDAP proxy tests...${NC}"
        mvn test -Dtest=LDAPRequestHandlerTest
        ;;
    integration)
        echo -e "${GREEN}Running integration tests...${NC}"
        mvn test -Dtest=MLEAProxyIntegrationTest
        ;;
    coverage)
        echo -e "${GREEN}Running tests with coverage...${NC}"
        mvn clean test jacoco:report
        echo -e "${BLUE}Coverage report: target/site/jacoco/index.html${NC}"
        ;;
    quick)
        echo -e "${GREEN}Running quick tests (excluding LDAP)...${NC}"
        mvn test -Dtest=OAuthTokenHandlerTest,SAMLAuthHandlerTest,MLEAProxyIntegrationTest
        ;;
    single)
        if [ -z "$2" ]; then
            echo -e "${RED}Error: Please specify a test class${NC}"
            usage
        fi
        echo -e "${GREEN}Running test class: $2${NC}"
        mvn test -Dtest="$2"
        ;;
    help|--help|-h)
        usage
        ;;
    *)
        echo -e "${RED}Error: Unknown option '$1'${NC}"
        usage
        ;;
esac

# Check exit code
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Tests completed successfully${NC}"
else
    echo -e "${RED}✗ Tests failed${NC}"
    exit 1
fi
