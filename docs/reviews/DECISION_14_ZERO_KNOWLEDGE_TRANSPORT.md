# Decision 14 — Zero-Knowledge Transport

**Status:** Accepted
**Owner:** Engineering + HealthFlow stakeholder
**Reference:** Integration Guide §26.1, Production-Readiness Plan §13.e, PR #7
**Reviewed in commit:** see git log for the commit introducing this document

---

## Context

When implementing P0-2 (JWE end-to-end encryption), an internal contradiction surfaced between two project artefacts:

- **Integration Guide §26.1** promises that "the platform cannot decrypt" payloads. The gateway is described as a routing fabric that handles encrypted JWE blobs but never holds the recipients' private keys.
- **Production-Readiness Remediation Plan §13.e (P0-2.e)** proposed that the gateway should decrypt at the API entry point, run FHIR validation against the plaintext, and re-encrypt before dispatching to the recipient.

These cannot both be true. Either the platform holds private keys for every recipient (so it can decrypt) or it doesn't (so it can't validate FHIR centrally).

## Options considered

| Option | Description | Trade-offs |
|---|---|---|
| **A. Zero-knowledge transport** | Gateway never decrypts. JWE blobs flow opaque from sender to recipient. FHIR validation is the recipient's responsibility, enforced via the published Implementation Guide. | Aligned with §26.1. Smaller attack surface. FHIR validation moves out of the platform. |
| **B. Decrypt-validate-re-encrypt** | Gateway holds private keys for all recipients and decrypts every payload to validate. | Enables central FHIR validation. Massive expansion of platform attack surface. Contradicts §26.1. |
| **C. Per-tenant opt-in to gateway-side validation** | Recipients individually opt in by delegating a gateway-readable key. Both modes coexist. | Most flexible. Most complex. Doubles the integration matrix. |

## Decision

**Option A — zero-knowledge transport.** The platform stays a routing fabric.

Rationale:
1. The integration guide is the published contract; integrators have already designed their threat models around §26.1's "platform cannot decrypt" promise. Reversing that without warning is a breach of contract.
2. The smaller attack surface is a meaningful production-readiness gain. A compromised gateway cannot leak any patient data because there is no plaintext to leak.
3. FHIR validation does not have to live in the gateway. It lives at the recipient SDK, enforced by the published HealthFlow Egyptian Implementation Guide (P0-5 work). Recipients run the validator after decryption.
4. Plan §13.e is in error and will be revised to reflect this decision.

## Consequences

**For code:**
- `PayloadUtils.removeSensitiveData` continues to drop the `encrypted_key` and `ciphertext` JWE segments before persistence. The Javadoc on that method now codifies the rationale.
- The `JWEHelper` class delivered in PR #7 is the **sender-side and recipient-side** primitive. The gateway never invokes `JWEHelper.decrypt()`; only the receiving HCX APIs do.
- `KeyCustodyClient.getRecipientPublicKey(participantCode)` is what the SENDER's SDK uses, fetching from the registry. The gateway does not need this method either.
- `KeyCustodyClient.getLocalPrivateKey()` is invoked only by an HCX recipient when handling its own inbound traffic. The gateway has no local private key for decryption — it only needs its private key for the outbound JWS signature on retry events.

**For the integration guide:**
- §26.1 stands as-is.
- §29 (FHIR R4) must be updated to clarify that FHIR validation runs at the recipient, not at the gateway. The recipient's HCX-API instance is responsible for invoking the HAPI-FHIR validator (P0-5 deliverable) against decrypted payloads.

**For the remediation plan:**
- §13.e (P0-2.e) "Wire into the dispatcher: decrypt the inbound JWE at the API entry point" is **superseded** by this decision. The dispatcher continues to forward opaque JWE compact serializations.
- §14 (P0-5 — FHIR R4 validation) is **scoped** to the recipient HCX-API instance, not the gateway. The HealthFlow Egyptian Implementation Guide is published as an `npm` package per §34 so all recipient SDKs can pull profiles directly.

**For the threat model:**
- The platform does not need to be PHI-cleared for plaintext access; the audit log stores only routing metadata + the stripped JWE shell.
- An HSM/KMS is still required at every HCX recipient (not the gateway) for private-key custody.
- The gateway's own private key (used for JWS signing of retry/notification events, not JWE) still belongs in Vault/HSM.

## What this document does NOT decide

- Whether the published integration guide should expose a future opt-in mode (Option C) at all. That is a product decision for a later release.
- Whether the platform should ever do **structural** validation on encrypted payloads (e.g. verifying the JWE compact serialization is well-formed, that the alg header is `RSA-OAEP-256`, etc.) — yes, those header-level checks are fine and don't break zero-knowledge.

## Profile-based enforcement (added in v1.3)

The hcx-apis module is multi-purpose. It serves both as the gateway's API
surface (where decryption MUST NOT happen) and as a reference recipient
HCX-API (where it MUST happen). To prevent the gateway from accidentally
decrypting, the deployment topology is now explicit via Spring profile:

- `gateway` profile (`application-gateway.yml`) — `crypto.jwe.enabled=false`,
  FHIR/Egyptian validation off. This is the production gateway role.
- `recipient-hcx-api` profile (`application-recipient-hcx-api.yml`) —
  `crypto.jwe.enabled=true`, FHIR/Egyptian validation on. This is the role
  a payer's or provider's own HCX-API instance takes when it terminates
  encrypted traffic from the gateway.

`DeploymentProfileGuard` (a Spring `ApplicationReadyEvent` listener) refuses
to start the application if the profile and the feature flags contradict
each other:

- `deployment.role=gateway` + any of (`crypto.jwe.enabled`,
  `fhir.validation.enabled`, `egyptian.validation.enabled`) = `true` →
  `IllegalStateException`. Loud failure prevents Decision 14 violation.
- `deployment.role=recipient-hcx-api` + any of those flags = `false` →
  `IllegalStateException`. A recipient that doesn't decrypt is a deployment
  bug.
- `deployment.role` unset → `IllegalStateException`. Forces operators to
  pick a topology explicitly rather than relying on default-on flags.

As an additional defense in depth, `JwePayloadProcessor` is annotated
`@ConditionalOnProperty(name = "crypto.jwe.enabled", havingValue = "true")`
so the bean does not even exist on the gateway profile. The
`@Autowired(required = false)` field in `BaseController` stays null and
the inbound payload is forwarded opaque.

## Linked

- PR #7: P0-2 scaffolding (the surfacing of this decision in the PR body).
- Integration Guide §25–28 (JWE), §29 (FHIR), §34 (SDK distribution).
- Production-Readiness Remediation Plan §13.e (now superseded).
- v1.3 Remediation Plan, Gap N1 (introduced the profile-based enforcement).
