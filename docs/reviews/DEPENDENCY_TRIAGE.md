# Dependency Triage — driving down the 342 Dependabot alerts

**Branch:** `main` (post-consolidation, May 2026)
**Dependabot count:** 342 (20 critical, 140 high, 150 moderate, 32 low)
**Methodology:** Inventoried all 28 `pom.xml` files plus `onboarding-app/package.json`. Looked at exact pinned versions of crypto-, parser-, and runtime-class deps. For each high-risk dep, traced production reachability before assigning severity.

This is a triage memo, not a fix plan. Concrete remediation lives under backlog items P3-3 (dependency hygiene) and P1-5 (container / runtime hardening). The top item below is severe enough to deserve being lifted into P0 if HealthFlow's threat model treats JWS signature forgery as a P0 risk.

---

## Tier 1 — fix this week

### 1. `io.jsonwebtoken:jjwt:0.9.1` — JWT signature verification on a deprecated, vulnerable library

| Property | Value |
|---|---|
| Pinned at | `0.9.1` (released **July 2018**) |
| Current | `0.12.6` (December 2024 line) |
| Used in | `hcx-core/hcx-common/src/main/java/org/healthflow/common/utils/JWTUtils.java`, `hcx-pipeline-jobs/core/src/main/scala/org/healthflow/dp/core/util/JWTUtil.scala` |
| Production reachability | **YES** — `JWTUtils.isValidSignature` is the platform's only signature verification path. Called for notification verification and JWS validation across the gateway and pipeline jobs. |
| Why it matters | jjwt 0.9.1 predates the 0.10+ split (`jjwt-api` / `jjwt-impl` / `jjwt-jackson`), uses an old `Jwts.parser().setSigningKey(...)` API, and accumulated multiple CVEs through 2019–2022 in adjacent code paths (`io.jsonwebtoken.SigningKeyResolver`, key-deserialization). Anyone maintaining this code for 7+ years is also exposed to the 0.10 algorithm-confusion class of issues. |

**Recommended action:**
- Add `<jjwt.version>0.12.6</jjwt.version>` to a parent pom property block.
- Replace direct `jjwt` artefact with the three-artefact split: `jjwt-api` (compile), `jjwt-impl` (runtime), `jjwt-jackson` (runtime).
- Migrate the API: `Jwts.parser().setSigningKey(...).parseClaimsJws(...)` → `Jwts.parser().verifyWith(...).build().parseSignedClaims(...)`.
- This is a small, mechanical change in two files (`JWTUtils.java`, `JWTUtil.scala`) but it touches the signature verification path, so cover it with round-trip tests for valid + tampered + algorithm-confusion (`alg: none`, `alg: HS256` with RSA public key as secret) inputs.

### 2. `spring-boot 2.5.5` — out of OSS support since November 2022

| Property | Value |
|---|---|
| Pinned at | `2.5.5` (October 2021) — `hcx-apis`, `hcx-onboard`, `hcx-scheduler-jobs/common-scheduler-job` |
| Current | `3.4.x`. Last 2.7.x extended-support release: `2.7.18`. |
| Why it matters | 2.5.x has been out of OSS support for ~30 months. Multiple CVEs in `spring-web`, `spring-security`, `spring-cloud-gateway` (the api-gateway), and transitives accumulated since. Every `spring-*` Dependabot alert on the default branch is downstream of this pin. |

**Recommended action (two paths):**
- **Path A — minimum viable (1 sprint):** bump to `2.7.18` to clear the OSS-support gap. Mostly compatible; some `Wargs` annotation tightening.
- **Path B — strategic (3 sprints, recommended):** bump to Spring Boot 3.x. Forces the Java 17 LTS migration that backlog item P1-5 already calls for, gets us off Spring 5.x → 6.x, and resolves the bulk of the dependabot count in one move. Java 17 is already required by the v1.2 backlog so this consolidates work.

### 3. `apache-flink 1.12.0` — five major versions behind, EOL

| Property | Value |
|---|---|
| Pinned at | `1.12.0` (December 2020) |
| Current | `1.20.x`, with `2.0.0` released March 2025 |
| Used in | All 13 jobs under `hcx-pipeline-jobs/` |
| Why it matters | The pipeline runtime is the most exposed component (network-facing per-job dispatchers consuming Kafka). Flink 1.12 has multiple advisories around RCE through legacy Akka serialization and untrusted-input deserialization. |

**Recommended action:** plan a Flink 1.12 → 1.18 LTS upgrade as a dedicated milestone. Breaking changes (Flink → DataStream V2 API, removal of legacy Akka dependency in 1.18+, Java 11 → 17/21 baseline). Significant — should be its own epic, separate from P3-3. Note: this overlaps with the existing branch `feature/update-elasticsearch-8.17` and similar.

---

## Tier 2 — fix this quarter

### 4. `jackson-databind` version drift across modules

| Module | Pinned at |
|---|---|
| `hcx-core/hcx-common` | `2.12.5` (Sep 2021) |
| `hcx-core/audit-indexer` | `2.12.5` |
| `hcx-pipeline-jobs/core` | `2.10.0` (Sep 2019) — **oldest, most exposed** |

**Why it matters:** `2.10.0` has known polymorphic-deserialization gadget chains (CVE-2020-3663x series). Even `2.12.5` has CVE-2022-42003/42004 unless patched.

**Recommended action:** Centralize via a `<jackson.version>` property at the root pom (currently absent). Pin to `2.17.x`. Eliminates ≥ 30 dependabot rows in one change.

### 5. `okhttp` — split-version footprint

| Module | Pinned at | Scope |
|---|---|---|
| `api-gateway` | `4.9.3` | compile (production) |
| `hcx-apis` | `3.14.9` | test |
| `hcx-onboard` | `3.14.9` | test |

**Why it matters:** `3.14.9` is the last 3.x release and has known cert-pinning-bypass + HTTP/2 issues. Production exposure is the gateway's `4.9.3` (also dated; current is `4.12.x`).

**Recommended action:** Move test-scoped deps to `4.12.x`; bump api-gateway to `4.12.x`. Low-risk change.

### 6. `commons-text 1.9` — Text4Shell present but **not exploitable as used**

| Property | Value |
|---|---|
| Pinned at | `1.9` |
| CVE | CVE-2022-42889 ("Text4Shell"), patched in `1.10.0` |
| Used in | `notification-trigger-job/.../NotificationTriggerProcessFunction.java:165`, `notification-job/.../BaseNotificationFunction.java:4` |
| Exploitability | **Not exploitable in current usage.** Both call sites use `new StringSubstitutor(map)` with a `Map<String,String>`. That constructor does **not** install the `interpolatorStringLookup()` factory that backs `${script:...}`/`${dns:...}`/`${url:...}` (the lookups Text4Shell weaponizes). It only does plain `${key}` map substitution. |
| Why bump anyway | Hygiene; defensive against a future engineer switching to `StringSubstitutor.createInterpolator()` and accidentally re-introducing the vector. |

**Recommended action:** Bump to `1.12.x`. Trivial change.

### 7. `postgresql:42.3.1` JDBC driver

| Property | Value |
|---|---|
| Pinned at | `42.3.1` (December 2021) — `hcx-core/postgresql-client` |
| Current | `42.7.x` |
| Why it matters | CVE-2022-21724 (driver code execution via type info handling); CVE-2024-1597 (escape handling) — patched in 42.7.2. |

**Recommended action:** Bump to `42.7.x`. Small change.

### 8. `elasticsearch-rest-high-level-client`

Deprecated by Elastic in favour of the Java API client. Pipeline-jobs core and audit-indexer both use it. Migration is non-trivial but eventually mandatory. Branch `feature/update-elasticsearch-8.17` (still un-merged) appears to address part of this — verify and either merge it or close it as superseded.

---

## Tier 3 — frontend (likely accounts for the bulk of "moderate" alerts)

### 9. `react-scripts:5.0.1` and the webpack 4 footprint in `onboarding-app/`

The 28 React/JS deps in `onboarding-app/package.json` are likely responsible for the 150 moderate-severity alerts on the default branch (webpack 4 + babel + postcss transitive vulns). A clean fix is migration to **Vite** (active maintenance, no webpack 4) — a 1-week task that also reduces build times by ≈10×. Not security-critical individually but dominant in the alert count.

### 10. `axios:^1.2.2`

`axios` had multiple SSRF and prototype-pollution CVEs through 2024. Bump to `^1.7.x`.

---

## What to merge / what's already moving

- **Branch `feature/update-elasticsearch-8.17`** — still on origin, last touched April 2025. Status unknown. If it's complete, merge it. If it's superseded by the consolidation, close it.
- **The `claude/post-merge-realm-fix` PR** (this PR) lays the groundwork by getting the v1.2 backlog into the repo. Folding this triage memo into the same PR is a net-add of ~250 lines of documentation, no code change.

---

## Numbers — what to expect

A conservative estimate of the alert reduction from each tier-1 + tier-2 action:

| Action | Est. alerts cleared |
|---|---|
| 1. jjwt 0.9.1 → 0.12.x | ~5 |
| 2. spring-boot 2.5.5 → 2.7.18 (path A) | ~80 |
| 2. spring-boot 2.5.5 → 3.x (path B) | ~120 |
| 3. flink 1.12 → 1.18 | ~40 |
| 4. jackson-databind centralize → 2.17.x | ~30 |
| 5. okhttp align → 4.12.x | ~5 |
| 6. commons-text 1.9 → 1.12.x | ~3 |
| 7. postgresql 42.3.1 → 42.7.x | ~3 |
| 9. onboarding-app webpack4 → Vite | ~120 |
| **Subtotal (paths A + 3 + 4 + 9)** | **~280 of 342** |
| **Subtotal (paths B + 3 + 4 + 9)** | **~315 of 342** |

The remaining ~30 alerts are likely in transitive deps that don't move until the upstream parent moves (e.g. Spring Cloud Gateway transitives) — they'll clear when their parents are bumped.

---

## Linked

- Production-Readiness Assessment v1.2 — backlog items P3-3 (dependency hygiene), P1-5 (container/runtime hardening, includes Java 17 LTS).
- This is a candidate for promotion to a P0 sub-task: **"Bump `io.jsonwebtoken:jjwt` from 0.9.1 to 0.12.x and add round-trip + algorithm-confusion tests."**
