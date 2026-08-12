"""Pure tests for the Phase-3 quick-qualification model and CLI."""

from __future__ import annotations

import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
from contextlib import redirect_stderr, redirect_stdout
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
MODEL_PATH = ROOT / "scripts" / "minecraft_qualification_model.py"
RUNNER_PATH = ROOT / "scripts" / "run_minecraft_qualification.py"


def load(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    import sys
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


MODEL = load("minecraft_qualification_model", MODEL_PATH)
VALIDATOR = load("validate_minecraft_version_matrix", ROOT / "scripts" / "validate_minecraft_version_matrix.py")
RUNNER = load("run_minecraft_qualification", RUNNER_PATH)


class MinecraftQualificationModelTest(unittest.TestCase):
    def setUp(self) -> None:
        self.manifest = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        self.cell = self.manifest["cells"][0]

    def test_safe_path_tree_is_contained_under_evidence_and_rejects_traversal(self) -> None:
        paths = MODEL.QualificationPaths.from_cell(ROOT, self.cell, "run-1")
        self.assertTrue(MODEL.is_within(paths.cell_root, ROOT / "dist/qualification"))
        self.assertEqual("run-1", paths.run_id)
        with self.assertRaises(MODEL.InvocationError):
            MODEL.QualificationPaths.from_cell(ROOT, self.cell, "../escape")
        unsafe = copy.deepcopy(self.cell)
        unsafe["profile"]["evidence_directory"] = "dist/qualification/../escape"
        with self.assertRaises(MODEL.InvocationError):
            MODEL.QualificationPaths.from_cell(ROOT, unsafe, "safe")

    def test_selection_is_explicit_and_all_supported_only_includes_terminal_support(self) -> None:
        selected = MODEL.select_cells(self.manifest, ["26.1-fabric"])
        self.assertEqual(["26.1-fabric"], [cell["id"] for cell in selected])
        supported = MODEL.select_cells(self.manifest, all_supported=True)
        self.assertEqual({"26.1.2-fabric", "26.1.2-neoforge"}, {cell["id"] for cell in supported})
        with self.assertRaises(MODEL.InvocationError):
            MODEL.select_cells(self.manifest)
        with self.assertRaises(MODEL.InvocationError):
            MODEL.select_cells(self.manifest, ["26.1-fabric"], all_cells=True)

    def test_lock_reclaim_only_allows_dead_pid_on_the_same_host(self) -> None:
        lock = MODEL.LockSnapshot(44, "host-a", "run")
        self.assertEqual(MODEL.LockAction.RECLAIM, MODEL.decide_lock(lock, "host-a", lambda _: False).action)
        self.assertEqual(MODEL.LockAction.BLOCK, MODEL.decide_lock(lock, "host-a", lambda _: True).action)
        self.assertEqual(MODEL.LockAction.BLOCK, MODEL.decide_lock(lock, "host-b", lambda _: False).action)
        self.assertEqual(MODEL.LockAction.ACQUIRE, MODEL.decide_lock(None, "host-a", lambda _: True).action)

    def test_port_probe_is_injected_and_validated_without_binding(self) -> None:
        calls: list[int] = []
        self.assertTrue(MODEL.port_available(26101, lambda port: calls.append(port) is None))
        self.assertEqual([26101], calls)
        with self.assertRaises(MODEL.InvocationError):
            MODEL.port_available(0, lambda _: True)

    def test_checksum_verification_and_download_plans_never_fetch(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "sample.bin"
            target.write_bytes(b"ringworld")
            digest = hashlib.sha256(b"ringworld").hexdigest()
            evidence = MODEL.checksum_evidence(target, "sha256", digest)
            self.assertTrue(evidence.verified)
        paths = MODEL.QualificationPaths.from_cell(ROOT, self.cell, "run")
        plans = MODEL.download_plans(self.cell, paths)
        self.assertEqual(5, len(plans))
        self.assertTrue(all(plan.url.startswith("https://") for plan in plans))
        self.assertTrue(all(MODEL.is_within(plan.destination, paths.cache_directory) for plan in plans))

    @staticmethod
    def property_values(command) -> dict[str, list[str]]:
        values: dict[str, list[str]] = {}
        for argument in command.argv:
            if not argument.startswith("-P"):
                continue
            name, value = argument[2:].split("=", 1)
            values.setdefault(name, []).append(value)
        return values

    def test_command_plan_uses_only_reviewed_properties_for_every_manifest_cell(self) -> None:
        expected_loader_properties = {
            "fabric": {"loader_version", "loom_version", "fabric_api_version"},
            "neoforge": {"neoforge_version", "moddevgradle_version"},
        }
        allowed_common = {
            "ringQualificationRoot", "ringQualificationCell", "ringQualificationPort", "minecraft_version",
            "mod_version", "release_label",
        }
        prohibited_fragments = ("RunDirectory", "CacheDirectory", "BuildDirectory", "EvidenceDirectory", "ServerPort", "RunId")
        for cell in self.manifest["cells"]:
            paths = MODEL.QualificationPaths.from_cell(ROOT, cell, "run")
            commands = MODEL.planned_commands(cell, paths)
            self.assertEqual(2, len(commands), cell["id"])
            self.assertTrue(all(isinstance(command.argv, tuple) for command in commands), cell["id"])
            expected_names = allowed_common | expected_loader_properties[cell["loader"]]
            dependency_versions = {entry["coordinate"]: entry["version"] for entry in cell["dependencies"]}
            expected_versions = {
                "minecraft_version": cell["minecraft"]["version"],
                "ringQualificationRoot": str(paths.run_root),
                "ringQualificationCell": cell["id"],
                "ringQualificationPort": str(cell["profile"]["server_port"]),
                "mod_version": f"0.0.0-qualification+mc{cell['minecraft']['version']}",
                "release_label": f"qualification-{cell['id']}",
            }
            for coordinate, property_name in MODEL.DEPENDENCY_PROPERTIES[cell["loader"]]:
                expected_versions[property_name] = dependency_versions[coordinate]
            for command in commands:
                values = self.property_values(command)
                self.assertEqual(expected_names, set(values), cell["id"])
                self.assertTrue(all(len(value) == 1 for value in values.values()), cell["id"])
                self.assertEqual(expected_versions, {name: value[0] for name, value in values.items()}, cell["id"])
                self.assertEqual((), command.environment, cell["id"])
                self.assertFalse(any(fragment in argument for argument in command.argv for fragment in prohibited_fragments))
                if cell["minecraft"]["version"] != "26.1.2":
                    self.assertNotIn("26.1.2", "\0".join(command.argv), cell["id"])
            argv = "\n".join("\0".join(command.argv) for command in commands)
            self.assertIn(":test", argv)
            expected_smoke = (
                ":runQualificationSmokeServer" if cell["loader"] == "fabric"
                else ":neoforge:runQualificationSmokeServer"
            )
            self.assertIn(expected_smoke, argv)

    def test_required_dependency_properties_fail_closed_for_missing_or_duplicate_coordinates(self) -> None:
        missing = copy.deepcopy(self.cell)
        missing["dependencies"] = [
            dependency for dependency in missing["dependencies"]
            if dependency["coordinate"] != "net.fabricmc:fabric-loader"
        ]
        with self.assertRaises(MODEL.InvocationError):
            MODEL.required_dependency_properties(missing)
        duplicate = copy.deepcopy(self.cell)
        duplicate["dependencies"].append(copy.deepcopy(duplicate["dependencies"][0]))
        with self.assertRaises(MODEL.InvocationError):
            MODEL.required_dependency_properties(duplicate)

    def test_dry_run_is_deterministic_and_cannot_be_pass(self) -> None:
        report = MODEL.plan_matrix((self.cell,), ROOT, "dry-run", dry_run=True)
        self.assertEqual(MODEL.Verdict.INCOMPLETE, report.verdict)
        self.assertIn(MODEL.DRY_RUN_NO_EXECUTION, MODEL.render_json(report))
        self.assertEqual(MODEL.render_json(report), MODEL.render_json(report))
        self.assertEqual(MODEL.render_markdown(report), MODEL.render_markdown(report))

    def test_real_pre_adapter_report_is_explicitly_incomplete(self) -> None:
        report = MODEL.plan_matrix((self.cell,), ROOT, "planned", dry_run=False)
        self.assertEqual(MODEL.Verdict.INCOMPLETE, report.verdict)
        reasons = {phase.reason for phase in report.cells[0].phases}
        self.assertIn(MODEL.QUALIFICATION_EXECUTION_NOT_IMPLEMENTED, reasons)

    def test_frozen_models_reject_assignment(self) -> None:
        paths = MODEL.QualificationPaths.from_cell(ROOT, self.cell, "run")
        with self.assertRaises(Exception):
            paths.run_id = "changed"  # type: ignore[misc]


class MinecraftQualificationCliTest(unittest.TestCase):
    def call(self, argv: list[str]) -> tuple[int, str, str]:
        stdout, stderr = io.StringIO(), io.StringIO()
        with redirect_stdout(stdout), redirect_stderr(stderr):
            code = RUNNER.main(argv, repository_root=ROOT)
        return code, stdout.getvalue(), stderr.getvalue()

    def test_dry_run_validates_plans_and_returns_incomplete(self) -> None:
        code, stdout, stderr = self.call(["--tier", "quick", "--cell", "26.1-fabric", "--dry-run"])
        self.assertEqual(1, code)
        self.assertEqual("", stderr)
        self.assertIn("DRY_RUN_NO_EXECUTION", stdout)
        self.assertIn('"format": 1', stdout)

    def test_real_run_stops_before_gradle_with_execution_not_implemented_reason(self) -> None:
        code, stdout, stderr = self.call(["--tier", "quick", "--cell", "26.1-fabric"])
        self.assertEqual(1, code)
        self.assertEqual("", stderr)
        self.assertIn("QUALIFICATION_EXECUTION_NOT_IMPLEMENTED", stdout)

    def test_invocation_errors_are_exit_two(self) -> None:
        code, _, stderr = self.call(["--tier", "quick", "--all", "--all-supported", "--dry-run"])
        self.assertEqual(2, code)
        self.assertIn("select exactly one", stderr)


if __name__ == "__main__":
    unittest.main()
