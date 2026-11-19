"""Read the 26.3 Large save at Medium spacing; optionally measure a top-only scan."""
import argparse
import gzip
import json
import multiprocessing
from pathlib import Path
import struct
import time
import zlib

import numpy as np
from read_fixture_blocks import NBT, kind

WIDTH, LENGTH = 512, 32768
indices = np.arange(4096).reshape(16, 16, 16)[:, 1::2, 1::2]


def palette_kind(entry):
    if isinstance(entry, str):
        return kind(entry)
    # 26.3 also stores the block ID in the unnamed field of a compound.
    for key in ("id", "Name", ""):
        if isinstance(entry.get(key), str):
            return kind(entry[key])
    raise ValueError(f"Unknown block palette entry: {entry!r}")


def scan_region(task):
    path, depth = task
    _, rx, rz, _ = path.name.split(".")
    result = []
    with path.open("rb") as region:
        for i, entry in enumerate(struct.unpack(">1024I", region.read(4096))):
            cx, cz = int(rx) * 32 + i % 32, int(rz) * 32 + i // 32
            if not entry or not (0 <= cx < LENGTH//16 and -WIDTH//32 <= cz < WIDTH//32):
                continue
            region.seek((entry >> 8) * 4096)
            size, codec = struct.unpack(">IB", region.read(5))
            raw = region.read(size - 1)
            chunk = NBT({1: gzip.decompress, 2: zlib.decompress, 3: bytes}[codec](raw)).root()
            assert (cx, cz) == (chunk["xPos"], chunk["zPos"])
            grid = np.zeros((384, 8, 8), "u1")
            for section in chunk["sections"]:
                y = section["Y"]*16 + 64
                if not 0 <= y < 384 or "block_states" not in section:
                    continue
                blocks = section["block_states"]
                palette = np.array([palette_kind(entry) for entry in blocks["palette"]], "u1")
                if len(palette) == 1:
                    grid[y:y+16] = palette[0]
                    continue
                bits = max(4, (len(palette)-1).bit_length())
                slots = 64 // bits
                packed = np.frombuffer(blocks["data"], dtype=">u8")
                ids = (packed[indices//slots] >> ((indices % slots)*bits).astype("u8")) & ((1<<bits)-1)
                grid[y:y+16] = palette[ids]
            peak = 383 - (grid[::-1] != 0).argmax(0)
            levels = np.maximum(peak[..., None] - np.arange(depth), 0)
            samples = np.take_along_axis(grid.transpose(1, 2, 0), levels, axis=2)
            result.append((cx, cz, (peak-64).astype("i2"), samples))
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world", type=Path)
    parser.add_argument("out", type=Path)
    parser.add_argument("--top-only", action="store_true")
    args = parser.parse_args()
    depth = 1 if args.top_only else 33
    directory = args.world / "dimensions/minecraft/overworld"
    with gzip.open(directory / "data/ringworld/terrain-atlas.rwat.gz", "rb") as stream:
        _, _, _, width, length, step, _, _, _ = struct.unpack(">iiqiiiiiq", stream.read(44))
    assert (width, length, step) == (WIDTH, LENGTH, 1)
    args.out.mkdir(parents=True, exist_ok=True)
    start = time.monotonic()
    height = np.full((WIDTH//2, LENGTH//2), -999, "i2")
    materials = np.empty((*height.shape, depth), "u1")
    count = 0
    with multiprocessing.Pool(3) as pool:
        paths = sorted((directory / "region").glob("*.mca"))
        for chunks in pool.imap_unordered(scan_region, ((p, depth) for p in paths), 1):
            for cx, cz, h, m in chunks:
                x, z = cx*8, (cz+WIDTH//32)*8
                height[z:z+8, x:x+8], materials[z:z+8, x:x+8] = h, m
                count += 1
    assert count == 65536 and (height != -999).all()
    np.save(args.out / "height.npy", height)
    np.save(args.out / "materials.npy", materials)
    print(json.dumps(dict(chunks=count, samples=height.size, depth=depth, seconds=time.monotonic()-start)))
