#!/usr/bin/env python3
"""Run the exact frozen-candidate two-client multiplayer fixture for one cell.

The runner consumes a previously passed quick run, launches its retained jar
against one exact manifest cell, and writes immutable nightly evidence. It
never builds a replacement jar and never reads launcher accounts.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import socket
import struct
import subprocess
import sys
import time
from typing import Any, Mapping, Sequence

from minecraft_qualification_executor import (
    QualificationExecutionError, QualificationLock, create_contained_directories,
    execute_command, new_run_id, sanitized_environment, write_terminal_report,
)
from minecraft_qualification_model import (
    CommandRecord, PhaseName, QualificationPaths, Verdict, gradle_properties,
)
from run_atlas_recovery_qualification import (
    AtlasRecoveryInvocation, AtlasRecoveryInvocationError, _manifest_path,
    prepare_invocation,
)
from run_minecraft_qualification import (
    ROOT, stage_gradle_distribution_zip, validate_gradle_dependency_cache,
    validate_gradle_distribution_zip,
)


FIXTURE = "frozen-multiplayer"
EVIDENCE_SUBDIRECTORY = "nightly/06-seam-gameplay-multiplayer"
PASS_MARKERS = (
    "[multiplayer] full scenario result=true",
    "[multiplayer-extended] ordinary Nether portal wait result=true",
    "[multiplayer-extended] multi-lap Nether portal routing result=true",
    "[multiplayer-extended] seam thunder/lightning result=true",
    "[multiplayer] bidirectional seam placement result=true",
    "[multiplayer-extended] alias block-entity recovery policy result=true",
)
CLIENT_MARKERS = {
    "client-a": (
        "[multiplayer:A] client world fully loaded",
        "[multiplayer:A] local scenario result=true; stopping client",
    ),
    "client-b": (
        "[multiplayer:B] client world fully loaded",
        "[multiplayer:B] local scenario result=true; stopping client",
    ),
}
CAPTURES = (
    "client-a/screenshots/ringworld-multiplayer-a.png",
    "client-b/screenshots/ringworld-multiplayer-b.png",
    "client-a/screenshots/ringworld-multiplayer-weather-a.png",
    "client-b/screenshots/ringworld-multiplayer-weather-b.png",
)
_RUN_ID = re.compile(r"^[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$")


class GradleMultiplayerError(QualificationExecutionError):
    """Frozen multiplayer evidence is unsafe or incomplete."""


def _configure_rcon(server_directory: Path, port: int, password: str) -> Path:
    """Enable loopback RCON for graceful control of the disposable server."""
    if not (1 <= port <= 65535):
        raise GradleMultiplayerError("invalid disposable RCON port")
    properties = server_directory / "server.properties"
    values: dict[str, str] = {}
    if properties.is_file():
        for line in properties.read_text(encoding="utf-8").splitlines():
            if line and not line.lstrip().startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values[key] = value
    values.update({
        "enable-rcon": "true",
        "rcon.password": password,
        "rcon.port": str(port),
        "broadcast-rcon-to-ops": "false",
    })
    properties.parent.mkdir(parents=True, exist_ok=True)
    properties.write_text(
        "# Disposable qualification runtime only.\n"
        + "".join(f"{key}={values[key]}\n" for key in sorted(values)),
        encoding="utf-8",
    )
    return properties


def _receive_rcon_packet(connection: socket.socket) -> tuple[int, int, bytes]:
    def receive_exact(size: int) -> bytes:
        payload = b""
        while len(payload) < size:
            block = connection.recv(size - len(payload))
            if not block:
                raise GradleMultiplayerError("RCON connection closed unexpectedly")
            payload += block
        return payload

    length = struct.unpack("<i", receive_exact(4))[0]
    if length < 10 or length > 1024 * 1024:
        raise GradleMultiplayerError("invalid RCON response length")
    packet = receive_exact(length)
    request_id, packet_type = struct.unpack("<ii", packet[:8])
    if packet[-2:] != b"\x00\x00":
        raise GradleMultiplayerError("malformed RCON response")
    return request_id, packet_type, packet[8:-2]


def _send_rcon_packet(connection: socket.socket, request_id: int,
                      packet_type: int, payload: str) -> None:
    body = struct.pack("<ii", request_id, packet_type) + payload.encode("utf-8") + b"\x00\x00"
    connection.sendall(struct.pack("<i", len(body)) + body)


def _graceful_rcon_stop(port: int, password: str) -> None:
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=10) as connection:
            connection.settimeout(10)
            _send_rcon_packet(connection, 41, 3, password)
            auth_id, _auth_type, _auth_payload = _receive_rcon_packet(connection)
            if auth_id != 41:
                raise GradleMultiplayerError("disposable RCON authentication failed")
            _send_rcon_packet(connection, 42, 2, "stop")
            command_id, _command_type, _command_payload = _receive_rcon_packet(connection)
            if command_id != 42:
                raise GradleMultiplayerError("disposable RCON stop was not acknowledged")
    except OSError as error:
        raise GradleMultiplayerError(f"could not stop disposable server through RCON: {error}") from error


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--cell", required=True)
    result.add_argument("--quick-run-id", required=True)
    result.add_argument("--manifest", default="config/minecraft-version-matrix.json")
    result.add_argument("--gradle-dependency-cache")
    result.add_argument("--gradle-distribution-zip")
    return result


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _tasks(loader: str) -> Mapping[str, str]:
    if loader == "fabric":
        return {
            "prepare": ":prepareMultiplayerHarness",
            "assets": ":downloadAssets",
            "server": ":runMultiplayerServer",
            "client_a": ":runMultiplayerClientA",
            "client_b": ":runMultiplayerClientB",
            "verify": ":verifyMultiplayerHarness",
        }
    if loader == "neoforge":
        return {
            "prepare": ":neoforge:prepareNeoForgeMultiplayerHarness",
            "assets": ":neoforge:downloadAssets",
            "server": ":neoforge:runMultiplayerServer",
            "client_a": ":neoforge:runMultiplayerClientA",
            "client_b": ":neoforge:runMultiplayerClientB",
            "verify": ":neoforge:verifyNeoForgeMultiplayerHarness",
        }
    raise GradleMultiplayerError("unsupported loader")


def _base_argv(prepared: AtlasRecoveryInvocation) -> tuple[str, ...]:
    paths = prepared.paths
    properties = tuple(f"-P{name}={value}" for name, value in gradle_properties(prepared.cell, paths))
    return (
        str(paths.repository_root / "gradlew"), "--console=plain", "--no-daemon", "--max-workers=1",
        "--project-cache-dir", str(paths.cache_directory / "gradle-project"), *properties,
        f"-PringQualificationFrozenCandidateJar={prepared.candidate.path}",
        f"-PringQualificationFrozenCandidateSha256={prepared.candidate.sha256}",
    )


def _record(prepared: AtlasRecoveryInvocation, task_args: Sequence[str], timeout: int,
            dependency_cache: Path | None) -> CommandRecord:
    environment = (("GRADLE_USER_HOME", str(prepared.paths.gradle_home)),)
    if dependency_cache is not None:
        environment += (("GRADLE_RO_DEP_CACHE", str(dependency_cache)),)
    return CommandRecord(
        PhaseName.DEDICATED_SMOKE, _base_argv(prepared) + tuple(task_args),
        prepared.paths.repository_root, environment, timeout,
    )


def _timeout(cell: Mapping[str, Any]) -> int:
    profile = cell.get("profile")
    value = profile.get("timeout_seconds") if isinstance(profile, Mapping) else None
    if not isinstance(value, int) or isinstance(value, bool) or value < 60:
        raise GradleMultiplayerError("cell has no valid multiplayer timeout")
    return value


def _verify_installed_candidates(prepared: AtlasRecoveryInvocation) -> tuple[dict[str, str], ...]:
    root = prepared.paths.run_directory / "run-multiplayer"
    installed: list[dict[str, str]] = []
    for role in ("server", "client-a", "client-b"):
        mods = root / role / "mods"
        jars = tuple(sorted(mods.glob("*.jar")))
        expected = mods / "ringworld-qualification.jar"
        if jars != (expected,) or expected.is_symlink() or not expected.is_file():
            raise GradleMultiplayerError(f"{role} must contain only the retained RingWorld jar")
        digest = _sha256(expected)
        if digest != prepared.candidate.sha256:
            raise GradleMultiplayerError(f"{role} frozen candidate hash changed")
        installed.append({"role": role, "path": str(expected), "sha256": digest})
    return tuple(installed)


def _read_log(path: Path) -> str:
    if path.is_symlink() or not path.is_file():
        return ""
    return path.read_text(encoding="utf-8", errors="replace")


def _wait_marker(process: subprocess.Popen[bytes], log: Path, marker: str,
                 timeout_seconds: int) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        text = _read_log(log)
        if marker in text:
            return
        code = process.poll()
        if code is not None:
            raise GradleMultiplayerError(f"process exited {code} before marker {marker!r}")
        time.sleep(0.25)
    raise GradleMultiplayerError(f"timed out waiting for marker {marker!r}")


def _terminate(process: subprocess.Popen[bytes]) -> None:
    if process.poll() is not None:
        return
    if os.name == "nt":
        process.terminate()
    else:
        os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        if os.name == "nt":
            process.kill()
        else:
            os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=10)


def _start(record: CommandRecord, output: Path, *, stdin: bool = False) -> tuple[subprocess.Popen[bytes], Any]:
    output.parent.mkdir(parents=True, exist_ok=True)
    stream = output.open("xb")
    flags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
    try:
        process = subprocess.Popen(
            list(record.argv), cwd=record.cwd, env=sanitized_environment(record.environment),
            stdin=subprocess.PIPE if stdin else subprocess.DEVNULL,
            stdout=stream, stderr=subprocess.STDOUT, start_new_session=os.name != "nt",
            creationflags=flags,
        )
    except BaseException:
        stream.close()
        raise
    return process, stream


def _process_record(name: str, process: subprocess.Popen[bytes], output: Path,
                    started: str, elapsed: float) -> dict[str, Any]:
    return {
        "name": name, "exit_code": process.returncode, "started_at": started,
        "elapsed_seconds": elapsed, "output": str(output), "output_sha256": _sha256(output),
    }


def _executed_record(name: str, result: Any) -> dict[str, Any]:
    return {
        "name": name, "phase": result.phase, "verdict": result.verdict.value,
        "argv": list(result.argv), "exit_code": result.return_code,
        "started_at": result.started_at_utc, "elapsed_seconds": result.elapsed_seconds,
        "stdout": result.stdout_log, "stderr": result.stderr_log, "reason": result.reason,
    }


def _verify_fixture(prepared: AtlasRecoveryInvocation) -> tuple[dict[str, Any], ...]:
    root = prepared.paths.run_directory / "run-multiplayer"
    logs: list[dict[str, Any]] = []
    server = root / "server/logs/latest.log"
    server_text = _read_log(server)
    if not server_text or any(marker not in server_text for marker in PASS_MARKERS) \
            or "[multiplayer] full scenario result=false" in server_text:
        raise GradleMultiplayerError("server gameplay markers are incomplete")
    expected_version = prepared.cell["minecraft"]["version"]
    if f"Starting minecraft server version {expected_version}" not in server_text:
        raise GradleMultiplayerError("server did not launch the selected Minecraft patch")
    logs.append({"role": "server", "path": str(server), "sha256": _sha256(server)})
    for role, markers in CLIENT_MARKERS.items():
        path = root / role / "logs/latest.log"
        text = _read_log(path)
        if not text or any(marker not in text for marker in markers):
            raise GradleMultiplayerError(f"{role} markers are incomplete")
        patch_marker = (f"Loading Minecraft {expected_version} "
                        if prepared.cell["loader"] == "fabric"
                        else f"Minecraft {expected_version} (minecraft)")
        if patch_marker not in text:
            raise GradleMultiplayerError(f"{role} did not launch the selected Minecraft patch")
        logs.append({"role": role, "path": str(path), "sha256": _sha256(path)})
    for relative in CAPTURES:
        capture = root / relative
        if capture.is_symlink() or not capture.is_file() or capture.stat().st_size < 128 \
                or not capture.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"):
            raise GradleMultiplayerError(f"invalid or missing capture: {relative}")
    return tuple(logs)


def _execute(prepared: AtlasRecoveryInvocation, dependency_cache: Path | None,
             distribution_zip: Path | None) -> dict[str, Any]:
    paths, cell = prepared.paths, prepared.cell
    timeout = _timeout(cell)
    tasks = _tasks(str(cell["loader"]))
    create_contained_directories(paths)
    stage_gradle_distribution_zip(distribution_zip, paths.repository_root, paths)
    eula = paths.run_directory / "run-multiplayer/server/eula.txt"
    eula.parent.mkdir(parents=True, exist_ok=True)
    eula.write_text("# Disposable qualification runtime only.\neula=true\n", encoding="utf-8")

    commands: list[dict[str, Any]] = []
    prepare = execute_command(_record(prepared, (tasks["prepare"],), timeout, dependency_cache), paths, ordinal=1)
    commands.append(_executed_record("prepare", prepare))
    if prepare.verdict is not Verdict.PASS:
        raise GradleMultiplayerError("fixture preparation failed")
    installed = _verify_installed_candidates(prepared)
    assets = execute_command(_record(prepared, (tasks["assets"],), timeout, dependency_cache), paths, ordinal=2)
    commands.append(_executed_record("assets", assets))
    if assets.verdict is not Verdict.PASS:
        raise GradleMultiplayerError("serial asset warmup failed")

    rcon_port = int(cell["profile"]["server_port"]) + 1000
    rcon_password = f"ringworld-{paths.run_id[-12:]}"
    _configure_rcon(paths.run_directory / "run-multiplayer/server", rcon_port, rcon_password)

    prepare_task = tasks["prepare"]
    server_record = _record(prepared, (tasks["server"], "-x", prepare_task), timeout, dependency_cache)
    client_a_record = _record(prepared, (tasks["client_a"],), timeout, dependency_cache)
    client_b_record = _record(prepared, (tasks["client_b"],), timeout, dependency_cache)
    process_specs = (
        ("server", server_record, paths.logs_directory / "03-server-process.log", True),
        ("client-a", client_a_record, paths.logs_directory / "04-client-a-process.log", False),
        ("client-b", client_b_record, paths.logs_directory / "05-client-b-process.log", False),
    )
    running: dict[str, tuple[subprocess.Popen[bytes], Any, Path, str, float]] = {}
    started_monotonic = time.monotonic()
    try:
        name, record, output, with_stdin = process_specs[0]
        process, stream = _start(record, output, stdin=with_stdin)
        running[name] = (process, stream, output, datetime.now(timezone.utc).isoformat(), time.monotonic())
        server_log = paths.run_directory / "run-multiplayer/server/logs/latest.log"
        _wait_marker(process, server_log, "Done (", min(timeout, 300))
        for name, record, output, with_stdin in process_specs[1:]:
            process, stream = _start(record, output, stdin=with_stdin)
            running[name] = (process, stream, output, datetime.now(timezone.utc).isoformat(), time.monotonic())
        for name in ("client-a", "client-b"):
            process = running[name][0]
            try:
                process.wait(timeout=timeout)
            except subprocess.TimeoutExpired as error:
                raise GradleMultiplayerError(f"{name} timed out") from error
            if process.returncode != 0:
                raise GradleMultiplayerError(f"{name} exited {process.returncode}")
        _wait_marker(running["server"][0], server_log, PASS_MARKERS[0], min(timeout, 300))
        server = running["server"][0]
        _graceful_rcon_stop(rcon_port, rcon_password)
        try:
            server.wait(timeout=60)
        except subprocess.TimeoutExpired as error:
            raise GradleMultiplayerError("server did not stop normally") from error
        if server.returncode != 0:
            raise GradleMultiplayerError(f"server exited {server.returncode}")
    finally:
        for process, _stream, _output, _started, _monotonic in running.values():
            _terminate(process)
        for _process, stream, _output, _started, _monotonic in running.values():
            stream.close()

    for name, (process, _stream, output, started, monotonic_start) in running.items():
        commands.append(_process_record(name, process, output, started, time.monotonic() - monotonic_start))
    verify = execute_command(_record(prepared, (tasks["verify"],), timeout, dependency_cache), paths, ordinal=6)
    commands.append(_executed_record("verify", verify))
    if verify.verdict is not Verdict.PASS:
        raise GradleMultiplayerError("Gradle fixture verifier failed")
    game_logs = _verify_fixture(prepared)
    captures = tuple({
        "path": str(paths.run_directory / "run-multiplayer" / relative),
        "sha256": _sha256(paths.run_directory / "run-multiplayer" / relative),
        "bytes": (paths.run_directory / "run-multiplayer" / relative).stat().st_size,
    } for relative in CAPTURES)
    return {
        "commands": commands, "installed_candidates": installed, "game_logs": game_logs,
        "captures": captures, "elapsed_seconds": time.monotonic() - started_monotonic,
    }


def run(arguments: argparse.Namespace, *, repository_root: Path = ROOT) -> dict[str, Any]:
    root = repository_root.resolve(strict=False)
    manifest = _manifest_path(root, arguments.manifest)
    dependency_cache = validate_gradle_dependency_cache(
        Path(arguments.gradle_dependency_cache) if arguments.gradle_dependency_cache else None, root)
    distribution = validate_gradle_distribution_zip(
        Path(arguments.gradle_distribution_zip) if arguments.gradle_distribution_zip else None, root)
    run_id = new_run_id()
    if _RUN_ID.fullmatch(run_id) is None:
        raise GradleMultiplayerError("unsafe run ID")
    prepared = prepare_invocation(
        repository_root=root, manifest_path=manifest, cell_id=arguments.cell,
        quick_run_id=arguments.quick_run_id, run_id=run_id,
    )
    payload: dict[str, Any]
    with QualificationLock.acquire(prepared.paths.lock_path, run_id):
        try:
            details = _execute(prepared, dependency_cache, distribution.source if distribution else None)
            verdict, reason = Verdict.PASS, None
        except (GradleMultiplayerError, OSError, ValueError) as error:
            details, verdict, reason = {}, Verdict.FAIL, str(error)
        payload = {
            "format": 1, "fixture": FIXTURE, "cell": prepared.cell["id"],
            "loader": prepared.cell["loader"], "minecraft": prepared.cell["minecraft"]["version"],
            "run_id": run_id, "verdict": verdict.value, "reason": reason,
            "source": prepared.source_provenance,
            "quick_evidence": {
                "path": str(prepared.quick_terminal_evidence.path),
                "sha256": prepared.quick_terminal_evidence.sha256,
            },
            "frozen_candidate": {
                "path": str(prepared.candidate.path), "sha256": prepared.candidate.sha256,
                "minecraft_range": prepared.candidate.declared_target_range,
            },
            **details,
            "claims": {
                "exact_patch_dependencies": verdict is Verdict.PASS,
                "frozen_candidate_jar": verdict is Verdict.PASS,
                "two_real_clients": verdict is Verdict.PASS,
                "dedicated_server": verdict is Verdict.PASS,
                "production_launcher": False,
            },
        }
        write_terminal_report(
            prepared.paths.evidence_directory / EVIDENCE_SUBDIRECTORY, payload,
            f"# {prepared.cell['id']} frozen multiplayer qualification\n\n"
            f"Verdict: **{verdict.value}**\n\n"
            "Exact retained-jar, exact-patch dedicated server and two-client gameplay evidence. "
            "This is not a packaged production-launcher claim.\n",
            stem="terminal",
        )
    return payload


def main(argv: list[str] | None = None) -> int:
    arguments = parser().parse_args(argv)
    try:
        result = run(arguments)
    except (AtlasRecoveryInvocationError, GradleMultiplayerError, QualificationExecutionError,
            OSError, ValueError) as error:
        print("INVOCATION ERROR: " + str(error), file=sys.stderr)
        return 2
    print(json.dumps({key: result.get(key) for key in (
        "fixture", "cell", "loader", "minecraft", "run_id", "verdict", "reason",
    )}, sort_keys=True))
    return 0 if result["verdict"] == Verdict.PASS.value else 1


if __name__ == "__main__":
    raise SystemExit(main())
