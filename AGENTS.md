# RingWorld agent guide

This file is the first-stop operating guide for coding agents working in this
repository. Read it before changing topology, networking, world generation, or
rendering. Detailed design documents live under [`docs/`](docs/README.md).

Last playable code audit: 2026-07-28, covering the final Minecraft 1.21.11
implementation identified in the private development archive as
`mc-1.21.11-final` at commit `2c98650`. That pre-public ref is provenance only
and is intentionally not present in the clean public Git history.

Active port checkpoint: Minecraft 26.1.2/Java 25 integrated safe-small runtime
gate. The Fabric and NeoForge builds each pass all 235 unit/parameterized
cases. Fabric has completed the client/runtime gates described below. NeoForge
26.1.2.87 on ModDevGradle 2.0.143 reaches `Done` on a dedicated server and has
a client checkpoint: shared client payload/session state, mixins, shaders, and
resources load through NeoForge adapters; its render pipeline registers; and a
copied production 16,384×256 world opens through the integrated server with a
format-2 settings acknowledgement and streaming atlas metadata/tiles. The
`:neoforge:runProductionProjectionClient` copies a named
source save into an isolated run directory, waits for a complete atlas, writes
tangent/handoff/radial captures, records frame pacing, verifies the outputs,
and exits. Its production 16,384×256 noon, dusk, night, and rain runs pass;
settled stages averaged 8.3–10.7 ms per frame. The disposable visual-parity
gate also passes a natural seam view and both textured rims. Same-process
layout switching clears stale state, and the production lifecycle passes
Overworld/Nether/End transitions, save/disconnect, and reopen. NeoForge also
passes the production/multi-seed structure matrix, a complete unattended
headless atlas prewarm, and the dedicated two-client seam/combat/block/bed/
death/physical-portal/boat/teleport/reconnect matrix. Loader-labelled Fabric
and NeoForge client/server packages, strict jar verification, same-commit
shared-contract comparison, and a real packaged macOS NeoForge client smoke
also pass. The shared GUI-scale-4 atlas map/control fixture passes all eleven
captures and its ordered live-revision probe on both loaders. A real graphical
Windows run, exact-candidate review, and owner release go/no-go remain.
Fresh and copied-1.21.11 dedicated servers launch with dimension-owned
storage. A real client completes resource/shader loading, a 100% atlas-backed
ring, tangent/radial captures, two natural wraps, and representative
gameplay/rim probes. The dedicated two-client seam/combat/stateful-block/bed/
death/physical-portal/boat/teleport/reconnect matrix also passes. The
multi-seed worldgen matrix covers all 14 major biome families, deliberate
seam-crossing structures, caves, ores, trees, loot, saved scarce-structure
outcomes, and exact reload evidence. A copied
16,384×256 world now passes the
Overworld/Nether/End transfer, save/disconnect, client-state cleanup, and
same-process reopen gate. Safe-small 6/12/28-chunk and production-size
tangent/radial visual handoff review and repeatable Fabric release staging also
pass. The complete production atlas regression gate passes generation/recovery,
live revisions, layout/lifecycle switching, two-client synchronization, and
resource/frame-pacing review. The P1–P4 topology, worldgen, protocol, and
renderer architecture parents are closed. Optional convenience packaging now
has a frozen exact candidate with passing macOS in-place, empty-data macOS
first-run, and dedicated-server smokes. A real graphical Windows launch,
independent release-candidate review, broader
gameplay compatibility, and compatibility work remain, so
the Fabric alpha is not a stable release yet. See
`docs/CURRENT_STATE.md` and `docs/VISUAL_HANDOFF_REVIEW_2026-08-01.md`.
The Fabric alpha `0.2.0+mc26.1.2` is currently **Under review** on Modrinth;
that submission does not make the remaining port gates complete. The approved
next order is NeoForge standalone parity, standalone gameplay/visual polish,
then exact-candidate release preparation. Broad third-party compatibility is
deferred until owner sign-off; see `docs/DUAL_LOADER_STANDALONE_PLAN.md`.

For any later Modrinth build, use the fail-closed local staging procedure in
[`docs/MODRINTH_RELEASE.md`](docs/MODRINTH_RELEASE.md). It stages only the
runtime jar and records a checksum plus the exact clean, pushed public branch
revision. It never uploads or changes the listing.

## Codex weekly usage pause

Before substantial RingWorld work and after a long tool-heavy milestone, run:

```sh
python3 scripts/codex_usage_monitor.py
```

Above 20% remaining, `OK` permits normal operation. At exactly 20% remaining
or below, `PAUSE` means pause all RingWorld work: do not dispatch new tasks,
stop active work at the next safe handoff, and do not resume below the
threshold without explicit owner authorization. Do not infer the weekly
allowance from context tokens or a shorter quota window. The optional
five-minute macOS monitor and its non-secret status file are documented in
[`docs/CODEX_USAGE_MONITOR.md`](docs/CODEX_USAGE_MONITOR.md). The secondary
agent uses a separate account and must monitor its own allowance.

## What this project is

RingWorld is a dual-loader mod ported from Minecraft Java 1.21.11 to 26.1.2.
Fabric is the current test release; NeoForge runtime and local packaging parity
are complete. No NeoForge artifact has been published yet.
The validated design turns only the Overworld into a finite band:

- canonical X runs around the circumference and is periodic;
- Z runs across a finite width;
- Y remains ordinary Minecraft height;
- the client bends intrinsic X/Y/Z coordinates into a cylindrical image;
- Nether and End remain vanilla.

This is an engine-level mod. Both the server and every client need it.

## Licensing and distribution policy

RingWorld is open-source software licensed under the Mozilla Public License
2.0. The authoritative terms are in [`LICENSE`](LICENSE), with practical
distribution guidance in [`docs/LICENSING.md`](docs/LICENSING.md). Never
describe the project or an artifact as MIT, proprietary, source-unavailable,
or noncommercial-only.

- Existing RingWorld source files and modifications to them remain MPL-2.0
  when distributed. Separate files in a larger work may use other licences.
- Redistribution, commercial use, forks, ports, and modpack inclusion are
  permitted subject to the MPL-2.0. Do not add restrictions that contradict
  the licence.
- Every mod jar must declare `MPL-2.0` and contain
  `LICENSE-RINGWORLD.txt`.
- Every outer client or server package must include the current `LICENSE`
  beside its launcher or primary instructions.
- Distribution of an executable must also make the MPL-covered Source Code
  Form available by reasonable means and tell recipients how to obtain it.
- Packaging and deployment checks must inspect the nested RingWorld jar and
  fail if `MIT`, `LicenseRef-RingWorld-Evaluation-1.0`, another stale licence
  identifier, or a missing licence file is found.
- Contributions are accepted under MPL-2.0 subject to
  [`CONTRIBUTING.md`](CONTRIBUTING.md). Do not accept code the contributor is
  not entitled to license, including decompiled Minecraft source.
- MPL-2.0 does not grant rights to project trademarks or branding. Do not
  imply that a fork or compatibility build is an official RingWorld release.

The unintended MIT-labelled 0.1.0 public test bundles were withdrawn on
2026-07-28. Older copies may still exist; do not imply that a new licence
retroactively revokes rights previously granted to a recipient. Likewise,
copies distributed under the former RingWorld Evaluation License retain the
terms attached to those copies; MPL-2.0 applies to repository versions released
under it.

## Loader support policy

Fabric is the current released-test implementation. NeoForge has full
graphical, dedicated-server, topology, worldgen, atlas, storage, multiplayer,
and local packaging parity; hosted publication is not yet authorized. Future
development must not deepen Fabric coupling. Design new gameplay, topology, persistence,
worldgen, rendering math, protocol models, and tests as loader-agnostic common
code. When a loader API is unavoidable, isolate it behind a narrow platform
adapter and provide, or leave a documented implementation path for, both
Fabric and NeoForge.

In particular:

- do not add Fabric event, networking, path, registry, or entrypoint calls to
  otherwise loader-neutral classes;
- keep wire formats, saved-data formats, coordinate rules, shader contracts,
  and compatibility APIs identical across loaders;
- prefer shared mixins against Minecraft internals when their targets and
  behavior are valid on both platforms;
- put loader metadata, lifecycle registration, payload plumbing, environment
  lookup, packaging, and launch fixtures in platform-owned code;
- add platform-parity tests for any behavior that crosses an adapter;
- document a deliberate single-loader exception before merging it, including
  why shared or dual support is not currently practical.

Dual Fabric/NeoForge support is the intended architecture, not a claim about
the artifacts currently released. Do not advertise NeoForge compatibility
until its client, dedicated server, topology, rendering, and multiplayer gates
pass.

## The invariants

Do not violate these without deliberately redesigning the architecture and
updating all documentation and tests.

1. **The server owns one X plane.** Persistent blocks, chunks, entities,
   tickets, scheduled ticks, and saves use X in `[0, circumference)`.
2. **A client chart is not another world copy.** A client may display canonical
   X=0 as presentation X=`circumference`, but that image is transient and must
   never be persisted or create a second server chunk.
3. **Natural seam travel is a local step.** Crossing the seam must preserve
   velocity, yaw, pitch, camera continuity, vehicle state, and nearby entity
   visibility. It must not use a corrective teleport packet.
4. **Use nearest periodic images for relationships.** Distance, reach,
   queries, raycasts, explosions, AI targets, packet effects, and tracking use
   the shortest X delta.
5. **Canonicalize at ownership boundaries.** Convert presentation coordinates
   when data enters canonical storage or leaves the client. Do not scatter
   `% circumference` through unrelated code.
6. **Gameplay gravity stays vanilla `-Y`.** Intrinsic coordinates make this
   local outward gravity once rendered on the cylinder. There is no radial
   physics rewrite.
7. **Real chunks are authoritative.** The terrain atlas and complete-ring GPU
   surface are visual LOD only. They do not provide collision, entities,
   block interaction, or simulation.
8. **World dimensions are immutable after first Overworld load.** Bootstrap
   config creates `RingWorldSettings`; saved settings win thereafter.
9. **Only the Overworld is curved and periodic.** Every mixin needs an explicit
   Overworld guard unless its state is already Overworld-scoped.
10. **Normal render distance is intentional.** The discarded forced
    100-chunk experiment caused unacceptable CPU/GPU cost. Do not restore it
    as the distant-visibility solution.

## Coordinate vocabulary

Use these exact terms in code and documentation:

- **canonical coordinate**: the single server/save coordinate, X in `[0, C)`;
- **presentation coordinate/chart**: a nearby periodic image used by a client
  for continuous travel, such as X=`C+2`;
- **intrinsic coordinate**: Minecraft surface coordinates `(X, Y, Z)` before
  visual bending;
- **physical ring space**: the cylindrical 3D embedding used by rendering and
  compatibility helpers;
- **nearest image**: the copy of a canonical X closest to an observer's
  presentation X.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for formulas and data flow.

## Repository map

- `src/main/java/dev/ringworld/world/`: pure geometry, topology, settings,
  validation/cost reports, render profiles, worldgen coordinate transforms,
  atlas format, and sky-cycle math.
- `src/main/java/dev/ringworld/api/`: versioned read-only coordinate/pose API
  and loader-neutral compatibility inventory.
- `src/platform/fabric/java/`: Fabric server/common entrypoint, lifecycle,
  networking, path, command, and discovery adapters.
- `src/platform/fabricClient/java/`: Fabric client entrypoint, networking,
  lifecycle, cache-path, and automated-client adapters.
- `src/platform/neoforge/java/`: NeoForge bootstrap, dedicated-server
  lifecycle, command, and payload-transport adapters.
- `src/platform/neoforgeClient/java/`: NeoForge client lifecycle, payload,
  render-pipeline, cache-path, and diagnostic-world adapters.
- `neoforge/`: NeoForge 26.1.2.87 ModDevGradle 2.0.143 Java 25 module and its
  isolated development run directories.
- `src/main/java/dev/ringworld/mixin/`: authoritative server/worldgen patches.
- `src/main/java/dev/ringworld/server/`: lifecycle, canonical entity folding,
  atlas pregeneration, local smoke fixtures, and multiplayer harness.
- `src/main/java/dev/ringworld/net/`: required geometry handshake and atlas
  payloads.
- `src/client/java/dev/ringworld/client/`: client state and automated clients.
- `src/client/java/dev/ringworld/client/mixin/`: presentation packet mapping,
  finite-edge meshing, curved culling, entity/block-entity rendering,
  interaction overlays, shader globals, and sky rendering.
- `src/client/java/dev/ringworld/client/render/`: curved frustum and the
  complete-ring texture prototype.
- `src/client/resources/assets/minecraft/shaders/`: vanilla shader overrides
  and the extended Globals include for terrain and clouds.
- `src/test/`: pure unit tests for geometry, topology, dimensions, rendering
  profiles, atlas, and sky timing.
- `deploy/`: generic client-launcher, dedicated-server, and Modrinth staging
  templates. `scripts/prepare_release_packages.py` assembles optional local
  client/server packages from them; generated archives remain ignored.
- `docs/DIMENSION_SCALING_PLAN.md`: authoritative audit and staged plan for
  removing test-world assumptions from custom dimensions.
- `docs/ATLAS_PREGENERATION_PLAN.md`: planned **Generate Entire Ring** UI and
  extraction of the current atlas scheduler into one resumable service.
- `docs/DUAL_LOADER_STANDALONE_PLAN.md`: approved NeoForge-first execution,
  standalone polish, release, and deferred-compatibility order.
- `docs/ATLAS_FIDELITY_BENCHMARK_2026-08-01.md`: production step 8/4/2/1
  resource comparison and the decision to retain fixed eight-block sampling.
- `docs/MINECRAFT_26_1_PORT_PLAN.md`: authoritative Minecraft 26.1.2 port,
  agent ownership, integration, validation, and deployment plan.
- `docs/MINECRAFT_1_21_11_FINAL_BASELINE.md`: immutable pre-port validation,
  hashes, performance evidence, and protected rollback inventory.
- `docs/MINECRAFT_26_1_COMPILER_BASELINE.md`: historical Java 25/26.1.2
  compiler inventory and the subsequent green build/server checkpoint.
- `dist/`, `run/`, `run-multiplayer/`, `run-atlas-ui/`, `run-headless-prewarm/`, `logs/`, `.gradle/`, and `build/`:
  generated or local runtime state; all are intentionally ignored.

The complete mixin ownership table is in
[`docs/MIXIN_MAP.md`](docs/MIXIN_MAP.md).

## Build and fast validation

The active port requires Java 25:

```sh
JAVA_HOME=/path/to/jdk-25/Contents/Home \
PATH="$JAVA_HOME/bin:$PATH" \
./gradlew clean test build --console=plain
```

The expected development artifact is
`build/libs/ringworld-0.2.0+mc26.1.2.jar`; the current suite contains 235
unit/parameterized cases. A green source build and dedicated-server launch are
not a release gate: required client, rendering, gameplay, multiplayer,
packaging, and staging checks must remain green together.

The NeoForge module uses the same Java 25 toolchain and also passes all 235
unit/parameterized cases:

```sh
./gradlew :neoforge:test :neoforge:build --console=plain
```

Both loaders now provide a task named `runServer`; always select the loader
explicitly: `./gradlew :runServer` for Fabric or
`./gradlew :neoforge:runServer` for NeoForge. The NeoForge dedicated launch
has reached `Done` and observed atlas progress. `./gradlew
:neoforge:runProductionProjectionClient -PringNeoForgeProjectionSource="NeoForge Test"`
copies the named ignored source save into an isolated run, waits for atlas
completion, produces tangent/handoff/radial diagnostics, verifies them, and
exits. Noon, dusk, night, and rain pass. The qualified NeoForge visual-parity,
layout-switch, production-lifecycle, stronghold/worldgen, headless-prewarm,
and dedicated two-client gates also pass on isolated fixtures. Local
dual-loader packaging and the packaged macOS NeoForge client smoke also pass;
a qualified `:neoforge:runAtlasUiClient` additionally verifies the shared
pause-menu atlas workflow and all eleven screenshots. The remaining release
gates are tracked under #12, #13, and #97.

`scripts/stage_modrinth_release.py --loader both --build` checks the active
Java generation, always performs a fresh dual build, pair-validates the known
outputs, and writes provenance manifests consumed by optional packaging. It
accepts no alternate jar path. Keep that fail-closed Java 25 preflight
synchronized with the active Minecraft toolchain; do not replace its direct
setup error with the compiler failure produced by an older Gradle JVM.

The frozen 1.21.11 tag uses Java 21 and passes 73 unit/parameterized cases plus
the runtime suites recorded in
[`docs/MINECRAFT_1_21_11_FINAL_BASELINE.md`](docs/MINECRAFT_1_21_11_FINAL_BASELINE.md).

## Local packaged client

The active macOS development instance is generated under:

```text
dist/client-bundle/.prism-data/instances/RingWorld-Test/
```

The current saved test world is:

```text
dist/client-bundle/.prism-data/instances/RingWorld-Test/.minecraft/saves/New World
```

The one-click launcher can open that world with:

```sh
dist/client-bundle/.launcher/macos/Prism\ Launcher.app/Contents/MacOS/prismlauncher \
  -d "$PWD/dist/client-bundle/.prism-data" \
  -l RingWorld-Test \
  -w "New World"
```

Treat all of `dist/` as sensitive local state. A live Prism data directory can
contain Microsoft/Minecraft tokens in `accounts.json`. Never remove `dist/`
from `.gitignore`, stage it, archive it for sharing after login, or print its
credential files.

## Change workflow

**Documentation is part of every change.** For every code, configuration,
protocol, build, test, packaging, deployment, or behavior change, update all
relevant project documentation in the same change. At minimum, check
`README.md`, `AGENTS.md`, and every applicable file under `docs/`. A change is
not complete while its documentation describes the previous behavior. This
also applies to renamed classes, changed constants, new or removed mixins,
altered test commands, known limitations, and rejected or superseded designs.

1. Check `git status` and preserve unrelated user work.
2. Identify which coordinate domain the failing value belongs to.
3. Prefer changing the shared pure helper (`RingGeometry`, `RingTopology`,
   `RingChunkCoordinates`) over adding another local wrap calculation.
4. Inspect every call path on both sides of the seam: server storage, packet
   encoding, client projection, renderer, and reconnect/save.
5. Add or extend a pure unit test where possible.
6. Run the validation appropriate to the current port gate. The active Java 25
   branch requires `./gradlew test build`; runtime-sensitive changes also
   require the relevant isolated server or client launch.
7. For topology or packet changes, run the two-client multiplayer harness.
8. For rendering changes, launch a real world and inspect upward views, the
   live/LOD handoff, the seam, both width rims, day/dusk/night, and movement
   frame pacing.
9. Update the appropriate file under `docs/` in the same change.

Mixin method descriptors on the active branch target unobfuscated Minecraft
26.1.2. A Minecraft, mappings, Loader, Loom, or Fabric API upgrade is a porting
project: audit every injection target and shader ABI rather than only changing
version numbers.

## Current implementation cautions

- The complete-ring renderer accepts a current-world partial atlas once it has
  at least one trustworthy cell. Missing cells stay transparent; progressive
  updates reuse a source-resolution texture and one reference-height mesh,
  then completion upgrades exactly once to the expanded texture and detailed
  terrain-height mesh. Session disconnect/settings handlers must still clear
  the static GPU texture and mesh, and the renderer must reject absent,
  zero-cell, corrupt, or wrong-world atlases.
  Fabric disconnect callbacks may arrive on a network thread, so they must
  enqueue cache saves and GPU teardown onto the client thread. Failing to clear
  that state lets a newly created world display the previous world's ring.
- `NoiseBasedChunkGenerator.iterateNoiseColumn` is the shared vanilla
  base-height/base-column path used to anchor structures before their chunks
  exist. Canonicalize its X argument exactly once at that method boundary
  before vanilla derives cell/cache/interpolation positions, and run its
  `NoiseChunk` construction under the same `RingNoiseSamplingContext` as real
  terrain whenever the generator has Overworld geometry. Do not patch
  individual structures or let this path use a raw alias or flat router:
  villages and other surface structures then choose a Y that disagrees with
  the cylindrical terrain below them. Leave Z and the null-geometry Nether/End
  path vanilla.
- Atlas tile application is idempotent. Duplicate dirty tiles must not advance
  the client render revision, force another cache save, or rebuild the complete
  texture/mesh. Only the actual incomplete-to-complete transition bypasses the
  normal publish/save coalescing windows. Incomplete visual updates publish at
  most once per second. A genuinely changed complete atlas publishes after
  three quiet seconds or at a ten-second maximum delay; the later ordered
  revision commit saves durable cache state but must not request a second
  identical render build.
- Complete-ring texture pixels, relief, mips, and `NativeImage` levels are
  prepared asynchronously from `RingTerrainAtlas.snapshot()`. The render
  thread alone validates world hash/geometry/revision and uploads the result.
  Session clear must invalidate the build generation and close both completed
  and abandoned images; do not sample the mutable live atlas on that worker.
- Atlas format 6 adds a durable monotonic surface revision. Tiles never commit
  that revision individually: `terrain_atlas_revision_v1` arrives only after
  all preceding tiles for the batch. Complete clients stay subscribed after
  their initial transfer. Reconnect cache reuse requires exact world hash,
  geometry, completeness, and revision; a mismatch receives a full snapshot.
- `LevelMixin` observes successful server `Level.setBlock` mutations and only
  enqueues canonical atlas cells. `RingAtlasPregenerationService` remains the
  sole writer, processes at most 64 recaptures per tick, and collapses more
  than 4,096 exact pending cells into tile work. Do not sample inline in the
  mixin or create another edit listener/writer.
- Issue #69 retains one supported eight-block atlas sample step. Finer 4/2/1
  candidates multiply production cells and cold tile traffic by 4/16/64 while
  leaving the capped GPU texture and mesh unchanged. Do not make the step
  adaptive or configurable without matched visual evidence plus a persisted,
  versioned identity and deliberate cache rebuild.
- `RingAtlasPregenerationCursor` is shared, loader-neutral traversal state for
  the future atlas service. It is X-major, derives finite Z from
  `RingGeometry.minChunkZ()`, resumes from present atlas cells, and never uses
  a power-of-two shortcut for canonical X. Keep scheduler, command, lifecycle,
  and loader concerns out of this model until the service extraction lands.
- `RingAtlasPregenerationService` is now the only server-side writer after an
  atlas loads. Its state transitions, future consumption, capture, dirty-tile
  publication, checkpointing, and verified completion run on the server
  thread. Handles enqueue off-thread control requests; do not mutate the
  atlas from the Fabric adapter. Retain a selected canonical chunk until a
  full result is captured, including retry/cancel/unload paths, or a failed
  future can skip terrain permanently.
- `-Dringworld.headlessPrewarm=true` is an explicit dedicated-server-only
  adapter mode. It suppresses normal background autostart, safely replaces
  only the unstarted config-disabled `IDLE` handle, rejects joins, and waits
  for the service's verified atlas completion before normal world save, atomic
  JSON result write, and halt. SIGTERM writes `INTERRUPTED` after checkpoint;
  the Gradle wrapper, not Minecraft's process exit code, converts a non-
  `COMPLETE` terminal report to failure. Use only `run-headless-prewarm/` or a
  separately prepared disposable runtime directory; never open a copy source.
- An explicit headless launch can reject an ordinary copied Overworld before
  `ServerLevelEvents.LOAD`, while `ServerLevel` attaches its tick schedulers.
  That narrow constructor-tail bridge writes `REJECTED` evidence with
  unavailable identity sentinels, then rethrows the unchanged settings error.
  Do not let the invalid world continue with bootstrap geometry or add hot-path
  topology exceptions to mask this intentional rejection.
- Initial spawn selection is the one creation-time bootstrap geometry use: it
  runs before an Overworld has saved settings and delegates finite-Z clamping
  to loader-neutral `RingSpawnBounds`. Keep it scoped to first-world creation;
  saved-world runtime logic must never read bootstrap geometry.
- `ring_surface.vsh` deliberately clamps only far-out proxy clip-space Z while
  preserving X/Y/W. Minecraft's level far plane is derived from chunk render
  distance and clips most of a production 16,384-block cylinder, especially
  in the tangent/along-ring view. Do not remove this as redundant sky code or
  replace it by globally increasing render distance/far depth. Re-run both the
  tangent and radial-up projection captures after changing projection,
  celestial render order, or the proxy pipeline.
- Settings payload identifiers are wire-layout-versioned
  (`settings_v2`/`settings_ack_v2`). Never append or reorder codec fields while
  reusing an old identifier; old clients crash on unread bytes before a useful
  rejection can be sent. Advance the channel generation and keep the
  `RingProtocolIdentityTest` expectation synchronized.
- The mandatory play handshake is exact-version and exact-required-channel,
  not feature-bit negotiation. `RingHandshakeTracker` gives each join 300
  ticks to acknowledge, gates every RingWorld request, treats duplicate
  acknowledgement idempotently, and clears on disconnect. Keep its state
  loader-neutral and server-thread-owned; never let an atlas request bypass it.
- Atlas-generation payloads are separately versioned (`atlas_pregen_*_v1`).
  Preserve their explicit action/state wire values and complete immutable
  status snapshot; never append to atlas/settings codecs. The pause-menu map
  is Overworld-and-acknowledgement guarded, non-pausing so integrated-server
  generation continues, read-only for non-owners, and clears its status/toast
  state on disconnect or layout switch. Server adapters must rate-limit
  observers to 20 ticks plus transitions and recheck every control request on
  the server thread.
- Shareable launcher templates live in `deploy/client/`. Fabric and NeoForge
  use separate managed Prism instance IDs. Every launch must refresh only that
  instance's RingWorld jar and, for Fabric, Fabric API while preserving
  accounts, saves, options, resource packs, local configuration, unrelated
  mods, and unrelated instance settings. The macOS launcher may
  change only Java selection keys after validating an executable as Java 25;
  its search locations must remain portable and credential-free. When Java 25
  is absent, preserve Prism automatic selection. Test both paths with an
  isolated `HOME`, plus fresh and in-place upgrade paths, before publishing.
- Optional packages must be built with `scripts/prepare_release_packages.py`
  from a format-2 staging manifest created by the mandatory clean dual-build
  release gate. Never restore free-form jar or source-revision inputs. The
  builder emits no web content and has no publish/deploy path. Keep its
  reproducible ZIPs and checksum manifests under ignored local staging only.
- `/ringworld atlas status|pause|resume` controls background pregeneration.
  Pause is process-local and does not alter immutable saved layout.
- Atlas format 6 represents exposed top-face height and
  texture-luminance-corrected, biome-tinted colour from the actual highest
  surface block at eight-block source resolution, plus the durable surface
  revision used for reconnect validation. `ChunkAccess.getHeight`
  already returns that block's Y; subtracting one samples dirt beneath grass.
  Dedicated servers do not load Minecraft's grass/foliage colormap textures,
  so their zero tint lookup must fall back to the sampled block's map colour;
  otherwise a freshly pregenerated remote atlas is mostly black.
  The client adds relief shading and a filtered mip chain, but the expanded GPU
  image remains a visual approximation rather than captured block geometry.
- The active live/LOD handoff is a broad shader cross-fade: proxy opacity starts
  at 68% of view distance and is effectively opaque by 98%. `terrain.fsh`
  progressively dithers away outer live terrain from 78% through 102% so the
  earlier sky-pass proxy is actually visible through opaque chunks. Terrain
  contrast is intentionally stronger than the older fog-band design. Keep both
  shader curves, local rim exclusion, and transition tuning together.
- `RingRenderProfile` is the authoritative source for live dither, proxy
  alpha/detail distances, `C/2` clamping, and GPU texture/mesh budgets. Cloud
  fade still combines vanilla cloud range and circumference in its shader;
  avoid introducing another copy of the terrain handoff percentages.
- The active surface shader binds Minecraft's live lightmap as `Sampler2` and
  uses its full-skylight/no-block-light texel for atlas albedo. Do not restore
  the old scalar nighttime floor: it left the proxy bright green while live
  terrain received RGB night exposure. The static atlas deliberately does not
  reproduce local block lights.
- The active sky has no shadow slabs. `SkyRenderingMixin` changes both vanilla
  `30.0F` sun half-width constants to `RingSkyCycle.SUN_HALF_WIDTH` only during
  the fixed RingWorld redraw, and replaces that draw's dynamic colour with the
  smooth global `SunVisual` phase. The old twenty-panel design survives only
  in `docs/SUN_RENDERING_SNAPSHOT_2026-07-26.md`.
- The fixed sun uses Minecraft's original celestial sprite. Do not add a
  global `assets/minecraft/textures/environment/celestial/sun.png` override
  unless a future design explicitly calls for custom art; the abandoned
  containment-array experiment made the small sun visually busy.
- Structure placement and arbitrary third-party mods have not received broad
  seam coverage. Coordinate-sensitive mods, renderers, gravity systems, and
  chunk internals are likely incompatible.
- Compatibility contract/API version 1 is documented in
  `docs/COMPATIBILITY.md`. Keep `RingCompatibilityContract`, the Fabric probe,
  `fabric.mod.json`'s `ringworld:compatibility_api`, public API documentation,
  and tests synchronized. Detection logs high-confidence unsupported mod IDs;
  it must not silently claim that unlisted combinations are supported.
- Package and Modrinth staging must reject a jar whose
  `ringworld:compatibility_api` metadata differs from the Java contract. The
  Windows launcher/update path has a real-Windows CI fixture, but that fixture
  uses a harmless Prism stand-in and is not graphical Minecraft evidence.
- New-world strongholds carry a saved guarantee bit into both the placement
  state and noise generator. After vanilla builds the complete piece graph,
  `StrongholdStructureMixin` applies the smallest X/Z translation needed to
  keep the terrain-adjusted bounds inside canonical X and finite Z. Keep the
  policy flag `volatile`: generation runs on worker threads. Missing-policy
  legacy worlds must remain untouched.
- The extended Globals UBO publishes the complete format-2 layout and render
  profile. Its std140 field order, `GlobalSettings` allocation, and every
  custom program that declares Globals must change together.
- The dedicated multiplayer clients must not connect before
  `Minecraft.isGameLoadFinished()`. Joining during the initial resource
  reload can run leaf display ticks against unprepared particle sprites.
- `runLayoutSwitchClient` opens two existing saves in one JVM and stops after
  logging its result. Keep it non-destructive: it may save normally, but must
  not move players or edit terrain.
- `runProductionLifecycleClient` first copies a named production save into its
  own ignored run directory. Its test-only coordinator must use the 26.1
  `TeleportTransition` API, stay separate from smoke/layout-switch/multiplayer,
  and leave the source save untouched. The client, not the coordinator, owns
  non-Overworld inactivity and exact restored-layout/atlas assertions. Let
  Minecraft's integrated-server disconnect path own saving; never call
  `MinecraftServer.saveEverything` from the render thread.
- `RingWorldCreationScreen.extractRenderState` must not call a background
  extraction method. Minecraft 26.1's
  `Screen.extractRenderStateWithTooltipAndSubtitles` already owns the frame's
  single legal menu-blur pass.
- New worlds persist `RingStructurePolicy` with the mandatory stronghold bit.
  A missing policy identifies an older world and deliberately retains its
  previous vanilla structure placement. Do not infer or enable the guarantee
  from geometry alone; that silently changes an immutable existing world.
- The optional ocean-monument request is also new-world-only. Policy format 2
  persists `PENDING` and then one terminal `SATISFIED` canonical candidate or
  typed `UNSATISFIED` result before structure generation. Bind only the exact
  built-in structure-set/structure holders, keep the 64-block seam/rim
  envelope, and never recompute or move a saved result after a datapack change.
  Candidate selection and both monument biome gates must use the generator's
  periodic climate sampler; `RandomState.sampler()` is flat. Forced placement,
  locate, references, and reload must remain canonical and use no alias chunk.
- The reusable multiplayer fixture must clear stale automated boats, wait for
  both clients to acknowledge the new boat before moving it, detect folds by
  periodic motion rather than a one-block sample window, and compare explicit
  return positions and teleport targets independently of the current
  presentation chart.
- The vanilla entity loop's asynchronous simulation graph can retain the old
  side of a natural seam crossing. `ServerWorldMixin` first checks the
  canonical graph key, then falls back only to non-spectator players within
  the configured nearest-periodic chunk distance. Do not broaden this into
  global forced ticking or replace the configured simulation distance with a
  hard-coded radius.
- `PersistentEntitySectionManager` seam load requests must call
  `ensureChunkQueuedForLoad` directly. Routing that request through
  `updateChunkStatus` can downgrade an already-`TICKING` seam chunk to
  `TRACKED`, intermittently freezing items, projectiles, and mobs just after
  X folds through zero.
- A mob fold must shift its active navigation path, target, and raw-coordinate
  stuck/timeout caches by the exact canonical X delta. Recomputing only the
  target leaves the old-chart path behind and can stop navigation at the seam.
- `RingRenderProfile` visual-policy version 5 owns the live/proxy/detail
  transitions, reveal, haze, and local cloud fade. Keep Java profile fields
  and the seven appended RingWorld Globals vectors synchronized. The 2026-08-01
  6/12/28 review retained its deterministic interleaved-gradient dither; an
  unordered pixel-hash experiment produced worse salt-and-pepper grain and was
  rejected. Profile 5 also raises the circumference mesh cap to 2,048 so the
  16,384-block default retains the atlas's eight-block height spacing; do not
  reduce it to the old visibly faceted 512-segment production mesh without a
  replacement visual/resource review.
- Cloud base is synchronized as saved wall top plus eight blocks. Do not
  reintroduce a literal Y=104; custom wall height must move both.
- The active local development geometry is the safe-small 2,048-by-416 preset
  (128 by 26 chunks). The retired 1,600-block circumference is not safe across
  the complete vanilla build height: with `SURFACE_Y=64`, its physical centre
  lies near Y=318.65. It may appear only as a retired deployment/rollback note
  or a required validation-failure fixture. The public server also uses the
  safe-small 2,048-by-416 preset as of 27 July 2026.
- The production/default geometry is 16,384-by-256 (1,024 by 16 chunks). Width
  256 is intentional: the formerly default 4,096-block band looked too broad
  in the sky. The power-of-two circumference is exactly 32 region widths,
  2,048 source-atlas columns, and four blocks per capped proxy texel. Existing
  saved worlds remain immutable, and the 15,552-by-4,096 layouts in historical
  validation evidence are not current defaults.
- The local visual harness reads `testViewDistanceChunks` (2–32) before
  reducing to six chunks for seam/rim traversal. Use 6/12/28 for the safe-small
  capture matrix. It derives capture pitch from the physical target surface;
  do not restore a fixed pitch. If pregeneration is disabled and no complete
  atlas cache exists, its 600-tick timeout skips only the LOD image; do not
  count that case as visual LOD evidence.
- Through C=4,096, its accelerated second-circuit middle uses a 4–8 block
  circumference-derived step in a high flight lane. Larger matrix cases sample
  the far-side chart with an explicit setup teleport. Both actual seam
  approaches still run at 0.25 blocks per tick at Y=120; keep setup and
  assertion roles separate when changing test timing.
- `ChunkBuilderBuiltChunkMixin` bypasses vanilla's eight-neighbour
  mesh-readiness check only for chunk rows outside the finite Z band. Removing
  that exception makes genuine rim blocks collide but remain invisible;
  broadening it to interior neighbours risks meshing incomplete chunks.
- `ChunkRenderingDataPreparerMixin` deliberately disables vanilla smart
  six-face section occlusion only in the RingWorld Overworld. The flat
  visibility graph can hide sections that cylindrical rendering bends back
  into view. Curved frustum and render-distance culling must remain enabled to
  bound the performance cost.
- `LivingEntitySleepingPositionMixin` maps only the local client's replicated
  sleeping `BlockPos` to its nearest presentation image. Bed positions remain
  canonical in synchronized entity data, saves, and all server logic. Do not
  replace this with a server-side sleeping offset or map other entities' beds:
  vanilla's client callback, wake-up, orientation, and bed-existence paths
  must all use the same nearby local copy.
- `ServerPlayerSleepMixin` replaces only the private vanilla bed-reach box in
  the RingWorld Overworld with the equivalent nearest-periodic X test. It also
  realigns the connection movement baselines after the server moves a player
  into a sleeping pose. Keep the bed position canonical, retain vanilla Y/Z
  limits, and do not apply either path in Nether or End.
- Filled-map pixels, player/banner/frame decorations, and spawn/lodestone/recovery
  compass targets use their nearest periodic X image only in the RingWorld
  Overworld. Banner/frame positions and compass tracker targets remain
  canonical saved data; vanilla map centres remain one immutable saved
  reference (which may be seam-equivalent to C after vanilla grid rounding).
  Do not create another saved map copy or extend this slice to the locator bar
  without a separate audit.
- The reusable multiplayer harness waits for both real clients to report a
  fully loaded world before setup teleports. Its night fixture advances the
  26.1 `WorldClock` monotonically to the next 13,000-tick phase; rewinding to
  absolute day-zero time makes reused-world bed tests nondeterministic.
- Its extended water fixture seals a two-cell trough, clears canonical X=0,
  places the only source at C-1, and must assert water at X=0 on both server
  and clients. Observing C-1
  only re-observes the source. The historical 2026-08-01 dedicated evidence
  predates this destination assertion and must not be described as its pass.
- Its extended hostile-navigation fixture clears and bounds a short ground lane,
  removes only entities carrying its dedicated navigator tag before reuse, and
  requires a persistent Zombie to complete vanilla navigation from C-5 toward
  X=2, finish its path within the target tolerance, and fold naturally into
  canonical low X. It has no client visual
  assertion and needs a fresh dedicated run before being claimed passed.
- Rigid models submitted outside the terrain shader must use
  `RingObjectTransform`: embed the anchor with `toCameraLocal`, then rotate
  the model into that anchor's tangent frame. `EntityRenderManagerMixin` and
  `LevelRendererMixin` deliberately share it. A flat camera-relative
  translation makes chests, lectern books, breaking overlays, and outlines
  rise out of curved ground as the player approaches.
- The frozen 1.21.11 Mojang baseline targeted the unnamed
  `ServerLevel.method_31420` entity-tick lambda. Minecraft 26.1 exposes the
  same call inside named `ServerLevel.tick`; the active mixin targets `tick`
  and must not regress to a synthetic or optional target.

The detailed current status and open risks are maintained in
[`docs/CURRENT_STATE.md`](docs/CURRENT_STATE.md).

## Definition of done for risky changes

A topology/rendering change is not done merely because Minecraft launches.
Completion means:

- canonical storage never creates an X chunk outside the one circumference;
- two players can see and affect each other through the seam;
- natural crossing has no camera pop or corrective position packet;
- movement is smooth at ordinary render distance;
- chunks, light, biomes, blocks, sounds, particles, and entities remain on the
  same presentation chart;
- save/reconnect, explicit teleport, death/respawn, and vehicle correction do
  not retain a stale chart;
- real terrain and the distant ring remain aligned while looking upward;
- Nether and End remain unchanged;
- unit build and relevant integration tests pass.
