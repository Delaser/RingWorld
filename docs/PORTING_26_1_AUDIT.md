# Minecraft 26.1.2 source and port audit

Status: S1 audit complete; implementation unresolved

Audit base: `1d7a2b3a756ddd03ee79394d34aec86af6acaeb3`

Target: Minecraft Java 26.1.2, Fabric Loader 0.19.3, Loom 1.17-SNAPSHOT,
Fabric API 0.155.2+26.1.2, Java 25

This document is a source audit, not permission to weaken an injection or a
claim that the port compiles. A candidate marked high confidence means that
the named 26.1.2 class and relevant method body were inspected in Mojang's
official unobfuscated artifact. It does not mean that the final descriptor,
ordinal, local capture, or bytecode injection point has passed Mixin
application and runtime validation.

The topology invariants in [`../AGENTS.md`](../AGENTS.md) and the ownership
rules in [`MINECRAFT_26_1_PORT_PLAN.md`](MINECRAFT_26_1_PORT_PLAN.md) remain
authoritative.

## Sources and method

Primary sources inspected on 2026-07-28:

- Mojang's
  [26.1.2 version metadata](https://piston-meta.mojang.com/v1/packages/8228875b88ad88b4bc0dc7a2afbc6903bccf93b1/26.1.2.json),
  client jar SHA-1 `4e618f09a0c649dde3fdf829df443ce0b8831e65`,
  and server jar SHA-1 `97ccd4c0ed3f81bbb7bfacddd1090b0c56f9bc51`;
- the official unobfuscated 26.1.2 client and bundled server classes,
  decompiled only to make their source structure searchable;
- Fabric API tag
  [`0.155.2+26.1.2`](https://github.com/FabricMC/fabric/tree/0.155.2%2B26.1.2)
  at commit `f9468776b662dd2ab7875e9cdcdf2b653171309d`;
- Fabric's
  [26.1 porting guide](https://docs.fabricmc.net/develop/porting/index),
  [mapping migration guide](https://docs.fabricmc.net/develop/porting/mappings/),
  [26.1 announcement](https://www.fabricmc.net/2026/03/14/261.html), and
  [26.1.2 example mod](https://github.com/FabricMC/fabric-example-mod/tree/26.1.2).

The 1.21.11 Yarn targets were correlated to Mojang names through the official
1.21.11 Mojang mappings, Fabric intermediary 1.21.11, and Yarn
`1.21.11+build.6`. Every candidate class below was then checked in the 26.1.2
artifact. Relevant method bodies were inspected for each row; unresolved
items are called out rather than inferred from names alone.

## Toolchain and dependency changes

| Area | 1.21.11 baseline | 26.1.2 requirement | Audit note |
| --- | --- | --- | --- |
| Gradle | Wrapper 9.5.0 | Example mod uses 9.5.1 | Update the wrapper before Phase 2 and retain checksum validation. |
| JVM | Java 21 | Java 25 | Gradle runtime, compiler release, Loom launches, packages, and mixin compatibility level must all move together. |
| Minecraft | `1.21.11` | `26.1.2` | Keep `fabric.mod.json` exact at first; do not use a broad 26.1 range until the runtime matrix passes. |
| Loader | `0.19.3` | `0.19.3` | Version is unchanged in the official 26.1.2 example at audit time. |
| Loom | `net.fabricmc.fabric-loom-remap` 1.17-SNAPSHOT | `net.fabricmc.fabric-loom` 1.17-SNAPSHOT | The new plugin does not remap Minecraft or mod artifacts. |
| Mappings | Yarn `1.21.11+build.6` | none | First migrate the 1.21.11 sources and every descriptor to Mojang names; then remove the mappings dependency for 26.1.2. |
| Fabric API | `0.141.4+1.21.11` | `0.155.2+26.1.2` | API names now follow Mojang terminology; migration tooling does not perform all Fabric API renames. |
| Dependency configurations | `modImplementation` | `implementation` | Also replace `modCompileOnly`/`modApi` if introduced before Phase 2. |
| Artifacts | remapped-jar assumptions | ordinary `jar` | Packaging and checksums must use the non-remapped artifact. |
| Mod version | `0.1.0` | `0.2.0+mc26.1.2` | Coordinate with packaging; S1 does not change it. |
| Mixin configs | `JAVA_21`, `defaultRequire: 1` | `JAVA_25`, retain `defaultRequire: 1` | Never lower injection requirements to get a launch. |

The example mod still uses split client/common source sets and ordinary
`minecraft`, `implementation` Loader, and `implementation` Fabric API
dependencies. No access widener is currently present; if one is added during
the mapping migration its namespace must be `official`, not `named`.

## Fabric API inventory

The following RingWorld imports were checked against the official Fabric API
tag.

| Current API | 26.1.2 result |
| --- | --- |
| `ClientTickEvents` | Class remains. Mojang client types in callback code still need renaming. |
| `ClientPlayConnectionEvents` | Class and `DISCONNECT` remain. |
| `ClientPlayNetworking` | Class and global receiver/send/canSend surface remain; payload types and vanilla packet/buffer classes use Mojang names. |
| `WorldRenderEvents` in `client.rendering.v1.world` | Renamed and moved to `LevelRenderEvents` in `client.rendering.v1.level`. `END_MAIN` remains but now receives `LevelRenderContext`; drawing/extraction phases are explicit. |
| `PayloadTypeRegistry` | Class remains; play registries are now directional `clientboundPlay()` and `serverboundPlay()`. Codec generic types use Mojang networking names. |
| `ServerPlayConnectionEvents` | Class and `JOIN` remain. Callback vanilla types use Mojang names. |
| `ServerPlayNetworking` | Class and receiver/send/canSend surface remain. |
| `ServerChunkEvents.CHUNK_LOAD` | Event remains, but the callback is now `(ServerLevel, LevelChunk, boolean generated)`. The current two-argument lambda will not compile. |
| `ServerTickEvents.END_WORLD_TICK` | Renamed to `END_LEVEL_TICK`. |
| `ServerWorldEvents` | Renamed to `ServerLevelEvents`; `LOAD` and `UNLOAD` remain with `ServerLevel`. |
| `CommandRegistrationCallback` | Class and three-argument callback remain; command source, registry, permission, and component types use Mojang names. |

The networking wire layout does not need to change merely because the Java
names change. Expected vanilla renames include:

- `RegistryByteBuf` -> `RegistryFriendlyByteBuf`;
- `PacketCodec` -> `StreamCodec`;
- `PacketCodecs` -> `ByteBufCodecs`;
- `CustomPayload`/`CustomPayload.Id` -> `CustomPacketPayload`/
  `CustomPacketPayload.Type`;
- `Identifier` -> `ResourceLocation`;
- `getId()` -> `type()`.

Keep `settings_v2` only if the resulting `StreamCodec` emits exactly the same
field order and encodings. A code-level rename is not a protocol generation
change; any serialized-layout change requires `settings_v3` and a synchronized
protocol identity test.

## Mixin inventory

The authoritative JSON files contain 24 common/server entries and 11 client
entries: exactly 35 mixins. All 35 appear once below.

Confidence meanings:

- **High**: candidate class and the relevant method body/call site exist with
  the same behavioral role.
- **Medium**: class and behavior exist, but descriptors, ownership, or call
  placement changed enough to require a new injection design.
- **Low**: the old injection point was removed, split, moved, or was already a
  fragile broad/private target; source inspection found only a behavioral
  successor.

### Common and server mixins (24)

| # | Mixin | 1.21.11 Yarn target and injection | Candidate 26.1.2 Mojang target | Confidence | Principal risk | Required validation |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | `BoundedRegionArrayMixin` | `BoundedRegionArray`; constructor, `get`, `isWithinBounds` | `net.minecraft.util.StaticCache2D`; constructor, `get(int,int)`, `contains(int,int)` | High | Constructor layout and the point at which aliases enter the cache must remain local to worldgen. | Multi-seed feature generation across both X edges; assert no non-canonical holder. |
| 2 | `ChunkGeneratorMixin` | `ChunkGenerator.generateFeatures` head/tail | `net.minecraft.world.level.chunk.ChunkGenerator.applyBiomeDecoration(WorldGenLevel,ChunkAccess,StructureManager)` | High | Decoration ordering and spill cleanup must still bracket vanilla features; exterior and rim writes must not touch unrelated levels. | Local harness terrain/rims, exterior void, and multi-seed seam decoration fixtures. |
| 3 | `ChunkNoiseSamplerMixin` | `ChunkNoiseSampler.<init>` redirect of `NoiseConfig.getNoiseRouter()` | `net.minecraft.world.level.levelgen.NoiseChunk.<init>`; inspected `RandomState.router()` call | Medium | The router owner changed from `NoiseConfig` to `RandomState`; aquifer, beardifier, interpolation, and cache identity must stay vanilla. | Noise seam equality, aquifer continuity, Nether/End comparison, multi-seed terrain generation. |
| 4 | `ChunkPosDistanceLevelPropagatorMixin` | `ChunkPosDistanceLevelPropagator.propagateLevel`/`recalculateLevel`, redirect `ChunkPos.toLong` | `net.minecraft.server.level.ChunkTracker.checkNeighborsAfterUpdate` and `getComputedLevel`; inspected `ChunkPos.pack` neighbour loops | Medium | Both graph traversal loops must join the periodic edges without creating duplicate graph nodes. | Holder/ticket audit, seam simulation eligibility, unload/reload, two-client tracking. |
| 5 | `ChunkRegionMixin` | `ChunkRegion`; constructor, `getBlockEntity`, `setBlockState`, `markBlockForPostProcessing`, `spawnEntity` | `net.minecraft.server.level.WorldGenRegion`; constructor, `getBlockEntity`, `setBlock`, private `markPosForPostprocessing`, `addFreshEntity` | High | Mojang names and signatures changed; preserve vanilla radius validation before projecting a local alias. | Feature spillover fixture, block entities/entities at seam, scheduled post-processing, rim/exterior. |
| 6 | `DensityCoordinateConsumerMixin` | Seven `DensityFunctionTypes` leaves: `Noise`, `Shift`, `ShiftA`, `ShiftB`, `ShiftedNoise`, `WeirdScaledSampler`, `EndIslands` | `net.minecraft.world.level.levelgen.DensityFunctions` inner classes: same first six names; `EndIslandDensityFunction` replaces `EndIslands` | High | Tag only coordinate-consuming leaves; tagging interpolators/caches changes density identity. Inner-class access and interface shape still need compile verification. | Density-function class inventory plus Overworld seam and Nether/End noise comparison. |
| 7 | `EntityDistanceMixin` | `Entity.squaredDistanceTo` overloads and `distanceTo` | `net.minecraft.world.entity.Entity.distanceToSqr(double,double,double)`, `distanceToSqr(Vec3)`, and `distanceTo(Entity)` | High | Official overload set changed; never make non-Overworld or Y/Z distance periodic. | Unit nearest-image cases, seam entity query/tracking, Nether/End controls. |
| 8 | `EntityNavigationMixin` | `EntityNavigation.findPathToAny(Set,...)` | `net.minecraft.world.entity.ai.navigation.PathNavigation.createPath(Set<BlockPos>,int,boolean,int,float)` family | Medium | The protected overload now includes maximum path length; select the final target before node evaluation and avoid whole-ring paths. | Ground AI seam harness, unreachable/exterior target, non-Overworld navigation. |
| 9 | `EntityTrackingSectionMixin` | `EntityTrackingSection.forEach` two overloads | `net.minecraft.world.level.entity.EntitySection.getEntities(AABB,AbortableIterationConsumer)` and typed overload | High | Consumer/continuation types changed; canonicalized bounds must neither duplicate nor omit results. | Seam query windows, typed and untyped queries, full-circumference box de-duplication. |
| 10 | `ExplosionImplMixin` | `ExplosionImpl.calculateReceivedDamage` ray context and `damageEntities` eye position | `net.minecraft.world.level.ServerExplosion.getSeenPercent(Vec3,Entity)` and private `hurtEntities()` | Medium | Exposure calculation is now a static method and damage is private; redirect placement must preserve canonical entity identity and nearest-image ray/impulse. | Seam explosion damage, exposure, knockback direction, and ordinary-world control. |
| 11 | `MinecraftServerMixin` | `MinecraftServer.setupSpawn`, redirect `findBestSpawnPosition` | `net.minecraft.server.MinecraftServer.setInitialSpawn(...)`; inspected private static spawn setup | Medium | The method is static/private and has a `LevelLoadListener`; constrain only the Overworld spawn search and retain progress reporting. | Fresh safe-small and production creation, spawn interior bounds, ordinary debug world. |
| 12 | `MultiTickSchedulerMixin` | `MultiTickScheduler.scheduleTick`/`isQueued` | `net.minecraft.world.ticks.WorldGenTickAccess.schedule(ScheduledTick)` and `hasScheduledTick(BlockPos,T)` | High | Scheduled tick record and method names changed; storage key must match the canonical block written by worldgen. | Seam water/block tick fixtures during generation and after chunk reload. |
| 13 | `NoiseChunkGeneratorMixin` | `NoiseChunkGenerator.populateNoise`, `buildSurface`, `carve`, broad `method="*"` router redirect, `createChunkNoiseSampler` | `net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator.fillFromNoise`, `buildSurface`, `applyCarvers`, exact `doCreateBiomes` router call, private `createNoiseChunk` | Medium | Source audit narrowed the former wildcard: `doCreateBiomes` is the direct gameplay router consumer; terrain density remains scoped through `createNoiseChunk` and `NoiseChunk.<init>`. Debug-screen router access is deliberately vanilla. The private factory and constructor ABI remain runtime-sensitive. | Compile/mixin audit, multi-seed seam density/biome/surface/carver fixtures, aquifers, exterior/rims, Nether/End. |
| 14 | `PlayerInteractionDistanceMixin` | `PlayerEntity.canInteractWithBlockAt`, `canInteractWithEntityIn`, `canAttackEntityIn` | `net.minecraft.world.entity.player.Player.isWithinBlockInteractionRange`, `isWithinEntityInteractionRange` overloads, `isWithinAttackRange` | High | Attack now includes `ItemStack` and `AABB`; preserve weapon-specific reach and server authority. | Cross-seam block use, melee, buffered reach boundaries, too-far rejection, Nether/End. |
| 15 | `ProjectileUtilMixin` | `ProjectileUtil.getEntityCollision` and `collectPiercingCollisions` | `net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult` overloads and `getHitEntitiesAlong` | Medium | The piercing path now returns `Either<BlockHitResult,Collection<EntityHitResult>>`; return canonical entities while testing projected hitboxes. | Ordinary and piercing seam projectile fixtures, nearest hit ordering, block occlusion. |
| 16 | `ServerChunkManagerMixin` | `ServerChunkManager.getChunk`, `getChunkHolder`, ticket methods, `setChunkForced`, `updateChunks` | `net.minecraft.server.level.ServerChunkCache.getChunk`, private `getVisibleChunkIfPresent`, `addTicket`, `addTicketAndLoadWithRadius`, `addTicketWithRadius`, `removeTicketWithRadius`, `updateChunkForced`, `runDistanceManagerUpdates` | Medium | Ticket APIs and holder lookup visibility changed; every external ticket source still needs one canonical graph key. | Canonical holder/ticket audit, forced chunks, load/unload, atlas generation, two-client seam. |
| 17 | `ServerChunkLoadingManagerMixin` | `ServerChunkLoadingManager` holder/region generation, player section/watch filters/diffs, tracking and tick-distance methods | `net.minecraft.server.level.ChunkMap`; inspected `getVisibleChunkIfPresent`, `acquireGeneration`/`releaseGeneration`, `scheduleGenerationTask`, `updatePlayerStatus`, `updatePlayerPos`, `updateChunkTracking`, `isChunkTracked`, `getPlayers`, `anyPlayerCloseEnoughTo` | Low | 26.1 substantially reorganized generation leases, watch state, and distance logic. Old methods such as `getRegion`, `createLoader`, and `sendWatchPackets` do not have one-to-one successors. | Required-injection audit, generation dependency graph, watch diffs, simulation/tracking, long teleport, reconnect, two clients. |
| 18 | `ServerEntityManagerMixin` | `ServerEntityManager.addEntity`, tracking status, loaded/tick/save/load keys, initial section | `net.minecraft.world.level.entity.PersistentEntitySectionManager`; private `addEntity`, `updateChunkStatus`, `isLoaded(UUID)`, `areEntitiesLoaded(long)`, save/unload pipeline | Medium | Several old key-based entry points changed names or ownership; disk-loaded and new entities must use the same canonical section. | Save/reconnect, entity unload/reload, seam crossing while ticking, duplicate UUID rejection. |
| 19 | `ServerEntityManagerListenerMixin` | `ServerEntityManager.Listener.updateEntityPosition` | `PersistentEntitySectionManager.Callback.onMove()` | High | Inner-class name and callback method changed; update the section key exactly once after a fold. | Moving item/boat/projectile through seam, post-crossing query and save/reload. |
| 20 | `ServerEntityTrackerMixin` | `ServerChunkLoadingManager.EntityTracker.updateTrackedStatus`, redirect `Vec3d.subtract` | `net.minecraft.server.level.ChunkMap.TrackedEntity.updatePlayer(ServerPlayer)` | High | The candidate combines distance, broadcast predicate, and chunk watch state; periodic distance alone must not bypass watch eligibility. | Two-player/entity visibility across seam, range boundary, spectator and non-Overworld controls. |
| 21 | `ServerPlayNetworkHandlerMixin` | `ServerPlayNetworkHandler.onPlayerMove`/`onVehicleMove` | `net.minecraft.server.network.ServerGamePacketListenerImpl.handleMovePlayer` and `handleMoveVehicle` | High | Packet records, relative flags, anti-cheat baselines, vehicle/passenger reconciliation, and correction thresholds changed. | Natural player/vehicle crossing with zero corrective teleports, bad-move rejection, yaw/pitch/velocity continuity. |
| 22 | `ServerWorldMixin` | `ServerWorld` constructor, loaded/load/tick checks, synthetic `method_31420`, proximity delivery | `net.minecraft.server.level.ServerLevel`; constructor and named `tick` redirect of `DistanceManager.inEntityTickingRange`, plus `areEntitiesLoaded`, `waitForEntities`, tick/spawn predicates, and final `sendParticles` proximity path | Medium | Entity load/tick predicates were reorganized, and no fallback may become global forced ticking. Runtime mixin application and moving-entity eligibility remain unproved. | Moving entity eligibility, scheduled ticks, spawn checks, proximity particles/effects, save/reconnect, Nether/End. |
| 23 | `WorldEntityLookupMixin` | `World.getOtherEntities` and typed `collectEntitiesByType` | `net.minecraft.world.level.Level.getEntities` untyped and typed overloads, including the bounded-output overload | High | Method names collapsed to overloads; split seam windows once and suppress duplicates, including whole-ring queries. | Unit query-window matrix, typed/untyped seam queries, full-circumference and max-result behavior. |
| 24 | `WorldTickSchedulerMixin` | `WorldTickScheduler.scheduleTick` | `net.minecraft.world.ticks.LevelTicks.schedule(ScheduledTick)` | High | `ScheduledTick` record accessors and container lookup changed; never store alias positions. | Runtime block/fluid ticks across seam and after save/reload. |

### Client mixins (11)

| # | Mixin | 1.21.11 Yarn target and injection | Candidate 26.1.2 Mojang target | Confidence | Principal risk | Required validation |
| ---: | --- | --- | --- | --- | --- | --- |
| 25 | `CreateWorldScreenInvoker` | `CreateWorldScreen.createLevel` invoker | `net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.onCreate()` | High | Private target renamed; retain test-only opt-in and never create a world when `testMode=false`. | Automated fresh-world creation and manual ordinary Create World flow. |
| 26 | `CreateWorldScreenMixin` | `CreateWorldScreen.init` tail | Same Mojang class, `init()` | High | The screen now uses `onCreate()` and extract/render-state UI flow; preserve the single background/blur owner. | GUI scale 4 multi-frame check, presets/custom validation/cost preview, invalid and safe layouts. |
| 27 | `EntityRenderManagerMixin` | `EntityRenderManager.render`, first `MatrixStack.translate` | `net.minecraft.client.renderer.entity.EntityRenderDispatcher.submit`, inspected first `PoseStack.translate` after render offset | High | Rendering uses extracted entity state and submit collection; tangent transform must use the same chart as terrain without mutating gameplay coordinates. | Entity/player/vehicle seam rendering, tangent orientation, local camera controls, Nether/End. |
| 28 | `ClientChunkMapMixin` | `ClientChunkManager.ClientChunkMap.<init>` plus centre/full clear access | `net.minecraft.client.multiplayer.ClientChunkCache.Storage.<init>(ClientChunkCache,int)` | Medium | Inner storage fields and empty-section tracking changed; a disjoint chart clear must also leave renderer/light empty-section state consistent. | Natural seam without clear, long teleport with re-key/clear, chunk/light/biome continuity. |
| 29 | `ClientConnectionMixin` | `ClientConnection.send(Packet)` outbound action canonicalization | `net.minecraft.network.Connection.send(Packet)` overload family | High | Select the single common path without double-transforming delegated overloads; audit every 26.1 positional serverbound packet. | Break/use/place across seam, ordinary packets unchanged, explicit teleport chart. |
| 30 | `ClientPlayNetworkHandlerMixin` | `ClientPlayNetworkHandler` positional chunk/entity/block/effect handlers | `net.minecraft.client.multiplayer.ClientPacketListener`: `handleSetChunkCacheCenter`, `handleAddEntity`, `handleEntityPositionSync`, `handleMoveEntity`, `handleMoveVehicle`, `handleMovePlayer`, `handleLevelChunkWithLight`, `handleLightUpdatePacket`, `handleChunksBiomes`, `handleForgetLevelChunk`, `handleChunkBlocksUpdate`, `handleBlockUpdate`, `handleBlockEntityData`, `handleBlockDestruction`, `handleBlockEvent`, `handleLevelEvent`, `handleParticleEvent`, `handleExplosion`, `handleSoundEvent` | Medium | Packet classes/accessors and some update shapes changed; perform a complete positional-packet audit, including newly added packets, rather than a name-only port. | Full local and two-client packet matrix: chunks, lights, biomes, blocks, entities, sounds, particles, explosions, teleports, reconnect. |
| 31 | `ChunkBuilderBuiltChunkMixin` | `ChunkBuilder.BuiltChunk.shouldBuild`, redirect private neighbour readiness | `net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection.hasAllNeighbors()` | High | Method now explicitly checks eight horizontal/diagonal neighbours. Bypass only an intentionally absent exterior-Z neighbour. | Both finite rims visible/colliding, interior incomplete neighbour still blocks meshing, rotation/reload. |
| 32 | `ChunkRenderingDataPreparerMixin` | `ChunkRenderingDataPreparer.updateSectionOcclusionGraph`, `collectChunks`, `updateNow` | `net.minecraft.client.renderer.SectionOcclusionGraph.update` and `addSectionsInFrustum`; `LevelRenderer.offsetFrustum` call is now in `LevelRenderer` setup | Low | One old redirect moved out of the target class. Preserve curved frustum and distance culling while disabling only flat smart occlusion in RingWorld Overworld. | Upward/tangent section visibility, mountain occlusion rotation, 6/12/28 captures, ordinary world. |
| 33 | `GlobalSettingsMixin` | `GlobalSettings.<init>` buffer size and `set(...)` replacement | `net.minecraft.client.renderer.GlobalSettingsUniform`; field-initializer `GpuDevice.createBuffer(...,136,UBO_SIZE)` and `update(...,DeltaTracker,...,Vec3,...)` | High | Vanilla Globals remains 136 bytes with the same seven fields, but the method takes camera `Vec3` directly. Keep Java/std140/custom shaders synchronized; prefer a RingWorld-owned UBO if Phase 4 supports it. | Shader compilation, layout-size assertion, terrain/cloud globals, world switch cleanup, non-RingWorld shaders. |
| 34 | `PlayerPositionDebugHudEntryMixin` | `PlayerPositionDebugHudEntry.render` | `net.minecraft.client.gui.components.debug.DebugEntryPosition.display(DebugScreenDisplayer,Level,LevelChunk,LevelChunk)` | High | Debug API is now displayer-based; show canonical data without feeding it back into storage or chart state. | F3 at X=0/C, long teleport, atlas status, ordinary dimension display. |
| 35 | `SkyRenderingMixin` | `SkyRendering.close`, `updateRenderState`, `renderMoon`, `renderSun`, two `30.0F` constants, dynamic colour argument, `renderCelestialBodies` tail | `net.minecraft.client.renderer.SkyRenderer.close`, `extractRenderState`, private `renderMoon`, private `renderSun`, public `renderSunMoonAndStars`; inspected `30.0F` sun scale and `DynamicUniforms.writeTransform` | Medium | Celestial sprites now come from an atlas and the dynamic-uniform call is `writeTransform`; the constant count remains two only after bytecode verification. Do not restore moon/shadow slabs or override the sun texture globally. | Day/dusk/night tone captures, fixed angle/size, no moon, stationary stars, tangent/upward proxy render, resource reload. |

## Shader and render-pipeline ABI

The official 26.1.2 shader resources were extracted from the client jar and
compared with RingWorld's overrides.

### Globals

`assets/minecraft/shaders/include/globals.glsl` still declares this std140
order:

```text
ivec3 CameraBlockPos
vec3 CameraOffset
vec2 ScreenSize
float GlintAlpha
float GameTime
int MenuBlurRadius
int UseRgss
```

`GlobalSettingsUniform.UBO_SIZE` is still 136 bytes. The Java publisher changed
from `GlobalSettings.set(..., Camera, ...)` to
`GlobalSettingsUniform.update(..., DeltaTracker, ..., Vec3 cameraPos, ...)`.
The vanilla buffer is initialized through
`GpuDevice.createBuffer(Supplier,int,long)` in a field initializer. RingWorld's
appended vectors cannot be assumed safe merely because the GLSL prefix is
unchanged: allocation size, Java writer, every declaring shader, and
disconnect/layout-switch cleanup still form one ABI.

### Terrain and chunks

The official terrain vertex shader still imports `globals.glsl`,
`chunksection.glsl`, `projection.glsl`, fog, and lightmap helpers. The
`ChunkSection` UBO remains:

```text
mat4 ModelViewMat
float ChunkVisibility
ivec2 TextureSize
ivec3 ChunkPosition
```

Terrain still computes camera-relative intrinsic position as:

```glsl
Position + (ChunkPosition - CameraBlockPos) + CameraOffset
```

and still samples the lightmap through `Sampler2`. These are encouraging ABI
matches, not approval to reuse the overrides unchanged. Section submission,
visibility, render-state extraction, and GPU buffer ownership changed in Java.
Rebase RingWorld's shaders on the exact 26.1.2 vanilla files and reapply only
the curvature, dither, lightmap, and proxy changes.

### Clouds and sky

`rendertype_clouds.vsh` still uses the `CloudInfo` std140 block and
`CloudFaces` integer buffer, but consumes `dynamictransforms.glsl` and
`projection.glsl`, not Globals. RingWorld's cloud fade therefore still needs
an explicit RingWorld data source; extending Globals alone does not supply the
cloud program.

`SkyRenderer` now owns atlas-backed celestial GPU buffers. The sun path still
scales by `30.0F`, but calls `DynamicUniforms.writeTransform`, binds the
celestial atlas, and draws with `RenderPipelines.CELESTIAL`. The current
`DynamicUniforms.write(...)` target is invalid. The complete-ring proxy render
hook must be placed in the new draw phase without assuming the old
`renderCelestialBodies` signature.

### Pipeline conclusions

- Use Blaze3D `GpuDevice`, `CommandEncoder`, `RenderPass`, buffer, texture, and
  render-pipeline abstractions; do not add raw OpenGL.
- Prefer a RingWorld-owned UBO if it can be bound to every custom program
  without replacing vanilla Globals.
- Treat resource names surviving (`terrain.*`, `rendertype_clouds.*`,
  `globals.glsl`) as compatibility hints only.
- Re-run shader compilation plus 6/12/28 safe-small and production
  tangent/radial captures after every ABI change.

## World storage and saved-data audit

Current manual paths:

| Owner | Current path logic | 26.1.2 concern |
| --- | --- | --- |
| `RingWorldSettings.hasExistingOverworldRegions` | `server.getSavePath(ROOT).resolve("region")` | Root-level Overworld assumptions no longer express dimension ownership. |
| `RingTerrainAtlasServer.path` | `server.getSavePath(ROOT).resolve("data").resolve("ringworld-terrain-atlas.rwat.gz")` | Atlas storage must follow the RingWorld Overworld dimension, not a generic root. |
| `ClientRingState` | `<gameDir>/ringworld-cache/terrain-<worldHash>.rwat.gz` | Client cache is not world storage; retain world-hash isolation and clear static GPU/client state on disconnect. |

The inspected 26.1.2 source constructs dimension storage through
`LevelStorageSource.LevelStorageAccess.getDimensionPath(ResourceKey<Level>)`.
`ChunkMap` uses:

```text
getDimensionPath(level.dimension()).resolve("region")
```

and `ServerChunkCache` uses:

```text
getDimensionPath(level.dimension()).resolve("data")
```

`ServerLevel.getDataStorage()` now returns `SavedDataStorage`. Expected saved
data renames are:

- `PersistentState` -> `SavedData`;
- `PersistentStateManager` -> `SavedDataStorage`;
- `PersistentStateType` -> `SavedDataType`;
- `markDirty()` -> `setDirty()`;
- get/create flow -> `get`, `set`, or `computeIfAbsent` with the new
  `SavedDataType`.

S2 must determine the cleanest supported way to obtain the exact Overworld
dimension path. `MinecraftServer.getWorldPath(LevelResource)` still returns a
root resource and is not the replacement. The `LevelStorageAccess` is supplied
to `ServerLevel`/`ServerChunkCache` constructors but is not exposed by a
public `ServerLevel` accessor in the inspected source. Prefer a narrow
accessor/service established during Phase 2 over reconstructing
`DIM-1`/`DIM1`/namespaced directory strings.

Migration requirements discovered for S2:

1. Check the new dimension-owned saved-data location first.
2. Detect the precise legacy 1.21.11 Overworld settings and atlas locations.
3. Migrate once with an atomic write/rename strategy, or invalidate the atlas
   safely while preserving immutable settings.
4. Inspect ordinary-world regions through
   `getDimensionPath(Level.OVERWORLD).resolve("region")`.
5. Test fresh, legacy copied world, missing/corrupt state, interrupted write,
   old atlas format, and different-world-hash cases on copies only.
6. Do not bump settings or atlas formats unless serialized bytes change.

## Broad rename and removal inventory

Common Mojang terminology needed throughout the port includes:

| Yarn/current name | Mojang/26.1.2 name |
| --- | --- |
| `World`, `ServerWorld`, `ClientWorld` | `Level`, `ServerLevel`, `ClientLevel` |
| `ServerChunkManager`, `ServerChunkLoadingManager` | `ServerChunkCache`, `ChunkMap` |
| `ChunkRegion` | `WorldGenRegion` |
| `ChunkNoiseSampler`, `NoiseChunkGenerator` | `NoiseChunk`, `NoiseBasedChunkGenerator` |
| `EntityNavigation`, `EntityTrackingSection` | `PathNavigation`, `EntitySection` |
| `ServerEntityManager` | `PersistentEntitySectionManager` |
| `ExplosionImpl` | `ServerExplosion` |
| `PlayerEntity`, `ServerPlayerEntity` | `Player`, `ServerPlayer` |
| `ServerPlayNetworkHandler`, `ClientPlayNetworkHandler` | `ServerGamePacketListenerImpl`, `ClientPacketListener` |
| `ClientConnection` | `Connection` |
| `MinecraftClient`, `WorldRenderer` | `Minecraft`, `LevelRenderer` |
| `EntityRenderManager` | `EntityRenderDispatcher` |
| `ChunkBuilder.BuiltChunk` | `SectionRenderDispatcher.RenderSection` |
| `ChunkRenderingDataPreparer` | `SectionOcclusionGraph` |
| `GlobalSettings`, `RenderTickCounter` | `GlobalSettingsUniform`, `DeltaTracker` |
| `SkyRendering` | `SkyRenderer` |
| `MatrixStack` | `PoseStack` |
| `Text`, `Identifier`, `RegistryKey` | `Component`, `ResourceLocation`, `ResourceKey` |
| `MathHelper`, `Vec3d`, `Box` | `Mth`, `Vec3`, `AABB` |
| `PacketCodec`, `PacketCodecs` | `StreamCodec`, `ByteBufCodecs` |
| S2C/C2S packet classes | `Clientbound*`/`Serverbound*` packet classes |

Additional structural changes requiring a compiler/source audit:

- several coordinate holders use accessors such as `ChunkPos.x()`/`z()` rather
  than Yarn-exposed fields;
- worldgen methods use `RandomState`, `WorldGenLevel`, `ChunkAccess`, and
  Mojang's chunk-status packages;
- client rendering separates state extraction, submit collection, and drawing;
- entity rendering injects through `submit` with render-state objects rather
  than the old direct render path;
- debug HUD entries publish through `DebugScreenDisplayer`;
- world creation uses `WorldCreationUiState` and `onCreate`;
- command code uses `Commands`, `CommandSourceStack`, `Component`, and Mojang
  permission types;
- `DataFixTypes`, saved-data codecs, record codecs, and registry lookup types
  must be recompiled even where their simple names survive.

## Unresolved questions and integration hazards

1. Which exact Phase 1 Mojang-mapped 1.21.11 commit will become the semantic
   comparison point for every descriptor and ordinal?
2. Can a RingWorld-owned UBO be bound to terrain, clouds, and the proxy without
   extending vanilla Globals, and does this remain compatible with the 26.1
   render-pipeline registry?
3. Which `ChunkMap` methods replace the complete old watch-filter/diff
   behavior without duplicating chunks at the presentation seam?
4. Where should the periodic chunk graph context wrap both
   `ChunkTracker.checkNeighborsAfterUpdate` and `getComputedLevel` while
   retaining the vanilla invalid-node sentinel?
5. **Resolved in primary source port:** 26.1.2 places the call directly in
   named `ServerLevel.tick`; the redirect remains narrowly scoped to
   `DistanceManager.inEntityTickingRange(long)`.
6. Should explosion exposure be injected into static
   `ServerExplosion.getSeenPercent`, private `hurtEntities`, or a shared ray
   helper to cover both damage and knockback with one nearest-image decision?
7. How should S2 obtain `LevelStorageAccess.getDimensionPath` without widening
   ownership or reconstructing dimension folder names?
8. Does the 26.1 client introduce any new positional play packet not handled
   by the candidate `ClientPacketListener` list?
9. Do the new `LevelRenderEvents` phases require moving the proxy render from
   `END_MAIN` to a terrain-specific event to preserve depth and the live/proxy
   cross-fade?
10. Does `SkyRenderer.renderSun` still contain exactly two bytecode `30.0F`
    constants after compilation, or does one decompiler-visible scale compile
    differently? Keep `require = 2` until bytecode proves a deliberate change.
11. Can `PersistentEntitySectionManager.Callback.onMove` be patched without
    capturing stale section state when an entity folds during the asynchronous
    simulation graph update?
12. Which copied 1.21.11 world and checksum will be the immutable upgrade
    fixture? The coordination issue reports the frozen baseline at
    `2c98650e850064428c50667ba0809736294e549e`.

## S1 validation

Validation was run on the assigned audit base with the portable Microsoft
OpenJDK 25 runtime:

```text
./gradlew test build
BUILD SUCCESSFUL in 4m 22s
73 tests, 0 failures, 0 errors, 0 skipped
```

This is the 1.21.11 baseline build, not a 26.1.2 compile. Loom emitted the
non-fatal warning `Cannot remap modifiers because it does not exist in any of
the targets [] or their parents.` Gradle also reported deprecated features
that will be incompatible with Gradle 10 and warned that the checkout is in
OneDrive. Neither warning failed this build, but the Phase 2 toolchain update
should run with `--warning-mode all` in a non-cloud path and classify the
deprecations.

Additional S1 checks:

```text
git diff --check
configured mixins: 35 (24 common/server, 11 client)
documented mixins: 35 unique, 0 missing, 0 extra
```

## Recommended integration order

1. Complete the 1.21.11 Yarn-to-Mojang migration and record all exact old
   official descriptors.
2. Establish the Java 25/26.1.2 compiler-error baseline without disabling any
   mixin.
3. Port common type/API renames and Fabric lifecycle/network registrations.
4. Port topology/worldgen/network mixins in primary ownership order.
5. Integrate S2 storage, then S3 UI, then S4 harness work.
6. Restore rendering in the staged order from the port plan.
7. Run `./gradlew test build` and `git diff --check` after each integration,
   followed by the applicable runtime gate.

The highest-risk early targets are `NoiseChunkGeneratorMixin`,
`ServerChunkLoadingManagerMixin`, `ServerWorldMixin`, and
`ChunkRenderingDataPreparerMixin`. None should be declared resolved until its
new descriptor, injection count, semantics, and runtime evidence have been
reviewed.
