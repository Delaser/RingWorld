#!/usr/bin/env python3
"""No-network subprocess tests for the self-halting worldgen stage runner."""

from __future__ import annotations

from pathlib import Path
import socket
import sys
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from external_runtime_worldgen_stage_runner import (  # noqa: E402
    ExternalRuntimeWorldgenStageError,
    ExternalRuntimeWorldgenStagePlan,
    WorldgenSemanticMarker,
    run_external_runtime_worldgen_stage,
)


def _unused_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.bind(("127.0.0.1", 0))
        return int(probe.getsockname()[1])


class ExternalRuntimeWorldgenStageRunnerTest(unittest.TestCase):
    def _plan(self, root: Path, mode: str = "pass") -> tuple[ExternalRuntimeWorldgenStagePlan, Path, Path]:
        runtime, logs = root / "runtime", root / "logs"
        runtime.mkdir()
        logs.mkdir()
        port = _unused_port()
        child = runtime / "child.py"
        source = f'''
import socket, sys, time
listener = socket.socket(); listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
listener.bind(("127.0.0.1", {port})); listener.listen(1)
print("Done (0.1s)!", flush=True)
if {mode!r} == "fatal": print("Fatal error", flush=True)
elif {mode!r} == "early":
    print("[stronghold-test] PASS", flush=True)
    print("[worldgen-matrix] seed=-12 layout=2048x416 crossingStarts=1", flush=True)
    print("[worldgen-matrix] monumentStatus=SATISFIED monumentReason=NONE", flush=True)
else:
    print("[worldgen-matrix] seed=-12 layout=2048x416 biomeFamilies=[forest, ocean] crossingStarts=1", flush=True)
    if {mode!r} == "duplicate": print("[worldgen-matrix] seed=-12 layout=2048x416 crossingStarts=1", flush=True)
    print("[worldgen-matrix] monumentStatus=SATISFIED monumentReason=NONE", flush=True)
    print("[stronghold-test] PASS", flush=True)
time.sleep(0.15)
listener.close()
'''
        child.write_text(textwrap.dedent(source), encoding="utf-8")
        plan = ExternalRuntimeWorldgenStagePlan(
            name="production-fresh", runtime_root=runtime,
            argv=(sys.executable, "-u", str(child)), cwd=runtime, timeout_seconds=5,
            localhost_port=port,
            markers=(
                WorldgenSemanticMarker("worldgen-record", "[worldgen-matrix] seed="),
                WorldgenSemanticMarker("monument-record", "[worldgen-matrix] monumentStatus="),
                WorldgenSemanticMarker("fixture-pass", "[stronghold-test] PASS"),
            ),
        )
        return plan, root, logs

    def test_self_halting_child_returns_hashed_ordered_observation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            plan, root, logs = self._plan(Path(directory))
            result = run_external_runtime_worldgen_stage(plan, cell_root=root, logs_directory=logs)
            self.assertTrue(result.self_halted)
            self.assertEqual(0, result.return_code)
            self.assertEqual(
                ("server-ready", "worldgen-record", "monument-record", "fixture-pass"),
                tuple(event.name for event in result.marker_events),
            )
            self.assertEqual(64, len(result.server_log_sha256))
            self.assertTrue(Path(result.server_log).is_file())

    def test_fatal_child_never_returns_an_observation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            plan, root, logs = self._plan(Path(directory), "fatal")
            with self.assertRaisesRegex(ExternalRuntimeWorldgenStageError, "FATAL_SERVER_LOG"):
                run_external_runtime_worldgen_stage(plan, cell_root=root, logs_directory=logs)

    def test_duplicate_prior_record_cannot_advance_the_contract(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            plan, root, logs = self._plan(Path(directory), "duplicate")
            result = run_external_runtime_worldgen_stage(plan, cell_root=root, logs_directory=logs)
            self.assertEqual(
                ("server-ready", "worldgen-record", "monument-record", "fixture-pass"),
                tuple(event.name for event in result.marker_events),
            )

    def test_early_out_of_order_pass_and_wrong_cwd_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            plan, root, logs = self._plan(Path(directory), "early")
            with self.assertRaisesRegex(ExternalRuntimeWorldgenStageError, "marker sequence"):
                run_external_runtime_worldgen_stage(plan, cell_root=root, logs_directory=logs)
        with tempfile.TemporaryDirectory() as directory:
            plan, root, logs = self._plan(Path(directory))
            invalid = ExternalRuntimeWorldgenStagePlan(
                plan.name, plan.runtime_root, plan.argv, root, plan.timeout_seconds, plan.localhost_port, plan.markers,
            )
            with self.assertRaisesRegex(ExternalRuntimeWorldgenStageError, "cwd"):
                run_external_runtime_worldgen_stage(invalid, cell_root=root, logs_directory=logs)

    def test_symlinked_runtime_and_mutable_argv_are_rejected_before_launch(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            plan, _, logs = self._plan(root)
            linked_runtime = root / "linked-runtime"
            linked_runtime.symlink_to(plan.runtime_root, target_is_directory=True)
            unsafe = ExternalRuntimeWorldgenStagePlan(
                plan.name, linked_runtime, plan.argv, linked_runtime, plan.timeout_seconds, plan.localhost_port, plan.markers,
            )
            with self.assertRaisesRegex(ExternalRuntimeWorldgenStageError, "runtime root"):
                run_external_runtime_worldgen_stage(unsafe, cell_root=root, logs_directory=logs)
            mutable = ExternalRuntimeWorldgenStagePlan(
                plan.name, plan.runtime_root, list(plan.argv), plan.cwd, plan.timeout_seconds, plan.localhost_port, plan.markers,  # type: ignore[arg-type]
            )
            with self.assertRaisesRegex(ExternalRuntimeWorldgenStageError, "argv"):
                run_external_runtime_worldgen_stage(mutable, cell_root=root, logs_directory=logs)


if __name__ == "__main__":
    unittest.main()
