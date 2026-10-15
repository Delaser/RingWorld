#!/usr/bin/env python3
"""Validate and summarize raw inclusive server measurements, without a verdict."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct
try:
    from .verify_backport_frame_evidence import clock_anchor
except ImportError:
    from verify_backport_frame_evidence import clock_anchor

ROW = struct.Struct('>iiiqqqqq')
CAPACITY = 131_072


def rows(path: Path) -> tuple[dict, list[tuple]]:
    if not 12 + ROW.size <= path.stat().st_size <= 12 + CAPACITY * ROW.size:
        raise ValueError('tick evidence has an invalid byte budget')
    data = path.read_bytes()
    magic, version, count = struct.unpack('>III', data[:12])
    if (magic, version) != (0x5257544B, 1) or not 0 < count <= CAPACITY or len(data) != 12 + count * ROW.size:
        raise ValueError('tick evidence header/count/length mismatch')
    metadata = json.loads(path.with_suffix('.json').read_text(encoding='utf-8'))
    if (metadata.get('schemaVersion') != 1 or type(metadata.get('samples')) is not int
            or metadata['samples'] != count or metadata.get('unfinishedLoop') is not False
            or type(metadata.get('ownerCpuAvailable')) is not bool
            or type(metadata.get('lateHookStressProbe')) is not bool
            or type(metadata.get('shutdownNanos')) is not int or metadata['shutdownNanos'] < 0
            or metadata.get('sha256') != hashlib.sha256(data).hexdigest()):
        raise ValueError('tick metadata is missing, incomplete or differs from retained bytes')
    if any(key.startswith('clockAnchor') for key in metadata):
        clock_anchor(metadata)
    samples = list(ROW.iter_unpack(data[12:]))
    for index, (tick, players, phase, start, game, busy, idle, cpu) in enumerate(samples):
        if (tick < 0 or not 0 <= players <= 1024 or not 0 <= phase <= 255
                or game < 0 or busy < 0 or idle < 0 or game + busy <= 0
                or (cpu < 0 if metadata['ownerCpuAvailable'] else cpu != -1)):
            raise ValueError('invalid tick duration, CPU availability or fixture identity')
        if index:
            previous = samples[index - 1]
            if tick < previous[0] or start <= previous[3] or start - previous[3] < sum(previous[4:7]):
                raise ValueError('nonmonotonic or overlapping tick windows')
    return metadata, samples


def summarize(samples: list[int]) -> dict:
    ordered = sorted(samples)
    count = len(samples)
    return {'samples': count, 'meanMs': sum(samples) / count / 1e6,
            **{key: ordered[math.ceil(count * fraction) - 1] / 1e6 for key, fraction in
               [('p50Ms', .5), ('p95Ms', .95), ('p99Ms', .99), ('maxMs', 1)]},
            **{f'over{limit}Ms': sum(value > limit * 1_000_000 for value in samples)
               for limit in (50, 100, 250, 500)}}


def verify(path: Path) -> dict:
    metadata, samples = rows(path)
    phases = {}
    for phase in sorted({row[2] for row in samples}):
        selected = [row for row in samples if row[2] == phase]
        phases[str(phase)] = {'work': summarize([row[4] + row[5] for row in selected]),
                             'game': summarize([row[4] for row in selected]),
                             'scheduledBusy': summarize([row[5] for row in selected]),
                             'idle': summarize([row[6] for row in selected]),
                             'minimumPlayers': min(row[1] for row in selected),
                             'maximumPlayers': max(row[1] for row in selected)}
    return {'schemaVersion': 1, 'kind': 'raw-inclusive-tick-summary-not-performance-verdict',
            'sha256': metadata['sha256'], 'samples': len(samples),
            'lateHookStressProbe': metadata['lateHookStressProbe'],
            'ownerCpuAvailable': metadata['ownerCpuAvailable'], 'shutdownMs': metadata['shutdownNanos'] / 1e6,
            'work': summarize([row[4] + row[5] for row in samples]), 'phases': phases,
            'loopIntervals': summarize([b[3] - a[3] for a, b in zip(samples, samples[1:])])
                if len(samples) > 1 else None,
            'betweenWindowGaps': summarize([b[3] - a[3] - sum(a[4:7]) for a, b in zip(samples, samples[1:])])
                if len(samples) > 1 else None,
            'ownerCpu': summarize([row[7] for row in samples]) if metadata['ownerCpuAvailable'] else None}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('path', type=Path)
    print(json.dumps(verify(parser.parse_args().path), indent=2))
