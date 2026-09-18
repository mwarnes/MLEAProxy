# MarkLogic OAuth 2.0 Authorization Code Flow Guide

Configuring MarkLogic Server to authenticate Admin UI and Query Console users against
MLEAProxy using the OAuth 2.0 Authorization Code flow.

> ## ⚠️ Requires MarkLogic 12.1 or later
>
> The Authorization Code flow is **only available from MarkLogic 12.1**. Before 12.1,
> MarkLogic supported OAuth 2.0 only as a **Resource Server**: a client obtained a JWT
> elsewhere and presented it as a `Bearer` token on API requests, and MarkLogic verified
> it using preloaded keys or JWKS.
>
> From 12.1, MarkLogic can additionally act as an OAuth **client**: a browser session with
> no JWT is redirected to an Authorization Server to sign in, which is what allows Admin UI
> and Query Console logins.
>
> If you are on 12.0 or earlier, use the Resource Server flow described in the
> [OAuth Guide](./OAUTH_GUIDE.md) instead. Nothing in this document applies.

---

## Table of Contents

- [How the flow works](#how-the-flow-works)
- [What MarkLogic 12.1 actually sends](#what-marklogic-121-actually-sends)
- [Prerequisites](#prerequisites)
- [Step 1: Start MLEAProxy with HTTPS](#step-1-start-mleaproxy-with-https)
- [Step 2: Import the CA certificate into MarkLogic](#step-2-import-the-ca-certificate-into-marklogic)
- [Step 3: Enable TLS on the target App Server](#step-3-enable-tls-on-the-target-app-server)
- [Step 4: Create the External Security configuration](#step-4-create-the-external-security-configuration)
- [Step 5: Attach it to the App Server](#step-5-attach-it-to-the-app-server)
- [Step 6: Sign in](#step-6-sign-in)
- [Troubleshooting](#troubleshooting)
- [Known MarkLogic 12.1 behaviours](#known-marklogic-121-behaviours)
- [Verifying without a browser](#verifying-without-a-browser)

---

## How the flow works

```
   Browser                MarkLogic App Server            MLEAProxy
      |                           |                            |
      |-- GET /qconsole --------->|                            |
      |                           | no JWT: redirect           |
      |<-- 302 to authorize ------|                            |
      |                                                        |
      |-- GET /oauth/authorize ------------------------------->|
      |    response_type=code, PKCE S256, response_mode=form_post
      |<-- login page -----------------------------------------|
      |                                                        |
      |-- POST username + roles ----------------------------->|
      |<-- self-submitting form with code ---------------------|
      |                           |                            |
      |-- POST code ------------->|                            |
      |                           |-- POST /oauth/token ------>|
      |                           |    code + code_verifier    |
      |                           |<-- access_token (RS256) ---|
      |                           |-- GET /oauth/jwks -------->|
      |                           |<-- public key -------------|
      |                           | validate, create temp user |
      |<-- authenticated ---------|                            |
```

The MLEAProxy login page does **not** verify credentials. As with the SAML handler, you
declare the username and roles you want represented, which is what makes it useful for
exercising MarkLogic's authorization behaviour without maintaining real accounts.

---

## What MarkLogic 12.1 actually sends

Captured from a real Query Console login. Useful when comparing against another
Authorization Server.

```
GET /oauth/authorize
  ?grant_type=authorization_code          <- note: not where RFC 6749 puts it
  &state=ZC9sz_l7k2l5QIs0aVDLcv9OLWP2G-GQlqHBDjeB_Z4
  &response_type=code
  &response_mode=form_post                <- response must be POSTed, not 302'd
  &client_id=marklogic-qconsole
  &redirect_uri=https://rocky.example.com:8000
  &scope=                                 <- empty
  &code_challenge=naJKGQCIoBWFujpYTltzscUOEqi383NqUil2D5RfJA8
  &code_challenge_method=S256             <- PKCE is mandatory
```

| Behaviour | Consequence |
|---|---|
| `response_mode=form_post` | The authorization response must be delivered as an HTML form POST to the redirect URI, not appended to a 302 `Location`. MLEAProxy uses a self-submitting form. |
| PKCE `S256`, always sent | The `code_verifier` is verified at the token endpoint. There is no opting out. |
| `grant_type` on the authorize request | Not per RFC 6749. MLEAProxy accepts and ignores it. |
| `scope` empty | Roles come from the MLEAProxy login page or `users.json`, not from a scope. |
| No `redirect_uri` if unconfigured | If `oauth-redirect-uri` is empty in MarkLogic, no `redirect_uri` parameter is sent at all and the code cannot be returned. |
| No `nonce` | MarkLogic wants an OAuth access token, not an OIDC `id_token`. |

---

## Prerequisites

- MarkLogic **12.1** or later
- MLEAProxy 2.0.4 or later, reachable from the MarkLogic host
- The ability to import a CA certificate into MarkLogic
- A hostname that resolves: both MarkLogic and the browser must reach MLEAProxy by a name
  covered by its TLS certificate

---

## Step 1: Start MLEAProxy with HTTPS

The Authorization Code flow is browser-facing, and MarkLogic requires HTTPS for the token
and JWKS endpoints. The HTTPS listener is enabled by default on port 8443, in addition to
the plain HTTP listener, so existing Resource Server setups keep working.

```properties
# mleaproxy.properties
mleaproxy.https.enabled=true
mleaproxy.https.port=8443
mleaproxy.https.subject-alt-names=marklogic.example.com
```

Set `subject-alt-names` to every name by which MarkLogic or a browser might reach
MLEAProxy. The detected hostname, `localhost`, `127.0.0.1` and `::1` are always included.

On first start MLEAProxy generates a private CA and a server certificate signed by it:

```
HTTPS Listener / TLS:
Base URL:                 https://mleaproxy.example.com:8443
CA Certificate:           ./certificates/ca-certificate.pem
CA Download URL:          https://mleaproxy.example.com:8443/tls/ca
CA Subject:               CN=MLEAProxy Development CA
CA SHA-256:               B8:C1:B7:E7:...
```

> The SAML signing certificate bundled at `static/certificates/certificate.pem` cannot be
> reused for TLS. It has no `subjectAltName` and no `serverAuth` extended key usage, so
> browsers and MarkLogic reject it for server authentication.

---

## Step 2: Import the CA certificate into MarkLogic

MarkLogic makes **server-to-server** HTTPS calls to MLEAProxy for the token exchange and
JWKS retrieval. It will refuse them until it trusts the CA.

Download it over **plain HTTP** to avoid the chicken-and-egg problem of fetching the CA
over a connection secured by that same CA:

```bash
curl -O http://mleaproxy.example.com:9080/tls/ca
```

Or copy the PEM from the status page: `http://mleaproxy.example.com:9080/status`

Import in the Admin UI under **Security → Certificate Authorities → Import**.

Import the same CA into your **browser** as well (Firefox: Settings → Privacy & Security →
Certificates → View Certificates → Authorities → Import, ticking *"Trust this CA to
identify websites"*), otherwise the login page raises a certificate warning.

---

## Step 3: Enable TLS on the target App Server

MarkLogic requires the redirect URI to use TLS or be a loopback address:

```
XDMP-OAUTHVALIDATION: OAuth client (MarkLogic) must have a URI with TLS (https)
or a loopback URI as opposed to: 'http://marklogic.example.com:8000'
```

Assign a certificate template to the App Server you are protecting (App Services on 8000
serves Query Console; Admin on 8001 serves the Admin UI).

**The certificate must match the hostname you browse to.** MarkLogic's own generated
certificate uses the *cluster host name*, which is often not the name users type. Check:

```bash
# MarkLogic's idea of the host name
curl -s -u admin:password --anyauth \
  "http://localhost:8002/manage/v2/hosts?format=json" | grep nameref
# the OS host name
hostname -f
```

If they differ, generate a certificate whose CN and SANs cover the name you actually use,
and import it into the template. Signing it with the MLEAProxy CA means the single CA
import from Step 2 covers both MLEAProxy and the App Server.

> Also check **`ssl-require-client-certificate`** on the App Server. It is inert while TLS
> is off, but once a template is assigned it starts demanding a *client* certificate from
> the browser, which fails in a way that looks unrelated. Set it to `false` unless you
> want mutual TLS.

---

## Step 4: Create the External Security configuration

**Security → External Security → Create**. Set `authentication` and `authorization` to
`oauth`, then:

| Field | Value | Notes |
|---|---|---|
| `oauth-flow-type` | `Authorization code` | 12.1 and later only |
| `oauth-vendor` | `Other` | |
| `oauth-client-id` | `marklogic-qconsole` | Any value; appears as `client_id` and `aud` |
| **Client secret** | any value | **Must be set.** MLEAProxy does not verify it, but MarkLogic refuses to run the flow without one |
| `oauth-authorization-server-uri` | `https://mleaproxy.example.com:8443/oauth/authorize` | |
| `oauth-token-server-uri` | `https://mleaproxy.example.com:8443/oauth/token` | **HTTPS, and the HTTPS port** |
| `oauth-jwks-uri` | `https://mleaproxy.example.com:8443/oauth/jwks` | **HTTPS, and the HTTPS port** |
| `oauth-redirect-uri` | `https://marklogic.example.com:8000` | Single scheme; see Known behaviours |
| `oauth-jwt-issuer-uri` | `mleaproxy-oauth-server` | Must equal MLEAProxy's `iss`; see Known behaviours |
| `oauth-jwt-alg` | `RS256` | MLEAProxy signs RS256 and publishes an RSA key |
| `oauth-username-attribute` | `username` | |
| `oauth-role-attribute` | `roles` | Array claim. `roles_string` (underscore) is the flat form |
| `oauth-token-type` | `JSON Web Tokens` | |

The default `iss` is `mleaproxy-oauth-server`. To use something else, set
`oauth.jwt.issuer` in MLEAProxy and change `oauth-jwt-issuer-uri` to match. Check
`/.well-known/openid-configuration` for the value in use.

---

## Step 5: Attach it to the App Server

**Configure → Groups → Default → App Servers →** *(your server)*, set
**authentication** to `oauth` and select the External Security configuration.

---

## Step 6: Sign in

Browse to the App Server, for example `https://marklogic.example.com:8000/qconsole`.
MarkLogic redirects to the MLEAProxy login page, where you enter a username and roles.

On success, MarkLogic logs:

```
OAuth Authentication with Username: [martin] Roles: [marklogic-admin]
OAuth temporary user created with name: [martin]
OAuth authenticated!
ExternalSecurity[OAUTH-Auth-CodeFlow] pass
```

and MLEAProxy logs:

```
Issued authorization code for client 'marklogic-qconsole' user 'martin'
Authorization code exchanged successfully for client: marklogic-qconsole, user: martin
```

> The roles you type must be **real MarkLogic role names**. A misspelling authenticates
> successfully and then grants nothing, which surfaces later as a permissions error rather
> than a login failure.

---

## Troubleshooting

MarkLogic's OAuth error messages frequently point somewhere other than the actual fault.
This table maps each message to what it really means.

| MarkLogic error | Actual cause | Fix |
|---|---|---|
| `SVC-SOCCONN: Certificate verify failed. Please import the proper CA certificate.` | Usually **not** a missing CA. Most often `https://` pointing at MLEAProxy's **plain-HTTP port**. A TLS handshake against an HTTP port surfaces as a certificate error. | Point the token and JWKS URIs at the HTTPS port (8443). Verify with `curl -v --cacert ca-certificate.pem <uri>`: exit 35 or "packet length too long" means a non-TLS port; a genuine trust failure says "unable to get local issuer certificate". |
| `OAuth client secret is misconfigured or doesn't exist.` | MarkLogic's **own** client secret field is empty. Nothing was sent to MLEAProxy. | Set any client secret in the External Security configuration. |
| `XDMP-INTERNAL: Internal error: std::bad_cast` | The `aud` claim is a JSON array. MarkLogic 12.1 only handles a single string. | Fixed in MLEAProxy 2.0.4. On older versions, upgrade. |
| `OAuth JWT Issuer URI must be configured` | `oauth-jwt-issuer-uri` is empty. Required for this flow despite the docs implying otherwise. | Set it to MLEAProxy's `iss` (`mleaproxy-oauth-server` by default). |
| `XDMP-OAUTHVALIDATION: ...must have a URI with TLS (https) or a loopback URI as opposed to: 'http://http://host:8000'` | A **doubled scheme** in `oauth-redirect-uri`, typically from the Admin UI prepending one. | Ensure exactly one scheme in the field. |
| `XDMP-OAUTHVALIDATION: ...as opposed to 'http://host:8000'` | The target App Server is not using TLS. | Assign a certificate template (Step 3), or use a loopback redirect URI for local testing. |
| Certificate warning in the browser, or `SEC_ERROR_UNKNOWN_ISSUER` | The CA is not trusted by the browser, or the App Server certificate's CN does not match the name you browse to. | Import the CA into the browser; check the certificate covers the hostname in use (Step 3). |
| Login succeeds but nothing is permitted | `oauth-role-attribute` names a claim that does not exist, or the role names are not real MarkLogic roles. | Use `roles` or `roles_string` (underscore, not hyphen), and real role names. |

### First question to ask

Check whether MLEAProxy issued a code and whether an exchange followed:

```bash
grep -E "Issued authorization code|Authorization code exchanged" mleaproxy.log
```

- **No code issued** → MarkLogic never reached the authorize endpoint. The fault is in
  MarkLogic's configuration or its redirect URI validation.
- **Code issued, no exchange** → MarkLogic could not call the token endpoint. Suspect the
  URI, its port, its scheme, or the missing client secret.
- **Code exchanged, still failing** → the token was returned but rejected. Suspect claim
  shapes, the issuer, or the role attribute.

MarkLogic's own view, which is verbose and helpful:

```bash
grep -a "OAuth Status" /var/opt/MarkLogic/Logs/ErrorLog.txt | tail -30
```

---

## Known MarkLogic 12.1 behaviours

Observed on MarkLogic **12.1.0**. Stated factually so they are recognisable; they may
change in later releases.

1. **An array `aud` claim causes an internal error.** A token whose `aud` is
   `["client"]` rather than `"client"` fails with `std::bad_cast` while being extracted.
   RFC 7519 §4.1.3 explicitly permits both forms. A C++ cast failure escaping as
   `XDMP-INTERNAL` gives no indication that the audience claim is responsible.

2. **`oauth-jwt-issuer-uri` is required for the Authorization Code flow**, although the
   documentation describes it as "Required if OAuth Vendor is Microsoft Entra or Amazon
   Cognito". With vendor `Other` and the field empty, validation fails with
   `OAuth JWT Issuer URI must be configured`. The field accepts a plain non-URL string
   despite its name. It is *not* enforced when validating a presented `Bearer` token,
   which may be why the documentation treats it as optional.

3. **The Admin UI can double the scheme on `oauth-redirect-uri`**, storing
   `http://http://host:8000` when a value with a scheme is entered, which then fails
   validation.

4. **`SVC-SOCCONN: Certificate verify failed`** is reported for any TLS failure on an
   outbound OAuth connection, including a handshake against a non-TLS port. The message
   names neither the URI attempted nor the certificate expected.

5. **The cluster host name may not match the browsable hostname.** A certificate generated
   by MarkLogic uses the cluster host name, producing a mismatch that looks like a trust
   problem.

---

## Verifying without a browser

A full login is slow to iterate on. Because an App Server configured for `oauth` also
accepts a presented `Bearer` token, token *validation* can be exercised in one command:

```bash
TOKEN=$(curl -s --cacert ca-certificate.pem \
  -X POST https://mleaproxy.example.com:8443/oauth/token \
  -d grant_type=password -d client_id=marklogic-qconsole \
  -d client_secret=secret -d username=martin -d password=password \
  -d roles=marklogic-admin | jq -r .access_token)

curl -sk -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer $TOKEN" https://marklogic.example.com:8000/
```

`303` means the token was accepted; `500` with `bad_cast` or another `XDMP-` code points
at the token contents.

> The `Bearer` path is **less strict** than the browser flow - notably it does not require
> `oauth-jwt-issuer-uri`. A token that passes here can still be rejected in a real login.

To test individual claim shapes, extract the signing key and mint variants:

```bash
unzip -p mlesproxy-2.0.4.jar \
  BOOT-INF/classes/static/certificates/privkey.pem > signkey.pem
```

Sign a JWT with that key, keeping the `kid` from a genuine token so it matches the
published JWKS, and change one claim at a time.

---

## See also

- [OAuth Guide](./OAUTH_GUIDE.md) - the Resource Server flow, token endpoint reference and
  configuration properties
- [JWKS MarkLogic Integration Guide](./JWKS-MarkLogic-Integration-Usage-Guide.md)
- [Configuration Guide](./CONFIGURATION_GUIDE.md)
