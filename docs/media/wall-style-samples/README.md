# Wall selector samples

30 real Minecraft 26.2 Fabric screenshots: all 10 materials × all 3 selectable patterns. Open `index.html` to compare choices, or `comparison.jpg` for the complete contact sheet.

- `previews/`: clean 640-pixel-wide PNG images for a future in-game selector.
- `fullsize/`: full-resolution WebP exports (quality 90) of the HUD-free framebuffer captures. Untouched PNG originals remain in the local capture directory.
- `labelled/`: 1280-pixel-wide JPEG presentation cards with Minecraft bitmap-font labels added after capture.
- `manifest.json`: enum names and IDs, labels, relative asset paths, checksums and capture settings.

The wall editor now shows the currently selected combination using bundled copies of `previews/` in `assets/ringworld/textures/gui/wall_samples/`. Changing material, pattern or preset updates the image. The caption identifies the fixed sample thickness and zero decay; the fields do not regenerate this static image. The preview shrinks at compact GUI sizes. Retired saved patterns show an unavailable-sample message until a selectable pattern is chosen.

Matched settings: noon, clear weather, FOV 60, 7-block thickness, zero decay. Each specimen is 96 × 33 blocks, rendered at the same position and camera. Production material generation samples seed 8128, circumference 16384, Y64–96, centered at X1850. Specimens are elevated in a disposable 2048 × 128 world to remove foreground obstructions. They are actual blocks rendered by Minecraft, not painted texture mockups or Atlas colors. The source elevation and display elevation differ; these show material and pattern choices, not a landscape-scale wall simulation.

RingWorld Structure adds geometric elements only to the RingWorld palette. Its sample includes the deterministically selected exposed-machinery feature at the source coordinates. Other materials retain that pattern's normal base texture. Clustered, Strata, Panels & ribs and Hybrid are supported for old saves but excluded from the current selector, so they are not in this set. No decay comparison is implied by these zero-decay samples.

Regenerate with Java 25, from the repository root:

```sh
bash scripts/wall_samples/capture.sh > logs/wall-selector-samples/capture.log 2>&1
python3 scripts/wall_samples/package.py \
  --raw logs/wall-selector-samples/run/screenshots \
  --minecraft-jar /path/to/26.2/minecraft-client.jar
```

Packaging requires Pillow. The capture fixture opens its own disposable world, renders in a hidden window and exits. Serialize it with other heavy Minecraft fixtures. Its runtime source is `RingWallSelectorSamples.java`, enabled only by `ringworld.captureWallSelectorSamples` inside the existing appearance fixture. Minecraft font pixels are obtained from the locally installed client only for the labels; no standalone font asset is included.

Original PNG captures and the runtime log are retained locally under ignored `logs/wall-selector-samples/`. These are unreleased development media, not 26.3 release qualification.

Updated selection: **RingWorld / RingWorld Structure** is the new-world material/pattern default. Masonry and Gradient are the other pattern choices. Existing screenshots were reused and relabelled; no new game captures or material-generation changes were made. Default thickness (5) and decay (25%) are retained; these comparison samples still use thickness 7 and zero decay.

Editor validation: Fabric 26.2 completed the 19-capture creation-UI fixture, including default RingWorld and changed Overgrown/Masonry previews at GUI scale 4 and a 320 × 270 logical view. Both loader source builds compiled. Actual editor screenshots are retained in `editor/default.png` and `editor/narrow.png`.
