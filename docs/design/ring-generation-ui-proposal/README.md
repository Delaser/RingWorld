# Ring generation UI proposal — 26 September 2026

Open [the rendered report](index.html) or [the interactive mockup](prototype.html).
Design review only; no Minecraft UI implementation, renderer, world generation,
configuration format or saved world was changed.

## Recommendation

One editor with Ring, Terrain, Walls, Sky and Preview tabs; one shared draft and
Apply / Cancel footer. Preserve all currently supported options and defaults.
Use a larger existing wall sample and three directly selectable patterns.
Move maths into optional details and make the seed preview useful with a local
segment plus full-ring overview. Keep local Low / Medium / High display detail
separate, in the existing in-world panel alongside generation progress.

The report covers current screens, all proposed pages, the return to vanilla
creation, dialogs, compact layout, validation states, complete control mapping,
implementation order and acceptance checks. It is not approval to implement.

## Evidence

- Audited repository source: `d35bf21` on `codex/atlas-fidelity-gallery`.
- Fresh 26.3 Fabric `runCreationUiClient` run completed successfully in 21 seconds.
- Retained current screenshots in `assets/current-*.png` were copied unchanged
  from `dist/qualification/ui-design-review/26.3-fabric/run/run-creation-ui/screenshots/`.
- This fixture is a menu-only capture: it does not create a world. Its saved test
  settings are not a statement of factory defaults.
- In-world generation and local display were audited from source; no new live
  in-world capture is claimed.
- Actual default wall style: RingWorld / RingWorld Structure, thickness 7, decay 10%.
- Existing selected-combo samples show thickness 7 and decay 0%; the proposal
  labels that difference and does not pretend these controls change the image.

## Mockup limits

The standalone HTML is a design reference, not a replacement game client.
The terrain SVG and sky illustrations are schematic, not generated world data.
Generation progress values are examples. The prototype demonstrates page
navigation, geometry selection, three RingWorld wall patterns, simple toggles,
local detail, dialogs and Atlas-limit feedback. The report describes the full
intended behavior; the prototype does not implement every Minecraft validation
rule, server state, keyboard convention or all 30 sample combinations.

The “Apply & return” panel explains the return to the vanilla Create World
screen; it is not a proposed extra mandatory step.

## Rendering and checks

`renders/` contains 14 individual proposal views and the report cover. Normal
views use 640 × 480 logical pixels at 2× device scale. Compact views use 320 × 270.
Browser checks cover page navigation, draft retention across tabs, wall sample
selection, the Atlas-limit error, a visible compact footer, and JavaScript errors.
These checks qualify the HTML mockup only, not a Minecraft implementation.

Run `node render.cjs` with Playwright installed. Set `PLAYWRIGHT_MODULE` to the
module path and `CHROME_PATH` to a headless-capable Chrome binary if needed.
Defaults use the local Codex runtime and installed macOS Chrome. Headless
rendering does not take desktop focus.

The review-only bitmap font derives from the locally installed Minecraft 26.3
ASCII glyphs. Minecraft assets remain Mojang/Microsoft property; it is not a
new mod font asset. Wall samples already exist in the repository. Do not bundle
this design directory into mod jars or treat its assets as newly licensed art.
