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

- **pom.xml**: 2.0.5
- **Git tags**: v2.0.0, v2.0.1, v2.0.2, v2.0.5
- **Latest GitHub release**: v2.0.5 (September 2026)

There are no v2.0.3 or v2.0.4 tags: those were development versions that were
never released, so v2.0.5 covers everything back to v2.0.2.

Check this section against reality with `./scripts/check-version.sh` rather than
trusting it - it has gone stale before.

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

The version appears in about 20 files, not just `pom.xml`. A pom-only bump
builds a JAR that none of the start scripts can find, because each one resolves
it by exact filename.

Start with `pom.xml`:

```xml
<artifactId>mlesproxy</artifactId>
<version>2.0.6</version>  <!-- Change this -->
```

Then sweep the rest:

```bash
# Everything that names the JAR or the distribution archive
git grep -l '2\.0\.5' -- . | grep -v '^src/'
```

That covers the six `scripts/start*.sh`, `scripts/check-version.sh`,
`create-distribution.sh`, `AGENTS.md`, `examples/marklogic/`, and the command
examples throughout `docs/user/`.

**Do not bump these** - they are statements about history, not the current
build:

- `@since` tags in Java sources (they record when a type was added)
- "MLEAProxy 2.0.4 or later" as the minimum for the Authorization Code flow
- "Fixed in MLEAProxy 2.0.4" for the `aud` claim shape

Afterwards, confirm nothing was missed:

```bash
git grep -n '2\.0\.5' -- . | grep -v '^src/'   # should show only the above
```

### Step 2: Build and Test

```bash
# Clean build
./build.sh clean package

# Run tests
mvn test

# Verify JAR and the version it reports
ls -lh target/mlesproxy-2.0.6.jar
unzip -p target/mlesproxy-2.0.6.jar META-INF/MANIFEST.MF | grep Implementation-Version
```

> `build.sh` pipes Maven through `grep` to filter JDK warnings, so its exit
> status is grep's, not Maven's - a failed build can still exit 0. Run
> `mvn test` separately and check that, rather than relying on `./build.sh`
> succeeding.

### Step 3: Copy to Release Directory

```bash
mkdir -p release
cp target/mlesproxy-2.0.6.jar release/
```

### Step 4: Commit and Tag

```bash
# Commit the version change - Step 1 touches around 20 files, not just pom.xml
git add -A
git commit -m "chore: bump version to 2.0.6"

# Create tag
git tag -a v2.0.6 -m "Release version 2.0.6"

# Push
git push origin master
git push origin v2.0.6
```

### Step 5: Create GitHub Release

**Option A: Using GitHub CLI (Recommended)**

```bash
gh release create v2.0.6 \
  release/mlesproxy-2.0.6.jar \
  --title "MLEAProxy v2.0.6" \
  --notes "Release notes here"
```

**Option B: Using GitHub Web Interface**

1. Go to: https://github.com/mwarnes/MLEAProxy/releases/new
2. Select tag: v2.0.6
3. Release title: "MLEAProxy v2.0.6"
4. Add release notes
5. Attach JAR: `release/mlesproxy-2.0.6.jar`
6. Click "Publish release"

---

## Version Numbering

MLEAProxy uses semantic versioning: **MAJOR.MINOR.PATCH**

- **MAJOR** (X.y.z): Breaking changes
- **MINOR** (x.Y.z): New features, backwards compatible
- **PATCH** (x.y.Z): Bug fixes, backwards compatible

### Current: 2.0.5

**Next versions:**

- **2.0.6**: Bug fix release
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
- [ ] Version incremented in pom.xml **and** swept through the scripts,
      `create-distribution.sh` and docs (Step 1)
- [ ] `git grep` confirms no stale references to the previous version

---

## Post-Release

After creating a release, decide on next development version:

**Option 1: Continue with released version**

```bash
# Keep pom.xml at 2.0.5
# Continue development for next patch release
```

**Option 2: Bump to next version with SNAPSHOT**

```bash
# Update pom.xml to 2.0.6-SNAPSHOT
# Indicates ongoing development
```

---

## Fixing Version Mismatch

`./scripts/check-version.sh` compares the pom version, the built JAR and the
latest GitHub release, and reports any disagreement.

A mismatch is not necessarily wrong - the pom is normally ahead of the latest
release while a version is in development, which is how 2.0.3 and 2.0.4 came
and went without ever being tagged. It matters when you intended to release and
did not.

**Either:**

1. **Release what is in the pom** - follow the process above, or run
   `./scripts/create-release.sh`.

2. **Move the pom to match the latest release** - only when the in-progress
   version is being abandoned:
   ```bash
   # Bump pom.xml and everything in Step 1, then
   git commit -am "chore: sync version to <version>"
   git push
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
gh release delete v2.0.6 --yes
```

**Delete git tag:**

```bash
git tag -d v2.0.6
git push origin :refs/tags/v2.0.6
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

**Full Changelog**: https://github.com/mwarnes/MLEAProxy/compare/v2.0.5...v2.0.6
```

---

For more information about the release script:

```bash
./scripts/create-release.sh --help
```
