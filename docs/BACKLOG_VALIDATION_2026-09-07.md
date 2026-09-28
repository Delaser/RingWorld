# Development backlog validation — 7 September 2026

This is development evidence, not a release or universal compatibility claim. River work, Climate Tour, distant underside geometry and release/26.3 preparation are excluded by the owner.

## Changes and discoveries

- Three local display levels: Low retains old Low, Medium retains old High, High retains old Max. Server generation fidelity and per-player selection remain separate.
- Selecting the active level no longer rebuilds it. Full-resolution surface jobs reuse their already captured immutable Atlas rather than copying it again.
- The exterior visibility injection previously assumed the 26.2 BlockPos argument. A real 26.1.2 client caught the invalid injection; target the common `SectionPos.asLong` call instead.
- Lithium's `ai.poi.tasks` redirects collide with RingWorld's periodic raid lookup on both loaders. Loader metadata now requests Lithium's supported option override for that group only. Explicit-config diagnostic runs pass startup, 15 seconds of ticking, save and normal stop on all four cells; fresh metadata-only candidates still require rerunning.

## Retained checks

Default Vanilla generation, both loaders, 26.1.2: production 16,384×256 seed `ringworld-regression-1` fresh and reload; 2,048×416 seeds `ringworld-matrix-0` and `ringworld-matrix-3`. Both matrices pass and agree on starts, references, loot and sampled biome families. Reports are in `dist/qualification/backlog-{fabric,neoforge}/evidence/worldgen-matrix/summary.json`.

In the shared sampled seam strip, default start/crossing/loot counts are 4/4/14, 3/2/10 and 1/0/9 respectively. Increased-density fresh/reload production remains 4/4/14; the second seed is 3/2/27. These are strip counts, not whole-ring balance evidence.

Industrial thickness 7, pattern 6, palette 4 and zero decay passed the increased-density Vanilla matrix. New runtime assertions check both faces against saved decay, 256 patterned lowest blocks, retained bedrock above, and reload stability.

Archipelago production seed currently fails the existing smooth-join criterion: average seam height delta 2.508, maximum 49, longest cliff run 7. Adjacent pairs average 0.598 and 1.024. Preserve this failure; do not relax the threshold merely to pass the layout.

The pre-compatibility universal prototypes start and stop on both loaders for 26.1.2 and 26.2 (`logs/wall-elements-scale/universal-smokes.log`). They are one jar per Minecraft ABI, not one jar spanning both versions. Later source changes require new hashes and new runtime evidence.

## Third-party evidence limits

Exact Modrinth metadata and SHA-512-verified downloads are retained under `logs/backlog-compatibility` and `dist/backlog-compatibility/downloads`. Selected Lithium versions are 0.24.7 for 26.1.2 and 0.25.3 for 26.2; FerriteCore is 9.0.0, with each loader's own artifact.

Create's official inventory has no matching 26.1.2/26.2 release. The retained 1.21.1 / Create 6.0.10 handoff reports three failures: seam belt linking, square tank formation and default Flywheel contraption rendering. Do not transplant that old result into a current support claim. Renderer replacements already listed as unsupported in `COMPATIBILITY.md` remain unsupported.

The corrected Fabric 26.1.2 real-client Atlas UI gate passes pause/resume/cancel/retry/completion, two block edits with ordered revisions, and normal disconnect/session cleanup (`logs/backlog-atlas-ui-fabric-fixed.log`).

The Fabric 26.1.2 dedicated two-client scenario passes with Low on A and High on B while Atlas pregeneration continues. Both clients and the server report their full scenario passes and stop normally. This covers the scenario's seam/combat/placement/vehicle/reconnect/bed/death/portal/weather assertions; it is not proof of a completed production-size one-block multiplayer Atlas.

Additional fresh/reload cells pass: Archipelago 2,048×416 with industrial decay 10% on Fabric; the same seed with increased density, 192-high walls and 100% decay on NeoForge; and production Vanilla increased density with that 100% decay style on NeoForge. The production Archipelago failure remains, with undecorated base heights averaging 2.183 across the seam (largest delta 49), so decoration alone does not explain it.

NeoForge 26.2 also passes the complete dedicated two-client scenario with different LOD settings. Fabric 26.2 passes the full Atlas UI/revision/disconnect fixture with Lithium 0.25.3, FerriteCore 9.0.0 and JEI 30.29.0.201 installed together, using the automatic metadata override. Exact mod hashes and pass markers are tracked in `docs/evidence/backlog-2026-09-07`.

JEI 29.34.0.90 requires NeoForge 26.1.2.99 and is incompatible with the project's pinned 26.1.2.87 loader. JEI 29.29.0.77 requires 26.1.2.81 and was selected for the separate pinned-loader test. The rejected newer version remains a recorded dependency mismatch, not a RingWorld crash fix.

NeoForge 26.1.2 passes the full Atlas recovery/edit/disconnect fixture with Lithium 0.24.7, FerriteCore 9.0.0 and JEI 29.29.0.77, using the automatic metadata override. Both modded integrated runs retain default Lithium configuration files. This establishes coexistence during the exercised Atlas workflow, not every recipe, optimisation or multiplayer interaction offered by those mods.

Ten undecorated reference cuts were checked for the failing Archipelago seed. The cut 32 blocks from the seam also exceeds the average threshold (2.057; maximum 73), while the actual seam is 2.183/max 49. Other reference averages range 0.142–1.618. Thus the strict failure is real, but a unique coordinate discontinuity is not established; elevated natural relief also triggers it nearby. Keep the absolute gate failing and retain the diagnostic rather than silently weakening the acceptance rule or rewriting saved terrain generation.

Final normal-source universal jar hashes are `c47e4584049cc92ce967fb3fe73adf7e149de24187c9311380b7144c847ccaef` (26.1.2) and `f6668ab51c05729ad31a30ed83a0988e459bda441395b34ccf3666536f7151e7` (26.2). Each exact file passes Fabric and NeoForge startup, 15-second tick dwell, save and normal stop both alone and with the matching Lithium/FerriteCore pair: eight passes. These are development smoke jars; their clients have not been qualified as packaged universal artifacts. Both fresh normal-source builds pass 435 JVM tests per loader with no failures/errors/skips; the retained fuser passes five Python tests.

The production walking probe exposed surface-build starvation during the one-block Atlas download: requiring exact equality with the latest live render revision discarded completed mesh work whenever tiles arrived first. The renderer now publishes a coherent captured build only if it advances the displayed revision, is not from the future, and matches the world plus existing session/quality generation guard. New tests reject rollback and cross-world publication. The first fixed runtime publishes the initial mesh promptly and subsequently reuses it during partial tile updates, instead of rebuilding it for each discarded job. Normal-source universal hashes above predate this final fix and are superseded by the final evidence update below.

## Production movement and remaining hitches

The Medium display test used copies of the owner's 16,384×256 one-block Atlas save, render distance 28, clear daytime, FOV 70, no VSync, a 260 FPS cap and forward input with auto-jump. Both runs moved 32.2 blocks before a terrain obstacle stopped further progress. Before: 2,197 frames/39.35 s, mean 55.8 FPS, maximum 97.8 ms, two frames over 50 ms. After: 1,899 frames/40.71 s, mean 46.6 FPS, maximum 123.6 ms, two frames over 50 ms. This is **not** an equivalent-workload speed comparison: before the fix the ring was absent during build starvation; after it was actually drawn throughout. It does not prove hitch-free walking or a completed-Atlas performance improvement.

The confirmed fix is timely coherent mesh publication and removal of the repeated discarded mesh work. Remaining observed stall candidates include the first wall GPU upload (21–42 ms), combined initial resource update (40–75 ms), occasional Atlas snapshot copies (24–40 ms), and GC pauses (up to 22 ms in the retained recordings). Cache compression/save took up to 0.97 seconds **on its background worker**, not the render thread. Larger frame outliers remain unattributed; do not equate a background save duration with a frame pause. JFR windows extend beyond movement windows; exact measurements and limits are in `walking.json`.

## Final source checkpoint

After the starvation fix, both normal-source version builds pass **436 tests per loader**, zero failures/errors/skips. The final exact universal files are:

| Minecraft | SHA-256 |
| --- | --- |
| 26.1.2 | `33cd71dc152f6a68445da6545da42dfd65009708e9dcef7a396ef2352c5d6120` |
| 26.2 | `9749025617408c18c43c80c62b261ba04c5e30e2311532b94b15ffeaf7566590` |

These supersede the earlier hashes. Both repeat all eight server startup/tick/save/stop smokes with and without Lithium/FerriteCore. The tracked fusion/smoke JSON records refer to these final bytes. Full UI and multiplayer workflows above predate only the final snapshot publication-policy fix; the final fix has its new monotonicity/identity tests and the real production one-block client run. Neither those retained workflows nor the copied-world probe is a frozen packaged-client qualification for these final universal jars.
