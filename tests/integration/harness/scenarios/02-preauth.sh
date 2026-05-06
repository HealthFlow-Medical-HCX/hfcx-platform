#!/usr/bin/env bash
# §31 cycle 2: Pre-Authorization
set -euo pipefail

SCENARIO_NAME="02-preauth"
HERE=$(cd "$(dirname "$0")" && pwd)
HARNESS_DIR=$(cd "$HERE/.." && pwd)

# shellcheck source=../lib/_common.sh
source "$HARNESS_DIR/lib/_common.sh"

CYCLE_PATH="/v1/preauth/submit"
FIXTURE="$HARNESS_DIR/fixtures/preauth-request.json"

run_subscenario "happy-path"        post_jwe "$CYCLE_PATH" "$FIXTURE" 202
run_subscenario "invalid-payload"   post_raw "$CYCLE_PATH" '{"not":"a-bundle"}' "4xx"
run_subscenario "missing-header"    post_jwe_no_auth "$CYCLE_PATH" "$FIXTURE" "4xx"
run_subscenario "wrong-recipient"   post_jwe_wrong_recipient "$CYCLE_PATH" "$FIXTURE" "4xx"

scenario_done "$SCENARIO_NAME"
