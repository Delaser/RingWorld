# Large ring trial

The corrected player-built bridge has no additional sky pixels in the five fixed test views. The narrow slit occurred where a fine side face cut across a corner of the old curved mesh. The final bright pixel lay between two exposed vertical faces. The baker now splits the join at each original mesh corner and overlaps the ends and heights of exposed side faces by 1/32 of a block. The openings beneath the bridge remain visible.

| Sky pixels in the fixed crop | View 1 | View 2 | View 3 | View 4 | View 5 |
| --- | ---: | ---: | ---: | ---: | ---: |
| One-layer control | 0 | 0 | 0 | 0 | 9 |
| Previous two-layer trial | 2 | 4 | 4 | 6 | 11 |
| Corrected two-layer trial | 0 | 0 | 0 | 0 | 9 |

The nine pixels in the last view match the control at the same coordinates and lie at the edge of the crop. [Bridge comparison](bridge-gap.png) · [Per-view overlap check](bridge-coverage.json)

## World and client

All 65,536 saved chunks were present on the copied Large ring, 32,768 × 512 blocks. At Medium detail the baker read 4,194,304 columns at two-block spacing. It found 357,570 with a second occupied span. The mesh replaces 170,700 coarse terrain quads and adds 3,679,601 faces across the full ring. Of 512 pages, 98 are empty. The run used a local dedicated NeoForge 26.3 server and a separate native Minecraft client on the same 16 GB Apple M2. The client ran at 2560 × 1440, Medium detail and 12 chunks. The showcase server and source save were untouched.

The regular saved Atlas occupies 13.57 MB. The extra independently downloadable pages and manifest occupy 51.42 MB in decimal units. The server does not send that whole addition to every player. Each group contains 16 nearby pages. GPU geometry for the entire base mesh is retained. The dense spruce site adds 1,417,236 vertices to the 6,979,584-vertex base mesh. The jungle site adds 143,568. [Verified page totals](page-audit.json) · [Reproduction instructions](REPLAY.md)

## Joins and movement

This test used a fresh client directory for the cold join and a new client process using the same cache for the reconnect. The world, loader, view distance and ten camera poses matched. Times run from the Minecraft connection log to the server login or the first complete, detailed GPU mesh. Logs have one-second resolution.

| Measured on localhost | One layer | Corrected two layers |
| --- | ---: | ---: |
| Cold player login | 1 s | 1 s |
| Complete detailed Atlas, cold | 425 s | 416 s |
| Warm player login | 1 s | 1 s |
| Complete detailed Atlas, warm | 7 s | 11 s |
| Extra layer data through first detailed window | 0 | 459,176 bytes |
| Extra layer data over the cold route | 0 | 3,742,157 bytes |
| Extra layer data over the warm route | 0 | 0 |

The ordinary Atlas still streamed 65,536 tiles on a cold join in both builds. One trial per condition cannot establish a nine-second speedup or a stable four-second warm penalty. These join tests used normal 20 TPS and a loopback sidecar for page transfers. A real multiplayer protocol, WAN latency and concurrent joins are untested.

The movement probe crossed between X=2303.5 and 2304.5 four times at the same altitude and facing. Each sample includes six seconds of every rendered frame. Both servers ran at 20 TPS for the measured route. During preloading only, the dedicated movement rigs accelerated tile delivery and then restored 20 TPS before the first pose.

| Worst frame | Crossing 1 | Crossing 2 | Crossing 3 | Crossing 4 |
| --- | ---: | ---: | ---: | ---: |
| One layer | 69.1 ms | 40.4 ms | 41.3 ms | 57.8 ms |
| Two layers | 97.7 ms | 77.6 ms | 81.2 ms | 71.6 ms |

There was one whole-mesh GPU upload on the baseline route and five on the two-layer route. Candidate mesh uploads took 39.5–68.6 ms on the render thread. Changing a 256-block page group currently invalidates the combined mesh, so moving back across the same boundary also uploads the base ring. The next change should give each page its own GPU buffer and move its coverage flag into a small shader mask. That would keep the base mesh resident and let neighbouring groups reuse their overlapping pages. [Frame captures and timings](measurements.json) · [Mesh packing and GPU upload events](movement-mesh.json) · [One-layer movement frames](movement-one-layer.csv) · [Two-layer movement frames](movement-two-layers.csv)

## Drawing cost at rest

The same client alternated both meshes without changing world, settings, camera or process. Each site contributed 400 measured frames per condition across two passes. The jungle used one/two/two/one order and the spruce forest used the reverse. Every pass was checked against its uploaded vertex count.

| Native client frame time | One-layer median | Two-layer median | One-layer P95 | Two-layer P95 |
| --- | ---: | ---: | ---: | ---: |
| Jungle | 33.37 ms | 33.46 ms | 34.81 ms | 34.81 ms |
| Spruce forest | 33.34 ms | 33.33 ms | 38.69 ms | 37.54 ms |

Medians and P95 were close on the fixed cameras. The worst stationary spruce frame was 223.18 ms with layers and 88.91 ms with one layer. The layer pass also logged terrain mesh repacking and GPU uploads after the teleport. The ten-pose runs in separate processes showed more variation. On the cold route P95 went from 37.74 to 46.39 ms. The final cold run overlapped unrelated disk compression during part of that route. The warm-route P95 went from 34.16 to 34.79 ms. The same-client comparison and the separate boundary-crossing probe are the stronger measurements of drawing cost and movement cost respectively.

[Large ring visual comparison](large-ring.webp) · [Same-client pass results](paired-summary.json) · [Same-client frames](paired-frames.csv) · [All ten-pose cold and warm frames](large-frames.csv)

## Generation and storage

Starting with the existing saved Large ring, the complete offline retrofit took **44.0 seconds**. A top-only scan of the same saved chunks averaged 32.4 s. Scanning 33 material levels averaged 36.7 s, followed by 2.0 s to choose spans and colours, 3.7 s to pack the mesh, and 1.6 s to prepare downloadable pages. The order was top-only, full, full, top-only. Both used three region-reader processes on the same disk. The final replay regenerated the exact manifest digest used by the measured client.

A separate instrumented NeoForge server read 64 columns per resident chunk across the Large ring. It warmed up on 32 chunks and measured 256 different chunks in alternating order. The existing surface selector averaged 0.0125 ms per chunk. Including the second-span scan averaged 0.0195 ms, an additional 0.0070 ms per chunk. P95 rose from 0.0564 to 0.0718 ms. Chunk acquisition, colour sampling, mesh packing and network transfer are outside this microbenchmark. Fresh Minecraft terrain generation was not rerun. [Offline generation measurements](generation.json) · [Live server samples](live-sample-cost.csv)

## Integration status and follow-up

The owner accepted this work for integration on 20 November 2026. The water fixes apply in normal play. The second terrain layer remains an opt-in Medium LOD experiment using a local fixture or a baked page manifest.

The repeatable travel hitch remains open. A page-group change uploads the combined terrain mesh, including the base ring. The next performance check is the same one-block boundary crossing after giving pages independent GPU buffers, followed by the five bridge views. The numbers above were measured at 1× with the original 16-page window. The current 2× default has visual coverage on this Large ring. Its generation, join and frame-time costs have not been rebenchmarked as a set.

Both Fabric and NeoForge Gradle builds passed for 26.1, 26.1.1, 26.1.2, 26.2 and 26.3. The native visual, frame and multiplayer measurements in this report used NeoForge 26.3.
