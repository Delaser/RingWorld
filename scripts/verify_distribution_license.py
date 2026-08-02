#!/usr/bin/env python3
"""Verify RingWorld licence metadata and safe package contents for both loaders."""

from __future__ import annotations

import argparse
import io
import json
from pathlib import Path
import re
import sys
import zipfile

try:
    import tomllib
except ModuleNotFoundError:  # Python 3.9 remains supported by the release tools.
    tomllib = None


EXPECTED_IDENTIFIER = "MPL-2.0"
EMBEDDED_LICENSE = "LICENSE-RINGWORLD.txt"
OUTER_LICENSE = "LICENSE"
CLIENT_NESTED_INSTANCE = "RingWorld-Prism-Instance.zip"
FABRIC_METADATA = "fabric.mod.json"
NEOFORGE_METADATA = "META-INF/neoforge.mods.toml"
LOADERS = {"fabric", "neoforge"}
RUNTIME_PATH_PARTS = {
    ".prism-data", "accounts.json", "launcher_accounts.json", "usercache.json",
    "usernamecache.json", "options.txt",
}
RUNTIME_DIRECTORIES = {
    "saves", "screenshots", "logs", "crash-reports", "serverconfig", "resourcepacks",
}


class VerificationError(RuntimeError):
    pass


_TABLE = re.compile(r"^\[\[([A-Za-z0-9_.-]+)\]\]$")
_ASSIGNMENT = re.compile(r"^([A-Za-z0-9_.-]+)\s*=\s*(.*)$")


def _toml_string(value: str) -> str:
    if len(value) < 2 or value[0] not in {'"', "'"} or value[-1] != value[0]:
        raise ValueError("expected a quoted TOML scalar")
    if value[0] == '"':
        return json.loads(value)
    return value[1:-1]


def parse_neoforge_metadata(text: str) -> dict:
    """Parse the intentionally narrow generated `neoforge.mods.toml` contract.

    Python 3.9 has no stdlib TOML parser. The release verifier needs only the
    scalar root properties plus RingWorld's three list-table forms, so reject
    all other TOML rather than silently accepting a partial descriptor.
    """
    if tomllib is not None:
        value = tomllib.loads(text)
        if not isinstance(value, dict):
            raise ValueError("metadata must be a TOML table")
        return value
    result: dict[str, object] = {"mods": [], "mixins": [], "dependencies": {"ringworld": []}}
    current: dict | None = result
    multiline_quote: str | None = None
    for raw in text.splitlines():
        line = raw.strip()
        if multiline_quote is not None:
            if multiline_quote in line:
                multiline_quote = None
            continue
        if not line or line.startswith("#"):
            continue
        table = _TABLE.fullmatch(line)
        if table:
            name = table.group(1)
            if name == "mods":
                current = {}
                result["mods"].append(current)
            elif name == "mixins":
                current = {}
                result["mixins"].append(current)
            elif name == "dependencies.ringworld":
                current = {}
                result["dependencies"]["ringworld"].append(current)
            else:
                raise ValueError(f"unsupported list table {name}")
            continue
        assignment = _ASSIGNMENT.fullmatch(line)
        if assignment is None or current is None:
            raise ValueError(f"invalid TOML line {line!r}")
        key, value = assignment.groups()
        if value.startswith("'''") or value.startswith('\"\"\"'):
            quote = value[:3]
            if not (len(value) >= 6 and value.endswith(quote)):
                multiline_quote = quote
            current[key] = ""
            continue
        current[key] = _toml_string(value)
    if multiline_quote is not None:
        raise ValueError("unterminated TOML multiline string")
    return result


def resolve_loader(names: list[str], loader: str = "auto") -> str:
    if loader not in {"auto", *LOADERS}:
        raise VerificationError(f"unknown loader {loader!r}")
    available = {
        candidate for candidate, metadata in (("fabric", FABRIC_METADATA), ("neoforge", NEOFORGE_METADATA))
        if metadata in names
    }
    if loader != "auto":
        if loader not in available:
            metadata = FABRIC_METADATA if loader == "fabric" else NEOFORGE_METADATA
            raise VerificationError(f"missing {metadata} for {loader} jar")
        if len(available) != 1:
            raise VerificationError("runtime jar must contain metadata for exactly one loader")
        return loader
    if len(available) != 1:
        raise VerificationError("runtime jar must contain metadata for exactly one supported loader")
    return available.pop()


def read_jar_metadata(
    archive: zipfile.ZipFile, label: str, *, loader: str = "auto"
) -> tuple[str, dict]:
    resolved = resolve_loader(archive.namelist(), loader)
    try:
        if resolved == "fabric":
            value = json.loads(archive.read(FABRIC_METADATA))
        else:
            value = parse_neoforge_metadata(archive.read(NEOFORGE_METADATA).decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as exc:
        metadata = FABRIC_METADATA if resolved == "fabric" else NEOFORGE_METADATA
        raise VerificationError(f"{label}: invalid {metadata}") from exc
    if not isinstance(value, dict):
        raise VerificationError(f"{label}: metadata must be an object")
    return resolved, value


def verify_jar(
    archive: zipfile.ZipFile, label: str, expected_license: bytes, *, loader: str = "auto"
) -> str:
    verify_names(archive.namelist(), label)
    resolved, metadata = read_jar_metadata(archive, label, loader=loader)
    actual_identifier = metadata.get("license")
    if actual_identifier != EXPECTED_IDENTIFIER:
        raise VerificationError(
            f"{label}: expected licence {EXPECTED_IDENTIFIER!r}, found {actual_identifier!r}"
        )
    try:
        embedded = archive.read(EMBEDDED_LICENSE)
    except KeyError as exc:
        raise VerificationError(f"{label}: missing {EMBEDDED_LICENSE}") from exc
    if embedded != expected_license:
        raise VerificationError(f"{label}: embedded licence does not match LICENSE")
    return resolved


def verify_jar_bytes(
    data: bytes, label: str, expected_license: bytes, *, loader: str = "auto"
) -> str:
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        return verify_jar(archive, label, expected_license, loader=loader)


def verify_jar_path(path: Path, expected_license: bytes, *, loader: str = "auto") -> str:
    with zipfile.ZipFile(path) as archive:
        return verify_jar(archive, str(path), expected_license, loader=loader)


def ringworld_jars(names: list[str]) -> list[str]:
    return [
        name for name in names
        if Path(name).name.startswith("ringworld-") and name.endswith(".jar")
        and "-sources" not in Path(name).name
    ]


def verify_names(names: list[str], label: str) -> None:
    for name in names:
        path = Path(name.replace("\\", "/"))
        if path.is_absolute() or (len(path.as_posix()) >= 2 and path.as_posix()[1] == ":") \
                or ".." in path.parts:
            raise VerificationError(f"{label}: unsafe archive path {name}")
        parts = {part.lower() for part in path.parts}
        if parts & RUNTIME_PATH_PARTS:
            raise VerificationError(f"{label}: forbidden credential/runtime path {name}")
        if parts & RUNTIME_DIRECTORIES:
            raise VerificationError(f"{label}: forbidden runtime directory {name}")
        if path.name.lower().endswith(("-sources.jar", ".java", ".kt", ".groovy")):
            raise VerificationError(f"{label}: source artifact is not distributable: {name}")


def verify_outer_license(archive: zipfile.ZipFile, label: str, expected_license: bytes) -> None:
    if OUTER_LICENSE not in archive.namelist():
        raise VerificationError(f"{label}: outer package missing {OUTER_LICENSE}")
    if archive.read(OUTER_LICENSE) != expected_license:
        raise VerificationError(f"{label}: outer licence does not match LICENSE")


def verify_jars_in_archive(
    archive: zipfile.ZipFile, label: str, expected_license: bytes, *, loader: str = "auto"
) -> str:
    jars = ringworld_jars(archive.namelist())
    if len(jars) != 1:
        raise VerificationError(f"{label}: expected one RingWorld jar, found {len(jars)}")
    return verify_jar_bytes(archive.read(jars[0]), f"{label}!/{jars[0]}", expected_license,
                            loader=loader)


def verify_bundle(
    path: Path, expected_license: bytes, *, kind: str = "auto", loader: str = "auto"
) -> str:
    with zipfile.ZipFile(path) as outer:
        outer_names = outer.namelist()
        verify_names(outer_names, str(path))
        verify_outer_license(outer, str(path), expected_license)
        resolved = verify_jars_in_archive(outer, str(path), expected_license, loader=loader)
        nested_names = [name for name in outer_names if Path(name).name == CLIENT_NESTED_INSTANCE]
        is_client = kind == "client" or (kind == "auto" and bool(nested_names))
        if not is_client:
            if kind == "server" and nested_names:
                raise VerificationError(f"{path}: server overlay must not contain {CLIENT_NESTED_INSTANCE}")
            return resolved
        if len(nested_names) != 1:
            raise VerificationError(f"{path}: expected one {CLIENT_NESTED_INSTANCE}, found {len(nested_names)}")
        with zipfile.ZipFile(io.BytesIO(outer.read(nested_names[0]))) as nested:
            label = f"{path}!/{nested_names[0]}"
            verify_names(nested.namelist(), label)
            verify_outer_license(nested, label, expected_license)
            nested_loader = verify_jars_in_archive(nested, label, expected_license, loader=resolved)
            if nested_loader != resolved:
                raise VerificationError(f"{path}: nested client instance loader differs from outer bundle")
    return resolved


def verify_artifact(
    path: Path, expected_license: bytes, *, kind: str = "auto", loader: str = "auto"
) -> str:
    if kind not in {"auto", "jar", "client", "server"}:
        raise VerificationError(f"unknown artifact kind {kind!r}")
    if path.suffix == ".jar" and (kind == "jar" or kind == "auto"):
        return verify_jar_path(path, expected_license, loader=loader)
    if path.suffix == ".zip":
        return verify_bundle(path, expected_license, kind=kind, loader=loader)
    raise VerificationError(f"{path}: expected a .jar or .zip")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("artifacts", nargs="+", type=Path, help="RingWorld jar or shareable client ZIP")
    parser.add_argument("--license", type=Path, default=Path("LICENSE"), help="authoritative RingWorld licence file")
    parser.add_argument("--kind", choices=("auto", "jar", "client", "server"), default="auto")
    parser.add_argument("--loader", choices=("auto", "fabric", "neoforge"), default="auto")
    args = parser.parse_args()
    expected_license = args.license.read_bytes()
    try:
        for artifact in args.artifacts:
            loader = verify_artifact(artifact, expected_license, kind=args.kind, loader=args.loader)
            print(f"PASS {artifact} loader={loader}")
    except (OSError, ValueError, zipfile.BadZipFile, VerificationError) as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
