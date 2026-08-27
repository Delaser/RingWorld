#!/usr/bin/env python3
"""Pure contract tests for copied-world forward upgrades."""

from __future__ import annotations

from dataclasses import replace
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT / "scripts") not in sys.path:
    sys.path.insert(0, str(ROOT / "scripts"))

from minecraft_atlas_recovery_qualification import PersistedRingSettingsObservation  # noqa: E402
from minecraft_qualification_model import InvocationError  # noqa: E402
from minecraft_world_upgrade_qualification import (  # noqa: E402
    ForwardUpgradeEvidence, ForwardUpgradeIdentity, validate_forward_world_upgrade,
)
from test_minecraft_worldgen_qualification import WorldgenQualificationTest  # noqa: E402


HASH = "a" * 64


class ForwardUpgradeQualificationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        matrix = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        cls.source = next(cell for cell in matrix["cells"] if cell["id"] == "26.1-fabric")
        cls.target = next(cell for cell in matrix["cells"] if cell["id"] == "26.1.1-fabric")

    def valid(self, root: Path):
        # Reuse the exact parsed production-resume facts from fixture 02's
        # pure test; forward validation must call the same reload comparator.
        _, matrix = WorldgenQualificationTest().valid(root / "matrix")
        record = matrix.stages[1].log.record
        source_world = root / "source/world"
        target_world = root / "run/nightly/05-world-upgrade/runtime/world"
        settings = lambda world: PersistedRingSettingsObservation(
            256, 16_384, 1, 160, 64, 4, 3,
            world / "dimensions/minecraft/overworld/data/ringworld/settings.dat", HASH,
        )
        identity = ForwardUpgradeIdentity(
            "26.1-fabric", "26.1.1-fabric", "fabric", "26.1", "26.1.1",
            HASH, "b" * 64, "c" * 64,
        )
        evidence = ForwardUpgradeEvidence(
            root / "run/nightly/05-world-upgrade", root / "evidence/nightly/05-world-upgrade", root / "logs",
            source_world, target_world, settings(source_world), settings(target_world), record, dict(record),
        )
        return identity, evidence

    def test_accepts_supported_copy_with_exact_settings_and_resume_facts(self):
        with tempfile.TemporaryDirectory() as directory:
            identity, evidence = self.valid(Path(directory))
            result = validate_forward_world_upgrade(self.source, self.target, identity, evidence)
            self.assertEqual("26.1.1", result.as_dict()["targetMinecraft"])

    def test_rejects_downgrade_settings_or_resume_drift(self):
        with tempfile.TemporaryDirectory() as directory:
            identity, evidence = self.valid(Path(directory))
            with self.assertRaises(InvocationError):
                validate_forward_world_upgrade(self.target, self.source, identity, evidence)
            with self.assertRaises(InvocationError):
                validate_forward_world_upgrade(
                    self.source, self.target, identity,
                    replace(evidence, target_settings=replace(evidence.target_settings, width_blocks=128)),
                )
            with self.assertRaises(InvocationError):
                validate_forward_world_upgrade(
                    self.source, self.target, identity,
                    replace(evidence, target_record={**evidence.target_record, "loot": 2}),
                )

    def test_accepts_a_later_candidate_group_without_a_path_allowlist(self):
        with tempfile.TemporaryDirectory() as directory:
            identity, evidence = self.valid(Path(directory))
            source = {**self.source, "id": "26.1-fabric", "minecraft": {"version": "26.1"}}
            target = {**self.target, "id": "26.2-fabric", "minecraft": {"version": "26.2"}}
            identity = replace(identity, target_cell_id="26.2-fabric", target_minecraft_version="26.2")
            result = validate_forward_world_upgrade(source, target, identity, evidence)
            self.assertEqual("26.2", result.as_dict()["targetMinecraft"])
            with self.assertRaises(InvocationError):
                validate_forward_world_upgrade(source, source, identity, evidence)


if __name__ == "__main__":
    unittest.main()
