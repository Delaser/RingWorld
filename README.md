# RingWorld

RingWorld is an experimental Fabric mod that turns the Minecraft Overworld
into a finite, genuinely looping cylindrical world.

Walk far enough around the circumference and you return to the same place
without entering a duplicate world or crossing a corrective teleport. Players,
entities, chunks, blocks, and interactions share one periodic topology, while
the client bends nearby terrain into a cylinder and renders the rest of the
world as a continuous ring across the sky.

The Nether and End remain vanilla.

> **Port status:** the active development branch targets Minecraft Java
> 26.1.2. The common and client source sets now compile together on Java 25,
> all 127 unit/parameterized cases pass, and Loom produces the 26.1 mod jars.
> Fresh-world and copied-1.21.11 dedicated-server launch gates also pass,
> including dimension-owned saved-data migration. A safe-small integrated
> client has completed terrain, full-atlas rendering, two natural wraps, and
> the representative gameplay/rim matrix. A dedicated two-client seam,
> combat, block, boat, teleport, and reconnect scenario also passes.
> A copied 16,384×256 world also passes Nether/End transfers, normal save and
> disconnect, client-state clearing, and an in-process reopen with the exact
> layout and complete atlas restored. The safe-small 6/12/28-chunk visual
> matrix and a complete production-size tangent/radial projection review now
> pass, and the repeatable Fabric release-staging workflow is complete.
> Remaining automated-harness and pre-release compatibility gates are still
> outstanding, so this is not a stable release yet. The first 26.1.2 Fabric
> alpha is an early test build, not a stable or broadly compatible release.
> The validated server, client packages, and rollback tag remain Minecraft 1.21.11
> (`mc-1.21.11-final`).

> **Loader direction:** current builds remain Fabric-only. Future development
> is required to keep RingWorld's core loader-agnostic and place unavoidable
> loader integration behind Fabric and NeoForge platform adapters. Dual-loader
> support is the intended architecture; this is not yet a claim that a tested
> NeoForge artifact is available.

> **Licence status:** RingWorld is open-source software licensed under the
> [Mozilla Public License 2.0](LICENSE). Changes to existing RingWorld source
> files remain MPL-2.0 when distributed, while separate compatibility and
> modpack code may use other licences. See the practical
> [licensing guide](docs/LICENSING.md).

## Distribution

The first Fabric alpha, `0.2.0+mc26.1.2`, is currently **Under review** on
[Modrinth](https://modrinth.com/mod/ringworld) as version `1MhIDQ2h`. It will
become publicly downloadable after approval. The alpha targets Minecraft 26.1.2
and Java 25, requires Fabric API, and must be installed on both the client and
server.

Maintainers stage a later manual upload with
`python3 scripts/stage_modrinth_release.py --build` from a clean, pushed public
branch. It produces one reviewed runtime jar, checksums, and an exact source
revision; it does not upload or change the listing. See
[the release procedure](docs/MODRINTH_RELEASE.md).

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
- **Finite width.** The playable band ends at two thick, finite-height
  cobblestone and mossy-cobblestone rims. They are breakable; leaving the ring
  is allowed.
- **Familiar physics.** Gameplay retains vanilla `-Y` gravity. In intrinsic
  surface coordinates that is the local outward direction once the world is
  rendered as a cylinder.
- **Ringworld sky.** The sun is small, fixed toward the ring centre, and
  changes brightness and colour through a global Minecraft day/night cycle.
  Clouds follow the same cylindrical geometry as the terrain.

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

The complete coordinate model and data flow are documented in
[Architecture](docs/ARCHITECTURE.md) and
[Network protocol and client charts](docs/NETWORK_PROTOCOL.md).

## Real terrain and the distant ring

Nearby real chunks remain authoritative for collision, block interaction,
entities, lighting, and simulation. RingWorld does not force the client to load
the entire circumference as vanilla chunks.

Instead, the server incrementally samples generated surface height and colour
into a periodic terrain atlas. After that atlas is complete, the client builds
a bounded GPU texture and mesh covering the whole cylinder. Real terrain
cross-fades into this proxy near the configured render distance.

On the server, one Overworld-owned pregeneration service is the only atlas
writer. It preserves canonical X-major traversal, gives ordinary player chunk
work priority, resumes from saved format-5 cells, and verifies the final
atomic atlas save before declaring completion. Existing atlas status, pause,
and resume commands control that same background job.

In a loaded RingWorld Overworld, the pause menu includes **RingWorld Map**.
It shows authoritative atlas progress and lets the integrated-world owner or a
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
The editor provides:

- safe-small, production, and current presets;
- custom circumference, width, and wall height;
- validation of chunk alignment, radial/build-height safety, finite-rim
  interior, coordinate bounds, and atlas resource limits;
- estimates for chunks, radius, atlas size, GPU texture, and mesh cost;
- a final confirmation because the saved dimensions are immutable.

Dedicated servers use `config/ringworld.properties` before first world load:

```properties
widthBlocks=256
circumferenceBlocks=16384
wallHeightBlocks=160
testMode=false
testViewDistanceChunks=28
pregenerateTerrainAtlas=true
```

Width and circumference must be multiples of 16 and pass the creation safety
checks. Saved settings always override later bootstrap configuration changes.

### Reference layouts

| Layout | Circumference | Width | Purpose |
| --- | ---: | ---: | --- |
| Production default | 16,384 blocks / 1,024 chunks | 256 blocks / 16 chunks | Approximately 63 minutes to walk a lap; power-of-two, atlas, chunk, and 32-region alignment |
| Safe-small | 2,048 blocks / 128 chunks | 416 blocks / 26 chunks | Fast development, atlas, and multiplayer testing |

The production atlas covers 16,384 canonical chunks and is a substantial
background generation job. A clean-atlas copied-world benchmark completed it
in 13 minutes 37 seconds at about 80.2 cells per second on the development
machine. Administrators can inspect or control it without changing the saved
layout:

```text
/ringworld atlas status
/ringworld atlas pause
/ringworld atlas resume
```

Detailed sizing, persistence, deployment, and recovery guidance lives in
[Configuration and operations](docs/OPERATIONS.md).

## Current implementation

The current build includes:

- canonical periodic chunk, entity, tick, query, tracking, and interaction
  paths, including retained entity pairing across one pending canonical seam
  chunk transition and local-client bed positions projected into the current
  presentation chart;
- continuous client charts and natural player/vehicle seam folding;
- periodic density-noise sampling, including canonicalized structure
  base-height/base-column queries, and canonical seam-crossing worldgen writes;
- finite exterior void and five-block textured, breakable rims;
- curved terrain, entity, block-entity, interaction-overlay, cloud, frustum,
  and section-visibility handling;
- a fixed ring-centred sun with smooth global tone and intensity changes;
- persistent tiled terrain-atlas transfer and client cache;
- a loader-neutral atlas-pregeneration job model and deterministic canonical
  cursor plus one world-owned service shared by background, map, and explicit
  headless prewarm runs;
- atlas-backed full-ring rendering at normal chunk distance;
- configurable immutable dimensions with creation-time validation and cost
  preview;
- a deterministic stronghold whose complete terrain-adjusted piece graph is
  fitted inside the band, with an activatable End portal room, for every newly
  created RingWorld;
- automated local, layout-switch, production-projection, production-lifecycle,
  and two-client multiplayer harnesses.

Representative automated coverage includes repeated seam crossings, combat,
block updates, an arrow, a boat, a ground navigator, water flow, an explosion,
effects, reconnects, long teleports, rims, and exterior void behavior.

For demonstrated results, open risks, and the prioritized roadmap, see
[Current state](docs/CURRENT_STATE.md).

## Known limitations

- The complete distant ring appears only after its terrain atlas reaches 100%.
- Production clean-atlas generation, projection, transfer, multiplayer,
  lifecycle, memory, and static GPU resource gates pass; the 6/12/28 visual and
  repeated frame-pacing comparison matrix remains open.
- Structures other than the guaranteed stronghold, carvers, redstone,
  fluids, death/respawn, vehicles, and projectiles still need more seam
  coverage.
- The atlas is refreshed when surface chunks are captured or loaded, not
  immediately after every player block edit.
- Shader packs and mods that assume a flat Overworld, unbounded chunk X,
  ordinary global Euclidean distance, different gravity, or unchanged
  renderer/worldgen internals are likely incompatible.
- The sky cycle is globally synchronized with vanilla time; it is not a
  position-dependent physical eclipse simulation.
- There is no supported flat-world conversion or in-place RingWorld resize.

This is an engine-level prototype, not a claim of compatibility with arbitrary
Fabric modpacks.

## Testing

The commands below describe the green 1.21.11 baseline. Run them from a
separate checkout of `mc-1.21.11-final`; they will be restored on this branch
after common and client compilation succeeds:

```sh
./gradlew test build
```

Two-client dedicated multiplayer regression, in separate terminals:

```sh
./gradlew runMultiplayerServer
./gradlew runMultiplayerClientA
./gradlew runMultiplayerClientB
```

Additional automated runs:

```sh
./gradlew runLayoutSwitchClient
./gradlew runProductionProjectionClient -PringProjectionWorld="save-folder-id"
./gradlew runProductionLifecycleClient -PringProductionLifecycleSource="save-folder-id"
./gradlew runHeadlessPrewarmServer
```

`runHeadlessPrewarmServer` works only in its ignored disposable run directory.
After the server owner accepts its local EULA, it creates or copies only that
runtime world, rejects player joins, resumes from atlas cells after a stop, and
writes `world/ringworld-prewarm/progress.json` plus `result.json`. The Gradle
finalizer turns any non-`COMPLETE` terminal report into a nonzero command
result. To prepare a read-only source copy, use
`-PringHeadlessPrewarmSource="save-folder-id"`; it reads only `run/saves` and
never launches that source in place. After an interrupted disposable run, add
`-PringHeadlessPrewarmResume=true` to retain that runtime world and resume it.
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
| [Dimension scaling plan](docs/DIMENSION_SCALING_PLAN.md) | Dimension-sensitive variables, budgets, and remaining matrix |
| [Atlas pregeneration plan](docs/ATLAS_PREGENERATION_PLAN.md) | One-click complete-map generation with resumable background and headless execution |
| [Mixin map](docs/MIXIN_MAP.md) | Ownership and risk of each Minecraft injection |
| [Network protocol](docs/NETWORK_PROTOCOL.md) | Geometry handshake, atlas transport, and presentation mapping |
| [Operations](docs/OPERATIONS.md) | Configuration, installation, packaging, deployment, and recovery |
| [Rendering](docs/RENDERING.md) | Curvature, visibility, terrain proxy, sky, clouds, and handoff |
| [Testing](docs/TESTING.md) | Unit, local, visual, layout-switch, and multiplayer procedures |
| [Sun renderer snapshot](docs/SUN_RENDERING_SNAPSHOT_2026-07-26.md) | Rollback record for the removed shadow-panel experiment |

Documentation is part of every code change. If behavior changes, update the
relevant document in the same commit.

## Compatibility API

`dev.ringworld.api.RingWorldApi` exposes read-only helpers for detecting a
RingWorld and converting canonical coordinates into physical ring-space
positions. The API is intentionally small; presentation-chart negotiation and
broad third-party compatibility contracts are not yet stable.

## License

Copyright © 2026 Delaser and RingWorld contributors.

RingWorld is licensed under the [Mozilla Public License 2.0](LICENSE).
MPL-2.0 permits use, modification, redistribution, commercial distribution,
modpack inclusion, compatibility forks, and ports. Modified RingWorld files
must remain available under MPL-2.0 when distributed; separate files in a
larger work may use other licences.

See [Licensing](docs/LICENSING.md) for practical distribution guidance and
[Contributing](CONTRIBUTING.md) for the contribution process.
