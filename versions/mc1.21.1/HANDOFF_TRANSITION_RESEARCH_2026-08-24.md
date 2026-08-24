# Minecraft 1.21.1 live/Atlas handoff research — 2026-08-24

This is the running engineering record for the second visual-smoothness pass on
the Minecraft 1.21.1 backport. It records accepted and rejected experiments so
that useful results can be carried forward to the 26.1 mainline without
repeating visual dead ends.

## Scope and rollback point

- Accepted starting checkpoint: commit `7010d2a` (`checkpoint: smooth 1.21.1
  atlas handoff`).
- The checkpoint is preserved in Git before any experiment in this pass.
- Runtime screenshots and logs are intentionally kept outside Git under
  `.codex-tmp/handoff-smoothness-2026-08-24/`; this document is their durable
  index and technical interpretation.
- The complete production `16,384 x 256` Atlas source world remains immutable.
  Each graphical run uses the existing copied-world fixture.
- Fabric is the fast experiment loop. A candidate is not accepted until the
  same source passes NeoForge and the loader captures are visually compared.

## Retained architecture

The retained renderer does not switch abruptly from live chunks to the Atlas.
It draws the complete-ring Atlas proxy after `compileSections(Camera)` and
immediately before vanilla terrain, with depth writes disabled, then performs
a real source-alpha transition in the legacy terrain pipeline. This timing is
important: it lets the coverage gate inspect the exact current
`visibleSections` list after compilation and prevents an older sky-tail proxy
draw from covering the replacement central-star quad. The profile is derived
only from the stable effective render distance (`effectiveChunks * 16`). The
earlier dynamic received-chunk availability profile and its four-block tick
quantization have been removed.
The shared `RingRenderProfile` still carries the mainline fields:

- proxy opacity: `0.68` to `0.98` of the handoff view distance;
- live-terrain dither: `0.78` to `1.02`;
- Atlas terrain detail: `0.76` to `1.25`;
- Atlas reveal: `0.52` to `0.98`;
- far haze: `0.04` to `0.16`, exponent `1.35`.

Experiment 19 derives a backport-only proxy ramp from those unchanged fields:
the proxy fades from `0.58V` to opaque at `0.68V`, then live alpha, reconstructed
proxy tone, and the reveal floor transition continuously from `0.68V` through
`1.02V`. At `V=256`, that is an `87.04`-block live/proxy overlap. The same
fixed-distance policy applies to solid, cutout, and translucent terrain,
including water. A narrow 1.21.1 render-state adapter owns and restores blend
state; it does not change the protocol, Atlas bytes, common profile fields, or
loader adapters.

Initial chunk streaming is now a separate coverage concern rather than an
input to the visual profile. Its conservative Atlas floor is documented below;
once the finite-band drawable window is complete, the floor becomes an exact
Experiment 19 no-op.

## Baseline evidence

`00-checkpoint/` contains Fabric and NeoForge tangent, handoff, and radial-up
captures copied before this pass. This is the visual rollback reference.

The remaining defect is subtle rather than the former hard cutoff: water and
some terrain silhouettes can still make the live/proxy boundary readable in a
still image. Candidate changes must improve that boundary without flattening
Atlas detail, leaking sky, increasing dither grain, or destabilizing the view
when chunks arrive and leave.

## Experiment protocol

For every attempted policy:

1. record the hypothesis and exact source change here;
2. run focused unit/contract checks;
3. run the copied production projection client with clouds disabled at daytime;
4. archive tangent, handoff, and radial-up PNGs plus the relevant log;
5. calculate fixed-region transition metrics and generate comparison crops;
6. reject or promote against the preserved checkpoint;
7. for a promoted candidate, repeat on NeoForge and run the broader visual gate.

No experiment may change persisted Atlas bytes merely to tune the handoff.
Changes to Atlas colour encoding would require an explicit format/identity bump
and regeneration, so they are a separate, higher-risk line of research.

The capture must also prove `loadedX=+N/-N` for its requested/effective radius
before settling. `hasRenderedAllSections()` describes the sections already
known to the renderer, not the complete requested network radius. NeoForge
28-chunk and one 16-chunk environment run armed prematurely at `+20/-20` and
`+11/-11`. Their logs are useful negative fixture evidence, but their PNGs were
written only after later settle time and contain no capture-time radius marker;
they do **not** prove what the early streaming edge looked like. The settled
fixture waits for both contiguous directions and fails closed on timeout.

### Fixed-region image analysis

All candidate measurements use the original 854x480 handoff PNG without
rescaling. Coordinates below use half-open image ranges, and RGB channels use
their ordinary 0–255 values:

- local residual uses `x=[170,690)`, `y=[185,365)`. The region is edge-padded
  by one pixel, each pixel is compared with its arithmetic 3x3 local RGB mean,
  and the Euclidean RGB residual is summarized by mean and 95th percentile;
- the targeted water measure uses `x=[359,598)`, `y=[225,310)`. Horizontal
  adjacent-pixel differences are summed across RGB, then summarized by their
  mean and the fraction at or above an RGB-L1 distance of `60`;
- the loader comparison is the absolute per-channel difference over the full
  frame, summarized by mean and 95th percentile.

These are fixed-pose comparative measures, not perceptual quality scores.
Foliage and water animation can shift the broad local-residual result between
runs; the water high-gradient fraction is the more targeted detector for the
stippled handoff. Visual review of the full tangent, handoff, and radial-up
captures remains mandatory.

## Experiment log

### 00 — accepted checkpoint

- Source: `7010d2a`.
- Evidence: `00-checkpoint/fabric/` and `00-checkpoint/neoforge/`.
- Status: rollback baseline.

### 01 — one-Atlas-sample live-edge safety inset

- Hypothesis: `ClientChunkCache.hasChunk()` proves that a chunk was received,
  but not that its boundary section is compiled and drawable on the same
  frame. The checkpoint ends live dither exactly at the measured chunk edge,
  allowing a final hard section row to become visible.
- Change under test: subtract one eight-block Atlas sample from the measured
  live radius before fitting the unchanged mainline profile. For the current
  production fixture, the live endpoint moves from 208 to 200 blocks and the
  profile input moves from 203.92 to 196.08 blocks. The 78–102% dither remains
  approximately 153–200 blocks wide.
- Architectural value: the inset is loader-neutral, retains the shared profile
  ratios and accepted dither, and does not alter Atlas bytes or protocol state.
- Risk to inspect: the lower-detail proxy takes ownership eight blocks sooner;
  the minimum six-chunk local radius must remain protected.
- Evidence: `01-inset8/fabric/` plus `comparison-00-01-full.png` and
  `comparison-00-01-transition-crop.png`.
- Result: rejected. After accounting for the 0.684-degree pitch change (about
  four screen pixels), the whole-ROI maximum adjacent-row RGB jump changed
  from 43.58 to 45.66; mean and 95th-percentile changes were small, and the
  worst strip was unchanged. Grain was effectively neutral. Radial-up was
  pixel-identical and no regression appeared, but there was no material win.

### 02 — synchronize projection view distance before login

- Finding: the fixture set the requested 16-chunk option only after joining the
  integrated server. In Minecraft 1.21.1 that option callback rebuilds the
  client renderer but does not broadcast new `ClientInformation`. The client
  therefore reported an effective distance of 16 while the server retained the
  default request of 12. The server's 12-chunk `ChunkMap` filter explains the
  observed contiguous `+13/-13` row exactly; this was not ordinary streaming
  lag.
- Change under test: set the isolated client's requested view distance before
  opening the copied world, so the login packet and integrated server agree.
  No renderer policy changes are included in this attempt.
- Mainline value: any automated client that changes view distance after login
  must either broadcast options or configure it before login. A capture that
  does neither can misdiagnose a correct transition as a renderer defect.
- Evidence: `02-prelogin16/fabric/`.
- Result: confirmed. The server began at 16 chunks, the capture observed
  `+16/-16`, and the handoff profile input moved from 203.92 to 250.98 blocks.
  The transition is farther away and less dominant onscreen. This fixture fix
  is retained independently of subsequent renderer experiments.

### 03 — synchronized mainline transition semantics

- Hypothesis: the checkpoint's compressed proxy ramp, global availability
  profile, synthetic live-tone convergence, and special translucent alpha fade
  were compensating for the fixture's stale 12-chunk server window. Once login
  is synchronized, the most updateable and potentially smoothest result is the
  already-reviewed mainline policy itself.
- Change under test: use `getEffectiveRenderDistance() * 16` as the stable
  profile input; restore the full 68–98% proxy opacity ramp; remove synthetic
  live-tone blending; and apply the same deterministic screen-space dither to
  opaque, cutout, and translucent terrain.
- Mainline value: keeps shared profile semantics exact and removes a moving
  global radius that otherwise changes live coverage, Atlas detail, and cloud
  fade in four-block client-tick steps.
- Risk to inspect: the legacy framebuffer may composite some sky through the
  broad proxy alpha, especially behind translucent water or mismatched proxy
  silhouettes.
- Evidence: `03-mainline-parity/fabric/`.
- Result: rejected on 1.21.1. The synchronized distance and geometry are sound,
  but the broad proxy alpha exposes a pale dotted sky shelf through discarded
  live fragments. This confirms that mainline's numeric policy cannot be
  copied without adapting its coverage/compositing semantics to the legacy
  framebuffer.

### 04 — broad proxy ramp with complementary coverage

- Hypothesis: preserve mainline's independent 68–98% proxy ramp but correlate
  it with the live dither. Where the exact screen-space threshold makes live
  terrain discard, force the already-rendered proxy pixel opaque; elsewhere,
  retain ordinary proxy alpha. The union of live and proxy coverage therefore
  contains no sky hole.
- Change under test: duplicate the stable interleaved-gradient threshold in
  `ring_surface.fsh`, calculate the same 78–102% live coverage, and raise proxy
  alpha to one when `threshold < liveCoverage`, exactly matching the later
  live-terrain discard branch.
- Architectural value: one proxy draw, no new render state, no loader adapter,
  unchanged protocol/Atlas/profile, and a narrowly documented 1.21.1 shader
  compatibility layer.
- Risks: live and proxy relief differ at trees/coastlines; cutout holes may be
  filled by Atlas terrain; incomplete Atlas guard pixels become opaque fog;
  and resource packs must replace both sides consistently.
- Evidence: `04-complementary/fabric/`.
- Result: rejected as a complete solution, but the technique remains a valid
  coverage invariant. It did not remove the pale dotted shelf. Because every
  discarded live pixel now has opaque proxy behind it, the remaining shelf is
  primarily the colour/lighting difference between a live fragment and the
  corresponding Atlas fragment, not bright sky leaking through an uncovered
  pixel. The guard also changes incomplete-Atlas semantics and is therefore not
  retained without a visible benefit.

### 05 — inverted complementary predicate diagnostic

- Hypothesis: intentionally invert the guard to verify that the predicate has
  the expected relationship with the later live shader.
- Change under test: force proxy alpha to one when
  `threshold > liveCoverage`, which is where live terrain survives.
- Evidence: `05-complementary-corrected/fabric/` (the directory name predates
  this audit; its role is recorded correctly here and in comparison labels).
- Result: rejected diagnostic. It preserves ordinary broad proxy alpha exactly
  where live terrain discards and remains visibly shelved. The small pixelwise
  difference from experiment 04 confirms the guard executes, while the shared
  large colour discontinuity confirms the next work must match proxy/live tone
  or avoid spatially interleaving unlike colours.

### 06 — stable mainline distances with legacy tone convergence

- Hypothesis: the reusable part of mainline is its fixed effective-distance
  profile, while 1.21.1 still needs a narrow compositor adapter analogous to
  mainline terrain's `ChunkVisibility` fog-colour convergence. The checkpoint's
  synthetic tone and continuous translucent fade provide that adapter; its
  global received-chunk radius and compressed proxy span do not need to return.
- Change under test: retain the synchronized fixed 256-block profile and full
  68–98% proxy ramp; restore fog-aware live-tone convergence for solid/cutout
  layers; restore the continuous alpha fade for translucent water; and remove
  the complementary proxy guard.
- Mainline value: separates shared policy from version-specific composition.
  It also tests whether mainline's newer terrain visibility term, rather than
  its distance constants alone, is the missing parity mechanism.
- Risks: the reconstructed tone uses the live fragment's colour rather than the
  Atlas texel, so coastlines and eight-block Atlas sampling can still disagree;
  broad proxy alpha may still include sky in the composite before live draw.
- Evidence: `06-stable-tone/fabric/`.
- Result: partial improvement, not promoted. Fog-aware live convergence and the
  continuous translucent fade remove the harsh white shelf seen in experiments
  03–05, confirming that 1.21.1 needs a compositor-specific tone adapter. The
  full 68–98% proxy ramp still leaves more pale grain than experiment 02:
  fixed-ROI 3x3 residual mean/p95 were `8.58/41.33`, versus `7.91/36.43` for
  experiment 02. This isolates proxy/sky alpha as the remaining difference.

### 07 — stable profile with opaque-underlay invariant

- Hypothesis: retain all shared mainline distance constants except the proxy
  opacity endpoint. On the legacy framebuffer, make the proxy fully opaque at
  live-fade start so later dither is a two-representation handoff rather than a
  three-way mix of live terrain, translucent proxy, and sky.
- Change under test: keep the fixed 256-block profile, tone convergence, and
  continuous translucent fade from experiment 06; set only proxy fade end to
  the shared live-fade start (`0.78 * viewDistance`).
- Architectural value: one explicit 1.21.1 composition invariant replaces the
  former runtime chunk-availability heuristic; mainline profile start points,
  live fade, detail, reveal, haze, Atlas data, and protocol remain shared.
- Evidence: `07-stable-opaque/fabric/`.
- Result: promoted as the current dither-based candidate. Its fixed-ROI local
  residual mean/p95 are `7.69/35.25`, improving on experiment 02's
  `7.91/36.43`; pale neutral pixels remain bounded (`6` versus `2`) and are far
  below experiment 06's `44`. The capture is visually smooth and the profile
  is stable, but a dark dither texture remains visible over ocean relief.

### 08 — front-loaded live-tone convergence

- Hypothesis: dither is most visible near half coverage, where unlike live and
  proxy colours each occupy many pixels. Move the surviving live fragments
  toward their proxy-like fog tone faster than coverage disappears, without
  changing geometry or handoff distances.
- Change under test: retain experiment 07 and replace the linear tone weight
  `c` with `1 - (1 - c)^2`. This finite-slope curve maps 25/50/75% coverage to
  44/75/94% tone convergence while still reaching the exact endpoints.
- Mainline value: this can be compared with the newer engine's
  `ChunkVisibility` ordering; it is a general fallback when an old terrain
  pipeline cannot sample or blend the proxy directly.
- Risk: earlier fog convergence may create a muted band even if it suppresses
  pixel grain.
- Evidence: `08-frontloaded-tone/fabric/`.
- Result: promoted as the current dither candidate. Against experiment 07 at
  the same pose, fixed-ROI residual mean/p95 improve from `7.69/35.25` to
  `7.31/32.55`, the water-band horizontal RGB-L1 mean improves from `11.97` to
  `11.65`, and the pale-neutral count remains `6`. No muted band is apparent.

### 09 — legacy lightmap sampling parity

- Finding: vanilla 1.21.1 live terrain divides UV2 `(0,240)` by 256 and clamps
  it, while the lightmap is linearly filtered. Full sky/no block light therefore
  samples `(0.5/16, 15/16)`, halfway between rows 14 and 15. The proxy sampled
  `(0.5/16, 15.5/16)`, the exact centre of row 15, which is the correct
  newer-mainline convention but makes the backport proxy slightly brighter.
- Change under test: retain experiment 08 and make the proxy use the exact
  1.21.1 live UV/filter convention. Atlas bytes and shared profile are unchanged.
- Mainline value: do not copy this coordinate forward. Instead, treat matching
  live/proxy lightmap UV and sampler semantics as a per-version shader contract;
  mainline correctly uses row-15-centre semantics for both paths.
- Risk: the proxy will be slightly darker at night/rain; those environments
  need explicit captures if the noon result is promoted.
- Evidence: `09-lightmap-match/fabric/`.
- Result: promoted. Against experiment 08 at the same pose, fixed-ROI residual
  mean/p95 improve from `7.31/32.55` to `7.22/32.01`, water-band horizontal
  RGB-L1 mean improves from `11.65` to `11.32`, and pale-neutral pixels fall
  from `6` to `3`. The visual change is subtle but every targeted measure moves
  in the expected direction.

### 10 — true legacy opaque-layer alpha crossfade

- Hypothesis: any binary dither remains visible when live block texture/AO and
  Atlas map-colour relief differ. A mathematically continuous source-alpha
  blend can instead produce `live * (1-c) + proxy * c` at each covered pixel.
- Change under test: around only the RingWorld solid, cutout-mipped, and cutout
  section draws, enable standard source-alpha blending after the render type's
  setup and restore vanilla state before its clear. Keep depth tests and depth
  writes. Replace opaque/cutout dither discard with output alpha `1-c`; retain
  experiment 09's opaque proxy underlay, front-loaded tone, continuous
  translucent fade, and matched lightmap sample.
- Architectural value: one narrow 1.21.1 render-state adapter; the shared
  profile, shader distances, Atlas/protocol, and both loader paths remain
  common. Mainline should retain its native pipeline and `ChunkVisibility`.
- Risks: blended opaque geometry still writes depth, so cutout foliage and
  overlapping surfaces can expose draw-order artifacts; the transition must be
  tested at natural seams, rims, multiple view distances, and in motion.
- Evidence: `10-alpha-crossfade/fabric/`.
- Result: promoted as the overall candidate. The stippled shelf disappears.
  Against experiment 09 at the same pose, fixed-ROI residual mean/p95 fall from
  `7.22/32.01` to `5.71/24.49`, water-band RGB-L1 mean falls from `11.32` to
  `6.54`, and high-gradient pixel fraction falls from `5.58%` to `1.13%`.
  Tangent and radial captures show no obvious geometry or state leak. This is
  the first experiment that changes the transition from spatially interleaved
  dots to a continuous blend.

### 11 — alpha crossfade without reconstructed proxy tone

- Hypothesis: once the framebuffer performs a real live-over-proxy crossfade,
  synthetic live-tone convergence may be redundant and can create an
  unnecessarily muted band. Removing it also restores source similarity with
  mainline's live shader and eliminates two backport-only terrain uniforms.
- Change under test: retain experiment 10's render-state/alpha behavior,
  opaque underlay, and matched legacy lightmap sample; remove proxy-tone RGB
  mixing from every live terrain layer. Alpha alone controls representation
  weight.
- Architectural value: smallest shader delta and easiest future mainline
  updates if visual quality holds.
- Risk: the low-frequency live/Atlas colour shift may become more visible even
  though dither grain remains absent.
- Evidence: `11-pure-alpha/fabric/`.
- Result: rejected. The image remains much better than every dither candidate,
  but compared with experiment 10 the fixed-ROI residual mean/p95 rise from
  `5.71/24.49` to `5.93/25.17`, water-band RGB-L1 rises from `6.54` to `7.34`,
  and high-gradient fraction rises from `1.13%` to `1.88%`. Tone convergence
  remains useful even when alpha provides continuous representation weight.

### 12 — alpha crossfade with linear tone convergence

- Hypothesis: experiment 10's front-loaded quadratic is smoothest but may mute
  the live band sooner than necessary. Linear tone convergence may retain more
  local colour while the alpha crossfade suppresses high-frequency mismatch.
- Change under test: restore proxy-like tone convergence using weight `c`
  instead of experiment 10's `1 - (1-c)^2`; retain all alpha/state/lightmap and
  stable-profile behavior.
- Evidence: `12-linear-alpha/fabric/`.

### 13 — alpha crossfade with balanced tone convergence

- Hypothesis: the midpoint between linear and experiment 10's front-loaded
  curve may retain more local live colour than experiment 10 while suppressing
  more low-frequency mismatch than experiment 12.
- Change under test: use `c * (1.5 - 0.5c)`, exactly halfway between `c` and
  `1 - (1-c)^2`, while retaining the same alpha/state/lightmap policy.
- Evidence: `13-balanced-alpha/fabric/`.

### 14 — near-handoff proxy detail floor

- Finding: at a 256-block profile input, the shared proxy reveal is roughly
  `0.50` when live fading starts at 199.68 blocks, while vanilla live terrain
  remains essentially unfogged until 230.4 blocks. Proxy detail then rises as
  vanilla live fog reaches full strength at 256 blocks. Even with continuous
  alpha, that opposing movement leaves a smooth pale strip.
- Hypothesis: in the legacy compositor only, raise proxy terrain reveal beneath
  the handoff toward `1.0`, then merge that floor smoothly into the unchanged
  shared far reveal by `RingWorldDetail.y` (320 blocks). Apply the same model to
  live tone convergence so its final fragments approach the actual proxy tone.
- Change under test: retain experiment 10's full quadratic tone convergence;
  add a reveal floor `mix(revealFar, 1, 1-smootherstep(liveStart, detailEnd,d))`
  before ordinary far haze and incomplete-Atlas haze.
- Mainline value: this is an explicit fallback for engines without a native
  chunk-visibility tone input; mainline should first compare its native
  `ChunkVisibility` behavior rather than copy the floor automatically.
- Evidence: `14-reveal-floor-alpha/fabric/`.
- Result: visually promising diagnostic. The pale strip is substantially less
  legible, water-band RGB-L1 falls from experiment 10's `6.5435` to `6.0065`,
  and high-gradient coverage falls from `1.1270%` to `0.6031%`. Fixed-ROI
  residual mean rises slightly from `5.7068` to `5.7964`, while p95 remains
  nearly flat (`24.4896` to `24.5426`). The strong floor confirms the reveal
  trough, but it changes proxy detail from proxy start through detail end; a
  bounded overlap-only alternative is tested next.

### 15 — overlap-only proxy detail advance

- Hypothesis: advance the existing detail curve only in the middle of the live
  alpha handoff, with a bell window that is exactly zero (and has zero slope)
  at both endpoints. This should lift the reveal trough without changing any
  post-handoff or distant-ring pixel.
- Change under test: use live coverage `c`, window `4c(1-c)`, and sample the
  shared detail curve at `d + 0.25*(liveEnd-liveStart)*window` in both the proxy
  and reconstructed live tone. Haze remains evaluated at the real distance.
- Mainline value: this reuses existing profile fields and is a bounded fallback
  candidate if native `ChunkVisibility` still leaves a measurable trough.
- Evidence: `15-detail-advance-alpha/fabric/`.
- Result: rejected. It is mathematically bounded and updateable, but produces
  residual mean/p95 `5.7313/24.5948`, water RGB-L1 `6.8464`, and high-gradient
  coverage `1.1616%`: all worse than experiment 14, and the water measure is
  also worse than experiment 10. The small detail-distance advance does not
  lift the reveal trough enough.

### 16 — reveal floor with opaque-layer dither

- Hypothesis: experiment 14's stronger live/proxy tone match may make binary
  coverage visually acceptable again. If so, the backport can avoid enabling
  blending on vanilla solid/cutout render types, preserving their normal
  depth, ordering, sprite-alpha, and NeoForge render-stage semantics.
- Change under test: retain experiment 14's reveal floor, full tone convergence,
  translucent alpha fade, and lightmap match; restore deterministic coverage
  discard for solid, cutout-mipped, and cutout terrain and remove the temporary
  opaque render-state override.
- Evidence: `16-reveal-floor-dither/fabric/`.
- Result: rejected. Residual mean/p95 regress to `7.2462/32.8540`, water
  RGB-L1 nearly doubles from experiment 14 to `11.7927`, and high-gradient
  coverage rises to `5.7687%`. Tone matching alone cannot hide a binary choice
  between detailed live textures and Atlas map colour. Continuous alpha is
  retained, with its render-state ownership narrowed and restored before
  NeoForge after-layer callbacks.

### 17 — softer reveal floor with hardened alpha state

- Hypothesis: experiment 14's maximum floor of `1.0` may expose more Atlas
  saturation than necessary. A `0.90` floor might retain most of the band
  reduction while reducing the local texture/Atlas residual.
- Change under test: restore continuous alpha using a dedicated legacy mixin
  with active-state tracking and pre-`ShaderInstance.clear()` restoration;
  distinguish opaque cutout from translucent tripwire with a static shader
  mode; lower only the handoff-floor maximum from `1.0` to `0.90`.
- Evidence: `17-soft-floor-alpha/fabric/`.
- Result: rejected. Water RGB-L1 rises from experiment 14's `6.0065` to
  `6.1445`, high-gradient coverage rises from `0.6031%` to `0.6822%`, and
  residual mean/p95 rise from `5.7964/24.5426` to `5.8773/25.3034`. The
  physically bounded full-colour ceiling is both smoother and simpler.

### 18 — hardened alpha with full reveal floor

- Hypothesis: experiment 14 identified the correct colour/reveal policy, while
  experiment 17 supplied the production-safe state ownership and layer-alpha
  semantics. Combining them should retain experiment 14's smooth water band
  without leaking blend state into NeoForge's after-layer callbacks or
  interpreting opaque cutout texture alpha as translucency.
- Change under test: restore experiment 14's `1.0` reveal floor with the final
  narrow blend-state adapter, shared Handoff.w contract restored, opaque
  cutout alpha normalization, and separate translucent tripwire mode.
- Result: promoted from the focused handoff pass. The continuous alpha blend
  remains materially smoother than every dither variant, and the full reveal
  floor recovers experiment 14's water-band result while retaining the
  hardened state adapter. No hard live/Atlas coverage edge, render-state leak,
  or material loader divergence is apparent in the reviewed tangent, handoff,
  radial-up, view-distance, dusk, night, or rain stills.

The exact fixed-region results are:

| Capture | Residual mean / p95 | Water RGB-L1 mean | Water gradients >=60 |
| --- | ---: | ---: | ---: |
| 10 Fabric, first alpha | `5.7068 / 24.4896` | `6.5435` | `1.1270%` |
| 14 Fabric, reveal-floor diagnostic | `5.7964 / 24.5426` | `6.0065` | `0.6031%` |
| 15 Fabric, bounded detail advance | `5.7313 / 24.5948` | `6.8464` | `1.1616%` |
| 16 Fabric, reveal-floor dither | `7.2462 / 32.8540` | `11.7927` | `5.7687%` |
| 17 Fabric, 0.90 floor | `5.8773 / 25.3034` | `6.1445` | `0.6822%` |
| 18 Fabric, hardened full floor | `5.8932 / 25.3204` | `6.0072` | `0.6525%` |
| 18 NeoForge, hardened full floor | `5.9239 / 25.3943` | `6.0665` | `0.6574%` |

The Experiment 18 Fabric/NeoForge full-frame absolute channel difference is
`0.5534` on a 0–255 scale, with p95 `3`. This is strong material parity, not a
pixel-identity assertion: foliage and water animation were captured at
slightly different instants.

The settled-radius visual matrix produced tangent, handoff, and radial-up
captures for every cell. The table records the handoff capture's contiguous X
radius and settled-frame maximum/over-50-ms count:

| Cell | Fabric | NeoForge |
| --- | --- | --- |
| 6 chunks, noon | `+6/-6`; max `26.23 ms`; `0` over 50 ms | `+6/-6`; max `29.24 ms`; `0` over 50 ms |
| 12 chunks, noon | `+12/-12`; max `31.53 ms`; `0` over 50 ms | `+12/-12`; max `43.56 ms`; `0` over 50 ms |
| 28 chunks, noon | `+28/-28`; max `63.05 ms`; `1` over 50 ms | `+28/-28`; max `49.21 ms`; `0` over 50 ms |
| 16 chunks, dusk | `+16/-16`; max `36.78 ms`; `0` over 50 ms | `+16/-16`; max `36.36 ms`; `0` over 50 ms |
| 16 chunks, night | `+16/-16`; max `37.12 ms`; `0` over 50 ms | `+16/-16`; max `39.10 ms`; `0` over 50 ms |
| 16 chunks, rain | `+16/-16`; max `34.93 ms`; `0` over 50 ms | `+16/-16`; max `34.38 ms`; `0` over 50 ms |

Before the full-radius wait was added, NeoForge's ordinary renderer-readiness
predicate allowed two premature captures. The 28-chunk noon run armed at
`+20/-20`, with 895 handoff-stage samples, a `100.62 ms` maximum, and `20`
frames over 50 ms. The 16-chunk dusk run armed at `+11/-11`, with 911 samples,
a `120.56 ms` maximum, and `7` frames over 50 ms. Those arm markers and stalls
are negative fixture evidence. The screenshots were saved after subsequent
settle time and were not bound to the incomplete radii, so they cannot be used
as early-streaming visual evidence. The corrected settled reruns reached
`+28/-28` and `+16/-16` before arming and reduced the over-50-ms counts to
zero.

Evidence and generated review sheets:

- raw Experiment 18 captures: `18-hardened-full-floor/`;
- candidate progression: `comparisons/candidate-progression-09-18.png`;
- final Fabric/NeoForge view pairs: `comparisons/final-loader-views.png`;
- settled loader matrix: `comparisons/final-loader-matrix-handoff.png`;
- premature-arm-run versus settled NeoForge outputs (not capture-time
  early-streaming proof):
  `comparisons/neoforge-streaming-edge-vs-settled.png`;
- reproducible metric output: `comparisons/experiment-18-metrics.json`.

All of those paths are relative to
`.codex-tmp/handoff-smoothness-2026-08-24/` and intentionally remain outside
Git; this document is the durable interpretation of that local evidence.

### 19 — wider continuous overlap

- Hypothesis: experiment 18 made the representation change continuous, but its
  live layer still held full ownership until `0.78V`. Begin that source-alpha
  handoff at the shared proxy start, `0.68V`, and extend the proxy closer to the
  player so it is already opaque before the longer overlap begins.
- Change under test: preserve every shared `RingWorldHandoff` field and derive
  two legacy-only points in the 1.21.1 shaders. The proxy begins at
  `2 * proxyStart - liveStart`, or `0.58V`, and reaches full opacity at the
  shared `proxyStart`, `0.68V`. Live alpha, tone convergence, and the reveal
  floor then run from `proxyStart` through the unchanged shared live endpoint,
  `1.02V`. At a 16-chunk profile this creates an `87.04`-block continuous
  live/proxy overlap, versus experiment 18's `61.44` blocks.
- Architectural value: this answers the visual problem by changing only the
  backport compositor. Common profile constants, shader field meanings,
  protocol data, Atlas bytes, and loader adapters remain unchanged, which
  keeps later mainline merges mechanical.
- Result: promoted as the final visual checkpoint. No reviewed 6-, 12-, 16-,
  or 28-chunk noon/dusk/night/rain view contains a hard cutoff. All fourteen
  Fabric/NeoForge cells reached their full requested X radius and recorded zero
  settled handoff frames over 50 ms.

At the representative 16-chunk noon pose, the wider overlap improved every
targeted Fabric diagnostic relative to experiment 18:

| Diagnostic | Experiment 18 | Experiment 19 | Reduction |
| --- | ---: | ---: | ---: |
| Residual mean | `5.8932` | `5.4971` | `6.72%` |
| Residual p95 | `25.3204` | `23.4616` | `7.34%` |
| Water RGB-L1 mean | `6.0072` | `4.9491` | `17.61%` |
| Water gradients >=60 | `0.6525%` | `0.3213%` | `50.76%` |

Across the full seven-cell matrix, Fabric improved its aggregate residual
mean/p95 by `5.17%/5.89%`, water mean by `7.94%`, and high-gradient fraction
by `19.81%`. NeoForge improved the same measures by
`4.85%/5.72%`, `8.48%`, and `12.32%`. The 12-chunk Fabric cell was the
largest consistent win: `10.48%` lower residual mean, `10.99%` lower p95,
`22.21%` lower water mean, and `49.28%` lower high-gradient fraction.

The settled Experiment 19 runtime matrix is:

| Cell | Fabric max / >50 ms | NeoForge max / >50 ms |
| --- | ---: | ---: |
| 6 chunks, noon | `26.13 ms / 0` | `24.61 ms / 0` |
| 12 chunks, noon | `30.68 ms / 0` | `28.32 ms / 0` |
| 16 chunks, noon | `29.11 ms / 0` | `31.96 ms / 0` |
| 16 chunks, dusk | `31.14 ms / 0` | `36.25 ms / 0` |
| 16 chunks, night | `33.26 ms / 0` | `33.00 ms / 0` |
| 16 chunks, rain | `33.42 ms / 0` | `32.75 ms / 0` |
| 28 chunks, noon | `48.36 ms / 0` | `48.21 ms / 0` |

All radii were exact (`+N/-N`). Excluding rain timing differences, the two
loaders' handoff images have a mean absolute channel difference of only
`0.3346/255`, and `78.72%` of pixels are exactly identical. The wider overlap
therefore did not introduce a loader-specific compositor path.

Experiment 19 evidence:

- raw cells: `19-wider-continuous-overlap/matrix/`;
- Experiment 18/19 matrix: `comparisons/experiment-18-vs-19-matrix-handoff.png`;
- transition crops: `comparisons/experiment-18-vs-19-transition-crops.png`;
- amplified differences:
  `comparisons/experiment-18-vs-19-transition-difference-4x.png`;
- loader matrix: `comparisons/experiment-19-loader-matrix-handoff.png`;
- cross-loader differences:
  `comparisons/experiment-19-cross-loader-difference-6x.png`;
- reproducible values: `comparisons/experiment-19-matrix-metrics.json`.

#### Retained Fabulous checkpoint

The final explicit-Fabulous 16-chunk noon rerun retained the Experiment 19
image rather than relying on an options-file default. Its source is the
settled visual checkpoint before the later streaming-floor hardening; the
current-source qualification status is recorded separately below.

At the exact loader-matched handoff pose:

- full-frame absolute channel mean is `0.541314/255`, with p99 `7`;
- 3x3-blurred luma correlation is `0.999930369`;
- the upper-frame comparison is byte-exact;
- the targeted water ROI has channel mean `0.849011` and p99 `10`;
- Fabric residual mean/p95 are `5.505207/23.451816`, with water RGB-L1
  `5.026742` and high-gradient fraction `0.003114`;
- NeoForge residual mean/p95 are `5.519027/23.485483`, with water RGB-L1
  `5.040435` and high-gradient fraction `0.003114`;
- the radial-up captures are byte-exact.

Evidence paths, relative to the research root, are:

- raw captures:
  `19-wider-continuous-overlap/fabulous/{fabric,neoforge}/vd16-noon-final/`;
- contact sheet: `comparisons/final-fabulous-loader-contact-sheet.png`;
- exact hashes and metrics: `comparisons/final-fabulous-loader-metrics.json`.

### Final hardening — exact dynamic proxy envelope

The live terrain shader reconstructs a proxy-like tone, but experiment 19's
first implementation omitted two dynamic factors already applied to the real
pre-terrain proxy: the weather contribution and progressive-Atlas generation
fog. These factors do **not** change the proxy geometry's source alpha or the
`0.58V` to `0.68V` coverage ramp. Instead, rain can reduce the textured terrain
contribution to zero, and generation fog moves that contribution toward the
fog colour. Without the same envelope, live fragments could converge toward a
textured proxy tone that the actual proxy was not contributing, creating a
broad false band.

The retained fix is one backport-only scalar uniform,
`RingWorldLegacyProxyRevealScale`. `RingSurfaceTextureRenderer` publishes the
exact same-frame value used by the real proxy:

```text
clamp(weatherAlpha, 0, 1) * (1 - clamp(generationFog, 0, 1))
```

The scalar multiplies only the reconstructed live terrain contribution toward
the proxy colour. It does not alter live fragment alpha, proxy geometry alpha,
the shared render profile, network payload, saved Atlas, or mainline shader
contract. It resets to zero at the head of every active RingWorld sky attempt,
before every vanilla early return, so lava, powder snow, blindness, darkness,
or another blocked-sky path cannot leave live terrain converging toward a
terrain contribution that was not drawn. Outside an active RingWorld shader
path, vanilla fragment alpha is preserved exactly.

The Atlas UI fixture was strengthened to pause one real generation at roughly
25%, move to a calculated handoff pose, prove a complete `+6/-6` client radius,
wait for the GPU texture morph, and capture the incomplete surface before
resuming the original completion/revision/disconnect workflow. The observed
exact envelope values were:

| Loader | Visible completion | Generation fog | Reveal scale |
| --- | ---: | ---: | ---: |
| Fabric | `0.35942268` | `0.66001886` | `0.33998114` |
| NeoForge | `0.36621094` | `0.65044135` | `0.34955865` |

Both values match `1 - generationFog` to floating-point precision. The partial
handoff's maximum adjacent-row RGB-L1 jump is `20.43` on Fabric and `21.44` on
NeoForge, far below the fail-closed threshold of `75`, with no row above `25`.
Its 10–90% convergence spans `65` and `63` pixels respectively: intentionally
broad and diffuse, not a cutoff. The blurred cross-loader correlation is
`0.99901`.

Exact-envelope 16-chunk rain captures publish a zero terrain-contribution
scale on both loaders while retaining the same alpha coverage. They improve
every fixed-image diagnostic relative to the earlier rain captures. Fabric
improves residual mean/p95 by `8.07%/8.94%`, water mean by
`11.44%`, and high-gradient fraction by `14.54%`; NeoForge improves those
measures by `10.98%/11.06%`, `17.65%`, and `35.69%`.

The retained source also passed these integration gates on both loaders:

- ordinary and Fabulous 16-chunk noon projection, with a full radius,
  terrain-contribution scale `1.0`, and zero settled frames over 50 ms;
- exact-envelope 16-chunk rain projection, terrain-contribution scale `0.0`
  with unchanged alpha coverage;
- complete production natural seam and both textured-rim visual captures;
- far/near curved block/entity capture, checking that the temporary terrain
  blend state does not leak into later object layers;
- production Overworld -> Nether -> Overworld -> End -> Overworld transfer,
  normal save/disconnect, raw client-state teardown, and same-process reopen
  with all `65,536` Atlas cells.

Additional visual-analysis evidence:

- rain before/after: `comparisons/exact-envelope-rain-before-after-contact.png`;
- partial-Atlas loader pair:
  `comparisons/exact-envelope-atlas-partial-handoff-loader-contact.png`;
- partial convergence profiles:
  `comparisons/exact-envelope-atlas-partial-handoff-profiles.png`;
- exact values and hashes: `comparisons/exact-envelope-visual-metrics.json`;
- lifecycle logs:
  `19-wider-continuous-overlap/production-lifecycle/{fabric,neoforge}/`;
- Fabulous captures:
  `19-wider-continuous-overlap/fabulous/{fabric,neoforge}/vd16-noon/`.

### Blend-only foliage history fixture

Opaque water is not the most demanding alpha-state case. Cutout foliage can
expose ordering, sprite-alpha, stale-state, and camera-history defects even
when a static water crop looks smooth. The retained shared fixture therefore
builds seven persistent azalea/flowering-azalea planes at intrinsic distances
`184, 192, 200, 208, 216, 232, 248` blocks. With `V=256`, every plane lies in
Experiment 19's `0.68V` to `1.02V` continuous overlap. Their Atlas-derived ray
centres are `[81, 79, 77, 75, 73, 69, 65]`.

The fixture uses a fresh copy of the immutable `16,384 x 256` production
world, exact world hash `c4f99d1076b39de3`, sample step `8`, and all `65,536`
Atlas cells. It fixes the framebuffer at `1280x720`, FOV `70`, Fancy graphics,
no clouds, and a hidden HUD. It waits for a complete quiescent Atlas before
deriving the final pitch, creates exactly `7,508` persistent foliage blocks
with fourteen edge sentinels, waits for the full finite-band drawable window,
and records `streamingWindowComplete=true` at every settled capture.

The camera crosses the fixture and returns in 144 natural quarter-block steps.
Eight images bind the two histories and an empty reference:

- negative-Z, centre A, and a two-frame centre-A control;
- positive-Z, centre B, and a two-frame centre-B control;
- two centre images after removing the fixture.

The acceptance mask is derived independently for each history from foliage
chroma, temporal stability, and difference from the empty reference, then uses
`presentA || presentB`. The union is important: foliage missing in only one
history becomes a large residual instead of disappearing from the mask. The
blocked-sky probe also proves a `1 -> 0 -> 1` terrain-contribution envelope
without changing vanilla alpha.

This is intentionally a **blend-only** fixture. Foliage placement and removal
do not change the Atlas texture or its revision: Fabric remained at revision
`132`, NeoForge at `129`, from fixture-ready through all captures and the
clear. That is useful isolation, not live-Atlas-update evidence. The test asks
whether identical proxy pixels and changed live cutout geometry composite
without direction-dependent residue.

The final dual-loader results are:

- both loaders pass all eight captures, exact geometry, full `+16/-16`, and
  `streamingWindowComplete=true`;
- both union masks contain `8,138` pixels with bounding box
  `x=560..719`, `y=295..375` (`160x81`);
- the mask PNG is byte-exact across loaders, SHA-256
  `8dc3ddcfbdd68f1a1122f5c015259e3fce6eb8004e5ea0e26d732cdd563eea6c`;
- the history-residual PNG is byte-exact across loaders, SHA-256
  `62fa6d4e2bb61d89cdfdd120d905b5389bc7fd31315d5798d38e4bb958ebecb9`;
- each loader's masked history residual has mean `0`, p99 `0`, and no pixel
  above RGB-L1 `24`;
- cross-loader centre A/control/B/control pixels are exact on the mask;
  negative/positive means are at most `0.000369` RGB-L1 with maximum `1`, and
  empty-reference means are at most `0.019907`, p99 `1`, maximum `5`;
- Fabric sampled 1,366 motion frames with one over 50 ms; NeoForge sampled
  1,364 with none over 50 ms.

Evidence is under
`19-wider-continuous-overlap/foliage-motion/blend-only/{fabric,neoforge}/`.
Each loader directory contains the eight raw captures, derived mask and
residual, and `latest.log`.

### Initial-streaming Atlas coverage floor

Experiment 19's fixed profile assumes that live terrain is actually drawable
where its alpha begins to fall. Initial network delivery and section
compilation can violate that assumption transiently. This is not a reason to
restore the removed dynamic availability profile: moving `V` also moves
detail, fog, clouds, and the entire handoff every time a chunk arrives.

The current backport instead publishes a separate two-value uniform through
the pure `RingStreamingProxyCoverage` policy:

| Drawable finite-band window | Published span | Effect |
| --- | --- | --- |
| incomplete | `(0, 0)` | Atlas proxy is opaque at every positive intrinsic distance |
| complete | `(V, V)` | step occurs only at `V`, where Experiment 19 is already opaque; exact no-op |

The proxy shader takes the maximum of this coverage floor and the unchanged
`0.58V -> 0.68V` Experiment 19 alpha. Thus the emergency state can prevent a
sky hole without changing the settled colour curve, Atlas, wire format, or
common render profile.

Completeness is defined over Minecraft 1.21.1's real two-dimensional
`ChunkTrackingView.isInViewDistance` shape, intersected with RingWorld's finite
drawable Z band via `!geometry.isExteriorChunkZ(chunkZ)`. The finite-band
intersection is essential: a 256-block-wide ring intentionally has no real
chunks across much of a 16-chunk vanilla square. Requiring those exterior
chunks left the emergency underlay permanently enabled. The foliage fixture's
200-tick readiness telemetry isolated this defect: pose, full X radius,
rendered sections, and all 7,508 fixture blocks were ready while only the
unbounded window flag remained false.

The first transition to complete requires all of the following:

1. every in-view, non-exterior chunk is present; and
2. `levelRenderer.hasRenderedAllSections()` is true;
3. every non-air, non-exterior section in the exact current
   `LevelRenderer.visibleSections` list that intersects the proxy's non-opaque
   region has a compiled buffer; and
4. the same exact `LevelChunk` identities are observed on two distinct client
   ticks.

After that first proof, an ordinary dirty-section rebuild retains its existing
vertex buffer and does not flash the emergency Atlas through foliage or water.
The exact-section bridge runs after section compilation and fails closed for an
empty visible list, an active captured frustum, a missing chunk, an invalid
section index, or a required non-air `UNCOMPILED` section. Air-only sections
and finite-band exterior traversal placeholders are deliberately ignored.

The network proof is cached by level, camera chunk, effective radius,
loaded-chunk count, and the complete required `LevelChunk` identity array. The
full finite-band scan runs at most once per game tick. A balanced unload/load
cannot inherit proof merely because the count stayed constant. An adjacent
one-chunk chart move may retain coverage only when the old/new intersection has
identical chunk ownership and the new fringe lies wholly behind the already
opaque proxy; its new ownership must then be confirmed on the next tick.
Level, non-adjacent chart, radius, or chunk-identity changes invalidate
immediately. Session teardown clears all proof and observed identities.

Two simpler designs were rejected:

- contiguous `+X/-X` radii miss absent side and diagonal chunks, so they can
  declare coverage while a visible hole remains elsewhere in the frustum;
- `ClientChunkCache.hasChunk()` alone proves receipt, not that the first
  section compile is drawable. A proposed one-chunk fade at the minimum X edge
  therefore still had temporal and two-dimensional false-pass cases.

The earlier locally archived `streaming-fallback` pair was superseded for two
independent reasons. It mixed a rainy capture into a coverage test, so the
weather envelope zeroed the textured Atlas terrain contribution even though
geometry alpha was unchanged. It also predated the finite-band correction and
counted intentionally nonexistent exterior-Z chunks as missing. Those images
were useful diagnostics, but neither was valid qualification evidence. The
archive paths below now contain only the final clear-noon, finite-band runs.

The final post-compile early-streaming gate passes on both loaders. Each
copied production world acknowledged format 3, received all `65,536` Atlas
cells, fixed Fabulous graphics at 1,280x720 with clouds and HUD off, and
captured at clear noon before full-radius delivery. Both arm markers bind:

- requested and effective radius `16` chunks, but contiguous loaded X only
  `+2/-3` on Fabric and `+3/-4` on NeoForge;
- `incompleteWindow=true`, `renderedAllSections=false`, and respectively `485`
  and `469` missing chunks in the finite drawable tracking window;
- `missingNonXChunk=true`, with first missing chunk `(240,-6)`, proving the
  fixture did not rely only on an X-edge gap;
- fixed Experiment 19 proxy onset `148.48000000000002` blocks, target distances
  `32.0` and `48.0`, and emergency fallback span exactly `(0.0,0.0)`;
- time `6000`, rain `0.0`, thunder `0.0`, FOV `70`, and identical centred pose,
  pitch, and surface height.

Fabric's 16 captured frames averaged `8.88658125 ms`, peaked at `16.9252 ms`,
and had none over 50 ms. NeoForge's 14 frames averaged `9.340464286 ms`, peaked
at `19.406 ms`, and likewise had none over 50 ms. Raw pixel identity is not
expected here: the gate intentionally captures while the loaders have
different transient live-section compilation histories. The exact markers,
clear weather, fixed pose, and opaque emergency Atlas coverage are the
cross-loader contract.

After taking the screenshot, the fixture remains in-world and fails closed
until streaming drains. Fabric completed about 36 seconds after arming and
NeoForge about 12 seconds after arming. Both final drain markers bind
`missingChunks=0`, `renderedAllSections=true`,
`streamingWindowComplete=true`, and `stableTicks=20`, followed by
`result=true`, normal disconnect, and save. This post-capture drain makes the
early screenshot evidence compatible with clean teardown without allowing the
capture itself to slip forward into a settled state.

Final evidence, relative to the research root:

- `19-wider-continuous-overlap/streaming-fallback/final-post-compile/fabric/`
  — PNG SHA-256
  `10d8517ad97f8ad405e7551c843d028fff2d6e0b71ab08dfe69d30f76fba342c`, log
  SHA-256 `ec18becd5ed8328ec4e8f871dbe24088bb083b5b13e895e4a0ecfb7ad8d07844`;
- `19-wider-continuous-overlap/streaming-fallback/final-post-compile/neoforge/`
  — PNG SHA-256
  `16bd5f4af19d4f7c4dca402f3ee4dc4e4e27ce98e8de33dd740cfb51f8038365`, log
  SHA-256 `e3fbb5a39f37244e520ae74bf08f27497ac8b135112f644b78f2322c38e33556`.

The final settled projection/current-source matrix remains a separate result.
The foliage fixture below proves the settled `(V,V)` no-op and controlled safe
fallback during motion, while this gate proves the sole permitted
incomplete-window `(0,0)` state.

### Final post-compile acceptance matrix

The implementation was frozen after the exact-current-section and finite-band
fixes. No further blend-curve adjustment was needed. All evidence below lives
under `19-wider-continuous-overlap/`:

- `foliage-motion/final-post-compile/{fabric,neoforge}/`: each loader created
  and cleared exactly `7,508` foliage blocks and wrote nine fixed 1,280x720
  captures. The independent foliage union mask contains `8,138` pixels over
  `160x81`; history residual mean and p99 are both zero with no pixel above the
  RGB-L1 threshold. Fabric measured 2,676 frames at `8.515762 ms` average,
  max `25.9828 ms`, and zero over 50 ms; NeoForge measured 2,668 at
  `8.543401 ms`, max `39.505 ms`, and zero over 50 ms.
- Exact current visible-section readiness can conservatively return to the
  safe Atlas floor while newly exposed sections compile. That is accepted only
  when the proxy actually drew and the published span is exactly `(0,0)`.
  Fabric recorded 22 such frames with a longest run of 10; NeoForge recorded
  33 with a longest run of 14. Both are below the fixed 64-frame, 32-run, and
  2%-of-samples limits; both recorded `unsafeStreamingCoverageFrames=0`.
- `partial-atlas/final-post-compile/{fabric,neoforge}/`: both GUI-scale-4 Atlas
  fixtures captured the progressive handoff at about 37.1% visible completion
  with proxy reveal about 35.7%, then completed all 4,096 cells, processed two
  ordered live revisions, saved, disconnected normally, and cleared session
  state.
- `fabulous/final-post-compile/vd16-noon/{fabric,neoforge}/`: settled Fabulous
  production captures pass at 16 chunks. Handoff windows average `8.509294 ms`
  on Fabric and `8.544994 ms` on NeoForge, with zero frames over 50 ms. Fresh
  Fabric/NeoForge handoff mean absolute channel difference is `0.287597/255`,
  p99 `4`, and downsampled luma correlation `0.999982336`.
- The fresh 1,280x720 settled images remain equivalent to the retained
  854x480 Experiment 19 captures after common-size resampling: blurred luma
  correlation is `0.999816627` for Fabric and `0.999827175` for NeoForge. This
  closes the permitted comparison without spending the single optional
  corrective iteration.
- `fabulous/final-post-compile/vd28-rain/{fabric,neoforge}/`: the combined
  weather/high-distance gate passes. Actual handoff windows average
  `11.209960 ms` Fabric and `9.812556 ms` NeoForge, with zero frames over
  50 ms; radial windows likewise have zero over 50 ms.
- `lifecycle/final-post-compile/{fabric,neoforge}/`: both copied production
  worlds pass Overworld/Nether/Overworld/End/Overworld, normal save and
  disconnect, raw client-session teardown, and same-process reopen with the
  original `16,384x256` fingerprint and complete Atlas.

The decisive renderer contract is also automatic: both loader modules pass
all `352` unit/parameterized cases, the Fabric source/runtime contract checks,
strict dependency inventory (`369` components and `748` artifacts), and clean
builds. Runtime PNGs and logs remain ignored evidence, while this document is
the durable index.

### Known limitations and non-claims

- The complete Atlas remains a low-detail visual LOD. Continuous composition
  hides the ownership boundary; it does not add live block texture, entities,
  collision, or simulation to the distant proxy.
- Very short six-chunk views can still show a broad low-detail/blur halo even
  without a hard cutoff. Rain remains a separate visible atmospheric curtain.
- The incomplete-window `(0,0)` floor is deliberately conservative and can
  place opaque Atlas beneath transparent live content during initial loading.
  It is temporary coverage insurance, not a new settled look.
- The per-tick proof bounds scan cost and transient state. A balanced
  unload/load within one tick can be observed on the next tick rather than the
  same render invocation.
- The foliage fixture is synthetic, fixed-pose, Fancy-graphics evidence. Its
  unchanged Atlas revision means it does not qualify live Atlas revision
  propagation, natural biome foliage variety, resource packs, shader mods, or
  broad third-party compatibility.
- Fixed-region metrics detect regressions but are not perceptual scores. Full
  images, motion, weather, rims, partial Atlas, lifecycle, and both loaders
  remain separate gates.
- Moving the ring and replacement star after `compileSections` means
  NeoForge's `AFTER_SKY` third-party render callbacks run before RingWorld's
  ring/star draw on 1.21.1. This ordering is a documented compatibility limit;
  returning the proxy to sky tail would overwrite the depth-mask-disabled star
  and lose exact current-section readiness.

## Findings to carry into future backports and mainline

The following findings are portable:

1. Configure an automated client's view distance before login, or explicitly
   broadcast the changed client information. Changing only the local slider
   after joining creates misleading server/client radius disagreement.
2. Treat renderer readiness and network-radius completeness as separate gates.
   Contiguous `loadedX=+N/-N` is useful settled telemetry but is not a complete
   coverage proof. Inspect the engine's actual two-dimensional tracking shape,
   intersect it with the world's finite drawable domain, and require a first
   rendered-all proof. Either `hasChunk()` or `hasRenderedAllSections()` alone
   can pass at the wrong time.
3. Verify that live and proxy paths use the same lightmap UV/filter convention
   for each Minecraft version. The correct coordinate is an engine contract,
   not a constant to copy between versions.
4. Inspect the whole representation curve, not only coverage endpoints. On
   1.21.1, proxy reveal was near one half when live fading began while vanilla
   live fog remained weak, creating an opposing reveal trough even under a
   continuous alpha blend.
5. Preserve shared profile-field meanings on the wire and in common code.
   In particular, `RingWorldHandoff.w` remains the shared `proxyFadeEnd`; the
   backport's earlier opaque-underlay endpoint is derived locally by its legacy
   proxy shader.
6. Keep settled visual policy separate from transient streaming insurance.
   Moving the entire handoff profile with chunk availability makes detail,
   haze, clouds, and tone breathe during delivery. A fail-closed underlay that
   becomes a mathematical no-op is easier to reason about and update.
7. Cache an expensive completeness scan at the render/tick boundary, not
   forever. Key it by the level, chart/camera chunk, effective radius, and a
   cheap chunk-set signal; invalidate lifecycle changes immediately and rescan
   stable state on the next tick.
8. Separate colour contribution from geometry alpha in diagnostics. Rain and
   generation fog can zero or fog-colour Atlas terrain without changing proxy
   coverage. A single ambiguous "weather alpha" label leads to the wrong live
   reconstruction and the wrong test assertion.
9. Use reversible, unchanged-Atlas geometry fixtures to isolate compositor
   history, and separate them from live Atlas-revision fixtures. A union of
   independently detected history masks prevents one-sided missing geometry
   from being silently excluded.
10. Time a coverage-dependent proxy after the engine has finalized the section
    list for that frame and immediately before terrain. A global queue-empty
    flag can be true while the replacement view graph is not installed; a scan
    of every possible section is over-conservative. The exact visible draw list
    is the useful ownership boundary.
11. Treat finite-world traversal placeholders as topology scaffolding, not
    missing terrain. Intersect every readiness and streaming scan with the
    drawable domain before using it to hold an emergency underlay open.

The 1.21.1 blend hook, its exact lightmap coordinate, the cutout/tripwire mode,
and the full reveal floor are legacy-pipeline adapters, not automatic mainline
changes. Mainline has native `ChunkVisibility` and different render/lightmap
semantics. Its next useful check is to plot or capture native live visibility
against proxy reveal over the handoff and add the same pre-login/full-radius
fixture assertions. Only if that measurement exposes the same reveal trough
should a bounded reveal adjustment be considered there.

The rejected experiments also narrow future work: deterministic dither remains
high-frequency visible without temporal antialiasing; a small overlap-only
detail-distance advance does not repair the low-frequency reveal trough; and
blue-noise substitution cannot solve a tone mismatch. Any future render-state
adapter must own and restore blend state before loader callbacks observe the
terrain layer.
