#!/usr/bin/env python3
"""Static contracts for frozen production lifecycle qualification."""

from __future__ import annotations

import gzip
from pathlib import Path
import struct
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from run_gradle_production_lifecycle_qualification import (  # noqa: E402
    ATLAS_MAGIC, ATLAS_VERSION, GradleProductionLifecycleError,
    _atlas_observation, _tasks, _world_inventory,
)


class GradleProductionLifecycleQualificationTest(unittest.TestCase):
    def test_task_inventory_is_loader_symmetric(self) -> None:
        fabric, neoforge = _tasks("fabric"), _tasks("neoforge")
        self.assertEqual(set(fabric), set(neoforge))
        self.assertEqual(":runProductionLifecycleClient", fabric["run"])
        self.assertEqual(":neoforge:runProductionLifecycleClient", neoforge["run"])
        with self.assertRaises(GradleProductionLifecycleError):
            _tasks("forge")

    def test_atlas_observation_requires_complete_matching_geometry(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "atlas.rwat.gz"
            width, circumference, step = 16, 32, 8
            columns, rows = circumference // step, width // step
            header = struct.pack(">IIQIIIIIQ", ATLAS_MAGIC, ATLAS_VERSION, 7,
                                 width, circumference, step, columns, rows, 3)
            cell = b"\x01" + struct.pack(">hI", 64, 0x336699)
            path.write_bytes(gzip.compress(header + cell * (columns * rows)))
            observation = _atlas_observation(path, width, circumference)
            self.assertEqual(columns * rows, observation["present_cells"])
            broken = bytearray(gzip.decompress(path.read_bytes()))
            broken[len(header)] = 0
            path.write_bytes(gzip.compress(bytes(broken)))
            with self.assertRaisesRegex(GradleProductionLifecycleError, "complete"):
                _atlas_observation(path, width, circumference)

    def test_world_inventory_is_content_bound_and_rejects_symlinks(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "level.dat").write_bytes(b"first")
            first = _world_inventory(root)
            (root / "level.dat").write_bytes(b"second")
            second = _world_inventory(root)
            self.assertNotEqual(first["sha256"], second["sha256"])
            target = root / "target"
            target.write_bytes(b"target")
            link = root / "link"
            try:
                link.symlink_to(target)
            except OSError:
                self.skipTest("symlinks are unavailable")
            with self.assertRaisesRegex(GradleProductionLifecycleError, "symlink"):
                _world_inventory(root)

    def test_gradle_profiles_use_frozen_source_sets(self) -> None:
        fabric = (ROOT / "build.gradle").read_text(encoding="utf-8")
        neoforge = (ROOT / "neoforge/build.gradle").read_text(encoding="utf-8")
        fabric_profile = fabric[fabric.index("productionLifecycleClient {"):
                                fabric.index("curvedObjectCaptureClient {")]
        neo_profile = neoforge[neoforge.index("productionLifecycleClient {"):
                              neoforge.index("strongholdTestServer {")]
        self.assertIn("qualificationFrozenRuntimeSourceSet", fabric_profile)
        self.assertIn("qualificationFrozenRuntimeSourceSet", neo_profile)
        self.assertIn("headlessPrewarmServer", fabric)
        self.assertIn("headlessPrewarmServer", neoforge)


if __name__ == "__main__":
    unittest.main()
