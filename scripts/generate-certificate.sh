#!/bin/bash
#
# Generates the TLS server certificate used by MLEAProxy's HTTPS listener, signed by
# the MLEAProxy CA, and points the configuration at it.
#
# OAuth 2.0 and SAML both need HTTPS: MarkLogic 12.1 refuses a plain-HTTP token or JWKS
# URI, and the Authorization Code flow redirects a browser to MLEAProxy's login page.
# A certificate is only accepted if the name the client dialled appears in it, so the
# certificate has to match the hostname MarkLogic and the browser actually use.
#
# The CA is reused whenever one already exists. That is deliberate: the CA is the file
# imported into MarkLogic's Certificate Authorities store, so reissuing the server
# certificate for a new hostname must not invalidate that import.
#
# Usage: ./scripts/generate-certificate.sh [HOSTNAME] [options]
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Kept in step with TlsCertificateService, so a certificate made here and one generated
# by MLEAProxy at startup are interchangeable.
CA_SUBJECT_CN="MLEAProxy Development CA"
ORG_UNIT="MLEAProxy HTTPS Listener"
ORG="Progress MarkLogic External Security Proxy"
CA_DAYS=3650
SERVER_DAYS=825
KEY_BITS=2048

CERT_DIR="$PROJECT_ROOT/certificates"
PROPERTIES_FILE="$PROJECT_ROOT/mleaproxy.properties"
HOSTNAME_ARG=""
EXTRA_SANS=()
FORCE=false
NEW_CA=false
UPDATE_CONFIG=true

usage() {
    cat <<'EOF'
Generate the MLEAProxy HTTPS server certificate, signed by the MLEAProxy CA.

Usage:
  ./scripts/generate-certificate.sh [HOSTNAME] [options]

Arguments:
  HOSTNAME              Name the certificate must match. When omitted, the current
                        host's fully qualified name is detected automatically.

Options:
  -a, --alt NAME        Extra subjectAltName entry; repeat for more than one.
                        DNS names and IP addresses are both accepted and are
                        classified automatically.
  -d, --dir DIR         Directory for the certificates (default: ./certificates)
  -p, --properties FILE Properties file to update (default: ./mleaproxy.properties)
      --days N          Server certificate lifetime in days (default: 825)
      --new-ca          Create a fresh CA, replacing any existing one. Every
                        MarkLogic server that imported the old CA must reimport.
  -f, --force           Replace an existing server certificate
  -n, --no-config       Only write the certificates; leave the properties alone
  -h, --help            Show this help

The certificate always covers localhost, 127.0.0.1 and ::1 in addition to the
hostname, so local testing keeps working.

Examples:
  ./scripts/generate-certificate.sh
  ./scripts/generate-certificate.sh mleaproxy.example.com
  ./scripts/generate-certificate.sh mleaproxy.example.com -a 192.168.1.50 -a mlproxy
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -a|--alt)
            [[ $# -ge 2 ]] || { echo -e "${RED}✗ $1 requires a value${NC}" >&2; exit 1; }
            EXTRA_SANS+=("$2"); shift 2 ;;
        -d|--dir)
            [[ $# -ge 2 ]] || { echo -e "${RED}✗ $1 requires a value${NC}" >&2; exit 1; }
            CERT_DIR="$2"; shift 2 ;;
        -p|--properties)
            [[ $# -ge 2 ]] || { echo -e "${RED}✗ $1 requires a value${NC}" >&2; exit 1; }
            PROPERTIES_FILE="$2"; shift 2 ;;
        --days)
            [[ $# -ge 2 ]] || { echo -e "${RED}✗ $1 requires a value${NC}" >&2; exit 1; }
            SERVER_DAYS="$2"; shift 2 ;;
        --new-ca)   NEW_CA=true; shift ;;
        -f|--force) FORCE=true; shift ;;
        -n|--no-config) UPDATE_CONFIG=false; shift ;;
        -h|--help)  usage; exit 0 ;;
        -*)
            echo -e "${RED}✗ Unknown option: $1${NC}" >&2
            echo "  Run with --help for usage" >&2
            exit 1 ;;
        *)
            if [[ -n "$HOSTNAME_ARG" ]]; then
                echo -e "${RED}✗ Only one hostname may be given${NC}" >&2
                echo "  Use -a/--alt for additional names" >&2
                exit 1
            fi
            HOSTNAME_ARG="$1"; shift ;;
    esac
done

echo -e "${BLUE}================================================================================${NC}"
echo -e "${BLUE}                  MLEAProxy TLS Certificate Generation${NC}"
echo -e "${BLUE}================================================================================${NC}"
echo ""

if ! command -v openssl &> /dev/null; then
    echo -e "${RED}✗ openssl not found${NC}"
    echo ""
    echo "openssl is required to generate certificates. Install it with:"
    echo "  • macOS:  brew install openssl"
    echo "  • Debian/Ubuntu: apt-get install openssl"
    echo "  • RHEL/Fedora:   dnf install openssl"
    echo ""
    echo "Alternatively, start MLEAProxy with no certificate files present and it will"
    echo "generate a certificate for the detected hostname itself."
    exit 2
fi

if ! [[ "$SERVER_DAYS" =~ ^[0-9]+$ ]] || [[ "$SERVER_DAYS" -lt 1 ]]; then
    echo -e "${RED}✗ --days must be a positive whole number (got: $SERVER_DAYS)${NC}"
    exit 1
fi

# ---------------------------------------------------------------------------
# Hostname
# ---------------------------------------------------------------------------

# Tries each source in turn, preferring a fully qualified name: a short name in the
# certificate fails validation as soon as MarkLogic is configured with the FQDN.
detect_hostname() {
    local candidate

    candidate=$(hostname -f 2>/dev/null) || candidate=""
    if is_usable_hostname "$candidate"; then echo "$candidate"; return; fi

    if command -v hostnamectl &> /dev/null; then
        candidate=$(hostnamectl --static 2>/dev/null) || candidate=""
        if is_usable_hostname "$candidate"; then echo "$candidate"; return; fi
    fi

    if command -v python3 &> /dev/null; then
        candidate=$(python3 -c 'import socket; print(socket.getfqdn())' 2>/dev/null) || candidate=""
        if is_usable_hostname "$candidate"; then echo "$candidate"; return; fi
    fi

    # No FQDN available, so fall back to the short name rather than giving up.
    candidate=$(hostname 2>/dev/null) || candidate=""
    if is_usable_hostname "$candidate"; then echo "$candidate"; return; fi

    candidate=$(uname -n 2>/dev/null) || candidate=""
    if is_usable_hostname "$candidate"; then echo "$candidate"; return; fi

    echo "localhost"
}

is_usable_hostname() {
    local value="${1:-}"
    [[ -n "$value" ]] || return 1
    [[ "$value" != "localhost" ]] || return 1
    [[ "$value" != "localhost.localdomain" ]] || return 1
    [[ "$value" != 127.* ]] || return 1
    return 0
}

# openssl writes nothing useful on success and everything useful to stderr on
# failure, so its output is held back and only shown when something goes wrong.
run_openssl() {
    local description="$1"; shift
    if ! openssl "$@" > "$WORK_DIR/openssl.out" 2>&1; then
        echo -e "${RED}✗ $description failed${NC}" >&2
        sed 's/^/    /' "$WORK_DIR/openssl.out" >&2
        exit 1
    fi
}

is_ip_address() {
    local value="$1"
    # Any colon means IPv6; IPv4 is four dotted octets in range.
    if [[ "$value" == *:* ]]; then
        return 0
    fi
    if [[ "$value" =~ ^([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})$ ]]; then
        local octet
        for octet in "${BASH_REMATCH[@]:1:4}"; do
            # Zero padding is rejected outright: OpenSSL will not parse 010.2.3.4,
            # and treating it as a hostname instead would hide the mistake.
            [[ "$octet" == "0" || "$octet" != 0* ]] || return 1
            [[ "$octet" -le 255 ]] || return 1
        done
        return 0
    fi
    return 1
}

if [[ -n "$HOSTNAME_ARG" ]]; then
    PRIMARY_HOST="$HOSTNAME_ARG"
    echo -e "Hostname:        ${GREEN}${PRIMARY_HOST}${NC} (specified)"
else
    PRIMARY_HOST=$(detect_hostname)
    echo -e "Hostname:        ${GREEN}${PRIMARY_HOST}${NC} (detected)"
    if [[ "$PRIMARY_HOST" == "localhost" ]]; then
        echo -e "  ${YELLOW}⚠ No hostname could be determined, so the certificate will only be"
        echo -e "    valid for local access. Pass the name MarkLogic will use:"
        echo -e "      ./scripts/generate-certificate.sh mleaproxy.example.com${NC}"
    elif [[ "$PRIMARY_HOST" != *.* ]]; then
        echo -e "  ${YELLOW}⚠ This is a short name, not a fully qualified one. If MarkLogic"
        echo -e "    reaches MLEAProxy by FQDN, pass that name instead or add it with -a.${NC}"
    fi
fi

if ! is_ip_address "$PRIMARY_HOST" \
    && ! [[ "$PRIMARY_HOST" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]]; then
    echo -e "${RED}✗ '$PRIMARY_HOST' is not a valid hostname or IP address${NC}"
    exit 1
fi

# ---------------------------------------------------------------------------
# Subject alternative names
# ---------------------------------------------------------------------------
# Every name a client might dial has to be listed, because a certificate's CN is
# ignored by modern TLS clients - only subjectAltName is consulted.

SANS=("$PRIMARY_HOST")
# The short form of an FQDN, since MarkLogic is often configured with just that.
if [[ "$PRIMARY_HOST" == *.* ]] && ! is_ip_address "$PRIMARY_HOST"; then
    SANS+=("${PRIMARY_HOST%%.*}")
fi
SANS+=("localhost" "127.0.0.1" "::1")
if [[ ${#EXTRA_SANS[@]} -gt 0 ]]; then
    SANS+=("${EXTRA_SANS[@]}")
fi

DNS_NAMES=()
IP_NAMES=()
seen=""
for san in "${SANS[@]}"; do
    san="$(echo "$san" | tr -d '[:space:]')"
    [[ -n "$san" ]] || continue
    case "$seen" in *"|$san|"*) continue ;; esac
    seen="$seen|$san|"
    if is_ip_address "$san"; then
        IP_NAMES+=("$san")
    elif [[ "$san" =~ ^[0-9.]+$ ]]; then
        # All digits and dots but not a valid address: a mistyped IP rather than a
        # hostname, and recording it as a DNS name would quietly produce a
        # certificate that never matches anything.
        echo -e "${RED}✗ '$san' is not a valid IP address${NC}"
        echo "  An IPv4 octet must be 0-255 and may not be zero padded:"
        echo "  use 10.2.3.4 rather than 010.2.3.4"
        exit 1
    elif [[ "$san" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]]; then
        DNS_NAMES+=("$san")
    else
        echo -e "${RED}✗ '$san' is neither a valid hostname nor a valid IP address${NC}"
        echo "  Note that an IPv4 octet may not be zero padded: use 10.2.3.4, not 010.2.3.4"
        exit 1
    fi
done

ALL_NAMES=("${DNS_NAMES[@]}" "${IP_NAMES[@]}")
echo -e "Subject names:   ${GREEN}$(IFS=', '; echo "${ALL_NAMES[*]}")${NC}"
echo ""

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

mkdir -p "$CERT_DIR"
CERT_DIR="$(cd "$CERT_DIR" && pwd)"

CA_CERT="$CERT_DIR/ca-certificate.pem"
CA_KEY="$CERT_DIR/ca-privkey.pem"
SERVER_CERT="$CERT_DIR/tls-certificate.pem"
SERVER_KEY="$CERT_DIR/tls-privkey.pem"
CA_SERIAL="$CERT_DIR/ca-serial.srl"

if [[ -f "$SERVER_CERT" || -f "$SERVER_KEY" ]] && [[ "$FORCE" != true ]]; then
    echo -e "${YELLOW}⚠ A server certificate already exists:${NC}"
    [[ -f "$SERVER_CERT" ]] && echo "    $SERVER_CERT"
    [[ -f "$SERVER_KEY" ]] && echo "    $SERVER_KEY"
    echo ""
    echo "  Current subject alternative names:"
    if [[ -f "$SERVER_CERT" ]]; then
        openssl x509 -in "$SERVER_CERT" -noout -ext subjectAltName 2>/dev/null \
            | sed -n '2p' | sed 's/^ */    /' || echo "    (none)"
    fi
    echo ""
    echo "  Re-run with --force to replace it."
    exit 1
fi

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

# ---------------------------------------------------------------------------
# Certificate authority
# ---------------------------------------------------------------------------

# A CA is only created when there isn't one. Reissuing the server certificate under
# the existing CA means MarkLogic's imported trust anchor stays valid.
if [[ "$NEW_CA" == true ]] || [[ ! -f "$CA_CERT" ]] || [[ ! -f "$CA_KEY" ]]; then

    if [[ "$NEW_CA" != true ]] && { [[ -f "$CA_CERT" ]] || [[ -f "$CA_KEY" ]]; }; then
        echo -e "${RED}✗ The CA is incomplete: both the certificate and its key are needed${NC}"
        echo "    certificate: $CA_CERT $( [[ -f "$CA_CERT" ]] && echo '(present)' || echo '(MISSING)')"
        echo "    private key: $CA_KEY $( [[ -f "$CA_KEY" ]] && echo '(present)' || echo '(MISSING)')"
        echo ""
        echo "  Restore the missing file, or run with --new-ca to start over. A new CA"
        echo "  must be reimported into every MarkLogic server that trusts the old one."
        exit 1
    fi

    if [[ "$NEW_CA" == true ]] && [[ -f "$CA_CERT" ]]; then
        echo -e "${YELLOW}⚠ Replacing the existing CA. Every MarkLogic server that imported"
        echo -e "  the old CA must import the new one, or TLS will start failing.${NC}"
        echo ""
    fi

    echo "Creating certificate authority..."

    cat > "$WORK_DIR/ca.cnf" <<EOF
[req]
distinguished_name = dn
prompt = no
x509_extensions = v3_ca

[dn]
CN = $CA_SUBJECT_CN
OU = $ORG_UNIT
O = $ORG

[v3_ca]
basicConstraints = critical,CA:TRUE
keyUsage = critical,keyCertSign,cRLSign,digitalSignature
subjectKeyIdentifier = hash
EOF

    run_openssl "CA key generation" \
        genpkey -algorithm RSA -pkeyopt "rsa_keygen_bits:$KEY_BITS" \
        -out "$WORK_DIR/ca-privkey.pem"
    run_openssl "CA certificate generation" \
        req -x509 -new -key "$WORK_DIR/ca-privkey.pem" -sha256 -days "$CA_DAYS" \
        -config "$WORK_DIR/ca.cnf" -out "$WORK_DIR/ca-certificate.pem"

    mv "$WORK_DIR/ca-privkey.pem" "$CA_KEY"
    mv "$WORK_DIR/ca-certificate.pem" "$CA_CERT"
    chmod 600 "$CA_KEY"
    rm -f "$CA_SERIAL"
    echo -e "${GREEN}✓ CA created: $CA_CERT${NC}"
    CA_IS_NEW=true
else
    # Confirm the key really belongs to the certificate before signing with it: a
    # mismatch would otherwise surface much later as an unverifiable chain.
    cert_modulus=$(openssl x509 -in "$CA_CERT" -noout -modulus 2>/dev/null) || cert_modulus=""
    key_modulus=$(openssl rsa -in "$CA_KEY" -noout -modulus 2>/dev/null) || key_modulus=""
    if [[ -z "$cert_modulus" || "$cert_modulus" != "$key_modulus" ]]; then
        echo -e "${RED}✗ $CA_KEY is not the private key for $CA_CERT${NC}"
        echo "  Run with --new-ca to generate a matching pair (requires reimporting"
        echo "  the CA into MarkLogic)."
        exit 1
    fi
    if ! openssl x509 -in "$CA_CERT" -noout -text 2>/dev/null | grep -q "CA:TRUE"; then
        echo -e "${RED}✗ $CA_CERT is not a CA certificate and cannot sign${NC}"
        echo "  Run with --new-ca to generate a usable CA."
        exit 1
    fi
    echo -e "${GREEN}✓ Reusing CA: $CA_CERT${NC}"
    echo "  Already imported into MarkLogic? No reimport is needed."
    CA_IS_NEW=false
fi
echo ""

# ---------------------------------------------------------------------------
# Server certificate
# ---------------------------------------------------------------------------

echo "Generating server certificate..."

# CN is limited to 64 characters; subjectAltName carries the real identity anyway.
SERVER_CN="$PRIMARY_HOST"
if [[ ${#SERVER_CN} -gt 64 ]]; then
    SERVER_CN="MLEAProxy HTTPS"
fi

{
    cat <<EOF
[req]
distinguished_name = dn
prompt = no
req_extensions = v3_req

[dn]
CN = $SERVER_CN
OU = $ORG_UNIT
O = $ORG

[v3_req]
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[v3_sign]
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid,issuer
subjectAltName = @alt_names

[alt_names]
EOF
    i=1
    for name in "${DNS_NAMES[@]}"; do
        echo "DNS.$i = $name"
        i=$((i + 1))
    done
    i=1
    for name in "${IP_NAMES[@]}"; do
        echo "IP.$i = $name"
        i=$((i + 1))
    done
} > "$WORK_DIR/server.cnf"

# genpkey emits PKCS#8 ("BEGIN PRIVATE KEY"), which is the only form
# TlsCertificateService can read.
run_openssl "server key generation" \
    genpkey -algorithm RSA -pkeyopt "rsa_keygen_bits:$KEY_BITS" \
    -out "$WORK_DIR/tls-privkey.pem"

run_openssl "certificate signing request" \
    req -new -key "$WORK_DIR/tls-privkey.pem" -config "$WORK_DIR/server.cnf" \
    -out "$WORK_DIR/server.csr"

# v3_sign rather than v3_req, because the authority key identifier can only be
# computed once the issuing certificate is known.
run_openssl "certificate signing" \
    x509 -req -in "$WORK_DIR/server.csr" \
    -CA "$CA_CERT" -CAkey "$CA_KEY" \
    -CAserial "$CA_SERIAL" -CAcreateserial \
    -days "$SERVER_DAYS" -sha256 \
    -extfile "$WORK_DIR/server.cnf" -extensions v3_sign \
    -out "$WORK_DIR/tls-certificate.pem"

# Catch a broken chain here rather than at handshake time.
if ! openssl verify -CAfile "$CA_CERT" "$WORK_DIR/tls-certificate.pem" > /dev/null 2>&1; then
    echo -e "${RED}✗ The generated certificate does not verify against the CA${NC}"
    openssl verify -CAfile "$CA_CERT" "$WORK_DIR/tls-certificate.pem" || true
    exit 1
fi

mv "$WORK_DIR/tls-privkey.pem" "$SERVER_KEY"
mv "$WORK_DIR/tls-certificate.pem" "$SERVER_CERT"
chmod 600 "$SERVER_KEY"

echo -e "${GREEN}✓ Server certificate: $SERVER_CERT${NC}"
echo -e "${GREEN}✓ Private key:        $SERVER_KEY${NC}"
echo ""

CA_FINGERPRINT=$(openssl x509 -in "$CA_CERT" -noout -fingerprint -sha256 2>/dev/null \
    | sed 's/.*=//')
SERVER_NOT_AFTER=$(openssl x509 -in "$SERVER_CERT" -noout -enddate 2>/dev/null \
    | sed 's/notAfter=//')

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

# Rewrites a property in place, whether it is currently set or commented out, and
# appends it when absent. ./mleaproxy.properties has the highest precedence of the
# file-based property sources, so what is written here wins.
set_property() {
    local file="$1" key="$2" value="$3"
    KEY="$key" VALUE="$value" awk '
        BEGIN { key = ENVIRON["KEY"]; value = ENVIRON["VALUE"]; done = 0 }
        {
            stripped = $0
            sub(/^[[:space:]]*#?[[:space:]]*/, "", stripped)
            if (index(stripped, key "=") == 1) {
                if (!done) { print key "=" value; done = 1 }
                next
            }
            print
        }
        END { if (!done) print key "=" value }
    ' "$file" > "$file.tmp" && mv "$file.tmp" "$file"
}

# Relative paths where possible, so the certificates directory can move with the
# project; MLEAProxy resolves these against its working directory.
relative_if_inside_project() {
    local path="$1"
    if [[ "$path" == "$PROJECT_ROOT"/* ]]; then
        echo "./${path#"$PROJECT_ROOT"/}"
    else
        echo "$path"
    fi
}

if [[ "$UPDATE_CONFIG" == true ]]; then
    echo "Updating $PROPERTIES_FILE..."

    if [[ ! -f "$PROPERTIES_FILE" ]]; then
        cat > "$PROPERTIES_FILE" <<EOF
# ================================================================
# MLEAProxy Local Configuration
# ================================================================
# Overrides the defaults bundled in the JAR. Written by
# scripts/generate-certificate.sh; hand edits below are preserved.
# ================================================================

EOF
        echo "  Created (it did not exist)"
    fi

    ALL_SANS=$(IFS=,; echo "${DNS_NAMES[*]},${IP_NAMES[*]}" | sed 's/,,*/,/g; s/^,//; s/,$//')

    set_property "$PROPERTIES_FILE" "mleaproxy.https.enabled" "true"
    set_property "$PROPERTIES_FILE" "mleaproxy.https.certificate" "$(relative_if_inside_project "$SERVER_CERT")"
    set_property "$PROPERTIES_FILE" "mleaproxy.https.private-key" "$(relative_if_inside_project "$SERVER_KEY")"
    set_property "$PROPERTIES_FILE" "mleaproxy.https.ca-certificate" "$(relative_if_inside_project "$CA_CERT")"
    set_property "$PROPERTIES_FILE" "mleaproxy.https.ca-private-key" "$(relative_if_inside_project "$CA_KEY")"
    set_property "$PROPERTIES_FILE" "mleaproxy.https.subject-alt-names" "$ALL_SANS"

    # The URLs MLEAProxy advertises - JWKS, token, authorize - are built from this
    # hostname. If it did not match the certificate, MarkLogic would be handed a URL
    # the certificate does not cover and the handshake would fail.
    set_property "$PROPERTIES_FILE" "mleaproxy.server.hostname" "$PRIMARY_HOST"

    echo -e "${GREEN}✓ Configuration updated${NC}"

    # These take precedence over the hostname just written, so a stale value here would
    # send MarkLogic to a URL the new certificate does not cover.
    for override in oauth.server.base.url oauth.authorize.base.url; do
        current=$(grep -E "^[[:space:]]*${override//./\.}[[:space:]]*=" "$PROPERTIES_FILE" \
            | tail -n 1 | sed 's/.*=[[:space:]]*//' | tr -d '[:space:]') || current=""
        if [[ -n "$current" && "$current" != *"$PRIMARY_HOST"* ]]; then
            echo -e "  ${YELLOW}⚠ $override is set to $current, which does not use"
            echo -e "    $PRIMARY_HOST. It overrides the hostname written above, so"
            echo -e "    MarkLogic would be handed a URL this certificate does not cover.${NC}"
        fi
    done
    echo ""
else
    echo -e "${YELLOW}⚠ Configuration not updated (--no-config)${NC}"
    echo "  Set these in mleaproxy.properties to use the new certificate:"
    echo "    mleaproxy.https.certificate=$(relative_if_inside_project "$SERVER_CERT")"
    echo "    mleaproxy.https.private-key=$(relative_if_inside_project "$SERVER_KEY")"
    echo "    mleaproxy.https.ca-certificate=$(relative_if_inside_project "$CA_CERT")"
    echo "    mleaproxy.https.ca-private-key=$(relative_if_inside_project "$CA_KEY")"
    echo "    mleaproxy.server.hostname=$PRIMARY_HOST"
    echo ""
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

# The ports are only needed for the URLs printed below, so fall back to the defaults
# from mleaproxy.properties rather than failing when nothing is configured locally.
read_port() {
    local key="$1" fallback="$2" value=""
    if [[ -f "$PROPERTIES_FILE" ]]; then
        value=$(grep -E "^[[:space:]]*${key}[[:space:]]*=" "$PROPERTIES_FILE" | tail -n 1 \
            | sed 's/.*=[[:space:]]*//' | tr -d '[:space:]') || value=""
    fi
    echo "${value:-$fallback}"
}

HTTPS_PORT=$(read_port 'mleaproxy\.https\.port' 8443)
HTTP_PORT=$(read_port 'server\.port' 8080)

echo -e "${BLUE}================================================================================${NC}"
echo -e "${BLUE}                              Summary${NC}"
echo -e "${BLUE}================================================================================${NC}"
echo ""
echo "  Server certificate valid until: $SERVER_NOT_AFTER"
echo "  CA SHA-256 fingerprint:         $CA_FINGERPRINT"
echo ""
echo -e "${YELLOW}Next steps${NC}"
echo ""
echo "  1. Restart MLEAProxy so it picks up the new certificate:"
echo "       ./scripts/stop.sh && ./scripts/start.sh"
echo ""
if [[ "$CA_IS_NEW" == true ]]; then
    echo "  2. Import the CA into MarkLogic (Security → Certificate Authorities):"
else
    echo "  2. If MarkLogic has not already imported this CA, do so now"
    echo "     (Security → Certificate Authorities):"
fi
echo "       Download: http://$PRIMARY_HOST:$HTTP_PORT/tls/ca"
echo "       Or from the status page: http://$PRIMARY_HOST:$HTTP_PORT/status"
echo "       Or copy the file:        $CA_CERT"
echo ""
echo "     Without it MarkLogic cannot fetch JWKS or exchange an authorization code,"
echo "     and reports only: SVC-SOCCONN: Certificate verify failed"
echo ""
echo "  3. Verify the listener:"
echo "       openssl s_client -connect $PRIMARY_HOST:$HTTPS_PORT -CAfile $CA_CERT </dev/null"
echo "       curl --cacert $CA_CERT https://$PRIMARY_HOST:$HTTPS_PORT/oauth/jwks"
echo ""
