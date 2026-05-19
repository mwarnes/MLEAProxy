# JWKS Simple Script - PEM Format Update

## Summary

The `scripts/extract-jwks-keys.sh` script has been updated to convert RSA keys from JWKS format to proper PEM format, making them ready for use in MarkLogic External Security configurations or other applications that require PEM-formatted RSA public keys.

## What the Script Now Does

1. **Extracts Key ID**: First line shows the key identifier (kid)
2. **Converts to PEM**: Converts RSA modulus (n) and exponent (e) to proper PEM format
3. **Outputs Standard Format**: Produces standard RSA PUBLIC KEY in PEM format

## Example Output

```
DCYjkxqf7xLskUc_9tlaJ8-2QC4Vx-G1nPC1qPQro1Q
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuekhsx8JZydBcsINa2Gt
TiA6f2qNVlJoCXktjIIuOKpD4sTa8J8DsMQTBxS7kCk1yuqjMnkiVH5qiBD58rFs
wYIVjIkRQ5s9zP/1Icq14oB3L7fn+hkiwePKn1zZP3wM25bVGiu/WwUTClrEWhMH
1zorjbgUSzIBYNTVHwb0y3SGibtrYQYR/1XQ1ZPgJ7OWTDFve/mksa5gQQACkHpt
6oGsqvQpdzjvSYkR0wQn25vqEHvR1Q9zjy62tC2Jj2wKjjioMBzBj4n2dV52wkJs
TcaZ+SF+QavAW9x/2qwr0gfkXrlTy2b6O7QyuipiLaRQVJdT59RVw29/TXA3sU3R
xwIDAQAB
-----END PUBLIC KEY-----

9xJihMI2qDH9Aon014odONQLByhF553_8Wu_zcM2StM
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA8I5S+2YnzUeYsV0K0b8A
/XQ4kBWuhZtojzp6ccIb6/pPXNoUaWwr6NK+NyOZTRhOXA2m99QB3zvIH89JyZac
ywH0SFGdR+opHGQ9PXLURN/kzEGJMjHDwu6zKaMj69gaD86lWzKBPvOLFG7A3623
6edTyoedTLyCw431hgsjsNSefoC7KU/cKAUFCzD03UuNaJk/CbTgqhFJouPtOH0I
GnWp6N67oaJvEEkV7lGTk6cKQ08WEYxSIv/8MsEUkUSCWxRcOfHNXnPvCUs/V0Ko
dMmoSnV/jud88iIU9B2pAJDVGfnElOnMyR16wLRGTYOHctgBxvcKZe3XgvQ5bGZl
IQIDAQAB
-----END PUBLIC KEY-----
```

## Technical Details

### Conversion Process
1. **Base64URL Decoding**: Converts JWKS base64url-encoded modulus and exponent to binary
2. **DER Encoding**: Creates proper ASN.1 DER structure for RSA public key
3. **SubjectPublicKeyInfo**: Wraps the RSA key in standard X.509 SubjectPublicKeyInfo structure
4. **PEM Conversion**: Uses OpenSSL to convert DER to PEM format

### Requirements
- `curl` - for fetching JWKS data
- `jq` - for JSON parsing
- `python3` - for ASN.1 DER encoding
- `openssl` - for DER to PEM conversion

### Fallback Behavior
If `python3` or `openssl` are not available, the script falls back to outputting the raw base64url modulus value.

## Usage

```bash
./scripts/extract-jwks-keys.sh https://oauth.warnesnet.com:8443/realms/progress-marklogic/protocol/openid-connect/certs
```

## Verification

The generated PEM keys can be verified using OpenSSL:

```bash
# Test first key from output
./scripts/extract-jwks-keys.sh <JWKS_URL> | grep -A 10 "BEGIN PUBLIC KEY" | head -9 | openssl rsa -pubin -text -noout
```

This will show the key details including:
- Key size (e.g., 2048 bit)
- Modulus in hexadecimal format
- Exponent (typically 65537)

## Use Cases

- **MarkLogic External Security**: Copy the PEM key directly into MarkLogic External Security configuration
- **JWT Verification**: Use the PEM key for manual JWT signature verification
- **Certificate Tools**: Import into certificate management tools
- **API Testing**: Use with tools like Postman or curl for JWT verification