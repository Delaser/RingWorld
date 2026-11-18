# RingWorld 26.x performance and crash review, 18 November 2026

Reviewed the 26.x release source on main at 653b9b5a, the version-specific overrides, both loader adapters, the client Atlas and GPU preparation paths, server streaming and persistence, world generation, seam queries, mixins and the version build matrix. The separate 1.21.1 backport has its own cache implementation. The measurements below cover 26.3.

## Large Atlas join work (#300)

The old reader buffered compressed file input, then read individual cell fields directly from the gzip inflater. This also ran on the client thread when checking a saved cache at join. Added buffering on the decompressed side and checked identity, dimensions and revision before allocating the arrays or traversing the cells. A stale client cache can now be discarded after reading its header. A matching cache still receives full validation, including the end-of-stream check.

Measured three reads per configuration on the same synthetic complete 32,768 × 512 cache on a Mac, Java 25, with a 1,300 MiB heap. It used a repeating height and colour pattern and was about 1.2 MiB compressed. Other development workloads were running. The old reader took 24.7–66.9 s (median 27.3 s), the buffered reader 0.87–0.95 s (median 0.89 s). Rejecting a different revision took 0.1–20.6 ms. These timings cover cache lookup only. A cache miss still requires a replacement Atlas and a transfer.

A second three-read comparison used a saved, complete Medium Atlas from release qualification, with all 4,194,304 real cells present. Median read time fell from 6.95 s to 0.34 s. All six reads returned revision zero and terrain fingerprint 7076686259763628007.

Server-side, a full Large transfer previously created 65,536 tile coordinate objects at join and indexed every one in a hash set for each player. The initial transfer now advances a row-major cursor. The server encodes each tile when due. Changes to already-sent tiles are queued once, while outstanding initial tiles pick up their latest values on first send. A client with a complete current cache starts at the end of the cursor.

The next measurement for #300 is a cold and warm join on constrained client hardware with matched server and client timestamps. The render thread still copies a full Atlas before worker preparation. Client cache writes can take another snapshot, and final mesh/texture uploads run on the render path. A Large snapshot holds approximately 192 MiB of cell arrays. Measure these stages and peak live memory before changing snapshot ownership or upload scheduling. The reporter's timeout remains unverified.

## Creeper explosions (#301)

On a local Minecraft 26.3 dedicated server with NeoForge 26.3.0.100, released RingWorld 1.4 destroyed a test platform with an ignited creeper. Adding Lithium 0.26.3 triggered the reporter's zero-target failure in the exposure-ray constructor redirect. Lithium reuses the raycast context in that method. RingWorld now chooses the periodic copy of the explosion center at method entry, once per entity.

With that class substituted into the published JAR, the same blast destroyed the platform both with and without Lithium, and both servers stopped normally. In an additional 16,384-block ring test, a creeper at X=16,381.5 damaged a pig at X=2.5 across the seam. The released RingWorld-only control and the corrected Lithium run both took its health from 10 to 4.9166665. These runs isolate the reported mod pair. The reporter's full modpack and the separate Sable startup issue were outside these fixtures.

## Other paths reviewed

- Simulation visits every tracked Overworld entity each tick to fold out-of-range positions back onto the ring. Cost scales with loaded entities. Profile a mob-heavy world before replacing that pass. Vehicles, knockback, teleports and entities moved by other mods all depend on the end-of-tick guarantee.
- The world generator already caches immutable cylindrical coordinate tables, bounds chunk admission and limits capture work per tick. The server checkpoint writer already moves compression off the server thread. The join measurements point to client decoding and allocation as higher priorities for this pass.
- Periodic entity queries take the vanilla path when their bounding box stays inside the circumference. The extra query windows and deduplication apply at the seam. Both loaders use the shared Atlas stream and cache reader, so the fixes can be maintained in one place.

## Validation

The final source built and passed the shared Java suites on Fabric and NeoForge at each representative Minecraft version. Each loader ran 519 tests on 26.1.2, 519 on 26.2 and 522 on 26.3, for 3,120 executions and zero failures. The repository Python suite ran 509 tests with four platform skips. New cases cover stale headers versus truncated matching caches, initial tile order, dirty tiles during a transfer and clients joining with a complete cache.

The final 26.3 NeoForge JAR built using the repository's pinned dependencies also passed the plain blast and Lithium cross-seam damage probes on the reported 26.3.0.100 runtime. An exploratory build using 26.3.0.100 as a compile-time dependency failed while recompiling NeoForm's generated HolderSet source with the default development plugin. The runtime checks used the official installed 26.3.0.100 server. These were source suites and targeted runtime checks.

## Large-ring cache and tile-stream retest after PR #308

The baseline was `fd22257`. Both server and client used the same candidate in each run. Each server started from a separate filesystem clone of the same fully generated Large world (32,768 × 512 blocks, 16,777,216 samples). The game was Minecraft 26.3 with NeoForge 26.3.0.7-beta on an M2 Mac, using loopback multiplayer, a 1 GiB client heap, two JVM-visible client processors, 12-chunk render distance and an 854 × 480 client window. Each cold client started with an empty cache. Each warm client reopened the complete cache written by its own cold run.

Elapsed times start at the client’s connection message. Login uses the server’s player-admission message. First surface and full detail use client renderer publication messages. There was one run per condition and the logs resolve whole seconds.

| Build | Cache | Login | Cache check finished | First Atlas surface | Full-detail Atlas |
| --- | --- | ---: | ---: | ---: | ---: |
| Before | Cold | 1 s | 2 s | 5 s | 413 s |
| Before | Warm | 1 s | 3 s | 6 s | 6 s |
| After | Cold | 2 s | 2 s | 5 s | 414 s |
| After | Warm | 1 s | 3 s | 6 s | 6 s |

On the complete test ring all 65,536 tiles contained terrain. At eight tiles per tick and 20 TPS, transmitting that initial set takes about 410 seconds. The targeted savings apply to partial rings and to edits made during transfer. Both cold and both warm joins succeeded. The reported laptop and network timeout in #300 remain unverified.

| Production-code probe | Before | After |
| --- | ---: | ---: |
| Allocated 1,024-sample pages after reopening a one-cell Large Atlas | 16,384 | 1 |
| Tiles sent for that one-cell Atlas | 65,536 | 1 |
| Empty tile payload bytes before transport compression | 201,454,590 | 0 |
| Packet carrying an edit to the first tile just sent | 65,537 | 2 |
| Equal-revision save fork with height 103 tries to reuse a cache containing height 90 | Accepted | Rejected |

The edit probe kept a second populated tile pending so replay priority was exercised independently of the empty-tile skip. Fabric and NeoForge builds and Java tests passed on 26.1, 26.1.1, 26.1.2, 26.2 and 26.3. The Python suite passed with four platform skips. This is targeted validation for #300.

## Progressive Atlas texture uploads, frame times and gameplay regressions

Compared the merged #309 code (`f0497ae`) with the candidate on local NeoForge 26.3 multiplayer. Both servers used separate copies of the same fully generated 32,768 × 512 ring. Each client used a 1 GiB heap, two JVM-visible processors, a 12-chunk view distance and a muted 854 × 480 window on the same M2 Mac. Cold clients began empty. Each warm join reused the complete cache written by its own cold client. The client and server processes were stopped after measurement.

| Measurement | Cold before | Cold after | Warm before | Warm after |
| --- | ---: | ---: | ---: | ---: |
| Seconds from connect to first Atlas surface | 6 | 5 | 6 | 6 |
| Seconds from connect to full detail | 414 | 413 | 6 | 6 |
| Batches using the full-upload path | 398 | 2 | 1 | 1 |
| Batches using the changed-row path | 0 | 397 | 0 | 0 |
| MiB of texture data submitted, including mips | 16981.32 | 738.3 | 42.67 | 42.67 |
| Atlas upload batches over 16.67 ms | 288 | 2 | 1 | 1 |
| Longest Atlas upload (ms) | 40.5 | 28.9 | 33.5 | 33.2 |
| Whole frames sampled | 13428 | 13662 | 229 | 286 |
| Whole-frame p95 (ms) | 34.8 | 34.7 | 21.9 | 23.3 |
| Whole-frame p99 (ms) | 42.6 | 34.8 | 75.3 | 88.3 |
| Whole frames over 50 ms | 23 | 15 | 5 | 6 |
| Whole frames over 100 ms | 4 | 2 | 1 | 2 |
| Longest whole frame (ms) | 300.7 | 165.1 | 187.2 | 139.6 |

Upload stalls measure render-thread time inside an Atlas transfer. Whole-frame timings come from the same test-only frame-end timestamp recorder on both builds, from the first in-world frames through the wall-clock second when the first full-detail mesh appeared. Join milestones use one-second logs. Bytes are texture payloads submitted to the graphics API, including mip levels, before any driver-internal work. One cold and one warm join per build were sampled. Local validation jobs overlapped part of the baseline cold transfer. The reporter’s hardware and network remain untested.

The final cold update changed every row when the client switched to its detailed mesh. Including the two initial textures, three transfers carried full-texture payloads. Cold texture traffic fell 95.7%. The stream still took nearly seven minutes to reach full detail.

Progressive updates reuse the preceding crossfade target and write adjacent changed scanlines in each mip. Initial targets receive a full upload. The render thread finishes each transfer before publishing the new texture. The previous texture remains available for the crossfade and the idle spare is released.

| Regression probe | Before | After |
| --- | --- | --- |
| Restore filesystem access after a failed checkpoint, edit terrain, then flush | Three flushes failed and no file was written | First flush saved the newer height and cleared the dirty flag |
| Queue 4,096 unavailable cells and one loaded overflow edit for 4,000 ticks | 256,000 lookups and the edit was never visited | 4,352 lookups, the loaded edit was visited once on tick one |
| Scheduled tick at X=0, queried through X=2048 | Pending and due-this-tick queries both missed | Both queries found the canonical tick |
| Equal top and side material changes on an exposed rim at constant height | 6,144 red vertices would change on rebuild, but the rebuild was skipped | The same colour change triggered a rebuild |

Fabric dedicated server probes verified tick aliases and rim mesh refresh on 26.1.2, 26.2 and 26.3. Fabric and NeoForge JAR builds and Java tests passed for all five 26.x targets. The Python suite ran 509 tests with four skips.

A NeoForge 26.3 GPU readback checked all 10,752 bytes across three mip levels after changing 448 texels in disjoint row ranges. All four colour channels matched the CPU reference, including untouched rows. On the warm reconnect both builds restored all 16,777,216 cached cells, streamed zero tiles, and uploaded one complete texture. The two short warm samples showed mixed whole-frame timings.
