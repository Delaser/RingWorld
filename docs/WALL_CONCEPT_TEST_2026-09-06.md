# Four Industrial concept trials

All four proposed concepts were prototyped as real Minecraft block specimens in the isolated copied study world, with the original as control. Production pattern selection remains unchanged. Runtime: Fabric 26.2, complete Medium 16384×256 one-block Atlas, Max LOD. No client restart or desktop focus manipulation was needed.

Gallery: `logs/wall-concept-comparison/index.html`. Twenty screenshots cover five samples × front/angled × noon/midnight. All use the original Industrial palette, seed 8128, matching canonical sample coordinates, FOV 60, 40% approved top-edge decay and identical lantern locations. HUD hidden, simulation frozen during capture; simulation/noon restored afterwards with the game open facing Repair history. Labels use Minecraft bitmap glyphs added after capture.

## Prototype controls

Each 96×48 material slice preserves the original's five-material histogram. The common decay mask and lantern substitutions are applied afterwards, so visible material ratios can differ slightly. Geometry and light positions match. Material coverage compensation preferentially changes areas outside authored features; this can add incidental speckling. The original field itself is passed unchanged for the control.

Repair history inserts sparse stepped rectangular repairs. Service routes adds narrow trunks and short terminal branches. Staggered courses shifts original sample rows within unequal-height bands and adds interrupted joints. Weathered joints uses short downward trails seeded from existing high-roll material regions; it is an approximation rather than a full structural-joint model. These are first visual implementations of the concepts, not complete production algorithms.

## Checks and timing

250 standalone cases pass determinism, input immutability, valid palette rolls, exact material histogram and baseline identity. Warmed Java 25 standalone timing over a synthetic 4608-cell face (101 measurements after 20 warm-ups):

| Pattern | Median ms | p95 ms |
|---|---:|---:|
| Original copy | 0.002 | 0.003 |
| Repair history | 0.791 | 1.402 |
| Service routes | 0.720 | 1.309 |
| Staggered courses | 0.738 | 1.072 |
| Weathered joints | 0.763 | 1.167 |

This excludes original production sampling, block placement, lighting and Atlas rebuild/upload. It is not FPS or a generation-regression benchmark. Full-slice allocation and sorting keep the prototypes out of production; any chosen pattern needs a bounded canonical sampler and seam tests before promotion. No new production code changed in this trial, so the prior 429-test builds are not represented as new concept qualification.

The first attach setup failed on a changed reflective BlockState method signature. The successful v2 uses the canonical lantern state; all five READY records are retained. Source and run instructions: `scripts/wall_concepts/README.md`.

## Visual assessment and limits

Repair history retains the closest overall character. Staggered courses makes the strongest change to the wall's organization. Service routes and Weathered joints are subtle at this scale. These are agent observations, not owner approval.

Opposite-ring material appearance and full-ring seam continuity are not established: the distant wall shader still renders the original pattern. Real blocks should be reviewed before writing that approximation. No claim of unchanged FPS or reduced stuttering is made. The five prototype specimens are available for owner inspection; the original remains the production choice.

## Large-scale authored study

After rejecting the four noise-oriented trials, the owner requested a demonstration of large structure. A 384×96×7-block specimen now stands at X6144–6527, Y128–223, Z−90–−84 in the same copied world. Five unequal armour sections surround a 56-block-wide sealed gate and a smaller maintenance hatch. Both entrances are recessed two blocks; six lights mark their edges. The palette is original Industrial, but material proportions are deliberately changed to create quiet panel interiors; original grain appears around joins. Approved decay is 10% for this specimen. This is authored architecture to demonstrate scale, not a production procedural generator or functioning gate.

Three captures (day overview, gate detail, night overview) are in logs/large-wall-concept/index.html with original screenshots in the study run. Front camera X6336,Y182,Z120,yaw180,pitch2,FOV70. Gate camera X6336,Y164,Z−8,yaw180,pitch0. Captures use hidden HUD and frozen simulation; normal simulation/noon and overview camera restored. Visually checked the overview and gate detail. The specimen is elevated for inspection, not integrated into terrain. Original world generation and distant Atlas wall selection remain unchanged. Source: scripts/wall_concepts/LargeWallPattern.java, LargeWallProbe.java, capture_large.py and package_large.py.
