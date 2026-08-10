# Visual polish checkpoint — 2026-08-02

This is issue-#96 safe-small dual-loader evidence for public `main` commit
`4b41eb6668433705ec6a029d7d05740a14b29c62` (runtime code unchanged from
`8f7bc80831964ce00a315b2f52daf1f4349c7eff`). Runtime worlds, logs, and
screenshots remain in ignored local paths.

> Historical terrain evidence: these captures used the alpha legacy
> terrain-noise mapping. Issue #149 intentionally preserves that mapping for
> existing saves but gives fresh format-3 worlds a corrected annular mapping;
> fresh cardinal and visual review must replace this evidence before release.

## Fabric safe-small matrix

A fresh 2,048×416 world and complete 13,312-cell atlas were generated for each
6/12/28-chunk run. Tangent live/LOD and radial-up captures completed at every
distance; the ring remained closed, aligned to real terrain, and visible
through the radial view. The fixed dither handoff remained broad rather than a
single hard fog line. No proxy was present over the inspected local rim.

| View distance | Average frame | Maximum frame | Frames over 50 ms |
| --- | ---: | ---: | ---: |
| 6 chunks | 8.636 ms | 38.924 ms | 0 |
| 12 chunks | 8.646 ms | 134.982 ms | 1 |
| 28 chunks | 11.915 ms | 84.787 ms | 2 |

The 12/28 maxima were isolated during the full automated generation/gameplay
sequence, not sustained movement stalls. The 6-chunk run also completed the
entire topology/gameplay/rim harness: two natural wraps, canonical ownership,
projectile/vehicle/AI/fluid/explosion probes, and both finite-rim assertions
passed. All three runs captured noon, dusk, midnight, and rain before the
complete-atlas handoff views.

Environment: macOS 26.5.2 (25F84), Apple M2 eight-core GPU, built-in
2560×1664 Retina display, Minecraft 26.1.2, Java 25. Fabric development jar
SHA-256: `f456ea9d17af35b61481b51e637a259f2c1a1deee36383a84402f7227f83a0d3`.

## NeoForge safe-small matrix

The same complete 2,048×416 saved world was copied into an isolated NeoForge
run for each view distance. The player started at the saved gameplay pose
`(2040, 120, 0.5)`, each run received all 13,312 atlas cells, captured the
tangent, derived-pitch handoff, and 651.899-block-diameter radial-up views,
passed the NeoForge evidence verifier, and exited normally.

| View distance | Tangent average | Handoff average | Radial average | Maximum frame | Frames over 50 ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| 6 chunks | 8.446 ms | 8.330 ms | 8.328 ms | 23.429 ms | 0 |
| 12 chunks | 9.904 ms | 9.044 ms | 8.352 ms | 35.948 ms | 0 |
| 28 chunks | 11.253 ms | 9.355 ms | 8.584 ms | 38.281 ms | 0 |

Visual inspection found the ring closed and aligned at all three distances,
with the same broad real-terrain/proxy transition and no local rim overlay.
This completes the safe-small cross-loader matrix.

## Production dual-loader checkpoint

The 16,384×256 format-6 fixture exposed a weakness in the old projection
harness: it inherited the saved player's last position, which happened to be
at `z=124.7` beside the maximum rim. The shared runner now requests the same
server-authoritative spectator pose `(4096, 120, 0.5)` on both loaders before
waiting for chunks and measuring the three views. This makes the evidence
independent of save history without changing the source world.

At 12 chunks, both centered runs received the complete 65,536-cell atlas and
passed tangent, handoff, and radial-up capture verification:

| Loader | Tangent average | Handoff average | Radial average | Maximum frame | Frames over 50 ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Fabric | 8.953 ms | 8.417 ms | 8.373 ms | 147.336 ms | 2 |
| NeoForge | 8.869 ms | 8.419 ms | 8.357 ms | 77.054 ms | 3 |

The settled handoff and radial stages had no frames over 50 ms. The isolated
spikes occurred while the tangent stage was still receiving chunk/atlas
revisions. Visual inspection found loader-identical alignment and transition
shape. Straight up, the production band is intentionally narrow: its
256-block width is only about 4.9% of the 5,215-block ring diameter.

The server-authoritative production seam and both five-block textured-rim
captures also passed on Fabric and NeoForge at 12 chunks. Natural seam motion
used 0.25-block steps without a position or camera pop, and the proxy remained
excluded from both local walls. Object/block-entity, changed-gamma/night-
vision, broader weather/exposure, and motion review remain open.

The exact-current NeoForge environment rerun also corrected the projection
harness's dusk timestamp from 14,008 (already night) to the actual warm dusk
keyframe at tick 12,000. Environment labels must match the fixed-sun tone
cycle before their screenshots count as visual evidence.

## Curved rigid objects and fresh NeoForge login

Fresh 2,048×416 Fabric and NeoForge worlds rendered the same curved fixture
from X=0.5 and X=32.5. Visual inspection confirmed the chest, lectern/book,
sign, bed, ender chest, shulker box, banner, copper golem, dropped item, boat,
cow, and zombie remain seated on the curved stone-brick surface as the camera
approaches. The two loaders' final captures match.

The two graphical tasks must run sequentially as
`:runCurvedObjectCaptureClient` and
`:neoforge:runCurvedObjectCaptureClient`. An unqualified Gradle task selector
matches both projects and can start both clients together; one Fabric launch
stalled during resource loading on macOS until the tasks were separated.

The first NeoForge retry exposed an invalid empty-sky PASS: its settings packet
was sent by `PlayerLoggedInEvent`, after NeoForge flushed the initial chunk
buffer. Canonical edge chunks therefore reached a fresh client before ring
geometry and were rejected. A NeoForge-only `PlayerList` adapter now queues
settings directly after vanilla's play-login packet and before all world
packets. The rerun rendered the complete fixture. The verifier now also waits
for the expected client block entities and fails after 1,200 ticks, so this
regression cannot pass on screenshots alone.

The later headless-admission audit preserves that ordinary-login ordering but
adds a cancellable NeoForge method-head guard before vanilla creates the play
listener or begins buffering play packets.
Explicit atlas prewarm now rejects the connection without queuing settings,
starting handshake state, or exposing atlas metadata; the logged-in event is a
fallback only.

Changed-gamma/night-vision, close cloud-height, real-player proximity, and
motion review remain open.
