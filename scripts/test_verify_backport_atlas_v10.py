import unittest
from verify_backport_atlas_v10 import identity


class AtlasV10IdentityTest(unittest.TestCase):
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
        self.assertEqual(8360113050493173708, identity(settings)[1])

    def test_frozen_negative_seed_and_retired_pattern_identity(self):
        settings = {"width": 256, "circumference": 16384, "seed": -7617918596273893784,
                    "wallHeight": 160, "surfaceReferenceY": 64, "terrainNoiseMapping": 4,
                    "format": 5, "wallStyle": {"thickness": 7, "palette": 4, "pattern": 3,
                    "decay": 10, "format": 1}, "generation": {"atlas_fidelity": 3, "layout": 0,
                    "continuous_river": 0, "more_structures": 0, "format": 1}}
        self.assertEqual(10564136735434774545, identity(settings)[1])


if __name__ == "__main__":
    unittest.main()
