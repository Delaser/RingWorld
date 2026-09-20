# RingWorld 1.2 release preparation

These are release-review inputs. The prior 26.3 files are staged locally but superseded; nothing new
is published. The older-line files are not staged. Historical 1.1 inputs
remain unchanged. Each of the three version lines has one descriptor and one
change-only changelog for its separate Fabric and NeoForge files. Descriptors
pass the existing support-contract validation and changelog rendering.

The owner authorized publishing updated supported versions to CurseForge and,
on September 20, continuing until ready for full launch, pausing at 60% weekly
usage remaining. No additional publication approval is needed for that scope.
The owner accepts the successful older-version source builds and automated
tests; do not repeat their full in-game/nightly qualification suites. This is
an acceptance decision, not new evidence of those unrun checks passing.

Current candidate and evidence: [26.3 status](../../../docs/26_3_PORT_STATUS.md).
The corrected 26.3 quick run is `20260920T104806Z-dd4541a72f59`, built from
`4860724`. Both loaders pass with the structure-sampler correction and installed
dedicated servers. The prior `39216e8` candidates are superseded.

## Current release scope

The owner explicitly waived saved-world upgrade checks and optional launcher
bundles, then deferred Modrinth because login is unavailable. Publish six mod
jars to CurseForge only. Older full nightly sweeps remain waived. Do not restart
those checks or ask for publication authorization again.

Archipelago is hidden from the creation UI at the owner's request; its saved
settings and generator remain readable. Its failed optional terrain gate stays
in the backlog. Whole-ring structure-density balance remains unmeasured.

The owner paused publication to correct the Atlas design, then authorized
completion and CurseForge upload within 60% remaining after both 26.3 checks passed. The shared
master now uses one sample per block. Client detail is independently Low,
Medium (default), or High, with a button on the RingWorld Map and the existing
slash commands. The generation-quality selector is removed. Historical fidelity
IDs remain readable for saved identities; they no longer choose server sampling.
Previously staged jars predate this correction and must not be uploaded.

After the targeted checks pass, refresh the six candidates through the existing
quick artifact/startup gate, stage exact release metadata, review these clear
change-only changelogs and upload plans, and upload to CurseForge. Record IDs,
moderation state and downloaded hashes. Preserve historical evidence and never
upload diagnostic jars. No upgrade or launcher work is in this release scope.

Run heavy work serially, reuse verified caches and completed evidence, keep
clients hidden/muted, and stop safely at 60% weekly remaining. No new framework,
broad compatibility sweep or unrelated feature work is part of this path.

The 26.3 NeoForge pin is upstream `26.3.0.7-beta`; do not describe it as a
stable NeoForge release. Occasional frame stalls and the nonfatal NightConfig
startup watcher exception remain recorded limitations. These checks cover
OpenGL, not Minecraft's experimental Vulkan backend.

Rollback descriptors name the previous published 1.1 files. For a 26.3 world,
returning to 26.2 requires its pre-upgrade backup; the old mod jar alone cannot
downgrade a save. Corresponding-source links are filled from the actual frozen
candidate by the existing staging command.
