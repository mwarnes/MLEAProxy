# SSL Certificates for rocky.warnesnet.com

This directory contains example SSL certificates for configuring HTTPS on MLEAProxy.

## Files

- `ca.crt` - WarnesNet Development CA certificate
- `rocky.cnf` - OpenSSL configuration for generating server certificates

## Creating Server Certificates

```bash
# Generate CA (if not using existing)
openssl genrsa -out ca.key 4096
openssl req -new -x509 -days 3650 -key ca.key -out ca.crt \
    -subj "/CN=WarnesNet Development CA"

# Generate server key and certificate
openssl genrsa -out server.key 2048
openssl req -new -key server.key -out server.csr -config rocky.cnf
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out server.crt -days 365 -sha256 -extfile rocky.cnf -extensions req_ext

# Create PKCS12 keystore for Spring Boot
openssl pkcs12 -export -in server.crt -inkey server.key -out server.p12 \
    -name server -password pass:changeit
```

## Spring Boot Configuration

```properties
# application.properties or mleaproxy.properties
server.ssl.enabled=true
server.ssl.key-store=/path/to/server.p12
server.ssl.key-store-password=changeit
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=server

# Important: Set external URL for OAuth endpoints
oauth.server.base.url=https://your-hostname:8888
```
