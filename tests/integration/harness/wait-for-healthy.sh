#!/usr/bin/env bash
# =============================================================================
# wait-for-healthy.sh — block until a docker-compose service is healthy.
# =============================================================================
# Usage: wait-for-healthy.sh <compose-file> <service> [timeout-seconds=180]
#
# Polls `docker inspect` for the container's health status, recognising
# both Compose v1 (`<project>_<svc>_1`) and v2 (`<project>-<svc>-1`) name
# patterns. Falls back to checking running state for services that don't
# define a healthcheck.
# =============================================================================
set -euo pipefail

COMPOSE_FILE="${1:?compose file required}"
SERVICE="${2:?service name required}"
TIMEOUT="${3:-180}"

deadline=$(( $(date +%s) + TIMEOUT ))

# Resolve the actual container ID for the service via compose itself —
# avoids guessing project name / separator.
get_cid() {
    docker compose -f "$COMPOSE_FILE" ps -q "$SERVICE" 2>/dev/null | head -n1
}

while :; do
    CID=$(get_cid || true)
    if [[ -n "${CID:-}" ]]; then
        STATUS=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$CID" 2>/dev/null || echo "missing")
        case "$STATUS" in
            healthy|running)
                echo "wait-for-healthy: $SERVICE is $STATUS"
                exit 0
                ;;
            unhealthy)
                echo "wait-for-healthy: $SERVICE reported UNHEALTHY" >&2
                docker logs --tail 80 "$CID" >&2 || true
                exit 1
                ;;
        esac
    fi
    if (( $(date +%s) >= deadline )); then
        echo "wait-for-healthy: timeout after ${TIMEOUT}s waiting for $SERVICE" >&2
        if [[ -n "${CID:-}" ]]; then
            docker logs --tail 80 "$CID" >&2 || true
        fi
        exit 1
    fi
    sleep 3
done
