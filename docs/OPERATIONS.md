# Configuration and operations

## Active port stack

| Component | Version |
| --- | --- |
| Minecraft Java | 26.1.2 |
| Java | 25 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.155.2+26.1.2 |
| Mappings | None; Minecraft 26.1.2 is unobfuscated |
| Fabric Loom | 1.17 snapshot used by `gradle.properties` |
| Gradle wrapper | 9.5.1 |

This stack now produces a green development build and passes isolated fresh
and copied-world dedicated-server launch gates plus the integrated safe-small
client atlas/rendering/gameplay harness and dedicated two-client multiplayer
matrix. It remains non-playable until multi-size visual review,
automated-harness completion, packaging, and staging gates pass. The working
service, packages, and rollback remain Minecraft 1.21.11 at
`mc-1.21.11-final`; do not deploy the 26.1 branch until every release gate in
the port plan passes. The finished mod must be installed on the server and
every client.

## Bootstrap configuration

File:

```text
<gameDir>/config/ringworld.properties
```

If absent, the mod creates it at startup.

| Property | Default | Validation/meaning |
| --- | ---: | --- |
| `widthBlocks` | 256 | At least 256, divisible by 16, sufficient rim interior, and within atlas/axis budgets |
| `circumferenceBlocks` | 16384 | Power-of-two; exactly 1,024 chunks and 32 region widths; large enough for 64 blocks of radial clearance above the build top (2,016 minimum for vanilla bounds) |
| `wallHeightBlocks` | 160 | At least 32; measured from world minimum Y; wall and cloud top must fit the build range |
| `testMode` | false | Enables destructive local automated harness |
| `testViewDistanceChunks` | 28 | Initial live/LOD capture distance for the local harness; 2–32 |
| `pregenerateTerrainAtlas` | true | Generates missing canonical surface chunks in background |

The config record is cached for the process lifetime. Restart after manually
editing the file. The in-game editor updates both the file and process cache
immediately.

The Create World screen has a bottom-left `RingWorld C×W` button. Its editor
provides safe-small (2,048×416), production-default, and current presets plus
custom circumference, width, and wall-height fields. It previews:

- chunks around/across and total canonical chunks;
- radius, physical centre, and apparent opposite width;
- wall/cloud elevation and top radial clearance;
- atlas cells/raw memory;
- GPU texture size, blocks per texel, and mesh vertices.

Invalid layouts cannot be applied. The chosen values are bootstrap defaults
for the next new world; they do not mutate any save already created. Applying
a valid layout opens a second confirmation that names the dimensions and wall
height before those immutable first-load defaults are written.

## Persistence and immutability

On first Overworld load, the mod writes persistent state with:

```text
width, circumference, generator seed, wall height, surface reference, format version
```

Every saved layout field takes precedence on subsequent loads. Changing
bootstrap dimensions or wall height does not resize or redecorate an existing
RingWorld. Format-1 saves migrate to format 2 with surface reference Y=64.

Minecraft 26.1 stores RingWorld settings at:

```text
<world>/dimensions/minecraft/overworld/data/ringworld/settings.dat
```

On the first load of a copied 1.21.11 RingWorld, the old
`<world>/data/ringworld_settings.dat` is atomically copied to that namespaced
dimension-owned path before decoding. The saved geometry remains authoritative;
the original legacy file is left untouched as part of the world copy.

Back up a world before changing any RingWorld version or decorative setting.
An Overworld with existing `.mca` files under
`<world>/dimensions/minecraft/overworld/region` but no readable RingWorld
settings is explicitly rejected. There is no supported flat-world conversion
path.

## Ring sizes

### Production defaults

```text
circumference: 16384 blocks = 1024 chunks
width:           256 blocks = 16 chunks
radius:         about 2607.59 blocks
```

The walking circumference is approximately 63 minutes at normal Minecraft
walking speed. Its power-of-two length aligns exactly with chunks, 32 complete
region-file widths, the eight-block atlas sample grid, and the capped proxy
texture and mesh budgets.

### Development geometry

```text
circumference: 2048 blocks = 128 chunks
width:          416 blocks = 26 chunks
radius:         about 325.95 blocks
```

This safe-small ring intentionally exaggerates visible curvature and completes
the atlas quickly. With the current Y=64 surface reference its physical centre
is near Y=389.95, leaving about 69.95 radial blocks beyond the top vanilla
build plane. The retired 1600×320 fixture crossed the ring centre near Y=319
and remains valid only as a required validation-failure case; see
[`DIMENSION_SCALING_PLAN.md`](DIMENSION_SCALING_PLAN.md).

## Atlas cost

Atlas pregeneration visits one missing canonical chunk at a time when the
normal server chunk queue has fewer than 64 pending tasks.

| Geometry | Canonical chunks | Source cells at 8-block step |
| --- | ---: | ---: |
| 2048×416 safe-small | 3,328 | 13,312 |
| 16384×256 default | 16,384 | 65,536 |

Production-default atlas completion is therefore a large world-generation
operation. Monitor disk use, server tick time, and progress logs. Set
`pregenerateTerrainAtlas=false` to postpone background generation; the
complete-ring texture will not build until a complete atlas is available.
Progress logs report captured cells, cells per second, and an ETA once a rate
can be measured.

A clean-atlas benchmark on a disposable copy of the production 16,384×256
world completed all 65,536 cells in 13 minutes 37 seconds, or about 80.2 cells
per second. The generating server peaked at about 1.06 GiB RSS in 15-second
samples, the completed compressed atlas was 76 KiB, and the copied world grew
by about 169.3 MiB, chiefly from generated chunk data. It produced no
server-behind warning, generator error, RingWorld exception, or crash. Treat
these as one-machine reference measurements rather than resource guarantees.

The 16,384×256 production-default static resource envelope is approximately
0.44 MiB of raw atlas arrays/wire payload, 5.33 MiB for the RGBA8 GPU texture
including its mip chain, 2.25 MiB for the maximum-detail mesh, and 12.0 MiB of
conservative texture-build scratch. Gzip disk size depends on terrain but
cannot be used as the memory budget. The creation editor reports these
calculated values. The technical 16-million-cell atlas ceiling represents
about 106.8 MiB of raw atlas arrays and is a hard allocation limit, not a
recommended production target.

Operators with gamemaster permission can inspect or control background
pregeneration without changing immutable world layout:

```text
/ringworld atlas status
/ringworld atlas pause
/ringworld atlas resume
```

Pause stops scheduling new atlas chunks after any one in-flight chunk
completes. Player-driven chunk capture, cache saving, and client tile streaming
continue. The pause is operational process state rather than saved layout
state, so a server restart returns to the configured
`pregenerateTerrainAtlas` behavior.

Server atlas:

```text
<world>/dimensions/minecraft/overworld/data/ringworld/terrain-atlas.rwat.gz
```

Client atlas:

```text
<gameDir>/ringworld-cache/terrain-<worldHash>.rwat.gz
```

Deleting an atlas cache is recoverable but forces regeneration or
retransmission. Do not delete the world settings state unless intentionally
invalidating the world.

The current release performs this work automatically through its background
server-tick scheduler, so walking a lap is not required, but it lacks a clear
player control and progress UI. A **Generate Entire Ring** button, reusable
function, and explicit operator and headless `prewarm-and-stop` workflows are
planned in [`ATLAS_PREGENERATION_PLAN.md`](ATLAS_PREGENERATION_PLAN.md); those
commands and launch modes must not be advertised as implemented until their
integration gates pass.

Copied 1.21.11 worlds may also contain the legacy server atlas at
`<world>/data/ringworld-terrain-atlas.rwat.gz`. It migrates once only when the
new path is absent and its format, geometry, sampling layout, and world hash
match the saved RingWorld settings. A corrupt, old-format, or different-world
legacy atlas is left in place and rebuilt at the new path. If a new-path atlas
already exists but is invalid, it is authoritative and rebuilt without legacy
fallback. A leftover `.tmp` file from an interrupted write is safe: the next
successful save or validated migration replaces it atomically.

The current disk atlas format is 5. Upgrading from an older format
automatically invalidates and rebuilds both server and client caches so the
renderer samples the actual highest block rather than the block below it,
records its exposed top-face height, and receives texture-luminance-corrected
biome surface colours. Format 5 also replaces zero grass/foliage tint from a
dedicated server's unloaded client-only colour maps with the sampled block map
colour. This is independent of the persisted RingWorld settings/protocol
format.

## Build

Build the active branch under Java 25:

```sh
JAVA_HOME=/path/to/jdk-25/Contents/Home \
PATH="$JAVA_HOME/bin:$PATH" \
./gradlew clean test build --console=plain
```

Expected development artifacts:

```text
build/libs/ringworld-0.2.0+mc26.1.2.jar
build/libs/ringworld-0.2.0+mc26.1.2-sources.jar
```

The current suite contains 94 unit/parameterized cases. The historical Phase 2
95-error inventory and the subsequent source-port checkpoint are recorded in
`MINECRAFT_26_1_COMPILER_BASELINE.md`. These artifacts are not deployable
release candidates until the remaining runtime gates pass.

The frozen 1.21.11 tag builds under Java 21 with:

```sh
./gradlew clean test build
```

Frozen artifacts:

```text
build/libs/ringworld-0.1.0.jar
build/libs/ringworld-0.1.0-sources.jar
```

`clean` is optional for normal development but useful before a release.

## Frozen 1.21.11 server installation

These instructions describe the active public/rollback service, not the
non-playable 26.1 port branch.

Install:

- Fabric server for Minecraft 1.21.11;
- Fabric API matching the project;
- the same RingWorld jar used by clients.

Place the bootstrap config before the first world load. The repository
contains templates under `deploy/server/`:

```text
DEPLOYMENT.md
config/ringworld.properties
server.properties.example
ringworld.service
rcon-send.py
eula.txt
```

Copy `server.properties.example` to the installed `server.properties` and
apply local values there. The deployed file is intentionally ignored and must
never be committed because it may contain an RCON password.

The example service template assumes:

```text
install directory: /opt/ringworld-server
service: ringworld.service
user/group: minecraftuser
```

Choose geometry before the first Overworld load. Saved dimensions are
immutable, so changing them later requires a new world or a deliberate
migration workflow.

Typical service operations:

```sh
systemctl status ringworld
journalctl -u ringworld -f
systemctl restart ringworld
systemctl stop ringworld
```

Before replacing the jar:

1. stop the server cleanly;
2. back up the world, config, and existing jar;
3. copy the new server/client-identical jar;
4. start and watch logs for mixin/shader/protocol errors;
5. connect a matching client;
6. validate geometry acknowledgement and atlas cache;
7. perform a seam interaction test.

## Frozen 1.21.11 client installation

Clients need:

- Minecraft Java 1.21.11;
- Fabric Loader 0.19.3;
- Fabric API;
- RingWorld jar.

Server join fails for a client that cannot receive the required settings
payload. A mismatched settings format or geometry acknowledgement is also
rejected.

The active local development bundle is under `dist/client-bundle/`, but
`dist/` is deliberately not versioned. It contains generated launcher/runtime
state and may contain live account tokens after sign-in.

Versioned launcher sources live under `deploy/client/`. Copy them into each
generated bundle before publishing. On every start they refresh only the
packaged RingWorld jar, Fabric API jar, and `mmc-pack.json` in an existing
instance. They preserve accounts, saves, options, screenshots, resource packs,
existing RingWorld configuration, and `instance.cfg`.

Never distribute a used `.prism-data` directory. Create a fresh package from
the source instance that contains only:

- mod jars;
- config;
- instance metadata;
- launcher scripts/instructions.

Publication infrastructure and generated archives are intentionally outside
this source repository. Validate every outer archive, nested Prism instance,
and embedded mod hash before distribution.

RingWorld is licensed under MPL-2.0. Before any client or server artifact is
published:

1. confirm `fabric.mod.json` inside every RingWorld jar declares
   `MPL-2.0`;
2. confirm every RingWorld jar contains `LICENSE-RINGWORLD.txt`;
3. confirm the outer archive and nested Prism instance both include the
   current top-level `LICENSE`;
4. scan metadata for stale `MIT` or
   `LicenseRef-RingWorld-Evaluation-1.0` declarations;
5. make the MPL-covered Source Code Form for the exact release revision
   available by reasonable means and tell recipients where to obtain it;
6. never publish a credential-bearing runtime directory; and
7. state accurately that modified RingWorld files remain MPL-2.0 when
   distributed, while separate files in a larger work may use other terms.

The planned official source-delivery method is a public Git tag matching the
artifact. If the repository is still private, publishing a newly built MPL
executable is blocked unless a different compliant source-delivery method is
included. See [`LICENSING.md`](LICENSING.md).

Run the automated licence gate against each jar and outer bundle:

```sh
python3 scripts/verify_distribution_license.py \
  build/libs/ringworld-0.2.0+mc26.1.2.jar \
  dist/RingWorld-Test-Client.zip \
  dist/RingWorld-Test-Client-Windows.zip

python3 -m unittest scripts/test_verify_distribution_license.py
```

The MIT-labelled 0.1.0 universal and Windows bundles were withdrawn on
28 July 2026. Their server-side rollback is
`/var/backups/ringworld-web-license-correction-20260728T120000Z/`. Do not
restore those artifacts to a public path. The corrected HTTPS-verified
evaluation packages below are historical, pre-MPL artifacts and retain the
licence attached to those copies:

```text
141832393128820be4917d29bec11822f3a253f50e627f3b187e673df22918ea  RingWorld-Test-Client.zip
c92498ab3546a3ca4cd86a684eba8c37b97ef4814181aa1d686055dfc29dcc2d  RingWorld-Test-Client-Windows.zip
```

Test both package paths: a completely fresh bundle and an in-place upgrade over
an existing `.prism-data` directory containing sentinel account, save, option,
and configuration files. A new ZIP whose launcher only initializes a missing
instance does not update existing users and can leave a stale network codec.

On the public host, replace the page and archives only after copying the
existing files to a timestamped directory under `/var/backups/`. The current
27 July 2026 publication rollback, taken immediately before publishing visual
profile 4, is `/var/backups/ringworld-web-20260727T132653Z/`.

## Local macOS launch

The existing packaged test instance can be opened directly:

```sh
dist/client-bundle/.launcher/macos/Prism\ Launcher.app/Contents/MacOS/prismlauncher \
  -d "$PWD/dist/client-bundle/.prism-data" \
  -l RingWorld-Test \
  -w "New World"
```

Copy a newly built jar into the active instance before launch:

```text
dist/client-bundle/.prism-data/instances/RingWorld-Test/.minecraft/mods/
```

Also update any clean source instance used to rebuild shareable archives.

## Observability

Useful log messages:

```text
RingWorld bootstrap settings
Created RingWorld layout
Migrated RingWorld settings format
[diagnostic] joined ring world
RingWorld settings acknowledged
Loaded/Saved RingWorld terrain atlas
RingWorld terrain atlas progress
RingWorld atlas: ... generation running|paused|complete
Textured ring surface ready
Migrated legacy rim chunk
[test] ...
[multiplayer] ...
```

F3 replaces the normal position section in the Overworld with:

- canonical Ring XYZ;
- canonical block/chunk/region;
- facing direction;
- circumference/chunk count;
- atlas completion and sample step.

## Recovery notes

- An embedded player on join is moved upward only when their actual collision
  box is obstructed.
- Invalid/mismatched atlas files are ignored and rebuilt; a wrong-world hash
  is never accepted or migrated.
- A stale complete client atlas is protected by world hash.
- Legacy stone-brick boundary chunks migrate gradually, one loaded chunk per
  tick.
- Nether and End should remain usable even if Overworld-specific rendering is
  unavailable; a missing dimension guard is a bug.
