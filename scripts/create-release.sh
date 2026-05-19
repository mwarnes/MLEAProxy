#!/bin/bash
# ================================================================
# MLEAProxy - Create Release Script
# ================================================================
# Creates a new GitHub release with proper versioning
# ================================================================

set -e  # Exit on error

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}                    MLEAProxy Release Creator${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
echo ""

# Check for gh CLI
if ! command -v gh &> /dev/null; then
    echo -e "${RED}Error: GitHub CLI (gh) is not installed${NC}"
    echo ""
    echo "Install with:"
    echo "  brew install gh"
    echo ""
    echo "Then authenticate:"
    echo "  gh auth login"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo -e "${RED}Error: Not authenticated with GitHub${NC}"
    echo "Run: gh auth login"
    exit 1
fi

# Get current version from pom.xml
CURRENT_VERSION=$(grep -A 1 "<artifactId>mlesproxy</artifactId>" pom.xml | grep "<version>" | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | head -1)

echo -e "${CYAN}📦 Current Version:${NC} $CURRENT_VERSION"
echo ""

# Check if working tree is clean
if ! git diff-index --quiet HEAD --; then
    echo -e "${YELLOW}⚠️  Warning: Working directory has uncommitted changes${NC}"
    echo ""
    git status --short
    echo ""
    read -p "Continue anyway? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Cancelled."
        exit 1
    fi
fi

# Prompt for new version
echo -e "${CYAN}Enter new version number (e.g., 2.0.2):${NC}"
read -p "Version: " NEW_VERSION

if [ -z "$NEW_VERSION" ]; then
    echo -e "${RED}Error: Version cannot be empty${NC}"
    exit 1
fi

# Remove 'v' prefix if provided
NEW_VERSION=${NEW_VERSION#v}

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}                         Release Plan${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${CYAN}Steps:${NC}"
echo "  1. Update pom.xml version: $CURRENT_VERSION → $NEW_VERSION"
echo "  2. Build JAR file"
echo "  3. Run tests"
echo "  4. Commit version change"
echo "  5. Create git tag: v$NEW_VERSION"
echo "  6. Push commits and tags"
echo "  7. Create GitHub release with JAR"
echo ""
read -p "Proceed with release? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cancelled."
    exit 1
fi

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}                         Creating Release${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════════════${NC}"
echo ""

# Step 1: Update pom.xml version
echo -e "${CYAN}1. Updating pom.xml version...${NC}"
# Use a more precise sed that targets the project version specifically
# Look for the line after <artifactId>mlesproxy</artifactId> and update the next <version> tag
sed -i.bak "/<artifactId>mlesproxy<\/artifactId>/,/<version>/{s/<version>$CURRENT_VERSION<\/version>/<version>$NEW_VERSION<\/version>/;}" pom.xml
rm -f pom.xml.bak

# Verify the change worked
NEW_POM_VERSION=$(grep -A 1 "<artifactId>mlesproxy</artifactId>" pom.xml | grep "<version>" | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | head -1)
if [ "$NEW_POM_VERSION" != "$NEW_VERSION" ]; then
    echo -e "${RED}Error: Failed to update pom.xml version${NC}"
    echo "Expected: $NEW_VERSION, Got: $NEW_POM_VERSION"
    exit 1
fi
echo "   ✓ Updated to $NEW_VERSION"

# Step 2: Build JAR
echo ""
echo -e "${CYAN}2. Building JAR file...${NC}"
if ! ./build.sh clean package; then
    echo -e "${RED}Error: Build failed${NC}"
    # Restore original version
    sed -i.bak "s/<version>$NEW_VERSION<\/version>/<version>$CURRENT_VERSION<\/version>/" pom.xml
    rm pom.xml.bak
    exit 1
fi
echo "   ✓ Build successful"

# Step 3: Run tests
echo ""
echo -e "${CYAN}3. Running tests...${NC}"
if ! mvn test -q; then
    echo -e "${RED}Error: Tests failed${NC}"
    # Restore original version
    sed -i.bak "s/<version>$NEW_VERSION<\/version>/<version>$CURRENT_VERSION<\/version>/" pom.xml
    rm pom.xml.bak
    exit 1
fi
echo "   ✓ All tests passed"

# Copy JAR to release directory
mkdir -p release
cp "target/mlesproxy-$NEW_VERSION.jar" "release/mlesproxy-$NEW_VERSION.jar"
echo "   ✓ JAR copied to release/"

# Step 4: Commit version change
echo ""
echo -e "${CYAN}4. Committing version change...${NC}"
git add pom.xml
git commit -m "chore: bump version to $NEW_VERSION"
echo "   ✓ Committed"

# Step 5: Create tag
echo ""
echo -e "${CYAN}5. Creating git tag v$NEW_VERSION...${NC}"
git tag -a "v$NEW_VERSION" -m "Release version $NEW_VERSION"
echo "   ✓ Tag created"

# Step 6: Push
echo ""
echo -e "${CYAN}6. Pushing to GitHub...${NC}"
git push origin master
git push origin "v$NEW_VERSION"
echo "   ✓ Pushed"

# Step 7: Create GitHub release
echo ""
echo -e "${CYAN}7. Creating GitHub release...${NC}"
echo ""
echo -e "${CYAN}Enter release notes (press Ctrl+D when done):${NC}"
RELEASE_NOTES=$(cat)

if [ -z "$RELEASE_NOTES" ]; then
    RELEASE_NOTES="Release version $NEW_VERSION"
fi

gh release create "v$NEW_VERSION" \
    "release/mlesproxy-$NEW_VERSION.jar" \
    --title "MLEAProxy v$NEW_VERSION" \
    --notes "$RELEASE_NOTES"

echo "   ✓ GitHub release created"

echo ""
echo -e "${GREEN}╔════════════════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                    Release Created Successfully!                           ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${CYAN}Release Details:${NC}"
echo "  Version: v$NEW_VERSION"
echo "  Tag: v$NEW_VERSION"
echo "  JAR: release/mlesproxy-$NEW_VERSION.jar"
echo ""
echo -e "${CYAN}View release:${NC}"
echo "  https://github.com/mwarnes/MLEAProxy/releases/tag/v$NEW_VERSION"
echo ""
echo -e "${CYAN}Next steps:${NC}"
echo "  1. Update pom.xml to next development version (e.g., ${NEW_VERSION%.*}.$((${NEW_VERSION##*.}+1))-SNAPSHOT)"
echo "  2. Or continue development with $NEW_VERSION"
echo ""
