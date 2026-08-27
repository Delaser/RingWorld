# RingWorld

RingWorld turns Minecraft's Overworld into a finite world that genuinely
loops back into itself.

Walk far enough around the ring and you return to where you started. There is
no border teleport and no second copy of the world. Players, mobs, vehicles,
blocks, redstone, fluids, projectiles, maps, structures, and portals can all
work across the join.

[Download on Modrinth](https://modrinth.com/mod/ringworld/versions) ·
[Download on CurseForge](https://www.curseforge.com/minecraft/mc-mods/ringworld) ·
[Project showcase](https://andwhatnotstudio.com/ringworld/) ·
[Report a problem](https://github.com/Delaser/RingWorld/issues)

## What does it look like?

Nearby Minecraft terrain visibly curves away from you. Beyond normal render
distance, a lightweight copy of the generated surface continues around the
sky, over your head, and back to the opposite horizon. The aim is to make the
Overworld feel like one continuous ring rather than a flat map with a trick at
the edge.

The ring has a finite width, with tall cobblestone and mossy-cobblestone walls
along both sides. The walls are breakable. If you really want to climb over
one and throw yourself into the void, the mod will not stop you.

Gravity and ordinary Minecraft movement stay familiar. The Nether and End
also remain normal dimensions. Nether portals return to a safe matching point
on the finite Overworld ring, including after very long Nether journeys.

## The important bits

- **A real gameplay loop.** The first and last blocks of the circumference
  are neighbours. You can travel, fight, build, place blocks, move through
  portals, and see other players across that join.
- **Visible curvature.** Terrain, entities, clouds, the sun, and distant
  scenery share the same cylindrical presentation.
- **Normal Minecraft terrain.** Biomes, caves, ores, trees, structures,
  weather, mobs, loot, strongholds, and the End portal remain part of world
  generation.
- **A complete ring in the sky.** You do not need a 100-chunk render distance
  to see the rest of the world.
- **Custom dimensions.** Choose a preset or enter your own circumference,
  width, and wall height when creating the world.
- **Fabric and NeoForge.** RingWorld 1.0 is available for both loaders.
- **Server support.** The mod works in single-player and on dedicated
  multiplayer servers. Every connecting player needs the matching mod.

## Download and requirements

The current public release is **RingWorld 1.0 for Minecraft Java 26.1.2**.

Work on broader version support, including 26.2, is in progress. A version
being in the test matrix does not mean the published mod supports it yet.
Contributors can follow the [version qualification guide](docs/VERSION_QUALIFICATION.md).

| Loader | Required software |
| --- | --- |
| Fabric | Minecraft 26.1.2, Java 25, Fabric Loader 0.19.3, Fabric API 0.155.2+26.1.2 |
| NeoForge | Minecraft 26.1.2, Java 25, NeoForge 26.1.2.87 or later compatible 26.1.2 build |

Install the Fabric **or** NeoForge RingWorld jar in the normal `mods` folder.
Do not install both. A Fabric client joins a Fabric RingWorld server; a
NeoForge client joins a NeoForge RingWorld server. The server and every client
must use compatible RingWorld builds.

CurseForge and Modrinth provide the ordinary standalone mod files. The
[showcase site](https://andwhatnotstudio.com/ringworld/) has additional project
information and demo material.

## Creating a RingWorld

RingWorld is selected while creating a new world:

1. Open **Create New World**.
2. Select the **RingWorld C×W** button in the bottom-left corner.
3. Choose Small, Medium, Large, or enter custom dimensions.
4. Review the world-size estimate and create the world.

The built-in presets are:

| Preset | Circumference × width | Approximate walking lap |
| --- | --- | --- |
| Small | 2,048 × 128 blocks | 8 minutes |
| Medium | 16,384 × 256 blocks | 1 hour 3 minutes |
| Large | 32,768 × 512 blocks | 2 hours 6 minutes |

Medium is the recommended default. Small has dramatic curvature but a very
narrow interior. Large needs considerably more generation time and disk
space.

Ring dimensions are saved permanently when the Overworld is first created.
Changing a configuration file later does not resize an existing ring, and an
ordinary flat Overworld cannot currently be converted into one.

For dedicated-server configuration, commands, backups, and recovery, see the
[operations guide](docs/OPERATIONS.md).

## Why the distant ring takes time to appear

Minecraft normally generates terrain only near players. RingWorld needs a
summary of the whole surface before it can show the complete ring overhead,
so it builds a terrain Atlas in the background.

It appears in stages:

1. **Real nearby chunks load first.** You can begin playing normally.
2. **A fogged placeholder fills the unknown ring.** It takes colour from the
   terrain that has already been seen.
3. **The placeholder improves as more regions generate.** A small
   `Ring Atlas Generating: X%` display shows progress.
4. **At verified completion, the distant ring switches to its detailed 3D
   surface.** The placeholder and generation haze disappear.

Generation is resumable. Stopping the game or server does not require the
Atlas to start again from zero. The pause-menu **RingWorld Map** shows its
current state and, for the world owner or a server gamemaster, provides
controls to generate, pause, resume, or cancel the job.

There is deliberately no promised fixed completion time. Circumference,
width, storage speed, CPU performance, other players, and ordinary chunk work
all affect it. The real loaded chunks remain authoritative throughout; the
distant ring is a visual stand-in and never supplies collision, mobs, block
interaction, or simulation.

## Multiplayer

RingWorld is an engine-level mod, not a client-only visual effect. Install it
on the dedicated server and every client.

The server sends the saved ring dimensions and terrain identity during login.
Clients with a missing or incompatible RingWorld build are rejected instead
of being allowed to join with the wrong world geometry.

The seam is shared multiplayer space. Players standing on opposite numerical
sides of it should still be nearby, visible, and able to interact normally.

## Current limitations

- Shader packs and mods that assume an unmodified flat Overworld renderer may
  be incompatible.
- Mods that assume infinite chunk X, ordinary global distance, or untouched
  world-generation internals may need explicit RingWorld support.
- The distant Atlas is a surface summary. It cannot reproduce individual
  distant blocks, buildings, mobs, transparent layers, local block lights, or
  live weather volumes.
- Existing worlds cannot be converted or resized in place.
- Only Minecraft 26.1.2 is currently advertised as supported. Other versions
  are not supported merely because they compile or begin loading.

Compatibility reports are welcome. Please include the Minecraft version,
loader, RingWorld version, other installed mods, logs, and reproduction steps.

## Version support and contributing

The project is building a repeatable qualification system for Minecraft
26.1.x and later releases. It compiles each target, launches clean external
servers and clients, runs gameplay/worldgen/Atlas/rendering fixtures, and
records exactly which version or loader fails. A version is advertised only
after the required automated and human checks pass.

There is also an open community contribution lane for the planned Minecraft
1.21.1 backport:

- [1.21.1 backport epic](https://github.com/Delaser/RingWorld/issues/181)
- [1.21.1 milestone](https://github.com/Delaser/RingWorld/milestone/1)
- [1.21.1 contributor guide](versions/mc1.21.1/README.md)

The backport is not yet playable or supported. Contributors should take one
of the linked issues and target the `port/mc-1.21.1` integration branch.

General bug reports, compatibility observations, documentation improvements,
and code contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md)
and [AGENTS.md](AGENTS.md) before changing topology, networking, world
generation, persistence, or rendering.

## Building from source

The current 26.1.2 source requires Java 25.

```sh
./gradlew clean test build --console=plain
./gradlew :neoforge:test :neoforge:build --console=plain
```

The Fabric jar is written under `build/libs/`; the NeoForge jar is under
`neoforge/build/libs/`. A green build is only a compiler/unit-test result. See
the testing and release documentation before distributing a build.

## Technical documentation

The detailed engineering information previously kept on this page lives in
the project documentation:

| Document | What it covers |
| --- | --- |
| [Documentation index](docs/README.md) | Complete documentation map |
| [Current state](docs/CURRENT_STATE.md) | Implemented behavior, evidence, open work, and current qualification status |
| [Architecture](docs/ARCHITECTURE.md) | Canonical coordinates, client charts, rendering, and data flow |
| [Operations](docs/OPERATIONS.md) | World configuration, servers, commands, backups, Atlas recovery, and packaging |
| [Compatibility](docs/COMPATIBILITY.md) | Supported environment, API contract, and known mod conflicts |
| [Rendering](docs/RENDERING.md) | Curvature, distant terrain, fog, sky, clouds, and visual handoff |
| [Network protocol](docs/NETWORK_PROTOCOL.md) | Required settings handshake and Atlas transport |
| [Testing](docs/TESTING.md) | Automated fixtures, commands, evidence, and safety rules |
| [Minecraft version plan](docs/MINECRAFT_VERSION_SUPPORT_PLAN.md) | Automated 26.1.x qualification and rolling-version release work |
| [Licensing](docs/LICENSING.md) | MPL-2.0 distribution and source-availability guidance |

This separation is intentional: the README explains the mod to players and
new contributors, while the evidence and implementation details remain
available to maintainers without overwhelming the project landing page.

## License

RingWorld is open-source software licensed under the
[Mozilla Public License 2.0](LICENSE).

You may use it, include it in modpacks, modify it, redistribute it, and
contribute improvements under the terms of that licence. Modified RingWorld
source files remain MPL-2.0 when distributed. The licence does not grant
rights to imply that an unofficial fork is endorsed by the RingWorld project.
