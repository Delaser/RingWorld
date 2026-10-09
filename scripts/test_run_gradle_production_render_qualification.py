#!/usr/bin/env python3
"""Static contracts for frozen production rendering qualification."""

from __future__ import annotations

from pathlib import Path
import sys
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch
import run_gradle_production_render_qualification as runner

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from run_gradle_production_render_qualification import (  # noqa: E402
    ENVIRONMENTS, GradleProductionRenderError, _camera_arguments, _png, _tasks,
)


class GradleProductionRenderQualificationTest(unittest.TestCase):
    def test_task_inventory_is_loader_symmetric(self) -> None:
        fabric, neoforge = _tasks("fabric"), _tasks("neoforge")
        self.assertEqual(set(fabric), set(neoforge))
        self.assertEqual(":runProductionProjectionClient", fabric["projection"])
        self.assertEqual(":neoforge:runProductionVisualParityClient", neoforge["parity"])
        with self.assertRaises(GradleProductionRenderError):
            _tasks("forge")

    def test_loader_finalizers_share_the_batch_verifier(self) -> None:
        fabric = (ROOT / "build.gradle").read_text()
        neo = (ROOT / "neoforge/build.gradle").read_text()
        self.assertIn("ext.verifyRingProjectionOutputs = verifyFabricProjectionOutputs", fabric)
        self.assertIn("rootProject.ext.verifyRingProjectionOutputs(neoForgeProjectionRun", neo)

    def test_all_environment_modes_are_owned(self) -> None:
        self.assertEqual(("noon", "dusk", "night", "rain"), ENVIRONMENTS)

    def test_camera_override_is_canonical_and_optional(self) -> None:
        self.assertEqual((), _camera_arguments(None))
        self.assertEqual(("-PringProjectionCameraX=3072.0",), _camera_arguments(3072.0))
        for value in (-1, 16384, float("nan"), float("inf")):
            with self.subTest(value=value), self.assertRaises(GradleProductionRenderError):
                _camera_arguments(value)

    def test_batch_requires_each_environment_completion_and_frame_metrics(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory)
            (runtime / "logs").mkdir()
            prepared = SimpleNamespace(cell={"minecraft": {"version": "26.3"}, "loader": "fabric"})
            base = "Loading Minecraft 26.3 with Fabric\n[projection-capture] result=true, captures complete\n"
            evidence = "[projection-capture] environment=night complete\n" + "\n".join(
                f"[projection-capture] environment=night {view} frame metrics: samples=100"
                for view in ("tangent", "handoff", "radial-up"))
            log = runtime / "logs/latest.log"
            with patch.object(runner, "_runtime", return_value=runtime), patch.object(runner, "_png", return_value={}):
                log.write_text(base + evidence)
                self.assertEqual(3, len(runner._verify_projection(prepared, "night")))
                with self.assertRaisesRegex(GradleProductionRenderError, "completion"):
                    runner._verify_projection(prepared, "rain")
                log.write_text(base + evidence.replace("environment=night handoff", "environment=rain handoff"))
                with self.assertRaisesRegex(GradleProductionRenderError, "frame metrics"):
                    runner._verify_projection(prepared, "night")

    def test_all_weather_modes_use_one_projection_launch_and_world_copy(self) -> None:
        prepared = SimpleNamespace(paths=SimpleNamespace(repository_root=ROOT, gradle_home=Path("/seed")),
                                   cell={"loader": "fabric", "minecraft": {"version": "26.3"}, "profile": {"timeout_seconds": 3600}})
        passed = SimpleNamespace(verdict=runner.Verdict.PASS)
        with patch.object(runner, "create_contained_directories"), patch.object(runner, "stage_gradle_distribution_zip"), \
             patch.object(runner, "_stage_loom_seed"), patch.object(runner, "_world_inventory", return_value={}), \
             patch.object(runner, "_world_observation", return_value={"minecraft_version": "26.3"}), \
             patch.object(runner, "_runtime", side_effect=lambda p,n: Path("/fixture")/n), \
             patch.object(runner, "_install_runtime", return_value={}), patch.object(runner, "_fresh_copy") as copies, \
             patch.object(runner, "_record", side_effect=lambda p,args,*rest: args), \
             patch.object(runner, "execute_command", return_value=passed) as execute, \
             patch.object(runner, "_executed_record", return_value={}), \
             patch.object(runner, "_verify_projection", return_value=({}, {}, {})) as verify, \
             patch.object(runner, "_verify_parity", return_value=({}, {}, {}, {})), \
             patch.object(runner, "_copy_log", return_value={}):
            result = runner._execute(prepared, Path("/source"), None, None, ())
            self.assertEqual(3, execute.call_count)  # assets, batched projection, parity
            self.assertEqual(2, copies.call_count)
            self.assertEqual(list(ENVIRONMENTS), [c.args[1] for c in verify.call_args_list])
            self.assertEqual(16, len(result["captures"]))
            self.assertIn("-PringProjectionEnvironment=all", execute.call_args_list[1].args[0])

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
