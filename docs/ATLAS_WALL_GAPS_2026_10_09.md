# Atlas wall openings: pre-fix investigation, 2026-10-09

The following describes the pre-fix mesh. The implementation and subsequent
verification are recorded in [issue #264 implementation](ATLAS_WALL_CLOSURE_264.md).

The outside-building feature does not change the Atlas footprint or its wall
mesh. The observed openings have two independently reproducible geometry causes.

## Elevated terrain ends above the wall

`RingSurfaceMesh` clips terrain to the inner rim, samples inward to avoid
pulling wall heights into terrain, and emits only the surface triangles. Its
wall prism ends at the configured wall top. There is no closing terrain side
face when the sampled terrain is higher than the wall. A controlled constant
terrain height of 144 with wall top 96 leaves a 48-block span with zero vertical
triangles covering it. This happens without any decay.

## Decay removes a texture without modelling the remaining solid

`RingWallTexture` samples the same deterministic `RingWallPattern.blockPresent`
used for real wall generation. Missing decayed blocks become transparent, and
both surface shader ABIs discard wall fragments below alpha 0.5. The proxy
still has only two inner faces, two outer faces and two flat caps at the original
height. It has no lowered caps or exposed sides where individual columns
collapse. Inner and outer collapse heights can differ across the wall thickness.

The controlled seed-255 sample (2,048 circumference, five-block thickness,
RingWorld Structure, wall Y=64..95) has 755 missing inner-face block samples at
25% decay, compared with zero at 0%. Inner and outer heights differ in 626
columns. The mesh has zero caps below the original top in either case. These
are material/geometry controls, not a native pixel comparison; they do not
establish the cause of every individual opening in the owner's image.

## Follow-up fix

Close the terrain's finite edges with terrain-material geometry, preserving
local bottom heights and periodic continuity. Audit terrain above the authored
wall top so it is not wrongly clipped to the inner wall face. Model decayed
wall caps and exposed sides from the same captured style/seed as the texture,
with bounded LOD geometry. Keep real decay and wall height unchanged.

Check intact and decayed walls from both sides, high and low terrain edges,
partial/complete Atlas stages and the X seam. Carry any fix through all three
supported Minecraft lines and both loaders before integration. Do not hide
the defect by removing transparency, stretching the industrial wall upward,
or adding distant underside meshes.

Evidence: the native [live view](media/floating-exterior-structure/wall-placeholder-gaps-live.png)
and [sample gallery](media/floating-exterior-structure/index.html). The small
[standalone geometry diagnostic](diagnostics/WallGapDiagnostic.java) is banked
in the repository; compile/run it with the development runtime classpath. The original output is retained locally under `logs/atlas-wall-gap-diagnosis/`.
The diagnostic now asserts that edge faces and lowered caps exist; see the
implementation record for the original and repaired evidence boundaries.
