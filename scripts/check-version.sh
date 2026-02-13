#!/bin/bash
# ================================================================
# MLEAProxy - Version Check Script
# ================================================================
# Checks local JAR version against latest GitHub release
# ================================================================

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuration
GITHUB_REPO="mwarnes/MLEAProxy"
LOCAL_JAR="release/mlesproxy-2.0.0.jar"
POM_FILE="pom.xml"

echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}                    MLEAProxy Version Check${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
echo ""

# ----------------------------------------------------------------
# Check Local Version (from pom.xml)
# ----------------------------------------------------------------
if [ -f "$POM_FILE" ]; then
    LOCAL_VERSION=$(grep -A 1 "<artifactId>mlesproxy</artifactId>" "$POM_FILE" | grep "<version>" | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | head -1)
    echo -e "${CYAN}📦 Local Project Version:${NC}"
    echo "  Version: $LOCAL_VERSION"
    echo "  Source: pom.xml"
else
    echo -e "${RED}Error: pom.xml not found${NC}"
    exit 1
fi

echo ""

# ----------------------------------------------------------------
# Check Local JAR
# ----------------------------------------------------------------
if [ -f "$LOCAL_JAR" ]; then
    JAR_SIZE=$(ls -lh "$LOCAL_JAR" | awk '{print $5}')
    JAR_DATE=$(ls -l "$LOCAL_JAR" | awk '{print $6, $7, $8}')

    echo -e "${CYAN}📁 Local JAR File:${NC}"
    echo "  Path: $LOCAL_JAR"
    echo "  Size: $JAR_SIZE"
    echo "  Modified: $JAR_DATE"

    # Try to extract version from JAR manifest
    if command -v unzip &> /dev/null; then
        MANIFEST_VERSION=$(unzip -p "$LOCAL_JAR" META-INF/MANIFEST.MF 2>/dev/null | grep "Implementation-Version:" | cut -d: -f2 | tr -d ' \r\n')
        if [ -n "$MANIFEST_VERSION" ]; then
            echo "  Manifest Version: $MANIFEST_VERSION"
        fi

        BUILD_TIME=$(unzip -p "$LOCAL_JAR" META-INF/MANIFEST.MF 2>/dev/null | grep "Build-Time:" | cut -d: -f2- | tr -d '\r\n' | xargs)
        if [ -n "$BUILD_TIME" ]; then
            echo "  Build Time: $BUILD_TIME"
        fi
    fi
else
    echo -e "${YELLOW}⚠️  Local JAR not found at: $LOCAL_JAR${NC}"
    echo "  Run: ./build.sh clean package"
fi

echo ""

# ----------------------------------------------------------------
# Check GitHub Latest Release
# ----------------------------------------------------------------
echo -e "${CYAN}🌐 Checking GitHub for latest release...${NC}"

if ! command -v curl &> /dev/null; then
    echo -e "${RED}Error: curl not found. Cannot check GitHub releases.${NC}"
    exit 1
fi

# Get latest release info from GitHub API
GITHUB_API_URL="https://api.github.com/repos/${GITHUB_REPO}/releases/latest"
RELEASE_INFO=$(curl -s "$GITHUB_API_URL")

# Check if API call was successful
if echo "$RELEASE_INFO" | grep -q "Not Found"; then
    echo -e "${YELLOW}⚠️  No releases found on GitHub${NC}"
    echo "  Repository: https://github.com/$GITHUB_REPO"
    echo ""
    echo -e "${CYAN}💡 Note:${NC} This might be a private repository or no releases have been published yet."
    LATEST_VERSION="unknown"
else
    # Extract version tag (remove 'v' prefix if present)
    LATEST_VERSION=$(echo "$RELEASE_INFO" | grep '"tag_name":' | sed 's/.*"tag_name": "\(.*\)".*/\1/' | sed 's/^v//')
    RELEASE_NAME=$(echo "$RELEASE_INFO" | grep '"name":' | head -1 | sed 's/.*"name": "\(.*\)".*/\1/')
    RELEASE_DATE=$(echo "$RELEASE_INFO" | grep '"published_at":' | sed 's/.*"published_at": "\(.*\)".*/\1/' | cut -d'T' -f1)
    RELEASE_URL=$(echo "$RELEASE_INFO" | grep '"html_url":' | head -1 | sed 's/.*"html_url": "\(.*\)".*/\1/')

    echo "  Latest Release: $LATEST_VERSION"
    echo "  Release Name: $RELEASE_NAME"
    echo "  Published: $RELEASE_DATE"
    echo "  URL: $RELEASE_URL"
fi

echo ""

# ----------------------------------------------------------------
# Version Comparison
# ----------------------------------------------------------------
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}                         Version Comparison${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
echo ""

if [ "$LATEST_VERSION" = "unknown" ]; then
    echo -e "${YELLOW}⚠️  Cannot compare versions (GitHub releases not available)${NC}"
    echo ""
    echo -e "${CYAN}Your current version:${NC} $LOCAL_VERSION"
elif [ "$LOCAL_VERSION" = "$LATEST_VERSION" ]; then
    echo -e "${GREEN}✅ You have the latest version!${NC}"
    echo ""
    echo "  Local Version:  $LOCAL_VERSION"
    echo "  Latest Version: $LATEST_VERSION"
elif [ "$LOCAL_VERSION" \> "$LATEST_VERSION" ]; then
    echo -e "${CYAN}🚀 You have a newer version than the latest release!${NC}"
    echo ""
    echo "  Local Version:  $LOCAL_VERSION (newer)"
    echo "  Latest Release: $LATEST_VERSION"
    echo ""
    echo "  This is normal if you're working on unreleased features."
else
    echo -e "${YELLOW}⚠️  A newer version is available!${NC}"
    echo ""
    echo "  Local Version:  $LOCAL_VERSION"
    echo "  Latest Version: $LATEST_VERSION"
    echo ""
    echo -e "${CYAN}To update:${NC}"
    echo "  1. Pull latest changes: git pull origin master"
    echo "  2. Rebuild: ./build.sh clean package"
    echo "  3. Or download release: $RELEASE_URL"
fi

echo ""

# ----------------------------------------------------------------
# Git Status Check
# ----------------------------------------------------------------
if git rev-parse --git-dir > /dev/null 2>&1; then
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                         Git Repository Status${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""

    # Current branch
    CURRENT_BRANCH=$(git branch --show-current)
    echo -e "${CYAN}📍 Current Branch:${NC} $CURRENT_BRANCH"

    # Latest commit
    LATEST_COMMIT=$(git log -1 --format="%h - %s (%ar)")
    echo -e "${CYAN}📝 Latest Commit:${NC} $LATEST_COMMIT"

    # Check if behind remote
    git fetch origin "$CURRENT_BRANCH" 2>/dev/null
    LOCAL_COMMIT=$(git rev-parse HEAD)
    REMOTE_COMMIT=$(git rev-parse "origin/$CURRENT_BRANCH" 2>/dev/null)

    if [ "$LOCAL_COMMIT" = "$REMOTE_COMMIT" ]; then
        echo -e "${CYAN}🔄 Remote Status:${NC} ${GREEN}Up to date${NC}"
    else
        BEHIND_COUNT=$(git rev-list HEAD..origin/$CURRENT_BRANCH --count 2>/dev/null)
        if [ "$BEHIND_COUNT" -gt 0 ]; then
            echo -e "${CYAN}🔄 Remote Status:${NC} ${YELLOW}Behind by $BEHIND_COUNT commit(s)${NC}"
            echo "  Run: git pull origin $CURRENT_BRANCH"
        else
            AHEAD_COUNT=$(git rev-list origin/$CURRENT_BRANCH..HEAD --count 2>/dev/null)
            if [ "$AHEAD_COUNT" -gt 0 ]; then
                echo -e "${CYAN}🔄 Remote Status:${NC} ${CYAN}Ahead by $AHEAD_COUNT commit(s)${NC}"
            fi
        fi
    fi

    echo ""
fi

# ----------------------------------------------------------------
# Build Check
# ----------------------------------------------------------------
if [ ! -f "target/mlesproxy-$LOCAL_VERSION.jar" ]; then
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                         Build Status${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "${YELLOW}⚠️  JAR not found in target/ directory${NC}"
    echo ""
    echo -e "${CYAN}To build:${NC}"
    echo "  ./build.sh clean package"
    echo ""
fi

echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
