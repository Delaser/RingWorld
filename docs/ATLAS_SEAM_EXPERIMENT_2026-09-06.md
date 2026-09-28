# Atlas/live seam experiment — profile 6

Baseline: pushed checkpoint `b5469760d092fa79a6f783daf17c15a522b33953`. The owner requested the first seam-smoothing experiment after reviewing that checkpoint. The owner accepted the visual result on 2026-09-06, noting an occasional small stutter remains.

Profile 6 keeps live terrain solid until 90% of effective view distance instead of 78%; fade end stays 102%. The band therefore shrinks from 24% to 12% of view distance for ordinary views. The proxy alpha/depth curve remains unchanged. Near Atlas reveal rises from 0.52 to 0.82; far reveal/haze remain unchanged.

Both fragment shaders use one shared atmospheric tint/reveal helper. Live terrain eases toward that treatment from the detail-start distance to the start of its geometry fade, retaining real texture/face lighting and environmental fog. This matches atmospheric colour, not individual live block RGB to sampled Atlas RGB. The previously unused fourth Atlas-light uniform component now carries the backdrop ID; the uniform layout/size is unchanged. No added textures, geometry, render passes, or extra texture fetches. Existing terrain screen-space dithering remains inside the narrower band. Water transparency, shape mismatch and fine silhouettes remain visible; this is not a claim of an invisible seam.

## Validation

- Both Fabric and NeoForge builds pass 424 JVM tests each on 26.1.2 and 26.2. The added policy test checks the narrower band and overlap ordering at multiple view distances.
- Python suite passes 429 tests with two platform skips. Updated the existing shader depth/alpha contract to recognize the shared edge-colour helper; depth semantics are unchanged.
- Fabric 26.2 runtime loads all shaders and the complete 16,384×256 one-block Atlas at current Max. Original source fidelity defaults restored after runtime compilation.
- Same-position/yaw/pitch before/after noon and midnight screenshots: `logs/seam-handoff-v6/index.html`, originals under its `raw/`. Both daytime frames were visually inspected, plus the post-change night frame. Entity positions can differ across restart.
- Controlled camera pan: 450 scheduled yaw offsets tracing one ±3° cycle, no position changes, no screenshot capture during measurement. Frame intervals are recorded by the existing gallery frame callback, not one-second FPS counters. Simulation frozen in both windows; temporary 260 FPS/idle-limiter override; normal settings and simulation restored afterwards. Separate client runs and uncontrolled host load limit the comparison.

| Profile | Frames | Duration | Average FPS | Maximum frame | Frames >50 ms |
|---|---:|---:|---:|---:|---:|
| 5 baseline | 1341 | 20.747 s | 64.63 | 27.41 ms | 0 |
| 6 experiment | 1346 | 20.437 s | 65.86 | 36.96 ms | 0 |

No obvious performance regression in this short pan test. This does not establish improved FPS or eliminate movement shimmer. No synchronized before/after video was recorded; owner motion review remains useful. Terrain/water appearance still differs at the handoff.

Camera: X3167.6824780489164,Y98.15814676123665,Z121.84930255458498,yaw251.74527,pitch13.050017. Historical client PID6454, runtime log `logs/seam-handoff-v6/runtime.log`; frame results `baseline-motion.txt` and `after-motion.txt`. That client was subsequently saved and closed for the isolated wall study; the source world was preserved.
