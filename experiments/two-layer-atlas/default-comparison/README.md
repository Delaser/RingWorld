# Two-layer Atlas on the Large ring

[Compare seven matched camera views](index.html). Select either image for the full 2560 × 1440 capture. The two single-version buttons keep the camera framing fixed when switching between builds.

## Setup

The control is published RingWorld 1.4.0 for NeoForge on Minecraft 26.3, Modrinth version `UdZZwm0o`, SHA-256 `c0cba972efd6ce9fa428372cb3e1569b81331533d538bac3302a9560321e5d01`. Each client connected to a dedicated server running the matching JAR. Each server used a copy of the same fully generated 32,768 × 512 Overworld, seed 202610093. The camera ran in spectator mode at clear noon with clouds off, Medium LOD, 12 chunks and no HUD. All three matched runs disabled VSync and refreshed the test client's input timer to prevent Minecraft's inactivity cap. The Mac has an Apple M2 and 16 GB of shared memory.

The release-to-dev comparison includes intervening terrain, water and material changes. For the performance comparison, both dev runs used the same fully populated two-layer Atlas at the default 2× width. Its 337,535 split columns were read from all 65,536 saved chunks using the server's production column sampler. A separate replay started from a legacy cache and exercised ordinary chunk-load sampling and multiplayer delivery.

The capture waits for the target player pose, loaded terrain and a ready Atlas. Each of the seven scene samples contains 150 consecutive rendered frames after settling. Three additional moves alternate between X 2303.5 and X 2304.5 at Y 120, Z 12.5, crossing the former trial's paging boundary in both directions. Crossing samples start with the teleport request and include world streaming and settling. Frame times include both Minecraft and RingWorld on this machine. On cold-cache dev runs the isolated server accelerated initial tile delivery, then returned to 20 TPS before the first camera pose. The timings exclude initial join and generation.

## Storage and drawing

The two unused high bytes of the existing top and side colour fields carry each span. On this save the compressed Atlas grew from 13,573,323 to 14,650,751 bytes (+7.9%). Encoded tiles retain the same uncompressed length. The old local HTTP page service and manifest are gone.

The complete Medium mesh contains 6,979,584 ordinary vertices and 3,517,488 additional layer vertices. The final renderer submitted between 3,570 and 230,406 additional vertices across the seven viewpoints. The rest stay in the GPU buffer. Separate GPU buffers hold at most 147,456 whole-triangle vertices (3.375 MiB) each. Uploading one per render pass spreads replacement mesh uploads while the previous complete mesh remains displayed. For 30 seconds after a completed cross-fade, the previous complete texture is available for changed-row uploads. This avoids allocating and transferring 11.2 million texels on every small edit.

[Frame measurements](measurements.json)

## Results

Frame time in milliseconds. Lower is better. Each scene contributes 150 frames. The crossing samples include the move and settling frames.

| Build | Scene median | Scene p95 | Crossing median | Crossing p95 |
| --- | ---: | ---: | ---: | ---: |
| Published 1.4 | 15.75 | 24.52 | 17.06 | 26.33 |
| Dev before the second performance pass | 20.27 | 32.22 | 22.59 | 32.43 |
| Final dev | 19.00 | 29.74 | 18.18 | 28.93 |

| Scene | 1.4 median | First dev median | Final dev median | Final dev p95 |
| --- | ---: | ---: | ---: | ---: |
| Jungle canopy | 14.41 | 22.92 | 20.08 | 29.57 |
| Spruce forest | 16.19 | 23.69 | 23.14 | 30.04 |
| Deep-water bay | 14.55 | 23.67 | 24.72 | 34.08 |
| Shallow reef | 19.99 | 19.87 | 19.88 | 31.60 |
| Across the wrap seam | 16.09 | 16.26 | 13.05 | 21.15 |
| Above the outer rim | 16.72 | 16.68 | 16.50 | 22.43 |
| Looking up the far arc | 9.67 | 16.91 | 11.47 | 18.49 |

## What changed in the captures

In the spruce view, look past the live trees toward the far wall. The single dark-green silhouette in 1.4 breaks up into crowns and visible ground in dev. In the shallow reef, the bright cyan arc across the water is much less distinct. The reef comparison includes the earlier water handoff work. All seven release and dev images use identical recorded positions, yaw and pitch.

## Follow-up performance pass

| Measured on the Large ring | First two-layer dev | Final dev |
| --- | ---: | ---: |
| Longest individual mesh upload | 189.28 ms | 13.78 ms |
| Longest individual Atlas texture upload | 72.24 ms | 14.86 ms |
| Longest frame during the three boundary crossings | 145.67 ms | 32.75 ms |
| Mesh builds during the three boundary crossings | 0 | 0 |

Updating slices of a single large GPU allocation still incurred a costly first write on this driver. The final mesh uses independently allocated triangle pages. The texture transfer now compares each mip with its target, resumes at the next changed row and uploads at most 524,288 texels per render pass. Both the previous texture and mesh remain usable until the entire replacement is ready.

With mesh paging alone, the seven-scene median was 17.31 ms and the worst texture upload was 68.45 ms. With texture budgeting, the median was 19.00 ms and that upload peak fell to 14.86 ms. These are individual native runs on one machine. The final build still took 3.25 ms longer per median frame than published 1.4. The deep-water bay showed the largest remaining difference. Whole frames can still exceed the individual transfer times.

The five supported 26.x versions passed Fabric and NeoForge build and test tasks. The source-parity suite passed all 14 checks. Native captures used NeoForge 26.3. Final tested development JAR SHA-256: `80cd1cf0d7d65e73d2ab5cb442d6c12c4a6b4691e28b3f5ff966ef4b61ca4ce5`.
