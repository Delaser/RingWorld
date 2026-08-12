#!/usr/bin/env python3
"""Pure contract tests for the Phase 4 nightly qualification planner."""

from __future__ import annotations

import json
from pathlib import Path
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from external_runtime_smoke import CandidateJar  # noqa: E402
from minecraft_nightly_qualification_model import (  # noqa: E402
    NIGHTLY_PRODUCTION_TIMEOUT_SECONDS,
    NIGHTLY_SAFE_SMALL_TIMEOUT_SECONDS,
    NightlyFixture,
    ImmutableNightlyInput,
    NightlySourceInputs,
    nightly_qualification_plan,
)
from minecraft_qualification_model import InvocationError, QualificationPaths  # noqa: E402


HASH = "a" * 64


class NightlyQualificationModelTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        data = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        cls.cell = next(cell for cell in data["cells"] if cell["id"] == "26.1-fabric")

    def paths(self, root: Path) -> QualificationPaths:
        return QualificationPaths.from_cell(root, self.cell, "20260812T120000Z-0123456789ab")

    @staticmethod
    def inputs(root: Path, *, production: bool = True) -> NightlySourceInputs:
        frozen = root / "dist" / "qualification" / "ringworld" / "26.1" / "fabric" / "run" / "frozen-candidates" / "fabric" / "ringworld.jar"
        quick = root / "dist" / "qualification" / "quick-terminal-evidence.json"
        world = root / "fixtures" / "production-world.zip"
        return NightlySourceInputs(
            CandidateJar(frozen, HASH, "fabric", ">=26.1 <=26.1.2"),
            ImmutableNightlyInput("quick-terminal-evidence", quick, HASH),
            ImmutableNightlyInput("production-world", world, HASH) if production else None,
        )

    def test_complete_plan_is_ordered_distinct_and_contained(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = self.paths(root)
            plan = nightly_qualification_plan(self.cell, paths, self.inputs(root))
            self.assertEqual("26.1-fabric", plan.cell_id)
            self.assertEqual(11, len(plan.fixture_plans))
            self.assertEqual(tuple(range(1, 12)), tuple(item.ordinal for item in plan.fixture_plans))
            self.assertEqual(NightlyFixture.CREATION_SETTINGS_UI, plan.fixture_plans[0].fixture)
            self.assertEqual(NightlyFixture.PRODUCTION_ATLAS_RENDER, plan.fixture_plans[-1].fixture)
            self.assertEqual(NIGHTLY_SAFE_SMALL_TIMEOUT_SECONDS, plan.fixture_plans[0].timeout_seconds)
            self.assertEqual(NIGHTLY_PRODUCTION_TIMEOUT_SECONDS, plan.fixture_plans[-1].timeout_seconds)
            self.assertEqual(11, len({item.port for item in plan.fixture_plans}))
            self.assertTrue(all(item.port > self.cell["profile"]["server_port"] for item in plan.fixture_plans))
            for fixture in plan.fixture_plans:
                self.assertEqual("fixture-pass", fixture.required_markers[-1])
                self.assertEqual(len(fixture.required_markers), len(set(fixture.required_markers)))
                self.assertTrue(str(fixture.runtime_root).startswith(str(paths.cell_root)))
                self.assertTrue(str(fixture.world_root).startswith(str(paths.cell_root)))
                self.assertEqual(fixture.runtime_root / "world", fixture.world_root)
                self.assertTrue(str(fixture.evidence_json).startswith(str(paths.cell_root)))
                self.assertEqual(fixture.evidence_json, fixture.required_outputs[0])
                self.assertEqual(fixture.evidence_markdown, fixture.required_outputs[1])
            self.assertEqual(
                ("frozen-candidate", "quick-terminal-evidence", "production-world"),
                plan.fixture_plans[-1].input_roles,
            )
            lifecycle = next(item for item in plan.fixture_plans if item.fixture is NightlyFixture.LIFECYCLE_PORTALS)
            self.assertEqual(
                ("frozen-candidate", "quick-terminal-evidence", "production-world"),
                lifecycle.input_roles,
            )

    def test_missing_production_world_fails_closed_before_any_runtime_plan(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(InvocationError):
                nightly_qualification_plan(self.cell, self.paths(Path(directory)), self.inputs(Path(directory), production=False))

    def test_rejects_wrong_loader_hash_paths_and_paths_cell_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = self.paths(root)
            wrong_loader = NightlySourceInputs(
                CandidateJar(Path("/frozen.jar"), HASH, "neoforge"),
                ImmutableNightlyInput("quick-terminal-evidence", Path("/quick.json"), HASH),
                ImmutableNightlyInput("production-world", Path("/world.zip"), HASH),
            )
            with self.assertRaises(InvocationError):
                nightly_qualification_plan(self.cell, paths, wrong_loader)
            bad_hash = self.inputs(root)
            object.__setattr__(bad_hash.frozen_candidate, "sha256", "bad")
            with self.assertRaises(InvocationError):
                nightly_qualification_plan(self.cell, paths, bad_hash)
            other = dict(self.cell)
            other["id"] = "26.1.1-fabric"
            with self.assertRaises(InvocationError):
                nightly_qualification_plan(other, paths, self.inputs(root))

    def test_rejects_bad_quick_role_and_fixture_port_overflow(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = self.paths(root)
            source = self.inputs(root)
            bad_role = NightlySourceInputs(
                source.frozen_candidate,
                ImmutableNightlyInput("wrong", source.quick_terminal_evidence.path, HASH),
                source.production_world,
            )
            with self.assertRaises(InvocationError):
                nightly_qualification_plan(self.cell, paths, bad_role)
            overflowing = {**self.cell, "profile": {**self.cell["profile"], "server_port": 65530}}
            with self.assertRaises(InvocationError):
                nightly_qualification_plan(overflowing, paths, source)


if __name__ == "__main__":
    unittest.main()
