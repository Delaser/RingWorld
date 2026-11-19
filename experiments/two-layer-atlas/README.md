# Two-layer Atlas trial

This isolated client experiment compares a single-height surface with a ground surface and one overhead span. The two acceptance scenes are a natural forest by the rim and a 101-block stone-brick bridge built from ordinary blocks in a disposable copy of seed 255. The bridge has four polished-andesite piers and spruce parapets. The original world and the published mod are unaffected.

## Rendering

With the JVM property `ringworld.layerTrial` set to the absolute path of the capture, the detailed mesh worker adds the replacement faces and marks the corresponding original terrain triangles. The shader selects the layer mesh between the proxy fade start and 1.4 times the native fade end. Normal depth and atmospheric handoff still apply. Outside that band the original surface draws. The outer switch is a hard boundary in this trial. Both meshes are submitted even when only one is visible.

The input is a local snapshot of the two test areas. An offline baker reads the save at two-block spacing, keeps the largest air gap of at least two blocks within its 32-block search, and stores exposed ground, crown/deck tops, undersides and side faces. The capture uses both the original Atlas colours and representative material colours. Small branches, fences and leaf transparency are simplified. Wall geometry uses the normal renderer. Boundary faces join the captured ground to the coarse mesh around each patch.

The `layers.bin.gz` fixture is independent of the normal Atlas save. It is loaded only when explicitly selected and when the layout fingerprint, lattice and rim bounds match. Select it only with this measurement world. It is a static capture and does not respond to subsequent world edits. The trial does not modify the multiplayer protocol.

## Measurements

The comparison uses Medium detail on NeoForge 26.3 with a 12-chunk view distance. The real-block control uses the same coordinates and orientations at 22 chunks. All clients use 2560×1440, fixed noon, clear weather, clouds off, sound muted and a verified fixed-animation resource pack. Frame times are elapsed times between rendered frames on an Apple M2. Each pass warms the camera after a teleport, records 150 consecutive frames per pose, then takes its screenshot. Loading, screenshots and teleport time are outside this measurement. Each scene has five viewpoints and two timed passes per build, with baseline/trial/trial/baseline ordering.

[Forest comparison](forest.png) · [Five forest views](forest-route.webp)

[Bridge comparison](bridge.png) · [Five bridge views](bridge-route.webp)

[Frame-time summary](frame-summary.csv) · [Individual frame times](frame-samples.csv)

### What the pictures show

The finer mesh on the previous experiment still joined trees into ramps. Here individual crown clusters and the ground between them are visible, though their faces are coarser and flatter than live leaves. On the bridge the central deck remains continuous and the supports separate from the water. The surface behind the openings still has Atlas detail.

In the middle bridge crop, the first two-layer bake exposed 275 sky-coloured pixels through a patch boundary. Adding faces that meet the original coarse boundary brought that count to 4. The baseline and real-chunk reference both counted zero there. The five corrected bridge crops counted 2, 4, 4, 6, and 11, against 0, 0, 0, 0, and 9 on the baseline.

The two scenes add 61,914 submitted vertices to the 141,312-vertex Medium mesh. This measures the drawing cost of a static fixture, not the cost of scanning, transmitting, or rebuilding layers on a multiplayer server. It also does not establish the cost of enabling layers for an entire ring. The fixture includes an explicit outer distance switch and a small residual boundary artifact.

| Scene | Median baseline → trial | P95 baseline → trial | P99 baseline → trial |
| --- | ---: | ---: | ---: |
| Forest | 11.43 → 12.16 ms | 17.28 → 17.86 ms | 19.40 → 19.41 ms |
| Bridge | 9.53 → 9.76 ms | 15.39 → 15.91 ms | 16.90 → 17.79 ms |

These are pooled results from 1,500 frames per scene per build. In the forest the two trial passes had medians of 12.96 and 11.67 ms. Median frame time increased in both scenes. All measured frames were below 50 ms. The per-run summary records the variability between passes.

For the fixed forest crop, mean absolute RGB-channel error on reference-green pixels fell from 10.88 to 8.90. In the strip around the deck it fell from 19.36 to 13.43. [Per-view image measurements](image-errors.csv)

### Replaying the fixture

The optional offline tools use Python and NumPy. With the complete measurement
world (including the bridge), run these commands from this directory. The
capture bounds, material approximations and layout are specific to the fixture.

```sh
python read_fixture_blocks.py /path/to/world columns.npz
python bake_fixture.py /path/to/world columns.npz capture.bin.gz
```
