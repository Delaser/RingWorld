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


def clock_anchor(metadata: dict) -> tuple[int, int, int]:
    keys = ('clockAnchorNanoTime', 'clockAnchorEpochSecond',
            'clockAnchorNanoAdjustment', 'clockAnchorUncertaintyNanos')
    if any(type(metadata.get(key)) is not int for key in keys):
        raise ValueError('missing integer clock correlation')
    monotonic, seconds, fraction, uncertainty = (metadata[key] for key in keys)
    if not 0 <= fraction < 1_000_000_000 or not 0 <= uncertainty <= 1_000_000_000:
        raise ValueError('invalid clock fraction or uncertainty')
    return monotonic, seconds * 1_000_000_000 + fraction, uncertainty


def runtime_rows(path: Path) -> tuple[dict, list[int]]:
    summary = verify(path)
    metadata = json.loads(path.with_suffix('.json').read_text(encoding='utf-8'))
    if (metadata.get('schemaVersion') != 1
            or metadata.get('kind') != 'runtime-frame-window-not-performance-verdict'
            or type(metadata.get('sequence')) is not int or metadata['sequence'] < 0
            or type(metadata.get('samples')) is not int or metadata['samples'] != summary['samples']
            or metadata.get('sha256') != summary['sha256']
            or any(type(metadata.get(key)) is not int for key in ('firstStartNanoTime', 'lastEndNanoTime'))):
        raise ValueError('runtime metadata differs from raw frame window')
    clock_anchor(metadata)
    durations = [row[0] for row in struct.iter_unpack('>q', path.read_bytes()[12:])]
    if sum(durations) != metadata['lastEndNanoTime'] - metadata['firstStartNanoTime']:
        raise ValueError('runtime frame window contains unaccounted time')
    return metadata, durations


def verify_runtime_sequence(paths: list[Path], completion_path: Path) -> dict:
    if not paths:
        raise ValueError('missing runtime frame windows')
    previous = None
    reports = []
    for ordinal, path in enumerate(paths):
        metadata, durations = runtime_rows(path)
        if metadata['sequence'] != ordinal or previous and (
                metadata['firstStartNanoTime'] != previous['lastEndNanoTime']
                or clock_anchor(metadata) != clock_anchor(previous)
                or metadata.get('label') != previous.get('label')
                or metadata.get('pid') != previous.get('pid')):
            raise ValueError('runtime windows are missing, reordered or belong to different clients')
        reports.append(verify(path))
        previous = metadata
    complete = json.loads(completion_path.read_text(encoding='utf-8'))
    first = runtime_rows(paths[0])[0]
    count = sum(item['samples'] for item in reports)
    if (complete.get('schemaVersion') != 1
            or complete.get('kind') != 'complete-runtime-frame-stream-not-performance-verdict'
            or type(complete.get('samples')) is not int or complete['samples'] != count
            or type(complete.get('windows')) is not int or complete['windows'] != len(paths)
            or complete.get('label') != first.get('label')
            or complete.get('firstStartNanoTime') != first['firstStartNanoTime']
            or complete.get('lastEndNanoTime') != previous['lastEndNanoTime']):
        raise ValueError('missing or incomplete runtime stream receipt')
    end_mono, end_wall, end_error = clock_anchor(complete)
    start_mono, start_wall, start_error = clock_anchor(first)
    if end_mono < previous['lastEndNanoTime'] or start_mono > first['firstStartNanoTime']:
        raise ValueError('clock anchors do not bound the runtime stream')
    return {'kind': 'complete-runtime-frame-stream-not-performance-verdict',
            'windows': reports, 'samples': count,
            'clockDriftNanos': (end_wall - start_wall) - (end_mono - start_mono),
            'clockBracketUncertaintyNanos': start_error + end_error}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('path', type=Path)
    print(json.dumps(verify(parser.parse_args().path), indent=2))
