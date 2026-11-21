# Small Atlas edits at the deep-water bay

This replay compares merged development commit `7bdefd8` (PR #321) with the sparse-upload change. A change to four surface blocks used to upload the entire Large ring texture once its previous cross-fade texture had expired. The new renderer seeds the replacement texture with a GPU copy, patches the changed rows, then starts the usual 750 ms cross-fade. Identical pixels require no texture transfer. First display, a resize and broad changes still use the budgeted full upload.

## Spruce to bay

Both builds started in fresh processes with copies of the same fully generated 32,768 × 512 ring, seed 202610093, a completed warm Atlas cache and a private dedicated server. The client used NeoForge 26.3 on an Apple M2 with OpenGL, Medium LOD, a 12-chunk render distance, clear noon, clouds and VSync off. The requested window was 1280 × 720 logical pixels. The two builds ran sequentially.

From the jungle, teleport to the spruce viewpoint (7080.5, 108, 0.5, yaw 90°, pitch 12°). Wait until the client has rendered the terrain and released the spare texture. Replace the four existing surface blocks at X 7064–7065 and Z 0–1 with red, then white, then red concrete on successive trips. As soon as the client receives the edited Atlas tile (441, 16), teleport to the bay (3361.5, 109, −27.5, yaw −100°, pitch 18°). The client coalesces the edit and publishes it at the bay. Other naturally refreshed rows can travel with that edit.

| Build | Trip | Uploaded texels | CPU → GPU (MiB) | Upload frames | Upload calls (ms) | GPU copy call (ms) | Arrival median (ms) | Arrival p95 (ms) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Before | 1 | 11,184,800 | 42.667 | 22 | 50.87 | 0.00 | 17.36 | 29.90 |
| Before | 2 | 11,184,800 | 42.667 | 22 | 49.32 | 0.00 | 16.71 | 31.98 |
| Before | 3 | 11,184,800 | 42.667 | 22 | 43.22 | 0.00 | 17.00 | 32.32 |
| After | 1 | 48,640 | 0.186 | 1 | 1.52 | 5.23 | 17.57 | 29.24 |
| After | 2 | 48,640 | 0.186 | 1 | 1.29 | 5.93 | 16.74 | 25.23 |
| After | 3 | 48,640 | 0.186 | 1 | 1.26 | 3.96 | 15.54 | 22.91 |

Upload and copy times measure wall time spent in the render-thread calls, including any driver waits. They do not measure asynchronous GPU execution. The GPU still copies 42.67 MiB locally for each new sparse target. The CPU sends only the changed RGBA rows. The previous texture stays visible through staging and the spare still expires after 30 seconds.

Arrival covers the first eight seconds from each teleport, including vanilla section work. A second window covers 22 consecutive frames from the first transfer or copy call, matching the duration in frames of the old full upload. Late samples are five to twelve seconds after the 150-frame settled sample. The following summaries use the median of the three visit statistics.

| Whole-frame measurement (ms) | Before | After |
|---|---:|---:|
| Arrival median | 17.00 | 16.74 |
| Arrival p95 | 31.98 | 25.23 |
| 22-frame transfer window median | 17.80 | 14.76 |
| 22-frame transfer window p95 | 28.79 | 22.71 |
| Late median | 14.64 | 13.50 |

Uploads started 6.7–8.6 seconds after the teleport. In several visits, the first eight seconds ended before the upload began. Changes in the whole arrival window include chunk loading and ordinary frame-to-frame variation. Across the three visits, the median render-thread time spent issuing the copy and row transfers was 6.75 ms, down from 49.32 ms.

## Validation

After the last timed frame, the probe read the displayed OpenGL texture and compared every RGBA row hash with the worker-built image. Both builds matched across all 10 mip levels, 1,023 rows and 11,184,800 texels. Earlier prototypes using a shader draw and a texture-subimage copy stalled for 221 ms and 166 ms respectively on first use. The final implementation uses a framebuffer blit on OpenGL and restores the caller’s framebuffer, scissor and sRGB state. Its first copy in this fresh client process took 9.12 ms in spruce before the timed bay visits. Driver caches were not cleared.

Fabric and NeoForge builds and unit tests passed on 26.1, 26.1.1, 26.1.2, 26.2 and 26.3. All 14 source-parity checks passed. Native timing and GPU readback used NeoForge 26.3.

[Per-trip measurements](sparse-edits.csv) · [Individual timed frames](sparse-edit-frames.csv.gz)
