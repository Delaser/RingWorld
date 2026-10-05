# Nearby block distortion trial

This is an unmerged visual experiment on `codex/nearby-distortion-trial`, not
part of the 1.3 release. It starts disabled and resets on disconnect.
Follow-up tracker: [GitHub issue #256](https://github.com/Delaser/RingWorld/issues/256).

Owner accepted the current appearance on **5 October 2026**, after the
fixed-height correction in `9398edd`. Acceptance banks the experiment for
follow-up; it does not authorize merging or publishing it. The implementation
remains on the trial branch while other experiments continue.

- `/ringworld distortion on`, `off`, or `show`
- `/ringworld distortion distance <1–8>`: chunks each way, default 3
- In-world RingWorld menu → Display: trial toggle and correction distance

The correction spans the full ring width. Within the chosen longitudinal
radius, arc spacing becomes one rendered block per intrinsic block at each
block's own height. A smooth transition ends at four times that radius (default:
3 chunks of correction, fading out by 12 chunks). The remaining circumference
absorbs the angular adjustment. All angular slopes stay positive; the opposite
point closes exactly at half a revolution. Cube dimensions remain approximate
because the ring still curves, especially at very short radii.

The mapping uses a fixed visual radius and each vertex's own height. Camera Y
only translates the scene: ascending or descending does not change its shape.
Movement along X still carries the local correction region with the player;
the wider fade reduces how abruptly the surrounding ring changes shape.

New worlds require at least 2048 blocks around the ring and 128 blocks across.
At Y319, the original local circumference scale is only about 0.22, so nearby
blocks become narrow wedges. The geometry layer also accepts legacy 1024-block
rings, whose physical centre (Y≈227) falls below maximum build height.

The trial increases the visual reference radius when needed so the world-top
plane retains a positive radius of twice the correction distance. On the
minimum new ring, that adds roughly 8% to the reference radius. Changing
altitude does not change this safeguard; changing correction distance can.
The unit-spacing guarantee covers legal block heights. Angular spacing is
bounded for objects above build height to avoid singularities.

Terrain, cloud geometry, Atlas surfaces, object placement, selection outlines,
and curved frustum bounds use the same trial mapping. Server physics, block
coordinates, generation and saves retain their existing interpretation. The
public physical-coordinate API continues describing the original embedding.
Sky lighting remains an existing approximation; particles and third-party
renderers have not been qualified against this trial.

## Disposable owner demo

`:runDistortionTrialClient` creates or opens **RingWorld Nearby Distortion Trial
2048** only in `logs/nearby-distortion-trial/run`. Seed 67890, circumference 2048,
width 128, creative mode, clear midday. A marked platform occupies Y319 and the
player starts at feet Y320, looking along the ring at the platform and nearby
steps. Fresh setup uses an ordinary 12-chunk render distance; resuming preserves the
owner's saved FOV and render distance.

The fixture captures `screenshots/distortion-off.png` and `distortion-on.png`
from the same pose, checks the live client command, then leaves the game open
with correction enabled. Existing owner worlds are not modified.

Validation is recorded below as it completes. This trial is not release
qualification: no multiplayer, external renderer, performance or full release
suite claims are implied.

## Initial verification (historical, 5 October 2026)

| Minecraft line | Fabric compile + unit suite | NeoForge compile + unit suite |
| --- | --- | --- |
| 26.1.x (26.1.2 development runtime) | PASS, 451 cases | PASS, 451 cases |
| 26.2 | PASS, 451 cases | PASS, 451 cases |
| 26.3 | PASS, 454 cases | PASS, 454 cases |

These checks reuse the existing official patched Minecraft development jars;
NeoForge artifact regeneration was skipped after an unsuccessful rebuild and a
network wait. This is compile/unit evidence, not a fresh release qualification.
The new geometry tests cover legacy 1024-block and larger rings, build-height
and bottom-height spacing, positive angular slopes, smooth joins, periodic
closure and conservative culling across the canonical seam.

The owner demo runs on 26.3 Fabric. Runtime visual acceptance remains the
owner's decision. Other loader/version runtime visual checks are not claimed.

26.3 Fabric runtime PASS: cold resource/shader compilation, disposable world
creation, platform setup at Y319, feet Y320, native live-command execution,
and matched on/off screenshots. The game was left open with correction on.
The screenshot files are in the isolated run's `screenshots/` directory.
Minecraft's unauthenticated development profile logged account/Realms service
errors; no trial shader, world-generation or render exceptions occurred during
the successful run. Earlier setup failures (first-run onboarding and use of the
legacy geometry minimum for new creation) were corrected before this run.

## Earlier vertical-band correction (superseded)

The first owner capture showed ground directly below a build-height observer
being stretched. The original trial applied camera-altitude spacing to every
height, widening reference-surface blocks by about 3.7× on the minimum ring.
The follow-up limits that angular correction to the camera's height band.
Regression coverage checks unchanged ground-level angular spacing, smooth
vertical joins and bounds containment throughout the vertical fade. This does
not remove the already documented global reference-radius safeguard.

The 26.3 Fabric/NeoForge follow-up compile and unit suites pass 455 cases each.
The Fabric client was relaunched with the new CPU and shader mapping and
resumed the saved test world/viewpoint, with correction enabled. Cold shader
compilation and startup succeeded. Owner appearance acceptance remains pending.
The first resumed capture ran before Atlas streaming finished; resumed captures
now wait for a complete Atlas and a ready surface renderer.

## Atlas holes on steep terrain

The owner captured triangular holes after enabling the correction. The trial
had incorrectly recovered canonical vertex phase from texture U. Steep Atlas
faces deliberately use a constant upper-sample UV, independent of their shared
physical lattice positions. Reprojecting that UV collapsed steep triangles and
split them away from their neighbours. Both shader adapters now derive angle
and radius from the actual physical Position; UVs only choose texture samples.
The disabled path retains its original physical position and handoff behaviour.

This inverse assumes a positive original physical radius, which holds through
Y319 for all new-world layouts (minimum circumference 2048). Atlas heights
beyond the original centre on legacy 1024-block rings remain unqualified;
explicit canonical vertex metadata would be needed to disambiguate those.

Verification: both 26.3 compile/unit suites still pass 455 cases each. The
Fabric client cold-launches the corrected shader and resumes the saved ground
viewpoint (503.589,64,-2.980, yaw89.650, pitch−44.550). The complete detailed
Atlas capture no longer shows the triangular sky gaps visible in the owner's
before image. Captures are retained in `logs/nearby-distortion-trial/review/`
as `atlas-holes-before.png` and `atlas-holes-after.png`. These are saved-pose
captures, not identical graphics-setting comparisons: the owner's before shot
used 32 chunks; the resumed fixture used 12. The corrected game remains open
with the trial enabled. Other loader/version runtime visuals remain pending.

## Fixed-height correction and wider X fade

The owner reported continued distortion while travelling along X and wanted
vertical movement to stop warping the scene. The former camera-centred height
band and altitude-dependent radius changed world geometry during flight.
The latest mapping uses each vertex's fixed world height and a radius derived
only from world build height and the selected correction distance. Camera Y
now contributes only the camera-relative vertical translation. The longitudinal
fade ends at four times the core distance, rather than twice, spreading the
change over a wider interval. This does not eliminate the moving correction
region's shape changes during X travel.

Regression coverage compares world points at bottom, surface and maximum block
height from cameras below ground, at the surface, above build height and above
the visual centre. Horizontal positions and conservative bounds remain fixed;
vertical positions and bounds change only by the negative camera displacement.
It also checks unit near-field arc spacing at each legal height, positive
slopes, periodic closure, smooth joins and culling containment.

Latest compile/unit checks pass on all six source cells: 452 cases per loader
on 26.1.2 and 26.2, and 455 per loader on 26.3, with zero failures or skips.
Logs are `logs/nearby-distortion-trial/altitude-stable-<version>-build.log`.
These reuse the existing patched development jars as described above.

The 26.3 Fabric client cold-launches the updated shader, loads the complete
Atlas, and resumes the owner's latest saved viewpoint (497.720,143.065,7.797,
yaw88.299, pitch90), preserving the selected 32-chunk render distance. The
capture is `logs/nearby-distortion-trial/run/screenshots/distortion-fixed-height.png`.
Terrain and walls appear continuous in this static capture. Vertical-motion
invariance is covered by the regression test; the owner subsequently accepted
the current appearance on 5 October 2026.
The client remains open with the trial enabled. No release qualification or
other loader/version runtime visuals are claimed.

## Projection contract

The current implementation is `RingNearbyProjection`, mirrored by
`ringworld_projection.glsl`. Its inputs are circumference `C`, the original
geometry radius `R0 = C / (2π)`, reference surface height `S`, exclusive world
top `H`, selected chunk distance `d`, and canonical camera/vertex coordinates.

```text
A = min(16d, C/8)                     core distance
B = 4A                               end of longitudinal fade
R = max(R0, H - S + 2A)               fixed visual reference radius
r(y) = R + S - y                      vertex radius
n(y) = 1 / max(r(y), 2A)              near angular slope
M = (A+B)/2
f(y) = (π - n(y)M) / (C/2 - M)       far angular slope
```

For shortest periodic displacement `dx` from camera X to vertex X and
`u = abs(dx)`, the slope is `n` up to `A`, blends to `f` with cubic smoothstep
`3t²-2t³` for `t=(u-A)/(B-A)`, and stays `f` beyond `B`. `angle` integrates this
slope analytically; the transition integral is `t³ - t⁴/2`. Signed phase and
full turns are restored on the CPU. The half-ring integral is exactly `π`, so
the mapping closes rather than leaving a circumference seam. With `A<=C/8`
and `n<=1/(2A)`, the far slope remains positive for all command distances.

Camera-local vertex placement is:

```text
θ = angle(dx, vertexY)
x' = r(vertexY) sin(θ)
y' = r(cameraY) - r(vertexY) cos(θ)
z' = vertexZ - cameraZ
```

Neither `θ` nor the vertex radius depends on camera Y. Moving the camera
vertically by `ΔY` therefore changes only `y'` by `-ΔY`. Do not reintroduce
camera-centred height weights or a radius that grows with camera altitude.
For legal heights, `r(y)>=2A` and the near tangent scale `r(y)n(y)` is one.
This preserves local arc length; it does not make all curved block faces exact
Euclidean cubes. Height-dependent phase can still shear geometry away from the
camera, and X movement still shifts the correction region.

Rigid objects use the projected anchor and tangent rotation. Outlines and
mining overlays also use the tangent scale, as in the existing renderer;
entities and block entities retain rigid model dimensions. Conservative
frustum bounds cover endpoint height/angle ranges and angular cardinal points,
so the renderer does not cull geometry using the old cylinder's bounds.

## Implementation map

Paths below are repository-relative. Shared files serve both loaders; version
adapters handle Minecraft ABI and shader-language differences.

| Responsibility | Source |
| --- | --- |
| CPU projection, object transforms, conservative bounds | `src/main/java/dev/ringworld/world/RingNearbyProjection.java` |
| GLSL equivalent | `src/client/resources/assets/minecraft/shaders/include/ringworld_projection.glsl` |
| Session toggle, distance, world-height input and centre direction | `src/client/java/dev/ringworld/client/RingDistortionTuning.java` |
| Disconnect resets | `src/client/java/dev/ringworld/client/RingWorldClientSession.java` |
| Display controls | `src/client/java/dev/ringworld/client/RingWorldMapScreen.java` |
| Loader command registration | `src/platform/fabricClient/java/dev/ringworld/client/RingWorldClient.java`, `src/platform/neoforgeClient/java/dev/ringworld/platform/neoforge/NeoForgeRingWorldClient.java` |
| Supplemental command validation | `src/main/java/dev/ringworld/world/RingLodCommandSuggestions.java` and its test |
| Global shader uniform upload | Shared and 26.3 `client/mixin/GlobalSettingsMixin.java` |
| Terrain/cloud projection | Shared and 26.3 `shaders/core/terrain.vsh`, `rendertype_clouds.vsh` |
| Atlas geometry and draw transform | Shared and 26.3 `assets/ringworld/shaders/core/ring_surface.vsh`, `client/render/RingSurfaceTextureRenderer.java` |
| Entities, block entities, outlines and mining overlays | Shared/26.3 `EntityRenderManagerMixin.java`; 26.1/26.2/26.3 `LevelRendererMixin.java` |
| Culling and sky-centre direction | `client/render/CurvedRingFrustum.java`; shared/26.3 `SkyRenderingMixin.java` |
| Regression tests | `src/test/java/dev/ringworld/world/RingNearbyProjectionTest.java` |
| Disposable client fixture and run registration | `src/client/java/dev/ringworld/client/RingDistortionTrialClient.java`, `build.gradle` |

Shader globals append a matching std140 `vec4 RingWorldDistortion` on CPU and
GPU: enabled flag, core `A`, fade end `B`, and reference radius `R`. Keep both
uniform adapters and both globals declarations in sync. 26.3 uses `#include`;
the older shader adapter uses `#moj_import`. Trial Atlas shaders already return
camera-local positions; their draw path must skip the normal cylinder's
translation/rotation to avoid applying that transform twice.

Atlas reconstruction uses `atan(Position.x, -Position.y)` and `length(Position.xy)`
to recover original phase and height. Never use texture UV for geometry:
steep faces intentionally share upper-sample UVs. The positive-original-radius
limitation for legacy 1024-block worlds remains as recorded above.

## Reproduce and resume

Use Java 25 and the following checked dependency pins:

| Minecraft development runtime | Fabric Loader | Fabric API | NeoForge | Latest cases per loader |
| --- | --- | --- | --- | --- |
| 26.1.2 (26.1.x adapter) | 0.19.3 | 0.155.2+26.1.2 | 26.1.2.87 | 452 |
| 26.2 | 0.19.3 | 0.158.0+26.2 | 26.2.0.69 | 452 |
| 26.3 | 0.19.5 | 0.160.5+26.3 | 26.3.0.7-beta | 455 |

Example for the tested 26.3 client:

```sh
./gradlew --offline --no-daemon --max-workers=2 \
  -Pminecraft_version=26.3 -Ploader_version=0.19.5 \
  -Pfabric_api_version=0.160.5+26.3 -Pneoforge_version=26.3.0.7-beta \
  -Pmod_version=1.3.0+mc26.3-distortion-trial -Prelease_label=distortion-trial \
  -PringDistortionTrialResume=true :runDistortionTrialClient
```

Omit `-PringDistortionTrialResume=true` for platform setup and the original
matched on/off capture sequence. Resume preserves the saved pose, FOV and render
distance, enables the correction, waits for a complete Atlas and rendered
sections, captures the current view, and leaves the client open for owner tests.
This property is fixture-only, not a player setting. Never point the fixture at
an owner production save.

Add `-PringDistortionTrialExtendPlatform=true` alongside the resume property to
extend this disposable world's Y319 platform around the full 2048-block
circumference. The nine-block-wide path uses smooth quartz, gold stripes every
16 blocks, and sea-lantern edges at Z=-4/+4. The original X432–592 section is
preserved, including owner edits. Extension runs on the integrated server in
one 16-block X slice per completion, verifies each new block placement, and
leaves the saved player pose intact. It is opt-in fixture plumbing only; it
does not change normal world generation or projection.


For compile/unit checks, replace the resume property and run task with
`:test :compileClientJava :neoforge:test :neoforge:compileJava`. The recorded
checks additionally excluded `:neoforge:createMinecraftArtifacts` to reuse
existing, matching patched development artifacts after the regeneration
failure; this requires those artifacts to exist and is not a fresh-environment
build recipe. Serialize heavy builds. Stop the test client before switching
version lines so resource outputs cannot change underneath it.

Logs and screenshots listed above are local ignored development evidence,
not checked-in assets. The regression source and Git commits are the durable
record; preserve or recapture visuals before cleaning that run directory.

## Follow-up and disposal

- Revisit remaining X-travel shear, including tall walls, overhead structures,
  close terrain below the camera and the Atlas/live-block handoff.
- Check all distances (1–8 chunks), small/medium/large layouts, and the
  circumference seam while walking, flying, and toggling the trial.
- Qualify runtime visuals and reloads on both loaders and all supported 26.x
  adapters, plus reconnect/session reset and performance/frame pacing.
- Review particles, sky lighting, above-build-height objects, legacy-ring Atlas
  ambiguity and the public physical-coordinate API before proposing release.
- Decide whether this becomes a persistent client option; currently it is
  session-local, default off, and resets on disconnect. No packet or world-save
  field was added.

To stop using it now, run `/ringworld distortion off` or disconnect. To discard
implementation later, return to the chosen baseline and omit the trial commits;
do not reset unrelated local work. The branch starts from unified-jar exploration
commit `c9f23dd`, not main. If accepted for release later, port only the trial
changes onto the then-current release baseline, review their dependencies, and
run the required full six-cell release qualification. Do not merge the entire
experimental branch automatically.

Implementation history: `0ed709c` initial trial; `bbe503d` superseded vertical
band; `9251844` Atlas UV/geometry separation; `9398edd` fixed-height mapping and
wider longitudinal fade. Retain the final behaviour and Atlas fix if carrying
this forward; the superseded intermediate height-band behaviour is not the
accepted design.

Platform-loop runtime check (5 October 2026): the opt-in 26.3 Fabric resume
extension completed and verified every new placement at Y319 across the full
2048-block circumference, keeping Z=-4..4 and the original X432–592 section.
The saved player pose was retained and the client stayed open. Log:
`/tmp/ring-distortion-client-loop.log`. This was a fixture compile/runtime
check, not a repeat of release or six-cell unit qualification.
