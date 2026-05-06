# HFCX SDK Delivery Plan

**Status:** Active commitment as of v1.4 corrective sprint (May 2026).
**Owner of overall plan:** `<ENGINEERING-LEAD>` (filled in at merge time)
**Driver:** Integration Guide §34 promises SDKs; Decision 14 architecturally
requires them — recipients terminate JWE in their own SDK; the gateway
forwards opaque blobs.

## Reviewer instructions before merge

This document lands with placeholder slots. **Before merging this PR**, the
engineering lead must:

1. Fill in every `<NAME>` slot below with a real owner.
2. Fill in every `<DATE>` slot with a real target. Suggested dates appear
   alongside as "recommend ~N weeks out".
3. Open the four GitHub tracking issues listed at the bottom of this file
   and replace `<LINK>` with the issue URLs.

After all placeholders are replaced, the merge can proceed. CI does not
verify the slots — that's the human reviewer's job.

## Why SDKs unblock the network

Without SDKs, no integrator can join the network. This blocks GA, not just
pilot. A pilot is technically possible because the two pilot partners can
roll their own JWE+FHIR+Egyptian-validator stack (or the platform team can
ship them a thin Java reference implementation). But a publicly-launched
network with the documented contract requires SDKs.

The cross-validation that surfaced this gap (Manus second-opinion review,
May 2026) found:

- No `sdk/` directory in this repo.
- No `eg.gov.healthflow` packages on Maven Central.
- No `@healthflow/sdk` on npm.
- No `healthflow-sdk` on PyPI.
- No `hfcx-sdk-{python,java,javascript}` repos on the GitHub org.

The v1.2/v1.3 prompts treated SDKs as "out of scope, sibling repos." That
deferral is now closed: SDK delivery is a tracked, owned, dated commitment.

## Sequencing rationale

**Java first** because:
- The platform itself is Java; `JWEHelper`, `FhirValidationService`, and
  `EgyptianFieldValidator` can be extracted into a shared library that both
  the platform and the SDK depend on. No translation cost.
- The largest-volume Egyptian payer integrators run Java backends.

**Python second** because:
- It is the second-largest integrator audience (analytics tooling, smaller
  providers running Django/Flask billing systems).
- It has the cheapest crypto + FHIR ecosystem after Java
  (`cryptography`, `fhir.resources`).

**.NET third** because:
- Smaller integrator audience but key for some hospital-system vendors.
- Crypto + FHIR libraries are stable (`System.Security.Cryptography`,
  `Hl7.Fhir.R4`).

**JavaScript fourth** because:
- Beneficiary Service Platforms (Integration Guide §4.5) use JavaScript.
- BSPs are read-only on claim status, so they need decrypt + FHIR-parse
  but not the full encrypt path. Smaller surface area.

## Per-SDK delivery

### Java SDK (`hfcx-sdk-java`)

- **Owner:** `<NAME>`
- **Repo:** `HealthFlow-Medical-HCX/hfcx-sdk-java` (to be created)
- **Approach:** Extract `JWEHelper`, `FhirValidationService`,
  `EgyptianFieldValidator`, and the Egyptian-IG package into a new module
  `hcx-core/hcx-sdk` published to Maven Central. The platform's `hcx-apis`
  depends on the same artifact. SDK consumers depend on it directly.
- **API surface:** `HfcxClient` with `checkEligibility`, `submitPreauth`,
  `submitClaim`, `sendCommunication`, plus `decryptIncoming` for the
  recipient side.
- **Delivery target:** `<DATE — recommend ~6 weeks out>`
- **Tracking issue:** `<LINK>`

### Python SDK (`hfcx-sdk-python`)

- **Owner:** `<NAME>`
- **Repo:** `HealthFlow-Medical-HCX/hfcx-sdk-python` (to be created)
- **Approach:** Re-implement using `cryptography` for JWE,
  `fhir.resources` for R4 model + validation. Bundle a Python copy of the
  Egyptian IG.
- **API surface:** mirror Java's; idiomatic Python (snake_case, dataclasses).
- **Delivery target:** `<DATE — recommend ~10 weeks out>`
- **Tracking issue:** `<LINK>`

### .NET SDK (`hfcx-sdk-dotnet`)

- **Owner:** `<NAME, can be unassigned for now>`
- **Repo:** `HealthFlow-Medical-HCX/hfcx-sdk-dotnet` (to be created)
- **Approach:** `System.Security.Cryptography` for JWE, `Hl7.Fhir.R4` for
  FHIR. NuGet for distribution.
- **Delivery target:** `<DATE — post-GA>`
- **Tracking issue:** `<LINK>`

### JavaScript SDK (`hfcx-sdk-javascript`)

- **Owner:** `<NAME, can be unassigned for now>`
- **Repo:** `HealthFlow-Medical-HCX/hfcx-sdk-javascript` (to be created)
- **Approach:** WebCrypto for JWE-decrypt (encryption deferred — BSPs are
  read-only on claim status). `fhir.js` or a minimal R4 model for parsing.
  Published to npm under `@healthflow/sdk`.
- **Delivery target:** `<DATE — post-GA>`
- **Tracking issue:** `<LINK>`

## Pilot strategy in the absence of SDKs

The two pilot partners scheduled for the next sprint will receive a
"Java SDK pre-release" — the extracted `hcx-core/hcx-sdk` module published
as a `1.0.0-SNAPSHOT` to a private Maven repository. They build against it,
report bugs, the SDK feedback shapes the 1.0.0 release. This unblocks pilot
without requiring all four SDKs to ship first.

## GA gate

GA launch requires:

- `hfcx-sdk-java >= 1.0.0` on Maven Central, AND
- `hfcx-sdk-python >= 1.0.0` on PyPI.

`.NET` and `JavaScript` SDKs may follow within 90 days of GA.

A "1.0.0 release" for an SDK means:

- Round-trips a §31 cycle (coverage-eligibility / preauth / claim /
  payment-notice) end-to-end against the sandbox.
- Passes `tests/integration/` against a deployment of the platform.
- Has a published reference example for each cycle.
- Has no known crypto / FHIR-validation correctness bugs.

## Reporting cadence

Weekly status update from each SDK owner to the engineering lead until
1.0.0. The platform's own release cadence does not gate on SDK delivery
beyond the GA gate above.

## Tracking issues

Per the v1.4 plan §V4 acceptance criteria, four GitHub issues should be
opened on this repo, one per SDK, each with the title
`[SDK] hfcx-sdk-<language> — 1.0.0 delivery` and a link back to this
document.

**Status:** GitHub Issues is currently **disabled** on this repository
(API returns `410 Issues has been disabled`). Two paths forward; the
engineering lead picks one before merging this plan:

1. **Enable Issues** in repo Settings → General → Features → Issues,
   then open the four issues with the bodies pre-drafted in this PR's
   description. Replace the `<LINK>` slots in the table below with the
   issue URLs after creation.
2. **Track elsewhere** (Jira, GitHub Projects board, Linear, etc.).
   In that case, replace this section's table with links to that
   external tracker and note the system used.

| SDK | Tracker link |
|---|---|
| Java | `<LINK>` |
| Python | `<LINK>` |
| .NET | `<LINK>` |
| JavaScript | `<LINK>` |

The four pre-drafted issue bodies are reproduced in the PR description
that introduces this document, so they can be copy-pasted into whichever
tracker is chosen.

## What is NOT decided here

- Specific API signatures per SDK beyond the high-level `HfcxClient`
  shape. SDK 1.0.0 RFCs decide those, owned by each SDK's owner.
- Whether SDK source will live in the same monorepo or in sibling repos.
  Recommended: sibling repos so SDK release cadence is independent of
  platform releases. Final decision is the engineering lead's.
- Versioning relationship between platform and SDKs. Recommended:
  semver-independent, with a compatibility matrix maintained per SDK.
- License of SDK code. Recommended: Apache 2.0 to match the platform.
