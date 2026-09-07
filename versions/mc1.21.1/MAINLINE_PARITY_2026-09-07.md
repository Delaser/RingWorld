# Minecraft 1.21.1 mainline parity — 2026-09-07

## Provenance and scope

Fetched `origin` from `https://github.com/Delaser/RingWorld.git` on 2026-09-07.

| Reference | Exact commit |
| --- | --- |
| Public `main` | `e058c6965b7189316f72e683ebc08ad89f566e11` |
| Public `port/mc-1.21.1` | `f4522daeda564a767b622d2829dfe3db7a535ddf` |
| Preserved local `codex/mc1211-optional-visuals` | `962675e2b2b2fa3744c147bacbbfb5ec8d6f3fdd` |
| Common public ancestor | `2312fc96bdc8a4a75b7fb7b84b84631b9f59e3fa` |

Development continues from the preserved local head on
`codex/mc1211-mainline-review`, in
`C:/Users/Admin/.codex/worktrees/2769/RingWorld`. No other worktree or branch
was reset or overwritten. Remote feature branches, including
`codex/atlas-worldgen-features` and `codex/atlas-fidelity-gallery`, are not
public mainline and are not part of this comparison. Separate Create WIP
remains benched; pre-existing compatibility files in the base are unchanged.

The usage monitor was invoked but failed with Windows error 10093 while
selecting subprocess pipes. The desktop account query independently returned
2% used in the exact 10,080-minute weekly window (98% remaining).

## Live feature checklist

| Mainline behavior | State and 1.21.1 realization |
| --- | --- |
| Canonical topology, seam gameplay, worldgen, maps/compasses, Atlas service | Already present in public backport; preserve its 1.21.1 ABI fixes and retained runtime evidence. |
| Shared block-coordinate helper and conservative presentation bounds | Already present; common API tree matches mainline. Active block-entity load geometry retains the backport's owner-aware load context. |
| Format-4 immutable rim styles, ten palettes, six patterns, thickness/decay | Already present in common models, storage, generation, UI and both loader transports. |
| Periodic wall noise at non-divisible circumferences | Already present, including mainline `b384211` correction. |
| Distant-wall pattern variation | Implemented here: restore staggered masonry, irregular strata, variable panel sizes and hybrid ribs from mainline's shader. Remove the additional green decay tint absent from mainline. |
| Neutral initial ring before seed preview | Implemented here: partial captures remain uniformly neutral until a coherent seed preview exists; real cells then replace preview pixels. Existing regression updated. |
| Atmosphere/Night/Void and Small/Large/None | Already present, including saved profiles, live commands, physical star orientation, fog matching and lower atmosphere. |
| Atlas format 8: measured mycelium and exposed block light | Already present, including bounded live invalidation, RGB/light mip filtering and local ring-light tuning. |
| Staged server previews and isolated creation seed preview | Already present, including cancellation, stale-result rejection, centred seam and client session teardown. |
| Compact creation/editor/Atlas controls | Already present; compact monument-button labels restored here. |
| 26.2 reversed depth, render-state extraction, new registry/entity APIs | Deliberately inapplicable: retain the 1.21.1 OpenGL/Java 21 and Minecraft ABI adapters. |
| Mainline release/qualification tooling and experimental unified jar | Deliberately outside product parity: no support matrix, release metadata, unified packaging, publication or exhaustive qualification changes. Existing strict 1.21.1 dependency and MPL checks retained. |
| Create compatibility development | Benched by owner instruction; no integration or qualification. |

The starting feature mapping and prior independent reviews are indexed in
[the optional-visual integration manifest](OPTIONAL_VISUAL_INTEGRATION_MANIFEST_2026-09-02.md).
Direct source comparison found the three presentation gaps above despite the
earlier feature-level integration record. Other differences include deliberate
backport improvements: fail-closed legacy rim rewriting, strict light-byte
validation, ordered preview acceptance, command routing and streaming underlay.
These remain intact. No storage or wire format was redesigned.

## Ordered work and validation

1. Establish immutable provenance and preserve the consolidated branch — done.
2. Compare post-ancestor mainline product changes and preserve ABI adaptations — done.
3. Restore the missing initial-ring, wall-pattern and compact-label behavior — implemented.
4. Clean baseline and changed-source Java 21 dual-loader builds — passed:
   474 Fabric/common and 497 NeoForge tests, no failures/errors/skips.
   Existing optional-visual smoke — both loaders have passing retained evidence
   as described below, with earlier failures retained.
5. Build-validated product source is `2edce1a4c23a5cc9d223c13b95119cd89e35ede0`.
   The six-line fixture-only wait is `12632a3`.
   Final documentation and commit consolidation — complete. Checkpoint history
   remains on `codex/mc1211-fresh-build-parity`; the review branch groups the
   identical final changes into product/tests, fixture repairs and documentation.

No release claim follows from this work. The retained menu-only results
predate these changes. Dedicated two-client optional visuals, complete
sky/sun/preset coverage, packages, cross-OS runs and release qualification remain
unqualified as detailed in the integration manifest.

## Build environment

The initial fresh-worktree build failed before compilation because Loom regenerated
40 locally remapped artifacts with different archive checksums. Following the
reviewed README/datasheet procedure, the ignored `remapped_mods` and
`minecraftMaven` trees were seeded from the consolidated branch's `f862` worktree.
The normal strict build then passed; no verification metadata, dependency pins
or source files in the cache source worktree changed. The inventory check
verified 371 components and 752 artifacts, including Mojang inputs.

Java home: `C:/Users/Admin/Documents/ChatGPT/RingWorld/.codex-tmp/jdk21/jdk-21.0.12.1+1`.
Command: `gradlew.bat test build :neoforge:build --no-parallel --console=plain --no-daemon`.
The ordinary build includes the pre-existing Create dependency-isolation check;
that is not a Create runtime qualification or resumed compatibility project.

## Retained first smoke failure

The first Fabric run at product source `2edce1a` completed its in-game sequence
but failed the unchanged verifier because light-on and light-off PNGs were
byte-identical. The log records light 15/revision 7 at 21:38:50, capture at
21:38:51, light 0/revision 8 at 21:38:52, and capture at 21:38:53, without an
intervening texture upload. `ClientRingState` intentionally waits three quiet
seconds or a ten-second maximum before publishing a complete-Atlas change.
The fixture's 60-frame wait was shorter than that window. Commit `12632a3`
adds a light-stage-only 12-second wall-clock wait covering the maximum delay,
asynchronous upload and 750 ms morph; product behavior and verifier assertions
are unchanged. Original logs and all eight PNGs remain in ignored
`.codex-tmp/parity-fabric-first-failure/`. This first invocation remains FAIL.

## Bounded visual evidence

The replacement Fabric invocation at `12632a3` passes the unchanged eight-PNG
verifier, complete 4,096-cell Atlas, live sky/sun, light 15 then 0, saved-world
reopen and normal session/texture teardown. It completed in 2m23s. Its light
texture uploaded before each capture, and original-resolution inspection shows
a warm point in the on image absent from the off image. The partial-preview
and inner/top and outer/top wall views were inspected at original 1280x720.
The first frame is **after** the seed preview arrives; neutral pre-preview
behavior is covered by the unit regression, not a claimed screenshot.

Only the fixture's Industrial/Hybrid nine-block wall and selected sky/sun
combinations are visual evidence. The full wall pattern calculation matches
mainline source, but this is not an all-palette/all-pattern pixel qualification.
Compact monument labels were restored by source comparison; the full creation
UI matrix was not repeated. The driver optimizes out the now-unused optional
`RingWorldWallStyle` uniform and logs a warning; the null-checked upload path
and shader draw remain functional.

Fabric outputs: `run-optional-visual-smoke/`.

- `logs/latest.log` SHA-256: `cb273473e69a9ed18993e52826aa2c8e381ef12bbad7dba7b0dc0b89cd9f616b`.
- `evidence/optional-visual-contact-sheet.png` SHA-256: `5c67bc5008fcf25509798cf9b18d951854b34117d9ef1e603b6a83cf342bf185`.

The first NeoForge invocation completed the Atlas but stalled after capture 01.
The generic `testMode` server sequence teleported the player to X=2040 after
the visual fixture requested X=512. The existing log later confirms canonical
player X=2040; the visual fixture only waits for its requested pose. Closing
the identified Minecraft window stopped normally and saved all dimensions;
the unchanged verifier failed. This invocation remains incomplete/FAIL,
preserved under `.codex-tmp/parity-neoforge-first-incomplete/` (log SHA-256
`7574d6357e21d524dec143c41318eacf42d3101090dd7d0800ca1f53b0075423`).

Fixture-only commit `3f3a548` changes `testMode` from true to false in the two
optional-visual task preparers. Their own `ringworld.optionalVisualSmoke`
property still creates the world and controls the poses; no product guard or
pose-retry loop changes. The Fabric PASS predates this preparer-only correction.
The one replacement NeoForge run at `3f3a548` completed all eight captures,
light 15 then 0, reopen and normal teardown, in 2m49s. Its verifier first
rejected the stale expected `testMode=true` entry. Commit `a7bbee3` changes
only that exact expected value to false; the verifier-only invocation then
passes in 23 seconds against the retained output. There was no further game
launch. Thus this is retained runtime plus a corrected verifier result, not
one monolithic successful invocation.

Original-resolution inspection of NeoForge's preview, both wall seam views
and light pair confirms visible geometry/patterns and a warm light point on
that is absent off. NeoForge outputs: `neoforge/run-optional-visual-smoke/`.

- `logs/latest.log` SHA-256: `a3997f20f063d725ad7d5c6a59af7dae553dca20501afe4cd6f47a811dd959bc`.
- `evidence/optional-visual-contact-sheet.png` SHA-256: `4b44abe07260458e7caf6df9312f9af5c2baf2c1003fb4f71c0b729784f8a7d8`.

The 971-test full build precedes the fixture-only wait and task isolation;
subsequent client runs compile the fixture on both loaders. No additional
matrix or tests were run. No frozen-jar, packaging or release claim follows.
All artifact paths above are relative to the worktree recorded at the top.
At the final usage milestone the script still reports Windows error 10093;
the desktop query confirms 93% of the weekly allowance remains.
