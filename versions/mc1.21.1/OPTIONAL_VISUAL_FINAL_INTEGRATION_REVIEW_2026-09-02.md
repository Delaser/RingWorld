# Optional-visual final integration review — 2026-09-02

## Verdict

PASS with no findings for the bounded final-consolidation task. The reviewed
local branch `codex/mc1211-optional-visuals` was clean and unpushed at exact
head `e64084d5623fea278cc66461ef5feb93c20a5f76`, with implementation/evidence
base `b660151167e8d6319dd54240d29780c90ceb4457` in its direct linear ancestry.

This verdict approves the integration record and final build checkpoint. It
does not add a support, publication, package, frozen-artifact, production, or
Create-compatibility claim.

## Consolidated review history

The sixteen independent review objects supplied for consolidation were
resolved before review. Each original object changes exactly one Markdown file
under `versions/mc1.21.1/`. Their consolidated counterparts form a linear
documentation-only sequence from `23a3a65606468947464b1b9e3b3331ea74c68559`
through `cc703a35c13559151b7cce260839ca1b8bf901fe` after `b660151`.

The first cherry-pick had the documented modify/delete conflict because the
review file's earlier base was absent at `b660151`; the consolidated tree keeps
the final reviewed document. Line-ending-normalized comparisons confirmed all
sixteen consolidated review documents match their original reviewed versions.
The remaining fifteen cherry-picks were conflict-free. There are no merge
commits in the branch range, and `git diff --check` passes.

## Manifest and handoff audit

Commit `e64084d5623fea278cc66461ef5feb93c20a5f76` adds only
`OPTIONAL_VISUAL_INTEGRATION_MANIFEST_2026-09-02.md` and updates this version
directory's `README.md`.

The manifest accurately distinguishes:

- the four mainline behavior commits and four later mainline contract fixes;
- the exact 1.21.1 model, server, protocol, Atlas, preview, creation UI, CPU,
  GPU, lighting, sky, fog, command, UI, fixture, correction, and evidence
  commits;
- the original and consolidated identities of all sixteen independent review
  objects; and
- the two retained bounded graphical results from work that remains
  unqualified.

All 98 referenced commit abbreviations or object IDs resolve to commits in the
local repository. The stated mainline source range has valid ancestry, all 18
relative Markdown links in the manifest resolve, and the README links to the
manifest plus both retained evidence/review pairs. The new text explicitly
withholds support and release metadata changes and does not overstate the
Windows Java 21 development-client evidence.

## Scope and contamination audit

The changed-path and commit-subject audit found no Create-mod, contraption,
kinetic, Flywheel, or Ponder implementation in the optional-visual branch. The
only changed path whose name contains `Create` is Minecraft's vanilla
`CreateWorldScreenMixin.java`, used by the world-creation UI backport. The new
manifest and README mention Create only to exclude the pre-existing
experimental compatibility investigation from this integration and its
claims.

The strict dependency verification metadata and the pinned dependency
inventory are unchanged in the branch diff. The final branch has no upstream
and was not pushed.

## Final build evidence

The implementer ran the requested Java 21 full
`test build :neoforge:build` gate at exact head `e64084d` with unchanged
verification metadata. It completed successfully in 42 seconds, including both
loader test/build tasks, strict dependency inventory verification (371
components and 752 artifacts), Fabric runtime-contract verification, loader
boundaries, and NeoForge Create-dependency isolation.

Independent recounting of the generated JUnit XML reports produced:

| Graph | Suites | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: | ---: |
| Fabric/common | 91 | 474 | 0 | 0 | 0 |
| NeoForge | 97 | 497 | 0 | 0 | 0 |
| Total | 188 | 971 | 0 | 0 | 0 |

No graphical matrix was rerun for this final documentation consolidation. The
earlier reviewed menu-only and integrated in-world results remain the bounded
runtime evidence indexed by the manifest.
