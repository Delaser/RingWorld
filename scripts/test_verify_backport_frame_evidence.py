import struct
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from scripts.verify_backport_frame_evidence import verify, verify_runtime_sequence


class BackportFrameEvidenceTests(unittest.TestCase):
    def test_percentiles_boundaries_and_counts_use_actual_samples(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'samples.rwfr'
            samples = [1, 50_000_000, 50_000_001, 100_000_001, 500_000_001]
            path.write_bytes(struct.pack('>III', 0x52574652, 1, 5) + struct.pack('>5q', *samples))
            report = verify(path)
            self.assertEqual((5, 50.000001, 500.000001, 3, 2, 1),
                             tuple(report[k] for k in ('samples', 'p50Ms', 'p95Ms', 'over50Ms', 'over100Ms', 'over500Ms')))
            self.assertNotIn('status', report)

    def test_missing_trailing_malformed_and_empty_measurements_fail(self):
        valid = struct.pack('>IIIq', 0x52574652, 1, 1, 1)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'samples.rwfr'
            for data in [b'', valid[:-1], valid + b'x', struct.pack('>IIIq', 0x52574652, 2, 1, 1),
                         struct.pack('>IIIq', 0x52574652, 1, 2, 1), struct.pack('>IIIq', 0x52574652, 1, 1, -1)]:
                path.write_bytes(data)
                with self.subTest(data=data), self.assertRaises(ValueError): verify(path)

    def test_runtime_sequence_binds_every_duration_hash_clock_and_boundary(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = [Path(directory) / (str(i) + '.rwfr') for i in range(2)]
            originals = []
            for i, path in enumerate(paths):
                path.write_bytes(struct.pack('>IIIqq', 0x52574652, 1, 2, 11, 12))
                metadata = {'schemaVersion': 1, 'kind': 'runtime-frame-window-not-performance-verdict',
                            'sequence': i, 'samples': 2, 'firstStartNanoTime': 100 + i * 23,
                            'lastEndNanoTime': 123 + i * 23, 'label': 'A', 'pid': 42,
                            'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                            'clockAnchorNanoTime': 80, 'clockAnchorEpochSecond': 1700000000,
                            'clockAnchorNanoAdjustment': 999999999, 'clockAnchorUncertaintyNanos': 100}
                originals.append(metadata)
                path.with_suffix('.json').write_text(json.dumps(metadata))
            complete_path = Path(directory) / 'complete.json'
            complete = {'schemaVersion': 1, 'kind': 'complete-runtime-frame-stream-not-performance-verdict',
                        'label': 'A', 'samples': 4, 'windows': 2, 'firstStartNanoTime': 100,
                        'lastEndNanoTime': 146, 'clockAnchorNanoTime': 200,
                        'clockAnchorEpochSecond': 1700000001, 'clockAnchorNanoAdjustment': 119,
                        'clockAnchorUncertaintyNanos': 100}
            complete_path.write_text(json.dumps(complete))
            self.assertEqual(4, verify_runtime_sequence(paths, complete_path)['samples'])
            for key, value in [('sequence', 3), ('samples', 1), ('sha256', '0' * 64),
                               ('firstStartNanoTime', 124), ('lastEndNanoTime', 147),
                               ('clockAnchorNanoAdjustment', 1000000000), ('clockAnchorNanoTime', 81),
                               ('clockAnchorUncertaintyNanos', -1), ('label', 'B'), ('pid', 43)]:
                paths[1].with_suffix('.json').write_text(json.dumps(originals[1] | {key: value}))
                with self.subTest(key=key), self.assertRaises(ValueError): verify_runtime_sequence(paths, complete_path)
            paths[1].with_suffix('.json').write_text(json.dumps(originals[1]))
            with self.assertRaises(ValueError): verify_runtime_sequence(paths[::-1], complete_path)
            with self.assertRaises(ValueError): verify_runtime_sequence([], complete_path)
            with self.assertRaises(ValueError): verify_runtime_sequence(paths[:1], complete_path)
            for key, value in [('samples', 3), ('windows', 1), ('lastEndNanoTime', 145),
                               ('clockAnchorNanoTime', 145)]:
                complete_path.write_text(json.dumps(complete | {key: value}))
                with self.subTest(key=key), self.assertRaises(ValueError): verify_runtime_sequence(paths, complete_path)


if __name__ == '__main__': unittest.main()
