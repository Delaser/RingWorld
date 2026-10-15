import gzip
import struct
import sys
from pathlib import Path
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from verify_backport_atlas_v11 import cell_content_identity, identity, verify


class BackportAtlasContentTests(unittest.TestCase):
    def test_shared_native_vector_preserves_unsigned_overflow_and_signed_height(self):
        self.assertEqual(0xE5C305F9F3BA1215, cell_content_identity(0, -64, 0x123456, 0x74, 0xABCDEF))

    def test_streamed_disk_identity_uses_cell_indices_and_ignores_absent_payload(self):
        settings = {'seed': 259, 'width': 128, 'circumference': 1024, 'wallHeight': 160,
                    'terrainNoiseMapping': 4, 'format': 5}
        def named(kind, name, payload):
            encoded = name.encode()
            return bytes([kind]) + struct.pack('>H', len(encoded)) + encoded + payload
        fields = b''.join(named(4 if key == 'seed' else 3, key,
                               struct.pack('>q' if key == 'seed' else '>i', value))
                          for key, value in settings.items())
        nbt = b'\x0a\x00\x00' + named(10, 'data', fields + b'\0') + b'\0'
        cells = settings['width'] * settings['circumference']
        _, world_hash = identity(settings)
        header = struct.pack('>IIQiiiiiQ', 0x52574154, 11, world_hash, 128, 1024, 1, 1024, 128, 42)
        absent = struct.pack('>BhIBI', 0, 777, 0xFFFFFF, 0xFF, 0xFFFFFF)
        present = struct.pack('>BhIBI', 1, -64, 0x123456, 0x74, 0xABCDEF)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); saved = root / 'settings.dat'; atlas = root / 'atlas.gz'
            saved.write_bytes(gzip.compress(nbt))
            for index in (0, 4095, 4096, cells - 1):
                atlas.write_bytes(gzip.compress(header + absent * index + present + absent * (cells - index - 1)))
                report = verify(saved, atlas, require_complete=False, include_content_identity=True)
                self.assertEqual('PARTIAL', report['status'])
                self.assertEqual(1, report['presentCells'])
                self.assertEqual(str(cell_content_identity(index, -64, 0x123456, 0x74, 0xABCDEF)), report['contentFingerprint'])
            self.assertNotIn('contentFingerprint', verify(saved, atlas, require_complete=False))


if __name__ == '__main__': unittest.main()
