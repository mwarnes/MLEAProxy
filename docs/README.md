# MLEAProxy Documentation

Welcome to the MLEAProxy documentation. This directory contains all project documentation organized by audience and purpose.

## 📁 Documentation Structure

### 📘 [User Documentation](./user/)

User-facing documentation including configuration guides, protocol guides, and quick references.

**Key Documents:**
- [Configuration Guide](./user/CONFIGURATION_GUIDE.md)
- [LDAP Guide](./user/LDAP_GUIDE.md)
- [OAuth Guide](./user/OAUTH_GUIDE.md)
- [SAML Guide](./user/SAML_GUIDE.md)
- [Kerberos Guide](./user/KERBEROS_GUIDE.md)
- [Testing Guide](./user/TESTING_GUIDE.md)

👉 **[Browse User Documentation →](./user/)**

### 🔧 [Developer Documentation](./developer/)

Technical implementation details, build notes, migration summaries, and development history.

**Key Documents:**
- [Kerberos Implementation Phases](./developer/KERBEROS_PHASE1_COMPLETE.md)
- [Code Review 2025](./developer/CODE_REVIEW_2025.md)
- [Test Suite Summary](./developer/TEST_SUITE_SUMMARY.md)
- [Architecture Documents](./developer/SHARED_PORT_ARCHITECTURE.md)
- [Build Process](./developer/build.md)

👉 **[Browse Developer Documentation →](./developer/)**

## 🚀 Quick Start

1. **New Users**: Start with the main [README](../README.md) and [User Documentation](./user/)
2. **Developers**: Review [Developer Documentation](./developer/) for implementation details
3. **Configuration**: See the [Configuration Guide](./user/CONFIGURATION_GUIDE.md)
4. **Testing**: Follow the [Testing Guide](./user/TESTING_GUIDE.md)

## 📚 Documentation by Protocol

| Protocol | User Guide | Developer Notes |
|----------|------------|-----------------|
| **LDAP/LDAPS** | [LDAP Guide](./user/LDAP_GUIDE.md) | [In-Memory LDAP Summary](./developer/IN_MEMORY_LDAP_DOCUMENTATION_SUMMARY.md) |
| **OAuth 2.0** | [OAuth Guide](./user/OAUTH_GUIDE.md) | [OAuth Endpoints](./developer/OAUTH_JWKS_WELLKNOWN_COMPLETE.md) |
| **SAML 2.0** | [SAML Guide](./user/SAML_GUIDE.md) | [SAML Metadata](./developer/SAML_IDP_METADATA_COMPLETE.md) |
| **Kerberos** | [Kerberos Guide](./user/KERBEROS_GUIDE.md) | [Phase 1-4 Details](./developer/KERBEROS_PHASE1_COMPLETE.md) |

## 📝 Recent Updates

**October 8, 2025**
- Reorganized documentation into user/developer structure
- Updated all documentation cross-references
- Created comprehensive index files

**October 7, 2025**
- Added Kerberos Phase 4 documentation
- Consolidated LDAP configuration (merged directory.properties)
- Created comprehensive Kerberos Guide

## 🔍 Finding Documentation

- **By Audience**: Navigate to [user/](./user/) or [developer/](./developer/)
- **By Protocol**: Use the protocol table above
- **By Topic**: Check the README.md files in each folder
- **Search**: Use your IDE's search across `docs/` folder

## 🤝 Contributing to Documentation

When adding new documentation:

1. **User Documentation** → Place in `docs/user/`
   - Configuration guides
   - Usage instructions
   - Quick references
   - Integration guides

2. **Developer Documentation** → Place in `docs/developer/`
   - Implementation notes
   - Build summaries
   - Migration plans
   - Code reviews
   - Architecture decisions

3. **Update Index Files**
   - Add entries to relevant README.md files
   - Update cross-references
   - Maintain consistent formatting

---

**Documentation Organization Date**: October 8, 2025  
**Total Documents**: 47 files  
**Structure**: 2 main categories (User + Developer)
