# HFCX SDK for Java

Official Java SDK for the HealthFlow HFCX platform — Egypt's open
protocol for decentralised health-claims data exchange.

## Status

**Sprint J1 — bootstrap.** The SDK module layout and primitives are in
place. The high-level `HfcxClient` API, sender flows, and recipient
handler land in subsequent sprints (see `docs/CHANGELOG.md` and the
agentic delivery prompt at `docs/agentic-delivery-prompt.md`).

## Quickstart (target API after J3)

```java
HfcxClient client = HfcxClient.builder()
    .gatewayUrl("https://healthflow.gov.eg")
    .participantCode("myhospital@hcx-egypt")
    .privateKeyPath("/run/secrets/hfcx-private-key.pem")
    .keycloak(tokenClient)
    .build();

ClaimSubmissionResponse resp = client.submitClaim(
    ClaimSubmissionRequest.builder()
        .recipientCode("payerco@hcx-egypt")
        .claimBundle(myFhirBundle)
        .build());
System.out.println(resp.getCorrelationId() + " " + resp.getStatus());
```

(Today the builder doesn't exist yet — J3.)

## What ships in J1

- `eg.gov.healthflow.hfcx.sdk.crypto.JWEHelper` — RSA-OAEP-256 + A256GCM encrypt/decrypt
- `eg.gov.healthflow.hfcx.sdk.fhir.FhirValidationService` — HAPI-FHIR R4 validation, Egyptian-IG aware
- `eg.gov.healthflow.hfcx.sdk.validators.*` — National-ID, IBAN, phone, governorate validators + Bundle-walking orchestrator
- `eg.gov.healthflow.hfcx.sdk.enums.EgyptianGovernorate` — typed enum of all 27 governorates
- `eg.gov.healthflow.hfcx.sdk.exceptions.ValidationException` — temporary; the full exception hierarchy lands in J6

## Install

```xml
<dependency>
    <groupId>eg.gov.healthflow</groupId>
    <artifactId>hfcx-sdk-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

The SNAPSHOT publishes to Sonatype OSSRH after the operational setup in
`BOOTSTRAP.md` is complete. The 1.0.0 release lands on Maven Central
after Sprint J7.

## Architecture

This SDK implements the **participant** side of the HFCX protocol —
either as a sender (provider/payer pushing claims) or as a recipient
(payer/provider receiving claims). It is NOT for use on the HFCX
gateway; per Decision 14 of the platform repo, the gateway is
encryption-transparent and operates without an SDK.

## Versioning

Semver. SDK 1.x is compatible with platform 1.x. The bundled FHIR IG
version is pinned in `fhir-ig/PLATFORM_VERSION`.

## Documentation

- API reference (Javadoc): published to `https://javadoc.io` after each release.
- Cross-SDK parity table: `docs/CROSS_SDK_PARITY.md`.
- Examples: `hfcx-sdk-examples/` and `docs/examples/`.
- Integration Guide: see the platform repo `HealthFlow-Medical-HCX/hfcx-platform`.

## License

Apache 2.0. See `LICENSE`.
