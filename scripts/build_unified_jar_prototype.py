#!/usr/bin/env python3
"""Experimental, non-release Fabric/NeoForge JAR fuser.

This deliberately has no Gradle, packaging, staging, publication, or runtime
integration. It only proves whether two already-built loader artifacts can be
represented by one byte-safe ZIP without silently resolving loader conflicts.
Its output is not a RingWorld release candidate or compatibility claim.
"""

from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import asdict, dataclass
import hashlib
import json
from pathlib import Path, PurePosixPath
import stat
import sys
from typing import Mapping
import zipfile


FABRIC_METADATA = "fabric.mod.json"
NEOFORGE_METADATA = "META-INF/neoforge.mods.toml"
LICENSE = "LICENSE-RINGWORLD.txt"
MANIFEST = "META-INF/MANIFEST.MF"
_DESCRIPTORS = frozenset((FABRIC_METADATA, NEOFORGE_METADATA))
_FIXED_TIMESTAMP = (1980, 1, 1, 0, 0, 0)


class UnifiedJarError(ValueError):
    """The two inputs cannot safely form a prototype unified JAR."""


@dataclass(frozen=True)
class UnifiedJarReport:
    """Static result only; it explicitly does not establish release acceptance."""

    prototype: bool
    release_acceptance: bool
    fabric_sha256: str
    neoforge_sha256: str
    output_sha256: str
    fabric_entry_count: int
    neoforge_entry_count: int
    shared_entry_count: int
    fabric_exclusive_entry_count: int
    neoforge_exclusive_entry_count: int
    output_entry_count: int


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _reject_unsafe_name(name: str, label: str) -> None:
    if not name or "\\" in name or "\x00" in name or name.startswith("/"):
        raise UnifiedJarError(f"{label} contains unsafe archive entry {name!r}")
    path = PurePosixPath(name)
    if path.is_absolute() or any(part in ("", ".", "..") for part in path.parts):
        raise UnifiedJarError(f"{label} contains unsafe archive entry {name!r}")


def _is_signature(name: str) -> bool:
    path = PurePosixPath(name)
    if len(path.parts) < 2 or path.parts[0].upper() != "META-INF":
        return False
    basename = path.name.upper()
    return basename.startswith("SIG-") or basename.endswith((".SF", ".RSA", ".DSA", ".EC"))


def _is_symlink(info: zipfile.ZipInfo) -> bool:
    return stat.S_IFMT(info.external_attr >> 16) == stat.S_IFLNK


def _read_jar(path: Path, label: str) -> dict[str, bytes]:
    if not path.is_file() or path.is_symlink() or path.suffix.lower() != ".jar":
        raise UnifiedJarError(f"{label} must be an existing non-symlink .jar file")
    try:
        with zipfile.ZipFile(path) as archive:
            infos = archive.infolist()
            duplicates = sorted(name for name, count in Counter(info.filename for info in infos).items()
                                if count > 1)
            if duplicates:
                raise UnifiedJarError(f"{label} contains duplicate archive entry {duplicates[0]!r}")
            result: dict[str, bytes] = {}
            for info in infos:
                _reject_unsafe_name(info.filename, label)
                if _is_symlink(info):
                    raise UnifiedJarError(f"{label} contains symlink archive entry {info.filename!r}")
                if _is_signature(info.filename):
                    raise UnifiedJarError(f"{label} contains signature archive entry {info.filename!r}")
                if info.is_dir():
                    continue
                result[info.filename] = archive.read(info)
    except (OSError, zipfile.BadZipFile, zipfile.LargeZipFile) as error:
        raise UnifiedJarError(f"cannot open {label}: {error}") from error
    return result


def _require_entry(entries: Mapping[str, bytes], name: str, label: str) -> None:
    if name not in entries:
        raise UnifiedJarError(f"{label} is missing required {name}")


def _write_deterministic_zip(path: Path, entries: Mapping[str, bytes]) -> None:
    with zipfile.ZipFile(path, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(entries):
            info = zipfile.ZipInfo(name, date_time=_FIXED_TIMESTAMP)
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, entries[name], compress_type=zipfile.ZIP_DEFLATED,
                             compresslevel=9)


def _verify_output(path: Path, expected: Mapping[str, bytes], fabric_manifest: bytes) -> None:
    output = _read_jar(path, "output JAR")
    if output != expected:
        raise UnifiedJarError("output JAR contents do not match the verified input union")
    _require_entry(output, FABRIC_METADATA, "output JAR")
    _require_entry(output, NEOFORGE_METADATA, "output JAR")
    if output.get(MANIFEST) != fabric_manifest:
        raise UnifiedJarError("output JAR did not preserve the Fabric manifest")


def build_unified_jar(
    fabric_jar: Path, neoforge_jar: Path, output_jar: Path, report_path: Path | None = None,
) -> UnifiedJarReport:
    """Fuse two static artifacts after strict byte-level safety checks.

    The output must not already exist. This keeps the prototype fail-closed and
    prevents it from replacing a reviewed artifact by accident.
    """
    fabric_jar = Path(fabric_jar)
    neoforge_jar = Path(neoforge_jar)
    output_jar = Path(output_jar)
    report_path = Path(report_path) if report_path is not None else None
    if output_jar.exists():
        raise FileExistsError(f"output JAR already exists: {output_jar}")
    if report_path is not None and report_path.exists():
        raise FileExistsError(f"report already exists: {report_path}")
    output_resolved = output_jar.resolve()
    if output_resolved in {fabric_jar.resolve(), neoforge_jar.resolve()}:
        raise UnifiedJarError("output JAR must differ from both input JARs")

    fabric = _read_jar(fabric_jar, "Fabric input")
    neoforge = _read_jar(neoforge_jar, "NeoForge input")
    _require_entry(fabric, FABRIC_METADATA, "Fabric input")
    _require_entry(neoforge, NEOFORGE_METADATA, "NeoForge input")
    if NEOFORGE_METADATA in fabric:
        raise UnifiedJarError("Fabric input already contains NeoForge metadata")
    if FABRIC_METADATA in neoforge:
        raise UnifiedJarError("NeoForge input already contains Fabric metadata")
    _require_entry(fabric, LICENSE, "Fabric input")
    _require_entry(neoforge, LICENSE, "NeoForge input")
    _require_entry(fabric, MANIFEST, "Fabric input")

    shared = set(fabric).intersection(neoforge)
    for name in sorted(shared - {MANIFEST}):
        if fabric[name] != neoforge[name]:
            raise UnifiedJarError(f"shared archive entry differs between inputs: {name}")

    unified = dict(fabric)
    for name, data in neoforge.items():
        if name not in unified:
            unified[name] = data
    _require_entry(unified, LICENSE, "unified JAR")
    if not _DESCRIPTORS.issubset(unified):
        raise UnifiedJarError("unified JAR must contain both loader descriptors")

    report_created = False
    try:
        _write_deterministic_zip(output_jar, unified)
        _verify_output(output_jar, unified, fabric[MANIFEST])
        report = UnifiedJarReport(
            prototype=True,
            release_acceptance=False,
            fabric_sha256=_sha256(fabric_jar),
            neoforge_sha256=_sha256(neoforge_jar),
            output_sha256=_sha256(output_jar),
            fabric_entry_count=len(fabric),
            neoforge_entry_count=len(neoforge),
            shared_entry_count=len(shared),
            fabric_exclusive_entry_count=len(set(fabric) - set(neoforge)),
            neoforge_exclusive_entry_count=len(set(neoforge) - set(fabric)),
            output_entry_count=len(unified),
        )
        if report_path is not None:
            with report_path.open("x", encoding="utf-8") as report_file:
                report_created = True
                json.dump(asdict(report), report_file, indent=2, sort_keys=True)
                report_file.write("\n")
    except Exception:
        output_jar.unlink(missing_ok=True)
        if report_created and report_path is not None:
            report_path.unlink(missing_ok=True)
        raise
    return report


def _parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fabric-jar", required=True, type=Path)
    parser.add_argument("--neoforge-jar", required=True, type=Path)
    parser.add_argument("--output-jar", required=True, type=Path)
    parser.add_argument("--report", type=Path,
                        help="optional static JSON report; no release acceptance is implied")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(sys.argv[1:] if argv is None else argv)
    try:
        report = build_unified_jar(args.fabric_jar, args.neoforge_jar,
                                   args.output_jar, args.report)
    except (FileExistsError, UnifiedJarError) as error:
        print(f"unified JAR prototype failed: {error}", file=sys.stderr)
        return 2
    print(json.dumps(asdict(report), sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
