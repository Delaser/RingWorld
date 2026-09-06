# Atlas stutter investigation and background mesh preparation

The Medium 16,384×256 test ring at current Max has 6,586,368 vertices. Three controlled same-content synchronous mesh rebuilds blocked the render thread for 319, 278 and 275 ms. This change moves mesh construction **and vertex emission/packing** onto the named, serial `RingWorld surface builder` worker. Texture pixels and geometry use the same captured Atlas snapshot, revision, quality and wall parameters. Only GPU upload and resource installation remain on the render thread.

A clear/reload/quality change invalidates the build generation. Queued obsolete jobs skip expensive work; completed obsolete results release their native images and packed vertex storage. Failed builds release owned storage as well. Partial and unchanged-height/side-material revisions retain the existing mesh according to the established refresh policy. A serial worker prevents abandoned jobs from multiplying concurrent native build allocations. This does not reduce the ring's visual detail.

## Verification

- Fabric and NeoForge builds pass all 423 JVM tests each on 26.1.2 and 26.2, using the normal source defaults.
- Python suite: 429 tests, two expected platform skips.
- Fabric 26.2 reopened the complete one-block source test world with the experimental source setting; normal source defaults were restored afterwards.
- Three forced asynchronous refreshes each settled to ready, no pending job, 6,586,368 vertices.
- JFR sampled mesh construction, triangle emission, vertex packing and texture building exclusively on the named surface worker, with no matching render-thread samples in that recording.
- Runtime lifecycle probe caught a pending Max job, selected Low, verified the abandoned job finished exceptionally, and verified Low's 466,944 vertices. Returning to Max settled correctly to 6,586,368 vertices.
- Final framebuffer capture was visually inspected: live world, rim, distant surface and lighting render. Camera was not moved by the test; the owner client remains open on Max.

This is development evidence from the existing test client, not a matched walking benchmark, a NeoForge client smoke, or release qualification.

## Remaining stutter candidates

Repeated updates in the 35-second post-change recording (17:43:47–17:44:11 local time):

| Operation | Observed duration | Interpretation |
|---|---:|---|
| GPU mesh upload | 35–42 ms | Still blocks rendering; entire vertex buffer is replaced. |
| GPU texture upload | 9–22 ms | Still synchronous; separate from mesh preparation. |
| Atlas snapshot copy | 6–26 ms | Must currently capture mutable client data on its owner thread. |
| Complete resource install including release | 53–104 ms | Includes upload/cleanup and any overlapping pause; not additive to rows above. |
| Garbage collection pause | Up to 52 ms | Separate JVM-wide pause; one occurred during a resource update. |

First-load upload was 57 ms, with an 80 ms complete install. Worker mesh/packing can still take hundreds of milliseconds, especially before JIT warmup, but it no longer directly blocks the render thread. Worker allocations can still contribute to GC pressure. The earlier cache compression fix remains active; cache snapshot/submission is now separately timed, but no new long cache-submission event was established by this recording.

Runtime warnings beginning `RingWorld render stall candidate` flag render-side snapshot, cache submission, mesh upload, texture upload, and total resource-update durations of at least 16 ms. The total includes component times; do not sum them. GC is measured with JFR, not attributed to a particular method solely because its wall-clock measurement overlaps the pause. Optional JVM property `ringworld.profileSurfaceBuilds=true` additionally logs worker timing and render stages over 1 ms for diagnostics.

Next performance targets are bounded/incremental GPU uploads and reduced repeated snapshot/scratch allocations. The synchronous cache-load path during world connection also deserves separate startup profiling; it was not established as a periodic walking stall here. Continuous GPU waits in the earlier live profile indicate normal rendering load and do not independently prove a periodic defect.

Local evidence: `logs/medium-ring-stutter/` contains before/after JFR files, JSON events, timing logs, lifecycle probe/results, four build logs and Python results. `runtime-after.log` belongs to the current Fabric client (PID 3887); `async-refresh-results.txt` and `lifecycle-results.txt` retain assertions. Final image: `logs/village-lighting-comparison/run/screenshots/async-mesh-after.png`.
