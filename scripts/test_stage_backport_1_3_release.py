import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from scripts import stage_backport_1_3_release as stage


class BackportStageTest(unittest.TestCase):
    def test_profile_restores_historical_identity(self):
        old = stage.core.ARTIFACT_VERSION
        with stage.release_profile():
            self.assertEqual(stage.core.ARTIFACT_VERSION, '1.3.0+mc1.21.1')
        self.assertEqual(stage.core.ARTIFACT_VERSION, old)

    def test_profile_rejects_wrong_loader_and_old_identity(self):
        config = json.loads((stage.DEPLOY / 'fabric.json').read_text())
        with stage.release_profile():
            stage.core.validate_release_config(config, 'fabric')
            with self.assertRaises(stage.core.VerificationError):
                stage.core.validate_release_config(config, 'neoforge')
            config['version']['artifact_version'] = '1.0.0+mc26.1.2'
            with self.assertRaises(stage.core.VerificationError):
                stage.core.validate_release_config(config, 'fabric')

    def compare(self, mutation=None, omit=None):
        with tempfile.TemporaryDirectory() as directory:
            paths = [Path(directory) / name for name in ('fabric.jar', 'neoforge.jar')]
            entries = (*stage.core.SHARED_CRITICAL_ENTRIES, 'assets/ringworld/shaders/core/ring_surface.fsh')
            for index, path in enumerate(paths):
                with zipfile.ZipFile(path, 'w') as jar:
                    for entry in entries:
                        if index == 1 and entry == omit:
                            continue
                        jar.writestr(entry, 'changed' if index == 1 and entry == mutation else 'same')
            return stage.verify_named_contract(*paths)

    def test_named_contract_matches(self):
        self.assertGreater(len(self.compare()['entries']), 10)

    def test_named_contract_rejects_changed_payload(self):
        with self.assertRaises(stage.core.VerificationError):
            self.compare(mutation='dev/ringworld/net/RingTerrainAtlasTilePayload.class')

    def test_named_contract_rejects_changed_ring_shader(self):
        with self.assertRaises(stage.core.VerificationError):
            self.compare(mutation='assets/ringworld/shaders/core/ring_surface.fsh')

    def test_named_contract_rejects_missing_entry(self):
        with self.assertRaises(stage.core.VerificationError):
            self.compare(omit='dev/ringworld/world/RingWorldSettings.class')


if __name__ == '__main__':
    unittest.main()
