#!/usr/bin/env python3
"""Static safety checks for the destructive raid-fixture preparer."""

from __future__ import annotations

from pathlib import Path
import subprocess
import tempfile
import unittest
import uuid


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "prepare_raid_seam_fixture.sh"
QUALIFICATION_ROOT = ROOT / "dist" / "qualification"


class PrepareRaidSeamFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.text = SCRIPT.read_text(encoding="utf-8")

    def test_historic_paths_remain_the_default(self) -> None:
        self.assertIn('fabric) root="run-raid-seam" ;;', self.text)
        self.assertIn('neoforge) root="neoforge/run-raid-seam" ;;', self.text)

    def test_qualification_root_is_explicit_and_contained(self) -> None:
        self.assertIn("RINGWORLD_QUALIFICATION_CELL_ROOT", self.text)
        self.assertIn("--qualification-cell-root", self.text)
        self.assertIn('repository / "dist" / "qualification"', self.text)
        self.assertIn('".." in requested.parts', self.text)
        self.assertIn('candidate.relative_to(allowed)', self.text)
        self.assertIn('re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,95}"', self.text)
        self.assertIn('fixture = candidate / "run" / "run-raid-seam"', self.text)

    def test_every_managed_qualification_component_rejects_symlinks(self) -> None:
        self.assertIn("managed_paths = [", self.text)
        self.assertIn('for client in ("client-a", "client-b")', self.text)
        self.assertIn("if current.is_symlink():", self.text)
        self.assertIn("qualification fixture path contains a symlink", self.text)

    def test_validation_precedes_fixture_deletion(self) -> None:
        self.assertLess(
            self.text.index('if [[ -n "$qualification_cell_root" ]]'),
            self.text.index('rm -rf "$server/world"'),
        )

    def test_symlinked_qualification_parent_is_rejected_before_external_write(self) -> None:
        QUALIFICATION_ROOT.mkdir(parents=True, exist_ok=True)
        link = QUALIFICATION_ROOT / f"fixture-link-test-{uuid.uuid4().hex}"
        with tempfile.TemporaryDirectory() as temporary:
            external = Path(temporary)
            link.symlink_to(external, target_is_directory=True)
            try:
                completed = subprocess.run(
                    [str(SCRIPT), "--qualification-cell-root", str(link / "cell"), "fabric"],
                    cwd=ROOT,
                    text=True,
                    capture_output=True,
                    check=False,
                )
                self.assertNotEqual(0, completed.returncode)
                self.assertIn("contains a symlink", completed.stderr)
                self.assertEqual([], list(external.iterdir()))
            finally:
                link.unlink()

    def test_symlinked_managed_descendant_is_rejected_before_delete(self) -> None:
        QUALIFICATION_ROOT.mkdir(parents=True, exist_ok=True)
        cell = QUALIFICATION_ROOT / f"fixture-cell-test-{uuid.uuid4().hex}"
        fixture = cell / "run" / "run-raid-seam"
        fixture.mkdir(parents=True)
        with tempfile.TemporaryDirectory() as temporary:
            external = Path(temporary)
            server = fixture / "server"
            server.symlink_to(external, target_is_directory=True)
            try:
                completed = subprocess.run(
                    [str(SCRIPT), "--qualification-cell-root", str(cell), "fabric"],
                    cwd=ROOT,
                    text=True,
                    capture_output=True,
                    check=False,
                )
                self.assertNotEqual(0, completed.returncode)
                self.assertIn("contains a symlink", completed.stderr)
                self.assertEqual([], list(external.iterdir()))
            finally:
                server.unlink()
                fixture.rmdir()
                (cell / "run").rmdir()
                cell.rmdir()


if __name__ == "__main__":
    unittest.main()
