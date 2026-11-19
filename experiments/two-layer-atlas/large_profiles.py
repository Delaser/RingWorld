"""Reduce each Medium column to the ground and one upper span above its largest air gap."""
import argparse
import gzip
import json
import struct
import time
from pathlib import Path

import numpy as np

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("profiles", type=Path)
parser.add_argument("atlas", type=Path)
args = parser.parse_args()
start = time.monotonic()
d = args.profiles
height = np.load(d / "height.npy")
materials = np.load(d / "materials.npy", mmap_mode="r")
with gzip.open(args.atlas, "rb") as stream:
    _, _, _, width, length, step, _, _, _ = struct.unpack(">iiqiiiiiq", stream.read(44))
    assert (width, length, step) == (512, 32768, 1)
    cell = [("ok", "?"), ("y", ">i2"), ("rgb", ">u4"), ("light", "u1"), ("side", ">u4")]
    atlas = np.frombuffer(stream.read(), dtype=cell).reshape(width, length)[1::2, 1::2]
assert atlas["ok"].all()
palette = np.array([0, 7696229, 4482104, 6705719, 2641027, 5467707,
                    7821889, 14405539, 8750469, 8026744, 8816513, 7558706], np.int32)
ground = height + 1
top = np.zeros_like(height)
low = np.zeros_like(height)
rgb = atlas["rgb"].astype(np.int32)
channels = [(rgb >> bits) & 255 for bits in (16, 8, 0)]
peak = np.maximum.reduce(channels)
water = sum(np.floor((v*.94 + peak*.06)*.89 + .5).astype(np.int32) << bits
            for v, bits in zip(channels, (16, 8, 0)))
rgb = np.where(atlas["light"] >> 4 > 0, water, rgb)
top_color = np.where(ground == atlas["y"], rgb, palette[materials[:, :, 0]])
ground_color = top_color.copy()

possible = np.isin(materials[:, :, 0], [2, 3, 9, 10, 11])
gap_size = np.zeros_like(height)
best = np.zeros_like(height)
gap_start = np.zeros_like(height)
gap_end = np.zeros_like(height)
floor_kind = np.zeros_like(height)
reached_ground = np.zeros_like(possible)
for depth in range(1, 33):
    mat = materials[:, :, depth]
    air = mat == 0
    use = (gap_size >= 2) & (gap_size >= best) & ~air & ~reached_ground
    best[use] = gap_size[use]
    gap_start[use] = depth - gap_size[use]
    gap_end[use] = depth
    floor_kind[use] = mat[use]
    reached_ground |= np.isin(mat, [1, 4, 5, 6, 7, 8])
    gap_size = np.where(~reached_ground & air, gap_size+1, 0)
split = possible & reached_ground & (best >= 2)
top[split] = ground[split]
low[split] = ground[split] - gap_start[split]
ground[split] -= gap_end[split]
ground_color[split] = np.where(ground[split] == atlas["y"][split], rgb[split], palette[floor_kind[split]])
for name, data in (("ground", ground), ("top", top), ("low", low), ("gcolor", ground_color),
                   ("tcolor", top_color), ("original", atlas["y"].astype(np.int16))):
    np.save(d / (name + ".npy"), data)
print(json.dumps(dict(split=int(split.sum()), seconds=time.monotonic()-start)))
