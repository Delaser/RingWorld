"""Static guardrails for qualification-only loader metadata ranges.

These checks deliberately inspect build wiring rather than invoking Gradle.
The version matrix runner owns real build/runtime evidence; this file protects
the boundary that keeps broad dependency metadata out of normal artifacts.
"""

from __future__ import annotations

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class QualificationMetadataRangesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.fabric_build = (ROOT / "build.gradle").read_text(encoding="utf-8")
        cls.neoforge_build = (ROOT / "neoforge" / "build.gradle").read_text(
            encoding="utf-8"
        )
        cls.fabric_metadata = (ROOT / "src" / "main" / "resources" / "fabric.mod.json").read_text(
            encoding="utf-8"
        )
        cls.neoforge_metadata = (
            ROOT / "neoforge" / "src" / "main" / "templates" / "META-INF" / "neoforge.mods.toml"
        ).read_text(encoding="utf-8")

    def test_normal_metadata_keeps_the_exact_26_1_2_forms(self) -> None:
        self.assertIn('"minecraft": "${minecraft_version}"', self.fabric_metadata)
        self.assertIn('versionRange="${neoforge_metadata_version}"', self.neoforge_metadata)
        self.assertIn('versionRange="${minecraft_metadata_version}"', self.neoforge_metadata)
        self.assertIn(
            'def minecraftMetadataVersion = qualificationMinecraftRange ?: "[${rootProject.minecraft_version}]"',
            self.neoforge_build,
        )
        self.assertIn(
            'def neoForgeMetadataVersion = qualificationNeoForgeRange ?: "[${rootProject.neoforge_version},)"',
            self.neoforge_build,
        )

    def test_ranges_are_opt_in_and_wired_to_the_correct_metadata(self) -> None:
        self.assertIn('providers.gradleProperty("ringQualificationMinecraftRange")', self.fabric_build)
        self.assertIn('providers.gradleProperty("ringQualificationNeoForgeRange")', self.fabric_build)
        self.assertIn('expand version: project.version, minecraft_version: minecraftMetadataVersion', self.fabric_build)
        self.assertIn('minecraft_metadata_version: minecraftMetadataVersion', self.neoforge_build)
        self.assertIn('neoforge_metadata_version: neoForgeMetadataVersion', self.neoforge_build)
        self.assertIn("'ringQualificationMinecraftRange', qualificationMinecraftRange", self.neoforge_build)
        self.assertIn("'ringQualificationNeoForgeRange', qualificationNeoForgeRange", self.neoforge_build)

    def test_ranges_are_rejected_without_the_isolated_qualification_pair(self) -> None:
        self.assertIn('require ringQualificationRoot and ringQualificationCell', self.fabric_build)
        self.assertIn('if (!qualificationEnabled', self.fabric_build)
        self.assertIn('qualificationMinecraftRangeProperty != null || qualificationNeoForgeRangeProperty != null', self.fabric_build)

    def test_unsafe_or_unbounded_values_have_narrow_loader_specific_guards(self) -> None:
        self.assertIn('without quotes, whitespace padding, or placeholders', self.fabric_build)
        self.assertIn('bounded Fabric version predicate', self.fabric_build)
        self.assertIn('bounded NeoForge/Minecraft Maven version range', self.fabric_build)
        self.assertIn('def clause = "(?:>=|>|<=|<|=|~|\\\\^)', self.fabric_build)
        self.assertIn('^[\\[(][0-9][0-9A-Za-z._-]{0,63},[0-9][0-9A-Za-z._-]{0,63}[\\])]$', self.fabric_build)

    def test_no_unconditional_broad_release_metadata_exists(self) -> None:
        self.assertNotIn('"minecraft": ">=26.1 <26.2"', self.fabric_metadata)
        self.assertNotIn('versionRange="[26.1,26.2)"', self.neoforge_metadata)
        self.assertNotIn('ringQualificationMinecraftRange ?: ">=26.1 <26.2"', self.fabric_build)


if __name__ == "__main__":
    unittest.main()
