# Atlas visual baseline and mesh-fidelity review — 2026-08-01

This document records the first production-layout evidence for GitHub issue
`#66`. It supplements, rather than replaces, the approved safe-small profile-4
review in `VISUAL_HANDOFF_REVIEW_2026-08-01.md`.

## Fixture and capture contract

The fixture is a copied 26.1.2 world with the production 16,384×256 layout and
a complete 65,536-cell format-5 terrain atlas. The camera remains at the saved
player position over jungle/forest hills, water, and a visible width rim. Every
run normalizes the disposable copy to clear noon, applies the requested client
view distance, and captures three views:

1. horizontal tangent/+X, proving the complete arch survives the level far
   plane;
2. the geometry-derived live/proxy handoff pitch at the nominal chunk edge;
3. radial-up, proving the full diameter remains visible.

Each view settles for 100 client ticks. The harness records rendered-frame
average, maximum, sample count, and frames exceeding 50 ms. Screenshots and
logs remain ignored local evidence under `run-production-projection/`.
The same copied fixture can be normalized to `noon`, `dusk`, `night`, or
`rain`; non-noon screenshot names carry the environment identifier.

## Profile-4 baseline

The old production mesh was capped at 512 circumference segments: one height
vertex every 32 blocks even though the atlas samples every eight blocks.

| View distance | Handoff pitch | Tangent avg | Handoff avg | Radial avg |
| ---: | ---: | ---: | ---: | ---: |
| 6 chunks | 23.219° | 8.639 ms | 8.567 ms | 8.656 ms |
| 12 chunks | 11.195° | 9.510 ms | 8.426 ms | 8.636 ms |
| 28 chunks | -0.906° | 13.257 ms | 10.148 ms | 8.688 ms |

The 12- and 28-chunk transitions were already broadly convincing. At six
chunks the proxy is close enough that the 32-block triangles visibly flatten
and shift hills immediately beyond the live-terrain dither. Colour and
lightmap exposure remain comparable; the dominant defect is geometry, not a
fog-colour band.

## Profile-5 change and result

Visual profile 5 raises the circumference mesh cap from 512 to 2,048. The
default production layout now retains the atlas's full eight-block height
spacing around the ring. The texture, atlas format, network protocol,
live/proxy fade, haze, and real-chunk authority are unchanged.

| View distance | Tangent avg | Handoff avg | Radial avg | Handoff >50 ms |
| ---: | ---: | ---: | ---: | ---: |
| 6 chunks | 8.862 ms | 8.590 ms | 8.639 ms | 0 |
| 12 chunks | 9.608 ms | 8.513 ms | 8.603 ms | 0 |
| 28 chunks | 11.643 ms | 10.954 ms | 8.658 ms | 0 |

The higher-resolution silhouette follows sampled ridges and waterways more
closely, most visibly in the six-chunk handoff, without a material frame-time
regression in the settled handoff or radial stages. Tangent settling still
contains occasional chunk-upload spikes at high view distance, as it did in
the profile-4 baseline; it is reported separately from the stable handoff
interval rather than hidden in one aggregate number.

The production mesh grows from 98,304 vertices/2,359,296 estimated bytes to
393,216 vertices/9,437,184 estimated bytes. Its GPU texture remains
5,592,384 estimated bytes including mips. A 28-chunk development-client sample
observed about 1.56 GiB RSS while chunks and the mesh were active; this is a
single operational observation, not a controlled heap comparison, so the
bounded 6.75 MiB mesh increase is the reliable change-specific figure.

## Acceptance result

- A fresh safe-small profile-5 run completed the full gameplay/rim harness at
  12 chunks, including noon/dusk/night/rain captures, a 100% atlas, both wraps,
  and every representative gameplay probe. Reusing that complete save for
  6/12/28 projection captures produced clean tangent/handoff/radial results;
  the proxy remained absent over both local real rims.
- The complete production copy also passed deterministic dusk, night, and rain
  capture runs at 12 chunks. Live and proxy terrain dim together through the
  shared lightmap; rain changes both toward the same cool exposure rather than
  leaving a bright atlas behind the live chunks.
- The production fixture places water through the right side of the reviewed
  handoff. Its real translucent surface remains in front while the atlas
  supplies only the distant opaque continuation, as designed.
- The six-chunk production view remains an obvious low-detail approximation
  when inspected closely, because an eight-block surface atlas cannot reproduce
  individual trees, transparent layers, or block silhouettes. It no longer
  exposes the old 32-block mesh facets, and it does not form a hard band or
  opaque fog wall. Further adaptive fidelity/resource tiers belong to issue
  `#69`; they are not a reason to conceal the current boundary with more fog.

These results satisfy issue `#66`'s visual/profile gate. Captures are evidence
for this implementation and do not turn the atlas into authoritative geometry.
