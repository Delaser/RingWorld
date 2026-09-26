# Real-terrain / Atlas transition plan for 1.4

Status: material stages implemented and comparison gates exercised, 2026-09-26. Accepted visual baseline:
`37f3257`, following the depth/wall-flicker checkpoint `aea59e5`.
Start with material matching; keep each visual change independently reviewable.
The goal is a less noticeable boundary during movement, without recurring
stutters or materially worse frame times.

## 1. Record a repeatable baseline

Reuse the fully generated 16,384 × 256 test ring and the existing ocean/forest
viewpoints. Save position, rotation, time, weather, render distance, client LOD,
resolution and cloud settings with each capture. Include an along-ring wall
view and a snowy cliff. Capture stationary frames and the same sideways/forward
movement route, using Minecraft's own capture API without stealing focus.

Use Medium initially. Warm the renderer and wait for chunk/Atlas work to settle;
record median, p95 and p99 frame times, frames over 50 ms, and resource-upload
stall logs. Keep startup/reload timing separate from ordinary movement.
Existing pictures are useful references, not a controlled performance baseline.

## 2. Match wall material colours and shading first

Start in `RingWallShaderStyle`, which currently derives wall colours from
`BlockState.getMapColor`. Compute representative colours from the current
client's rendered block-face textures instead. Handle transparent pixels and
face orientation, retaining palette weights, industrial motifs and decay.
Use current texture/model APIs rather than assuming one texture filename per
block; retain a map-colour fallback when a usable face texture is unavailable.

Cache the small palette, invalidate it on resource reload, and pass immutable
colours into the existing wall worker. Do not add per-frame image scans or GPU
readbacks. Compare face brightness separately from material colour so a lighting
mismatch is not compensated by an incorrect palette.

Gate: the dark-grey-to-tan wall jump is visibly reduced at the same viewpoint;
no lost decay holes, changed structures, renewed shimmer or reload failures.
Check the available wall blocksets, both faces, day and night. Reuse the existing
mip filtering and accepted depth mapping.

This can improve walls without changing Atlas storage. General terrain texture
matching is a separate decision: current terrain cells store RGB rather than
block/material IDs, so client resource-pack matching cannot simply be inferred
for every surface from that RGB.

## 3. Make the accepted water colour reliable

Replace the shader's ocean-colour/Y=63 heuristic with explicit water coverage
captured from the actual fluid state. Keep block-light data independent. Update
storage, tiles, format/cache identity and both loaders together; decide the
smallest representation before changing the format. Existing world blocks are
preserved; Atlas caches must be versioned and rebuilt through the normal path.

Carry fractional water coverage through display downsampling and mipmaps so
shorelines and biome boundaries blend rather than acquire a hard mask edge.
Apply the accepted tint to marked water, and align the distant live-water colour
target with the final Atlas tint. Preserve the nearby water appearance.

Gate: ocean, river, swamp, custom biome colours and non-default water heights
work; blue non-water blocks remain untouched. Verify colour/light capture,
cache round-trip, tile transfer, Low/Medium/High display, shorelines and reloads.
Keep this as its own commit so visual tuning and data-format changes are separable.

## 4. Decide whether extra geometry is worth its cost

First compare current Medium and High on the same forest/cliff route. Both
sample step and geometry caps matter; do not assume the label means every axis
is rendered at one-block resolution.

If High materially reduces silhouette changes, prototype finer geometry only
near the transition, using the existing one-block source Atlas. Retain the
selected far-distance quality. Build on the worker, update by coarse camera
regions with hysteresis, and bound uploads; never rebuild the ring every frame.
Maintain shared edge positions across resolution boundaries and the periodic seam.

Gate: reduced cliff/canopy popping without cracks or recurring rebuild stalls.
If High does not help, stop this approach: a heightfield cannot represent trunks,
overhangs and stacked surfaces merely by adding more samples. Record those cases
rather than promising that resolution alone will fix them.

## 5. Prototype continuous blending only if still needed

If the remaining distraction is the ordered screen-door pattern, test rendering
the Atlas into a separate colour/depth target and combining it with real terrain
using a depth-aware smooth blend. Preserve foreground occlusion and the accepted
near/far depth ordering. Decide placement around translucent water/OIT before
implementation so water is neither double-blended nor incorrectly occluded.

Implement independently; do not copy Distant Horizons code. Keep the current
path available for a controlled A/B comparison during development. This stage
adds render targets and a pass, so measure GPU cost and memory before retaining it.
No new user-facing quality toggle is required for the experiment.

Gate: reduced stipple during motion without ghosted trees, doubled coastlines,
see-through cliffs, sky holes or broken water. If image blending leaves too much
heightfield mismatch, document that limit instead of hiding it with more fog.

## Shared acceptance and release checks

- Compare each stage against the accepted baseline, then checkpoint the improvement.
- Proposed performance gate: no repeatable p95 regression above 10% and no
  additional recurring >50 ms movement stalls. Repeat comparable runs before
  attributing a change to the renderer; do not infer performance from screenshots.
- Verify the handoff at short and long render distances, each of the three LOD
  levels, day/night, rain, both rims, the periodic seam and underwater views.
- Preserve environmental fog, nearby interaction geometry and multiplayer's
  independent client detail setting. Do not increase global view distance or
  return to flattened proxy depth to conceal the boundary.
- Run tests appropriate to the changed layer and real client reload/movement
  checks. Before 1.4, qualify final source on the supported loader/version matrix;
  current visual evidence is 26.3 Fabric, not universal release qualification.

Implementation order: baseline → wall materials → authored water → geometry
comparison. Only proceed to adaptive geometry or compositing when the comparison
shows a worthwhile remaining problem. The accepted game stays available for
owner review between stages.


## Execution checkpoint

Implemented texture-derived wall palettes and authored water coverage. Kept the
accepted depth, wall mip filtering, lighting and live-terrain dither policies.
No new quality setting, adaptive mesh, render target or compositing pass.

The 26.3 Fabric comparison used the complete 16,384 × 256 ring at 2940 × 1846,
noon, clear weather, clouds off, 16-chunk view distance and 20 ticks/second.
Two matched ocean movement pairs put map-colour p95 at 30.61/31.13 ms and
texture-colour p95 at 30.86/31.50 ms (less than 2% difference). One texture run
coincided with a logged 52 ms cache snapshot; this is not evidence that all
stuttering is fixed. Startup, regeneration, reload and quality-change uploads
are excluded from these runs.

High increased ocean p95 from 30.86 to 39.76 ms and forest p95 from 28.47 to
36.65 ms. It adds detail but leaves the obvious cliff/canopy boundary and the
vertical surfaces below thin structures. The review world also contains old
wall-study panels; their heightfield curtains are particularly visible and
must not be presented as a natural-terrain quality measurement. These captures
do not establish a worthwhile transition gain from adaptive geometry.
Defer that prototype. Continuous compositing is also held: the visible residual
is substantial geometry/material mismatch, which a smoother alpha blend alone
would risk turning into overlapping surfaces.

Local before/after images, frame summaries, capture settings and failure
thread dumps are in `logs/transition-plan-execution/`. The comparison page uses
actual Minecraft screenshots. Full release qualification and owner visual
acceptance of these new material changes remain separate from this checkpoint.


A separate 32-chunk cold-start blocker emerged twice: the render thread waits
for a compiled pipeline while all seven shared workers are occupied by chunk
buffer work. A diagnostic two-worker increase immediately released the second
stall; normal parallelism was restored after 30 seconds. No production pool
change is included. Fix and qualify that startup/reload path before release;
keep it separate from the material and geometry decisions above.
