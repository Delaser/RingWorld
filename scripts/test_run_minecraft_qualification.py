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
from unittest.mock import patch
import zipfile


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
# The runner imports executor primitives; register the local module explicitly
# so this test remains independent of how unittest discovers ``scripts/``.
LICENSE = load("verify_distribution_license", ROOT / "scripts" / "verify_distribution_license.py")
EXECUTOR = load("minecraft_qualification_executor", ROOT / "scripts" / "minecraft_qualification_executor.py")
RUNNER = load("run_minecraft_qualification", RUNNER_PATH)


class MinecraftQualificationModelTest(unittest.TestCase):
    def setUp(self) -> None:
        self.manifest = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        self.cell = self.manifest["cells"][0]

    def test_safe_path_tree_is_contained_under_evidence_and_rejects_traversal(self) -> None:
        paths = MODEL.QualificationPaths.from_cell(ROOT, self.cell, "run-1")
        self.assertTrue(MODEL.is_within(paths.cell_root, ROOT / "dist/qualification"))
        self.assertEqual("run-1", paths.run_id)
        second = MODEL.QualificationPaths.from_cell(ROOT, self.cell, "run-2")
        self.assertEqual(paths.lock_path, second.lock_path)
        self.assertTrue(MODEL.is_within(paths.lock_path, ROOT / "dist/qualification" / ".locks"))
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
        self.assertEqual(6, len(plans))
        self.assertEqual("Fabric Installer", plans[-1].name)
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
                self.assertEqual((("GRADLE_USER_HOME", str(paths.gradle_home)),), command.environment, cell["id"])
                self.assertIn("--no-daemon", command.argv, cell["id"])
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

    def test_pass_phase_without_evidence_is_rejected(self) -> None:
        with self.assertRaises(MODEL.InvocationError):
            MODEL.PhaseResult(MODEL.PhaseName.BUILD_AND_UNIT, MODEL.Verdict.PASS)


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

    def test_real_run_delegates_to_execution_state_machine_without_running_gradle_in_this_test(self) -> None:
        fake = MODEL.plan_matrix((self.manifest_cell(),), ROOT, "dry-run", dry_run=True)
        with patch.object(RUNNER, "execute_quick_matrix", return_value=fake) as execute:
            code, stdout, stderr = self.call(["--tier", "quick", "--cell", "26.1-fabric"])
        self.assertEqual(1, code)
        self.assertEqual("", stderr)
        self.assertIn("DRY_RUN_NO_EXECUTION", stdout)
        execute.assert_called_once()

    def manifest_cell(self):
        manifest = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        return manifest["cells"][0]

    def test_invocation_errors_are_exit_two(self) -> None:
        code, _, stderr = self.call(["--tier", "quick", "--all", "--all-supported", "--dry-run"])
        self.assertEqual(2, code)
        self.assertIn("select exactly one", stderr)

    def test_jobs_and_resume_fail_closed_before_execution(self) -> None:
        code, _, stderr = self.call(["--tier", "quick", "--cell", "26.1-fabric", "--jobs", "2"])
        self.assertEqual(2, code)
        self.assertIn("exactly 1", stderr)
        code, _, stderr = self.call(["--tier", "quick", "--cell", "26.1-fabric", "--resume"])
        self.assertEqual(2, code)
        self.assertIn("not implemented", stderr)

    def test_untracked_source_file_blocks_provenance_before_any_execution(self) -> None:
        with patch.object(RUNNER, "_checked_text", return_value="?? scripts/local-untracked.py\n") as checked:
            with self.assertRaises(EXECUTOR.QualificationExecutionError):
                RUNNER.collect_source_provenance(ROOT, ROOT / "config/minecraft-version-matrix.json")
        self.assertEqual(("git", "status", "--porcelain", "--untracked-files=all"), checked.call_args.args[0])


class MinecraftQualificationExecutionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.cell = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))["cells"][0]
        self.run_id = "20260812T120000Z-0123456789ab"

    @staticmethod
    def provenance(root: Path, manifest: Path):
        return RUNNER.SourceProvenance(
            commit="a" * 40,
            branch="codex/test",
            upstream="a" * 40,
            origin="https://github.com/Delaser/RingWorld.git",
            manifest_sha256="b" * 64,
            gradle_wrapper_sha256="c" * 64,
            java_version='openjdk version "25.0.1"',
        )

    @staticmethod
    def passing_adapter(context):
        return MODEL.PhaseResult(
            context.phase,
            MODEL.Verdict.PASS,
            commands=(context.command,) if context.command else (),
            evidence=(MODEL.EvidenceReference("fake", context.phase.value, "injected test evidence"),),
        )

    def write_diagnostic_jar(self, paths: Path, loader: str = "fabric") -> Path:
        jar = paths / loader / "libs" / "ringworld-qualification.jar"
        jar.parent.mkdir(parents=True)
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("LICENSE-RINGWORLD.txt", "Mozilla Public License Version 2.0\nMPL-2.0\n")
            archive.writestr(
                "ringworld-build.properties",
                f"artifactVersion=0.0.0-qualification+mc26.1\nreleaseLabel=qualification-26.1-fabric\n",
            )
            if loader == "fabric":
                archive.writestr("fabric.mod.json", json.dumps({
                    "id": "ringworld", "version": "0.0.0-qualification+mc26.1", "license": "MPL-2.0",
                    "depends": {"minecraft": "26.1"},
                }))
            else:
                archive.writestr("META-INF/neoforge.mods.toml", "\n".join((
                    'license="MPL-2.0"', '[[mods]]', 'modId="ringworld"',
                    'version="0.0.0-qualification+mc26.1"',
                    '[[dependencies.ringworld]]', 'modId="minecraft"', 'versionRange="[26.1]"',
                )))
        return jar

    def test_injected_adapters_write_immutable_cell_and_matrix_evidence_without_gradle(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = RUNNER.execute_quick_matrix(
                (self.cell,), root,
                run_id_factory=lambda: self.run_id,
                phase_adapters={phase: self.passing_adapter for phase in MODEL.PhaseName if phase not in {MODEL.PhaseName.MANIFEST_VALIDATION, MODEL.PhaseName.INPUT_PLAN}},
                provenance_provider=self.provenance,
            )
            self.assertEqual(MODEL.Verdict.PASS, report.verdict)
            cell = report.cells[0]
            self.assertTrue((cell.paths.evidence_directory / "cell-report.json").is_file())
            self.assertTrue((root / "dist" / "qualification" / "matrix" / self.run_id / "matrix-report.json").is_file())
            self.assertTrue(all(phase.evidence for phase in cell.phases if phase.verdict is MODEL.Verdict.PASS))
            self.assertIn(("GRADLE_USER_HOME", str(cell.paths.gradle_home)), next(phase for phase in cell.phases if phase.phase is MODEL.PhaseName.BUILD_AND_UNIT).commands[0].environment)

    def test_missing_adapter_is_incomplete_and_cannot_be_reported_as_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            report = RUNNER.execute_quick_matrix(
                (self.cell,), Path(temporary), run_id_factory=lambda: self.run_id,
                phase_adapters={MODEL.PhaseName.BUILD_AND_UNIT: self.passing_adapter},
                provenance_provider=self.provenance,
            )
            self.assertEqual(MODEL.Verdict.INCOMPLETE, report.verdict)
            self.assertIn(RUNNER.NO_PHASE_ADAPTER, {phase.reason for phase in report.cells[0].phases})

    def test_artifact_adapter_requires_one_isolated_mpl_diagnostic_jar_and_records_hash(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = MODEL.QualificationPaths.from_cell(root, self.cell, self.run_id)
            jar = self.write_diagnostic_jar(paths.build_directory)
            result = RUNNER.artifact_verify_adapter(RUNNER.PhaseAdapterContext(
                self.cell, paths, MODEL.PhaseName.ARTIFACT_VERIFY, None, 3,
            ))
            self.assertEqual(MODEL.Verdict.PASS, result.verdict)
            self.assertEqual(1, len(result.artifacts))
            self.assertEqual(str(jar), result.artifacts[0].path)
            self.assertTrue(result.artifacts[0].verified)
            self.assertEqual(64, len(result.artifacts[0].actual or ""))
            self.assertTrue(any(item.kind == "jar-license" for item in result.evidence))

    def test_artifact_adapter_rejects_ambiguous_build_output(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = MODEL.QualificationPaths.from_cell(root, self.cell, self.run_id)
            jar = self.write_diagnostic_jar(paths.build_directory)
            jar.with_name("another-runtime.jar").write_bytes(jar.read_bytes())
            result = RUNNER.artifact_verify_adapter(RUNNER.PhaseAdapterContext(
                self.cell, paths, MODEL.PhaseName.ARTIFACT_VERIFY, None, 3,
            ))
            self.assertEqual(MODEL.Verdict.FAIL, result.verdict)
            self.assertEqual("ARTIFACT_VERIFY_FAILED:PackageVerificationError", result.reason)

    def test_artifact_phase_is_wired_after_build_and_carries_cell_artifact_evidence(self) -> None:
        def build_creates_jar(context):
            self.write_diagnostic_jar(context.paths.build_directory)
            return MODEL.PhaseResult(
                context.phase, MODEL.Verdict.PASS,
                commands=(context.command,),
                evidence=(MODEL.EvidenceReference("fake-build", "jar", "test build produced isolated jar"),),
            )

        with tempfile.TemporaryDirectory() as temporary:
            report = RUNNER.execute_quick_matrix(
                (self.cell,), Path(temporary), run_id_factory=lambda: self.run_id,
                phase_adapters={
                    MODEL.PhaseName.BUILD_AND_UNIT: build_creates_jar,
                    MODEL.PhaseName.ARTIFACT_VERIFY: RUNNER.artifact_verify_adapter,
                },
                provenance_provider=self.provenance,
            )
            cell = report.cells[0]
            self.assertEqual(MODEL.Verdict.PASS, next(
                phase.verdict for phase in cell.phases if phase.phase is MODEL.PhaseName.ARTIFACT_VERIFY
            ))
            self.assertEqual(1, len(cell.artifacts))
            self.assertEqual(MODEL.Verdict.INCOMPLETE, report.verdict)

    def test_fail_fast_still_leaves_a_terminal_report_for_later_selected_cells(self) -> None:
        def fail_build(context):
            if context.phase is MODEL.PhaseName.BUILD_AND_UNIT:
                return MODEL.PhaseResult(
                    context.phase, MODEL.Verdict.FAIL, "FAKE_FAILURE",
                    commands=(context.command,),
                    evidence=(MODEL.EvidenceReference("fake", "build", "intentional failure"),),
                )
            return self.passing_adapter(context)

        second = copy.deepcopy(self.cell)
        second["id"] = "26.1-fabric-second"
        second["profile"]["evidence_directory"] = "dist/qualification/ringworld/26.1/fabric-second"
        second["profile"]["server_port"] = 26501
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = RUNNER.execute_quick_matrix(
                (self.cell, second), root, run_id_factory=lambda: self.run_id,
                phase_adapters={phase: fail_build for phase in MODEL.PhaseName if phase not in {MODEL.PhaseName.MANIFEST_VALIDATION, MODEL.PhaseName.INPUT_PLAN}},
                provenance_provider=self.provenance,
            )
            self.assertEqual(MODEL.Verdict.FAIL, report.verdict)
            self.assertEqual(RUNNER.CELL_ABORTED_AFTER_FAILURE, next(
                phase.reason for phase in report.cells[1].phases if phase.phase is MODEL.PhaseName.BUILD_AND_UNIT
            ))
            self.assertTrue((report.cells[1].paths.evidence_directory / "cell-report.json").is_file())


if __name__ == "__main__":
    unittest.main()
