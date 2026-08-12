#!/usr/bin/env python3
"""Pure external dedicated-runtime smoke-plan model.

This module is deliberately a plan, not an installer or launcher.  It never
downloads, reads a candidate jar, creates a directory, accepts an EULA outside
a disposable cell, or starts a process.  The execution adapter that follows
must consume this model verbatim and record its evidence separately.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import re
from typing import Any, Mapping, Sequence

from minecraft_qualification_model import (
    InvocationError,
    QualificationPaths,
    contained_path,
    is_within,
    qualification_port,
    required_minecraft_version,
)


SHA256 = re.compile(r"^[0-9a-f]{64}$")
SAFE_SMALL_CIRCUMFERENCE = 2048
SAFE_SMALL_WIDTH = 416
SAFE_SMALL_WALL_HEIGHT = 160
SAFE_SMALL_SEED = "ringworld-qualification-safe-small-v1"


@dataclass(frozen=True)
class CandidateJar:
    """An already-built jar which an execution adapter must checksum again."""

    path: Path
    sha256: str
    loader: str
    declared_target_range: str | None = None


@dataclass(frozen=True)
class RuntimeDownload:
    name: str
    url: str
    algorithm: str
    checksum: str
    destination: Path


@dataclass(frozen=True)
class InstallerInvocation:
    loader: str
    argv: tuple[str, ...]
    cwd: Path


@dataclass(frozen=True)
class RuntimeLayout:
    """Every destination is below one disposable cell root."""

    root: Path
    mods_directory: Path
    config_directory: Path
    eula_path: Path
    server_properties_path: Path
    ringworld_properties_path: Path
    log_path: Path
    fabric_server_jar: Path | None
    neoforge_run_script: Path | None
    neoforge_user_jvm_args: Path | None


@dataclass(frozen=True)
class PlannedFile:
    path: Path
    contents: str


@dataclass(frozen=True)
class ModInventoryEntry:
    name: str
    source: Path
    sha256: str
    destination: Path


@dataclass(frozen=True)
class GeneratedRunScriptContract:
    """The file created by the official NeoForge installer, not a local shim."""

    path: Path
    launch_argv: tuple[str, ...]
    required_sibling: Path


@dataclass(frozen=True)
class LaunchPlan:
    argv: tuple[str, ...]
    cwd: Path
    timeout_seconds: int


@dataclass(frozen=True)
class ExpectedLogMarker:
    name: str
    required_substring: str


@dataclass(frozen=True)
class ExternalRuntimeSmokePlan:
    cell_id: str
    minecraft_version: str
    loader: str
    candidate: CandidateJar
    candidate_origin: str
    downloads: tuple[RuntimeDownload, ...]
    installer: InstallerInvocation
    layout: RuntimeLayout
    files: tuple[PlannedFile, ...]
    mods: tuple[ModInventoryEntry, ...]
    launch: LaunchPlan
    expected_log_markers: tuple[ExpectedLogMarker, ...]
    lock_path: Path
    generated_run_script: GeneratedRunScriptContract | None
    future_validations: tuple[str, ...]


def _require_sha256(value: str, label: str) -> str:
    if not isinstance(value, str) or not SHA256.fullmatch(value):
        raise InvocationError(f"{label} must be a lower-case SHA-256")
    return value


def _require_loader(cell: Mapping[str, Any]) -> str:
    loader = cell.get("loader")
    if loader not in {"fabric", "neoforge"}:
        raise InvocationError(f"unsupported loader {loader!r}")
    return str(loader)


def _require_mapping(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise InvocationError(f"{label} must be an object")
    return value


def _pinned_download(value: Mapping[str, Any], destination: Path, label: str) -> RuntimeDownload:
    name, url, checksum = value.get("name"), value.get("url"), _require_mapping(value.get("checksum"), f"{label}.checksum")
    algorithm, digest = checksum.get("algorithm"), checksum.get("value")
    if not isinstance(name, str) or not name or not isinstance(url, str) or not url.startswith("https://"):
        raise InvocationError(f"{label} must have a pinned HTTPS name and URL")
    if algorithm not in {"sha1", "sha256"} or not isinstance(digest, str) or not digest:
        raise InvocationError(f"{label} must have a SHA-1 or SHA-256 checksum")
    return RuntimeDownload(name, url, algorithm, digest, destination)


def _dependency(cell: Mapping[str, Any], coordinate: str) -> Mapping[str, Any]:
    dependencies = cell.get("dependencies")
    if not isinstance(dependencies, Sequence) or isinstance(dependencies, (str, bytes)):
        raise InvocationError("cell has no dependency list")
    matches = [item for item in dependencies if isinstance(item, Mapping) and item.get("coordinate") == coordinate]
    if len(matches) != 1:
        raise InvocationError(f"cell must pin exactly one {coordinate} dependency")
    return matches[0]


def _fabric_loader_version(cell: Mapping[str, Any]) -> str:
    value = _dependency(cell, "net.fabricmc:fabric-loader").get("version")
    if not isinstance(value, str) or not value:
        raise InvocationError("Fabric Loader version is missing")
    return value


def _candidate_origin(candidate: CandidateJar, paths: QualificationPaths, frozen_candidate_root: Path | None) -> str:
    if candidate.loader not in {"fabric", "neoforge"}:
        raise InvocationError("candidate loader must be fabric or neoforge")
    _require_sha256(candidate.sha256, "candidate SHA-256")
    source = candidate.path.resolve(strict=False)
    if source.suffix != ".jar":
        raise InvocationError("candidate path must name a jar")
    if is_within(source, paths.build_directory):
        return "cell-build"
    if frozen_candidate_root is None:
        raise InvocationError("candidate must be below this cell build directory or an explicit frozen candidate root")
    frozen_root = frozen_candidate_root.resolve(strict=False)
    if not is_within(source, frozen_root):
        raise InvocationError("candidate is outside the explicit frozen candidate root")
    return "frozen-candidate"


def _assert_disposable(layout: RuntimeLayout, paths: QualificationPaths) -> None:
    for value in (
        layout.root,
        layout.mods_directory,
        layout.config_directory,
        layout.eula_path,
        layout.server_properties_path,
        layout.ringworld_properties_path,
        layout.log_path,
        layout.fabric_server_jar,
        layout.neoforge_run_script,
        layout.neoforge_user_jvm_args,
    ):
        if value is not None and not is_within(value, paths.cell_root):
            raise InvocationError("external runtime path escapes the disposable cell root")


def runtime_layout(paths: QualificationPaths, loader: str) -> RuntimeLayout:
    if loader not in {"fabric", "neoforge"}:
        raise InvocationError("runtime layout requires a supported loader")
    root = contained_path(paths.run_directory, "external-dedicated", "external runtime root")
    layout = RuntimeLayout(
        root=root,
        mods_directory=contained_path(root, "mods", "mods directory"),
        config_directory=contained_path(root, "config", "config directory"),
        eula_path=contained_path(root, "eula.txt", "EULA path"),
        server_properties_path=contained_path(root, "server.properties", "server properties path"),
        ringworld_properties_path=contained_path(root, "config/ringworld.properties", "RingWorld config path"),
        log_path=contained_path(root, "logs/latest.log", "server log path"),
        fabric_server_jar=contained_path(root, "fabric-server-launch.jar", "Fabric launch jar") if loader == "fabric" else None,
        neoforge_run_script=contained_path(root, "run.sh", "NeoForge run script") if loader == "neoforge" else None,
        neoforge_user_jvm_args=contained_path(root, "user_jvm_args.txt", "NeoForge JVM arguments") if loader == "neoforge" else None,
    )
    _assert_disposable(layout, paths)
    return layout


def safe_small_files(layout: RuntimeLayout, port: int) -> tuple[PlannedFile, ...]:
    """Return the exact first-world configuration; no file is written here."""
    return (
        PlannedFile(layout.eula_path, "eula=true\n"),
        PlannedFile(
            layout.server_properties_path,
            "\n".join((
                "server-ip=127.0.0.1",
                f"server-port={port}",
                "online-mode=false",
                "max-players=1",
                "view-distance=6",
                "simulation-distance=4",
                "level-name=qualification-safe-small",
                f"level-seed={SAFE_SMALL_SEED}",
                "gamemode=creative",
                "difficulty=normal",
                "allow-nether=true",
                "enable-rcon=false",
                "white-list=false",
                "pause-when-empty-seconds=-1",
                "motd=RingWorld external qualification smoke",
                "",
            )),
        ),
        PlannedFile(
            layout.ringworld_properties_path,
            "\n".join((
                f"widthBlocks={SAFE_SMALL_WIDTH}",
                f"circumferenceBlocks={SAFE_SMALL_CIRCUMFERENCE}",
                f"wallHeightBlocks={SAFE_SMALL_WALL_HEIGHT}",
                "testMode=false",
                "testViewDistanceChunks=6",
                "pregenerateTerrainAtlas=false",
                "requestOceanMonument=false",
                "",
            )),
        ),
    )


def _timeout_seconds(cell: Mapping[str, Any]) -> int:
    profile = _require_mapping(cell.get("profile"), "cell profile")
    value = profile.get("timeout_seconds")
    if not isinstance(value, int) or isinstance(value, bool) or value < 1:
        raise InvocationError("cell profile needs a positive timeout_seconds")
    return value


def external_runtime_smoke_plan(
    cell: Mapping[str, Any],
    candidate: CandidateJar,
    paths: QualificationPaths,
    *,
    frozen_candidate_root: Path | None = None,
) -> ExternalRuntimeSmokePlan:
    """Plan an isolated dedicated smoke without touching its inputs or output tree.

    A frozen jar is intentionally reusable across every version cell for its
    loader.  This model only proves its source location and declared hash; jar
    metadata game-version ranges remain an explicit later validation.
    """
    loader = _require_loader(cell)
    cell_id = cell.get("id")
    if not isinstance(cell_id, str) or cell_id != paths.cell_id:
        raise InvocationError("qualification paths must belong to the selected cell")
    if candidate.loader != loader:
        raise InvocationError("candidate loader does not match the qualification cell")
    origin = _candidate_origin(candidate, paths, frozen_candidate_root)
    version = required_minecraft_version(cell)
    port = qualification_port(cell)
    timeout_seconds = _timeout_seconds(cell)
    layout = runtime_layout(paths, loader)
    installer_value = _require_mapping(cell.get("runtime_install"), "runtime installer")
    installer_download = _pinned_download(
        installer_value,
        contained_path(paths.cache_directory, "external-runtime/installer.jar", "installer download"),
        "runtime installer",
    )
    downloads: list[RuntimeDownload] = [installer_download]
    mods = [
        ModInventoryEntry(
            "RingWorld",
            candidate.path.resolve(strict=False),
            candidate.sha256,
            contained_path(layout.mods_directory, "ringworld-candidate.jar", "RingWorld mod destination"),
        )
    ]
    generated_run_script: GeneratedRunScriptContract | None = None
    if loader == "fabric":
        api = _dependency(cell, "net.fabricmc.fabric-api:fabric-api")
        api_version = api.get("version")
        if not isinstance(api_version, str) or not api_version:
            raise InvocationError("Fabric API version is missing")
        api_download = _pinned_download(
            api,
            contained_path(paths.cache_directory, f"external-runtime/fabric-api-{api_version}.jar", "Fabric API download"),
            "Fabric API",
        )
        downloads.append(api_download)
        mods.append(ModInventoryEntry("Fabric API", api_download.destination, api_download.checksum,
                                      contained_path(layout.mods_directory, f"fabric-api-{api_version}.jar", "Fabric API mod destination")))
        installer = InstallerInvocation(
            "fabric",
            ("java", "-jar", str(installer_download.destination), "server", "-mcversion", version,
             "-loader", _fabric_loader_version(cell), "-downloadMinecraft", "-dir", str(layout.root)),
            paths.cell_root,
        )
        assert layout.fabric_server_jar is not None
        launch = LaunchPlan(("java", "-jar", str(layout.fabric_server_jar), "nogui"), layout.root,
                            timeout_seconds)
    else:
        installer = InstallerInvocation(
            "neoforge",
            ("java", "-jar", str(installer_download.destination), "--installServer", str(layout.root)),
            paths.cell_root,
        )
        assert layout.neoforge_run_script is not None and layout.neoforge_user_jvm_args is not None
        generated_run_script = GeneratedRunScriptContract(
            layout.neoforge_run_script,
            ("./run.sh", "nogui"),
            layout.neoforge_user_jvm_args,
        )
        launch = LaunchPlan(generated_run_script.launch_argv, layout.root, timeout_seconds)
    markers = (
        ExpectedLogMarker("ringworld-bootstrap", "RingWorld bootstrap settings: width=416, circumference=2048, wallHeight=160"),
        ExpectedLogMarker("atlas-disabled", "pregenerateTerrainAtlas=false"),
        ExpectedLogMarker("server-ready", "Done ("),
    )
    _assert_disposable(layout, paths)
    return ExternalRuntimeSmokePlan(
        cell_id, version, loader, candidate, origin, tuple(downloads), installer, layout,
        safe_small_files(layout, port), tuple(mods), launch, markers, paths.lock_path, generated_run_script,
        (
            "Verify the candidate jar SHA-256 after it is copied into mods.",
            "Inspect the candidate's declared loader and Minecraft metadata range before treating a smoke as PASS.",
            "A same-loader frozen candidate is intentionally reusable across 26.1, 26.1.1, and 26.1.2; metadata-range validation is separate.",
        ),
    )


# Concise alias for future orchestration code.
plan_external_runtime_smoke = external_runtime_smoke_plan
