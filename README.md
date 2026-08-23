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

> **Minecraft 1.21.1 backport branch:** this branch contains RingWorld 1.0
> Beta 1 for Fabric and NeoForge on Java 21. The exact release jars, hashes,
> validation scope, limitations, known build problems, and future update
> guidance are recorded in the
> [1.21.1 backport guide](versions/mc1.21.1/README.md).

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
- **Fabric and NeoForge.** The 1.21.1 Beta is built and validated for both
  loaders from one shared source revision.
- **Server support.** The mod works in single-player and on dedicated
  multiplayer servers. Every connecting player needs the matching mod.

## Download and requirements

This branch's current build is **RingWorld 1.0 Beta 1 for Minecraft Java
1.21.1**. It is separate from the stable Minecraft 26.1.2 release on `main`.

| Loader | Required software |
| --- | --- |
| Fabric | Minecraft 1.21.1, Java 21, Fabric Loader 0.16.14 or newer compatible build, Fabric API for 1.21.1 (validated with 0.116.15+1.21.1) |
| NeoForge | Minecraft 1.21.1, Java 21, NeoForge 21.1.239 or newer compatible 1.21.1 build |

Install the Fabric **or** NeoForge RingWorld jar in the normal `mods` folder.
Do not install both. A Fabric client joins a Fabric RingWorld server; a
NeoForge client joins a NeoForge RingWorld server. The server and every client
must use compatible RingWorld builds.

Matched standalone Beta jars were submitted to CurseForge as files
[8714613](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/8714613)
(Fabric) and
[8714619](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/8714619)
(NeoForge). Processing or moderation may delay public availability. There is
currently no claimed 1.21.1 Modrinth version, installer, launcher bundle, or
outer server package.

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

- The 1.21.1 build is a Beta with Windows-x64 runtime and release evidence;
  equivalent Linux and macOS release gates have not run.
- Shader packs and mods that assume an unmodified flat Overworld renderer may
  be incompatible.
- Mods that assume infinite chunk X, ordinary global distance, or untouched
  world-generation internals may need explicit RingWorld support.
- The distant Atlas is a surface summary. It cannot reproduce individual
  distant blocks, buildings, mobs, transparent layers, local block lights, or
  live weather volumes.
- Existing worlds cannot be converted or resized in place.
- Only Minecraft 1.21.1 is accepted by this branch's jars. Do not move a
  26.1/26.1.2 world backwards into 1.21.1 or assume a later Minecraft version
  is compatible merely because it begins loading.

The [backport guide](versions/mc1.21.1/README.md#known-limitations-and-problems)
records the complete platform, build-cache, mod-compatibility, Atlas,
rendering, save, and coverage boundaries.

Compatibility reports are welcome. Please include the Minecraft version,
loader, RingWorld version, other installed mods, logs, and reproduction steps.

## Backport status and contributing

The Minecraft 1.21.1 backport is integrated on `port/mc-1.21.1`. Its matched
Fabric and NeoForge Beta artifacts were built from exact public commit
`7010d2af3750cd040302b0a6bc580b6440a3b779`; later branch work records release
tooling and documentation without changing those jars.

Start with these records:

- [1.21.1 backport epic](https://github.com/Delaser/RingWorld/issues/181)
- [1.21.1 milestone](https://github.com/Delaser/RingWorld/milestone/1)
- [1.21.1 release, maintenance, limitations, and update guide](versions/mc1.21.1/README.md)
- [exact compiler and runtime evidence](versions/mc1.21.1/COMPILER_BASELINE.md)

Contributors should branch from and target `port/mc-1.21.1`. Minecraft 26.1
remains the behavioral authority: keep topology, storage, protocol, worldgen,
Atlas formats, and tests shared, and isolate only genuine 1.21.1 API,
descriptor, renderer, shader, lifecycle, and loader differences.

General bug reports, compatibility observations, documentation improvements,
and code contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md)
and [AGENTS.md](AGENTS.md) before changing topology, networking, world
generation, persistence, or rendering.

## Building from source

This backport branch requires Java 21. The normal graph builds and tests both
loaders:

```powershell
.\gradlew.bat clean build :neoforge:build --console=plain --no-daemon
```

The Fabric jar is written under `build/libs/`; the NeoForge jar is under
`neoforge/build/libs/`. The default artifact identity is deliberately
`0.0.0-backport+mc1.21.1`; do not distribute it. A release-labelled rebuild
requires explicit version/label overrides, strict dependency verification,
loader metadata and licence inspection, clean pushed-source equality, and the
relevant runtime/visual gates. See the
[backport build notes](versions/mc1.21.1/README.md#building-the-branch).

## Technical documentation

The detailed engineering information previously kept on this page lives in
the project documentation:

| Document | What it covers |
| --- | --- |
| [Documentation index](docs/README.md) | Complete documentation map |
| [Minecraft 1.21.1 backport guide](versions/mc1.21.1/README.md) | Beta identity, validated behavior, API/renderer/network findings, limitations, build hazards, and future update procedure |
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
