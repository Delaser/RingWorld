# Optional visual compact Atlas/preview UI review — 2026-09-02

## Verdict

**PASS with no findings** for the bounded Minecraft 1.21.1 Atlas HUD and map
preview-status UI batch after the pre-final layout correction.

Reviewed commits:

- parent: `54f32f40b8320d37379e8afe4108e74d7c212aff`;
- source and tests: `341bdcd38d9bd6da003f950f34e2b155f9af6ae6`;
- implementation documentation:
  `154509f4dd8cc6f8f199f3c4bf4770fcd0c2b86d`.

The source commit is based directly on the stated parent, and the documentation
commit is based directly on the source commit. The exact range changes seven
expected files and passes `git diff --check`.

## Play-HUD delegation and completion gate

The existing `GuiMixin` retains the hidden-GUI, active-level, and active
RingWorld session guards, then delegates all drawing to the new
`RingAtlasHudRenderer`. Its injection now names the full reviewed 1.21.1
`Gui.render(GuiGraphics, DeltaTracker)` descriptor. The focused ASM test reads
the mapped `Gui.class` and requires that exact method and descriptor rather
than relying only on a copied source signature.

`RingAtlasHudRenderer` reads only the current client Atlas. It obtains display
text through the established `RingAtlasHudProgress` model, pushes the 1.21.1
GUI pose, applies a `0.5` scale, draws the compact background and text at the
effective top-left position, and restores the pose. A missing Atlas returns
before drawing; a complete Atlas produces an empty progress label, so no draw
or pose mutation occurs once every authoritative cell is present.

The renderer contains no terrain-preview diagnostics. Those therefore do not
become permanent play-HUD rows.

## Map preview status

The loader-neutral `RingTerrainPreviewHud` always derives four ordered entries
for Current, High, Very high, and Ultra. Its state progression distinguishes
waiting, generating, active, and ready stages for every accepted session stage
from absent (`-1`) through Ultra. Invalid stage values fail closed.

The existing `RingWorldMapScreen` renders those four entries in a centered
two-column grid beneath its authoritative generation status. The grid retains
ten-pixel outer margins, an eight-pixel gap, a maximum 150-pixel column width,
and remains within 320-, 854-, and 1920-pixel logical screens in focused tests.
Version-owned label fitting truncates overflow with an ellipsis.

Read-only review found that the initial live diff retained the older
`43 + i * 15` status spacing, placing ETA at y=148 across the new preview
heading at y=143. The final source uses the corrected 12-pixel spacing, ending
the last existing row at y=127; the source contract now pins that separation.
The preview entries, feedback row, existing action controls, and Done button
therefore remain vertically ordered at the documented 320x270 logical floor.

## Session absence and teardown

No new client state was introduced. `ClientRingState` continues to accept only
ordered preview stages bound to the current Atlas world hash and clears both
the preview object and stage back to `-1` during ordinary session teardown.
The map cannot open without current RingWorld geometry and request capability;
an absent status returns before preview-row drawing. The play HUD is separately
guarded by active RingWorld geometry and an installed incomplete Atlas.

## Scope and evidence

The exact range contains only `RingAtlasHudRenderer`, the exact existing GUI
Mixin delegation, the existing map-screen status layout, the pure preview HUD
model, focused model/descriptor/source tests, and the technical-datasheet
checkpoint. It does not change terrain rendering or shaders, sky/fog,
commands, protocol, server behavior, persistence, world generation, creation
UI, Create integration, packaging, dependencies, or release metadata.

The implementer reports final Java 21 compilation of both client graphs and
nine focused progress, preview-state/layout, GUI descriptor/source, and retained
session/teardown cases on each graph: 18 executions with no failures, errors,
or skips. This independent review did not repeat builds or launch Minecraft.

## Remaining qualification

The bounded PASS proves source ownership, exact mapped delegation, model
behavior, completion/absence gates, and bounded logical layout. It does not
prove real GUI-scale appearance, font clipping under resource packs, runtime
Mixin application, or graphical teardown. Those remain focused client UI
qualification, not blockers for this checkpoint.
