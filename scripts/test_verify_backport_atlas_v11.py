import gzip
import struct
import tempfile
import unittest
from pathlib import Path
from verify_backport_atlas_v11 import identity, verify
from verify_backport_atlas_v10 import identity as old_identity


class AtlasV11IdentityTest(unittest.TestCase):
    def test_complete_disk_contract_rejects_old_semantics_and_corruption(self):
        settings = {"width": 128, "circumference": 1024, "seed": -123,
                    "wallHeight": 160, "terrainNoiseMapping": 4, "format": 5}
        def name(text):
            encoded = text.encode()
            return struct.pack(">H", len(encoded)) + encoded
        fields = b"".join((b"\x04" if key == "seed" else b"\x03") + name(key)
                          + struct.pack(">q" if key == "seed" else ">i", value)
                          for key, value in settings.items())
        nbt = b"\x0a\x00\x00\x0a" + name("data") + fields + b"\x00\x00"
        header = struct.pack(">IIQiiiiiQ", 0x52574154, 11, identity(settings)[1],
                             128, 1024, 1, 1024, 128, 7)
        cell = struct.pack(">BhIBI", 1, 74, 0x123456, 0xA7, 0x654321)
        raw = header + cell * (128 * 1024)
        with tempfile.TemporaryDirectory() as directory:
            saved = Path(directory) / "settings.dat"
            atlas = Path(directory) / "atlas.gz"
            saved.write_bytes(gzip.compress(nbt))
            atlas.write_bytes(gzip.compress(raw))
            result = verify(saved, atlas)
            self.assertEqual((11, 131072, 131072, 131072),
                             (result["atlasFormat"], result["presentCells"],
                              result["waterCells"], result["litCells"]))
            old_header = struct.pack(">IIQiiiiiQ", 0x52574154, 10, old_identity(settings)[1],
                                     128, 1024, 1, 1024, 128, 7)
            cases = [old_header + raw[44:], raw[:-1], raw + b"trailing",
                     raw[:44] + b"\x00" + raw[45:], raw[:44] + b"\x02" + raw[45:]]
            for malformed in cases:
                with self.subTest(header=malformed[:44], length=len(malformed)):
                    atlas.write_bytes(gzip.compress(malformed))
                    with self.assertRaises(ValueError):
                        verify(saved, atlas)
            atlas.write_bytes(gzip.compress(raw[:44] + b"\x00" + raw[45:]))
            partial = verify(saved, atlas, require_complete=False)
            self.assertEqual("PARTIAL", partial["status"])
            self.assertEqual(131071, partial["presentCells"])

    def test_omitted_codec_defaults_match_explicit_defaults(self):
        settings = {"width": 256, "circumference": 16384, "seed": 155088888,
                    "wallHeight": 160, "surfaceReferenceY": 64, "terrainNoiseMapping": 4,
                    "format": 5}
        explicit = dict(settings, wallStyle={"thickness": 5, "palette": 0, "pattern": 0,
                                            "decay": 0, "format": 1},
                        generation={"atlas_fidelity": 1, "layout": 0,
                                    "continuous_river": 0, "more_structures": 0, "format": 1})
        self.assertEqual(identity(explicit), identity(settings))

    def test_frozen_legacy_identity_matches_java_contract(self):
        settings = {"width": 416, "circumference": 2048, "seed": 155088888,
                    "wallHeight": 160, "surfaceReferenceY": 64, "terrainNoiseMapping": 4,
                    "format": 3, "wallStyle": {"thickness": 5, "palette": 0, "pattern": 0,
                    "decay": 0, "format": 1}, "generation": {"atlas_fidelity": 1, "layout": 0,
                    "continuous_river": 0, "more_structures": 0, "format": 1}}
        self.assertEqual(1954828054372612691, identity(settings)[1])

    def test_frozen_negative_seed_and_retired_pattern_identity(self):
        settings = {"width": 256, "circumference": 16384, "seed": -7617918596273893784,
                    "wallHeight": 160, "surfaceReferenceY": 64, "terrainNoiseMapping": 4,
                    "format": 5, "wallStyle": {"thickness": 7, "palette": 4, "pattern": 3,
                    "decay": 10, "format": 1}, "generation": {"atlas_fidelity": 3, "layout": 0,
                    "continuous_river": 0, "more_structures": 0, "format": 1}}
        self.assertEqual(12595855380162204875, identity(settings)[1])


if __name__ == "__main__":
    unittest.main()
