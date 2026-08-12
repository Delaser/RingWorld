#!/usr/bin/env python3
"""Fail-closed primitives for isolated Minecraft qualification execution.

This module is deliberately stdlib-only.  It does not know how to download a
Minecraft runtime or choose a Gradle task; the planner supplies a reviewed
``CommandRecord`` later.  The primitives here make that future adapter unable
to silently reuse state, publish a partial report as success, or inspect an
ambiguous package.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timezone
from enum import Enum
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import tempfile
import threading
import time
from typing import Any, Mapping, Sequence
import uuid
import zipfile

from minecraft_qualification_model import (
    CommandRecord,
    InvocationError,
    QualificationPaths,
    Verdict,
    contained_path,
    is_within,
)
from verify_distribution_license import parse_neoforge_metadata


LOCK_FORMAT = 1
REPORT_FORMAT = 1
_SAFE_RUN_ID = re.compile(r"^[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$")
_SECRET_KEY = re.compile(r"(?:token|secret|password|credential|private.?key|api.?key)", re.IGNORECASE)
_SAFE_INHERITED_ENVIRONMENT = frozenset(
    {
        "PATH",
        "HOME",
        "TMPDIR",
        "TMP",
        "TEMP",
        "LANG",
        "JAVA_HOME",
        "JDK_HOME",
        "GRADLE_USER_HOME",
        "SYSTEMROOT",
        "WINDIR",
        "COMSPEC",
    }
)
_SAFE_PREFIXED_ENVIRONMENT = ("LC_",)
MAX_LOG_BYTES = 2 * 1024 * 1024
_LOG_TRUNCATION_MARKER = b"\n[RingWorld qualification log truncated]\n"
_LOG_REDACTION_TAIL_BYTES = 4096
_LOG_SECRET_VALUE = re.compile(
    r"(?i)((?:token|secret|password|credential|private[ _-]?key|api[ _-]?key)\s*(?:=|:)\s*)([^\s\"']+)"
)
_LOG_BEARER_VALUE = re.compile(r"(?i)(authorization\s*:\s*bearer\s+)([^\s\"']+)")
FORBIDDEN_STALE_LICENSE_STRINGS = (
    "LicenseRef-RingWorld-Evaluation-1.0",
    "RingWorld Evaluation License",
    "MIT",
)
CANONICAL_RINGWORLD_MPL_SHA256 = "1f256ecad192880510e84ad60474eab7589218784b9a50bc7ceee34c2b91f1d5"


class QualificationExecutionError(RuntimeError):
    """Qualification execution could not safely continue."""


class LockError(QualificationExecutionError):
    """A per-cell execution lock is missing, malformed, or owned elsewhere."""


class EvidenceError(QualificationExecutionError):
    """Evidence would be ambiguous, incomplete, or overwritten."""


class PackageVerificationError(QualificationExecutionError):
    """A candidate jar does not match the declared diagnostic identity."""


def new_run_id(now: datetime | None = None) -> str:
    """Return a UTC, collision-resistant run identifier suitable for paths."""
    instant = now or datetime.now(timezone.utc)
    if instant.tzinfo is None:
        raise QualificationExecutionError("run-id time must be timezone-aware")
    stamp = instant.astimezone(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    return f"{stamp}-{uuid.uuid4().hex[:12]}"


def _assert_run_id(run_id: str) -> str:
    if not isinstance(run_id, str) or not _SAFE_RUN_ID.fullmatch(run_id):
        raise QualificationExecutionError("run id must be a UTC executor run id")
    return run_id


@dataclass(frozen=True)
class LockIdentity:
    pid: int
    hostname: str
    run_id: str

    def as_json(self) -> dict[str, Any]:
        return {"format": LOCK_FORMAT, "pid": self.pid, "hostname": self.hostname, "run_id": self.run_id}


class QualificationLock:
    """Held advisory lock with informative, non-authoritative owner metadata.

    The lock file is deliberately never unlinked.  Reclaiming a PID JSON file
    with unlink/create introduces a compare-and-delete race: an old process
    can erase a new owner's lock.  The operating-system lock is the authority;
    the JSON snapshot only helps an operator understand who last held it.
    """

    def __init__(self, path: Path, identity: LockIdentity, descriptor: int) -> None:
        self.path = path
        self.identity = identity
        self._descriptor = descriptor
        self._held = False

    @staticmethod
    def _acquire_os_lock(descriptor: int) -> None:
        if os.name == "nt":
            import msvcrt

            os.lseek(descriptor, 0, os.SEEK_SET)
            if os.fstat(descriptor).st_size == 0:
                os.write(descriptor, b"\0")
                os.fsync(descriptor)
                os.lseek(descriptor, 0, os.SEEK_SET)
            try:
                msvcrt.locking(descriptor, msvcrt.LK_NBLCK, 1)
            except OSError as error:
                raise LockError("qualification lock is currently held") from error
            return
        try:
            import fcntl
        except ImportError as error:  # pragma: no cover - every supported host has one backend
            raise LockError("no safe operating-system lock backend is available") from error
        try:
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            raise LockError("qualification lock is currently held") from error

    @staticmethod
    def _release_os_lock(descriptor: int) -> None:
        if os.name == "nt":
            import msvcrt

            os.lseek(descriptor, 0, os.SEEK_SET)
            msvcrt.locking(descriptor, msvcrt.LK_UNLCK, 1)
            return
        import fcntl

        fcntl.flock(descriptor, fcntl.LOCK_UN)

    @classmethod
    def acquire(
        cls,
        path: Path,
        run_id: str,
        *,
        recover_same_host_dead_pid: bool = False,
        hostname: str | None = None,
        pid: int | None = None,
    ) -> "QualificationLock":
        run_id = _assert_run_id(run_id)
        identity = LockIdentity(pid=os.getpid() if pid is None else pid, hostname=hostname or socket.gethostname(), run_id=run_id)
        if identity.pid < 1 or not identity.hostname:
            raise LockError("qualification lock identity is invalid")
        path.parent.mkdir(parents=True, exist_ok=True)
        flags = os.O_RDWR | os.O_CREAT
        flags |= getattr(os, "O_CLOEXEC", 0)
        flags |= getattr(os, "O_NOFOLLOW", 0)
        try:
            descriptor = os.open(path, flags, 0o600)
        except OSError as error:
            raise LockError(f"cannot open qualification lock {path}: {error}") from error
        try:
            cls._acquire_os_lock(descriptor)
        except Exception:
            os.close(descriptor)
            raise
        lock = cls(path, identity, descriptor)
        payload = (json.dumps(identity.as_json(), sort_keys=True) + "\n").encode("utf-8")
        # ``recover_same_host_dead_pid`` is retained as a source-compatible
        # advisory flag. A released OS lock is reusable regardless of stale
        # metadata; a held lock is never reclaimed by PID guessing.
        del recover_same_host_dead_pid
        try:
            os.ftruncate(descriptor, 0)
            os.lseek(descriptor, 0, os.SEEK_SET)
            os.write(descriptor, payload)
            os.fsync(descriptor)
        except OSError as error:
            try:
                cls._release_os_lock(descriptor)
            finally:
                os.close(descriptor)
            raise LockError(f"cannot write qualification lock {path}: {error}") from error
        lock._held = True
        return lock

    def release(self) -> None:
        if not self._held:
            return
        try:
            self._release_os_lock(self._descriptor)
        except OSError as error:
            raise LockError(f"cannot release qualification lock {self.path}: {error}") from error
        finally:
            self._held = False
            os.close(self._descriptor)

    def __enter__(self) -> "QualificationLock":
        return self

    def __exit__(self, exception_type: object, exception: object, traceback: object) -> None:
        self.release()

    def require_held_for(self, path: Path, run_id: str) -> None:
        """Bind a borrowed lock to exactly one qualification execution.

        This is intentionally an instance capability, not a reconstruction
        from lock-file JSON: the operating-system lock held by this exact
        object is the authority.  It lets a higher-level serial runner lend
        its existing lock to a nested executor without a self-deadlock.
        """
        _assert_run_id(run_id)
        if not self._held:
            raise LockError("supplied qualification lock is not held")
        if self.identity.run_id != run_id:
            raise LockError("supplied qualification lock belongs to a different run id")
        if self.path.resolve(strict=False) != path.resolve(strict=False):
            raise LockError("supplied qualification lock belongs to a different path")


def create_contained_directories(paths: QualificationPaths) -> None:
    """Create only the reviewed, per-cell directories below ``cell_root``."""
    if not is_within(paths.run_root, paths.repository_root):
        raise QualificationExecutionError("qualification run root escapes repository root")
    if not is_within(paths.cell_root, paths.run_root):
        raise QualificationExecutionError("qualification cell root escapes run root")
    for directory in (
        paths.run_root,
        paths.cell_root,
        paths.gradle_home,
        paths.run_directory,
        paths.cache_directory,
        paths.build_directory,
        paths.evidence_directory,
        paths.logs_directory,
        paths.world_directory,
    ):
        if not is_within(directory, paths.cell_root) and directory != paths.run_root:
            raise QualificationExecutionError(f"qualification directory escapes its cell root: {directory}")
        directory.mkdir(parents=True, exist_ok=True)


def sanitized_environment(
    supplied: Sequence[tuple[str, str]] = (), *, inherited: Mapping[str, str] | None = None
) -> dict[str, str]:
    """Return an explicit minimal environment, excluding credential-like keys."""
    source = os.environ if inherited is None else inherited
    result: dict[str, str] = {}
    for key, value in source.items():
        upper = key.upper()
        if _SECRET_KEY.search(key):
            continue
        if upper in _SAFE_INHERITED_ENVIRONMENT or any(upper.startswith(prefix) for prefix in _SAFE_PREFIXED_ENVIRONMENT):
            result[key] = value
    for key, value in supplied:
        if not isinstance(key, str) or not isinstance(value, str) or not key or "=" in key:
            raise QualificationExecutionError("command environment contains an invalid key or value")
        if _SECRET_KEY.search(key):
            raise QualificationExecutionError(f"command environment may not set secret-like key {key!r}")
        upper = key.upper()
        if upper not in _SAFE_INHERITED_ENVIRONMENT and not any(upper.startswith(prefix) for prefix in _SAFE_PREFIXED_ENVIRONMENT):
            raise QualificationExecutionError(f"command environment key {key!r} is not allowlisted")
        result[key] = value
    return result


@dataclass(frozen=True)
class ExecutedCommand:
    phase: str
    verdict: Verdict
    argv: tuple[str, ...]
    return_code: int | None
    started_at_utc: str
    elapsed_seconds: float
    stdout_log: str
    stderr_log: str
    reason: str | None = None


def _exclusive_log_path(logs_directory: Path, name: str) -> Path:
    path = contained_path(logs_directory, name, "command log")
    if path.exists():
        raise EvidenceError(f"command log already exists: {path}")
    return path


class _BoundedRedactingLog:
    """Drain untrusted process output into a bounded, redacted UTF-8 log."""

    def __init__(self, path: Path) -> None:
        self._destination = path.open("xb")
        self._pending = bytearray()
        self._written = 0
        self._truncated = False

    @staticmethod
    def _redact(value: bytes) -> bytes:
        text = value.decode("utf-8", errors="replace")
        text = _LOG_SECRET_VALUE.sub(r"\1<redacted>", text)
        text = _LOG_BEARER_VALUE.sub(r"\1<redacted>", text)
        return text.encode("utf-8")

    def _write(self, value: bytes) -> None:
        if not value:
            return
        limit = MAX_LOG_BYTES - len(_LOG_TRUNCATION_MARKER)
        remaining = limit - self._written
        if remaining <= 0:
            self._truncated = True
            return
        if len(value) > remaining:
            value = value[:remaining]
            self._truncated = True
        self._destination.write(value)
        self._written += len(value)

    def feed(self, value: bytes) -> None:
        self._pending.extend(value)
        if len(self._pending) <= _LOG_REDACTION_TAIL_BYTES:
            return
        cut = len(self._pending) - _LOG_REDACTION_TAIL_BYTES
        outgoing = bytes(self._pending[:cut])
        del self._pending[:cut]
        self._write(self._redact(outgoing))

    def close(self) -> None:
        try:
            self._write(self._redact(bytes(self._pending)))
            self._pending.clear()
            if self._truncated:
                marker = _LOG_TRUNCATION_MARKER[:MAX_LOG_BYTES - self._written]
                self._destination.write(marker)
                self._written += len(marker)
            self._destination.flush()
            os.fsync(self._destination.fileno())
        finally:
            self._destination.close()


def _drain_pipe(source: Any, sink: _BoundedRedactingLog, failures: list[BaseException]) -> None:
    try:
        for block in iter(lambda: source.read(64 * 1024), b""):
            sink.feed(block)
    except BaseException as error:  # surface log-write failures on the command thread
        failures.append(error)
    finally:
        source.close()


def _terminate_process_group(process: subprocess.Popen[bytes]) -> None:
    """Terminate the whole command group and reap it before evidence closes."""
    if process.poll() is not None:
        return
    if os.name == "nt":
        process.terminate()
    else:
        import signal

        os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=5)
        return
    except subprocess.TimeoutExpired:
        pass
    if os.name == "nt":
        process.kill()
    else:
        import signal

        os.killpg(process.pid, signal.SIGKILL)
    process.wait(timeout=5)


def execute_command(record: CommandRecord, paths: QualificationPaths, *, ordinal: int) -> ExecutedCommand:
    """Run one reviewed command, retaining immutable stdout/stderr logs."""
    if ordinal < 1 or not record.argv or record.timeout_seconds < 1:
        raise QualificationExecutionError("command record is invalid")
    if not is_within(record.cwd, paths.repository_root):
        raise QualificationExecutionError("command working directory escapes repository root")
    if not record.cwd.is_dir():
        raise QualificationExecutionError(f"command working directory does not exist: {record.cwd}")
    create_contained_directories(paths)
    prefix = f"{ordinal:02d}-{record.phase.value.lower()}"
    stdout_path = _exclusive_log_path(paths.logs_directory, f"{prefix}.stdout.log")
    stderr_path = _exclusive_log_path(paths.logs_directory, f"{prefix}.stderr.log")
    started = datetime.now(timezone.utc)
    monotonic_start = time.monotonic()
    stdout_sink: _BoundedRedactingLog | None = None
    stderr_sink: _BoundedRedactingLog | None = None
    try:
        stdout_sink = _BoundedRedactingLog(stdout_path)
        stderr_sink = _BoundedRedactingLog(stderr_path)
        creation_flags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
        process = subprocess.Popen(
            list(record.argv), cwd=record.cwd, env=sanitized_environment(record.environment),
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=os.name != "nt",
            creationflags=creation_flags,
        )
        assert process.stdout is not None and process.stderr is not None
        drain_failures: list[BaseException] = []
        stdout_thread = threading.Thread(target=_drain_pipe, args=(process.stdout, stdout_sink, drain_failures), daemon=True)
        stderr_thread = threading.Thread(target=_drain_pipe, args=(process.stderr, stderr_sink, drain_failures), daemon=True)
        stdout_thread.start()
        stderr_thread.start()
        try:
            return_code = process.wait(timeout=record.timeout_seconds)
            timeout_reason = None
        except subprocess.TimeoutExpired:
            _terminate_process_group(process)
            return_code = None
            timeout_reason = f"TIMEOUT_AFTER_{record.timeout_seconds}_SECONDS"
        stdout_thread.join(timeout=10)
        stderr_thread.join(timeout=10)
        if stdout_thread.is_alive() or stderr_thread.is_alive():
            raise QualificationExecutionError("command output pipes did not close after process termination")
        if drain_failures:
            raise QualificationExecutionError("command output could not be recorded safely") from drain_failures[0]
    except OSError as error:
        # A launch failure still has immutable logs if they were created.
        return ExecutedCommand(
            record.phase.value, Verdict.FAIL, record.argv, None, started.isoformat(), time.monotonic() - monotonic_start,
            str(stdout_path), str(stderr_path), f"PROCESS_START_FAILED:{error.__class__.__name__}",
        )
    finally:
        if stdout_sink is not None:
            stdout_sink.close()
        if stderr_sink is not None:
            stderr_sink.close()
    if timeout_reason is not None:
        return ExecutedCommand(
            record.phase.value, Verdict.FAIL, record.argv, None, started.isoformat(), time.monotonic() - monotonic_start,
            str(stdout_path), str(stderr_path), timeout_reason,
        )
    verdict = Verdict.PASS if return_code == 0 else Verdict.FAIL
    return ExecutedCommand(
        record.phase.value, verdict, record.argv, return_code, started.isoformat(), time.monotonic() - monotonic_start,
        str(stdout_path), str(stderr_path), None if verdict is Verdict.PASS else f"EXIT_{return_code}",
    )


def _atomic_write_new(path: Path, content: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        raise EvidenceError(f"evidence already exists: {path}")
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as destination:
            destination.write(content)
            destination.flush()
            os.fsync(destination.fileno())
        try:
            os.link(temporary, path)
        except FileExistsError as error:
            raise EvidenceError(f"evidence already exists: {path}") from error
    finally:
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass


def write_terminal_report(
    evidence_directory: Path,
    report: Mapping[str, Any],
    markdown: str,
    *,
    stem: str = "qualification-report",
) -> tuple[Path, Path]:
    """Atomically create immutable JSON and Markdown terminal evidence files."""
    if not isinstance(markdown, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,95}", stem):
        raise EvidenceError("report stem or markdown is invalid")
    json_path = contained_path(evidence_directory, f"{stem}.json", "JSON report")
    markdown_path = contained_path(evidence_directory, f"{stem}.md", "Markdown report")
    serialised = json.dumps({"format": REPORT_FORMAT, **report}, indent=2, sort_keys=True).encode("utf-8") + b"\n"
    _atomic_write_new(json_path, serialised)
    try:
        _atomic_write_new(markdown_path, markdown.encode("utf-8"))
    except Exception:
        # Do not delete the JSON: immutable partial evidence is safer than
        # silently rewriting a terminal result.  Callers must report failure.
        raise
    return json_path, markdown_path


@dataclass(frozen=True)
class PinnedFileVerification:
    path: str
    algorithm: str
    expected: str
    actual: str
    verified: bool


def verify_pinned_file(path: Path, algorithm: str, expected: str) -> PinnedFileVerification:
    """Verify a SHA-1 or SHA-256 pinned local input without downloading it."""
    normalized_algorithm = algorithm.lower()
    if normalized_algorithm not in {"sha1", "sha256"}:
        raise QualificationExecutionError("only sha1 and sha256 qualification pins are supported")
    expected = expected.lower()
    expected_length = 40 if normalized_algorithm == "sha1" else 64
    if not re.fullmatch(rf"[0-9a-f]{{{expected_length}}}", expected):
        raise QualificationExecutionError("pinned checksum has an invalid hexadecimal length")
    digest = hashlib.new(normalized_algorithm)
    try:
        with path.open("rb") as source:
            for block in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(block)
    except OSError as error:
        raise QualificationExecutionError(f"cannot read pinned file {path}: {error}") from error
    actual = digest.hexdigest()
    return PinnedFileVerification(str(path), normalized_algorithm, expected, actual, actual == expected)


def exact_runtime_jar(root: Path) -> Path:
    """Return one runtime jar or reject ambiguity and source jars."""
    if not root.is_dir():
        raise PackageVerificationError(f"runtime-jar directory does not exist: {root}")
    jars = tuple(sorted(candidate for candidate in root.rglob("*.jar") if candidate.is_file()))
    source_jars = tuple(candidate for candidate in jars if "-sources" in candidate.name.lower())
    if source_jars:
        raise PackageVerificationError("source jar is not a runtime artifact: " + ", ".join(str(item) for item in source_jars))
    if not jars:
        raise PackageVerificationError("no runtime jar was found")
    if len(jars) != 1:
        raise PackageVerificationError("expected exactly one runtime jar, found: " + ", ".join(str(item) for item in jars))
    return jars[0]


@dataclass(frozen=True)
class JarInspection:
    path: str
    loader: str
    minecraft_version: str
    diagnostic_version: str
    metadata_entry: str
    license_entry: str


def _zip_text(archive: zipfile.ZipFile, name: str) -> str:
    try:
        return archive.read(name).decode("utf-8")
    except KeyError as error:
        raise PackageVerificationError(f"jar misses required entry {name}") from error
    except UnicodeDecodeError as error:
        raise PackageVerificationError(f"jar entry {name} is not UTF-8 text") from error


def _minecraft_dependency_matches(value: Any, minecraft_version: str) -> bool:
    return isinstance(value, str) and value == minecraft_version


def _single_archive_entry(archive: zipfile.ZipFile, name: str) -> str:
    if archive.namelist().count(name) != 1:
        raise PackageVerificationError(f"jar must contain exactly one {name}")
    return _zip_text(archive, name)


def _parse_build_identity(archive: zipfile.ZipFile, diagnostic_version: str) -> None:
    text = _single_archive_entry(archive, "ringworld-build.properties")
    properties: dict[str, str] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise PackageVerificationError("ringworld-build.properties has an invalid line")
        key, value = line.split("=", 1)
        if key in properties:
            raise PackageVerificationError("ringworld-build.properties duplicates a key")
        properties[key] = value
    if properties.get("artifactVersion") != diagnostic_version:
        raise PackageVerificationError("build identity artifactVersion does not match diagnostic version")
    release_label = properties.get("releaseLabel")
    if not isinstance(release_label, str) or not re.fullmatch(r"qualification-[A-Za-z0-9][A-Za-z0-9._-]{0,95}", release_label):
        raise PackageVerificationError("build identity does not declare a safe qualification release label")


def _single_neoforge_dependency(metadata: Mapping[str, Any], mod_id: str) -> Mapping[str, Any]:
    dependencies = metadata.get("dependencies")
    if not isinstance(dependencies, Mapping):
        raise PackageVerificationError("NeoForge metadata has no dependency table")
    entries = dependencies.get("ringworld")
    if not isinstance(entries, Sequence) or isinstance(entries, (str, bytes)):
        raise PackageVerificationError("NeoForge metadata has no RingWorld dependencies")
    matches = [entry for entry in entries if isinstance(entry, Mapping) and entry.get("modId") == mod_id]
    if len(matches) != 1:
        raise PackageVerificationError(f"NeoForge metadata must declare exactly one {mod_id} dependency")
    return matches[0]


def inspect_runtime_jar(
    path: Path,
    *,
    loader: str,
    minecraft_version: str,
    diagnostic_version: str,
    forbidden_strings: Sequence[str] = FORBIDDEN_STALE_LICENSE_STRINGS,
) -> JarInspection:
    """Fail closed unless a jar declares the expected loader and MPL identity."""
    if loader not in {"fabric", "neoforge"}:
        raise PackageVerificationError(f"unsupported loader {loader!r}")
    if not path.is_file() or path.suffix.lower() != ".jar":
        raise PackageVerificationError("candidate is not a runtime jar")
    try:
        with zipfile.ZipFile(path) as archive:
            license_entry = "LICENSE-RINGWORLD.txt"
            names = archive.namelist()
            duplicates = sorted(name for name, count in Counter(names).items() if count > 1)
            if duplicates:
                raise PackageVerificationError("jar has duplicate archive entries: " + ", ".join(duplicates))
            fabric_present = "fabric.mod.json" in names
            neoforge_present = "META-INF/neoforge.mods.toml" in names
            if fabric_present == neoforge_present:
                raise PackageVerificationError("jar must contain metadata for exactly one supported loader")
            if loader == "fabric" and not fabric_present:
                raise PackageVerificationError("fabric jar is missing fabric.mod.json")
            if loader == "neoforge" and not neoforge_present:
                raise PackageVerificationError("NeoForge jar is missing META-INF/neoforge.mods.toml")
            license_text = _single_archive_entry(archive, license_entry)
            if hashlib.sha256(archive.read(license_entry)).hexdigest() != CANONICAL_RINGWORLD_MPL_SHA256:
                raise PackageVerificationError("embedded RingWorld license is not the canonical MPL-2.0 text")
            text_entries = [
                name for name in names
                if name.lower().endswith((".json", ".toml", ".txt", ".properties", ".mf", ".md"))
                or Path(name).name.lower().startswith("license")
            ]
            for name in text_entries:
                info = archive.getinfo(name)
                if info.file_size > 5 * 1024 * 1024:
                    raise PackageVerificationError(f"jar text entry is too large to inspect safely: {name}")
                try:
                    text = archive.read(name).decode("utf-8")
                except UnicodeDecodeError:
                    continue
                for forbidden in forbidden_strings:
                    if forbidden.lower() == "mit":
                        found = re.search(r"\bMIT\b", text, re.IGNORECASE)
                    else:
                        found = forbidden.lower() in text.lower()
                    if found:
                        raise PackageVerificationError(f"jar contains stale license string {forbidden!r} in {name}")
            if loader == "fabric":
                metadata_entry = "fabric.mod.json"
                try:
                    metadata = json.loads(_single_archive_entry(archive, metadata_entry))
                except json.JSONDecodeError as error:
                    raise PackageVerificationError("fabric.mod.json is invalid JSON") from error
                if not isinstance(metadata, Mapping):
                    raise PackageVerificationError("fabric.mod.json must be an object")
                if metadata.get("id") != "ringworld":
                    raise PackageVerificationError("fabric jar does not identify RingWorld")
                if metadata.get("version") != diagnostic_version:
                    raise PackageVerificationError("fabric jar diagnostic version does not match")
                if metadata.get("license") != "MPL-2.0":
                    raise PackageVerificationError("fabric metadata does not declare MPL-2.0")
                depends = metadata.get("depends")
                if not isinstance(depends, Mapping) or not _minecraft_dependency_matches(depends.get("minecraft"), minecraft_version):
                    raise PackageVerificationError("fabric metadata does not target the requested Minecraft version")
            else:
                metadata_entry = "META-INF/neoforge.mods.toml"
                try:
                    metadata = parse_neoforge_metadata(_single_archive_entry(archive, metadata_entry))
                except (UnicodeDecodeError, ValueError) as error:
                    raise PackageVerificationError("NeoForge metadata is invalid TOML") from error
                if metadata.get("license") != "MPL-2.0":
                    raise PackageVerificationError("NeoForge metadata does not declare MPL-2.0")
                mods = metadata.get("mods")
                if not isinstance(mods, Sequence) or isinstance(mods, (str, bytes)) or len(mods) != 1 \
                        or not isinstance(mods[0], Mapping) or mods[0].get("modId") != "ringworld":
                    raise PackageVerificationError("NeoForge jar does not identify RingWorld")
                if mods[0].get("version") != diagnostic_version:
                    raise PackageVerificationError("NeoForge jar diagnostic version does not match")
                minecraft_dependency = _single_neoforge_dependency(metadata, "minecraft")
                if minecraft_dependency.get("versionRange") != f"[{minecraft_version}]":
                    raise PackageVerificationError("NeoForge metadata does not target the requested Minecraft version")
            _parse_build_identity(archive, diagnostic_version)
    except (OSError, zipfile.BadZipFile) as error:
        raise PackageVerificationError(f"cannot inspect runtime jar {path}: {error}") from error
    return JarInspection(str(path), loader, minecraft_version, diagnostic_version, metadata_entry, license_entry)
