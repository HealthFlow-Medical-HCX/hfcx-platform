# Data Protection Impact Assessment — HCX Egypt v1

**Status:** TEMPLATE — every section below is a TODO for HealthFlow legal/compliance to fill in. This file is scaffolding committed by the engineering remediation track (P0-8) to give the compliance lead a single source of truth to extend; the engineering track is **not** authoritative on any of the policy questions below.

**Owner:** HealthFlow Data Protection Officer (TODO — name)
**Reviewers:** HealthFlow Legal, HealthFlow Compliance, Engineering Lead, CISO
**Document version:** v1 (template)
**Last reviewed:** N/A (template)
**Next review due:** TODO — set per HealthFlow's DPIA-review cadence (recommend annual + on every material protocol change)

---

## 0. Why this document exists

Egyptian Personal Data Protection Law No. 151 of 2020 (PDPL) requires every controller of personal data to maintain a Data Protection Impact Assessment for processing operations likely to result in high risk to the rights and freedoms of natural persons. Health-claims processing — which is the entirety of HCX Egypt's purpose — is unambiguously high-risk under PDPL Article (3) and the Executive Regulations.

This document is the formal record. It exists to demonstrate compliance to the Personal Data Protection Centre, to the Financial Regulatory Authority (FRA) for insurance-data oversight, to the Ministry of Health and Population (MoHP) for clinical-data oversight, and to the Egyptian Drug Authority (EDA) where pharmacy claims are involved.

## 1. Description of the processing

> TODO — Compliance to fill in. Suggested structure:
>
> - Nature of the processing: claims-exchange-as-a-service (transport of FHIR Bundles between providers, payers, TPAs).
> - Scope: number of participants currently registered; expected throughput in claims/day at GA.
> - Context: HCX is Egypt's national exchange; participation will become mandatory for licensed payers and large providers per the timeline FRA + MoHP set.
> - Purposes: insurance eligibility verification, claim adjudication, payment notification, communication, predetermination, audit of all of the above.

## 2. Data inventory and classification

> TODO — Compliance + Engineering Lead. For each data class below, fill in: source, sensitivity tier (PHI / PII / commercial / operational), encryption-at-rest status, encryption-in-transit status, access-control mechanism, retention.
>
> Data classes to enumerate:
> - Patient PHI inside FHIR Bundles (zero-knowledge to the platform per [Decision 14](../docs/reviews/DECISION_14_ZERO_KNOWLEDGE_TRANSPORT.md))
> - Routing metadata (sender_code, recipient_code, api_call_id, correlation_id)
> - Audit log entries (who routed what, when)
> - Participant registry (organisation name, contact, banking, certificates, national_id of authorized signatory)
> - OTP / onboarding ephemera (email, mobile, OTP codes)
> - Operational telemetry (logs, metrics, traces)

## 3. Lawful bases

> TODO — Compliance to map each data class above to its PDPL Article 5 lawful basis. Most claims-exchange flows are likely Article 5(1)(b) "performance of a contract" (insurance) or 5(1)(c) "compliance with a legal obligation" (regulatory reporting), with explicit consent under 5(1)(a) for any processing that goes beyond the strict claims path.

## 4. Data subjects

> TODO — Compliance. Categories:
> - Patients (the source of the PHI)
> - Healthcare workers (incidentally identified in claims)
> - Participant organisation contacts (registered on the platform)
> - HealthFlow staff (access logs)

## 5. Data flows

> TODO — Engineering Lead to provide the canonical data-flow diagram; Compliance to certify it covers every PDPL-relevant flow.
>
> At minimum the diagram must show:
> - Sender → HealthFlow gateway (encrypted JWE in)
> - HealthFlow gateway → Kafka → Flink dispatcher → recipient (encrypted JWE out, never decrypted in-platform per Decision 14)
> - Audit log writes to ElasticSearch
> - Participant registry writes to Sunbird RC + Postgres
> - Backups (target locations, retention)
> - Cross-border data transfers (none expected; see §10)

## 6. Retention

> TODO — HealthFlow Compliance + Legal to set the actual numbers. The engineering track has implemented the enforcement mechanism (see `hcx-scheduler-jobs/.../PayloadRetentionScheduler.java`) but has intentionally NOT picked the values; the values are required environment variables that fail-closed if unset (`POSTGRES_RETENTION_DAYS`, `POSTGRES_RETENTION_GRACE_DAYS`).
>
> Suggested defaults to challenge or accept:
>
> | Data class | Suggested retention | Source of suggestion |
> |---|---|---|
> | Payload rows (encrypted FHIR shells) | 7 years | Egyptian medical-record retention norm |
> | Audit log (`hcx_audit` ES index) | 7 years hot/warm/cold/frozen, then delete | PDPL Article 9 audit-trail expectation |
> | OTP rows | 90 days | Onboarding flow only |
> | Participant registry inactive records | indefinite (legal-hold expected) | Regulatory reference data |
> | Operational logs (application stdout) | 30 days | Standard SRE practice |
> | Backups | 90 days rolling, 1 yearly snapshot | Disaster-recovery RPO |

## 7. Right of access, rectification, erasure

> TODO — Compliance to formalise the SLA for each right. Engineering scaffolding:
> - Right to erasure: `POST /v1/admin/participant/{participant_code}/erase` (see `AdminErasureController`). Currently scrubs registry status + onboarding tables; ES audit-index scrub via ILM policy.
> - Right of access: TODO — not yet implemented as a self-service endpoint.
> - Right to rectification: covered by existing `POST /v1/participant/update` for participant fields; patient-data rectification is the recipient's responsibility (zero-knowledge).

## 8. Security measures

> TODO — Compliance to certify each measure's adequacy. Engineering inventory (as of Phase 1–4 of the production-readiness remediation):
>
> - Transport: TLS 1.2+ on every external endpoint (TODO — verify after P1 hardening).
> - End-to-end JWE per Integration Guide §25–28 (P0-2; PR #7).
> - Zero-knowledge gateway per [Decision 14](../docs/reviews/DECISION_14_ZERO_KNOWLEDGE_TRANSPORT.md).
> - Replay protection at the gateway (P0-7; PR #4) via Redis NX EX.
> - SQL injection eliminated via PreparedStatement (P0-3; PR #4).
> - Egyptian field validation for National ID, IBAN, governorate, phone (P0-4b; PR #5).
> - Audit logging to ElasticSearch on every protocol action.
> - Key custody: Vault for dev/staging, PKCS#11 HSM for production (P0-2 scaffolding; production wiring TBD).
> - Replay window: 10 s timestamp + 70 s Redis idempotency cache (P0-7).
> - Default Postgres credentials removed (P0-3); env-var only.

## 9. Risk assessment

> TODO — Compliance to enumerate and score risks per PDPL Executive Regulation methodology. Suggested risk register starting points:
>
> - R1: Plaintext PHI leak via gateway compromise — *mitigated by zero-knowledge architecture (Decision 14)*
> - R2: Replay attack within timestamp window — *mitigated by P0-7 Redis NX*
> - R3: SQL injection via participant-controlled fields — *mitigated by P0-3 PreparedStatement migration*
> - R4: Cross-border data transfer (currently zero) — *mitigated by P0-1 data-residency removal of `ap-south-1`*
> - R5: Long-lived audit-log accumulation — *mitigated by ILM policy in phase3/monitoring*
> - R6: HSM/Vault unavailability blocks production traffic — *NOT yet mitigated; TODO design HA topology*
> - R7: Stale participant cert leads to dispatch failures — *mitigated by P0-2.f cert-expiry warnings*

## 10. Cross-border data transfers

> TODO — Compliance. After the P0-1 migration the platform has no `ap-south-1` (Mumbai) endpoints in defaults, but there's nothing structurally preventing a deployment from pointing at a non-Egyptian data centre.
>
> This section must:
> - Inventory every external endpoint the platform reaches (registry, Keycloak, Kafka, Postgres, Redis, ES, S3-equivalent, Vault, HSM, audit forwarders).
> - For each, declare the deployment region.
> - Where any region is outside Egypt, document the legal basis (PDPL Article 14: adequate protection, binding corporate rules, explicit consent, etc.).

## 11. Consultation

> TODO — Compliance to record consultation with: Personal Data Protection Centre, FRA (insurance regulator), MoHP, EDA, patient-representative bodies.

## 12. Sign-offs

> TODO — Document each sign-off with name, role, date.

---

## Engineering follow-ups surfaced by this template

The engineering team commits to:

1. Provide the canonical data-flow diagram referenced in §5.
2. Add a `executeUpdate(String, Object...)` overload to `PostgreSQLClient` that returns affected-row counts so the retention scheduler can report real numbers (currently reports `-1` placeholder).
3. Wire the audit-indexer into `AdminErasureController` so the right-to-erasure endpoint can also do an ES delete-by-query, not just rely on ILM.
4. Document the operational runbook for the right-to-erasure endpoint (who has the admin role, how the request is logged, how the receipt is stored).
5. Implement the right-of-access endpoint (§7).
