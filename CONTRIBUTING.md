# Contributing to hfcx-platform

This document describes how changes flow into `main` for the
HealthFlow HCX Egypt platform.

## Branching

- Trunk: `main`. CI must be green before merging.
- Feature branches: short-lived, named for the work
  (`feat/...`, `fix/...`, `chore/...`, `refactor/...`).
- Remediation branches: see "Remediation cycle workflow" below.

## Remediation cycle workflow

The platform is undergoing a multi-cycle production-readiness remediation
tracked in `docs/remediation/`. The current cycle is **v1.3**
(pilot-readiness follow-up); the canonical plan lives at
`docs/remediation/agentic-plan-v1.3.md` once committed.

### Hard rules — explicitly different from v1.2

The v1.2 cycle landed all 22 gaps in a single squashed merge (PR #14).
Bisecting a regression to a specific gap was effectively impossible, and
two cross-gap regressions slipped through. The v1.3 cycle and every
subsequent cycle therefore follow these rules:

1. **One PR per gap.** Branch name `fix/v1<N>-<gap-id>-<short-slug>`,
   for example `fix/v13-n1-deployment-profiles`.

2. **STOP after every PR.** Do not start the next gap until the previous
   one has been merged into `main` by a human reviewer. If you are an
   AI agent, end your turn after pushing the branch and opening the PR.

3. **No squash-merge.** Every PR description must request
   "Create a merge commit" or "Rebase and merge", explicitly NOT
   "Squash and merge". Branch protection on `main` enforces this for
   the duration of the remediation cycles.

4. **Conventional Commits.** The PR title is the merge commit subject;
   write it as a Conventional Commit:
   `fix(ci): stop overriding sonar.projectKey in maven.yml (G12-followup)`.

5. **PR template.** GitHub will pre-populate the PR body from
   `.github/pull_request_template.md`. Fill in every section. The
   acceptance-criteria checklist comes verbatim from the gap's section
   in the cycle's plan document.

6. **No cross-gap drive-by fixes.** If you notice a problem outside the
   current gap's scope while working on it, file a `TODO(v1.<next>): …`
   comment in the affected file (or open a tracking issue) and keep
   going. Do not bundle the unrelated fix into the current PR — it
   defeats the bisection guarantee that one-PR-per-gap is meant to
   provide.

7. **Test discipline.**
   - Java/Kotlin: `mvn -pl <module> -am test` after every change.
   - Scala (hcx-pipeline-jobs): also run `mvn -pl hcx-pipeline-jobs -am
     compile` because Scala package mismatches surface only at compile
     time.
   - Workflow changes: push to a draft PR first, watch the run,
     then mark ready for review.

### Process discipline reviewer checklist

When reviewing a remediation-cycle PR, confirm:

- [ ] PR title is in Conventional Commits form and includes the gap ID.
- [ ] Body follows `pull_request_template.md` and references the plan
      section.
- [ ] Acceptance-criteria checkboxes are all ticked.
- [ ] No cross-gap changes — if the diff touches files unrelated to
      the gap, ask the contributor to split.
- [ ] Merge using "Create a merge commit" or "Rebase and merge",
      never "Squash and merge".

After merging, comment on the PR with a one-paragraph close-out
summarising what was done, test results, and any v1.<next> follow-ups
the work surfaced.

## SDK delivery (separate, multi-sprint track)

SDK delivery for the four supported languages (Java, Python, .NET,
JavaScript) is a separate, multi-sprint track tracked under
`docs/strategy/sdk-delivery-plan.md` rather than as a single
remediation gap. The plan document records owners, delivery targets,
and the GA gate. Per-SDK work happens in dedicated repos
(`hfcx-sdk-<language>`); this repo's per-gap PR discipline does not
apply to SDK code, but the plan itself is updated via per-edit PRs
against this repo.

The pilot strategy works without all SDKs shipped — the platform
team ships pilot partners a Java SDK pre-release. GA, by contrast,
gates on the Java + Python SDKs both reaching 1.0.0. See the plan
document for the full GA gate definition.

## Non-remediation contributions

For ordinary feature, bug-fix, and refactoring work outside an active
remediation cycle, the relaxed rule is "one logical change per PR with
a Conventional Commit title". Squash-merge is allowed for those PRs
when there are many WIP commits worth collapsing.

## Code style

Follow the conventions already in the codebase. The project uses
Lombok in Java, standard Scala style in pipeline-jobs, and yamllint-
friendly YAML formatting in `deployment/` and `.github/workflows/`.

## Questions

For day-to-day questions, ping the engineering lead. For questions
about the remediation cycles or this process specifically, the canonical
record is the plan document under `docs/remediation/`.
