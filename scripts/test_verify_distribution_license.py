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
        verify_bundle,
    )
except ModuleNotFoundError:
    from verify_distribution_license import (
        EMBEDDED_LICENSE,
        EXPECTED_IDENTIFIER,
        OUTER_LICENSE,
        VerificationError,
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


if __name__ == "__main__":
    unittest.main()
