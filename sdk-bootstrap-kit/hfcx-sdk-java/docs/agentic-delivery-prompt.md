# Agentic Delivery Prompt — committed marker

The full agentic delivery prompt that drives this SDK's sprints is
maintained in the platform repo. To pick it up in a fresh Claude Code
session against this repo, fetch:

  https://github.com/HealthFlow-Medical-HCX/hfcx-platform/blob/main/docs/strategy/sdk-delivery-plan.md

and look for the section pointing at the canonical agentic prompt.

When the platform repo's plan moves under a stable URL, update this
file to embed the prompt directly.

## Sprint state — Java SDK

| Sprint | Status |
|---|---|
| J1 — Bootstrap + extraction | Done (this kit) |
| J2 — Keycloak token client | Not started |
| J3 — HfcxClient skeleton + correlation IDs | Not started |
| J4 — Outbound encryption path | Not started |
| J5 — Inbound decryption + validation pipeline | Not started |
| J6 — Error taxonomy + integration test pass | Not started |
| J7 — Documentation + 1.0.0 release | Not started |

Each subsequent sprint follows the per-gap PR discipline from the
platform's `CONTRIBUTING.md` (one PR per sprint, no squash, conventional
commits).
