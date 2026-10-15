# RingWorld 1.4: backport, dual-version maintenance and acceptance

Audit date: 15 October 2026. This is an implementation and acceptance plan,
not a declaration that either backport is already a qualified 1.4 release.
The immediate deliverable is the full source audit and executable-plan contract;
product implementation and frozen-candidate qualification are subsequent stages.

## 1. Sources, scope and present evidence

The published 1.4 runtime source is
[`1533878fe3a6eab7562830b3f39cbd7caae6d46a`](https://github.com/Delaser/RingWorld/commit/1533878fe3a6eab7562830b3f39cbd7caae6d46a).
The publication record at main `653b9b5a9fdbf05c900d1a3db7ddd48e40fc8c83`
identifies six standalone files, covering 26.1–26.1.2, 26.2 and 26.3 on Fabric
and NeoForge. It records ten quick cells and 100 accepted full-fixture keys,
reviewed composite coverage with original failures retained, and independently
matching hosted hashes. This audit reads that record; it does not independently
re-download those releases or inspect current moderation.

Do not use the earlier `6ee5bd56` candidate as the implementation authority.
Its green suite preceded the reproduced Large-server Atlas checkpoint stall.
The replacement source contains the checkpoint, chunk-work, lighting,
render-compatibility, underwater and final fixture repairs together.

The local starting point is `8f1fe43a3f706d98883b75fce478dc80c5c56817`:
the 1.21.1/Java 21 1.3 development/staging checkpoint. Its October 3 release
upload was not performed. Public Beta history and retained 1.3 evidence remain
historical; do not relabel them as 1.4 results. Before planning, this audit ran
both existing builds/checks, then reran all Java tests: Fabric/common 531 and
NeoForge 554; zero failures, errors or skips. A hidden GLFW preflight creates
a context successfully. Fresh baseline creation/Atlas UI results are recorded
separately in the audit record; they exercise existing 1.3, not the new port.

The comparison uses the published source and the intervening delta from the
previous mainline audit `a46965c`, plus direct inspection of server scheduling,
checkpoint, light-map, networking, UI, geometry, shader and acceptance code.

Reference records, all pinned to their appropriate revision:

- [Published 1.4 record](https://github.com/Delaser/RingWorld/blob/653b9b5a9fdbf05c900d1a3db7ddd48e40fc8c83/docs/RELEASE_1_4_PUBLICATION_2026-10-14.md)
- [Final patch notes](https://github.com/Delaser/RingWorld/blob/653b9b5a9fdbf05c900d1a3db7ddd48e40fc8c83/docs/RELEASE_1_4_PATCH_NOTES.md)
- [Release runbook](https://github.com/Delaser/RingWorld/blob/1533878fe3a6eab7562830b3f39cbd7caae6d46a/docs/RELEASE_RUNBOOK.md)
- [Checkpoint ownership and measurements](https://github.com/Delaser/RingWorld/blob/1533878fe3a6eab7562830b3f39cbd7caae6d46a/docs/SERVER_ATLAS_CHECKPOINTS.md)
- [Chunk work and rejected experiments](https://github.com/Delaser/RingWorld/blob/1533878fe3a6eab7562830b3f39cbd7caae6d46a/docs/SERVER_STALL_INVESTIGATION_2026_10_10.md)
- [Lighting snapshots](https://github.com/Delaser/RingWorld/blob/1533878fe3a6eab7562830b3f39cbd7caae6d46a/docs/SERVER_LIGHTING_SNAPSHOTS_2026_10_11.md)
- [Rendering/Sodium and underwater limits](https://github.com/Delaser/RingWorld/blob/1533878fe3a6eab7562830b3f39cbd7caae6d46a/docs/SODIUM_COMPATIBILITY_2026_10_13.md)

## 2. What 1.4 changes

The following is the semantic port inventory. Names are source landmarks,
not an instruction to copy Minecraft-dependent files without adaptation.

| ID | Behavior and important contract | Primary source | Required checks |
| --- | --- | --- | --- |
| F01 | Atlas checkpoints no longer encode/compress/write on the gameplay thread. One reusable snapshot buffer, one outstanding operation, 16,384-cell owner-thread slices and a soft 2 ms copy allowance. Changes to copied slices are repaired before publishing a coherent revision. Dirty generations are acknowledged only after persistence succeeds. | `RingAtlasCheckpointWriter`, `RingTerrainAtlas.copyCheckpointCells`, service completion/cancellation/unload | Concurrent edits, mutation during writing, failure/retry, torn/corrupt files, verification failure, shutdown flush, complete reopen, no owner/worker deadlock. |
| F02 | Pregeneration defaults to automatic 4/2/1 requests; fixed 1/2/4/8 overrides are live and world-session scoped. Eight slots share a canonical cursor and retain independent leases/retries. Ready slots rotate; lowering a target drains issued work rather than cancelling it. | `RingAtlasAdaptiveConcurrency`, `RingAtlasPregenerationBatch`, request/selection/service | Actual nonblocking requests, fairness, backpressure before each submission, pause/drain/resume/cancel/replacement, no skipped cells or leaked tickets. |
| F03 | Player-loaded chunks enter a coalescing coordinate queue. First available capture goes to player work, then ready pregeneration, then remaining player work. Combined captures are at most four per tick, adapt to 2/1, with a soft 2 ms target checked between whole chunks. Stale coordinates drop without loading neighbours. | `RingAtlasLoadedChunkQueue`, `RingAtlasCaptureBudget`, service | Player priority without starvation, stale/reloaded chunk identity, queue drainage and teardown, single-chunk overrun telemetry. |
| F04 | Scheduled Overworld autosaves sweep coordinates over ordinary ticks. Unload/eager work has bounded normal/catch-up/pressure budgets. Explicit saves, flushes and shutdown keep their durability semantics. Location advancements defer once after a chunk change, then retry even during continuous travel. | `ServerAutosaveMixin`, `ChunkMapMaintenanceMixin`, `ServerPlayerLocationWorkMixin` | Edits present on disk before explicit flush; restart; backlog catch-up; forced saves; no ticket-graph splitting, lost writes or advancement starvation. |
| F05 | Server Overworld lighting lookup snapshots partition keys into 256 copy-on-write maps. Values retain vanilla `DataLayer` ownership and caches. Client, Nether and End remain on vanilla storage. | `RingLightSectionTable`, three lighting mixins | Branching/randomized snapshots, cache invalidation, value isolation, concurrent publication, chunk load/unload and seam light; allocation/GC measurement. |
| F06 | Building outside finite Z is enabled by default and mutable per world. World-page toggle and operator command control player placement. Off prevents new player block/bucket placement, including either half of a crossing bed; existing structures, automation and collision remain. Client prediction follows server authority. | `RingBuildingSettings`, placement/bucket mixins, building payloads, map World page | Both rims, inventory/sounds unchanged after rejection, operator/non-operator, stale fingerprint, join/sync/reopen, placement through X seam. |
| F07 | Exterior chunks/builds and nearby entities remain visible in the bounded periodic watch window. Generation still produces the finite natural band. Tracking distance and vanilla void damage remain; exterior scenery does not gain Atlas coverage. | `RingChunkFilter`, `RingEntityTracking`, server tracking and version-owned extraction adapters | Actual client extraction on both Z sides; range rejection; dimension return; natural seam; falling; void damage; saved exterior builds. |
| F08 | Nearby block correction starts On, distance 3 chunks each way, adjustable 1–8, and resets on disconnect. A height-dependent, periodic, positive-slope angular map gives near-field unit arc spacing and fades by four core distances. Camera Y translates rather than reshapes the world. Server coordinates/physics and public physical-coordinate API remain unchanged. | `RingNearbyProjection`, `RingDistortionTuning`, shader projection, object/frustum callers | CPU/GPU correspondence, bottom/surface/Y319/above-build, 1/3/8, moving/stationary toggles, seam closure, conservative culling, reload/reset. |
| F09 | Both finite terrain edges get side-material closure. Decay uses bounded crest/cap geometry across wall thickness; wall mips stay opaque instead of cutting holes in an unchanged prism. Saved top is the anchor; exterior bottom is top minus saved height, positive outer face is `maxZ+1`. No distant underside is added. | `RingSurfaceMesh`, `RingWallTexture`, all renderer call sites | High/low terrain, both inner/outer rims, zero/normal decay, thickness 1/5/32, all LODs, exact shared boundaries and bounded vertex overhead. |
| F10 | Live/proxy coverage considers vertical separation as well as horizontal distance; material/detail distance remains horizontal. Visual policy advances 6→7. | `RingRenderProfile`, shared handoff/material shaders | Ground/high-altitude/looking-down views, no holes, correct water/depth ordering, early/partial/complete handoff and foliage rebuild. Preserve the legacy backport's proven underlay readiness. |
| F11 | Clouds use one saved-wall-derived base height for CPU face selection and GPU geometry. The flat Overworld horizon band is removed across heights/time/backdrops. | cloud/fog/sky adapters, `RingCloudBounds` | Sunrise/sunset/noon/night/rain, wall heights, under/above cloud deck, both Z rims, Nether/End control. |
| F12 | Atlas columns omit recognized manufactured layers only when thin enough and separated from lower material by real air. Thresholds: maximum 64-block interval and at least eight air blocks; stacked intervals repeat. Natural/unknown materials, fluids and unsupported roofs are retained. Support edits below the old top must invalidate the column. | `RingAtlasColumnSurface`, `RingAtlasSurfaceSampler`, invalidation | Manufactured/natural/unknown/stacked/supported/floating controls; changes under platform; initial capture equals recapture. |
| F13 | Rendering owns private state and shared projection/material rules. Mainline uses a private 176-byte block instead of modifying vanilla Globals; immutable `RingRenderView` is safe for visibility workers. Optional Sodium adapts shader source, bindings, visibility and stationary graph invalidation. It remains experimental. | `RingShaderContract`, frame/pipeline bindings, `RingRenderView`, optional mixin plugin/Sodium adapter | Vanilla state preservation, absent foreign classes/server load, reload/unload, shader anchor rejection, worker snapshot isolation; separately pinned renderer trials. |
| F14 | The Atlas is skipped when the actual render camera is in water. Real terrain keeps normal water fog; retained resources recover after resurfacing, including a resource reload underwater. Third-person camera fluid, not player fluid, decides. | `RingSurfaceTextureRenderer`, underwater fixture | Six above/submerged/near-surface/resurfaced/reload states, low view distance, complete GPU recovery, no far-ring ghost. |
| F15 | Thirty wall preview images are compressed; shared preview code loses duplication without changing the seed Use/Apply draft contract. | wall samples, shared preview screens | PNG decode/selection, all ten palettes/three patterns, same preview behavior and keyboard/resize flows, jar inventory/size. |
| F16 | All previous behavior remains: canonical X storage, natural wrapping, vanilla gravity, finite natural Z, dimensions/terrain identity, structures, maps/compasses, portal routing, raids, stateful blocks, authority and UI. | Existing 1.21.1 port and full mainline suite | Ten core fixtures, migration/rejection, no cross-version gameplay or save-downgrade claim. |

### Controls and data identity

- Keep the five-page creation editor: Ring/Terrain/Walls/Sky/Preview. Its draft,
  seed Use/Apply, resize, validation and discard/restore behavior is still required.
- The in-world menu now has **Generation, World, Display**. World owns the
  synchronized outside-building toggle; Display owns local correction and LOD.
  Menu remains non-pausing; ordinary players get read-only world controls.
- `/ringworld chunk_gen_rate [1|2|4|8|auto]` is an operator/world control, not a
  CPU-core selector. Auto uses about one-second observations: >40 ms poor,
  >=100 ms severe, <25 ms healthy; two poor samples back off, ten healthy recover.
  Integrated owner FPS uses min(60, cap), 80%/40%/95% thresholds and expires after
  three seconds. Remote clients cannot throttle a dedicated server with FPS.
- `/ringworld building outside on|off|show` is server-owned; both client command
  adapters must forward it rather than swallow it under a local `/ringworld` root.
  `/ringworld distortion on|off|show` and `distance 1..8` remain local.
- Immutable settings remain format 5 / mapping 4 / `settings_v7`. Mutable
  building state is separately saved and synchronized through
  `building_settings_v1` and `building_control_v1`, fingerprint/permission checked.
- Atlas advances **10→11** because sampling meaning changes, not its 12-byte
  cell layout. One-block source and 16,777,216-cell admission remain. Metadata/tile
  IDs remain v4; their format/hash checks must reject old content. Rebuild old
  visual caches from real chunks; never regenerate saved terrain to migrate a cache.
- Small/Medium/Large source counts remain 262,144 / 4,194,304 / 16,777,216.
  Raw cell data is approximately 3/48/192 MiB, not total process memory.
  A Large checkpoint adds one reusable 192 MiB buffer; verification and client
  snapshots/resources need additional headroom.

The feature boundaries also carry forward: distant surfaces are an approximation,
exterior builds have no distant Atlas representation, and nearby correction does
not make every curved cube exact or change the public physical-coordinate API.
Upstream explicitly leaves high Atlas surfaces above the original physical centre
of legacy 1,024-block rings unqualified: inverse projection cannot disambiguate
their original signed radius. Include those saved-layout boundary cases and
either provide tested canonical vertex metadata or state the exact visual limit;
do not silently change saved geometry to hide it. New-world circumference remains
at least 2,048. Shader packs and arbitrary renderer/modpack combinations remain
separate compatibility projects.

## 3. Two blocking findings in the existing port path

**A. Nonblocking acquisition must be repaired before concurrency.** At the local
baseline, `RingAtlasPregenerationService.Job.tick` calls public
`ServerChunkCache.getChunkFuture` on the server thread. Direct `javap` inspection
of the pinned named 1.21.1 Minecraft jar confirms that branch calls
`MainThreadExecutor.managedBlock` before returning. The comment promising an
immediate future is incorrect. Adding a ticket first does not remove that wait.
Provide an audited owner-thread nonblocking acquisition boundary for each
Minecraft version, with retained ticket and FULL-future validation. Do not use
an off-thread world-access workaround. Verify both the call graph and a real
cold-server trace; a passing pure future test alone cannot prove this property.

**B. Preserve the local edit-queue fix.** Published upstream 1.4 still re-enqueues
unloaded recapture cells and drains exact cells ahead of overflow. The existing
backport has a tested fairness/unloaded-neighbour correction from its real lamp
update failure. A wholesale service/queue replacement would regress it. Merge
the new sampler, loaded queue and checkpoints around that fix; retain the
4096-cell overflow/starvation test and real lamp placement/removal/reopen proof.
The new deferred loaded-capture queue means the foliage fixture's idle probe
must also observe pending loaded captures before arming its immutable revision.

These are source-confirmed port blockers. The first is not yet a measured
whole-game performance claim; the second must not be generalized into a claim
that every upstream play session reproduces starvation.

## 4. Version and loader support design

| Target | Runtime/bytecode | Planned loaders | Status |
| --- | --- | --- | --- |
| Minecraft 1.21.1 | Java 21 / class major 65 | Fabric, NeoForge | Existing development baseline; 1.4 implementation and acceptance pending. |
| Minecraft 1.20.1 | Java 17 / class major 61 | Fabric, Forge 47.x | New port, no artifact or runtime evidence yet. Exact dependencies must be reviewed/pinned. |

The owner confirmed Fabric+Forge for 1.20.1 and Fabric+NeoForge for 1.21.1 on
15 October 2026. [NeoForge's guide](https://docs.neoforged.net/user/docs/) explicitly
recommends Forge for 1.20.1. The
[Forge 1.20.1 guide](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/)
requires Java 17. Legacy NeoForge on 1.20.1 is outside the agreed matrix;
do not relabel Forge evidence as NeoForge evidence.
This is a documented version-specific loader exception, not deeper Forge
coupling in common logic. Current 1.21.1 Fabric/NeoForge support remains intact.

Ship separate jars per version and loader. No universal 1.20.1/1.21.1 jar,
cross-Minecraft connections or newer-save downgrade is planned.

### Repository and maintenance

1. Keep the reviewed 1.21.1 root/toolchain intact while porting 1.4; preserve its
   Gradle cache and strict verification inventory. Avoid a fresh Loom remap
   masquerading as a normal dependency update.
2. Add an isolated `backports/mc1.20.1/` Gradle root with Fabric/Forge modules,
   its own wrapper, dependency inventory, mixin resources, game runs and platform
   adapters. Its compiler/runtime target is 17; the build JVM is separately
   pinned to the selected plugin's proven requirements.
3. Extract only stable version-independent algorithms into a Java-17-compatible
   common source area incrementally. Models that import Minecraft `Vec3`, NBT,
   saved-data or packet classes need version adapters rather than a false
   "pure common" label. Geometry/projection policy, snapshot bookkeeping,
   concurrency/budget logic, wire field models and expected test vectors can share.
4. Prefer the maintained
   [ModDevGradle legacy Forge plugin](https://github.com/neoforged/ModDevGradle/blob/main/LEGACY.md)
   as the first toolchain spike. Compare against ForgeGradle if exact Mixin,
   reobfuscation or runtime tests fail. No unreviewed dependency number is a pin.
5. Maintain one feature/ABI ledger: upstream source, each port implementation,
   contract changes, test keys, artifact hashes and intentional differences.
   Every fix gets both Minecraft decisions, even when one is "not affected".
6. CI builds all four cells and pure/shared contracts. Loader parity compares
   same-version common named classes/resources or normalized contracts where
   compiler/mapping differences make byte equality inappropriate. Do not demand
   raw cross-version class equality or identical terrain between Minecraft versions.
7. Keep release/maintenance branches per game line after integration. Shared
   bug fixes land as reviewed semantic changes with parity tests; do not repeatedly
   merge all of 26.x into a backport or let one branch silently drift.

### Explicit 1.20.1 adaptation work

- Remove Java 21+ calls such as `Math.clamp` and sequenced `List.getFirst/getLast`
  from Java-17 sources. Preserve numerical behavior with boundary tests; require
  `--release 17`, and audit nested jars for unsupported class versions.
- Replace 1.21.1 `CustomPacketPayload`/`StreamCodec` transport with 1.20.1
  Fabric buffer codecs and Forge `SimpleChannel`. Preserve field widths, limits,
  fingerprints, arrival order and revision commits in independent shared vectors.
  [Forge networking](https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/)
  requires explicit main-thread handoff; settings must still precede position/chunks.
  Do not reuse Fabric 1.21.1's render-thread assumption for the older handler API.
- Adapt saved-data factories/serialization, registry lookups, resource names,
  item NBT versus components, maps/banner/frame storage, compass trackers and
  command/permission APIs. [Minecraft 1.20.5](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-5)
  changed the item component/runtime baseline; these differences cannot be
  solved by lowering a dependency version.
- Audit every server/client mixin descriptor, method body and injection count:
  chunk holders/tickets, lighting wrappers, entity tracking/navigation/raids,
  teleport/portal/sleep, rendering stages and GUI. Required hooks fail closed;
  do not make them optional to obtain a successful launch.
- Keep the older GLSL 150/per-program uniform adapter, with RingWorld-owned
  values and resources. Port the semantic projection/material contract rather
  than 26.x `GpuBuffer`/RenderPass/RenderPearl classes. The existing backport
  already avoids replacing vanilla Globals; preserve that useful property.
- Sodium versions/package names/shader layouts differ. Port optional adapters
  against exact target builds and fail clearly on unknown anchors. Forge 1.20.1
  renderer alternatives require their own tests; experimental Sodium evidence
  on newer NeoForge cannot qualify them. Create/Flywheel remains benched.
- Replace unavailable fixture-only vanilla items/entities with documented
  equivalents that test the same behavior. Do not delete assertions or promise
  newer vanilla content in an older game.
- Generate one immutable production source world natively per Minecraft version.
  Never open a 26.x or 1.21.1 world on 1.20.1. Forward migration, if offered, is
  a separate copied-world qualification and does not imply downgrade support.

## 5. Implementation sequence and exit gates

| Stage | Work | Exit condition |
| --- | --- | --- |
| P0 Baseline | Pin source/inputs, inventory saves, build both current loaders, collect UI/Atlas/runtime reference views and performance controls. Audit host display, paths, Java and process cleanup. | Honest baseline receipts; no original-save changes; known blockers recorded. |
| P1 Nonblocking owner boundary | Repair/version-audit chunk acquisition and keep local recapture fairness. Introduce the shared bounded batch/selection tests. | Cold acquisition returns without owner managed-blocking; FULL validation, cancel/release/failure paths pass on both 1.21.1 loaders. |
| P2 Atlas data/persistence | Port floating-column sampler and invalidation, format 11, coherent bounded writer, verification, cancellation and unload ownership. Extend independent disk readers. | Byte-level content/identity tests, interrupted/resumed/reopened dedicated phases, injected write faults, real lamp/platform edits and shutdown pass. |
| P3 Server performance | Add adaptive requests and player capture queue, then scheduled chunk work and partitioned lighting, in separately measured changes. | Fixed/auto/fairness and real integrated FPS tests; native storage ownership tests; matched Large 3-client measurements and durability pass without new full-GC/retention regressions. |
| P4 Exterior gameplay | Port mutable saved policy, early settings synchronization, authoritative placement, command forwarding and exterior watch/extraction behavior. | Fresh/reopened two-client both-rim/seam, rejection/permission and void/range checks pass. |
| P5 Rendering/UI | Port correction, immutable render view, private shader values, wall closure, altitude coverage, clouds/horizon, submerged policy, menu and compressed previews. | Matched captures plus motion/reload checks on both loaders; no regression to legacy streaming underlay, depth or star ordering. |
| P6 1.20.1 compiler/ABI | Establish pinned Java-17 Fabric/Forge graphs and translate the already understood policies through older Minecraft/loader APIs. Land common pure helpers incrementally. | Both dedicated/client launches and complete feature ledger, then the same full acceptance matrix. A compiler-only success is not feature acceptance. |
| P7 Freeze/qualify | Freeze one source checkpoint and one standalone runtime hash per target/loader. Execute all core and feature gates, performance/soak, save upgrades and platform checks. Diagnose/repair until all required keys pass. | Zero unresolved failures/pending keys; verified exact artifacts, report audit and visual review. |
| P8 Stage/handoff | Build/stage through a backport-aware fail-closed release path; verify provenance, licensing, sources, metadata and rollback instructions. | Four reviewable packages and evidence. Publication is separate from this planning request. |

During development, select affected tests for feedback. Any shipped-source
change invalidates final-candidate acceptance and requires freshly frozen
artifacts plus the full target matrix. A shared helper change affects every
consumer. Do not mix old successes into a new candidate.
Regenerate disposable format-11 production inputs before tests that require a
complete cache. Update exact fixture world-hash/format guards from independently
decoded input settings and Atlas identity; the existing foliage fixture's
format-10 world hash cannot be carried over or replaced with a wildcard.

## 6. Acceptance: the same ten 26.x fixture families

The actual 1.4 coordinator `run_minecraft_nightly_matrix.py` selects the ten
families below. The underlying model also writes a distinct handshake terminal
from the Atlas UI invocation; it is not an omitted eleventh launch. There are
**40 core fixture invocations for four backport cells**, not 100: 26.x had ten
Minecraft/loader runtime cells, whereas these two exact Minecraft versions have
four. Coverage within each cell remains equivalent.

| Core key | Required assertions for every cell |
| --- | --- |
| creation-ui | Actual footer/editor entry; all five pages, ten presets, seed Use/Apply, invalid input, discard/restore, narration/focus, scales 1–4 and 320×270 logical minimum. No world created by the menu-only gate. |
| worldgen | Existing aggregate/multi-seed biome/structure/cave/ore/tree/loot gates; deliberate seam-crossing structures; scarce-policy SATISFIED/UNSATISFIED and reload; canonical storage and ring-aware locate. Version-native expected terrain. |
| atlas-recovery | Official packaged dedicated runtime; independently observed partial disk checkpoint, normal interrupted stop, same disposable world resumed to verified completion, complete reopen with no generation. Settings/hash/presence/length independently parsed. |
| atlas-ui | Progressive/complete ring, Generation/World/Display, owner/read-only state, pause/resume/stop/retry, revisioned stone placement/removal, lighting and permission updates; required handshake before world traffic, normal disconnect and raw client/GPU/control reset. Gold floating markers are unsuitable under the new sampler. |
| multiplayer | Two real clients: natural seam movement and remote-step limit, velocity/camera, combat/projectiles/explosions, items/stateful blocks/fluids, beds/death/respawn, boats, teleport, physical portal delay and multi-lap returns, weather/lightning, hostile navigation and reconnect. Keep exact destination-water and target-arrival assertions. |
| raid | Separate real arm/save and restart/reload, restored first wave, canonical raid/POI state, natural raider folding, vanilla victory/Hero of the Village and normal exits. |
| map-compass | Both seam directions, filled-map pixels and player/banner/item-frame decorations, scale/lock, remove/restore banner, spawn/lodestone/recovery nearest images; normal save/disconnect/reopen persistence. |
| production-lifecycle | Version-native stopped complete 16,384×256 source copy; Overworld/Nether/End/return, save/disconnect, raw teardown and same-process reopen; full Atlas identity retained. Test-only player safety through reopen, without dismissing death as success. |
| curved-objects | Real near/far block entities/entities seated in terrain; interactions/selection/mining alignment; ring correction off/on; dimension controls. Document equivalent older-game fixture subjects. |
| production-render | Noon/dusk/night/rain × tangent/handoff/radial-up (12 captures and per-view metrics), then a separate fresh natural seam/reverse join/both-rim session (4 captures and nonzero motion samples). Exact pose, received chunks, complete published scene, crossfade and screenshot callbacks required. |

Run all four cells unfiltered for release. Fixture count does not substitute for
assertion equivalence. Java unit test totals may differ because APIs/subjects
vary, but every applicable behavioral assertion needs a mapped implementation.
Use exact packaged candidates where the 26.x runner does, and add explicit
packaged client creation/handshake/shader checks; source-development classpaths
alone cannot establish distribution correctness.

### Carry the final acceptance-tooling repairs too

The published source includes qualification changes beyond gameplay features:

- Protect only registered lifecycle fixture players in actual Creative mode,
  reapplying mode/abilities to a replacement player on reopen. Layout switching
  uses a fresh server-owned Spectator acknowledgement for each copied save.
  Explicit survival combat/consumption/sleep/death/portal phases remain Survival.
  Death is still a test failure; no healing loop or auto-respawn can hide it.
- The tagged navigation zombie has a fixture-owned non-interruptible MOVE goal
  so random strolling cannot replace its single commanded target. Vanilla path
  computation/following/folding and unrelated LOOK goals remain. Preserve the
  original target, completed-path/arrival requirements and 1,200-tick timeout;
  this is deterministic test input, not a product AI fix or disabled AI.
- Capture readiness requires an arrived camera/rim chunk set and a coherent,
  complete published scene. A still-incomplete preceding crossfade side is not
  a complete view. Ordinary capture readiness must not demand the newest live
  revision or an empty worker queue while harmless updates continue; the special
  immutable-foliage fixture retains its stronger pre-arming drain proof.
- Read the complete fresh session across midnight log rotation, retaining dated
  compressed parts and readable combined logs. Corrupt/missing/oversized parts,
  incomplete screenshot callbacks and unsafe process cleanup fail explicitly.
- The cross-platform coordinator retains independent failures, immutable
  per-fixture checkpoints, exact-source resume validation, bounded recognized
  infrastructure retries and distinct composite versus monolithic verdicts.
- Batch weather captures in one client only after rechecking each environment,
  pose, sections and fresh metrics. Keep seam/rims and recovery/lifecycle separate;
  do not reduce deliberate multiplayer/raid settles to make a timing headline.
- Optional NeoForm cache seeds must match authenticated Mojang metadata,
  including the asset index. Copy into private stopped-worker inputs and verify
  actual expensive-node cache hits. Keep cold/warm setup timing separate from
  gameplay performance and total-suite time. Never share writable runtime caches.

These repairs also need older-version adapters and tests. The
[final fixture/cache record](https://github.com/Delaser/RingWorld/blob/1533878fe3a6eab7562830b3f39cbd7caae6d46a/docs/RELEASE_FIXTURE_STABILITY_2026_10_13.md)
supersedes earlier notes that described the navigation interference as unresolved.

## 7. Mandatory extensions beyond the historical ten

Seven additional families are defined in the
[machine-readable plan](../config/backport-1.4-acceptance-plan.json), for
**28 additional cell-family keys**. These are multi-phase gates, not claims
of 28 individual unit tests or executed cases.

1. **atlas-concurrency-checkpoints:** fixed 1/2/4/8 and automatic 4→2→1→2→4;
   integrated FPS cap/focus/pause/expiration; remote FPS cannot throttle server;
   backpressure, fairness, pause/drain/cancel/replacement and ticket failure;
   concurrent writes, sustained edits, injected allocation/serialization/write/
   replacement/verify failure; shutdown while copying/writing; no starvation.
2. **scheduled-save-lighting:** normal/catch-up/pressure cleanup; autosave
   persisted before explicit flush, forced save and restart; no delayed location
   check starvation; real light-map wrapper branching/cache/value isolation;
   clients/Nether/End vanilla control; compare allocating/saving and GC profiles.
3. **exterior-building-visibility:** two clients, both Z rims and periodic X;
   block/bed/bucket acceptance/rejection and unchanged inventories; non-operator
   and stale fingerprint denial; world setting/structures survive reconnect;
   actual extraction, range rejection, falling and ordinary void damage.
4. **projection-sky-wall-water:** correction off/on and 1/3/8 ranges at legal
   extremes/above build height; moving and stationary culling changes; geometry
   joins/decay/thickness/all LODs; altitude handoff; finite clouds/horizon;
   floating/natural/support edits; six underwater/reload states; early streaming,
   foliage recompilation and Fabulous noon/rain at 28 chunks. Safe-small seam/rims
   at 6/12/28, production 16, plus widths such as 416 and non-power-of-two C.
5. **version-upgrade-rejection:** copied existing 1.21.1 Beta/local 1.3 saves to
   1.4; retain canonical chunks/inventories/seed/dimensions/mapping/policies;
   incompatible/corrupt/old Atlas rebuild; old clients, absent mod, wrong loader,
   malformed codecs, stale world packets and ordinary non-RingWorld saves reject
   cleanly. On new 1.20.1, test fresh/reopen and applicable legacy/cache inputs;
   do not invent a previous published 1.20.1 RingWorld release to upgrade from.
6. **memory-performance-soak:** paired controlled benchmarks below, Large at
   four-GiB dedicated heap, three live healthy clients, partial and complete
   Atlas, repeated revision/reload/layout changes and at least 60 minutes of
   mixed travel/generation/building. Ten disconnect/reopens track heap/native/GPU
   resource and ticket/queue retirement. No unrelated builds during sampling.
7. **render-core-isolation:** vanilla renderer/state/resource lifecycle, foreign
   renderer absent, dedicated classloading, immutable visibility snapshots,
   shader anchor/contract checks and dimension controls. Optional renderer trials
   are separately pinned and reported; a vanilla acceptance pass does not certify
   Sodium, Embeddium, Iris, Nvidium, Vulkan, Create or an arbitrary modpack.

If experimental Sodium is included in a backport's feature claim, its applicable
loader/version must additionally pass no-mod paired controls, seam/rims,
correction toggles/resource reload, sky/weather/dimension return, curved objects,
six submerged states and a dedicated two-client run. Build a distinct conditional
matrix; never bundle third-party shader implementations or claim compatibility
from detection alone.

## 8. Performance acceptance and reliability standards

The historical 26.x renderer verifier requires nonzero frame samples and valid
captures, **not a fixed frame budget**. A functional full-suite PASS alone does
not answer this request's performance requirement. The following are proposed
backport acceptance criteria to establish before final runs, not retroactive
claims about published 26.x metrics.

### Controlled measurements

- Record CPU/GPU/driver, OS, Java/GC, heap, render/simulation distance, frame cap,
  resolution, graphics mode, LOD, correction setting, exact mod hashes, world
  inventory, camera path, seed and Atlas state. Keep baseline/candidate identical
  within each Minecraft/loader cell. Use warmup and at least three paired repeats;
  alternate order and retain raw data. Cold startup/transfer are separate phases.
- Measure server tick mean/P50/P95/P99/max, >50/>100/>250/>500 ms counts,
  checkpoint owner max/copy total/worker time, queue/ticket/backlog convergence,
  allocation, GC events/pauses, post-GC live heap, RSS/native/GPU buffers,
  transfer bytes/completion, throughput and client frame mean/P95/P99/max.
- Reproduce the Large-server workload: stopped 32,768×512 native source,
  four-GiB server heap, three healthy clients, view 28/simulation 8; 60 s settle,
  two 45 s exterior/60 s centre movement cycles, then ordinary-save waiting.
  Independently read an edit from disk before explicit flush and again after
  restart. Extend sampling to observe a real scheduled save, not just a timer
  chosen too short for that Minecraft version.
- Checkpoint comparison warms both old synchronous and new paths, then six
  alternating AB/BA pairs on a disposable file with identical complete content.
  Keep that deliberate synchronous stress probe out of ordinary-play/soak data.
  Do not compare 26.x's published millisecond figures directly to another CPU/JVM.

### Release gates

- Zero crashes, watchdogs, OOM, lost inventories/blocks/settings, corrupt final
  caches, stale-world rendering, unsafe coverage frames or unresolved assertions.
  Normal shutdown/reopen and required durable cancellation must complete within
  their audited deadlines. Missing measurements or killed/dead clients cannot pass.
- Warm ordinary Small/Medium gameplay aims at 20 TPS: mean tick <50 ms and
  P95 <=50 ms. Stress windows are reported separately; no claim that every
  loading or GC tick is below 50 ms. Sustained inability to keep up at the
  documented default profile blocks release or narrows the supported profile.
- For a cell with an established functional baseline, reject reproducible
  regressions >10% in P95 or >15% in P99 tick/frame time across three paired
  runs, or increased severe-stall frequency, unless the difference is traced to
  host variation and rerun. A one-off maximum is investigated, not averaged away.
  First 1.20.1 acceptance establishes its own measured reference from a correct
  prototype; 26.x timing is context, not a cross-version performance promise.
- No recurring multi-second gameplay-thread checkpoint encoding/compression;
  all such I/O stays off-thread. Copy/capture budgets remain soft: separately
  report over-budget single operations. Recurring checkpoint-attributed steps
  >50 ms require diagnosis; recurring >100 ms after warmup is a release blocker.
- No full-compaction/OOM regression introduced by ordinary cleanup/light-map
  publication in the controlled four-GiB workload; no continuing post-GC heap,
  native/GPU resource, ticket or queue growth after workload quiescence/reopens.
  Compare retained resources with known caches and measurement error rather
  than inferring a leak solely from a high process RSS.
- Fixed/auto admission must make progress and back off as specified. Complete
  progress queries must not repeatedly scan all cells per player. Partial Large
  status/edits also need measurement; the complete shortcut does not prove their
  cost is acceptable. No throughput claim without measured elapsed work.

On failure, retain the attempt, classify host/harness/product cause, make a
focused correction, run the actual failed gate, then freeze new artifacts and
repeat required full coverage. Never weaken arrival, coverage, revision, save
or image assertions to reach green. Infrastructure retries are bounded and
separate; gameplay assertion failures are not infrastructure by default.

## 9. Self-checking execution and evidence

The old local scripts and current 26.x coordinators cannot simply consume a
1.20.1 manifest: version parsing, Java 25 requirements, allowed loaders,
installer hosts, tasks and source/disk formats are guarded. Implement an explicit
backport profile/runner without weakening those established 26.x checks.

Required runner behavior:

1. Read an exact per-cell manifest: game/JDK/build JVM, dependency hashes,
   installer/Mojang hashes, loader, build root, resource ABI, protocols, data
   formats, geometry limits, port and immutable input-world inventory.
2. Validate clean pushed source and strict dependencies; build/freeze once per
   cell and rehash before every install/launch. Diagnostic source builds remain
   non-publishable. Stage from that exact candidate or prove narrowly reviewed
   descriptor-only equivalence; rerun packaged checks on the final exact file.
3. Allocate fresh runtime, ports, Gradle user/project caches and output paths.
   Use supported read-only cache seeds only as acceleration. Probe child-tree
   timeout cleanup and true graphical availability first. Windows long paths,
   hidden hardware OpenGL, midnight log rollover and screenshot callbacks are
   explicit host contracts. Serialize heavy graphical/performance work.
4. Write immutable per-phase/per-cell terminals and a complete expected-key
   aggregate. Require ordered markers, nonzero samples, decodable/hash-bound PNGs,
   exact protocol/hash, normal exit, source/world immutability and independent
   persisted-state evidence. Process exit zero alone never yields PASS.
5. Continue independent cases after a classified safe failure and retain timing;
   source/identity or unsafe cleanup failures stop affected work. Resume only
   with the same source, candidates, manifests, input worlds and verified prior
   artifacts. Complete repaired/resumed coverage is explicitly composite; retain
   failed attempts and do not relabel it monolithic. A source change starts a
   new qualification identity, including a shipped test-fixture change.
6. Final aggregation requires all 40 core and 28 feature-family keys, conditional
   compatibility keys where claimed, performance/soak verdicts, platform scope,
   distribution checks and visual review. NOT_RUN/INCOMPLETE/FAIL all block acceptance.

Use the existing runtime tasks as adapters while porting. Afterwards provide
one documented command for plan/dry-run, quick and full acceptance per target,
and a serial all-backport coordinator. Proposed interface (not implemented yet):

```text
python scripts/run_backport_acceptance.py --manifest config/backports/1.21.1.json --tier quick --all
python scripts/run_backport_acceptance.py --manifest config/backports/1.20.1.json --tier quick --all
python scripts/run_backport_acceptance.py --tier full --all-backports --production-worlds INPUT_INDEX --execute
```

The planning contract is available now:

```text
python scripts/validate_backport_acceptance_plan.py
python -m unittest scripts.test_validate_backport_acceptance_plan
```

It verifies coverage, source identity, target Java, data contracts and honest
pending status. It does not execute Minecraft. `--require-executable` must fail
until concrete pins, runners and input worlds exist; a valid plan must never
print or imply runtime qualification PASS.
Its regression tests are included in the Windows/Linux static workflow; CI
validation of the planning contract remains separate from native acceptance.

## 10. Platform, upgrade and release boundaries

- Run the full four-cell acceptance on the primary Windows GPU host. Add native
  Linux dedicated/container and macOS graphical/package checks before advertising
  those platforms; CI/fake-launcher tests alone are not native game evidence.
  A host-only prerequisite failure is INCOMPLETE, not a product PASS.
- Copied-save upgrades are mandatory for the existing 1.21.1 backport even though
  they were excluded from the published 26.x release scope. Preserve original
  file inventories, conflicting inventory recovery and old settings/noise IDs.
- MPL-2.0 metadata and `LICENSE-RINGWORLD.txt`, clean immutable source URL,
  runtime-only jars, correct game/Java/loader dependencies and Fabric API relation
  remain mandatory. No Minecraft, tokens, accounts, test worlds, extra mods,
  development jars or measurement-only probes in uploaded artifacts.
- Review UI and moving visuals, not merely a `CAPTURE COMPLETE` marker. Produce
  paired loader views with input/setting labels, raw captures, frame records and
  an honest known-limitations list. Keep optional renderer claims narrow.
- An acceptable build means feature parity plus performance, persistence,
  multiplayer, rendering and exact packaged-candidate evidence. A successful
  compile, old green suite or source-only launch is insufficient. The plan's
  diagnosis/fix/retest loop continues until those criteria are met; currently
  neither 1.4 backport is at that acceptance stage.
