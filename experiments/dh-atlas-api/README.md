# Distant Horizons surface handoff verification

The optional DH integration is part of the normal RingWorld client on all supported 26.x targets. This directory contains test fixtures and the original Large-ring measurements. Player installation and limitations are in the [compatibility guide](../../docs/COMPATIBILITY.md#distant-horizons).

## Handoff

1. In the RingWorld Overworld, cancel the stock flat DH draw using its before-render event. Let the existing RingWorld renderer publish its placeholder, including the seed preview when one has arrived.
2. Start a single background reader once the placeholder is on the GPU. It reads canonical chunk coordinates via getAllTerrainDataAtChunkPos. A successful request can still have missing columns. Count a chunk only when all 256 columns have usable spans.
3. Publish the privately built Atlas after every canonical chunk and every target sample is present. Keep the current GPU mesh visible during the normal staged upload. Discard the reader and its partial result when the renderer clears.
4. Retry missing chunks with a fresh DH read cache. Release decoded sections every 128 chunk checks and check the first column before requesting all 256. The bridge reads existing DH data. Exploration, generation or a separate import must supply missing data. A cache that never covers the entire ring keeps displaying the placeholder.

## Run a client probe

Build the QA-only jar with `python3 build_probe.py --classpath-file CLIENT_CLASSPATH --output PROBE.jar` using Java 25. The classpath file lists one dependency per line, including the compiled RingWorld client classes. Install the probe only in a disposable client instance. Use a completed native ring for the control and a populated DH cache for the handoff test. Start with `-Dprobe.expectDh=true` to require a published placeholder followed by a distinct, complete DH Atlas and a capture-ready detailed mesh. Omit that property for the native control. The client logs `RW_DH_PROBE_PASS` and exits on success. Count the RingWorld chat notice in the game log. Expect one on the first join and zero after restarting the client and reopening the same world with the same profile.

## 26.x client runtime matrix · 23 November 2026

Each cell ran twice with the unmodified upstream DH jar on a 2,048 × 128 ring. Both launches had to display the placeholder, read all 1,024 canonical chunks, and reach a capture-ready curved mesh with all 262,144 samples. The first launch used a fresh notice history. The second started a new game process with the same save and player. All ten cells logged one RingWorld notice on first join and zero on rejoin.

| Minecraft | Distant Horizons | Fabric 0.19.5 | NeoForge |
|---|---|---|---|
| 26.1 | [3.3.3](https://modrinth.com/mod/distanthorizons/version/QHxoccsN) | Pass | Pass · 26.1.0.19-beta |
| 26.1.1 | 3.3.3 | Pass | Pass · 26.1.1.15-beta |
| 26.1.2 | 3.3.3 | Pass | Pass · 26.1.2.87 |
| 26.2 | [3.3.3](https://modrinth.com/mod/distanthorizons/version/aAow4a9f) | Pass | Pass · 26.2.0.69 |
| 26.3 | [3.3.4](https://modrinth.com/mod/distanthorizons/version/TGgEbP9A) | Pass | Pass · 26.3.0.7-beta |

The 26.1-family jar declares Minecraft 26.1.0, 26.1.1 and 26.1.2. The published jar loaded unchanged on 26.1. DH's Fabric builds for 26.2 and 26.3 require Fabric Loader 0.19.5 or later. These handoff runs used a prefilled Small-ring DH cache. The cold Large-ring generation measurement below remains specific to Fabric 26.2.

On two of the twenty starts, DH logged a `LodQuadTree` detail-level exception during rejoin. Both occurred on NeoForge, on Minecraft 26.1.1 and 26.3. Both clients subsequently displayed the detailed mesh and shut down normally. The cause of this intermittent DH error remains open.

### Controls and multiplayer

With DH absent, Fabric and NeoForge on 26.1.2, 26.2 and 26.3 each displayed a complete native Atlas and logged zero DH notices. The ten normal client jars include the bridge. DH API classes and the QA fixture are supplied separately.

Dedicated multiplayer passed on 26.2 with Fabric clients on a Fabric server and NeoForge clients on a NeoForge server. Neither server had DH installed. Both clients held the placeholder on an empty cache, then displayed the detail on a seeded rejoin with notice counts of one and zero respectively. A NeoForge client pointed at the Fabric server was rejected during loader negotiation. Cross-loader networking and dedicated-server sessions on the other Minecraft versions remain outside this verification.

To reproduce, use a Small dedicated RingWorld server without DH. Connect with a fresh player notice history and empty client DH cache. Use `-Dprobe.expectPartial=true -Dprobe.expectDh=true` to check that the placeholder remains visible through the first 200 client ticks and check the log for one notice. After clean disconnect, seed a copy of the same ring's completed DH cache in the disposable client's server-data directory. Rejoin as the same player with just `-Dprobe.expectDh=true` and check for the detailed mesh and zero additional notices.

## Trial coverage

Two singleplayer tests used a 2,048 × 128 block ring with 1,024 canonical chunks. One started with a complete DH cache. The other held at 584 readable chunks until the test harness submitted the saved world chunks to DH. Both tests checked actual curved-mesh draw calls across the handoff. The chunk feeder, camera automation and screenshots used for that trial are separate from the shipped mod.

The integration maps the highest visible block or fluid span and its biome colour. Stacked geometry, bridge undersides and live edits after the swap remain open. Fully air columns are conservatively treated as unavailable. The usual RingWorld server Atlas still runs. Stock DH can continue its own housekeeping even with the flat draw cancelled.

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

The next performance target from this run is the wall texture upload before the completed mesh is published.
