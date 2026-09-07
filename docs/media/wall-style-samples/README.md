# Wall selector samples

50 real Minecraft 26.2 Fabric screenshots: all 10 materials × all 5 selectable patterns. Open `index.html` to compare choices, or `comparison.jpg` for the complete contact sheet.

- `previews/`: clean 640-pixel-wide PNG images for a future in-game selector.
- `fullsize/`: full-resolution WebP exports (quality 90) of the HUD-free framebuffer captures. Untouched PNG originals remain in the local capture directory.
- `labelled/`: 1280-pixel-wide JPEG presentation cards with Minecraft bitmap-font labels added after capture.
- `manifest.json`: enum names and IDs, labels, relative asset paths, checksums and capture settings.

The gallery demonstrates choosing a material and pattern. The game's settings screen has not yet been wired to these preview assets.

Matched settings: noon, clear weather, FOV 60, 7-block thickness, zero decay. Each specimen is 96 × 33 blocks, rendered at the same position and camera. Production material generation samples seed 8128, circumference 16384, Y64–96, centered at X1850. Specimens are elevated in a disposable 2048 × 128 world to remove foreground obstructions. They are actual blocks rendered by Minecraft, not painted texture mockups or Atlas colors. The source elevation and display elevation differ; these show material and pattern choices, not a landscape-scale wall simulation.

Industrial structures adds geometric elements only to the Industrial palette. Its sample includes the deterministically selected exposed-machinery feature at the source coordinates. Other materials retain that pattern's normal base texture. Clustered and Strata are supported for old saves but excluded from the current selector, so they are not in this set. No decay comparison is implied by these zero-decay samples.

Regenerate with Java 25, from the repository root:

```sh
bash scripts/wall_samples/capture.sh > logs/wall-selector-samples/capture.log 2>&1
python3 scripts/wall_samples/package.py \
  --raw logs/wall-selector-samples/run/screenshots \
  --minecraft-jar /path/to/26.2/minecraft-client.jar
```

Packaging requires Pillow. The capture fixture opens its own disposable world, renders in a hidden window and exits. Serialize it with other heavy Minecraft fixtures. Its runtime source is `RingWallSelectorSamples.java`, enabled only by `ringworld.captureWallSelectorSamples` inside the existing appearance fixture. Minecraft font pixels are obtained from the locally installed client only for the labels; no standalone font asset is included.

Original PNG captures and the runtime log are retained locally under ignored `logs/wall-selector-samples/`. These are unreleased development media, not 26.3 release qualification.
