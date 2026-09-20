# RingWorld 1.2 release preparation

These are release-review inputs. The 26.3 files are staged locally; nothing new
is published. The older-line files are not staged. Historical 1.1 inputs
remain unchanged. Each of the three version lines has one descriptor and one
change-only changelog for its separate Fabric and NeoForge files. Descriptors
pass the existing support-contract validation and changelog rendering.

The owner authorized publishing updated supported versions to CurseForge and,
on September 20, continuing until ready for full launch, pausing at 65% weekly
usage remaining. No additional publication approval is needed for that scope.
The owner accepts the successful older-version source builds and automated
tests; do not repeat their full in-game/nightly qualification suites. This is
an acceptance decision, not new evidence of those unrun checks passing.

Current candidate and evidence: [26.3 status](../../../docs/26_3_PORT_STATUS.md).
The corrected 26.3 quick run is `20260920T104806Z-dd4541a72f59`, built from
`4860724`. Both loaders pass with the structure-sampler correction and installed
dedicated servers. The prior `39216e8` candidates are superseded.

## Minimum path to the CurseForge release

1. Freeze and stage six runtime jars: Fabric and NeoForge for 26.1.x, 26.2 and
   26.3. The two 26.3 release jars are ready. For the other four, use the existing
   quick build/artifact/dedicated-startup gate required by
   `stage_qualified_release.py`, then its metadata-equivalent staging. One jar
   per loader covers all three 26.1 patches. Do not repeat full nightly suites,
   rewrite terminal evidence or upload diagnostic qualification jars.
2. Complete a focused copied-world upgrade batch on both loaders, checking old
   settings, terrain/structure persistence, Atlas migration and save/reopen with
   the final candidate. Preserve originals. The existing formal upgrade runner
   requires a passed source-worldgen record; where that prerequisite is missing,
   prepare only the required source fixture, not the whole nightly matrix.
3. Close the optional-generation release decision. Archipelago has a retained
   strict smooth-join failure; whole-ring increased-structure balance is unproven.
   Recommendation: explicitly disclose these optional-feature limitations and
   defer further tuning. This recommendation is not yet owner acceptance and
   must not be represented as passing qualification. Default-terrain 26.3
   worldgen passes on both loaders.
4. Review the three change-only changelogs and six upload plans for exact versions,
   loader tags, Fabric API dependencies, source links, licences and hashes.
   Use the existing authenticated CurseForge route; authorization already exists.
5. Upload the six jars, record file IDs and moderation state, download and compare
   hosted hashes, then commit the publication record. A submitted file is not
   necessarily public until CurseForge finishes moderation.

Recommend shipping mod jars first and deferring optional launcher ZIPs. Native
Windows/macOS package review remains required for those bundles, and is not
evidence already obtained. The 26.3 metadata equivalence, both packaged server
smokes and [final Windows launcher regression](https://github.com/Delaser/RingWorld/actions/runs/35510620067)
pass. The latter is an automated launcher test, not an in-game Windows review.

Run heavy work serially, reuse verified caches and completed evidence, keep
clients hidden/muted, and stop safely at 65% weekly remaining. No new framework,
broad compatibility sweep or unrelated feature work is part of this path.

The 26.3 NeoForge pin is upstream `26.3.0.7-beta`; do not describe it as a
stable NeoForge release. Occasional frame stalls and the nonfatal NightConfig
startup watcher exception remain recorded limitations. These checks cover
OpenGL, not Minecraft's experimental Vulkan backend.

Rollback descriptors name the previous published 1.1 files. For a 26.3 world,
returning to 26.2 requires its pre-upgrade backup; the old mod jar alone cannot
downgrade a save. Corresponding-source links are filled from the actual frozen
candidate by the existing staging command.
