#!/usr/bin/env python3

from __future__ import annotations

import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

try:
    from scripts.verify_distribution_license import (
        EMBEDDED_LICENSE,
        EXPECTED_IDENTIFIER,
        OUTER_LICENSE,
        VerificationError,
        verify_jar_path,
        verify_bundle,
    )
except ModuleNotFoundError:
    from verify_distribution_license import (
        EMBEDDED_LICENSE,
        EXPECTED_IDENTIFIER,
        OUTER_LICENSE,
        VerificationError,
        verify_jar_path,
        verify_bundle,
    )


LICENSE_BYTES = b"RingWorld test licence\n"


def jar_bytes(identifier: str = EXPECTED_IDENTIFIER) -> bytes:
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr(
            "fabric.mod.json",
            json.dumps({"id": "ringworld", "license": identifier}),
        )
        archive.writestr(EMBEDDED_LICENSE, LICENSE_BYTES)
    return output.getvalue()


def neoforge_jar_bytes(identifier: str = EXPECTED_IDENTIFIER) -> bytes:
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr(
            "META-INF/neoforge.mods.toml",
            f'''license="{identifier}"

[[mods]]
modId="ringworld"
version="1.0.0"
authors="Delaser"
description=''' + "'''" + '''
RingWorld test description.
''' + "'''" + '''

[[dependencies.ringworld]]
modId="neoforge"
type="required"
versionRange="[26.1.2.87,)"
''',
        )
        archive.writestr(EMBEDDED_LICENSE, LICENSE_BYTES)
    return output.getvalue()


def nested_instance_bytes(identifier: str = EXPECTED_IDENTIFIER) -> bytes:
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr(OUTER_LICENSE, LICENSE_BYTES)
        archive.writestr("minecraft/mods/ringworld-1.0.0.jar", jar_bytes(identifier))
    return output.getvalue()


def write_bundle(
    path: Path,
    *,
    identifier: str = EXPECTED_IDENTIFIER,
    include_outer_license: bool = True,
    unsafe_path: str | None = None,
) -> None:
    with zipfile.ZipFile(path, "w") as archive:
        if include_outer_license:
            archive.writestr(OUTER_LICENSE, LICENSE_BYTES)
        archive.writestr("instance/mods/ringworld-1.0.0.jar", jar_bytes(identifier))
        archive.writestr(
            "RingWorld-Prism-Instance.zip",
            nested_instance_bytes(identifier),
        )
        if unsafe_path:
            archive.writestr(unsafe_path, "forbidden")


class DistributionLicenceVerificationTest(unittest.TestCase):
    def test_accepts_consistent_bundle(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "client.zip")
            write_bundle(path)
            verify_bundle(path, LICENSE_BYTES)

    def test_rejects_stale_metadata(self) -> None:
        for identifier in ("MIT", "LicenseRef-RingWorld-Evaluation-1.0"):
            with (
                self.subTest(identifier=identifier),
                tempfile.TemporaryDirectory() as directory,
            ):
                path = Path(directory, "client.zip")
                write_bundle(path, identifier=identifier)
                with self.assertRaisesRegex(VerificationError, "expected licence"):
                    verify_bundle(path, LICENSE_BYTES)

    def test_rejects_missing_outer_licence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "client.zip")
            write_bundle(path, include_outer_license=False)
            with self.assertRaisesRegex(VerificationError, "outer package missing"):
                verify_bundle(path, LICENSE_BYTES)

    def test_rejects_runtime_state_source_and_traversal(self) -> None:
        for unsafe, message in (
            ("instance/.minecraft/saves/world/level.dat", "forbidden runtime directory"),
            ("instance/.minecraft/mods/ringworld-sources.jar", "source artifact"),
            ("../accounts.json", "unsafe archive path"),
            ("..\\accounts.json", "unsafe archive path"),
            ("C:\\accounts.json", "unsafe archive path"),
        ):
            with self.subTest(path=unsafe), tempfile.TemporaryDirectory() as directory:
                path = Path(directory, "client.zip")
                write_bundle(path, unsafe_path=unsafe)
                with self.assertRaisesRegex(VerificationError, message):
                    verify_bundle(path, LICENSE_BYTES)

    def test_accepts_neoforge_metadata_and_rejects_wrong_explicit_loader(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory, "ringworld-neoforge-1.0.0.jar")
            jar.write_bytes(neoforge_jar_bytes())
            self.assertEqual(verify_jar_path(jar, LICENSE_BYTES), "neoforge")
            self.assertEqual(verify_jar_path(jar, LICENSE_BYTES, loader="neoforge"), "neoforge")
            with self.assertRaisesRegex(VerificationError, "missing fabric.mod.json"):
                verify_jar_path(jar, LICENSE_BYTES, loader="fabric")

    def test_rejects_neoforge_stale_license_and_ambiguous_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            stale = Path(directory, "ringworld-neoforge-1.0.0.jar")
            stale.write_bytes(neoforge_jar_bytes("MIT"))
            with self.assertRaisesRegex(VerificationError, "expected licence"):
                verify_jar_path(stale, LICENSE_BYTES, loader="neoforge")

            ambiguous = Path(directory, "ringworld-ambiguous.jar")
            with zipfile.ZipFile(ambiguous, "w") as archive:
                archive.writestr("fabric.mod.json", json.dumps({"id": "ringworld", "license": "MPL-2.0"}))
                archive.writestr("META-INF/neoforge.mods.toml", 'license="MPL-2.0"\n')
                archive.writestr(EMBEDDED_LICENSE, LICENSE_BYTES)
            with self.assertRaisesRegex(VerificationError, "exactly one supported loader"):
                verify_jar_path(ambiguous, LICENSE_BYTES)


if __name__ == "__main__":
    unittest.main()
