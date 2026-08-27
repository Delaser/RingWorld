#!/usr/bin/env python3
"""Static contracts for the unattended nightly coordinator."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from run_minecraft_nightly_matrix import (  # noqa: E402
    FIXTURES, GRADLE_DOWNLOAD_FAILURE_REASON, NightlyMatrixError, _child_argv,
    _classified_infrastructure_reason, _cleanup_disposable_child_state, _cooldown,
    _retain_terminal_artifacts, _retryable_infrastructure_failure,
    _schedule_infrastructure_retry, _selected_fixtures, _terminal_markdown,
    _verify_terminals,
)


class MinecraftNightlyMatrixTest(unittest.TestCase):
    def arguments(self) -> argparse.Namespace:
        return argparse.Namespace(
            manifest="config/minecraft-version-matrix.json", quick_run_id="quick-run",
            gradle_dependency_cache="/cache", gradle_distribution_zip="/gradle.zip",
            gradle_loom_cache="/loom", production_world="/world",
            multiplayer_cooldown_seconds=120,
        )

    def test_default_fixture_order_is_complete_and_duplicates_fail(self) -> None:
        self.assertEqual(FIXTURES, _selected_fixtures(None))
        with self.assertRaisesRegex(NightlyMatrixError, "duplicates"):
            _selected_fixtures(("raid", "raid"))

    def test_production_command_binds_every_input(self) -> None:
        command = _child_argv(ROOT, "26.1-fabric", "production-render",
                              self.arguments(), Path("/world"))
        joined = " ".join(command)
        for value in ("--quick-run-id quick-run",
                      "--gradle-dependency-cache /cache",
                      "--gradle-distribution-zip /gradle.zip",
                      "--gradle-loom-cache /loom"):
            self.assertIn(value, joined)
        source_index = command.index("--source-world")
        self.assertEqual(str(Path("/world")), command[source_index + 1])

    def test_worldgen_command_does_not_receive_gradle_or_world_options(self) -> None:
        command = _child_argv(ROOT, "26.1-neoforge", "worldgen",
                              self.arguments(), Path("/world"))
        joined = " ".join(command)
        self.assertIn("--quick-run-id quick-run", joined)
        self.assertNotIn("--source-world", joined)
        self.assertNotIn("--gradle-dependency-cache", joined)

    def test_multiplayer_settle_is_forwarded_after_child_preparation(self) -> None:
        command = _child_argv(ROOT, "26.1-neoforge", "multiplayer",
                              self.arguments(), Path("/world"))
        index = command.index("--post-prepare-settle-seconds")
        self.assertEqual("120", command[index + 1])

    def test_raid_settle_is_forwarded_to_both_runtime_phases(self) -> None:
        command = _child_argv(ROOT, "26.1-fabric", "raid",
                              self.arguments(), Path("/world"))
        index = command.index("--phase-settle-seconds")
        self.assertEqual("120", command[index + 1])

    def test_26_2_two_cell_selection_has_manifest_derived_command_and_report(self) -> None:
        arguments = self.arguments()
        arguments.manifest = "config/minecraft-version-matrix-26.2.json"
        command = _child_argv(ROOT, "26.2-neoforge", "atlas-ui",
                              arguments, Path("/world"))
        self.assertEqual("26.2-neoforge", command[command.index("--cell") + 1])
        self.assertIn("--manifest config/minecraft-version-matrix-26.2.json", " ".join(command))

        markdown = _terminal_markdown(("26.2-fabric", "26.2-neoforge"), "PASS")
        self.assertIn("2 manifest-selected cells", markdown)
        self.assertNotIn("six-cell", markdown)

    def test_terminal_binding_rejects_wrong_candidate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cell = {"id": "26.1-fabric", "loader": "fabric",
                    "minecraft": {"version": "26.1"}}
            run_id = "20260826T000000Z-0123456789ab"
            path = (root / "dist/qualification/ringworld/26.1/fabric" / run_id
                    / "26.1-fabric/evidence/nightly/06-seam-gameplay-multiplayer/terminal.json")
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({
                "verdict": "PASS", "cell": "26.1-fabric",
                "source": {"commit": "abc"},
                "frozen_candidate": {"sha256": "candidate"},
                "quick_evidence": {"sha256": "quick"},
            }), encoding="utf-8")
            payload = {"run_id": run_id}
            records = _verify_terminals(root, cell, "multiplayer", payload,
                                        "abc", "candidate", "quick")
            self.assertEqual(1, len(records))
            with self.assertRaisesRegex(NightlyMatrixError, "candidate"):
                _verify_terminals(root, cell, "multiplayer", payload,
                                  "abc", "wrong", "quick")

    def test_cleanup_removes_only_disposable_child_caches(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cell = {"id": "26.1-fabric", "loader": "fabric",
                    "minecraft": {"version": "26.1"}}
            run_id = "20260826T000000Z-0123456789ab"
            cell_root = (root / "dist/qualification/ringworld/26.1/fabric" / run_id
                         / "26.1-fabric")
            for name in ("gradle-home", "cache", "build"):
                path = cell_root / name / "nested"
                path.mkdir(parents=True)
                (path / "temporary.bin").write_bytes(b"temporary")
            evidence = cell_root / "evidence/nightly/terminal.json"
            evidence.parent.mkdir(parents=True)
            evidence.write_text("{}", encoding="utf-8")
            runtime = cell_root / "run/world/level.dat"
            runtime.parent.mkdir(parents=True)
            runtime.write_bytes(b"world")

            removed, retained_source = _cleanup_disposable_child_state(
                root, cell, {"run_id": run_id})

            self.assertEqual(4, len(removed))
            self.assertIsNone(retained_source)
            self.assertTrue(evidence.is_file())
            self.assertFalse(runtime.exists())
            for name in ("gradle-home", "cache", "build", "run"):
                self.assertFalse((cell_root / name).exists())

    def test_cleanup_derives_external_child_run_id_from_verified_layout(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cell = {"id": "26.1-fabric", "loader": "fabric",
                    "minecraft": {"version": "26.1"}}
            run_id = "20260826T000000Z-0123456789ab"
            cell_root = (root / "dist/qualification/ringworld/26.1/fabric" / run_id
                         / "26.1-fabric")
            runtime = cell_root / "run/world/level.dat"
            runtime.parent.mkdir(parents=True)
            runtime.write_bytes(b"world")
            terminal = cell_root / "evidence/nightly/02-worldgen/terminal.json"
            terminal.parent.mkdir(parents=True)
            terminal.write_text("{}", encoding="utf-8")

            removed, retained_source = _cleanup_disposable_child_state(
                root, cell, {"terminal_evidence": str(terminal)})

            self.assertEqual((str((cell_root / "run").resolve(strict=False)),), removed)
            self.assertIsNone(retained_source)
            self.assertTrue(terminal.is_file())
            self.assertFalse(runtime.exists())

    def test_cleanup_retains_hash_bound_runtime_capture(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cell = {"id": "26.1-fabric", "loader": "fabric",
                    "minecraft": {"version": "26.1"}}
            run_id = "20260826T000000Z-0123456789ab"
            cell_root = (root / "dist/qualification/ringworld/26.1/fabric" / run_id
                         / "26.1-fabric")
            capture = cell_root / "run/client/screenshots/view.png"
            capture.parent.mkdir(parents=True)
            capture.write_bytes(b"capture")
            digest = hashlib.sha256(b"capture").hexdigest()
            terminal = cell_root / "evidence/nightly/04-atlas/terminal.json"
            terminal.parent.mkdir(parents=True)
            terminal.write_text(json.dumps({
                "captures": [{"path": str(capture), "sha256": digest}],
            }), encoding="utf-8")

            retained = _retain_terminal_artifacts(
                root, cell, {"run_id": run_id}, ({"path": str(terminal)},))
            self.assertEqual(1, len(retained))
            retained_path = Path(retained[0]["retained_path"])
            self.assertEqual(b"capture", retained_path.read_bytes())
            _cleanup_disposable_child_state(root, cell, {"run_id": run_id})
            self.assertTrue(retained_path.is_file())
            self.assertFalse(capture.exists())

    def test_successful_worldgen_cleanup_keeps_only_source_world(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cell = {"id": "26.2-fabric", "loader": "fabric",
                    "minecraft": {"version": "26.2"}}
            run_id = "20260827T000000Z-0123456789ab"
            cell_root = (root / "dist/qualification/ringworld/26.2/fabric" / run_id
                         / "26.2-fabric")
            source_world = (cell_root / "run/nightly/02-worldgen-seam-structures"
                            / "production/runtime/world")
            (source_world / "level.dat").parent.mkdir(parents=True)
            (source_world / "level.dat").write_bytes(b"world")
            for path in (
                    cell_root / "gradle-home/temporary.bin",
                    cell_root / "cache/temporary.bin",
                    cell_root / "build/temporary.bin",
                    cell_root / "run/nightly/02-worldgen-seam-structures/production/libs/temp.jar",
                    cell_root / "run/nightly/02-worldgen-seam-structures/production/runtime/logs/latest.log",
                    cell_root / "run/other/runtime.bin"):
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b"temporary")

            removed, retained_source = _cleanup_disposable_child_state(
                root, cell, {"run_id": run_id}, retain_worldgen_source=True)

            self.assertEqual(str(source_world.resolve(strict=True)), retained_source)
            self.assertTrue((source_world / "level.dat").is_file())
            self.assertEqual(3, len(removed))
            self.assertFalse((cell_root / "run/other").exists())
            self.assertFalse((source_world.parent / "logs").exists())
            self.assertFalse((source_world.parent.parent / "libs").exists())
            for name in ("gradle-home", "cache", "build"):
                self.assertFalse((cell_root / name).exists())

    def test_worldgen_cleanup_rejects_symlinked_source_world(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            outside = root / "outside-world"
            outside.mkdir()
            cell = {"id": "26.2-fabric", "loader": "fabric",
                    "minecraft": {"version": "26.2"}}
            run_id = "20260827T000000Z-0123456789ab"
            source_world = (root / "dist/qualification/ringworld/26.2/fabric" / run_id
                            / "26.2-fabric/run/nightly/02-worldgen-seam-structures"
                            / "production/runtime/world")
            source_world.parent.mkdir(parents=True)
            source_world.symlink_to(outside, target_is_directory=True)

            with self.assertRaisesRegex(NightlyMatrixError, "missing or is a symlink"):
                _cleanup_disposable_child_state(
                    root, cell, {"run_id": run_id}, retain_worldgen_source=True)

    def test_worldgen_cleanup_rejects_source_runtime_link_outside_run_root(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            outside = root / "outside-runtime"
            (outside / "world").mkdir(parents=True)
            cell = {"id": "26.2-fabric", "loader": "fabric",
                    "minecraft": {"version": "26.2"}}
            run_id = "20260827T000000Z-0123456789ab"
            production = (root / "dist/qualification/ringworld/26.2/fabric" / run_id
                          / "26.2-fabric/run/nightly/02-worldgen-seam-structures/production")
            production.mkdir(parents=True)
            (production / "runtime").symlink_to(outside, target_is_directory=True)

            with self.assertRaisesRegex(NightlyMatrixError, "missing or is a symlink"):
                _cleanup_disposable_child_state(
                    root, cell, {"run_id": run_id}, retain_worldgen_source=True)

    def test_cleanup_rejects_escaping_run_id(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cell = {"id": "26.1-fabric", "loader": "fabric",
                    "minecraft": {"version": "26.1"}}
            with self.assertRaisesRegex(NightlyMatrixError, "unsafe|escapes"):
                _cleanup_disposable_child_state(
                    root, cell, {"run_id": "../../../../../../outside"})

    def test_cooldown_is_bounded_and_uses_short_intervals(self) -> None:
        intervals: list[int] = []
        _cooldown(12, sleeper=intervals.append)
        self.assertEqual([5, 5, 2], intervals)
        with self.assertRaisesRegex(NightlyMatrixError, "bounded"):
            _cooldown(601, sleeper=intervals.append)

    def test_retry_is_narrowly_limited_to_pre_claim_server_startup_timeout(self) -> None:
        startup_timeout = "timed out waiting for marker 'Done ('"
        untouched_claims = {"dedicated_server": False, "two_real_clients": False}
        self.assertTrue(_retryable_infrastructure_failure(
            "FAIL", startup_timeout, {"claims": untouched_claims}))
        self.assertFalse(_retryable_infrastructure_failure(
            "PASS", startup_timeout, {"claims": untouched_claims}))
        self.assertFalse(_retryable_infrastructure_failure(
            "FAIL", startup_timeout, {"claims": {"dedicated_server": True}}))
        self.assertFalse(_retryable_infrastructure_failure(
            "FAIL", startup_timeout, {}))
        self.assertFalse(_retryable_infrastructure_failure(
            "FAIL", [startup_timeout], {"claims": untouched_claims}))
        self.assertFalse(_retryable_infrastructure_failure(
            "FAIL", "raid terminal marker is missing", {"claims": untouched_claims}))
        self.assertTrue(_schedule_infrastructure_retry(
            1, "FAIL", startup_timeout, {"claims": untouched_claims}))
        self.assertFalse(_schedule_infrastructure_retry(
            2, "FAIL", startup_timeout, {"claims": untouched_claims}))

    def test_retry_classifies_only_exact_unclaimed_gradle_download_failure(self) -> None:
        payload = {"claims": {
            "actual_minecraft_client": False,
            "disposable_world_created": None,
            "exact_patch_dependencies": False,
        }}
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "stderr.log"
            log.write_text(
                "A problem occurred configuring root project 'ringworld'.\n"
                "> Failed to setup Minecraft, net.fabricmc.loom.util.download.DownloadException: Failed to download\n"
                "BUILD FAILED in 7m 5s\n",
                encoding="utf-8",
            )
            reason = _classified_infrastructure_reason("EXIT_1", payload, log)
            self.assertEqual(GRADLE_DOWNLOAD_FAILURE_REASON, reason)
            self.assertTrue(_retryable_infrastructure_failure(
                "FAIL", reason, payload))

            self.assertEqual("EXIT_1", _classified_infrastructure_reason(
                "EXIT_1", {"claims": {"actual_minecraft_client": True}}, log))
            log.write_text("BUILD FAILED\ncompileJava failed\n", encoding="utf-8")
            self.assertEqual("EXIT_1", _classified_infrastructure_reason(
                "EXIT_1", payload, log))


if __name__ == "__main__":
    unittest.main()
