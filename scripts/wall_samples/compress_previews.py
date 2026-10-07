"""Build compact in-game PNGs; leave the website's full-quality images intact.

Run from the repository root: python3 scripts/wall_samples/compress_previews.py
Requires Pillow. Minecraft NativeImage validates a PNG header, so WebP is not
a drop-in replacement. 384x213 keeps the original 640x355 aspect ratio.
"""
from pathlib import Path
from PIL import Image

REPO_ROOT = Path(__file__).resolve().parents[2]
BUNDLED_PREVIEWS = REPO_ROOT / 'src/main/resources/assets/ringworld/textures/gui/wall_samples'


def save_bundled_preview(image, destination):
    preview = image.convert('RGB').resize((384, 213), Image.Resampling.LANCZOS)
    preview = preview.quantize(colors=256, method=Image.Quantize.MEDIANCUT,
                               dither=Image.Dither.NONE)
    destination.parent.mkdir(parents=True, exist_ok=True)
    preview.save(destination, format='PNG', optimize=True)


if __name__ == '__main__':
    sources = sorted((REPO_ROOT / 'docs/media/wall-style-samples/previews').glob('*.png'))
    if not sources:
        raise SystemExit('No website preview PNGs found')
    for source in sources:
        with Image.open(source) as image:
            save_bundled_preview(image, BUNDLED_PREVIEWS / source.name)
    total = sum((BUNDLED_PREVIEWS / source.name).stat().st_size for source in sources)
    print(f'Compressed {len(sources)} bundled previews: {total:,} bytes')
