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

## World-creation and in-world UI redesign (27 September)

The owner approved the [rendered proposal](design/ring-generation-ui-proposal/index.html).
The normal Create World entry now opens one RingWorld draft with Ring, Terrain,
Walls, Sky and Preview tabs. Typing a seed changes the preview only. Use locks
that candidate as the applied seed shown below the field; Apply settings then
copies the locked seed into vanilla Create World along with the RingWorld
options. Cancel discards the editor draft. Restore saved settings has its own
confirmation flow.
The Walls tab shows the selected material/pattern sample and keeps the three
patterns visible. The seed preview adds a correctly proportioned local section
and a full-ring navigation strip. The in-world panel separates authoritative
generation controls from local Low/Medium/High display detail. Atlas detail
remains a player-side setting, not a world-generation option.

Development verification: the creation fixture passed 26 captures at GUI
scales 1–4, including a 320×270 logical view and a real preview/draft seed
round-trip, on 26.1.2 Fabric, 26.2 Fabric, and both 26.3 loaders. The in-world
26.3 generation fixture passed on both loaders: start/pause/resume/stop/retry/
completion/reconnect sequence and captured the Display and compact Technical
details pages. [Actual screenshots](media/ring-generation-ui-implemented/index.html)
are retained in the repository. All three supported version lines pass source
builds and Java tests on Fabric and NeoForge. These are development checks;
they do not qualify the final 1.4 release candidate or replace the frozen
runtime matrix.


## Wall material follow-up

The 26.3 Atlas wall palette now uses representative block-face texture colours
instead of map colours. This reduces the contrast jump between real wall blocks
and the distant wall. Palette weights, industrial motifs, decay, lighting and
mip filtering are unchanged. Colours are cached and rebuilt after resource
reload; older version adapters keep their established map-colour path.

The initial wall-only 26.3 Fabric build passed 445 cases. All ten block palettes
were also resolved in the live 26.3 client before and after two reloads. Matched
map/texture palette captures show closer tones without adding a rendering pass.
Owner accepted the updated wall and water appearance on 2026-09-26 after
reviewing clear-weather, rain and resource-pack captures. The resource-pack
test intentionally made deepslate bricks pink; it was removed afterward and
the original colours returned. The pink colour is not a RingWorld default.


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
The 26.3 Fabric 32-chunk development client was also checked in rain and with
the temporary pink deepslate-brick resource pack. The nearby and Atlas walls
both followed the pack, and a second reload restored the original colours.
These checks and the owner's visual approval cover the development candidate;
they do not replace frozen-candidate release qualification.

Two wall palette A/B pairs stayed within 2% p95 frame time. Existing cache
snapshot hitches remain: one repeat included a logged 52 ms snapshot and four
frames over 50 ms. A 32-chunk movement run averaged 44.4 FPS, p95 31.26 ms, with
one frame over 50 ms. Two cold 32-chunk development launches stalled in
Minecraft pipeline compilation/chunk-buffer work. The first required termination.
On the second, the shared background pool had seven occupied workers and 4,852
queued submissions; temporarily increasing its parallelism by two cleared the
stall immediately. The diagnostic restored parallelism after 30 seconds and
left the client running at 32 chunks. This strongly supports worker starvation.

A 26.3 client mixin now gives pipeline compilation one dedicated daemon worker,
so it can finish while chunk buffers occupy Minecraft's shared pool. It does
not change that pool's size or move GPU finalization off the render thread.
Both 26.3 loader builds/tests pass. A 32-chunk Fabric cold launch opened a
complete cached Atlas, reloaded resources and stopped normally. A 32-chunk
NeoForge cold launch downloaded the complete Atlas, reloaded resources and
stopped normally. The pipeline worker appeared in the live Fabric thread dump.
These are development runs; frozen-candidate qualification is still required.
Ordinary cache-copy and GPU-upload hitches are separate.

The 26.3 renderer now reuses its GPU wall texture when the wall inputs are
unchanged. The worker checks the sampled terrain heights used by the inner
industrial motifs, so rim terrain edits still invalidate the texture; a
resource reload also rebuilds it. In a 32-chunk Fabric development run, four
successive ready-surface updates needed one initial wall upload instead of a
wall upload on every update. A reload correctly caused a fresh upload. This
removes one recurring 30–60 ms render-stage cost in that run; Atlas snapshot,
mesh and texture uploads remain measured hitch sources.

The 26.3 NeoForge client also reloaded and stopped normally at 32 chunks. It
uploaded the wall texture on startup and after reload, then reused it through
later ready-surface updates.

Complete client Atlas cache saves now wait for ten quiet seconds after the last
tile change before copying the Atlas; forced disconnect saves and incomplete
Atlas saves retain their previous behaviour. This avoids scheduling repeated
full snapshots during active updates, but the remaining startup/reload uploads
and ordinary cache-copy cost have not been eliminated or benchmarked away.

After these shared-source changes, all six source build/test cells pass: 447
cases per loader on 26.1, 447 on 26.2 and 450 on 26.3, with no failures.

The clean 26.3 quick qualification run `20260926T203515Z-6d299758f87d`
passes both Fabric and NeoForge on pushed source `3016a4b`. Each cell built
and inspected its frozen jar, passed unit/build checks, and started and stopped
an installed dedicated server using that exact candidate. This is the quick
gate, not the ten-fixture-per-cell nightly matrix. An initial attempt exposed
the Python 3.9 release reader rejecting the valid NeoForge Lithium options
table; that reader is fixed and covered by a focused test. A second attempt
encountered a transient Maven `No route to host` during an isolated Fabric
build. The passing run used the documented read-only dependency cache; neither
failed attempt is counted as a pass.

The frozen 26.3 production-render slice
`20260926T210453Z-752830dc4ca0` passes on both loaders using the exact
quick-run jars. It opened a complete format-10 16,384 × 256 Atlas, captured
noon/dusk/night/rain projection views and ran the separate visual-parity
client. The source is the `SeamTest` development world, which contains old
wall-study panels; these images verify runtime behaviour and do not establish
natural-terrain visual quality. The coordinator reports `INCOMPLETE` by design
because this was one selected fixture, not the full nightly matrix. The
production-world reader was updated to accept format 10's packed light/water
byte before this slice ran.

High costs about 28–29% more p95 frame time on the two tested routes while
leaving the principal cliff/canopy and thin-structure mismatch visible. Adaptive
geometry and continuous compositing are deferred by the plan's comparison gate.
The remaining boundary is not claimed invisible. Details, measurements and
limits are in `TRANSITION_IMPROVEMENT_PLAN_1_4.md`; local evidence is retained in
`logs/transition-plan-execution/`. Owner visual review and 26.3 frozen quick
qualification passed. The full nightly matrix, remaining supported-version
qualification, and the planned Astra UI design pass remain before publication.
