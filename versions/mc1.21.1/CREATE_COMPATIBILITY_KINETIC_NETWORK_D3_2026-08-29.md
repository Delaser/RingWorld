# Create standalone kinetic-network D3 qualification (2026-08-29)

Immutable fixture/verifier source commit
`08a223b57cb106d1298250c1a10df2f7b4a4bf9c` validates the standalone kinetic block-entity correction from
`66c8c81c8be3fe54ea16d4d2db0315bb7b931080`. The exact qualified runtime is
Minecraft 1.21.1, NeoForge 21.1.239, Create 6.0.10, and Create's nested
Flywheel 1.0.6. This is bounded compatibility evidence, not release or
published-support metadata. Fabric Create remains unqualified.

## Result

The broad standalone network did not reproduce another production defect.
All expected Create kinetic visuals draw at the curved physical-ring
projection under Flywheel indirect and explicit instancing in normal, high,
and low presentation charts. Matched backend-OFF runs render through the
unchanged vanilla block-entity fallback. Center, viewport-edge, near, and far
views show no missing, flat, or prematurely culled component.

The result depends on the D2 correction. Before D2, a real high-chart cog was
live but visibly drawn at its flat projection. D3 is regression and breadth
qualification of that fix; it is not evidence that the unmodified phase-3B
adapter was already correct.

No shared topology, tracking, persistence, protocol, terrain/proxy frustum,
shader, Atlas, public API, or support-metadata change was made. The exact
adapter remains four server and six physical-client mixins. The dead
`EntityVisibilityTester` CPU surface remains absent.

## Real network and runtime observations

Each disposable world creates real registered Create blocks and lets Create
construct their real block entities, kinetic networks, visuals, and rotating
instances. The twelve observed targets are:

- shaft, small cog, large cog, creative motor;
- encased fan, mechanical press, mechanical mixer, mechanical pump;
- stationary Mechanical Bearing and Gantry Carriage controls;
- powered Mechanical Piston and Rope Pulley controllers.

Every non-stationary target reports nonzero real Create speed. The bearing and
gantry are deliberate zero-speed stationary-controller controls. The verifier
binds every target to its exact block-entity class, canonical server BlockPos,
nearest-image client BlockPos, exact Flywheel visual class, child
`EmbeddedEnvironment`, positive matrix index, finite curved transform, and
live instance position. Normal coordinates have lap delta 0; high and low
presentation coordinates differ from canonical ownership by exactly +2048
and -2048 respectively. No presentation position is persisted.

The retained run matrix is:

| Backend | Chart | Density | Median / p95 / p99 ms | Frames over 50 ms |
|---|---:|---:|---:|---:|
| indirect | normal | 128 | 8.482 / 13.811 / 25.200 | 5 |
| indirect | high | 128 | 8.476 / 14.200 / 26.889 | 4 |
| indirect | low | 0 | 8.456 / 14.039 / 24.335 | 2 |
| instancing | high | 0 | 8.493 / 13.728 / 26.128 | 4 |
| instancing | low | 0 | 8.489 / 13.335 / 23.528 | 3 |
| OFF | high | 0 | 8.481 / 13.491 / 26.133 | 0 |
| OFF | low | 0 | 8.378 / 13.326 / 20.854 | 0 |

The density control consists of 64 independently powered motor/shaft pairs,
128 standalone kinetic visuals, in addition to the 26 target/support/power
visuals. Both normal and high indirect runs therefore require exactly 154
simultaneously owned child embeddings. High-chart p95 and p99 remain within
the verifier's bounded 1.5x-plus-2-ms limit relative to normal; there is no
material curved-chart regression in this sample. These figures are one-machine
diagnostics, not hardware requirements or a general performance guarantee.

## Pixel and projection oracle

Thirty-five screenshots are retained: five per run. Three fixed-camera center
frames use deliberately non-symmetric separated phases; the other frames cover
the viewport edge and a medium/far curvature case. Every proof logs projected
curved and flat bounds for all twelve targets and requires the curved bounds to
intersect the 1280x720 viewport.

The verifier clips each animated target to its projected curved ROI and counts
RGB L1 changes greater than 30 across adjacent phase pairs. Non-symmetric ON
models require at least two changed pixels; the visually rotationally symmetric
shaft/pulley models require at least one plus nonzero audited speed. OFF keeps
per-component counts but uses an aggregate guard because several exact vanilla
models are rotationally symmetric: at least five component ROIs and twenty
pixels must move. A separate colored static reference ROI selects an adjacent
pair with no more than eight-percent change, preventing camera or broad-world
movement from satisfying the motion oracle. Both phase pairs remain recorded.

This oracle was hardened after two rejected calibration attempts. A 38-block
framing made the mixer too small for meaningful motion evidence. A 20-tick
interval could also sample a symmetric 32-RPM shaft at an equivalent visual
phase. The accepted fixture uses a 24-block near view and 9/11-tick separated
intervals. Rejected captures and their shutdown experiments are not included
in the manifest.

Manual review of all seven generated contact sheets confirms the whole target
network is visible in the three center frames, remains visible at the expected
viewport edge, and is present at the far view. Indirect, instancing, and OFF
footprints are materially consistent for matched chart/camera poses.

## Ownership and lifecycle

Every ON run exercises native render-origin recreation, full block remove and
re-add, and explicit exact `ClientLevel.unload(LevelChunk)` callbacks for all
target chunks. Each owner table is empty before its child deletion is checked;
the final state is `owned=0`, `created=deleted`, and `failedDeletes=0`. The
server's canonical blocks are left unchanged by the client unload. The low
indirect run additionally performs a normal save/disconnect, proves RingWorld
client state cleared, reopens the same world in the same process, observes a
fresh complete 26-owner generation in the low chart, removes it, and again
finishes balanced at zero.

The first attempted movement-driven unload used a temporary server view-distance
change and caused disposable integrated-server shutdown stalls. Those runs were
cancelled and are not evidence. D3 instead invokes Minecraft's exact client
chunk-unload lifecycle (`LevelChunk.clearAllBlockEntities` through
`ClientLevel.unload`) and reserves durable server reload for one ordinary
26-visual run. No production view-distance or shutdown behavior changed.

## Evidence binding

The machine-readable manifest is ignored runtime evidence at
`neoforge/run-create-compat-kinetic-network-indirect-high/d3-matrix-manifest.json`.
It binds source commit `08a223b57cb106d1298250c1a10df2f7b4a4bf9c`, seven exact logs, 35 screenshots, projected
ROIs, per-component motion counts/centroids, static-reference selection,
backend identity, performance, absolute/relative paths, and SHA-256 hashes.
Its SHA-256 is
`12505495158264b0794658327801b6d8d745468bcfc7c16b30642f13f57a81f0`.

Contact-sheet SHA-256 values, in matrix order, are:

- indirect normal: `fc5c923d296995a9bcef0afad05b83431e49e08ac180979896b49e6fee677645`;
- indirect high: `aff2262eb7c65474ce5975162aa3d52526efa564c60b6693332a9bb6820ebe18`;
- indirect low: `9606c408c86cc2aced4536fab3875f02f35ad7ee94aaa93e39e40428af8bc002`;
- instancing high: `baad401d3f3a66b8e45449a1338ae2bdcc605497b8acd00ddc5033753eed49cd`;
- instancing low: `e7c834a484331381abfac53bb14777361604244466a6084e977922f1126552b3`;
- OFF high: `9780126be3977cd3efb5c7eacc47a9ff18d9cc25421c0d20def6fdfc49e1eee2`;
- OFF low: `05cb788c3db7dd1b30487ea0c2718b6661ff6d50caaeaadca6045ee4d543437a`.

## Final source and regression gates

All final source gates ran under Microsoft OpenJDK 21.0.12.1. The combined
`test build :neoforge:build` graph passes with 368 tests in 62 suites for the
Fabric graph and 386 tests in 67 suites for the NeoForge graph, with zero
failures, errors, or skips. The mandatory dependency gates retain exactly 371
components, 752 artifacts, and 752 SHA-256 pins. Create remains isolated from
ordinary runtime classpaths; the installed fixture artifact and four audited
Flywheel shaders retain their reviewed hashes.

The exact dedicated-server gates report four server and zero client mixins;
the Create-absent dedicated server reports zero and zero. The exact default
graphical client reports four server and six client mixins, finite embedding
matrices, both controller charts, canonical durable NBT, belt transfer, and
durable tank state. The existing D2 frozen-cog verifier and C.1 high-indirect
glued-bearing regression pass unchanged. The ordinary no-Create creation UI
client retains all 13 captures with an empty `mods/` directory, zero Create
compatibility applications, no loaded Create artifact, and no Create/Flywheel
linkage error.

## Limits and forward guidance

D3 qualifies standalone kinetic visual placement, animation, culling breadth,
owner lifecycle, and one durable reopen. It does not claim processing recipes,
fluid transfer, trains, elevators, or the queued glued Mechanical Piston,
Gantry, and Rope Pulley contraption matrix. Those moving linear contraptions
remain a separate Checkpoint B surface and must not be inferred from stationary
controller visuals here.

For a future 26.1/26.2 Create adapter, carry forward the architectural finding,
not these 1.21.1 mixin descriptors: standalone `KineticBlockEntity` visuals
need identity-owned child embeddings at visual construction, updated before
Flywheel render planning consumes them, with OFF left to the vanilla curved
block-entity path. Re-audit exact Create/Flywheel classes, shaders, construction,
render, recreation, and deletion ABI before implementing that version.
