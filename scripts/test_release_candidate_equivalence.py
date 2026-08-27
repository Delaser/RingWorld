#!/usr/bin/env python3
"""Focused synthetic-jar tests for release candidate byte equivalence."""

from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest
import warnings
import zipfile
from unittest.mock import patch

from release_candidate_equivalence import (
    ReleaseEquivalenceError,
    materialize_release_candidate,
    verify_release_candidate_equivalence,
)
from minecraft_support_contract import LEGACY_CONTRACT, SupportContract


ROOT = Path(__file__).resolve().parents[1]
LICENSE = (ROOT / "LICENSE").read_bytes()
RELEASE_VERSION = "1.0.0+mc26.1.2"
RELEASE_LABEL = "1.0"


def _fabric_metadata(version: str, contract: SupportContract = LEGACY_CONTRACT) -> bytes:
    return json.dumps({
        "id": "ringworld",
        "version": version,
        "license": "MPL-2.0",
        "depends": {"minecraft": contract.minecraft_range("fabric")},
    }, sort_keys=True).encode("utf-8")


def _neoforge_metadata(version: str, contract: SupportContract = LEGACY_CONTRACT) -> bytes:
    return "\n".join((
        'license="MPL-2.0"',
        "[[mods]]",
        'modId="ringworld"',
        f'version="{version}"',
        "[[dependencies.ringworld]]",
        'modId="neoforge"',
        f'versionRange="{contract.neoforge_range}"',
        "[[dependencies.ringworld]]",
        'modId="minecraft"',
        f'versionRange="{contract.minecraft_range("neoforge")}"',
    )).encode("utf-8")


def _entries(loader: str, *, release: bool, changed_class: bool = False,
             added: bool = False, source: bool = False,
             contract: SupportContract = LEGACY_CONTRACT) -> dict[str, bytes]:
    version = RELEASE_VERSION if release else contract.artifact_version
    label = RELEASE_LABEL if release else contract.release_label(loader)
    metadata_name = "fabric.mod.json" if loader == "fabric" else "META-INF/neoforge.mods.toml"
    metadata = (_fabric_metadata(version, contract) if loader == "fabric"
                else _neoforge_metadata(version, contract))
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


def _verify(qualification: Path, release: Path, loader: str,
            contract: SupportContract = LEGACY_CONTRACT) -> object:
    return verify_release_candidate_equivalence(
        qualification,
        release,
        loader=loader,
        expected_license=LICENSE,
        release_version=RELEASE_VERSION,
        release_label=RELEASE_LABEL,
        contract=contract,
    )


class ReleaseCandidateEquivalenceTest(unittest.TestCase):
    def test_materializes_only_allowed_public_metadata_for_both_loaders(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for loader in ("fabric", "neoforge"):
                qualification, release = root / f"{loader}-q.jar", root / f"{loader}-r.jar"
                _write_jar(qualification, _entries(loader, release=False))
                result = materialize_release_candidate(
                    qualification, release, loader=loader, expected_license=LICENSE,
                    release_version=RELEASE_VERSION, release_label=RELEASE_LABEL,
                )
                self.assertEqual(RELEASE_VERSION, result.release_version)
                with zipfile.ZipFile(qualification) as before, zipfile.ZipFile(release) as after:
                    allowed = {"fabric.mod.json", "ringworld-build.properties"} if loader == "fabric" \
                        else {"META-INF/neoforge.mods.toml", "ringworld-build.properties"}
                    for name in set(before.namelist()) - allowed:
                        self.assertEqual(before.read(name), after.read(name))

    def test_materialization_rejects_missing_approved_metadata_before_output_creation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            qualification, release = root / "qualification.jar", root / "release.jar"
            entries = _entries("fabric", release=False)
            entries["ringworld-build.properties"] = b"artifactVersion=0.0.0-qualification+mc26.1\n"
            _write_jar(qualification, entries)
            with self.assertRaisesRegex(ReleaseEquivalenceError, "pre-materialization verification"):
                materialize_release_candidate(qualification, release, loader="fabric", expected_license=LICENSE,
                                              release_version=RELEASE_VERSION, release_label=RELEASE_LABEL)
            self.assertFalse(release.exists())

    def test_materialization_never_replaces_existing_or_racing_output(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            qualification, release = root / "qualification.jar", root / "release.jar"
            _write_jar(qualification, _entries("fabric", release=False))

            release.write_bytes(b"pre-existing competitor")
            with self.assertRaises(FileExistsError):
                materialize_release_candidate(qualification, release, loader="fabric", expected_license=LICENSE,
                                              release_version=RELEASE_VERSION, release_label=RELEASE_LABEL)
            self.assertEqual(b"pre-existing competitor", release.read_bytes())

            release.unlink()
            real_zip_file = zipfile.ZipFile

            def race_output_creation(path: object, mode: str = "r", *args: object, **kwargs: object) -> zipfile.ZipFile:
                if Path(path) == release and mode == "x":
                    release.write_bytes(b"racing competitor")
                return real_zip_file(path, mode, *args, **kwargs)

            with patch("release_candidate_equivalence.zipfile.ZipFile", side_effect=race_output_creation):
                with self.assertRaises(FileExistsError):
                    materialize_release_candidate(qualification, release, loader="fabric", expected_license=LICENSE,
                                                  release_version=RELEASE_VERSION, release_label=RELEASE_LABEL)
            self.assertEqual(b"racing competitor", release.read_bytes())

    def test_materialization_removes_only_its_output_after_equivalence_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            qualification, release = root / "qualification.jar", root / "release.jar"
            _write_jar(qualification, _entries("fabric", release=False))
            with patch("release_candidate_equivalence.verify_release_candidate_equivalence",
                       side_effect=ReleaseEquivalenceError("forced verification failure")):
                with self.assertRaisesRegex(ReleaseEquivalenceError, "forced verification failure"):
                    materialize_release_candidate(qualification, release, loader="fabric", expected_license=LICENSE,
                                                  release_version=RELEASE_VERSION, release_label=RELEASE_LABEL)
            self.assertFalse(release.exists())

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
            with self.assertRaisesRegex(ReleaseEquivalenceError, "manifest-approved"):
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

    def test_accepts_the_manifest_derived_26_2_range(self) -> None:
        contract = SupportContract("26.2", ("26.2",), ("26.2.0.69",))
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            qualification, release = root / "qualification.jar", root / "release.jar"
            version = "1.2.0+mc26.2"
            label = "1.2"
            _write_jar(qualification, _entries("fabric", release=False, contract=contract))
            release_entries = _entries("fabric", release=True, contract=contract)
            release_entries["ringworld-build.properties"] = (
                f"artifactVersion={version}\nreleaseLabel={label}\n".encode()
            )
            release_entries["fabric.mod.json"] = _fabric_metadata(version, contract)
            _write_jar(release, release_entries)
            result = verify_release_candidate_equivalence(
                qualification, release, loader="fabric", expected_license=LICENSE,
                release_version=version, release_label=label, contract=contract,
            )
            self.assertEqual(version, result.release_version)


if __name__ == "__main__":
    unittest.main()
