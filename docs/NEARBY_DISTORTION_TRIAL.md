# Nearby block distortion trial

This is an unmerged visual experiment on `codex/nearby-distortion-trial`, not
part of the 1.3 release. It starts disabled and resets on disconnect.

- `/ringworld distortion on`, `off`, or `show`
- `/ringworld distortion distance <1–8>`: chunks each way, default 3
- In-world RingWorld menu → Display: trial toggle and correction distance

The correction spans the full ring width. It is limited to the chosen distance
above and below the camera as well as along the ring, fading vertically over
another equal distance. Lower terrain outside this height band keeps its
original angular spacing; the small reference-radius safeguard remains global.
Within the chosen longitudinal
radius, arc spacing at camera altitude becomes one rendered block per intrinsic
block. A smooth transition over another equal radius redistributes the remaining
angular spacing around the circumference. All angular slopes stay positive;
the opposite point closes exactly at half a revolution. Cube dimensions remain
approximate because the ring still curves, especially at very short radii.

New worlds require at least 2048 blocks around the ring and 128 blocks across.
At Y319, the original local circumference scale is only about 0.22, so nearby
blocks become narrow wedges. The geometry layer also accepts legacy 1024-block
rings, whose physical centre (Y≈227) falls below maximum build height.

The trial increases the visual reference radius when needed so the highest
visible plane retains a positive radius of twice the correction distance. On
the minimum new ring, that adds roughly 8% to the reference radius at the test
viewpoint. Changing altitude can change this safeguard above build height;
changing correction distance can change it at all heights. These are explicit
experiment tradeoffs, not world changes.

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
steps. Ordinary 12-chunk render distance is retained.

The fixture captures `screenshots/distortion-off.png` and `distortion-on.png`
from the same pose, checks the live client command, then leaves the game open
with correction enabled. Existing owner worlds are not modified.

Validation is recorded below as it completes. This trial is not release
qualification: no multiplayer, external renderer, performance or full release
suite claims are implied.

## Verification (5 October 2026)

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

## Vertical-band correction

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
