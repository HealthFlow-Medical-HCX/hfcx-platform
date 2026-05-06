#!/usr/bin/env bash
# =============================================================================
# Gap-17 integration test harness — entrypoint.
# =============================================================================
# Brings up the test docker-compose, generates ephemeral RSA keys, runs the
# four §31 protocol scenarios, then tears everything down.
#
# Idempotent: a prior aborted run leaves a stack labelled `hcx-it`. We
# always `down -v` before `up` so a second invocation is clean.
# =============================================================================
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
HARNESS_DIR="$HERE"
INTEG_DIR=$(cd "$HERE/.." && pwd)
COMPOSE_FILE="$INTEG_DIR/docker-compose-test.yml"
PROJECT="hcx-it"

export HARNESS_DIR
export GATEWAY_URL="${GATEWAY_URL:-http://localhost:58081}"
export AUDIT_TOPIC="${AUDIT_TOPIC:-local.hcx.audit}"

KEYS_DIR="$INTEG_DIR/.keys"
export PAYER_PUBKEY_PEM="$KEYS_DIR/payer-public.pem"
export ROGUE_PUBKEY_PEM="$KEYS_DIR/rogue-public.pem"

log()  { printf '[harness] %s\n' "$*"; }
fail() { printf '[harness] FAIL: %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 0. Pre-flight.
# ---------------------------------------------------------------------------
if ! command -v docker >/dev/null 2>&1; then
    fail "docker not on PATH; the integration harness requires Docker."
fi
if ! docker info >/dev/null 2>&1; then
    fail "docker daemon not reachable."
fi
if ! docker compose version >/dev/null 2>&1; then
    fail "docker compose v2 required (try: docker compose version)."
fi

# ---------------------------------------------------------------------------
# 1. Ephemeral RSA keys (idempotent — overwrites on re-run).
# ---------------------------------------------------------------------------
log "Generating ephemeral RSA keypairs under $KEYS_DIR"
mkdir -p "$KEYS_DIR"
for who in provider payer rogue; do
    if [[ ! -f "$KEYS_DIR/$who-private.pem" ]]; then
        openssl genrsa -out "$KEYS_DIR/$who-private.pem" 2048 2>/dev/null
        openssl rsa -in "$KEYS_DIR/$who-private.pem" -pubout \
            -out "$KEYS_DIR/$who-public.pem" 2>/dev/null
    fi
done

# ---------------------------------------------------------------------------
# 2. Tear down any prior state.
# ---------------------------------------------------------------------------
log "Cleaning prior stack (idempotent)"
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" down -v --remove-orphans \
    >/dev/null 2>&1 || true

# ---------------------------------------------------------------------------
# 3. Validate compose file early — fast feedback before pulling images.
# ---------------------------------------------------------------------------
log "Validating docker-compose-test.yml"
docker compose -f "$COMPOSE_FILE" config -q || fail "compose config invalid"

# ---------------------------------------------------------------------------
# 4. Determine whether hcx-api / hcx-api-gateway images exist; if not, skip
#    the full HTTP scenarios and just exercise the mock services. This keeps
#    the harness useful in environments that don't have the prod images.
# ---------------------------------------------------------------------------
HAVE_HCX_API=true
HAVE_GATEWAY=true
docker image inspect hcx-api:egypt-fixed         >/dev/null 2>&1 || HAVE_HCX_API=false
docker image inspect hcx-api-gateway:egypt-fixed >/dev/null 2>&1 || HAVE_GATEWAY=false

if ! $HAVE_HCX_API || ! $HAVE_GATEWAY; then
    log "Note: hcx-api or hcx-api-gateway image missing locally."
    log "      The harness will boot infra + mocks and run a degraded set of"
    log "      checks against the mock services directly. Build the prod"
    log "      images via 'docker build' for full coverage."
    DEGRADED=true
else
    DEGRADED=false
fi

# ---------------------------------------------------------------------------
# 5. Bring stack up.
# ---------------------------------------------------------------------------
log "Starting stack (project=$PROJECT)"
if $DEGRADED; then
    docker compose -p "$PROJECT" -f "$COMPOSE_FILE" up -d --build \
        hcx-test-postgres hcx-test-redis hcx-test-kafka hcx-test-keycloak \
        mock-provider mock-payer
else
    docker compose -p "$PROJECT" -f "$COMPOSE_FILE" up -d --build
fi

# ---------------------------------------------------------------------------
# 6. Wait for health.
# ---------------------------------------------------------------------------
log "Waiting for infrastructure"
bash "$HARNESS_DIR/wait-for-healthy.sh" "$COMPOSE_FILE" hcx-test-postgres 90
bash "$HARNESS_DIR/wait-for-healthy.sh" "$COMPOSE_FILE" hcx-test-redis    60
bash "$HARNESS_DIR/wait-for-healthy.sh" "$COMPOSE_FILE" hcx-test-kafka    120
bash "$HARNESS_DIR/wait-for-healthy.sh" "$COMPOSE_FILE" mock-provider     120
bash "$HARNESS_DIR/wait-for-healthy.sh" "$COMPOSE_FILE" mock-payer        120

if ! $DEGRADED; then
    bash "$HARNESS_DIR/wait-for-healthy.sh" "$COMPOSE_FILE" hcx-test-api          240
    bash "$HARNESS_DIR/wait-for-healthy.sh" "$COMPOSE_FILE" hcx-test-api-gateway  240
fi

# ---------------------------------------------------------------------------
# 7. Sanity-ping the mock services from inside the network so we know the
#    Spring Boot apps are actually serving routes.
# ---------------------------------------------------------------------------
log "Pinging mock services"
docker run --rm --network "${PROJECT}_hcx-test-network" curlimages/curl:8.4.0 \
    -fsS http://mock-provider:8090/actuator/health | grep -q '"UP"' \
    || fail "mock-provider /actuator/health not UP"
docker run --rm --network "${PROJECT}_hcx-test-network" curlimages/curl:8.4.0 \
    -fsS http://mock-payer:8091/actuator/health | grep -q '"UP"' \
    || fail "mock-payer /actuator/health not UP"

# ---------------------------------------------------------------------------
# 8. Run scenarios. In degraded mode we exercise the mocks directly; in full
#    mode we drive the gateway.
# ---------------------------------------------------------------------------
if $DEGRADED; then
    log "DEGRADED mode: invoking mocks directly (gateway image not present)"
    NET="${PROJECT}_hcx-test-network"
    for cycle in coverageeligibility/check preauth/submit claim/submit paymentnotice/request; do
        for target in mock-provider:8090 mock-payer:8091; do
            code=$(docker run --rm --network "$NET" curlimages/curl:8.4.0 \
                -sS -o /dev/null -w '%{http_code}' \
                -X POST "http://$target/v1/$cycle" \
                -H 'Content-Type: application/json' \
                --data '{"resourceType":"Bundle","entry":[]}' || echo 000)
            [[ "$code" == "202" ]] || fail "mock $target /v1/$cycle returned $code"
            log "   mock $target /v1/$cycle => $code"
        done
    done
else
    log "Running §31 scenarios against gateway $GATEWAY_URL"
    bash "$HARNESS_DIR/scenarios/01-coverage-eligibility.sh"
    bash "$HARNESS_DIR/scenarios/02-preauth.sh"
    bash "$HARNESS_DIR/scenarios/03-claim.sh"
    bash "$HARNESS_DIR/scenarios/04-payment-notice.sh"
fi

# ---------------------------------------------------------------------------
# 9. Tear down (best-effort; don't mask success).
# ---------------------------------------------------------------------------
if [[ "${KEEP_STACK:-0}" != "1" ]]; then
    log "Tearing down stack"
    docker compose -p "$PROJECT" -f "$COMPOSE_FILE" down -v --remove-orphans \
        >/dev/null 2>&1 || true
else
    log "KEEP_STACK=1 — leaving stack running for inspection"
fi

log "ALL CHECKS PASS"
