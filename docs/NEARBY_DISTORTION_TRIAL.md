# Nearby block normalisation (#256)

The owner approved the trial on 5 October and again on 9 October 2026,
then requested integration through a PR. It was ported onto current main
without the trial branch's unified-JAR or floating-Atlas experiments.
This is upcoming functionality; published 1.3 files are unchanged.
It starts disabled and resets on disconnect.

- `/ringworld distortion on`, `off`, or `show`
- `/ringworld distortion distance <1–8>`: chunks each way, default 3
- In-world RingWorld menu → Display: Nearby block normalisation and correction distance

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

For unattended development checks, `-Dringworld.distortionTrialExit=true`
waits for the complete Atlas, exercises distances 1, 8 and 3, reloads resources,
captures lower/above-build views, checks disconnect reset, and saves/stops normally.
The runner supplies disposable save copies. Its copied-world file-fix helper
handles only the named backup/completion flow while this exit-mode opt-in is set. Checks described here are development
evidence, not frozen-candidate release qualification.

## Historical trial verification (5 October 2026)

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
invariance is covered by the regression test; owner motion review is pending.
The client remains open with the trial enabled. No release qualification or
other loader/version runtime visuals are claimed.

## Integration on current main (9 October 2026)

The port includes only commits `0ed709c`, `bbe503d`, `9251844` and `9398edd`
relative to the trial's `c9f23dd` baseline. Their net result is the accepted
fixed-height mapping. The unified-JAR baseline and later floating-object Atlas
experiments (#257) are excluded.

The current wall closure, top-anchored saved wall height, altitude-aware handoff,
depth ordering, horizon removal, outside building and dynamic cloud fixes are
preserved. Both 26.3 cloud shader entry points now use the same correction;
Globals and its Java buffer writer gain one matching vec4 in each resource ABI.
The disabled branch still uses the original transform. No atlas/save/network
format or server configuration changes are introduced.

### Source map

- `RingNearbyProjection`: fixed radius, smooth periodic angular mapping,
  local object transforms and conservative bounds.
- `RingDistortionTuning`: client/session toggle, range and disconnect reset.
- `ringworld_projection.glsl`: GPU equivalent for terrain, clouds and Atlas.
- Shared and 26.3 Globals adapters: per-frame toggle/core/fade/radius values.
- Version-owned LevelRenderer and entity adapters: outlines, overlays and anchors;
  `CurvedRingFrustum` and SkyRendering: matching culling and centre direction.
- Fabric/NeoForge command registration and RingWorld Display screen: live controls.
- `RingDistortionTrialClient`: opt-in disposable minimum-ring native checks.

### Integration checks

All six source builds/unit suites pass: 462 cases per loader on 26.1.2 and
26.2, and 465 on 26.3, with no failures or skips. The 355-test static contract
suite passes. Regression cases cover legal-height unit spacing, vertical
camera invariance, positive angular slopes, smooth joins, full ring closure
and culling containment through the canonical seam.

All six native checks pass on disposable copies of the completed 2048×128
study: 30 captures total (off/on, resource reload, lower camera and above-build
camera), live command execution at distances 1/8/3, complete Atlas streaming,
normal save/stop and disconnect reset. Source-runtime loaders are Fabric
0.19.3/0.19.5 and NeoForge 26.1.2.87 / 26.2.0.69 / 26.3.0.7-beta.
Representative captures from each source ABI and both loaders were inspected;
these static captures do not establish full motion or multiplayer parity.

The first 26.2 Fabric attempt was terminated at the copied-world file-fix
prompt (exit 143). Its log is retained as
`native-26.2-fabric-file-fix-blocked.log`. The helper was repaired to use the
existing narrow backup/completion flow, and the subsequent run passes.
A development-profile certificate HTTP 401 appears in the successful 26.2
Fabric log; no other ERROR/FATAL entries occur in the six successful runs.
This service authentication failure is recorded separately from rendering checks.
Local ignored evidence: `logs/nearby-normalisation-256/`. The owner's minimum-ring
26.3 Fabric motion/performance review was accepted before requesting this merge.
No numerical FPS benchmark is claimed.

### Remaining release checks and limitations

The local moving correction still changes surrounding geometry while travelling
along X; the four-times fade softens this. Exact cubes throughout the ring are
not promised. Default off and session-local controls are intentional.
Particles, third-party renderers and multiplayer presentation have not received
new native qualification for this option. Legacy 1024-block Atlas inversion
above its original centre remains unqualified. Run fresh frozen-candidate
release qualification before publishing; the already-published 1.3 evidence
does not qualify changed bytes.
