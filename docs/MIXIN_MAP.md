# Mixin ownership map

The active mixins target Minecraft 26.1.2 under official Mojang mappings. The
frozen 1.21.11 implementation remains available at `mc-1.21.11-final`; port
audit evidence is tracked in `PORTING_26_1_AUDIT.md`. Use this map to locate
the owner of a behavior before adding another injection.

The authoritative mixin lists are:

- `src/main/resources/ringworld.mixins.json`
- `src/main/resources/ringworld.client.mixins.json`

If a mixin is added, removed, or renamed, update those files and this table in
the same change.

Both Fabric and the NeoForge 26.1.2.87 module package the shared server and
client mixin configurations. NeoForge passes the graphical projection,
seam/rim, layout/lifecycle, dedicated multiplayer, worldgen/structure, and
headless-atlas runtime gates. Shared mixin changes still require both-loader
review because one loader's runtime success does not validate the other.

## Common/server mixins

| Mixin | Vanilla target | Owned behavior | Main risk |
| --- | --- | --- | --- |
| `BoundedRegionArrayMixin` | `StaticCache2D` | Projects seam aliases into a local worldgen region while retaining canonical holders | Wrong bounds logic can break all feature neighbour access |
| `ChunkGeneratorMixin` | `ChunkGenerator` | Skips exterior features, clears spillover, and installs the finite rim after features | Mutating live chunks or building the rim before features causes hangs/overwrites |
| `ChunkGeneratorLocateMixin` | `ChunkGenerator` | Projects all Overworld locate results to their nearest periodic image and mirrors vanilla's canonical presence/reference lookup for a saved forced monument candidate | Loading an alias chunk duplicates starts; ignoring `skipKnownStructures` breaks explorer-map semantics |
| `ChunkGeneratorStructureStateMixin` | `ChunkGeneratorStructureState` | Replaces stronghold rings and resolves/binds the saved exact built-in monument candidate using periodic biome validation | Enabling without saved policy changes old worlds; salt/class matching or flat climate sampling can force an invalid start |
| `StrongholdStructureMixin` | `StrongholdStructure.generatePieces` | Minimally translates the completed terrain-adjusted guaranteed-stronghold graph into canonical X and finite Z | Omitting the 12-block terrain-adjustment envelope can leave the reported structure start outside a 256-block band; applying it without saved policy mutates legacy layouts |
| `StructurePlacementMixin` | `StructurePlacement.isStructureChunk` | Admits only the compatible saved monument candidate for only its registry-bound placement, while retaining frequency and exclusion checks | A broad override duplicates or relocates unrelated random-spread structures |
| `OceanMonumentStructureMixin` | `OceanMonumentStructure.findGenerationPoint` | Replaces the flat surrounding-biome sampler with the generator's periodic climate sampler only in RingWorld | A flat sampler can approve/reject terrain different from the generated chunk |
| `StructureBiomeMixin` | `Structure.isValidBiome` | Makes final structure anchor-biome validation use periodic climate only for the RingWorld generator | A global redirect changes Nether/End and ordinary worlds; omitting it leaves forced starts invalid |
| `ChunkNoiseSamplerMixin` | `NoiseChunk` | Substitutes the Overworld periodic noise router during sampler construction | Global substitution would curve Nether/End or break aquifers |
| `ChunkPosDistanceLevelPropagatorMixin` | `ChunkTracker` | Joins chunk ticket/simulation propagation at the two X edges | A missed context leaks non-canonical holders or unloads seam neighbours |
| `ChunkRegionMixin` | `WorldGenRegion` | Canonicalizes worldgen block/entity/tick writes and selects local holder aliases | Canonicalizing before vanilla radius validation changes feature locality |
| `DensityCoordinateConsumerMixin` | Noise/shift density leaf implementations | Tags density functions that truly consume horizontal coordinates | Tagging caches/interpolators breaks sampler identity and terrain |
| `EntityDistanceMixin` | `Entity` | Uses shortest periodic X for entity distance overloads | Must remain Overworld-only and preserve Y/Z |
| `EntityNavigationMixin` | `PathNavigation` | Projects AI path targets into the image nearest the mob and shifts active paths/timeout caches by the exact canonical fold delta | Canonical targets without local projection cause full-ring paths; stale old-chart nodes stop a mob after wrapping |
| `EntityTrackingSectionMixin` | `EntitySection` | Compares canonicalized entity bounds during section queries | Duplicate/missing entity results at seam |
| `ExplosionImplMixin` | `ServerExplosion` | Projects exposure rays and knockback direction to nearest entity image | Visual explosion can work while damage/impulse remains wrong |
| `EyeOfEnderMixin` | `EyeOfEnder` | Shifts the Eye's transient guidance target by the exact canonical entity-fold delta | A stale target makes a seam-crossing Eye fly beyond its intended local signal point |
| `LevelMixin` | `Level.setBlock(BlockPos, BlockState, int, int)` | Enqueues successful server block mutations for bounded canonical atlas-cell recapture | Sampling inline or enqueueing outside the RingWorld service creates stalls or a second writer; the Overworld service guard must remain authoritative |
| `MapItemMixin` | `MapItem.update` | Keeps filled-map holder sampling, chunk selection, and banner checks in the map centre's nearest periodic X image | Sampling an alias chunk or leaving the holder on the far flat-X side produces blank/incorrect seam pixels; the one saved map centre may be seam-equivalent to C after vanilla grid rounding |
| `MapItemSavedDataMixin` | `MapItemSavedData.addDecoration`, `toggleBanner`, `tickCarriedBy` | Calculates player/banner/frame offsets and the banner in-map gate through the map centre's nearest image, then realigns saved markers once a world is available | Persisting a presentation marker would create a second map coordinate; only transient decoration offsets may move |
| `MinecraftServerMixin` | `MinecraftServer` | Supplies validated bootstrap geometry only during first-world spawn selection through loader-neutral `RingSpawnBounds`, and exposes the authoritative read-only dimension storage path | Spawn redirection must remain creation-scoped; saved worlds must not read bootstrap geometry, and storage consumers must not reconstruct dimension folders |
| `MultiTickSchedulerMixin` | `WorldGenTickAccess` | Canonicalizes generation-time block/fluid scheduled ticks | Tick key must match canonical block storage |
| `NoiseChunkGeneratorMixin` | `NoiseBasedChunkGenerator` | Attaches geometry, skips exterior density/surface/carvers, scopes the periodic router to biome, real-terrain, and shared base-height/base-column sampler construction, and canonicalizes query X before private height-query interpolation | The private sampler factory, `iterateNoiseColumn` X-boundary normalization and constructor redirect, and biome climate call must remain paired without intercepting unrelated router consumers; a raw alias or missed query sampler floats heightmap-projected structures |
| `PlayerInteractionDistanceMixin` | `Player` | Periodic block use, entity interaction, and attack reach | Server authority; client-only fixes do not restore combat |
| `ProjectileUtilMixin` | `ProjectileUtil` | Raycasts ordinary and piercing projectiles against projected seam hitboxes | Must return the canonical entity while testing its nearest box |
| `PathfindToRaidGoalMixin` | `PathfindToRaidGoal` | Applies periodic village membership and aims raider pathfinding at the raid centre's nearest presentation image | The saved raid centre remains canonical; only the transient AI target may use a presentation image |
| `RaidMixin` | `Raid` | Makes centre relocation, village probes, raider retention, wave height/chunk readiness, and returned wave positions periodic and canonical | Wave readiness crossing X=0/C must split into canonical windows; never request or persist alias chunks |
| `RaiderMoveThroughVillageGoalMixin` | `Raider.RaiderMoveThroughVillageGoal` | De-duplicates HOME POIs across canonical seam windows, applies periodic arrival checks, and aims transient movement at the nearest POI image | `poiPos`, the visited list, and POI storage remain canonical; only path calculations may use a presentation image |
| `RaidsMixin` | `Raids` | Unions village POIs across three query images for raid-centre averaging, then canonicalizes raid creation | Synthetic nearest-image POI records are read-only averaging inputs; never insert them into `PoiManager` |
| `RaidsAccessor` | `Raids` | Exposes the saved raid values only to `ServerWorldMixin` so it can select the nearest active raid periodically | Iteration must preserve vanilla's active-only, strict-distance selection; never mutate the map through this accessor |
| `ServerChunkLoadingManagerMixin` | `ChunkMap` | Canonical generation regions, periodic watch filters, watch diffs, tracking/tick distance | Central chunk lifecycle patch; regressions cause hangs or duplicate holders |
| `ServerChunkManagerMixin` | `ServerChunkCache` | Canonical chunk gets, holders, tickets, forced chunks, and propagation context | Lowest shared ownership boundary for the finite chunk graph |
| `ServerEntityManagerListenerMixin` | `PersistentEntitySectionManager.Callback` | Keeps moving entities indexed in canonical sections | A seam-crossing entity can become unqueryable if its section key is stale |
| `ServerEntityManagerMixin` | `PersistentEntitySectionManager` | Canonicalizes new/disk entities, tracking status, loaded/tick keys, and initial section, and queues missing seam entity reads directly | Save/reconnect and ticking depend on this; `updateChunkStatus` can downgrade a `TICKING` seam chunk to `TRACKED`, freezing entities after a fold |
| `ServerEntityTrackerMixin` | `ChunkMap.TrackedEntity` | Uses periodic distance and retains an existing pairing through one pending canonical-fold chunk delivery transition | Initial pairing must still require chunk readiness; retaining outside the periodic watch window leaks entities |
| `ServerPlayNetworkHandlerMixin` | `ServerGamePacketListenerImpl` | Validates continuous player/vehicle seam movement, folds canonical without correction, and exposes a narrow reset for server-owned pose changes | Anti-cheat baselines and passengers must shift with the source chart; a bed or other server pose must not leave a baseline one circumference away |
| `ServerPlayerSleepMixin` | `ServerPlayer.isReachableBedBlock`, `startSleeping` | Uses vanilla's bed reach box with nearest-periodic X and realigns movement baselines after the sleeping pose is applied | Bed positions must stay canonical; changing Y/Z limits or applying outside the RingWorld Overworld changes vanilla behavior |
| `ServerWorldMixin` | `ServerLevel` | Canonical loaded/tick checks, constructor-tail scheduler geometry attachment, nearest-periodic simulation eligibility fallback in the private 26.1 `lambda$tick$0(TickRateManager,ProfilerFiller,Entity)` entity consumer, entity region load, proximity delivery, and nearest-active raid lookup | The constructor bridge records an explicit-headless pre-load rejection and rethrows its original settings failure; it must never create bootstrap geometry for an ordinary copied world. Raid lookup must retain vanilla's active-only 96-block threshold and strict nearest tie behavior. The entity eligibility call is inside the synthetic tick consumer rather than `tick` itself, and the fallback must never become global forced ticking |
| `WorldEntityLookupMixin` | `Level` | Splits seam-crossing entity query boxes into canonical windows | Must suppress duplicates and scan full-circumference boxes once |
| `WorldTickSchedulerMixin` | `LevelTicks` | Canonicalizes runtime block/fluid tick positions | A tick stored under an alias can never find its canonical block |

## Client mixins

### `MinecraftMixin`

- Targets: `Minecraft.disconnect(Screen, boolean, boolean)` and
  `Minecraft.clearClientLevel`.
- Purpose: clears static RingWorld geometry, atlas, and GPU surface state when
  a local or remote world is torn down. The three-argument disconnect path
  owns integrated-world exit; `clearClientLevel` covers the separate remote
  teardown path. Fabric's play-connection event remains a redundant network
  lifecycle hook.
- Coordinate domain: session ownership boundary; no coordinate conversion.
- Loader note: the target is a Minecraft lifecycle method rather than a
  Fabric event, so the same mixin is intended to remain valid on NeoForge.

| Mixin | Vanilla target | Owned behavior | Main risk |
| --- | --- | --- | --- |
| `ChunkRenderingDataPreparerMixin` | `SectionOcclusionGraph` | Wraps terrain collection/update frusta in `CurvedRingFrustum` and disables flat six-face section occlusion in the RingWorld Overworld | Restoring smart occlusion hides terrain that curvature bends into view; disabling other frustum/distance checks would be too broad |
| `CompassAngleStateMixin` | `CompassAngleState` | Validates and points spawn, lodestone, and recovery compass targets through the nearest periodic image for their holder | Flat validity at an exact seam-equivalent target produces a false zero-vector bearing; saved targets remain canonical and locator pointers are outside this slice |
| `ChunkBuilderBuiltChunkMixin` | `SectionRenderDispatcher.RenderSection` | Treats intentionally absent exterior-Z neighbours as ready so finite rim sections can mesh | Private renderer method; never bypass readiness for an interior neighbour |
| `ClientChunkMapMixin` | `ClientChunkCache.Storage` | Exposes centre and full clear for disjoint chart re-keying | Clearing on small moves causes visible reload churn |
| `ClientConnectionMixin` | `Connection` | Canonicalizes outbound block break/use, sign update, pick-block, and block-entity tag-query packets | Missing a packet type makes visible seam blocks non-interactive or edits the wrong canonical block |
| `ClientPlayNetworkHandlerMixin` | `ClientPacketListener` | Projects canonical chunks, entities, minecart steps, blocks/sign screens, damage/look/effects, and explicit teleport targets into the nearest chart | Largest client packet surface; every Minecraft update must repeat the positional-packet audit |
| `ConfirmScreenAccessor` | `ConfirmScreen` | Exposes the affirmative button only to the opt-in atlas UI acceptance fixture so it exercises the real confirmation callback | Test-only accessor; production code must not use it to bypass player confirmation |
| `CreateWorldScreenMixin` | `CreateWorldScreen.init`, redirect of `HeaderAndFooterLayout.addToFooter(LayoutElement)` | Adds the immutable RingWorld layout editor and current C×W summary to the managed Create/Cancel footer row; the editor invokes its UI-local refresh hook after saving | The exact 26.1 layout call is required; a separately positioned button overlaps vanilla controls at GUI scale 4. The reused parent screen must refresh without reinitializing its layout. Changes bootstrap defaults for the next new world only; saved settings remain authoritative |
| `CreateWorldScreenInvoker` | `CreateWorldScreen` | Invokes level creation for the opt-in local automated harness | Test-only; must not auto-create when `testMode=false` |
| `EntityRenderManagerMixin` | `EntityRenderDispatcher` | Curved translation and tangent rotation for entity models | Transform must match terrain and leave local camera controls unchanged |
| `LevelRendererMixin` | `LevelRenderer.submitBlockEntities`, `submitBlockDestroyAnimation`, and `renderHitOutline` | Applies the shared curved anchor and tangent pose to block entities, breaking overlays, and selection outlines | These passes bypass the terrain shader; a flat translation makes them visibly detach from curved blocks as distance changes |
| `GlobalSettingsMixin` | `GlobalSettingsUniform.<init>` and `update(..., Vec3, ...)` | Extends Globals with named layout, vertical, render-distance, handoff, detail/reveal, haze, cloud-fade, and visual-profile fields | Shader ABI extension; std140 field order, buffer sizing, and the 26.1 extracted camera-position parameter are version-sensitive |
| `LivingEntitySleepingPositionMixin` | `LivingEntity.getSleepingPos()` | Projects only the local player's replicated canonical bed position into the nearest presentation chart before vanilla's client sleeping callback, wake-up, orientation, and bed lookup consume it | Mapping server data or another entity's bed would violate the single canonical storage plane; this must remain a client-only local-player return mapping |
| `MinecraftMixin` | `Minecraft.disconnect(Screen, boolean, boolean)` and `clearClientLevel` | Clears geometry, atlas, and GPU surface state on both integrated and remote world teardown paths | Missing either path can leak the previous world's static client state into an in-process reopen |
| `PauseScreenMixin` | `PauseScreen.init` | Adds a separate top-right `RingWorld Map` entry only after RingWorld geometry and atlas identity are acknowledged | Adding another row to vanilla's centre stack overlaps Save and Quit at GUI scale 4; ordinary worlds and other dimensions must expose no button |
| `PlayerPositionDebugHudEntryMixin` | `DebugEntryPosition` | Replaces F3 position section with canonical Ring coordinates and atlas state | Debug display only; never use it as storage logic |
| `SkyRenderingMixin` | `SkyRenderer` | Small fixed ring-centred sun, time-based sun tint/intensity, no moon, stationary stars, and complete-ring texture invocation | `renderSun` constants and dynamic colour arguments are version-sensitive |

## Non-mixin owners

Several important behaviors are deliberately implemented with loader events or
ordinary helpers:

| Owner | Responsibility |
| --- | --- |
| `RingWorldMod` | Common initialization |
| `RingWorldServer` | World lifecycle, end-tick canonical folding, boundary migration, smoke fixtures |
| `RingWorldStorageAccess` | Read-only bridge from a `ServerLevel` to Minecraft's authoritative per-dimension storage root |
| `RingWorldNetworking` / `RingHandshakeTracker` | Payload registration, exact required-channel contract, mandatory acknowledgement deadline, request gating, and disconnect cleanup |
| `RingTerrainAtlasServer` | Atlas generation, persistence, and tile streams |
| `HeadlessPrewarmCoordinator` / `HeadlessPrewarmEvidenceFiles` / loader adapters | Loader-neutral explicit headless-prewarm orchestration and evidence hygiene with thin Fabric/NeoForge lifecycle bridges; records constructor-tail `REJECTED` evidence without identity when settings are unavailable, gates joins, checkpoints/reports/saves/stops, but never owns another scheduler or atlas writer |
| `RingWorldClient` / `NeoForgeRingWorldClient` | Loader client lifecycle and payload receivers; Fabric and NeoForge respectively configure the shared transport/session, cache path, test hooks, and render-pipeline registration |
| `RingClientPayloadTransport` / `RingWorldClientSession` | Shared client outbound-payload capability/delivery boundary and per-session atlas/GPU/geometry teardown |
| `RingProjectionCaptureClient` / `RingVisualParityCaptureClient` / `LayoutSwitchTestClient` / `ProductionLifecycleTestClient` | Loader-neutral graphical projection, seam/rim, stale-session, dimension-transition, and reopen gates driven by loader-owned client tick/render events |
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
