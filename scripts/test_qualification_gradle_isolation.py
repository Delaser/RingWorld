"""Static guardrails for opt-in Gradle qualification-cell isolation."""

from __future__ import annotations

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]


class QualificationGradleIsolationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.fabric = (ROOT / "build.gradle").read_text(encoding="utf-8")
        cls.neoforge = (ROOT / "neoforge" / "build.gradle").read_text(encoding="utf-8")

    def test_root_and_cell_are_a_safe_opt_in_pair(self) -> None:
        self.assertIn('ringQualificationRoot and ringQualificationCell must be supplied together', self.fabric)
        self.assertIn('new File(repositoryRoot, "dist/qualification")', self.fabric)
        self.assertNotIn('new File(repositoryRoot, "build/qualification")', self.fabric)
        self.assertIn('ringQualificationRoot must not be empty or contain path traversal', self.fabric)
        self.assertIn('ringQualificationCell must be one safe cell identifier', self.fabric)

    def test_each_loader_redirects_build_outputs_below_the_cell(self) -> None:
        self.assertIn('new File(qualificationCellRoot, "build/fabric")', self.fabric)
        self.assertIn("new File(rootProject.ext.ringQualificationCellRoot, 'build/neoforge')", self.neoforge)

    def test_all_declared_run_directories_use_the_cell_resolver(self) -> None:
        self.assertNotRegex(self.fabric, r'runDir\s+["\']run[-/]')
        self.assertNotRegex(self.neoforge, r"gameDirectory\s*=\s*project\.file\(['\"]run[-/]")
        self.assertNotRegex(self.fabric, r'file\(["\']run[-/]')
        self.assertNotRegex(self.neoforge, r"project\.file\(['\"]run[-/]")
        self.assertGreaterEqual(self.fabric.count('qualificationRunDirectory("run-'), 15)
        self.assertGreaterEqual(self.neoforge.count("qualificationRunFile('run-"), 20)

    def test_verifiers_and_preparers_share_the_same_run_resolver(self) -> None:
        for marker in (
            'def sourceSavesDirectory = qualificationRunFile("run/saves")',
            'def multiplayerHarnessDirectory = qualificationRunFile("run-multiplayer")',
            'verifyFabricProjectionOutputs(qualificationRunFile("run-production-projection")',
            'verifyFabricVisualParityOutputs(qualificationRunFile("run-production-visual-parity"))',
        ):
            self.assertIn(marker, self.fabric)
        for marker in (
            "def neoForgeProjectionSourceSaves = qualificationRunFile('run-client/saves')",
            "def neoForgeMultiplayerRun = qualificationRunFile('run-multiplayer')",
            "def neoForgeHeadlessPrewarmRun = qualificationRunFile('run-headless-prewarm')",
        ):
            self.assertIn(marker, self.neoforge)

    def test_qualification_ports_have_dedicated_property_plumbing(self) -> None:
        self.assertIn('providers.gradleProperty("ringQualificationFabricMultiplayerPort")', self.fabric)
        self.assertIn('providers.gradleProperty("ringQualificationPort")', self.fabric)
        self.assertIn('providers.gradleProperty("ringQualificationFabricRaidSeamPort")', self.fabric)
        self.assertIn('providers.gradleProperty("ringQualificationRaidSeamPort")', self.fabric)
        self.assertIn("'ringQualificationNeoForgeMultiplayerPort'", self.neoforge)
        self.assertIn("'ringQualificationNeoForgeRaidSeamPort'", self.neoforge)
        self.assertIn("providers.gradleProperty('ringQualificationPort')", self.neoforge)

    def test_qualification_smokes_use_cell_ports_and_run_roots(self) -> None:
        self.assertIn('qualificationSmokeServer {', self.fabric)
        self.assertIn('programArgs "--port", fabricMultiplayerPort().toString(), "nogui"', self.fabric)
        self.assertIn("qualificationSmokeServer {", self.neoforge)
        self.assertIn("ringQualificationNeoForgeSmokePort", self.neoforge)
        self.assertIn('new File(new File(qualificationCellRoot, "run"), relative)', self.fabric)

    def test_frozen_candidate_mode_is_qualification_only_and_loader_symmetric(self) -> None:
        self.assertIn(
            'ringQualificationFrozenCandidateJar and ringQualificationFrozenCandidateSha256 must be supplied together',
            self.fabric,
        )
        self.assertIn('a frozen candidate requires ringQualificationRoot and ringQualificationCell', self.fabric)
        self.assertIn('the frozen candidate changed after qualification planning', self.fabric)
        self.assertIn('if (qualificationFrozenCandidateJar == null)', self.fabric)
        self.assertIn('if (qualificationFrozenCandidateJar == null)', self.neoforge)
        self.assertIn('rename { "ringworld-qualification.jar" }', self.fabric)
        self.assertIn("rename { 'ringworld-qualification.jar' }", self.neoforge)
        for runtime in ('server', 'client-a', 'client-b'):
            self.assertIn(runtime, self.fabric)
        for runtime in ('neoForgeMultiplayerServerRun', 'neoForgeMultiplayerClientARun',
                        'neoForgeMultiplayerClientBRun'):
            self.assertIn(runtime, self.neoforge)
        self.assertIn('qualificationFrozenRuntimeSourceSet', self.fabric)
        self.assertIn('runtimeClasspath = configurations.runtimeClasspath', self.fabric)
        self.assertIn('qualificationFrozenRuntimeSourceSet', self.neoforge)
        self.assertIn("prepareNeoForgeRaidSeamTestWorld", self.neoforge)
        self.assertIn("neoForgeRaidSeamServerRun", self.neoforge)

    def test_fabric_metadata_uses_selected_minecraft_version(self) -> None:
        metadata = (ROOT / "src" / "main" / "resources" / "fabric.mod.json").read_text(encoding="utf-8")
        self.assertIn('"minecraft": "${minecraft_version}"', metadata)
        self.assertIn("expand version: project.version, minecraft_version: project.minecraft_version", self.fabric)


if __name__ == '__main__':
    unittest.main()
