#!/usr/bin/env python3
"""Static contract for the bounded cross-platform qualification workflow."""

from __future__ import annotations

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github" / "workflows" / "qualification-static.yml"


class QualificationStaticWorkflowTest(unittest.TestCase):
    def test_lifecycle_runtime_regression_has_explicit_jdk_on_both_hosts(self) -> None:
        source = WORKFLOW.read_text(encoding="utf-8")
        self.assertEqual(2, source.count('- "scripts/test_production_lifecycle_player_safety.py"'))
        self.assertEqual(2, source.count('- "src/main/java/dev/ringworld/server/RingWorldProductionLifecycleTest.java"'))
        job = source[source.index("  lifecycle-player-safety:"):source.index("  qualification-static:")]
        self.assertIn("os: [ubuntu-latest, windows-latest]", job)
        self.assertIn("actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961", job)
        self.assertIn('java-version: "25"', job)
        self.assertIn("python -m unittest scripts/test_production_lifecycle_player_safety.py", job)
        self.assertIn("scripts/test_capture_player_safety.py", job)
        self.assertEqual(2, source.count('- "scripts/test_capture_player_safety.py"'))

    def test_workflow_uses_one_cross_platform_pure_python_command(self) -> None:
        source = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("PYTHONPATH: scripts", source)
        self.assertIn("python -m unittest", source)
        self.assertIn('"scripts/external_graphical_*.py"', source)
        self.assertIn('"scripts/run_*_qualification.py"', source)
        # Shared-only renderer/preview edits must run the ABI parity guard on
        # pull requests and main pushes, just like version-owned edits.
        self.assertEqual(2, source.count('- "src/client/**"'))
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
            "test_minecraft_world_upgrade_qualification.py",
            "test_run_world_upgrade_qualification.py",
            "test_release_candidate_equivalence.py",
            "test_stage_qualified_release.py",
            "test_publish_qualified_release.py",
            "test_external_graphical_creation_ui.py",
            "test_run_creation_ui_qualification.py",
            "test_run_gradle_multiplayer_qualification.py",
            "test_run_gradle_raid_qualification.py",
            "test_run_gradle_production_lifecycle_qualification.py",
            "test_run_gradle_production_render_qualification.py",
            "test_copied_world_file_fix_upgrade.py",
            "test_run_minecraft_nightly_matrix.py",
            "test_review_composite_nightly_evidence.py",
        ):
            self.assertIn(test, source)
        for prohibited in ("./gradlew", "curl ", "wget ", "java ", "--execute", "urlopen("):
            self.assertNotIn(prohibited, source.lower())

    def test_ci_deduplicates_only_the_same_workflow_ref_and_revision(self) -> None:
        for name in ("java-build.yml", "qualification-static.yml", "package-windows.yml"):
            source = (WORKFLOW.parent / name).read_text(encoding="utf-8")
            concurrency = source[source.index("concurrency:"):source.index("permissions:")]
            for identity in ("github.workflow", "github.ref", "github.sha"):
                self.assertIn(identity, concurrency)
            self.assertIn("cancel-in-progress: true", concurrency)

    def test_binary_recovery_evidence_uses_windows_binary_descriptors(self) -> None:
        for name in (
            "external_runtime_atlas_recovery_executor.py",
            "external_runtime_atlas_stage_runner.py",
        ):
            source = (ROOT / "scripts" / name).read_text(encoding="utf-8")
            self.assertIn('getattr(os, "O_BINARY", 0)', source)


if __name__ == "__main__":
    unittest.main()
