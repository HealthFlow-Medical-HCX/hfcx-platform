# Security Scan Workflow

**Workflow:** `.github/workflows/security-scan.yml`
**Cadence:** weekly (Sunday 02:00 UTC) + on-demand via `workflow_dispatch`

This document explains what the security-scan workflow does, how to read its results, and how to extend it. Created as part of P1 supply-chain hardening (remediation plan §3.12).

---

## What it does

| Job | Tool | Output | Where to find results |
|---|---|---|---|
| `trivy-fs-scan` | [Trivy](https://aquasecurity.github.io/trivy/) filesystem scan | SARIF | **Security tab → Code scanning → "trivy-fs"** category |
| `sbom` | [Syft](https://github.com/anchore/syft) | SPDX-JSON + CycloneDX-JSON | **Actions → workflow run → Artifacts** (90-day retention) |
| `trivy-image-scan` | Trivy image scan (5 services) | SARIF | **DISABLED** until registry credentials wired in — see below |

The filesystem scan covers Maven dependencies (every `pom.xml`), npm dependencies (every `package.json`), Dockerfile base images, and infrastructure-as-code misconfigurations. SARIF upload to the GitHub Security tab gives the same triage surface as Dependabot alerts so engineers don't have two places to look.

The SBOM jobs produce machine-readable inventories of every dependency. Both SPDX and CycloneDX are generated because different downstream tools (compliance reporting, license review, vulnerability lookup) prefer different formats. They're stored as workflow artifacts; download them from the workflow run's Artifacts panel.

## Severity gating

By default the scan reports `CRITICAL` and `HIGH` findings only. Lower severities are skipped to keep the Security tab manageable. Override via `workflow_dispatch` input:

```
gh workflow run security-scan.yml -f severity=CRITICAL,HIGH,MEDIUM
```

The workflow itself **does not fail** on findings (`exit-code: "0"`). This is deliberate — the SARIF upload is the actionable output. Branch-protection rules can require the upload step to succeed, which is a separate (and stronger) gate than failing the scan job.

## How to triage a finding

1. Open **Security → Code scanning** in GitHub.
2. Filter to category `trivy-fs`.
3. For each finding, the SARIF includes:
   - **Rule ID** — the CVE identifier
   - **Path** — which file (which `pom.xml` or `package.json`) introduces the vulnerable dep
   - **Severity** — Trivy's normalized severity (CRITICAL / HIGH / MEDIUM / LOW)
   - **Recommendation** — the fixed version, if available
4. If the finding is genuine and a fix exists, open a PR bumping the dependency version.
5. If the finding is in transitive code that we don't actually invoke (e.g. an optional Spring autoconfig path), use the GitHub UI to mark the finding as "Used in tests only" or "Won't fix" with a justification — that hides it from the active list while preserving the audit trail.

## Why the per-image scan is disabled

The `trivy-image-scan` job is committed but skipped (`if: false`). It scans built Docker images for vulnerabilities in the OS layer + the application layer combined — strictly a superset of what the filesystem scan finds.

To enable:
1. Decide where image scans run — every push to a service's docker-build workflow vs. weekly here.
2. Wire registry credentials into this workflow (currently the `*-docker-build.yml` workflows already authenticate with `secrets.DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN`; this workflow doesn't yet).
3. Remove the `if: false` line.

Recommendation: enable per-image scanning as a follow-up step in **each** `*-docker-build.yml` workflow rather than centralising here. That way every push gets scanned at build time and a vulnerable image never gets tagged.

## Why no Cosign signing

Image signing was deferred from this PR. It needs a separate decision:

- **Sigstore keyless** — uses GitHub Actions OIDC; no key management; free; the dominant default.
- **BYO KMS (AWS KMS, Vault Transit)** — more control, more operational overhead.

If/when keyless signing is chosen, add a `cosign sign` step to each `*-docker-build.yml` after the `docker push` and configure the `id-token: write` permission. That's a 5-line addition per workflow.

## Why no OWASP dependency-check Maven plugin

OWASP dep-check overlaps almost entirely with what Trivy fs scan finds for Maven dependencies. Running both means the same CVEs surface twice. Trivy is faster (no NVD download cycle). If a future need arises for the dep-check report's specific format (e.g. a regulator asks), bind the plugin to `mvn verify` then.

## Failures and recovery

If the Trivy upload step fails:
- Check `actions: read` and `security-events: write` permissions on the workflow.
- Trivy's GitHub action may rate-limit on cached DB downloads — re-running usually succeeds.

If Syft generation fails:
- The Anchore action sometimes can't parse exotic Maven plugin configurations. Use `format: text` as a temporary fallback to confirm the rest of the pipeline is healthy.

## Linked

- Production-Readiness Remediation Plan §3.12 (CI/CD and supply-chain security)
- `compliance/DPIA-HCX-Egypt-v1.md` §8 (security measures)
