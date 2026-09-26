# RingWorld 1.4 — unreleased rendering checkpoint

Owner accepted the terrain/wall flicker fixes and subsequent land/water
transition appearance on 2026-09-26. Bank these
changes for 1.4; this checkpoint does not publish jars or change version numbers.

## Changes accepted for 1.4

- Fixed distant terrain polygons flashing over one another as the camera moves,
  including the red castle and white mountaintops. The 26.3 Atlas shader now
  reconstructs fragment depth and continuously orders distant geometry starting
  at the actual projection clamp. This removes the formerly flat depth band
  between roughly 672 and 1024 blocks in the tested view.
- Reduced distant wall-pattern shimmer with filtered mipmaps. Wall faces stay
  separate at every mip level, circumference sampling wraps cleanly, and decay
  holes do not darken the averaged wall colours. Active in the 26.3 renderer.
- Fixed a 26.3 resource-reload crash where sky rendering could use a missing
  cached sky colour immediately after renderer recreation.
- Replaced irregular live-terrain fade speckle with fixed ordered coverage.
  This reduces random-looking noise but is still a stippled transition; further
  work on the visible real-terrain/Atlas boundary is pending.

- Reduced the pale band over land by sharing one continuous distance-haze
  curve between real terrain and the Atlas, preserving environmental fog.
- Gradually fades underwater detail and water texture contrast before the
  transition to opaque Atlas water; corrects fog blending in translucent passes.
- Accepted a lighter, less saturated Atlas ocean colour. The visual trial uses
  a restricted colour/height match; replace that with an authored water mask
  before calling it general-purpose release-ready water handling.

## Verification

- Minecraft 26.3 Fabric: `:test :build` passed, 442 tests, no failures or skips.
- The fully generated 16,384 × 256 SeamTest ring loaded with Medium client LOD.
- Two consecutive in-world resource reloads completed, each followed by 100
  settled client ticks and a ready Atlas, without the former crash.
- Owner confirmed terrain depth stability, accepted the wall filtering, and
  then accepted the land/water transition and Atlas ocean-colour checkpoint.
- Follow-up builds retain 442 passing Java cases; 10 version-source contracts
  pass. Real-world reloads and land/water screenshots pass after correcting an
  initial atlas-lookup ID mistake. Land A/B used the same viewpoint with clouds
  disabled temporarily; the player's pose and cloud option were restored.
- Before/after ocean-colour captures and transition evidence are retained in
  ignored `logs/live-seam-capture`. No steady-frame performance claim is made.
- Numerical depth checks covered 1,834,952 float32 samples across seven far
  planes and both depth-coordinate ranges.
- Local evidence is retained in ignored `logs/depth-band-fix` and
  `logs/wall-filter-fix`. These paths are development artifacts, not public
  downloads. Wall uploads took roughly 52–74 ms on initial load/reload; this
  is not a steady-frame performance benchmark.

## Remaining before release

Review remaining geometry/material transition differences and qualify the
final 1.4 candidate on the supported loaders and versions. Authored water
identification is now implemented; see the follow-up below. The 26.3 depth,
reload, and wall-filter changes have not been claimed as tested on older
versions or on NeoForge. See [rendering design](RENDERING.md) for the current
implementation. No Distant Horizons code was copied.


## Wall material follow-up

The 26.3 Atlas wall palette now uses representative block-face texture colours
instead of map colours. This reduces the contrast jump between real wall blocks
and the distant wall. Palette weights, industrial motifs, decay, lighting and
mip filtering are unchanged. Colours are cached and rebuilt after resource
reload; older version adapters keep their established map-colour path.

The initial wall-only 26.3 Fabric build passed 445 cases. All ten block palettes
were also resolved in the live 26.3 client before and after two reloads. Matched
map/texture palette captures show closer tones without adding a rendering pass.
Owner acceptance of this new material change remains pending.


## Authored water and transition comparison follow-up

- Replaced the ocean colour/sea-level guess with water coverage captured from
  the actual surface fluid. Blue dry blocks are no longer selected by colour,
  and the tint can apply to rivers, custom water colours and other elevations.
- Kept light independent from water coverage in the existing byte; Atlas cells
  remain twelve bytes. Cache format is now 10 and metadata/tile payloads use v4.
  Existing blocks are preserved; old Atlas caches rebuild normally.
- Carries fractional coverage through client downsampling and bilinear samples,
  then applies the tint before texture mip filtering. Real-water fading uses
  the same colour target in 26.3. No extra render pass or GPU texture is added.

Final source builds/tests pass on both loaders: 446 cases per loader on 26.1,
446 on 26.2 and 449 on 26.3, with no failures/errors/skips. The 37 Atlas Python
checks and 10 version-source contracts pass. Both 26.3 jars contain the wall
texture accessor and matching mixin declaration. These are source/build checks,
not a new full release qualification of all six runtime combinations.

Live 26.3 Fabric evidence includes a normal complete cache rebuild (4,194,304
cells, 1,976,318 marked water cells, 180,023 lit cells), live server-to-client
water/land samples, two successful resource reloads, all three detail levels,
day/night, both viewing directions/rims, periodic seam, underwater and 6/16/32
chunk-distance captures. Cache rebuild/transfer and quality changes have
separate upload stalls; they are excluded from settled movement comparisons.
Rain and custom-resource-pack visual matrices remain release checks.

Two wall palette A/B pairs stayed within 2% p95 frame time. Existing cache
snapshot hitches remain: one repeat included a logged 52 ms snapshot and four
frames over 50 ms. A 32-chunk movement run averaged 44.4 FPS, p95 31.26 ms, with
one frame over 50 ms. Two cold 32-chunk development launches stalled in
Minecraft pipeline compilation/chunk-buffer work. The first required termination.
On the second, the shared background pool had seven occupied workers and 4,852
queued submissions; temporarily increasing its parallelism by two cleared the
stall immediately. The diagnostic restored parallelism after 30 seconds and
left the client running at 32 chunks. This strongly supports worker starvation;
no production thread-pool change was made. Retain both dumps and resolve this
startup/reload risk before 1.4, rather than claiming launch/stutter issues solved.

High costs about 28–29% more p95 frame time on the two tested routes while
leaving the principal cliff/canopy and thin-structure mismatch visible. Adaptive
geometry and continuous compositing are deferred by the plan's comparison gate.
The remaining boundary is not claimed invisible. Details, measurements and
limits are in `TRANSITION_IMPROVEMENT_PLAN_1_4.md`; local evidence is retained in
`logs/transition-plan-execution/`. Owner review and frozen-candidate release
qualification are still required before publication.
