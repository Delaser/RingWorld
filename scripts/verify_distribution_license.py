#!/usr/bin/env python3
"""Verify RingWorld licence metadata and safe package contents."""

from __future__ import annotations

import argparse
import io
import json
from pathlib import Path
import sys
import zipfile


EXPECTED_IDENTIFIER = "MPL-2.0"
EMBEDDED_LICENSE = "LICENSE-RINGWORLD.txt"
OUTER_LICENSE = "LICENSE"
CLIENT_NESTED_INSTANCE = "RingWorld-Prism-Instance.zip"
RUNTIME_PATH_PARTS = {
    ".prism-data",
    "accounts.json",
    "launcher_accounts.json",
    "usercache.json",
    "usernamecache.json",
    "options.txt",
}
RUNTIME_DIRECTORIES = {
    "saves",
    "screenshots",
    "logs",
    "crash-reports",
    "serverconfig",
    "resourcepacks",
}


class VerificationError(RuntimeError):
    pass


def verify_jar(archive: zipfile.ZipFile, label: str, expected_license: bytes) -> None:
    verify_names(archive.namelist(), label)
    try:
        metadata = json.loads(archive.read("fabric.mod.json"))
    except KeyError as exc:
        raise VerificationError(f"{label}: missing fabric.mod.json") from exc

    actual_identifier = metadata.get("license")
    if actual_identifier != EXPECTED_IDENTIFIER:
        raise VerificationError(
            f"{label}: expected licence {EXPECTED_IDENTIFIER!r}, "
            f"found {actual_identifier!r}"
        )

    try:
        embedded = archive.read(EMBEDDED_LICENSE)
    except KeyError as exc:
        raise VerificationError(f"{label}: missing {EMBEDDED_LICENSE}") from exc

    if embedded != expected_license:
        raise VerificationError(f"{label}: embedded licence does not match LICENSE")


def verify_jar_bytes(data: bytes, label: str, expected_license: bytes) -> None:
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        verify_jar(archive, label, expected_license)


def verify_jar_path(path: Path, expected_license: bytes) -> None:
    with zipfile.ZipFile(path) as archive:
        verify_jar(archive, str(path), expected_license)


def ringworld_jars(names: list[str]) -> list[str]:
    return [
        name
        for name in names
        if Path(name).name.startswith("ringworld-")
        and name.endswith(".jar")
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


def verify_jars_in_archive(archive: zipfile.ZipFile, label: str, expected_license: bytes) -> None:
    jars = ringworld_jars(archive.namelist())
    if len(jars) != 1:
        raise VerificationError(f"{label}: expected one RingWorld jar, found {len(jars)}")
    for jar_name in jars:
        verify_jar_bytes(archive.read(jar_name), f"{label}!/{jar_name}", expected_license)


def verify_bundle(path: Path, expected_license: bytes, *, kind: str = "auto") -> None:
    with zipfile.ZipFile(path) as outer:
        outer_names = outer.namelist()
        verify_names(outer_names, str(path))
        verify_outer_license(outer, str(path), expected_license)
        verify_jars_in_archive(outer, str(path), expected_license)

        nested_names = [
            name
            for name in outer_names
            if Path(name).name == CLIENT_NESTED_INSTANCE
        ]
        is_client = kind == "client" or (kind == "auto" and bool(nested_names))
        if not is_client:
            if kind == "server" and nested_names:
                raise VerificationError(
                    f"{path}: server overlay must not contain {CLIENT_NESTED_INSTANCE}"
                )
            return
        if len(nested_names) != 1:
            raise VerificationError(
                f"{path}: expected one {CLIENT_NESTED_INSTANCE}, found {len(nested_names)}"
            )

        nested_name = nested_names[0]
        with zipfile.ZipFile(io.BytesIO(outer.read(nested_name))) as nested:
            label = f"{path}!/{nested_name}"
            verify_names(nested.namelist(), label)
            verify_outer_license(nested, label, expected_license)
            verify_jars_in_archive(nested, label, expected_license)


def verify_artifact(path: Path, expected_license: bytes, *, kind: str = "auto") -> None:
    if kind not in {"auto", "jar", "client", "server"}:
        raise VerificationError(f"unknown artifact kind {kind!r}")
    if kind == "jar" or (kind == "auto" and path.suffix == ".jar"):
        verify_jar_path(path, expected_license)
    elif path.suffix == ".zip":
        verify_bundle(path, expected_license, kind=kind)
    else:
        raise VerificationError(f"{path}: expected a .jar or .zip")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "artifacts",
        nargs="+",
        type=Path,
        help="RingWorld jar or shareable client ZIP",
    )
    parser.add_argument(
        "--license",
        type=Path,
        default=Path("LICENSE"),
        help="authoritative RingWorld licence file",
    )
    parser.add_argument(
        "--kind",
        choices=("auto", "jar", "client", "server"),
        default="auto",
        help="expected artifact layout (default: infer from extension/layout)",
    )
    args = parser.parse_args()

    expected_license = args.license.read_bytes()
    try:
        for artifact in args.artifacts:
            verify_artifact(artifact, expected_license, kind=args.kind)
            print(f"PASS {artifact}")
    except (OSError, ValueError, zipfile.BadZipFile, VerificationError) as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
