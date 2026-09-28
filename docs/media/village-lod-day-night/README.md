# Village LOD — day and night

Saved 2026-09-06 for a future RingWorld website/changelog. These are real Minecraft 26.2 Fabric development-client screenshots, not release-qualified 26.3 imagery. Nothing has been published.

## Assets

- `index.html`: local gallery with downloadable full-resolution originals.
- `comparison.png`: full scenes paired day/night, rows Low/Medium/High.
- `village-detail.png`: the same array with identical 256×256 crops enlarged 4×.
- `raw/`: six untouched 2560×1600, HUD-free game screenshots.
- `captures.json`: exact label-to-runtime-setting mapping.

Minecraft bitmap-font labels are added after capture in separate header bands. Detail crops use source rectangle x=1152, y=672, width=256, height=256. No exposure, colour, lighting, or scene content has been edited after capture. The array displays day on the left and night on the right.

## Settings and provenance

| Website label | Current experimental command |
|---|---|
| Low | `/ringworld lod low` |
| Medium | `/ringworld lod high` |
| High | `/ringworld lod max` |

The labels reflect the owner's agreed three-level naming. These assets do not implement the command/UI rename; current runtime commands still use the six-level names.

Medium ring: circumference 16,384 blocks, width 256 blocks. Complete one-block format-9 source Atlas: 4,194,304 cells. Side material colours enabled; Gamma falloff 2.0, peak 1.25. Camera (3755.1666667,145,24.5), yaw -90°, pitch -82.4042°, FOV 30. Village target approximately (11264.5,80,24.5), at 165° arc separation. Clouds off; clear weather; noon and midnight. Same camera and settings except time and LOD. Simulation frozen during captures, then resumed; client left open at night on current Max.

Village is a vanilla structure-spawned village (`minecraft:village_plains`), accepted by the owner; it is not manually built or claimed to be a seed-placed natural village. Captures use the disposable copy of the Medium Industrial Village Lighting Test save; the original save is untouched.

Source screenshots were captured through the running client's framebuffer without activating the laptop window. Capture script and packaging evidence are retained locally under `logs/natural-village-165/capture_day_night.py`, `capture-day-night.log`, and `package_day_night.py`; logs are ignored by Git. The portable final assets and this manifest live outside ignored logs for future repository inclusion.

Suggested caption: “The same generated village across the ring, by day and night, at Low, Medium and High detail.” For `village-detail.png`, append “4× crops of the original screenshots.”
