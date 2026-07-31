# RingWorld agent guide

This file is the first-stop operating guide for coding agents working in this
repository. Read it before changing topology, networking, world generation, or
rendering. Detailed design documents live under [`docs/`](docs/README.md).

Last playable code audit: 2026-07-28, covering the final Minecraft 1.21.11
implementation identified in the private development archive as
`mc-1.21.11-final` at commit `2c98650`. That pre-public ref is provenance only
and is intentionally not present in the clean public Git history.

Active port checkpoint: Minecraft 26.1.2/Java 25 integrated safe-small runtime
gate. Common/client compilation and all 90 unit/parameterized cases pass.
Fresh and copied-1.21.11 dedicated servers launch with dimension-owned
storage. A real client completes resource/shader loading, a 100% atlas-backed
ring, tangent/radial captures, two natural wraps, and representative
gameplay/rim probes. The dedicated two-client seam/combat/block/boat/teleport/
reconnect matrix also passes. Multi-size visual review, automated-harness
completion, packaging, and staging remain, so the port is not playable yet.
See `docs/CURRENT_STATE.md`.

## What this project is

RingWorld is a Fabric mod being ported from Minecraft Java 1.21.11 to 26.1.2.
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

The current runnable implementation is Fabric-only, but future development
must not deepen that coupling. Design new gameplay, topology, persistence,
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
- `src/main/java/dev/ringworld/mixin/`: authoritative server/worldgen patches.
- `src/main/java/dev/ringworld/server/`: lifecycle, canonical entity folding,
  atlas pregeneration, local smoke fixtures, and multiplayer harness.
- `src/main/java/dev/ringworld/net/`: required geometry handshake and atlas
  payloads.
- `src/client/java/dev/ringworld/client/`: client state and automated clients.
- `src/client/java/dev/ringworld/client/mixin/`: presentation packet mapping,
  finite-edge meshing, curved culling, entity rendering, shader globals, and
  sky rendering.
- `src/client/java/dev/ringworld/client/render/`: curved frustum and the
  complete-ring texture prototype.
- `src/client/resources/assets/minecraft/shaders/`: vanilla shader overrides
  and the extended Globals include for terrain and clouds.
- `src/test/`: pure unit tests for geometry, topology, dimensions, rendering
  profiles, atlas, and sky timing.
- `deploy/`: generic client-launcher and dedicated-server templates. Generated
  packages and publication infrastructure are intentionally not versioned.
- `docs/DIMENSION_SCALING_PLAN.md`: authoritative audit and staged plan for
  removing test-world assumptions from custom dimensions.
- `docs/ATLAS_PREGENERATION_PLAN.md`: planned **Generate Entire Ring** UI and
  extraction of the current atlas scheduler into one resumable service.
- `docs/MINECRAFT_26_1_PORT_PLAN.md`: authoritative Minecraft 26.1.2 port,
  agent ownership, integration, validation, and deployment plan.
- `docs/MINECRAFT_1_21_11_FINAL_BASELINE.md`: immutable pre-port validation,
  hashes, performance evidence, and protected rollback inventory.
- `docs/MINECRAFT_26_1_COMPILER_BASELINE.md`: historical Java 25/26.1.2
  compiler inventory and the subsequent green build/server checkpoint.
- `dist/`, `run/`, `run-multiplayer/`, `logs/`, `.gradle/`, and `build/`:
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
`build/libs/ringworld-0.2.0+mc26.1.2.jar`; the current suite contains 90
unit/parameterized cases. A green source build and dedicated-server launch are
not a release gate: required client, rendering, gameplay, multiplayer,
packaging, and staging checks still remain.

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

- The complete-ring texture is generated only after the terrain atlas is
  complete. Before that, only real chunks and the remaining atmospheric
  effects are available. Session disconnect/settings handlers must clear the
  static GPU texture and mesh, and the renderer must reject incomplete atlases;
  otherwise a newly created world displays the previous world's ring.
- Atlas tile application is idempotent. Duplicate dirty tiles must not advance
  the client atlas revision, force another cache save, or rebuild the complete
  texture/mesh. Only the actual incomplete-to-complete transition bypasses the
  normal publish/save coalescing windows.
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
- Shareable launcher templates live in `deploy/client/`. Every launch must
  refresh the bundle-managed RingWorld/Fabric jars in an existing Prism
  instance while preserving accounts, saves, options, resource packs, local
  configuration, and instance settings. Test both fresh and in-place upgrade
  paths before publishing.
- `/ringworld atlas status|pause|resume` controls background pregeneration.
  Pause is process-local and does not alter immutable saved layout.
- Atlas format 5 represents exposed top-face height and
  texture-luminance-corrected, biome-tinted colour from the actual highest
  surface block at eight-block source resolution. `ChunkAccess.getHeight`
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
- The extended Globals UBO publishes the complete format-2 layout and render
  profile. Its std140 field order, `GlobalSettings` allocation, and every
  custom program that declares Globals must change together.
- The dedicated multiplayer clients must not connect before
  `Minecraft.isGameLoadFinished()`. Joining during the initial resource
  reload can run leaf display ticks against unprepared particle sprites.
- `runLayoutSwitchClient` opens two existing saves in one JVM and stops after
  logging its result. Keep it non-destructive: it may save normally, but must
  not move players or edit terrain.
- `RingWorldCreationScreen.extractRenderState` must not call a background
  extraction method. Minecraft 26.1's
  `Screen.extractRenderStateWithTooltipAndSubtitles` already owns the frame's
  single legal menu-blur pass.
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
- `RingRenderProfile` visual-policy version 4 owns the live/proxy/detail
  transitions, reveal, haze, and local cloud fade. Keep Java profile fields
  and the seven appended RingWorld Globals vectors synchronized.
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
