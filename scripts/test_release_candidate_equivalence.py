#!/usr/bin/env python3
"""Focused synthetic-jar tests for release candidate byte equivalence."""

from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest
import warnings
import zipfile

from release_candidate_equivalence import (
    ReleaseEquivalenceError,
    verify_release_candidate_equivalence,
)


ROOT = Path(__file__).resolve().parents[1]
LICENSE = (ROOT / "LICENSE").read_bytes()
RELEASE_VERSION = "1.0.0+mc26.1.2"
RELEASE_LABEL = "1.0"


def _fabric_metadata(version: str) -> bytes:
    return json.dumps({
        "id": "ringworld",
        "version": version,
        "license": "MPL-2.0",
        "depends": {"minecraft": ">=26.1 <=26.1.2"},
    }, sort_keys=True).encode("utf-8")


def _neoforge_metadata(version: str) -> bytes:
    return "\n".join((
        'license="MPL-2.0"',
        "[[mods]]",
        'modId="ringworld"',
        f'version="{version}"',
        "[[dependencies.ringworld]]",
        'modId="neoforge"',
        'versionRange="[26.1.0.19-beta,26.1.2.87]"',
        "[[dependencies.ringworld]]",
        'modId="minecraft"',
        'versionRange="[26.1,26.1.2]"',
    )).encode("utf-8")


def _entries(loader: str, *, release: bool, changed_class: bool = False,
             added: bool = False, source: bool = False) -> dict[str, bytes]:
    version = RELEASE_VERSION if release else "0.0.0-qualification+mc26.1"
    label = RELEASE_LABEL if release else f"qualification-26.1-{loader}"
    metadata_name = "fabric.mod.json" if loader == "fabric" else "META-INF/neoforge.mods.toml"
    metadata = _fabric_metadata(version) if loader == "fabric" else _neoforge_metadata(version)
    result = {
        "LICENSE-RINGWORLD.txt": LICENSE,
        "ringworld-build.properties": (
            f"artifactVersion={version}\nreleaseLabel={label}\n".encode("utf-8")
        ),
        metadata_name: metadata,
        "dev/ringworld/Example.class": b"release-class" if changed_class else b"candidate-class",
        "assets/ringworld/texture.bin": b"identical-texture",
    }
    if added:
        result["assets/ringworld/added.bin"] = b"not-approved"
    if source:
        result["dev/ringworld/Leaked.java"] = b"class Leaked {}"
    return result


def _write_jar(path: Path, entries: dict[str, bytes], *, reverse: bool = False,
               timestamp: tuple[int, int, int, int, int, int] = (2026, 1, 1, 0, 0, 0),
               duplicate: str | None = None) -> None:
    names = sorted(entries, reverse=reverse)
    with zipfile.ZipFile(path, "w") as archive:
        for name in names:
            info = zipfile.ZipInfo(name, date_time=timestamp)
            archive.writestr(info, entries[name])
        if duplicate is not None:
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                archive.writestr(duplicate, entries[duplicate])


def _verify(qualification: Path, release: Path, loader: str) -> object:
    return verify_release_candidate_equivalence(
        qualification,
        release,
        loader=loader,
        expected_license=LICENSE,
        release_version=RELEASE_VERSION,
        release_label=RELEASE_LABEL,
    )


class ReleaseCandidateEquivalenceTest(unittest.TestCase):
    def test_accepts_metadata_only_difference_despite_zip_order_and_timestamps(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for loader in ("fabric", "neoforge"):
                qualification, release = root / f"{loader}-q.jar", root / f"{loader}-r.jar"
                _write_jar(qualification, _entries(loader, release=False), timestamp=(2026, 1, 1, 0, 0, 0))
                _write_jar(release, _entries(loader, release=True), reverse=True,
                           timestamp=(2026, 8, 13, 12, 0, 0))
                result = _verify(qualification, release, loader)
                self.assertEqual(loader, result.loader)
                self.assertEqual(RELEASE_VERSION, result.release_version)
                self.assertEqual(RELEASE_LABEL, result.release_label)

    def test_rejects_byte_change_outside_the_explicit_allowlist(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            qualification, release = root / "qualification.jar", root / "release.jar"
            _write_jar(qualification, _entries("fabric", release=False))
            _write_jar(release, _entries("fabric", release=True, changed_class=True))
            with self.assertRaisesRegex(ReleaseEquivalenceError, "Example.class"):
                _verify(qualification, release, "fabric")

    def test_rejects_added_removed_duplicate_and_source_entries(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            qualification = root / "qualification.jar"
            _write_jar(qualification, _entries("fabric", release=False))
            added = root / "added.jar"
            _write_jar(added, _entries("fabric", release=True, added=True))
            with self.assertRaisesRegex(ReleaseEquivalenceError, "member set"):
                _verify(qualification, added, "fabric")
            duplicate = root / "duplicate.jar"
            _write_jar(duplicate, _entries("fabric", release=True), duplicate="assets/ringworld/texture.bin")
            with self.assertRaisesRegex(ReleaseEquivalenceError, "duplicate archive entry"):
                _verify(qualification, duplicate, "fabric")
            source = root / "source.jar"
            _write_jar(source, _entries("fabric", release=True, source=True))
            with self.assertRaisesRegex(ReleaseEquivalenceError, "source artifact"):
                _verify(qualification, source, "fabric")

    def test_rejects_stale_release_identity_or_range(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            qualification, release = root / "qualification.jar", root / "release.jar"
            _write_jar(qualification, _entries("fabric", release=False))
            stale = _entries("fabric", release=True)
            stale["ringworld-build.properties"] = (
                f"artifactVersion={RELEASE_VERSION}\nreleaseLabel=0.9\n".encode("utf-8")
            )
            _write_jar(release, stale)
            with self.assertRaisesRegex(ReleaseEquivalenceError, "releaseLabel"):
                _verify(qualification, release, "fabric")
            bad_range = _entries("fabric", release=True)
            bad_range["fabric.mod.json"] = json.dumps({
                "id": "ringworld", "version": RELEASE_VERSION, "license": "MPL-2.0",
                "depends": {"minecraft": ">=26.1 <=26.1.3"},
            }).encode("utf-8")
            _write_jar(release, bad_range)
            with self.assertRaisesRegex(ReleaseEquivalenceError, "approved 26.1"):
                _verify(qualification, release, "fabric")

    def test_rejects_unapproved_fabric_descriptor_and_build_property_changes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            qualification, release = root / "qualification.jar", root / "release.jar"
            _write_jar(qualification, _entries("fabric", release=False))
            descriptor_changed = _entries("fabric", release=True)
            descriptor_changed["fabric.mod.json"] = json.dumps({
                "id": "ringworld", "version": RELEASE_VERSION, "license": "MPL-2.0",
                "depends": {"minecraft": ">=26.1 <=26.1.2"}, "environment": "client",
            }).encode("utf-8")
            _write_jar(release, descriptor_changed)
            with self.assertRaisesRegex(ReleaseEquivalenceError, "descriptor changes"):
                _verify(qualification, release, "fabric")
            properties_changed = _entries("fabric", release=True)
            properties_changed["ringworld-build.properties"] += b"unapproved=true\n"
            _write_jar(release, properties_changed)
            with self.assertRaisesRegex(ReleaseEquivalenceError, "properties changes"):
                _verify(qualification, release, "fabric")

    def test_rejects_unapproved_neoforge_descriptor_change(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            qualification, release = root / "qualification.jar", root / "release.jar"
            _write_jar(qualification, _entries("neoforge", release=False))
            changed = _entries("neoforge", release=True)
            changed["META-INF/neoforge.mods.toml"] += b"\ndisplayTest=\"IGNORE_SERVER_VERSION\"\n"
            _write_jar(release, changed)
            with self.assertRaisesRegex(ReleaseEquivalenceError, "descriptor changes"):
                _verify(qualification, release, "neoforge")

    def test_rejects_cross_loader_comparison(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            qualification, release = root / "qualification.jar", root / "release.jar"
            _write_jar(qualification, _entries("fabric", release=False))
            _write_jar(release, _entries("neoforge", release=True))
            with self.assertRaises(ReleaseEquivalenceError):
                _verify(qualification, release, "fabric")


if __name__ == "__main__":
    unittest.main()
