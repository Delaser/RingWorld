import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
INSTALLER = ROOT / "deploy" / "alpha" / "Install-RingWorld-Alpha-Windows.bat"


class AlphaInstallerContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = INSTALLER.read_text(encoding="utf-8")

    def test_follows_manifest_artifact_and_not_a_fixed_build_hash(self):
        self.assertIn("RELEASE-MANIFEST.json", self.text)
        self.assertIn("ConvertFrom-Json", self.text)
        self.assertIn("$artifacts[0].name", self.text)
        self.assertIn("$artifacts[0].sha256", self.text)
        self.assertNotIn("$matches=", self.text.lower())
        self.assertNotIn("EXPECTED_SHA256", self.text)
        self.assertNotRegex(self.text, r"[0-9a-f]{64}")

    def test_fails_closed_on_identity_and_archive_contracts(self):
        for required in (
                "MPL-2.0",
                "sourceRevision",
                "sourceUrl",
                "-Fabric-Client-Windows[.]zip$",
                "Get-FileHash -Algorithm SHA256",
                "No unverified package was launched",
        ):
            self.assertIn(required, self.text)


if __name__ == "__main__":
    unittest.main()
