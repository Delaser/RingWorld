# Create linear-contraption D4 qualification (2026-08-29)

Immutable implementation and fixture source commit
`fe52551eefd3c9788b6e8aaf5e2a508e26d55c10` qualifies the exact runtime tuple
Minecraft 1.21.1, NeoForge 21.1.239, Create 6.0.10, and Create's nested
Flywheel 1.0.6. This is bounded qualification evidence, not release or
published-support metadata. Fabric Create remains unqualified.

## Result and corrected defects

The real Mechanical Piston, Gantry Carriage, and Rope Pulley routes pass the
bounded functional and graphical matrix after two narrow physical-client
corrections. Neither correction changes server ownership, persistence,
tracking, protocol, or shared topology.

### Gantry chart-local smoothing

Before correction, the same tracked gantry could jump from presentation X
about 2020.25 to 1280.5 when its server coordinate folded from canonical X
2047.625 to 0. Numeric id, UUID, Java object, server entity, and authoritative
motion remained continuous. This was not tracking removal, a corrective
teleport, or canonical ownership failure.

Exact Create bytecode explains the value. `GantryContraptionEntity.handlePacket`
stores `packet.coord() - getAxisCoord()` directly in `clientOffsetDiff`. The
packet coordinate is canonical while the physical client may retain the
entity in a high or low presentation chart. `tickContraption()` then applies
Create's native `0.75` decay to that lap-sized accumulator before
`updateClientMotion`.

The exact-tuple client mixin projects only a finite X-axis accumulator to its
nearest periodic image at the sole tick consumption boundary, before native
decay:

`nearestImageX(currentAxisCoord + clientOffsetDiff, currentAxisCoord) - currentAxisCoord`.

Create still owns packet handling, decay, direction modification, entity
position, and motion. Missing geometry, a non-current or non-Overworld client
level, a non-X movement axis, and non-finite input preserve the original
field.

### Backend-OFF moved block rendering

Independent review of the first otherwise-passing D4 evidence found that the
backend-OFF piston and gantry kept their cached block structure continuous but
visibly detached moved chest and shulker block-entity renderers. Indirect and
instancing were correct. The earlier aggregate palette oracle did not detect
the per-payload displacement, so those original OFF placement claims were
invalidated and replaced by the corrected evidence below.

The exact render-stack audit found one curvature mismatch. RingWorld's entity
renderer had already supplied Create's OFF renderer with the curved parent
pose. Create rendered moved block entities through that parent exactly once,
but its cached structure buffer selected Minecraft chunk-layer `RenderType`s.
Those layers entered `ringworld_terrain.vsh` and received a second nonlinear
curvature pass. The block-entity renderers remained the single-curved spatial
reference, which made them appear detached from the twice-curved structure.

The correction redirects the sole OFF structure-buffer
`MultiBufferSource.getBuffer(RenderType)` sink to exact entity-space block
atlas layers only when the entity belongs to the current attached client-side
Overworld level and live ring geometry exists. A stale or foreign-level entity
therefore cannot borrow the replacement session's geometry during teardown or
level replacement. It does not change Create's cache, layer loop, BER path,
parent pose, matrices, shaders, or Flywheel ON path. Standalone kinetic child
embeddings and `ContraptionVisual` are untouched.

Detailed layer strings, shader inspection, and matrix samples are guarded by
one JVM-startup fixture boolean. With neither bearing nor linear fixture
property enabled, the ordinary OFF render loop performs no diagnostic string
or matrix argument evaluation; the production layer mapping remains active.

## Exact ABI and activation boundary

The strict plugin preflights the gantry target, fields
`movementAxis:Lnet/minecraft/core/Direction;` and `clientOffsetDiff:D`,
`handlePacket(GantryContraptionUpdatePacket)V`, and `tickContraption()V`. It
verifies the sole handler `coord()D -> getAxisCoord()D -> DSUB -> PUTFIELD`
shape and the sole tick `GETFIELD -> 0.75D -> DMUL -> PUTFIELD` decay shape.
The injection is `require=1`, `expect=1`, and `allow=1`.

The plugin also preflights exact `ContraptionEntityRenderer.render` and
dependency descriptors, the ordered OFF branch from `supportsVisualization`
through `chunkBufferLayers`, the sole `getBuffer(RenderType)` sink, and the
following `renderInto` call. The renderer redirect is likewise
`require=1`, `expect=1`, and `allow=1`. It preflights the two retained Create
entity-layer factories. No `@Pseudo`, dead target, global replacement, or
unsupported tuple fallback can partially apply the adapter.

The qualified physical client has four server and eight client mixins. The
exact dedicated server remains four server and zero client mixins. With
Create absent the counts are zero and zero. Unsupported tuples warn once and
disable the complete adapter.

Focused gantry tests cover positive and negative seam deltas, already-local
and idempotent deltas, multi-lap chart references, non-X and no-geometry
guards, and non-finite fail-open behavior.

## OFF layer-state audit

Five source chunk layers are selected by Create's cached structure loop. The
redirect never returns one of those terrain layers, and fixture verification
rejects any mapped shader that exposes `RingWorldLayout`.

| Source layer | Corrected entity-space layer | Preserved material state |
| --- | --- | --- |
| solid | Create `entitySolidBlockMipped` | mipped block atlas, opaque, cull, LEQUAL, color/depth write, main target, lightmap, unsorted |
| cutout_mipped | Create `entityCutoutBlockMipped` | mipped block atlas, opaque, cull, LEQUAL, color/depth write, main target, lightmap, unsorted |
| cutout | RingWorld exact-tuple entity cutout | non-mipped block sheet, opaque, cull, LEQUAL, color/depth write, main target, lightmap/overlay, unsorted |
| translucent | RingWorld exact-tuple entity translucent-cull | mipped block sheet, translucent, cull, LEQUAL, color/depth write, translucent target, lightmap/overlay, sorted |
| tripwire | RingWorld exact-tuple entity tripwire | mipped block sheet, translucent, cull, LEQUAL, color/depth write, weather target, lightmap/overlay, sorted |

All mapped layers use `NEW_ENTITY` and the appropriate entity shader. The
three custom singleton layers preserve source preferred buffer sizes:
786432 for cutout and translucent, and 1536 for tripwire. The retained Create
solid and cutout-mipped factories prefer 256-byte initial buffers; this is a
non-semantic allocation preference, not an assertion that all source buffer
sizes are identical. No harmful repeated growth was observed in the bounded
matrix, so custom replacements were not added speculatively.

A legitimate moved tripwire payload was assembled, selected the weather-target
entity layer, and restored correctly. This proves the exact state mapping and
real selection path, but the matrix has no isolated tripwire-only fine-detail
pixel oracle; independent tripwire graphical qualification remains a stated
limit.

## Real mechanism matrix

All mechanisms use Create's real blocks, block entities, kinetic power, glue
graph, contraption entities, motion, and native restoration. No result state
is manufactured.

- Mechanical Piston: a 91-block asymmetric glued contraption with translucent
  layers, tripwire, moved chest and shulker payloads, and an adjacent unglued
  negative. It covers high positive-seam and fresh low negative-seam
  extension/retraction, reversal, exact restoration, and active high-chart
  save/reopen. High indirect, explicit instancing, and OFF runs are matched.
- Gantry Carriage: a 27-block asymmetric glue-only assembly with three moved
  block-entity payload types and an adjacent unglued negative. High and fresh
  low indirect, high instancing, and corrected high OFF preserve identity
  through crossing and reversal, then restore exact states, NBT, and glue.
  Native return stops at Create's entity-anchor half-block restoration cell;
  the fixture waits for ordinary reverse motion before invoking Create's
  native limit and does not directly move or disassemble the entity.
- Rope Pulley: a bounded 16-block normal-X vertical control with two moved
  block-entity payload types. It proves movement, reversal, whole-assembly
  pixels, payload NBT preservation, and restoration without exercising the X
  seam correction.

Every server-owned block and controller remains canonical. Client presentation
positions are transient, claimed crossings bind client and server positions
separately, and no duplicate block or entity ownership is created.

## Graphical and payload evidence

Nine isolated runs retain 28 screenshots. Every expected-visible capture
requires curved projected bounds within the 1280x720 viewport and binds the
backend, route, phase, entity id/UUID/Java object, visual identity, canonical
server position, presentation client position, render membership, and finite
Flywheel matrices where applicable.

The whole-assembly oracle clips to the projected ROI, classifies mutually
exclusive gold, magenta, lime, and purple palettes, requires at least three
groups, and requires a bounded footprint. Piston and gantry now additionally
apply strict payload-local checks in every relevant frame: chest and shulker
projected ROIs, pixel count and footprint, centroid within 15 pixels, adjacency
to the distance-2 lime and distance-4 gold branch blocks, and matched
OFF-versus-indirect residual within 12 pixels. This is the check that detects
the original detached-payload defect.

The pulley's blue shulker faces are occluded at the sampled live angles, so
its manifest records the optional face checks as unavailable. Its independent
evidence is the whole-assembly pixel oracle plus moved-container NBT and exact
restoration; it is not used to broaden the seam payload claim.

The machine-readable manifest is retained under the ignored runtime directory
`neoforge/run-create-compat-linear-piston-indirect-high/linear-matrix-manifest.json`.
It binds source commit `fe52551eefd3c9788b6e8aaf5e2a508e26d55c10`, all
nine logs, screenshots, projected and payload ROIs, palette and adjacency
results, contact sheets, absolute/relative paths, and hashes. Its SHA-256 is
`e64ae400c2d1e06902f741db188b3ad675967c975cf5cc7d3b4a645b81b1c247`.

Final contact-sheet SHA-256 values are:

- piston indirect high: `8abad2d2574168e635b4d097fa59ae06ff546aebc67811043769d68c59b0ae72`;
- piston indirect low: `90fa6432f59620114a6dd2e4e9699aee54707c2fc05b7a7d8e65849dc26830af`;
- piston instancing high: `4461b87ec69cf619161dadd722461f0285f548d83598e43b0fc536d41a01db4d`;
- piston OFF high: `0bcc72002769d30bb561b189bd78a660822bca390373301e51cc6e64e242f280`;
- gantry indirect high: `ea0c78cb0fcbac03ed42600693ae51d16a6ab76add35aab5f10d42591de4e512`;
- gantry indirect low: `8896c8c4ed5ac7f64a586caa1723f1dbdab2365e9f3f4166ed35b59ee4f834cf`;
- gantry instancing high: `0cc699a2357e21c8dafa33f46934dfa67459e090dfa41d6f00d44af9016569d5`;
- gantry OFF high: `2bc05df90592b5d13a35dea79b8f05eed8a30d02a615b3d2a3b13086491476fe`;
- pulley indirect normal: `3035551d434b4252e70c6abe655a89bc1d099b4b64a1942954d722c075a355f8`.

The earlier pre-fix OFF screenshots are rejected diagnostic evidence and are
not members of the final manifest. The corrected piston and gantry OFF images
show the chest and shulker payloads joined to their expected branches. The C.1
bearing OFF evidence was also regenerated and rechecked with the stronger
payload-aware verifier:

- bearing indirect manifest SHA-256:
  `d03a8c8ff292b287faf9e4dc11e862c35d1db91a4b13f733aee1b31f95dc06c1`;
- bearing OFF manifest SHA-256:
  `19d367e2cc9d4045e9f08b632673fc5a2a2db9ee2a22f503d14ba76543186666`.

Each bearing backend passes at least three unobscured sweep-angle payload
checks and one lifecycle-frame check; individual frames where the moving
payload is legitimately occluded do not manufacture a pixel result.

## Regression and source gates

All source gates used Microsoft OpenJDK 21.0.12.1. Forced test execution
passes 368 tests in 62 suites for Fabric and 391 tests in 68 suites for
NeoForge, with zero failures, errors, or skips. `test build :neoforge:build`
passes. Mandatory inventory and isolation retain 371 components, 752
artifacts, 752 SHA-256 pins, the exact Create outer-jar hash, and all four
reviewed Flywheel shader hashes.

The exact dedicated server passes at four server/zero client mixins. The exact
physical client reaches its terminal PASS at four/eight and verifies real belt
clicks, preview, transfer, durable state, and finite curved transforms. After
that terminal result the integrated-server launcher spent over ten minutes in
native `ChunkMap.processUnloads`; it was stopped after evidence capture. This
is reported as a host teardown anomaly, not a clean launcher-task exit, and
the terminal-result verifier passes independently.

The Create-absent dedicated server passes zero/zero. The ordinary no-Create
creation UI client retains all 13 captures with no Create/Flywheel jar, zero
adapter applications, and no linkage error. The C.1 indirect/OFF bearing
controls, D2 frozen cog, D3 seven-run kinetic network and density lifecycle,
and all nine D4 linear runs pass. D2 retains 4 runs/48 captures with the flat
ROI eliminated and curved ROI matching OFF; D3 retains 7 runs/35 captures and
ends its density replay at 616 created/616 deleted, zero failed deletes, and
zero owners.

Final supporting manifest hashes include:

- D2: `01a0a7b3a5f9c1d19951419174a746729be9ad259e95073532f5f15b51c4d362`;
- D3: `cc66c9fd4895f4f1adbaf4531b7cf644c18fd4bcede419066cfc2a0c6ffd7a1b`.

`git diff --check`, strict dependency isolation, inventory, shader hashes, and
the fail-closed mixin target/count gates all pass.

## Limits and forward guidance

This qualifies the bounded piston, gantry, and vertical pulley routes and the
corrected OFF structure/material path only for the exact tuple. It does not
qualify trains, elevators, mirrored pulley layouts, Fabric Create, or an
independent tripwire fine-detail pixel outcome, and it does not change
published support metadata.

For future 26.1/26.2 compatibility, carry two semantic findings, not these
1.21.1 descriptors:

1. A client smoothing accumulator that subtracts a canonical packet coordinate
   from a presentation-coordinate entity must choose the nearest periodic
   image at its local consumption boundary.
2. A fallback renderer whose parent entity pose already contains RingWorld
   curvature must not send its local cached structure through a chunk terrain
   shader that applies curvature again. Preserve each source material's atlas,
   transparency, target, depth/write, cull, lightmap, overlay, and sorting
   state when selecting an entity-space equivalent.

Re-audit the exact packet, render sink, and layer-state ABI for each future
Create/Flywheel/Minecraft tuple before adapting it.
