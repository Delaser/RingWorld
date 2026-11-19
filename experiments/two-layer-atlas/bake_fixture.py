"""Bake two occupied spans at each 2-block sample in the measured transition patches."""
import struct, gzip, io, sys
from pathlib import Path
import numpy as np
a = np.load(Path(sys.argv[2]))
height = a['h']
c = a['cls']
b = gzip.decompress((Path(sys.argv[1]) / 'dimensions/minecraft/overworld/data/ringworld/terrain-atlas.rwat.gz').read_bytes())
(_, _, world, width, L, step, cols, rows, rev) = struct.unpack('>iiqiiiiiq', b[:44])
assert (width, L, step) == (128, 2048, 1)
atlas = np.frombuffer(b[44:], dtype=[('ok', '?'), ('y', '>i2'), ('rgb', '>u4'), ('light', 'u1'), ('side', '>u4')]).reshape(rows, cols)

def tinted(rgb, scale):
    channels = [rgb >> k & 255 for k in (16, 8, 0)]
    return sum((round(v * scale) << k for (v, k) in zip(channels, (16, 8, 0))))

def water(rgb):
    channels = [rgb >> k & 255 for k in (16, 8, 0)]
    return sum((round((v * 0.94 + max(channels) * 0.06) * 0.89) << k for (v, k) in zip(channels, (16, 8, 0))))
palette = {1: 7696229, 2: 4482104, 3: 6705719, 4: 2641027, 5: 5467707, 6: 7821889, 7: 14405539, 8: 8750469, 9: 8026744, 10: 8816513, 11: 7558706}

def profile(x, z):
    """Find the largest outdoor opening, then return the lower solid and optional upper span."""
    x = int(x) % L
    z = min(63, max(-64, int(np.floor(z)))) + 64
    mat = c[z, x]
    top = int(height[z, x]) + 1
    kind = int(mat[0])
    entry = atlas[z, x]
    base = int(entry['rgb'])
    if entry['light'] >> 4:
        base = water(base)
    topcolor = base if int(entry['y']) == top else palette[kind]
    if kind not in (2, 3, 9, 10, 11):
        return [(0, top, topcolor)]
    stop = next((i for (i, v) in enumerate(mat[1:33], 1) if v in (1, 4, 5, 6, 7, 8)), None)
    if stop is None:
        return [(0, top, topcolor)]
    gaps = []
    start = 0
    for i in range(1, stop + 1):
        if mat[i] == 0:
            if start == 0:
                start = i
        elif start:
            gaps.append((i - start, start, i))
            start = 0
    if not gaps or max(gaps)[0] < 2:
        return [(0, top, topcolor)]
    (_, lo, hi) = max(gaps)
    lower = int(mat[hi])
    bottomcolor = base if int(entry['y']) == top - hi else palette[lower]
    return [(0, top - hi, bottomcolor), (top - lo, top, topcolor)]
(segs, bands) = (512, 32)
(minz, maxz) = (-59.5, 59.5)
regions = [(0, 32, 1, 20), (480, 512, 1, 20), (452, 461, 1, 31)]
mask = np.zeros((segs, bands), 'u1')
faces = io.BytesIO()
num = 0
counter = 0

def add(rgb, vertices):
    global num
    faces.write(struct.pack('>I12f', rgb, *[a for pt in vertices for a in pt]))
    num += 1

def edges(lo, hi):
    return sorted(set([lo, hi] + list(range(int(np.ceil(lo / 2)) * 2, int(np.ceil(hi / 2)) * 2, 2))))

def sample_medium(x, z):
    ax = x / 2 - 0.75
    az = (z + 64) / 2 - 0.75
    ix = int(np.floor(ax))
    iz = int(np.floor(az))
    tx = ax - ix
    tz = az - iz
    return sum((float(atlas['y'][max(0, min(63, iz + dz)) * 2 + 1, (ix + dx) % 1024 * 2 + 1]) * (tx if dx else 1 - tx) * (tz if dz else 1 - tz) for dz in (0, 1) for dx in (0, 1)))

def legacy_edge(x, z):
    """Interpolate the original lattice edge to stitch the replacement ground to it."""
    u = x / 4
    v = (z - minz) * bands / (maxz - minz)
    i = int(np.floor(u + 1e-08))
    j = int(np.floor(v + 1e-08))
    tx = u - i
    tz = v - j
    return sum((sample_medium((i + dx) * 4, minz + (j + dz) * (maxz - minz) / bands) * (tx if dx else 1 - tx) * (tz if dz else 1 - tz) for dx in (0, 1) for dz in (0, 1)))
def stitch_edge(A, B, floor, color):
    # Each coarse lattice corner is a bend in the old edge, including corners
    # inside one of our two-block samples. Closing to only the ends cuts across it.
    dx, dz = B[0] - A[0], B[1] - A[1]
    if dx:
        cuts = [(k * 4 - A[0]) / dx for k in range(int(np.floor(min(A[0], B[0]) / 4)) + 1, int(np.ceil(max(A[0], B[0]) / 4)))]
    else:
        spacing = (maxz - minz) / bands
        cuts = [(minz + k * spacing - A[1]) / dz for k in range(int(np.floor((min(A[1], B[1]) - minz) / spacing)) + 1, int(np.ceil((max(A[1], B[1]) - minz) / spacing)))]
    points = [(A[0] + dx*t, A[1] + dz*t) for t in [0.0, *sorted(cuts), 1.0]]
    for a, b in zip(points, points[1:]):
        # Sub-block overlap also covers raster precision and the difference
        # between the curved projection of a coarse chord and a subdivided one.
        ya, yb = legacy_edge(*a), legacy_edge(*b)
        lo_a, hi_a = min(ya, floor) - 0.125, max(ya, floor) + 0.125
        lo_b, hi_b = min(yb, floor) - 0.125, max(yb, floor) + 0.125
        add(color, [(a[0], lo_a, a[1]), (b[0], lo_b, b[1]), (b[0], hi_b, b[1]), (a[0], hi_a, a[1])])

for (xs, xe, zs, ze) in regions:
    mask[xs:xe, zs:ze] = 1
    X = edges(xs * 4, xe * 4)
    Z = edges(minz + zs * (maxz - minz) / bands, minz + ze * (maxz - minz) / bands)
    grid = {(i, j): profile((x + X[i + 1]) / 2, (z + Z[j + 1]) / 2) for (i, x) in enumerate(X[:-1]) for (j, z) in enumerate(Z[:-1])}
    for ((i, j), spans) in grid.items():
        (x, xx) = X[i:i + 2]
        (z, zz) = Z[j:j + 2]
        if len(spans) == 2:
            counter += 1
        for (low, high, rgb) in spans:
            add(rgb, [(x, high, z), (xx, high, z), (xx, high, zz), (x, high, zz)])
            if low:
                add(tinted(rgb, 0.6), [(x, low, z), (x, low, zz), (xx, low, zz), (xx, low, z)])
            for (di, dj, A, B, shade) in [(-1, 0, (x, z), (x, zz), 0.72), (1, 0, (xx, z), (xx, zz), 0.72), (0, -1, (x, z), (xx, z), 0.84), (0, 1, (x, zz), (xx, zz), 0.84)]:
                neighbour = grid.get((i + di, j + dj))
                if neighbour is None:
                    if not low:
                        stitch_edge(A, B, high, tinted(rgb, shade))
                    neighbour = profile((x + xx) / 2 + di * 2, (z + zz) / 2 + dj * 2)
                exposed = [(low, high)]
                for (nl, nh, _) in neighbour:
                    exposed = [(u, v) for (a0, b0) in exposed for (u, v) in [(a0, min(nl, b0)), (max(nh, a0), b0)] if u < v]
                for (u, v) in exposed:
                    # Add a tiny overlap along the side plane. At a stepped
                    # corner, shortening the wall to exactly the fine chord
                    # can expose the background between two vertical faces.
                    eps = 1 / 32
                    ax = A[0] - eps * np.sign(B[0] - A[0])
                    az = A[1] - eps * np.sign(B[1] - A[1])
                    bx = B[0] + eps * np.sign(B[0] - A[0])
                    bz = B[1] + eps * np.sign(B[1] - A[1])
                    add(tinted(rgb, shade), [(ax, u-eps, az), (bx, u-eps, bz),
                                            (bx, v+eps, bz), (ax, v+eps, az)])
with gzip.open(Path(sys.argv[3]), 'wb') as f:
    f.write(struct.pack('>Iqiiff', 1381452849, world, segs, bands, minz, maxz))
    f.write(mask.tobytes())
    f.write(struct.pack('>i', num))
    f.write(faces.getvalue())
print(num, 'faces', num * 6, 'vertices', counter, 'split columns; bridge spans', profile(1825, 1), 'tile count', mask.sum())
