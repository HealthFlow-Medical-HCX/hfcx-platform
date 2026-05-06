# SDK Bootstrap Kits

This directory holds bootstrap kits for the four HFCX SDK repositories
that the SDK Delivery Plan (`docs/strategy/sdk-delivery-plan.md`) tracks:

| Language | Bootstrap kit | New repo target |
|---|---|---|
| Java | `hfcx-sdk-java/` | `HealthFlow-Medical-HCX/hfcx-sdk-java` |
| Python | (not yet bootstrapped — Sprint P1) | `HealthFlow-Medical-HCX/hfcx-sdk-python` |
| .NET | (not yet bootstrapped — Sprint D1) | `HealthFlow-Medical-HCX/hfcx-sdk-dotnet` |
| JavaScript | (not yet bootstrapped — Sprint S1) | `HealthFlow-Medical-HCX/hfcx-sdk-javascript` |

## What a bootstrap kit is

A bootstrap kit is a fully-functional SDK repository tree, committed to
this platform repo because the agentic delivery session that produced
it has GitHub MCP scope restricted to `hfcx-platform` and cannot create
a new repository directly.

Each kit's own `BOOTSTRAP.md` describes the human steps to convert the
kit into a real, standalone GitHub repository (clone the new empty repo,
copy the kit contents, push, set up Sonatype/PyPI/npm/NuGet credentials,
configure branch protection, etc.).

After a kit has been moved into its real repository and the new repo's
CI is green, the kit gets deleted from this platform repo as a follow-up
cleanup PR.

## Bootstrap kit lifecycle

1. **Sprint J1 / P1 / D1 / S1** of the SDK Delivery Plan produces a
   bootstrap kit and commits it here under per-language sub-directory.
2. The engineering lead follows the kit's `BOOTSTRAP.md` to set up the
   real repo.
3. After the real repo's first CI run is green, a follow-up PR against
   `hfcx-platform` deletes the kit and updates `docs/strategy/sdk-delivery-plan.md`
   with the real repo URL.
4. All subsequent SDK sprints (J2+, P2+, etc.) happen in the real repo,
   not here.

## Why kits are committed rather than packaged separately

So a future Claude Code session against the new SDK repo can verify
the kit's authenticity by diffing against this directory, and so the
historical kit contents are inspectable from the platform repo's git
history without depending on a tarball release artifact.
