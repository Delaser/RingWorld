# Industrial concept specimens

Offline prototypes for the four concepts in `docs/WALL_PATTERN_CONCEPTS_2026-09-06.md`. These are not registered production pattern IDs and do not change world defaults.

`WallConceptPatterns.java` arranges supplied original panel rolls into Repair history, Service routes, Staggered courses and Weathered joints. It matches the exact five-material histogram over each complete 96×48 slice. Sorting and full-slice allocation deliberately make this an offline study, not a block-by-block world-generation implementation. The standalone `WallConceptCheck` checks determinism, input ownership, palette ranges/coverage and baseline identity over 250 cases, and reports warmed timing.

`WallConceptProbe2.java` is a Java attach agent. It requires the running Fabric 26.2 isolated study world, and mutates five 96×48×7 specimens at X4096,4224,4352,4480,4608; Y128–175; Z0–6. It samples the original production material rolls at the same canonical coordinates, applies each prototype, retains production 40% approved collapse and identical rare lantern positions, and uses the production palette to place real blocks. It performs bounded placement batches on the server thread. Do not use on an ordinary play world.

Compile all Java files with Java 25 and `--add-modules jdk.attach`, with output in an ignored directory. Package `WallConceptProbe2.class` and `WallConceptPatterns.class` into an agent jar with Agent-Class `WallConceptProbe2`. Attach via its main method, passing explicit PID, absolute jar path and output log path. The retained successful run is `logs/wall-concept-comparison/setup-v2.txt`; the first attempt is retained as a failure due to a version-specific reflective method signature, corrected using the canonical lantern state.

`capture.py --pid PID` uses the retained VillageProbe2 agent to capture twenty frames. It requires successful study setup, freezes simulation, changes spectator pose/time/FOV/HUD, and restores simulation/noon at the Repair history sample in a finally block. Run only in the copied study world. `package.py` requires Pillow and macOS sips, uses the local Minecraft 26.2 bitmap font, and assembles labelled PNGs and an HTML gallery. Paths are relative to repository root.

Evidence: `logs/wall-concept-comparison/index.html`, `gallery.json`, `checks.txt`, `setup-v2.txt`; raw screenshots remain under the isolated study run. The actual concepts have not been implemented in the distant Atlas wall shader, tested across a full ring seam or promoted to multiplayer/world generation. No FPS claim follows from the CPU timing.

## Elements fitted to the existing wall

`WallScaleSurvey2.java` measures shoreline heights after explicitly loading the selected chunks; the initial survey returned unloaded-column sentinel heights and is retained only as ignored evidence. Confirmed world minimum -64, wall height 160, top-exclusive 96; shoreline surface 63 at the eight chosen sites. Saved Industrial PANELS wall thickness is 7, so its inner face is Z-122.

`ScaledWallElements2.java` and `ScaledWallProbe2.java` author eight localized structures directly into that existing wall in the copied study world. Origins: 4160,4352,4416,4480,4544,4608,4672,4736. Edits occupy Y63–95, Z−125..−119, X origin+8..55, leaving all other existing blocks intact. Final v2 puts recess/interior rules before frame rules. Original prototypes and diagnostic agents remain in ignored logs. These are visual motifs, not functioning machinery or full production generators.

`capture_scaled.py --pid PID` captures sixteen front/angled daylight frames using FOV70; camera positions are recorded in gallery.json. It restores normal simulation and the buttress view. `package_scaled.py` assembles labelled pairs and a gallery with block dimensions. Evidence: logs/wall-elements-scale/index.html, setup2.txt, survey2.txt, style.txt, and blocks5.txt. The last confirms the drainage recess has matching server/client air and backing blocks. First captures from farther away are archived because the dark recess was difficult to distinguish; final captures are closer while retaining the wall top and shoreline in view.
