#!/usr/bin/env python3
"""No-network subprocess tests for the external Atlas stage runner."""

from __future__ import annotations

import base64
import gzip
import json
from pathlib import Path
import socket
import struct
import sys
import tempfile
import textwrap
import unittest
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from external_runtime_atlas_recovery_plan import AtlasRecoveryStagePlan  # noqa: E402
from external_runtime_atlas_stage_runner import (  # noqa: E402
    AtlasRecoveryStageRunnerError,
    run_external_runtime_atlas_recovery_stage,
)
from external_runtime_smoke import LaunchPlan, PlannedFile  # noqa: E402
from minecraft_atlas_recovery_persistence import ATLAS_MAGIC  # noqa: E402
from minecraft_atlas_recovery_qualification import (  # noqa: E402
    ATLAS_FORMAT_VERSION,
    ATLAS_SAMPLE_STEP_BLOCKS,
    EXPECTED_ATLAS_COLUMNS,
    EXPECTED_ATLAS_ROWS,
)


def _unused_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.bind(("127.0.0.1", 0))
        return int(probe.getsockname()[1])


def _settings() -> bytes:
    def name(value: str) -> bytes:
        raw = value.encode("utf-8")
        return struct.pack(">H", len(raw)) + raw
    entries = (("width", 3, 416), ("circumference", 3, 2048), ("seed", 4, 17),
               ("wallHeight", 3, 160), ("surfaceReferenceY", 3, 64),
               ("terrainNoiseMapping", 3, 4), ("format", 3, 3))
    raw = bytearray(b"\x0a" + name(""))
    raw.extend(b"\x0a" + name("data"))
    for key, kind, value in entries:
        raw.append(kind)
        raw.extend(name(key))
        raw.extend(struct.pack(">q" if kind == 4 else ">i", value))
    raw.extend(b"\x00\x00")
    return gzip.compress(bytes(raw), mtime=0)


def _atlas(complete: bool) -> bytes:
    cells = EXPECTED_ATLAS_COLUMNS * EXPECTED_ATLAS_ROWS
    raw = bytearray(struct.pack(">IIQIIIIIQ", ATLAS_MAGIC, ATLAS_FORMAT_VERSION, 1, 416, 2048,
                                ATLAS_SAMPLE_STEP_BLOCKS, EXPECTED_ATLAS_COLUMNS,
                                EXPECTED_ATLAS_ROWS, 1 if not complete else 2))
    for index in range(cells):
        present = complete or index in {0, 1, EXPECTED_ATLAS_COLUMNS, EXPECTED_ATLAS_COLUMNS + 1}
        raw.extend(bytes((int(present),)) + struct.pack(">hI", 64, 0x00AA00))
    return gzip.compress(bytes(raw), mtime=0)


class ExternalRuntimeAtlasStageRunnerTest(unittest.TestCase):
    def _fixture(self, root: Path, mode: str):
        runtime = root / "runtime"
        world = runtime / "world"
        logs = root / "logs"
        runtime.mkdir()
        logs.mkdir()
        port = _unused_port()
        settings, partial, complete = _settings(), _atlas(False), _atlas(True)
        script = runtime / "server.py"
        settings_path = world / "dimensions/minecraft/overworld/data/ringworld/settings.dat"
        atlas_path = world / "dimensions/minecraft/overworld/data/ringworld/terrain-atlas.rwat.gz"
        report_path = world / "ringworld-prewarm/result.json"
        # The fake is a real child process: it binds localhost, writes the
        # durable bytes, receives a normal stop only in interruption mode, and
        # exits after its terminal report.  It is not a Minecraft substitute.
        source = f'''
import base64, json, socket, sys, time
from pathlib import Path
settings = Path({str(settings_path)!r}); atlas = Path({str(atlas_path)!r}); report = Path({str(report_path)!r})
settings.parent.mkdir(parents=True, exist_ok=True); settings.write_bytes(base64.b64decode({base64.b64encode(settings)!r}))
if {mode!r} == "interrupted": atlas.write_bytes(base64.b64decode({base64.b64encode(partial)!r}))
listener = socket.socket(); listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); listener.bind(("127.0.0.1", {port})); listener.listen(2)
print("Done (0.1s)!", flush=True)
if {mode!r} == "interrupted":
    sys.stdin.readline()
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps({{"schemaVersion": 2, "identityAvailable": True, "status": "INTERRUPTED"}}), encoding="utf-8")
else:
    time.sleep(0.35)
    atlas.write_bytes(base64.b64decode({base64.b64encode(complete)!r}))
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps({{"schemaVersion": 2, "identityAvailable": True, "status": "COMPLETE"}}), encoding="utf-8")
listener.close()
'''
        script.write_text(textwrap.dedent(source), encoding="utf-8")
        launch = LaunchPlan((sys.executable, "-u", str(script)), runtime, 10)
        stage = AtlasRecoveryStagePlan(mode, launch, report_path, "INTERRUPTED" if mode == "interrupted" else "COMPLETE",
                                       root / "evidence/report.json", root / "evidence/atlas.gz", root / "evidence/markers.json")
        smoke = SimpleNamespace(
            layout=SimpleNamespace(root=runtime, server_properties_path=runtime / "server.properties"),
            files=(PlannedFile(runtime / "server.properties", f"server-ip=127.0.0.1\nserver-port={port}\n"),),
        )
        plan = SimpleNamespace(runtime_root=runtime, smoke=smoke, settings_path=settings_path, atlas_path=atlas_path,
                               world_root=world, stages=(stage,))
        paths = SimpleNamespace(logs_directory=logs, cell_root=root)
        return stage, plan, paths

    def test_interruption_waits_for_durable_partial_then_stops_cleanly(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            stage, plan, paths = self._fixture(Path(directory), "interrupted")
            result = run_external_runtime_atlas_recovery_stage(stage, plan, paths)
            self.assertEqual(("atlas-started", "atlas-interrupted"), tuple(item.name for item in result.marker_events))
            self.assertTrue(result.graceful_stop_sent)
            self.assertFalse(result.self_halted)
            self.assertTrue(Path(result.server_log).is_file())

    def test_recovery_observes_growth_and_only_self_halts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            stage, plan, paths = self._fixture(Path(directory), "recovery")
            plan.atlas_path.parent.mkdir(parents=True, exist_ok=True)
            plan.atlas_path.write_bytes(_atlas(False))
            plan.settings_path.parent.mkdir(parents=True, exist_ok=True)
            plan.settings_path.write_bytes(_settings())
            result = run_external_runtime_atlas_recovery_stage(stage, plan, paths)
            self.assertEqual(("atlas-restarted", "atlas-recovered", "atlas-complete", "fixture-pass"),
                             tuple(item.name for item in result.marker_events))
            self.assertFalse(result.graceful_stop_sent)
            self.assertTrue(result.self_halted)

    def test_fatal_output_never_becomes_a_stage_result(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            stage, plan, paths = self._fixture(Path(directory), "interrupted")
            script = Path(stage.launch.argv[-1])
            script.write_text("print('Fatal error', flush=True)\n", encoding="utf-8")
            with self.assertRaisesRegex(AtlasRecoveryStageRunnerError, "FATAL_SERVER_LOG"):
                run_external_runtime_atlas_recovery_stage(stage, plan, paths)


if __name__ == "__main__":
    unittest.main()
