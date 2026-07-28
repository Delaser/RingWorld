# Current state

Last audited: 2026-07-28 against the final Minecraft 1.21.11 implementation
tagged `mc-1.21.11-final` at commit `2c98650`.

The Minecraft 26.1.2 port is active on `codex/minecraft-26.1-port`; see
[`MINECRAFT_26_1_PORT_PLAN.md`](MINECRAFT_26_1_PORT_PLAN.md) and the
[`final baseline`](MINECRAFT_1_21_11_FINAL_BASELINE.md).

This document separates demonstrated implementation from planned or incomplete
work. It should be updated after every substantial milestone.

Port Phase 1 is complete: the project moved to official Mojang mappings while
remaining on Minecraft 1.21.11. All 73 tests, the destructive
safe-small harness, same-process layout switch, dedicated two-client scenario,
and production tangent/radial projection capture passed without changing the
wire protocol, saved formats, or topology behavior. At that checkpoint the only
intermediary-looking source identifier was Mojang's still-unnamed
`ServerLevel.method_31420` synthetic entity-tick lambda, documented in
`MIXIN_MAP.md`.

Phase 2 is also established: the active branch now resolves unobfuscated
Minecraft 26.1.2 and Fabric API 0.155.2 under Java 25 and Gradle 9.5.1. Its
expected checkpoint is a failing common compile with 95 inventoried errors;
the client, mixins, runtime, and saved-world upgrade are not yet ported. See
[`MINECRAFT_26_1_COMPILER_BASELINE.md`](MINECRAFT_26_1_COMPILER_BASELINE.md).
The playable implementation remains the frozen `mc-1.21.11-final` tag.

The first primary common pass has reduced those 95 errors to five
storage-owned diagnostics while preserving the Phase 2 checkpoint in history.
A detached client probe temporarily shimmed only those five errors, exposed 21
client diagnostics, and the primary branch has removed the seven mechanical
`ChunkPos` cases. Direct whole-project client validation still waits for S2's
saved-data/storage integration.

The “Implemented” sections below describe that validated 1.21.11 behavior and
the contract the port must restore. They are not claims that the active 26.1.2
compiler checkpoint currently launches.

## Implemented

### Topology and storage

- One canonical Overworld X plane.
- Periodic block/chunk coordinate helpers.
- Periodic chunk holder, ticket, watch, and simulation propagation, plus a
  configured-distance nearest-player eligibility fallback for transient or
  stale seam-side entity graph state.
- Continuous client presentation charts.
- Natural player and vehicle seam folding without a corrective teleport.
- Explicit same-world teleports project their canonical X target into the
  nearest client presentation image, avoiding seam-adjacent chart eviction.
- Canonical entity indexing, save/load, and tick eligibility; seam-crossing
  non-player entities no longer depend solely on the asynchronously propagated
  side of the player ticket.
- Periodic entity queries, distances, tracking, reach, projectiles,
  explosions, AI targets, and proximity effects.
- Canonical block/fluid scheduled ticks.

### World generation

- Cylindrical coordinate sampling for horizontal noise consumers.
- Vanilla sampler/cache/aquifer identity preserved.
- Canonical seam-crossing worldgen writes and neighbour aliases.
- Finite Z band with exterior void.
- Five-block breakable cobblestone/mossy-cobblestone rims.
- Shortened wall height and gradual migration from legacy stone-brick rims.
- Boundary section meshing that tolerates only intentionally absent exterior
  neighbours, keeping genuine rim blocks visible as well as collidable.
- Spawn constrained to the safe width interior.

### Rendering

- Curved terrain shader using a synchronized format-2 layout.
- Named extended Globals UBO fields for circumference, width, surface
  reference, saved wall height, wall/cloud elevations, physical centre, view
  distance, handoff, detail, and haze.
- Sub-block-stable camera reconstruction.
- Curved CPU section culling for upward views.
- RingWorld-only non-occluding section-graph traversal so flat mountains cannot
  suppress chunks that curvature bends back into view; curved frustum and
  render-distance culling remain active.
- Curved/tangent-aligned entity models.
- Exact-cylinder local cloud deck derived from saved wall top plus eight
  blocks.
- Fixed ring-centred dimming Minecraft sun at about 0.9 degrees apparent
  diameter and hidden moon.
- Smooth global noon/dawn/dusk/midnight sun intensity and colour tone driven
  by vanilla time; the former shadow-panel mesh is removed.
- Persistent periodic format-5 atlas of exposed top-face height and
  texture-corrected biome colour sampled from the actual highest surface block.
  Dedicated servers fall back to the sampled block's map colour when their
  unloaded client-only grass/foliage colormaps return zero.
- Relief-shaded, mipmapped complete-ring GPU texture/mesh at normal real-chunk
  render distance.
- Live RGB lightmap exposure for the distant surface using the
  full-skylight/no-block-light texel, matching client day/night, weather,
  gamma, lightning, darkness, and night-vision state.
- A broad live/LOD alpha cross-fade with reduced, terrain-hugging fog colour:
  proxy opacity grows from 68% to 98% of effective view distance while a
  RingWorld-only terrain fragment dither spans 78% to 102%. This makes the
  proxy effectively opaque before disappearing live terrain can expose sky.
- Canonical F3 coordinates and atlas state.
- Dimension-aware `RingRenderProfile` with half-circumference clamping and
  bounded texture/mesh resources.

### Multiplayer and tooling

- Required full-layout server/client settings handshake.
- Independently verified layout fingerprint and format mismatch rejection.
- Dedicated test clients wait for the initial resource reload before joining,
  preventing an early-connect particle-sprite initialization race.
- Tiled atlas transfer and world-hash client cache.
- Checked atlas allocation, typed tile coordinates, long pregeneration
  counters, transfer estimates, progress rate, and ETA.
- Gamemaster-level atlas status/pause/resume commands.
- Local destructive smoke harness.
- Same-JVM saved-layout switch harness that verifies disconnect clearing and
  second-world geometry/atlas replacement.
- The automated upward live/LOD capture waits for the current world's atlas to
  be complete before reducing view distance for seam traversal. With
  pregeneration explicitly disabled and no cache, it records a skipped LOD
  capture after 600 ticks and continues topology/rim coverage.
- First-seam gameplay fixtures remain resident for a complete 240-tick
  observation window before the accelerated circuit starts. The server
  identifies that circuit from high-X then low-X canonical poses rather than
  counting repeated packets from one presentation chart.
- Dedicated two-client seam/combat/block/vehicle/teleport/reconnect harness.
- The reusable multiplayer fixture removes stale automated boats, waits for
  both clients to acquire the new vehicle, detects canonical folds across
  overloaded server ticks, and treats periodic teleport targets as equivalent
  client-chart images.
- Gradle wrapper, parameterized pure dimension tests, server deployment templates, and private
  GitHub source repository.
- Latest profile-3 safe-small runtime (2,048×416 at 28-chunk capture) passed two
  natural wraps with zero camera delta/correction packets and passed block,
  entity, projectile, vehicle, AI, fluid, explosion, collision, rim, and
  exterior-void probes. The harness measured 15.99 ms average across the first
  seam interval and 11.84 ms beside the rim.
- The derived-pitch 6-chunk case also passed the complete harness, with the
  capture aimed at the actual 96-block handoff and 15.9/13.3 ms first-seam/rim
  averages.
- Derived-pitch 12- and 28-chunk comparison captures are complete. A
  2,048×256 no-cache width stress passed both wraps, gameplay, AI, rim, and
  exterior checks; its LOD capture was deliberately skipped because neither
  pregeneration nor a complete cache was available.
- A reused-world dedicated 2,048×416 server plus two real clients passed the
  complete seam, combat, block, vehicle, long-teleport, return-chart, and
  reconnect matrix. Its largest sampled movement packet was 0.25 blocks.
- The public AndWhatNot server was rebuilt on 27 July 2026 with a fresh
  2,048×416 world and the current mod, then changed to survival as the public
  default. Its preceding 1,600×320 world,
  jar, configuration, server properties, and operator list are retained in a
  timestamped rollback backup. The server reached `Done`, accepted RCON, and
  completed and saved the expected 13,312-cell asynchronous atlas.
- Matching credential-free universal/macOS and Windows packages are published
  at `https://andwhatnotstudio.com/ringworld/`. Both published downloads were
  fetched through HTTPS and verified against the page's SHA-256 manifest.
- Meridian remained active with zero service restarts during the world
  cutover and reattached to the new Minecraft `latest.log`.
- The first fresh public atlas exposed a dedicated-server-only colour failure:
  10,192 of 13,312 cells were black because common-code grass/foliage lookup
  arrays remain zero-filled without a client resource reload. Format 5
  invalidates that cache and uses block map colour only when the biome lookup
  is unavailable; ordinary integrated-server biome tint remains unchanged.
  The rebuilt live atlas contains zero black cells (median luminance 72.7
  instead of 0), and the corrected universal/macOS and Windows packages were
  republished with matching HTTPS-verified checksums.
- Layout wire generation 2 uses versioned `settings_v2` and
  `settings_ack_v2` channels. Stale generation-1 clients are rejected with a
  package-update message instead of crashing Netty after leaving ten unread
  settings bytes. Shareable launchers now refresh managed mod files in an
  existing Prism instance on every start without touching accounts, saves, or
  user settings. The full isolated two-client harness passed after this change:
  both clients acknowledged the new geometry channel, crossed and interacted
  through the seam, completed combat and vehicle checks, and passed the
  deliberate disconnect/reconnect sequence.
- Visual profile 4 prevents Minecraft's chunk-derived far plane from clipping
  the complete-ring proxy. A synthetic complete 15,552×4,096 atlas produced a
  4,096×1,024 texture and 393,216-vertex mesh; separate tangent/along-ring and
  radial-up framebuffer captures both showed continuous proxy coverage. The
  runtime diagnostic measured a 1,024-block level far plane versus about
  4,893 blocks through the opposite reference surface and 5,305 blocks to the
  far width edge at the test camera. Real trees and terrain remained visibly
  in front of the sky-stage proxy in both captures.
- The visual-profile-4 client packages were published after credential and
  archive-integrity checks. The HTTPS-verified SHA-256 values are
  `92026aa66ff062ed44e0074ec4502f25b702b4b43c655421e3bcefeeac04ff29`
  for the universal package and
  `380b1cfc2fc112dc16487f773d5ddf543d8eb09b003ec9fd911ab7a1dc66adc5`
  for the Windows package. The public server was not restarted because this
  update changes only the client-side visual proxy and diagnostics.

## Deliberate design decisions

- Gravity remains vanilla in intrinsic coordinates.
- Nether and End remain vanilla.
- The server stores no duplicate circumference laps.
- Distant visibility is a texture/LOD problem, not a forced whole-ring chunk
  render-distance problem.
- Walls are finite-height, textured, thick, and breakable; players may leave
  the ring.
- Current day/night is global. Position-aware darkness will not be faked
  visually without matching server simulation.

## Known limitations and risks

### Distant surface

- The active far ring appears only after the atlas is complete.
- Disconnect and settings-reception paths clear the previous world's GPU mesh
  and texture. The renderer independently rejects absent/incomplete current
  atlases, preventing stale terrain while a new world is pregenerated.
- Source resolution is one height/surface-colour sample per eight blocks.
- The client expands colour data but cannot recreate blocks, transparent
  layers, trees, buildings, mobs, or weather volumes.
- Texture-luminance-corrected biome tint, relief shading, periodic mip
  filtering, live full-skylight exposure, partial terrain visibility through
  the transition, and local proxy exclusion are implemented. Their visual
  tuning still needs captured comparisons across weather, time, and water.
- The lightmap match represents exposed terrain globally. Dynamic local block
  lights are not encoded in the static atlas.
- The atlas is refreshed when chunks are captured/loaded, not immediately on
  every block edit. Player construction can remain stale until recapture.
- The LOD retains limited terrain contrast at the nominal chunk edge to avoid a
  visible flat-colour belt. Translucent live surfaces still require dedicated
  visual regression and cannot be reproduced faithfully by the opaque atlas.

### Rendering maintenance

- The extended Globals UBO and custom shader include are version-sensitive.
- `RingRenderProfile` visual-policy version 4 owns live/proxy/detail distances,
  near/far reveal, haze endpoints/exponent, and local cloud fade. Cross-size
  captures are still required before treating those values as production art
  tuning.
- Custom shaders replace vanilla assets and can conflict with renderer/shader
  mods.
- Boundary rendering redirects a private
  `SectionRenderDispatcher.RenderSection` readiness check and must be
  re-audited on Minecraft or mappings upgrades.

### Worldgen

- Broad multi-seed structure/carver/feature coverage at the seam is incomplete.
- Periodic density noise does not guarantee every vanilla structure placement
  seed or third-party generator treats X=0/C as adjacent.
- Production default atlas pregeneration is very large: 248,832 chunks.
- Existing Overworld region files without RingWorld saved settings are
  explicitly rejected; no conversion tool exists.
- Decorative wall-height changes can produce mixed old/new boundary chunks.

### Gameplay coverage

- Representative arrows, a boat, one navigator, water, explosions, effects,
  blocks, and melee are tested; arbitrary redstone, fluids, projectiles,
  vehicles, portals, raids, maps, commands, and modded systems are not.
- Explicit teleport and reconnect have harness coverage, but death/respawn and
  every portal route need broader regression testing.
- No global compatibility layer catches every new positional Minecraft packet
  or mod packet.

### Protocol and compatibility

- The handshake has no explicit acknowledgement timeout after settings send.
- Protocol compatibility is one format integer, not feature negotiation.
- Vanilla clients are intentionally unsupported.
- Mods assuming a flat renderer, ordinary unbounded chunk X, global Euclidean
  distance, different gravity, or unchanged shader/worldgen internals are
  likely incompatible.

### Configuration/user experience

- The Create World screen has a RingWorld layout editor with safe-small,
  production, and current presets plus live validation/cost preview.
- The layout editor relies on Minecraft's framework-managed background pass;
  it does not request a second menu blur while rendered over Create World.
- Applying a valid creation layout requires a second explicit confirmation
  that repeats the immutable dimensions and wall height.
- Dedicated servers use equivalent first-world bootstrap properties.
- There is no supported in-place resize or conversion tool.
- New-world dimension validation now checks the full-height radial clearance,
  finite-rim interior, wall/build bounds, axis limits, and atlas allocation
  budget before settings are created. The active 2,048-by-416 development
  preset passes with about 70 radial blocks above Y=320; the retired
  1,600-block circumference is a required validation failure.
- Saved format-2 settings win before generation; format 1 migrates explicitly.
- The full immutable layout is sent to clients and used for walls, clouds,
  shaders, and atlas identity.
- The source-audited variable registry and correction plan are maintained in
  `DIMENSION_SCALING_PLAN.md`.

## Removed/rejected approaches

### Seam teleport

An early implementation snapped the player from one edge to the other. It
could not keep other players, entities, chunks, and interactions continuous.
The current canonical-server/presentation-client architecture replaced it.

### Multiple stored laps

Keeping server entities thousands of blocks apart in different logical laps
broke distance, combat, tracking, and save semantics. The server now owns one
canonical plane only.

### Forced 100-chunk render distance

Rendering the complete development circumference as real chunks looked good
but consumed unacceptable CPU, memory, chunk meshing, and GPU resources. The
normal-distance real terrain plus atlas-backed GPU ring replaced it.

### Radial physics rewrite

Literal vector gravity would require pervasive movement, fluid, AI, projectile,
vehicle, and mod compatibility work. Intrinsic coordinates already make
vanilla `-Y` the correct local outward gravity after visual embedding.

### Shadow-panel sky

The first fixed-sun design rendered twenty moving slabs around the star. Their
scale and silhouette dominated the sky and did not fit the desired visual.
The active cycle keeps the sun fixed and uses a continuous global
dimming/colour shift instead. The removed implementation remains documented in
`SUN_RENDERING_SNAPSHOT_2026-07-26.md`.

### Artificial containment-array sun

A custom cyan, amber, and white 32×32 machine-like sun was tested after the
shadow panels. At the intended small angular size it looked busy and visually
odd, so the active renderer returned to Minecraft's original sun sprite while
retaining the fixed pose and continuous dimming/colour cycle.

## Recommended next work

Priorities are ordered by player-visible value and architectural leverage.

1. **Complete the large-layout visual/resource matrix**
   - topology, gameplay, rims, and same-process resource replacement now pass
     on safe-small, minimum-width, production, long/narrow, and wide/medium
     layouts;
   - capture complete-atlas live/LOD comparisons on production, long/narrow,
     and wide/medium worlds;
   - parameterize the dedicated two-client test beyond the safe-small size;
   - benchmark full production-default atlas pregeneration, disk, transfer,
     and GPU build cost before deploying it.
2. **Tune and validate the texture LOD transition**
   - capture matched upward screenshots for clear/rain, day/dusk/night, and
     water-heavy terrain;
   - tune detail reveal and far haze from those comparisons;
   - validate exact seam UV behavior and mip stability while moving;
   - add structure/vertical/transparent representation only if the remaining
     atlas mismatch is still visible through the transition.
3. **Finish the adaptive visual profile**
   - tune the now-centralized cloud fade, reveal, and haze policy against the
     multi-size capture matrix;
   - increment visual-policy semantics when comparison captures would change;
   - derive automated screenshot targets from geometry instead of fixed pitch.
4. **Harden atlas lifecycle**
   - decide how block edits invalidate surface cells;
   - support progressive rendering safely if desired;
   - benchmark production-scale memory/network/pregeneration;
   - expose admin status/control commands.
5. **Broaden multiplayer gameplay regression**
   - death/respawn, portals, maps, more vehicles/projectiles;
   - redstone and cross-seam block entities;
   - fluid networks and explosions with terrain destruction.
6. **Worldgen seam matrix**
   - multiple seeds;
   - every major biome;
   - structures deliberately forced across X=0/C;
   - loot, mobs, portals, and locate commands.
7. **Configuration UX follow-up**
   - add an explicit second confirmation if user testing shows the current
     immutability notice is insufficient;
   - add dedicated-server admin status/pause/resume controls;
   - improve creation cost warnings with measured production benchmarks.
8. **Day/night visual polish**
   - capture the new small sun at all four tone keyframes;
   - tune keyframe colours only against matched sky, live-terrain, and
     distant-ring screenshots;
   - keep the single authoritative vanilla gameplay clock.
9. **Compatibility API**
   - expand the read-only API for canonical/presentation/physical poses;
   - document compatibility contracts and failure detection.

## Evidence required before calling the mod broadly playable

- Stable frame pacing during ordinary movement at practical render distance.
- No camera or chunk pop at repeated seam crossings.
- Complete two-client test matrix on a dedicated server.
- Multi-seed worldgen/structure continuity.
- Save/reconnect/death/portal lifecycle coverage.
- Clean live/LOD transition in clear and rainy day/night captures.
- Production-size atlas benchmark or a scalable alternative.
- Explicit supported/incompatible mod list.
