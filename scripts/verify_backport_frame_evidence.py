#!/usr/bin/env python3
"""Independently summarize bounded raw graphical fixture samples (not a verdict)."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct


def verify(path: Path) -> dict:
    if not 20 <= path.stat().st_size <= 12 + 65_536 * 8:
        raise ValueError('frame evidence has an invalid byte budget')
    data = path.read_bytes()
    magic, version, count = struct.unpack('>III', data[:12])
    if (magic, version) != (0x52574652, 1) or not 0 < count <= 65_536 or len(data) != 12 + count * 8:
        raise ValueError('frame evidence header/count/length mismatch')
    samples = [item[0] for item in struct.iter_unpack('>q', data[12:])]
    if any(value <= 0 for value in samples):
        raise ValueError('frame durations must be positive')
    ordered = sorted(samples)
    return {'schemaVersion': 1, 'kind': 'raw-frame-summary-not-performance-verdict',
            'sha256': hashlib.sha256(data).hexdigest(), 'samples': count,
            'meanMs': sum(samples) / count / 1e6,
            **{key: ordered[math.ceil(count * fraction) - 1] / 1e6 for key, fraction in
               [('p50Ms', .5), ('p95Ms', .95), ('p99Ms', .99), ('maxMs', 1)]},
            **{f'over{limit}Ms': sum(value > limit * 1_000_000 for value in samples)
               for limit in (50, 100, 250, 500)}}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('path', type=Path)
    print(json.dumps(verify(parser.parse_args().path), indent=2))
