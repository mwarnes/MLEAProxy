# SAML Signing Certificates

This directory contains the self-signed certificate and private key used for signing SAML assertions.

## Files

- **certificate.pem** - X.509 certificate (public key)
- **privkey.pem** - RSA private key (2048-bit)
- **regenerate-cert.sh** - Script to regenerate the certificate

## Certificate Details

```
Subject: O=Progress MarkLogic External Security Proxy, OU=SAML Identity Provider, CN=SAML Signing Certificate
Validity: 10 years
Key Type: RSA 2048-bit
```

## Regenerating the Certificate

If you need to regenerate the certificate (e.g., before expiration or to change the subject):

```bash
cd src/main/resources/static/certificates
./regenerate-cert.sh
```

This will:
1. Generate a new RSA 2048-bit private key
2. Create a new self-signed certificate valid for 10 years
3. Overwrite the existing `certificate.pem` and `privkey.pem` files

**Important:** After regenerating:
- Service Providers will need to update their metadata/trust store
- The certificate fingerprint will change
- Any cached metadata should be cleared

## Viewing Certificate Details

```bash
# View certificate information
openssl x509 -in certificate.pem -noout -text

# View subject and validity
openssl x509 -in certificate.pem -noout -subject -issuer -dates

# View certificate fingerprint (SHA256)
openssl x509 -in certificate.pem -noout -fingerprint -sha256
```

## Usage in MLEAProxy

The certificate is loaded by `SAMLAuthHandler` at startup and used to:
- Sign SAML assertions in authentication responses
- Populate the `<KeyInfo>` section in SAML metadata
- Establish trust with Service Providers

The certificate is configured via:
```properties
saml.certificate.path=classpath:static/certificates/certificate.pem
saml.signing.key.path=classpath:static/certificates/privkey.pem
```
