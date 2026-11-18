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
