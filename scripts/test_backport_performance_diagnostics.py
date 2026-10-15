import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest

from scripts.run_backport_performance_diagnostics import epoch_nanos, measured_frames


class PerformanceWindowTests(unittest.TestCase):
    def test_clock_join_uses_integer_nanoseconds_and_retains_warmup_boundary_spikes_in_raw_data(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'frames.rwfr'
            # Two frames straddle the selected window boundaries. The raw
            # stream retains them; only the fully contained frames are measured.
            durations = [10, 100_000_000, 20, 30, 200_000_000]
            path.write_bytes(struct.pack('>III', 0x52574652, 1, len(durations))
                             + struct.pack('>5q', *durations))
            metadata = {'schemaVersion': 1, 'kind': 'runtime-frame-window-not-performance-verdict',
                        'sequence': 0, 'samples': 5, 'firstStartNanoTime': -100,
                        'lastEndNanoTime': -100 + sum(durations),
                        'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                        'clockAnchorNanoTime': -200, 'clockAnchorEpochSecond': 1792000000,
                        'clockAnchorNanoAdjustment': 123456789, 'clockAnchorUncertaintyNanos': 100}
            path.with_suffix('.json').write_text(json.dumps(metadata))
            start = epoch_nanos(metadata, -100)
            self.assertEqual(1792000000123456889, start)
            self.assertEqual([20, 30], measured_frames([path], start + 11, start + 100_000_070))
            self.assertEqual(durations, measured_frames([path], start, start + sum(durations)))


if __name__ == '__main__': unittest.main()
