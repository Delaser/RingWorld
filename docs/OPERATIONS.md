# Configuration and operations

## Minecraft 1.21.1 Beta stack

| Component | Version |
| --- | --- |
| Minecraft Java | 1.21.1 exactly |
| Java | 21 |
| Fabric Loader/API | 0.16.14 / 0.116.15+1.21.1 |
| NeoForge | 21.1.239 |
| Mappings | Mojang official names; Fabric remaps its runtime jar to intermediary |
| Fabric Loom | 1.8.13 |
| ModDevGradle | 2.0.143 |
| Gradle wrapper | 8.10 |

The 1.21.1 Beta uses the same player-facing configuration, immutable saved
layout, Atlas controls, backup requirements, and server/client installation
model described below. Build commands, file metadata, platform limits,
build-cache warning, and future update procedure differ from the mainline
stack; use the
[`1.21.1 backport guide`](../versions/mc1.21.1/README.md) as authority for
those boundaries.

## Stable mainline stack

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
matrix. Safe-small 6/12/28 and production tangent/radial visual review passes,
as does the fail-closed Fabric/Modrinth staging workflow. The 26.1.2 Fabric
alpha is playable but not yet a stable release: optional convenience packages,
independent release-candidate review, broader regression coverage, and
compatibility work remain. Deployment and live-world changes still require
explicit owner approval. The mod must be installed on the server and every
client. The historical 1.21.11 rollback is `mc-1.21.11-final`.

## Bootstrap configuration

File:

```text
<gameDir>/config/ringworld.properties
```

If absent, the mod creates it at startup.

| Property | Default | Validation/meaning |
| --- | ---: | --- |
| `widthBlocks` | 256 | At least 256, divisible by 16, sufficient rim interior, and within atlas/axis budgets |
| `circumferenceBlocks` | 16384 | Power-of-two; exactly 1,024 chunks and 32 region widths; large enough for 64 blocks of radial clearance above the build top (2,016 aligned playable minimum for vanilla bounds; 1,024 is structural-only) |
| `wallHeightBlocks` | 160 | At least 32; measured from world minimum Y; wall and cloud top must fit the build range |
| `testMode` | false | Enables destructive local automated harness |
| `testViewDistanceChunks` | 28 | Initial live/LOD capture distance for the local harness; 2–32 |
| `pregenerateTerrainAtlas` | true | Generates missing canonical surface chunks in background |
| `requestOceanMonument` | false | New-world-only opt-in guarantee; saves a deterministic satisfied/unsatisfied result before generation |

The config record is cached for the process lifetime. Restart after manually
editing the file. The in-game editor updates both the file and process cache
immediately.

The Create World screen has a bottom-left `RingWorld C×W` button. Its centered,
responsive editor provides **Small** (2,048×128×160), **Medium**
(16,384×256×160), and **Large** (32,768×512×160) presets, plus custom
circumference, width, wall-height, a reset to `config/ringworld.properties`,
and the new-world ocean-monument control. Reset does not read or change an
existing world's saved layout. The live maths panel shows:

- walking-lap time at 4.317 blocks/s;
- radius, diameter, and opposite-band angular width;
- canonical chunks, playable interior, and five-block rims;
- atlas grid/cells/raw size, rim/cloud Y, and measured-reference
  pregeneration/disk estimates.

Updating an existing world to a build containing the seam block-entity fix
does not require regeneration. New runtime block-entity reads and writes use
canonical Overworld ownership immediately. Before upgrading a world known to
contain a split seam double chest, back it up and inspect both half-inventories:
the mod deliberately does not guess how to merge two already-divergent
inventories or delete a possible alias copy. A lone saved alias is repaired to
its canonical owner on load. If canonical and alias NBT both exist, both are
kept and the server log warns with the exact positions; recover the contents
from the backup before removing or rebuilding that seam container.

Only atlas, chunk-count, pregeneration, or disk thresholds produce the amber
**High cost** label; an apparent-width visual advisory alone does not.

The same information remains visible at GUI scale 4 and a 320×270 logical
viewport. Small disables the monument option because width 128 cannot fit its
required margins; width 160 or greater enables it. Its guaranteed stronghold
keeps the portal room and terrain envelope inside the band while optional
graph bounds may extend into suppressed exterior space. Small players may
need to mine to the portal room, so the editor labels this preset experimental.

Invalid layouts cannot be applied. Parsing and basic structural checks
aggregate applicable field-level messages, so malformed circumference, width,
and wall-height values can be corrected together. Once those fields meet their
minimum and alignment rules, cross-field radial and wall errors are reported
together. The chosen values are bootstrap defaults for the next new world;
they do not mutate any save already created. Applying a valid layout opens a
second confirmation that names the dimensions, wall height, and monument
choice before immutable first-load defaults are written. The monument option
searches once for a valid ocean-monument location as that new world first
loads, then saves the satisfied or unavailable result separately in
`ringworld:structure_policy`; existing worlds never gain it from a later
config edit.

## Persistence and immutability

On first Overworld load, the mod writes persistent state with:

```text
width, circumference, generator seed, wall height, surface reference, format version
```

Every saved layout field takes precedence on subsequent loads. Changing
bootstrap dimensions or wall height does not resize or redecorate an existing
RingWorld. Format-1 and format-2 saves migrate to format 3 with surface
reference Y=64 and their legacy terrain-noise mapping preserved. Fresh worlds
use the corrected annular mapping. This prevents unexplored chunks in an alpha
world from changing terrain algorithms after an update. The mapping is part of
the atlas world hash, so an incompatible cached atlas is discarded and rebuilt.

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

### What players see

A new world does not wait for the complete distant ring before joining. The
client and server deliberately reveal it in four layers:

1. ordinary authoritative chunks around the player;
2. an immediate curved placeholder, biome-flavoured where trustworthy samples
   exist, with heavy low-progress haze and temporary rim returns;
3. checkpointed Atlas revisions that stream and cross-fade as canonical chunks
   are sampled;
4. the expanded full-detail texture and terrain-height mesh after verified
   100% completion.

The HUD displays `Ring Atlas Generating: X%` while the acknowledged Atlas is
incomplete. The label and generation haze reach zero at completion. The
RingWorld Map reports the authoritative job state, cell count, rate, and ETA;
closing it does not stop the job. Player travel is not required. A disconnect,
restart, pause, or clean shutdown retains captured cells, and a later start or
resume continues from the first missing canonical chunk.

Operators should set expectations accordingly: the world is usable before the
Atlas completes, but the far ring will initially be coarser, foggier, and less
colour-accurate. The job generates and saves real chunks, so duration and disk
growth scale with ring size and vary with seed, storage, CPU, active players,
and other mods. Use the current rate and ETA rather than promising the
development benchmark on production hardware.

Atlas pregeneration visits one missing canonical chunk at a time when the
normal server chunk queue has fewer than 64 pending tasks.

| Geometry | Canonical chunks | Source cells at 8-block step |
| --- | ---: | ---: |
| 2048×416 safe-small | 3,328 | 13,312 |
| 16384×256 default | 16,384 | 65,536 |

The supported sample step remains fixed at eight blocks. The production atlas
uses 458,752 raw primitive-array bytes and about 459,264 encoded full-stream
bytes. The #69 benchmark rejected adaptive 4/2/1-block profiles for this
release because they multiply source/cache/transfer cost by 4/16/64 without
changing the capped GPU texture or mesh. See
[`ATLAS_FIDELITY_BENCHMARK_2026-08-01.md`](ATLAS_FIDELITY_BENCHMARK_2026-08-01.md).

The ignored two-client Atlas-concurrency harness can create either development
or production-sized disposable worlds through
`ringMultiplayerCircumferenceBlocks`, `ringMultiplayerWidthBlocks`, and
`ringMultiplayerWallHeightBlocks`; it is never a live-server configuration.
The Atlas verifier derives its required count from `(circumference / 8) *
(width / 8)` and rejects a different logged total. See the exact loader-
qualified production commands and safe restart procedure in
[`TESTING.md`](TESTING.md#opt-in-atlas-concurrency-gate-130).

Production-default atlas completion is therefore a large world-generation
operation. Monitor disk use, server tick time, and progress logs. Set
`pregenerateTerrainAtlas=false` to postpone automatic background generation. The distant
surface progressively reveals only trustworthy cells from player-loaded or
pregenerated chunks; missing cells use the deterministic opaque fallback until generated.
Progress logs report captured cells, cells per second, and an ETA once a rate
can be measured.

A clean-atlas benchmark on a disposable copy of the production 16,384×256
world completed all 65,536 cells in 13 minutes 37 seconds, or about 80.2 cells
per second. The generating server peaked at about 1.06 GiB RSS in 15-second
samples, the completed compressed atlas was 76 KiB, and the copied world grew
by about 169.3 MiB, chiefly from generated chunk data. It produced no
server-behind warning, generator error, RingWorld exception, or crash. Treat
these as one-machine reference measurements rather than resource guarantees.

The creation editor scales that measured 16,384-chunk, 817-second,
177,523,917-byte reference with checked integer arithmetic. It reports the
result as a planning estimate, not a promise: seed, storage, CPU, JVM, and other
mods can change real cost. Layouts estimated above 30 minutes or 512 MiB add a
warning. Atlas wire size and the minimum eight-tiles-per-tick transfer duration
are calculated exactly from the selected geometry.

The 16,384×256 production-default static resource envelope is approximately
0.44 MiB of raw atlas arrays/wire payload, 5.33 MiB for the RGBA8 GPU texture
including its mip chain, 9.0 MiB for the maximum-detail mesh, and 12.0 MiB of
conservative texture-build scratch. Gzip disk size depends on terrain but
cannot be used as the memory budget. The creation editor reports these
calculated values. The technical 16-million-cell atlas ceiling represents
about 106.8 MiB of raw atlas arrays and is a hard allocation limit, not a
recommended production target.

Operators with gamemaster permission can inspect or control background
pregeneration without changing immutable world layout:

```text
/ringworld atlas status
/ringworld atlas start
/ringworld atlas pause
/ringworld atlas resume
```

`start` explicitly begins an idle partial atlas. `resume` resumes a process-local
paused job; after a restart, it also starts from the durable partial atlas when
the handle has returned to `IDLE`. Neither command changes immutable world
layout or discards saved cells. The local #131 runtime gate covers both the
idle start and restart-from-`IDLE` resume paths; either command rejects a
release-pending terminal handle until its loading ticket has closed.

Players in a RingWorld Overworld can instead open **RingWorld Map** from the
pause menu. Its generation actions require an integrated-world owner or a
dedicated gamemaster; other players see read-only status. **Generate Entire
Ring** confirms the exact canonical chunk count and warns that it generates
and saves real terrain/region files. Closing the map does not pause or cancel
the job; reopening attaches to the same dimension-owned handle. The header
shows the embedded release/artifact identity (`1.0 · 1.0.0+mc26.1.2` for
the prepared candidate), and the first status line shows the persisted terrain
mapping so a screenshot identifies both the installed build and worldgen.

Pause stops scheduling new atlas chunks after any one in-flight chunk
completes. Player-driven chunk capture, cache saving, and client tile streaming
continue. The pause is operational process state rather than saved layout
state, so a server restart returns to the configured
`pregenerateTerrainAtlas` behavior.

Server atlas:

The background setting starts one idempotent `BACKGROUND` handle per loaded
RingWorld Overworld. `/ringworld atlas status|start|pause|resume` observes or
controls that same process-local handle. Cancel and explicit headless prewarm
are not exposed by the current command adapter. Cancellation during the
service phase checkpoints durable cells rather than deleting terrain; a failed
checkpoint is reported as failure rather than a misleading successful cancel.
With `pregenerateTerrainAtlas=false`, that handle is intentionally `IDLE` and
continues to sample player-loaded chunks. A gamemaster may later use
`/ringworld atlas start` or `/ringworld atlas resume` to start the same
single-writer service from the durable partial atlas.

If the previous job is terminal but still owns a ticket whose release is being
retried, `start` is temporarily rejected rather than replacing that job. The
command and map return the release-pending message; retry shortly after a
server tick. This fail-closed interval prevents a failed `close()` from
orphaning its loading ticket.

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

For an explicit non-interactive preparation run, use the checked-in Gradle
fixture only after accepting its disposable EULA:

```sh
./gradlew :runHeadlessPrewarmServer --console=plain
./gradlew :runHeadlessPrewarmServer --console=plain \
  -PringHeadlessPrewarmSource="save-folder-id"
./gradlew :runHeadlessPrewarmServer --console=plain \
  -PringHeadlessPrewarmResume=true
```

For a new production-default disposable world, add
`-PringHeadlessPrewarmCircumference=16384 -PringHeadlessPrewarmWidth=256`.
NeoForge provides equivalent `ringNeoForgeHeadlessPrewarmCircumference` and
`ringNeoForgeHeadlessPrewarmWidth` properties.

Fresh format-3 production prewarms completed successfully on both loaders on
2026-08-10. Fabric generated all 16,384 chunks / 65,536 cells in 38m16s at
about 29 cells/s; NeoForge completed the same totals in 41m at about 27
cells/s. Both wrote schema-2 `COMPLETE` reports with mapping `2`, saved normally,
and then passed a separate complete-atlas resume/load run.

The second form copies `run/saves/<save-folder-id>` to the ignored
`run-headless-prewarm/world`; it never opens or modifies the source. The first
form creates only that selected disposable world. Both use the normal dedicated
server world directory, disable empty-server pausing, reject an accepted join
immediately, and write atomic
JSON under `world/ringworld-prewarm/`: `progress.json` every 20 ticks and
`result.json` on `COMPLETE`, `FAILED`, `INTERRUPTED`, or `REJECTED`. Reports
carry schema version 2, elapsed time, exact durable chunks/cells, world hash,
layout fingerprint, explicit terrain-noise mapping, atlas path, rate/ETA/error
where relevant, and a failure reason. A rejected startup has
`identityAvailable:false` and zero/null identity
sentinels. An external SIGTERM cancels/releases an outstanding atlas chunk
request without resolving a possibly evicted chunk-cache result, then
checkpoints before writing `INTERRUPTED`; rerun with
`-PringHeadlessPrewarmResume=true` to retain the disposable runtime world and
resume from saved atlas cells. The selected cursor is not advanced during
teardown, so an uncaptured final chunk is safely retried. The Gradle
finalizer fails unless the terminal result is `COMPLETE`, because Minecraft can
exit zero after a failed run. Both the dedicated coordinator and Gradle fixture
delete the selected old result/progress files before every launch (including
resume), then parse schema version, identity, atlas path, and exact complete
totals rather than trusting stale text. Do not
add the headless JVM option to an ordinary service unit or point it at a
production/source world.

Fresh and normal resumed format-3 runs default to expected mapping `4`
(`annular-complete-v2`). To verify an intentionally copied older world, pass
the world's explicit mapping instead; for example,
`-PringHeadlessPrewarmExpectedTerrainNoiseMapping=1` for Fabric or
`-PringNeoForgeHeadlessPrewarmExpectedTerrainNoiseMapping=1` for NeoForge.
The verifier rejects any terminal report whose explicit mapping differs from
that expectation.

An ordinary copied world with existing region files and no RingWorld settings
is rejected during `ServerLevel` construction, before the normal Fabric level
load hook. In explicit headless mode the constructor-tail bridge clears stale
evidence and writes `REJECTED` with `identityAvailable:false`, zero totals and
identity values, a null atlas path, and the original failure reason; it then
rethrows that original startup failure. This is an intentional failed run, not
a conversion path or a successful clean server halt.

Copied 1.21.11 worlds may also contain the legacy server atlas at
`<world>/data/ringworld-terrain-atlas.rwat.gz`. It migrates once only when the
new path is absent and its format, geometry, sampling layout, and world hash
match the saved RingWorld settings. A corrupt, old-format, or different-world
legacy atlas is left in place and rebuilt at the new path. If a new-path atlas
already exists but is invalid, it is authoritative and rebuilt without legacy
fallback. A leftover `.tmp` file from an interrupted write is safe: the next
successful save or validated migration replaces it atomically.

The current disk atlas format is 6. Upgrading from an older format
automatically invalidates and rebuilds both server and client caches so the
renderer samples the actual highest block rather than the block below it,
records its exposed top-face height, and receives texture-luminance-corrected
biome surface colours. Format 5 also replaces zero grass/foliage tint from a
dedicated server's unloaded client-only colour maps with the sampled block map
colour. Format 6 also persists a monotonic surface revision. Connected clients
receive bounded changed tiles after exposed terrain edits; reconnect reuse is
allowed only when the complete client cache revision exactly matches the
server. This is independent of the persisted RingWorld settings/protocol
format.

## Build

Build `port/mc-1.21.1` under Java 21. The normal project graph includes both
loaders:

```powershell
.\gradlew.bat clean build :neoforge:build --console=plain --no-daemon
```

Its default `0.0.0-backport+mc1.21.1` artifacts are diagnostic only. Use the
[backport build and release record](../versions/mc1.21.1/README.md#building-the-branch)
before producing or distributing a release-labelled jar.

Build stable `main` under Java 25:

```sh
JAVA_HOME=/path/to/jdk-25/Contents/Home \
PATH="$JAVA_HOME/bin:$PATH" \
./gradlew clean test build --console=plain
```

Expected development artifacts:

```text
build/libs/ringworld-1.0.0+mc26.1.2.jar
build/libs/ringworld-1.0.0+mc26.1.2-sources.jar
```

The current suite passes 338 unit/parameterized cases per loader. The
historical Phase 2 95-error inventory and the subsequent source-port
checkpoint are recorded in
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

## Optional client and server package staging

The standalone Modrinth jar is the normal installation path. Optional Prism
client bundles and a dedicated-server overlay are built locally from the same
verified runtime jar; they do not replace or hide that jar.

The active local development bundle is under `dist/client-bundle/`, but
`dist/` is deliberately not versioned. It contains generated launcher/runtime
state and may contain live account tokens after sign-in.

Versioned launcher sources live under `deploy/client/`. Copy them into each
generated bundle before publishing. On every start they refresh only the
packaged RingWorld jar, Fabric API jar, and `mmc-pack.json` in an existing
instance. They preserve accounts, saves, options, screenshots, resource packs,
existing RingWorld configuration, and unrelated `instance.cfg` values. The
only intentional instance-settings migration enables automatic Java selection
and clears an explicit Java-location override so Prism can select Java 25.

The generated client instance includes one minimal public `servers.dat` entry:
**RingWorld Test Server** at `andwhatnotstudio.com:25565`. It is generated from
constants in `prepare_release_packages.py`, contains no player data, and is
only copied for a newly created managed instance. It must never auto-join the
server or replace an existing user's server list.

The optional unlisted Windows test package is served from
`/ringworld/alpha/`. Build it only from a clean pushed revision through the
normal staging and `prepare_release_packages.py` gates. Publish the ZIP under
the exact artifact name stored in `RELEASE-MANIFEST.json`, the stable
loader-specific `deploy/alpha/Install-RingWorld-Alpha-{Fabric,NeoForge}-Windows.bat`
bootstrappers, `RELEASE-MANIFEST-{FABRIC,NEOFORGE}.json`, `SHA256SUMS.txt`, MPL
licence, and landing page together; back up the previous directory outside the
document root and install the landing page last. Keep the historical
`Install-RingWorld-Alpha-Windows.bat` and `RELEASE-MANIFEST.json` as Fabric
aliases so previously downloaded installers continue to update. Each
bootstrapper downloads its current manifest on every run, validates
format/loader/licence/source identity, selects exactly one safe loader-matched
Windows artifact, and verifies its manifest SHA-256 before extraction. The two
named installers use separate local installation directories, so testing one
loader does not overwrite the other.
It must never contain a build-specific hash or trust an artifact filename that
can escape the alpha directory. Run `python3 -m unittest
scripts/test_alpha_installer.py` and verify the downloaded HTTPS ZIP hash rather
than trusting the uploaded file alone. Do not link the alpha page from the main
showcase unless the owner explicitly changes its unlisted status.

Never distribute a used `.prism-data` directory. Create a fresh package from
the source instance that contains only:

- mod jars;
- config;
- instance metadata;
- launcher scripts/instructions.

Generated archives are intentionally ignored. Validate every outer archive,
nested Prism instance, embedded mod hash, checksum, and exact public source
revision before distribution.

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

Each package manifest links the full public commit used for the artifact. A
release tag may additionally identify an approved build. See
[`LICENSING.md`](LICENSING.md).

First create the dual-loader review stages from a clean, pushed public commit,
then build optional packages from the generated provenance manifest:

```sh
python3 scripts/stage_modrinth_release.py --loader both --build

python3 scripts/prepare_release_packages.py \
  --loader fabric \
  --stage-manifest dist/modrinth/1.0.0+mc26.1.2/fabric/STAGING-MANIFEST.json \
  --fabric-api /path/to/fabric-api-0.155.2+26.1.2.jar \
  --output dist/release-candidate-fabric

python3 scripts/prepare_release_packages.py \
  --loader neoforge \
  --stage-manifest dist/modrinth/1.0.0+mc26.1.2/neoforge/STAGING-MANIFEST.json \
  --output dist/release-candidate-neoforge

python3 -m unittest \
  scripts/test_verify_distribution_license.py \
  scripts/test_prepare_release_packages.py \
  scripts/test_stage_modrinth_release.py
```

Put Java 25 first on the environment before staging. The staging script always
runs a clean dual build and rejects any other active Java generation with a
direct setup message before invoking Gradle. Package assembly derives the jar,
version, hash, loader, and source revision from that generated stage; it has no
free-form artifact or source-revision argument.

Test both package paths: a completely fresh bundle and an in-place upgrade over
an existing `.prism-data` directory containing sentinel account, save, option,
and configuration files. A new ZIP whose launcher only initializes a missing
instance does not update existing users and can leave a stale network codec.

The macOS launcher validates Java rather than trusting a configured path. It
selects a detected Java 25 runtime from the prior instance or common macOS,
Homebrew, SDK, and user-local locations. If none exists it leaves Java
selection to Prism. Package tests isolate `HOME` so both the no-runtime
fallback and replacement of a stale Java 21 path by a discovered Java 25 path
are deterministic.

The builder fails closed on stale MIT/evaluation metadata, a missing or
mismatched licence, a stale compatibility API version, source jars, archive
traversal, accounts, saves, logs, options, screenshots, resource packs,
existing managed jars, or a non-exact source revision. The Windows launcher
update path runs on a Windows GitHub Actions runner when package inputs change.
It emits no website content and cannot publish, deploy, restart a service, or
touch a live world.

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
- persisted terrain mapping name and number;
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
- Nether travel can span any number of effective RingWorld laps. On return,
  vanilla first scales the Nether coordinates by 8; RingWorld then wraps X and
  clamps only an out-of-band Z target to the nearest portal-safe interior
  latitude. Existing safe canonical portals are reused across the X seam.
  There is no operator setting for this policy and an existing world does not
  need regeneration after the mod update.
