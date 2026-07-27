# MLEAProxy Distribution Package Design

**Date:** 2026-07-27
**Version:** 2.0.3
**Target Audience:** MarkLogic engineers (internal company portal)
**Platforms:** macOS, Linux

## Overview

Create a distributable zip package containing the MLEAProxy JAR, user documentation, example configurations, and runtime scripts. The package must be self-contained and portable - users can unzip on a clean macOS or Linux machine and run the server without access to source code or build tools.

## Goals

1. **Self-Contained Distribution**: Everything needed to run MLEAProxy in production
2. **Portable**: No hardcoded paths, machine-specific hostnames, or local references
3. **User-Friendly**: Interactive launcher and keytab generation helper for first-time users
4. **Complete Documentation**: All user guides, examples, and quick-start instructions
5. **MarkLogic Focus**: Include MarkLogic integration tests and examples (primary user base is MarkLogic engineers)

## Distribution Structure

The zip file `mleaproxy-2.0.3-distribution.zip` extracts to:

```text
mleaproxy-2.0.3/
├── mlesproxy-2.0.3.jar          # Application JAR
├── README.txt                    # Quick start guide (NEW)
├── users.json                    # Example user database
├── application.properties        # Base configuration
├── ldap.properties              # LDAP config example
├── oauth.properties             # OAuth config example
├── saml.properties              # SAML config example
├── kerberos.properties          # Kerberos config example
├── jwt-secrets.json             # JWT signing keys
├── JWTSecretsPayload.json       # JWT secrets payload example
├── oauth2-base-config.json      # OAuth minimal config
├── oauth2-complete-config.json  # OAuth full config
├── oauth2-test-*.json           # OAuth test configs
├── marklogic-external-security-*.json  # MarkLogic external security examples
├── docs/                        # User documentation only
│   ├── README.md
│   ├── QUICKSTART_VERIFICATION.md
│   ├── CONFIGURATION_GUIDE.md
│   ├── LDAP_GUIDE.md
│   ├── OAUTH_GUIDE.md
│   ├── SAML_GUIDE.md
│   ├── KERBEROS_GUIDE.md
│   ├── JWKS-MarkLogic-Integration-Usage-Guide.md
│   └── JWKS-MarkLogic-Integration-Usage-Guide.pdf
├── examples/                    # Protocol examples and tests
│   ├── certs/                  # Certificate examples
│   ├── kerberos/               # Kerberos config examples
│   │   ├── keytabs/           # Empty, with README on how to generate
│   │   └── krb5.conf
│   ├── ldap/                   # LDAP examples
│   ├── marklogic/              # MarkLogic integration tests
│   │   ├── README.md
│   │   ├── marklogic-utils.sh
│   │   ├── test-ldap-integration.sh
│   │   ├── test-oauth-integration.sh
│   │   ├── test-saml-integration.sh
│   │   ├── test-kerberos-integration.sh
│   │   └── configs/
│   ├── oauth/                  # OAuth examples
│   └── saml/                   # SAML examples
├── scripts/                     # Runtime control scripts
│   ├── start.sh                # NEW: Interactive launcher
│   ├── start-all.sh            # Start all protocols
│   ├── start-ldap.sh           # Start LDAP only
│   ├── start-oauth.sh          # Start OAuth only
│   ├── start-saml.sh           # Start SAML only
│   ├── start-kerberos.sh       # Start Kerberos only
│   ├── stop.sh                 # Stop server
│   ├── status.sh               # Check server status
│   └── create-keytab.sh        # NEW: Keytab generation helper
└── kerberos/                    # Runtime Kerberos directory
    ├── keytabs/                # Empty, for generated keytabs
    └── krb5.conf               # Copy from examples/kerberos/
```

### Exclusions

**Not Included:**

- Source code (`src/`, `pom.xml`)
- Build artifacts (`target/` except JAR, `.git/`)
- Developer documentation (`docs/developer/`, `docs/superpowers/`)
- Build scripts (`build.sh`, `run-tests.sh`, `dev-aliases.sh`)
- Development tools (`scripts/check-version.sh`, `scripts/create-release.sh`)
- Test-only files (`http_client/`)

## New Components

### 1. README.txt - Quick Start Guide

Plain text file (terminal-friendly) with quick start instructions, prerequisites, usage examples, and documentation pointers.

### 2. scripts/start.sh - Interactive Launcher

Menu-driven interface for starting protocols. Validates Java 21+, required files, port availability. Launches via existing protocol scripts.

### 3. scripts/create-keytab.sh - Keytab Generation Helper

Interactive wizard for generating Kerberos keytabs using ktutil. Prompts for realm, principal, password. Updates kerberos.properties automatically.

### 4. examples/kerberos/keytabs/README.md

Instructions for manual keytab generation (ktutil and kadmin commands).

## Distribution Creation Process

### create-distribution.sh Script

**Location:** Project root

**Process:**

1. **Validate Prerequisites** - Check JAR exists (target/ or release/)
2. **Portability Validation** - Scan for hardcoded paths and hostnames
3. **Create Distribution Directory** - dist-temp/mleaproxy-2.0.3/
4. **Copy Files** - JAR, configs, docs, examples, scripts
5. **Generate New Files** - README.txt, start.sh, create-keytab.sh, keytab README
6. **Set Permissions** - chmod +x all .sh files
7. **Create Zip Archive** - mleaproxy-2.0.3-distribution.zip
8. **Calculate Size** - Report size (~60-65 MB expected)
9. **Cleanup** - Remove dist-temp/
10. **Report Success** - Show usage instructions

**Portability Validation:**

Forbidden patterns:

- Absolute paths: `/Users/martin/`, `/Users/*/`, `/home/*/`, `/opt/`
- Machine-specific hostnames: `Martins-Air.localdomain`

Allowed references:

- `localhost`, `127.0.0.1`
- Relative paths: `./`, `../`
- Environment variables: `${HOME}`, `${JAVA_HOME}`

If violations found, exit with error showing offending files/lines.

## Testing & Validation

### Automated Tests (in script)

1. JAR exists and size check
2. Portability validation
3. Post-creation: zip exists, size range check

### Manual Testing Steps

1. Extract to clean directory
2. Verify structure
3. Test interactive launcher
4. Test keytab helper
5. Test protocol start script
6. Verify permissions
7. Check for portability issues

## Success Criteria

**Distribution Package:**

- ✓ Zip file: mleaproxy-2.0.3-distribution.zip (60-70 MB)
- ✓ Self-contained, no source code
- ✓ All scripts executable

**Portability:**

- ✓ No hardcoded paths
- ✓ Works on clean macOS/Linux with Java 21+

**User Experience:**

- ✓ README.txt clear quick-start
- ✓ Interactive launcher functional
- ✓ Keytab helper guides setup
- ✓ MarkLogic integration tests included

**Quality:**

- ✓ No validation errors
- ✓ Manual testing passes
- ✓ Ready for company portal
