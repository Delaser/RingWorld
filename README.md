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
> 26.1.2. Its frozen compiler baseline recorded 95 common errors; the first
> primary source pass leaves five storage-owned errors. It is not playable
> yet. The validated server, client packages, and rollback tag remain
> Minecraft 1.21.11 (`mc-1.21.11-final`).

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

## Try the public test

The current survival test server is:

```text
andwhatnotstudio.com:25565
```

Matching credential-free client bundles for Windows and macOS/universal are
available from:

**[andwhatnotstudio.com/ringworld](https://andwhatnotstudio.com/ringworld/)**

The download page includes SHA-256 checksums. The launcher installs the
packaged instance into its own Prism data directory and can update the managed
RingWorld and Fabric files without replacing an existing login, save, options,
or local RingWorld configuration.

The public server currently uses the deliberately small 2,048 × 416 test
layout so curvature, wrapping, atlas generation, and multiplayer behavior can
be exercised quickly.

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

The active port checkpoint is reproduced with Java 25:

```sh
JAVA_HOME=/path/to/jdk-25/Contents/Home \
PATH="$JAVA_HOME/bin:$PATH" \
./gradlew clean compileJava --console=plain
```

The Phase 2 checkpoint failed with the 95 errors inventoried in
[the 26.1 compiler baseline](docs/MINECRAFT_26_1_COMPILER_BASELINE.md); current
primary source is gated by five storage-owned errors.
There is intentionally no 26.1 release artifact yet.

Client launch and install instructions remain suspended until the 26.1 common
and client source sets compile. For the working 1.21.11 package, use the public
download above or the frozen tag rather than attempting to package this branch.

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
widthBlocks=4096
circumferenceBlocks=15552
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
| Production default | 15,552 blocks / 972 chunks | 4,096 blocks / 256 chunks | Approximately one hour to walk a lap at normal speed |
| Safe-small | 2,048 blocks / 128 chunks | 416 blocks / 26 chunks | Fast development, atlas, and multiplayer testing |

The production atlas covers 248,832 canonical chunks and is a substantial
background generation job. Administrators can inspect or control it without
changing the saved layout:

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
  paths;
- continuous client charts and natural player/vehicle seam folding;
- periodic density-noise sampling and canonical seam-crossing worldgen writes;
- finite exterior void and five-block textured, breakable rims;
- curved terrain, entity, cloud, frustum, and section-visibility handling;
- a fixed ring-centred sun with smooth global tone and intensity changes;
- persistent tiled terrain-atlas transfer and client cache;
- atlas-backed full-ring rendering at normal chunk distance;
- configurable immutable dimensions with creation-time validation and cost
  preview;
- automated local, layout-switch, production-projection, and two-client
  multiplayer harnesses.

Representative automated coverage includes repeated seam crossings, combat,
block updates, an arrow, a boat, a ground navigator, water flow, an explosion,
effects, reconnects, long teleports, rims, and exterior void behavior.

For demonstrated results, open risks, and the prioritized roadmap, see
[Current state](docs/CURRENT_STATE.md).

## Known limitations

- The complete distant ring appears only after its terrain atlas reaches 100%.
- The production-default atlas has not yet completed the full end-to-end
  generation, disk, transfer, and GPU benchmark matrix.
- Broad multi-seed structures, carvers, portals, redstone, block entities,
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
./gradlew runProductionProjectionClient
```

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

RingWorld is licensed under the [MIT License](LICENSE).
