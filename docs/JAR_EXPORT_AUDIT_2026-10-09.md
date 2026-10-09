# JAR export audit — 9 October 2026

The audit follows build → frozen quick/full qualification → public metadata
materialisation → staging → upload plan → submission → hosted verification.
The current operator sequence is [JAR export](JAR_EXPORT.md). This task neither
starts a release nor uploads, promotes or deletes hosted files.

## Findings and repairs

| Finding | Resolution |
| --- | --- |
| Current host docs recommended the hard-coded 1.0 clean-build route, while supported matrix exports use frozen qualification. | Replace repeated obsolete instructions with a single current runbook; retain links to historical publication records. |
| README still described 1.3 as upcoming and pointed downloads at 1.2. | Align it with the recorded 1.3 publication and retained Modrinth 1.1 scope. |
| Qualified staging silently selected historical 1.1 inputs when manifest/config/notes were omitted. | Require all three paths explicitly at the CLI. Historical descriptors remain available as explicit inputs. |
| Restaging deleted the previous export before writing its replacement. | Prepare the complete new stage first, retain the old stage until promotion, and restore it if promotion raises an error. |
| Release config could carry stale public loader version numbers or different host project/dependency IDs. | Check these against the runtime artifact identity and official RingWorld/Fabric API projects before staging. |
| Publisher checked JAR hash but accepted changed loader tags, game versions, project IDs, public version numbers, dependencies and notes. | Require the generated qualified stage format and valid source commit, then compare host metadata with staging's shared metadata generator and the canonical rendered changelog. |
| Gradle enabled Maven publication without publications, repositories or any caller. | Remove the unused plugin. Runtime and source JAR generation remain unchanged. |

The metadata tests first reproduced thirteen accepted-invalid cases in the old
publisher, then pass after the repair. CLI tests also prove a missing group,
config or notes path fails before any staging work. No host request is made by
these tests or by publisher dry runs.

## What stays

- The six runtime outputs and all supported-version/loader qualification gates.
- Frozen hashes, metadata-only equivalence, licence/archive verification,
  corresponding-source links, dependency relations and rollback records.
- `stage_modrinth_release.py` shared helpers: current staging, publication,
  equivalence/verification and optional package code import them. Removing the
  whole historical module would break active tooling.
- Source JAR generation and optional client/server package tooling, kept outside
  the normal six-JAR export path. Historical release records are preserved.

## Remaining manual steps

There is no all-six export summary/coordinator enforcing one source revision
across the three separately staged candidate groups. A later small release
summary could bind six hashes, source commits, quick/full/composite evidence,
owner visual approval and host receipts in one reviewable record. Today those
checks remain in the release checklist; staging validates quick evidence and
does not independently require the full nightly suite or owner visual sign-off.

The publisher creates one held/unlisted file and prints its response/ID. Host
promotion and independently downloaded CDN hash/metadata verification are
separate operator actions. There is no durable receipt journal or automatic
duplicate reconciliation; inspect the listing before retrying an uncertain
submission. Do not add automatic retries that can create duplicate files.

Ordinary Gradle development defaults still use the historical 1.0 identity.
That is not a qualified public export; select a new public version explicitly
at freeze/staging instead of copying `build/libs` directly to a host. Changing
the development label is not required to repair the qualified release flow.

## Validation

All six source build/test cells pass: 485 Java cases per loader on 26.1.2 and
26.2, and 488 per loader on 26.3 (2,916 total, zero failures/errors/skips).
All six runtime JARs are byte-identical to the pre-audit JARs after removing
the Maven plugin. Every retained 1.1/1.2/1.3 release config validates against
its matching qualification manifest. Runtime Java/shader/asset sources are
unchanged, so no additional native game launch was required.

The complete Python suite passes 448 tests, with two Windows-only tests skipped
on macOS. Focused tests exercise metadata drift, stale public IDs, missing
explicit CLI inputs, unrecognized output preservation, and restoration of the
previous export after an injected promotion failure. These changes were tested
in the working tree based on `d3ae197`; final code and workflow are retained in
the audit PR. Evidence lives under ignored `logs/jar-export-audit/`, including
`build-results.json`, build logs, `python-tests.log` and `jar-comparison.json`.
These are tooling checks, not fresh frozen release qualification or live API
verification. No credentials were read or upload requests executed.
