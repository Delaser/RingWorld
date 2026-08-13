#!/usr/bin/env python3
"""Static contract for the bounded cross-platform qualification workflow."""

from __future__ import annotations

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github" / "workflows" / "qualification-static.yml"


class QualificationStaticWorkflowTest(unittest.TestCase):
    def test_workflow_uses_one_cross_platform_pure_python_command(self) -> None:
        source = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("PYTHONPATH: scripts", source)
        self.assertIn("python -m unittest", source)
        self.assertIn('"scripts/external_graphical_*.py"', source)
        self.assertIn('"scripts/run_*_qualification.py"', source)
        for test in (
            "test_validate_minecraft_version_matrix.py",
            "test_qualification_gradle_isolation.py",
            "test_qualification_metadata_ranges.py",
            "test_minecraft_qualification_ranges.py",
            "test_minecraft_frozen_candidate.py",
            "test_minecraft_qualification_evidence.py",
            "test_minecraft_qualification_executor.py",
            "test_external_runtime_smoke.py",
            "test_external_runtime_executor.py",
            "test_external_runtime_qualification_adapter.py",
            "test_run_minecraft_qualification.py",
            "test_minecraft_nightly_qualification_model.py",
            "test_minecraft_atlas_recovery_qualification.py",
            "test_minecraft_atlas_recovery_persistence.py",
            "test_external_runtime_atlas_recovery_plan.py",
            "test_external_runtime_atlas_recovery_executor.py",
            "test_external_runtime_atlas_stage_runner.py",
            "test_run_atlas_recovery_qualification.py",
            "test_minecraft_worldgen_qualification.py",
            "test_external_runtime_worldgen_plan.py",
            "test_external_runtime_worldgen_executor.py",
            "test_external_runtime_worldgen_stage_runner.py",
            "test_run_worldgen_qualification.py",
            "test_external_graphical_creation_ui.py",
            "test_run_creation_ui_qualification.py",
        ):
            self.assertIn(test, source)
        for prohibited in ("./gradlew", "curl ", "wget ", "java ", "publish", "upload"):
            self.assertNotIn(prohibited, source.lower())

    def test_binary_recovery_evidence_uses_windows_binary_descriptors(self) -> None:
        for name in (
            "external_runtime_atlas_recovery_executor.py",
            "external_runtime_atlas_stage_runner.py",
        ):
            source = (ROOT / "scripts" / name).read_text(encoding="utf-8")
            self.assertIn('getattr(os, "O_BINARY", 0)', source)


if __name__ == "__main__":
    unittest.main()
