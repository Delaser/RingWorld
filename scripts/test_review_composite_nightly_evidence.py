#!/usr/bin/env python3
"""Focused contracts for explicit composite nightly evidence review."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT / "scripts") not in sys.path:
    sys.path.insert(0, str(ROOT / "scripts"))

from review_composite_nightly_evidence import CompositeEvidenceError, review  # noqa: E402


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class CompositeEvidenceReviewTest(unittest.TestCase):
    def terminal(self, root: Path, cell: str, fixture: str, *, exact: bool, artifact: Path | None = None) -> Path:
        path = root / f"{cell}-{fixture}.json"
        record: dict[str, object] = {"verdict": "PASS", "cell": cell, "fixture": fixture,
                                     "claims": {"frozen_candidate_jar": False}}
        if exact:
            record["frozen_candidate"] = {"sha256": "n" * 64}
            record["quick_evidence"] = {"sha256": "q" * 64}
        if artifact is not None:
            record["captures"] = [{"path": str(artifact), "sha256": digest(artifact)}]
        path.write_text(json.dumps(record), encoding="utf-8")
        return path

    def result(self, cell: str, fixture: str, verdict: str, terminal: Path | None = None,
               retained: list[dict[str, object]] | None = None) -> dict[str, object]:
        return {"cell": cell, "fixture": fixture, "verdict": verdict,
                "terminal_evidence": ([] if terminal is None else [{"path": str(terminal), "sha256": digest(terminal)}]),
                "retained_artifacts": retained or []}

    def test_explicit_composite_preserves_failed_primary_and_evidence_classes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact = root / "retained.png"
            artifact.write_bytes(b"capture")
            fabric = self.terminal(root, "26.1-fabric", "creation-settings-ui", exact=False)
            raid = self.terminal(root, "26.1-neoforge", "raid-seam", exact=True)
            downstream = {fixture: self.terminal(root, "26.1-neoforge", name,
                                                   exact=fixture in {"production-lifecycle", "production-render"},
                                                   artifact=artifact if fixture == "map-compass" else None)
                          for fixture, name in {"map-compass": "map-compass-reconnect",
                                                "production-lifecycle": "production-lifecycle",
                                                "curved-objects": "curved-objects",
                                                "production-render": "production-atlas-render"}.items()}
            primary = root / "primary.json"
            primary.write_text(json.dumps({"verdict": "FAIL", "results": [
                self.result("26.1-fabric", "creation-ui", "PASS", fabric),
                self.result("26.1-neoforge", "raid", "FAIL"),
                *(self.result("26.1-neoforge", fixture, "INCOMPLETE") for fixture in downstream),
            ]}), encoding="utf-8")
            downstream_aggregate = root / "downstream.json"
            downstream_aggregate.write_text(json.dumps({"results": [
                self.result("26.1-neoforge", fixture, "PASS", terminal,
                            [{"source_path": str(artifact), "retained_path": str(artifact), "sha256": digest(artifact)}]
                            if fixture == "map-compass" else [])
                for fixture, terminal in downstream.items()]}), encoding="utf-8")
            records = ({"cell": "26.1-fabric", "sha256": "q" * 64},
                       {"cell": "26.1-neoforge", "sha256": "q" * 64})
            with patch("review_composite_nightly_evidence.validate_quick_matrix", return_value=({"fabric": "f" * 64, "neoforge": "n" * 64}, records)), \
                 patch("review_composite_nightly_evidence.load_manifest", return_value={"cells": []}), \
                 patch("review_composite_nightly_evidence.contract_from_manifest", return_value=object()):
                report = review(repository=root, manifest_path=root / "manifest.json", quick_run_id="quick",
                                primary=primary, raid_repair=raid, downstream=downstream_aggregate,
                                downstream_fixtures=tuple(downstream))
            self.assertEqual("COMPOSITE_COVERAGE_REVIEWED", report["verdict"])
            self.assertFalse(report["monolithic_pass"])
            self.assertEqual({"exact-frozen-candidate", "source-abi"},
                             {item["terminals"][0]["evidence_class"] for item in report["coverage"]})

    def test_rejects_duplicate_or_incomplete_explicit_downstream_selection(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            primary = root / "primary.json"
            primary.write_text(json.dumps({"verdict": "FAIL", "results": [
                self.result("26.1-neoforge", "raid", "FAIL"),
                self.result("26.1-neoforge", "map-compass", "INCOMPLETE"),
            ]}), encoding="utf-8")
            raid = self.terminal(root, "26.1-neoforge", "raid-seam", exact=True)
            downstream = root / "downstream.json"
            downstream.write_text(json.dumps({"results": []}), encoding="utf-8")
            with patch("review_composite_nightly_evidence.validate_quick_matrix", return_value=({"fabric": "f" * 64, "neoforge": "n" * 64}, ({"cell": "26.1-neoforge", "sha256": "q" * 64},))), \
                 patch("review_composite_nightly_evidence.load_manifest", return_value={"cells": []}), \
                 patch("review_composite_nightly_evidence.contract_from_manifest", return_value=object()):
                with self.assertRaisesRegex(CompositeEvidenceError, "duplicates"):
                    review(repository=root, manifest_path=root / "manifest.json", quick_run_id="quick",
                           primary=primary, raid_repair=raid, downstream=downstream,
                           downstream_fixtures=("map-compass", "map-compass"))


if __name__ == "__main__":
    unittest.main()
