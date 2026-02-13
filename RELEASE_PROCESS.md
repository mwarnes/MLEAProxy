# MLEAProxy Release Process

This document explains how to create new releases for MLEAProxy.

## Understanding GitHub Releases

**GitHub releases are 100% manual** - they never update automatically. You must:

1. Update the version in code
2. Build and test
3. Create a git tag
4. Push the tag
5. Create the GitHub release (manually or via CLI)

## Current State

- **pom.xml**: 2.0.0 (current development version)
- **Git tags**: v2.0.0, v2.0.1
- **Latest GitHub release**: v2.0.1 (October 2025)

## Prerequisites

Install GitHub CLI (required for automated releases):

```bash
# macOS
brew install gh

# Authenticate
gh auth login
```

---

## Quick Release (Automated)

Use the automated release script:

```bash
./scripts/create-release.sh
```

**What it does:**

1. ✓ Updates pom.xml version
2. ✓ Builds JAR file
3. ✓ Runs all tests
4. ✓ Commits version change
5. ✓ Creates git tag
6. ✓ Pushes to GitHub
7. ✓ Creates GitHub release with JAR attachment

---

## Manual Release Process

If you prefer to do it manually:

### Step 1: Update Version

Edit `pom.xml` and change version:

```xml
<artifactId>mlesproxy</artifactId>
<version>2.0.2</version>  <!-- Change this -->
```

### Step 2: Build and Test

```bash
# Clean build
./build.sh clean package

# Run tests
mvn test

# Verify JAR
ls -lh target/mlesproxy-2.0.2.jar
```

### Step 3: Copy to Release Directory

```bash
mkdir -p release
cp target/mlesproxy-2.0.2.jar release/
```

### Step 4: Commit and Tag

```bash
# Commit version change
git add pom.xml
git commit -m "chore: bump version to 2.0.2"

# Create tag
git tag -a v2.0.2 -m "Release version 2.0.2"

# Push
git push origin master
git push origin v2.0.2
```

### Step 5: Create GitHub Release

**Option A: Using GitHub CLI (Recommended)**

```bash
gh release create v2.0.2 \
  release/mlesproxy-2.0.2.jar \
  --title "MLEAProxy v2.0.2" \
  --notes "Release notes here"
```

**Option B: Using GitHub Web Interface**

1. Go to: https://github.com/mwarnes/MLEAProxy/releases/new
2. Select tag: v2.0.2
3. Release title: "MLEAProxy v2.0.2"
4. Add release notes
5. Attach JAR: `release/mlesproxy-2.0.2.jar`
6. Click "Publish release"

---

## Version Numbering

MLEAProxy uses semantic versioning: **MAJOR.MINOR.PATCH**

- **MAJOR** (2.x.x): Breaking changes
- **MINOR** (x.0.x): New features, backwards compatible
- **PATCH** (x.x.0): Bug fixes, backwards compatible

### Current: 2.0.0

**Next versions:**

- **2.0.1**: Bug fix release
- **2.0.2**: Another bug fix
- **2.1.0**: New feature (backwards compatible)
- **3.0.0**: Breaking changes

---

## Release Checklist

Before creating a release:

- [ ] All tests passing (`mvn test`)
- [ ] Build successful (`./build.sh clean package`)
- [ ] CHANGELOG.md updated (if exists)
- [ ] Documentation up to date
- [ ] No uncommitted changes
- [ ] Version number incremented in pom.xml

---

## Post-Release

After creating a release, decide on next development version:

**Option 1: Continue with released version**

```bash
# Keep pom.xml at 2.0.2
# Continue development for next patch release
```

**Option 2: Bump to next version with SNAPSHOT**

```bash
# Update pom.xml to 2.0.3-SNAPSHOT
# Indicates ongoing development
```

---

## Fixing Version Mismatch

If your pom.xml version doesn't match the latest tag:

### Current Situation

- pom.xml: 2.0.0
- Latest tag: v2.0.1
- Latest release: v2.0.1

### Fix

**Either:**

1. **Sync to latest release**:
   ```bash
   # Update pom.xml to 2.0.1
   sed -i.bak 's/<version>2.0.0<\/version>/<version>2.0.1<\/version>/' pom.xml
   git add pom.xml
   git commit -m "chore: sync version to 2.0.1"
   git push
   ```

2. **Prepare next release**:
   ```bash
   # Update pom.xml to 2.0.2 and create release
   ./scripts/create-release.sh
   ```

---

## Viewing Releases

**Check GitHub releases:**

```bash
gh release list
```

**Check local tags:**

```bash
git tag -l
```

**Check version:**

```bash
./scripts/check-version.sh
```

---

## Deleting a Release (if needed)

**Delete GitHub release:**

```bash
gh release delete v2.0.2 --yes
```

**Delete git tag:**

```bash
git tag -d v2.0.2
git push origin :refs/tags/v2.0.2
```

---

## CI/CD Integration (Future)

Consider automating releases with GitHub Actions:

1. Create `.github/workflows/release.yml`
2. Trigger on version tags (v*)
3. Automatically build, test, and create release
4. Attach JAR to release

---

## Questions?

- **When should I create a release?** When you want to distribute a stable version
- **How often?** As needed - could be weekly, monthly, or per feature
- **Do I need to?** Only if you want versioned distributions. Development can continue without releases
- **Will it auto-update?** NO - releases are completely manual

---

## Example Release Notes Template

```markdown
## What's Changed

### Features
- Added comprehensive startup scripts with detailed server information
- New version check script to compare local vs GitHub releases
- Enhanced SAML and Kerberos startup display

### Bug Fixes
- Fixed resource leaks in ApplicationListener
- Fixed thread-unsafe rate limiting

### Documentation
- Updated scripts/README.md with example outputs
- Added CLEANUP_GIT.md with repository maintenance guide
- Enhanced protocol-specific documentation

### Dependencies
- Updated UnboundID LDAP SDK to 7.0.4
- Updated other dependencies (see pom.xml)

**Full Changelog**: https://github.com/mwarnes/MLEAProxy/compare/v2.0.1...v2.0.2
```

---

For more information about the release script:

```bash
./scripts/create-release.sh --help
```
