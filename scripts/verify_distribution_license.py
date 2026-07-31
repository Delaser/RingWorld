#!/usr/bin/env python3
"""Verify RingWorld licence metadata in jars and shareable client bundles."""

from __future__ import annotations

import argparse
import io
import json
from pathlib import Path
import sys
import zipfile


EXPECTED_IDENTIFIER = "MPL-2.0"
EMBEDDED_LICENSE = "LICENSE-RINGWORLD.txt"


class VerificationError(RuntimeError):
    pass


def verify_jar(archive: zipfile.ZipFile, label: str, expected_license: bytes) -> None:
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


def verify_bundle(path: Path, expected_license: bytes) -> None:
    with zipfile.ZipFile(path) as outer:
        outer_names = outer.namelist()
        if EMBEDDED_LICENSE not in outer_names:
            raise VerificationError(f"{path}: outer bundle missing {EMBEDDED_LICENSE}")
        if outer.read(EMBEDDED_LICENSE) != expected_license:
            raise VerificationError(f"{path}: outer licence does not match LICENSE")

        outer_jars = ringworld_jars(outer_names)
        if not outer_jars:
            raise VerificationError(f"{path}: no RingWorld jar found")
        for jar_name in outer_jars:
            verify_jar_bytes(
                outer.read(jar_name),
                f"{path}!/{jar_name}",
                expected_license,
            )

        nested_names = [
            name
            for name in outer_names
            if Path(name).name == "RingWorld-Prism-Instance.zip"
        ]
        if len(nested_names) != 1:
            raise VerificationError(
                f"{path}: expected one RingWorld-Prism-Instance.zip, "
                f"found {len(nested_names)}"
            )

        nested_name = nested_names[0]
        with zipfile.ZipFile(io.BytesIO(outer.read(nested_name))) as nested:
            names = nested.namelist()
            if EMBEDDED_LICENSE not in names:
                raise VerificationError(
                    f"{path}!/{nested_name}: missing {EMBEDDED_LICENSE}"
                )
            if nested.read(EMBEDDED_LICENSE) != expected_license:
                raise VerificationError(
                    f"{path}!/{nested_name}: licence does not match LICENSE"
                )

            nested_jars = ringworld_jars(names)
            if not nested_jars:
                raise VerificationError(
                    f"{path}!/{nested_name}: no RingWorld jar found"
                )
            for jar_name in nested_jars:
                verify_jar_bytes(
                    nested.read(jar_name),
                    f"{path}!/{nested_name}!/{jar_name}",
                    expected_license,
                )


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
    args = parser.parse_args()

    expected_license = args.license.read_bytes()
    try:
        for artifact in args.artifacts:
            if artifact.suffix == ".jar":
                verify_jar_path(artifact, expected_license)
            elif artifact.suffix == ".zip":
                verify_bundle(artifact, expected_license)
            else:
                raise VerificationError(
                    f"{artifact}: expected a .jar or shareable .zip"
                )
            print(f"PASS {artifact}")
    except (OSError, ValueError, zipfile.BadZipFile, VerificationError) as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
