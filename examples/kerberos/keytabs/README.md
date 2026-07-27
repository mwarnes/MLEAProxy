# Kerberos Keytabs

This directory is for Kerberos keytab files used by MLEAProxy.

## Quick Setup

Run the keytab generation wizard from the project root:

```bash
../../scripts/create-keytab.sh
```

## Manual Generation

### Using ktutil

```bash
ktutil
addent -password -p HTTP/hostname@MARKLOGIC.LOCAL -k 1 -e aes256-cts-hmac-sha1-96
# Enter password when prompted
wkt HTTP-hostname-MARKLOGIC-LOCAL.keytab
quit

# Verify
klist -k -t HTTP-hostname-MARKLOGIC-LOCAL.keytab
```

### Using kadmin

```bash
kadmin.local
addprinc -randkey HTTP/hostname@MARKLOGIC.LOCAL
ktadd -k HTTP-hostname-MARKLOGIC-LOCAL.keytab HTTP/hostname@MARKLOGIC.LOCAL
quit
```

## File Naming

Keytabs are named to match their principal:

- Principal: `HTTP/rocky@MARKLOGIC.LOCAL`
- Keytab: `HTTP-rocky-MARKLOGIC-LOCAL.keytab`

## Configuration

Update `kerberos.properties`:

```properties
mleaproxy.kerberos.principal=HTTP/hostname@MARKLOGIC.LOCAL
mleaproxy.kerberos.keytab=kerberos/keytabs/HTTP-hostname-MARKLOGIC-LOCAL.keytab
```

## Security

- Permissions: `chmod 600 *.keytab`
- Never commit to git (in .gitignore)
- Environment-specific (dev/test/prod each need their own)
