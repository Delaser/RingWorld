"""Protect the diagnostic evidence boundary against partial/stale success."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from scripts.run_backport_exterior_diagnostics import runtime_inventory, verify_log


class ExteriorDiagnosticEvidenceTests(unittest.TestCase):
    def test_exit_or_final_marker_does_not_replace_ordered_stages(self):
        markers = ['stage=0 result=true', 'stage=1 result=true', 'final result=true']
        verify_log('\n'.join(markers), markers, 'complete')
        for text in ['final result=true', '\n'.join(reversed(markers)), '\n'.join(markers[:2])]:
            with self.subTest(text=text), self.assertRaises(RuntimeError):
                verify_log(text, markers, 'partial')

    def test_failure_anywhere_in_raw_log_blocks_success(self):
        for failure in ['[off-ring] result=false timeout', 'Failed to save chunk',
                        'OutOfMemoryError', 'Mixin apply failed']:
            for text in [failure + '\nPASS', 'PASS\n' + failure]:
                with self.subTest(text=text), self.assertRaises(RuntimeError):
                    verify_log(text, ['PASS'], 'failed')

    def test_client_class_and_module_paths_are_exactly_hash_bound(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            jar = root / 'runtime.jar'; jar.write_bytes(b'reviewed library')
            item = {'path': str(jar), 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest()}
            (root / 'inventory.json').write_text(json.dumps({'classpath': [item]}))
            (root / 'java-args.txt').write_text('"-cp"\n"' + str(jar) + '"')
            self.assertEqual([item], runtime_inventory(root))
            jar.write_bytes(b'changed library')
            with self.assertRaises(RuntimeError): runtime_inventory(root)
            jar.write_bytes(b'reviewed library')
            (root / 'java-args.txt').write_text('"-cp"\n"' + str(jar) + ';unreviewed.jar"')
            with self.assertRaises(RuntimeError): runtime_inventory(root)


if __name__ == '__main__':
    unittest.main()
