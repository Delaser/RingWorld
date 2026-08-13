#!/usr/bin/env python3
"""Bounded process owner for one self-halting external worldgen fixture.

This deliberately contains no downloader, Gradle invocation, server assembly,
or RingWorld-specific file parsing.  A higher-level executor supplies a
reviewed launch plan.  This module starts that *exact* process in its own
process group, accepts only a localhost-ready server, and returns an immutable
observation only when the child itself has proved the ordered worldgen gate and
exited cleanly.  It never writes ``stop`` to the child.
"""

from __future__ import annotations

from dataclasses import dataclass
from hashlib import sha256
import os
from pathlib import Path
import queue
import re
import socket
import stat
import subprocess
import threading
import time
from typing import BinaryIO

from external_runtime_executor import FATAL_SERVER_MARKERS
from minecraft_qualification_executor import _BoundedRedactingLog, _terminate_process_group, sanitized_environment
from minecraft_qualification_model import is_within, socket_port_probe


POLL_SECONDS = 0.10
SERVER_READY_SUBSTRING = "Done ("
_STAGE_NAME = re.compile(r"[a-z][a-z0-9-]{0,63}")


class ExternalRuntimeWorldgenStageError(RuntimeError):
    """The self-halting worldgen process did not meet its reviewed contract."""


@dataclass(frozen=True)
class WorldgenSemanticMarker:
    """One ordered, literal marker a reviewed fixture must emit."""

    name: str
    text: str


@dataclass(frozen=True)
class ExternalRuntimeWorldgenStagePlan:
    """A narrow, already-reviewed launch contract for one stage.

    ``runtime_root`` is both the only permitted working directory and the
    containment root for the supplied launch.  Keeping it explicit lets an
    adapter reject accidental Gradle/run-directory launches before a process
    starts.
    """

    name: str
    runtime_root: Path
    argv: tuple[str, ...]
    cwd: Path
    timeout_seconds: int
    localhost_port: int
    markers: tuple[WorldgenSemanticMarker, ...]


@dataclass(frozen=True)
class WorldgenStageMarkerEvent:
    name: str
    timestamp_epoch_ms: int


@dataclass(frozen=True)
class ExternalRuntimeWorldgenStageObservation:
    """Hash-addressed facts observed from exactly one child process."""

    stage_name: str
    argv: tuple[str, ...]
    cwd: str
    return_code: int
    started_epoch_ms: int
    finished_epoch_ms: int
    elapsed_ms: int
    server_log: str
    server_log_sha256: str
    marker_events: tuple[WorldgenStageMarkerEvent, ...]
    self_halted: bool


class _Events:
    def __init__(self) -> None:
        self._events: list[WorldgenStageMarkerEvent] = []
        self._last = -1

    def add(self, name: str) -> None:
        stamp = time.time_ns() // 1_000_000
        stamp = max(stamp, self._last + 1)
        self._last = stamp
        self._events.append(WorldgenStageMarkerEvent(name, stamp))

    def result(self) -> tuple[WorldgenStageMarkerEvent, ...]:
        return tuple(self._events)


def _read_lines(source: BinaryIO, destination: "queue.Queue[bytes | None]") -> None:
    try:
        for line in iter(source.readline, b""):
            destination.put(line)
    finally:
        source.close()
        destination.put(None)


def _regular_directory(path: Path, root: Path, label: str) -> None:
    if not path.is_absolute() or not is_within(path, root) or path.is_symlink() or not path.is_dir():
        raise ExternalRuntimeWorldgenStageError(f"{label} is not a contained regular directory")
    relative = path.resolve(strict=False).relative_to(root.resolve(strict=False))
    current = root
    if root.is_symlink() or not root.is_dir():
        raise ExternalRuntimeWorldgenStageError("worldgen containment root is unsafe")
    for part in relative.parts:
        current /= part
        if current.is_symlink() or not current.is_dir():
            raise ExternalRuntimeWorldgenStageError(f"{label} traverses an unsafe directory")


def _validate(plan: ExternalRuntimeWorldgenStagePlan, cell_root: Path, logs_directory: Path) -> None:
    if not isinstance(plan, ExternalRuntimeWorldgenStagePlan):
        raise ExternalRuntimeWorldgenStageError("worldgen stage must be an exact reviewed plan")
    if not _STAGE_NAME.fullmatch(plan.name):
        raise ExternalRuntimeWorldgenStageError("worldgen stage name is invalid")
    _regular_directory(cell_root, cell_root, "qualification cell root")
    _regular_directory(plan.runtime_root, cell_root, "worldgen runtime root")
    _regular_directory(logs_directory, cell_root, "worldgen logs directory")
    if plan.cwd != plan.runtime_root:
        raise ExternalRuntimeWorldgenStageError("worldgen launch cwd does not match its reviewed runtime root")
    if not isinstance(plan.argv, tuple) or not plan.argv \
            or any(not isinstance(item, str) or not item or "\x00" in item for item in plan.argv):
        raise ExternalRuntimeWorldgenStageError("worldgen launch argv is invalid")
    if plan.timeout_seconds < 1:
        raise ExternalRuntimeWorldgenStageError("worldgen stage timeout is invalid")
    if not 1 <= plan.localhost_port <= 65535:
        raise ExternalRuntimeWorldgenStageError("worldgen stage port is invalid")
    # These are lifecycle witnesses only.  In particular, the record's
    # ``crossingStarts`` value is intentionally not interpreted here: the
    # higher-level independent parser owns all worldgen correctness claims.
    required_names = ("worldgen-record", "monument-record", "fixture-pass")
    if tuple(marker.name for marker in plan.markers) != required_names:
        raise ExternalRuntimeWorldgenStageError("worldgen marker contract is not the reviewed ordered contract")
    if any(not marker.text or "\n" in marker.text or "\r" in marker.text for marker in plan.markers):
        raise ExternalRuntimeWorldgenStageError("worldgen marker text is unsafe")
    if not socket_port_probe(plan.localhost_port):
        raise ExternalRuntimeWorldgenStageError("worldgen localhost port is already in use")


def _log_digest(path: Path) -> str:
    digest = sha256()
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(path, flags)
    try:
        if not stat.S_ISREG(os.fstat(descriptor).st_mode):
            raise ExternalRuntimeWorldgenStageError("worldgen log is not a regular file")
        while True:
            block = os.read(descriptor, 1024 * 1024)
            if not block:
                return digest.hexdigest()
            digest.update(block)
    finally:
        os.close(descriptor)


def run_external_runtime_worldgen_stage(
    plan: ExternalRuntimeWorldgenStagePlan,
    *,
    cell_root: Path,
    logs_directory: Path,
) -> ExternalRuntimeWorldgenStageObservation:
    """Run exactly one self-halting external fixture.

    The ordered output contract is: localhost server readiness, one worldgen
    record, one monument record, then the stronghold test's PASS marker.  A
    higher-level parser validates the facts in those records.  A zero exit
    before that sequence is a failure.  No stdin is
    opened, so this runner cannot accidentally convert a self-halting fixture
    into a normal ``stop``-driven one.
    """
    _validate(plan, cell_root, logs_directory)
    log_path = logs_directory / f"04-worldgen-{plan.name}.combined.log"
    if not is_within(log_path, logs_directory) or log_path.exists() or log_path.is_symlink():
        raise ExternalRuntimeWorldgenStageError("worldgen stage log already exists or is unsafe")

    events = _Events()
    started_mono = time.monotonic()
    started_epoch_ms = time.time_ns() // 1_000_000
    deadline = started_mono + plan.timeout_seconds
    process: subprocess.Popen[bytes] | None = None
    sink: _BoundedRedactingLog | None = None
    drain: threading.Thread | None = None
    received: "queue.Queue[bytes | None]" = queue.Queue()
    output_tail = ""
    ready = False
    next_marker = 0
    try:
        sink = _BoundedRedactingLog(log_path)
        flags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
        process = subprocess.Popen(
            list(plan.argv), cwd=plan.cwd, env=sanitized_environment(), stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            start_new_session=os.name != "nt", creationflags=flags,
        )
        assert process.stdout is not None
        drain = threading.Thread(target=_read_lines, args=(process.stdout, received), daemon=True)
        drain.start()
        while time.monotonic() < deadline:
            try:
                line = received.get(timeout=POLL_SECONDS)
            except queue.Empty:
                line = None
            if line:
                sink.feed(line)
                decoded = line.decode("utf-8", errors="replace")
                output_tail = (output_tail + decoded)[-65536:]
                fatal = next((token for token in FATAL_SERVER_MARKERS if token in output_tail), None)
                if fatal is not None:
                    raise ExternalRuntimeWorldgenStageError("FATAL_SERVER_LOG:" + fatal)
                if not ready and SERVER_READY_SUBSTRING in output_tail:
                    try:
                        with socket.create_connection(("127.0.0.1", plan.localhost_port), timeout=0.15):
                            pass
                    except OSError:
                        pass
                    else:
                        ready = True
                        events.add("server-ready")
                # Match exactly one expected transition per received line.
                # Searching the accumulated tail would allow an early PASS or
                # a duplicate old record to satisfy a later stage after the
                # expected order has changed.
                if ready and next_marker < len(plan.markers) \
                        and plan.markers[next_marker].text in decoded:
                    events.add(plan.markers[next_marker].name)
                    next_marker += 1
            if process.poll() is not None:
                break
        if process.poll() is None:
            raise ExternalRuntimeWorldgenStageError(f"WORLDGEN_STAGE_TIMEOUT_AFTER_{plan.timeout_seconds}_SECONDS")
        return_code = process.wait(timeout=1)
        drain.join(timeout=10)
        if drain.is_alive():
            raise ExternalRuntimeWorldgenStageError("worldgen stage output pipe did not close after exit")
        while True:
            try:
                line = received.get_nowait()
            except queue.Empty:
                break
            if line:
                sink.feed(line)
                output_tail = (output_tail + line.decode("utf-8", errors="replace"))[-65536:]
        fatal = next((token for token in FATAL_SERVER_MARKERS if token in output_tail), None)
        if fatal is not None:
            raise ExternalRuntimeWorldgenStageError("FATAL_SERVER_LOG:" + fatal)
        if not ready:
            raise ExternalRuntimeWorldgenStageError("worldgen stage never reached a ready localhost server")
        if next_marker != len(plan.markers):
            raise ExternalRuntimeWorldgenStageError("worldgen stage exited before the reviewed marker sequence completed")
        if return_code != 0:
            raise ExternalRuntimeWorldgenStageError(f"WORLDGEN_STAGE_EXIT_{return_code}")
    finally:
        if process is not None and process.poll() is None:
            _terminate_process_group(process)
        if drain is not None:
            drain.join(timeout=10)
        if sink is not None:
            sink.close()

    finished_epoch_ms = time.time_ns() // 1_000_000
    return ExternalRuntimeWorldgenStageObservation(
        stage_name=plan.name,
        argv=plan.argv,
        cwd=str(plan.cwd),
        return_code=return_code,
        started_epoch_ms=started_epoch_ms,
        finished_epoch_ms=finished_epoch_ms,
        elapsed_ms=max(0, round((time.monotonic() - started_mono) * 1000)),
        server_log=str(log_path),
        server_log_sha256=_log_digest(log_path),
        marker_events=events.result(),
        self_halted=True,
    )


# Short adapter seam for a later worldgen executor.
run_worldgen_stage = run_external_runtime_worldgen_stage
