#!/usr/bin/env python3
"""Static contracts for frozen production rendering qualification."""

from __future__ import annotations

from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from run_gradle_production_render_qualification import (  # noqa: E402
    ENVIRONMENTS, GradleProductionRenderError, _png, _tasks,
)


class GradleProductionRenderQualificationTest(unittest.TestCase):
    def test_task_inventory_is_loader_symmetric(self) -> None:
        fabric, neoforge = _tasks("fabric"), _tasks("neoforge")
        self.assertEqual(set(fabric), set(neoforge))
        self.assertEqual(":runProductionProjectionClient", fabric["projection"])
        self.assertEqual(":neoforge:runProductionVisualParityClient", neoforge["parity"])
        with self.assertRaises(GradleProductionRenderError):
            _tasks("forge")

    def test_all_environment_modes_are_owned(self) -> None:
        self.assertEqual(("noon", "dusk", "night", "rain"), ENVIRONMENTS)

    def test_png_verifier_rejects_non_png(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "capture.png"
            path.write_bytes(b"not a png" * 32)
            with self.assertRaisesRegex(GradleProductionRenderError, "not PNG"):
                _png(path)

    def test_gradle_profiles_use_frozen_source_sets(self) -> None:
        fabric = (ROOT / "build.gradle").read_text(encoding="utf-8")
        neoforge = (ROOT / "neoforge/build.gradle").read_text(encoding="utf-8")
        for source in (fabric, neoforge):
            for profile in ("productionProjectionClient {", "productionVisualParityClient {"):
                start = source.index(profile)
                self.assertIn("qualificationFrozenRuntimeSourceSet", source[start:start + 800])


if __name__ == "__main__":
    unittest.main()
