# Dimension scaling audit and implementation plan

Status: issue #24 custom-dimension matrix completed, source- and runtime-audited
on 2026-08-01. Phases 1–6 are implemented. Phase 7 has completed pure validation, the
safe-small view-distance matrix, minimum-width stress, dedicated two-client
coverage, production/long/wide topology and rim runs, and a same-process saved
layout switch. The production full-atlas throughput/transfer benchmark and
broader cross-size complete-atlas visual/frame-pacing gates remain open as
marked below.

## Goal

Any supported width and circumference selected before world creation should
produce the same coherent experience as a deliberately tuned reference world:

- one canonical circumference with a continuous seam;
- a finite, centred width with visible, collidable rims;
- one physical cylinder shared by blocks, entities, clouds, sun, culling, and
  the complete-ring texture;
- a live-terrain/LOD transition that remains aligned at practical render
  distances;
- predictable atlas, network, memory, and pregeneration cost;
- validation that rejects geometry which cannot represent the full Overworld
  height safely;
- no dependency on a single hardcoded development world.

Width and circumference remain independent user choices. Presets may preserve
a familiar apparent ring width, but the mod must not silently change one field
when the creator edits the other.

## Important finding: the current development circumference is vertically unsafe

The renderer uses:

```text
radius R              = circumference / 2π
surface reference S   = 64
physical radius at Y  = R + S - Y
physical centre Y     = R + S
```

The complete rendered Overworld reaches the top plane at Y=320. A valid
cylinder must keep `R + S - Y` positive over the supported vertical range,
with additional clearance so high blocks, entities, clouds, and numerical
error do not collapse into the ring centre.

| Geometry | Radius | Physical centre Y | Clearance to Y=320 | Opposite ring width | Canonical chunks | Atlas cells |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Retired/legacy, 1600×320 | 254.648 | 318.648 | **-1.352** | 34.88° | 2,000 | 8,000 |
| Production default, 16384×256 | 2607.595 | 2671.595 | 2351.595 | 2.81° | 16,384 | 65,536 |
| Active safe-small reference, 2048×416 | 325.949 | 389.949 | 69.949 | 35.39° | 3,328 | 13,312 |

The current 100-chunk circumference works at ordinary terrain elevations, but
its highest build layers reach or pass through the physical centre. It is not
a valid minimum-size proof.

For a selected vertical clearance `K`, validation should use:

```text
minimum circumference =
    alignUpTo16(2π × (topYExclusive - surfaceReferenceY + K))
```

With vanilla `topYExclusive=320`, `surfaceReferenceY=64`, and 64 blocks of
clearance, the mathematical minimum aligns to 2,016 blocks. A
2,048-block/128-chunk circumference is the cleaner supported small preset.
A 416-block/26-chunk width preserves almost the same apparent 35-degree width
as the current 1,600-by-320 test world.

## Variable classification

Every value in the following registry belongs to one of four classes:

- **authoritative**: saved with the world and included in its compatibility
  identity;
- **derived**: calculated from authoritative settings and dimension bounds in
  one shared helper;
- **profile**: visual or resource policy calculated from geometry, client
  render distance, and an explicit quality budget;
- **fixed design**: intentionally independent of dimensions, documented and
  tested so it is not mistaken for an unfinished hardcode.

### Authoritative settings and lifecycle

| Variable/current source | Current behavior | Required treatment |
| --- | --- | --- |
| `widthBlocks`, `RingWorldSettings` / `RingWorldConfig` | Default and minimum 256; chunk aligned; persisted, validated, previewed, and sent | Authoritative. The slender default is an intentional visual choice. Upper/resource validation is enforced before new settings or atlas allocation. |
| `circumferenceBlocks`, same classes | Default 16,384; structural minimum 1,024; playable layouts additionally require full-height radial clearance | Authoritative. The safe-small preset is 2,048; 1,600 is accepted only when already persisted as a legacy world. The default is a power of two and exactly 32 region widths, but custom layouts must remain fully supported. |
| `wallHeightBlocks` | Default 160; persisted, validated, and used by generation, migration, clients, and clouds | Authoritative. Changing bootstrap config cannot alter an existing world's rims. |
| `generatorSeed` | Persisted, sent, and fingerprinted | Authoritative and present in world/atlas identity. |
| `FORMAT_VERSION` | Settings format 2 | Format 1 migrates explicitly with surface reference Y=64. |
| surface reference | Saved and sent as `surfaceReferenceY`; format 2 currently requires 64 | Authoritative protocol/layout field; all active shaders consume the synchronized value. |
| rim thickness/style version | Thickness 5 and about 30% mossy remain code constants | Fixed design included in layout and atlas fingerprints. |
| `testMode` | Process-local destructive harness switch | Keep operational, never persist as world geometry. |
| `pregenerateTerrainAtlas` | Process-local administration switch | Keep operational. It may pause work but must not change atlas identity or dimensions. |
| initial-spawn Z clamp | `RingSpawnBounds` receives validated bootstrap geometry before saved data exists | Deliberate creation-time exception only. Keep the finite-rim margin loader-neutral and do not read bootstrap configuration from saved-world runtime paths. |

The earlier lifecycle race is removed. `ServerWorldMixin` loads or creates
persisted settings and attaches the Overworld generator at the constructor
tail, before the lifecycle event loads the atlas. `ServerChunkManagerMixin` no
longer installs bootstrap geometry.

### Derived topology and physical geometry

| Derived value | Current source/formula | Scaling requirement |
| --- | --- | --- |
| circumference chunks | `circumferenceBlocks / 16` in several classes | Expose once through `RingGeometry`; use it for holders, tickets, watch windows, atlas scans, and tests. |
| width chunks | `widthBlocks / 16` | Expose once and use it for atlas/preload cost and boundary rows. |
| `minWidthZ`, `maxWidthZ` | `-W/2`, `min+W-1` | Keep derived and centred. Test every supported width, including the minimum. |
| radius | `C / 2π` | Keep one double-precision Java source and synchronize shader inputs. |
| physical centre Y | `radius + surfaceReferenceY` | Add as a named derived value and use it for vertical-safety validation and the star direction. |
| physical radius at Y | `radius + surfaceReferenceY - Y` | Require a positive safety margin through the top rendered plane. Test the bottom, terrain, wall top, cloud base, and build top. |
| wall top | `RingGenerationBoundary.wallTopExclusive(worldMinY, worldHeight, wallHeightBlocks)` | Derive from saved wall height and actual Overworld bounds. The rim stops at this exclusive bound without erasing naturally generated terrain above it. |
| playable interior width | `width - 2 × rimThickness` | Validate enough interior remains for spawn and normal terrain. Do not let rims overlap on narrow bands. |
| spawn-safe width range | current margin `min(32, max(1, W/4))` | Include rim thickness and a named safety clearance. Test minimum and narrow widths. |
| opposite apparent width | `2 atan((W/2)/(2R))` at the midline | Show in the creation UI. Use warnings/presets, not an implicit width rewrite. |
| maximum around-ring surface distance | `C/2` | Use in render-profile clamping. No handoff end or fog tail may extend beyond the same physical half-ring. |
| nearest-image/view relationship | periodic chunk distance | Test view distances below, equal to, and above half the circumference chunk count. Exact opposite-chunk ties must remain stable. |

The existing `RingGeometry` should remain the authoritative home for pure
coordinate math. Vertical bounds and presentation policies should be added as
named derived helpers rather than copied formulas.

### Rims, clouds, and vertical presentation

| Variable/current source | Current value | Required treatment |
| --- | ---: | --- |
| `RingGenerationBoundary.RIM_THICKNESS` | 5 blocks | Fixed design for now; validate it against width and include it in worldgen identity. |
| wall-height minimum | 32 blocks | Reassess against build bounds and cloud placement. Give validation errors in absolute Y as well as height-from-bottom. |
| wall materials | cobble/mossy cobble, about 30% mossy | Fixed design; no dimension scaling required. |
| cloud base | Synchronized `wallTopY + 8` | Derived from saved wall height and actual world bottom. |
| cloud clearance above rim | Named fixed value, 8 blocks | Validated against the build ceiling. |
| cloud curvature reference | Named Globals fields | Uses synchronized surface reference, circumference, and physical centre. |
| cloud local arc | `min(FogCloudsEnd×0.82, C×0.12)`, start at 55% | Move into the render profile and test small/large circumferences and multiple cloud distances. |
| star direction | `RingGeometry.directionToRingCenter` | Already dimension-derived. Add width-edge and vertical-extreme tests. |
| sun apparent size and day length | about 0.9°, 24,000 ticks | Fixed design. Do **not** scale with circumference unless the art direction changes. |

The format-2 settings handshake sends width, circumference, seed, wall height,
surface reference, format, and a stable layout fingerprint. The client
recomputes that fingerprint before installing the geometry and acknowledges
only format plus the independently verified value.

### Live terrain, complete-ring LOD, fog, and shader values

The active path has dimension-aware geometry but dimension-sensitive tuning is
split between Java and GLSL.

| Variable/current source | Current value | Required treatment |
| --- | ---: | --- |
| shader layout transport | Named RingWorld fields appended to the Globals UBO | Carries activation, C/W, saved wall height, vertical layout, view distance, handoff, detail, and haze without altering menu blur. |
| terrain shader surface reference | `RingWorldVertical.x` | Synchronized from the saved format-2 layout. |
| terrain fog distance scale | `1.02` in `terrain.vsh` | Profile constant; calibrate across curvature ratios `viewDistance/C`. |
| live-terrain dither | `RingRenderProfile`: 78% to 102% of effective view | Shared with proxy values and clamped at `C/2`. |
| proxy opacity | `RingRenderProfile`: 68% to 98% in visual profile 5 | Centralized and clamped at `C/2`; reaches opacity before the live edge so dither cannot expose translucent sky. |
| proxy detail | `RingRenderProfile`: 76% to 125% with a 16-block minimum span | Centralized and clamped at `C/2`. |
| proxy reveal | 0.52 to 0.98 in visual profile 5 | Profile/art constant; raised after derived-pitch 6/12/28 captures exposed a blue handoff band. |
| far haze | 0.04 to 0.16 with exponent 1.35 in visual profile 5 | Profile/art constant; use normalized half-ring distance from the shared layout. |
| short-ring behavior | Effective view and every endpoint clamp to `C/2` | Whole-ring requests are explicit and unit-tested. |
| predecessor visibility/taper | Deleted | The active exact-width texture path is the only distant-ring renderer. |
| active mesh sizing | One segment/band per eight blocks, capped at 2,048×128 | Profile 5 retains eight-block height spacing for the 16,384×256 default; larger/wider layouts remain budget-capped. |
| active surface model offset | Camera angle/Z remain dynamic transform values; layout/profile use named Globals fields | Keep camera-local values per draw; do not duplicate immutable geometry there. |
| sun quad internals | half-width 3, visible fraction 0.2625, nominal render distance 100 | Fixed angular design. Use the named render-distance constant in the calculation or delete it if redundant; do not derive sun angle from C/W. |

`RingRenderProfile` is computed from immutable layout plus current client view
distance. It owns live/proxy/dither/detail distances and GPU texture/mesh
budgets. The cloud fade still combines Minecraft's cloud distance with C in
the cloud shader; moving this final policy into the shared profile is open.

### Atlas, GPU, network, and pregeneration budgets

| Variable/current source | Current value/limit | Scaling risk and required treatment |
| --- | --- | --- |
| `SAMPLE_STEP_BLOCKS` | 8 | #69 retains one fixed profile: finer 4/2/1 candidates cost 4×/16×/64× cells and transfer without changing current GPU output. Persist and version the chosen step before any future adaptive experiment. |
| atlas columns/rows | `ceil(C/step)`, `ceil(W/step)` with checked `long` cost | Allocation is rejected above 16,000,000 cells. |
| atlas arrays | Checked cell count before Java allocation | Creation UI/log reports raw memory estimate. |
| `TILE_SIZE` | 16 cells, 128 blocks at step 8 | Fixed transport unit is acceptable. Validate metadata before allocation. |
| tile coordinates | `TileCoordinate` record | No 16-bit packing limit or collision. |
| tile payload cap | 4,096 bytes; actual full tile about 1,794 bytes including dimensions | Fixed safety cap; keep and test edge tiles. |
| GPU texture cap | 4,096×1,024 | Treat as a quality budget, not geometry. Report effective blocks/texel and avoid pretending oversampling adds atlas detail. |
| mesh cap | 2,048 circumference segments × 128 width bands, 1,572,864 vertices | Profile 5 raised the old 512-segment cap after production six-chunk captures exposed 32-block triangles. Issue #69 still owns adaptive quality/resource tiers for larger layouts. |
| mip count | stops when the smaller texture axis reaches one | Keep, but test very narrow and non-power-of-two widths. |
| pregeneration work | Checked `long (C/16)×(W/16)` chunks, one in flight | Creation UI reports total; server status logs cells/s and ETA. Large-ring benchmarking remains open. |
| pregeneration queue gate | fewer than 64 pending tasks | Operational profile; benchmark rather than scale linearly from geometry. |
| stream rate | 8 tiles/tick | Operational profile; add byte/time estimates and avoid login bursts for large atlases. |
| save/broadcast cadence | 200/20 ticks | Operational profile; benchmark dirty-set and save cost at large atlas sizes. |
| atlas world hash | Layout fingerprint plus atlas format/sample semantics | Seed, C/W, wall, surface, format, rim thickness/style, and atlas meaning invalidate cache. |
| noise coordinate precompute | two `int[C]` arrays up to C=1,048,576 | About 8 bytes per circumference block and cached per geometry. Add lifecycle/budget handling; the trigonometric fallback above the threshold is a performance cliff that must be benchmarked. |
| shader numeric precision | circumference converted to `float` | Define a supported maximum or use a high/low phase representation before block precision is lost on very large rings. |
| proxy projection depth | physical mesh used the chunk-derived level far plane | Visual profile 5 preserves profile 4's physical X/Y/W correction and compresses only far-out proxy clip-space Z; never solve this by increasing real chunk distance. Test tangent and radial-up views independently. |

The 16,384×256 production default is 16,384 chunks and 65,536 source cells.
Dimension selection must present this as an operational cost, not only as a
geometric choice. A complete-ring proxy currently does not appear until every
atlas cell is present.

### Test harness, deployment, and documentation assumptions

The following values are test fixtures, not product geometry, and must be
derived or explicitly labelled:

| Location/assumption | Current value | Required treatment |
| --- | ---: | --- |
| local harness creation log/config | 128×26 chunks / 2048×416 | Safe-small reference is active; next load a named matrix case. |
| local visual view distance | 28 chunks | Test several distances and clamp render-profile math against C/2. |
| local upward capture pitch | -52° | Derive from the target intrinsic distance and ring curvature. |
| local test Y positions | 106, 119, 120 and minimum 120 | Select from actual terrain, wall top, and world bounds. |
| local fixed X positions | 100 and seam offsets | Use named safe fractions/offsets and validate them against C. |
| multiplayer far teleport | X=64.5 | Derive a non-seam location from C and the view window. |
| server deployment geometry | 2048×416, wall height 160 | Safe-small public test deployment; the retired 1600×320 save exists only as a rollback backup and validation-failure fixture. |
| server view/simulation distance | 28 / 8 chunks | Treat as deployment policy and include it in the geometry compatibility report. |
| unit tests | include current-default 16384×256, former-wide 15552×4096, 1600×320, and atlas-only 1024 circumference cases | Keep current defaults explicit while retaining former-wide and non-power-of-two layouts as regression fixtures. Atlas math may use a tiny non-renderable fixture, but it must be named as such. |
| visual docs/captions | “100×20 test ring” and Y=104 clouds | Update in the same phase as the implementation and deployed fixture. |

## Target architecture

### One immutable layout

Extend the existing settings/geometry boundary rather than introducing local
formulas:

```text
RingWorldSettings
  authoritative saved fields
       |
       v
RingGeometry
  topology, radius, width bounds, physical transforms,
  vertical safety, cost estimates
       |
       +--> RingWorld server/worldgen layout
       |
       +--> versioned settings payload + fingerprint
       |
       +--> RingRenderProfile(geometry, world bounds, view distance, quality)
                 |
                 +--> terrain shader
                 +--> ring-surface shader
                 +--> cloud shader
                 +--> culling and visual tests
```

Suggested authoritative fields for the next settings format:

```text
widthBlocks
circumferenceBlocks
generatorSeed
wallHeightBlocks
surfaceReferenceY
layoutVersion
```

Rim thickness, cloud clearance, atlas semantics, and visual-profile version may
remain code-defined initially, but any change that alters generated blocks or
cache meaning must invalidate the appropriate fingerprint.

### Validation output

Validation should return structured errors, warnings, and estimates rather
than throwing the first generic `IllegalArgumentException`.

Required errors:

- width and circumference are not positive chunk multiples;
- width leaves insufficient interior after both rims;
- circumference fails full-height radial clearance;
- wall top/cloud base cannot fit the Overworld bounds;
- shader/coordinate representation cannot preserve required precision;
- atlas dimensions overflow transport or allocation limits.

Required warnings:

- view distance reaches or exceeds half the circumference;
- apparent ring width is unusually narrow or fills most of the sky;
- atlas/pregeneration estimate exceeds tested budgets;
- GPU blocks-per-texel exceeds the high-fidelity target;
- the selected dimensions fall outside the automated test matrix.

The creation UI and dedicated-server startup log should show:

```text
chunks around × chunks across
radius and physical centre Y
opposite ring angular width
wall top and cloud base Y
radial clearance at build top
canonical chunks to pregenerate
atlas source cells / estimated memory
GPU texture dimensions / blocks per texel
maximum tested view distance
```

## Implementation phases

### Phase 1: central model and hard validation

1. Add named derived geometry methods and checked cost calculations.
2. Define the supported vertical clearance and maximum allocation/precision
   envelope.
3. Reject the current 1,024-block nominal minimum for playable render geometry.
4. Add structured validation results and unit tests.
5. Add the safe small 2,048-by-416 reference preset.
6. Keep tiny geometries only in isolated pure atlas/topology tests where
   physical rendering is not constructed.

Exit condition: every world-shaping and cost value can be obtained from one
pure model, and invalid dimensions fail before a world or atlas is allocated.

Implemented:

- `RingGeometry` now owns radius-at-height, physical-centre, chunk-count, and
  opposite-angular-width derivation.
- `RingDimensionReport` supplies structured full-height, rim, wall, axis, and
  atlas-budget validation plus cost estimates.
- New settings are rejected before persistence when this report is invalid;
  legacy saved settings remain loadable for an explicit migration path.
- The active local and multiplayer fixtures use 2048×416. Tiny geometries are
  labelled as non-renderable pure topology/atlas fixtures in tests.

### Phase 2: persistence, bootstrap ordering, and protocol

1. Make saved wall height authoritative.
2. Remove the bootstrap-config/persisted-settings generator race.
3. Add format-1 migration with explicit defaults for new layout fields.
4. Send the full immutable layout/fingerprint to clients and validate the same
   fingerprint in the acknowledgement.
5. Include every terrain-affecting value in atlas/cache identity.
6. Verify two different local worlds can use different saved layouts during
   one game process without stale state.

Exit condition: config is used only to create new settings; saved settings win
before any chunk generation, on both integrated and dedicated servers.

Implemented:

- Settings format 2 saves wall height and surface reference, and explicitly
  migrates format 1.
- Saved settings attach before geometry-dependent Overworld generation;
  bootstrap config is now new-world input only.
- The settings payload carries the complete layout and fingerprint. Its
  acknowledgement verifies a client-recomputed fingerprint.
- Layout and terrain-atlas identity include seed, dimensions, wall/surface
  values, format, rim thickness/style, and atlas semantics.

Still to verify in Phase 7: switch repeatedly between fresh worlds with
different layouts/seeds in one game process and inspect both live and cached
terrain.

### Phase 3: synchronized rendering and vertical layout

1. Introduce a named RingWorld shader uniform block.
2. Remove `MenuBlurRadius` geometry packing.
3. Synchronize circumference, radius or phase representation, width bounds,
   surface reference, wall top, cloud base, half-circumference, and effective
   view distance.
4. Replace Y=64/Y=104 shader literals with synchronized values.
5. Derive clouds from wall top plus clearance.
6. Verify sun direction at the width edges and at vertical extremes.
7. Remove the dormant CPU Arch renderer and obsolete `RingVisibility`
   constants before further handoff tuning.

Exit condition: changing saved dimensions or wall height leaves no old test
geometry in any active shader or sky effect.

Implemented:

- Globals now has named RingWorld layout, vertical, render, handoff, and detail
  fields; vanilla menu blur is untouched.
- Terrain, clouds, and the complete-ring surface consume the same synchronized
  C/W, surface, wall/cloud, centre, view, transition, and haze values.
- Cloud base derives from saved wall top plus eight blocks.
- The dormant CPU Arch and obsolete `RingVisibility` code/tests are deleted.
- Pure geometry tests cover the sun/centre direction from the band centre and
  both width edges.

### Phase 4: one adaptive live/LOD profile

1. Create `RingRenderProfile`.
2. Make proxy opacity, terrain dither, detail reveal, fog, far haze, and cloud
   fade consume the same calculated distances.
3. Handle view distances below, near, and beyond `C/2`.
4. Derive mesh subdivisions from angular/height error within a vertex budget.
5. Report texture resolution and blocks per texel.
6. Capture aligned live/LOD transitions at all matrix sizes before accepting
   new tuning.

Exit condition: there is one tested handoff definition, no duplicated Java and
GLSL percentages, and no interactable rim is covered by proxy geometry.

Implemented:

- `RingRenderProfile` visual-policy version 1 owns live dither, proxy alpha,
  detail reveal strength, haze endpoints/exponent, curved-cloud fade, texture
  resolution, mesh subdivisions, and whole-ring `C/2` clamping.
- Java publishes the calculated endpoints directly to both active shaders.
- GPU resources are bounded at 4,096×1,024 texels and 2,048×128 mesh cells, and
  the creation editor reports effective blocks per texel, vertex count, GPU
  texture/mesh bytes, and conservative build scratch.
- Unit tests cover safe-small, production, and whole-ring profiles.

Open:

- Finish issue #66's safe-small profile-5 and weather/translucency review. The
  production 6/12/28 alignment and mesh comparison are recorded in
  `ATLAS_VISUAL_BASELINE_2026-08-01.md`.

### Phase 5: atlas and operational scaling

1. Use checked `long` cost arithmetic before array construction.
2. Establish tested memory, disk, transfer, and pregeneration budgets.
3. Replace 16-bit packed tile coordinates or enforce the resulting limit.
4. Decide whether sample step is fixed, adaptive-and-persisted, or replaced by
   a paged atlas.
5. Benchmark the noise-coordinate cache and remove its large-world performance
   cliff.
6. Add admin-visible progress/ETA and make large-world cost explicit.

Exit condition: every supported creation choice has bounded allocations and an
honest path to a complete distant-ring texture.

Implemented:

- Atlas cell/dimension multiplication is checked before allocation and capped
  at 16,000,000 cells.
- Chunk and cell progress counters use `long`.
- Tile work queues use a typed coordinate record rather than packed 16-bit
  halves.
- Server logs include raw transfer estimates, cells per second, and ETA.
- Gamemaster-level `/ringworld atlas status|pause|resume` commands expose and
  control background pregeneration without mutating saved layout.
- Noise-coordinate caches are cleared when the Overworld unloads.

Open:

- Benchmark the production default end to end and decide whether a fixed
  eight-block sample step remains operationally acceptable.
- Measure compressed disk size and wall-clock completion in the production
  benchmark; static raw/GPU/scratch budgets are now explicit.

### Phase 6: world-creation and dedicated-server UX

1. Add width, circumference, and wall-height controls to world creation.
2. Provide safe-small, production-default, and advanced custom presets.
3. Show the validation/cost report live.
4. Require confirmation that dimensions are immutable.
5. Keep equivalent first-world bootstrap properties for dedicated servers.
6. Refuse or explicitly migrate existing flat worlds rather than silently
   assigning RingWorld settings.

Exit condition: creators can select a valid layout without editing a file, and
both client and dedicated-server flows apply identical validation.

Implemented:

- The Create World screen opens a RingWorld editor for circumference, width,
  and wall height with safe-small, production, and current presets.
- The editor previews chunks, physical dimensions, wall/cloud elevation,
  clearance, atlas memory, GPU resolution, blocks per texel, and vertex count;
  invalid layouts cannot be applied.
- Applying a valid layout opens an explicit confirmation that repeats the
  immutable circumference, width, and wall height.
- Dedicated first-world properties pass through the same validator and startup
  report.
- A world with existing Overworld region files but no RingWorld saved settings
  is refused instead of silently converted.

### Phase 7: multi-size automation and deployment

Parameterize unit, local visual, and two-client tests. At minimum cover:

| Case | Purpose |
| --- | --- |
| 2048×416, view 6/12/28 | Safe small reference; strong visible curvature |
| 2048×256 | Minimum-width/rim/spawn/cloud stress |
| 16384×256, view 12/28 | Production default, minimum width, 32-region alignment, and approximately 63-minute circumference |
| 15552×4096, view 28 | Former-wide default retained as a sky/resource regression |
| 32768×512 | Long, narrow ring; low local curvature and atlas aspect ratio |
| 4096×2048 | Wide-band sky, width-edge sun tilt, and finite-Z culling |
| view distance at/above C/2 in a bounded fixture | Whole-ring watch/filter and LOD special case |
| C=1600 with full vanilla height | Required validation failure |
| misaligned, overlapping-rim, excessive-atlas inputs | Required validation failures |

The pure unit matrix covers safe-small, the aligned 2,016×256 playable
minimum, 2,048×256 narrow stress, the 16,384×256 production default,
15,552×4,096 former-wide regression, 32,768×512 long/narrow, 4,096×2,048
wide/medium, and a 4,096×640 custom-wall layout. It parameterizes render
distances 6/12/28/64, whole-ring clamping, atlas dimensions/GPU budgets,
worldgen seam coordinates and finite-band limits, spawn bounds, settings payload
identity/acknowledgement rejection, the 1,024 structural-only and 1,600 unsafe
curvature cases, misalignment, custom wall/cloud elevation, excessive atlas
input, and the maximum technical circumference warning envelope. The local
harness capture distance is
parameterized by `testViewDistanceChunks` for 6/12/28 runs, and its pitch is
derived from the physical target surface at that distance.

Runtime evidence on 2026-07-26: a fresh 2,048×416 format-2 world completed the
28-chunk live/LOD capture, two natural seam crossings, gameplay probes, and rim
probe. Both crossings retained yaw/pitch with zero correction packets; block,
entity, projectile, vehicle, AI, fluid, explosion, late tracking, collision,
rim, and exterior-void checks passed. The first crossing averaged 19.5 ms per
rendered frame under the destructive harness load; the rim interval averaged
16.9 ms.

Issue #24 also ran the isolated dedicated stronghold/worldgen gate against the
aligned 2,016×256 playable minimum and a 4,096×2,048 wide layout with custom
192-block wall height, with atlas pregeneration disabled. Both passed periodic
base-height/base-column queries, canonical stronghold/portal-room bounds,
portal activation, folded Eye target, both textured rim rows through their
saved wall height, and generated exterior void. The minimum run reported
126×16 chunks and 8,064 atlas cells; the wide/custom-wall run reported 256×128
chunks, 131,072 cells, wall top Y=128, and cloud base Y=136. These targeted
server checks complement, rather than replace, existing
safe-small/production client and multiplayer evidence.

The derived-pitch 6-chunk case then completed on 2026-07-27. It aimed directly
at the 96-block handoff (pitch 18.30° for the sampled Y=76.41 surface), retained
camera orientation through both crossings, and passed every gameplay/rim
probe, including AI after the stabilized 240-tick observation. Its first-seam
and rim averages were 15.9 ms and 13.3 ms.

Derived-pitch 12- and 28-chunk captures are also complete. After comparison
tuning, visual profile 3 at 28 chunks passed the full 2,048×416 harness with
15.99 ms average first-seam frame time, 11.84 ms rim average, zero camera
delta/correction packets at both wraps, and all gameplay/rim/void probes
green. A 2,048×256 no-cache run separately passed topology, gameplay, AI,
rim, and exterior checks; its LOD capture was correctly recorded as skipped
because pregeneration and a complete cache were intentionally unavailable.

A synthetic complete 15,552×4,096 atlas covered the former-wide production
projection envelope without requiring a 248,832-chunk pregeneration for each
shader iteration. Visual profile 4 passed distinct horizontal tangent and
straight-up captures with all four diagnostic circumference sectors
continuous. The runtime measured a 1,024-block test far plane against roughly
4,893 blocks to the opposite reference surface and 5,305 blocks to the far
width edge. Authoritative real trees and terrain covered the earlier sky-pass
proxy. This remains a useful wide-band regression, but the current
production-default visual/resource gate is 16,384×256.

The reusable dedicated 2,048×416 server and two real clients then passed the
full seam visibility, combat, block edit, vehicle, long teleport, return, and
reconnect matrix. The repeatable fixture now waits for both clients to acquire
its boat, clears stale test boats, tolerates an overloaded server sampling a
small fold step before the last canonical block, and compares explicit return
positions periodically rather than requiring one presentation chart.

The large-layout topology/rim matrix is also complete with atlas pregeneration
disabled so it measured normal local chunks rather than waiting for unrelated
full-ring generation:

| Runtime case | Result |
| --- | --- |
| 15,552×4,096 former-wide production fixture | Both natural seam approaches, all gameplay probes, rim, shortened top, and exterior void passed; first-seam average 18.53 ms, rim average 21.50 ms. The far-side setup was an explicit test teleport, while the seam itself remained a natural 0.25-block step. |
| 32,768×512 long/narrow | Same topology/gameplay/rim matrix passed; first-seam average 17.04 ms, rim average 17.60 ms. |
| 4,096×2,048 wide/medium | A literal real-chunk circuit and both natural wraps passed; latest full-run averages were 16.74 ms at the first seam and 17.08 ms at the rim. |

The wide run exposed an intermittent vanilla simulation-level graph state:
an arrow, moving item, navigator, and boat could all stop immediately after
folding into chunk zero despite a nearby player. `ServerWorldMixin` now checks
the canonical graph and falls back to the configured nearest-periodic player
simulation distance. Two consecutive fresh wide worlds passed the formerly
intermittent 240-tick entity fixture; the first also completed the literal
circuit and rim phase.

`runLayoutSwitchClient` then opened a 4,096×2,048 save and a 32,768×512 save in
one JVM. Disconnect cleared the first client geometry/atlas, and the second
handshake installed a different fingerprint with the expected 4,096×64 atlas
instead of retaining the first world's 512×256 atlas.

Remaining Phase 7 work is visual/resource evidence: actual-terrain
complete-atlas live/LOD captures on the production, long/narrow, and wide
layouts, plus measured
production atlas pregeneration, compressed size, transfer, and build cost
before any deployment decision.

For each playable case, verify:

- seam movement, camera, chunks, entities, blocks, and vehicles;
- both rims and exterior void;
- spawn and reconnect;
- worldgen seam continuity and representative structures;
- physical render alignment from ground to build top;
- clouds above the actual wall;
- ring-centred sun across the width;
- live/LOD handoff at both apparent directions and the seam;
- atlas cache identity when seed/layout changes;
- no stale geometry when switching worlds;
- frame pacing and allocation budgets.

The public server moved from 1,600-by-320 to a fresh 2,048-by-416 world on
27 July 2026 after an explicit decision and timestamped backup. The old save
was not resized. Any future public geometry change requires the same
backup-and-new-world procedure because dimensions remain immutable.

## Definition of done

Dimension scaling is complete only when:

- no active renderer or shader contains the current test world's C, W, wall
  top, or cloud Y;
- the full synchronized layout is available before server generation and
  client rendering;
- every physical radius remains positive with the chosen vertical clearance;
- walls, clouds, sun, chunks, entities, culling, and LOD use the same cylinder;
- view-distance transitions remain continuous for every supported C/W ratio;
- atlas and noise allocations are checked and bounded before use;
- world creation presents validation and cost, and saved settings always win;
- the parameterized unit, local visual, and two-client matrices pass;
- README, agent guidance, protocol, rendering, operations, testing, deployment,
  and current-state documentation describe the same behavior.
