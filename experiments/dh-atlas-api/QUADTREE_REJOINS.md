# Distant Horizons quadtree exception on NeoForge rejoin

The DH 3.3.3 and 3.3.4 render trees can throw `LodQuadTree shouldn't be updating renderSections below the expected detail level: [10]` when the camera leaves a zoomed state during a tree traversal. Both versions reproduced the same exception without RingWorld. DH catches it in its timer thread and abandons that update. Both original RingWorld sessions went on to display the detailed ring.

## What happens on join

DH estimates camera zoom from Minecraft's projection. In separate DH-only rejoins with a passive zoom observer, the estimate briefly reached 16× on 26.1.1 and 13.01× on 26.3 before returning to 1×. The player used no spyglass in either of those runs.

The render thread updates a shared zoom value and cone. The DH client tick timer reads them as it walks the tree. A parent section can request more detail under zoom and send the traversal into its children. If the render thread resets zoom before the child is checked, the child can be two levels finer than DH now expects. DH allows this difference while zoom is active. At 1× it throws. Both distributed jars contain the same `LodQuadTree.class` (SHA-256 `80e4d086f258d1d7a0da909b0aa77c4a41faccaf8e416df498e4f5cc622a6089`). [Parent/child decision](https://gitlab.com/jeseibel/distant-horizons-core/-/blob/65cbb1ac6233e9cd9219c10302d2f847d8a46a08/core/src/main/java/com/seibel/distanthorizons/core/render/QuadTree/LodQuadTree.java#L618-645) · [Render-thread zoom update](https://gitlab.com/jeseibel/distant-horizons-core/-/blob/65cbb1ac6233e9cd9219c10302d2f847d8a46a08/core/src/main/java/com/seibel/distanthorizons/core/render/CameraZoom.java#L87-173)

The original exceptions preceded RingWorld's first visible placeholder by two seconds on 26.1.1 and one second on 26.3. The DH bridge starts its cache reader only after that placeholder is ready. DH updates its camera zoom before firing the cancellable render event that RingWorld subscribes to. The original logs contain no parent/child snapshots, so attribution of those two specific events comes from matching the stack, timing and diagnostic reproductions.

## Ordinary DH-only rejoins · 23 November 2026

Six fresh client processes per version opened copies of the same warm save and cache. NeoForge listed just Minecraft, NeoForge and Distant Horizons. A launcher agent hid the window and requested a clean exit after twelve seconds in the world. It did not instrument DH, reposition the player, zoom the camera or pause the timer. On 26.1.1 the sixth rejoin logged the original level-10 exception. On 26.3 none of the six ordinary rejoins logged it. These small counts establish reproducibility and do not estimate a player’s likelihood of seeing it.

## Controlled reproduction

Use an integrated NeoForge client, a copy of the warm save at (2, 120, 0.5), 12 vanilla chunks, a DH radius of 256 chunks and Medium horizontal quality. Keep DH's zoom-quality adjustment enabled. Observe the client tick thread after it decides to subdivide a zoomed parent whose children would be too fine at 1×. Let the render thread continue and resume the traversal after zoom returns to 1×. A vanilla spyglass release provides a repeatable transition when the startup zoom is too brief. For a debugger reproduction, suspend just the DH timer thread at the entrance to `onDetailLevelTooLow` after it chooses the level-9 parent centred at (−3840, 256) under magnification. Release the spyglass, let the render thread reset magnification to 1×, then resume the timer. The diagnostic pause changes scheduling only. It never writes the zoom, terrain, expected level or section coordinates.

| Minecraft / NeoForge | DH | Error with zoom quality enabled | Same test with zoom quality disabled |
|---|---|---|---|
| 26.1.1 / 26.1.1.15-beta | 3.3.3 | Twice, once during join before any spyglass use and once on release | Zero |
| 26.3 / 26.3.0.7-beta | 3.3.4 | Once on spyglass release | Zero |

In the 26.1.1 join reproduction the level-9 parent at (−3840, 256) requested level 7 at 6.49× zoom. Its level-8 child at (−3968, 128) subsequently requested level 10 at 1×. In the 26.3 reproduction the same parent requested level 8 at 3.43× and its child requested level 10 at 1×. Both clients continued running after the exception.

## Remediation

An upstream fix would publish one immutable camera-zoom snapshot, including the cone direction and width, and use that same snapshot for every decision in one quadtree traversal. A later camera update would take effect on the next traversal.

For affected players, setting `increaseQualityWhenZoomedIn = false` in the client’s `config/DistantHorizons.toml` prevented the controlled failure on both versions. This gives up extra DH detail while zoomed. RingWorld leaves that choice to the player.
