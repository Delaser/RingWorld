# Mixin ownership map

The active branch is compiling against Minecraft 26.1.2, but the mixin source
still represents the validated Minecraft 1.21.11 behavior under official
Mojang mappings until each row is explicitly ported. Candidate 26.1.2 targets
and confidence are tracked in `PORTING_26_1_AUDIT.md`. Use this map to locate
the owner of a behavior before adding another injection.

The authoritative mixin lists are:

- `src/main/resources/ringworld.mixins.json`
- `src/main/resources/ringworld.client.mixins.json`

If a mixin is added, removed, or renamed, update those files and this table in
the same change.

## Common/server mixins

| Mixin | Vanilla target | Owned behavior | Main risk |
| --- | --- | --- | --- |
| `BoundedRegionArrayMixin` | `StaticCache2D` | Projects seam aliases into a local worldgen region while retaining canonical holders | Wrong bounds logic can break all feature neighbour access |
| `ChunkGeneratorMixin` | `ChunkGenerator` | Skips exterior features, clears spillover, and installs the finite rim after features | Mutating live chunks or building the rim before features causes hangs/overwrites |
| `ChunkNoiseSamplerMixin` | `NoiseChunk` | Substitutes the Overworld periodic noise router during sampler construction | Global substitution would curve Nether/End or break aquifers |
| `ChunkPosDistanceLevelPropagatorMixin` | `ChunkTracker` | Joins chunk ticket/simulation propagation at the two X edges | A missed context leaks non-canonical holders or unloads seam neighbours |
| `ChunkRegionMixin` | `WorldGenRegion` | Canonicalizes worldgen block/entity/tick writes and selects local holder aliases | Canonicalizing before vanilla radius validation changes feature locality |
| `DensityCoordinateConsumerMixin` | Noise/shift density leaf implementations | Tags density functions that truly consume horizontal coordinates | Tagging caches/interpolators breaks sampler identity and terrain |
| `EntityDistanceMixin` | `Entity` | Uses shortest periodic X for entity distance overloads | Must remain Overworld-only and preserve Y/Z |
| `EntityNavigationMixin` | `PathNavigation` | Projects AI path targets into the image nearest the mob | Canonical targets without local projection cause full-ring paths |
| `EntityTrackingSectionMixin` | `EntitySection` | Compares canonicalized entity bounds during section queries | Duplicate/missing entity results at seam |
| `ExplosionImplMixin` | `ServerExplosion` | Projects exposure rays and knockback direction to nearest entity image | Visual explosion can work while damage/impulse remains wrong |
| `MinecraftServerMixin` | `MinecraftServer` | Constrains first-world spawn search to the finite Z interior | Can affect non-Overworld spawn setup if guard regresses |
| `MultiTickSchedulerMixin` | `WorldGenTickAccess` | Canonicalizes generation-time block/fluid scheduled ticks | Tick key must match canonical block storage |
| `NoiseChunkGeneratorMixin` | `NoiseBasedChunkGenerator` | Attaches geometry, skips exterior density/surface/carvers, scopes the periodic router to biome and terrain sampler construction | The private sampler factory and biome climate call must remain paired without intercepting unrelated router consumers |
| `PlayerInteractionDistanceMixin` | `Player` | Periodic block use, entity interaction, and attack reach | Server authority; client-only fixes do not restore combat |
| `ProjectileUtilMixin` | `ProjectileUtil` | Raycasts ordinary and piercing projectiles against projected seam hitboxes | Must return the canonical entity while testing its nearest box |
| `ServerChunkLoadingManagerMixin` | `ChunkMap` | Canonical generation regions, periodic watch filters, watch diffs, tracking/tick distance | Central chunk lifecycle patch; regressions cause hangs or duplicate holders |
| `ServerChunkManagerMixin` | `ServerChunkCache` | Canonical chunk gets, holders, tickets, forced chunks, and propagation context | Lowest shared ownership boundary for the finite chunk graph |
| `ServerEntityManagerListenerMixin` | `PersistentEntitySectionManager.Callback` | Keeps moving entities indexed in canonical sections | A seam-crossing entity can become unqueryable if its section key is stale |
| `ServerEntityManagerMixin` | `PersistentEntitySectionManager` | Canonicalizes new/disk entities, tracking status, loaded/tick keys, and initial section | Save/reconnect and entity ticking depend on this |
| `ServerEntityTrackerMixin` | `ChunkMap.TrackedEntity` | Uses periodic distance when deciding player tracking | Remote players/entities vanish across seam without it |
| `ServerPlayNetworkHandlerMixin` | `ServerGamePacketListenerImpl` | Validates continuous player/vehicle seam movement and folds canonical without correction | Anti-cheat baselines and passengers must shift with the source chart |
| `ServerWorldMixin` | `ServerLevel` | Canonical loaded/tick checks, nearest-periodic simulation eligibility fallback, entity region load, proximity delivery | Several unrelated world-facing ownership checks converge here; never turn the fallback into global forced ticking |
| `WorldEntityLookupMixin` | `Level` | Splits seam-crossing entity query boxes into canonical windows | Must suppress duplicates and scan full-circumference boxes once |
| `WorldTickSchedulerMixin` | `LevelTicks` | Canonicalizes runtime block/fluid tick positions | A tick stored under an alias can never find its canonical block |

## Client mixins

| Mixin | Vanilla target | Owned behavior | Main risk |
| --- | --- | --- | --- |
| `ChunkRenderingDataPreparerMixin` | `SectionOcclusionGraph` | Wraps terrain collection/update frusta in `CurvedRingFrustum` and disables flat six-face section occlusion in the RingWorld Overworld | Restoring smart occlusion hides terrain that curvature bends into view; disabling other frustum/distance checks would be too broad |
| `ChunkBuilderBuiltChunkMixin` | `SectionRenderDispatcher.RenderSection` | Treats intentionally absent exterior-Z neighbours as ready so finite rim sections can mesh | Private renderer method; never bypass readiness for an interior neighbour |
| `ClientChunkMapMixin` | `ClientChunkCache.Storage` | Exposes centre and full clear for disjoint chart re-keying | Clearing on small moves causes visible reload churn |
| `ClientConnectionMixin` | `Connection` | Canonicalizes outbound block break/use packets | Missing a packet type makes visible seam blocks non-interactive |
| `ClientPlayNetworkHandlerMixin` | `ClientPacketListener` | Projects canonical chunks, entities, blocks, effects, and explicit teleport targets into the nearest chart | Largest client packet surface; new positional packets need an audit |
| `CreateWorldScreenMixin` | `CreateWorldScreen` | Adds the immutable RingWorld layout editor entry point and current C×W summary | Changes bootstrap defaults for the next new world only; saved settings remain authoritative |
| `CreateWorldScreenInvoker` | `CreateWorldScreen` | Invokes level creation for the opt-in local automated harness | Test-only; must not auto-create when `testMode=false` |
| `EntityRenderManagerMixin` | `EntityRenderDispatcher` | Curved translation and tangent rotation for entity models | Transform must match terrain and leave local camera controls unchanged |
| `GlobalSettingsMixin` | `GlobalSettingsUniform` | Extends Globals with named layout, vertical, render-distance, handoff, detail/reveal, haze, cloud-fade, and visual-profile fields | Shader ABI extension; std140 field order and buffer sizing are version-sensitive |
| `PlayerPositionDebugHudEntryMixin` | `DebugEntryPosition` | Replaces F3 position section with canonical Ring coordinates and atlas state | Debug display only; never use it as storage logic |
| `SkyRenderingMixin` | `SkyRenderer` | Small fixed ring-centred sun, time-based sun tint/intensity, no moon, stationary stars, and complete-ring texture invocation | `renderSun` constants and dynamic colour arguments are version-sensitive |

## Non-mixin owners

Several important behaviors are deliberately implemented with Fabric events or
ordinary helpers:

| Owner | Responsibility |
| --- | --- |
| `RingWorldMod` | Common initialization |
| `RingWorldServer` | World lifecycle, end-tick canonical folding, boundary migration, smoke fixtures |
| `RingWorldNetworking` | Payload registration and mandatory handshake |
| `RingTerrainAtlasServer` | Atlas generation, persistence, and tile streams |
| `RingWorldClient` | Client handshake receivers, atlas cache, visual/test hooks |
| `ClientRingState` | Immutable geometry, continuous chart, atlas cache/revision |
| `RingSurfaceTextureRenderer` | Active complete-ring LOD |
| `CurvedRingFrustum` | Exact section-envelope culling |

## Upgrade procedure

For a Minecraft or mappings upgrade:

1. update dependency versions on a dedicated branch;
2. compile and record every failed target;
3. compare descriptors and bytecode semantics, not only names;
4. verify injection ordinals and local variable timing;
5. audit broad redirects; `NoiseChunkGeneratorMixin` no longer uses its old
   wildcard router redirect, and any new wildcard interception needs a
   call-site inventory first;
6. inspect shader imports, Globals layout, and camera-origin conventions;
7. run unit tests;
8. run local world creation and reconnect;
9. run the two-client seam harness;
10. capture visual tests before merging.

Do not silence an injection failure by lowering `defaultRequire` or marking a
critical mixin optional. A clean launch with a missing topology patch is more
dangerous than an explicit startup failure.

The frozen 1.21.11 Mojang baseline used the unnamed
`ServerLevel.method_31420` asynchronous entity-tick lambda. In 26.1.2 the
source-audited `DistanceManager.inEntityTickingRange` call is directly inside
named `ServerLevel.tick`, which is now the active redirect target. No
intermediary-looking identifier remains accepted in active source.
