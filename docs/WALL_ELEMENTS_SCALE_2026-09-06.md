# Wall elements at the actual exposed height

The owner requested samples of all eight structural elements fitted to the current wall after noting that the earlier elevated mockup exaggerated its height.

Read-only runtime survey confirmed minimum Y−64 and configured wall height 160: the generated top is Y95 (exclusive 96). Selected shoreline sites have surface Y63, leaving 33 exposed blocks. The saved Industrial PANELS style has thickness 7 and its inward-facing surface is Z−122. Unloaded height-map columns initially returned −64; the retained second survey explicitly loaded selected chunks before measuring.

Eight authored samples were placed directly on the original wall in the isolated copied world, without raising its top or moving terrain. Existing materials and wall texture remain around each feature. At this sample checkpoint, no production pattern was added or selected.

| Element | Dimensions / relief | Site origin X |
|---|---|---:|
| Buttress | 5 wide, 31 high, projects up to 3 | 4160 |
| Expansion joint | 3 wide, 33 high, recessed 3 | 4352 |
| Drainage outlet | 5×5 opening within 9×8 frame, recessed 3 | 4416 |
| Ventilation bank | 15×7 grille within 19×11 frame, recessed 2 | 4480 |
| Maintenance gallery | 37 long, ledge projects 3, small access panel | 4544 |
| Service shaft | 9 wide, 29 high, projects 2 | 4608 |
| Exposed machinery | 21×17 frame, recessed 3, ribs and copper bars | 4672 |
| Braced breach | tapered opening up to 24 high, surface braces | 4736 |

Gallery: `logs/wall-elements-scale/index.html`. Sixteen labelled daylight frames provide front and angled views, with original screenshots retained under the study run. Minecraft font labels are added after capture. Each frame retains wall/shoreline context; actual visible wall height elsewhere varies with terrain. Final FOV70 cameras are closer than the first pass so dark recesses can be inspected. Drainage opening blocks were checked in both server and client after the first capture was hard to interpret; they match.

Runtime remains Fabric 26.2, complete Medium Atlas, Max LOD. Simulation frozen only for captures and resumed afterwards; client left facing the buttress. Prototype code is in `scripts/wall_concepts/ScaledWallElements2.java` and `ScaledWallProbe2.java`. The initial motif pass placed some frame rules before interior rules; v2 corrects that before final screenshots. No loader build was needed for these attach-only visual samples. These are not functional drainage systems, access doors, production generation or opposite-ring LOD qualification.


## Approved production generation

Following owner approval (“add it all”), all eight motifs are implemented by `RingIndustrialElements` and `RingIndustrialElementPlacement`. The Industrial preset selects **Industrial structures** (pattern id 6); **Panels & ribs** (id 3) remains unchanged for saved styles and manual selection. Original Industrial material noise and the approved coherent top decay are retained. Structures require the Industrial palette; choosing this pattern with another palette does not add these motifs.

A seed-dependent partition places roughly one feature per 160 blocks on each inward wall face, independently for the two sides. Each complete group of eight cells contains the eight kinds in a seeded permutation. Canonical positions repeat exactly after one circumference. Motifs fit 24–33 exposed blocks and never raise the configured wall top; sites with less than 24 blocks remain plain. Three deterministic terrain-noise heights at the centre and ±18 blocks choose a shared anchor. This is a sparse terrain approximation, not a full survey of every block under a feature. Projections occupy air only. Recesses preserve at least one outer backing block, and all writes remain inside the supplied generation chunk. Decay can remove parts of a feature.

Placement runs after the existing rim installer in the asynchronous decoration pipeline. No neighbouring chunks are loaded to decide a feature. New generation uses the saved style; old chunks are not retrofitted. These remain decorative motifs rather than working drains, doors or machinery. The distant Atlas wall retains its coarse wall approximation; the new relief has not been implemented or qualified in that proxy.

Validation: Fabric and NeoForge compile and pass 433 JVM tests each on both 26.1.2 and 26.2. Python suite passes 429 tests with two platform skips. New pure tests cover canonical wrapping, both faces, motif coverage, placement spacing, height/thickness limits, retained backing and legacy selection. Retained logs: `production-tests-26.1.log`, `production-tests-26.2.log`, `production-python-tests.log` under `logs/wall-elements-scale`.

An attach-only integration probe invoked the actual production rim/element installers on 80 existing chunks in the isolated study copy with the runtime generator's actual base-height queries. It completed without error and recorded 3,824 element block changes across the scanned Y48–95 area. Of 26 chunks requiring terrain queries, median installer time was 10.87 ms; maximum was 87.64 ms (cold). This probe deliberately ran on the server thread to safely mutate loaded test chunks; normal decoration uses the generation pipeline. It is not a new-world generation, frame-pacing or multiplayer qualification. Retained source: `scripts/wall_concepts/ProductionElementsProbe.java`; evidence: `logs/wall-elements-scale/production-placement.txt`. Client remains open in the disposable copy. Experimental one-block Atlas fidelity was used only for this runtime; normal source configuration was restored after launch.


## Machinery/channel revision

Owner removed the X-braced breach and requested more machinery and embedded channels. The generation pool now contains twelve motifs: six retained structural elements, the original exposed machinery pocket, a wide twin-bay machinery variant, a tall machinery variant, and three embedded channel layouts (straight, stepped, paired). Every full twelve-cell group uses a seeded coprime permutation; density and all height/backing/chunk bounds are unchanged. The historical eight-sample gallery documents the earlier approval and is not the revised pool.

The grey ring during rain had a separate shader cause: `SkyRenderingMixin` passes vanilla celestial alpha (`rainBrightness = 1 - rainLevel`) through `ColorModulator.y`. Multiplying terrain reveal by that value erased all detail during full rain, leaving only fog colour. The surface shader now ignores celestial alpha for terrain reveal while retaining the weather-dependent lightmap, fog tint, generation haze and seam alpha. Vanilla bytecode inspection is retained in `logs/wall-elements-scale/sky-bytecode.txt`.


Validation of this revision: 434 JVM tests per loader pass on 26.1.2 and 26.2; full 26.2 builds pass; Python suite passes 429 tests with two platform skips. The shader was resource-reloaded in the running Fabric client and a rain screenshot confirms retained terrain colours/detail (`run/screenshots/weather-rain-fixed.png` under the Industrial study). Clear weather restored without moving the player. Wall generation Java changes require the next client launch; existing placed study samples, including the historic breach, are not retrofitted by this source change.

Owner weather tuning: restored bounded rain occlusion, rising smoothly beyond the live-terrain detail transition to 35% extra fog blend at full rain. Clear weather remains unchanged and at least 65% of normal terrain reveal survives. Resource-reloaded for live review; this is a shader-only visual adjustment.
