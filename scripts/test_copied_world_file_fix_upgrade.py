"""Static guardrails for the opt-in copied-world file-fix fixture helper."""

from __future__ import annotations

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class CopiedWorldFileFixUpgradeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.helper = (ROOT / "src/client/java/dev/ringworld/client/CopiedWorldFileFixUpgrade.java") \
            .read_text(encoding="utf-8")
        cls.projection = (ROOT / "src/client/java/dev/ringworld/client/RingProjectionCaptureClient.java") \
            .read_text(encoding="utf-8")
        cls.parity = (ROOT / "src/client/java/dev/ringworld/client/RingVisualParityCaptureClient.java") \
            .read_text(encoding="utf-8")
        cls.lifecycle = (ROOT / "src/client/java/dev/ringworld/client/ProductionLifecycleTestClient.java") \
            .read_text(encoding="utf-8")
        cls.accessor = (ROOT / "src/client/java/dev/ringworld/client/mixin/ConfirmScreenAccessor.java") \
            .read_text(encoding="utf-8")

    def test_only_file_fix_then_completion_sequence_is_accepted(self) -> None:
        for key in (
            "selectWorld.backupQuestion.file_fixing_required",
            "selectWorld.backupJoinConfirmButton",
            "upgradeWorld.done",
            "upgradeWorld.joinNow",
        ):
            self.assertIn(key, self.helper)
        self.assertIn("acceptedFileFixBackup && isFileFixCompletion", self.helper)
        self.assertNotIn("backupQuestion.downgrade", self.helper)
        self.assertNotIn("backupQuestion.snapshot", self.helper)
        self.assertNotIn("backupQuestion.experimental", self.helper)

    def test_automation_requires_an_existing_fixture_and_ready_visible_buttons(self) -> None:
        for property_name in (
            "ringworld.captureRingProjection",
            "ringworld.captureRingVisualParity",
            "ringworld.productionLifecycleTest",
        ):
            self.assertIn(property_name, self.helper)
        self.assertIn("if (!fixtureEnabled()) return false", self.helper)
        self.assertIn("backup == null || !backup.isActive() || !backup.visible", self.helper)
        self.assertIn("joinNow == null || !joinNow.isActive() || !joinNow.visible", self.helper)

    def test_no_version_specific_screen_linkage_is_needed(self) -> None:
        self.assertIn('"net.minecraft.client.gui.screens.BackupConfirmScreen"', self.helper)
        self.assertNotIn("import net.minecraft.client.gui.screens.BackupConfirmScreen", self.helper)
        self.assertIn('@Accessor("message")', self.accessor)

    def test_all_copied_world_fixtures_use_helper_and_timeout_screen_detail(self) -> None:
        for source in (self.projection, self.parity, self.lifecycle):
            self.assertIn("CopiedWorldFileFixUpgrade", source)
            self.assertIn("CopiedWorldFileFixUpgrade.currentScreen(client)", source)
        self.assertIn("initialOpenRequested || stage == 6", self.lifecycle)


if __name__ == "__main__":
    unittest.main()
