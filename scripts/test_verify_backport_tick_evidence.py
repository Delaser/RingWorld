import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest

from scripts.verify_backport_tick_evidence import ROW, rows, verify


class BackportTickEvidenceTests(unittest.TestCase):
    def fixture(self, directory):
        path = Path(directory) / 'samples.rwtk'
        data = struct.pack('>III', 0x5257544B, 1, 3) + b''.join(ROW.pack(*sample) for sample in [
            (1, 3, 0, 1, 1_000_000, 49_000_001, 5_000_000, 7_000_000),
            (2, 3, 0, 60_000_000, 100_000_001, 1, 0, 10_000_000),
            (2, 0, 1, 170_000_000, 1, 1, 1_000_000, 0)])
        path.write_bytes(data)
        metadata = {'schemaVersion': 1, 'samples': 3, 'sha256': hashlib.sha256(data).hexdigest(),
                    'ownerCpuAvailable': True, 'unfinishedLoop': False, 'shutdownNanos': 1,
                    'lateHookStressProbe': False}
        path.with_suffix('.json').write_text(json.dumps(metadata))
        return path, metadata

    def test_includes_late_game_work_and_scheduled_work_with_pause_and_phase(self):
        with tempfile.TemporaryDirectory() as directory:
            path, _ = self.fixture(directory)
            report = verify(path)
            self.assertEqual(3, report['samples'])
            self.assertEqual(2, report['work']['over50Ms'])
            self.assertEqual(1, report['work']['over100Ms'])
            self.assertEqual(0, report['phases']['1']['minimumPlayers'])
            self.assertEqual(3, report['phases']['0']['maximumPlayers'])
            self.assertEqual(59.999999, report['loopIntervals']['p50Ms'])
            self.assertNotIn('status', report)

    def test_metadata_corruption_and_unfinished_measurement_cannot_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            path, original = self.fixture(directory)
            for key, value in [('sha256', '0' * 64), ('samples', 2), ('unfinishedLoop', True),
                               ('ownerCpuAvailable', False), ('lateHookStressProbe', None), ('shutdownNanos', -1)]:
                metadata = original | {key: value}
                path.with_suffix('.json').write_text(json.dumps(metadata))
                with self.subTest(key=key), self.assertRaises(ValueError): rows(path)

    def test_malformed_counts_trailing_bytes_and_invalid_native_windows_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            path, original = self.fixture(directory)
            valid = path.read_bytes()
            mutations = [b'', valid[:-1], valid + b'x', struct.pack('>III', 0x5257544B, 2, 3) + valid[12:]]
            for offset, replacement in [(12, struct.pack('>i', -1)), (20, struct.pack('>i', 256)),
                                        (24, struct.pack('>q', 1_000_000_000)), (32, struct.pack('>q', -1)),
                                        (56, struct.pack('>q', -1))]:
                mutations.append(valid[:offset] + replacement + valid[offset + len(replacement):])
            for data in mutations:
                path.write_bytes(data)
                path.with_suffix('.json').write_text(json.dumps(original | {'sha256': hashlib.sha256(data).hexdigest()}))
                with self.subTest(data=data), self.assertRaises(ValueError): rows(path)


if __name__ == '__main__': unittest.main()
