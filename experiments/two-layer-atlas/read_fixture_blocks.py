"""Extract the upper 40 blocks of each column from this complete 26.3 test world.

Usage: python read_fixture_blocks.py WORLD_DIR COLUMNS.npz
"""
from pathlib import Path
import gzip, zlib, struct, io, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
regions = Path(sys.argv[1]) / 'dimensions/minecraft/overworld/region'

class NBT:

    def __init__(self, b):
        self.f = io.BytesIO(b)

    def n(self, fmt):
        return struct.unpack('>' + fmt, self.f.read(struct.calcsize('>' + fmt)))[0]

    def string(self):
        return self.f.read(self.n('H')).decode('utf8', errors='replace')

    def tag(self, t):
        if 1 <= t <= 6:
            return self.n('bhiqfd'[t - 1])
        if t in (7, 11, 12):
            return self.f.read(self.n('i') * {7: 1, 11: 4, 12: 8}[t])
        if t == 8:
            return self.string()
        if t == 9:
            (typ, n) = (self.n('B'), self.n('i'))
            return [self.tag(typ) for _ in range(n)]
        if t == 10:
            v = {}
            while (typ := self.n('B')):
                k = self.string()
                v[k] = self.tag(typ)
            return v
        raise ValueError(t)

    def root(self):
        assert self.n('B') == 10
        self.string()
        return self.tag(10)

def chunks():
    for path in sorted(regions.glob('r.*.*.mca')):
        (_, rx, rz, _) = path.name.split('.')
        (rx, rz) = map(int, (rx, rz))
        with path.open('rb') as f:
            for (i, entry) in enumerate(struct.unpack('>1024I', f.read(4096))):
                (x, z) = (32 * rx + i % 32, 32 * rz + i // 32)
                if not entry or not (0 <= x < 128 and -4 <= z < 4):
                    continue
                f.seek((entry >> 8) * 4096)
                (l, typ) = struct.unpack('>IB', f.read(5))
                b = f.read(l - 1)
                yield (x, z, NBT({1: gzip.decompress, 2: zlib.decompress, 3: lambda v: v}[typ](b)).root())

def kind(name):
    s = name.split(':')[-1]
    if s in ('air', 'cave_air', 'void_air'):
        return 0
    if s.endswith('_leaves'):
        return 2
    if s.endswith(('_log', '_wood')):
        return 3
    if s in ('water', 'bubble_column'):
        return 4
    return {'grass_block': 5, 'dirt': 6, 'podzol': 6, 'coarse_dirt': 6, 'sand': 7, 'stone': 8, 'gravel': 8, 'stone_bricks': 9, 'polished_andesite': 10, 'spruce_planks': 11, 'short_grass': 5, 'leaf_litter': 6, 'fern': 5}.get(s, 1)
if __name__ == '__main__':
    cls = np.zeros((128, 2048, 40), np.uint8)
    h = np.full((128, 2048), -999, np.int16)
    seen = set()
    ids = np.arange(4096)
    for (cx, cz, c) in chunks():
        assert (cx, cz) == (c['xPos'], c['zPos'])
        seen.add((cx, cz))
        grid = np.zeros((384, 16, 16), np.uint8)
        for sec in c['sections']:
            lo = sec['Y'] * 16 + 64
            if not 0 <= lo < 384 or 'block_states' not in sec:
                continue
            v = sec['block_states']
            pal = np.array([kind(s if isinstance(s, str) else s.get('id', s.get('Name', s.get('')))) for s in v['palette']], np.uint8)
            if len(pal) == 1:
                grid[lo:lo + 16] = pal[0]
                continue
            n = max(4, (len(pal) - 1).bit_length())
            per = 64 // n
            a = np.frombuffer(v['data'], dtype='>u8')
            ind = a[ids // per] >> (ids % per * n).astype(np.uint64) & (1 << n) - 1
            grid[lo:lo + 16] = pal[ind].reshape(16, 16, 16)
        zs = slice(cz * 16 + 64, cz * 16 + 80)
        xs = slice(cx * 16, cx * 16 + 16)
        peak = 383 - (grid[::-1] != 0).argmax(0)
        h[zs, xs] = peak - 64
        depths = np.maximum(peak[..., None] - np.arange(40), 0)
        cls[zs, xs] = np.take_along_axis(grid.transpose(1, 2, 0), depths, axis=2)
    if len(seen) != 1024 or (h == -999).any():
        raise ValueError('Expected the complete 2048 by 128 fixture')
    print('chunks', len(seen), 'missing', np.count_nonzero(h == -999), flush=True)
    np.savez_compressed(Path(sys.argv[2]), h=h, cls=cls)
