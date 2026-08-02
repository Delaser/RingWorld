# Visual polish checkpoint — 2026-08-02

This is issue-#96 safe-small dual-loader evidence for public `main` commit
`4b41eb6668433705ec6a029d7d05740a14b29c62` (runtime code unchanged from
`8f7bc80831964ce00a315b2f52daf1f4349c7eff`). Runtime worlds, logs, and
screenshots remain in ignored local paths.

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
This completes the safe-small cross-loader matrix. Production-size,
object/block-entity, changed-gamma/night-vision, and motion review remain open.
