# Atlas production release gate — 2026-08-01

Issue [#70](https://github.com/Delaser/RingWorld/issues/70) closes the atlas
priority phase. This gate was run on Minecraft 26.1.2, Fabric Loader 0.19.3,
Fabric API 0.155.2+26.1.2, Java 25.0.4, and the production 16,384×256 layout.
The source branch was `codex/issue-70-atlas-regression`, based on `6e4f38c`.
Runtime saves, logs, screenshots, caches, and reports remained ignored local
evidence; this document records the reproducible results.

## Result

The gate passes. Format-6 generation and recovery, progressive UI, live atlas
updates, cache reuse, world/layout switching, two-client synchronization,
dimension lifecycle, complete-loop rendering, and final build validation all
completed without a RingWorld error.

Two performance changes were required during the gate:

- texture sampling, relief, mip construction, and `NativeImage` filling now
  run from an immutable atlas snapshot away from the render thread; only the
  GPU upload remains on that thread;
- changed cells in an already-complete atlas publish after three quiet seconds
  or at a ten-second maximum delay. Ordered revision commits save durable cache
  state but no longer trigger a second identical GPU rebuild.

Incomplete-atlas publication remains capped at once per second so the player
can watch coverage grow. Real chunks remain authoritative at every point.

## Commands

All commands used Java 25 and `--console=plain`.

```sh
./gradlew runHeadlessPrewarmServer \
  -PringHeadlessPrewarmSource='Atlas Visual Complete Source' \
  -PringHeadlessPrewarmResult=issue70-production.json

./gradlew runHeadlessPrewarmServer \
  -PringHeadlessPrewarmResume=true \
  -PringHeadlessPrewarmResult=issue70-production.json

./gradlew runHeadlessPrewarmServer \
  -PringHeadlessPrewarmResume=true \
  -PringHeadlessPrewarmResult=issue70-complete-cache.json

./gradlew runAtlasUiClient

./gradlew runLayoutSwitchClient \
  -PringLayoutSwitchFirstSource='Issue70 Format6 Safe Small' \
  -PringLayoutSwitchSecondSource='Issue70 Format6 Production'

./gradlew runProductionLifecycleClient \
  -PringProductionLifecycleSource='Issue70 Format6 Production'

./gradlew runProductionProjectionClient \
  -PringProjectionWorld='<safe-small or production source>' \
  -PringProjectionViewDistanceChunks='<6, 12, or 28>'

./gradlew clean test build runAtlasFidelityBenchmark
```

The existing dedicated multiplayer fixture was run with a fresh disposable
copy of the complete safe-small world and two clients. Server and both client
test configs had `testMode=false`; this prevented the unrelated singleplayer
showcase traversal from contaminating multiplayer positions.

## Lifecycle and recovery matrix

| Case | Evidence | Result |
| --- | --- | --- |
| Fresh and progressive | Real GUI fixture began at 0/13,312 cells and displayed partial transparent coverage | Pass |
| Pause/resume/cancel/retry | Eleven GUI-scale-4 captures exercised the real buttons and payloads | Pass |
| Interrupted/resumed | Production process stopped at 1,825/16,384 chunks and resumed to 16,384/16,384 | Pass |
| Complete cache | Headless rerun loaded 65,536/65,536 cells with zero generated chunks and zero reported generation milliseconds | Pass |
| Corrupt/current and wrong world | `RingAtlasPregenerationServiceStorageTest` and `RingTerrainAtlasTest` reject corrupt format-6 and mismatched hash/geometry inputs | Pass |
| Construction changes | GUI fixture placed and removed a sampled gold block and received revisions 2 and 3 with the matching client height | Pass |
| Layout switch | One JVM cleared 2,048×416 state, opened 16,384×256, and installed its 2,048×32 source atlas | Pass |
| Dimension lifecycle | Overworld → Nether → Overworld → End → Overworld, save/disconnect, and reopen restored the exact complete production atlas | Pass |
| Two clients | Both clients received revisions 11/17/24/31/36/40; reconnect reused the complete cache and did not start rebuild churn | Pass |

The two-client server also passed natural seam travel, melee, block updates,
boat/passenger visibility, long teleport and periodic return, disconnect, and
reconnect. Natural crossing retained a 0.25-block maximum packet and server
tick step, with no corrective seam teleport.

## Production generation and resource evidence

| Measurement | Result |
| --- | ---: |
| Geometry | 16,384×256 blocks; 16,384 chunks |
| Atlas | 2,048×32; 65,536 cells; sample step 8 |
| First interrupted segment | 91,454 ms; 1,825 chunks / 7,300 cells |
| Resume segment | 728,209 ms |
| Combined wall time | 819,663 ms (13m 39.7s) |
| Atlas file | 77,540 bytes |
| Raw atlas arrays | 458,752 bytes |
| Complete wire snapshot | 459,264 bytes |
| Source world | 298 files; 337,664 KiB |
| Completed disposable copy | 345,208 KiB |
| Sampled server RSS peak | 630,256 KiB |
| Sampled used Java heap | about 193 MiB |
| GPU texture with mips | 5,592,384 bytes |
| GPU mesh | 393,216 vertices; 9,437,184 bytes |
| CPU texture-build scratch | 12,582,912 bytes |

The complete production atlas has world hash `18346684028133338951`, layout
fingerprint `6057283249690875888`, and SHA-256
`bddabe97f427370fbad41cf76ddc96d7476927f649695a219741bfaf527084c7`.
The safe-small format-6 atlas SHA-256 is
`e6e1970b1136f9e51b6c6271d9254a7a7625484a521f72e31eb2a89e2c1c0a0c`.
The 298-file production source aggregate remained
`9a5dbb3efb2ddfd249cafc7175219a215ec8d06e00ecad45ab32b6e29a75dfd3`
before and after the copied-world run.

## Visual and frame-pacing matrix

Every row captured tangent/along-ring, live/proxy handoff, and radial-up
views. All eighteen images contained the closed atlas-backed ring without a
projection cutoff, wrong-world texture, or seam break.

| Layout / view | Tangent avg / max / >50 ms | Handoff avg / max / >50 ms | Radial avg / max / >50 ms |
| --- | ---: | ---: | ---: |
| Safe-small / 6 | 8.557 / 21.066 / 0 | 8.548 / 12.979 / 0 | 8.618 / 11.728 / 0 |
| Safe-small / 12 | 9.162 / 48.544 / 0 | 8.894 / 25.997 / 0 | 8.583 / 14.706 / 0 |
| Safe-small / 28 | 9.894 / 65.323 / 5 | 8.974 / 71.008 / 1 | 8.685 / 21.604 / 0 |
| Production / 6 | 8.745 / 90.174 / 1 | 8.431 / 20.630 / 0 | 8.413 / 17.608 / 0 |
| Production / 12 | 10.731 / 117.328 / 3 | 8.533 / 25.835 / 0 | 8.394 / 17.658 / 0 |
| Production / 28 | 12.143 / 165.531 / 11 | 11.951 / 92.184 / 3 | 8.711 / 75.546 / 1 |

Values are milliseconds and include cold chunk-section UBO growth, cache
transfer, and the first full proxy upload. The quiet 6/12-chunk phases are
clean; 28 chunks and cold production startup retain isolated long frames.
Before batching, one revision burst rebuilt the full texture repeatedly.
After the change it produces one trailing upload.

Representative final screenshot SHA-256 values:

| Layout/view | Handoff | Tangent | Radial-up |
| --- | --- | --- | --- |
| Safe-small 6 | `c651bc750074116621246d22129044eeb9f10e0d9be76a2460089cf9de268015` | `3ab9a1e5b0f3e60e223f2ba1a95fb8a5b811fbf1a5a2f2cc0b26d78c90845f71` | `4b4ae10b78f957b1a3923ab7da7b49a48edd8defc7d0d6b4e66d0caf864fbd10` |
| Safe-small 12 | `d2fe57270cf37ade8195ca6f0fa2a0275ad6bc4b96f4a5e25510a1194cdd74c7` | `a8bf09b1d00e4c3f3bd5d071e1d60770892fd857c952714dd85aae649e6e4ef1` | `d66f3d135730d4636805b74a4da7d632294c53c1a0824ea70db56b162b86b5e8` |
| Safe-small 28 | `53c8014359155912aae041026531ccb867bb318faac13b774620d302699ed74c` | `4b332f77aaaf0a87b1448aec9bda1c0256b9a4eccaa84247535d4cb58b54e854` | `ce86be9afdef70c9ca3b660d354f9a56c6f0803b6e739ae0857a4e32b03649a9` |
| Production 6 | `194b938a48d18fd41c43925a01f4c51ebf8e9928a349edca4afece811d8bdb3b` | `ba39d1a9a542bb78a858646bde99080a16557429b29aff37e94f38804aa20a31` | `b906047dd529b3e35fe6afbc406c55fcee6f916d46cbb214e46a4243af5f33b8` |
| Production 12 | `f33fbcaccf007bb9f1bce891be52c5ca50113abb6b4c5b983b43e5c06bf072de` | `cefd9b02be4c9e651b8d7e88fba3a4027cadf2f149da4e0203ec87b75c142631` | `cafb0e583e9f603142c3fc5d739c8a9da4b70b165d38206d13cfbcdefd99ce12` |
| Production 28 | `5aa7b696923183b8f1c91629fc147537f307818a3337caeb5c319b7342b4ed32` | `911da6c8bd338db07e98f9077aab3b05184903d2a2163c63e4f69899a97fb953` | `80ad6ea9d7628e626f8b7953e48a09e092021a55eaa972d7fa4ebcb85cf69d4d` |

## Final build and residual risk

`clean test build` passed 220/220 unit and parameterized cases. The runtime jar
SHA-256 was
`cd2b30797e2b7e539ef4192bb07c32a00d68c914bf877a23d9c58a78b06961aa`.

Remaining atlas risks are bounded and explicit:

- 28-chunk cold starts can still grow Minecraft's chunk-section buffers and
  produce isolated long frames;
- a complete changed atlas trails distant visual edits by three quiet seconds,
  bounded to ten seconds under continuous churn, then performs one full GPU
  upload; nearby real chunks update immediately;
- the atlas remains an eight-block visual sample and cannot reproduce distant
  block entities, transparent layers, local lights, mobs, or arbitrary
  third-party terrain semantics;
- one macOS test launch stopped at a black pre-world window after resource
  loading; no RingWorld world/session code ran, and a clean retry completed the
  full 2m52s GUI gate. Package launch reliability remains tracked separately.
