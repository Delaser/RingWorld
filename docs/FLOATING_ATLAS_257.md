# Floating builds and the distant Atlas (#257)

## Selected implementation

This implements the lighter omission alternative from [#257](https://github.com/Delaser/RingWorld/issues/257).
Recognized detached building layers are omitted from the distant terrain summary;
the usual sampler captures the ground beneath them. Real blocks remain in the
world, with normal nearby rendering, collisions, editing and saves. The building
has no distant replacement after real chunks fade out. This deliberately avoids
the extra prism geometry and layered transport of the discarded volume prototype.
The unrelated experimental branch was not merged.

`RingAtlasColumnSurface` selects a block Y from the current chunk's
`WORLD_SURFACE` column. It skips a layer only when its top and bottom are
recognized manufactured materials, the contiguous layer is at most 64 blocks
thick, an actual air gap of at least eight blocks lies beneath it, and another
non-air block exists below the gap. It repeats for stacked detached layers and
stops at the first retained surface. Reads never go below the dimension minimum;
an ordinary natural column needs only one classification. It never loads a
neighbour chunk or scans the world for structural connectivity.

`RingAtlasSurfaceSampler` recognizes planks, wool, stone-brick blocks, slabs,
stairs and walls via Minecraft tags, plus an explicit vanilla list for concrete,
glass, quartz, metal/gem storage blocks, sea lanterns and selected polished
stone/deepslate masonry. Natural stone, dirt, logs, foliage, fluids, ores and
unrecognized materials are retained. An explicitly manufactured tag can opt a
modded block into the rule; registry-name matching applies only to vanilla IDs.

The selector is used in **both** initial chunk capture and the existing dirty-cell
recapture in `RingAtlasPregenerationService`. Normal surface/side colours,
foliage and water tint, texture luminance, water coverage and exposed block light
are sampled at the selected underlying surface. No client-side column scan or
transient replacement snapshot is introduced. The server remains the only writer;
live edits use the existing bounded recapture queue and ordered tile/revision
stream on Fabric and NeoForge. Support changes beneath a manufactured top also
invalidate its column even when they lie below the stored top face. The edit hook
checks only that top material; the full column scan remains queued. This prevents
support removal from leaving a previously ground-connected cliff in the Atlas.

## Cache and version scope

Disk Atlas format is now **11**. The format-derived world hash changes too, so
old top-only samples cannot silently retain false cliffs. Both client and server
reject old caches and rebuild from canonical chunks. A progressive placeholder
is visible while missing samples are captured; generation remains resumable.
Real world blocks and saved world settings are unchanged. This is not a full
world regeneration and does not force disabled background pregeneration on.

The twelve-byte cell representation, v4 metadata/tile channels, block-light/water
nibble packing and existing revision protocol are unchanged. Python recovery
readers and their independently checked Java hash goldens now use format 11.
The change is shared across 26.1–26.1.2, 26.2 and 26.3, on both loaders.

## Intentional limits

This is a conservative column heuristic, not a general floating-object renderer.
Small gaps, layers thicker than 64 blocks, columns extending to the ground, and
builds made from natural or unknown materials remain in the heightfield. A layer
with natural material at its top or underside is retained. Mixed or vegetated
builds can therefore be only partly omitted. A roof supported by pillars in
other columns can be omitted between the pillars; connectivity is not inferred.
A structure over empty void is retained if there is no underlying sample.
Outside the ring's finite Z band there is still no terrain Atlas coverage.

No setting or slash command is added for this sampling policy. Nearby block
normalisation (#256), wall closure/depth, altitude-aware coverage and cloud
behaviour remain separate and unchanged. Nether and End are unaffected.

## Development validation

The pure selector tests cover stacked layers, protected natural/unknown surfaces,
solid supports, small gaps, exact eight-block/64-block boundaries, thick layers,
empty columns and bounded reads. Existing Atlas storage, tile, LOD, material,
revision and lifecycle contracts run with the new cache identity.

The opt-in `ringworld.captureFloatingAtlas` native fixture uses a disposable copy
of `RingWorld Floating Structure Study (1)`, named
`RingWorld Floating Atlas Review`. It rebuilds the old cache, captures a baseline,
places stacked quartz/gold platforms, and checks grass, water and foliage samples
including both sides of the canonical seam. Natural stone/log, polished deepslate,
short-gap and thick-layer controls exercise actual Minecraft material classification.
It checks that actual blocks remain,
then adds/removes a vertical support and observes authoritative and client Atlas
changes. It separately recaptures a loaded chunk, saves/disconnects, checks raw
client teardown, and reopens the same world with filtered data and real blocks.
Five captures show the baseline, nearby blocks and Low/Medium/High distant views.
Settled capture windows record real frame counts, mean/max duration and frames
above 50 ms at a 60 FPS cap; these observations are not an uncapped benchmark.
The runner mutes the disposable client and uses a hidden window.

Evidence is retained under ignored `logs/floating-atlas-257/`. These are focused
source-development checks, not frozen-candidate release qualification. Owner
motion/appearance review and the full release suite remain separate gates before
merge/publication. All six source build/test cells pass: 467 Java cases per loader on 26.1.2/26.2
and 470 per loader on 26.3, with zero failures/errors/skips. The 355 static checks
pass. The final native evidence table is pending completion of the six-cell run.
