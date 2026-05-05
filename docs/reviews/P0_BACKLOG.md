# P0 Production-Readiness Backlog

Source: Production-Readiness Assessment v1.1 (Section 3.1) + v1.2 corrections patch.

GitHub Issues are disabled on this repository, so these P0 items live here as a tracked backlog. When Issues are enabled, migrate each section below into a separate issue with the suggested labels.

**Sequencing note:** P0-1 (data residency), P0-3 (SQL injection), P0-4 (registry schema), P0-4b (validator wiring), P0-7 (replay), and P0-8 (DPIA documentation) can run in parallel once owners are assigned. P0-2 (JWE) and P0-5 (FHIR) are the largest and should each own a dedicated track. P0-6 (API version + error codes) should land last so it can encode the final shape of the other changes.

---

## P0-1 — Complete data-residency migration off `ap-south-1` (Mumbai) to Egypt-resident infrastructure

**Labels:** `production-readiness`, `P0`, `infrastructure`, `compliance`

### Background

The Egyptian Personal Data Protection Law (Law 151/2020) and the HealthFlow Integration Guide §28.5 require data residency in Egypt. Five hardcoded `ap-south-1` ELB URLs remain across `application.yml` files. Two of those — the registry `basePath` and the JWT JWK URL in `api-gateway/src/main/resources/application.yaml` (lines 70 and 61) — are not configuration drift but **active runtime dependencies on India infrastructure**: the JWK URL is fetched on every JWK cache refresh, so every authenticated request to the gateway depends on the Mumbai ELB being reachable.

### Files containing `ap-south-1` references

- `hcx-apis/src/main/resources/application.yml:24` — registry `basePath`
- `api-gateway/src/main/resources/application.yaml:61` — JWT JWK URL
- `api-gateway/src/main/resources/application.yaml:70` — registry `basePath`
- `api-gateway/src/test/resources/application-test.yml:54` — JWT JWK URL (test config)
- `hcx-scheduler-jobs/common-scheduler-job/src/main/resources/application.yml:20` — registry `basePath`

Plus `aws-region: ap-south-1` in 6 GitHub Actions workflows under `.github/workflows/*-docker-build.yml`.

### Scope

1. Stand up Egypt-resident infrastructure (recommend AWS `me-south-1` Bahrain, AWS `me-central-1` UAE, or an Egyptian government cloud).
2. Repoint the five `application.yml` URLs at the new endpoints.
3. Repoint the GitHub Actions `aws-region` to the new region for image push and any deployment steps.
4. Externalize the URLs via env vars (already partially done via `${...}` substitution); guarantee no `ap-south-1` literal survives as a fallback.
5. Document the cutover in the deployment guide.

### Acceptance criteria

- [ ] `grep -rn "ap-south-1" --include="*.yml" --include="*.yaml" .` returns 0 hits.
- [ ] `grep -rn "elb.amazonaws.com" --include="*.yml" --include="*.yaml" .` matches only Egypt-region hosts (or none, if env-var only).
- [ ] Fresh deployment from `main` has no runtime dependency on India infrastructure.
- [ ] DPIA section of the deployment guide updated.

### Linked

v1.2 Sections 2.1, 2.13. Correction 8 in `PRODUCTION_READINESS_v1.2_corrections.md`.

---

## P0-2 — Implement RSA-OAEP-256 + A256GCM JWE encryption end-to-end with HSM/KMS-backed keys

**Labels:** `production-readiness`, `P0`, `security`, `cryptography`

### Background

The HealthFlow Integration Guide §25–28 commits the platform to end-to-end **RSA-OAEP-256 + A256GCM JWE** encryption with the recipient's public key, with the platform unable to decrypt, and with private keys stored in an HSM, secure key vault, or KMS.

Reality across all branches (validated against `deployment-fixes-dec-2025`):

- No JWE library on the classpath. `git grep "JWEDecrypter\|JWEEncrypter\|new JWEObject"` returns 0 on every branch.
- `nimbus-jose-jwt` and `jose4j` are not declared in any `pom.xml`.
- `Request.getHeadersFromPayload()` base64-decodes only the protected JOSE header for routing; the encrypted-key segment, IV, ciphertext, and authentication tag are stored as opaque text.
- `PayloadUtils.removeSensitiveData` literally drops the encrypted_key and ciphertext segments before persisting to Postgres — the platform stores a stub of the JWE, not the real encrypted payload.
- The only crypto code is `JWTUtils.isValidSignature` — JWS verification with `SHA256withRSA` for notifications. Not JWE.
- HashiCorp Vault scaffolding under `phase3/security/` stores secrets but does not implement JWE.

### Scope

1. Add `nimbus-jose-jwt` (or `jose4j`) to `hcx-core/hcx-common`.
2. Implement JWE encrypt at the sender SDK using the recipient's public key fetched from the registry (`encryption_cert` URI).
3. Implement JWE decrypt at the recipient services with a private key fetched from KMS/Vault at startup.
4. Integrate AWS KMS or HashiCorp Vault Transit secrets engine for private-key custody — keys never leave the HSM/KMS boundary in plaintext.
5. Stop dropping `encrypted_key` and `ciphertext` segments in `PayloadUtils.removeSensitiveData`.
6. Add an automated certificate-expiry scheduler against the registry's existing `encryption_cert_expiry` and `signing_cert_path_expiry` fields with 30/14/7/1-day notifications (overlaps with P1-8).

### Acceptance criteria

- [ ] `git grep "nimbus-jose-jwt\|jose4j"` finds the dep declared in at least one `pom.xml`.
- [ ] Round-trip JWE encrypt-and-decrypt unit tests for RSA-OAEP-256 + A256GCM pass.
- [ ] Integration test: provider SDK encrypts a payload to a payer recipient → gateway routes without decrypting → payer SDK decrypts successfully.
- [ ] Negative test: gateway cannot decrypt the payload (proves zero-knowledge).
- [ ] Threat model documenting key custody and rotation procedure.

### Linked

v1.2 Section 2.3.

---

## P0-3 — Replace `String.format`-built SQL with `PreparedStatement`; remove default Postgres credentials

**Labels:** `production-readiness`, `P0`, `security`, `persistence`, `sql-injection`

### Background

Sixteen SQL queries across the API and onboarding tiers are constructed via `String.format` string substitution rather than parameter-bound `PreparedStatement`. Any participant-controlled value reaching these formatters without prior sanitization is a SQL-injection vector. Confirmed unchanged across every branch reviewed.

### Hits (validated on `deployment-fixes-dec-2025`)

```
hcx-apis/src/main/java/org/healthflow/hcx/handlers/EventHandler.java:55          INSERT INTO payload …
hcx-apis/src/main/java/org/healthflow/hcx/service/ParticipantService.java:133    SELECT status FROM …
hcx-apis/src/main/java/org/healthflow/hcx/service/ParticipantService.java:145    SELECT * FROM …
hcx-apis/src/main/java/org/healthflow/hcx/service/NotificationService.java:118   SELECT subscription_id FROM …
hcx-apis/src/main/java/org/healthflow/hcx/service/NotificationService.java:209   SELECT %s FROM …
hcx-apis/src/main/java/org/healthflow/hcx/service/NotificationService.java:217   UPDATE … RETURNING …
hcx-onboard/src/main/java/org/healthflow/hcx/services/ParticipantService.java:108,113,138,163,182,205,244,270,277,302
```

Plus `application.yml` exposes `postgres.subscription.insertQuery` and `postgres.subscription.subscriptionQuery` as parameterized SQL templates (with `%s` placeholders), then executed via `postgreSQLClient.execute(query)` — also injection-prone.

### Default credentials

`hcx-apis/src/main/resources/application.yml:33–35`:

```yaml
postgres:
  url: jdbc:postgresql://localhost:5432/postgres
  user: postgres
  password: postgres
```

### Scope

1. Convert every `String.format` SQL call to `PreparedStatement` with parameter binding.
2. Replace the `application.yml` SQL-template pattern with parameterized queries built in code.
3. Remove default `user: postgres / password: postgres`; require credentials via env var or secret manager (Vault is already scaffolded under `phase3/security/`).
4. Audit any other `postgreSQLClient.execute(String)` call sites for similar patterns.

### Acceptance criteria

- [ ] `grep -rn "String.format.*INSERT\|String.format.*UPDATE\|String.format.*SELECT" hcx-apis hcx-core api-gateway hcx-onboard --include="*.java"` returns 0 hits.
- [ ] No SQL templates with `%s` placeholders survive in `application.yml`.
- [ ] Postgres credentials have no defaults; startup fails fast if env vars are missing.
- [ ] SQL-injection regression test (e.g. `'; DROP TABLE payload; --` as a `participant_code`) is repelled at every endpoint.

### Linked

v1.2 Section 2.12.

---

## P0-4 — Re-localize `Organisation` registry schema for Egypt

**Labels:** `production-readiness`, `P0`, `registry`, `localization`

### Background

`hcx-registry/schemas/Organisation.json` is byte-identical across `main`, `phase1-foundation-setup`, and `deployment-fixes-dec-2025`. The schema is still entirely Indian:

- **Role enum** (line 38): `provider, payor, agency.tpa, agency.regulator, research, member.isnp, agency.sponsor, HIE/HIO.HCX`
  - `member.isnp` is an Indian PFRDA construct.
  - `HIE/HIO.HCX` is the Swasth India HIE label.
  - Missing: `agency.fra`, `agency.mohp`, `agency.eha`, `agency.bsp`.
- **Address sub-schema** (lines 151–162): `plot, street, landmark, village, district, state, pincode` — `village` and `pincode` are Indian.
  - Missing: governorate, markaz, postal-code (Egyptian).
- **Payment details** (lines 99–125): requires either `upi_id` (Indian UPI) or `account_number + ifsc_code` (Indian IFSC).
  - Missing: Egyptian IBAN, BIC, Meeza wallet identifiers.

### Scope

1. Replace the role enum with the Egyptian taxonomy: `provider, payer, tpa, bsp, regulator.fra, regulator.mohp, regulator.eha, research`.
2. Replace the address sub-schema fields: `street, building_number, governorate, markaz, postal_code`.
3. Replace `payment_details`: require Egyptian IBAN (29-char mod-97) + BIC for inter-bank, or Meeza wallet ID for retail.
4. Add a `national_id` field to the `User` schema with the 14-digit Egyptian National ID format.
5. Add JSON Schema validation for each new field (the Egyptian validators in `hcx-core/hcx-common/src/main/java/org/healthflow/hcx/utils/validators/` are ready — see P0-4b for wiring them).
6. Document the breaking schema change and provide a one-time migration script for existing registry entries.

### Acceptance criteria

- [ ] `grep -rn "upi_id\|ifsc_code\|pincode\|village\|district\|member.isnp\|HIE/HIO.HCX" hcx-registry/` returns 0 hits.
- [ ] `grep -rn "governorate\|markaz\|iban" hcx-registry/` returns multiple hits.
- [ ] Schema validation tests cover each new Egyptian field's positive and negative cases.
- [ ] Registry migration script committed and dry-run-tested.

### Linked

v1.2 Section 2.1. Sub-task: P0-4b.

---

## P0-4b — Wire Egyptian validators into participant CRUD and registry-schema validation paths

**Labels:** `production-readiness`, `P0`, `registry`, `localization`

### Background

Four Egyptian validators were committed into the source tree on `phase1-foundation-setup` and now live on `main`:

- `hcx-core/hcx-common/src/main/java/org/healthflow/hcx/enums/EgyptianGovernorate.java` (all 27 governorates)
- `hcx-core/hcx-common/src/main/java/org/healthflow/hcx/utils/validators/EgyptianNationalIDValidator.java` (14-digit format, governorate extraction, Luhn check)
- `hcx-core/hcx-common/src/main/java/org/healthflow/hcx/utils/validators/EgyptianIBANValidator.java` (29-character mod-97 validation)
- `hcx-core/hcx-common/src/main/java/org/healthflow/hcx/utils/validators/EgyptianPhoneValidator.java`

A repository-wide grep confirms none of them are imported anywhere in the protocol path:

```bash
grep -rn "EgyptianNationalIDValidator\|EgyptianIBANValidator\|EgyptianGovernorate\|EgyptianPhoneValidator" \
  hcx-apis/src/main hcx-core/hcx-common/src/main api-gateway/src/main hcx-onboard/src/main \
  | grep -v "validators/Egyptian\|enums/Egyptian"
# returns 0 matches — validators are dead code
```

### Scope

1. Wire `EgyptianNationalIDValidator` into the `ParticipantController` create/update endpoints in `hcx-onboard` and `hcx-apis`.
2. Wire `EgyptianIBANValidator` into the `payment_details` validation path (depends on P0-4 schema migration).
3. Wire `EgyptianPhoneValidator` into the participant `primary_mobile` and `phone` fields.
4. Wire `EgyptianGovernorate` enum into address validation (depends on P0-4 schema migration).
5. Add the validators to the JSON Schema validation step (custom keyword extensions on the JSON Schema validator).
6. Add unit tests for each wiring point that prove a malformed value is rejected at the API boundary, not the database.

### Acceptance criteria

- [ ] `grep -rn "EgyptianNationalIDValidator" hcx-apis/src/main hcx-onboard/src/main` returns hits in service or controller code.
- [ ] Same for the other three validators.
- [ ] API contract tests for participant create/update reject invalid Egyptian National ID, IBAN, phone, and governorate values with HTTP 400 and a documented error code.

### Depends on

P0-4.

### Linked

v1.2 Section 2.1.

---

## P0-5 — Implement FHIR R4 validation and ship a HealthFlow Egyptian FHIR Implementation Guide

**Labels:** `production-readiness`, `P0`, `fhir`, `validation`

### Background

The HealthFlow Integration Guide §29 commits the platform to FHIR R4 (4.0.1) with HealthFlow-defined profiles, value sets, and Egyptian extensions. ICD-10, CPT, HCPCS, RxNorm, or local Egyptian drug codes are listed as the standard vocabularies. The protocol resources `Patient`, `Practitioner`, `Organization`, `Coverage`, `CoverageEligibilityRequest`, `CoverageEligibilityResponse`, `Claim`, `ClaimResponse`, `Communication`, `PaymentNotice`, and `Bundle` are enumerated.

Reality across all branches:

- No FHIR library on the classpath. `git grep "hapi-fhir\|FhirContext\|FhirValidator"` returns 0.
- Controllers accept `Map<String, Object>` and immediately push the body to Kafka with no parsing, no profile validation, no value-set lookup.
- The Flink dispatcher in `BaseDispatcherFunction.processElement` treats the payload as an opaque JSON blob.
- `ClaimsProcessFunction.validate()` is a stub: returns `new ValidationResult(true, null)` with `// TODO: Add domain specific validations`.
- No HealthFlow Egyptian Implementation Guide artifacts exist.

### Scope

This is the largest P0 item. Likely needs its own epic with sub-issues. Outline:

1. **FHIR library:** add HAPI-FHIR (`ca.uhn.hapi.fhir:hapi-fhir-base` + `hapi-fhir-structures-r4`) to `hcx-core/hcx-common`.
2. **Egyptian Implementation Guide:** publish StructureDefinitions, ValueSets, CodeSystems, and Bundle profiles for each protocol resource. Include Egyptian extensions (National ID, governorate, EDA drug codes, etc.).
3. **Validation step:** insert a FHIR `FhirValidator` step in each per-workflow Flink job before dispatch. Replace the empty `validate()` stubs.
4. **Negative tests:** confirm invalid Bundle / Claim / CoverageEligibilityRequest payloads are rejected with structured error responses matching the documented `ERR-P-xxx` taxonomy (overlaps with P0-6).
5. **IG package:** publish the IG as an `npm` package (`@healthflow/fhir-ig-egypt` per the integration guide §34) so integrators can pull profiles directly.

### Acceptance criteria

- [ ] `git grep "hapi-fhir"` finds the dep in at least one `pom.xml`.
- [ ] `ClaimsProcessFunction.validate()` and equivalents in every per-workflow job perform real FHIR validation.
- [ ] A directory `hcx-fhir-ig-egypt/` (or similar) contains StructureDefinitions for at least Patient, Coverage, CoverageEligibilityRequest/Response, Claim, ClaimResponse.
- [ ] Negative test fixtures for malformed Bundles produce structured error responses and do not reach Kafka.

### Linked

v1.2 Section 2.2.

---

## P0-6 — Align gateway external API version and error-code taxonomy with the integration guide

**Labels:** `production-readiness`, `P0`, `gateway`, `documentation`

### Background

The integration guide §24.2 advertises base path `https://healthflow.gov.eg/api/v1/{resource}/{action}`. The gateway currently exposes external versions `v0.7` and `v0.8` only and rewrites them to the internal `v1` path. There is no public `v1` route, so an integrator following the guide gets `404` on the first request.

The integration guide §24.6 documents error codes `ERR-P-xxx`, `ERR-B-xxx`, `ERR-T-xxx`. The internal `ErrorCodes` enum uses unrelated names like `ERR_INVALID_PAYLOAD`, `ERR_INVALID_SIGNATURE`, `ERR_ACCESS_DENIED`. They do not match.

### Evidence

- `api-gateway/src/main/resources/application.yaml:9–11`:
  ```yaml
  external07: ${version_external_07:v0.7}
  external08: ${version_external_08:v0.8}
  internal:   ${version_internal:v1}
  ```
- `hcx-core/hcx-common/src/main/java/org/healthflow/hcx/common/ErrorCodes.java` — uses `ERR_*` snake-case enum, not the documented `ERR-P/B/T-xxx` taxonomy.

### Scope

1. **API version**: pick one direction:
   - **(a)** add `v1` to the external version set and route it to the same internal `v1` paths the existing rewrites point to; or
   - **(b)** rewrite the integration guide to advertise `v0.8` as the supported external version.
   - Recommended: (a) — clients can always fall back to `v0.8` during transition.
2. **Error codes**: align both directions:
   - Map the internal `ErrorCodes` enum values to the documented `ERR-P-xxx`, `ERR-B-xxx`, `ERR-T-xxx` codes; or rewrite the documentation to match the internal codes.
   - Recommended: rewrite the enum, since external-facing codes should be stable identifiers integrators can rely on.
3. **Header naming**: the constant in `Constants.java:129` is `x-hcx-sender_code` (underscore between `sender` and `code`). The integration guide uses both `X-HCX-Sender-Code` and `X-HCX-Sender_Code`. Standardize on the underscore form in both places.
4. **/auth/token**: the integration guide §14.3 documents `POST /auth/token` but it is served by Keycloak, not by this codebase. Document that explicitly.

### Acceptance criteria

- [ ] Hitting `/api/v1/coverageeligibility/check` returns 200/202, not 404.
- [ ] `git grep "ERR_INVALID_"` returns 0 (or at least: every error-response payload to the integrator carries the `ERR-P/B/T-xxx` form).
- [ ] Documentation diff posted alongside the code change.

### Linked

v1.2 Section 2.4.

---

## P0-7 — Implement gateway-side replay protection / API call-ID idempotency in Redis

**Labels:** `production-readiness`, `P0`, `gateway`, `security`

### Background

The integration guide §26.3 requires the gateway to verify that each `X-HCX-API-Call-ID` is unique (a duplicate is treated as a replay).

Reality: the only defense is `timestamp.range = 10000` in `api-gateway/src/main/resources/application.yaml:51` — a 10-second window. A captured request can be replayed within that window with no detection.

### Scope

1. On every accepted request, record `X-HCX-API-Call-ID` in Redis with TTL = `timestamp.range` + safety margin (e.g. 60 s).
2. On request entry in the gateway filter chain (after structural header validation, before forwarding), check Redis: if the call-ID is already present, reject with HTTP 409 / `ERR-P-DUPLICATE` (or whatever the aligned taxonomy from P0-6 dictates).
3. Make the TTL and Redis key prefix configurable (`replay.protection.ttl`, `replay.protection.key_prefix`).
4. Add metrics: `replay.detected.count` and `replay.cache.hits/misses`.

### Acceptance criteria

- [ ] Duplicate `X-HCX-API-Call-ID` within the TTL window is rejected with 409.
- [ ] Same call-ID after the TTL window is accepted (allows correct re-tries beyond the window).
- [ ] Redis outage gracefully degrades to log-and-allow OR fail-closed — make the choice explicit and documented.
- [ ] Load test: 1k req/s with random call-IDs shows < 5 ms p99 overhead from the check.

### Linked

v1.2 Section 2.5.

---

## P0-8 — Data-retention, right-to-erasure, and PDPL Data Protection Impact Assessment

**Labels:** `production-readiness`, `P0`, `compliance`, `privacy`

### Background

Egyptian Personal Data Protection Law (Law 151/2020) and integration guide §28.5 imply the platform must support data retention limits, right-to-erasure, and a documented Data Protection Impact Assessment.

Reality:

- The Postgres `payload` table has no TTL and no purge job — patient data accumulates indefinitely.
- The `hcx_audit` ElasticSearch index has no Index Lifecycle Management (ILM) policy.
- There is no participant-data-purge utility (right to erasure).
- No DPIA document exists in the repo.
- Encryption-at-rest is documented as a recommendation but no Postgres TDE / encrypted EBS / S3 SSE-KMS configuration ships with the repo (overlaps with P1-7).

### Scope

1. **Retention policy**:
   - Define data classification (PHI, PII, audit, operational) and a retention period for each.
   - Add a scheduled job in `hcx-scheduler-jobs` that purges `payload` rows older than the configured retention.
   - Configure ILM on the `hcx_audit` ES index (rollover at size/age, freeze, then delete).
2. **Right to erasure**:
   - Add a participant-purge utility callable by an admin endpoint or a CLI in `hcx-onboard`. It should redact all rows tied to a `participant_code` (payload, audit, subscription) per the legal-hold rules documented in the DPIA.
   - Audit-log the purge action itself (so the act of erasure is traceable even if the data is gone).
3. **DPIA**: produce a Data Protection Impact Assessment under `docs/compliance/PDPL_DPIA.md` covering data flows, lawful basis, retention, transfers (cross-border concerns from the surviving `ap-south-1` URLs — depends on P0-1), and risk-mitigation measures.

### Acceptance criteria

- [ ] Retention scheduler runs nightly and reports rows purged.
- [ ] Erasure utility tested end-to-end on a test participant.
- [ ] DPIA committed and reviewed by HealthFlow legal/compliance.

### Linked

v1.2 Section 2.13.
