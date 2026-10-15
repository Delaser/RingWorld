import struct
from pathlib import Path
import tempfile
import unittest

from scripts.verify_backport_frame_evidence import verify


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


if __name__ == '__main__': unittest.main()
