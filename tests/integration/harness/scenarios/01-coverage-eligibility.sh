#!/usr/bin/env bash
# =============================================================================
# §31 cycle 1: Coverage Eligibility
# =============================================================================
# Drives the gateway with happy path + 3 negative variants. Asserts HTTP 202
# on the happy path and non-202 on the failure cases. Optionally checks the
# audit topic for the correlation_id.
# =============================================================================
set -euo pipefail

SCENARIO_NAME="01-coverage-eligibility"
HERE=$(cd "$(dirname "$0")" && pwd)
HARNESS_DIR=$(cd "$HERE/.." && pwd)

# shellcheck source=../lib/_common.sh
source "$HARNESS_DIR/lib/_common.sh"

CYCLE_PATH="/v1/coverageeligibility/check"
FIXTURE="$HARNESS_DIR/fixtures/coverage-eligibility-request.json"

run_subscenario "happy-path"        post_jwe "$CYCLE_PATH" "$FIXTURE" 202
run_subscenario "invalid-payload"   post_raw "$CYCLE_PATH" '{"not":"a-bundle"}' "4xx"
run_subscenario "missing-header"    post_jwe_no_auth "$CYCLE_PATH" "$FIXTURE" "4xx"
run_subscenario "wrong-recipient"   post_jwe_wrong_recipient "$CYCLE_PATH" "$FIXTURE" "4xx"

scenario_done "$SCENARIO_NAME"
