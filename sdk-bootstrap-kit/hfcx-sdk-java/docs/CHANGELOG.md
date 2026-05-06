# Changelog

All notable changes to the HFCX SDK for Java are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added — Sprint J1 (bootstrap)

- Repository layout per Section 3 of `docs/agentic-delivery-prompt.md`.
- Maven multi-module build with three modules:
  - `hfcx-sdk-core` — JWE primitives, FHIR validator, Egyptian validators, governorate enum
  - `hfcx-sdk-client` — HfcxClient skeleton; full implementation lands in J3
  - `hfcx-sdk-examples` — runnable examples; J1 ships only the BootstrapSmoke
- `eg.gov.healthflow.hfcx.sdk.crypto.JWEHelper` — extracted from the platform's `hcx-core/hcx-common`. RSA-OAEP-256 + A256GCM, header-validation downgrade protection.
- `eg.gov.healthflow.hfcx.sdk.fhir.FhirValidationService` — extracted. HAPI-FHIR R4 with NPM IG package loading.
- `eg.gov.healthflow.hfcx.sdk.validators.EgyptianFieldValidator` plus the four sibling validators (NationalID, IBAN, Phone) — extracted.
- `eg.gov.healthflow.hfcx.sdk.enums.EgyptianGovernorate` — typed enum of all 27 governorates.
- `eg.gov.healthflow.hfcx.sdk.exceptions.ValidationException` — interim exception type used by the extracted validators; the full `ProtocolException` / `BusinessException` / `TechnicalException` hierarchy lands in J6.
- GitHub Actions: `test.yml` (mvn verify on every PR) and `publish.yml` (Sonatype OSSRH on tag push).
- "Block embedded private keys" CI guard mirroring the platform's V1 v1.4 guard.

### Notes

- Code in `hfcx-sdk-core` is **copied** from the platform repo, not moved.
  The platform retains its own copy until a follow-up post-1.0.0
  refactor flips `hcx-core/hcx-common` to depend on this artifact.
- Sonatype OSSRH publishing setup is `TODO(human)` per `BOOTSTRAP.md`.
- `fhir-ig/egyptian-ig.tgz` is synced from the platform release matching
  the SDK version: SDK 1.0.0 ships platform 1.0.0's IG.

## [1.0.0] — TBD (target ~6 weeks after Sprint J1)

(Filled in at release time per Sprint J7.)
