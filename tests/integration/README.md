# §31 Protocol Integration Test Harness

Runnable end-to-end integration tests covering the four §31 protocol cycles:

1. **Coverage Eligibility** (`POST /v1/coverageeligibility/check`)
2. **Pre-Authorization** (`POST /v1/preauth/submit`)
3. **Claim** (`POST /v1/claim/submit`)
4. **Payment Notice** (`POST /v1/paymentnotice/request`)

The harness is purely additive — it does not modify any production source under
`hcx-apis/`, `api-gateway/`, `hcx-core/`, or `hcx-onboard/`.

## Running

```bash
bash tests/integration/harness/run.sh
```

Requires Docker + Docker Compose v2 on the host. The script:

1. Generates ephemeral RSA keypairs under `tests/integration/.keys/`.
2. Brings up `docker-compose-test.yml` with project name `hcx-it`.
3. Waits for every service to become healthy.
4. Runs the four scenario scripts under `harness/scenarios/`.
5. Tears the stack down (set `KEEP_STACK=1` to leave it running).

End-to-end runtime target: **under 10 minutes**.

The script is **idempotent** — running it twice in a row gives the same result.

### Degraded mode

If `hcx-api:egypt-fixed` or `hcx-api-gateway:egypt-fixed` images are not
present locally, the harness boots only the infra + mock services and
exercises the mock endpoints directly. This keeps it useful as a smoke
test even without the prod images. To get full gateway coverage, build the
images first:

```bash
docker build -t hcx-api:egypt-fixed         hcx-apis/
docker build -t hcx-api-gateway:egypt-fixed api-gateway/
```

## Layout

```
tests/integration/
  README.md
  docker-compose-test.yml      # postgres, redis, kafka, keycloak, hcx-api,
                               # api-gateway, mock-provider, mock-payer
  harness/
    run.sh                     # entrypoint
    wait-for-healthy.sh        # generic readiness probe
    scenarios/
      01-coverage-eligibility.sh
      02-preauth.sh
      03-claim.sh
      04-payment-notice.sh
    fixtures/                  # cleartext FHIR Bundles
    lib/
      _common.sh               # POST helpers, status assertions
      jwe-encrypt.sh           # openssl-based RSA-OAEP-256 + A256GCM
      assert-kafka.sh          # consume-and-grep helper
  mock-provider/               # Spring Boot 2.5.5 standalone module
  mock-payer/                  # Spring Boot 2.5.5 standalone module
```

Mock services are **not** in the root Maven reactor — they're built only
when their Dockerfiles are referenced from `docker-compose-test.yml`.

## Sub-scenarios per cycle

Each of the four scenario scripts exercises four sub-scenarios:

| sub-scenario       | what it sends                                   | expected |
| ------------------ | ----------------------------------------------- | -------- |
| `happy-path`       | JWE-encrypted Bundle + all §31 headers          | 202      |
| `invalid-payload`  | Plain JSON, no JWE                              | 4xx      |
| `missing-header`   | Valid JWE but no `x-hcx-*` headers              | 4xx      |
| `wrong-recipient`  | JWE encrypted to a key the recipient doesn't own | 4xx      |

The happy path also tries to assert the correlation_id appears on the
`local.hcx.audit` Kafka topic. This is best-effort — a missing topic is
logged but does not fail the scenario, since topic auto-creation may
race the assertion on a cold-started broker.

## Mock services

`mock-provider` (port 8090) and `mock-payer` (port 8091) are minimal
Spring Boot 2.5.5 apps. They:

- Implement both forward (`/check`, `/submit`, `/request`) and reverse
  (`/on_*`) endpoints for all four cycles.
- Echo back HTTP 202 with `{timestamp, correlation_id, api_call_id, result}`.
- Log every received request for harness debugging.

They do **not** validate FHIR Bundles or perform real JWE crypto today —
see "Known limitations" below.

## CI

`.github/workflows/integration-test.yml` runs this harness on PRs and main
pushes when paths under `tests/integration/`, `hcx-apis/`, or
`api-gateway/` change. 15-minute job timeout, logs uploaded as artifact
on failure.

## Known limitations

These were deferred to keep Gap 17 a clean, single landable change. Each is
marked `TODO(gap-17-followup)` in the relevant file.

- **Mock services do not perform real JWE decrypt** — they accept any JSON
  body. Decision 14 says the gateway is JWE-transparent, so end-to-end
  crypto is the *participant's* responsibility; today our mocks stub it.
  Followup: replace the controller bodies with a Nimbus-JOSE-based decrypt.
- **`jwe-encrypt.sh` is structurally valid but not interoperable with
  Nimbus-JOSE.** It produces a 5-segment compact JWE, sufficient for the
  gateway's structural checks, but the AES-GCM tag handling depends on the
  host's openssl version. Followup: replace with a small `nimbus-jwe-cli`
  helper jar invoked by the script.
- **No FHIR validation in fixtures.** Bundles are minimally well-formed
  but not IG-compliant. Followup: hook in `hl7-fhir-validator-cli`.
- **Audit assertion is best-effort.** First run after `compose up` may not
  see the topic in time; the harness logs but doesn't fail.
- **Keycloak runs in dev/H2 mode** with the default `master` realm,
  *not* the `hcx-egypt` realm used by prod. Token issuance is therefore
  not exercised end-to-end.
- **Elasticsearch is not provisioned.** Aspects in `hcx-apis` that
  hard-depend on ES are skipped via `SPRING_PROFILES_ACTIVE=dev`. Followup:
  add an ES service (or stub via `OS_HEAP=256m` single-node).
- **Registry lookups are stubbed via `REGISTRY_BASE_PATH=http://localhost:0`.**
  Scenarios that require participant resolution will fail in full mode
  until either a mock registry is added or the gateway tolerates the
  lookup error. Followup: add a tiny `mock-registry` Spring Boot app.
- **Sub-scenario response codes are tolerant** (`4xx` rather than exact
  400/401). Once the gateway's error envelope is finalized for §31 errors,
  tighten these to exact codes.

## Local debugging

```bash
# Keep the stack up after the run:
KEEP_STACK=1 bash tests/integration/harness/run.sh

# Tail kafka:
docker exec -it hcx-test-kafka \
    kafka-console-consumer --bootstrap-server localhost:29092 \
    --topic local.hcx.audit --from-beginning

# Hit a mock directly:
docker exec hcx-test-kafka curl -fsS \
    http://mock-provider:8090/actuator/health
```
