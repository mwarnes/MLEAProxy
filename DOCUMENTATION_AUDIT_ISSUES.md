# Documentation Audit Issues

**Generated:** 2026-07-25  
**Auditor:** Pi Agent (Subagent-Driven Development)  
**MLEAProxy Version:** 2.0.3  
**Audit Scope:** All user documentation (8 files)

---

## Summary

- **Total Issues:** 88
- **Category 1 (Simple Fixes):** 47
- **Category 2 (Requires MLEAProxy):** 10
- **Category 3 (Requires MarkLogic):** 0 (not tested)
- **Category 4 (Structural):** 23
- **Critical Issues:** 8 (missing endpoints, broken functionality)

---

## Category 1: Simple Fixes

*No external dependencies - typos, broken links, formatting issues*

### README.md (15 issues)

- [ ] Line 14: Reference to `TESTING_GUIDE.md` - **file does not exist** (broken link) 🔴
- [ ] Line 14-18: Inconsistent path format - some use `./docs/user/` prefix, some don't
- [ ] Line 113: Link to `./LDAP_GUIDE.md` missing `docs/user/` prefix
- [ ] Line 114: Link to `./OAUTH_GUIDE.md` missing `docs/user/` prefix
- [ ] Line 115: Link to `./SAML_GUIDE.md` missing `docs/user/` prefix
- [ ] Line 116: Link to `./KERBEROS_GUIDE.md` missing `docs/user/` prefix
- [ ] Line 152: Port mismatch - shows "10389" but QUICKSTART shows three ports (10389, 20389, 60389)
- [ ] Line 274: Port reference inconsistency with QUICKSTART
- [ ] Multiple sections: Inconsistent use of `---` horizontal separators
- [ ] Line 297: Commented-out "What's New in 2025" section - remove or uncomment
- [ ] Line 571: Reference to `./LDAP_GUIDE.md` should be `./docs/user/LDAP_GUIDE.md`
- [ ] Line 572: Reference to `./OAUTH_GUIDE.md` should be `./docs/user/OAUTH_GUIDE.md`
- [ ] Line 573: Reference to `./SAML_GUIDE.md` should be `./docs/user/SAML_GUIDE.md`
- [ ] Line 574: Reference to `./KERBEROS_GUIDE.md` should be `./docs/user/KERBEROS_GUIDE.md`
- [ ] Line 576: Reference to `./TESTING_GUIDE.md` - **file does not exist** (broken link) 🔴

### docs/user/README.md (3 issues)

- [ ] Line 16: Reference to `TESTING_GUIDE.md` - **file does not exist** (broken link) 🔴
- [ ] Line 34: "Last Updated: May 18, 2026" - **future date**, should be 2025
- [ ] Missing file: `TESTING_GUIDE.md` referenced throughout but doesn't exist

### docs/user/QUICKSTART_VERIFICATION.md (5 issues)

- [ ] Line 1: Missing file path/breadcrumb navigation
- [ ] Line 139: Test 3 example doesn't show actual LDIF output (vague description)
- [ ] Line 320-321: Code block missing language identifier for syntax highlighting
- [ ] Line 390: Script uses `jq` but doesn't check if installed (needs prerequisite or fallback)
- [ ] **CRITICAL:** Documents 7 endpoints that don't exist (see Category 2) 🔴

### docs/user/LDAP_GUIDE.md (4 issues)

- [ ] Line 41: Port "10389" and "20389" inconsistent with README defaults
- [ ] Line 65: Link format inconsistency
- [ ] Multiple code blocks: Missing syntax highlighting language identifiers (properties, bash, ldif)
- [ ] Line 571-577: Related Documentation section uses relative paths without `./` prefix

### docs/user/OAUTH_GUIDE.md (5 issues)

- [ ] Line 246: "mleaproxy-2.0.3.jar" - verify version matches release
- [ ] Line 469: Reference to `./scripts/extract-jwks-keys.sh` - verify script exists
- [ ] Line 476: Reference to `./scripts/cleanup-obsolete-jwks-keys.sh` - verify script exists
- [ ] Line 480: Reference to `docs/developer/README-JWKS-Integration.md` - verify file exists
- [ ] Line 695-702: Related Documentation section - inconsistent link format

### docs/user/SAML_GUIDE.md (4 issues)

- [ ] Line 1: Missing breadcrumb navigation
- [ ] Line 245: Test command uses `fold -w 64` (may not work on all platforms - macOS/Linux difference)
- [ ] Line 579-585: Related Documentation section - inconsistent link format
- [ ] Multiple code blocks: Missing language identifiers for syntax highlighting

### docs/user/KERBEROS_GUIDE.md (6 issues)

- [ ] Line 1: Missing breadcrumb navigation
- [ ] Line 151: Port 8088 explanation could be more prominent (callout box)
- [ ] Line 247: Should note `./kerberos/krb5.conf` is auto-generated on first run
- [ ] Line 556-563: Related Documentation section - inconsistent link format
- [ ] Multiple code blocks: Missing syntax highlighting language identifiers
- [ ] Lines 419-421: References to `../developer/KERBEROS_PHASE[1-3]_COMPLETE.md` - verify files exist

### docs/user/CONFIGURATION_GUIDE.md (5 issues)

- [ ] Line 1: **No table of contents despite 880+ lines** 🔴
- [ ] Line 26: "mleaproxy-2.0.3.jar" version reference consistency
- [ ] Line 62-71: Property loading priority list - inconsistent numbering/formatting
- [ ] Multiple code blocks: Missing syntax highlighting (properties, bash, yaml)
- [ ] No "Related Documentation" section at end
- [ ] No breadcrumb navigation

---

## Category 2: Requires MLEAProxy Running

*Issues found by testing commands with MLEAProxy running*

### Missing Endpoints (7 issues - CRITICAL) 🔴

These endpoints are documented but return 404:

- [ ] **`/status`** - Documented in task brief, returns 404
  - **Note:** StatusHandler.java was implemented in earlier commits, but JAR may not have been rebuilt
  - **Action:** Verify implementation, rebuild JAR, or remove from docs if not implemented

- [ ] **`/saml/metadata`** - Should redirect to `/saml/idp-metadata`
  - Referenced in: README.md endpoint table, SAML_GUIDE.md
  - Working alternative: `/saml/idp-metadata` ✅
  - **Action:** Update all docs to use `/saml/idp-metadata` instead

- [ ] **`/saml/cacerts`** - Should be equivalent to `/saml/ca`
  - Referenced in: README.md endpoint table, SAML_GUIDE.md
  - Working alternative: `/saml/ca` ✅
  - **Action:** Update all docs to use `/saml/ca` instead

- [ ] **`/kerberos/oauth`** - OAuth token via Kerberos auth
  - Referenced in: README.md endpoint table line 475
  - **Action:** Remove from docs or implement endpoint

- [ ] **`/kerberos/saml`** - SAML assertion via Kerberos auth
  - Referenced in: README.md endpoint table line 476
  - **Action:** Remove from docs or implement endpoint

- [ ] **`/b64encode`** - Base64 encoding utility
  - Referenced in: README.md endpoint table line 479
  - **Action:** Remove from docs or implement endpoint

- [ ] **`/b64decode`** - Base64 decoding utility
  - Referenced in: README.md endpoint table line 480
  - **Action:** Remove from docs or implement endpoint

### LDAP Proxy Issues (2 issues - CRITICAL) 🔴

- [ ] **LDAP Proxy Listener (port 10389) broken** - Returns "Inappropriate matching" error
  - Command: `ldapsearch -H ldap://localhost:10389 -D "cn=admin" -w password -b "dc=MarkLogic,dc=Local" "(objectClass=*)" -LLL`
  - Expected: User authentication and data return
  - Actual: `result: 18 Inappropriate matching / text: Invalid request parameters`
  - Referenced in: QUICKSTART_VERIFICATION.md line 74
  - **Action:** Fix code or update docs with correct search filter syntax

- [ ] **LDAP JSON Listener (port 20389) broken** - Same "Inappropriate matching" error
  - Command: `ldapsearch -H ldap://localhost:20389 -D "cn=admin" -w password -b "dc=test" "(objectClass=*)" -LLL`
  - Actual: Same error as proxy listener
  - **Action:** Investigate JsonRequestProcessor implementation

### Kerberos Configuration Issue (1 issue) 🔴

- [ ] **Kerberos KDC fails to start on default port 60088**
  - Error: `Failed to start KDC-Server. Permission denied`
  - Cause: Port 60088 is a low port requiring elevated privileges on macOS/Linux
  - Impact: Cannot test `kinit`, `klist`, or SPNEGO authentication
  - **Note:** Port 8088 is documented as default in guides (good!), but config file may still use 60088
  - **Action:** Update default config to use 8088, add troubleshooting docs for permission errors

---

## Category 3: Requires External Services (MarkLogic)

*Not tested in this audit - MarkLogic Server required*

No issues documented - MarkLogic integration examples were not tested as they require external MarkLogic Server.

**Future:** Test Category 3 examples when MarkLogic Server is available.

---

## Category 4: Structural Issues

*Navigation, consistency, organization problems*

### Global Structural Issues (4 issues)

- [ ] **Missing breadcrumb navigation in ALL guides** (except README)
  - No "🏠 Home > 📚 User Docs > Guide Name" navigation
  - Affects: All protocol guides, QUICKSTART, CONFIGURATION_GUIDE

- [ ] **Inconsistent "Related Documentation" sections**
  - Present in: LDAP_GUIDE, OAUTH_GUIDE, SAML_GUIDE, KERBEROS_GUIDE
  - Missing in: README, QUICKSTART_VERIFICATION, CONFIGURATION_GUIDE
  - Different link formats across guides (some `./`, some relative, some full `../../`)

- [ ] **No "Quick Links" or "Jump to Section" shortcuts** for long documents
  - Especially needed in CONFIGURATION_GUIDE (880+ lines)

- [ ] **Inconsistent code block syntax highlighting**
  - Some code blocks missing language identifiers (bash, properties, json, ldif, yaml)
  - Affects readability and GitHub rendering

### File-Specific Structural Issues (19 issues)

#### README.md (2 issues)

- [ ] Inconsistent section separators (some sections have `---`, others don't)
- [ ] Endpoint Reference Table shows non-existent endpoints (see Category 2)

#### docs/user/QUICKSTART_VERIFICATION.md (3 issues)

- [ ] Missing table of contents (moderate length document)
- [ ] No breadcrumb navigation
- [ ] No "Related Documentation" section at end

#### docs/user/LDAP_GUIDE.md (3 issues)

- [ ] No breadcrumb navigation
- [ ] Inconsistent link formats in Related Documentation section
- [ ] Examples not clearly ordered (basic → intermediate → MarkLogic)

#### docs/user/OAUTH_GUIDE.md (3 issues)

- [ ] No breadcrumb navigation
- [ ] Related Documentation links inconsistent format
- [ ] Some code blocks missing syntax highlighting

#### docs/user/SAML_GUIDE.md (3 issues)

- [ ] No breadcrumb navigation
- [ ] Related Documentation links inconsistent format
- [ ] Multiple code blocks missing language identifiers

#### docs/user/KERBEROS_GUIDE.md (3 issues)

- [ ] No breadcrumb navigation
- [ ] Related Documentation links inconsistent format
- [ ] Code blocks missing syntax highlighting

#### docs/user/CONFIGURATION_GUIDE.md (2 issues) 🔴

- [ ] **No table of contents** (CRITICAL for 880+ line document)
- [ ] No breadcrumb navigation
- [ ] No "Related Documentation" section
- [ ] Property loading priority list uses inconsistent formatting

---

## Testing Notes

### Test Environment

- **MLEAProxy Version:** 2.0.3
- **Build Date:** 2026-07-25
- **Configuration:** Default (users.json from project root)
- **Java Version:** OpenJDK 21
- **Platform:** macOS (SAST timezone)

### Testing Methodology

**Category 1 & 4:** Complete read-through of all 8 documentation files
- Checked all links (internal and cross-references)
- Verified file references exist
- Noted formatting inconsistencies
- Identified structural gaps

**Category 2:** Started MLEAProxy and tested 20 documented commands
- LDAP: In-memory server ✅, Proxy listeners ❌
- OAuth: All endpoints working ✅
- SAML: Core endpoints working ✅, alternate names missing ❌
- Kerberos: Auth endpoints working ✅ (anonymous mode), KDC startup failed ❌
- Utility: Status and b64 endpoints missing ❌

**Category 3:** Not tested (requires MarkLogic Server)

### Pass/Fail Summary

**Working (11 commands):**
- LDAP in-memory server (port 60389) - full functionality
- OAuth token generation (password grant, client_credentials grant)
- OAuth JWKS and OpenID discovery
- SAML IdP metadata and CA certificate retrieval
- SAML auth and wrapassertion endpoints (exist, need params)
- Kerberos auth endpoints (return anonymous tokens when KDC disabled)

**Failing (8 commands + 1 config issue):**
- LDAP proxy listener (port 10389) - "Inappropriate matching" errors
- LDAP JSON listener (port 20389) - same errors
- 7 documented endpoints return 404
- Kerberos KDC fails to start (permission denied on port 60088)

---

## Priority Recommendations

### Critical (Fix Immediately) 🔴

1. **Remove or implement 7 missing endpoints** from endpoint reference table
   - Update README.md and guide references
   - Remove: `/saml/metadata`, `/saml/cacerts`, `/kerberos/oauth`, `/kerberos/saml`, `/b64encode`, `/b64decode`
   - Verify: `/status` (should exist based on StatusHandler.java commit)

2. **Fix or document LDAP proxy listener issues** (ports 10389, 20389)
   - Either fix code to handle `(objectClass=*)` searches
   - Or update docs with correct/restricted search filter syntax

3. **Add table of contents to CONFIGURATION_GUIDE.md** (880 lines, unusable without TOC)

4. **Fix 3 broken TESTING_GUIDE.md references** (file doesn't exist)

### High Priority (Phase 3)

5. **Add breadcrumb navigation to all guides** (Phase 3 structural improvements)

6. **Standardize "Related Documentation" sections** across all files

7. **Add syntax highlighting to all code blocks** (improves readability)

8. **Fix Kerberos KDC default port** (document 8088 as default, add troubleshooting for permission errors)

### Medium Priority

9. **Verify all script/file references exist** (extract-jwks-keys.sh, developer docs, etc.)

10. **Standardize link formats** across all documentation (choose one style: `./`, relative, or full)

11. **Update example ordering** to follow basic → intermediate → MarkLogic pattern

### Low Priority

12. **Fix future date** in docs/user/README.md (May 18, 2026 → 2025)

13. **Clean up commented-out sections** (README.md "What's New")

14. **Add platform-specific notes** for commands like `fold` that differ across macOS/Linux

---

## Next Steps

**Immediate Actions:**
1. Rebuild JAR with latest code (StatusHandler.java) to verify `/status` endpoint
2. Remove 6 confirmed missing endpoints from README.md endpoint table
3. Fix 3 TESTING_GUIDE.md broken link references
4. Add TOC to CONFIGURATION_GUIDE.md

**Phase 3 (Structural Improvements):**
1. Add breadcrumb navigation to all guides
2. Standardize "Related Documentation" sections
3. Add syntax highlighting to all code blocks
4. Reorder examples (basic → intermediate → MarkLogic)

**Investigation Required:**
1. LDAP proxy listeners - why do they fail with "Inappropriate matching"?
2. Kerberos KDC - verify port 8088 is truly the default in config files
3. Missing endpoints - were they planned but not implemented, or documentation errors?

---

**End of Audit Report**
