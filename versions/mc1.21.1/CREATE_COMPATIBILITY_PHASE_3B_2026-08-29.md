# Create 6.0.10 compatibility phase 3B checkpoint (2026-08-29)

This checkpoint completes the bounded client/gameplay implementation for the
exact tuple Minecraft 1.21.1, NeoForge 21.1.239, Create 6.0.10, and the
Flywheel 1.0.6 nested by that Create artifact. It is qualification evidence,
not a published compatibility or release claim. Fabric remains unqualified;
no Fabric runtime dependency or adapter is present.

## Adapter boundary

The optional mixin plugin still activates without initializing Create classes.
It recognizes only the exact tuple, preflights server targets on both physical
sides, and preflights client targets only on `Dist.CLIENT`. An absent Create
installation is silent. A present but different tuple warns once and disables
the complete adapter. The exact tuple retains strict target and injector
requirements.

The physical client applies exactly four client mixins:

1. `BeltConnectorHandlerMixin` maps only the stored canonical first pulley to
   the player's nearest presentation image while Create performs preview
   lookup and validation.
2. `BeltBlockEntityClientMixin` maps canonical controller NBT to the owning
   client block entity's presentation chart and canonicalizes only the NBT
   value passed to Create's write path.
3. `FluidTankBlockEntityClientMixin` applies the same transient-controller
   rule to tanks.
4. `ContraptionVisualMixin` replaces only Create's audited embedding
   translation in the Overworld. It composes curved render-origin-local
   translation, tangent rotation about local +Z, then leaves Create's local
   transforms untouched.

Client controller hooks require an attached owning client Overworld before
consulting `ClientRingState`. Integrated-server block entities are no-ops.
Reads that legitimately occur before client-level attachment are marked for
the first safe tick instead of guessing geometry. Presentation controller
fields are never written back as ownership coordinates.

The server mixin count remains four. Controller repair, lookup, cache, and
persistence rules from phase 3A are unchanged: only canonical positions cross
server ownership boundaries.

## Rejected Flywheel CPU culling target

The audited `EntityVisibilityTester` / `AbstractEntityVisual.isVisible`
candidate is not a live contraption-culling surface for Flywheel 1.0.6 and is
deliberately absent from the plugin preflight, mixin JSON, source, and applied
counts. `RingPresentationBounds` remains a reviewed shared primitive for a
future backend/version that exposes a live CPU bounds surface; this exact
adapter does not call it and does not cite it as runtime evidence.

The exact live GPU path consumes the corrected `ContraptionVisual` embedding
for drawing and culling:

- `IndirectInstancer.writeModel` stores `environment.matrixIndex()` in the
  model descriptor.
- `assets/flywheel/flywheel/internal/indirect/cull.glsl` transforms the model
  sphere by the per-instance transform and, for a positive matrix index, by
  `_flw_matrices[matrixIndex].pose` before frustum and occlusion tests.
- the indirect vertex path reads the same draw matrix index and `common.vert`
  applies `_flw_modelMatrix` to the instance-transformed vertex;
- `EmbeddedEnvironment.flush/composeMatrices` uploads the same parent-then-
  child composed pose;
- the instancing backend uses that embedding as `_flw_modelMatrixUniform` and
  has no corresponding indirect GPU cull stage.

The mandatory dependency-isolation gate now extracts the nested
`META-INF/jarjar/flywheel-neoforge-1.21.1-1.0.6.jar` from the independently
verified outer Create jar and requires these live shader identities:

| Nested path | SHA-256 |
| --- | --- |
| `assets/flywheel/flywheel/internal/indirect/cull.glsl` | `d4fe4fb66dd389f2e414cbc787fa58c1d723b1c6bf374a95f680336e7acc3d48` |
| `assets/flywheel/flywheel/internal/indirect/main.vert` | `ad1ceedaf430b078dea6e3555639979922717b0001e7a9e0481599bd3be4cd44` |
| `assets/flywheel/flywheel/internal/instancing/main.vert` | `7be34310b320b5f04943c2db4ca8efdf965afba87e141e33709cef4f579c3ae4` |
| `assets/flywheel/flywheel/internal/common.vert` | `4d9ab62a7da527c7f4727be9f128fd685e951317ac63f4308069d8036984d701` |

## Bounded qualification fixture

The default-backend fixture creates one disposable 2,048x416 ring and uses
real `MultiPlayerGameMode.useItemOn` predictions and packets. It waits for
Create's authoritative server cooldown rather than racing client and server
ticks. A canonical platform keeps the player within ordinary reach; no reach,
cooldown, packet, or formation rule is bypassed.

The fixture proves:

- first and second connector clicks in the high chart form the canonical
  `2045 -> 1` belt, and the reverse low-chart clicks form `1 -> 2045`;
- preview lookup and `canConnect` keep both endpoints in the active chart;
- a Creative Motor powers a three-diamond stack across the canonical seam,
  after which the motor is removed and the stack is durably stopped beyond
  position 3;
- a seam tank and a non-seam 2x2x2 baseline both expose capacity 64,000 and
  retain 3,000 mB; the vault negative control remains unmodified;
- raw server/reloaded controller NBT remains canonical, while reopened client
  fields are `2045/2047` in the high chart and `-3/-1` in the low chart;
- one mixed opaque/translucent OCE is mounted on a Minecart root in the high
  chart and crosses the positive seam under ordinary Minecart ticks. Its
  numeric IDs, UUIDs, client Java objects, client-level membership, client
  leave counters, and Flywheel visual identity/create/delete counters remain
  unchanged for the complete crossing and a 40-client-tick post-crossing
  window;
- after the deliberate high-to-low waypoint relocation has completed and the
  low chart is stable, the fixture discards the old route and creates an
  explicitly distinct Minecart + OCE pair. That fresh pair crosses the reverse
  seam under ordinary Minecart ticks and satisfies the same uninterrupted
  identity, membership, no-leave, and visual-continuity assertions. Only this
  second pair supplies the low-chart moving capture;
- both mounted contraptions carry mixed opaque/translucent blocks and
  nontrivial local yaw, and the live default backend supplies thousands of
  finite curved embedding matrices throughout the two routes;
- a normal save, complete disconnect/client-state clear, integrated-server
  restart, and world reopen retain belt item, tank fluid/capacity, and
  canonical controller ownership.

The backend-OFF task copies that exact durable world byte-for-byte into a
separate run, records per-file provenance, creates a disposable visual
contraption, selects `flywheel:off`, proves zero `ContraptionVisual` embedding
calls, and captures the rendered fallback. Code-path audit establishes that
OFF reaches RingWorld's existing vanilla entity renderer and that only one
RingWorld curvature hook is applicable there; the fixture does not count that
hook's runtime invocations. The synthetic render-only contraptions in both
modes are discarded before shutdown; they are not claimed as durable
contraption persistence.

## Tracking audit and fixture correction

An earlier diagnostic fixture moved an unmounted
`OrientedContraptionEntity` directly from a server task and then travelled the
player through distant waypoints to change charts. A capture taken after the
waypoint trip was invalid: the client no longer had the entity in
`ClientLevel`, and a later entity with the same numeric ID and UUID was a new
Java object. UUID continuity alone had concealed remove/add replacement.

The bounded packet/tracker lifecycle trace separated the events:

1. At the actual positive seam tick, the direct OCE, an identically driven
   vanilla ArmorStand control, and a real mounted Minecart + OCE route all kept
   the same server/client IDs, UUIDs, client Java objects, tracking membership,
   and (for Create) Flywheel visual. No tracker unpair, remove packet, client
   removal callback, or Create discard occurred at the fold.
2. The later disappearance occurred only when the player travelled through
   the high-to-low chart waypoints. All nearby diagnostic entities left range
   together, the server unpaired them, removal packets reached the client, and
   the client discarded them. A subsequent replacement reused numeric ID and
   UUID but not Java identity, so it is deliberately rejected as continuity.
3. The final fixture therefore retires every high-route identity at relocation
   and establishes a new low-route baseline only after the low chart and
   chunks are stable. Captures are accepted only inside each route's continuous
   identity window.

The direct OCE and ArmorStand remain diagnostic evidence, not production
fixture behavior. No shared topology, entity-tracking, packet, or corrective
teleport change is warranted by this finding, and the temporary broad trace
mixins were removed.

## Final local evidence

Java 21 full gates pass with 368 tests in 62 suites for the Fabric graph and
372 tests in 64 suites for the NeoForge graph, with zero failures, errors, or
skips. The mandatory dependency inventory remains 371 components / 752
artifacts / 752 SHA-256 pins. Ordinary builds retain no Create runtime input;
the custom compatibility runtime resolves exactly one outer Create jar with
SHA-256
`ef87fe5709f1ba1f5b8bb20a2925b5afb4669e178fd6d8bf10c167759eefe37a`.

The final default client selected `flywheel:indirect`, applied 4 server and 4
client mixins, recorded 3,731 curved embedding transforms and zero non-finite
matrices, and passed real clicks, previews, item transfer, both mounted routes,
durable reload, and both reopened controller charts. The copied-world OFF
client selected `flywheel:off`, applied the same 4/4 exact adapter set, recorded
zero `ContraptionVisual` transforms, and rendered through the vanilla fallback
shown by the retained captures. The one-applicable-curvature-hook conclusion
comes from the audited control flow, not a runtime invocation counter.
Dedicated boots pass at 0 server / 0 client mixins without Create and 4 server
/ 0 client mixins for the exact tuple.

The post-3B NeoForge no-Create graphical regression also passes
`:neoforge:runCreationUiClient` with all 13 menu-only captures. Its isolated
`neoforge/run-creation-ui/mods/` directory contains zero files, its mod list
contains only Minecraft, NeoForge, and RingWorld, and its log contains zero
Create-compatibility applications or Create/Flywheel linkage errors. The
retained `logs/latest.log` SHA-256 is
`1feac1f7812d4e226c3c00b88a3edc85068d352a6fa693cce0c162cff6f9bde9`.

Accepted screenshot hashes:

| Capture | SHA-256 |
| --- | --- |
| `ringworld-create-default-high-opaque-translucent.png` | `aa487f9afdb57f3eefee89949507b57963b9a743a470ecc085dbe8eff0a3e32e` |
| `ringworld-create-default-moving-high.png` | `592e17f422e97b4fe68beb2b2c49aa3029cb356cdae62bcd43e6f9b996a5a1e9` |
| `ringworld-create-default-moving-low.png` | `e7899539c941499699457d673495a1d6e42a9c9f1138df5d4db2f6f02a60c846` |
| `ringworld-create-default-reopened-high.png` | `a0e6fcb5cb289ac53be24d27cb0d18137f1d4ef55ccedc1cdc988435ba2d768c` |
| `ringworld-create-default-reopened-low.png` | `544a82c5dc207bbd61e59271453e4cd23517533207cf99e8ed5e79a68b35e297` |
| `ringworld-create-off-high-translucent.png` | `56208601a890c9dcc172f48f4624f840bf7e38df442f471b547cba8ab7324f93` |
| `ringworld-create-off-low-opaque.png` | `325064271311e4935f35387654f0a1ac7a59121170c00b8ea4bc6dd9a7e0c5eb` |

Each verifier writes `capture-manifest.json` at the root of its ignored run
directory. Every entry binds the retained screenshot's run-relative and
absolute path, SHA-256, selected backend, route, direction, chart, and
opaque/translucent/moving or durable-gameplay state. OCE-targeted captures are
accepted only after the fixture proves that exact UUID/numeric-ID Java object
is simultaneously present through `ClientLevel.getEntity(id)` and
`entitiesForRendering`; the proof log line is copied into the manifest.
Durable controller captures explicitly declare that they have no OCE target.

Fresh preparation deletes every earlier screenshot and manifest. The invalid
`ringworld-create-default-moving-low-reentry.png` replacement-identity capture
is forbidden by the verifier and is absent from both final manifests. Its
rejection remains documented above, but it is not retained or counted as
qualification evidence.

Run directories and evidence remain local and ignored:

- `neoforge/run-create-compat-client-default/`
- `neoforge/run-create-compat-client-off/`
- `neoforge/run-create-compat/`
- `neoforge/run-create-compat-absent/`

The final commit hash, unit totals, screenshot SHA-256 values, frame counters,
and exact terminal markers are recorded in the control-task handoff after the
clean source gate. No support metadata, release metadata, dependency scope, or
ordinary runtime classpath is changed by this checkpoint.
