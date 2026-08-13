# Testing

RingWorld needs tests at three levels:

1. pure geometry/topology unit tests;
2. a real integrated client/server smoke world;
3. two real clients on a dedicated server.

Rendering and mixin behavior cannot be proven by unit tests alone.

## Active port checkpoint

The active public `main` integration line requires Java 25. The Fabric build
and the NeoForge 26.1.2.87 / ModDevGradle 2.0.143 build each pass all 338
unit/parameterized cases. Fabric common/client compilation also passes:

```sh
JAVA_HOME=/path/to/jdk-25/Contents/Home \
PATH="$JAVA_HOME/bin:$PATH" \
./gradlew clean test build --console=plain
```

See `MINECRAFT_26_1_COMPILER_BASELINE.md` for the historical 95-error inventory
and its resolution. A green build and dedicated-server launch do not establish
client rendering, gameplay, or multiplayer compatibility.

The NeoForge build uses the same Java 25 toolchain:

```sh
./gradlew :neoforge:test :neoforge:build --console=plain
```

Both projects expose `runServer`. Launch the intended dedicated server with
`./gradlew :runServer` (Fabric) or `./gradlew :neoforge:runServer` (NeoForge),
not an unqualified `runServer` task.

## Rolling Minecraft version qualification

Minecraft 26.1 is the development compatibility floor, but the current tasks
and published artifacts remain proven for 26.1.2 only. Minecraft 26.1 and
26.1.1 become supported only after the same exact loader-specific jar passes
their build, server, world, Atlas, multiplayer, rendering, lifecycle, and
packaging cells. Later stable Minecraft versions follow the same intake and
must not inherit a support claim from compilation or launch alone.

The approved manifest, quick/nightly/release tiers, isolated-fixture rules,
same-hash requirement, forward-only world-copy policy, and planned
orchestrator interface are specified in
[`MINECRAFT_VERSION_SUPPORT_PLAN.md`](MINECRAFT_VERSION_SUPPORT_PLAN.md).
The execution-capable orchestrator is fail-closed and may run its external
smoke only for a complete loader triplet with clean provenance and one frozen
candidate. Do not treat its planning output as current evidence or broaden
loader metadata before its initial six-cell matrix passes.

The Phase 1 pinned matrix and pure validator are implemented. They retain
26.1/26.1.1 as `pending` cells despite exact Fabric inputs and pinned NeoForge
beta trial inputs, and reject promoting those cells without qualification
evidence:

```sh
python3 scripts/validate_minecraft_version_matrix.py
python3 scripts/test_validate_minecraft_version_matrix.py
```

The validator covers six unique cells, exact/non-floating dependency inputs,
checksums, qualification-only profile paths, port isolation, legal states,
immutable artifacts/evidence for passing and published cells, evidence for
every terminal result, host-specific publication state and downloaded hashes,
official evidence URLs, and consistent same-artifact claims. These tests
validate the matrix policy; they do not qualify a Minecraft runtime.

The current Phase 2/3 foundations are checked with:

```sh
python3 scripts/test_qualification_gradle_isolation.py
python3 scripts/test_qualification_metadata_ranges.py
python3 scripts/test_minecraft_qualification_ranges.py
python3 scripts/test_minecraft_frozen_candidate.py
python3 scripts/test_minecraft_qualification_evidence.py
python3 scripts/test_run_minecraft_qualification.py
python3 scripts/test_minecraft_qualification_executor.py
python3 scripts/test_external_runtime_smoke.py
python3 scripts/test_external_runtime_executor.py
python3 scripts/test_external_runtime_qualification_adapter.py
python3 scripts/test_minecraft_nightly_qualification_model.py
python3 scripts/run_minecraft_qualification.py \
  --tier quick --cell 26.1-fabric --dry-run
```

The pure nightly model does not execute a fixture. It plans each server world
at `<fixture-runtime>/world`; lifecycle/portal and production-render entries
both require an immutable production-world input. Its creation entry covers
the existing settings UI only. Atlas prewarm recovery is the sole planned
same-fixture restart.

Both the frozen-candidate build and each diagnostic cell use
`--max-workers=1`. This keeps dependency resolution serial on constrained
hosts while retaining isolated Gradle homes, outputs, and evidence.
The wrapper properties also pin the official Gradle 9.5.1 binary ZIP SHA-256;
do not remove that verification to work around a transient download failure.

On a worker that has provisioned an external read-only dependency cache, the
pure planning check may include it explicitly:

```sh
python3 scripts/run_minecraft_qualification.py \
  --tier quick --cell 26.1-fabric --dry-run \
  --gradle-dependency-cache /absolute/worker-provisioned-gradle-cache \
  --gradle-distribution-zip /absolute/external/gradle-9.5.1-bin.zip
```

The directory must already exist, be non-symlinked (including its path
components), and be outside the checkout, `dist/`, qualification cell
build/run state, and the operator home. This only supplies
`GRADLE_RO_DEP_CACHE`; it never replaces the per-cell `GRADLE_USER_HOME`,
enables Gradle offline mode, or constitutes runtime/support evidence. The
generated input-plan and frozen-candidate evidence label the cache as
non-authoritative acceleration. Provision the directory from a compatible
Gradle `caches/modules-2` tree, excluding `*.lock` and `gc.properties`, and do
not mutate it while qualification reads it. The runner revalidates its path at
each Gradle command boundary; cache bytes are never support evidence.

The optional wrapper ZIP must be the exact non-symlinked external file named
by `gradle-wrapper.properties`, outside the checkout, qualification state, and
operator home. The runner rehashes it against `distributionSha256Sum` before
each frozen or diagnostic Gradle launch and copies it into that cell's empty
wrapper store. It does not forge the wrapper `.ok` marker, pre-extract Gradle,
or weaken URL/checksum validation. This avoids repeatedly downloading the
same distribution into isolated homes; it is acceleration, not qualification
evidence.

The executor and external-runtime planner checks cover their isolated
primitives and plan contracts. They verify pinned/no-redirect downloads, the
official installer contract (including its pre-created empty contained target), installed Mojang-server identity, exact mod
inventory, loopback port preflight, loader/RingWorld/ready markers, and an
ordered stop/save/clean-exit sequence. The external adapter additionally
rechecks the installed server with its official manifest algorithm while
recording a separate SHA-256 inventory identity. It carries the reviewed
loader-specific installer name into terminal validation rather than requiring
one hard-coded label. The strict schema independently binds that installer's
name, URL, algorithm, and checksum to the canonical manifest cell; both Fabric
and NeoForge names have acceptance and mismatch coverage. It also
checks full-triplet same-file identity and clean source provenance before it
can perform runtime I/O, then emits a separately schema-validated immutable
`strict-terminal-evidence.json`. The focused bridge tests also replace the
retained jar after preparation and prove that this fails before the executor
is called, then lend a real runner lock through a deterministic terminal
fixture and verify the immutable strict record. This green command set remains
tooling evidence, not a completed runtime matrix.

GitHub Actions runs the same pure qualification contract suite as
`Qualification static guard` on Ubuntu and Windows. The Windows job includes
the held-lock and space/Unicode qualification-path test, using the single
cross-platform `PYTHONPATH=scripts` import root. It intentionally does
not invoke Gradle, download or install Minecraft, run a server, contact an
external service, package artifacts, or publish anything. Its result is a
portable static-tooling check only, not qualification or release evidence.
The executable `LICENSE` input is pinned to LF in `.gitattributes`, and the
synthetic jar tests canonicalize that text before hashing. Windows lock tests
release the held byte-range lock before reading its advisory metadata, while
disposable cache/ZIP tests use a simulated non-overlapping operator home; the
production protections against home-directory state remain unchanged.

A non-dry runner requires a completely clean, pushed checkout and Java 25.
It executes the isolated source build/unit adapter and then verifies exactly
one per-cell diagnostic runtime jar for loader metadata, MPL-2.0, diagnostic
build identity, the exact repository MPL-2.0 text, and SHA-256. A partial loader selection writes immutable local
reports and returns `INCOMPLETE` without runtime I/O. A complete three-version
loader selection additionally builds one frozen oldest-ABI jar and may run
that exact file through the external dedicated-server adapter. It becomes
compatibility evidence only when every strict terminal record passes.

For a selected complete loader triplet, a failed frozen-candidate preflight
stops before the per-cell diagnostic builds: they cannot repair the missing
shared candidate. The immutable report keeps the failure attributable by
recording it at `SHARED_CONTRACT`, marks its skipped diagnostics
`FROZEN_PREFLIGHT_ABORTED`, and marks later selected cells
`CELL_ABORTED_AFTER_FAILURE`. Partial selections retain their normal diagnostic
behavior and remain `INCOMPLETE`.

The first real clean/pushed runner execution on 2026-08-12 selected
`26.1-fabric`, used Java 25 and Fabric Loom 1.17.19, and completed `:test
:build` in 2m59s with 337 tests and no failures or errors. It deliberately
ended `INCOMPLETE` at commit `51e7a95d56617e0af7b575dbc9c076727f5e65e2`
because the later adapters were absent. This proves the execution boundary and
26.1 source build only; it is not external-runtime or support evidence.

After the diagnostic verifier was corrected to compare the embedded licence
against the canonical repository MPL-2.0 bytes, clean commit `954bc7c` repeated
the 26.1 Fabric cell as run `20260812T151647Z-66228770c525`. All 337 tests and
the isolated build passed; artifact verification accepted the real runtime jar
with SHA-256
`7669a10461801bd0e24db60fbb3cab925d5177905e698377e65eb1e69b82a43f`.
The terminal verdict is correctly `INCOMPLETE` at shared-contract and
dedicated-smoke because only one loader cell was selected, and no external
runtime I/O occurred.

The dry run must exit nonzero with `INCOMPLETE` and
`DRY_RUN_NO_EXECUTION`; it is planning output, not evidence. A partial non-dry
selection is also deliberately `INCOMPLETE`. Inspect
the opt-in Gradle layout without launching Minecraft with:

```sh
./gradlew help --console=plain --no-daemon \
  -PringQualificationRoot=dist/qualification/config-smoke \
  -PringQualificationCell=26.1.2-fabric \
  -PringQualificationPort=26129
```

Both qualification properties are required together. The root must be below
`dist/qualification`; normal invocations with neither property retain the
established development paths.

The 2026-08-12 source-build ABI diagnostic used the manifest's exact inputs
for Fabric 26.1/26.1.1 and the pinned NeoForge 26.1/26.1.1 beta trials. All
four isolated `test build` cells pass 337 tests with zero failures/errors and
generate metadata for the selected Minecraft version. Diagnostic artifacts
use `0.0.0-qualification+mc<version>` plus a qualification release label so
they cannot be confused with release files. This is compile/package evidence
only; it does not qualify a dedicated server, graphical client, or one frozen
jar across patches.

The reviewed qualification-only metadata ranges are Fabric
`>=26.1 <=26.1.2`, NeoForge Minecraft `[26.1,26.1.2]`, and NeoForge loader
`[26.1.0.19-beta,26.1.2.87]`. Real Java 25 builds from the 26.1 ABI produced
both loader jars with those exact declarations; a separate normal resource
generation check retained Fabric `26.1.2` and NeoForge `[26.1.2]` plus
`[26.1.2.87,)`. This is candidate preparation only. External runtime smokes
must still install one unchanged jar per loader into every patch cell.

The raid/worldgen fixture routing checks are:

```sh
python3 scripts/test_prepare_raid_seam_fixture.py
python3 scripts/test_run_worldgen_structure_matrix.py
bash -n scripts/prepare_raid_seam_fixture.sh
```

Both fixtures keep their historical defaults. Qualification orchestration may
instead supply `--qualification-cell-root <cell>` or
`RINGWORLD_QUALIFICATION_CELL_ROOT`; the resolved cell must remain below
`dist/qualification`, and all fixture-managed state then stays below that
cell. Because it performs deletion, the raid preparer also rejects every
existing symlink component on its managed paths before creating or deleting
fixture state.

Fresh Fabric and NeoForge dedicated-server launches reach `Done`; the NeoForge
launch also starts and progresses atlas generation. The NeoForge client now
also has a focused diagnostic gate:

```sh
./gradlew :neoforge:runProductionProjectionClient \
  -PringNeoForgeProjectionSource="NeoForge Test"
```

It copies the named source save from `neoforge/run-client/saves/` into
`neoforge/run-production-projection/saves/`, opens only that disposable copy,
waits for resource/shader loading, the current settings acknowledgement, and a
complete atlas, records tangent/handoff/radial screenshots plus frame metrics,
verifies the evidence, and exits. The historical format-2 production
16,384×256 noon run passed with settled tangent/handoff/radial averages of
10.7/8.4/8.4 ms per frame; the dusk, night, and rain variants also passed.
Those captures describe alpha 3 and do not qualify the current format-3
candidate. A fresh candidate source must acknowledge format 3 and complete
annular mapping v2 (4).
Dusk is frozen at tick 12,000, matching
`RingSkyCycle`'s warm dusk keyframe; tick 14,008 is already night and must not
be used as dusk evidence.

The NeoForge visual and lifecycle gates use separate disposable run
directories:

```sh
./gradlew :neoforge:runProductionVisualParityClient \
  -PringNeoForgeVisualParitySource="NeoForge Test"
./gradlew :neoforge:runLayoutSwitchClient \
  -PringNeoForgeLayoutSwitchFirstSource="NeoForge Test" \
  -PringNeoForgeLayoutSwitchSecondSource="NeoForge Layout Source Legacy-Length"
./gradlew :neoforge:runProductionLifecycleClient \
  -PringNeoForgeProductionLifecycleSource="NeoForge Test"
```

The established first gate verifies screenshots at the natural seam and both
five-block textured rims. It now also records the natural 0.25-block seam
crossing's rendered-frame sample count plus raw average, maximum, and over-50-ms
times through the post-crossing settle. A nonzero sample count is required; no
fixed frame-time threshold is imposed across hardware. The 2026-08-08
production run recorded 428 frames at 16.661 ms average, 21.858 ms maximum,
and zero frames over 50 ms. The second switches between
different immutable layouts and proves that disconnect clears client geometry
and atlas state. The third proves inactive RingWorld state in Nether/End, exact
Overworld restoration, normal save and disconnect, then reopen. Every source
must be an ignored save under `neoforge/run-client/saves/`; the tasks mutate
only freshly copied destinations. The server/runtime gates below also pass, and
local package parity is complete. Hosted NeoForge publication remains gated on
final candidate review and owner approval.

Fabric has the matching production seam-and-rim gate:

```sh
./gradlew :runProductionVisualParityClient \
  -PringVisualParitySource="production-ring-save-folder" \
  -PringVisualParityViewDistanceChunks=12
```

It copies a source below `run/saves/` into
`run-production-visual-parity/saves/`, captures the same natural seam and both
rims, records the seam-motion frame metrics, verifies all evidence, and exits.
Both loader runners use the same shared `RingVisualParityCaptureClient`; only
their launch/copy adapters differ. The verifier finds the metric inside the
normal timestamped log line, rejects a missing or zero-sample record, but
deliberately does not apply a cross-hardware FPS threshold.
The 2026-08-08 production run recorded 426 frames at 16.742 ms average,
51.018 ms maximum, and one frame over 50 ms.

Issue #149 repeated the projection and natural seam/both-rim visual-parity
gates on 2026-08-10 using independently generated format-3 annular production
sources. Both loaders acknowledged format 3, received all 65,536 Atlas cells,
and passed tangent, 12-chunk handoff, radial, seam, and both-rim verification.
The fresh seam-motion windows sampled 702 frames at 11.99 ms average on Fabric
and 753 frames at 9.60 ms average on NeoForge; both retained a 0.25-block
maximum natural crossing step. Owner inspection is still required for the
subjective banding/colour/LOD verdict.

NeoForge's dedicated multiplayer gate uses three isolated processes below
`neoforge/run-multiplayer/`: one server and clients A/B. Prepare the fixture,
start `:neoforge:runMultiplayerServer` and both qualified client runs, then
verify after all three exit:

```sh
./gradlew :neoforge:verifyNeoForgeMultiplayerHarness --console=plain
```

The passing matrix covers natural seam travel with a maximum 0.25-block tick
step, nearest-image visibility/combat, blocks, explosions, bed/death state,
physical Nether and End portals, boats/passengers, explicit teleport,
reconnect, and canonical player storage. The NeoForge clients intentionally
use five chunks for this cold-start fixture; that is a harness setting, not a
forced gameplay render distance.

## Unit and build validation

Run:

```sh
./gradlew test build
```

`check` also runs `verifyLoaderBoundary`. That task scans the shared
`src/main/java` and `src/client/java` trees and fails if either Fabric or
NeoForge API namespaces leak into them. Loader integrations belong in their
corresponding `src/platform/` trees.

Expected artifact:

```text
build/libs/ringworld-1.0.0+mc26.1.2.jar
```

The NeoForge development artifact is
`neoforge/build/libs/ringworld-neoforge-1.0.0+mc26.1.2.jar`.

The active suite passes 338 unit/parameterized cases per loader:

| Class | Coverage |
| --- | --- |
| `RingGeometryTest` | Seam continuity, presentation charts and sleeping-position images, default walking length, physical/tangent transforms, noise seam, culling envelope, visibility math, query windows |
| `RingInteractionCoordinatesTest` | Bidirectional seam-face block-use normalization, positive/negative presentation aliases, and exact preservation of the hit-to-clicked-block offset |
| `RingBlockEntityOwnershipTest` | Live and still-packed exact aliases remain addressable for save/removal while clean aliases resolve to canonical ownership |
| `RingMapCompassSupportTest` | Bidirectional nearest-image map sampling/decorations, scale-one seam-banner placement, banner gate, and spawn/lodestone/recovery compass bearing plus exact-target validity |
| `RingRaidSupportTest` | Periodic raid distance/selection, seam-window POI queries, nearest-image village-centre averaging/deduplication, and canonical wave-spawn readiness windows |
| `RingObjectTransformTest` | Exact curved rigid-anchor pose, tangent orientation, and presentation-seam continuity |
| `RingChunkTopologyTest` | Canonical chunk images, joined-edge distance, periodic entity simulation distance, watch windows, incremental seam diff, long teleport, finite whole-ring filter |
| `RingDimensionReportTest` | Full-height radial safety, aligned playable minimum, structural-only/unsafe curvature, walls/clouds, allocation bounds, measured-reference cost warnings, and maximum technical warning envelope |
| `RingDimensionCostEstimateTest` | Exact production benchmark retention, atlas wire/tick calculation, checked scaling, and supported-maximum arithmetic |
| `RingGenerationBoundaryTest` | Shared generated-rim top bound for default/custom wall heights and world-top clamping |
| `RingCloudBoundsTest` | Exact inner rim-face cloud clipping planes for all presets and custom wall thickness |
| `RingPortalDestinationBoundsTest` | Positive/negative multi-lap X normalization, both finite-Z clamps, preserved safe targets, Small-preset frame/creation clearance, and three-image periodic portal lookup distance |
| `RingDimensionMatrixTest` | Safe-small, aligned playable minimum, narrow, production, former-wide, long/narrow, wide/medium, and custom-wall layouts across topology, final saved spawn canonicalization for negative/seam aliases, worldgen coordinate seams/finite-band limits, atlas and 6/12/28/64 render budgets |
| `RingSettingsHandshakeTest` | Immutable safe-small/production/wide/custom-wall payload identity, acknowledgement, mapping mismatch, malformed mapping, and fingerprint rejection |
| `RingHandshakeTrackerTest` | Correct, duplicate, missing/expired, unexpected, disconnect, and reconnect acknowledgement state |
| `RingLayoutFingerprintTest` | Immutable layout and rim semantic identity |
| `RingRenderProfileTest` | Shared handoff values, texture/mesh budgets, and whole-ring clamping |
| `RingEntityTrackingTest` | Existing pairing is retained only for a watched pending canonical destination; initial and out-of-window pairings remain rejected |
| `RingSkyCycleTest` | Fixed angle, reduced vanilla-sun size, noon/dawn/dusk/midnight tone keyframes, smooth interpolation, time wrapping |
| `RingTerrainAtlasTest` | Seam interpolation, colour/height interpolation, tile/disk round-trip, durable revision persistence/rollback rejection, idempotent duplicate-tile detection, independent render snapshots, completion, cache monotonicity, mapping-sensitive world hash, and frozen alpha-format incompatible-cache rejection |
| `RingAtlasHudProgressTest` | Whole-percent incomplete-Atlas label, 99% floor before completion, completion disappearance, and malformed-count rejection |
| `RingAtlasSurfaceInvalidationTest` | Presentation-X canonicalization, finite-Z exclusion, and stored-top relevance for terrain mutations |
| `RingAtlasRecaptureQueueTest` | Exact-cell deduplication, 64-cell bounded drain, and bulk overflow collapse into tile work |
| `RingAtlasPregenerationCursorTest` | X-major canonical enumeration, finite-Z coordinates, non-power-of-two circumference, atlas-backed resume/skip, checked totals, options, state transitions, and zero-work/restarted rate/ETA |
| `RingAtlasPregenerationSelectionTest` | Server-service retry seam: failed and shutdown-discarded selected canonical chunks remain selected without false retry advancement, partial storage resumes at its first missing chunk, and retry exhaustion is explicit without a `ServerLevel` fixture |
| `RingAtlasChunkRequestTest` | Ticket-backed radius-load retention, completed-result handoff, completed-load teardown without result resolution, exact-once cancellation/release, retryable release failure, and start/null-future cleanup |
| `RingAtlasJobReplacementPolicyTest` | Failed/cancelled/complete and headless-idle replacement candidates cannot orphan an outstanding request; clean terminal/idle jobs remain replaceable and active jobs are reused |
| `RingAtlasCommandPolicyTest` | Idle/failed durable restart, strict pause/resume, completed/active deduplication, and unsupported command cancellation |
| `RingAtlasDirtyTileQueueTest` | Final dirty tile stays published until the Fabric adapter drains it, including a completion transition in the same server tick |
| `RingAtlasPregenerationServiceStorageTest` | Fresh/partial/complete/corrupt format-6 service persistence seams: interrupted partial checkpoints resume without a byte rewrite, complete reload is idempotent, and corrupt current input is rejected |
| `RingAtlasPregenerationSchedulingPolicyTest` | Config-disabled `IDLE`, paused, saving, and cancelled handles cannot schedule chunks; only a running handle may request work |
| `AtlasPregenerationHeadlessPolicyTest` | Explicit headless startup suppresses normal background autostart and replaces only the unstarted config-disabled `IDLE` handle |
| `AtlasPregenerationReportTest` | Schema-2 loader-neutral terminal report validation requires complete evidence, a supported explicit terrain-noise mapping, or documented unavailable-identity sentinels |
| `HeadlessPrewarmEvidenceFilesTest` | Direct dedicated launch removes stale terminal/progress evidence before publishing a new headless job |
| `FabricHeadlessNetworkingAdmissionTest` | Platform-isolated compiled-bytecode assertion that Fabric's later array-backed JOIN listener rechecks headless admission and returns before settings/handshake work |
| `NeoForgeHeadlessPlayerAdmissionTest` | Platform-isolated active/inactive admission side effects plus a compiled-bytecode assertion that the cancellable NeoForge PlayerList guard runs at method HEAD, before play-packet buffering and the later settings injection |
| `RingSurfaceLodTest` | Texture-luminance colour correction, relief shading, flat-colour preservation, explicit missing-cell alpha, alpha-weighted periodic-X/clamped-Z mip filtering, one-pixel stability, malformed input rejection |
| `RingSurfaceBuildSnapshotTest` | Immutable atlas-content retention across live changes, colour-only height-fingerprint stability, and identity/revision rejection |
| `RingSurfaceMeshTest` | Production and safe-small shared-lattice continuity, exact physical seam closure, reference-height path, and incomplete-Atlas inner-rim returns |
| `RingSurfacePlaceholderTest` | Zero-cell deterministic opaque fallback, world identity, exact known-cell retention, bounded resampling, and smooth distance-decayed generated palette influence |
| `RingSurfaceMorphTest` | Clamped 750 ms transition timing and symmetric smoother-step progression |
| `RingSurfaceGenerationFogTest` | Heavy zero-coverage haze, smooth progress clearing, exact completed removal, and malformed-input clamping |
| `RingSurfaceMeshRefreshPolicyTest` | Partial-mesh reuse, height-fingerprint-driven complete-mesh refresh, and forced rebuilds across layout/completion transitions |
| `RingTerrainNoiseMappingTest` | Exact legacy vectors, annular seam/cardinals, orthogonal derivatives, mapping-cache isolation, complete-mapping carver identity, overflow rejection, and preset safety |
| `RingSurfaceSamplingContextTest` | Mapping-3 unit/scaled surface-noise seam continuity plus mapping-2 compatibility and thread-context teardown |
| `RingSeamTerrainAuditTest` | Broad contiguous join-wall rejection while retaining isolated natural cliffs |
| `RingWorldSettingsStorageTest` | Dimension-owned paths plus codec-backed format-1/2 migration that persists legacy terrain mapping while fresh format-3 settings use annular terrain |
| `RingTerrainAtlasServerStorageTest` | Dimension-owned server atlas path and legacy atlas migration source |
| `RingWorldCreationUiModelTest` | Small/Medium/Large presets, exact live maths, valid/custom previews, aggregated malformed/structural/cross-field validation, immutable-next-new-world confirmation, and geometry-aware monument state |
| `RingPhysicalPoseTest` | Cardinal physical position/basis, vanilla yaw/pitch conversion, and rendered local-up direction |
| `RingCompatibilityContractTest` | Versioned API/contract, baseline stack, case-normalized exact conflict matching, and immutable inventory |
| `RingProtocolIdentityTest` | Settings, acknowledgement, revisioned terrain-atlas, and pregeneration channel names remain synchronized with their wire layouts |
| `AtlasPregenerationUiModelTest` | Status-total validation, durable chunk presentation, state-aware controls, permissions, and explicit action/state wire values |
| `RingStrongholdPlacementTest` | Deterministic canonical placement, seam clearance, seed variation, supported circumference shapes, full-graph fitting, and narrow-band portal-room preservation |
| `RingStructurePolicyTest` | Stronghold bit plus monument request, pending/terminal result, and legacy-v1 disabled behavior |
| `RingMonumentPlacementTest` | Deterministic bounded candidate walk, canonical/finite bounds, seam/rim envelope, seed variation, and search exhaustion |

The #95 navigation checkpoint includes a disposable graphical acceptance
fixture on both loaders. It creates a fresh 2,048×416 world and checks
bidirectional seam pixels; player and white-banner decorations; decorations
from real world-added item frames on both seam sides; a scale-one map and its
locked result; seam-banner removal and restoration; and spawn, lodestone, and
recovery compass targets. The seam-equivalent exact-target assertion reuses
one compass wobble state for two seeded samples, avoiding a probabilistic
comparison between independent random offsets. It then performs Minecraft's
normal save/disconnect/reopen flow, verifies raw session teardown independent
of the active-level guard, and rechecks the scaled locked map's pixel and
decorations, the persistent live frame and framed map, persisted
spawn/lodestone/recovery targets, and the nearest-image compass calculation.
Each run decodes and validates every matching required PNG screenshot: it must
be at least 16×16 and no larger than 16,384×16,384 pixels, and contain
visible, non-uniform image content rather than merely existing. It then exits
itself:

```sh
./gradlew :runMapCompassCaptureClient --console=plain
./gradlew :neoforge:runMapCompassCaptureClient --console=plain
```

Fresh 2026-08-06 Fabric and NeoForge runs pass this expanded gate and each
produced all eight screenshots. The teardown assertion covers raw
geometry/camera/atlas state, atlas-control state, and complete-ring GPU
resources before the same-process reopen.

Run the qualified tasks separately so two graphical clients do not contend for
the same display. Their disposable outputs live under the ignored root and
NeoForge `run-map-compass-capture/` directories.

After PR #110, clean public `main` commit `8bb17914` again passed all 235 cases
on each loader and both explicit dedicated-server launches reached `Done`.
The resulting local runtime-jar SHA-256 values were
`5804931222db74590835f978084e6987da88f5bf40eecfe4d2289d9365c441ff`
for Fabric and
`53c5786dea95f75f46350ce6e4d77aa5a8ee0f9c75f49cdccc615b309accc277`
for NeoForge. These are headless development-build identities, not frozen
release candidates. Graphical and strengthened two-client acceptance remains
open.

The first #111 runtime slice replaces RingWorld Overworld `getRaidAt` with a
nearest-periodic active-raid scan while preserving vanilla's strict 9,216
squared-distance threshold. All 241 cases pass on each loader, and fresh
Fabric and NeoForge dedicated servers reached `Done` with the required
accessor/injection applied. The next integration checkpoint adds periodic POI
discovery and village tests, canonical centre creation/relocation, split
canonical wave-spawn readiness, periodic raider retention, and nearest-image
goal targets. Both loader builds and dedicated servers reach `Done` with all
four new mixins registered. This proves target application and startup only;
the opt-in deterministic dual-loader seam-raid fixture below must still be run
and its real completion evidence recorded.

### Two-phase seam-raid regression

This disposable dedicated fixture uses two real clients named `RingTesterA` and
`RingTesterB`. Its arm process creates two occupied HOME POIs at `C-2` and
`4`, starts a real omen raid, shortens only its pre-wave countdown, and saves
after its first vanilla wave. Its reload process opens the same world, checks
the saved raid/bossbar/raider state, makes a real raider navigate naturally
through the seam, then completes the real victory path and verifies Hero of
the Village.

Prepare one isolated loader directory. For each phase, leave the server running
in terminal 1 and start one client in each of terminals 2 and 3; restart the
clients between phases:

```sh
scripts/prepare_raid_seam_fixture.sh fabric
# terminal 1
./gradlew :runRaidSeamArmServer
# terminal 2
./gradlew :runRaidSeamClientA
# terminal 3
./gradlew :runRaidSeamClientB
# after the arm server exits, stop its two clients
# terminal 1
./gradlew :runRaidSeamReloadServer
# terminal 2
./gradlew :runRaidSeamClientA
# terminal 3
./gradlew :runRaidSeamClientB
```

The leading `:` is required because both loader projects own identically named
run profiles. For NeoForge, replace the commands with `:neoforge:runRaidSeamArmServer`,
`:neoforge:runRaidSeamReloadServer`, `:neoforge:runRaidSeamClientA`, and
`:neoforge:runRaidSeamClientB`, after
`scripts/prepare_raid_seam_fixture.sh neoforge`.
The NeoForge preparation disables only its separate early splash window in the
two disposable clients; Minecraft's real game windows still launch and render.

The arm server must log `[raid-seam] arm-save-ready=true`; the reload server
must log `[raid-seam] PASS` and no `[raid-seam] FAIL`. The fixture is opt-in
only (`-Dringworld.raidSeamTest=true`) and never runs in ordinary worlds.

The 2026-08-02 acceptance run passed both phases on Fabric and NeoForge. Each
arm phase created canonical centre `X=1`, retained both seam-side players in
the bossbar, spawned and saved a real first wave, and logged
`arm-save-ready=true`. Each reload first verified both occupied POIs survived
from disk, then restored the same raid and tagged raider,
observed a natural high-to-low canonical fold, completed vanilla victory, gave
RingTesterA Hero of the Village, and logged `[raid-seam] PASS` with no final
`FAIL`.

Inspect machine-readable results under:

```text
build/test-results/test/
```

Compatibility contract changes must also launch one baseline client through
resource/shader initialization and inspect `latest.log` for false conflict
matches. Positive matching stays a pure test; do not install an unsupported
third-party renderer into a release fixture solely to prove the warning.

## Atlas fidelity benchmark

Run the production step 8/4/2/1 cost comparison under Java 25:

```sh
./gradlew runAtlasFidelityBenchmark --console=plain
```

This does not launch Minecraft. It exercises real format-6 save/load and tile
encoding plus the renderer-equivalent CPU texture, relief, and mip pipeline.
The ignored report is written to
`build/reports/ringworld/atlas-fidelity.md`; interpretation and the retained-
profile decision are in
[`ATLAS_FIDELITY_BENCHMARK_2026-08-01.md`](ATLAS_FIDELITY_BENCHMARK_2026-08-01.md).

## Atlas map GUI-scale regression

From an isolated `run-atlas-ui` directory configured for a safe-small world
with `testMode=true` and `pregenerateTerrainAtlas=false`, run one loader's
fixture:

```sh
./gradlew runAtlasUiClient --console=plain
./gradlew :neoforge:runAtlasUiClient --console=plain
```

The loader-specific opt-in client resets its own saves/cache/log/screenshots, sets GUI scale 4,
hides the development coordinate overlay, waits three rendered frames after
every screen change, and records an unobstructed pause-menu,
map, confirmation, running, a partial-atlas gameplay view, background
close/reopen, pause/resume, cancel/retry, and complete screens as
`atlas-ui-*.png`. It presses the actual confirmation widget, stops after
`[atlas-ui-test] PASS`, and its finalizer verifies the marker plus all eleven
PNGs. After completion it also places and removes a sampled high surface block,
requiring two changed tile broadcasts and ordered durable revision commits
before passing. The initial map stage additionally requires the embedded
`1.0 · 1.0.0+mc26.1.2` label and fresh-world
`Worldgen: annular-complete-v2 (4)` identity. It is a real integrated-server test:
generation remains active because `RingWorldMapScreen` is explicitly
non-pausing. Keep its run directory ignored and do not point it at a personal
Prism instance or a production world.

The 2026-08-02 NeoForge 26.1.2.87 run completed in 2m32s. It produced all
eleven required screenshots, generated and durably verified 13,312/13,312
atlas cells, then committed the gold-block placement and removal as ordered
atlas revisions 12 and 13 before reporting `[atlas-ui-test] PASS`. Fabric and
NeoForge therefore exercise the same player-facing atlas workflow; only their
fixture startup and lifecycle registration are loader-owned.

The 2026-08-10 Fabric rerun after adding the menu identity lines passed all
eleven captures and the revisioned-edit probe. Its initial GUI-scale-4 capture
visibly showed `1.0 · 1.0.0+mc26.1.2` and
`Worldgen: annular-complete-v2 (4)` without overlapping the progress fields or
controls.

For an exact 26.1.x source-ABI cell with fully isolated Gradle/build/game
state and immutable evidence, run one graphical cell at a time:

```sh
python3 scripts/run_gradle_atlas_ui_qualification.py --cell 26.1-fabric
```

The equivalent `-neoforge` cell selects its loader task. Optional reviewed
dependency-cache and wrapper-ZIP inputs use the same flags and restrictions as
the creation UI runner. A PASS requires the actual integrated Minecraft
client, exactly one disposable world, all eleven PNGs, complete Atlas state,
and the fixture's revisioned block placement/removal terminal marker. It is
source-ABI evidence, not a production-launcher or frozen-candidate claim.
Both loader preparers write `onboardAccessibility:false` into the disposable
profile. Without it, a genuinely fresh game directory opens Minecraft's
accessibility onboarding over the automated Create World flow and never
exercises the fixture. The fixture must also wait for the real `TitleScreen`;
opening the editor from Minecraft's temporary startup `GenericMessageScreen`
allows the later title transition to discard it.
The Fabric entrypoint must return immediately while this opt-in fixture is
enabled, even after its world-creation invocation and before `client.player`
exists. Falling through to the older generic `testMode` launcher in that
interval creates a second integrated world and invalidates the connection.

After any mapping or game-version migration, also search active Java and
descriptor text for `class_`, `field_`, and `method_`. The active unobfuscated
26.1 source permits no intermediary residue. `ServerLevel` entity tick
eligibility is in the private synthetic `lambda$tick$0`; its exact descriptor
is documented in `MIXIN_MAP.md`. A clean compile alone is not evidence that a
required mixin still applies.

## 26.1 dedicated-server storage gate

The detailed historical storage evidence below began as Fabric evidence.
NeoForge now separately passes fresh dimension-owned storage, copied-world
layout/atlas restoration, the loader-selectable worldgen/reload matrix,
headless completion, and the dedicated topology/two-client gate.

Run storage migration gates only from a disposable worktree/run directory.
Never point them at `dist/`, the public service, or the only copy of a world.
The 2026-07-28 checkpoint demonstrated:

- a fresh 2,048×416 server world reached `Done`, saved settings and atlas under
  `dimensions/minecraft/overworld/data/ringworld/`, and stopped cleanly;
- a copied 1.21.11 RingWorld completed Mojang's upgrade and copied its legacy
  settings byte-for-byte into the new path without modifying the source;
- an invalid legacy atlas was rejected and rebuilt at the authoritative new
  path rather than being silently trusted;
- the exact required `ServerLevel.lambda$tick$0` mixin applied at runtime.

These are server/storage gates only. They do not replace `runClient`, visual,
seam, or two-client multiplayer validation.

## Headless atlas prewarm dedicated-server gate

On first use the preparation task creates an ignored
`run-headless-prewarm/eula.txt` with `eula=false` and stops. Review Mojang's
EULA, set `eula=true`, then run a fresh safe-small prewarm:

```sh
./gradlew :runHeadlessPrewarmServer --console=plain
```

To exercise a fresh production-default layout instead of safe-small, add
`-PringHeadlessPrewarmCircumference=16384 -PringHeadlessPrewarmWidth=256`.

For a copied fixture, first place the source save under ignored `run/saves/`,
then run:

```sh
./gradlew :runHeadlessPrewarmServer --console=plain \
  -PringHeadlessPrewarmSource="save-folder-id"
```

The preparation task deletes and writes only `run-headless-prewarm/world`; it
checks the source identifier, reads the source only to copy it, and never
launches the source. The dedicated adapter suppresses ordinary background
autostart, disables empty-server pausing, immediately disconnects accepted
player joins, and uses normal
server ticks. Inspect `world/ringworld-prewarm/progress.json` and
`result.json`. Schema 2 records the explicit terrain-noise mapping beside the
mapping-sensitive world/layout hashes. The finalizer accepts only
`"status": "COMPLETE"`, a supported mapping, and exact totals, so corrupt,
incompatible, ordinary-existing, interrupted, and failed fixtures fail even if
Minecraft exits zero. Stop during generation to verify checkpoint/restart: the
first result is `INTERRUPTED` and the next run resumes from durable atlas cells.
Use `-PringHeadlessPrewarmResume=true` for that second run; the default fresh
task deliberately deletes its disposable world.

The Phase 4 pure recovery contract is covered by:

```sh
PYTHONPATH=scripts python3 -m unittest \
  scripts/test_minecraft_atlas_recovery_qualification.py \
  scripts/test_minecraft_atlas_recovery_persistence.py \
  scripts/test_external_runtime_atlas_recovery_plan.py \
  scripts/test_external_runtime_atlas_recovery_executor.py \
  scripts/test_external_runtime_atlas_stage_runner.py \
  scripts/test_minecraft_worldgen_qualification.py \
  scripts/test_external_runtime_worldgen_plan.py \
  scripts/test_external_runtime_worldgen_executor.py \
  scripts/test_external_runtime_worldgen_stage_runner.py \
  scripts/test_run_worldgen_qualification.py
```

This command does not launch Minecraft or use the network. The process tests
use bounded local Python children to exercise stop, self-halt, fatal-output,
port, and immutable-log behavior. The combined gate rejects report-only claims
by requiring independent saved-settings and Atlas-file observations, the same disposable
world and Atlas path across stages, a real partial checkpoint, exact complete
totals, clean exits, and ordered interruption/recovery ledgers. The real
external gate passes all six 26.1.x cells with one unchanged jar per loader.
Fabric runs are `20260813T091340Z-0f6a75a06e36` (26.1),
`20260813T084030Z-a3030342d49c` (26.1.1), and
`20260813T084918Z-2a61b8523682` (26.1.2). NeoForge runs are
`20260813T092207Z-56b8d1593d37` (26.1),
`20260813T085803Z-abc3ee37973d` (26.1.1), and
`20260813T090427Z-21cef9b5920b` (26.1.2). Every run independently captured
settings, the partial restart bytes, the complete Atlas, both schema-2
reports, bounded logs, and ordered clean-exit markers. This is the
Atlas-recovery nightly slice only.
The persistence tests use hand-built gzip NBT and Atlas-v6 data and include a
known Java hash vector. A local read-only check against the 26.1 NeoForge quick
world independently reproduced layout fingerprint `4064118068185880929` and
Atlas world hash `8665210144080158345` from its persisted settings. Real
external Fabric and NeoForge interruption/recovery runs now supply runtime
evidence; synthetic static results alone remain insufficient for any other
nightly fixture.

From a clean pushed Java 25 checkout, run one real external recovery fixture
with the exact quick evidence that supplied its frozen candidate:

```sh
python3 scripts/run_atlas_recovery_qualification.py \
  --cell 26.1-fabric \
  --quick-run-id 20260813T072608Z-b7c68e555818
```

The analogous NeoForge proof uses `--cell 26.1-neoforge` and quick run
`20260813T080722Z-377cfb994c93`. These are disposable local qualification
worlds; they do not connect to or mutate the live demo server.

The production-style worldgen/structure nightly slice is:

```sh
python3 scripts/run_worldgen_qualification.py \
  --cell 26.1-fabric \
  --quick-run-id 20260813T072608Z-b7c68e555818

python3 scripts/run_worldgen_qualification.py \
  --cell 26.1-neoforge \
  --quick-run-id 20260813T080722Z-377cfb994c93
```

Each command installs three fresh official dedicated runtimes. Production
fresh/reload alone shares a world; the seam and terminal-policy seeds are
separate. The processes self-halt after the existing stronghold/worldgen
fixture emits its matrix, monument, and PASS records. The executor rejects
duplicate records, independently decodes saved settings, captures every log,
and validates the four-stage aggregate before writing terminal evidence.
Static tests do not substitute for these real commands. Fabric passes in runs
`20260813T073235Z-1e16c008e584` (26.1),
`20260813T083349Z-0cdcffa76005` (26.1.1), and
`20260813T083518Z-b942314e7e0d` (26.1.2). NeoForge passes in runs
`20260813T082128Z-c2fae65dec2c` (26.1),
`20260813T083644Z-e7ed932a1499` (26.1.1), and
`20260813T083822Z-03549862d588` (26.1.2). Patch selections validate their own
quick evidence but deliberately reuse the one retained oldest-ABI jar; zero
or multiple loader candidate roots are rejected.

The seam-height audit rejects a broad discontinuity, including a sub-threshold
wall whose average adjacent-column delta exceeds two blocks. It deliberately
allows isolated natural cliffs. A real production 16,384x256 qualification
probe observed one isolated 12-block step, a longest run of one, and average
delta 1.199; treating that as a full map-boundary wall was a false positive.

The external graphical creation-settings fixture is separate from the Gradle
development client:

```sh
python3 scripts/run_creation_ui_qualification.py \
  --cell 26.1-fabric \
  --quick-run-id 20260813T072608Z-b7c68e555818 \
  --prism-archive /absolute/path/PrismLauncher-macOS-11.0.3.zip \
  --java /absolute/path/to/java-25/bin/java
```

It creates a fresh offline Prism root only below the new qualification cell,
loads the exact frozen candidate and selected loader versions, and must produce
all thirteen existing creation-UI PNG captures plus a PASS marker and clean
exit without a world. It rejects a Prism archive whose SHA-256 is not
`b8e06ef55ec78fceddfa9f4270b3d4d93f2606b83f70ad6a2c6dde90f2b65408`.
The pure gate is:

```sh
PYTHONPATH=scripts python3 -m unittest \
  scripts/test_external_graphical_creation_ui.py \
  scripts/test_run_creation_ui_qualification.py
```

Fake Prism children and synthetic PNG headers used by that unit test are
contract evidence only, never a graphical-client PASS.

Fresh format-3 fixtures default to `terrainNoiseMapping=4`. A deliberate copied
legacy-world run must set `-PringHeadlessPrewarmExpectedTerrainNoiseMapping=1`;
the NeoForge equivalent is
`-PringNeoForgeHeadlessPrewarmExpectedTerrainNoiseMapping=1`. Missing, unknown,
or unexpected mapping evidence is a verifier failure.

NeoForge provides the same contract through its own isolated directory and
loader lifecycle adapter:

```sh
./gradlew :neoforge:runHeadlessPrewarmServer --console=plain
```

On first use the task creates
`neoforge/run-headless-prewarm/eula.txt` with `eula=false` and stops. Review
Mojang's EULA, then set `eula=true` once.
Use `-PringNeoForgeHeadlessPrewarmSource=<save-folder-id>`,
`-PringNeoForgeHeadlessPrewarmResume=true`, and
`-PringNeoForgeHeadlessPrewarmResult=<filename.json>` for copy, resume, and
terminal-result variants. Fresh geometry overrides are
`-PringNeoForgeHeadlessPrewarmCircumference=<blocks>` and
`-PringNeoForgeHeadlessPrewarmWidth=<blocks>`. A fresh 2,048×416 NeoForge run completed all 3,328
chunks/13,312 cells and the verifier accepted only its identity-bearing
`COMPLETE` report. Fabric also completed an exact production prewarm on
2026-08-06. On 2026-08-10 fresh format-3 production runs completed separately
on Fabric and NeoForge: each generated all 16,384 chunks / 65,536 cells, wrote
schema-2 mapping-2 `COMPLETE` evidence, saved normally, and passed a subsequent
complete-atlas resume/load run. Fabric took 38m16s at about 29 cells/s;
NeoForge took about 41m at about 27 cells/s.

The structure matrix is also loader-selectable:

```sh
python3 scripts/run_worldgen_structure_matrix.py --loader fabric
python3 scripts/run_worldgen_structure_matrix.py --loader neoforge
```

The runner uses root-qualified `:runStrongholdTestServer` for Fabric and
`:neoforge:runStrongholdTestServer` for NeoForge. Do not remove the leading
colon: the unqualified task name matches both projects and can corrupt the two
disposable worlds by launching both loaders together. On the production reload,
the cardinal gate still proves periodic base-height/base-column equality but
does not compare a pre-feature query to an already feature-populated chunk
heightmap.

Each loader uses separate worlds, logs, and report directories. The NeoForge
matrix passes fresh and exact-reload production 16,384×256 plus the two
safe-small policy cases.

The copied `ordinary-world-rejection` fixture is expected to fail its Gradle
finalizer and retain Minecraft's original startup error. Its acceptance
evidence is `result.json` with `"status": "REJECTED"`,
`"identityAvailable": false`, all identity/totals zero, a null atlas path,
and the immutable-settings rejection reason. This occurs at the
`ServerLevel` constructor tail before Fabric level-load events; it must not
create settings or use bootstrap geometry to continue the invalid world.

The 2026-08-01 copied legacy-open-proof gate copied a 66-file, 47,931,005-byte
1.21.11 source into the ignored fixture, recorded SHA-256 aggregate
`f9e61702b230423b05c94b609cc8d4a4451d6c0ed3bd701e7b9479078b3fa265` before
and after, migrated settings only in the destination, rejected its incompatible
legacy atlas, and completed 2,000 chunks / 8,000 cells at about 80 cells/s.

## Guaranteed-structure dedicated-server gate

On first use the preparation task creates ignored
`run-stronghold-test/eula.txt` (or NeoForge's corresponding subproject path)
with `eula=false` and stops. Review Mojang's EULA, set `eula=true`, then run:

```sh
./gradlew runStrongholdTestServer --console=plain \
  -PringStrongholdTestSeed=ringworld-regression-1 \
  -PringStrongholdTestCircumference=16384 \
  -PringStrongholdTestWidth=256 \
  -PringStrongholdTestWallHeight=160
```

The preparation task deletes only the disposable test world and stale result
log, writes the selected layout and seed, enables the ocean-monument request,
and disables atlas pregeneration. The server must
log `[stronghold-test] PASS`; a missing marker or a logged failure makes the
Gradle verification task fail. The gate verifies the deterministic canonical
start, complete piece-graph and portal-room bounds, all 12 generated frame
blocks, any minimal whole-graph boundary fit, an activatable frame orientation,
periodic `getBaseHeight` and full
`getBaseColumn` equality at canonical X and X+C (including X=0), and canonical
`getBaseHeight` agreement with noise-complete `WORLD_SURFACE_WG` terrain at
remote deterministic positions (the shared sampler path used by structure
height placement). It deliberately keeps X=0 and X=C-1 as periodic
base-height/base-column checks only because spawn preparation can advance
either seam neighbour without leaving it resident in `getChunkNow`; the
remaining cardinal probes reject an already loaded chunk before comparing
fresh noise terrain. The gate also verifies a nearest-periodic locate target
and Eye target continuity after a canonical seam fold. It also requires the
saved monument request to resolve to a canonical valid start, verifies its
complete bounds, forces it without an alias chunk, locates it from an adjacent
presentation chart, and exercises unexplored-reference semantics. It then generates both
exterior neighbour rows before requiring textured rim material through the
saved wall top and void immediately beyond both rims. Terrain at or above the
top remains vanilla and is intentionally not asserted. Run again with
`-PringStrongholdTestResume=true` to retain the disposable world and verify the
same terminal policy, structure start, periodic locate, and used-reference
behavior after a process restart.

Evidence on 2026-08-01 passed eight production seeds with complete piece-graph
bounds, including `height-query-production`, whose pre-fit terrain-adjusted
bounds reached Z=-132 and were fitted to Z=-128. It also
passed 2,048×416 safe-small, 15,552×4,096 non-power-of-two geometry,
activation, and save/reload. The
1,024-block circumference is a geometry-helper fixture only; full-height
dimension validation correctly rejects it, and 2,048 is the smallest active
safe preset.

Issue #24 added two fresh isolated dimension cases to this gate with atlas
pregeneration disabled: the aligned playable minimum `2016×256` (126×16
chunks, 8,064 atlas cells) and wide/custom-wall `4096×2048`, wall height 192
(256×128 chunks, 131,072 atlas cells, saved wall top Y=128). Both logged
`[stronghold-test] PASS`, matching canonical/periodic
height queries, bounded stronghold and portal-room geometry, an activatable
portal, folded Eye continuity, both textured rims through their saved height,
and generated exterior void.

Issue #149 supersedes that wide layout for fresh format-3 admission: annular
terrain cannot cross radius zero, so a width must leave at least a 32-block
noise radius after the 64-block query margin. On 2026-08-10 both loader gates
passed a fresh 16,384×256 annular world for seed `ringworld-regression-1`.
Each emitted matching base-height/base-column and generated-terrain evidence at
X `0`, `4096`, `8192`, `12288`, and `16383`, at Z `-120`, `0`, and `119`,
then passed all biome-family, seam-mineshaft, rim, monument, stronghold, and
End-portal checks. Reproduce sequentially with:

```sh
./gradlew :runStrongholdTestServer --console=plain \
  -PringStrongholdTestSeed=ringworld-regression-1 \
  -PringStrongholdTestCircumference=16384 \
  -PringStrongholdTestWidth=256 \
  -PringStrongholdTestWallHeight=160 \
  -PringStrongholdTestResume=false -PringWorldgenMatrix=true

./gradlew :neoforge:runStrongholdTestServer --console=plain \
  -PringStrongholdTestSeed=ringworld-regression-1 \
  -PringStrongholdTestCircumference=16384 \
  -PringStrongholdTestWidth=256 \
  -PringStrongholdTestWallHeight=160 \
  -PringStrongholdTestResume=false -PringWorldgenMatrix=true
```

## Multi-seed worldgen and structure seam matrix

After accepting the ignored stronghold test server's local EULA, run:

```sh
python3 scripts/run_worldgen_structure_matrix.py
```

The runner executes production and safe-small seeds, reloads the production
save before another case can replace it, and checks the emitted record against
the requested immutable layout. It samples canonical X and X+C through the
installed periodic climate sampler, generates four complete chunk columns on
both sides of the seam across the finite width, and audits caves, ores, logs,
structure starts/bounds, references, loot containers, and structure spawn
overrides. It requires all 14 defined major biome families in aggregate, at
least one actual seam-crossing structure, both satisfied and bounded-
unsatisfied monument policy outcomes, and exact stable evidence after reload.

Parser and aggregate-policy tests run separately with:

```sh
python3 scripts/test_run_worldgen_structure_matrix.py
```

Machine-readable results are ignored under
`build/reports/ringworld-worldgen-matrix/`. Exact evidence and limits are in
`WORLDGEN_STRUCTURE_MATRIX_2026-08-01.md`. The normal
`runStrongholdTestServer` remains strict: without
`-PringWorldgenMatrix=true`, a requested but unsatisfied monument fails it.

On 2026-08-10, the root-qualified runner passed the complete format-3 annular
matrix independently on Fabric and NeoForge: production fresh/reload records
were stable, all 14 major biome families were present, and the aggregate
cave/ore/tree/loot, seam-crossing structure, satisfied monument, bounded
unsatisfied monument, stronghold, portal, rim, and exterior checks passed.

Issue #158 introduces mapping 3 for fresh worlds. Focused fresh
2,048×256 Fabric and NeoForge matrices passed on 2026-08-10 with 128 generated
seam-strip chunks each and `seamTerrain=Report[largestDelta=0, cliffColumns=0,
longestCliffRun=0, averageAbsoluteDelta=0.0, passes=true]`. A fresh production
NeoForge stronghold/cardinal run also passed with mapping 3. The complete
multi-seed production matrix remains required before release-candidate freeze.

The production visual-parity gate now requires both the natural forward seam
capture and a `seam-join` look-back from canonical X=2 toward C-1. The latter
is the terrain-join view: the forward crossing alone cannot reveal a wall left
behind the player. The F3 RingWorld group reports the persisted terrain
mapping; current fresh-world evidence must show `annular-complete-v2 (4)`.
Values 1, 2, and 3 identify older worlds whose generator identity is
deliberately preserved. Fresh production mapping-3 Fabric and
NeoForge runs passed this new view on 2026-08-10. The captured real chunk
terrain crosses the join without a flat height wall; Fabric recorded 847 seam
motion frames at 8.49 ms average and NeoForge recorded 846 at 8.44 ms average,
with one frame over 50 ms in each run.

The uploaded seed `-4558730636853595596` exposed a gap in that mapping-3
qualification: vanilla's direct `BlendedNoise` leaf still consumed flat X/Z,
and the old broad-cliff audit ignored a nine-block wall because it was below
the twelve-block cliff threshold. Mapping 4 includes that leaf in the annular
router and the strengthened gate rejects average join mismatch above one
block. On the exact 16,384x256 reproduction, Fabric and NeoForge both report
`largestDelta=8`, no cliff columns, and average absolute join delta
`0.35365853658536583`; the isolated maximum remains natural variation while
the broad wall is gone. Mapping-3 saves are not silently migrated.

The post-#148/#157 biome-flavoured placeholder and 750 ms texture-morph
production visual-parity runs passed on both loaders on 2026-08-10. Fabric
streamed live Atlas revisions, completed the natural seam and both rim
captures, and recorded 831 motion frames averaging 8.65 ms with two frames over
50 ms. NeoForge visibly rendered 9.4% and 62.5% partial states on the opaque
reference mesh before the exact complete texture and detailed mesh replaced
them; it completed the same seam and rim captures and recorded 855 motion
frames averaging 8.41 ms with one frame over 50 ms. Both runs linked the
two-texture shader, published multiple revisions, and exited cleanly without a
GPU texture-lifetime failure. The captures certify the completed handoff; the
intermediate percentage markers and renderer logs certify the progressive path.
The later progress-haze and neutral bridge-wall refinement passed the same gate
on both loaders. Fabric exercised 12.5% and 71.9% partial states before
completion and recorded 845 motion frames averaging 8.51 ms with one over
50 ms. NeoForge exercised the partial path through 71.9%, completed the same
seam/rim captures, and recorded 847 frames averaging 8.42 ms with one over
50 ms. This proves shader linkage and teardown; subjective fog density and wall
colour still require owner review in a deliberately incomplete world.

## 26.1 integrated safe-small client gate

The 2026-07-28 isolated Java 25 client gate first confirmed that every startup
mixin and shader resource loaded to the UI. It then ran the destructive
2,048×416 creative harness twice:

- a no-pregeneration topology run completed two natural wraps and every
  representative gameplay/rim probe with 8.37/8.41 ms seam/rim averages and
  no frames over 50 ms;
- a full-pregeneration run completed all 13,312 atlas cells at roughly 82
  cells/sec, built a 2,048×416 GPU texture and 79,872-vertex surface, and saved
  both tangent and radial-up complete-ring captures;
- both full-atlas natural crossings preserved yaw/pitch and emitted zero
  correction packets; canonical storage, block interaction, entities,
  projectile collision, boat, AI, fluid, explosion, collision, rim, wall top,
  and exterior void all passed;
- the full-atlas run averaged 8.41/8.37 ms and recorded one isolated frame over
  50 ms in each measured phase while generation/upload work was active.

This establishes the safe-small functional renderer and gameplay gate. The
subsequent 6/12/28 safe-small matrix and complete 16,384×256 tangent/radial
projection gate are reviewed in
`VISUAL_HANDOFF_REVIEW_2026-08-01.md`; colour, live/LOD handoff, local proxy
exclusion, width-edge alignment, and ordinary-distance far-depth coverage pass.
Cold-start resource pressure and broader manual visual compatibility remain
separate release-hardening work.

The 26.1 projection task uses Minecraft's in-process world-open flow. Its
source value is the save-folder identifier, not the world-list display name.
The task preflights `run/saves/<identifier>/level.dat` before it starts, then
opens only an ignored copy. A missing or display-name value fails clearly
instead of leaving a client at the menu. This is a non-destructive existing-save
join; it resumes the copied world's atlas rather than creating another world.

## Curved rigid-object capture

Run the isolated live-renderer regression with Java 25:

```sh
./gradlew :runCurvedObjectCaptureClient --console=plain --no-daemon
./gradlew :neoforge:runCurvedObjectCaptureClient --console=plain --no-daemon
```

Keep the leading `:` on the Fabric task. Without it, Gradle selects identically
named tasks in every project and may launch the Fabric and NeoForge graphical
clients concurrently; macOS can then stall one client during resource loading.
Run the two commands sequentially.

Each loader's preparation task deletes only its ignored curved-object runtime
world, screenshots, and atlas cache, then creates a safe-small creative world.
It builds a curved stone-brick strip with a chest, lectern book, sign, ender
chest, two-part bed, shulker box, banner, copper golem, item, boat, cow, and
zombie, captures them from X=0.5 and X=32.5, and exits.
Both frames must show the rigid models seated on the same curved surface; the
nearer frame must not show them rising from the platform. The run also strictly
applies the `LevelRendererMixin` descriptors. Before either frame can pass, the
client must contain the expected chest, lectern, sign, bed, ender-chest,
shulker-box, and banner block entities
and report all visible sections rendered; a 1,200-tick deadline turns missing
chunks into `result=FAIL` instead of accepting an empty-sky screenshot.
Evidence is written under `run-curved-object-capture/screenshots/` for Fabric
and `neoforge/run-curved-object-capture/screenshots/` for NeoForge; both remain
ignored.

NeoForge queues RingWorld settings immediately behind the vanilla play-login
packet. Its normal player-login event occurs after the initial packet buffer is
flushed, which is too late: canonical edge chunks can otherwise be rejected by
a fresh client before it knows the periodic geometry. The capture deliberately
starts from a fresh world and therefore guards this packet-ordering boundary.
Explicit headless prewarm is the exception: a cancellable admission injection
runs at `PlayerList.placeNewPlayer` HEAD, before a play listener or packet buffer exists, so rejection sends no
RingWorld settings, creates no handshake entry, and cannot expose atlas
metadata. `NeoForgeHeadlessPlayerAdmissionTest` checks both admission outcomes
and the exact mixin targets; the ordinary graphical fixture continues to prove
the allowed login order.

Fabric retains its loader event admission boundary. Its JOIN event is
array-backed, so the networking listener must independently recheck the active
headless run even after the earlier lifecycle listener disconnects the player.
`FabricHeadlessNetworkingAdmissionTest` inspects the compiled listener and
requires the admission predicate, conditional return, and settings send in
that order.

## Local automated smoke world

The opt-in harness is destructive to its own test world: it teleports players,
clears camera/flight lanes, places fixtures, breaks blocks, spawns entities,
and changes time. Use only an isolated Gradle run directory or disposable
packaged world.

Configure `run/config/ringworld.properties` before launch:

```properties
widthBlocks=416
circumferenceBlocks=2048
wallHeightBlocks=160
testMode=true
testViewDistanceChunks=28
pregenerateTerrainAtlas=true
```

`testMode` does not itself override dimensions. The 128×26-chunk safe-small
test size comes from 2048×416 bootstrap values. It keeps about 70 radial blocks
of clearance above the top vanilla build plane while retaining the visibly
tight curvature of the retired 1600×320 fixture. The parameterized geometry
matrix is tracked in
[`DIMENSION_SCALING_PLAN.md`](DIMENSION_SCALING_PLAN.md).

Then run:

```sh
./gradlew runClient
```

The client creates a creative world named `RingWorld Automated Test` using the
fixed seed `-2162056627494116761`.

The harness exercises:

- ordinary terrain generation;
- a client-only canonical-bed getter probe across the seam; it verifies the
  replicated sleeping position resolves to the nearest presentation image
  without writing a sleeping position to the integrated server;
- creative/flying test setup;
- two natural seam crossings;
- camera yaw/pitch continuity and corrective-packet count;
- seam-adjacent explicit setup teleport remains on the nearest presentation
  image without evicting continuously watched destination chunks;
- seam block break/update;
- static and moving entity visibility/querying;
- projectile/entity collision;
- unoccupied boat motion;
- ground AI pathing;
- water scheduled ticks;
- explosion exposure/impulse;
- proximity particles;
- canonical chunk-holder audit;
- periodic block collision;
- exterior void and textured five-block rim;
- fixed small sun and its noon, warm-dusk, and cool-midnight tones;
- upward ring visibility and frame pacing.

The arrow, moving item, navigator, and boat intentionally start on the high-X
side and continue into canonical chunk zero. They are a shared regression for
entity simulation eligibility as well as individual gameplay systems. A
failure where several stop around X=0 while retaining velocity indicates a
stale chunk-level simulation graph, not four unrelated collision failures.

For the 16,384×256 production seam probe, log both canonical seam chunks as
manager-loaded, distance-ticking, and position-ticking at arm and result. Two
consecutive automated passes must record a projectile hit, a moving item near
canonical X≈6, and the navigator near X≈1.29. This specifically guards the
former post-fold failures where an entity-load request downgraded a seam chunk
from `TICKING` to `TRACKED`, or a mob retained path/stuck state from its old
presentation image.

The second circuit keeps both seam approaches at quarter-block motion in the
cleared Y=120 seam lane. Up to a 4,096-block circumference, its non-seam middle
flies near the build ceiling with a circumference-derived step clamped to 4–8
blocks per tick, waits for chunks ahead, and logs progress every 600 ticks.
Larger matrix cases use an explicit test-setup teleport to sample the far-side
chart, wait for the seam approach, and then perform the same natural
quarter-block crossing. This keeps production and long-ring topology tests
bounded without pretending that thousands of unrelated generated chunks are a
seam requirement.

Expected screenshots in `run/screenshots/`:

```text
ringworld-automated.png
ringworld-seam.png
ringworld-second-wrap.png
ringworld-boundary.png
ringworld-fixed-sun-day.png
ringworld-tone-dusk.png
ringworld-tone-night.png
ringworld-visible-arch.png
ringworld-visible-up.png
```

The filename `ringworld-visible-arch.png` predates the active texture renderer.
It now captures the tangent/along-ring projection and live/LOD handoff.
`ringworld-visible-up.png` separately captures the radial view through the
ring diameter; neither direction substitutes for the other on a large layout.

Search `run/logs/latest.log` for `[test]`. A useful pass includes true values
for:

- terrain present;
- circumference wrap;
- canonical player plane;
- seam block interaction;
- periodic entity query;
- moving entity canonicalization;
- second seam wrap;
- block collision;
- projectile collision;
- vehicle crossing;
- AI path;
- fluid flow;
- explosion reach;
- exterior void, rim present, and shortened top.

The initial terrain and three sun-tone screenshots may be captured while
pregeneration continues. The final `ringworld-visible-arch.png` capture waits
for a complete current-world atlas before exercising the configured
`testViewDistanceChunks` live/LOD handoff (normally 6, 12, or 28); only then
does the harness reduce view distance for seam traversal. Its pitch is derived
from the configured distance, current camera height, and sampled target
surface on the physical cylinder so each image actually intersects its claimed
handoff rather than using one hard-coded upward angle. When pregeneration is
explicitly disabled and no complete client cache exists, the harness logs a
skipped LOD capture after 600 ticks and continues its topology/rim probes at
six chunks. A skipped capture is not LOD evidence for that matrix case.

## Large-ring projection capture

For a non-destructive three-direction capture of an existing complete-atlas
world under `run/saves/`, use:

```sh
./gradlew :runProductionProjectionClient \
  -PringProjectionWorld="production-ring-save-folder" \
  -PringProjectionViewDistanceChunks=16 \
  -PringProjectionEnvironment=noon
```

The client waits for the current atlas to reach 100%, then writes:

```text
run-production-projection/screenshots/ringworld-projection-tangent.png
run-production-projection/screenshots/ringworld-projection-handoff.png
run-production-projection/screenshots/ringworld-projection-up.png
```

The Gradle task validates that `ringProjectionWorld` is an exact source
save-folder ID containing `level.dat`, then copies that save into the ignored
`run-production-projection/saves/` directory. It opens the copied destination
in-process via Minecraft's world-open flow, rather than relying on
`--quickPlaySingleplayer`; the source save is never opened or changed. Override
the destination (also a single folder ID) with:

```sh
-PringProjectionDestination="projection-copy-folder"
```

After the atlas is complete, the runner requests the deterministic,
server-authoritative centered spectator pose `(C/4, 120, 0.5)` and waits for
the surrounding sections to render. It does not inherit a stale saved player
position, and it moves only the disposable copy.

`ringProjectionViewDistanceChunks` defaults to 16 and is clamped to Minecraft's
supported 2–32 test range. `ringProjectionEnvironment` accepts `noon` (the
default), `dusk`, `night`, or `rain`; non-noon captures include that name in
their screenshot filename. The tangent capture looks horizontally along
canonical +X, where the cylinder most visibly encountered the old chunk-derived
far cutoff. The handoff capture derives its pitch from the atlas height at the
nominal live-distance edge. The radial capture looks straight up through the
largest surface diameter. The disposable copy is normalized to the selected
time/weather mode with a frozen clock. The log records the active level far
plane, opposite
reference-surface distance, far width-edge distance, geometry, texture size,
mesh vertex count, and per-view average/maximum/over-50-ms frame metrics. The
probe changes options, time, weather, and camera pose only in the copy; it does
not move the player or edit blocks. Its isolated `options.txt` also sets the
tutorial step to `none`, keeping release-evidence captures free of Minecraft's
first-world movement toast without changing a real client profile.
The refreshed 2026-08-08 noon evidence was visually inspected without that
overlay. Fabric averaged 9.021/8.599/8.356 ms across tangent/handoff/radial-up;
NeoForge averaged 8.903/8.694/8.356 ms.

Preparation removes prior logs, screenshots, cache, and crash reports from the
ignored run before launch. The task's `verifyProductionProjectionClient`
finalizer then requires the exact successful terminal marker, rejects any
failure marker, and decodes the three expected PNGs for the selected
environment with bounded dimensions. Missing, stale, mislabeled, or corrupt
capture evidence fails the same Gradle invocation. Review the per-view frame
metrics as well; the verifier establishes completion and readable evidence,
not subjective visual parity.

`ringProjectionWorld` must be the folder directly below `run/saves/` and must
contain `level.dat`. It is intentionally required: do not substitute the
world's display name or point this task at a Prism/packaged instance. For an
interrupted 16,384×256 validation world, place or retain that isolated world
under `run/saves/`, pass its exact folder name, and preserve the resulting
`run-production-projection/logs/` and screenshot evidence locally. The task
logs both the selected copy ID and the point at which that copied world is
ready; it never enables the destructive test-mode/create-world automation.
While active it also disables pause-on-focus-loss and uses the test-client
inactive-frame policy, so moving the Gradle client behind another app cannot
pause the integrated server during atlas completion.

Production evidence recorded on the 26.1 branch: a copied 16,384×256 world
resumed from 32,900/65,536 atlas cells to 100% without a player lap, completing
the remaining 32,636 cells in about 13 minutes 22 seconds (about 41 cells/s
over the resumed interval). It emitted both tangent and radial-up captures and
reported a clean capture result. The enhanced harness then reused that complete
copy for reproducible 6/12/28 tangent, handoff, and radial-up captures with
frame metrics. The profile-4/profile-5 comparison is recorded in
`ATLAS_VISUAL_BASELINE_2026-08-01.md`. The later complete production generation,
recovery, lifecycle, multiplayer, resource, and safe-small/production 6/12/28
matrix is recorded in `ATLAS_RELEASE_GATE_2026-08-01.md`.

When the projectile probe fails, its diagnostic includes position, velocity,
age, cached chunk, and current `shouldTickEntityAt` result. A folded position
alone is not a pass: the projectile must remain tick-eligible and actually hit
the seam-adjacent target.

## Copied production lifecycle regression

This isolated integrated-client runner exercises actual dimension transfers
without altering the source world. It first copies a complete production
16,384×256 save from `run/saves/` into the ignored
`run-production-lifecycle/saves/` directory, then opens only that copy:

```sh
./gradlew :runProductionLifecycleClient \
  -PringProductionLifecycleSource="production-save-folder" \
  -PringProductionLifecycleDestination="RingWorld Production Lifecycle"
```

The source property must be one existing save-folder identifier beneath
`run/saves/`; the destination is the isolated copy and may be changed for
concurrent local work. Both identifiers reject path traversal. The preparation
task fails before launch when `level.dat` is absent, never writes to the source,
and refreshes only the ignored destination. The client opens that copy through
Minecraft's in-process world-open flow rather than the unreliable quick-play
argument. Runtime directories must not be committed or packaged.

On 2026-08-10 this lifecycle passed again on independently generated format-3
annular production sources for both Fabric and NeoForge. Each completed
Overworld → Nether → Overworld → End → Overworld, saved and
disconnected normally, proved client/GPU state clear, and reopened the same
world with its exact format-3 fingerprint and complete Atlas restored.

The test client controls its integrated server directly through the Minecraft
26.1 `TeleportTransition` API. After
an initial Overworld-to-Nether setup move, the asserted sequence is Nether →
Overworld → End → Overworld. The client independently records a complete
production atlas and immutable layout baseline, proves
`ClientRingState.geometry()` is inactive in both non-Overworld dimensions,
verifies the exact geometry/fingerprint/complete atlas on both Overworld
returns, uses Minecraft's normal integrated-server save-and-disconnect path,
reopens the same copy, and verifies the baseline again. The client arms the
server transfer only after its complete baseline is ready, and the final
save/reopen waits for the server's full transfer result.
Search the client log for the bounded machine-readable completion marker:

```text
[production-lifecycle] result=true ...
```

`result=false` records the failing phase or state. This test does not replace
the dedicated two-client seam matrix, the layout-switch world replacement test,
or manual portal/respawn playtesting.

Its preparation removes old logs/cache/crash evidence, and the
`verifyProductionLifecycleClient` finalizer requires the successful terminal
marker, rejects any failure marker, and confirms that the copied destination
still contains `level.dat`. A crash or stale prior run cannot satisfy the gate.

The production 16,384×256 checkpoint passes with a complete 65,536-cell atlas.
It logged inactive client geometry in Nether and End, exact baseline restoration
after both Overworld returns, `client state cleared=true` before reopen, and a
final `result=true` after the same geometry, fingerprint, and atlas world hash
were restored. An earlier harness revision called `saveEverything` from the
render thread and raced server chunk/entity collections; the active runner
deliberately relies on Minecraft's normal integrated-server save-and-disconnect
path instead.

If the client reaches the presentation side of the seam but its interaction
fixture has not arrived, it logs presentation X, camera chart/crossing count,
and both logical/canonical client block states every 200 ticks. This turns a
previously silent wait into a packet/chart diagnostic; it does not waive the
block-interaction assertion.

Set `testMode=false` again for ordinary play. `RingWorldConfig` is cached for
the process lifetime; restart Minecraft after editing it manually. The
world-creation editor updates the cache itself.

When changing the creation editor, open it from Create World and leave it
visible for multiple frames at GUI scale 4. Minecraft 26.1 permits only one
menu-blur layer per frame; custom screens must not call a background
extraction method inside `extractRenderState`, because
`Screen.extractRenderStateWithTooltipAndSubtitles` already owns that pass.
At a 1920-by-1080 window this also exercises the compact 480-by-270 logical
layout. Verify that the RingWorld entry shares the vanilla footer row without
overlapping Create or Cancel, then exercise all four editor cases:

1. enter an invalid layout and confirm that the error is visible and
   **Use for new world** is disabled;
2. select **Small** and confirm `2048×128`, wall height `160`, live equations,
   and the disabled monument control;
3. select **Medium** and **Large**, confirming `16384×256` and `32768×512`,
   their walking-lap/generation maths, and the Large warning highlight;
4. enter a distinct valid custom layout, confirm its preview, choose
   **Use layout**, reject the immutable-layout confirmation once, then
   accept it and verify that Create World shows the new C×W summary.

Keep the editor open for at least several frames in each case and treat any
duplicate-blur exception, clipped controls, footer overlap, missing validation
message, or stale C×W summary as a failure. This is a local UI test; do not
create or connect to the live server.

## Creation UI GUI-scale regression

The menu-only creation-UI gate passed on Fabric and NeoForge on 2026-08-08.
Run only one loader at a time:

```sh
./gradlew :runCreationUiClient --console=plain
./gradlew :neoforge:runCreationUiClient --console=plain
```

For an exact 26.1.x manifest cell with isolated Gradle/build/game state and an
immutable evidence record, use:

```sh
python3 scripts/run_gradle_creation_ui_qualification.py --cell 26.1-fabric
```

Optional `--gradle-dependency-cache` and `--gradle-distribution-zip` arguments
follow the same external-path, checksum, isolation, and non-authoritative
rules as the quick runner. They reduce repeated matrix downloads but never
replace the disposable Gradle user/project caches.

The equivalent `-neoforge` cell selects the NeoForge task. This proves the
real graphical client and creation UI against that patch's exact source ABI;
it intentionally does not claim a production launcher or frozen packaged jar.
The separate Prism executor remains an authenticated-disposable-profile or
owner release gate: a fresh account-free Prism root stops at the official
login setup wizard even when `--offline` is requested. Never copy a user's
normal Prism account file into qualification state.
The runner also sets a cell-contained Gradle project cache; do not remove it
or Loom will route launch configuration through the checkout's `.gradle`
directory despite the disposable Gradle user home.

The six-cell source-ABI matrix passed on 2026-08-13 from clean pushed commit
`077615493e0f8a7b58e92aec51e9ec83535cb08f`. Each run produced all thirteen
captures and no `level.dat`:

| Cell | Run ID | Terminal SHA-256 |
| --- | --- | --- |
| 26.1 Fabric | `20260813T101541Z-e87eced07877` | `b7cbe6f950dd0b6aa699ab06a32e6947c87fc905cba99954d04d2bf31b3b5710` |
| 26.1.1 Fabric | `20260813T101904Z-f32dbc8917e9` | `e4c513b331494bf5316e9a16b91a68d50fe16383ffe44bca606307fc6833a675` |
| 26.1.2 Fabric | `20260813T102213Z-d33b1a707c5b` | `9bd5f40cb4f6a19a9b1e06672f6b6fd58ef73a18c5442f33d5ae9648b450c2b3` |
| 26.1 NeoForge | `20260813T102535Z-618362c64a62` | `2c147161cb82bc32f13c2b45ff1eb58a80baca231535e314b73a038ac1f29e79` |
| 26.1.1 NeoForge | `20260813T105844Z-fdefa2c044f5` | `22eabdb5c5a01fac8b5a9e91aed7726ef2c6a0389566026a1b3498af7834270d` |
| 26.1.2 NeoForge | `20260813T110726Z-2e96621d7486` | `0f4255e11c65701298a298cc2046924aad95dc83bef0cc340e971b2769d6dda6` |

An earlier 26.1.1 NeoForge attempt failed closed on a transient `No route to
host` from Mojang's library repository. It is retained as infrastructure
failure evidence and is not counted as a mod failure or reused as a passing
cell.

Each qualified task prepares its own ignored `run-creation-ui/` directory
(NeoForge uses `neoforge/run-creation-ui/`), deleting only disposable saves,
screenshots, RingWorld cache, and logs. The initial bootstrap configuration is
16,384×256×160 with `testMode=false`, atlas pregeneration disabled, and no
monument request. The client requests a 1,920-pixel-wide framebuffer at least
1,080 pixels tall (macOS may expose extra usable height), enforces both 480-
and 320-pixel-wide scale-4 layouts at least 270 pixels tall, uses GUI scales
1–4, and stops
without invoking Minecraft's create-world action. It waits for the title-screen
startup fade before capturing, so the Mojang splash cannot mask UI defects.
NeoForge disables only its separate early splash; the graphical Minecraft
window and renderer still run.

| Capture prefix | Required state |
| --- | --- |
| `creation-ui-01-footer-scale1` | Create World footer entry |
| `creation-ui-02-default-scale1` | Default editor at scale 1 |
| `creation-ui-03-default-scale2` | Default editor at scale 2 |
| `creation-ui-04-default-scale3` | Default editor at scale 3 |
| `creation-ui-05-default-scale4` | Compact default editor at scale 4 |
| `creation-ui-06-large-narrow-scale4` | Large editor retained across resize at 320×270, including monument choice and longest live maths |
| `creation-ui-07-invalid-five-errors-narrow-scale4` | All five invalid-layout errors and disabled apply action at narrow width |
| `creation-ui-08-small-scale4` | Small preset, exact maths, unavailable monument state, and visible experimental stronghold advisory |
| `creation-ui-09-medium-scale4` | Medium preset and exact maths |
| `creation-ui-10-large-scale4` | Large preset, exact maths, and generation warning |
| `creation-ui-11-custom-monument-scale4` | Valid 4,096×640×192 custom monument layout |
| `creation-ui-12-confirm-layout-scale4` | Immutable-layout confirmation |
| `creation-ui-13-footer-applied-scale4` | Refreshed Create World footer after confirmation |

The finalizer requires `[creation-ui-test] PASS` and no `FAIL`, every listed
prefix to match at least one decodable, dimension-safe, visible non-uniform
PNG, no `level.dat` below `saves/`, and final properties exactly
`circumferenceBlocks=4096`, `widthBlocks=640`, `wallHeightBlocks=192`, and
`requestOceanMonument=true` (while retaining `testMode=false` and disabled
atlas pregeneration). A missing, stale, blank, corrupt, failed, or
world-creating run therefore fails closed.

Accepted evidence: both qualified tasks emitted their 13 screenshot markers,
the final PASS marker, the exact persisted custom values, and no `level.dat`.
The compact default, five-error, custom-monument, confirmation, and refreshed-
footer captures were visually inspected without clipping, overlap, duplicate
blur, or startup-overlay contamination.

The Small preset also passed the real stronghold gate on both loaders with
seed `ringworld-small-128`, circumference 2,048, width 128, and wall height
160. The 148-piece graph exceeded the finite Z band, so optional graph bounds
extended into suppressed exterior space; the fitted portal-room terrain
envelope remained wholly in bounds, generated all 12
frames, activated, and passed periodic locate and Eye-of-Ender checks. The
monument policy was correctly disabled because no width-128 candidate can
retain the required 64-block margins.

## Non-destructive join screenshot

For a real saved world without the automated traversal, start the client with:

```text
-Dringworld.captureJoinFrame=true
```

After terrain settles, the client writes:

```text
screenshots/ringworld-join-diagnostic.png
```

This flag does not move the player or modify blocks. It is useful for launch,
black-screen, and first-frame regressions, but it captures the player's current
pose and therefore may not show the distant ring.

## Dedicated two-client regression

The Gradle project defines:

```sh
./gradlew :prepareMultiplayerHarness
./gradlew :runMultiplayerServer
./gradlew :runMultiplayerClientA
./gradlew :runMultiplayerClientB
./gradlew :verifyMultiplayerHarness --console=plain
```

Run the server and both clients in their own terminals, then run the verifier
after they exit. Runtime state is isolated under:

```text
run-multiplayer/server/
run-multiplayer/client-a/
run-multiplayer/client-b/
```

On a fresh checkout, `prepareMultiplayerHarness` creates
`run-multiplayer/server/eula.txt` with `eula=false` and stops. Review Mojang's
EULA, set it to `true`, then repeat preparation before launching the server.
Use the test geometry in each isolated
`config/ringworld.properties` and configure the dedicated server port expected
by the clients (default harness property 25566).

The tasks set:

```text
server:  -Dringworld.multiplayerTest=true
clientA: -Dringworld.multiplayerTestRole=A
clientB: -Dringworld.multiplayerTestRole=B
clients: -Dringworld.multiplayerTestPort=25566
```

The automated clients wait for Minecraft's initial resource reload to report
`isFinishedLoading()` before connecting, then both explicitly acknowledge
`isGameLoadFinished()` before the server applies any fixture teleport. Do not
remove either gate: a world may
otherwise begin random display ticks while particle sprite providers are still
unprepared. The harness uses the supported minimum simulation distance of five
chunks. Client A derives its next positive seam from its current presentation
chart; canonical X=2044 may correctly arrive as presentation X=-4, so the
driver must never aim at one hard-coded presentation seam. The vehicle probe
likewise compares the boat against the seam image nearest each observer rather
than canonical `C`. The server holds the boat and its armor-stand passenger on
the high side until both clients acknowledge the same identities, then
advances it through deterministic canonical samples. Both sides reject any
missing tick, replacement identity, lost mount, rotation discontinuity, or
motion beyond the fixture's one-block/0.05-speed limits. That separates actual
seam reindexing/interpolation failures from client-startup packet timing.
Intentional-teleport return checks likewise compare periodic positions rather
than requiring canonical `C-4` to appear in one particular client chart.
Fixture initialization removes stale automated boats from a reused harness
world, repeats that cleanup after the clients load the seam chunks, and seals
the seam lane so an ocean seed cannot refill it during the test. The server
detects a fold from the large canonical-coordinate
discontinuity plus its small positive periodic step, so an overloaded tick
does not have to sample the player inside the final one-block interval.

After both clients are ready, the opt-in Atlas-concurrency fixture additionally
requires 100 consecutive server intervals at or below 100 ms before it begins
the scenario. It fails closed after 60 wall-clock seconds or 1,200 observations.
The readiness gate deliberately preserves the original 100-tick Creative-to-
Survival dwell before seam movement is armed; it is a startup-stability guard,
not a gameplay shortcut.

The scenario verifies:

- both clients expose the complete required channel set, acknowledge geometry
  before the 300-tick deadline, and repeat the handshake on reconnect;
- canonical players remain one short periodic distance apart;
- client presentation movement is smooth through the seam;
- server player query and tracking cross the seam;
- real melee damage crosses the seam;
- a block interaction/update crosses the seam;
- Survival placement crosses the seam in both directions using the real
  `MultiPlayerGameMode.useItemOn` path: support `C-1` east face places at
  canonical `0`, and support `0` west face places at canonical `C-1`; each
  direction consumes exactly one item and is visible to the other client;
- a server-owned boat and its passenger retain identity, mount, orientation,
  zero fixture velocity, visibility, and canonical ownership through the fold;
- an intentional long teleport re-keys the client chart;
- client B disconnects and reconnects cleanly;
- a chest and book-bearing lectern synchronize on their nearest seam images;
- a real neighbour update powers a redstone lamp across X=`C-1`/`0`;
- water in a sealed two-cell trough flows from its sole source at canonical
  X=`C-1` into the initially empty canonical X=`0` destination on the server
  and each client, alongside a real BLOCK explosion crossing C-1→0 inside a
  deterministic seam-wrapped no-drop glass cell;
- a tagged hostile Zombie follows vanilla navigation in its bounded ground lane
  from canonical X=`C-5` toward X=`2` and naturally folds into low canonical
  X, finishes the path, and reaches the target tolerance before the server
  fixture can pass;
- a real survival bed spanning canonical X=`0`/`1` accepts a player beside
  `C`, stays canonical, and survives a disconnect: Minecraft's vanilla
  reconnect semantics wake the player beside
  the bed; both server and client require X/Y/Z proximity, and the client also
  requires a matching Overworld RingWorld session plus the loaded bed. The
  fixture then sleeps again, wakes on damage, and removes the bed cleanly when
  broken; a missing reconnect fails at the ordinary bounded timeout. The gate
  captures the old `ServerPlayer` at the authoritative successful server-side
  sleep start, then accepts either a sampled null-player interval or its
  definitive replacement because a cold server may delay the client
  acknowledgement or complete disconnect/login between two ticks;
- a double chest spanning canonical X=`C-1`/`0` is joined on both clients;
  both server container views have 54 slots, items written through opposite
  views are visible from both, and X=`-1`/`C` block-entity lookups resolve to
  the same two canonical owners. Serialized pending NBT also covers a lone
  alias repairing to canonical ownership and a canonical/alias collision
  retaining both inventories until explicit recovery. Save lookup runs while
  both entries are still packed, before the alias is loaded. The alias is loaded
  first through packed-pending promotion and the direct-entry reconciliation
  policy so the ownership decision cannot pass only under a favorable order.
  This does not yet substitute for a future alias-first region-file fixture
  that drives vanilla `LevelChunk.runPostLoad` end to end;
- the death screen, client respawn request, replacement server player, and
  canonical respawn all complete;
- real Nether portal blocks and `PortalForcer` linking carry the player to the
  vanilla Nether only after the normal survival wait, verify positive and
  negative multi-lap X targets plus targets beyond both Z rims, then return a
  deliberately four-lap player to a canonical safe Overworld portal;
- a real End portal block carries the player to the vanilla End and an End
  return portal restores the Overworld with client RingWorld state reattached;
- both clients report their phase matrix.
- both seam-side clients observe full rain/thunder and an actual lightning
  entity, with one labelled weather screenshot per client.

While either client waits to arm the first seam stage, the log periodically
records its local pose, game mode, and either the missing remote player or the
remote/expected nearest-image X pair. This is diagnostic only: it does not
relax the existing ready, proximity, smooth-step, or timeout assertions.

The build contract also inventories all 11 currently positional outbound
block packet families handled by `ClientConnectionMixin`; both client source
sets must compile their field-preserving packet reconstruction. The inventory
includes command/structure/jigsaw/test editors as well as ordinary gameplay
packets, so a future Minecraft packet addition requires another positional
audit rather than silently retaining presentation X.

The 2026-08-10 routing refresh also passes the entire matrix at 16,384x256 on
Fabric and on a warmed NeoForge retry. The first cold NeoForge production run
stopped at the pre-existing sleeping-reconnect fixture/resource-pressure
boundary tracked by #134 before the portal phase; it is not counted as a
portal failure.

The 2026-08-10 alpha-4 integration branch also passed fresh Atlas-disabled
2,048×416 Fabric and NeoForge runs after merging issues #145, #146, #147, and
#149. Both strict verifiers observed format-3 acknowledgement, both placement
directions, the joined 54-slot seam chest plus alias-recovery marker, the
multi-lap/out-of-width portal marker, sleeping reconnect, death/respawn,
Nether/End travel, and terminal seam weather in the same process matrix.

The historical 2026-08-01 dedicated result predates the X=`0` destination
assertion and observed only the seam-side source state. Fresh 2,048x416 runs
on 2026-08-02 passed the complete strengthened matrix on both Fabric and
NeoForge, including destination water, the tagged hostile-Zombie fold/path
completion, the ordinary 80-tick survival portal delay, and seam
thunder/lightning. NeoForge's standalone evidence verifier also requires both
weather screenshots and the explicit portal/weather server markers. Keep the
older source-only result labelled as historical rather than conflating it
with the stricter gate.

The 2026-08-10 Fabric regression for issue #146 completed the full disposable
matrix with `full scenario result=true`. Its server evidence recorded
`highSize=54, lowSize=54`, diamonds and emeralds visible through both
container views, and `lowAliasSame=true, highAliasSame=true`. Both real
clients accepted the joined chest states before the normal sleep/reconnect,
portal, and weather stages passed. NeoForge recorded the same inventory,
serialized pending-NBT recovery, alias, and both-client evidence; an earlier
run on the branch reached terminal `full scenario result=true` and passed its
loader-specific verifier. Subsequent cold replays hit #134's known client-
readiness contention before the extended fixture or the superseded sleep-
acknowledgement race, so they do not replace that evidence. Re-run the
NeoForge matrix warmed/staggered when closing #134.

Success is:

```text
[multiplayer] full scenario result=true
```

in `run-multiplayer/server/logs/latest.log`.

### Opt-in atlas-concurrency gate (#130)

The default multiplayer harness deliberately writes
`pregenerateTerrainAtlas=false`. To prove the watchdog-safe, single-writer
background atlas scheduler can advance while the existing two-real-client
matrix runs, use the shared opt-in Gradle property
`-PringMultiplayerPregenerateTerrainAtlas=true`. It only affects the ignored,
fresh `run-multiplayer/` fixture selected by the server preparation task; it
does not start a live server or change a deploy/save configuration.

The same disposable harness accepts three shared geometry properties:
`ringMultiplayerCircumferenceBlocks` (default `2048`),
`ringMultiplayerWidthBlocks` (default `416`), and
`ringMultiplayerWallHeightBlocks` (default `160`). Circumference and width
must be integer, 16-block-aligned values of at least `2048` and `128` blocks;
wall height must be an integer of at least `32` blocks. The normal first-world
layout report remains the final safety check. With the Atlas opt-in, the
verifier derives its required total from the selected layout as
`(C / 8) * (W / 8)` cells, so a mismatched server configuration fails closed.
Fabric uses port `25568` by default and accepts
`-PringFabricMultiplayerPort=<port>`; NeoForge uses `25566` and accepts
`-PringNeoForgeMultiplayerPort=<port>`. These distinct defaults prevent two
loader-qualified disposable profiles from accidentally binding the same port.

For Fabric, first run the preparation task. On first use it creates
`run-multiplayer/server/eula.txt` with `eula=false`; review Mojang's EULA and
set it to `true`, then repeat preparation. Start the following three commands
in separate terminals, wait for all to exit, and run the verifier:

```sh
./gradlew :prepareMultiplayerHarness -PringMultiplayerPregenerateTerrainAtlas=true --console=plain
./gradlew :runMultiplayerServer -PringMultiplayerPregenerateTerrainAtlas=true --console=plain
./gradlew :runMultiplayerClientA --console=plain
./gradlew :runMultiplayerClientB --console=plain
./gradlew :verifyMultiplayerHarness -PringMultiplayerPregenerateTerrainAtlas=true --console=plain
```

NeoForge is loader-parallel and uses its subproject-local fixture. Its prepare
task creates and gates the same persistent `eula.txt` acknowledgement; review
it, set `eula=true`, and repeat the preparation command before launching.
The preparation task disables only NeoForge's separate early splash for the
two automated clients. The real Minecraft windows and renderer still run;
this prevents a false GLFW primary-monitor failure if macOS has recently put
the display to sleep. Use:

```sh
./gradlew :neoforge:prepareNeoForgeMultiplayerHarness -PringMultiplayerPregenerateTerrainAtlas=true --console=plain
./gradlew :neoforge:runMultiplayerServer -PringMultiplayerPregenerateTerrainAtlas=true --console=plain
./gradlew :neoforge:runMultiplayerClientA --console=plain
./gradlew :neoforge:runMultiplayerClientB --console=plain
./gradlew :neoforge:verifyNeoForgeMultiplayerHarness -PringMultiplayerPregenerateTerrainAtlas=true --console=plain
```

For the exact production `16384×256` layout, pass the three layout properties
to the preparation, server, and verifier commands. For example, Fabric uses:

```sh
./gradlew :prepareMultiplayerHarness -PringMultiplayerPregenerateTerrainAtlas=true -PringMultiplayerCircumferenceBlocks=16384 -PringMultiplayerWidthBlocks=256 -PringMultiplayerWallHeightBlocks=160 --console=plain
./gradlew :runMultiplayerServer -PringMultiplayerPregenerateTerrainAtlas=true -PringMultiplayerCircumferenceBlocks=16384 -PringMultiplayerWidthBlocks=256 -PringMultiplayerWallHeightBlocks=160 --console=plain
./gradlew :runMultiplayerClientA --console=plain
./gradlew :runMultiplayerClientB --console=plain
./gradlew :verifyMultiplayerHarness -PringMultiplayerPregenerateTerrainAtlas=true -PringMultiplayerCircumferenceBlocks=16384 -PringMultiplayerWidthBlocks=256 -PringMultiplayerWallHeightBlocks=160 --console=plain
```

NeoForge uses the loader-qualified counterparts:

```sh
./gradlew :neoforge:prepareNeoForgeMultiplayerHarness -PringMultiplayerPregenerateTerrainAtlas=true -PringMultiplayerCircumferenceBlocks=16384 -PringMultiplayerWidthBlocks=256 -PringMultiplayerWallHeightBlocks=160 --console=plain
./gradlew :neoforge:runMultiplayerServer -PringMultiplayerPregenerateTerrainAtlas=true -PringMultiplayerCircumferenceBlocks=16384 -PringMultiplayerWidthBlocks=256 -PringMultiplayerWallHeightBlocks=160 --console=plain
./gradlew :neoforge:runMultiplayerClientA --console=plain
./gradlew :neoforge:runMultiplayerClientB --console=plain
./gradlew :neoforge:verifyNeoForgeMultiplayerHarness -PringMultiplayerPregenerateTerrainAtlas=true -PringMultiplayerCircumferenceBlocks=16384 -PringMultiplayerWidthBlocks=256 -PringMultiplayerWallHeightBlocks=160 --console=plain
```

Preparation deliberately deletes its disposable world. To restart the same
prepared server and resume its saved Atlas instead, omit preparation and skip
only that dependency, retaining the same geometry:

```sh
./gradlew :runMultiplayerServer -x prepareMultiplayerHarness --console=plain
./gradlew :neoforge:runMultiplayerServer -x prepareNeoForgeMultiplayerHarness --console=plain
```

Do not use either restart command after changing a layout property: prepare a
fresh fixture so immutable saved settings and the expected Atlas-cell total
remain aligned.

Each automated multiplayer client self-stops after it emits its terminal
result. Stop the dedicated server normally after both clients exit, then run
the verifier. With the property enabled, each verifier still requires the ordinary full
multiplayer PASS marker, portal/weather evidence, both client-start markers,
all four screenshots, and the ordered `multiplayer-cold` fixture/Nether/End/
post-End-stability/terminal telemetry markers. Weather cannot arm until a
fresh post-End barrier observes 100 consecutive server intervals at or below
100 ms; the existing 60-second/1,200-observation timeout fails the disposable
matrix. Telemetry records elapsed wall time, dimension/game time, loaded and
pending chunks, scheduled block/fluid ticks, entity/item/falling-block counts,
heap use, and Atlas state without loading chunks or mutating simulation. The
verifier additionally parses the server's periodic
`RingWorld terrain atlas progress` status: total cells must stay fixed, present
cells must never decrease, and at least two statuses must demonstrate a real
increase. A `generation complete` status is accepted by the same rule. This is an
advancement gate, not a requirement to complete all 3,328 chunks during the
multiplayer scenario.

The historical expanded isolated Minecraft 26.1.2/Java 25 run on 2026-08-01
achieved that result on the reused 2,048×416 server with no `moved too quickly`
or `moved wrongly` warning. The corrected fresh Fabric and cold NeoForge
atlas-concurrency/full-matrix runs both passed on 2026-08-08 after the readiness
gate, including the strict `maxRemoteStep <= 1.25` client requirement. Each
automated client emits its terminal result and self-stops; stop the dedicated
server normally after both have exited. Detailed scope and residual manual
coverage are recorded in [`SEAM_GAMEPLAY_REGRESSION_2026-08-01.md`](SEAM_GAMEPLAY_REGRESSION_2026-08-01.md).

The production-size NeoForge qualification on 2026-08-08 used
16,384×256×160 and the derived 65,536-cell total. Two normal graphical clients
completed the full matrix while Atlas generation advanced; the server saved
7,544 cells (11.5%), then a no-prepare restart loaded that exact partial Atlas,
advanced monotonically to 65,536/65,536, and saved normally. Two cold server-
behind warnings (7.464 and 8.296 seconds) remain under profiling issue #134;
there was no managed-block deadlock, Atlas regression, or multiplayer failure.

After the cold-fixture telemetry hardening, a fresh production repeat passed
again with Atlas advancing monotonically from 596 to 3,824 cells at roughly
28–32 cells/s. Both clients and the strict verifier completed; the largest
server-behind warning was 3.219 seconds during cold Nether generation, and no
watchdog or crash report was produced. The deterministic cross-seam glass
blast did not add item or falling-block entities.

The original run on 2026-07-31 achieved the baseline result
on a reused 2,048×416 server whose seam crossed an ocean. Both clients
acknowledged format 2; the natural seam crossing was canonical with a
0.25-block maximum packet step; visibility/query/distance, real melee, block
update/interaction, boat and passenger identity/mount continuity, long
teleport, periodic return, planned disconnect, and reconnect all passed. The
seed-independent sealed lane remained dry, and stale persisted boats that
loaded only after login were removed before the new fixture was acquired.

Production-layout evidence is narrower but now real: on 16,384×256, the first
cold dedicated run passed the server gameplay and reconnect probes but produced
`result=false` only because client B measured `maxRemoteStep=1.333` while the
server still received `maxPacketStep=0.25` but accumulated
`maxTickSample=4` under cold resource pressure. Re-running the same warmed
world/configuration produced
`result=true`: server `maxTickSample=0.25`, client B
`maxRemoteStep=0.2498857`, no missing ticks, lag warnings, or crashes, and all
seam/combat/block/vehicle/teleport/reconnect probes true. Keep cold-start
performance validation open; this evidence does not certify the entire
production matrix.

A later fresh-process cold run copied the complete production checkpoint into
the ignored multiplayer server slot and preserved the source hash. It reached
`full scenario result=true` in about 2 minutes 51 seconds with a 65,536-cell
atlas, `maxPacketStep=0.25`, `maxTickSample=2.75`, client A/B
`maxRemoteStep=0.0/1.25`, zero missing client ticks, and no crashes. The server
still reported 3.816-second initial-connect and 39.402-second reconnect
server-behind warnings.
Treat this as functional repeatability evidence, not a cold-start performance
pass. All three processes were stopped after the result; the local logs remain
under the ignored multiplayer run directories.

That trace revealed repeated client completion work from identical dirty tiles.
After making tile application idempotent and reserving forced publish/save for
the actual incomplete-to-complete transition, an equally cold comparison
reported:

- one completion notice per client instead of seven;
- two complete-ring mesh builds per client instead of three;
- about 69 seconds to the full result instead of 171;
- server `maxTickSample=0.75` instead of `2.75`;
- client B `maxRemoteStep=0.4167` instead of `1.25`, with zero missing ticks;
- one 2.020-second/40-tick initial-connect warning instead of the previous
  3.816-second initial warning plus 39.402-second reconnect warning;
- true seam/combat/block/vehicle/teleport/reconnect results and no crash.

Both comparison runs used renamed cold client caches, a fresh ignored server
copy of the same complete source, and unchanged source hashes.

A third cold run added one-second process sampling. The active scenario from
arm to final result took about 18 seconds and passed with server packet/tick
maxima `0.25/0.25`, client A/B remote maxima `0.0/0.2499238`, zero missing
ticks, no overload warning, and no crash. Client A logged one completion and
one mesh build; client B logged one completion and two mesh builds. Observed
RSS lower bounds were 591 MiB for the server, 871 MiB for client A, 941 MiB for
client B, and 2.15 GiB simultaneously. The sampler output was not persisted,
so these are lower bounds rather than exact peak claims. Existing swap use was
flat in retained samples. Full process start-to-result was about 2 minutes 22
seconds because offline Mojang/Realms requests delayed initial connection.
Client B's second build was logged at 00:54:24, one second after its first
complete build and before Client A requested the atlas at 00:54:26. During
that interval the server captured newly loaded chunks and saved changed atlas
data. Client A then built only the updated snapshot. Treat this single rebuild
as a legitimate changed surface revision; the defect was repeated rebuilds for
identical tile payloads.

Issue #70 strengthens that policy. A complete-atlas tile burst now waits for
three quiet seconds, bounded to ten seconds, and its later ordered commit saves
the durable cache without requesting an identical texture build. Expensive
pixel, relief, mip, and native-image preparation runs from an immutable atlas
snapshot off the render thread. In the final dedicated two-client rerun both
clients observed the identical revision sequence 11/17/24/31/36/40, completed
the full gameplay matrix, and reused the complete cache on reconnect without
rebuild churn. Exact logs, frame metrics, and residual cold-start spikes are in
`ATLAS_RELEASE_GATE_2026-08-01.md`.

A separate clean-atlas benchmark removed the atlas only from a disposable copy
of the production world and let the normal dedicated scheduler rebuild it. It
created the fresh atlas at 01:01:32, reached 65,536/65,536 cells at 01:15:09,
and saved 100% at 01:15:12: 13 minutes 37 seconds and about 80.2 cells per
second. The completed gzip was 76 KiB. The copy grew from 210,024 to 383,376
KiB (+169.3 MiB), chiefly from generated chunks, while 15-second process
sampling observed a 1.06 GiB server RSS peak. There were no server-behind
warnings, generation errors, RingWorld exceptions, crash reports, or observed
swap growth. Source `level.dat` and atlas hashes were unchanged, and every
benchmark process was stopped afterward. Runtime logs and artifacts stay in
the ignored multiplayer directories.

The integrated visual/seam harness deliberately holds position for 300 client
ticks after its first seam screenshot. This keeps the seam chunks resident
through the server's 240-tick projectile, navigation, fluid, vehicle, and
explosion observation window. Only then does it begin the accelerated second
circuit; the server waits for a real high-X then low-X traversal before moving
the player to the rim capture.

Client screenshots are named:

```text
ringworld-multiplayer-a.png
ringworld-multiplayer-b.png
```

Do not substitute two integrated single-player windows for this test. The
dedicated server path exercises entity IO, real watch state, and independent
client charts.

## Same-process saved-layout switch

The deterministic layout-switch client opens two copied existing saves in one
JVM. It copies explicit source folders from `run/saves/` into the ignored
`run-layout-switch/saves/` directory before launch, so the source worlds are
never opened or modified. It verifies the first layout, dimension-owned
settings and atlas storage, disconnects, confirms all RingWorld-owned client
session state (including the static GPU ring resources) was cleared, then opens
the second copy and checks that its handshake, atlas, and storage agree.

The default `different-layout` expectation requires different immutable
geometries. Use `same-geometry-different-seed` with two complete saved worlds
of equal width, circumference, and wall height but different seeds to prove
that equal geometry cannot reuse the prior world's atlas/cache identity:

```sh
./gradlew :runLayoutSwitchClient \
  -PringLayoutSwitchFirstSource="same-size-seed-a" \
  -PringLayoutSwitchSecondSource="same-size-seed-b" \
  -PringLayoutSwitchExpectation=same-geometry-different-seed

./gradlew :neoforge:runLayoutSwitchClient \
  -PringNeoForgeLayoutSwitchFirstSource="same-size-seed-a" \
  -PringNeoForgeLayoutSwitchSecondSource="same-size-seed-b" \
  -PringNeoForgeLayoutSwitchExpectation=same-geometry-different-seed
```

Without overrides, the task retains the two historical source folder defaults
(`RingWorld Automated Test (10)` and `RingWorld Automated Test (6)`). The
harness does not assume their numeric layouts in code. In default mode it
requires geometry and identity to differ. In same-geometry/different-seed mode
it waits for both complete atlases, requires the geometry to match, and requires
the settings fingerprint, atlas world hash, and a test-only full atlas-content
fingerprint to differ. Override destinations with
`ringLayoutSwitchFirstDestination` and `ringLayoutSwitchSecondDestination` if
needed; they must be distinct folder identifiers under `run-layout-switch/saves/`.
The matching NeoForge command uses the same expectation value with the
`ringNeoForgeLayoutSwitch*` property names.
The refreshed 2026-08-10 dual-loader evidence passed at production
16,384×256 with two complete format-3/annular Atlases, distinct settings
fingerprints, Atlas world hashes, and terrain-content fingerprints. Both
clients reported complete RingWorld-owned session/GPU teardown before opening
the second save. Fabric opened world hashes `4b031fbba76f61fc` then
`99b37c331155cee5`; NeoForge opened the same pair in reverse order.
Search `run-layout-switch/logs/latest.log` for:

```text
[layout-switch] result=true
[layout-switch] result-json={"passed":true,...}
```

The source and destination IDs must be single folder names containing no path
separators. The copied worlds may save normally while their own dimension-owned
settings and atlas paths are materialized; the harness does not move players or
edit terrain. Its source-copy stage replaces only prior copies beneath ignored
`run-layout-switch/`, never a source save.

The preparation stage also removes previous logs/cache/crash evidence. The
`verifyLayoutSwitchClient` finalizer accepts only the machine-readable
`"passed":true` marker with no failed marker, verifies that the selected
expectation was logged, and confirms both copied destinations still contain
`level.dat`. This is an identity/cache regression, not a visual substitute:
the owner must still inspect the interval while the second world's atlas is
incomplete for any old-ring artifact.

## Optional package safety and upgrade gate

Run the package/licence tests independently of Minecraft:

```sh
python3 -m unittest \
  scripts/test_alpha_installer.py \
  scripts/test_verify_distribution_license.py \
  scripts/test_stage_modrinth_release.py \
  scripts/test_prepare_release_packages.py
```

The package/licence suite covers the reusable alpha-channel manifest/checksum
bootstrapper, Fabric and NeoForge runtime metadata,
dual-candidate shared-contract comparison, staging provenance, reproducible
client/server archives, and loader-specific launcher updates. Package assembly
accepts only a generated format-2 staging manifest whose recorded SHA-256,
SHA-512, size, loader, release config, and public source revision match the
strictly validated staged jar; it cannot relabel an arbitrary jar with a
caller-supplied commit. Negative cases cover empty/non-runtime jars, decoy or
malformed NeoForge TOML, credentials/runtime state, source artifacts, path
traversal, stale licence/version/API metadata, auto-join, and altered
provenance. Synthetic runtime jars include the same
`ringworld-build.properties` artifact and release-label identity required from
real staged jars. The staging tests also require each loader's generated public
project description and changelog to render exactly the verified immutable
public source-commit URL; absent, duplicate, hard-coded GitHub revision URLs
or short/full SHAs, and unverified links fail before a stage is written. The
POSIX launcher cases execute fresh and in-place macOS paths,
verify that saves/options/config/user settings and unrelated mods survive, and
prove Fabric and NeoForge remain in separate `RingWorld-Test` and
`RingWorld-NeoForge` instances. The isolated home also checks Prism-managed
Java fallback and replacement of a stale Java 21 override with Java 25.

Pull requests touching package inputs also run `.github/workflows/package-windows.yml`
on a real Windows runner. Its platform-specific case executes
`Launch RingWorld.bat` twice for each loader with a harmless local Prism
executable stand-in, covering Windows `cmd`, PowerShell settings migration,
fresh installation, loader-specific instance selection, and in-place state
preservation without downloading software or launching the game. This is a
launcher/update gate, not the final graphical Minecraft gate.

The 1 August issue #12 checkpoint also launched the actual package jar in an
isolated existing macOS Prism instance: Prism selected Java 25, Minecraft
loaded RingWorld plus all resources/shaders, and the installed jar hash matched
the clean-build input. A fresh production-default 16,384-by-256 server assembled
from the overlay and official Fabric server launcher reached `Done`, began
atlas pregeneration, and saved/stopped cleanly. The distributable overlay keeps
`eula=false`; acceptance was changed only in the isolated test directory.

The Modrinth staging CLI always performs a fresh dual build after its Java 25
preflight; `--build` remains only as a harmless compatibility spelling. Java
21 and legacy Java 8 are identified correctly, malformed version output fails
closed, and a failed `java -version` produces a direct setup error without
starting Gradle. Custom cached jar paths are not a release provenance path.

Before closing package issue #12, also assemble the actual release-candidate
jar and perform isolated fresh and in-place macOS launches, a real Windows
launch, and a dedicated-server launch from the overlay. Static archive tests
do not substitute for those platform runtime gates. Generated packages and
test runtime state stay under ignored local directories and are never used as
deployment inputs without separate owner approval.

The historical `9b77326` Fabric-only candidate passes the exact-archive in-place macOS,
empty-data macOS first-run, and fresh production-layout dedicated-server
gates. Its jar and optional archive hashes are recorded in
`FABRIC_RELEASE_CANDIDATE_2026-08-01.md`. The real graphical Windows launch
remains open; do not infer it from the launcher-only Windows Actions result.
The current dual-loader candidate, machine evidence, and package hashes are
recorded in `DUAL_LOADER_RELEASE_CANDIDATE_2026-08-08.md`.

## Manual playability checklist

Use creative mode and an ordinary render distance (the current test profile is
28 chunks).

### Movement

- Walk and sprint normally; look for per-block or per-tick jitter.
- Cross X=0/C slowly in both directions.
- Cross while jumping, falling, flying, swimming, riding, and using elytra.
- Confirm yaw and pitch do not change.
- Confirm velocity does not reset.
- Open F3 and verify Ring X wraps into `[0,C)`.

### Multiplayer

- Put players on opposite canonical sides of the seam.
- Verify nameplates/models are adjacent and tangent-aligned.
- Chat, hit, interact, throw items, fire projectiles, and ride a vehicle.
- Break/place a block while the other player watches.
- Reconnect both sides of the seam.

### Gameplay systems

- Run ordinary survival and creative sessions on the exact Fabric and
  NeoForge candidates. Mine, build, eat, take damage, collect drops, and use
  inventories without commands or automation masking normal behavior.
- Have a hostile mob acquire and naturally navigate to a player through the
  seam. Confirm its canonical X folds naturally, it reaches the target, and no
  teleport correction is involved.
- Build a sealed two-cell water channel from canonical `C-1` into `0`. Place
  the only source at `C-1` and confirm the destination at `0` receives water on
  the server and both clients.
- Exercise a piston, hopper, redstone signal, explosion, projectile, boat with
  passenger, swimming, and elytra across or immediately beside the seam.
- Inspect chests, lecterns with books, signs, beds, and representative animated
  or entity-backed blocks while approaching from both directions. They must
  follow the same curve as ordinary block geometry without rising through the
  ground.

### Maps, compasses, weather, and structures

- The dual-loader `runMapCompassCaptureClient` fixtures automate seam pixels,
  player/banner markers, real item frames on both sides, map scale/lock,
  seam-banner removal and restoration, raw-session teardown plus normal
  save/disconnect/reopen persistence, all three compass targets, and
  deterministic exact-target behavior in both directions. Nether and End
  behavior must stay vanilla. Locator-bar seam support is not part of this
  gate.
- Start and complete a real raid near the seam. Confirm its center, raider
  navigation, wave completion, and rewards remain local through the wrap.
- Inspect clear, rain, thunder, and a lightning strike near the seam. Also
  check clouds and the full day/dusk/night dimming cycle.
- Locate and enter representative structures near the seam, including their
  loot. Verify the guaranteed stronghold/end portal and optional monument
  controls in a fresh world; record floating structures separately as the
  known height-placement polish issue rather than silently accepting them.

### Atlas controls

- On each loader, use the real **Generate Entire Ring** confirmation flow as
  the owner and as a dedicated-server gamemaster. Verify progress, pause,
  resume, cancel/retry, close/reopen, and disconnect/reconnect.
- A non-owner must receive read-only status, and controls must lock once the
  atlas is complete. A different-seed world must never reuse the previous
  world's completed surface.

### Rendering

- Look upward with high but practical render distance.
- Inspect both live/texture transition bases and the zenith.
- Move along X and Z; the distant atlas must stay anchored to the world.
- Verify real chunks overwrite the visual LOD near the player.
- Verify the stand-in is partially transparent but retains a recognizable
  terrain silhouette at the nominal chunk edge, then becomes opaque beyond the
  live range without a hard line or raised fog belt.
- Verify the stand-in becomes visible through the final live terrain band
  rather than appearing only after the last chunk. Move and rotate while
  watching the handoff; the fixed dither must not sparkle or form a visible
  checkerboard.
- Inspect water and other translucent live surfaces at the handoff.
- Stand beside both rim walls and verify no local proxy surface or atmospheric
  curtain is drawn over the wall or exterior void.
- Confirm cobblestone textures are visible on the same rim blocks that provide
  collision; press against both inner faces and inspect their tops.
- Walk while looking at the far ring and check that mip transitions do not
  shimmer or expose the canonical U seam.
- Compare a grassy live-chunk slope with the aligned atlas surface. Grass must
  retain its biome green rather than the dirt-brown map colour of the block
  underneath it, and the proxy top face must not sit one block below live
  terrain.
- Stand below or beside an opaque mountain and look upward along the
  circumference. Loaded terrain bent into view behind the mountain must remain
  present instead of disappearing at the mountain's flat silhouette. Repeat
  while rotating the camera and check for section-scale popping.
- Inspect clear/rain and day/dusk/night. At each phase, compare an exposed live
  top surface with the aligned proxy in the transition band; the proxy must
  follow the same RGB lightmap exposure rather than retaining a bright-green
  nighttime floor. Repeat with changed gamma, night vision, and a lightning
  flash when practical.
- Look at clouds from ground and near wall height.
- Check both wall edges and exterior void.

The automated safe-small sky sequence now captures noon, dusk, midnight, and
rainy noon. It sends `weather clear` after the rain frame and waits 100 client
ticks for Minecraft's weather interpolation to settle before arming the
tangent and radial-up handoff captures. This prevents a nominally clear
comparison from retaining the previous run's rain overlay.

The 2026-08-01 matrix reviewed complete-atlas tangent/up frames at 6, 12, and
28 chunks plus a complete 16,384×256 projection copy. Results, resource sizes,
frame measurements, and the rejected dither experiment are recorded in
[`VISUAL_HANDOFF_REVIEW_2026-08-01.md`](VISUAL_HANDOFF_REVIEW_2026-08-01.md).

The post-gameplay refresh completed safe-small 6/12/28 tangent, handoff, and
radial-up captures on both Fabric and NeoForge. Fabric regenerated the world
and complete atlas independently for each distance. NeoForge copied the same
complete-atlas gameplay pose into isolated runs, passed its evidence verifier,
and recorded zero frames over 50 ms across all nine measured views.
See [`VISUAL_POLISH_CHECKPOINT_2026-08-02.md`](VISUAL_POLISH_CHECKPOINT_2026-08-02.md).

### World lifecycle

- Save/quit/rejoin at the seam.
- Sleep at both ordinary and seam-adjacent beds. For the seam case, put a
  survival player just below canonical `C`, use a bed whose canonical X is
  just above zero, and confirm that sleep, a normal wake-up, damage
  interruption, bed destruction, and reconnect all remain beside the visible
  bed in the same presentation chart. The server/save value must remain in
  `[0,C)` throughout; a player appearing at raw canonical X or in the void is
  a regression in `LivingEntitySleepingPositionMixin`.
- Leave a world with a complete Atlas, create a different-seed world with the
  same dimensions, and watch the entire generation interval. The old ring must
  disappear immediately; the new world-specific opaque fallback must appear,
  progressively converge, and lose all placeholder influence at 100%.
- Die and respawn.
- Use `/tp` for a disjoint X move.
- Enter and return from Nether and End.
- Reload a chunk containing entities and scheduled ticks.

## Performance collection

Record:

- render and simulation distances;
- ring dimensions;
- atlas completion;
- average/max frame time and slow-frame count;
- client RSS and CPU after initial meshing settles;
- server tick time during atlas pregeneration;
- chunk pending-task count;
- whether the test is integrated or dedicated.

Do not compare the removed forced-100-chunk experiment with the active
28-chunk+texture path as if they load the same amount of real geometry.

## Exact candidate evidence record

Record Fabric and NeoForge separately. Shared-source unit tests do not replace
candidate runtime evidence.

| Field | Required value |
| --- | --- |
| Candidate identity | Clean commit, jar filename, and SHA-256 |
| Runtime | Loader/version, Minecraft, Java, OS, and GPU |
| World | Seed, circumference, width, wall height, fresh or upgraded |
| Reproduction | Exact build, launch, and harness commands |
| Automated result | Terminal PASS marker and relevant log excerpt |
| Visual result | Screenshots or video for seam, atlas handoff, rims, sky, maps, and block entities |
| Diagnostics | Warnings, crashes, disconnects, and any `moved too quickly` messages reviewed |
| Verdict | Pass/fail, tester, date, and linked follow-up issue for every failure |

## Failure triage

| Failure | First evidence to collect |
| --- | --- |
| Crash on join | Crash report, latest log, mixin target failure |
| Infinite falling/empty chunks | Server chunk/worldgen log, ring settings, exterior Z |
| Black screen | Player collision pose, render log, shader compile messages |
| Seam pop/rubber-band | Player packet steps, correction count, yaw/pitch, server canonical X |
| Missing remote entity | Server tracker result and client projected X |
| Block visible but unusable | Outbound action packet canonical position and reach result |
| Sleeping player appears in the void | Local `getSleepingPos()` presentation image, client chart, and canonical server/saved bed position |
| Upward chunk disappearance | Curved frustum envelope, RingWorld section-occlusion override, and terrain shader camera origin |
| Texture follows player | Atlas world hash, U/V mapping, global mesh model transform |
| Hard LOD seam | Actual view distance, proxy alpha/reveal curves, atlas/live alignment |
| Proxy brighter/greener at night | `Sampler2` lightmap binding and full-sky texel coordinates in `ring_surface.fsh` |
| Rim collides but is invisible | Boundary `BuiltChunk.shouldBuild`, exterior-neighbour exception, section rebuild |
| Server hitching | Atlas generation future and pending chunk tasks |
