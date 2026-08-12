#!/usr/bin/env python3
"""Bounded live-process stage runner for external Atlas recovery.

The recovery executor owns installer assembly and evidence capture.  This
module owns precisely one server process at a time: it proves the process
started, waits for a durable independently-decodable Atlas checkpoint, sends
one normal ``stop`` only for the interruption stage, and otherwise waits for
the headless coordinator to halt itself.  It never downloads, invokes Gradle,
or treats a development run as external runtime evidence.
"""

from __future__ import annotations

import json
import os
from pathlib import Path
import queue
import socket
import subprocess
import threading
import time
from typing import BinaryIO

from external_runtime_atlas_recovery_executor import (
    AtlasRecoveryExecutionError,
    AtlasRecoveryStageResult,
)
from external_runtime_atlas_recovery_plan import (
    AtlasRecoveryStagePlan,
    ExternalRuntimeAtlasRecoveryPlan,
)
from external_runtime_executor import FATAL_SERVER_MARKERS
from minecraft_atlas_recovery_persistence import (
    parse_persisted_ring_settings,
    parse_ring_terrain_atlas,
)
from minecraft_atlas_recovery_qualification import TimedMarker
from minecraft_qualification_executor import _BoundedRedactingLog, _terminate_process_group, sanitized_environment
from minecraft_qualification_model import InvocationError, QualificationPaths, is_within, socket_port_probe


POLL_SECONDS = 0.10
POST_EXIT_REPORT_SECONDS = 5.0
SERVER_READY_SUBSTRING = "Done ("


class AtlasRecoveryStageRunnerError(AtlasRecoveryExecutionError):
    """A live Atlas stage did not prove the required bounded lifecycle."""


class _Markers:
    def __init__(self) -> None:
        self._events: list[TimedMarker] = []
        self._last = -1

    def add(self, name: str) -> None:
        now = time.time_ns() // 1_000_000
        # A wall-clock sample is retained, with only equal-resolution samples
        # bumped one millisecond so the evidence has a strict causal order.
        now = max(now, self._last + 1)
        self._last = now
        self._events.append(TimedMarker(name, now))

    def result(self) -> tuple[TimedMarker, ...]:
        return tuple(self._events)


def _planned_port(plan: ExternalRuntimeAtlasRecoveryPlan) -> int:
    matching = [item for item in plan.smoke.files if item.path == plan.smoke.layout.server_properties_path]
    if len(matching) != 1:
        raise AtlasRecoveryStageRunnerError("Atlas stage has no unique server.properties plan")
    properties: dict[str, str] = {}
    for line in matching[0].contents.splitlines():
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise AtlasRecoveryStageRunnerError("Atlas stage server.properties contains an invalid line")
        key, value = line.split("=", 1)
        if key in properties:
            raise AtlasRecoveryStageRunnerError("Atlas stage server.properties duplicates a key")
        properties[key] = value
    if properties.get("server-ip") != "127.0.0.1":
        raise AtlasRecoveryStageRunnerError("Atlas stage must bind only localhost")
    try:
        port = int(properties["server-port"])
    except (KeyError, ValueError) as error:
        raise AtlasRecoveryStageRunnerError("Atlas stage has no valid server port") from error
    if not 1 <= port <= 65535:
        raise AtlasRecoveryStageRunnerError("Atlas stage server port is outside 1..65535")
    return port


def _port_open(port: int) -> bool:
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=0.15):
            return True
    except OSError:
        return False


def _drain_lines(source: BinaryIO, received: "queue.Queue[bytes | None]") -> None:
    try:
        for line in iter(source.readline, b""):
            received.put(line)
    finally:
        source.close()
        received.put(None)


def _valid_terminal_report(path: Path, status: str, root: Path) -> bool:
    if not is_within(path, root):
        return False
    try:
        raw = _secure_read(path, root)
        data = json.loads(raw.decode("utf-8"))
    except (OSError, ValueError, UnicodeError, json.JSONDecodeError):
        return False
    return isinstance(data, dict) and data.get("schemaVersion") == 2 \
        and data.get("identityAvailable") is True and data.get("status") == status


def _secure_read(path: Path, root: Path) -> bytes:
    """Read one regular contained file without following any symlink component."""
    if not is_within(path, root):
        raise OSError("path escapes its runtime root")
    current = root
    relative = path.resolve(strict=False).relative_to(root.resolve(strict=False))
    for part in relative.parts:
        current = current / part
        if current.exists() or current.is_symlink():
            if current.is_symlink():
                raise OSError("path traverses a symlink")
    descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    try:
        import stat
        if not stat.S_ISREG(os.fstat(descriptor).st_mode):
            raise OSError("path is not a regular file")
        chunks: list[bytes] = []
        while True:
            block = os.read(descriptor, 1024 * 1024)
            if not block:
                return b"".join(chunks)
            chunks.append(block)
    finally:
        os.close(descriptor)


def _durable_partial(plan: ExternalRuntimeAtlasRecoveryPlan) -> bool:
    """Return only after independent parsers see a genuine persisted checkpoint."""
    try:
        settings = parse_persisted_ring_settings(_secure_read(plan.settings_path, plan.world_root), plan.settings_path)
        atlas = parse_ring_terrain_atlas(_secure_read(plan.atlas_path, plan.world_root), plan.atlas_path)
    except (OSError, ValueError, UnicodeError, InvocationError):
        return False
    # The pure contract will repeat the exact identity comparison after both
    # reports are captured.  The stage runner only decides when stopping is
    # safe, so it requires observable nonzero, noncomplete persisted work.
    return settings.width_blocks == atlas.width_blocks and settings.circumference_blocks == atlas.circumference_blocks \
        and 0 < atlas.present_cells < atlas.columns * atlas.rows and atlas.present_chunks > 0


def _atlas_progress(plan: ExternalRuntimeAtlasRecoveryPlan) -> tuple[int, int] | None:
    try:
        atlas = parse_ring_terrain_atlas(_secure_read(plan.atlas_path, plan.world_root), plan.atlas_path)
    except (OSError, ValueError, UnicodeError, InvocationError):
        return None
    return atlas.present_cells, atlas.columns * atlas.rows


def _assert_stage(stage: AtlasRecoveryStagePlan, plan: ExternalRuntimeAtlasRecoveryPlan, paths: QualificationPaths) -> None:
    if stage not in plan.stages or stage.name not in {"interrupted", "recovery"}:
        raise AtlasRecoveryStageRunnerError("Atlas stage is not one of the reviewed fixture stages")
    if stage.launch.cwd != plan.runtime_root or not stage.launch.argv or stage.launch.timeout_seconds < 1:
        raise AtlasRecoveryStageRunnerError("Atlas stage launch does not use the exact fixture runtime")
    if plan.runtime_root != plan.smoke.layout.root or not is_within(plan.runtime_root, paths.cell_root):
        raise AtlasRecoveryStageRunnerError("Atlas stage runtime escapes its qualification cell")
    if not plan.runtime_root.is_dir() or plan.runtime_root.is_symlink():
        raise AtlasRecoveryStageRunnerError("Atlas stage runtime root is not an installed regular directory")
    if not is_within(paths.logs_directory, paths.cell_root) or paths.logs_directory.is_symlink() \
            or not paths.logs_directory.is_dir():
        raise AtlasRecoveryStageRunnerError("Atlas stage logs directory is not available")


def run_external_runtime_atlas_recovery_stage(
    stage: AtlasRecoveryStagePlan,
    plan: ExternalRuntimeAtlasRecoveryPlan,
    paths: QualificationPaths,
) -> AtlasRecoveryStageResult:
    """Run one exact external server launch and return observed lifecycle facts.

    It deliberately raises on every unsuccessful outcome.  The enclosing
    executor can therefore never translate a timeout, fatal log, missing
    durable checkpoint, or unexpected self-stop into a passing fixture.
    """
    _assert_stage(stage, plan, paths)
    port = _planned_port(plan)
    if not socket_port_probe(port):
        raise AtlasRecoveryStageRunnerError("Atlas stage localhost port is already in use")
    log_path = paths.logs_directory / f"03-atlas-recovery-{stage.name}.combined.log"
    if log_path.exists() or log_path.is_symlink() or not is_within(log_path, paths.logs_directory):
        raise AtlasRecoveryStageRunnerError("Atlas stage log already exists or escapes its evidence directory")
    markers = _Markers()
    started = time.monotonic()
    deadline = started + stage.launch.timeout_seconds
    creation_flags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
    process: subprocess.Popen[bytes] | None = None
    drain: threading.Thread | None = None
    sink: _BoundedRedactingLog | None = None
    received: "queue.Queue[bytes | None]" = queue.Queue()
    log_tail = ""
    server_started = False
    stop_sent = False
    partial_before_restart: tuple[int, int] | None = None
    recovered_growth = False
    failure: str | None = None
    try:
        sink = _BoundedRedactingLog(log_path)
        # Snapshot the exact durable checkpoint before the resumed JVM can
        # touch it.  The enclosing executor separately byte-compares this
        # same file against its interruption capture before it calls us.
        if stage.name == "recovery":
            partial_before_restart = _atlas_progress(plan)
            if partial_before_restart is None or not _durable_partial(plan):
                raise AtlasRecoveryStageRunnerError("recovery launch did not begin from a durable partial Atlas")
        process = subprocess.Popen(
            list(stage.launch.argv), cwd=stage.launch.cwd, env=sanitized_environment(), stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=os.name != "nt",
            creationflags=creation_flags,
        )
        assert process.stdout is not None and process.stdin is not None
        drain = threading.Thread(target=_drain_lines, args=(process.stdout, received), daemon=True)
        drain.start()
        while time.monotonic() < deadline:
            try:
                item = received.get(timeout=POLL_SECONDS)
            except queue.Empty:
                item = None
            if item:
                sink.feed(item)
                log_tail = (log_tail + item.decode("utf-8", errors="replace"))[-65536:]
                for marker in FATAL_SERVER_MARKERS:
                    if marker in log_tail:
                        failure = "FATAL_SERVER_LOG:" + marker
                        break
            if failure:
                break
            if not server_started and SERVER_READY_SUBSTRING in log_tail and _port_open(port):
                server_started = True
                markers.add("atlas-started" if stage.name == "interrupted" else "atlas-restarted")
            if server_started and stage.name == "interrupted" and not stop_sent and _durable_partial(plan):
                try:
                    process.stdin.write(b"stop\n")
                    process.stdin.flush()
                except OSError as error:
                    raise AtlasRecoveryStageRunnerError("could not send the interruption stop command") from error
                stop_sent = True
            if server_started and stage.name == "recovery" and partial_before_restart is not None:
                current = _atlas_progress(plan)
                if current is not None and current[0] > partial_before_restart[0]:
                    recovered_growth = True
                    if not any(event.name == "atlas-recovered" for event in markers.result()):
                        markers.add("atlas-recovered")
            if process.poll() is not None:
                break
        if failure:
            raise AtlasRecoveryStageRunnerError(failure)
        if process.poll() is None and time.monotonic() >= deadline:
            raise AtlasRecoveryStageRunnerError(f"ATLAS_STAGE_TIMEOUT_AFTER_{stage.launch.timeout_seconds}_SECONDS")
        if not server_started:
            raise AtlasRecoveryStageRunnerError("Atlas stage never reached a ready localhost server")
        if stage.name == "interrupted" and not stop_sent:
            raise AtlasRecoveryStageRunnerError("Atlas stage never produced a durable partial checkpoint")
        if stage.name == "recovery" and not recovered_growth:
            raise AtlasRecoveryStageRunnerError("Atlas recovery never advanced its durable checkpoint")
        try:
            return_code = process.wait(timeout=max(0.1, deadline - time.monotonic()))
        except subprocess.TimeoutExpired as error:
            raise AtlasRecoveryStageRunnerError("Atlas stage did not exit within its launch deadline") from error
        drain.join(timeout=10)
        if drain.is_alive():
            raise AtlasRecoveryStageRunnerError("Atlas stage output pipe did not close after exit")
        while True:
            try:
                item = received.get_nowait()
            except queue.Empty:
                break
            if item:
                sink.feed(item)
                log_tail = (log_tail + item.decode("utf-8", errors="replace"))[-65536:]
        for marker in FATAL_SERVER_MARKERS:
            if marker in log_tail:
                raise AtlasRecoveryStageRunnerError("FATAL_SERVER_LOG:" + marker)
        report_deadline = min(deadline, time.monotonic() + POST_EXIT_REPORT_SECONDS)
        while time.monotonic() < report_deadline and not _valid_terminal_report(stage.runtime_report_path, stage.expected_status, plan.world_root):
            time.sleep(POLL_SECONDS)
        if return_code != 0:
            raise AtlasRecoveryStageRunnerError(f"ATLAS_STAGE_EXIT_{return_code}")
        if not _valid_terminal_report(stage.runtime_report_path, stage.expected_status, plan.world_root):
            raise AtlasRecoveryStageRunnerError("Atlas stage did not write its expected terminal headless report")
        if stage.name == "interrupted":
            markers.add("atlas-interrupted")
            return AtlasRecoveryStageResult(return_code, markers.result(), True, False, str(log_path))
        # The report exists only after the coordinator verified completion and
        # called server.halt(false); this stage deliberately never writes stdin.
        markers.add("atlas-complete")
        markers.add("fixture-pass")
        return AtlasRecoveryStageResult(return_code, markers.result(), False, True, str(log_path))
    finally:
        if process is not None and process.poll() is None:
            _terminate_process_group(process)
        if process is not None and process.stdin is not None:
            try:
                process.stdin.close()
            except OSError:
                pass
        if drain is not None:
            drain.join(timeout=10)
            if drain.is_alive():
                raise AtlasRecoveryStageRunnerError("Atlas stage output pipe did not close")
        if sink is not None:
            sink.close()


# The name used by the recovery executor's injected StageRunner seam.
run_atlas_recovery_stage = run_external_runtime_atlas_recovery_stage
