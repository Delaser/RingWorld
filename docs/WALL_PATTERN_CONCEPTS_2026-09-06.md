# Industrial wall generation concepts — 2026-09-06

Status: proposals only. The owner chose the original Industrial materials and pattern after rejecting the grid, connected patches and block substitutions. The approved top-edge decay is retained separately. Revision7 restores exact original material rolls and the original palette, including polished basalt. No concept below is enabled in production.

## What the trials taught us

The original's dark palette, irregular panel sizes and small-scale detail work together. Smoothing the material field erased much of that character; andesite changed its brightness; smooth basalt changed its texture. This is our interpretation of the owner's reviews, not a universal rule. The next study should keep the entire original palette and isolate changes to the organization of detail.

## Four concepts

### 1. Repair history — recommended first

Keep most of the original wall. Add occasional rectangular or stepped repair areas that cross only part of a panel: a replaced corner, a short infill strip, a patched fracture. Choose repair blocks from the existing palette, with similar overall material proportions. Repairs should have small internal variation rather than perfectly flat fills. Old joints can end at a patch; a few new joints can be offset.

Starting experiment: affect roughly 10–15% of the visible face, with patches about 3–10 blocks wide. These are proposed art controls, not measured optimum values. Keep copper sparse. The intent is a wall maintained over time, with clusters having a plausible reason to exist. Main risk: patches become camouflage if too frequent or too contrasting.

Reference: Rieder's [facade made from production offcuts](https://www.rieder.cc/us/news/new-facade-from-leftover-materials) provides an architectural precedent for arranging available pieces into a coherent facade. This concept borrows the idea of material history, not its colours or exact design.

### 2. Service routes

Keep the original panels and introduce sparse, connected vertical runs with occasional right-angle branches. Use existing dark materials to indicate conduits, joiners or covered access channels. Copper and lights concentrate at a few junctions rather than tracing the entire route. Leave large stretches untouched.

Generation approach: a small authored set of compatible block motifs—straight run, elbow, junction and termination—selected deterministically. Edge connections must agree between neighbouring regions and across the ring seam. Main risk: conspicuous circuitry or repeated motifs; avoid glowing outlines and excessive branches.

Reference: [Tile-Based Texture Mapping on Graphics Hardware](https://graphics.stanford.edu/papers/tile_mapping_gh2004/) demonstrates assembling large, non-periodic texture maps from a small tile set. Applying compatible motifs to actual blocks is our proposed adaptation; the paper does not establish Minecraft performance or ring-seam correctness.

### 3. Staggered construction courses

Give the wall a long horizontal rhythm: unequal-height courses, staggered short vertical joints and occasional larger structural divisions. Preserve the original grain inside those regions. Different courses should not align into another square grid; avoid uniform full-height pillars.

Generation approach: a compact hierarchy of rules choosing course heights, panel widths and a few interruptions, all from canonical coordinates. Start with material-only changes rather than protruding geometry. Main risk: the wall may read as masonry instead of industrial machinery, or strong horizontal bands may dominate the distant ring.

Reference: [Inverse Procedural Modeling of Facade Layouts](https://arxiv.org/abs/1308.0419) studies split grammars that describe facade organization. Our course system would be a simple hand-authored grammar, not that paper's inference algorithm.

### 4. Weathered joints

Leave panel geometry and most material sampling exactly as they are. Concentrate a small amount of material variation along existing joints and beneath the approved broken top edge, with discontinuous downward trails and sheltered areas. Avoid spreading wear uniformly across the face.

Generation approach: a bounded mask derived from local joint coordinates and seeded, directionally stretched noise. No fluid simulation or neighbour-chunk loads. This is a separate visual concept; it does not change the accepted top-collapse function. Main risk: replacing texture noise with dark streaks that themselves look repetitive.

References: the National Park Service's [Preservation of Historic Concrete](https://www.nps.gov/orgs/1739/upload/preservation-brief-15-concrete.pdf) documents localized deterioration and moisture-related damage. [Red Blob Games' noise guide](https://www.redblobgames.com/maps/terrain-from-noise/) explains combining noise scales and handling wraparound. The mask described here is our visual approximation, not a physical deterioration model.

## Proposed evaluation

Start with Repair history and Weathered joints as separate candidates. Use the original as a control. Keep palette, material proportions, lighting, exposure, seed, camera and accepted decay constant. Capture close, grazing and opposite-ring views at day/night, plus the seam. Review the actual blocks before adding a distant shader approximation.

For a later richer motif system, [Synthesis of Tiled Patterns using Factor Graphs](https://graphics.stanford.edu/~mdfisher/tiledPatterns.html) is relevant: it combines valid local arrangements with statistical preferences learned from examples. We should first author and review a few motifs; do not put an unbounded solver in chunk generation.

Implementation constraints: deterministic canonical-X sampling, exact ring closure, bounded work per sampled block, no neighbouring chunk loads, no new render pass for the initial material-only experiments. Preserve the original option. Measure generation time and Atlas refresh cost before making performance claims. Previously generated walls require an explicit regeneration path; testing stays in copied worlds.

Owner subsequently requested all four trials. Real-block prototype results and qualification limits are recorded in [WALL_CONCEPT_TEST_2026-09-06.md](WALL_CONCEPT_TEST_2026-09-06.md). None is promoted to a production pattern.
