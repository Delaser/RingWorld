# Minecraft 26.1 visual handoff review — 2026-08-01

This review closes the focused live-terrain/complete-ring visual gate for the
Minecraft 26.1.2 port. It was run from integrated source commit `8f05914`, with
the weather-capture harness change on issue branch
`codex/issue-20-visual-handoff`. Runtime worlds, logs, and screenshots remain
ignored local evidence and are not distribution inputs.

## Result

Visual profile 4 is retained and approved for the port checkpoint. Real chunks
and the atlas surface remain aligned in tangent and radial-up views, the proxy
covers the complete cylinder at ordinary render distance, and the handoff does
not expose a hard sky gap or draw proxy terrain over the local rim.

The atlas is intentionally lower-detail than real chunks. Its eight-block
source sampling, bilinear expansion, relief shading, and mip chain make distant
terrain softer than block geometry. The broad terrain dither can also be seen
as fine grain in a still image at the transition. Those are bounded visual-LOD
limitations, not a second world surface or a topology discontinuity.

A profile-5 experiment replaced the stable interleaved-gradient threshold with
an unordered pixel hash. The result was visibly worse salt-and-pepper noise at
28 chunks, so the code and profile version were restored before integration.
No fade distance, haze value, texture budget, shader ABI, or gameplay behavior
changed as part of that rejected experiment.

## Capture matrix

The safe-small fixture was 2,048×416 with a complete 13,312-cell atlas and a
2,048×416 GPU texture. Each view distance produced tangent and radial-up
complete-ring captures; the six-chunk run also retained the rim capture.

| View distance | Seam samples | Average | Maximum | Frames over 50 ms |
| --- | ---: | ---: | ---: | ---: |
| 6 chunks | 11,315 | 12.389 ms | 112.135 ms | 4 |
| 12 chunks | 3,480 | 8.673 ms | 30.096 ms | 0 |
| 28 chunks | 4,512 | 9.015 ms | 45.996 ms | 0 |

The six-chunk rim interval added 895 samples at 16.686 ms average, 35.669 ms
maximum, and no frame over 50 ms. A final 28-chunk run after adding the weather
probe exercised noon rain, restored and settled clear weather before the
tangent/up comparison, and then completed the full rim probe. Its seam interval
averaged 9.344 ms across 6,733 samples, with a 372.748 ms maximum and 12 frames
over 50 ms while the integrated server was still generating the fresh atlas;
the later rim interval averaged 8.366 ms across 1,786 samples, peaked at
25.501 ms, and had no frame over 50 ms. Startup generation can therefore still
produce isolated long frames. These figures are ordinary-movement evidence,
not a cold-world loading budget.

The production fixture was 16,384×256 with all 65,536 atlas cells present. It
built the capped 4,096×256 texture and 98,304-vertex mesh, then completed both
projection captures with `result=true`. Its level far plane was 1,024 blocks,
while the opposite surface and far rim were about 5,158 and 5,160 blocks away;
the successful images therefore exercise the proxy-only far-depth compression
rather than a raised chunk render distance. The production projection harness
runs at its normal fixed 16-chunk distance and does not emit frame metrics.

## Reviewed visual contract

- Tangent and radial-up views retain the same canonical atlas alignment and
  continuous full loop.
- The proxy is absent from the local interaction area; real cobblestone and
  mossy-cobblestone rim faces and collision remain authoritative.
- Noon, dusk, midnight, and rainy-noon captures use the live Minecraft
  lightmap. Rain is cleared and allowed to settle before the clear handoff
  comparison.
- The fixed sun and global tone shift continue to affect live and proxy
  terrain together. The atlas deliberately does not invent local block light.
- Clouds retain the shared cylindrical transform and the saved-wall-top plus
  eight-block base. This review did not change the cloud shader or its fade.
- Profile 4's 68–98% proxy-opacity span, 78–102% live dither, 52–98% reveal,
  and 4–16% far haze remain the single dimension-aware policy.

## Remaining boundaries

This approval does not turn the atlas into captured block geometry. Distant
buildings, entities, transparent layers, local lamps, and live block edits can
differ from the static surface summary. Gamma, night vision, lightning, and a
close cloud-height shot remain useful manual release checks. Production cold
startup and multiplayer resource pressure are tracked separately; this review
does not replace those gates.
