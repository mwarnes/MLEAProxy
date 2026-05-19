# MLEAProxy User Documentation

This folder contains all user-facing documentation for MLEAProxy, including configuration guides, protocol documentation, and usage instructions.

## 📚 Documentation Index

### ⭐ Quick Start

- **[QUICKSTART_VERIFICATION.md](./QUICKSTART_VERIFICATION.md)** - **Start here!** Working examples for all protocols with verification commands

### Configuration & Setup

- **[CONFIGURATION_GUIDE.md](./CONFIGURATION_GUIDE.md)** - Complete configuration guide with new Spring-style property format
- Property prefix: `mleaproxy.*` (e.g., `mleaproxy.ldap-listeners.proxy.port=10389`)

### Protocol Guides

#### LDAP/LDAPS
- **[LDAP_GUIDE.md](./LDAP_GUIDE.md)** - Complete LDAP/LDAPS proxy and server guide (includes in-memory LDAP server documentation)

#### OAuth 2.0
- **[OAUTH_GUIDE.md](./OAUTH_GUIDE.md)** - OAuth 2.0 JWT token generation and endpoints

#### SAML 2.0
- **[SAML_GUIDE.md](./SAML_GUIDE.md)** - SAML 2.0 Identity Provider implementation guide
- **[MarkLogic-SAML-configuration.md](./MarkLogic-SAML-configuration.md)** - Configure MarkLogic Server with SAML IdP

#### Kerberos
- **[KERBEROS_GUIDE.md](./KERBEROS_GUIDE.md)** - Complete Kerberos implementation guide (all phases with detailed sections for Phase 2, 3, and 4)

### Quick References

- **[DISCOVERY_ENDPOINTS_QUICK_REF.md](./DISCOVERY_ENDPOINTS_QUICK_REF.md)** - Quick reference for OAuth/SAML discovery endpoints

---

## 🔗 Related Documentation

- **Main README**: [../../README.md](../../README.md) - Project overview and quick start
- **Developer Docs**: [../developer/](../developer/) - Technical implementation details and build notes

## 📖 Getting Started

1. **Quick Start**: Follow the [Quick Start & Verification Guide](./QUICKSTART_VERIFICATION.md)
2. **Configuration**: Review the [Configuration Guide](./CONFIGURATION_GUIDE.md) for all options
3. **Examples**: Check [examples/](../../examples/) for working configurations
4. **Protocol Deep-Dive**: Choose your protocol guide:
   - [LDAP_GUIDE](./LDAP_GUIDE.md) for LDAP/LDAPS
   - [OAUTH_GUIDE](./OAUTH_GUIDE.md) for OAuth 2.0
   - [SAML_GUIDE](./SAML_GUIDE.md) for SAML 2.0
   - [KERBEROS_GUIDE](./KERBEROS_GUIDE.md) for Kerberos

---

**Last Updated**: May 18, 2026
