#!/usr/bin/env python3
"""Production-style Prism client fixture for RingWorld's creation UI.

The fixture is deliberately narrow: it creates one fresh Prism root below a
qualification cell, installs one retained frozen RingWorld jar (plus Fabric
API on Fabric), launches the existing menu-only self-halting client fixture,
and records immutable evidence.  It never reads a normal Prism profile, user
account, save, or live server.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import signal
import stat
import subprocess
import threading
import time
from typing import Any, Callable, Mapping, Sequence
import zipfile

from external_runtime_executor import fetch_pinned_https
from external_runtime_smoke import CandidateJar, RuntimeDownload
from minecraft_qualification_executor import QualificationLock, write_terminal_report
from minecraft_qualification_model import QualificationPaths, Verdict, contained_path, is_within


PRISM_VERSION = "11.0.3"
PRISM_MACOS_ARCHIVE_URL = (
    "https://github.com/PrismLauncher/PrismLauncher/releases/download/11.0.3/"
    "PrismLauncher-macOS-11.0.3.zip"
)
PRISM_MACOS_ARCHIVE_SHA256 = "b8e06ef55ec78fceddfa9f4270b3d4d93f2606b83f70ad6a2c6dde90f2b65408"
MAX_CLIENT_LOG_BYTES = 16 * 1024 * 1024
FIXTURE_NAME = "01-creation-settings-ui"
INSTANCE_ID = "RingWorld-Creation-UI"
PASS_MARKER = "[creation-ui-test] PASS:"
FAIL_MARKERS = ("[creation-ui-test] FAIL:", "Crash report", "Exception in client tick loop", "A fatal error has occurred")
CAPTURES = (
    "creation-ui-01-footer-scale1.png",
    "creation-ui-02-default-scale1.png",
    "creation-ui-03-default-scale2.png",
    "creation-ui-04-default-scale3.png",
    "creation-ui-05-default-scale4.png",
    "creation-ui-06-large-narrow-scale4.png",
    "creation-ui-07-invalid-five-errors-narrow-scale4.png",
    "creation-ui-08-small-scale4.png",
    "creation-ui-09-medium-scale4.png",
    "creation-ui-10-large-scale4.png",
    "creation-ui-11-custom-monument-scale4.png",
    "creation-ui-12-confirm-layout-scale4.png",
    "creation-ui-13-footer-applied-scale4.png",
)
_SHA256 = re.compile(r"^[0-9a-f]{64}$")


class GraphicalCreationUiError(RuntimeError):
    """A graphical fixture input or observation failed closed."""


@dataclass(frozen=True)
class GraphicalCreationUiPlan:
    cell_id: str
    loader: str
    minecraft_version: str
    loader_version: str
    candidate: CandidateJar
    fabric_api: RuntimeDownload | None
    prism_archive: Path
    prism_archive_sha256: str
    java_executable: Path
    runtime_root: Path
    launcher_root: Path
    prism_data_root: Path
    instance_root: Path
    game_root: Path
    evidence_root: Path
    terminal_json: Path
    terminal_markdown: Path
    timeout_seconds: int
    source_provenance: Mapping[str, str]


@dataclass(frozen=True)
class GraphicalCreationUiResult:
    cell_id: str
    loader: str
    minecraft_version: str
    verdict: Verdict
    reason: str | None
    candidate_sha256: str
    prism_archive_sha256: str
    prism_executable_sha256: str | None
    fabric_api_sha256: str | None
    launcher_log: str | None
    launcher_log_sha256: str | None
    minecraft_log: str | None
    minecraft_log_sha256: str | None
    captures: tuple[Mapping[str, Any], ...]
    exit_code: int | None
    elapsed_seconds: float
    source_provenance: Mapping[str, str]


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _regular(path: Path, label: str) -> None:
    try:
        mode = path.lstat().st_mode
    except OSError as error:
        raise GraphicalCreationUiError(f"{label} is unavailable: {path}") from error
    if path.is_symlink() or not stat.S_ISREG(mode):
        raise GraphicalCreationUiError(f"{label} must be a regular non-symlink file: {path}")


def _dependency(cell: Mapping[str, Any], name: str) -> Mapping[str, Any]:
    matches = [item for item in cell.get("dependencies", ()) if isinstance(item, Mapping) and item.get("name") == name]
    if len(matches) != 1:
        raise GraphicalCreationUiError(f"cell must contain exactly one {name} dependency")
    return matches[0]


def _download_from_dependency(item: Mapping[str, Any], destination: Path) -> RuntimeDownload:
    checksum = item.get("checksum")
    if not isinstance(checksum, Mapping) or checksum.get("algorithm") != "sha256" \
            or not isinstance(checksum.get("value"), str) or _SHA256.fullmatch(checksum["value"]) is None:
        raise GraphicalCreationUiError("client dependency must have one SHA-256 pin")
    url, version = item.get("url"), item.get("version")
    if not isinstance(url, str) or not isinstance(version, str) or not version:
        raise GraphicalCreationUiError("client dependency is missing URL/version identity")
    return RuntimeDownload(str(item["name"]), url, "sha256", checksum["value"], destination)


def creation_ui_plan(
    cell: Mapping[str, Any],
    paths: QualificationPaths,
    candidate: CandidateJar,
    *,
    prism_archive: Path,
    java_executable: Path,
    source_provenance: Mapping[str, str],
) -> GraphicalCreationUiPlan:
    """Build a contained plan without creating, copying, or launching anything."""
    cell_id, loader = cell.get("id"), cell.get("loader")
    minecraft = cell.get("minecraft")
    if cell_id != paths.cell_id or loader not in {"fabric", "neoforge"} \
            or not isinstance(minecraft, Mapping) or not isinstance(minecraft.get("version"), str):
        raise GraphicalCreationUiError("creation UI plan cell identity is invalid")
    if candidate.loader != loader or not candidate.path.is_absolute() or _SHA256.fullmatch(candidate.sha256) is None:
        raise GraphicalCreationUiError("creation UI plan needs a matching absolute frozen candidate")
    if not prism_archive.is_absolute() or not java_executable.is_absolute():
        raise GraphicalCreationUiError("Prism archive and Java executable must be absolute inputs")
    runtime = contained_path(paths.run_directory, f"nightly/{FIXTURE_NAME}/runtime", "creation UI runtime")
    launcher = contained_path(runtime, "launcher", "creation UI launcher")
    data = contained_path(runtime, "prism-data", "creation UI Prism data")
    instance = contained_path(data, f"instances/{INSTANCE_ID}", "creation UI instance")
    game = contained_path(instance, ".minecraft", "creation UI game root")
    evidence = contained_path(paths.evidence_directory, f"nightly/{FIXTURE_NAME}", "creation UI evidence")
    for value in (runtime, launcher, data, instance, game, evidence):
        if not is_within(value, paths.cell_root):
            raise GraphicalCreationUiError("creation UI plan escapes its qualification cell")
    fabric_api = None
    if loader == "fabric":
        dependency = _dependency(cell, "Fabric API")
        fabric_api = _download_from_dependency(
            dependency,
            contained_path(paths.cache_directory, f"fabric-api-{dependency['version']}.jar", "Fabric API cache"),
        )
        loader_version = str(_dependency(cell, "Fabric Loader")["version"])
    else:
        loader_version = str(_dependency(cell, "NeoForge")["version"])
    timeout = cell.get("profile", {}).get("timeout_seconds")
    if not isinstance(timeout, int) or isinstance(timeout, bool) or timeout < 300:
        raise GraphicalCreationUiError("creation UI plan requires a bounded cell timeout")
    return GraphicalCreationUiPlan(
        str(cell_id), str(loader), str(minecraft["version"]), loader_version, candidate,
        fabric_api, prism_archive, PRISM_MACOS_ARCHIVE_SHA256, java_executable,
        runtime, launcher, data, instance, game, evidence,
        contained_path(evidence, "terminal.json", "creation UI terminal JSON"),
        contained_path(evidence, "terminal.md", "creation UI terminal Markdown"),
        timeout, dict(source_provenance),
    )


def _assert_fresh(plan: GraphicalCreationUiPlan, paths: QualificationPaths) -> None:
    if plan.cell_id != paths.cell_id or plan.runtime_root.exists() or plan.evidence_root.exists():
        raise GraphicalCreationUiError("creation UI fixture requires fresh cell output roots")
    _regular(plan.prism_archive, "Prism archive")
    _regular(plan.java_executable, "Java executable")
    _regular(plan.candidate.path, "frozen candidate")
    if _sha256(plan.prism_archive) != plan.prism_archive_sha256:
        raise GraphicalCreationUiError("Prism archive does not match the reviewed SHA-256")
    if _sha256(plan.candidate.path) != plan.candidate.sha256:
        raise GraphicalCreationUiError("frozen candidate changed before graphical execution")
    for destination in (plan.runtime_root, plan.evidence_root):
        current = paths.cell_root
        try:
            relative = destination.resolve(strict=False).relative_to(paths.cell_root.resolve(strict=False))
        except ValueError as error:
            raise GraphicalCreationUiError("graphical destination escapes its qualification cell") from error
        for part in relative.parts:
            current = current / part
            if current.exists() or current.is_symlink():
                if current.is_symlink():
                    raise GraphicalCreationUiError("graphical destination traverses a symlink")


def _extract_prism(archive: Path, destination: Path) -> Path:
    destination.mkdir(parents=True, exist_ok=False)
    with zipfile.ZipFile(archive) as source:
        for member in source.infolist():
            relative = PurePosixPath(member.filename)
            if relative.is_absolute() or not relative.parts or any(part in {"", ".", ".."} for part in relative.parts):
                raise GraphicalCreationUiError("Prism archive contains an unsafe path")
            mode = member.external_attr >> 16
            if stat.S_ISLNK(mode):
                raise GraphicalCreationUiError("Prism archive contains a symbolic link")
            target = destination.joinpath(*relative.parts)
            if not is_within(target, destination):
                raise GraphicalCreationUiError("Prism archive member escapes destination")
            if member.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with source.open(member) as incoming, target.open("xb") as outgoing:
                    shutil.copyfileobj(incoming, outgoing)
                if mode & stat.S_IXUSR:
                    target.chmod(target.stat().st_mode | stat.S_IXUSR)
    matches = list(destination.glob("*.app/Contents/MacOS/prismlauncher"))
    if len(matches) != 1:
        raise GraphicalCreationUiError("Prism archive did not contain exactly one macOS launcher")
    _regular(matches[0], "Prism executable")
    matches[0].chmod(matches[0].stat().st_mode | stat.S_IXUSR)
    return matches[0]


def _write_new(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8", newline="\n") as target:
        target.write(text)


def _instance_pack(plan: GraphicalCreationUiPlan) -> Mapping[str, Any]:
    loader_uid = "net.fabricmc.fabric-loader" if plan.loader == "fabric" else "net.neoforged"
    return {
        "formatVersion": 1,
        "components": [
            {"uid": "net.minecraft", "version": plan.minecraft_version, "important": True},
            {"uid": loader_uid, "version": plan.loader_version},
        ],
    }


def _prepare_instance(plan: GraphicalCreationUiPlan, paths: QualificationPaths) -> tuple[Path, str | None]:
    plan.prism_data_root.mkdir(parents=True, exist_ok=False)
    plan.game_root.mkdir(parents=True, exist_ok=False)
    _write_new(plan.prism_data_root / "prismlauncher.cfg", "[General]\nConfigVersion=1.3\nLanguage=en_US\n")
    _write_new(plan.instance_root / "mmc-pack.json", json.dumps(_instance_pack(plan), indent=2) + "\n")
    java = str(plan.java_executable).replace("\\", "/")
    _write_new(plan.instance_root / "instance.cfg", "\n".join((
        "[General]", "ConfigVersion=1.3", "InstanceType=OneSix", "name=RingWorld Creation UI Qualification",
        "AutomaticJava=false", "OverrideJavaLocation=true", f"JavaPath={java}",
        "OverrideMemory=true", "MinMemAlloc=1024", "MaxMemAlloc=4096",
        "OverrideJavaArgs=true", "JvmArgs=-Dringworld.creationUiTest=true",
        "OverrideWindow=true", "MinecraftWinWidth=1920", "MinecraftWinHeight=1080",
        "AutoCloseConsole=true", "QuitAfterGameStop=true", "ShowConsole=false", "ShowConsoleOnError=true",
        "JoinServerOnLaunch=false", "UseAccountForInstance=false", "",
    )))
    _write_new(plan.game_root / "config/ringworld.properties", "\n".join((
        "widthBlocks=256", "circumferenceBlocks=16384", "wallHeightBlocks=160",
        "testMode=false", "testViewDistanceChunks=6", "pregenerateTerrainAtlas=false",
        "requestOceanMonument=false", "",
    )))
    _write_new(plan.game_root / "options.txt", "onboardAccessibility:false\nguiScale:1\n")
    mods = plan.game_root / "mods"
    mods.mkdir()
    candidate_dest = mods / "ringworld-qualification.jar"
    with plan.candidate.path.open("rb") as incoming, candidate_dest.open("xb") as outgoing:
        shutil.copyfileobj(incoming, outgoing)
    if _sha256(candidate_dest) != plan.candidate.sha256:
        raise GraphicalCreationUiError("copied frozen candidate changed")
    fabric_api_hash = None
    if plan.fabric_api is not None:
        result = fetch_pinned_https(plan.fabric_api, paths)
        fabric_source = Path(result.path)
        fabric_dest = mods / Path(plan.fabric_api.destination).name
        with fabric_source.open("rb") as incoming, fabric_dest.open("xb") as outgoing:
            shutil.copyfileobj(incoming, outgoing)
        fabric_api_hash = _sha256(fabric_dest)
        if fabric_api_hash != plan.fabric_api.checksum:
            raise GraphicalCreationUiError("copied Fabric API changed")
    if sorted(path.name for path in mods.iterdir()) != sorted(
            ["ringworld-qualification.jar"] + ([Path(plan.fabric_api.destination).name] if plan.fabric_api else [])):
        raise GraphicalCreationUiError("graphical fixture mods inventory is not exact")
    return candidate_dest, fabric_api_hash


def _png_dimensions(path: Path) -> tuple[int, int]:
    _regular(path, "creation UI screenshot")
    with path.open("rb") as source:
        header = source.read(24)
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise GraphicalCreationUiError(f"capture is not a valid PNG: {path.name}")
    return int.from_bytes(header[16:20], "big"), int.from_bytes(header[20:24], "big")


def _pump(stream: Any, target: Path, state: dict[str, bool]) -> None:
    written = 0
    with target.open("xb") as output:
        for chunk in iter(lambda: stream.read(8192), b""):
            remaining = MAX_CLIENT_LOG_BYTES - written
            if remaining > 0:
                output.write(chunk[:remaining])
                output.flush()
                written += min(len(chunk), remaining)
            if len(chunk) > remaining:
                state["truncated"] = True


def _stop_process(process: subprocess.Popen[bytes]) -> None:
    if process.poll() is not None:
        return
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass


def _run_prism(plan: GraphicalCreationUiPlan, executable: Path) -> tuple[int, Path, Path]:
    launcher_log = plan.evidence_root / "prism-launcher.log"
    minecraft_log = plan.game_root / "logs/latest.log"
    argv = (str(executable), "-d", str(plan.prism_data_root), "-l", INSTANCE_ID, "-o", "CreationUiTester")
    process = subprocess.Popen(
        argv, cwd=plan.runtime_root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        start_new_session=True, env={**os.environ, "JAVA_HOME": str(plan.java_executable.parent.parent)},
    )
    assert process.stdout is not None
    pump_state = {"truncated": False}
    thread = threading.Thread(target=_pump, args=(process.stdout, launcher_log, pump_state), daemon=True)
    thread.start()
    deadline = time.monotonic() + plan.timeout_seconds
    passed = False
    try:
        while time.monotonic() < deadline:
            text = ""
            if minecraft_log.is_file() and not minecraft_log.is_symlink():
                if minecraft_log.stat().st_size > MAX_CLIENT_LOG_BYTES:
                    raise GraphicalCreationUiError("Minecraft client log exceeded its evidence bound")
                text = minecraft_log.read_text(encoding="utf-8", errors="replace")
                if any(marker in text for marker in FAIL_MARKERS):
                    raise GraphicalCreationUiError("Minecraft client logged a fatal creation UI marker")
                if PASS_MARKER in text:
                    passed = True
                    break
            if process.poll() is not None and not passed:
                raise GraphicalCreationUiError("Prism exited before the creation UI fixture passed")
            time.sleep(0.5)
        if not passed:
            raise GraphicalCreationUiError("creation UI fixture timed out")
        try:
            code = process.wait(timeout=60)
        except subprocess.TimeoutExpired as error:
            raise GraphicalCreationUiError("Prism did not exit after the self-halting client") from error
        thread.join(timeout=5)
        if thread.is_alive() or pump_state["truncated"]:
            raise GraphicalCreationUiError("Prism launcher log did not fit its bounded evidence file")
        if code != 0:
            raise GraphicalCreationUiError(f"Prism returned nonzero exit code {code}")
        return code, launcher_log, minecraft_log
    finally:
        _stop_process(process)


def execute_creation_ui(
    plan: GraphicalCreationUiPlan,
    paths: QualificationPaths,
    *,
    held_lock: QualificationLock,
    stage_runner: Callable[[GraphicalCreationUiPlan, Path], tuple[int, Path, Path]] = _run_prism,
) -> GraphicalCreationUiResult:
    """Execute one isolated real Prism client and write terminal evidence."""
    started = time.monotonic()
    held_lock.require_held_for(paths.lock_path, paths.run_id)
    _assert_fresh(plan, paths)
    plan.runtime_root.parent.mkdir(parents=True, exist_ok=True)
    plan.evidence_root.mkdir(parents=True, exist_ok=False)
    result: GraphicalCreationUiResult
    executable_hash = fabric_hash = launcher_hash = minecraft_hash = None
    launcher_log = minecraft_log = None
    exit_code = None
    captures: tuple[Mapping[str, Any], ...] = ()
    try:
        executable = _extract_prism(plan.prism_archive, plan.launcher_root)
        executable_hash = _sha256(executable)
        _prepare_instance(plan, paths)
        exit_code, launcher_path, minecraft_path = stage_runner(plan, executable)
        launcher_log, minecraft_log = str(launcher_path), str(minecraft_path)
        launcher_hash, minecraft_hash = _sha256(launcher_path), _sha256(minecraft_path)
        capture_rows = []
        for name in CAPTURES:
            capture = plan.game_root / "screenshots" / name
            width, height = _png_dimensions(capture)
            capture_rows.append({"name": name, "width": width, "height": height, "sha256": _sha256(capture)})
        captures = tuple(capture_rows)
        if (plan.game_root / "saves").exists() and any((plan.game_root / "saves").rglob("level.dat")):
            raise GraphicalCreationUiError("menu-only fixture unexpectedly created a world")
        if plan.fabric_api is not None:
            fabric_hash = plan.fabric_api.checksum
        if _sha256(plan.candidate.path) != plan.candidate.sha256:
            raise GraphicalCreationUiError("frozen candidate changed during graphical execution")
        result = GraphicalCreationUiResult(
            plan.cell_id, plan.loader, plan.minecraft_version, Verdict.PASS, None,
            plan.candidate.sha256, plan.prism_archive_sha256, executable_hash, fabric_hash,
            launcher_log, launcher_hash, minecraft_log, minecraft_hash, captures, exit_code,
            round(time.monotonic() - started, 3), dict(plan.source_provenance),
        )
    except Exception as error:
        result = GraphicalCreationUiResult(
            plan.cell_id, plan.loader, plan.minecraft_version, Verdict.FAIL, str(error),
            plan.candidate.sha256, plan.prism_archive_sha256, executable_hash, fabric_hash,
            launcher_log, launcher_hash, minecraft_log, minecraft_hash, captures, exit_code,
            round(time.monotonic() - started, 3), dict(plan.source_provenance),
        )
    payload = asdict(result)
    payload["verdict"] = result.verdict.value
    markdown = (
        f"# Creation UI qualification: {result.verdict.value}\n\n"
        f"- Cell: `{result.cell_id}`\n- Candidate: `{result.candidate_sha256}`\n"
        f"- Captures: {len(result.captures)}/{len(CAPTURES)}\n"
        f"- Reason: {result.reason or 'none'}\n"
    )
    written_json, written_markdown = write_terminal_report(
        plan.evidence_root, payload, markdown, stem="terminal",
    )
    if written_json != plan.terminal_json or written_markdown != plan.terminal_markdown:
        raise GraphicalCreationUiError("terminal report paths disagree with the creation UI plan")
    return result
