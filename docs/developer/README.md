# MLEAProxy Developer Documentation

This folder contains technical implementation details, build notes, migration summaries, and development history for MLEAProxy.

## 📚 Documentation Index

### Build & Implementation History

#### Kerberos Implementation
- **[KERBEROS_FEASIBILITY.md](./KERBEROS_FEASIBILITY.md)** - Initial Kerberos feasibility analysis
- **[KERBEROS_PHASE1_COMPLETE.md](./KERBEROS_PHASE1_COMPLETE.md)** - Phase 1: Embedded KDC implementation
- **[KERBEROS_PHASE2_COMPLETE.md](./KERBEROS_PHASE2_COMPLETE.md)** - Phase 2: SPNEGO authentication
- **[KERBEROS_PHASE3_COMPLETE.md](./KERBEROS_PHASE3_COMPLETE.md)** - Phase 3: OAuth/SAML protocol bridges
- **[KERBEROS_PHASE4_IN_PROGRESS.md](./KERBEROS_PHASE4_IN_PROGRESS.md)** - Phase 4: LDAP integration & refresh tokens (60% complete)

#### OAuth & SAML Enhancements
- **[OAUTH_JWKS_WELLKNOWN_COMPLETE.md](./OAUTH_JWKS_WELLKNOWN_COMPLETE.md)** - OAuth JWKS and .well-known endpoints implementation
- **[OAUTH_TOKEN_ENDPOINT.md](./OAUTH_TOKEN_ENDPOINT.md)** - OAuth token endpoint documentation
- **[OAUTH_SAML_DISCOVERY_SUMMARY.md](./OAUTH_SAML_DISCOVERY_SUMMARY.md)** - Discovery endpoints implementation summary
- **[SAML_IDP_METADATA_COMPLETE.md](./SAML_IDP_METADATA_COMPLETE.md)** - SAML IdP metadata endpoint implementation
- **[SAML_IDP_METADATA_SUMMARY.md](./SAML_IDP_METADATA_SUMMARY.md)** - SAML metadata implementation summary
- **[SAML_ROLE_RESOLUTION_FIX.md](./SAML_ROLE_RESOLUTION_FIX.md)** - SAML role resolution bug fix
- **[SAML_PORT_CONFIGURATION_UPDATE.md](./SAML_PORT_CONFIGURATION_UPDATE.md)** - SAML port configuration changes

#### LDAP Enhancements
- **[IN_MEMORY_LDAP_DOCUMENTATION_SUMMARY.md](./IN_MEMORY_LDAP_DOCUMENTATION_SUMMARY.md)** - In-memory LDAP server implementation summary
- **[XML_USER_REPOSITORY.md](./XML_USER_REPOSITORY.md)** - XML-based user repository implementation

#### Migration & Dependency Updates
- **[JJWT_MIGRATION_PLAN.md](./JJWT_MIGRATION_PLAN.md)** - Plan for migrating to JJWT 0.12.x
- **[JJWT_MIGRATION_COMPLETE.md](./JJWT_MIGRATION_COMPLETE.md)** - JJWT migration completion summary

### Code Quality & Fixes

- **[CODE_REVIEW_2025.md](./CODE_REVIEW_2025.md)** - 2025 code review findings and recommendations
- **[CODE_FIXES_SUMMARY_2025.md](./CODE_FIXES_SUMMARY_2025.md)** - Summary of code fixes applied in 2025
- **[TEST_SUITE_SUMMARY.md](./TEST_SUITE_SUMMARY.md)** - Test suite overview and coverage
- **[TEST_FIXES_SUMMARY.md](./TEST_FIXES_SUMMARY.md)** - Test fixes summary
- **[TEST_FIXES_OCT6_2025.md](./TEST_FIXES_OCT6_2025.md)** - Test fixes applied on October 6, 2025
- **[TESTING_SAML_ROLES.md](./TESTING_SAML_ROLES.md)** - SAML role testing documentation

### Architecture & Design

- **[SHARED_PORT_ARCHITECTURE.md](./SHARED_PORT_ARCHITECTURE.md)** - Shared port architecture design and implementation
- **[SYSTEM_PROPERTIES_SUPPORT.md](./SYSTEM_PROPERTIES_SUPPORT.md)** - System properties support implementation

### Documentation Updates

- **[DOCUMENTATION_REORGANIZATION.md](./DOCUMENTATION_REORGANIZATION.md)** - Documentation reorganization notes
- **[DOCUMENTATION_UPDATE_SUMMARY.md](./DOCUMENTATION_UPDATE_SUMMARY.md)** - Documentation update summary (Shared Port Architecture)
- **[DOCUMENTATION_UPDATE_SUMMARY_OCT7.md](./DOCUMENTATION_UPDATE_SUMMARY_OCT7.md)** - Documentation update summary (Kerberos Phase 4 & Config Consolidation)

### Build Process

- **[build.md](./build.md)** - Build process documentation and notes
- **[MAVEN_WARNING_SUPPRESSION.md](./MAVEN_WARNING_SUPPRESSION.md)** - Maven warning suppression configuration

### Optional Improvements

- **[OPTIONAL_IMPROVEMENTS_IMPLEMENTATION.md](./OPTIONAL_IMPROVEMENTS_IMPLEMENTATION.md)** - Optional feature improvements and enhancements

### Build Phase Completion Markers

- **[BP1_COMPLETE.md](./BP1_COMPLETE.md)** - Build Phase 1 completion
- **[BP7_COMPLETE.md](./BP7_COMPLETE.md)** - Build Phase 7 completion
- **[CQ3_COMPLETE.md](./CQ3_COMPLETE.md)** - Code Quality Phase 3 completion
- **[DOC1_COMPLETE.md](./DOC1_COMPLETE.md)** - Documentation Phase 1 completion

### Historical Archives

- **[README_OLD_BACKUP.md](./README_OLD_BACKUP.md)** - Backup of previous README version

---

## 🔗 Related Documentation

- **Main README**: [../../README.md](../../README.md) - Project overview and quick start
- **User Docs**: [../user/](../user/) - User-facing configuration and usage guides

## 📖 Understanding the Documentation

### File Naming Conventions

- **\*_COMPLETE.md**: Implementation completion summaries for major features
- **\*_SUMMARY.md**: High-level summaries of implementations or changes
- **\*_FIX.md**: Bug fix documentation
- **\*_PLAN.md**: Planning documents for future work
- **\*_IN_PROGRESS.md**: Work in progress documentation

### Kerberos Phases

1. **Phase 1**: Embedded KDC using Apache Kerby
2. **Phase 2**: SPNEGO HTTP authentication
3. **Phase 3**: OAuth and SAML protocol bridges
4. **Phase 4**: LDAP role integration and OAuth refresh tokens (in progress)

---

**Last Updated**: October 8, 2025
