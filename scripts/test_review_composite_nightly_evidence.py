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

from review_composite_nightly_evidence import (  # noqa: E402
    CompositeEvidenceError,
    _terminal,
    _verify_retained,
    review,
)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class CompositeEvidenceReviewTest(unittest.TestCase):
    fixtures = ("raid", "map-compass", "production-lifecycle", "curved-objects", "production-render")
    manifest = {"cells": [{"id": "26.1-neoforge", "loader": "neoforge",
                           "minecraft": {"version": "26.1"}}]}

    def terminal(self, root: Path, fixture: str, *, exact: bool, loader: str = "neoforge",
                 candidate: str = "n" * 64, artifact: Path | None = None) -> Path:
        names = {"raid": "raid-seam", "map-compass": "map-compass-reconnect",
                 "production-lifecycle": "production-lifecycle", "curved-objects": "curved-objects",
                 "production-render": "production-atlas-render"}
        path = root / f"{fixture}.json"
        record: dict[str, object] = {
            "verdict": "PASS", "cell": "26.1-neoforge", "fixture": names[fixture],
            "loader": loader, "minecraft": "26.1", "claims": {"frozen_candidate_jar": exact},
        }
        if exact:
            record["fixture"] = "frozen-" + names[fixture]
            record["frozen_candidate"] = {"sha256": candidate}
            record["quick_evidence"] = {"sha256": "q" * 64}
        if artifact is not None:
            record["captures"] = [{"path": str(artifact), "sha256": digest(artifact)}]
        path.write_text(json.dumps(record), encoding="utf-8")
        return path

    def result(self, fixture: str, verdict: str, terminal: Path | None = None,
               retained: list[dict[str, object]] | None = None) -> dict[str, object]:
        return {"cell": "26.1-neoforge", "fixture": fixture, "verdict": verdict,
                "terminal_evidence": ([] if terminal is None else [{"path": str(terminal), "sha256": digest(terminal)}]),
                "retained_artifacts": retained or []}

    def invoke(self, root: Path, primary: Path, raid: Path, downstream: Path, fixtures: tuple[str, ...]) -> dict[str, object]:
        records = ({"cell": "26.1-neoforge", "sha256": "q" * 64},)
        with patch("review_composite_nightly_evidence.FIXTURES", self.fixtures), \
             patch("review_composite_nightly_evidence.validate_quick_matrix", return_value=({"neoforge": "n" * 64}, records)), \
             patch("review_composite_nightly_evidence.load_manifest", return_value=self.manifest), \
             patch("review_composite_nightly_evidence.contract_from_manifest", return_value=object()):
            return review(repository=root, manifest_path=root / "manifest.json", quick_run_id="quick",
                          primary=primary, raid_repair=raid, downstream=downstream,
                          downstream_fixtures=fixtures)

    def test_explicit_composite_has_complete_manifest_coverage(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact = root / "retained.png"
            artifact.write_bytes(b"capture")
            raid = self.terminal(root, "raid", exact=True)
            downstream = {fixture: self.terminal(root, fixture,
                                                  exact=fixture in {"production-lifecycle", "production-render"},
                                                  artifact=artifact if fixture == "map-compass" else None)
                          for fixture in self.fixtures if fixture != "raid"}
            primary = root / "primary.json"
            primary.write_text(json.dumps({"verdict": "FAIL", "quick_run_id": "quick", "results": [
                self.result("raid", "FAIL"),
                *(self.result(fixture, "INCOMPLETE") for fixture in downstream),
            ]}), encoding="utf-8")
            downstream_aggregate = root / "downstream.json"
            downstream_aggregate.write_text(json.dumps({"quick_run_id": "quick", "results": [
                self.result(fixture, "PASS", terminal,
                            [{"source_path": str(artifact), "retained_path": str(artifact), "sha256": digest(artifact)}]
                            if fixture == "map-compass" else [])
                for fixture, terminal in downstream.items()]}), encoding="utf-8")
            report = self.invoke(root, primary, raid, downstream_aggregate, tuple(downstream))
            self.assertEqual("COMPOSITE_COVERAGE_REVIEWED", report["verdict"])
            self.assertFalse(report["monolithic_pass"])
            self.assertEqual({"exact-frozen-candidate", "source-abi"},
                             {item["terminals"][0]["evidence_class"] for item in report["coverage"]})

    def test_rejects_missing_manifest_fixture_and_wrong_quick_run(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            raid = self.terminal(root, "raid", exact=True)
            downstream = root / "downstream.json"
            downstream.write_text(json.dumps({"quick_run_id": "quick", "results": []}), encoding="utf-8")
            primary = root / "primary.json"
            primary.write_text(json.dumps({"verdict": "FAIL", "quick_run_id": "quick", "results": [
                self.result("raid", "FAIL"), self.result("map-compass", "INCOMPLETE"),
            ]}), encoding="utf-8")
            with self.assertRaisesRegex(CompositeEvidenceError, "exact manifest"):
                self.invoke(root, primary, raid, downstream, ("map-compass",))
            primary.write_text(json.dumps({"verdict": "FAIL", "quick_run_id": "wrong", "results": [
                *(self.result(fixture, "FAIL") for fixture in self.fixtures),
            ]}), encoding="utf-8")
            with self.assertRaisesRegex(CompositeEvidenceError, "quick run"):
                self.invoke(root, primary, raid, downstream, ())

    def test_rejects_wrong_loader_candidate_and_missing_or_corrupt_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            wrong_loader = self.terminal(root, "raid", exact=True, loader="fabric")
            with self.assertRaisesRegex(CompositeEvidenceError, "loader/Minecraft"):
                _terminal(wrong_loader, "26.1-neoforge", "raid", candidate_hash="n" * 64,
                          quick_hash="q" * 64, loader="neoforge", minecraft="26.1")
            wrong_candidate = self.terminal(root, "raid", exact=True, candidate="f" * 64)
            with self.assertRaisesRegex(CompositeEvidenceError, "quick candidate"):
                _terminal(wrong_candidate, "26.1-neoforge", "raid", candidate_hash="n" * 64,
                          quick_hash="q" * 64, loader="neoforge", minecraft="26.1")
            missing = root / "missing.png"
            with self.assertRaisesRegex(CompositeEvidenceError, "neither present nor retained"):
                _verify_retained({"retained_artifacts": []}, {(str(missing), "a" * 64)})
            retained = root / "retained.png"
            retained.write_bytes(b"bad")
            with self.assertRaisesRegex(CompositeEvidenceError, "hash is invalid"):
                _verify_retained({"retained_artifacts": [{"source_path": str(missing), "retained_path": str(retained),
                                                           "sha256": "a" * 64}]}, {(str(missing), "a" * 64)})


if __name__ == "__main__":
    unittest.main()
