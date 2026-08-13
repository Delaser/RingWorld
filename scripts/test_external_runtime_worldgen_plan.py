#!/usr/bin/env python3
"""Focused pure tests for the four-stage external worldgen plan."""

from __future__ import annotations

import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT / "scripts") not in sys.path:
    sys.path.insert(0, str(ROOT / "scripts"))

from external_runtime_atlas_recovery_plan import QuickTerminalEvidenceInput  # noqa: E402
from external_runtime_smoke import CandidateJar  # noqa: E402
from external_runtime_worldgen_plan import (  # noqa: E402
    external_runtime_worldgen_plan, external_runtime_worldgen_resume_stage,
)
from minecraft_qualification_model import InvocationError, QualificationPaths  # noqa: E402


class ExternalRuntimeWorldgenPlanTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        matrix = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        cls.cells = {cell["id"]: cell for cell in matrix["cells"]}

    def plan(self, cell_id: str):
        cell = self.cells[cell_id]
        paths = QualificationPaths.from_cell(ROOT, cell, "worldgen-plan")
        frozen = ROOT / "dist/qualification/frozen-candidates" / cell["loader"]
        return external_runtime_worldgen_plan(cell, CandidateJar(frozen / "ringworld.jar", "a" * 64, cell["loader"]), paths,
                                              QuickTerminalEvidenceInput(paths.evidence_directory / "strict-terminal-evidence.json", "b" * 64),
                                              frozen_candidate_root=frozen), paths

    def test_declares_exact_cases_shared_world_and_evidence(self):
        for cell_id in ("26.1-fabric", "26.1-neoforge"):
            plan, paths = self.plan(cell_id)
            self.assertEqual(paths.run_directory / "nightly/02-worldgen-seam-structures", plan.fixture_root)
            self.assertEqual(["production-fresh", "production-resume", "seam-crossing", "terminal-policy"], [stage.name for stage in plan.stages])
            self.assertEqual(plan.stages[0].world_root, plan.stages[1].world_root)
            self.assertEqual(3, len({stage.world_root for stage in plan.stages}))
            self.assertEqual((16384, 256, False), (plan.stages[0].circumference_blocks, plan.stages[0].width_blocks, plan.stages[0].resume))
            self.assertEqual((2048, 416), (plan.stages[2].circumference_blocks, plan.stages[2].width_blocks))
            self.assertTrue(dict(plan.stages[1].server_properties)["server-port"].isdigit())
            self.assertEqual(
                ("-Dringworld.strongholdTest=true", "-Dringworld.worldgenMatrix=true", "-Dringworld.strongholdTestResume=true"),
                plan.stages[1].jvm_properties,
            )
            self.assertEqual("ringworld-regression-1", dict(plan.stages[0].server_properties)["level-seed"])
            self.assertEqual("16384", dict(plan.stages[0].ringworld_properties)["circumferenceBlocks"])
            self.assertEqual(("worldgen-matrix-record", "stronghold-test-pass"), plan.stages[0].required_markers)

    def test_rejects_candidate_and_quick_evidence_escaping_roots(self):
        cell = self.cells["26.1-fabric"]
        paths = QualificationPaths.from_cell(ROOT, cell, "worldgen-plan-reject")
        frozen = ROOT / "dist/qualification/frozen-candidates/fabric"
        with self.assertRaises(InvocationError):
            external_runtime_worldgen_plan(cell, CandidateJar(ROOT / "outside.jar", "a" * 64, "fabric"), paths,
                                           QuickTerminalEvidenceInput(paths.evidence_directory / "strict-terminal-evidence.json", "b" * 64), frozen_candidate_root=frozen)
        with self.assertRaises(InvocationError):
            external_runtime_worldgen_plan(cell, CandidateJar(frozen / "ringworld.jar", "a" * 64, "fabric"), paths,
                                           QuickTerminalEvidenceInput(ROOT / "outside.json", "b" * 64), frozen_candidate_root=frozen)

    def test_patch_cell_accepts_only_its_exact_prior_quick_record(self):
        cell = self.cells["26.1.2-fabric"]
        paths = QualificationPaths.from_cell(ROOT, cell, "worldgen-current")
        quick_paths = QualificationPaths.from_cell(ROOT, cell, "worldgen-prior-quick")
        frozen = ROOT / "dist/qualification/ringworld/26.1/fabric/worldgen-prior-quick/frozen-candidates/fabric"
        quick = QuickTerminalEvidenceInput(
            quick_paths.evidence_directory / "strict-terminal-evidence.json", "b" * 64,
        )
        plan = external_runtime_worldgen_plan(
            cell, CandidateJar(frozen / "ringworld.jar", "a" * 64, "fabric"), paths, quick,
            frozen_candidate_root=frozen, quick_evidence_root=quick_paths.cell_root,
        )
        self.assertEqual("26.1.2-fabric", plan.cell_id)
        wrong_cell_root = quick_paths.cell_root.parent / "26.1-fabric"
        with self.assertRaises(InvocationError):
            external_runtime_worldgen_plan(
                cell, CandidateJar(frozen / "ringworld.jar", "a" * 64, "fabric"), paths,
                QuickTerminalEvidenceInput(wrong_cell_root / "evidence/strict-terminal-evidence.json", "b" * 64),
                frozen_candidate_root=frozen, quick_evidence_root=wrong_cell_root,
            )

    def test_copied_world_resume_stage_reuses_both_loader_runtime_contracts(self):
        for cell_id in ("26.1.1-fabric", "26.1.1-neoforge"):
            with self.subTest(cell=cell_id):
                plan, paths = self.plan(cell_id)
                stage = external_runtime_worldgen_resume_stage(
                    self.cells[cell_id], plan.candidate, paths,
                    frozen_candidate_root=plan.frozen_candidate_root,
                    fixture_root=paths.run_directory / "nightly/05-world-upgrade",
                    evidence_root=paths.evidence_directory / "nightly/05-world-upgrade",
                )
                self.assertEqual("production-resume", stage.name)
                self.assertTrue(stage.resume)
                self.assertEqual(stage.runtime_root / "world", stage.world_root)
                self.assertEqual("true", stage.jvm_properties[-1].split("=", 1)[1])


if __name__ == "__main__":
    unittest.main()
