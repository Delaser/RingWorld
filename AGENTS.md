# RingWorld agent guide

This file is the first-stop operating guide for coding agents working in this
repository. Read it before changing topology, networking, world generation, or
rendering. Detailed design documents live under [`docs/`](docs/README.md).

Last playable code audit: 2026-07-28, covering the final Minecraft 1.21.11
implementation identified in the private development archive as
`mc-1.21.11-final` at commit `2c98650`. That pre-public ref is provenance only
and is intentionally not present in the clean public Git history.

Active port checkpoint: Minecraft 26.1.2/Java 25 integrated safe-small runtime
gate. The Fabric and NeoForge builds each pass all 338 unit/parameterized
cases. Fabric has completed the client/runtime gates described below. NeoForge
26.1.2.87 on ModDevGradle 2.0.143 reaches `Done` on a dedicated server and has
a client checkpoint: shared client payload/session state, mixins, shaders, and
resources load through NeoForge adapters; its render pipeline registers; and a
copied production 16,384×256 world opens through the integrated server with a
format-3 settings acknowledgement and streaming atlas metadata/tiles. The
`:neoforge:runProductionProjectionClient` copies a named
source save into an isolated run directory, waits for a complete atlas, writes
tangent/handoff/radial captures, records frame pacing, verifies the outputs,
and exits. Its production 16,384×256 noon, dusk, night, and rain runs pass;
settled stages averaged 8.3–10.7 ms per frame. The disposable visual-parity
gate also passes a natural seam view and both textured rims; its refreshed
natural-crossing windows sampled 426 Fabric frames and 428 NeoForge frames,
with one and zero frames over 50 ms respectively. Same-process
layout switching clears stale state, and the production lifecycle passes
Overworld/Nether/End transitions, save/disconnect, and reopen. NeoForge also
passes the production/multi-seed structure matrix, a complete unattended
headless atlas prewarm, and the dedicated two-client seam/combat/block/bed/
death/physical-portal/boat/teleport/reconnect matrix, including destination
water, hostile navigation through the seam, and normalized positive/negative
multi-lap Nether returns with both out-of-band Z directions. The 2026-08-10
alpha-4 integration branch also passes the complete strict matrix on both
loaders with the format-3 noise identity, bidirectional seam placement,
canonical double-chest ownership/recovery, and portal routing fixes present
together. Loader-labelled Fabric
and NeoForge client/server packages, strict jar verification, same-commit
shared-contract comparison, and a real packaged macOS NeoForge client smoke
also pass. The shared GUI-scale-4 atlas map/control fixture passes all eleven
captures and its ordered live-revision probe on both loaders. The shared
menu-only world-creation fixture also passes thirteen footer/editor/error/preset/
confirmation captures across GUI scales 1–4, including a 320×270 logical view,
on both loaders without creating a world. Real graphical Windows runs,
exact-candidate review, and owner release go/no-go are complete. The
expanded shared real-client map/compass fixture also passes on both loaders:
filled-map pixels and player/banner markers cross the seam in both directions,
world-added item frames exercise both sides, scale/lock and banner
removal/restoration pass, and a normal save/disconnect proves raw
client-session teardown before reopening and rechecking persistent state.
Spawn, lodestone, and recovery needles use the nearest periodic target; the
exact-target random-spin assertion reuses one wobble state so its seeded
comparison is deterministic.
Fresh and copied-1.21.11 dedicated servers launch with dimension-owned
storage. A real client completes resource/shader loading, a 100% atlas-backed
ring, tangent/radial captures, two natural wraps, and representative
gameplay/rim probes. The dedicated two-client seam/combat/stateful-block/bed/
death/physical-portal/boat/teleport/reconnect matrix also passes, including
the strict destination-water, hostile-navigation, ordinary survival
Nether-portal delay, and two-client seam thunder/lightning assertions. The
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
renderer architecture parents are closed. Optional convenience packaging has
passing macOS in-place, empty-data macOS first-run, dedicated-server, and real
Windows owner smokes. Owner gameplay, visual, Windows, and independent review
gates are complete; matched 1.0 artifacts are published. Broad
compatibility work remains post-release. See
`docs/CURRENT_STATE.md` and `docs/VISUAL_HANDOFF_REVIEW_2026-08-01.md`.
Exact Fabric and NeoForge 1.0 Release files were published to Modrinth on
2026-08-10 from tag `v1.0.0+mc26.1.2` / commit `f3a5ce1`. Matching CurseForge
Release files are submitted and may remain Baking or Under Review; see
`docs/CURSEFORGE_RELEASE.md`. Separate self-updating Fabric and NeoForge 1.0
Windows installers are available only on the unlisted showcase alpha page.
Broad third-party compatibility remains the post-release priority; see
`docs/DUAL_LOADER_STANDALONE_PLAN.md`.
The current dual-loader candidate's validated code baseline is public commit
`967759be872080a72e48bd26f7a97df9ee0a0302`; its exact jar/package hashes,
machine evidence, and remaining human gates are recorded in
`docs/DUAL_LOADER_RELEASE_CANDIDATE_2026-08-08.md`.
The post-gameplay #96 refresh has current Fabric and NeoForge safe-small
6/12/28 tangent, handoff, radial-up, and frame evidence. All NeoForge measured
views completed with zero frames over 50 ms. The exact production 16,384×256
centered projection and server-authoritative seam/both-rim gates also pass on
both loaders. The shared fresh-world chest/lectern/sign/bed/ender-chest/
shulker-box/banner/copper-golem/item/boat/cow/zombie curved-object subset also
passes on both loaders. NeoForge
must queue immutable settings immediately after the play-login packet; its
ordinary logged-in event is after the initial chunk-buffer flush. See
`docs/VISUAL_POLISH_CHECKPOINT_2026-08-02.md`.

For any later Modrinth or CurseForge build, use the fail-closed local staging procedure in
[`docs/MODRINTH_RELEASE.md`](docs/MODRINTH_RELEASE.md). It stages only the
runtime jar and records a checksum plus the exact clean, pushed public branch
revision. It never uploads or changes either listing. CurseForge-specific
manual metadata and dependency rules are in
[`docs/CURSEFORGE_RELEASE.md`](docs/CURSEFORGE_RELEASE.md).

## Codex weekly usage pause

Before substantial RingWorld work and after a long tool-heavy milestone, run:

```sh
python3 scripts/codex_usage_monitor.py
```

Above 5% remaining, `OK` permits normal operation. At exactly 5% remaining or
below, `PAUSE` means pause all RingWorld work: do not dispatch new tasks,
stop active work at the next safe handoff, and do not resume below the
threshold without explicit owner authorization. Do not infer the weekly
allowance from context tokens or a shorter quota window. The optional
five-minute macOS monitor and its non-secret status file are documented in
[`docs/CODEX_USAGE_MONITOR.md`](docs/CODEX_USAGE_MONITOR.md). The secondary
agent uses a separate account and must monitor its own allowance.

## What this project is

RingWorld is a dual-loader mod ported from Minecraft Java 1.21.11. Minecraft
26.1 is the development compatibility floor, while 26.1.2 remains the only
currently proven and published release. Fabric and NeoForge are published as
matched 1.0 builds for 26.1.2. Runtime, packaging, owner, and independent
release gates are complete; broad compatibility and the rolling-version
qualification automation remain.
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

## Minecraft version support policy

Minecraft 26.1 is the source/build compatibility floor. This is not permission
to advertise the current jar for 26.1 or 26.1.1: 26.1.2 remains the exact
verified and published runtime until the six-cell Fabric/NeoForge 26.1.x
matrix passes. A 26.1.x-wide release must use the exact same jar hash for every
tested patch release on a loader.

Every later stable Minecraft release enters the automated qualification
pipeline described in
[`docs/MINECRAFT_VERSION_SUPPORT_PLAN.md`](docs/MINECRAFT_VERSION_SUPPORT_PLAN.md).
Treat each new stable line as a porting project, pin every dependency, audit
all mixin and shader ABI, use disposable worlds, and require the appropriate
quick, nightly, and release gates before changing support metadata. Snapshots,
pre-releases, and release candidates are unsupported unless explicitly marked
experimental. Never rewrite historical 26.1.2 evidence as broader evidence.

## Loader support policy

Fabric and NeoForge are current supported loaders. NeoForge has full
graphical, dedicated-server, topology, worldgen, atlas, storage, multiplayer,
and local packaging parity and is published at 1.0. Future
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
- `config/minecraft-version-matrix.json`: pinned Fabric/NeoForge qualification
  cells and publication evidence for the Minecraft 26.1.x support line.
- `scripts/validate_minecraft_version_matrix.py`: fail-closed pure validation
  for version inputs, isolated profiles, states, evidence, and same-jar claims.
- `scripts/run_minecraft_qualification.py` and
  `scripts/minecraft_qualification_model.py`: fail-closed serial Phase 3
  runner/model seam. Dry-run is write-free and `INCOMPLETE`; non-dry runs the
  isolated build/unit, per-cell diagnostic-artifact, and full-loader-triplet
  frozen-candidate adapters. The diagnostic artifact adapter accepts one
  direct runtime jar plus only its canonical Gradle sources sibling below
  `<cell>/build/<loader>/libs`, strict-checks loader/canonical-MPL/build identity, and
  records SHA-256. Frozen preparation builds once from the oldest ABI under the
  run root, retains/re-inspects one MPL-covered candidate, and records one
  path/hash for all three loader cells. It is not external-runtime
  qualification. Once clean provenance and a complete frozen loader triplet
  exist, the default runner also installs the external dedicated-smoke bridge,
  lends its held cell lock, and stores a separately schema-validated immutable
  `strict-terminal-evidence.json` before a runtime phase can pass. Partial
  selections stay `INCOMPLETE` without runtime I/O. A failed complete-triplet
  frozen preflight aborts per-cell diagnostics before Gradle work but still
  writes immutable reports with the shared-contract failure and explicit
  aborted phases; partial-triplet diagnostics remain unchanged. The optional
  `--gradle-dependency-cache` is only for a worker-provisioned, already-existing
  external read-only dependency cache. It must be absolute, non-symlinked, and
  outside the checkout, qualification state, and home; the runner passes it
  only as `GRADLE_RO_DEP_CACHE` while retaining each cell's disposable
  `GRADLE_USER_HOME`. It is non-authoritative acceleration, never offline
  qualification or support evidence. Provision it from a compatible Gradle
  `caches/modules-2` tree without lock/cleanup files and do not mutate it while
  a qualification run reads it; the runner rechecks the path at every Gradle
  command boundary but does not trust its contents.
  The isolated wrapper download remains separate and must match the official
  Gradle 9.5.1 binary ZIP SHA-256 pinned in `gradle-wrapper.properties`. A
  worker may provide that exact external ZIP with
  `--gradle-distribution-zip`; the runner rehashes it and exclusively copies it
  into each fresh cell's wrapper store without creating Gradle's `.ok` marker
  or bypassing wrapper verification.
  Current-main combined quick run `20260823T130347Z-a493af8d7261` passes all six
  Fabric/NeoForge 26.1.x cells. Fabric uses one unchanged frozen jar
  (`1fc017289ebcb102d9894ccb16a30a697e03104b5c8165b6799a1496c4486216`)
  and NeoForge uses one unchanged frozen jar
  (`5fd60d12db03386866cc153b7921b180cd1e4a96d4f443443364d04357b56823`).
  These are dedicated-server quick evidence only. Earlier six-cell worldgen
  and interrupted-Atlas-recovery nightly slices also pass with their
  then-current exact frozen jars.
  The six-cell creation/settings UI source-ABI slice passes thirteen captures
  per cell from clean commit `0776154`; it deliberately is not
  frozen-candidate or production-launcher evidence. The matching six-cell
  Atlas UI/client-handshake slice also passes: each patch-specific client
  accepts format 3/mapping 4, produces all eleven captures, and proves normal
  disconnect plus complete session teardown. The remaining frozen-candidate
  gameplay, lifecycle, and rendering matrix remains pending.
- `scripts/run_gradle_map_compass_qualification.py`: bounded source-ABI
  gameplay wrapper for the existing map/compass fixture. It requires eight
  captures, both seam directions, persistent map/banner/item-frame state, all
  compass targets, normal disconnect/session clear, and reopen. It does not
  claim production-launcher or frozen-candidate evidence. The six-cell matrix
  passes from clean commit `9015857`; exact run IDs and hashes are in
  `docs/TESTING.md`.
- `scripts/run_gradle_curved_objects_qualification.py`: bounded source-ABI
  wrapper for the existing curved block/entity fixture. It requires one
  disposable world and valid verified far/near captures. It is not frozen-jar
  or production-launcher evidence. All six cells pass from clean commit
  `f9cb4c2`; exact records are in `docs/TESTING.md`.
- `scripts/run_gradle_multiplayer_qualification.py`: exact frozen-candidate
  Phase 4 wrapper for the existing dedicated server plus two real graphical
  clients. It selects one quick-qualified cell, removes checkout classes from
  the loader development classpath, rehashes the retained jar installed in
  all three isolated `mods/` directories, warms assets serially, drives the
  seam/gameplay matrix, stops the server normally, and hashes logs/captures.
  This is exact-patch frozen-jar evidence but not a packaged-launcher claim.
  On an offline worker, `--gradle-loom-cache` may point at an external seed
  containing Mojang's version manifest, the selected patch's version JSON and
  client/server jars, and that version's asset index/objects. The runner
  independently verifies the manifest-bound SHA-1 and size of every copied
  file; it never exposes the operator's normal
  Gradle home. Server shutdown uses authenticated RCON enabled only inside the
  disposable runtime because NeoForge's Gradle launcher does not forward
  standard input to the game process.
  The formal six-cell matrix passes from clean commit `351056c`; exact run IDs
  and terminal hashes are recorded in `docs/TESTING.md`.
- `scripts/run_gradle_raid_qualification.py`: exact frozen-candidate Phase 4
  runner for the two-phase real raid fixture. It launches a dedicated server
  and two graphical clients for arm/save and reload/victory, preserves only
  the disposable world between phases, verifies canonical seam navigation,
  raid/bossbar persistence and client patch identity, and writes hash-bound
  fixture-07 evidence. Run one graphical cell at a time. The formal six-cell
  matrix passes from clean commit `daaa1da`; exact run IDs and terminal hashes
  are recorded in `docs/TESTING.md`.
- `scripts/run_gradle_production_lifecycle_qualification.py`: exact
  frozen-candidate Phase 4 runner for one independently inventoried complete
  16,384x256 format-3/mapping-4 source world. It copies rather than mutates the
  source, launches one real integrated client with checkout classes excluded,
  and verifies Overworld/Nether/End transfers, normal save/disconnect, raw
  client-state teardown, and same-world reopen. Run one graphical cell at a
  time. The formal six-cell matrix passes from clean pushed runner commit
  `d9ae051`; exact run IDs and terminal hashes are recorded in
  `docs/TESTING.md`. A range-wide source must be saved by the oldest target
  patch (26.1); the runner rejects later-save-to-earlier-runtime downgrade
  attempts before copying.
- `scripts/run_gradle_production_render_qualification.py`: exact
  frozen-candidate Phase 4 runner for the complete production Atlas/render
  slice. It inventories one reviewed 16,384x256 source, opens only fresh
  disposable copies, captures tangent/handoff/radial projection at noon,
  dusk, night, and rain, then runs natural seam/both-rim visual parity and
  records all frame logs and PNG hashes. Run one graphical cell at a time.
  The formal six-cell matrix passes from clean pushed runner commit `425cbcf`;
  exact run IDs and terminal hashes are recorded in `docs/TESTING.md`.
- `scripts/run_minecraft_nightly_matrix.py`: dry-run-first Phase 4 coordinator
  for the fixed ordered fixture sequence. With `--execute` it invokes each
  existing isolated operator serially, stops later fixtures in a failed cell,
  continues other cells, and exclusive-creates one aggregate report. A
  partial cell/fixture selection is always `INCOMPLETE`, never a matrix PASS.
  Its production-world input must already be below `dist/qualification`.
  It forwards a recorded 120-second settle interval to each multiplayer
  runner after isolated Gradle preparation/assets and to each raid runner
  both before its arm runtime and between its arm/reload runtimes. Do not move
  these waits ahead of child preparation: retained aggregate diagnostics
  proved that placement does not protect the strict readiness barrier or a
  freshly restarted two-client fixture from sustained host load. After each
  independently verified child result it deletes that
  child's disposable `gradle-home`, `cache`, `build`, and `run` directories;
  logs, captures, and immutable evidence are retained. External worldgen and
  Atlas runners intentionally emit `terminal_evidence` rather than `run_id`;
  cleanup must derive their run ID only from the already-contained canonical
  `<version>/<loader>/<run>/<cell>/evidence/nightly/...` terminal path. Never
  skip those external runtimes or trust an arbitrary path for cleanup. Before
  deleting a child `run` tree, copy every terminal-hash-bound PNG and log below
  that tree into `evidence/retained-artifacts`, rehash it, and record the
  retained path in the aggregate. Do not claim captures survive cleanup merely
  because their former paths and hashes remain in a terminal record.
- The Fabric and NeoForge multiplayer/raid qualification server and client
  runs cap each game JVM at 2 GiB (`-Xms256m -Xmx2g`). Keep those caps
  loader-symmetric: two default 4 GiB client heaps plus a server can exhaust a
  16 GiB qualification host. The multiplayer operator must also fail as soon
  as its server exits instead of leaving disconnected graphical clients alive
  until the outer timeout.
- `scripts/minecraft_qualification_executor.py`: stdlib-only execution
  primitives for held cell locks, contained directories, bounded
  credential-pattern-redacted subprocess logs, process-group timeout cleanup,
  immutable reports, pinned hashes, and strict diagnostic jar inspection. It
  supplies the runner's build and strict diagnostic-artifact primitives.
  Isolated Gradle builds use one worker so dependency resolution does not fan
  out across simultaneous connections on constrained hosts. Its
  external runtime API may reuse only a live runner-supplied lock with the
  exact path and run ID; standalone calls still acquire their own lock.
- `scripts/external_runtime_smoke.py`: pure production-style dedicated-server
  plan for the pinned Mojang server, official installer, exact mods inventory,
  safe-small config, launch, markers, and clean-stop contract. It performs no
  I/O or execution.
- `scripts/external_runtime_qualification_adapter.py`: structural bridge from
  frozen/provenance runner inputs to external dedicated smoke and strict
  terminal evidence. It must validate and exclusive-create the raw strict JSON
  record before returning a `PASS` phase, and must re-inspect the retained
  frozen jar before any installer, download, or runtime activity. Preserve the
  Mojang server's declared hash algorithm (currently SHA-1) for pin validation;
  the separate installed-file inventory remains SHA-256. Bind the installer
  download by the reviewed command path/checksum, then carry that reviewed
  loader-specific display name into terminal validation rather than requiring
  one hard-coded label. The terminal schema must independently bind that name,
  URL, checksum algorithm, and checksum value to the canonical manifest cell.
- `scripts/minecraft_nightly_qualification_model.py`: pure Phase 4 contract
  for reusing existing fixtures under one qualification cell. It names no
  executable and must not gain I/O, Gradle, or runtime behavior; a later
  executor must recheck immutable candidate/quick-evidence/production-world
  inputs and exclusive-create every output. The creation item covers settings
  UI only; lifecycle and production rendering both require the immutable
  production-world input. A server fixture owns `runtime/world`, and only
  Atlas recovery may restart its own newly created world.
- `scripts/minecraft_atlas_recovery_qualification.py`: pure Phase 4 evidence
  contract for one clean interrupted headless-Atlas run followed by a clean
  complete restart of that exact disposable world. It models the Java
  schema-2 report exactly and binds it to independent persisted-settings and
  Atlas-file observations; it performs no I/O or runtime work.
- `scripts/minecraft_atlas_recovery_persistence.py`: bounded independent
  gzip/NBT and Atlas-v6 byte parsers for that recovery evidence. It derives
  the Java-compatible unsigned layout/Atlas identities from decoded settings,
  rejects malformed/trailing payloads, and counts durable cells/chunks from
  the Atlas presence map rather than trusting report JSON.
- `scripts/external_runtime_atlas_recovery_plan.py`,
  `scripts/external_runtime_atlas_recovery_executor.py`, and
  `scripts/external_runtime_atlas_stage_runner.py`: the first concrete Phase 4
  external fixture. They bind a validated quick terminal record and retained
  frozen candidate, wait for an independently parsed partial Atlas before a
  normal stop, and require the same checkpoint to advance to a self-halting
  complete restart. Every capture and process log is hash-addressed. Static
  tests use fake local children; they are not a real Minecraft nightly PASS.
- `scripts/run_atlas_recovery_qualification.py`: clean-source operator CLI for
  that first nightly slice. It accepts one canonical cell and one prior quick
  run ID, revalidates the prior strict PASS record and frozen jar, requires the
  current public branch to be clean/pushed under Java 25, and records that
  executor-source provenance before launching the concrete two-stage runner.
  Real Fabric and NeoForge runs now pass all six 26.1.x cells with the
  refreshed frozen jars; retained run IDs and terminal-evidence hashes are in
  `docs/CURRENT_STATE.md`. This qualifies Atlas recovery only, not the rest of
  the Phase 4 nightly matrix.
- `scripts/run_gradle_creation_ui_qualification.py`: pinned source-ABI
  graphical client runner for the existing thirteen-capture creation/settings
  fixture. It uses isolated qualification Gradle, build, and game state,
  selects exact manifest dependencies, and writes immutable log/screenshot
  evidence. It deliberately records `production_launcher=false` and
  `frozen_candidate_jar=false`: official Prism requires a valid account before
  a fresh profile reaches its offline launch path. Packaged-client proof stays
  an authenticated or human release gate; never copy a user's normal Prism
  account data into qualification state.
  Every qualification Gradle command also supplies a cell-contained
  `--project-cache-dir`; an isolated `GRADLE_USER_HOME` alone is insufficient
  because Loom otherwise writes launch configuration into the checkout's
  shared `.gradle` directory.
  It accepts the quick runner's optional reviewed external dependency cache
  and exact wrapper ZIP seed; both are acceleration only and never support
  evidence.
- `scripts/run_gradle_atlas_ui_qualification.py`: matching source-ABI
  graphical runner for nightly fixture 04. It launches the existing integrated
  Atlas map/control workflow with exact cell dependencies, requires its eleven
  captures, one disposable world, complete Atlas, and revisioned placement/
  removal PASS, and records the same explicit non-production/non-frozen
  claims. Run only one graphical cell at a time.
  Each loader run must supply `ringworld.atlasUiExpectedBuildLabel` from its
  selected Gradle release label and artifact version. Do not restore a
  hard-coded published label: qualification builds deliberately use a
  diagnostic identity, and the fixture must independently verify it.
  The fixture also owns `pauseOnLostFocus=false`; its final revision probe
  needs the unattended integrated server to keep ticking after the map closes.
  Its disposable world is the supported Small 2,048×128 preset. Do not widen
  it merely to duplicate worldgen/recovery coverage; this fixture owns Atlas
  controls, progressive rendering, completion, and live revision behavior.
  The six-cell 26.1.x Fabric/NeoForge source-ABI matrix passes from clean
  commit `7a7c044`; exact run IDs and terminal hashes are recorded in
  `docs/TESTING.md`. This closes fixture 04 only and is not packaged-client or
  frozen-candidate evidence.
  The runner now also emits a distinct fixture-05 terminal record after an
  accepted format-3/mapping-4 handshake and normal disconnect/session clear.
  Do not count the earlier fixture-04 records as fixture-05 evidence; the
  expanded six-cell matrix still needs a clean-revision rerun.
  Fabric must keep the Atlas fixture exclusive after it invokes Create World;
  before the integrated player exists, the generic `testMode` launcher must
  not race it by creating a second automated world.
- `scripts/minecraft_worldgen_qualification.py`,
  `scripts/external_runtime_worldgen_plan.py`,
  `scripts/external_runtime_worldgen_executor.py`, and
  `scripts/external_runtime_worldgen_stage_runner.py`: the second concrete
  Phase 4 external fixture. It installs three disposable official runtimes,
  runs fresh/reload production worldgen plus seam-crossing and terminal-policy
  seeds, independently parses dimension-owned settings and the existing
  worldgen matrix record, and requires all biome families, structures, caves,
  ores, loot, seam-crossing starts, monument outcomes, mapping 4, and format 3.
  The stage process must self-halt; the runner never sends `stop`.
- `scripts/run_worldgen_qualification.py`: clean-source operator CLI for that
  four-stage worldgen fixture. It consumes a passed quick run and retained
  frozen jar exactly like the Atlas CLI. Patch-cell evidence remains in the
  selected patch directory, while both nightly CLIs locate exactly one shared
  loader candidate below the reviewed quick run's oldest-ABI cell and reject
  zero or duplicate candidates. Stage-process failures write terminal `FAIL`
  evidence rather than escaping as an unclassified traceback. Real Fabric and
  NeoForge worldgen runs pass all six 26.1.x cells with one unchanged jar per
  loader.
- `scripts/minecraft_world_upgrade_qualification.py` and
  `scripts/run_world_upgrade_qualification.py`: Phase 5's fail-closed
  copied-world forward-upgrade contract and operator CLI. They accept only
  `26.1 -> 26.1.1`, `26.1 -> 26.1.2`, or `26.1.1 -> 26.1.2` within one loader;
  revalidate source worldgen and target quick evidence; and never operate on
  the source world. The implementation, static workflow, and all six real
  same-loader forward paths pass; see `docs/MINECRAFT_VERSION_SUPPORT_PLAN.md`.
- `scripts/release_candidate_equivalence.py`: Phase 6's local, semantic
  release-staging guard. It proves a proposed public loader jar differs from
  the frozen wide-range candidate only in its reviewed loader descriptor and
  `ringworld-build.properties` public version/label fields. It is static
  release-equivalence evidence, never runtime qualification. Local
  `1.1.0+mc26.1` candidates now pass this guard against the refreshed combined
  quick candidates; they remain ignored, unstaged, and unpublished.
- `scripts/stage_qualified_release.py`: Phase 7's no-upload staging bridge.
  It consumes a reviewed six-cell quick run and the two equivalent public
  candidates, then revalidates every strict record and emits one ignored
  review directory per loader with the runtime jar, checksums, archive
  inventory, exact source route, host metadata, changelog, and rollback data.
  It has no token, upload, tag, deployment, or listing-mutation interface.
- `scripts/publish_qualified_release.py`: the dry-run-first Phase 8/9 host
  bridge. It plans one exact Modrinth or CurseForge multipart submission from
  the qualified stage without reading credentials. Execution needs an exact
  short-lived owner authorization, clean pushed source equality, and the
  host-specific token environment variable. It can create only a new unlisted
  or manually held file and has no update, delete, archive, promotion, tag,
  server, or deployment path.
- `scripts/external_graphical_creation_ui.py` and
  `scripts/run_creation_ui_qualification.py`: the first production-style
  graphical-client automation slice. It consumes the retained frozen jar and
  selected cell's strict quick evidence, verifies the reviewed Prism 11.0.3
  macOS archive, creates a fresh account-free/offline Prism root below fixture
  01, installs only RingWorld plus Fabric API where required, and drives the
  existing self-halting thirteen-capture creation-settings UI fixture. It
  requires Java 25, bounded launcher/client logs, valid PNGs, exact mod hashes,
  zero created worlds, and immutable terminal evidence. Pure/fake-process
  tests are not a real graphical PASS; run the CLI only on an available GUI
  host and never point it at a normal Prism directory.
- `scripts/external_runtime_executor.py`: isolated external-server executor for
  exact pinned downloads, official installer runs, installed Mojang-server
  identity, exact mod copies, port and marker checks, ordered stop/save/exit
  observations, and immutable local results. The default runner reaches it
  only for a clean, complete loader triplet and it is never a release
  publisher. It creates the already-validated empty runtime root before the
  official installer; Fabric Installer rejects a missing target directory.
- `scripts/minecraft_qualification_ranges.py`: strict pure parser for the
  reviewed qualification-only Fabric and NeoForge 26.1.x metadata ranges.
- `scripts/minecraft_frozen_candidate.py`: strict oldest-ABI candidate and
  same-file hash inspection. Passing it is declaration/identity evidence, not
  runtime support.
- `scripts/minecraft_qualification_evidence.py`: fail-closed terminal evidence
  schema. A cell cannot claim `PASS` without provenance, command and log
  hashes, installer/runtime inventory, frozen candidate identity, ordered
  runtime markers, clean exit, and same-file group evidence. Its pure external
  adapter cannot manufacture missing evidence from the executor's compact
  result. Safe relative evidence paths permit `+` in versioned artifact names
  while continuing to reject absolute paths and traversal.
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
- `docs/MINECRAFT_VERSION_SUPPORT_PLAN.md`: approved Minecraft 26.1 floor,
  26.1.x qualification matrix, rolling stable-version automation, release
  staging, and Modrinth/CurseForge publication plan.
- `docs/MINECRAFT_1_21_11_FINAL_BASELINE.md`: immutable pre-port validation,
  hashes, performance evidence, and protected rollback inventory.
- `docs/MINECRAFT_26_1_COMPILER_BASELINE.md`: historical Java 25/26.1.2
  compiler inventory and the subsequent green build/server checkpoint.
- `dist/`, `run/`, `run-multiplayer/`, `run-atlas-ui/`, `run-creation-ui/`, `run-map-compass-capture/`, `run-headless-prewarm/`,
  `run-raid-seam/`, `logs/`, `.gradle/`, and `build/`:
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
`build/libs/ringworld-1.0.0+mc26.1.2.jar`; the current suite contains 338
unit/parameterized cases. A green source build and dedicated-server launch are
not a release gate: required client, rendering, gameplay, multiplayer,
packaging, and staging checks must remain green together.

The NeoForge module uses the same Java 25 toolchain and also passes all 338
unit/parameterized cases:

```sh
./gradlew :neoforge:test :neoforge:build --console=plain
```

Version-matrix work may opt into a disposable Gradle cell with paired
`ringQualificationRoot` and `ringQualificationCell` properties. Both loaders'
build outputs, declared runs, preparers, and verifiers then resolve below that
cell, and `ringQualificationPort` supplies its smoke/multiplayer default. The
root is accepted only below `dist/qualification`. Supplying only one property,
using traversal, or pointing at ordinary development/package state must fail.
With neither property, all historical paths and ports remain unchanged. This
is build isolation, not cross-version proof; a same-jar claim requires one
frozen jar built once against the oldest ABI and then exercised in external
production-style runtimes. The runner's `SHARED_CONTRACT` preflight records
that one frozen path/hash only when a complete three-version loader triplet is
selected; it never synthesizes the claim from per-cell source builds.
Qualification source-build artifacts must use the diagnostic
`0.0.0-qualification+mc<version>` identity and a qualification release label;
never stage or publish them. The 26.1 and 26.1.1 Fabric and NeoForge beta
source-build cells currently pass 338 tests each, and their frozen-jar quick,
worldgen, and Atlas-recovery gates pass; they remain pending in public support
metadata until the rest of the nightly matrix passes.
An operator may opt into an external worker-provisioned read-only dependency
cache with `--gradle-dependency-cache /absolute/path`; it is rejected if it is
missing, symlinked, or overlaps the checkout, `dist/`, per-cell build/run
state, or the home directory. The cache is passed only as
`GRADLE_RO_DEP_CACHE`; each cell still owns `GRADLE_USER_HOME`, and the runner
never adds Gradle offline mode.
External runtime assembly must use the exact SHA-256-pinned installer in each
manifest cell: Fabric Installer for Fabric and the matching NeoForge installer
for NeoForge. Never count a Gradle development run, Fabric Loader jar, or
NeoForge universal jar as production-style server evidence.

Both loaders now provide several identically named runtime tasks. Always
select the loader explicitly—for example `./gradlew :runServer` or
`:runHeadlessPrewarmServer` for Fabric and `./gradlew :neoforge:runServer` or
`:neoforge:runHeadlessPrewarmServer` for NeoForge. An unqualified task name is
matched in both projects and, with parallel Gradle execution, can launch both
fixtures at once. The NeoForge dedicated launch
has reached `Done` and observed atlas progress. `./gradlew
:neoforge:runProductionProjectionClient -PringNeoForgeProjectionSource="NeoForge Test"`
copies the named ignored source save into an isolated run, waits for atlas
completion, produces tangent/handoff/radial diagnostics, verifies them, and
exits. Noon, dusk, night, and rain pass. The qualified NeoForge visual-parity,
layout-switch, production-lifecycle, stronghold/worldgen, headless-prewarm,
and dedicated two-client gates also pass on isolated fixtures. Local
dual-loader packaging and the packaged macOS NeoForge client smoke also pass;
a qualified `:neoforge:runAtlasUiClient` additionally verifies the shared
pause-menu atlas workflow and all eleven screenshots. Release publication is
tracked under #97; #12, #13, #95, and #96 are complete.

`scripts/stage_modrinth_release.py --loader both --build` checks the active
Java generation, always performs a fresh dual build, pair-validates the known
outputs, writes provenance manifests consumed by optional packaging, and
renders the exact verified public commit URL into every staged public
`PROJECT_DESCRIPTION.md` and `CHANGELOG.md`. It accepts no alternate jar path.
It also verifies the embedded `ringworld-build.properties` identity displayed
by the RingWorld Map, so `release_label` must advance with both loader release
descriptors.
The current release metadata separates the shared runtime artifact version
from loader-specific public 1.0 identifiers; never relabel a generic
artifact as a new hosted file outside this fail-closed path.
Keep that fail-closed Java 25 preflight and source-link placeholder validation
synchronized with the active Minecraft toolchain; do not replace its direct
setup error with the compiler failure produced by an older Gradle JVM. The
ignored manifest is not an acceptable sole corresponding-source route.

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
Keep public GitHub, showcase-site, Modrinth, and CurseForge explanations of
Atlas generation aligned: playable real chunks appear first, the incomplete
ring uses a fogged progressive placeholder, revisions are resumable and
cross-faded, and only verified completion removes the fallback and enables the
detailed mesh. Never promise one fixed generation time.

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
26.1.2. A Minecraft, mappings, Loader, Loom, ModDevGradle, or API upgrade is a
porting project: follow the version-support intake, audit every injection
target and shader ABI, and run the required matrix rather than only changing
version numbers.

## Current implementation cautions

- The complete-ring renderer accepts a current-world zero-cell or partial Atlas
  as soon as its identity metadata arrives. Missing cells use an opaque,
  deterministic world-hash fallback with smooth generated-terrain palette propagation;
  progressive updates use source-resolution textures, one reference-height
  mesh, and curved temporary returns at both inner rim faces,
  then verified completion performs one upgrade to the expanded texture and
  detailed terrain-height mesh. The partial ring starts under an 0.88-strength
  progress haze which reaches exactly zero at completion, and the temporary
  returns use their out-of-range V marker to select cobble/moss shading rather
  than sampling the terrain palette. Later complete-atlas revisions refresh the
  texture, but rebuild that detailed mesh only when the immutable build
  snapshot's surface-height fingerprint changes. Session disconnect/settings
  handlers must still clear the static GPU texture and mesh, and the renderer
  must reject absent, corrupt, or wrong-world atlases. Completion must remove
  every fallback pixel and temporary return. Each new incomplete texture keeps
  the prior GPU texture for a 750 ms shader cross-fade; session teardown must
  close both textures.
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
- Terrain-noise mapping is a persisted worldgen identity. Saved settings
  formats 1 and 2 must upgrade to format 3 with `LEGACY_AXIAL`; only a fresh
  format-3 world may select a persisted annular mapping. Mapping 2 preserves
  the first annular implementation for existing saves, and mapping 3 retains
  its exact alpha-4 terrain. Fresh worlds use `ANNULAR_COMPLETE_V2` (4), which
  additionally transforms vanilla's direct `BlendedNoise` sampler; omitting
  that leaf produced the reproducible X=16383/0 wall in an uploaded mapping-3
  save. It also maps vanilla surface-rule, badlands, frozen-ocean, and
  carver-seed coordinates. The annular transform uses
  `(R+Z)sin(theta),(R+Z)cos(theta)` and removes the legacy quarter-ring
  Jacobian collapse. Keep mapping selection identical across biome, density,
  cave/aquifer/ore, and base-height paths; include it in the settings
  handshake, layout fingerprint, generator cache key, and atlas world hash.
  Never silently migrate an existing world's mapping or accept an atlas from
  the other mapping.
- Curved clouds are fragment-clipped in intrinsic Z at the two inner rim-face
  planes published in `RingWorldAtmosphere2.zw`. Those planes currently derive
  from `RingGenerationBoundary.RIM_THICKNESS`; future wall-style settings must
  change the shared bound calculation rather than hard-code shader coordinates.
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
  Snapshot capture assigns the explicit no-detail height-fingerprint sentinel;
  only the existing texture worker may scan a complete atlas to resolve the
  detailed-mesh fingerprint. Partial builds must return the sentinel without
  scanning heights.
  Session clear must invalidate the build generation and close both completed
  and abandoned images; do not sample the mutable live atlas on that worker.
- `GuiMixin` owns the small top-left incomplete-Atlas progress label. It derives
  whole-percent progress from the current client Atlas, never from server job
  state, obeys Hide GUI, and must render nothing once the Atlas is complete.
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
- Minecraft 26.1.2's `ServerChunkCache.getChunkFuture` blocks through
  `managedBlock` when entered on the server thread. Atlas generation must use
  `RingAtlasChunkRequest` with `addTicketAndLoadWithRadius`: retain its unique,
  non-persistent loading ticket until the completed chunk is sampled on a
  normal service tick, then release it. Shutdown and level-unload callbacks
  must instead cancel/release an outstanding request without resolving its
  loaded-result supplier: the chunk cache may already have evicted that result.
  Leave the selected cursor unadvanced and checkpoint only captured atlas
  cells, so resume safely retries any missing chunk without logging a false
  runtime failure. Do not restore a direct end-tick
  `getChunkFuture(..., FULL, true)` call or the rejected worker-entry
  workaround; its one-tick vanilla ticket can expire before FULL.
- A terminal atlas job is replaceable only after it owns no outstanding
  `RingAtlasChunkRequest`. An ordinary consume-side ticket-release failure
  deliberately leaves the request attached so `consumeFuture` can retry
  `close()` on later ticks. `/ringworld atlas start`, the map control, and any
  other `pregenerate` caller must fail with actionable feedback while that
  retry is pending; never install a replacement job and orphan its ticket.
- `-Dringworld.headlessPrewarm=true` is an explicit dedicated-server-only
  adapter mode. It suppresses normal background autostart, safely replaces
  only the unstarted config-disabled `IDLE` handle, rejects joins, and waits
  for the service's verified atlas completion before normal world save, atomic
  JSON result write, and halt. SIGTERM writes `INTERRUPTED` after checkpoint;
  the Gradle wrapper, not Minecraft's process exit code, converts a non-
  `COMPLETE` terminal report to failure. Use only `run-headless-prewarm/` or a
  separately prepared disposable runtime directory; never open a copy source.
- Fabric `ServerPlayConnectionEvents.JOIN` listeners are array-backed and keep
  running after an earlier listener disconnects a rejected headless-prewarm
  join. `FabricRingWorldServer` owns that rejection and its message;
  `RingWorldNetworking` must independently recheck headless admission and
  return before settings, handshake, or atlas work. Do not duplicate the
  disconnect or assume it short-circuits later JOIN callbacks.
- NeoForge headless admission is owned by `NeoForgeHeadlessPlayerAdmission`
  at the cancellable `PlayerList.placeNewPlayer` method head, before vanilla
  creates the play listener or buffers its first packet. The rejection uses
  the existing configuration listener for its disconnect reason. It must
  never reach the later settings send, handshake tracker, atlas metadata, or
  ordinary logged-in event. Keep the event check only as a defensive fallback;
  moving rejection back to `PlayerLoggedInEvent` leaks the initial RingWorld
  protocol sequence before the join is denied.
- An explicit headless launch can reject an ordinary copied Overworld before
  `ServerLevelEvents.LOAD`, while `ServerLevel` attaches its tick schedulers.
  That narrow constructor-tail bridge writes `REJECTED` evidence with
  unavailable identity sentinels, then rethrows the unchanged settings error.
  Do not let the invalid world continue with bootstrap geometry or add hot-path
  topology exceptions to mask this intentional rejection.
- Initial spawn selection is the one creation-time bootstrap geometry use: it
  runs before an Overworld has saved settings and delegates finite-Z clamping
  plus final saved-X canonicalization to loader-neutral `RingSpawnBounds`.
  Vanilla's post-sampler safe-spawn spiral can cross either seam, so normalize
  every `RespawnData` result at that final ownership boundary, not merely the
  sampler suggestion. Keep it scoped to first-world creation; saved-world
  runtime logic must never read bootstrap geometry.
- Runtime block entities are owned by canonical server positions even when a
  neighbour traversal reaches them through X=`-1` or X=`C`. Keep
  `LevelChunkMixin` server-Overworld-only: client chunks deliberately key
  block entities by presentation coordinates. Canonicalize the `LevelChunk`
  block-state and block-entity map arguments before vanilla creates, reads,
  removes, or serializes an entry; do not patch individual double-container
  blocks. Saved chunk post-load must retain a raw periodic alias until the
  canonical/alias collision decision is made: a lone alias is repaired to its
  canonical owner, while two distinct inventories remain independently
  recoverable and emit an operator warning. Make this decision only after all
  direct saved entries are known, and reserve a canonical pending-NBT key
  before promoting an alias. Save and removal lookups must preserve both live
  and still-packed exact aliases; serialized entry order must never choose
  which inventory survives. Never silently merge or delete either inventory.
- Nether-to-Overworld portal routing normalizes the already vanilla-scaled
  target at `PortalForcer`: X wraps onto canonical storage, Z clamps to a
  portal-safe creation anchor, and lookup queries the adjacent X images before
  creation. Do not move this correction to the final entity transition; that
  would still search or create at a raw multi-lap/exterior coordinate. Keep
  Nether and End `PortalForcer` behavior vanilla.
- The world-creation editor must retain its unsaved field and monument draft
  across Minecraft `init()` rebuilds, including GUI-scale and window-size
  changes. Its automated fixture must wait for each asynchronous screenshot
  write callback before mutating widgets or resizing again; otherwise a valid
  capture can contain glyphs from two UI states. Keep the 320-by-270 Large
  preset capture because it exercises the longest live metric and warning
  lines at the supported compact size.
- `ring_surface.vsh` deliberately clamps only far-out proxy clip-space Z while
  preserving X/Y/W. Minecraft's level far plane is derived from chunk render
  distance and clips most of a production 16,384-block cylinder, especially
  in the tangent/along-ring view. Do not remove this as redundant sky code or
  replace it by globally increasing render distance/far depth. Re-run both the
  tangent and radial-up projection captures after changing projection,
  celestial render order, or the proxy pipeline.
- Settings payload identifiers are wire-layout-versioned
  (`settings_v3`/`settings_ack_v3`). Never append or reorder codec fields while
  reusing an old identifier; old clients crash on unread bytes before a useful
  rejection can be sent. Advance the channel generation and keep the
  `RingProtocolIdentityTest` expectation synchronized.
- `ClientPlayNetworkHandlerMixin` `HEAD` packet modifiers execute before
  vanilla's packet-thread guard. They must return the original packet when the
  client is off-thread and let the queued game-thread replay perform chart
  projection. Never read mutable player/level/chart state from that first
  network-thread invocation.
- The mandatory play handshake is exact-version and exact-required-channel,
  not feature-bit negotiation. `RingHandshakeTracker` gives each join 300
  ticks to acknowledge, gates every RingWorld request, treats duplicate
  acknowledgement idempotently, and clears on disconnect. Keep its state
  loader-neutral and server-thread-owned; never let an atlas request bypass it.
  NeoForge payload entrypoints must call `IPayloadContext.enqueueWork` before
  they inspect players, mutate handshake/atlas/session state, disconnect, or
  send a response; the main-thread registrar is an optimisation, not this
  ownership boundary.
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
  builder emits no web content and has no publish/deploy path. The staging
  tool alone renders public release text: both description and changelog
  templates must carry exactly one source-link placeholder, never a hard-coded
  GitHub commit/tree/blob URL or short/full SHA. Keep reproducible ZIPs and
  checksum manifests under ignored local staging only.
- `/ringworld atlas status|start|pause|resume` controls background pregeneration.
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
- Raid support keeps saved raid centres, POIs, wave positions, and chunks
  canonical while projecting only transient queries and path targets to their
  nearest periodic images. `ServerWorldMixin`, `RaidsMixin`, `RaidMixin`,
  `PathfindToRaidGoalMixin`, and `RaiderMoveThroughVillageGoalMixin` form one
  contract; keep `RaidsAccessor` read-only and update them together. The
  opt-in two-phase `RingWorldRaidSeamTest` saves a real first wave, then
  restarts to prove restored state, natural raider folding, victory, and Hero
  of the Village. The 2026-08-02 Fabric and NeoForge runs completed both
  phases with `[raid-seam] PASS`; keep those two-loader results as the minimum
  evidence when changing raid or POI behavior.
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
  keep the terrain-adjusted bounds inside canonical X and finite Z. At the
  supported 128-block minimum width, the full optional graph can be wider
  than finite Z; fit the inflated portal-room bounds instead, preserve the
  graph translation, and allow only optional branches to meet the rims. Never
  restore the former exception that crashed chunk generation, and keep the
  creation editor's visible Small-is-experimental/mining advisory. Keep the policy
  flag `volatile`: generation runs on worker threads. Missing-policy legacy
  worlds must remain untouched.
- The extended Globals UBO publishes the complete format-3 layout and render
  profile. Its std140 field order, `GlobalSettings` allocation, and every
  custom program that declares Globals must change together.
- The dedicated multiplayer clients must not connect before
  `Minecraft.isGameLoadFinished()`. Joining during the initial resource
  reload can run leaf display ticks against unprepared particle sprites.
- `:runLayoutSwitchClient` opens two existing saves in one JVM and stops after
  logging its result. Its default `different-layout` expectation checks a
  geometry change; `same-geometry-different-seed` additionally requires two
  complete, distinct atlas identities/content fingerprints and raw GPU/session
  teardown between same-size worlds. Keep it non-destructive: it may save
  normally, but must not move players or edit terrain.
- Outbound block-use packets must canonicalize the clicked block and translate
  the hit vector by the same whole-chart offset. Never wrap the hit vector
  independently: an east-face hit at `X=C` belongs locally to the canonical
  block at `X=C-1`, and wrapping only that vector makes vanilla reject the
  interaction as impossibly distant.
- `:runProductionLifecycleClient` first copies a named production save into its
  own ignored run directory. Its test-only coordinator must use the 26.1
  `TeleportTransition` API, stay separate from smoke/layout-switch/multiplayer,
  and leave the source save untouched. The client, not the coordinator, owns
  non-Overworld inactivity and exact restored-layout/atlas assertions. Let
  Minecraft's integrated-server disconnect path own saving; never call
  `MinecraftServer.saveEverything` from the render thread.
- The Fabric `:runProductionProjectionClient`, `:runProductionVisualParityClient`,
  `:runLayoutSwitchClient`, and `:runProductionLifecycleClient` preparation
  tasks must clear old run evidence, and each runtime task must retain its
  fail-closed verifier finalizer. The projection verifier decodes all three
  selected-environment PNGs; visual parity requires all three views and a
  nonzero seam-motion frame record; layout and lifecycle verify their exact
  terminal marker and copied save. Keep `verifyFabricRuntimeGateContracts`
  attached to `check` so missing, failed, and corrupt fixture evidence cannot
  silently pass after build-script changes.
- `RingWorldCreationScreen.extractRenderState` must not call a background
  extraction method. Minecraft 26.1's
  `Screen.extractRenderStateWithTooltipAndSubtitles` already owns the frame's
  single legal menu-blur pass. The public presets are Small 2,048×128,
  Medium 16,384×256, and Large 32,768×512. Keep all eight base equation lines
  (plus Small's experimental advisory) visible at the automated 320×270
  scale-4 view, keep the five-error state legible, and disable the monument
  toggle below its 160-block usable minimum. Cost emphasis must use
  `RingDimensionReport.hasHighGenerationCost()` because the general warning
  list also contains apparent-width/sky-composition advisories.
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
- The reusable multiplayer fixture must also clear saved rain/thunder before
  it starts and wait for both clients' target interaction chunk before placing
  the cross-seam test block. Its terminal gate requires the ordinary survival
  Nether-portal wait and an actual lightning entity observed by both seam-side
  clients; do not replace either with a direct-transition-only assertion.
- Keep its cross-seam explosion inside the deterministic seam-wrapped glass
  cell; do not restore arbitrary natural-terrain destruction, which once
  amplified cold item/falling-block tracking. Preserve the read-only
  `multiplayer-cold` phase telemetry and independent post-End readiness window
  before weather. Neither may load chunks, clear ordinary entities, weaken the
  watchdog, or alter production scheduling.
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
- `RingSurfaceMesh` owns the shared atlas mesh lattice. The GPU uses an
  unindexed triangle list, but every repeated interior boundary vertex must
  originate from that one lattice rather than a second atlas sample. Keep the
  physical X=0/C position exact while retaining distinct U=0/1 texture
  coordinates at that periodic seam, and preserve the focused
  production/safe-small continuity tests when changing mesh layout, height
  sampling, or triangle order.
- Partial-atlas revisions reuse the reference-height mesh. A complete-atlas
  revision always refreshes changed texture pixels, but it rebuilds the
  terrain-height mesh only when the surface-height fingerprint changes. Keep
  `RingSurfaceMeshRefreshPolicy` and its tests synchronized with renderer
  resource lifecycle changes; stale geometry under a height-changing texture
  creates false terrain edges. `RingSurfaceBuildSnapshot` owns the immutable
  atlas content and height fingerprint for each asynchronous texture result:
  build any matching mesh from that returned snapshot, never the possibly
  advanced live atlas. Its two-argument capture constructor must remain O(1)
  beyond the already-created atlas snapshot: it records
  `NO_DETAILED_HEIGHT_FINGERPRINT`, and the worker resolves a real fingerprint
  only for complete content.
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
- `RingVisualParityCaptureClient` records frame pacing only from the rendered,
  armed natural seam approach through its post-crossing settle. Both loader
  finalizers require a nonzero record but intentionally impose no fixed frame
  budget; the raw average, maximum, and over-50-ms count are release evidence
  to compare on the actual target hardware.
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
  limits, and do not apply either path in Nether or End. Vanilla deliberately
  wakes a saved sleeping player while loading `ServerPlayer`; the multiplayer
  regression therefore requires reconnect beside the canonical seam bed with
  matching Overworld geometry, loaded-bed state, and X/Y/Z proximity, then
  starts a second sleep before testing damage wake. A missing reconnect must
  still hit the ordinary bounded fixture timeout.
- Filled-map pixels, player/banner/frame decorations, and spawn/lodestone/recovery
  compass targets use their nearest periodic X image only in the RingWorld
  Overworld. Banner/frame positions and compass tracker targets remain
  canonical saved data; vanilla map centres remain one immutable saved
  reference (which may be seam-equivalent to C after vanilla grid rounding).
  Do not create another saved map copy or extend this slice to the locator bar
  without a separate audit.
- `RingMapCompassCaptureClient` is the loader-neutral real-client acceptance
  fixture. It covers bidirectional seam pixels/decorations, real world-added
  item frames on both sides, scale/lock, seam-banner removal/restoration, and
  a normal save/disconnect/reopen before rechecking map, frame, and compass
  persistence. Its disconnect gate reads raw geometry/camera/atlas state,
  atlas-control state, and complete-ring GPU ownership rather than relying on
  an absent client level. Its exact-target check must reuse one compass wobble
  state so independent random offsets cannot make the seeded comparison
  probabilistic. Run `:runMapCompassCaptureClient` and
  `:neoforge:runMapCompassCaptureClient` separately: the unqualified task name
  is ambiguous in the multi-project build. Its mixin invoker is test plumbing
  only and must not become production compass logic.
- The reusable multiplayer harness waits for both real clients to report a
  fully loaded world before setup teleports. Its night fixture advances the
  26.1 `WorldClock` monotonically to the next 13,000-tick phase; rewinding to
  absolute day-zero time makes reused-world bed tests nondeterministic.
- The opt-in Atlas-concurrency multiplayer gate adds a startup-stability
  barrier after both client-ready reports: require 100 consecutive server
  intervals at or below 100 ms, fail closed at 60 seconds or 1,200
  observations, then retain the original 100-tick Creative-to-Survival dwell
  before arming seam movement. Keep the `maxRemoteStep <= 1.25` assertion and
  client self-stop behavior; the corrected fresh Fabric and cold NeoForge
  concurrent runs passed this gate on 2026-08-08.
- Its disposable Fabric and NeoForge preparation tasks share
  `ringMultiplayerCircumferenceBlocks`, `ringMultiplayerWidthBlocks`, and
  `ringMultiplayerWallHeightBlocks` (safe-small defaults: `2048`, `416`,
  `160`). Circumference and width must meet the normal minimums and be
  16-block aligned; wall height must be at least 32. With atlas concurrency
  enabled, the verifier must match every logged total against `(C / 8) *
  (W / 8)`, not merely a fixed safe-small count. Rerun the server with
  `-x prepare...` only to resume the ignored disposable world without erasing
  it; do not use that escape hatch for a changed geometry.
  Fabric defaults this fixture to port 25568 and NeoForge to 25566; preserve
  their loader-specific port properties so independent Gradle invocations do
  not collide silently.
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
