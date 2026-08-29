# RingWorld for Minecraft 1.21.1

This is the maintenance and release record for RingWorld's Minecraft Java
1.21.1 backport. The public integration branch is `port/mc-1.21.1`.

The current build is **RingWorld 1.0 Beta 2 for Minecraft 1.21.1**. Matched
Fabric and NeoForge jars were built from public commit
`848b9cc5982ab473f84f91a4301ffb4176222ad6` and submitted to CurseForge on
2026-08-24. This is a playable public Beta, not a stable or broad modpack-
compatibility claim.

## Release identity

| Item | Fabric | NeoForge |
| --- | --- | --- |
| Minecraft | 1.21.1 exactly | 1.21.1 exactly |
| Java | 21 | 21 |
| Loader | Fabric Loader 0.16.14 or newer compatible 1.21.1 build | NeoForge 21.1.239 or newer compatible 1.21.1 build |
| Required dependency | Matching Fabric API; validated with 0.116.15+1.21.1 | None beyond NeoForge |
| Artifact version | `1.0.0-beta.2+mc1.21.1` | `1.0.0-beta.2+mc1.21.1` |
| CurseForge file | [8722177](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/8722177) | [8722178](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/8722178) |
| SHA-256 | `a51431f118a781f7214bd1a0e9706d81c4ec583f3af82f712fa6ba8b42c1da4b` | `e137f0712c2fab5807a77a4177d2d706abe3c7b656fbca809dee0a154844650e` |

CurseForge processing and moderation can delay public download availability.
No 1.21.1 Modrinth version or optional installer/package is claimed by this
record. The earlier Beta 1 files `8714613` and `8714619` remain historical;
Beta 2 is a new submission and did not edit or replace them.

## What the backport preserves

Minecraft 26.1 remains the behavioral authority. The 1.21.1 branch changes
Minecraft, loader, renderer, and build boundaries while preserving the
RingWorld design:

- the server owns one canonical X plane and the circumference is periodic;
- natural seam travel is a local movement step, not a corrective teleport;
- distance, tracking, combat, AI, projectiles, vehicles, maps, compasses,
  portals, structures, and effects use nearest periodic images;
- Z remains a finite band with two textured, breakable rims;
- Y physics and gravity remain vanilla;
- only the Overworld is curved and periodic;
- saved layout dimensions are immutable after first Overworld load;
- settings format 3, terrain-noise mapping 4, layout fingerprints, Atlas
  identity, and payload meaning remain shared with the authoritative design;
- real chunks remain authoritative while the Atlas-backed complete ring is
  visual LOD only; and
- Fabric and NeoForge share topology, settings, worldgen, storage, protocol,
  rendering policy, and tests, with loader code kept behind narrow adapters.

## Validated Beta experience

The Windows Java 21 checkpoint passed on both loaders:

- all 352 unit and parameterized cases and both loader build/contract gates;
- menu-only creation/settings clients with thirteen captures at GUI scales
  1-4, including the narrow 320x270 logical layout;
- a complete 2,048x128 integrated Atlas with all 4,096 cells, progressive and
  complete rendering, two ordered live revisions, save, disconnect, and raw
  client-session teardown;
- map and compass seam traversal, persistent pixels, banners, item frames,
  scale/lock state, and nearest-image spawn/lodestone/recovery targets;
- curved block/entity/object captures and same-process switching between
  different saved layouts;
- 6/12/28-chunk natural-seam and both-rim visual matrices;
- dedicated creation, settings, topology, persistence, aggregate worldgen,
  structures, and reload;
- the full two-client seam/gameplay fixture, including combat, blocks, beds,
  death, boats, hostile navigation, physical portals, multi-lap Nether
  returns, water destinations, teleport, reconnect, and weather;
- a two-phase saved raid through reload, natural seam folding, vanilla
  victory, and Hero of the Village;
- a complete 16,384x256 source world with all 65,536 Atlas cells, followed by
  Overworld/Nether/End lifecycle, save/disconnect, teardown, and reopen; and
- production noon, dusk, night, rain, tangent, handoff, radial, natural-seam,
  and both-rim capture gates.

The safe-small 6- and 12-chunk runs recorded no frame over 50 ms. Each
28-chunk loader run recorded one; their measured averages were 9.6-9.9 ms.
The final production natural-crossing windows averaged about 8.6 ms with no
frame over 50 ms. These are measurements from one Windows machine, not
minimum hardware guarantees.

Full commands, fixture boundaries, hashes, and the distinction between source
runtime evidence and release evidence are in
[`COMPILER_BASELINE.md`](COMPILER_BASELINE.md).
The retained live-chunk/Atlas compositor, all rejected transition experiments,
loader comparison metrics, screenshot index, and mainline-relevant findings
are recorded separately in
[`HANDOFF_TRANSITION_RESEARCH_2026-08-24.md`](HANDOFF_TRANSITION_RESEARCH_2026-08-24.md).
Its accepted 1.21.1 policy keeps the Experiment 19 continuous overlap, adds a
fail-closed Atlas floor for incomplete finite-band section coverage, and proves
the final renderer on both loaders across foliage rebuild/motion, initial
streaming, partial Atlas, settled Fabulous noon, Fabulous rain at 28 chunks,
and full production lifecycle/reopen. This is local Windows source/runtime
evidence, not packaging, public integration, broad compatibility, or release
approval.

## Backport-specific implementation findings

### Toolchain and mappings

The reviewed graph pins Minecraft 1.21.1, Java 21, Gradle 8.10, Fabric Loom
1.8.13, Fabric Loader 0.16.14, Fabric API 0.116.15+1.21.1, NeoForge 21.1.239,
and ModDevGradle 2.0.143. Sources use Mojang names. Fabric remaps the runtime
jar to intermediary names; NeoForge retains Mojang names.

That means compiled RingWorld classes are not expected to be byte-identical
between loader jars even when they come from the same source. Release checks
must instead require one source revision, identical shared resources and
contracts, correct loader metadata, shared tests, and explicit review of the
bounded loader/mapping differences.

The Windows-x64 dependency graph is fail-closed through
[`dependency-inventory.json`](dependency-inventory.json) and
[`../../gradle/verification-metadata.xml`](../../gradle/verification-metadata.xml):
371 components and all 752 resolved artifacts have SHA-256 pins. Linux or
macOS can select additional native artifacts; add their reviewed hashes
rather than weakening verification.

### Minecraft API mappings worth retaining

Common 26.1-to-1.21.1 adaptations include:

- `Identifier` to `ResourceLocation` and older `ResourceKey.location()`
  access;
- `ServerPlayer.serverLevel()` at server-owned level boundaries;
- 1.21.1 `ChunkPos` fields, `asLong()`, `toLong()`, and packed constructor;
- the older toast manager and button `onPress()` APIs;
- 1.21.1 teleport and `RelativeMovement` signatures;
- older raid, damage, game-rule, chunk-height, block-state mutation, and
  unsaved-state APIs; and
- `FMLEnvironment.dist` plus Java 21 Mixin compatibility on NeoForge.

PR [#229](https://github.com/Delaser/RingWorld/pull/229) by Cosmos616 was
inspected as a useful community mapping reference. It was not merged or
cherry-picked, but sixteen adapted files align byte-for-byte with the final
port, including the Gradle wrapper and several of the mechanical mappings
above. The contribution and exact reference commit are retained as provenance
in [`COMPILER_BASELINE.md`](COMPILER_BASELINE.md).

### Login and payload ordering

Both loaders must queue immutable settings immediately after Minecraft's
play-login packet and before initial position/chunk packets. Loader-owned
`PlayerList` Mixins provide that boundary and reject headless-prewarm joins at
method head. Later lifecycle callbacks remain defensive fallbacks only.

Fabric play payload callbacks already execute on the render thread. Do not
add another executor hop: it can let vanilla position or chunk packets
overtake the settings packet. NeoForge server handlers use `enqueueWork` for
their server-thread payload work, while the initial immutable settings packet
is still inserted at the earlier login boundary.

Minecraft 1.21.1 uses `StreamCodec`/`RegistryFriendlyByteBuf` payload APIs.
Protocol semantics were not widened or redesigned for the backport. In
particular, format-3 identity and world hashes use the explicit fixed-width
`RingWireCodecs.LONG`; do not silently substitute VarLong encoding under an
existing channel identifier.

### Renderer and handoff

Minecraft 1.21.1 does not expose the same terrain shader arrangement as 26.1.
The backport supplies loader-shared core shader definitions for solid,
cutout, cutout-mipped, translucent, tripwire, clouds, and the ring surface.
The shared `ringworld_handoff.glsl` policy, `RingStreamingProxyCoverage`, and
post-compile section view coordinate the live-chunk fade, Atlas reveal, haze,
finite-band coverage, and proxy exclusion. The accepted Experiment 19 policy
uses a fixed `0.58V→0.68V` proxy ramp and keeps live terrain overlapping the
Atlas through `1.02V`; an opaque Atlas floor remains until finite-band section
coverage is safe.

This per-render-type path is essential: disabling the 1.21.1 shader overrides
or treating the Atlas as an unrelated sky object restores a visible hard
cutoff. Future tuning should begin from Beta 2 source commit `848b9cc`, retain
the post-compile draw order and fail-closed coverage proof, preserve the common
handoff equations, and compare Fabric and NeoForge captures at 6/12/28 chunks
plus production tangent/radial views. The full experiment ledger and reusable
mainline findings are in
[`HANDOFF_TRANSITION_RESEARCH_2026-08-24.md`](HANDOFF_TRANSITION_RESEARCH_2026-08-24.md).

Cloud state is bridged separately through `RingCloudShaderState`. Shader and
JSON assets are version-sensitive ABI and must be re-audited whenever a
Minecraft, mappings, Fabric rendering, or NeoForge rendering dependency
changes.

### World storage and lifecycle

Settings and Atlas data are dimension-owned under the Overworld data path.
The client must clear raw settings, Atlas identity, textures, mesh, chart, and
session state on normal disconnect before another world opens in the same
process. A different-layout fixture exists specifically to prevent stale
world or GPU state from surviving that boundary.

Do not scatter 1.21.1 compatibility branches through shared geometry or saved
formats. Prefer a narrow compatibility helper, version-specific descriptor,
or loader adapter and keep the common behavioral contract unchanged.

### CurseForge author API

The author upload endpoint currently requires a dependency relation to carry
both an integer `projectID` and its `slug`. Omit `relations` when there is no
dependency; an empty `relations.projects` array is rejected. For this upload,
the endpoint rejected the numeric Minecraft 1.21.1 version row exposed by its
versions API, while the documented `gameVersionNames` alternative succeeded.
These rules are implemented in the guarded publisher and recorded in
[`../../docs/CURSEFORGE_RELEASE.md`](../../docs/CURSEFORGE_RELEASE.md).

## Known limitations and problems

### Release and platform scope

- This is a Beta. It is not the stable 26.1.2 release and is not a claim of
  broad third-party compatibility.
- Current graphical, dedicated, multiplayer, lifecycle, production-world,
  and packaging evidence is local Windows x64 evidence. Linux and macOS have
  not passed equivalent 1.21.1 release gates.
- Only Minecraft 1.21.1 is accepted by the jars. Similar 1.21.x versions are
  not supported by proximity.
- The release consists of standalone Fabric and NeoForge mod jars. No 1.21.1
  installer, launcher bundle, server package, or Modrinth version is claimed.

### Build reproducibility boundary

- The checked-in dependency verification describes the reviewed Windows
  graph. A fresh secondary worktree can cause Loom to regenerate local
  remapped modules with nondeterministic archive bytes and therefore trip
  strict verification even though upstream coordinates are unchanged.
- Never respond by running `--write-verification-metadata` during an ordinary
  build. Use the already reviewed project cache for a same-source rebuild, or
  treat regeneration as a deliberate dependency update: inspect every diff,
  record the reason, and finish with a clean enforcement build.
- The default version remains the diagnostic
  `0.0.0-backport+mc1.21.1`. A public artifact requires explicit release
  version and label overrides plus the clean-source staging checks.
- `-PringBackportCompilerScope=fabric` isolates Fabric configuration only for
  diagnosis. It is not a supported single-loader release mode.

### Mod compatibility

The experimental NeoForge Create 6.0.10 phase-3A server adapter, its exact
activation/dependency boundary, and the work still required before making a
compatibility claim are recorded in
[`CREATE_COMPATIBILITY_PHASE_3A_2026-08-29.md`](CREATE_COMPATIBILITY_PHASE_3A_2026-08-29.md).

The matching phase-3B client/gameplay checkpoint qualifies only the exact
Minecraft 1.21.1 / NeoForge 21.1.239 / Create 6.0.10 / Flywheel 1.0.6 tuple.
It covers seam belts and tanks, canonical persistence, transient client
controllers, mounted contraption continuity, the live Flywheel embedding, and
the backend-OFF vanilla fallback. Fabric Create remains unqualified, and the
rejected dead CPU-culling surface plus the precise runtime-evidence limits are
recorded in
[`CREATE_COMPATIBILITY_PHASE_3B_2026-08-29.md`](CREATE_COMPATIBILITY_PHASE_3B_2026-08-29.md).
This is local qualification evidence, not published support metadata or a
release claim.

The subsequent real glued Mechanical/Windmill Bearing investigation separates
functional lifecycle evidence from projected, palette-bound pixel evidence.
Its original checkpoint C images were rejected as mis-aimed; the corrected C.1
matrix visibly passes indirect, explicit instancing, and backend-OFF controls
without reproducing the reported rotating-assembly disappearance on the exact
tuple. Scope, limitations, retained manifests/contact sheets, and the reason no
speculative production fix was added are recorded in
[`CREATE_COMPATIBILITY_ROTATING_BEARING_C1_2026-08-29.md`](CREATE_COMPATIBILITY_ROTATING_BEARING_C1_2026-08-29.md).
This remains qualification evidence, not published support metadata.

The subsequent standalone kinetic-visual D2 spike reproduces and narrowly
corrects a distinct flat-placement defect in Create kinetic block-entity
Flywheel visuals. It records the exact per-visual child-embedding ABI,
identity-to-curved late-geometry transition, lifecycle ownership, matched
indirect/instancing/OFF pixel evidence, and the still-bounded qualification
limits in
[`CREATE_COMPATIBILITY_KINETIC_VISUAL_D2_2026-08-29.md`](CREATE_COMPATIBILITY_KINETIC_VISUAL_D2_2026-08-29.md).
The implementation is frozen at source commit
`66c8c81c8be3fe54ea16d4d2db0315bb7b931080`. This remains qualification
evidence, not published support or release metadata; Fabric Create remains
unqualified.

The D3 standalone kinetic-network matrix then exercises twelve real Create
block-entity visual types across indirect normal/high/low, explicit instancing
high/low, and backend-OFF high/low. It binds curved projection and per-component
pixel motion, a 128-visual density/performance control, render-origin and chunk
lifecycles, and one durable same-process low-chart reopen. No additional
production defect was reproduced after the D2 correction. Exact results,
rejected calibration attempts, evidence hashes, and the still-queued linear
contraption boundary are recorded in
[`CREATE_COMPATIBILITY_KINETIC_NETWORK_D3_2026-08-29.md`](CREATE_COMPATIBILITY_KINETIC_NETWORK_D3_2026-08-29.md).
This remains qualification evidence, not published support or release
metadata; Fabric Create remains unqualified.

The final D4 linear-contraption matrix qualifies real glued Mechanical Piston,
Gantry Carriage, and Rope Pulley mechanisms. It records two narrow exact-tuple
client corrections: gantry smoothing now interprets canonical sync coordinates
in the entity's nearest presentation chart, and backend-OFF cached contraption
blocks now use material-equivalent entity-space layers instead of receiving a
second nonlinear pass through RingWorld's terrain shader. The strict ABI and
five-layer state audit, eight-client-mixin boundary, nine-run
indirect/instancing/OFF matrix, per-payload chest/shulker adjacency oracle,
durable piston reopen, exact restoration, regression gates, and limitations
are recorded in
[`CREATE_COMPATIBILITY_LINEAR_CONTRAPTIONS_D4_2026-08-29.md`](CREATE_COMPATIBILITY_LINEAR_CONTRAPTIONS_D4_2026-08-29.md).
This remains qualification evidence, not published support or release
metadata; Fabric Create remains unqualified.

- Shader packs and renderer mods can conflict because the backport replaces
  version-sensitive vanilla core shader assets.
- World-generation, structure, chunk-ticket, view-distance, distance-query,
  entity-tracking, portal, map, or networking mods may assume an infinite flat
  Overworld and need explicit RingWorld support.
- Opaque third-party payloads and positional protocols are not globally
  rewritten. Both endpoints need the matching RingWorld build.
- Vanilla clients are intentionally unsupported.
- Compatibility beyond the documented first-party matrix is largely
  unqualified; back up worlds before adding or removing major mods.

### Atlas and visual fidelity

- The Atlas is one surface-height/colour summary per cell. It cannot reproduce
  individual distant blocks, buildings, transparent layers, mobs, local block
  lights, or live weather volumes.
- The complete distant ring appears progressively. Large fresh worlds can
  take substantial CPU, disk, and time before all Atlas cells exist. The Beta
  validated an already-complete immutable 16,384x256 source save, not a portable
  fresh-production completion-time guarantee.
- The adaptive live/Atlas transition is reviewed and substantially softened,
  but unusual terrain relief, water/translucency, shader mods, custom render
  distances, or changed fog equations can expose the boundary again.
- Atlas terrain updates occur when relevant chunks are captured/loaded, not
  synchronously after every distant block edit.
- Performance measurements come from one Windows system and do not establish
  minimum hardware requirements.

### Worlds and saves

- Ring dimensions are immutable after first Overworld load. There is no
  supported in-place resize.
- Existing flat Overworlds cannot be converted into RingWorlds.
- Do not downgrade a 26.1/26.1.2 Minecraft world into 1.21.1. Minecraft data
  version changes are outside RingWorld's saved-format compatibility.
- Moving a 1.21.1 Beta world forward to another RingWorld/Minecraft line is
  not supported until that exact upgrade path has passed qualification.
- Decorative wall changes affect newly generated/repaired boundary chunks and
  can leave mixed old/new rim appearance in an existing save.

### Coverage boundaries

- Automated coverage is extensive but not exhaustive. Arbitrary modded
  projectiles, vehicles, redstone/fluid networks, command families, map-mode
  playthroughs, and unusual structure combinations remain open compatibility
  territory.
- The production visual gate covers noon, dusk, night, rain, seam, handoff,
  rims, tangent, and radial views. It is not a comprehensive shader-pack,
  gamma, night-vision, accessibility, or every-GPU matrix.
- The finite band and Atlas allocation are validated at world creation, but
  extreme custom settings remain more expensive and less reviewed than the
  built-in presets.

## Building the branch

Use a Java 21 JDK. The normal graph builds and tests both loaders:

```powershell
git switch port/mc-1.21.1
.\gradlew.bat clean build :neoforge:build --console=plain --no-daemon
```

The diagnostic Fabric jar is written to `build/libs/`; NeoForge writes to
`neoforge/build/libs/`. Do not distribute jars carrying the default
`0.0.0-backport` identity.

The exact Beta label was produced with:

```powershell
.\gradlew.bat build :neoforge:build `
  "-Pmod_version=1.0.0-beta.2+mc1.21.1" `
  "-Prelease_label=1.0 Beta 2 for Minecraft 1.21.1"
```

A release build is not sufficient by itself. Verify both loader descriptors,
embedded `LICENSE-RINGWORLD.txt`, MPL-2.0 metadata, archive contents, exact
source revision, SHA-256 values, and the relevant runtime/visual gates before
staging.

## Updating from mainline

Use the 26.1 agent as behavioral authority and keep future merges reviewable:

1. Compare mainline changes by subsystem and invariant, not by attempting a
   blind whole-branch merge.
2. Apply loader-neutral geometry, topology, worldgen policy, protocol models,
   Atlas formats, and tests to shared source first.
3. Port only the Minecraft ABI boundary: mappings, method descriptors,
   renderer hooks, packet registration, lifecycle events, and loader metadata.
4. Preserve saved format 3, terrain-noise mapping 4, fixed-width wire fields,
   and existing channel identifiers unless a deliberately versioned migration
   is designed for every supported loader/version.
5. Re-audit every changed Mixin target and each per-render-type shader asset.
6. Re-run both 338-case loader suites, creation UI, Atlas UI, map/compass,
   layout switch, curved objects, dedicated topology/worldgen, two-client,
   raid, production lifecycle, and production visual gates in proportion to
   the change.
7. Rebuild from a clean pushed revision, stage one jar per loader, and record
   exact source, archive inventory, licence, metadata, and hashes.

Do not copy shared classes into `versions/mc1.21.1/` merely to resolve an API
error. Create a narrow compatibility seam or a version-specific resource only
where the Minecraft ABI genuinely differs.

## Contribution workflow

Branch from `port/mc-1.21.1` and open backport fixes against that branch.
Include the Minecraft version, loader, Java version, other mods, logs, and a
minimal reproduction. Rendering changes should include matched Fabric and
NeoForge screenshots at the relevant render distances; topology, storage,
networking, worldgen, or multiplayer changes need the corresponding runtime
fixture rather than compile output alone.

The historical backport epic and milestone remain useful issue indexes:

- [1.21.1 backport epic](https://github.com/Delaser/RingWorld/issues/181)
- [1.21.1 milestone](https://github.com/Delaser/RingWorld/milestone/1)
- [contribution guide](../../CONTRIBUTING.md)

## Licence

RingWorld is licensed under Mozilla Public License 2.0. Distributed jars must
declare `MPL-2.0`, contain `LICENSE-RINGWORLD.txt`, and identify a reasonable
way to obtain the corresponding MPL-covered source. Do not include Mojang
source/assets, credentials, private worlds, generated account data, or code a
contributor is not entitled to license.
