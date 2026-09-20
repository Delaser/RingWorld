# RingWorld 1.2 release preparation

These are draft inputs, not staged or published releases. Historical 1.1 inputs
remain unchanged. Each of the three version lines has one descriptor and one
change-only changelog for its separate Fabric and NeoForge files. Descriptors
pass the existing support-contract validation and changelog rendering.

The owner authorized publishing updated supported versions to CurseForge and,
on September 20, continuing until ready for full launch, pausing at 70% weekly
usage remaining. No additional publication approval is needed for that scope.

Current candidate and evidence: [26.3 status](../../../docs/26_3_PORT_STATUS.md).
The corrected 26.3 quick run is `20260920T104806Z-dd4541a72f59`, built from
`4860724`. Both loaders pass with the structure-sampler correction and installed
dedicated servers. The prior `39216e8` candidates are superseded.

Before upload:

- Complete the 26.3 nightly matrix and copied-world upgrades.
- Requalify the new shared runtime on all six 26.1.x and both 26.2 cells.
- Resolve or explicitly retain the Archipelago smooth-join failure and
  whole-ring structure-density acceptance gap; neither is a passing check.
- Complete release equivalence and required package/runtime checks, including
  native Windows and macOS evidence for the actual release files.
- Stage using these descriptors, the matching manifest and passed quick run;
  never upload a diagnostic qualification jar.
- Verify CurseForge file IDs and downloaded hashes after submission.

The 26.3 NeoForge pin is upstream `26.3.0.7-beta`; do not describe it as a
stable NeoForge release. Occasional frame stalls and the nonfatal NightConfig
startup watcher exception remain recorded limitations. These checks cover
OpenGL, not Minecraft's experimental Vulkan backend.

Rollback descriptors name the previous published 1.1 files. For a 26.3 world,
returning to 26.2 requires its pre-upgrade backup; the old mod jar alone cannot
downgrade a save. Corresponding-source links are filled from the actual frozen
candidate by the existing staging command.
