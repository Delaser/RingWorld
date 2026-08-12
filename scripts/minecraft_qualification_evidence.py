#!/usr/bin/env python3
"""Fail-closed, pure terminal-evidence validation for qualification cells.

The execution adapters may *produce* this JSON-shaped data, but this module
never reads files, invokes processes, or writes reports.  Its only job is to
make a terminal ``PASS`` mean one very specific, reviewable body of evidence.
``FAIL`` and ``INCOMPLETE`` records deliberately have a smaller contract: they
must identify the cell and explain why later evidence is unavailable.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
import re
from typing import Any, Mapping, Sequence
from urllib.parse import urlparse


EVIDENCE_SCHEMA_VERSION = 1
_SHA256 = re.compile(r"^[0-9a-f]{64}$")
_COMMIT = re.compile(r"^[0-9a-f]{40}$")
_SAFE_RELATIVE_PATH = re.compile(r"^(?!/)(?!.*(?:^|/)\.\.(?:/|$))[A-Za-z0-9][A-Za-z0-9._/-]*$")
_UTC_TIMESTAMP = re.compile(r"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,6})?Z$")
_REQUIRED_MARKERS = ("ringworld-bootstrap", "atlas-disabled", "server-ready", "server-stop", "world-save")
_TOP_LEVEL = frozenset({
    "schema_version", "verdict", "cell", "reason", "provenance", "commands",
    "installer", "runtime_inventory", "frozen_candidate", "markers", "runtime", "same_file",
})
_PASS_FIELDS = frozenset({
    "schema_version", "verdict", "cell", "provenance", "commands", "installer",
    "runtime_inventory", "frozen_candidate", "markers", "runtime", "same_file",
})
_NONPASS_FIELDS = frozenset({"schema_version", "verdict", "cell", "reason"})
_CELL_FIELDS = frozenset({"id", "minecraft_version", "loader", "port", "world_config"})


class TerminalEvidenceError(ValueError):
    """A terminal evidence record is missing, malformed, or contradictory."""


@dataclass(frozen=True)
class TerminalEvidence:
    """Minimal validated result returned to the matrix orchestrator."""

    cell_id: str
    verdict: str
    candidate_sha256: str | None


def _mapping(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise TerminalEvidenceError(f"{label} must be an object")
    return value


def _exact_keys(value: Mapping[str, Any], keys: frozenset[str], label: str) -> None:
    if set(value) != keys:
        missing = sorted(keys - set(value))
        unknown = sorted(set(value) - keys)
        detail = []
        if missing:
            detail.append("missing " + ", ".join(missing))
        if unknown:
            detail.append("unknown " + ", ".join(unknown))
        raise TerminalEvidenceError(f"{label} has invalid fields ({'; '.join(detail)})")


def _string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value:
        raise TerminalEvidenceError(f"{label} must be a non-empty string")
    return value


def _sha256(value: Any, label: str) -> str:
    result = _string(value, label)
    if _SHA256.fullmatch(result) is None:
        raise TerminalEvidenceError(f"{label} must be a lowercase SHA-256")
    return result


def _relative_path(value: Any, label: str) -> str:
    result = _string(value, label)
    if _SAFE_RELATIVE_PATH.fullmatch(result) is None:
        raise TerminalEvidenceError(f"{label} must be a safe relative path")
    return result


def _timestamp(value: Any, label: str) -> datetime:
    result = _string(value, label)
    if _UTC_TIMESTAMP.fullmatch(result) is None:
        raise TerminalEvidenceError(f"{label} must be an RFC3339 UTC timestamp")
    try:
        return datetime.fromisoformat(result[:-1] + "+00:00")
    except ValueError as error:
        raise TerminalEvidenceError(f"{label} is not a real timestamp") from error


def _public_origin(value: Any) -> str:
    origin = _string(value, "provenance.public_origin")
    parsed = urlparse(origin)
    if parsed.scheme != "https" or parsed.netloc != "github.com" or parsed.username or parsed.password \
            or parsed.query or parsed.fragment:
        raise TerminalEvidenceError("provenance.public_origin must be a credential-free public GitHub HTTPS URL")
    if parsed.path.rstrip("/") not in {"/Delaser/RingWorld", "/Delaser/RingWorld.git"}:
        raise TerminalEvidenceError("provenance.public_origin must identify Delaser/RingWorld")
    return origin


def _canonical_cell(record: Mapping[str, Any], canonical_cells: Mapping[str, Mapping[str, Any]]) -> Mapping[str, Any]:
    _exact_keys(record, _CELL_FIELDS, "cell")
    cell_id = _string(record.get("id"), "cell.id")
    expected = canonical_cells.get(cell_id)
    if not isinstance(expected, Mapping):
        raise TerminalEvidenceError("cell.id is not a canonical qualification cell")
    # A canonical cell is deliberately provided by the reviewed matrix, not
    # inferred from evidence.  Requiring exact values prevents a port or a
    # world profile from being silently swapped under a valid-looking ID.
    for field in ("id", "minecraft_version", "loader", "port", "world_config"):
        if field not in expected or record[field] != expected[field]:
            raise TerminalEvidenceError(f"cell.{field} does not match its canonical manifest cell")
    if record["loader"] not in {"fabric", "neoforge"}:
        raise TerminalEvidenceError("cell.loader is unsupported")
    if not isinstance(record["port"], int) or isinstance(record["port"], bool) or not 1 <= record["port"] <= 65535:
        raise TerminalEvidenceError("cell.port must be a TCP port")
    world = _mapping(record["world_config"], "cell.world_config")
    for required in ("seed", "circumference_blocks", "width_blocks", "wall_height_blocks", "pregenerate_terrain_atlas"):
        if required not in world:
            raise TerminalEvidenceError(f"cell.world_config is missing {required}")
    return expected


def _validate_provenance(value: Any) -> None:
    data = _mapping(value, "provenance")
    _exact_keys(data, frozenset({"commit", "clean", "public_origin", "manifest_sha256", "wrapper_sha256", "java", "platform"}), "provenance")
    commit = _string(data["commit"], "provenance.commit")
    if _COMMIT.fullmatch(commit) is None:
        raise TerminalEvidenceError("provenance.commit must be a full lowercase Git commit")
    if data["clean"] is not True:
        raise TerminalEvidenceError("provenance.clean must be true for PASS")
    _public_origin(data["public_origin"])
    _sha256(data["manifest_sha256"], "provenance.manifest_sha256")
    _sha256(data["wrapper_sha256"], "provenance.wrapper_sha256")
    java = _mapping(data["java"], "provenance.java")
    _exact_keys(java, frozenset({"major", "version"}), "provenance.java")
    if java["major"] != 25 or not _string(java["version"], "provenance.java.version").startswith("25"):
        raise TerminalEvidenceError("provenance.java must identify Java 25")
    platform = _mapping(data["platform"], "provenance.platform")
    _exact_keys(platform, frozenset({"system", "machine"}), "provenance.platform")
    _string(platform["system"], "provenance.platform.system")
    _string(platform["machine"], "provenance.platform.machine")


def _validate_commands(value: Any) -> None:
    if not isinstance(value, Sequence) or isinstance(value, (str, bytes)) or not value:
        raise TerminalEvidenceError("commands must be a non-empty array")
    seen: set[str] = set()
    for index, raw in enumerate(value):
        item = _mapping(raw, f"commands[{index}]")
        _exact_keys(item, frozenset({"phase", "argv", "exit_code", "started_at_utc", "ended_at_utc", "elapsed_seconds", "stdout_path", "stdout_sha256", "stderr_path", "stderr_sha256"}), f"commands[{index}]")
        phase = _string(item["phase"], f"commands[{index}].phase")
        if phase in seen:
            raise TerminalEvidenceError("commands cannot duplicate a phase")
        seen.add(phase)
        argv = item["argv"]
        if not isinstance(argv, Sequence) or isinstance(argv, (str, bytes)) or not argv or any(not isinstance(part, str) or not part for part in argv):
            raise TerminalEvidenceError(f"commands[{index}].argv must be a non-empty string array")
        if item["exit_code"] != 0 or isinstance(item["exit_code"], bool):
            raise TerminalEvidenceError(f"commands[{index}].exit_code must be zero for PASS")
        started = _timestamp(item["started_at_utc"], f"commands[{index}].started_at_utc")
        ended = _timestamp(item["ended_at_utc"], f"commands[{index}].ended_at_utc")
        if ended < started or not isinstance(item["elapsed_seconds"], (int, float)) or isinstance(item["elapsed_seconds"], bool) or item["elapsed_seconds"] < 0:
            raise TerminalEvidenceError(f"commands[{index}] has invalid timing")
        _relative_path(item["stdout_path"], f"commands[{index}].stdout_path")
        _relative_path(item["stderr_path"], f"commands[{index}].stderr_path")
        _sha256(item["stdout_sha256"], f"commands[{index}].stdout_sha256")
        _sha256(item["stderr_sha256"], f"commands[{index}].stderr_sha256")


def _validate_installer(value: Any) -> None:
    data = _mapping(value, "installer")
    _exact_keys(data, frozenset({"name", "url", "path", "sha256", "installed_sha256"}), "installer")
    _string(data["name"], "installer.name")
    url = _string(data["url"], "installer.url")
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise TerminalEvidenceError("installer.url must be a credential-free HTTPS URL")
    _relative_path(data["path"], "installer.path")
    if _sha256(data["sha256"], "installer.sha256") != _sha256(data["installed_sha256"], "installer.installed_sha256"):
        raise TerminalEvidenceError("installer installed hash differs from its pinned hash")


def _validate_inventory(value: Any) -> Mapping[str, str]:
    if not isinstance(value, Sequence) or isinstance(value, (str, bytes)) or not value:
        raise TerminalEvidenceError("runtime_inventory must be a non-empty array")
    result: dict[str, str] = {}
    roles: set[str] = set()
    for index, raw in enumerate(value):
        item = _mapping(raw, f"runtime_inventory[{index}]")
        _exact_keys(item, frozenset({"path", "sha256", "role"}), f"runtime_inventory[{index}]")
        path = _relative_path(item["path"], f"runtime_inventory[{index}].path")
        if path in result:
            raise TerminalEvidenceError("runtime_inventory cannot duplicate a path")
        role = _string(item["role"], f"runtime_inventory[{index}].role")
        if role in roles:
            raise TerminalEvidenceError("runtime_inventory cannot duplicate a role")
        result[path] = _sha256(item["sha256"], f"runtime_inventory[{index}].sha256")
        roles.add(role)
    if "ringworld" not in roles:
        raise TerminalEvidenceError("runtime_inventory must contain the RingWorld jar")
    return result


def _validate_frozen_candidate(value: Any, inventory: Mapping[str, str], expected_cell: Mapping[str, Any], ranges: Mapping[str, Mapping[str, str]]) -> str:
    data = _mapping(value, "frozen_candidate")
    _exact_keys(data, frozenset({"source_path", "source_sha256", "installed_path", "installed_sha256", "oldest_abi_minecraft_version", "minecraft_range", "loader_range"}), "frozen_candidate")
    source_path = _relative_path(data["source_path"], "frozen_candidate.source_path")
    installed_path = _relative_path(data["installed_path"], "frozen_candidate.installed_path")
    del source_path  # Source need not be part of the external runtime inventory.
    source_hash = _sha256(data["source_sha256"], "frozen_candidate.source_sha256")
    installed_hash = _sha256(data["installed_sha256"], "frozen_candidate.installed_sha256")
    if source_hash != installed_hash or inventory.get(installed_path) != installed_hash:
        raise TerminalEvidenceError("frozen candidate source, installed, and inventory hashes must be identical")
    loader = expected_cell["loader"]
    expected = ranges.get(loader)
    if not isinstance(expected, Mapping):
        raise TerminalEvidenceError("no reviewed range identity exists for this loader")
    required = {"oldest_abi_minecraft_version", "minecraft_range", "loader_range"}
    # Fabric has no independent loader-range declaration.  Its reviewed value
    # is the explicit empty string, rather than an omitted/self-invented key.
    if set(expected) != required or any(not isinstance(expected[field], str) for field in required):
        raise TerminalEvidenceError("reviewed range identity is malformed")
    for field in required:
        if not isinstance(data[field], str):
            raise TerminalEvidenceError(f"frozen_candidate.{field} must be a string")
        if data[field] != expected[field]:
            raise TerminalEvidenceError(f"frozen_candidate.{field} differs from its reviewed range identity")
    return source_hash


def _validate_markers(value: Any) -> None:
    if not isinstance(value, Sequence) or isinstance(value, (str, bytes)):
        raise TerminalEvidenceError("markers must be an array")
    names: list[str] = []
    moments: list[datetime] = []
    for index, raw in enumerate(value):
        item = _mapping(raw, f"markers[{index}]")
        _exact_keys(item, frozenset({"name", "timestamp_utc"}), f"markers[{index}]")
        names.append(_string(item["name"], f"markers[{index}].name"))
        moments.append(_timestamp(item["timestamp_utc"], f"markers[{index}].timestamp_utc"))
    if len(names) != len(set(names)):
        raise TerminalEvidenceError("markers cannot duplicate a name")
    if tuple(names) != _REQUIRED_MARKERS:
        raise TerminalEvidenceError("markers must contain the required ordered terminal sequence")
    if any(later <= earlier for earlier, later in zip(moments, moments[1:])):
        raise TerminalEvidenceError("markers must have strictly increasing timestamps")


def _validate_runtime(value: Any) -> None:
    data = _mapping(value, "runtime")
    _exact_keys(data, frozenset({"exit_code", "clean_stop", "clean_exit", "crash_detected"}), "runtime")
    if data["exit_code"] != 0 or data["clean_stop"] is not True or data["clean_exit"] is not True or data["crash_detected"] is not False:
        raise TerminalEvidenceError("runtime must have a clean, non-crashing zero exit")


def _validate_same_file(value: Any, candidate_hash: str, expected_cell: Mapping[str, Any], canonical_cells: Mapping[str, Mapping[str, Any]]) -> None:
    data = _mapping(value, "same_file")
    _exact_keys(data, frozenset({"group", "sha256", "cell_ids"}), "same_file")
    _string(data["group"], "same_file.group")
    if _sha256(data["sha256"], "same_file.sha256") != candidate_hash:
        raise TerminalEvidenceError("same_file hash differs from frozen candidate hash")
    ids = data["cell_ids"]
    if not isinstance(ids, Sequence) or isinstance(ids, (str, bytes)) or len(ids) != 3 or any(not isinstance(item, str) for item in ids):
        raise TerminalEvidenceError("same_file.cell_ids must list the three loader cells")
    if len(set(ids)) != len(ids):
        raise TerminalEvidenceError("same_file.cell_ids cannot duplicate a cell")
    loader = expected_cell["loader"]
    expected_ids = {cell_id for cell_id, cell in canonical_cells.items() if cell.get("loader") == loader}
    if set(ids) != expected_ids:
        raise TerminalEvidenceError("same_file.cell_ids must be exactly the canonical loader group")


def validate_terminal_evidence(
    record: Mapping[str, Any],
    canonical_cells: Mapping[str, Mapping[str, Any]],
    range_identities: Mapping[str, Mapping[str, str]],
) -> TerminalEvidence:
    """Validate one terminal record without touching the filesystem or network.

    ``canonical_cells`` is the reviewed selected matrix, keyed by cell ID.
    ``range_identities`` is the reviewed frozen-candidate metadata identity,
    keyed by loader.  Requiring both inputs stops a self-authored record from
    defining its own cell or compatibility scope.
    """
    data = _mapping(record, "terminal evidence")
    unknown = set(data) - _TOP_LEVEL
    if unknown:
        raise TerminalEvidenceError("terminal evidence has unknown fields: " + ", ".join(sorted(unknown)))
    if data.get("schema_version") != EVIDENCE_SCHEMA_VERSION:
        raise TerminalEvidenceError("terminal evidence has an unknown schema version")
    verdict = data.get("verdict")
    if verdict not in {"PASS", "FAIL", "INCOMPLETE"}:
        raise TerminalEvidenceError("terminal evidence verdict is invalid")
    cell = _mapping(data.get("cell"), "cell")
    expected_cell = _canonical_cell(cell, canonical_cells)
    cell_id = _string(cell["id"], "cell.id")
    if verdict != "PASS":
        _exact_keys(data, _NONPASS_FIELDS, "non-PASS terminal evidence")
        _string(data.get("reason"), "reason")
        return TerminalEvidence(cell_id, verdict, None)

    _exact_keys(data, _PASS_FIELDS, "PASS terminal evidence")
    _validate_provenance(data["provenance"])
    _validate_commands(data["commands"])
    _validate_installer(data["installer"])
    inventory = _validate_inventory(data["runtime_inventory"])
    candidate_hash = _validate_frozen_candidate(data["frozen_candidate"], inventory, expected_cell, range_identities)
    _validate_markers(data["markers"])
    _validate_runtime(data["runtime"])
    _validate_same_file(data["same_file"], candidate_hash, expected_cell, canonical_cells)
    return TerminalEvidence(cell_id, verdict, candidate_hash)
