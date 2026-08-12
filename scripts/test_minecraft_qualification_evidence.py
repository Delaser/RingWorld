#!/usr/bin/env python3
"""Focused pure negative tests for terminal qualification evidence."""

from __future__ import annotations

from copy import deepcopy
import unittest

from minecraft_qualification_evidence import (
    EVIDENCE_SCHEMA_VERSION,
    TerminalEvidenceError,
    validate_terminal_evidence,
)


HASH = "a" * 64
OTHER_HASH = "b" * 64
RANGES = {
    "fabric": {
        "oldest_abi_minecraft_version": "26.1",
        "minecraft_range": ">=26.1 <=26.1.2",
        "loader_range": "",
    },
    "neoforge": {
        "oldest_abi_minecraft_version": "26.1",
        "minecraft_range": "[26.1,26.1.2]",
        "loader_range": "[26.1.0.19-beta,26.1.2.87]",
    },
}


def canonical_cells() -> dict[str, dict[str, object]]:
    world = {
        "seed": "ringworld-qualification-safe-small-v1",
        "circumference_blocks": 2048,
        "width_blocks": 416,
        "wall_height_blocks": 160,
        "pregenerate_terrain_atlas": False,
    }
    return {
        f"{version}-fabric": {
            "id": f"{version}-fabric", "minecraft_version": version, "loader": "fabric",
            "port": 27000 + index, "world_config": deepcopy(world),
        }
        for index, version in enumerate(("26.1", "26.1.1", "26.1.2"))
    }


def passing_record() -> dict[str, object]:
    cells = canonical_cells()
    cell = deepcopy(cells["26.1-fabric"])
    return {
        "schema_version": EVIDENCE_SCHEMA_VERSION,
        "verdict": "PASS",
        "cell": cell,
        "provenance": {
            "commit": "1" * 40,
            "clean": True,
            "public_origin": "https://github.com/Delaser/RingWorld.git",
            "manifest_sha256": HASH,
            "wrapper_sha256": HASH,
            "java": {"major": 25, "version": "25.0.1"},
            "platform": {"system": "Linux", "machine": "x86_64"},
        },
        "commands": [{
            "phase": "build-and-unit", "argv": ["./gradlew", "test"], "exit_code": 0,
            "started_at_utc": "2026-08-12T12:00:00Z", "ended_at_utc": "2026-08-12T12:00:01Z",
            "elapsed_seconds": 1.0, "stdout_path": "logs/build.out", "stdout_sha256": HASH,
            "stderr_path": "logs/build.err", "stderr_sha256": HASH,
        }],
        "installer": {
            "name": "Fabric Installer", "url": "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.1/fabric-installer-1.1.1.jar",
            "path": "cache/installer.jar", "sha256": HASH, "installed_sha256": HASH,
        },
        "runtime_inventory": [
            {"path": "mods/ringworld-candidate.jar", "sha256": HASH, "role": "ringworld"},
            {"path": "mods/fabric-api.jar", "sha256": OTHER_HASH, "role": "fabric-api"},
        ],
        "frozen_candidate": {
            "source_path": "candidate/ringworld.jar", "source_sha256": HASH,
            "installed_path": "mods/ringworld-candidate.jar", "installed_sha256": HASH,
            "oldest_abi_minecraft_version": "26.1", "minecraft_range": ">=26.1 <=26.1.2", "loader_range": "",
        },
        "markers": [
            {"name": "ringworld-bootstrap", "timestamp_utc": "2026-08-12T12:00:02Z"},
            {"name": "atlas-disabled", "timestamp_utc": "2026-08-12T12:00:03Z"},
            {"name": "server-ready", "timestamp_utc": "2026-08-12T12:00:04Z"},
            {"name": "server-stop", "timestamp_utc": "2026-08-12T12:00:05Z"},
            {"name": "world-save", "timestamp_utc": "2026-08-12T12:00:06Z"},
        ],
        "runtime": {"exit_code": 0, "clean_stop": True, "clean_exit": True, "crash_detected": False},
        "same_file": {"group": "26.1.x-fabric", "sha256": HASH, "cell_ids": ["26.1-fabric", "26.1.1-fabric", "26.1.2-fabric"]},
    }


class TerminalEvidenceTest(unittest.TestCase):
    def assert_invalid(self, record: dict[str, object]) -> None:
        with self.assertRaises(TerminalEvidenceError):
            validate_terminal_evidence(record, canonical_cells(), RANGES)

    def test_valid_pass_and_nonpass_minimum(self) -> None:
        result = validate_terminal_evidence(passing_record(), canonical_cells(), RANGES)
        self.assertEqual(("26.1-fabric", "PASS", HASH), (result.cell_id, result.verdict, result.candidate_sha256))
        incomplete = {"schema_version": EVIDENCE_SCHEMA_VERSION, "verdict": "INCOMPLETE", "cell": canonical_cells()["26.1-fabric"], "reason": "NO_RUNTIME"}
        self.assertEqual("INCOMPLETE", validate_terminal_evidence(incomplete, canonical_cells(), RANGES).verdict)

    def test_pass_requires_every_evidence_family(self) -> None:
        for family in ("provenance", "commands", "installer", "runtime_inventory", "frozen_candidate", "markers", "runtime", "same_file"):
            with self.subTest(family=family):
                record = passing_record()
                del record[family]
                self.assert_invalid(record)

    def test_rejects_schema_unknown_field_and_nonpass_without_reason(self) -> None:
        record = passing_record()
        record["schema_version"] = 99
        self.assert_invalid(record)
        record = passing_record()
        record["unexpected"] = True
        self.assert_invalid(record)
        self.assert_invalid({"schema_version": EVIDENCE_SCHEMA_VERSION, "verdict": "FAIL", "cell": canonical_cells()["26.1-fabric"]})

    def test_rejects_noncanonical_identity_and_world_or_port_swap(self) -> None:
        record = passing_record()
        record["cell"]["minecraft_version"] = "26.1.2"  # type: ignore[index]
        self.assert_invalid(record)
        record = passing_record()
        record["cell"]["world_config"]["width_blocks"] = 999  # type: ignore[index]
        self.assert_invalid(record)
        record = passing_record()
        record["cell"]["port"] = 25565  # type: ignore[index]
        self.assert_invalid(record)

    def test_rejects_bad_provenance_and_command_contract(self) -> None:
        record = passing_record()
        record["provenance"]["clean"] = False  # type: ignore[index]
        self.assert_invalid(record)
        record = passing_record()
        record["commands"][0]["exit_code"] = 1  # type: ignore[index]
        self.assert_invalid(record)
        record = passing_record()
        record["commands"][0]["stdout_sha256"] = "UPPER"  # type: ignore[index]
        self.assert_invalid(record)

    def test_rejects_installer_inventory_and_candidate_mismatches(self) -> None:
        record = passing_record()
        record["installer"]["installed_sha256"] = OTHER_HASH  # type: ignore[index]
        self.assert_invalid(record)
        record = passing_record()
        record["runtime_inventory"].append(deepcopy(record["runtime_inventory"][0]))  # type: ignore[index]
        self.assert_invalid(record)
        record = passing_record()
        record["frozen_candidate"]["installed_sha256"] = OTHER_HASH  # type: ignore[index]
        self.assert_invalid(record)
        record = passing_record()
        record["frozen_candidate"]["minecraft_range"] = ">=26.1 <=26.1.3"  # type: ignore[index]
        self.assert_invalid(record)

    def test_rejects_duplicate_or_unordered_markers_unclean_runtime_and_same_file_mismatch(self) -> None:
        record = passing_record()
        record["markers"][2]["name"] = "atlas-disabled"  # type: ignore[index]
        self.assert_invalid(record)
        record = passing_record()
        record["markers"][3]["timestamp_utc"] = "2026-08-12T12:00:03Z"  # type: ignore[index]
        self.assert_invalid(record)
        record = passing_record()
        record["runtime"]["crash_detected"] = True  # type: ignore[index]
        self.assert_invalid(record)
        record = passing_record()
        record["same_file"]["sha256"] = OTHER_HASH  # type: ignore[index]
        self.assert_invalid(record)
        record = passing_record()
        record["same_file"]["cell_ids"] = ["26.1-fabric", "26.1-fabric", "26.1.2-fabric"]  # type: ignore[index]
        self.assert_invalid(record)


if __name__ == "__main__":
    unittest.main()
