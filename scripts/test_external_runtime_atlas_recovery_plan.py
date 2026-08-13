#!/usr/bin/env python3
"""Pure path/launch tests for the external Atlas-recovery plan."""

from __future__ import annotations

import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from external_runtime_atlas_recovery_plan import (  # noqa: E402
    FIXTURE_NAME,
    QuickTerminalEvidenceInput,
    external_runtime_atlas_recovery_plan,
)
from external_runtime_smoke import CandidateJar  # noqa: E402
from minecraft_qualification_model import InvocationError, QualificationPaths  # noqa: E402


class ExternalRuntimeAtlasRecoveryPlanTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        matrix = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        cls.cells = {item["id"]: item for item in matrix["cells"]}

    def plan(self, cell_id: str):
        cell = self.cells[cell_id]
        paths = QualificationPaths.from_cell(ROOT, cell, "atlas-recovery-plan")
        frozen = ROOT / "dist/qualification/frozen-candidates" / cell["loader"]
        candidate = CandidateJar(frozen / "ringworld.jar", "a" * 64, cell["loader"])
        quick = QuickTerminalEvidenceInput(paths.evidence_directory / "strict-terminal-evidence.json", "b" * 64)
        return external_runtime_atlas_recovery_plan(
            cell, candidate, paths, quick, frozen_candidate_root=frozen,
        ), paths

    def test_exact_nightly_runtime_world_and_evidence_paths(self) -> None:
        for cell_id in ("26.1-fabric", "26.1-neoforge"):
            plan, paths = self.plan(cell_id)
            self.assertEqual(paths.run_directory / "nightly" / FIXTURE_NAME / "runtime", plan.runtime_root)
            self.assertEqual(plan.runtime_root / "world", plan.world_root)
            server_properties = next(item.contents for item in plan.smoke.files
                                     if item.path == plan.smoke.layout.server_properties_path)
            self.assertIn("level-name=world", server_properties)
            self.assertEqual(paths.evidence_directory / "nightly" / FIXTURE_NAME, plan.evidence_root)
            self.assertEqual(plan.world_root / "ringworld-prewarm" / "result.json", plan.stages[0].runtime_report_path)
            self.assertEqual(plan.stages[0].runtime_report_path, plan.stages[1].runtime_report_path)
            self.assertEqual(plan.evidence_root / "recovery-input-atlas.rwat.gz", plan.recovery_input_atlas_path)

    def test_fabric_system_properties_precede_jar_and_neoforge_defers_to_installer_args(self) -> None:
        fabric, _ = self.plan("26.1-fabric")
        argv = fabric.stages[0].launch.argv
        jar = argv.index("-jar")
        self.assertLess(argv.index("-Dringworld.headlessPrewarm=true"), jar)
        self.assertLess(argv.index("-Dringworld.headlessPrewarmReport=result.json"), jar)
        neoforge, _ = self.plan("26.1-neoforge")
        self.assertEqual(neoforge.smoke.launch, neoforge.stages[0].launch)
        self.assertEqual(("./run.sh", "nogui"), neoforge.stages[0].launch.argv)

    def test_rejects_explicit_runtime_outside_qualification_cell(self) -> None:
        cell = self.cells["26.1-fabric"]
        paths = QualificationPaths.from_cell(ROOT, cell, "atlas-recovery-plan-reject")
        candidate = CandidateJar(ROOT / "dist/qualification/frozen-candidates/fabric/ringworld.jar", "a" * 64, "fabric")
        from external_runtime_smoke import external_runtime_smoke_plan
        with self.assertRaises(ValueError):
            external_runtime_smoke_plan(cell, candidate, paths, frozen_candidate_root=candidate.path.parent,
                                        runtime_root=ROOT / "outside-runtime")

    def test_patch_cell_binds_its_prior_quick_record_outside_oldest_abi_root(self) -> None:
        cell = self.cells["26.1.1-neoforge"]
        paths = QualificationPaths.from_cell(ROOT, cell, "atlas-current")
        quick_paths = QualificationPaths.from_cell(ROOT, cell, "atlas-prior-quick")
        frozen = ROOT / "dist/qualification/ringworld/26.1/neoforge/atlas-prior-quick/frozen-candidates/neoforge"
        candidate = CandidateJar(frozen / "ringworld.jar", "a" * 64, "neoforge")
        quick = QuickTerminalEvidenceInput(
            quick_paths.evidence_directory / "strict-terminal-evidence.json", "b" * 64,
        )
        plan = external_runtime_atlas_recovery_plan(
            cell, candidate, paths, quick, frozen_candidate_root=frozen,
            quick_evidence_root=quick_paths.cell_root,
        )
        self.assertEqual("26.1.1-neoforge", plan.smoke.cell_id)
        with self.assertRaises(InvocationError):
            external_runtime_atlas_recovery_plan(
                cell, candidate, paths,
                QuickTerminalEvidenceInput(quick_paths.cell_root / "evidence/other.json", "b" * 64),
                frozen_candidate_root=frozen, quick_evidence_root=quick_paths.cell_root,
            )


if __name__ == "__main__":
    unittest.main()
