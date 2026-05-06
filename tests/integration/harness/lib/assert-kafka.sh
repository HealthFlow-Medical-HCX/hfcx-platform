#!/usr/bin/env bash
# =============================================================================
# assert-kafka.sh — consume from a Kafka topic and assert a substring appears.
# =============================================================================
# Usage: assert-kafka.sh <topic> <expected-substring> [timeout-seconds=15]
#
# Runs `kafka-console-consumer` inside the running hcx-test-kafka container,
# reads from the beginning of the topic with a hard timeout, and greps the
# captured stream for the expected substring. Exit 0 on match, 1 otherwise.
#
# Idempotent: each invocation creates a fresh consumer with a unique group id.
# =============================================================================
set -euo pipefail

TOPIC="${1:?topic required}"
EXPECTED="${2:?expected substring required}"
TIMEOUT="${3:-15}"

CONTAINER="${KAFKA_CONTAINER:-hcx-test-kafka}"

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
    echo "assert-kafka: container '$CONTAINER' is not running" >&2
    exit 1
fi

# Use a unique group so we always start from --from-beginning cleanly.
GROUP="harness-$(date +%s%N)-$$"

OUT=$(docker exec "$CONTAINER" timeout "$TIMEOUT" \
    kafka-console-consumer \
        --bootstrap-server localhost:29092 \
        --topic "$TOPIC" \
        --from-beginning \
        --timeout-ms $((TIMEOUT * 1000)) \
        --consumer-property "group.id=$GROUP" \
        2>/dev/null || true)

if grep -qF -- "$EXPECTED" <<<"$OUT"; then
    echo "assert-kafka: matched '$EXPECTED' on topic '$TOPIC'"
    exit 0
fi

echo "assert-kafka: FAILED — '$EXPECTED' not found on topic '$TOPIC' within ${TIMEOUT}s" >&2
echo "---- captured ----" >&2
echo "$OUT" | head -50 >&2
echo "------------------" >&2
exit 1
