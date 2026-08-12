#!/usr/bin/env python3
"""Fail-closed executor for :mod:`external_runtime_smoke` plans.

This module is deliberately narrow.  It operates only below one reviewed
qualification cell, consumes an already-reviewed ``ExternalRuntimeSmokePlan``,
and never knows about normal development runs, packaged clients, user data, or
hosted releases.  A caller owns the higher-level matrix verdict/evidence
index; this adapter owns one production-style dedicated-server smoke.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from contextlib import nullcontext
import os
from pathlib import Path
import queue
import shutil
import stat
import subprocess
import tempfile
import threading
import time
from typing import Any, BinaryIO, Callable
from urllib.parse import urlparse
from urllib.request import HTTPRedirectHandler, build_opener

from external_runtime_smoke import (
    ExternalRuntimeSmokePlan,
    ModInventoryEntry,
    RuntimeDownload,
)
from minecraft_qualification_executor import (
    EvidenceError,
    ExecutedCommand,
    QualificationExecutionError,
    QualificationLock,
    _BoundedRedactingLog,
    _terminate_process_group,
    create_contained_directories,
    execute_command,
    sanitized_environment,
    verify_pinned_file,
    write_terminal_report,
)
from minecraft_qualification_model import CommandRecord, PhaseName, QualificationPaths, Verdict, is_within, socket_port_probe


MAX_DOWNLOAD_BYTES = 1024 * 1024 * 1024
SERVER_STOP_MARKERS = ("Stopping server", "Stopping the server")
FATAL_SERVER_MARKERS = ("Crash report", "Exception in server tick loop", "A fatal error has occurred", "Fatal error")
SERVER_SAVE_MARKERS = ("Saving chunks for level", "Saving worlds")
SEMANTIC_MARKERS = ("ringworld-bootstrap", "atlas-disabled", "server-ready", "server-stop", "world-save")


class ExternalRuntimeExecutionError(QualificationExecutionError):
    """A planned external runtime could not be assembled or exercised safely."""


@dataclass(frozen=True)
class DownloadResult:
    name: str
    path: str
    algorithm: str
    expected: str
    actual: str
    reused_cache: bool


@dataclass(frozen=True)
class ModCopyResult:
    name: str
    source: str
    destination: str
    expected_sha256: str
    actual_sha256: str


@dataclass(frozen=True)
class MarkerEvent:
    """One observed lifecycle event with a strictly ordered UTC timestamp."""

    name: str
    timestamp_utc: str


@dataclass(frozen=True)
class RuntimeIdentity:
    loader: str
    loader_identity: str
    launcher_path: str
    minecraft_server_path: str
    minecraft_server_expected: str
    minecraft_server_actual: str


@dataclass(frozen=True)
class ExternalRuntimeSmokeResult:
    cell_id: str
    loader: str
    minecraft_version: str
    verdict: Verdict
    reason: str | None
    downloads: tuple[DownloadResult, ...]
    installer: ExecutedCommand | None
    mods: tuple[ModCopyResult, ...]
    launcher_verified: bool
    observed_markers: tuple[str, ...]
    marker_ledger: tuple[MarkerEvent, ...]
    runtime_identity: RuntimeIdentity | None
    stop_marker: str | None
    server_return_code: int | None
    server_log: str | None
    started_at_utc: str
    elapsed_seconds: float


UrlOpen = Callable[..., Any]
CommandExecutor = Callable[[CommandRecord, QualificationPaths], ExecutedCommand]
ServerRunner = Callable[..., tuple]


class _MarkerLedger:
    """Append immutable event names in deterministic, strictly increasing time."""

    def __init__(self) -> None:
        self._events: list[MarkerEvent] = []
        self._last: datetime | None = None

    def add(self, name: str) -> None:
        if not name or any(event.name == name for event in self._events):
            raise ExternalRuntimeExecutionError(f"invalid or duplicate runtime marker {name!r}")
        moment = datetime.now(timezone.utc)
        if self._last is not None and moment <= self._last:
            moment = self._last + timedelta(microseconds=1)
        self._last = moment
        self._events.append(MarkerEvent(name, moment.isoformat(timespec="microseconds").replace("+00:00", "Z")))

    def events(self) -> tuple[MarkerEvent, ...]:
        return tuple(self._events)


def _regular_file(path: Path, label: str) -> None:
    try:
        status = path.lstat()
    except OSError as error:
        raise ExternalRuntimeExecutionError(f"{label} is unavailable: {path}") from error
    if not path.is_file() or path.is_symlink() or not stat.S_ISREG(status.st_mode):
        raise ExternalRuntimeExecutionError(f"{label} must be a regular non-symlink file: {path}")


def _assert_contained(plan: ExternalRuntimeSmokePlan, paths: QualificationPaths) -> None:
    qualification_root = paths.repository_root / "dist" / "qualification"
    if not is_within(paths.cell_root, qualification_root):
        raise ExternalRuntimeExecutionError("qualification cell is outside dist/qualification")
    if plan.cell_id != paths.cell_id:
        raise ExternalRuntimeExecutionError("external runtime plan and qualification paths select different cells")
    required = (
        plan.layout.root,
        plan.layout.mods_directory,
        plan.layout.config_directory,
        plan.layout.eula_path,
        plan.layout.server_properties_path,
        plan.layout.ringworld_properties_path,
        plan.layout.log_path,
    )
    if not all(is_within(value, paths.cell_root) for value in required):
        raise ExternalRuntimeExecutionError("external runtime plan escapes its disposable qualification cell")
    if not is_within(plan.lock_path, qualification_root):
        raise ExternalRuntimeExecutionError("external runtime plan lock escapes dist/qualification")
    if plan.lock_path != paths.lock_path:
        raise ExternalRuntimeExecutionError("external runtime plan does not use the cell's stable lock")
    for download in (plan.minecraft_server, *plan.downloads):
        if not is_within(download.destination, paths.cache_directory):
            raise ExternalRuntimeExecutionError("download destination escapes the disposable cache")
    for file in plan.files:
        if not is_within(file.path, plan.layout.root):
            raise ExternalRuntimeExecutionError("planned file escapes disposable runtime")
    for mod in plan.mods:
        if not is_within(mod.destination, plan.layout.mods_directory):
            raise ExternalRuntimeExecutionError("planned mod destination escapes mods directory")
    if plan.layout.root.exists():
        raise ExternalRuntimeExecutionError("external runtime already exists; use a fresh qualification run id")


def _assert_no_symlink_components(path: Path, ancestor: Path, label: str) -> None:
    """Refuse a writable runtime path routed through an existing symlink."""
    if not is_within(path, ancestor):
        raise ExternalRuntimeExecutionError(f"{label} escapes its qualification ancestor")
    current = ancestor
    try:
        relative = path.resolve(strict=False).relative_to(ancestor.resolve(strict=False))
    except ValueError as error:
        raise ExternalRuntimeExecutionError(f"{label} escapes its qualification ancestor") from error
    for part in relative.parts:
        current = current / part
        if current.exists() or current.is_symlink():
            if current.is_symlink():
                raise ExternalRuntimeExecutionError(f"{label} may not traverse symlink {current}")


def _planned_server_port(plan: ExternalRuntimeSmokePlan) -> int:
    properties = [file for file in plan.files if file.path == plan.layout.server_properties_path]
    if len(properties) != 1:
        raise ExternalRuntimeExecutionError("external runtime plan has no unique server.properties file")
    values: dict[str, str] = {}
    for line in properties[0].contents.splitlines():
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ExternalRuntimeExecutionError("planned server.properties contains an invalid line")
        key, value = line.split("=", 1)
        if key in values:
            raise ExternalRuntimeExecutionError("planned server.properties duplicates a key")
        values[key] = value
    if values.get("server-ip") != "127.0.0.1":
        raise ExternalRuntimeExecutionError("external runtime must bind only localhost")
    try:
        port = int(values.get("server-port", ""))
    except ValueError as error:
        raise ExternalRuntimeExecutionError("planned server.properties has an invalid server port") from error
    if not 1 <= port <= 65535:
        raise ExternalRuntimeExecutionError("planned server.properties has an out-of-range server port")
    return port


def _validate_https_url(url: str) -> None:
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ExternalRuntimeExecutionError("pinned runtime download must be an absolute credential-free HTTPS URL")


class _RejectRedirects(HTTPRedirectHandler):
    """Never follow a redirect before its unreviewed target can be checked."""

    def redirect_request(self, request: Any, fp: Any, code: int, message: str, headers: Any, newurl: str) -> Any:
        raise ExternalRuntimeExecutionError("runtime download attempted an unapproved redirect")


def _no_redirect_urlopen(url: str, *, timeout: int) -> Any:
    return build_opener(_RejectRedirects()).open(url, timeout=timeout)


def _atomic_link_new(temporary: Path, destination: Path) -> None:
    try:
        os.link(temporary, destination)
    except FileExistsError as error:
        raise ExternalRuntimeExecutionError(f"refusing to replace existing qualification input {destination}") from error


def fetch_pinned_https(
    download: RuntimeDownload,
    paths: QualificationPaths,
    *,
    opener: UrlOpen = _no_redirect_urlopen,
    timeout_seconds: int = 120,
) -> DownloadResult:
    """Fetch one immutable input, rejecting redirects and checksum mismatches.

    A valid cache entry is reused only after its pin is rechecked.  A wrong or
    symlinked entry is never replaced in place; a new run must be used instead.
    """
    if timeout_seconds < 1 or not is_within(download.destination, paths.cache_directory):
        raise ExternalRuntimeExecutionError("runtime download has an unsafe destination or timeout")
    _validate_https_url(download.url)
    destination = download.destination
    if destination.exists() or destination.is_symlink():
        _regular_file(destination, "cached runtime download")
        checked = verify_pinned_file(destination, download.algorithm, download.checksum)
        if not checked.verified:
            raise ExternalRuntimeExecutionError(f"cached runtime download fails its pin: {download.name}")
        return DownloadResult(download.name, str(destination), checked.algorithm, checked.expected, checked.actual, True)
    _assert_no_symlink_components(destination.parent, paths.cell_root, "runtime download cache")
    destination.parent.mkdir(parents=True, exist_ok=True)
    if not is_within(destination.parent, paths.cell_root):
        raise ExternalRuntimeExecutionError("runtime download parent escapes qualification cell")
    descriptor, name = tempfile.mkstemp(prefix=f".{destination.name}.", suffix=".partial", dir=destination.parent)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as sink:
            response = opener(download.url, timeout=timeout_seconds)
            try:
                final_url = response.geturl() if hasattr(response, "geturl") else download.url
                # A pin is for this exact URL and object.  Following a redirect
                # would silently turn a reviewed host/path into an unreviewed one.
                if final_url != download.url:
                    raise ExternalRuntimeExecutionError("runtime download redirected away from its exact pinned URL")
                _validate_https_url(final_url)
                total = 0
                while True:
                    chunk = response.read(1024 * 1024)
                    if not chunk:
                        break
                    if not isinstance(chunk, bytes):
                        raise ExternalRuntimeExecutionError("runtime download returned non-bytes data")
                    total += len(chunk)
                    if total > MAX_DOWNLOAD_BYTES:
                        raise ExternalRuntimeExecutionError("runtime download exceeds qualification size limit")
                    sink.write(chunk)
                sink.flush()
                os.fsync(sink.fileno())
            finally:
                close = getattr(response, "close", None)
                if callable(close):
                    close()
        checked = verify_pinned_file(temporary, download.algorithm, download.checksum)
        if not checked.verified:
            raise ExternalRuntimeExecutionError(f"downloaded runtime input fails its pin: {download.name}")
        _atomic_link_new(temporary, destination)
        return DownloadResult(download.name, str(destination), checked.algorithm, checked.expected, checked.actual, False)
    finally:
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass


def _write_planned_file(path: Path, contents: str, runtime_root: Path) -> None:
    if not is_within(path, runtime_root) or path.is_symlink() or path.exists():
        raise ExternalRuntimeExecutionError(f"refusing to replace or escape planned runtime file {path}")
    _assert_no_symlink_components(path.parent, runtime_root, "planned runtime file")
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor: int | None = None
    try:
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0), 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as output:
            descriptor = None
            output.write(contents)
            output.flush()
            os.fsync(output.fileno())
    except OSError as error:
        raise ExternalRuntimeExecutionError(f"cannot write planned runtime file {path}: {error}") from error
    finally:
        if descriptor is not None:
            os.close(descriptor)


def _copy_pinned_mod(entry: ModInventoryEntry, runtime_root: Path) -> ModCopyResult:
    if not is_within(entry.destination, runtime_root):
        raise ExternalRuntimeExecutionError("mod destination escapes runtime")
    _regular_file(entry.source, f"{entry.name} source")
    source_check = verify_pinned_file(entry.source, "sha256", entry.sha256)
    if not source_check.verified:
        raise ExternalRuntimeExecutionError(f"{entry.name} source fails its expected SHA-256")
    if entry.destination.exists() or entry.destination.is_symlink():
        raise ExternalRuntimeExecutionError(f"refusing to replace planned mod {entry.destination}")
    _assert_no_symlink_components(entry.destination.parent, runtime_root, "planned mod")
    entry.destination.parent.mkdir(parents=True, exist_ok=True)
    descriptor, name = tempfile.mkstemp(prefix=f".{entry.destination.name}.", suffix=".partial", dir=entry.destination.parent)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as output, entry.source.open("rb") as source:
            shutil.copyfileobj(source, output, 1024 * 1024)
            output.flush()
            os.fsync(output.fileno())
        _atomic_link_new(temporary, entry.destination)
        copied = verify_pinned_file(entry.destination, "sha256", entry.sha256)
        if not copied.verified:
            raise ExternalRuntimeExecutionError(f"copied {entry.name} fails its expected SHA-256")
        return ModCopyResult(entry.name, str(entry.source), str(entry.destination), entry.sha256, copied.actual)
    finally:
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass


def _verify_exact_mod_inventory(plan: ExternalRuntimeSmokePlan) -> None:
    expected = {entry.destination.resolve(strict=False) for entry in plan.mods}
    found = {path.resolve(strict=False) for path in plan.layout.mods_directory.glob("*.jar") if path.is_file()}
    unexpected_ringworld = [path for path in found if path.name.lower().startswith("ringworld") and path not in expected]
    if unexpected_ringworld:
        raise ExternalRuntimeExecutionError("runtime mods contains an extra RingWorld jar")
    if found != expected:
        raise ExternalRuntimeExecutionError("runtime mods inventory does not exactly match the reviewed plan")


def _verify_launcher(plan: ExternalRuntimeSmokePlan) -> None:
    if plan.loader == "fabric":
        if plan.layout.fabric_server_jar is None:
            raise ExternalRuntimeExecutionError("Fabric plan misses its generated launch jar")
        _regular_file(plan.layout.fabric_server_jar, "Fabric generated server launcher")
        if str(plan.layout.fabric_server_jar) not in plan.launch.argv:
            raise ExternalRuntimeExecutionError("Fabric launch command does not use the generated server launcher")
        return
    contract = plan.generated_run_script
    if contract is None or plan.layout.neoforge_run_script is None or plan.layout.neoforge_user_jvm_args is None:
        raise ExternalRuntimeExecutionError("NeoForge plan misses its generated run-script contract")
    if contract.path != plan.layout.neoforge_run_script or contract.required_sibling != plan.layout.neoforge_user_jvm_args:
        raise ExternalRuntimeExecutionError("NeoForge generated run-script contract does not match runtime layout")
    _regular_file(contract.path, "NeoForge generated run script")
    _regular_file(contract.required_sibling, "NeoForge generated JVM arguments")
    if not os.access(contract.path, os.X_OK) or plan.launch.argv != contract.launch_argv:
        raise ExternalRuntimeExecutionError("NeoForge launch command is not the generated executable run script")


def _launcher_path(plan: ExternalRuntimeSmokePlan) -> Path:
    if plan.loader == "fabric":
        assert plan.layout.fabric_server_jar is not None
        return plan.layout.fabric_server_jar
    assert plan.layout.neoforge_run_script is not None
    return plan.layout.neoforge_run_script


def _loader_identity(plan: ExternalRuntimeSmokePlan) -> str:
    """Return the reviewed loader version carried by the installer command."""
    if plan.loader == "fabric":
        try:
            index = plan.installer.argv.index("-loader")
            value = plan.installer.argv[index + 1]
        except (ValueError, IndexError) as error:
            raise ExternalRuntimeExecutionError("Fabric installer command does not declare a loader version") from error
        if not value:
            raise ExternalRuntimeExecutionError("Fabric installer command declares an empty loader version")
        return value
    # NeoForge's official installer artifact is versioned and independently
    # hash-pinned. Retain the exact reviewed URL rather than guessing a version
    # from an installed launcher filename.
    return plan.downloads[0].url


def _installed_minecraft_server(plan: ExternalRuntimeSmokePlan) -> RuntimeIdentity:
    """Find the installer-owned Mojang server copy by its reviewed SHA-1 pin."""
    expected = plan.minecraft_server
    candidates: list[tuple[Path, str]] = []
    for path in plan.layout.root.rglob("*.jar"):
        if not is_within(path, plan.layout.root) or is_within(path, plan.layout.mods_directory):
            continue
        if path.is_symlink() or not path.is_file():
            continue
        verified = verify_pinned_file(path, expected.algorithm, expected.checksum)
        if verified.verified:
            candidates.append((path, verified.actual))
    if len(candidates) != 1:
        detail = "none" if not candidates else ", ".join(str(path) for path, _ in candidates)
        raise ExternalRuntimeExecutionError("installer did not create one hash-verified Mojang server jar: " + detail)
    path, actual = candidates[0]
    return RuntimeIdentity(
        loader=plan.loader,
        loader_identity=_loader_identity(plan),
        launcher_path=str(_launcher_path(plan)),
        minecraft_server_path=str(path),
        minecraft_server_expected=expected.checksum,
        minecraft_server_actual=actual,
    )


def _drain_server_output(source: BinaryIO, received: "queue.Queue[bytes | None]") -> None:
    try:
        for line in iter(source.readline, b""):
            received.put(line)
    finally:
        source.close()
        received.put(None)


def _run_server(
    plan: ExternalRuntimeSmokePlan,
    paths: QualificationPaths,
    ledger: _MarkerLedger | None = None,
) -> tuple[Verdict, str | None, tuple[str, ...], str | None, int | None, str, tuple[MarkerEvent, ...]]:
    ledger = ledger or _MarkerLedger()
    log = paths.logs_directory / "02-external-runtime-server.combined.log"
    if log.exists():
        raise EvidenceError(f"external server log already exists: {log}")
    started = time.monotonic()
    creation_flags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
    try:
        ledger.add("runtime-start")
        process = subprocess.Popen(
            list(plan.launch.argv), cwd=plan.launch.cwd, env=sanitized_environment(), stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=os.name != "nt",
            creationflags=creation_flags,
        )
    except OSError as error:
        ledger.add("runtime-start-failed")
        return Verdict.FAIL, f"SERVER_START_FAILED:{error.__class__.__name__}", (), None, None, str(log), ledger.events()
    assert process.stdout is not None and process.stdin is not None
    received: "queue.Queue[bytes | None]" = queue.Queue()
    drain = threading.Thread(target=_drain_server_output, args=(process.stdout, received), daemon=True)
    drain.start()
    sink = _BoundedRedactingLog(log)
    observed: set[str] = set()
    complete_text = ""
    stop_sent = False
    stop_marker: str | None = None
    fatal_marker: str | None = None
    reason: str | None = None
    try:
        while time.monotonic() - started < plan.launch.timeout_seconds:
            try:
                item = received.get(timeout=0.1)
            except queue.Empty:
                if process.poll() is not None:
                    break
                continue
            if item is None:
                if process.poll() is not None:
                    break
                continue
            sink.feed(item)
            text = item.decode("utf-8", errors="replace")
            complete_text = (complete_text + text)[-65536:]
            for marker in plan.expected_log_markers:
                if marker.required_substring in complete_text:
                    if marker.name not in observed:
                        observed.add(marker.name)
                        ledger.add(marker.name)
            if stop_sent:
                for marker in SERVER_STOP_MARKERS:
                    if marker in complete_text and stop_marker is None:
                        stop_marker = marker
                        ledger.add("clean-stop")
            for marker in SERVER_SAVE_MARKERS:
                if marker in complete_text and not any(event.name == "world-save" for event in ledger.events()):
                    ledger.add("world-save")
            for marker in FATAL_SERVER_MARKERS:
                if marker in complete_text:
                    fatal_marker = marker
                    reason = "FATAL_SERVER_LOG:" + marker
                    break
            if fatal_marker is not None:
                break
            if len(observed) == len(plan.expected_log_markers) and not stop_sent:
                try:
                    process.stdin.write(b"stop\n")
                    process.stdin.flush()
                except OSError:
                    reason = "SERVER_STOP_WRITE_FAILED"
                    break
                stop_sent = True
                ledger.add("stop-sent")
                # ``server-stop`` is the semantic lifecycle transition: we
                # successfully sent the stop command.  ``clean-stop`` records
                # the later vanilla acknowledgement and remains separately
                # reviewable because it can arrive after world saving.
                ledger.add("server-stop")
        if len(observed) != len(plan.expected_log_markers):
            missing = sorted(marker.name for marker in plan.expected_log_markers if marker.name not in observed)
            reason = reason or "MISSING_MARKERS:" + ",".join(missing)
        elif not stop_sent:
            reason = reason or "SERVER_DID_NOT_REACH_STOP_PHASE"
        try:
            return_code = process.wait(timeout=30 if stop_sent else 2)
        except subprocess.TimeoutExpired:
            _terminate_process_group(process)
            return_code = None
            reason = reason or "SERVER_STOP_TIMEOUT"
        # The process has exited; wait for the reader so a final clean-stop
        # line cannot be lost in the queue race.
        drain.join(timeout=10)
        if drain.is_alive():
            raise ExternalRuntimeExecutionError("server output pipe did not close after exit")
        # Drain residual clean-stop output after reaping.
        while True:
            try:
                item = received.get_nowait()
            except queue.Empty:
                break
            if item is None:
                continue
            sink.feed(item)
            text = item.decode("utf-8", errors="replace")
            complete_text = (complete_text + text)[-65536:]
        for marker in SERVER_SAVE_MARKERS:
            if marker in complete_text and not any(event.name == "world-save" for event in ledger.events()):
                ledger.add("world-save")
        for marker in SERVER_STOP_MARKERS:
            if marker in complete_text:
                stop_marker = marker
                if not any(event.name == "clean-stop" for event in ledger.events()):
                    ledger.add("clean-stop")
                break
        for marker in FATAL_SERVER_MARKERS:
            if marker in complete_text:
                fatal_marker = marker
                reason = reason or "FATAL_SERVER_LOG:" + marker
                break
        if return_code != 0:
            reason = reason or f"SERVER_EXIT_{return_code}"
        if stop_marker is None:
            reason = reason or "SERVER_STOP_MARKER_MISSING"
        if not any(event.name == "world-save" for event in ledger.events()):
            reason = reason or "SERVER_SAVE_MARKER_MISSING"
        ledger.add("runtime-exit")
        verdict = Verdict.PASS if reason is None else Verdict.FAIL
        return verdict, reason, tuple(sorted(observed)), stop_marker, return_code, str(log), ledger.events()
    finally:
        if process.poll() is None:
            _terminate_process_group(process)
        try:
            process.stdin.close()
        except OSError:
            pass
        drain.join(timeout=10)
        sink.close()


def _result_payload(result: ExternalRuntimeSmokeResult) -> dict[str, Any]:
    installer: dict[str, Any] | None = None
    if result.installer is not None:
        installer = {
            "verdict": result.installer.verdict.value,
            "argv": list(result.installer.argv),
            "return_code": result.installer.return_code,
            "reason": result.installer.reason,
            "stdout_log": result.installer.stdout_log,
            "stderr_log": result.installer.stderr_log,
        }
    return {
        "cell_id": result.cell_id,
        "loader": result.loader,
        "minecraft_version": result.minecraft_version,
        "verdict": result.verdict.value,
        "reason": result.reason,
        "started_at_utc": result.started_at_utc,
        "elapsed_seconds": result.elapsed_seconds,
        "downloads": [item.__dict__ for item in result.downloads],
        "installer": installer,
        "mods": [item.__dict__ for item in result.mods],
        "launcher_verified": result.launcher_verified,
        "observed_markers": list(result.observed_markers),
        "marker_ledger": [item.__dict__ for item in result.marker_ledger],
        "runtime_identity": None if result.runtime_identity is None else result.runtime_identity.__dict__,
        "stop_marker": result.stop_marker,
        "server_return_code": result.server_return_code,
        "server_log": result.server_log,
    }


def _record_terminal_result(result: ExternalRuntimeSmokeResult, paths: QualificationPaths) -> ExternalRuntimeSmokeResult:
    markdown = (
        f"# External runtime smoke: {result.verdict.value}\n\n"
        f"- Cell: `{result.cell_id}`\n"
        f"- Loader: `{result.loader}`\n"
        f"- Minecraft: `{result.minecraft_version}`\n"
        f"- Reason: `{result.reason or 'none'}`\n"
    )
    write_terminal_report(paths.evidence_directory, _result_payload(result), markdown, stem="external-runtime-smoke")
    return result


def execute_external_runtime_smoke(
    plan: ExternalRuntimeSmokePlan,
    paths: QualificationPaths,
    run_id: str,
    *,
    opener: UrlOpen = _no_redirect_urlopen,
    command_executor: Callable[..., ExecutedCommand] = execute_command,
    server_runner: ServerRunner = _run_server,
    held_lock: QualificationLock | None = None,
) -> ExternalRuntimeSmokeResult:
    """Execute exactly one reviewed dedicated-runtime smoke plan.

    The return value is terminal even for a normal installer/server failure;
    unsafe input, path, or evidence errors raise instead of being turned into a
    misleading runtime verdict.
    """
    _assert_contained(plan, paths)
    # Standalone invocation owns a fresh OS lock.  The serial matrix runner
    # may lend its already-held exact cell lock, but only through the live
    # object that owns that OS lock; lock-file metadata is never sufficient.
    if held_lock is None:
        lock_context = QualificationLock.acquire(plan.lock_path, run_id)
    else:
        held_lock.require_held_for(plan.lock_path, run_id)
        lock_context = nullcontext(held_lock)
    started_at = datetime.now(timezone.utc)
    started = time.monotonic()
    downloads: list[DownloadResult] = []
    copied: list[ModCopyResult] = []
    installer_result: ExecutedCommand | None = None
    ledger = _MarkerLedger()
    with lock_context:
        _assert_no_symlink_components(paths.cell_root, paths.repository_root, "qualification cell")
        _assert_no_symlink_components(plan.layout.root, paths.cell_root, "external runtime")
        create_contained_directories(paths)
        # Fabric Installer requires its target directory to exist.  Create the
        # already-validated fresh root ourselves so both loaders receive the
        # same empty, contained, non-symlink installation boundary.
        plan.layout.root.mkdir(parents=False, exist_ok=False)
        downloads.append(fetch_pinned_https(plan.minecraft_server, paths, opener=opener))
        for item in plan.downloads:
            downloads.append(fetch_pinned_https(item, paths, opener=opener))
        ledger.add("installer-start")
        installer_record = CommandRecord(
            PhaseName.DEDICATED_SMOKE, plan.installer.argv, plan.installer.cwd, (), plan.launch.timeout_seconds,
        )
        installer_result = command_executor(installer_record, paths, ordinal=1)
        if installer_result.verdict is not Verdict.PASS:
            ledger.add("installer-failed")
            return _record_terminal_result(ExternalRuntimeSmokeResult(
                plan.cell_id, plan.loader, plan.minecraft_version, Verdict.FAIL, installer_result.reason,
                tuple(downloads), installer_result, (), False, (), ledger.events(), None, None, None, None,
                started_at.isoformat(), time.monotonic() - started,
            ), paths)
        ledger.add("installer-complete")
        _assert_no_symlink_components(plan.layout.root, paths.cell_root, "installed external runtime")
        _verify_launcher(plan)
        runtime_identity = _installed_minecraft_server(plan)
        for file in plan.files:
            _write_planned_file(file.path, file.contents, plan.layout.root)
        for mod in plan.mods:
            copied.append(_copy_pinned_mod(mod, plan.layout.root))
        _verify_exact_mod_inventory(plan)
        if not socket_port_probe(_planned_server_port(plan)):
            return _record_terminal_result(ExternalRuntimeSmokeResult(
                plan.cell_id, plan.loader, plan.minecraft_version, Verdict.FAIL, "SERVER_PORT_UNAVAILABLE",
                tuple(downloads), installer_result, tuple(copied), True, (), ledger.events(), runtime_identity, None, None, None,
                started_at.isoformat(), time.monotonic() - started,
            ), paths)
        verdict, reason, markers, stop_marker, return_code, server_log, markers_ledger = server_runner(plan, paths, ledger)
        return _record_terminal_result(ExternalRuntimeSmokeResult(
            plan.cell_id, plan.loader, plan.minecraft_version, verdict, reason, tuple(downloads), installer_result,
            tuple(copied), True, markers, markers_ledger, runtime_identity, stop_marker, return_code, server_log,
            started_at.isoformat(), time.monotonic() - started,
        ), paths)
