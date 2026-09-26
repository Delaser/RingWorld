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

Replace the ocean-colour heuristic with authored water identification, review
remaining geometry/material transition differences, then qualify the
final 1.4 candidate on the supported loaders and versions. The 26.3 depth,
reload, and wall-filter changes have not been claimed as tested on older
versions or on NeoForge. See [rendering design](RENDERING.md) for the current
implementation. No Distant Horizons code was copied.
