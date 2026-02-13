# Git Repository Cleanup Guide

This guide explains how to remove unwanted files from your Git repository.

## Files Currently Tracked That Should Be Ignored

The following files are tracked but should be removed from Git:

### 1. macOS Files (.DS_Store)
- `.DS_Store` (18 files throughout the repository)

### 2. IDE Configuration
- `.vscode/settings.json`

### 3. Generated Property Files (Root Directory)
- `ldap.properties`
- `oauth.properties`
- `saml.properties`
- `kerberos.properties`

### 4. Generated MarkLogic Configuration Files
- `marklogic-external-security-ldapjson-instructions.txt`
- `marklogic-external-security-ldapjson.json`
- `marklogic-external-security-marklogic-instructions.txt`
- `marklogic-external-security-marklogic.json`

---

## Quick Cleanup (Recommended)

This removes files from Git but keeps them on your local disk:

```bash
# Remove .DS_Store files
git rm --cached .DS_Store src/.DS_Store src/main/.DS_Store src/main/java/.DS_Store \
  src/main/java/com/.DS_Store src/main/resources/.DS_Store src/test/.DS_Store \
  src/test/java/.DS_Store src/test/java/com/.DS_Store

# Remove VS Code settings
git rm --cached .vscode/settings.json

# Remove generated property files
git rm --cached ldap.properties oauth.properties saml.properties kerberos.properties

# Remove generated MarkLogic config files
git rm --cached marklogic-external-security-ldapjson-instructions.txt \
  marklogic-external-security-ldapjson.json \
  marklogic-external-security-marklogic-instructions.txt \
  marklogic-external-security-marklogic.json

# Commit the cleanup
git commit -m "chore: remove tracked files that should be ignored

- Remove .DS_Store files (macOS artifacts)
- Remove .vscode/settings.json (IDE config)
- Remove generated property files (ldap, oauth, saml, kerberos)
- Remove generated MarkLogic external security config files
- Updated .gitignore to prevent future tracking

These files are generated at runtime or by startup scripts and should
not be version controlled."

# Push changes
git push origin master
```

---

## Complete History Cleanup (Optional - Advanced)

**WARNING**: This rewrites Git history. Only do this if:
- You're okay with rewriting history (requires force push)
- All collaborators are aware and can re-clone
- You want to reduce repository size significantly

### Step 1: Backup First

```bash
# Create a backup branch
git branch backup-before-cleanup
```

### Step 2: Use BFG Repo-Cleaner (Recommended)

```bash
# Install BFG (macOS)
brew install bfg

# Remove .DS_Store from entire history
bfg --delete-files .DS_Store

# Clean up the repository
git reflog expire --expire=now --all
git gc --prune=now --aggressive

# Force push (DESTRUCTIVE - warns collaborators first!)
git push origin master --force
```

### Step 3: Alternative - Git Filter-Branch (Manual)

```bash
# Remove .DS_Store files from entire history
git filter-branch --force --index-filter \
  'git rm --cached --ignore-unmatch **/.DS_Store .DS_Store' \
  --prune-empty --tag-name-filter cat -- --all

# Remove generated property files from history
git filter-branch --force --index-filter \
  'git rm --cached --ignore-unmatch ldap.properties oauth.properties saml.properties kerberos.properties' \
  --prune-empty --tag-name-filter cat -- --all

# Clean up
git reflog expire --expire=now --all
git gc --prune=now --aggressive

# Force push (DESTRUCTIVE)
git push origin master --force
```

### Step 4: Verify Size Reduction

```bash
# Check repository size before and after
du -sh .git

# If satisfied, delete backup branch
git branch -D backup-before-cleanup
```

---

## Post-Cleanup: Collaborator Instructions

If you performed history cleanup (force push), all collaborators need to:

```bash
# Save any local work
git stash

# Delete local repo and re-clone
cd ..
rm -rf MLEAProxy
git clone https://github.com/mwarnes/MLEAProxy.git
cd MLEAProxy

# Restore local work if needed
git stash pop
```

---

## Repository Size Analysis

**Current State:**
- Total repository: 386MB
- .git directory: 289MB (75% of total!)

**Expected After Quick Cleanup:**
- Minimal immediate size reduction (files still in history)
- Prevents future bloat

**Expected After History Cleanup:**
- Significant reduction (estimated 50-100MB saved)
- Faster clone times
- Cleaner history

---

## Prevent Future Issues

The updated `.gitignore` now prevents these files from being tracked:

✅ Operating system files (.DS_Store, Thumbs.db)
✅ IDE files (.vscode/, .idea/)
✅ Generated property files (*.properties in root)
✅ Log files (*.log)
✅ PID files (*.pid)
✅ Generated MarkLogic configs
✅ Test and coverage reports
✅ Temporary files

---

## Verification

After cleanup, verify with:

```bash
# Check working tree is clean
git status

# Verify ignored files aren't tracked
git ls-files | grep -E '(\.DS_Store|\.vscode|ldap\.properties|oauth\.properties)'
# Should return nothing

# Check repository size
du -sh .git
```

---

## Recommendation

**For immediate improvement:**
1. Run the **Quick Cleanup** commands above
2. Commit and push
3. Monitor repository size on next clone

**For maximum size reduction:**
1. Run **Quick Cleanup** first
2. If size is still an issue, perform **Complete History Cleanup**
3. Notify all collaborators to re-clone

---

## Questions?

- Quick cleanup: Safe, reversible, recommended
- History cleanup: Advanced, requires coordination, but reduces repo size significantly
