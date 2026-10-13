"""Native-platform checks for immutable NeoForm acceleration snapshots."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from minecraft_neoform_seed import NeoFormSeedError, provision, verified_files


class NeoFormSeedTest(unittest.TestCase):
    def test_snapshot_is_hash_bound_and_uses_independent_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cache, seed = root / 'cache', root / 'loom'
            (cache / 'intermediate_results').mkdir(parents=True)
            seed.mkdir()
            source = cache / 'intermediate_results/recompile_123_output.jar'
            source.write_bytes(b'compiled game')
            provision(cache, seed)
            files = verified_files(seed)
            self.assertEqual(1, len(files))
            source.write_bytes(b'modified original')
            self.assertEqual(b'compiled game', files[0].read_bytes())
            files[0].write_bytes(b'corrupted copy')
            with self.assertRaisesRegex(NeoFormSeedError, 'hash mismatch'):
                verified_files(seed)

    def test_missing_seed_is_optional_but_malformed_seed_is_not(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.assertEqual((), verified_files(root))
            for name in ('../escape.jar', 'C:\\outside.jar', '/outside.jar', 'test.lock'):
                (root / 'neoform-seed.json').write_text(json.dumps({'format': 1, 'files': [
                    {'name': name, 'bytes': 1, 'sha256': 'a' * 64}]}))
                with self.subTest(name=name), self.assertRaises(NeoFormSeedError):
                    verified_files(root)

    def test_snapshot_never_overwrites_existing_seed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'neoformruntime').mkdir()
            with self.assertRaisesRegex(NeoFormSeedError, 'overwrite'):
                provision(root, root)

    def test_snapshot_rejects_source_mutation_during_copy(self):
        import shutil
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cache, seed = root / 'cache', root / 'seed'
            (cache / 'intermediate_results').mkdir(parents=True)
            seed.mkdir()
            source = cache / 'intermediate_results/recompile_abc_output.jar'
            source.write_bytes(b'original')
            copy = shutil.copyfile
            def mutate(incoming, outgoing):
                incoming.write_bytes(b'changed!')
                return copy(incoming, outgoing)
            with patch('minecraft_neoform_seed.shutil.copyfile', side_effect=mutate):
                with self.assertRaisesRegex(NeoFormSeedError, 'changed while snapshotting'):
                    provision(cache, seed)
            self.assertFalse((seed / 'neoform-seed.json').exists())
