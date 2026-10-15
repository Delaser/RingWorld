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

    def test_affine_clock_correction_preserves_raw_cost_and_rejects_clock_steps(self):
        start = {'clockAnchorNanoTime': 100, 'clockAnchorEpochSecond': 1792000000,
                 'clockAnchorNanoAdjustment': 0, 'clockAnchorUncertaintyNanos': 100}
        end = start | {'clockAnchorNanoTime': 200000000100, 'clockAnchorEpochSecond': 1792000199,
                       'clockAnchorNanoAdjustment': 995000000}
        # A gradual -25 ppm adjustment is observable and calibrated. Neither
        # the frame duration nor a server work duration is rescaled for metrics.
        self.assertEqual(1792000099997500000, epoch_nanos(start, 100000000100, end))
        with self.assertRaises(ValueError):
            epoch_nanos(start, 100000000100, end | {'clockAnchorEpochSecond': 1792000198})
        with self.assertRaises(RuntimeError): epoch_nanos(start, 99, end)


if __name__ == '__main__': unittest.main()
