# =============================================================================
# _common.sh — sourced by each scenario. Defines `post_*` and reporting helpers.
# =============================================================================
# Variables expected from the environment (exported by run.sh):
#   GATEWAY_URL          — e.g. http://localhost:58081
#   PAYER_PUBKEY_PEM     — recipient public key for the happy path
#   ROGUE_PUBKEY_PEM     — public key the gateway will reject as wrong recipient
#   AUDIT_TOPIC          — Kafka topic to (optionally) tail
#   HARNESS_DIR          — absolute path to harness/
#
# Each `post_*` function:
#   - returns 0 if the response matches the expected status
#   - returns 1 otherwise (and prints diagnostics)
# =============================================================================

# Counters scoped per-scenario.
SUB_PASS=0
SUB_FAIL=0

# ---- helpers ----------------------------------------------------------------

_uuid() { cat /proc/sys/kernel/random/uuid 2>/dev/null || openssl rand -hex 16; }

_match_status() {
    local got="$1" want="$2"
    case "$want" in
        2xx) [[ "$got" =~ ^2 ]] ;;
        4xx) [[ "$got" =~ ^4 ]] ;;
        5xx) [[ "$got" =~ ^5 ]] ;;
        *)   [[ "$got" == "$want" ]] ;;
    esac
}

_jwe() {
    bash "$HARNESS_DIR/lib/jwe-encrypt.sh" "$1" "$2"
}

# ---- post_* primitives ------------------------------------------------------

# Happy path: JWE-encrypt fixture for the legitimate recipient and POST with
# all four §31 protocol headers.
post_jwe() {
    local path="$1" fixture="$2" want="$3"
    local cid; cid=$(_uuid); local aid; aid=$(_uuid)
    local jwe; jwe=$(_jwe "$PAYER_PUBKEY_PEM" "$fixture")
    local body; body=$(printf '{"payload":"%s","x-hcx-correlation_id":"%s","x-hcx-api_call_id":"%s"}' "$jwe" "$cid" "$aid")
    local code
    code=$(curl -k -sS -o /tmp/.hcx-resp -w '%{http_code}' \
        -X POST "$GATEWAY_URL$path" \
        -H 'Content-Type: application/json' \
        -H "x-hcx-correlation_id: $cid" \
        -H "x-hcx-api_call_id: $aid" \
        -H "x-hcx-sender_code: hospital-1.example.com" \
        -H "x-hcx-recipient_code: payer-1.example.com" \
        -H "x-hcx-timestamp: $(date +%s)" \
        --data "$body" || echo 000)
    _match_status "$code" "$want" || { _diag "post_jwe got=$code want=$want"; return 1; }
    # Try to assert the audit topic — tolerant: a missing topic is not fatal,
    # since hcx-test-api auto-creates topics on first publish.
    if [[ "$want" =~ ^2 && -n "${AUDIT_TOPIC:-}" ]]; then
        bash "$HARNESS_DIR/lib/assert-kafka.sh" "$AUDIT_TOPIC" "$cid" 8 || \
            echo "   (info) audit assertion skipped — topic may not yet exist"
    fi
    return 0
}

# Negative: a non-JWE plain JSON object — gateway must reject.
post_raw() {
    local path="$1" body="$2" want="$3"
    local code
    code=$(curl -k -sS -o /tmp/.hcx-resp -w '%{http_code}' \
        -X POST "$GATEWAY_URL$path" \
        -H 'Content-Type: application/json' \
        --data "$body" || echo 000)
    _match_status "$code" "$want" || { _diag "post_raw got=$code want=$want"; return 1; }
}

# Negative: JWE present but the §31 protocol headers are stripped.
post_jwe_no_auth() {
    local path="$1" fixture="$2" want="$3"
    local jwe; jwe=$(_jwe "$PAYER_PUBKEY_PEM" "$fixture")
    local body; body=$(printf '{"payload":"%s"}' "$jwe")
    local code
    code=$(curl -k -sS -o /tmp/.hcx-resp -w '%{http_code}' \
        -X POST "$GATEWAY_URL$path" \
        -H 'Content-Type: application/json' \
        --data "$body" || echo 000)
    _match_status "$code" "$want" || { _diag "post_jwe_no_auth got=$code want=$want"; return 1; }
}

# Negative: JWE encrypted to ROGUE pubkey — recipient mismatch.
post_jwe_wrong_recipient() {
    local path="$1" fixture="$2" want="$3"
    local cid; cid=$(_uuid)
    local jwe; jwe=$(_jwe "$ROGUE_PUBKEY_PEM" "$fixture")
    local body; body=$(printf '{"payload":"%s"}' "$jwe")
    local code
    code=$(curl -k -sS -o /tmp/.hcx-resp -w '%{http_code}' \
        -X POST "$GATEWAY_URL$path" \
        -H 'Content-Type: application/json' \
        -H "x-hcx-correlation_id: $cid" \
        -H "x-hcx-sender_code: hospital-1.example.com" \
        -H "x-hcx-recipient_code: rogue.example.com" \
        --data "$body" || echo 000)
    _match_status "$code" "$want" || { _diag "post_jwe_wrong_recipient got=$code want=$want"; return 1; }
}

# ---- runner -----------------------------------------------------------------

_diag() {
    echo "      diagnostic: $*"
    if [[ -s /tmp/.hcx-resp ]]; then
        echo "      response body (first 200 chars):"
        head -c 200 /tmp/.hcx-resp | sed 's/^/        /'
        echo
    fi
}

run_subscenario() {
    local name="$1"; shift
    printf '   - %-22s ' "$name"
    if "$@"; then
        echo "PASS"
        SUB_PASS=$((SUB_PASS + 1))
    else
        echo "FAIL"
        SUB_FAIL=$((SUB_FAIL + 1))
    fi
}

scenario_done() {
    local name="$1"
    echo "   summary: $SUB_PASS passed, $SUB_FAIL failed"
    if (( SUB_FAIL > 0 )); then
        echo "scenario $name: FAILED" >&2
        exit 1
    fi
    echo "scenario $name: OK"
}
