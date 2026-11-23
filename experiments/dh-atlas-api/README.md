# Distant Horizons surface handoff trial

This client add-on targets the RingWorld development renderer at c483799, Fabric on Minecraft 26.2, Java 25, and stock Distant Horizons 3.3.3. It can be removed from the client's mods directory after testing. Use a copy of the test world. The public DH terrain-data API supplies surface samples and RingWorld projects them onto its curved mesh.

## Handoff

1. In the RingWorld Overworld, cancel the stock flat DH draw using its before-render event. Let the existing RingWorld renderer publish its placeholder, including the seed preview when one has arrived.
2. Start a single background reader once the placeholder is on the GPU. It reads canonical chunk coordinates via getAllTerrainDataAtChunkPos. A successful request can still have missing columns. Count a chunk only when all 256 columns have usable spans.
3. Publish the privately built Atlas after every canonical chunk and every target sample is present. Keep the current GPU mesh visible during the normal staged upload. Discard the reader and its partial result when the renderer clears.
4. Retry missing chunks with a fresh DH read cache. Release decoded sections every 128 chunk checks and check the first column before requesting all 256. The bridge reads existing DH data. Exploration, generation or a separate import must supply missing data. A cache that never covers the entire ring keeps displaying the placeholder.

## Build

Run build_trial.py with Java 25 on PATH or set JAVA_HOME. Supply --classpath-file with the current RingWorld client development classpath (including compiled client/common classes, one entry per line), --loader-jar with Fabric Loader, --dh-jar with the stock Distant Horizons jar and --output with the desired client add-on jar path. Keep the regular RingWorld and DH jars installed alongside the resulting add-on.

## Trial coverage

Two singleplayer tests used a 2,048 × 128 block ring with 1,024 canonical chunks. One started with a complete DH cache. The other held at 584 readable chunks until the test harness submitted the saved world chunks to DH. Both tests checked actual curved-mesh draw calls across the handoff. The harness chunk feeder, camera automation and screenshots are separate from the add-on.

This trial maps the highest visible block or fluid span and its biome colour. Stacked geometry, bridge undersides, live edits after the swap, dedicated multiplayer and NeoForge remain open. Fully air columns are conservatively treated as unavailable. The usual RingWorld server Atlas still runs. Stock DH can continue its own housekeeping even with the flat draw cancelled.

## Reproduce the Large test

Build with `--large-probe` to include the camera and frame recorder. Use this jar only in a disposable integrated Large world. It moves the player in spectator mode and closes the client ten seconds after the first detailed draw. The regular build excludes these hooks.

Start with an empty DH cache. The recorded run began with just the Minecraft 26.2 level and world generator metadata, a fresh 32,768 × 512 Large layout, and native Atlas pregeneration enabled. No regions or Atlas databases were imported. By default the camera stays at one viewpoint. To exercise travel, add the JVM property `-Dtrial.tour=true`. That mode waits at the first viewpoint for 60 seconds, then visits both sides of the circumference in 192-block steps with a two-second dwell. Only Minecraft and DH can load or generate terrain. The probe never requests chunk tickets or writes DH data.

The client writes `large-frame-times.csv` in its game directory. Run `python3 proof/frame_times.py PATH_TO_CSV` from this directory to summarize end-to-end intervals between curved-ring render calls. Column three changes from zero to one on the first draw of the detailed mesh. The interval immediately following it includes work submitted after that first draw.


## Large cold-cache result

On 23 November 2026, a Fabric 26.2 integrated client on an Apple M2 with 16 GiB RAM ran against stock DH 3.3.3. The game had a 4 GiB heap, five available JVM processors, a 12-chunk Minecraft view, a 256-chunk DH radius, and no Iris or Sodium. RingWorld used its default adaptive generation rate. The stationary camera stayed at X 96.5, Y 160, Z −127.5. Both the RingWorld terrain data and the DH database began empty. No test code submitted chunks to either generator.

The native Atlas finished at 01:57:02. DH supplied its last 12 chunks by 01:57:28, covering all 16,777,216 columns. The first detailed draw occurred **1 h 27 min 29 s** after the first measured placeholder frame, about eight seconds after the final DH read. The GPU used the Medium display mesh, with 4,194,304 sampled cells. All 331,669 measured ring render calls issued a draw, including the 330,867 before the first detailed frame.

| Stationary phase | Frames | Median | P95 | P99 | Worst |
|---|---:|---:|---:|---:|---:|
| DH filling, after 5 s of startup to the reported final read | 329,943 | 15.00 ms | 22.16 ms | 26.61 ms | 678.22 ms |
| Preparing the detailed draw after the final read | 559 | 13.58 ms | 20.56 ms | 27.90 ms | 108.79 ms |
| One second either side of the first detailed draw | 120 | 14.75 ms | 25.00 ms | 35.56 ms | 108.79 ms |
| Settled, from two seconds after the draw to probe exit | 663 | 12.15 ms | 17.68 ms | 21.03 ms | 83.02 ms |

The 108.79 ms frame ended 0.42 s before the first detailed draw. RingWorld timed 87.98 ms inside the wall texture GPU upload, within an 89.50 ms resource update. The interval ending on the first detailed draw itself was 35.56 ms. The table measures intervals between ring render calls, including time spent elsewhere in the game. The final-read boundary was estimated from the log timestamps. The handoff window overlaps preparation, and the settled sample covers only eight seconds. The 678.22 ms generation outlier has no confirmed cause.

An initial cold Large run with the original pass-wide reader cache reproduced one DH `LodQuadTree` exception about expected detail level 9 at 00:16:45, about one second after the fixture joined. It preceded both the first visible placeholder and the first camera tour. That run subsequently stressed world generation with two-second camera hops, encountered DH low-memory warnings, and ended before a swap. The restarted stationary run used the bounded reader cache, finished from empty, and reported **zero** such exceptions. These two runs establish that the DH error can recur at startup. They do not isolate its cause.

The experiment stays opt-in. The next performance target is the wall texture upload before the completed mesh is published.
