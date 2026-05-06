<!--
  Pull-request template for the hfcx-platform repository.
  Established by Gap N5 of the v1.3 remediation plan; see
  CONTRIBUTING.md → "Remediation cycle workflow" for context.
-->

## Gap reference

**Gap ID:** <!-- e.g. N1, G12-followup, or "n/a" for non-remediation work -->
**One-line summary:** <!-- e.g. introduce gateway and recipient-hcx-api Spring profiles -->
**Reference doc section:** <!-- e.g. docs/remediation/agentic-plan-v1.3.md#gap-n1 -->

## Files changed

<!--
  Bullet list. For PRs that span more than ~15 files, summarise by directory
  and link to the diff for full inspection.
-->

-

## Acceptance criteria

<!--
  Copy verbatim from the gap's section in the remediation plan.
  Tick each box as it is satisfied, with a one-line note when relevant.
-->

- [ ]

## Test commands run

```
$ mvn -pl <module> -am test
... (paste output excerpt — tail -30 of the relevant section is plenty)
```

## Cross-gap drive-bys

<!--
  Per v1.3 process discipline, do NOT bundle unrelated fixes into a gap PR.
  If you noticed something during this work that should be a separate PR,
  list it here as `TODO(v1.4): <description>` so the reviewer can track it.
  Otherwise: "none".
-->

(none)

## Merge mode

Please use **"Create a merge commit"** or **"Rebase and merge"**.
**Do NOT squash** — per the v1.3 remediation plan, every gap must have a
distinct merge commit on `main` for audit and bisection purposes.
