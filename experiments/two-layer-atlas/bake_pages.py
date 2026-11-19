"""Bake independent 64-block mesh pages from the Large Medium-detail column profiles."""
from pathlib import Path
import argparse
import gzip
import json
import struct
import time
import zipfile

import numpy as np

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("profiles", type=Path)
parser.add_argument("atlas", type=Path)
parser.add_argument("output", type=Path)
parser.add_argument("--rim", type=int, default=5)
args = parser.parse_args()
started = time.monotonic()
with gzip.open(args.atlas, "rb") as stream:
    _, _, world, width, length, step, _, _, _ = struct.unpack(">iiqiiiiiq", stream.read(44))
assert (length, width, step) == (32768, 512, 1)
profiles = [np.load(args.profiles / (name + ".npy"), mmap_mode="r") for name in
            ("ground", "low", "top", "gcolor", "tcolor")]
original = np.load(args.profiles / "original.npy", mmap_mode="r")
segments, bands = length // 4, width // 4
min_z = -width / 2 + args.rim - .5
max_z = -min_z
zs = np.linspace(min_z, max_z, bands * 2 + 1)
rows = np.floor(((zs[:-1] + zs[1:]) / 2 + width / 2) / 2).astype(int)
split = profiles[2][rows] != 0
mask = split.reshape(bands, 2, segments, 2).any(axis=(1, 3))
# Skip the wall overlap. The stock terrain closes the surface there.
mask[[0, -1]] = False
active = mask.repeat(2, axis=0).repeat(2, axis=1)
record = np.dtype([("rgb", ">u4"), ("corner", ">f4", (4, 3))])
faces = 0

def tint(color, scale):
    return sum(np.rint(((color >> bits) & 255) * scale).astype("i4") << bits for bits in (16, 8, 0))

def coarse_vertex(x, z):
    """Sample the existing Medium Atlas at its half-cell offset."""
    u, v = x / 2 - .75, (z + width / 2) / 2 - .75
    i, j = np.floor(u).astype(int), np.floor(v).astype(int)
    du, dv = u-i, v-j
    return sum(original[np.clip(j+b, 0, width//2-1), (i+a) % (length//2)]
               * (du if a else 1-du) * (dv if b else 1-dv) for a in (0, 1) for b in (0, 1))

def legacy(x, z):
    v = (z-min_z)*bands/(max_z-min_z)
    i, j = np.floor(x/4).astype(int), np.floor(v + 1e-10).astype(int)
    tx, tz = x/4-i, v-j
    return sum(coarse_vertex((i+a)*4, min_z+(j+b)*(max_z-min_z)/bands)
               * (tx if a else 1-tx) * (tz if b else 1-tz) for a in (0, 1) for b in (0, 1))

with zipfile.ZipFile(args.output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=1) as archive:
    page_segments = 16
    archive.writestr("meta", struct.pack(">Iqiiffi", 0x52574c32, world, segments, bands,
                                         min_z, max_z, page_segments) + mask.T.astype("u1").tobytes())
    for page in range(segments // page_segments):
        start = page * page_segments * 2
        row, local = np.nonzero(active[:, start:start + page_segments * 2])
        column = local + start
        ground, cap_low, cap_high, ground_rgb, cap_rgb = (a[rows[row], column] for a in profiles)
        x0, x1, z0, z1 = column * 2, column * 2 + 2, zs[row], zs[row + 1]
        pieces = []

        def add(visible, color, corners):
            count = np.count_nonzero(visible)
            if not count:
                return
            out = np.empty(count, dtype=record)
            out["rgb"] = color[visible]
            for k, coords in enumerate(corners):
                for axis, coord in enumerate(coords):
                    out["corner"][:, k, axis] = coord[visible]
            pieces.append(out.tobytes())

        all_cells = np.ones(len(ground), bool)
        has_cap = cap_high != 0
        horizontals = [(ground, ground_rgb, all_cells), (cap_low, tint(cap_rgb, .6), has_cap),
                       (cap_high, cap_rgb, has_cap)]
        for y, color, visible in horizontals:
            add(visible, color, [(x0, y, z0), (x1, y, z0), (x1, y, z1), (x0, y, z1)])

        edges = [(-1, 0, x0, z0, x0, z1), (1, 0, x1, z0, x1, z1),
                 (0, -1, x0, z0, x1, z0), (0, 1, x0, z1, x1, z1)]
        for dc, dr, ax, az, bx, bz in edges:
            neighbor_column = (column + dc) % (length // 2)
            neighbor_row = np.clip(row + dr, 0, len(rows) - 1)
            neighbor_ground, neighbor_low, neighbor_high = (a[rows[neighbor_row], neighbor_column]
                                                           for a in profiles[:3])
            shade = .72 if dc else .84
            border = ~active[neighbor_row, neighbor_column]
            if border.any():
                # The fine grid includes every old lattice corner; join to the actual interpolated edge.
                ya, yb = legacy(ax, az), legacy(bx, bz)
                add(border, tint(ground_rgb, shade),
                    [(ax, np.minimum(ya, ground) - .125, az), (bx, np.minimum(yb, ground) - .125, bz),
                     (bx, np.maximum(yb, ground) + .125, bz), (ax, np.maximum(ya, ground) + .125, az)])

            # Use the bridge fix on exposed vertical corners.
            eps = 1 / 32
            dx, dz = eps * np.sign(bx - ax), eps * np.sign(bz - az)
            ax, bx, az, bz = ax - dx, bx + dx, az - dz, bz + dz
            walls = [(neighbor_ground, ground, ground_rgb, all_cells),
                     (np.maximum(neighbor_ground, cap_low), cap_high, cap_rgb, has_cap)]
            for bottom, top, color, visible in walls:
                # Subtract the neighbour's upper span from the exposed interval.
                lower_end = np.where(neighbor_high != 0, np.minimum(top, neighbor_low), top)
                upper_start = np.maximum(bottom, neighbor_high)
                intervals = [(bottom, lower_end, visible), (upper_start, top, visible & (neighbor_high != 0))]
                side_rgb = tint(color, shade)
                for low, high, keep in intervals:
                    add(keep & (high > low), side_rgb,
                        [(ax, low-eps, az), (bx, low-eps, bz), (bx, high+eps, bz), (ax, high+eps, az)])
        payload = b"".join(pieces)
        faces += len(payload)//52
        archive.writestr(str(page), payload)
print(json.dumps(dict(faces=faces,pages=segments//page_segments,quads=int(mask.sum()),
                      bytes=args.output.stat().st_size, seconds=time.monotonic()-started)), flush=True)
