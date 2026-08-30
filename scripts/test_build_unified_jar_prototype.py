#!/usr/bin/env python3
"""Synthetic static tests for the non-release unified-JAR prototype."""

from __future__ import annotations

import json
from pathlib import Path
import stat
import tempfile
import unittest
import warnings
import zipfile

from scripts.build_unified_jar_prototype import (
    FABRIC_METADATA,
    LICENSE,
    MANIFEST,
    NEOFORGE_METADATA,
    UnifiedJarError,
    build_unified_jar,
)


def _entries(loader: str) -> dict[str, bytes]:
    result = {
        LICENSE: b"MPL-2.0\n",
        MANIFEST: (b"Manifest-Version: 1.0\nCreated-By: Fabric\n" if loader == "fabric"
                   else b"Manifest-Version: 1.0\nCreated-By: NeoForge\n"),
        "dev/ringworld/Common.class": b"common-bytecode",
        "assets/ringworld/common.json": b"{\"shared\":true}\n",
        f"platform/{loader}/Only.class": loader.encode("utf-8"),
    }
    result[FABRIC_METADATA if loader == "fabric" else NEOFORGE_METADATA] = (
        b'{"id":"ringworld"}\n' if loader == "fabric" else b'modLoader="javafml"\n'
    )
    return result


def _write_jar(path: Path, entries: dict[str, bytes], *, reverse: bool = False,
               duplicate: str | None = None, symlink: str | None = None) -> None:
    with zipfile.ZipFile(path, "w") as archive:
        for name in sorted(entries, reverse=reverse):
            info = zipfile.ZipInfo(name, date_time=(2026, 8, 31, 0, 0, 0))
            archive.writestr(info, entries[name])
        if duplicate is not None:
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                archive.writestr(duplicate, entries[duplicate])
        if symlink is not None:
            info = zipfile.ZipInfo(symlink)
            info.create_system = 3
            info.external_attr = (stat.S_IFLNK | 0o777) << 16
            archive.writestr(info, b"target")


class BuildUnifiedJarPrototypeTest(unittest.TestCase):
    def test_fuses_both_descriptors_with_deterministic_output_and_report(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fabric, neoforge = root / "fabric.jar", root / "neoforge.jar"
            first, second = root / "first.jar", root / "second.jar"
            report_path = root / "report.json"
            _write_jar(fabric, _entries("fabric"), reverse=True)
            _write_jar(neoforge, _entries("neoforge"))

            report = build_unified_jar(fabric, neoforge, first, report_path)
            second_report = build_unified_jar(fabric, neoforge, second)

            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertEqual(report.output_sha256, second_report.output_sha256)
            self.assertTrue(report.prototype)
            self.assertFalse(report.release_acceptance)
            self.assertEqual(report.output_sha256, json.loads(report_path.read_text())["output_sha256"])
            with zipfile.ZipFile(first) as archive:
                self.assertEqual(sorted(archive.namelist()), archive.namelist())
                self.assertEqual(b"Manifest-Version: 1.0\nCreated-By: Fabric\n", archive.read(MANIFEST))
                self.assertIn(FABRIC_METADATA, archive.namelist())
                self.assertIn(NEOFORGE_METADATA, archive.namelist())
                self.assertEqual(b"common-bytecode", archive.read("dev/ringworld/Common.class"))

    def test_rejects_conflicting_shared_class_without_creating_output(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fabric_entries, neoforge_entries = _entries("fabric"), _entries("neoforge")
            neoforge_entries["dev/ringworld/Common.class"] = b"conflict"
            fabric, neoforge, output = root / "fabric.jar", root / "neoforge.jar", root / "out.jar"
            _write_jar(fabric, fabric_entries)
            _write_jar(neoforge, neoforge_entries)
            with self.assertRaisesRegex(UnifiedJarError, "shared archive entry differs"):
                build_unified_jar(fabric, neoforge, output)
            self.assertFalse(output.exists())

    def test_rejects_missing_loader_metadata_or_common_license(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for case, loader, missing in (
                ("metadata", "fabric", FABRIC_METADATA),
                ("license", "neoforge", LICENSE),
            ):
                with self.subTest(case=case):
                    fabric_entries, neoforge_entries = _entries("fabric"), _entries("neoforge")
                    (fabric_entries if loader == "fabric" else neoforge_entries).pop(missing)
                    fabric, neoforge = root / f"{case}-fabric.jar", root / f"{case}-neoforge.jar"
                    _write_jar(fabric, fabric_entries)
                    _write_jar(neoforge, neoforge_entries)
                    with self.assertRaisesRegex(UnifiedJarError, "missing required"):
                        build_unified_jar(fabric, neoforge, root / f"{case}.jar")

    def test_rejects_input_that_already_contains_opposite_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for case, loader, opposite in (
                ("fabric", "fabric", NEOFORGE_METADATA),
                ("neoforge", "neoforge", FABRIC_METADATA),
            ):
                with self.subTest(case=case):
                    fabric_entries, neoforge_entries = _entries("fabric"), _entries("neoforge")
                    (fabric_entries if loader == "fabric" else neoforge_entries)[opposite] = b"unexpected"
                    fabric, neoforge = root / f"{case}-fabric.jar", root / f"{case}-neoforge.jar"
                    _write_jar(fabric, fabric_entries)
                    _write_jar(neoforge, neoforge_entries)
                    with self.assertRaisesRegex(UnifiedJarError, "already contains"):
                        build_unified_jar(fabric, neoforge, root / f"{case}.jar")

    def test_rejects_duplicate_unsafe_signature_and_symlink_entries(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for case, extra, duplicate, symlink, expected in (
                ("duplicate", None, "dev/ringworld/Common.class", None, "duplicate archive entry"),
                ("unsafe", "../escape.class", None, None, "unsafe archive entry"),
                ("signature", "META-INF/RINGWORLD.SF", None, None, "signature archive entry"),
                ("symlink", None, None, "dev/ringworld/link", "symlink archive entry"),
            ):
                with self.subTest(case=case):
                    fabric_entries, neoforge_entries = _entries("fabric"), _entries("neoforge")
                    if extra is not None:
                        fabric_entries[extra] = b"unsafe"
                    fabric, neoforge = root / f"{case}-fabric.jar", root / f"{case}-neoforge.jar"
                    _write_jar(fabric, fabric_entries, duplicate=duplicate, symlink=symlink)
                    _write_jar(neoforge, neoforge_entries)
                    with self.assertRaisesRegex(UnifiedJarError, expected):
                        build_unified_jar(fabric, neoforge, root / f"{case}.jar")


if __name__ == "__main__":
    unittest.main()
