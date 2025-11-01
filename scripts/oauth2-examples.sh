#!/bin/bash

# ================================================================
# OAuth2 Configuration Examples
# ================================================================
#
# This script demonstrates various OAuth2 configuration scenarios
# using the MLEAProxy OAuth2 configuration scripts.
#
# Author: MLEAProxy Development Team
# Version: 1.0.0
# Date: October 2025
#
# ================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🔐 OAuth2 Configuration Examples"
echo "================================"
echo

# Example 1: MLEAProxy Development Setup
echo "📝 Example 1: MLEAProxy Development Setup"
echo "----------------------------------------"
echo
cat << 'EOF'
# Start MLEAProxy
cd MLEAProxy && mvn spring-boot:run &

# Configure MarkLogic with MLEAProxy OAuth
./scripts/configure-marklogic-oauth2.sh \
    --well-known-url http://localhost:8080/oauth/.well-known/config \
    --config-name MLEAProxy-Dev \
    --marklogic-host localhost \
    --client-id marklogic \
    --username-attribute preferred_username \
    --role-attribute marklogic-roles

# Test the configuration
./scripts/validate-oauth2-config.sh \
    --oauth-server-url http://localhost:8080 \
    --config-name MLEAProxy-Dev
EOF
echo

# Example 2: Keycloak Production Setup
echo "📝 Example 2: Keycloak Production Setup"
echo "---------------------------------------"
echo
cat << 'EOF'
# Configure MarkLogic with Keycloak OAuth
./scripts/configure-marklogic-oauth2.sh \
    --well-known-url https://keycloak.company.com/auth/realms/marklogic/.well-known/openid_configuration \
    --config-name Keycloak-Production \
    --marklogic-host ml-prod.company.com \
    --marklogic-user admin \
    --marklogic-pass "$ML_ADMIN_PASS" \
    --client-id marklogic-api \
    --username-attribute preferred_username \
    --role-attribute marklogic_roles \
    --cache-timeout 600

# Validate production configuration
./scripts/validate-oauth2-config.sh \
    --oauth-server-url https://keycloak.company.com \
    --config-name Keycloak-Production \
    --marklogic-host ml-prod.company.com \
    --performance --detailed
EOF
echo

# Example 3: Azure AD Enterprise Setup
echo "📝 Example 3: Azure AD Enterprise Setup"
echo "---------------------------------------"
echo
cat << 'EOF'
# Configure MarkLogic with Azure AD OAuth
./scripts/configure-marklogic-oauth2.sh \
    --well-known-url https://login.microsoftonline.com/YOUR-TENANT-ID/v2.0/.well-known/openid_configuration \
    --config-name AzureAD-Enterprise \
    --marklogic-host marklogic.azure.company.com \
    --marklogic-user ml-admin \
    --marklogic-pass "$AZURE_ML_PASS" \
    --client-id YOUR-AZURE-CLIENT-ID \
    --username-attribute upn \
    --role-attribute roles \
    --cache-timeout 900

# Test Azure AD integration
./scripts/validate-oauth2-config.sh \
    --oauth-server-url https://login.microsoftonline.com \
    --config-name AzureAD-Enterprise \
    --marklogic-host marklogic.azure.company.com \
    --client-id YOUR-AZURE-CLIENT-ID \
    --performance
EOF
echo

# Example 4: Validate Configuration
echo "📝 Example 4: Validate Configuration"
echo "-----------------------------------"
echo
cat << 'EOF'
# Validate existing OAuth2 configuration
./scripts/validate-oauth2-config.sh \
  --well-known-url "http://localhost:8080/oauth/.well-known/config" \
  --config-name "MLEAProxy-OAuth" \
  --marklogic-host "localhost" \
  --verbose

# This will test:
# 1. OAuth2 server connectivity
# 2. Discovery endpoint validation
# 3. Token generation flows  
# 4. MarkLogic configuration verification
# 5. End-to-end authentication validation
EOF
echo

# Example 5: CI/CD Integration
echo "📝 Example 5: CI/CD Integration"
echo "------------------------------"
echo
cat << 'EOF'
# Automated testing in CI/CD pipeline

# 1. Start services
docker run -d --name marklogic -p 8000:8000 -p 8002:8002 marklogic/marklogic-server:latest
cd MLEAProxy && mvn spring-boot:run &

# 2. Wait for services to start
sleep 60

# 3. Validate OAuth2 configuration
./scripts/validate-oauth2-config.sh \
    --oauth-server-url http://localhost:8080 \
    --config-name CI-Test \
    --marklogic-host localhost \
    --performance

# 5. Cleanup
docker stop marklogic && docker rm marklogic
pkill -f spring-boot:run
EOF
echo

echo "✅ All examples provided. Choose the one that matches your environment."
echo "📚 For more details, see the complete documentation in README.md"