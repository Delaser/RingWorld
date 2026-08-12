#!/usr/bin/env python3
"""Pure, fail-closed models for the Minecraft quick-qualification matrix.

This module deliberately does not start Gradle, Minecraft, downloads, or
subprocesses.  It is the stable planning/evidence seam for a later isolated
execution adapter.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from enum import Enum
import hashlib
import json
from pathlib import Path, PurePath
import re
import socket
from typing import Any, Callable, Iterable, Mapping, Protocol, Sequence


REPORT_FORMAT = 1
QUALIFICATION_EXECUTION_NOT_IMPLEMENTED = "QUALIFICATION_EXECUTION_NOT_IMPLEMENTED"
DRY_RUN_NO_EXECUTION = "DRY_RUN_NO_EXECUTION"
SAFE_IDENTIFIER = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$")

# These are the only manifest dependency coordinates that planning may turn
# into Gradle properties.  Keep this map deliberately small: a future
# execution adapter must not silently fall back to the repository's 26.1.2
# defaults for any matrix cell.
DEPENDENCY_PROPERTIES: Mapping[str, tuple[tuple[str, str], ...]] = {
    "fabric": (
        ("net.fabricmc:fabric-loader", "loader_version"),
        ("net.fabricmc:fabric-loom", "loom_version"),
        ("net.fabricmc.fabric-api:fabric-api", "fabric_api_version"),
    ),
    "neoforge": (
        ("net.neoforged:neoforge", "neoforge_version"),
        ("net.neoforged:moddev-gradle", "moddevgradle_version"),
    ),
}


class Verdict(str, Enum):
    PASS = "PASS"
    FAIL = "FAIL"
    INCOMPLETE = "INCOMPLETE"


class PhaseName(str, Enum):
    MANIFEST_VALIDATION = "MANIFEST_VALIDATION"
    INPUT_PLAN = "INPUT_PLAN"
    BUILD_AND_UNIT = "BUILD_AND_UNIT"
    ARTIFACT_VERIFY = "ARTIFACT_VERIFY"
    SHARED_CONTRACT = "SHARED_CONTRACT"
    DEDICATED_SMOKE = "DEDICATED_SMOKE"


PHASES: tuple[PhaseName, ...] = tuple(PhaseName)


@dataclass(frozen=True)
class EvidenceReference:
    """A reviewed fact that justifies a phase verdict.

    A reference may point to an immutable file created by the executor or to a
    deterministic input selected during write-free planning.  It is kept
    deliberately small so that future runtime adapters cannot smuggle an
    unstructured success through the report model.
    """

    kind: str
    location: str
    detail: str


def _require_phase_evidence(
    verdict: "Verdict", evidence: Sequence[EvidenceReference],
) -> tuple[EvidenceReference, ...]:
    result = tuple(evidence)
    if verdict is Verdict.PASS and not result:
        raise InvocationError("a PASS qualification phase must carry evidence")
    return result


class InvocationError(ValueError):
    """A command invocation is unsafe, invalid, or cannot select any cells."""


def require_safe_identifier(value: str, label: str) -> str:
    if not isinstance(value, str) or not SAFE_IDENTIFIER.fullmatch(value) or value in {".", ".."}:
        raise InvocationError(f"{label} must be a safe identifier")
    return value


def is_within(child: Path, parent: Path) -> bool:
    """Return whether *child* remains inside *parent*, without creating either."""
    try:
        child.resolve(strict=False).relative_to(parent.resolve(strict=False))
    except ValueError:
        return False
    return True


def contained_path(root: Path, relative: str | PurePath, label: str) -> Path:
    candidate = PurePath(relative)
    if candidate.is_absolute() or ".." in candidate.parts or str(candidate) in {"", "."}:
        raise InvocationError(f"{label} must be a non-traversing relative path")
    result = root / candidate
    if not is_within(result, root):
        raise InvocationError(f"{label} resolves outside its permitted root")
    return result


@dataclass(frozen=True)
class QualificationPaths:
    """One disposable, per-cell directory tree below reviewed evidence roots."""

    repository_root: Path
    run_id: str
    cell_id: str
    run_root: Path
    cell_root: Path
    gradle_home: Path
    run_directory: Path
    cache_directory: Path
    build_directory: Path
    evidence_directory: Path
    logs_directory: Path
    world_directory: Path
    lock_path: Path

    @classmethod
    def from_cell(cls, repository_root: Path, cell: Mapping[str, Any], run_id: str) -> "QualificationPaths":
        run_id = require_safe_identifier(run_id, "run id")
        cell_id = require_safe_identifier(str(cell.get("id", "")), "cell id")
        profile = cell.get("profile")
        if not isinstance(profile, Mapping):
            raise InvocationError(f"cell {cell_id} has no valid profile")
        evidence_relative = profile.get("evidence_directory")
        if not isinstance(evidence_relative, str):
            raise InvocationError(f"cell {cell_id} has no evidence directory")
        root = repository_root.resolve(strict=False)
        evidence_base = contained_path(root, evidence_relative, "profile evidence directory")
        required_base = root / "dist" / "qualification"
        if not is_within(evidence_base, required_base):
            raise InvocationError("profile evidence directory must be below dist/qualification")
        run_root = contained_path(evidence_base, run_id, "run id")
        cell_root = contained_path(run_root, cell_id, "cell id")
        return cls(
            repository_root=root,
            run_id=run_id,
            cell_id=cell_id,
            run_root=run_root,
            cell_root=cell_root,
            gradle_home=contained_path(cell_root, "gradle-home", "Gradle home"),
            run_directory=contained_path(cell_root, "run", "run directory"),
            cache_directory=contained_path(cell_root, "cache", "cache directory"),
            build_directory=contained_path(cell_root, "build", "build directory"),
            evidence_directory=contained_path(cell_root, "evidence", "evidence directory"),
            logs_directory=contained_path(cell_root, "logs", "logs directory"),
            world_directory=contained_path(cell_root, "world", "world directory"),
            lock_path=contained_path(
                contained_path(required_base, ".locks", "lock directory"),
                f"{cell_id}.lock",
                "lock path",
            ),
        )


@dataclass(frozen=True)
class CommandRecord:
    phase: PhaseName
    argv: tuple[str, ...]
    cwd: Path
    environment: tuple[tuple[str, str], ...]
    timeout_seconds: int


@dataclass(frozen=True)
class PhaseResult:
    phase: PhaseName
    verdict: Verdict
    reason: str | None = None
    commands: tuple[CommandRecord, ...] = ()
    evidence: tuple[EvidenceReference, ...] = ()
    artifacts: tuple["ArtifactEvidence", ...] = ()

    def __post_init__(self) -> None:
        _require_phase_evidence(self.verdict, self.evidence)


@dataclass(frozen=True)
class ArtifactEvidence:
    path: str
    algorithm: str
    expected: str
    actual: str | None
    verified: bool


@dataclass(frozen=True)
class CellReport:
    cell_id: str
    minecraft_version: str
    loader: str
    run_id: str
    verdict: Verdict
    phases: tuple[PhaseResult, ...]
    paths: QualificationPaths
    downloads: tuple["DownloadPlan", ...] = ()
    artifacts: tuple[ArtifactEvidence, ...] = ()


@dataclass(frozen=True)
class MatrixReport:
    format: int
    tier: str
    dry_run: bool
    run_id: str
    verdict: Verdict
    cells: tuple[CellReport, ...]


@dataclass(frozen=True)
class DownloadPlan:
    name: str
    url: str
    algorithm: str
    checksum: str
    destination: Path


@dataclass(frozen=True)
class LockSnapshot:
    pid: int
    hostname: str
    run_id: str


class LockAction(str, Enum):
    ACQUIRE = "ACQUIRE"
    RECLAIM = "RECLAIM"
    BLOCK = "BLOCK"


@dataclass(frozen=True)
class LockDecision:
    action: LockAction
    reason: str


def parse_lock_snapshot(value: Mapping[str, Any]) -> LockSnapshot:
    pid = value.get("pid")
    hostname = value.get("hostname")
    run_id = value.get("run_id")
    if not isinstance(pid, int) or isinstance(pid, bool) or pid < 1:
        raise InvocationError("lock pid must be a positive integer")
    if not isinstance(hostname, str) or not hostname:
        raise InvocationError("lock hostname must be a non-empty string")
    return LockSnapshot(pid=pid, hostname=hostname, run_id=require_safe_identifier(str(run_id), "lock run id"))


def decide_lock(
    existing: LockSnapshot | None,
    hostname: str,
    pid_is_alive: Callable[[int], bool],
) -> LockDecision:
    """Only reclaim a dead lock from this host; foreign locks never get guessed stale."""
    if existing is None:
        return LockDecision(LockAction.ACQUIRE, "NO_EXISTING_LOCK")
    if existing.hostname != hostname:
        return LockDecision(LockAction.BLOCK, "LOCK_HELD_BY_OTHER_HOST")
    if pid_is_alive(existing.pid):
        return LockDecision(LockAction.BLOCK, "LOCK_PID_IS_ALIVE")
    return LockDecision(LockAction.RECLAIM, "SAME_HOST_DEAD_PID")


class PortProbe(Protocol):
    def __call__(self, port: int) -> bool: ...


def socket_port_probe(port: int) -> bool:
    """Best-effort local availability probe; orchestration owns any eventual bind."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            probe.bind(("127.0.0.1", port))
        except OSError:
            return False
    return True


def port_available(port: int, probe: PortProbe) -> bool:
    if not isinstance(port, int) or isinstance(port, bool) or not 1 <= port <= 65535:
        raise InvocationError("port must be an integer from 1 through 65535")
    return bool(probe(port))


def checksum_evidence(path: Path, algorithm: str, expected: str) -> ArtifactEvidence:
    if algorithm not in hashlib.algorithms_available:
        raise InvocationError(f"unsupported checksum algorithm {algorithm!r}")
    digest = hashlib.new(algorithm)
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    actual = digest.hexdigest()
    return ArtifactEvidence(str(path), algorithm, expected, actual, actual == expected)


def download_plans(cell: Mapping[str, Any], paths: QualificationPaths) -> tuple[DownloadPlan, ...]:
    """Plan pinned downloads only.  Fetching belongs to a later execution adapter."""
    minecraft = cell.get("minecraft")
    dependencies = cell.get("dependencies")
    if not isinstance(minecraft, Mapping) or not isinstance(dependencies, Sequence):
        raise InvocationError(f"cell {cell.get('id', '<unknown>')} has invalid pinned inputs")
    candidates: list[Mapping[str, Any]] = []
    downloads = minecraft.get("downloads")
    if isinstance(downloads, Sequence):
        candidates.extend(item for item in downloads if isinstance(item, Mapping))
    candidates.extend(item for item in dependencies if isinstance(item, Mapping))
    runtime_install = cell.get("runtime_install")
    if not isinstance(runtime_install, Mapping):
        raise InvocationError(f"cell {cell.get('id', '<unknown>')} has no runtime installer")
    candidates.append(runtime_install)
    plans: list[DownloadPlan] = []
    for index, item in enumerate(candidates):
        checksum = item.get("checksum")
        name, url = item.get("name"), item.get("url")
        if not isinstance(checksum, Mapping) or not isinstance(name, str) or not isinstance(url, str):
            raise InvocationError(f"cell {cell.get('id', '<unknown>')} has invalid download {index}")
        algorithm, value = checksum.get("algorithm"), checksum.get("value")
        if not isinstance(algorithm, str) or not isinstance(value, str):
            raise InvocationError(f"cell {cell.get('id', '<unknown>')} has invalid checksum {index}")
        destination = contained_path(paths.cache_directory, f"downloads/{index:02d}-{require_safe_identifier(name.replace(' ', '-'), 'download name')}", "download destination")
        plans.append(DownloadPlan(name, url, algorithm, value, destination))
    return tuple(plans)


def select_cells(manifest: Mapping[str, Any], cell_ids: Iterable[str] = (), *, all_cells: bool = False, all_supported: bool = False) -> tuple[Mapping[str, Any], ...]:
    requested = tuple(cell_ids)
    if sum(bool(value) for value in (requested, all_cells, all_supported)) != 1:
        raise InvocationError("select exactly one of --cell, --all, or --all-supported")
    cells = manifest.get("cells")
    if not isinstance(cells, Sequence):
        raise InvocationError("manifest has no cells")
    indexed = {cell.get("id"): cell for cell in cells if isinstance(cell, Mapping) and isinstance(cell.get("id"), str)}
    if requested:
        if len(set(requested)) != len(requested):
            raise InvocationError("--cell may not select the same cell more than once")
        unknown = [cell_id for cell_id in requested if cell_id not in indexed]
        if unknown:
            raise InvocationError(f"unknown qualification cell(s): {', '.join(unknown)}")
        selected = [indexed[cell_id] for cell_id in requested]
    elif all_supported:
        selected = [cell for cell in cells if isinstance(cell, Mapping) and cell.get("status") in {"passing", "published"}]
    else:
        selected = [cell for cell in cells if isinstance(cell, Mapping)]
    if not selected:
        raise InvocationError("selection contains no qualification cells")
    return tuple(sorted(selected, key=lambda cell: str(cell["id"])))


def required_dependency_properties(cell: Mapping[str, Any]) -> tuple[tuple[str, str], ...]:
    """Return the exact loader input pins, rejecting omissions and duplicates.

    The manifest is authoritative.  This helper intentionally has no project
    property defaults: planning a 26.1 or 26.1.1 cell must never accidentally
    inherit the repository's normal 26.1.2 inputs.
    """
    loader = cell.get("loader")
    if not isinstance(loader, str) or loader not in DEPENDENCY_PROPERTIES:
        raise InvocationError(f"unsupported loader {loader!r}")
    dependencies = cell.get("dependencies")
    if not isinstance(dependencies, Sequence) or isinstance(dependencies, (str, bytes)):
        raise InvocationError(f"cell {cell.get('id', '<unknown>')} has no dependency list")

    expected = DEPENDENCY_PROPERTIES[loader]
    required_coordinates = {coordinate for coordinate, _ in expected}
    found: dict[str, str] = {}
    for dependency in dependencies:
        if not isinstance(dependency, Mapping):
            raise InvocationError(f"cell {cell.get('id', '<unknown>')} has an invalid dependency")
        coordinate = dependency.get("coordinate")
        if not isinstance(coordinate, str):
            raise InvocationError(f"cell {cell.get('id', '<unknown>')} has a dependency without a coordinate")
        if coordinate not in required_coordinates:
            continue
        if coordinate in found:
            raise InvocationError(
                f"cell {cell.get('id', '<unknown>')} duplicates required dependency {coordinate}"
            )
        version = dependency.get("version")
        if not isinstance(version, str) or not version.strip():
            raise InvocationError(
                f"cell {cell.get('id', '<unknown>')} has no version for required dependency {coordinate}"
            )
        found[coordinate] = version.strip()

    missing = [coordinate for coordinate, _ in expected if coordinate not in found]
    if missing:
        raise InvocationError(
            f"cell {cell.get('id', '<unknown>')} is missing required dependency "
            + ", ".join(missing)
        )
    return tuple((property_name, found[coordinate]) for coordinate, property_name in expected)


def required_minecraft_version(cell: Mapping[str, Any]) -> str:
    minecraft = cell.get("minecraft")
    if not isinstance(minecraft, Mapping):
        raise InvocationError(f"cell {cell.get('id', '<unknown>')} has no Minecraft input")
    version = minecraft.get("version")
    if not isinstance(version, str) or not version.strip():
        raise InvocationError(f"cell {cell.get('id', '<unknown>')} has no Minecraft version")
    return version.strip()


def qualification_port(cell: Mapping[str, Any]) -> int:
    profile = cell.get("profile")
    if not isinstance(profile, Mapping):
        raise InvocationError(f"cell {cell.get('id', '<unknown>')} has no profile")
    port = profile.get("server_port")
    if not isinstance(port, int) or isinstance(port, bool) or not 1 <= port <= 65535:
        raise InvocationError(f"cell {cell.get('id', '<unknown>')} has an invalid server port")
    return port


def gradle_properties(cell: Mapping[str, Any], paths: QualificationPaths) -> tuple[tuple[str, str], ...]:
    """Plan only Gradle's reviewed cell-selection and pinned build inputs.

    Run/cache/evidence/world paths are deliberately *not* Gradle properties.
    ``QualificationPaths`` retains them for the later evidence/execution
    adapter, which must establish its own reviewed contract before use.
    """
    minecraft_version = required_minecraft_version(cell)
    return (
        ("ringQualificationRoot", str(paths.run_root)),
        ("ringQualificationCell", paths.cell_id),
        ("ringQualificationPort", str(qualification_port(cell))),
        ("minecraft_version", minecraft_version),
        ("mod_version", f"0.0.0-qualification+mc{minecraft_version}"),
        ("release_label", f"qualification-{paths.cell_id}"),
        *required_dependency_properties(cell),
    )


def planned_commands(
    cell: Mapping[str, Any], paths: QualificationPaths, *, gradle_dependency_cache: Path | None = None,
) -> tuple[CommandRecord, ...]:
    loader = cell["loader"]
    profile = cell.get("profile")
    if not isinstance(profile, Mapping):
        raise InvocationError(f"cell {cell.get('id', '<unknown>')} has no profile")
    timeout = profile.get("timeout_seconds")
    if not isinstance(timeout, int) or isinstance(timeout, bool) or timeout < 1:
        raise InvocationError(f"cell {cell.get('id', '<unknown>')} has an invalid timeout")
    properties = gradle_properties(cell, paths)
    property_args = tuple(f"-P{name}={value}" for name, value in properties)
    # Gradle is the one tool that may retain downloaded/build state across
    # invocations, so each cell receives an explicit disposable home. Other
    # runtime/cache paths remain owned by later phase adapters.
    environment = (("GRADLE_USER_HOME", str(paths.gradle_home)),)
    if gradle_dependency_cache is not None:
        # This is an optional worker-provisioned *read-only dependency* cache.
        # It never replaces the disposable per-cell Gradle user home and must
        # be validated by the execution runner before it reaches this pure
        # command-planning seam.
        environment += (("GRADLE_RO_DEP_CACHE", str(gradle_dependency_cache)),)
    if loader == "fabric":
        build_tasks = (":test", ":build")
    elif loader == "neoforge":
        build_tasks = (":neoforge:test", ":neoforge:build")
    else:
        raise InvocationError(f"unsupported loader {loader!r}")
    executable = str(paths.repository_root / "gradlew")
    return (
        CommandRecord(
            PhaseName.BUILD_AND_UNIT,
            (executable, "--console=plain", "--no-daemon", "--max-workers=1", *property_args, *build_tasks),
            paths.repository_root,
            environment,
            timeout,
        ),
    )


def aggregate_verdict(phases: Sequence[PhaseResult]) -> Verdict:
    verdicts = {phase.verdict for phase in phases}
    if Verdict.FAIL in verdicts:
        return Verdict.FAIL
    if Verdict.INCOMPLETE in verdicts:
        return Verdict.INCOMPLETE
    return Verdict.PASS


def plan_cell(
    cell: Mapping[str, Any], repository_root: Path, run_id: str, *, dry_run: bool,
    gradle_dependency_cache: Path | None = None,
    gradle_distribution_zip: Path | None = None,
) -> CellReport:
    paths = QualificationPaths.from_cell(repository_root, cell, run_id)
    commands = planned_commands(cell, paths, gradle_dependency_cache=gradle_dependency_cache)
    command_by_phase = {command.phase: command for command in commands}
    reason = DRY_RUN_NO_EXECUTION if dry_run else QUALIFICATION_EXECUTION_NOT_IMPLEMENTED
    phases: list[PhaseResult] = []
    for phase in PHASES:
        if phase is PhaseName.MANIFEST_VALIDATION:
            phases.append(PhaseResult(
                phase,
                Verdict.PASS,
                evidence=(EvidenceReference("manifest-cell", str(cell["id"]), "manifest validated before planning"),),
            ))
        elif phase is PhaseName.INPUT_PLAN:
            evidence = [
                EvidenceReference("input-plan", str(paths.cell_root), "pinned inputs and isolated paths selected"),
            ]
            if gradle_dependency_cache is not None:
                evidence.append(EvidenceReference(
                    "gradle-ro-dependency-cache",
                    str(gradle_dependency_cache),
                    "optional worker-provisioned read-only dependency cache; non-authoritative acceleration only",
                ))
            if gradle_distribution_zip is not None:
                evidence.append(EvidenceReference(
                    "gradle-wrapper-distribution-zip",
                    str(gradle_distribution_zip),
                    "optional external wrapper ZIP seed; revalidated against gradle-wrapper.properties before every Gradle launch",
                ))
            phases.append(PhaseResult(
                phase,
                Verdict.PASS,
                evidence=tuple(evidence),
            ))
        else:
            command = command_by_phase.get(phase)
            phases.append(PhaseResult(phase, Verdict.INCOMPLETE, reason, (command,) if command else ()))
    phase_tuple = tuple(phases)
    return CellReport(
        cell_id=str(cell["id"]), minecraft_version=str(cell["minecraft"]["version"]), loader=str(cell["loader"]),
        run_id=paths.run_id, verdict=aggregate_verdict(phase_tuple), phases=phase_tuple, paths=paths,
        downloads=download_plans(cell, paths),
    )


def plan_matrix(
    cells: Sequence[Mapping[str, Any]], repository_root: Path, run_id: str, *, dry_run: bool,
    gradle_dependency_cache: Path | None = None,
    gradle_distribution_zip: Path | None = None,
) -> MatrixReport:
    reports = tuple(
        plan_cell(
            cell, repository_root, run_id, dry_run=dry_run,
            gradle_dependency_cache=gradle_dependency_cache,
            gradle_distribution_zip=gradle_distribution_zip,
        )
        for cell in cells
    )
    verdict = aggregate_verdict(tuple(
        PhaseResult(
            PhaseName.MANIFEST_VALIDATION,
            report.verdict,
            evidence=(EvidenceReference("cell-plan", report.cell_id, report.verdict.value),),
        )
        for report in reports
    ))
    return MatrixReport(REPORT_FORMAT, "quick", dry_run, run_id, verdict, reports)


def _json_value(value: Any) -> Any:
    if isinstance(value, Enum):
        return value.value
    if isinstance(value, Path):
        return str(value)
    if isinstance(value, tuple):
        return [_json_value(item) for item in value]
    if isinstance(value, list):
        return [_json_value(item) for item in value]
    if isinstance(value, dict):
        return {key: _json_value(item) for key, item in value.items()}
    return value


def report_dict(report: MatrixReport) -> dict[str, Any]:
    return _json_value(asdict(report))


def cell_report_dict(report: CellReport) -> dict[str, Any]:
    """Convert one cell report to JSON-safe immutable-evidence data."""
    return _json_value(asdict(report))


def render_json(report: MatrixReport) -> str:
    return json.dumps(report_dict(report), indent=2, sort_keys=True) + "\n"


def render_markdown(report: MatrixReport) -> str:
    rows = [
        "# Minecraft quick qualification",
        "",
        f"Run: `{report.run_id}`  ",
        f"Mode: `{'dry-run' if report.dry_run else 'execution'}`  ",
        f"Verdict: **{report.verdict.value}**",
        "",
        "| Cell | Minecraft | Loader | Verdict |",
        "| --- | --- | --- | --- |",
    ]
    rows.extend(f"| {cell.cell_id} | {cell.minecraft_version} | {cell.loader} | {cell.verdict.value} |" for cell in report.cells)
    for cell in report.cells:
        incomplete = [phase.reason for phase in cell.phases if phase.reason]
        if incomplete:
            rows.extend(("", f"- `{cell.cell_id}`: {', '.join(sorted(set(incomplete)))}"))
    return "\n".join(rows) + "\n"
