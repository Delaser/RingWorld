import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from scripts.verify_fabric_mixin_refmaps import verify


class FabricMixinMappingTests(unittest.TestCase):
    def fixture(self, path, selected='client-refmap.json', omit_client=False):
        owners = {'dev/ringworld/client/mixin/LivingEntitySleepingPositionMixin': {'getSleepingPos': 'mappedSleeping'},
                  'dev/ringworld/client/mixin/MinecraftMixin': {'clearClientLevel': 'mappedClear'}}
        with zipfile.ZipFile(path, 'w') as jar:
            for name, value in {
                'fabric.mod.json': {'id': 'ringworld', 'mixins': ['client.json']},
                'client.json': {'required': True, 'injectors': {'defaultRequire': 1},
                    'package': 'dev.ringworld.client.mixin', 'refmap': selected,
                    'client': ['LivingEntitySleepingPositionMixin', 'MinecraftMixin']},
                'server-refmap.json': {'mappings': {'dev/ringworld/mixin/ServerMixin': {'tick': 'mappedTick'}}},
                **({} if omit_client else {'client-refmap.json': {'mappings': owners}}),
            }.items(): jar.writestr(name, json.dumps(value))
            for owner in owners: jar.writestr(owner + '.class', b'fixture')

    def test_correct_client_table_is_selected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'mod.jar'; self.fixture(path)
            self.assertEqual('MAPPINGS_VALID_NOT_RUNTIME_QUALIFIED', verify(path)['status'])

    def test_both_maps_existing_does_not_make_wrong_linkage_valid(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'mod.jar'; self.fixture(path, selected='server-refmap.json')
            with self.assertRaisesRegex(ValueError, 'without the compiled mappings'): verify(path)

    def test_missing_mapping_table_or_ap_output_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'mod.jar'
            for selected in ('client-refmap.json', 'server-refmap.json'):
                self.fixture(path, selected=selected, omit_client=True)
                with self.assertRaises(ValueError): verify(path)


if __name__ == '__main__': unittest.main()
