# RingWorld

RingWorld is an experimental Minecraft mod that turns the Overworld into a
finite, genuinely looping cylindrical world. Fabric and NeoForge now have
matching client, server, topology, worldgen, atlas, multiplayer, and packaging
gates. Exact dual-loader alpha 3 files are uploaded to Modrinth and CurseForge
while both projects remain under review.

Walk far enough around the circumference and you return to the same place
without entering a duplicate world or crossing a corrective teleport. Players,
entities, chunks, blocks, and interactions share one periodic topology, while
the client bends nearby terrain into a cylinder and renders the rest of the
world as a continuous ring across the sky.

The Nether and End remain vanilla. Returning from the infinite Nether wraps
the vanilla-scaled X destination around the RingWorld circumference. A Z
destination beyond either finite rim is moved to the nearest portal-safe
interior latitude before portal lookup or creation, so long Nether journeys do
not create an exterior Overworld portal or strand the player in void.

> **Port status:** the active development branch targets Minecraft Java
> 26.1.2. The common and client source sets now compile together on Java 25,
> and the current suite passes 337 unit/parameterized cases per loader.
> Fresh-world and copied-1.21.11 dedicated-server launch gates also pass,
> including dimension-owned saved-data migration. A safe-small integrated
> client has completed terrain, full-atlas rendering, two natural wraps, and
> the representative gameplay/rim matrix. A dedicated two-client seam,
> combat, stateful block, bed/death lifecycle, physical portal, boat,
> teleport, reconnect, ordinary survival Nether-portal delay, and seam
> thunder/lightning scenario also passes on both loaders. Runtime block-entity
> ownership now uses canonical Overworld positions, so a double chest spanning
> `C-1`/`0` exposes one shared 54-slot inventory from either side of the seam.
> Outbound positional block packets, including command/structure/jigsaw/test
> editors, are also canonicalized from the active presentation chart.
> The physical portal
> gate now includes positive and negative multi-lap Nether X targets and
> targets beyond both finite Z rims.
> The opt-in Atlas-concurrency matrix now waits for a bounded stable server
> interval after both clients are ready, then passes its strict seam-step gate
> on fresh Fabric and cold NeoForge fixtures; automated clients exit after
> their terminal result.
> A copied 16,384×256 world also passes Nether/End transfers, normal save and
> disconnect, client-state clearing, and an in-process reopen with the exact
> layout and complete atlas restored. The safe-small and production
> 6/12/28-chunk visual matrices now include tangent, live/atlas handoff, and
> radial projection review; the production proxy retains the atlas's
> eight-block height spacing. The complete atlas production gate now also
> passes interrupted resume, complete-cache reuse, live revisions, layout
> switching, lifecycle, two-client synchronization, and measured resource
> budgets. The repeatable dual-loader release-staging workflow is also complete.
> A post-gameplay Fabric and NeoForge 6/12/28 visual refresh also passes. The
> production 16,384×256 terrain/seam/rim gate and a fresh-world curved-object
> fixture covering representative block entities, vehicles, items, passive
> mobs, and hostile mobs now pass on both loaders; exposure, close-cloud,
> real-player proximity, and final motion sign-off remain open. The refreshed
> production seam/rim runs retain raw seam-motion frame metrics on both loaders;
> those numbers are evidence to review on target hardware, not a universal FPS
> requirement.
> A dedicated multi-seed worldgen matrix now covers all 14 major biome
> families, caves, ores, trees, loot, canonical structure ownership, actual
> seam-crossing mineshafts, saved scarce-structure outcomes, and exact reload.
> The mandatory handshake now has an explicit deadline and exact channel-set
> compatibility, and the 26.1 positional packet surface has a documented
> support boundary. Custom-size previews now use the measured production
> generation/disk reference, and compatibility API/contract version 1 lists
> known unsupported renderer and topology combinations. Release-candidate
> packaging and independent review remain, so this is not a stable release
> yet. The first 26.1.2 Fabric
> alpha is an early test build, not a stable or broadly compatible release.
> The validated server, client packages, and rollback tag remain Minecraft 1.21.11
> (`mc-1.21.11-final`).

The current dual-loader alpha candidate and its exact source/artifact hashes
are recorded in the
[2026-08-08 candidate checkpoint](docs/DUAL_LOADER_RELEASE_CANDIDATE_2026-08-08.md).
It is machine-validated and uploaded as an alpha, but still awaits owner
gameplay/visual review, real graphical Windows launches, independent review,
and promotion approval.

> **Loader direction:** shared Minecraft code now has separate Fabric and
> NeoForge platform adapters. The NeoForge 26.1.2.87 / ModDevGradle 2.0.143
> Java 25 module builds with the same 337 tests; its dedicated server reaches
> `Done` and starts/progresses an atlas. Its client now loads the shared
> resources/shaders and mixins, acknowledges settings format 3, streams atlas
> metadata/tiles, and renders the complete textured surface in a copied
> 16,384×256 integrated world. The verified gate captures tangent, live/LOD
> handoff, and radial views with measured frame pacing at noon, dusk, night,
> and rain. Seam and both textured-rim captures, same-process layout switching,
> and the Overworld/Nether/End/save/reopen lifecycle also pass. NeoForge also
> passes the production and multi-seed worldgen/structure gates, safe-small
> unattended headless atlas completion, and the dedicated two-client seam/combat/block/
> bed/death/portal/boat/teleport/reconnect matrix. Loader-labelled packages,
> strict metadata/licence verification, a same-commit shared-contract gate,
> a real packaged macOS NeoForge client smoke, and the shared eleven-step
> pause-menu atlas generation/control fixture also pass. NeoForge alpha 3 is
> hosted for testing; promotion beyond alpha still requires the final release
> review and owner go/no-go.

> **Licence status:** RingWorld is open-source software licensed under the
> [Mozilla Public License 2.0](LICENSE). Changes to existing RingWorld source
> files remain MPL-2.0 when distributed, while separate compatibility and
> modpack code may use other licences. See the practical
> [licensing guide](docs/LICENSING.md).

## Distribution

The exact dual-loader candidate is uploaded to
[Modrinth](https://modrinth.com/mod/ringworld/versions) as Fabric version
`lnY3EC8t` and NeoForge version `D19TF1Qj`. The project is still **Under
review**. Both alpha 3 files target Minecraft 26.1.2 and Java 25 and must be
installed on the server and every client; only Fabric requires Fabric API.
The same verified jars have also been submitted as Alpha files to
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/ringworld) project
`1645598`; that new project is awaiting moderation. See the
[CurseForge release procedure](docs/CURSEFORGE_RELEASE.md) for the exact
metadata and future upload checklist.
Use the [owner sign-off runbook](docs/OWNER_RELEASE_SIGNOFF_2026-08-09.md)
before approving promotion or live deployment.

Maintainers stage matching Fabric and NeoForge candidates with
`python3 scripts/stage_modrinth_release.py --loader both --build` under Java 25
from a clean, pushed public branch. The command fails early with Java setup
guidance if the active runtime is not Java 25. It produces one reviewed runtime
jar per loader, checksums, and generated public description/changelog text with
the exact immutable source-commit URL; it verifies the shared
gameplay/protocol/shader contract before writing either stage. It does not
upload or change the listing. See
[the release procedure](docs/MODRINTH_RELEASE.md).

Optional loader-labelled Prism client bundles and dedicated-server overlays
can be assembled locally with `scripts/prepare_release_packages.py --loader
fabric|neoforge`. They are convenience artifacts only: the standalone Modrinth
jar remains the normal installation path. Fabric and NeoForge use separate
managed Prism instance IDs so a loader change cannot carry incompatible
third-party mods into the other runtime.
The macOS launcher selects an already-installed Java 25 when it can and
otherwise delegates Java installation to Prism. The builder requires an exact
public source commit, excludes account and runtime state, creates reproducible
archives and checksums, and has no website, upload, deployment, or
service-control capability.

> **Public history:** this repository intentionally begins with a clean
> MPL-2.0 root commit. Pre-public tag names and commit hashes retained in the
> engineering documents are provenance references to the private development
> archive; they are not reachable Git objects or downloadable source versions
> in this public repository.

## What it feels like to play

- **One real loop.** Canonical Overworld X runs from `0` to
  `circumference - 1`; the two ends are adjacent.
- **Seamless travel.** Natural seam crossings preserve movement, camera
  orientation, velocity, vehicles, and nearby entity visibility.
- **Curved terrain.** Loaded Minecraft chunks visibly rise away from the
  player along the ring.
- **A complete ring overhead.** A lightweight terrain-atlas proxy continues
  beyond ordinary chunk distance, through the zenith, and back to the opposite
  horizon.
- **Visible generation progress.** While that Atlas is incomplete, a compact
  top-left `Ring Atlas Generating: X%` indicator tracks whole-percent progress
  and disappears automatically at completion.
- **Finite width.** The playable band ends at two thick, finite-height
  cobblestone and mossy-cobblestone rims. They are breakable; leaving the ring
  is allowed.
- **Familiar physics.** Gameplay retains vanilla `-Y` gravity. In intrinsic
  surface coordinates that is the local outward direction once the world is
  rendered as a cylinder.
- **Ringworld sky.** The sun is small, fixed toward the ring centre, and
  changes brightness and colour through a global Minecraft day/night cycle.
  Clouds follow the same cylindrical geometry as the terrain and stop at the
  inner faces of the finite rim walls.

## How the true loop works

RingWorld separates storage, presentation, and rendering:

1. **The server owns one canonical X plane.** Blocks, chunks, entities, saves,
   tickets, and scheduled ticks never create additional laps.
2. **Relationships use the nearest periodic image.** Distance, tracking,
   reach, queries, raycasts, projectiles, explosions, AI targets, and effects
   treat the end of the circumference as adjacent to the beginning.
3. **Each client keeps a temporary presentation chart.** A player can move
   smoothly from canonical X=`circumference - 1` to X=`0` while the nearby
   client view remains continuous. Presentation coordinates are never saved.
4. **The renderer embeds intrinsic coordinates into physical ring space.**
   Terrain, entities, clouds, and celestial placement share the configured
   cylinder.

This is why the seam can contain visible players, mobs, vehicles, blocks, and
interactions rather than acting like a portal between two distant borders.

Fresh worlds persist complete annular mapping v2, which makes biome, density,
surface, carver, structure-height, and vanilla direct blended-noise sampling
periodic together.
Worlds created by older alpha builds retain their historical mapping rather
than silently changing how unexplored chunks generate. Press F3 in the
Overworld and check the RingWorld `Worldgen` line: new-world seam evidence
must show `annular-complete-v2 (4)`. Mapping 1, 2, or 3 identifies an older
world whose unexplored terrain deliberately retains its original generator.

The complete coordinate model and data flow are documented in
[Architecture](docs/ARCHITECTURE.md) and
[Network protocol and client charts](docs/NETWORK_PROTOCOL.md).

## Real terrain and the distant ring

Nearby real chunks remain authoritative for collision, block interaction,
entities, lighting, and simulation. Block-use packets preserve one local
clicked face through the seam, so Survival placement works in both `C-1 -> 0`
and `0 -> C-1` directions. RingWorld does not force the client to load the
entire circumference as vanilla chunks.

Instead, the server incrementally samples generated surface height and colour
into a periodic terrain atlas. From the first metadata frame, the client draws
an opaque world-hash-seeded fallback ring and temporary curved rim returns.
Received cells softly flavour nearby unknown terrain with their real surface
palette. A dense progress-driven haze hides the approximation at low coverage,
then clears continuously as the Atlas fills. Each published revision
cross-fades over 750 ms; completion removes all placeholder influence and haze
and upgrades once to the exact full-detail texture and terrain-height mesh.
Temporary rim returns use a cobble/moss treatment instead of sampling green
terrain colour.
Real terrain cross-fades into this proxy near the configured render distance.

On the server, one Overworld-owned pregeneration service is the only atlas
writer. It preserves canonical X-major traversal, gives ordinary player chunk
work priority, resumes from saved format-6 cells, and verifies the final
atomic atlas save before declaring completion. Existing atlas status, pause,
and resume commands control that same background job.

After completion, exposed terrain edits are recaptured in bounded batches.
Changed tiles are pushed to every connected client and committed under one
durable monotonic revision, so an exact reconnect reuses its cache while a
stale reconnect safely downloads the authoritative surface again.

In a loaded RingWorld Overworld, the pause menu includes **RingWorld Map**.
Its header identifies the embedded alpha/artifact build, and its first status
line identifies the saved world's terrain mapping. It shows authoritative
atlas progress and lets the integrated-world owner or a
dedicated-server gamemaster confirm **Generate Entire Ring**, pause, resume, or
cancel it. Closing the map returns to play while generation continues. Other
players receive read-only status; complete atlases cannot be regenerated from
the UI. Generation creates real canonical chunks and region files, so the
confirmation calls out its disk/time cost.

The proxy:

- is anchored to the same canonical coordinates and seam as gameplay;
- follows biome-tinted terrain colour, height, relief, mip filtering, fog, and
  the live lightmap;
- remains visible across production-scale rings without raising real chunk
  render distance;
- never supplies collision, entities, block interaction, or simulation.

It is deliberately an approximation. It cannot reproduce individual distant
blocks, transparent layers, buildings, mobs, local block lights, or live
weather volumes. See [Rendering](docs/RENDERING.md) for the full pipeline and
handoff behavior.

## Requirements

| Component | Version |
| --- | --- |
| Minecraft Java | 26.1.2 port target |
| Java | 25 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.155.2+26.1.2 |
| NeoForge | 26.1.2.87 / ModDevGradle 2.0.143 (runtime and local packaging parity) |
| Development mappings | None; 26.1.2 is unobfuscated |
| RingWorld | The same jar on server and clients |

The server performs a required geometry/protocol handshake and rejects missing
or incompatible clients.

## Build and install

Build the active port with Java 25:

```sh
JAVA_HOME=/path/to/jdk-25/Contents/Home \
PATH="$JAVA_HOME/bin:$PATH" \
./gradlew clean test build --console=plain
```

The expected development artifact is:

```text
build/libs/ringworld-0.2.0+mc26.1.2.jar
```

The parallel NeoForge module uses the same Java 25 toolchain:

```sh
./gradlew :neoforge:test :neoforge:build --console=plain
```

Both Fabric and NeoForge builds pass 337 unit/parameterized cases per loader.
When launching a dedicated development server, use the qualified task
for the intended loader: `./gradlew :runServer` for Fabric or
`./gradlew :neoforge:runServer` for NeoForge. Do not use an unqualified
`runServer` now that both tasks exist.

For the NeoForge graphical projection fixture, use:

```sh
./gradlew :neoforge:runProductionProjectionClient \
  -PringNeoForgeProjectionSource="NeoForge Test"
```

It copies the named ignored save from `neoforge/run-client/saves/` into a
disposable run directory, waits for a complete atlas, captures tangent,
live/LOD handoff, and radial views, verifies the outputs, then exits. The
production 16,384×256 noon, dusk, night, and rain gates pass. The additional
seam/rim, same-process layout-switch, production-lifecycle, worldgen,
headless-atlas, and dedicated two-client gameplay gates also pass on isolated
fixtures. Packaging remains a separate gate.

The historical 95-error compiler inventory and subsequent source-port
milestones are recorded in
[the 26.1 compiler baseline](docs/MINECRAFT_26_1_COMPILER_BASELINE.md).
The resulting jar is for development validation only. Client launch and
install instructions remain suspended until the 26.1 runtime gates pass. For
the working 1.21.11 package, use the public download above or the frozen tag.

Existing ordinary Overworlds cannot be converted, and existing RingWorld
dimensions cannot be resized in place.

## Create a world

On the Create World screen, select the bottom-left **RingWorld C×W** button.
The redesigned editor provides:

- **Small** (2,048×128), **Medium** (16,384×256), and **Large**
  (32,768×512) presets;
- custom circumference, width, and wall height;
- an optional guaranteed ocean monument for the new world;
- validation of chunk alignment, radial/build-height safety, finite-rim
  interior, coordinate bounds, and atlas resource limits;
- live equations for walking-lap time, radius/diameter, far-side angular width,
  chunks, playable area, atlas storage, rim/cloud height, and
  measured-reference pregeneration time and disk growth;
- a final confirmation because the saved dimensions are immutable.

Dedicated servers use `config/ringworld.properties` before first world load:

```properties
widthBlocks=256
circumferenceBlocks=16384
wallHeightBlocks=160
testMode=false
testViewDistanceChunks=28
pregenerateTerrainAtlas=true
requestOceanMonument=false
```

Width and circumference must be multiples of 16 and pass the creation safety
checks. New worlds require at least 2,048 blocks around and 128 blocks across.
The lower 1,024-block structural circumference remains readable only for
legacy saved settings and internal topology fixtures. A 128-wide Small ring
cannot fit the monument guarantee's 64-block margins, so that option is
disabled until width reaches 160 blocks.
Saved settings always override later bootstrap configuration changes.

### Reference layouts

| Layout | Circumference | Width | Purpose |
| --- | ---: | ---: | --- |
| Small | 2,048 blocks / 128 chunks | 128 blocks / 8 chunks | About 7m 54s per walking lap; strongest curvature; monument guarantee unavailable |
| Medium (default) | 16,384 blocks / 1,024 chunks | 256 blocks / 16 chunks | About 1h 03m per walking lap; balanced visual and generation cost |
| Large | 32,768 blocks / 2,048 chunks | 512 blocks / 32 chunks | About 2h 06m per walking lap; reference pregeneration about 54m 28s and 677.2 MiB |

The older 2,048×416 **safe-small test fixture** remains in automated renderer,
atlas, and multiplayer evidence; it is not one of the user-facing presets.
At 128 blocks across, a vanilla stronghold's optional graph may extend into
suppressed exterior space and meet the rims. The guaranteed portal room and
its 12-frame End portal are fitted safely inside the band and runtime-tested
on both loaders, though Small players may need to mine to the room. The editor
therefore labels Small experimental instead of implying a fully intact vanilla
stronghold graph.

The production atlas covers 16,384 canonical chunks and is a substantial
background generation job. A clean-atlas copied-world benchmark completed it
in 13 minutes 37 seconds at about 80.2 cells per second on the development
machine. Administrators can inspect or control it without changing the saved
layout:

```text
/ringworld atlas status
/ringworld atlas start
/ringworld atlas pause
/ringworld atlas resume
```

`start` begins an idle partial atlas even when automatic background generation
is disabled. `resume` resumes a paused job and also starts from saved partial
progress after a process restart returns the handle to `IDLE`.

Detailed sizing, persistence, deployment, and recovery guidance lives in
[Configuration and operations](docs/OPERATIONS.md).

## Current implementation

The current build includes:

- canonical periodic chunk, entity, tick, query, tracking, and interaction
  paths, including retained entity pairing across one pending canonical seam
  chunk transition and local-client bed positions projected into the current
  presentation chart;
- continuous client charts and natural player/vehicle seam folding;
- versioned periodic density-noise sampling: existing alpha worlds retain their
  exact legacy mapping, while fresh worlds use an orthogonal annular mapping
  that removes quarter-ring terrain banding; structure base-height/base-column
  queries and canonical seam-crossing worldgen writes use the same selection;
- finite exterior void and five-block textured, breakable rims;
- curved terrain, entity, block-entity, interaction-overlay, cloud, frustum,
  and section-visibility handling;
- a fixed ring-centred sun with smooth global tone and intensity changes;
- persistent tiled terrain-atlas transfer and client cache;
- bounded live atlas refresh after block, fluid, explosion, and bulk terrain
  changes, with revision-safe reconnects;
- a loader-neutral atlas-pregeneration job model and deterministic canonical
  cursor plus one world-owned service shared by background, map, and explicit
  headless prewarm runs;
- atlas-backed full-ring rendering at normal chunk distance;
- configurable immutable dimensions with creation-time validation and cost
  preview;
- a deterministic stronghold whose complete terrain-adjusted piece graph is
  fitted inside the band, with an activatable End portal room, for every newly
  created RingWorld;
- an opt-in new-world ocean-monument guarantee with a saved deterministic
  canonical candidate, exact periodic biome validation, bounded footprint,
  and seam-aware locate/reference behavior;
- automated local, layout-switch, production-projection, production-lifecycle,
  and two-client multiplayer harnesses.

Representative automated coverage includes repeated seam crossings, combat,
block and block-entity updates, redstone, an arrow, a boat, a ground navigator,
water flow, an explosion, beds, death/respawn, physical Nether/End portal
transfers, effects, reconnects, long teleports, rims, exterior void behavior,
and bidirectional filled-map/compass behavior at the seam, including map
scale/lock, banner removal/restoration, real item frames on both sides, and a
save/disconnect/raw-session-teardown/reopen persistence check.

The dedicated two-client fixture records water flowing through a sealed trough
from canonical `C-1` into the initially empty canonical `X=0` destination.
The historical 2026-08-01 dedicated result observed only the source state.
Fresh 2026-08-02 Fabric and NeoForge runs pass the strengthened destination
assertion.

The fixture also requires a tagged hostile Zombie to complete a vanilla
navigation request from canonical `C-5` toward `X=2` through a bounded lane,
naturally folding into low canonical X, and finishing within target tolerance.
The same fresh dual-loader runs pass this server-fixture assertion.

For demonstrated results, open risks, and the prioritized roadmap, see
[Current state](docs/CURRENT_STATE.md).

## Known limitations

- The distant ring fills in progressively as atlas cells arrive; only generated
  regions are shown until the atlas reaches 100%.
- Production clean-atlas generation, projection, transfer, multiplayer,
  lifecycle, memory, static GPU resources, and 6/12/28 visual/frame-pacing
  gates pass.
- The multi-seed matrix covers ordinary biome, carver, ore, tree, loot, and
  mineshaft seam generation, but does not claim exhaustive seam coverage for
  every vanilla structure. The two-phase seam-raid fixture passes on Fabric
  and NeoForge. Full map-mode playthroughs, complex
  redstone/fluid networks, and additional vehicle/projectile variants still
  need more coverage. Other scarce
  random-spread structures are not yet selectable; see
  the [scarce-structure audit](docs/SCARCE_STRUCTURE_GUARANTEE_AUDIT.md).
- The atlas is an eight-block surface sample: edits between sample points may
  not be visible in the distant proxy even though their affected cell is
  recaptured.
- Shader packs and mods that assume a flat Overworld, unbounded chunk X,
  ordinary global Euclidean distance, different gravity, or unchanged
  renderer/worldgen internals are likely incompatible.
- The sky cycle is globally synchronized with vanilla time; it is not a
  position-dependent physical eclipse simulation.
- There is no supported flat-world conversion or in-place RingWorld resize.

This is an engine-level prototype, not a claim of compatibility with arbitrary
Fabric modpacks.

## Testing

The active 26.1.2 build uses Java 25:

```sh
./gradlew test build
```

Two-client dedicated multiplayer regression, in separate terminals:

```sh
./gradlew :runMultiplayerServer
./gradlew :runMultiplayerClientA
./gradlew :runMultiplayerClientB
```

The disposable Atlas-concurrency harness also accepts a validated custom
layout, including the production `16384×256` geometry; its exact Fabric and
NeoForge commands, restart procedure, and cell-total verifier are in
[`docs/TESTING.md`](docs/TESTING.md#opt-in-atlas-concurrency-gate-130).

Additional automated runs:

```sh
./gradlew :runLayoutSwitchClient
./gradlew :runMapCompassCaptureClient
./gradlew :neoforge:runMapCompassCaptureClient
./gradlew :runProductionProjectionClient -PringProjectionWorld="save-folder-id"
./gradlew :runProductionLifecycleClient -PringProductionLifecycleSource="save-folder-id"
./gradlew :runHeadlessPrewarmServer
python3 scripts/run_worldgen_structure_matrix.py
```

The three copied-world Fabric runs clear their old logs/evidence before launch
and finish with a fail-closed Gradle verifier. Layout-switch requires its exact
pass marker, selected expectation, and copied saves; it can also exercise two
same-size worlds with different seeds. Lifecycle requires its exact pass marker
and copied save; projection additionally decodes all three environment-specific
PNG captures. A missing, failed, stale, or corrupt result therefore makes the
command fail.

`runHeadlessPrewarmServer` works only in its ignored disposable run directory.
After the server owner accepts its local EULA, it creates or copies only that
runtime world, rejects player joins before any RingWorld settings, handshake,
or atlas payload is sent, resumes from atlas cells after a stop, and
writes `world/ringworld-prewarm/progress.json` plus `result.json`. The Gradle
finalizer turns any non-`COMPLETE` terminal report into a nonzero command
result. Fabric's networking JOIN listener independently rechecks that admission
because disconnecting in an earlier array-backed JOIN listener does not stop
later listeners. To prepare a read-only source copy, use
`-PringHeadlessPrewarmSource="save-folder-id"`; it reads only `run/saves` and
never launches that source in place. After an interrupted disposable run, add
`-PringHeadlessPrewarmResume=true` to retain that runtime world and resume it.
Fresh dimension gates may override the safe-small defaults with
`-PringHeadlessPrewarmCircumference=16384 -PringHeadlessPrewarmWidth=256`;
NeoForge uses equivalent `ringNeoForgeHeadlessPrewarm*` properties.
Fabric's exact production prewarm completed successfully on 2026-08-06. That
evidence does not claim an equivalent production NeoForge prewarm.
If a copied ordinary flat world is rejected before the normal level-load
callback, the dedicated adapter still writes a terminal `REJECTED` result with
`identityAvailable:false` and zero/null identity fields, then preserves
Minecraft's original startup failure. It never creates RingWorld settings for
that source or copy.

The complete fixtures, expected log markers, screenshots, performance
measurements, and safe handling rules are in [Testing](docs/TESTING.md).

## Documentation

| Document | Purpose |
| --- | --- |
| [Agent guide](AGENTS.md) | Invariants, repository map, workflow, and maintenance cautions |
| [Documentation index](docs/README.md) | Entry point for the technical documentation |
| [Architecture](docs/ARCHITECTURE.md) | Coordinate domains and end-to-end system design |
| [Current state](docs/CURRENT_STATE.md) | Implemented behavior, evidence, limitations, and roadmap |
| [Dimension scaling plan](docs/DIMENSION_SCALING_PLAN.md) | Dimension-sensitive variables, budgets, completed #24 matrix evidence, and remaining production visual/benchmark gates |
| [Atlas pregeneration plan](docs/ATLAS_PREGENERATION_PLAN.md) | One-click complete-map generation with resumable background and headless execution |
| [Mixin map](docs/MIXIN_MAP.md) | Ownership and risk of each Minecraft injection |
| [Network protocol](docs/NETWORK_PROTOCOL.md) | Geometry handshake, atlas transport, and presentation mapping |
| [Compatibility contract](docs/COMPATIBILITY.md) | Supported baseline, known conflicts, versioned API, and loader boundary |
| [Operations](docs/OPERATIONS.md) | Configuration, installation, packaging, deployment, and recovery |
| [Rendering](docs/RENDERING.md) | Curvature, visibility, terrain proxy, sky, clouds, and handoff |
| [Atlas visual baseline](docs/ATLAS_VISUAL_BASELINE_2026-08-01.md) | Production and safe-small 6/12/28 profile-5 captures, frame pacing, and resource evidence |
| [Atlas fidelity decision](docs/ATLAS_FIDELITY_BENCHMARK_2026-08-01.md) | Production step 8/4/2/1 costs and the evidence-based decision to retain eight-block sampling |
| [Revisioned atlas updates](docs/ATLAS_REVISIONED_UPDATES_2026-08-01.md) | Bounded terrain invalidation, durable revisions, tile broadcast, and reconnect rules |
| [Testing](docs/TESTING.md) | Unit, local, visual, layout-switch, and multiplayer procedures |
| [Sun renderer snapshot](docs/SUN_RENDERING_SNAPSHOT_2026-07-26.md) | Rollback record for the removed shadow-panel experiment |

Documentation is part of every code change. If behavior changes, update the
relevant document in the same commit.

## Compatibility API

`dev.ringworld.api.RingWorldApi` exposes read-only helpers for detecting a
RingWorld and converting canonical, nearest-presentation, and physical
ring-space positions and poses. `RingWorldApi.API_VERSION` and
`RingCompatibilityContract.VERSION` are both 1. The API never mutates world or
chart state. See the explicit [compatibility contract](docs/COMPATIBILITY.md)
for the supported baseline, known unsupported mods/shaders, and integration
rules.

## License

Copyright © 2026 Delaser and RingWorld contributors.

RingWorld is licensed under the [Mozilla Public License 2.0](LICENSE).
MPL-2.0 permits use, modification, redistribution, commercial distribution,
modpack inclusion, compatibility forks, and ports. Modified RingWorld files
must remain available under MPL-2.0 when distributed; separate files in a
larger work may use other licences.

See [Licensing](docs/LICENSING.md) for practical distribution guidance and
[Contributing](CONTRIBUTING.md) for the contribution process.
