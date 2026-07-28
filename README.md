# RingWorld

Fabric mod targeting Minecraft Java 1.21.11. It makes the Overworld finite
across Z and periodic across X, with a curved client terrain view.

Before creating a world, open the bottom-left `RingWorld C×W` button on
Minecraft's Create World screen. Choose safe-small, production, or custom
dimensions; the editor validates the physical cylinder and previews chunk,
atlas, and GPU cost, then requires explicit confirmation of the immutable
layout. Dedicated servers use the equivalent
`config/ringworld.properties` bootstrap:

```properties
widthBlocks=4096
circumferenceBlocks=15552
wallHeightBlocks=160
pregenerateTerrainAtlas=true
```

Width and circumference must be multiples of 16 and pass full-height radial
clearance and resource checks. They are saved into the world on first load and
cannot be changed later. The width is centred on Z=0,
so the default band spans Z=-2048 through Z=2047. `wallHeightBlocks` is
measured from the world minimum height; the default puts the rim top near Y=96.
Both edges use five-block-thick, deterministically varied cobblestone and mossy
cobblestone. The exterior is cleared to void. The rim
is intentionally breakable: leaving the ring is allowed.
`pregenerateTerrainAtlas=true` generates one chunk asynchronously at a time
until the distant-ring overview is complete. Set it to `false` only when an
administrator wants to postpone that background pregeneration.
Gamemaster-level operators can use `/ringworld atlas status`, `pause`, and
`resume` to control that work at runtime without changing the saved layout.

Build with the included Gradle wrapper and Java 21:

```sh
./gradlew build
```

Install `build/libs/ringworld-0.1.0.jar` on the server and every client. The
server rejects a client that cannot receive the required geometry handshake.

The public survival test server currently uses the safe-small 2,048×416
layout. Matching credential-free macOS/universal and Windows launch bundles
are published at [andwhatnotstudio.com/ringworld](https://andwhatnotstudio.com/ringworld/).
The page includes SHA-256 checksums for both downloads. To update an existing
bundle, close Minecraft and extract the new ZIP over the same folder; the
launcher refreshes the managed mod files without replacing login data, saves,
or user settings.

## Documentation

Implementation and maintenance documentation is stored with the project:

- [`AGENTS.md`](AGENTS.md) is the operating guide and invariant list for future
  coding agents.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) explains the canonical server
  plane, continuous client charts, worldgen, simulation, and physical renderer.
- [`docs/DIMENSION_SCALING_PLAN.md`](docs/DIMENSION_SCALING_PLAN.md) audits
  every dimension-sensitive setting, derived value, shader constant, resource
  budget, and test fixture, then defines the custom-size implementation plan.
- [`docs/NETWORK_PROTOCOL.md`](docs/NETWORK_PROTOCOL.md) documents the login,
  atlas, and positional packet paths.
- [`docs/RENDERING.md`](docs/RENDERING.md) covers curved chunks, the complete
  ring texture, fog, clouds, and the fixed tone-shifting sun.
- [`docs/SUN_RENDERING_SNAPSHOT_2026-07-26.md`](docs/SUN_RENDERING_SNAPSHOT_2026-07-26.md)
  preserves the removed sun/panel implementation as a dated rollback target.
- [`docs/MIXIN_MAP.md`](docs/MIXIN_MAP.md) maps every Minecraft injection to
  its owned behavior and failure risk.
- [`docs/TESTING.md`](docs/TESTING.md),
  [`docs/OPERATIONS.md`](docs/OPERATIONS.md), and
  [`docs/CURRENT_STATE.md`](docs/CURRENT_STATE.md) contain the regression
  procedures, deployment/configuration guide, and honest implementation
  boundary.

## Playable continuous-loop stage

The current build implements the smooth circumference traversal path:

- The server stores every player and entity in exactly one canonical X plane,
  from `0` through `circumference - 1`. Crossing the seam folds the server pose
  after normal movement validation without a corrective teleport packet,
  velocity reset, or camera rotation.
- Each client keeps only a transient nearby presentation chart. This lets the
  camera, terrain, and remote entities move smoothly through the joined edge;
  it is never saved and is not a second gameplay coordinate plane.
- Chunk data, unloads, lighting, biome refreshes, block deltas, and the client
  chunk-window centre are mapped to the nearby client chart. Natural seam travel
  is incremental; command-scale position changes atomically re-key the client
  chart so old periodic copies cannot overlap the new one.
- Server chunk tickets and player watch centres remain canonical. Their
  periodic distance makes chunk `circumference - 1` directly adjacent to chunk
  `0`, so terrain continues streaming after every seam crossing.
- Entity storage, tracking, distance checks, queries, and client packet poses
  use the periodic topology. Players, items, and mobs can remain visible and
  interactive across the joined edge instead of disappearing at a teleport.
- Non-player entities are canonicalized before storage and after movement.
  Mobs, items, vehicles, and projectiles therefore keep simulating after
  crossing the seam instead of freezing outside the finite chunk graph.
- Distant entity models are translated through the curved embedding and
  rotated into the tangent frame at their X position. Their feet remain
  aligned with the visibly curved terrain while the local player's frame is
  unchanged.
- Vanilla's flat opaque-section propagation is disabled only for the RingWorld
  Overworld, preventing mountains from suppressing loaded sections that the
  cylindrical transform bends back into view. Curved frustum and render
  distance culling remain active.
- Wrapped block interaction, collision queries, and scheduled block/fluid
  ticks address the single canonical copy of world data.
- The F3 position panel presents only canonical Ring X (`0` through
  `circumference - 1`), canonical block/chunk/region coordinates, and current
  terrain-atlas completion.
- Entity projectile raycasts (including the piercing-arrow path), ground AI
  targets, explosion exposure and knockback, particles, sounds, and world
  events all select the nearest periodic image across the seam.
- The terrain shader receives the configured circumference at runtime and
  bends chunk geometry into the cylindrical band. It is not tied to the
  2,048-block development ring.
- Beyond loaded chunks, one static GPU mesh continues the real cylinder
  through the zenith and back to the opposite apparent horizon. It is textured
  from the canonical surface atlas, uses the exact configured radius and width,
  and changes only one ring-centred model transform as the camera moves.
- The source texture is an eight-block-resolution periodic height-and-colour
  atlas captured from the world's actual generated chunks. It samples the
  highest surface block and stores its exposed top-face height. Water, grass,
  and foliage retain biome tint corrected by representative block-texture
  luminance. The client expands it to a block-scale GPU image
  for the small test ring (bounded to 4096 by 1024 texels on larger worlds),
  adds restrained relief shading and a periodic filtered mip chain, and keeps
  terrain silhouettes and colours anchored to their canonical locations.
- Atlas generation is incremental and persisted under the world `data/`
  directory. It is split into small network tiles, cached by immutable world
  hash on each client, and reused on reconnect without retransmission.
- Complete-ring GPU resources are discarded on disconnect and before a new
  world's settings are installed. A fresh world displays only real chunks
  until its own world-hash atlas reaches 100%, never the previous world's ring.
- The curvature shader reconstructs the camera using vanilla's integer origin
  and negative fractional offset convention, so sub-block movement remains
  continuous instead of snapping at every block boundary.
- Cylindrical worldgen transforms only coordinate-consuming density leaves.
  Vanilla's `ChunkNoiseSampler` identity, density caches, and aquifer-local
  coordinates therefore remain intact.
- Breakable five-block cobble/mossy-cobble rims are generated at the two width
  boundaries; they are deliberately finite-height and exterior chunks generate
  as void. Only those intentionally absent exterior neighbours are treated as
  ready for client meshing, so collision-bearing boundary chunks still render.
- Fresh-world spawn selection retains vanilla's preferred circumference biome
  but constrains the width coordinate to the safe interior of the band, so a
  dedicated server cannot place first-time players in the exterior void.
- Exterior noise, surfaces, carvers, and features are suppressed during
  asynchronous chunk generation. Neither void clearing nor rim construction
  mutates live chunks on the server tick.
- Curvature is activated only in the Overworld. Nether and End terrain retain
  vanilla coordinates and rendering.

For the local smoke test, set `testMode=true` in the properties file. It
creates a creative 128-by-26-chunk (2,048×416 block) safe-small development
world and captures fixed-sun
noon, warm dusk, cool midnight, the complete visible Arch,
first-seam, second-wrap, and rim screenshots in `run/screenshots/`. It walks through two continuous seam
crossings; exercises a wrapped block action; tests moving-entity tracking,
periodic queries and collision, an arrow, a boat, a ground navigator, water
flow, an explosion, and nearby effects; checks camera continuity and
correction packets; records frame pacing; verifies the rim and void; and
saves the player just before the canonical seam for a reconnect test. Set it back to `false` for
normal play.

For the real-network regression, use three terminals:

```sh
gradle runMultiplayerServer
gradle runMultiplayerClientA
gradle runMultiplayerClientB
```

These runs use isolated directories under `run-multiplayer/` and do not touch
normal saves. The dedicated-server harness creates two actual client
connections, verifies the geometry acknowledgement, crosses the seam while
keeping both authoritative players canonical, observes the other player from both sides,
lands a real cross-seam melee hit, breaks and broadcasts a block update across the seam, tracks a server-owned
boat through the joined edge, performs an intentional long teleport and
return, then disconnects and reconnects client B. A complete pass is reported
as `[multiplayer] full scenario result=true` in the server log.

For the same-process immutable-layout lifecycle regression,
`./gradlew runLayoutSwitchClient` opens two differently sized existing saves,
verifies disconnect clearing and second-world atlas replacement, then stops
automatically. A pass is logged as `[layout-switch] result=true`.

## Current boundaries

This is the playable wraparound stage, not yet a complete rotating-gravity
simulation. Terrain and entity positions follow the curved topology, and the
Overworld now has a small fixed sun with a global dimming/colour-tone
day/night cycle.
Terrain section culling and the volumetric cloud deck now use the same
cylindrical camera space as the terrain shader. The automated suite covers an
ordinary arrow, boat, ground navigator, water outlet, explosion, nearby
effects, and a two-client dedicated-server seam/reconnect matrix. Broader
projectile, vehicle, fluid, portal, redstone, and structure combinations remain
necessary before arbitrary modded combinations can be promised.
World-generation density/noise is continuous and generation
writes are canonicalized; structures and other coordinate-sensitive generators
still need broader multi-seed seam coverage.

Every location intentionally shares one authoritative vanilla gameplay phase.
The visual tone is not a second local clock: skylight, spawning, crops,
daylight sensors, weather, live terrain, and the distant surface continue to
follow the same world time.

These constraints are intentional scope markers for the next engine-level
stage rather than claims of vanilla-client or broad-mod compatibility.

## Ringworld visibility and the Arch

There is no planetary horizon along the circumference. The inhabited surface
curves upward away from the observer, but distance and atmosphere erase nearby
detail into a mock horizon. Past that haze the rest of the world returns as a
continuous Arch: it rises from one apparent horizon, crosses the zenith, and
descends to the other. The authorized Known Space concordance describes the
same great Arch, with rapidly flaring bases that merge into atmospheric sky
and a bright, much thinner band near the zenith on Niven's vastly narrower
ring. NASA's Stanford-torus work is a smaller-scale engineering reference for
the same inside-facing rotating-habitat geometry.

References:

- [The Incompleat Known Space Concordance: Ringworld Appendix](https://news.larryniven.net/concordance/content.asp?ovr=t&page=Ringworld+Appendix)
- [NASA SP-413, Space Settlements: A Design Study](https://history.arc.nasa.gov/hist_pdfs/nasa_sp413.pdf)
- [NASA Ames/NSS space-settlement artwork archive](https://nss.org/settlement/nasa/70sArtHiRes/70sArt/art.html)

The apparent width is not an arbitrary sky texture. At the opposite side it is
derived from the configured radius and both width edges:

```text
R = circumference / 2π
oppositeWidth = atan2(maxZ - cameraZ, 2R) - atan2(minZ - cameraZ, 2R)
```

Consequently, the raw geometry of RingWorld's deliberately broad 128-by-26-
chunk safe-small development world occupies about 35 degrees across the zenith; the
15,552-by-4,096 default occupies about 45 degrees. The texture prototype uses
that physical width directly: it does not narrow, widen, or recenter the ring
for the camera. The fixed sun lies in front of the opposite ring surface, the
opaque ring lies in front of background stars.

The current renderer uses a real-chunk/GPU-texture handoff:

- Real curved chunk meshes remain authoritative nearby and are rendered after
  the sky, so the normal configured render distance is used in full and loaded
  blocks naturally overwrite every part of the textured surface.
- CPU section culling transforms each 16-block bound onto the cylinder before
  testing it against the camera. Looking upward therefore no longer removes
  chunks which the terrain shader has curved into view.
- A static cylindrical mesh covers the exact circumference and full finite
  width at the real physical radius. The mesh is uploaded once per world/atlas
  revision; walking does not rebuild it or allocate distant vanilla chunks.
- The texture mesh is sky LOD, so its vertex shader preserves the physical
  X/Y perspective but compresses clip-space depth beyond Minecraft's
  chunk-derived far plane. This is required for the 15,552-block default:
  its approximately 4,950-block diameter is much larger than an ordinary
  28-chunk level far plane. The correction is isolated to the proxy and does
  not increase real terrain render distance.
- Canonical X maps directly to texture U and the finite width maps to V. The
  complete texture therefore closes on the same seam as gameplay and remains
  fixed in world space instead of following the player.
- The eight-block server atlas is bilinearly expanded into a higher-resolution
  client texture and uploaded with a periodic-X, clamped-Z mip chain. For the
  2048-by-416 development ring that is a 2048-by-416 GPU image backed by only
  13,312 transmitted source cells.
- Biome-tinted surface colours, heights, and relief shading communicate land,
  water, biome, and
  silhouette changes beyond the loaded edge. This is intentionally a visual
  LOD: individual blocks, entities, foliage geometry, and transparent layers
  remain the responsibility of live chunks.
- The distant surface samples Minecraft's current full-skylight/no-block-light
  lightmap texel. Its RGB exposure therefore follows the same time, weather,
  gamma, lightning, darkness, and night-vision state as exposed live terrain
  instead of remaining artificially bright at night.
- Vanilla volumetric clouds start eight blocks above the saved rim top
  (Y=104 for the default 160-block wall) and use the same exact
  terrain-cylinder transform.
  They fade into the atmospheric Arch before the local grid can close into a
  full opaque tube, while broad pale Arch patches imply cloud systems beyond
  the live cloud range.
- The final real chunks use intrinsic surface distance for fog even though
  their rendered chord is shorter. The distant surface begins revealing below
  the final real chunks, retains more than half of its terrain signal while
  still partially transparent at the nominal chunk edge, and reaches full
  opacity only beyond the live range. Its reduced fog-colour component stays
  close to the terrain instead of forming a raised band. A stable fragment
  dither reveals it through the final live chunk band rather than waiting
  behind opaque terrain for the last chunk to end. The visual proxy is absent
  near the player, so it cannot draw a duplicate surface over an interactable
  rim wall or the adjacent exterior void.

The real-terrain LOD stage is now implemented. The server pregenerates the ring
asynchronously and captures a canonical periodic surface-colour atlas plus
matching heightmap from those actual chunks. Small tiles are distributed and
cached by world hash, then expanded into one GPU texture over the complete
ring-centred mesh. The source atlas deliberately remains at a compact, fixed
eight-block resolution. Biome tint, relief shading, mip filtering, live
lightmap exposure, and a fog-matched depth-aware transition are implemented,
but the atlas still cannot reconstruct block geometry, transparent layers,
buildings, local block light, or weather volumes. Those remaining fidelity
limits require richer source data rather than more real chunk loading.

## Sky and day/night design

The ring surrounds its star, so the sun must remain fixed near local zenith;
it never rises, sets, or orbits through the sky. The former moving shadow-slab
array has been removed. Day and night are now represented by a continuous
global exposure and colour shift:

- The original Minecraft sun sprite remains in use at about one tenth its
  ordinary apparent diameter: roughly 0.9 degrees instead of nine degrees.
- Noon is bright and nearly neutral; dawn and dusk are dim warm orange;
  midnight leaves only a very faint cool-blue disc.
- Values interpolate smoothly between the four keyframes with no slab edge,
  occlusion event, or sudden visual transition.
- Minecraft's existing 24,000-tick world clock still drives sky colour,
  skylight, fog, weather brightness, stars, beds, spawning, crops, daylight
  sensors, live chunks, and the distant-ring lightmap.
- The ordinary moon is hidden and stars remain stationary.

This is deliberately a global aesthetic cycle rather than a physical eclipse
simulation. Every circumference coordinate sees the same phase. The removed
twenty-panel version remains documented in
[`docs/SUN_RENDERING_SNAPSHOT_2026-07-26.md`](docs/SUN_RENDERING_SNAPSHOT_2026-07-26.md)
as a rollback target.

The sun now uses an authoritative ring/world-space pose rather than vanilla's
camera-relative sky quad. The renderer places the star at the physical centre
of the ring, derives
the view direction from the player's canonical X/Z position and tangent
frame. This prevents the sun from following or offsetting with the player's
point of view, keeps its pose continuous through the circumference seam, and
gives separated multiplayer clients one consistent celestial object.
Regression coverage should compare clients at the four cardinal ring
positions and both width edges, including a seam crossing, for stable sun
bearing and identical tone phase.
The volumetric cloud shader likewise reconstructs canonical world phase and
uses the exact terrain cylinder; it no longer creates a 1.4x local barrel
around each camera.

## Gravity and local-frame design

RingWorld deliberately retains vanilla Minecraft gravity. The simulation uses
intrinsic surface coordinates: X follows the circumference, Y is height above
the ring, and Z crosses its width. Embedding those coordinates into the curved
renderer turns vanilla downward `-Y` acceleration into local outward radial
gravity at every point on the ring. Players therefore remain upright relative
to the surface without replacing Minecraft's physics engine.

A global vector-gravity rewrite is out of scope unless the project later moves
entities into literal Cartesian space around the star. Avoiding that rewrite
preserves compatibility with movement, fall damage, mobs, fluids, vehicles,
projectiles, and other mods. Falling beyond a broken rim continues toward
lower intrinsic Y and into the exterior void, which is also the expected
outward direction for a rotating ring.

The current curved-local-frame stage provides:

- Distant entity models rotate into the tangent frame at their X.
- The player's camera stays upright in its own local frame without seam pops.
- Automated seam probes cover a boat, arrow, particle, ground navigator,
  water flow, and explosion while retaining familiar Minecraft physics.

Next-stage compatibility work should broaden that coverage to portals,
additional vehicles and projectiles, complex fluid/redstone layouts, and
modded entities.

As before, treat Coriolis forces and physically exact ballistic chords as
optional simulation features, not requirements for a playable ringworld.
