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
    review_repairs,
)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class CompositeEvidenceReviewTest(unittest.TestCase):
    fixtures = ("raid", "map-compass", "production-lifecycle", "curved-objects", "production-render")
    manifest = {"cells": [{"id": "26.1-neoforge", "loader": "neoforge",
                           "minecraft": {"version": "26.1"}}]}

    def terminal(self, root: Path, fixture: str, *, exact: bool, loader: str = "neoforge",
                 candidate: str = "n" * 64, quick: str = "q" * 64, artifact: Path | None = None,
                 commit: str | None = None, actual_fixture: str | None = None, name: str | None = None) -> Path:
        names = {"raid": "raid-seam", "map-compass": "map-compass-reconnect",
                 "production-lifecycle": "production-lifecycle", "curved-objects": "curved-objects",
                 "production-render": "production-atlas-render", "atlas-ui": "atlas-ui-revision"}
        path = root / f"{name or fixture}.json"
        record: dict[str, object] = {
            "verdict": "PASS", "cell": "26.1-neoforge", "fixture": actual_fixture or names[fixture],
            "loader": loader, "minecraft": "26.1", "claims": {"frozen_candidate_jar": exact},
        }
        if exact:
            record["fixture"] = "frozen-" + names[fixture]
            record["frozen_candidate"] = {"sha256": candidate}
            record["quick_evidence"] = {"sha256": quick}
        if artifact is not None:
            record["captures"] = [{"path": str(artifact), "sha256": digest(artifact)}]
        if commit is not None:
            record["source"] = {"commit": commit}
        path.write_text(json.dumps(record), encoding="utf-8")
        return path

    def result(self, fixture: str, verdict: str, terminal: Path | None = None,
               retained: list[dict[str, object]] | None = None) -> dict[str, object]:
        return {"cell": "26.1-neoforge", "fixture": fixture, "verdict": verdict,
                "terminal_evidence": ([] if terminal is None else [{"path": str(terminal), "sha256": digest(terminal)}]),
                "retained_artifacts": retained or []}

    def generic_invoke(self, root: Path, primary: Path, repairs: list[Path],
                       fixtures: tuple[str, ...] | None = None) -> dict[str, object]:
        records = ({"cell": "26.1-neoforge", "sha256": "q" * 64},)
        with patch("review_composite_nightly_evidence.FIXTURES", fixtures or self.fixtures), \
             patch("review_composite_nightly_evidence.validate_quick_matrix", return_value=({"neoforge": "n" * 64}, records)), \
             patch("review_composite_nightly_evidence.load_manifest", return_value=self.manifest), \
             patch("review_composite_nightly_evidence.contract_from_manifest", return_value=object()):
            return review_repairs(repository=root, manifest_path=root / "manifest.json", quick_run_id="quick",
                                  primary=primary, repairs=repairs)

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

    def test_generic_repair_covers_only_failed_keys_with_terminal_provenance(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            primary = root / "primary.json"
            primary.write_text(json.dumps({"verdict": "FAIL", "quick_run_id": "quick",
                "started_source_commit": "a" * 40, "results": [
                    self.result("raid", "PASS", self.terminal(root, "raid", exact=True, commit="a" * 40)),
                    self.result("map-compass", "PASS", self.terminal(root, "map-compass", exact=False, commit="a" * 40)),
                    *(self.result(fixture, "FAIL") for fixture in
                      ("production-lifecycle", "curved-objects", "production-render")),
                ]}), encoding="utf-8")
            repair = root / "repair.json"
            repaired = []
            for fixture in ("production-lifecycle", "curved-objects", "production-render"):
                terminal = self.terminal(root, fixture, exact=fixture != "curved-objects", commit="b" * 40)
                repaired.append(self.result(fixture, "PASS", terminal))
            repair.write_text(json.dumps({"verdict": "INCOMPLETE", "quick_run_id": "quick", "started_source_commit": "b" * 40,
                                          "results": repaired}), encoding="utf-8")
            report = self.generic_invoke(root, primary, [repair])
            self.assertEqual("COMPOSITE_COVERAGE_REVIEWED", report["verdict"])
            self.assertEqual(5, len(report["coverage"]))
            self.assertEqual(digest(primary), report["primary_aggregate_sha256"])
            self.assertEqual(digest(repair), report["repair_aggregates"][0]["sha256"])
            classes = {item["fixture"]: item["terminals"][0]["evidence_class"] for item in report["coverage"]}
            self.assertEqual("source-abi", classes["curved-objects"])
            self.assertEqual("exact-frozen-candidate", classes["production-render"])

    def test_generic_repair_rejects_primary_pass_duplicate_and_source_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            primary = root / "primary.json"
            primary.write_text(json.dumps({"verdict": "FAIL", "quick_run_id": "quick",
                "started_source_commit": "a" * 40, "results": [
                    self.result(fixture, "FAIL" if fixture == "curved-objects" else "PASS",
                                self.terminal(root, fixture, exact=fixture in {"raid", "production-lifecycle", "production-render"},
                                              commit="a" * 40))
                    for fixture in self.fixtures]}), encoding="utf-8")
            terminal = self.terminal(root, "curved-objects", exact=False)
            terminal.write_text(json.dumps({**json.loads(terminal.read_text()), "source": {"commit": "c" * 40}}))
            repair = root / "repair.json"
            repair.write_text(json.dumps({"verdict": "INCOMPLETE", "quick_run_id": "quick", "started_source_commit": "b" * 40,
                                          "results": [self.result("curved-objects", "PASS", terminal)]}), encoding="utf-8")
            with self.assertRaisesRegex(CompositeEvidenceError, "source commit"):
                self.generic_invoke(root, primary, [repair])
            terminal.write_text(json.dumps({**json.loads(terminal.read_text()), "source": {"commit": "b" * 40}}))
            bad = root / "bad.json"
            bad.write_text(json.dumps({"verdict": "INCOMPLETE", "quick_run_id": "quick", "started_source_commit": "b" * 40,
                                       "results": [self.result("raid", "PASS", self.terminal(root, "raid", exact=True))]}), encoding="utf-8")
            with self.assertRaisesRegex(CompositeEvidenceError, "primary PASS"):
                self.generic_invoke(root, primary, [repair, bad])

    def test_generic_requires_atlas_ui_and_handshake_terminal_pair(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            primary = root / "primary.json"
            primary.write_text(json.dumps({"verdict": "FAIL", "quick_run_id": "quick", "started_source_commit": "a" * 40,
                                           "results": [self.result("atlas-ui", "FAIL")]}), encoding="utf-8")
            handshake = self.terminal(root, "atlas-ui", exact=False, commit="b" * 40,
                                      actual_fixture="client-handshake")
            repair = root / "repair.json"
            repair.write_text(json.dumps({"verdict": "INCOMPLETE", "quick_run_id": "quick", "started_source_commit": "b" * 40,
                                          "results": [self.result("atlas-ui", "PASS", handshake)]}), encoding="utf-8")
            with self.assertRaisesRegex(CompositeEvidenceError, "Atlas UI coverage"):
                self.generic_invoke(root, primary, [repair], fixtures=("atlas-ui",))
            ui = self.terminal(root, "atlas-ui", exact=False, commit="b" * 40, name="atlas-ui-revision")
            repair.write_text(json.dumps({"verdict": "INCOMPLETE", "quick_run_id": "quick", "started_source_commit": "b" * 40,
                                          "results": [{**self.result("atlas-ui", "PASS", ui), "terminal_evidence": [
                                              {"path": str(ui), "sha256": digest(ui)},
                                              {"path": str(handshake), "sha256": digest(handshake)}]}]}), encoding="utf-8")
            self.assertEqual("COMPOSITE_COVERAGE_REVIEWED",
                             self.generic_invoke(root, primary, [repair], fixtures=("atlas-ui",))["verdict"])

    def test_generic_rejects_duplicate_missing_unknown_bad_binding_and_artifact(self) -> None:
        def primary(root: Path, fixtures: tuple[str, ...]) -> Path:
            path = root / "primary.json"
            path.write_text(json.dumps({"verdict": "FAIL", "quick_run_id": "quick", "started_source_commit": "a" * 40,
                                        "results": [self.result(fixture, "FAIL") for fixture in fixtures]}), encoding="utf-8")
            return path

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            curve = self.terminal(root, "curved-objects", exact=False, commit="b" * 40)
            repair = root / "repair.json"
            repair.write_text(json.dumps({"verdict": "INCOMPLETE", "quick_run_id": "quick", "started_source_commit": "b" * 40,
                                          "results": [self.result("curved-objects", "PASS", curve)]}), encoding="utf-8")
            with self.assertRaisesRegex(CompositeEvidenceError, "duplicate"):
                self.generic_invoke(root, primary(root, ("curved-objects",)), [repair, repair],
                                    fixtures=("curved-objects",))
            with self.assertRaisesRegex(CompositeEvidenceError, "do not cover"):
                self.generic_invoke(root, primary(root, ("curved-objects", "map-compass")), [repair],
                                    fixtures=("curved-objects", "map-compass"))
            unknown = root / "unknown.json"
            unknown.write_text(json.dumps({"verdict": "INCOMPLETE", "quick_run_id": "quick", "started_source_commit": "b" * 40,
                                           "results": [{**self.result("curved-objects", "PASS", curve), "fixture": "unknown"}]}), encoding="utf-8")
            with self.assertRaisesRegex(CompositeEvidenceError, "unknown key"):
                self.generic_invoke(root, primary(root, ("curved-objects",)), [unknown], fixtures=("curved-objects",))
            bad_exact = self.terminal(root, "production-render", exact=True, candidate="f" * 64, quick="x" * 64, commit="b" * 40)
            bad = root / "bad.json"
            bad.write_text(json.dumps({"verdict": "INCOMPLETE", "quick_run_id": "quick", "started_source_commit": "b" * 40,
                                       "results": [self.result("production-render", "PASS", bad_exact)]}), encoding="utf-8")
            with self.assertRaisesRegex(CompositeEvidenceError, "quick candidate"):
                self.generic_invoke(root, primary(root, ("production-render",)), [bad], fixtures=("production-render",))
            missing = root / "missing.png"
            map_terminal = self.terminal(root, "map-compass", exact=False, commit="b" * 40)
            data = json.loads(map_terminal.read_text())
            data["captures"] = [{"path": str(missing), "sha256": "a" * 64}]
            map_terminal.write_text(json.dumps(data), encoding="utf-8")
            artifact = root / "artifact.json"
            artifact.write_text(json.dumps({"verdict": "INCOMPLETE", "quick_run_id": "quick", "started_source_commit": "b" * 40,
                "results": [self.result("map-compass", "PASS", map_terminal)]}), encoding="utf-8")
            with self.assertRaisesRegex(CompositeEvidenceError, "neither present nor retained"):
                self.generic_invoke(root, primary(root, ("map-compass",)), [artifact], fixtures=("map-compass",))
            bad_copy = root / "bad.png"
            bad_copy.write_bytes(b"wrong")
            payload = json.loads(artifact.read_text())
            payload["results"][0]["retained_artifacts"] = [{"source_path": str(missing), "retained_path": str(bad_copy),
                                                                 "sha256": "a" * 64}]
            artifact.write_text(json.dumps(payload), encoding="utf-8")
            with self.assertRaisesRegex(CompositeEvidenceError, "hash is invalid"):
                self.generic_invoke(root, primary(root, ("map-compass",)), [artifact], fixtures=("map-compass",))

    def test_generic_rejects_invalid_primary_and_repair_aggregate_verdicts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            terminal = self.terminal(root, "curved-objects", exact=False, commit="b" * 40)
            primary = root / "primary.json"
            primary.write_text(json.dumps({"verdict": "FAIL", "quick_run_id": "quick", "started_source_commit": "a" * 40,
                                           "results": [self.result("curved-objects", "BROKEN")]}), encoding="utf-8")
            repair = root / "repair.json"
            repair.write_text(json.dumps({"verdict": "INCOMPLETE", "quick_run_id": "quick", "started_source_commit": "b" * 40,
                                          "results": [self.result("curved-objects", "PASS", terminal)]}), encoding="utf-8")
            with self.assertRaisesRegex(CompositeEvidenceError, "invalid result verdict"):
                self.generic_invoke(root, primary, [repair], fixtures=("curved-objects",))
            primary.write_text(json.dumps({"verdict": "FAIL", "quick_run_id": "quick", "started_source_commit": "a" * 40,
                                           "results": [self.result("curved-objects", "FAIL")]}), encoding="utf-8")
            repair.write_text(json.dumps({"verdict": "FAIL", "quick_run_id": "quick", "started_source_commit": "b" * 40,
                                          "results": [self.result("curved-objects", "PASS", terminal)]}), encoding="utf-8")
            with self.assertRaisesRegex(CompositeEvidenceError, "PASS or partial-selected INCOMPLETE"):
                self.generic_invoke(root, primary, [repair], fixtures=("curved-objects",))


if __name__ == "__main__":
    unittest.main()
