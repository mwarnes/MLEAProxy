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

# ----------------------------------------------------------------
# Version sweep
# ----------------------------------------------------------------
# The version is not confined to pom.xml. Each scripts/start*.sh resolves the
# JAR by exact filename, create-distribution.sh names the archive from it, and
# the docs quote it in every java -jar example - so a pom-only bump produces a
# build that none of the start scripts can find.

VERSION_BACKUP_DIR=""

# Files carrying the build's version. Java sources are excluded deliberately:
# their @since tags record when a type was added and must not move.
version_files() {
    local version="$1"
    git grep -l --fixed-strings "$version" -- . 2>/dev/null \
        | grep -v '^src/' \
        | grep -v '^docs/superpowers/' \
        || true
}

# Lines stating when something was introduced or fixed are history, not the
# current version, and are left alone. Without this a bump silently rewrites
# claims like "Fixed in MLEAProxy 2.0.4" into something untrue.
#
# Matched case-insensitively and deliberately biased towards skipping: leaving a
# line for a human to check costs a moment, whereas rewriting a historical claim
# produces documentation that is confidently wrong. Anything skipped is listed
# by the "still referencing" report in step 1.
HISTORICAL_RE='@since|or later|fixed in|added in|introduced in|deprecated in'

apply_version_sweep() {
    local from="$1" to="$2"
    local from_re
    # Doubled backslashes: awk resolves escape sequences in -v assignments, so
    # "X\\.Y\\.Z" here reaches the program as the regex "X\.Y\.Z". Passing a
    # single backslash yields a bare "." that matches any character, which would
    # make the sweep match things like "2x0y5".
    from_re=$(printf '%s' "$from" | sed 's/\./\\\\./g')

    VERSION_BACKUP_DIR=$(mktemp -d)
    local scratch count=0 skipped=0 file
    scratch=$(mktemp)

    while IFS= read -r file; do
        [ -n "$file" ] || continue
        mkdir -p "$VERSION_BACKUP_DIR/$(dirname "$file")"
        cp -p "$file" "$VERSION_BACKUP_DIR/$file"

        awk -v re="$from_re" -v new="$to" -v hist="$HISTORICAL_RE" '
            tolower($0) ~ hist { print; next }
            { gsub(re, new); print }
        ' "$file" > "$scratch"

        # Redirect rather than mv, so the executable bit on the start scripts
        # survives.
        cat "$scratch" > "$file"

        if ! cmp -s "$file" "$VERSION_BACKUP_DIR/$file"; then
            count=$((count + 1))
            echo "   • $file"
        else
            skipped=$((skipped + 1))
        fi
    done < <(version_files "$from")
    rm -f "$scratch"

    echo "   ✓ Updated $count file(s)"
    if [ "$skipped" -gt 0 ]; then
        echo -e "   ${YELLOW}• $skipped file(s) matched only historical references and were left alone${NC}"
    fi
}

# Puts every swept file back exactly as it was, so a failed build or test run
# does not leave the tree half-bumped.
restore_version_files() {
    [ -n "$VERSION_BACKUP_DIR" ] && [ -d "$VERSION_BACKUP_DIR" ] || return 0
    local file
    while IFS= read -r file; do
        [ -n "$file" ] || continue
        cp -p "$VERSION_BACKUP_DIR/$file" "$file"
    done < <(cd "$VERSION_BACKUP_DIR" && find . -type f | sed 's|^\./||')
    echo -e "   ${YELLOW}↩ Version changes reverted${NC}"
}

cleanup_version_backup() {
    [ -n "$VERSION_BACKUP_DIR" ] && rm -rf "$VERSION_BACKUP_DIR"
}
trap cleanup_version_backup EXIT

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
echo "  1. Update version across pom.xml, scripts and docs: $CURRENT_VERSION → $NEW_VERSION"
echo "     (shown for review before anything is committed)"
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

# Step 1: Update the version everywhere it appears
echo -e "${CYAN}1. Updating version across the repository...${NC}"
apply_version_sweep "$CURRENT_VERSION" "$NEW_VERSION"

# Verify pom.xml specifically, since everything downstream depends on it
NEW_POM_VERSION=$(grep -A 1 "<artifactId>mlesproxy</artifactId>" pom.xml | grep "<version>" | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | head -1)
if [ "$NEW_POM_VERSION" != "$NEW_VERSION" ]; then
    echo -e "${RED}Error: Failed to update pom.xml version${NC}"
    echo "Expected: $NEW_VERSION, Got: $NEW_POM_VERSION"
    restore_version_files
    exit 1
fi

# Anything still naming the old version is either a deliberate historical
# reference or something the sweep missed; either way it should be looked at.
REMAINING=$(version_files "$CURRENT_VERSION")
if [ -n "$REMAINING" ]; then
    echo -e "   ${YELLOW}Still referencing $CURRENT_VERSION (expected for historical notes):${NC}"
    echo "$REMAINING" | sed 's/^/     /'
fi
echo "   ✓ Version is now $NEW_VERSION"

# No keyword heuristic can reliably distinguish "the current version is X.Y.Z"
# from "vX.Y.Z covers everything back to <older>" - the second is history and
# must not move. The sweep is therefore shown for review before anything is
# built, committed or pushed. Tag lists, changelog entries and "Full Changelog"
# compare links are the usual offenders.
echo ""
echo -e "${CYAN}Review the version changes:${NC}"
git --no-pager diff --stat
echo ""
read -p "Show the full diff? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    git --no-pager diff
    echo ""
fi
read -p "Do these changes look correct? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    restore_version_files
    echo "Cancelled. Fix by hand, or adjust HISTORICAL_RE in this script."
    exit 1
fi

# Step 2: Build JAR
echo ""
echo -e "${CYAN}2. Building JAR file...${NC}"
# build.sh pipes Maven through grep to filter JDK warnings, so its exit status
# is grep's - a failed build can still exit 0. The JAR is the real evidence.
if ! ./build.sh clean package || [ ! -f "target/mlesproxy-$NEW_VERSION.jar" ]; then
    echo -e "${RED}Error: Build failed (no target/mlesproxy-$NEW_VERSION.jar)${NC}"
    restore_version_files
    exit 1
fi
echo "   ✓ Build successful"

# Step 3: Run tests
echo ""
echo -e "${CYAN}3. Running tests...${NC}"
if ! mvn test -q; then
    echo -e "${RED}Error: Tests failed${NC}"
    restore_version_files
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
# The sweep touches the scripts, create-distribution.sh and the docs as well
git add -A
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
