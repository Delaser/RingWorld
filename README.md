# RingWorld

Fabric mod targeting Minecraft Java 1.21.11. It makes the Overworld finite
across Z and periodic across X, with a curved client terrain view.

Before creating a world, edit `config/ringworld.properties`:

```properties
widthBlocks=4096
circumferenceBlocks=15552
wallHeightBlocks=160
pregenerateTerrainAtlas=true
```

Width and circumference must be multiples of 16. They are saved into the
world on first load and cannot be changed later. The width is centred on Z=0,
so the default band spans Z=-2048 through Z=2047. `wallHeightBlocks` is
measured from the world minimum height; the default puts the rim top near Y=96.
Both edges use five-block-thick, deterministically varied cobblestone and mossy
cobblestone. The exterior is cleared to void. The rim
is intentionally breakable: leaving the ring is allowed.
`pregenerateTerrainAtlas=true` generates one chunk asynchronously at a time
until the distant-ring overview is complete. Set it to `false` only when an
administrator wants to postpone that background pregeneration.

Build with the included Gradle wrapper and Java 21:

```sh
./gradlew build
```

Install `build/libs/ringworld-0.1.0.jar` on the server and every client. The
server rejects a client that cannot receive the required geometry handshake.

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
  1,600-block development ring.
- Beyond loaded chunks, one static GPU mesh continues the real cylinder
  through the zenith and back to the opposite apparent horizon. It is textured
  from the canonical surface atlas, uses the exact configured radius and width,
  and changes only one ring-centred model transform as the camera moves.
- The source texture is an eight-block-resolution periodic height-and-colour
  atlas captured from the world's actual generated chunks. The client expands
  it to a block-scale GPU image for the small test ring (bounded to 4096 by
  1024 texels on larger worlds), so terrain silhouettes, land, water, and biome
  colours stay anchored to their canonical locations around the complete ring.
- Atlas generation is incremental and persisted under the world `data/`
  directory. It is split into small network tiles, cached by immutable world
  hash on each client, and reused on reconnect without retransmission.
- The curvature shader reconstructs the camera using vanilla's integer origin
  and negative fractional offset convention, so sub-block movement remains
  continuous instead of snapping at every block boundary.
- Cylindrical worldgen transforms only coordinate-consuming density leaves.
  Vanilla's `ChunkNoiseSampler` identity, density caches, and aquifer-local
  coordinates therefore remain intact.
- Breakable five-block cobble/mossy-cobble rims are generated at the two width boundaries; they
  are deliberately finite-height and exterior chunks generate as void.
- Fresh-world spawn selection retains vanilla's preferred circumference biome
  but constrains the width coordinate to the safe interior of the band, so a
  dedicated server cannot place first-time players in the exterior void.
- Exterior noise, surfaces, carvers, and features are suppressed during
  asynchronous chunk generation. Neither void clearing nor rim construction
  mutates live chunks on the server tick.
- Curvature is activated only in the Overworld. Nether and End terrain retain
  vanilla coordinates and rendering.

For the local smoke test, set `testMode=true` in the properties file. It
creates a creative 100-by-20-chunk development world and captures fixed-sun
noon, moving-panel dusk, shadow-panel midnight, the complete visible Arch,
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

## Current boundaries

This is the playable wraparound stage, not yet a complete rotating-gravity
simulation. Terrain and entity positions follow the curved topology, and the
Overworld now has a fixed sun with a global shadow-panel day/night cycle.
Terrain section culling and the volumetric cloud deck now use the same
cylindrical camera space as the terrain shader. The automated suite covers an
ordinary arrow, boat, ground navigator, water outlet, explosion, nearby
effects, and a two-client dedicated-server seam/reconnect matrix. Broader
projectile, vehicle, fluid, portal, redstone, and structure combinations remain
necessary before arbitrary modded combinations can be promised.
World-generation density/noise is continuous and generation
writes are canonicalized; structures and other coordinate-sensitive generators
still need broader multi-seed seam coverage.

Position-aware travelling eclipse bands are designed but deliberately not
shipped yet. Local visual darkness must arrive atomically with position-aware
skylight, spawning, crops, daylight sensors, weather, and other server
mechanics; until then, every location shares the same authoritative phase.

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

Consequently, the raw geometry of RingWorld's deliberately broad 100-by-20-
chunk development world occupies about 35 degrees across the zenith; the
15,552-by-4,096 default occupies about 45 degrees. The texture prototype uses
that physical width directly: it does not narrow, widen, or recenter the ring
for the camera. The fixed sun lies in front of the opposite ring surface, the
shadow panels lie in front of the sun, and the opaque ring lies in front of
background stars.

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
- Canonical X maps directly to texture U and the finite width maps to V. The
  complete texture therefore closes on the same seam as gameplay and remains
  fixed in world space instead of following the player.
- The eight-block server atlas is bilinearly expanded into a higher-resolution
  client texture. For the 1600-by-320 development ring that is a 1600-by-320
  GPU image backed by only 8000 transmitted source cells.
- Actual surface map colours and heights communicate land, water, biome, and
  silhouette changes beyond the loaded edge. This is intentionally a visual
  LOD: individual blocks, entities, foliage geometry, and transparent layers
  remain the responsibility of live chunks.
- Vanilla volumetric clouds start eight blocks above the configured test rim,
  at Y=104, and use the same exact terrain-cylinder transform.
  They fade into the atmospheric Arch before the local grid can close into a
  full opaque tube, while broad pale Arch patches imply cloud systems beyond
  the live cloud range.
- The final real chunks use intrinsic surface distance for fog even though
  their rendered chord is shorter, with a slight distance boost to thicken the
  last-chunk haze. A sky-coloured atmospheric veil reaches a broad full-fog
  plateau around the nominal chunk edge and clears gradually over the distant
  proxy. A second layered curtain is rendered after terrain so streaming gaps
  and depth/order disagreement cannot expose the live-terrain/Arch line.

The real-terrain LOD stage is now implemented. The server pregenerates the ring
asynchronously and captures a canonical periodic surface-colour atlas plus
matching heightmap from those actual chunks. Small tiles are distributed and
cached by world hash, then expanded into one GPU texture over the complete
ring-centred mesh. The current prototype deliberately keeps the source atlas
at a compact, fixed eight-block resolution; mipmapping, atmospheric colour
grading, and a cleaner depth-aware transition remain refinement work. Weather
volumes still need a canonical far-field representation beyond the local cloud
deck. Later, authoritative position-aware eclipse bands can
light different parts of the visible Arch only after the same local phase also
drives skylight, spawning, crops, sensors, and other server mechanics.

## Sky and day/night design

The ring surrounds its star, so the sun must remain fixed near local zenith;
it must never rise, set, or orbit through the sky. Night is produced by a
rotating inner array of shadow panels between the star and the inhabited
surface, following the classic ringworld solution:

- The sun is a stationary disc overhead at every circumference coordinate.
- A panel's leading edge crosses the sun at dusk, its body eclipses the sun
  through the night, and its trailing edge reveals the sun at dawn.
- Sky colour, skylight, stars, fog, weather brightness, solar sensors, and mob
  spawning follow the same authoritative 24,000-tick clock as the eclipse.
- There is no ordinary moving moon. A moon or artificial night light could be
  added later as a separate ring-system object, but is not part of the base
  day/night cycle.
- The default cadence should retain Minecraft's 24,000-tick day for familiar
  gameplay, with a future world setting for day length and panel spacing.

The current implementation follows the classic scale model rather than using
one enormous shutter. Twenty small rectangular panels are spaced every 18
degrees on a shared inner orbit. The array advances only 18 degrees per
Minecraft day, so a complete revolution takes twenty days, but the next
evenly spaced panel still produces one eclipse per 24,000-tick gameplay day.
Each panel spans about six orbital degrees instead of wrapping across most of
the sky. Its width matches the visible sun and its roughly 2:1 planform is
close to the 2.5:1 proportion of the reference shadow squares.

The sun stays fixed at local zenith, the moon is suppressed, and stars no
longer rotate. All twenty panels share an orbit whose curvature is centered
on the sun, not the player; nearby panels pass in front of the sun while the
rest recede around it. The leading edge of the next panel makes first contact
at dusk, that panel centers on the sun at midnight, and its trailing edge
clears the sun at dawn. Vanilla sky colour, ambient darkness, skylight, beds,
spawning, crops, and daylight sensors remain on the same authoritative clock,
so the visual night and gameplay night agree.

The reference design describes twenty wire-linked panels at roughly
Mercury's orbital radius, each about 1.6 million by 4 million kilometres and
spaced about 9.6 million kilometres apart. Their motion relative to the much
faster rotating inhabited ring produces a thirty-hour local cycle: nine hours
of night and two 45-minute eclipse-like twilight periods. A passive array at
that scale would not behave like an ordinary set of independent satellites;
the linked structure and its alignment require active megastructure-scale
control. RingWorld preserves that visual and kinematic idea while scaling the
cadence to Minecraft's day and its deliberately oversized sun sprite.

The physically stronger follow-up is spatial illumination. Repeated shadow
bands travel around the circumference, so local time depends on both world
time and ring X. Conceptually:

```text
localPhase = fractional(worldTime / dayLength - canonicalX / shadowBandSpacing)
```

That stage must make skylight, spawning, crops, solar sensors, weather, and
client rendering query the same local eclipse function. It should not ship as
visual-only local darkness, because that would let neighbouring regions look
like night while the server still simulates global daytime.

The sun now uses an authoritative ring/world-space pose rather than vanilla's
camera-relative sky quad. The renderer places the star at the physical centre
of the ring, derives
the view direction from the player's canonical X/Z position and tangent
frame, and applies that same transform to the shadow-panel orbit. This prevents
the sun from following or offsetting with the player's point of view, keeps
its pose continuous through the circumference seam, and gives separated
multiplayer clients one consistent celestial object. Regression coverage
should compare clients at the four cardinal ring positions and both width
edges, including a seam crossing, for stable sun bearing and panel occlusion.
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
