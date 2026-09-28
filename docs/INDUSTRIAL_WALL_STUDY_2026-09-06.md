# Industrial wall pattern and decay study — 2026-09-06

The owner approved the first proposal's decay but rejected its structural grid, requesting a return toward the original pattern. The revision delegates material regions to the established Industrial panel sampler. Localized cracked deepslate and exposed/weathered copper add wear without replacing that layout. The approved decay function is unchanged: periodic, correlated top-edge damage, with structural supports and no detached holes through the lower wall.

Existing PANELS (id 3) behavior is preserved. New Industrial presets select Weathered panels (ENGINEERED, id 6), retaining thickness 7 and default decay 10%. Saved style format remains unchanged; settings payload identity advances to settings_v7 so older clients cannot misinterpret the new pattern. The distant shader uses the original panels treatment for the new pattern; its simplified wall proxy does not reproduce every local crack or broken top edge.

## Visual evidence

`logs/industrial-wall-study/index.html` and `comparison.png` pair original panels (left) with revised panels and approved decay (right), at 10%, 40%, and 70%. Six specimens use actual production block/material and presence samplers, not invented textures. Each is 96×48×7 blocks, using the same seed 8128 and canonical sample X=4096. They are placed separately in an isolated copy of the existing Medium world. The original playable world is preserved.

Captures use matching relative cameras, noon, FOV 60, hidden HUD, and frozen simulation. Minecraft bitmap-font labels were added afterwards. All six revised captures and the paired array were visually inspected. Rejected grid captures remain separately under `rejected-structural-grid/`; they are not the current gallery.

Runtime: Fabric 26.2, Medium 16384×256 ring, complete one-block Atlas, Max LOD. Normal source fidelity defaults were restored after the experimental runtime build. The copied client remains open facing the revised 10% specimen, with simulation resumed.

## Verification and limits

Both Fabric and NeoForge pass 429 JVM tests each on 26.1.2 and 26.2. Tests cover periodicity, original material-layout preservation without wear, bounded monotonic decay, crack distribution, and legacy pattern identity. The Python suite passes 429 tests with two platform skips. Final logs: `revision2-build-26.1-final.log`, `revision2-build-26.2.log`, and `revision2-python-tests.log` under the study directory. `revision2-setup.txt` records all six completed specimens.

No new neighbor chunk loads or render passes were introduced. This is a material/generation visual study, not a measured FPS comparison or full fresh-world/multiplayer release qualification. Existing generated walls are not automatically regenerated. The seam and wall experiments are local changes after pushed checkpoint b546976; the next release remains held for stable 26.3.

## Connected material patches — owner follow-up

The owner requested less TV-static speckling and more Tetris-like continuity. Weathered panels now retain the original panel boundaries and body palette bias while replacing independent per-block grain with correlated six-block noise sampled on two-block steps. Rib material variation uses that same connected grain. Crack/copper ageing and the approved collapse function remain unchanged (method SHA-256 verified). Legacy PANELS still uses its original sampler.

The continuity regression checks material categories over a 510×190 sample: isolated pixels must fall by at least half and adjacent material changes by at least 25% relative to legacy panels. Both loaders pass 429 JVM tests on both versions; Python 429 with two skips. Final follow-up evidence uses revision3-prefixed build/runtime/setup logs and screenshot suffixes. The distant shader also groups its fine grain into connected square-edged patches; its existing approximation is retained.

## Polished andesite material trial

The owner disliked the log-like vertical stripes in the connected patches. These were polished basalt. The Weathered panels Industrial palette now uses polished andesite for rolls 68–85 instead. Connected noise, panel boundaries, copper ageing, and decay are unchanged. Legacy PANELS retains polished basalt. Polished andesite was already included in the generated-wall block classifier, and the Atlas rim colour derives from the shared material palette. Revision4 captures compare legacy panels with the andesite trial; revision3-gallery preserves the previous striped version.

## Original noise with andesite — current trial

The owner requested the original noise pattern with the striped material replaced. Revision5 restores the original panel sampler for Weathered panels and removes the connected-grain experiment from both production sampling and the distant shader. Polished andesite still replaces basalt in that variant; the approved decay and localized weathering remain unchanged. Legacy PANELS still retains its original palette and behavior. The material-distribution regression again checks equality with the legacy sampler's material bands before wear. Revision4-gallery preserves the connected-andesite comparison; current gallery uses revision5 captures.

## Smooth basalt tone correction — current trial

The owner rejected andesite's lighter tone. Revision6 replaces that band with smooth basalt: cool dark grey without polished basalt's vertical stripes. Original noise and approved decay/weathering remain. Smooth basalt is added to isRimMaterial for regeneration/repair recognition; shared Atlas palette derivation uses the replacement automatically. Legacy PANELS remains unchanged. Source texture mean RGB: polished basalt side (88.5,88.2,91.5), smooth basalt (72.7,72.3,78.2), polished andesite (132.3,134.8,134.0). Smooth basalt is slightly darker than the original, substantially closer than andesite; these texture averages are not a claim of identical in-game lighting. Revision5-gallery preserves the rejected andesite trial. Current gallery uses revision6.

## Owner decision — restore original

The owner chose the original after reviewing all material/pattern trials. Revision7 restores exact original panel rolls and the original Industrial palette, including polished basalt and raw copper. Experimental cracks/copper ageing and replacement materials are removed. The previously approved top-edge collapse remains unchanged. Both loaders pass 429 JVM tests on both versions; exact original material rolls are tested at all decay settings. Revision7 is the current runtime/gallery; prior galleries remain archived. Future concepts are proposals only in WALL_PATTERN_CONCEPTS_2026-09-06.md.
