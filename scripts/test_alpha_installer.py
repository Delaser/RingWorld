import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
INSTALLERS = {
    "legacy-fabric": (
        ROOT / "deploy" / "alpha" / "Install-RingWorld-Alpha-Windows.bat",
        "RELEASE-MANIFEST.json",
        "fabric",
        "Fabric",
    ),
    "fabric": (
        ROOT / "deploy" / "alpha" / "Install-RingWorld-Alpha-Fabric-Windows.bat",
        "RELEASE-MANIFEST-FABRIC.json",
        "fabric",
        "Fabric",
    ),
    "neoforge": (
        ROOT / "deploy" / "alpha" / "Install-RingWorld-Alpha-NeoForge-Windows.bat",
        "RELEASE-MANIFEST-NEOFORGE.json",
        "neoforge",
        "NeoForge",
    ),
}


class AlphaInstallerContractTest(unittest.TestCase):
    def test_follows_manifest_artifact_and_not_a_fixed_build_hash(self):
        for name, (path, manifest, _loader, _display) in INSTALLERS.items():
            with self.subTest(installer=name):
                text = path.read_text(encoding="utf-8")
                self.assertIn(manifest, text)
                self.assertIn("ConvertFrom-Json", text)
                self.assertIn("$artifacts[0].name", text)
                self.assertIn("$artifacts[0].sha256", text)
                self.assertNotIn("$matches=", text.lower())
                self.assertNotIn("EXPECTED_SHA256", text)
                self.assertNotRegex(text, r"[0-9a-f]{64}")

    def test_fails_closed_on_identity_and_archive_contracts(self):
        for name, (path, _manifest, loader, display) in INSTALLERS.items():
            text = path.read_text(encoding="utf-8")
            with self.subTest(installer=name):
                for required in (
                        "MPL-2.0",
                        "sourceRevision",
                        "sourceUrl",
                        f"if([string]$manifest.loader -ne '{loader}')",
                        f"-{display}-Client-Windows[.]zip$",
                        "Get-FileHash -Algorithm SHA256",
                        "No unverified package was launched",
                ):
                    self.assertIn(required, text)

    def test_loader_specific_installers_do_not_overwrite_each_other(self):
        fabric = INSTALLERS["fabric"][0].read_text(encoding="utf-8")
        neoforge = INSTALLERS["neoforge"][0].read_text(encoding="utf-8")
        self.assertIn(r"Alpha4-Fabric", fabric)
        self.assertIn(r"Alpha4-NeoForge", neoforge)


if __name__ == "__main__":
    unittest.main()
