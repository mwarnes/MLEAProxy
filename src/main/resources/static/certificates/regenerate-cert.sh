#!/bin/bash
set -euo pipefail

# Certificate details
SUBJECT="/O=Progress MarkLogic External Security Proxy/OU=SAML Identity Provider/CN=SAML Signing Certificate"
DAYS=3650  # 10 years

echo "Generating new SAML signing certificate..."
echo "Subject: $SUBJECT"
echo ""

# Generate new RSA private key (2048-bit)
openssl genrsa -out privkey.pem 2048

# Generate self-signed certificate
openssl req -new -x509 \
    -key privkey.pem \
    -out certificate.pem \
    -days $DAYS \
    -subj "$SUBJECT"

# Verify the certificate
echo ""
echo "Certificate generated successfully!"
echo ""
echo "Certificate details:"
openssl x509 -in certificate.pem -noout -subject -issuer -dates

echo ""
echo "Files created:"
echo "  - privkey.pem (private key)"
echo "  - certificate.pem (certificate)"
