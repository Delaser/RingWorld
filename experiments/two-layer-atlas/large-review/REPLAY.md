# Replaying the Large two-layer experiment

The measurement save uses Minecraft 26.3, seed 202610093, a 32,768 × 512 ring, a five-block rim, and a complete one-block terrain Atlas. Work on a copy of the save. The trial samples at Medium resolution and only the Medium client mesh accepts these pages. The original benchmark used a 12-chunk effective view distance.

With Python, NumPy and Pillow installed, run from the experiments/two-layer-atlas directory. Set WORLD to the copied save and OUT to a scratch directory.

```sh
ATLAS="$WORLD/dimensions/minecraft/overworld/data/ringworld/terrain-atlas.rwat.gz"
python3 extract_large.py "$WORLD" "$OUT"
python3 large_profiles.py "$OUT" "$ATLAS"
python3 bake_pages.py "$OUT" "$ATLAS" "$OUT/layers.zip" --rim 5
python3 serve_pages.py "$OUT/layers.zip" "$OUT/http"
python3 -m http.server 25810 --bind 127.0.0.1 --directory "$OUT/http"
```

Give the trial client the printed digest as the JVM property `-Dringworld.layerTrialManifest=<digest>`. The dedicated Minecraft server serves the regular world and Atlas. A separate loopback HTTP process serves only the experiment. Each client checks the digest of its downloaded page and caches it under its own game directory. Use a fresh game directory for the cold run and reconnect from that same directory for the warm run.

All 512 pages are generated from saved blocks. Each page covers 64 blocks of circumference and the width between the rims. The benchmark used 16 nearby pages. The current client reserves room for the 2× band at the effective view distance (18 pages at 12 chunks) and moves the group every 256 blocks. Use `/ringworld lod band 1` to reproduce the earlier visible width. The original distant surface still covers the rest of the ring. Pages are immutable, so later block edits require another bake. Low/High, WAN synchronization, incremental edits, and integration with live chunk generation remain outside this experiment.

For a top-only scan control, run `extract_large.py "$WORLD" "$OUT/top-only" --top-only`. This uses the same region decoder, three workers, sample positions and output medium. It reads one material per column. It measures an offline scan of existing chunks. The server normally reuses its saved single-layer Atlas when it starts with a complete world.

The optional [frame observer](probes/Study.java) alternates the renderer at two stationary cameras in the same native process. The [test-only injection](probes/LayerChoice.java) makes the mesh builder omit the trial faces in the control condition. Verify the logged uploaded vertex count for each pass. The observer clears the renderer, waits for detailed geometry and section readiness, waits two more seconds and records 200 consecutive frames. The timing CSV from each pass is retained with the results.

The [server sampling probe](probes/SampleBench.java) times the existing surface selector and the same selector plus a second-span scan. It uses actual resident LevelChunks, alternates order within each chunk and consumes the results. Chunk acquisition happens before the timer. These times describe block reads on the server. The offline bake separately measures decoding saved chunks, page construction and compression.
