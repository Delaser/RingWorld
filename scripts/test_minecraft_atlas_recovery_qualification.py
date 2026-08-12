#!/usr/bin/env python3
"""Pure negative and positive tests for the Atlas prewarm/recovery contract."""

from __future__ import annotations

from dataclasses import replace
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from minecraft_atlas_recovery_qualification import (  # noqa: E402
    ATLAS_REPORT_SCHEMA, EXPECTED_TOTAL_CELLS, EXPECTED_TOTAL_CHUNKS,
    AtlasCacheObservation, AtlasRecoveryEvidence, AtlasReportFact,
    INTERRUPTED_MARKERS, MarkerLedger, PersistedRingSettingsObservation,
    QualificationIdentity, RECOVERY_MARKERS, TimedMarker,
    validate_atlas_recovery_qualification,
)
from minecraft_qualification_model import InvocationError  # noqa: E402

HASH = "a" * 64


class AtlasRecoveryQualificationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        matrix = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        cls.cell = next(item for item in matrix["cells"] if item["id"] == "26.1-fabric")

    def valid(self, root: Path):
        runtime = root / "run" / "nightly" / "03-atlas-prewarm-recovery" / "runtime"
        world, evidence = runtime / "world", root / "evidence" / "nightly" / "03-atlas-prewarm-recovery"
        settings = PersistedRingSettingsObservation("101", "202", 4, 2048, 416, 160, world / "data" / "ringworld-settings.dat", HASH)
        atlas_path = world / "ringworld" / "terrain-atlas-v6.bin"
        def report(status, chunks, cells, capture, failure):
            return AtlasReportFact(ATLAS_REPORT_SCHEMA, status, True, "101", "202", 4, 2048, 416, chunks, EXPECTED_TOTAL_CHUNKS, cells, EXPECTED_TOTAL_CELLS, 1_000, atlas_path, world / "ringworld-prewarm" / "result.json", evidence / capture, failure)
        def atlas(hash_value):
            return AtlasCacheObservation("101", "202", 4, 2048, 416, atlas_path, hash_value)
        def ledger(stage, names, start):
            return MarkerLedger(stage, evidence / f"{stage}-markers.json", tuple(TimedMarker(name, start + number * 10) for number, name in enumerate(names)))
        value = AtlasRecoveryEvidence(runtime, world, evidence, settings, report("INTERRUPTED", 100, 100, "interrupted-result.json", "controlled test interruption"), report("COMPLETE", EXPECTED_TOTAL_CHUNKS, EXPECTED_TOTAL_CELLS, "complete-result.json", None), atlas("b" * 64), atlas("c" * 64), ledger("interrupted", INTERRUPTED_MARKERS, 10), ledger("recovery", RECOVERY_MARKERS, 100), 0, 0)
        return QualificationIdentity("26.1-fabric", "fabric", "26.1", HASH, "d" * 64), value

    def reject(self, mutate):
        with tempfile.TemporaryDirectory() as directory:
            identity, evidence = self.valid(Path(directory))
            bad_identity, bad_evidence = mutate(identity, evidence)
            with self.assertRaises(InvocationError):
                validate_atlas_recovery_qualification(self.cell, bad_identity, bad_evidence)

    def test_accepts_exact_contract(self):
        with tempfile.TemporaryDirectory() as directory:
            identity, evidence = self.valid(Path(directory))
            result = validate_atlas_recovery_qualification(self.cell, identity, evidence)
            self.assertEqual("atlas-prewarm-recovery", result.as_dict()["fixture"])
            self.assertEqual(EXPECTED_TOTAL_CELLS, result.as_dict()["totalCells"])

    def test_rejects_canonical_identity_and_independent_settings_binding(self):
        self.reject(lambda i, e: (replace(i, loader="neoforge"), e))
        self.reject(lambda i, e: (replace(i, frozen_candidate_sha256="bad"), e))
        self.reject(lambda i, e: (i, replace(e, settings=replace(e.settings, wall_height_blocks=159))))
        self.reject(lambda i, e: (i, replace(e, settings=replace(e.settings, terrain_noise_mapping=3))))
        self.reject(lambda i, e: (i, replace(e, recovered_report=replace(e.recovered_report, world_hash="999"))))

    def test_rejects_raw_report_schema_status_identity_totals_and_partial_rules(self):
        self.reject(lambda i, e: (i, replace(e, interrupted_report=replace(e.interrupted_report, schema_version=1))))
        self.reject(lambda i, e: (i, replace(e, interrupted_report=replace(e.interrupted_report, status="COMPLETE"))))
        self.reject(lambda i, e: (i, replace(e, interrupted_report=replace(e.interrupted_report, identity_available=False))))
        self.reject(lambda i, e: (i, replace(e, interrupted_report=replace(e.interrupted_report, completed_cells=0))))
        self.reject(lambda i, e: (i, replace(e, interrupted_report=replace(e.interrupted_report, completed_cells=EXPECTED_TOTAL_CELLS))))
        self.reject(lambda i, e: (i, replace(e, recovered_report=replace(e.recovered_report, completed_chunks=EXPECTED_TOTAL_CHUNKS - 1))))
        self.reject(lambda i, e: (i, replace(e, interrupted_report=replace(e.interrupted_report, elapsed_millis=-1))))
        self.reject(lambda i, e: (i, replace(e, recovered_report=replace(e.recovered_report, failure_reason="unexpected"))))

    def test_rejects_paths_atlas_bindings_and_exit_state(self):
        self.reject(lambda i, e: (i, replace(e, world_root=e.runtime_root / "other")))
        self.reject(lambda i, e: (i, replace(e, interrupted_report=replace(e.interrupted_report, atlas_path=Path("relative.bin")))))
        self.reject(lambda i, e: (i, replace(e, recovered_report=replace(e.recovered_report, captured_report_path=e.interrupted_report.captured_report_path))))
        self.reject(lambda i, e: (i, replace(e, recovered_atlas=replace(e.recovered_atlas, world_hash="999"))))
        self.reject(lambda i, e: (i, replace(e, recovered_atlas=replace(e.recovered_atlas, atlas_path=e.world_root / "other.bin"))))
        self.reject(lambda i, e: (i, replace(e, interrupted_exit_code=1)))

    def test_rejects_wrong_or_unordered_marker_ledgers(self):
        self.reject(lambda i, e: (i, replace(e, recovery_ledger=replace(e.recovery_ledger, path=e.interrupted_ledger.path))))
        self.reject(lambda i, e: (i, replace(e, recovery_ledger=replace(e.recovery_ledger, events=tuple(reversed(e.recovery_ledger.events))))))
        self.reject(lambda i, e: (i, replace(e, interrupted_ledger=replace(e.interrupted_ledger, events=(TimedMarker("atlas-started", 10), TimedMarker("wrong", 20))))))
        self.reject(lambda i, e: (i, replace(
            e, recovery_ledger=replace(e.recovery_ledger, events=tuple(
                TimedMarker(event.name, event.timestamp_millis - 100)
                for event in e.recovery_ledger.events
            ))
        )))


if __name__ == "__main__":
    unittest.main()
