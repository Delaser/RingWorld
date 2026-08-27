"""Pure tests for the Phase-3 quick-qualification model and CLI."""

from __future__ import annotations

import copy
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
from contextlib import redirect_stderr, redirect_stdout
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from types import SimpleNamespace


ROOT = Path(__file__).resolve().parents[1]


def canonical_license_bytes() -> bytes:
    """Mirror the repository's LF-pinned executable licence on every host."""
    return (ROOT / "LICENSE").read_text(encoding="utf-8").replace("\r\n", "\n").encode("utf-8")


class ExternalHomeTestCase(unittest.TestCase):
    """Keep disposable Windows CI paths outside the simulated operator home."""

    def setUp(self) -> None:
        super().setUp()
        self._home_patch = patch.object(
            RUNNER.Path, "home", return_value=ROOT.parent / ".qualification-test-operator-home",
        )
        self._home_patch.start()

    def tearDown(self) -> None:
        self._home_patch.stop()
        super().tearDown()


MODEL_PATH = ROOT / "scripts" / "minecraft_qualification_model.py"
RUNNER_PATH = ROOT / "scripts" / "run_minecraft_qualification.py"


def load(name: str, path: Path):
    # Keep one shared module identity under broad unittest discovery. Reloading
    # the model here would create incompatible copies of its exception and
    # dataclass types for tests imported earlier in the same process.
    import sys
    if name in sys.modules:
        return sys.modules[name]
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
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


def write_wrapper_seed_fixture(root: Path, payload: bytes = b"pinned wrapper zip") -> tuple[Path, Path]:
    """Create a disposable checked-in-wrapper analogue plus external ZIP seed."""
    repository = root / "repository"
    properties = repository / "gradle" / "wrapper" / "gradle-wrapper.properties"
    properties.parent.mkdir(parents=True)
    source = root / "worker" / "gradle-9.5.1-bin.zip"
    source.parent.mkdir(parents=True)
    source.write_bytes(payload)
    properties.write_text("\n".join((
        "distributionBase=GRADLE_USER_HOME",
        "distributionPath=wrapper/dists",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.5.1-bin.zip",
        f"distributionSha256Sum={hashlib.sha256(payload).hexdigest()}",
        "networkTimeout=10000",
        "retries=3",
        "retryBackOffMs=500",
        "validateDistributionUrl=true",
        "zipStoreBase=GRADLE_USER_HOME",
        "zipStorePath=wrapper/dists",
        "",
    )), encoding="ISO-8859-1")
    return repository, source


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
            self.assertEqual(1, len(commands), cell["id"])
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
                self.assertIn("--max-workers=1", command.argv, cell["id"])
                self.assertIn("--project-cache-dir", command.argv, cell["id"])
                self.assertFalse(any(fragment in argument for argument in command.argv for fragment in prohibited_fragments))
                if cell["minecraft"]["version"] != "26.1.2":
                    self.assertNotIn("26.1.2", "\0".join(command.argv), cell["id"])
            argv = "\n".join("\0".join(command.argv) for command in commands)
            self.assertIn(":test", argv)
            self.assertNotIn("runQualificationSmokeServer", argv)

    def test_optional_read_only_dependency_cache_is_explicit_for_both_loaders(self) -> None:
        cache = Path("/worker/gradle-read-only-cache")
        seen_loaders = set()
        for cell in self.manifest["cells"]:
            paths = MODEL.QualificationPaths.from_cell(ROOT, cell, "run")
            command = MODEL.planned_commands(cell, paths, gradle_dependency_cache=cache)[0]
            self.assertEqual(
                (("GRADLE_USER_HOME", str(paths.gradle_home)), ("GRADLE_RO_DEP_CACHE", str(cache))),
                command.environment,
            )
            self.assertNotIn("--offline", command.argv)
            self.assertFalse(any(argument.startswith("-Dorg.gradle.offline") for argument in command.argv))
            seen_loaders.add(cell["loader"])
        self.assertEqual({"fabric", "neoforge"}, seen_loaders)

    def test_optional_wrapper_distribution_seed_is_reported_without_altering_gradle_commands(self) -> None:
        seed = Path("/worker/gradle-9.5.1-bin.zip")
        for cell in self.manifest["cells"]:
            paths = MODEL.QualificationPaths.from_cell(ROOT, cell, "run")
            report = MODEL.plan_cell(
                cell, ROOT, "run", dry_run=True, gradle_distribution_zip=seed,
            )
            command = MODEL.planned_commands(cell, paths)[0]
            self.assertEqual(("GRADLE_USER_HOME", str(paths.gradle_home)), command.environment[0])
            self.assertNotIn("--offline", command.argv)
            input_phase = next(phase for phase in report.phases if phase.phase is MODEL.PhaseName.INPUT_PLAN)
            evidence = [item for item in input_phase.evidence if item.kind == "gradle-wrapper-distribution-zip"]
            self.assertEqual([str(seed)], [item.location for item in evidence])

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
        markdown = MODEL.render_markdown(report)
        self.assertEqual(markdown, MODEL.render_markdown(report))
        self.assertIn("Mode: `dry-run`  ", markdown)
        self.assertNotIn("Mode: `dry-run `", markdown)

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


class MinecraftQualificationCliTest(ExternalHomeTestCase):
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

    def test_gradle_dependency_cache_is_opt_in_safe_and_reported(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            cache = Path(temporary).resolve()
            code, stdout, stderr = self.call([
                "--tier", "quick", "--cell", "26.1-fabric", "--dry-run",
                "--gradle-dependency-cache", str(cache),
            ])
        self.assertEqual(1, code)
        self.assertEqual("", stderr)
        self.assertIn('"GRADLE_RO_DEP_CACHE"', stdout)
        self.assertIn("non-authoritative acceleration only", stdout)

    def test_gradle_dependency_cache_rejects_unsafe_paths_before_planning(self) -> None:
        with self.assertRaises(MODEL.InvocationError):
            RUNNER.validate_gradle_dependency_cache(Path("relative-cache"), ROOT)
        with self.assertRaises(MODEL.InvocationError):
            RUNNER.validate_gradle_dependency_cache(Path("/definitely-missing-ringworld-cache"), ROOT)
        with self.assertRaises(MODEL.InvocationError):
            RUNNER.validate_gradle_dependency_cache(ROOT, ROOT)
        with self.assertRaises(MODEL.InvocationError):
            RUNNER.validate_gradle_dependency_cache(ROOT / "dist", ROOT)
        with self.assertRaises(MODEL.InvocationError):
            RUNNER.validate_gradle_dependency_cache(Path.home().resolve(), ROOT)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            cache = root / "cache"
            cache.mkdir()
            self.assertEqual(cache, RUNNER.validate_gradle_dependency_cache(cache, root / "repository"))
            link = root / "cache-link"
            link.symlink_to(cache, target_is_directory=True)
            with self.assertRaises(MODEL.InvocationError):
                RUNNER.validate_gradle_dependency_cache(link, root / "repository")

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


class GradleDistributionSeedTest(ExternalHomeTestCase):
    def setUp(self) -> None:
        super().setUp()
        self.manifest = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        self.run_id = "20260812T120000Z-0123456789ab"

    def test_seed_requires_the_pinned_external_file_and_stages_exact_wrapper_target_for_both_loaders(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            repository, source = write_wrapper_seed_fixture(root)
            seed = RUNNER.validate_gradle_distribution_zip(source, repository)
            self.assertIsNotNone(seed)
            assert seed is not None
            self.assertEqual("iq79hdu3mqx29lgffhp8bfmx", seed.url_hash)
            self.assertEqual("gradle-9.5.1-bin.zip", seed.archive_name)
            for loader in ("fabric", "neoforge"):
                cell = next(item for item in self.manifest["cells"] if item["loader"] == loader)
                paths = MODEL.QualificationPaths.from_cell(repository, cell, self.run_id)
                EXECUTOR.create_contained_directories(paths)
                destination = RUNNER.stage_gradle_distribution_zip(source, repository, paths)
                self.assertEqual(
                    paths.gradle_home / "wrapper" / "dists" / "gradle-9.5.1-bin"
                    / "iq79hdu3mqx29lgffhp8bfmx" / "gradle-9.5.1-bin.zip",
                    destination,
                )
                self.assertEqual(source.read_bytes(), destination.read_bytes())
                self.assertFalse((destination.parent / "gradle-9.5.1-bin.zip.ok").exists())

    def test_seed_rejects_relative_repository_home_symlink_and_checksum_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            repository, source = write_wrapper_seed_fixture(root)
            with self.assertRaises(MODEL.InvocationError):
                RUNNER.validate_gradle_distribution_zip(Path("relative.zip"), repository)
            with self.assertRaises(MODEL.InvocationError):
                RUNNER.validate_gradle_distribution_zip(repository / "gradle/wrapper/gradle-wrapper.properties", repository)
            source.write_bytes(b"wrong bytes")
            with self.assertRaises(MODEL.InvocationError):
                RUNNER.validate_gradle_distribution_zip(source, repository)
            source.write_bytes(b"pinned wrapper zip")
            if os.name == "nt":
                return
            link = root / "worker" / "seed-link.zip"
            link.symlink_to(source)
            with self.assertRaises(MODEL.InvocationError):
                RUNNER.validate_gradle_distribution_zip(link, repository)
            linked_parent = root / "linked-worker"
            linked_parent.symlink_to(source.parent, target_is_directory=True)
            with self.assertRaises(MODEL.InvocationError):
                RUNNER.validate_gradle_distribution_zip(linked_parent / source.name, repository)

    def test_seed_is_rechecked_before_diagnostic_and_frozen_gradle_launches(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            repository, source = write_wrapper_seed_fixture(root)
            fabric = next(item for item in self.manifest["cells"] if item["id"] == "26.1-fabric")
            paths = MODEL.QualificationPaths.from_cell(repository, fabric, self.run_id)
            command = MODEL.planned_commands(fabric, paths)[0]

            def fake_diagnostic(command, received_paths, *, ordinal):
                destination = received_paths.gradle_home / "wrapper/dists/gradle-9.5.1-bin/iq79hdu3mqx29lgffhp8bfmx/gradle-9.5.1-bin.zip"
                self.assertTrue(destination.is_file())
                self.assertEqual(source.read_bytes(), destination.read_bytes())
                return SimpleNamespace(
                    verdict=MODEL.Verdict.PASS, stdout_log="diagnostic.stdout", stderr_log="diagnostic.stderr",
                    return_code=0, reason=None,
                )

            context = RUNNER.PhaseAdapterContext(
                fabric, paths, MODEL.PhaseName.BUILD_AND_UNIT, command, 1,
                gradle_distribution_zip=source,
            )
            with patch.object(RUNNER, "execute_command", side_effect=fake_diagnostic):
                result = RUNNER.build_and_unit_adapter(context)
            self.assertIs(MODEL.Verdict.PASS, result.verdict)

            calls = []

            def fake_frozen(command, received_paths, *, ordinal):
                destination = received_paths.gradle_home / "wrapper/dists/gradle-9.5.1-bin/iq79hdu3mqx29lgffhp8bfmx/gradle-9.5.1-bin.zip"
                self.assertTrue(destination.is_file())
                loader = "neoforge" if ":neoforge:test" in command.argv else "fabric"
                MinecraftQualificationExecutionTest.write_frozen_candidate(self, received_paths.build_directory, loader)
                calls.append(loader)
                return SimpleNamespace(
                    verdict=MODEL.Verdict.PASS, stdout_log="frozen.stdout", stderr_log="frozen.stderr", reason=None,
                )

            triplet = tuple(item for item in self.manifest["cells"] if item["loader"] in {"fabric", "neoforge"})
            with patch.object(RUNNER, "execute_command", side_effect=fake_frozen):
                prepared = RUNNER.prepare_frozen_candidates(
                    triplet, repository, "20260812T120001Z-0123456789ab", gradle_distribution_zip=source,
                )
            self.assertEqual(["fabric", "neoforge"], calls)
            self.assertTrue(all(item.verdict is MODEL.Verdict.PASS for item in prepared.values()))


class MinecraftQualificationExecutionTest(ExternalHomeTestCase):
    def setUp(self) -> None:
        super().setUp()
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
            archive.writestr("LICENSE-RINGWORLD.txt", canonical_license_bytes())
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

    def write_frozen_candidate(self, paths: Path, loader: str) -> Path:
        jar = paths / loader / "libs" / "ringworld-qualification.jar"
        jar.parent.mkdir(parents=True)
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("LICENSE-RINGWORLD.txt", (ROOT / "LICENSE").read_text(encoding="utf-8"))
            archive.writestr(
                "ringworld-build.properties",
                f"artifactVersion=0.0.0-qualification+mc26.1\nreleaseLabel=qualification-26.1-{loader}\n",
            )
            if loader == "fabric":
                archive.writestr("fabric.mod.json", json.dumps({
                    "id": "ringworld", "version": "0.0.0-qualification+mc26.1", "license": "MPL-2.0",
                    "depends": {"minecraft": ">=26.1 <=26.1.2"},
                }))
            else:
                archive.writestr("META-INF/neoforge.mods.toml", "\n".join((
                    'license="MPL-2.0"', '[[mods]]', 'modId="ringworld"',
                    'version="0.0.0-qualification+mc26.1"',
                    '[[dependencies.ringworld]]', 'modId="neoforge"',
                    'versionRange="[26.1.0.19-beta,26.1.2.87]"',
                    '[[dependencies.ringworld]]', 'modId="minecraft"', 'versionRange="[26.1,26.1.2]"',
                )))
        return jar

    def full_loader_triplet(self, loader: str):
        manifest = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        return tuple(cell for cell in manifest["cells"] if cell["loader"] == loader)

    def test_frozen_candidate_preparation_builds_once_and_shared_contract_reuses_one_path_hash(self) -> None:
        calls = []

        def fake_execute(command, paths, *, ordinal):
            calls.append((command, paths, ordinal))
            self.write_frozen_candidate(paths.build_directory, "fabric")
            return SimpleNamespace(
                verdict=MODEL.Verdict.PASS, stdout_log="frozen.stdout", stderr_log="frozen.stderr", reason=None,
            )

        with tempfile.TemporaryDirectory() as temporary, patch.object(RUNNER, "execute_command", side_effect=fake_execute):
            root = Path(temporary)
            cells = self.full_loader_triplet("fabric")
            preparations = RUNNER.prepare_frozen_candidates(cells, root, self.run_id)
            prepared = preparations["fabric"]
            self.assertEqual(MODEL.Verdict.PASS, prepared.verdict)
            self.assertEqual(1, len(calls))
            self.assertIn("--max-workers=1", calls[0][0].argv)
            self.assertTrue(prepared.plan.candidate_path.is_file())
            adapter = RUNNER.shared_contract_adapter(preparations)
            results = []
            for ordinal, cell in enumerate(cells, 1):
                paths = MODEL.QualificationPaths.from_cell(root, cell, self.run_id)
                results.append(adapter(RUNNER.PhaseAdapterContext(
                    cell, paths, MODEL.PhaseName.SHARED_CONTRACT, None, ordinal,
                )))
            self.assertTrue(all(item.verdict is MODEL.Verdict.PASS for item in results))
            self.assertTrue(all(item.artifacts == prepared.artifacts for item in results))
            self.assertEqual(1, len({item.artifacts[0].actual for item in results}))
            self.assertEqual(1, len({item.artifacts[0].path for item in results}))

    def test_frozen_candidate_plans_preserve_cell_homes_and_opt_in_cache_for_both_loaders(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            cache = root / "worker-cache"
            cache.mkdir()
            repository = root / "repository"
            repository.mkdir()
            cache = RUNNER.validate_gradle_dependency_cache(cache, repository)
            for loader in ("fabric", "neoforge"):
                source = next(cell for cell in self.full_loader_triplet(loader) if cell["id"] == f"26.1-{loader}")
                default_plan = RUNNER.frozen_candidate_plan(source, repository, self.run_id)
                self.assertEqual(
                    (("GRADLE_USER_HOME", str(default_plan.paths.gradle_home)),), default_plan.command.environment,
                )
                plan = RUNNER.frozen_candidate_plan(
                    source, repository, self.run_id, gradle_dependency_cache=cache,
                )
                self.assertEqual(
                    (("GRADLE_USER_HOME", str(plan.paths.gradle_home)), ("GRADLE_RO_DEP_CACHE", str(cache))),
                    plan.command.environment,
                )
                self.assertNotIn("--offline", plan.command.argv)

    def test_frozen_preparation_records_opt_in_cache_as_non_authoritative(self) -> None:
        def fake_execute(command, paths, *, ordinal):
            self.write_frozen_candidate(paths.build_directory, "fabric")
            return SimpleNamespace(verdict=MODEL.Verdict.PASS, stdout_log="out", stderr_log="err", reason=None)

        with tempfile.TemporaryDirectory() as temporary, patch.object(RUNNER, "execute_command", side_effect=fake_execute):
            root = Path(temporary).resolve()
            cache = root / "worker-cache"
            cache.mkdir()
            preparations = RUNNER.prepare_frozen_candidates(
                self.full_loader_triplet("fabric"), root / "repository", self.run_id,
                gradle_dependency_cache=RUNNER.validate_gradle_dependency_cache(cache, root / "repository"),
            )
        cache_evidence = [
            item for item in preparations["fabric"].evidence if item.kind == "gradle-ro-dependency-cache"
        ]
        self.assertEqual(1, len(cache_evidence))
        self.assertIn("non-authoritative", cache_evidence[0].detail)

    def test_frozen_preparation_revalidates_cache_at_execution_boundary(self) -> None:
        with tempfile.TemporaryDirectory() as temporary, patch.object(RUNNER, "execute_command") as execute:
            root = Path(temporary).resolve()
            repository = root / "repository"
            repository.mkdir()
            unsafe = repository / "cache"
            unsafe.mkdir()
            with self.assertRaises(MODEL.InvocationError):
                RUNNER.prepare_frozen_candidates(
                    self.full_loader_triplet("fabric"), repository, self.run_id,
                    gradle_dependency_cache=unsafe,
                )
        execute.assert_not_called()

    def test_frozen_preparation_rejects_cache_replaced_after_initial_validation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary, patch.object(RUNNER, "execute_command") as execute:
            root = Path(temporary).resolve()
            repository = root / "repository"
            repository.mkdir()
            cache = root / "cache"
            cache.mkdir()
            replacement = root / "replacement"
            replacement.mkdir()
            original_create = RUNNER.create_contained_directories

            def replace_after_create(paths):
                original_create(paths)
                cache.rmdir()
                cache.symlink_to(replacement, target_is_directory=True)

            with patch.object(RUNNER, "create_contained_directories", side_effect=replace_after_create):
                preparations = RUNNER.prepare_frozen_candidates(
                    self.full_loader_triplet("fabric"), repository, self.run_id,
                    gradle_dependency_cache=cache,
                )
        self.assertEqual(MODEL.Verdict.FAIL, preparations["fabric"].verdict)
        execute.assert_not_called()

    def test_partial_loader_selection_never_builds_a_frozen_candidate(self) -> None:
        with tempfile.TemporaryDirectory() as temporary, patch.object(RUNNER, "execute_command") as execute:
            root = Path(temporary)
            preparations = RUNNER.prepare_frozen_candidates((self.cell,), root, self.run_id)
            self.assertEqual(MODEL.Verdict.INCOMPLETE, preparations["fabric"].verdict)
            self.assertEqual(RUNNER.SHARED_CONTRACT_REQUIRES_FULL_LOADER_TRIPLET, preparations["fabric"].reason)
            execute.assert_not_called()

    def test_frozen_candidate_preparation_fails_when_retained_jar_is_not_licensed(self) -> None:
        def fake_execute(command, paths, *, ordinal):
            jar = paths.build_directory / "fabric" / "libs" / "ringworld-qualification.jar"
            jar.parent.mkdir(parents=True)
            jar.write_bytes(b"not a candidate")
            return SimpleNamespace(verdict=MODEL.Verdict.PASS, stdout_log="out", stderr_log="err", reason=None)

        with tempfile.TemporaryDirectory() as temporary, patch.object(RUNNER, "execute_command", side_effect=fake_execute):
            preparations = RUNNER.prepare_frozen_candidates(self.full_loader_triplet("fabric"), Path(temporary), self.run_id)
            self.assertEqual(MODEL.Verdict.FAIL, preparations["fabric"].verdict)
            self.assertTrue(preparations["fabric"].reason.startswith(RUNNER.FROZEN_CANDIDATE_PREPARATION_FAILED))

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

    def test_runner_lends_the_current_cell_lock_to_every_phase_adapter(self) -> None:
        seen = []

        def capture_lock(context):
            seen.append(context.held_lock)
            return self.passing_adapter(context)

        with tempfile.TemporaryDirectory() as temporary:
            RUNNER.execute_quick_matrix(
                (self.cell,), Path(temporary), run_id_factory=lambda: self.run_id,
                phase_adapters={
                    phase: capture_lock for phase in MODEL.PhaseName
                    if phase not in {MODEL.PhaseName.MANIFEST_VALIDATION, MODEL.PhaseName.INPUT_PLAN}
                },
                provenance_provider=self.provenance,
            )
        self.assertTrue(seen)
        self.assertTrue(all(lock is not None for lock in seen))

    def test_default_preparation_is_called_only_after_provenance_validation(self) -> None:
        order = []

        def provenance(root, manifest):
            order.append("provenance")
            return self.provenance(root, manifest)

        def frozen(cells, root, run_id):
            order.append("frozen")
            return {}

        with tempfile.TemporaryDirectory() as temporary, patch.object(RUNNER, "default_phase_adapters", return_value={}):
            RUNNER.execute_quick_matrix(
                (self.cell,), Path(temporary), run_id_factory=lambda: self.run_id,
                provenance_provider=provenance, frozen_preparation_provider=frozen,
            )
        self.assertEqual(["provenance", "frozen"], order)

    def test_cache_enabled_custom_preparation_provider_fails_closed_when_contract_is_old(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            repository = root / "repository"
            repository.mkdir()
            cache = root / "cache"
            cache.mkdir()
            with self.assertRaises(EXECUTOR.QualificationExecutionError):
                RUNNER.execute_quick_matrix(
                    (self.cell,), repository, run_id_factory=lambda: self.run_id,
                    provenance_provider=self.provenance,
                    frozen_preparation_provider=lambda cells, repo, run_id: {},
                    gradle_dependency_cache=cache,
                )

    def test_default_partial_selection_reaches_external_bridge_without_runtime_io(self) -> None:
        external = __import__("external_runtime_qualification_adapter")
        passing = {
            phase: self.passing_adapter for phase in (
                MODEL.PhaseName.BUILD_AND_UNIT,
                MODEL.PhaseName.ARTIFACT_VERIFY,
                MODEL.PhaseName.SHARED_CONTRACT,
            )
        }
        with tempfile.TemporaryDirectory() as temporary, \
                patch.object(RUNNER, "default_phase_adapters", return_value=passing), \
                patch.object(external, "execute_external_runtime_smoke") as runtime:
            report = RUNNER.execute_quick_matrix(
                (self.cell,), Path(temporary), run_id_factory=lambda: self.run_id,
                provenance_provider=self.provenance,
                frozen_preparation_provider=lambda cells, root, run_id: {},
            )
        runtime.assert_not_called()
        dedicated = next(
            phase for phase in report.cells[0].phases if phase.phase is MODEL.PhaseName.DEDICATED_SMOKE
        )
        self.assertEqual(MODEL.Verdict.INCOMPLETE, dedicated.verdict)
        self.assertEqual(external.FROZEN_CANDIDATE_UNAVAILABLE, dedicated.reason)

    def test_default_full_triplet_factory_receives_preparation_and_live_cell_locks(self) -> None:
        external = __import__("external_runtime_qualification_adapter")
        cells = self.full_loader_triplet("fabric")
        preparation = {"fabric": object()}
        factory_inputs, locks = [], []

        def factory(selected, provenance, preparations, *, contract):
            self.assertEqual(RUNNER.LEGACY_CONTRACT, contract)
            factory_inputs.append((tuple(selected), provenance, preparations))

            def dedicated(context):
                locks.append(context.held_lock)
                return self.passing_adapter(context)
            return dedicated

        passing = {
            phase: self.passing_adapter for phase in (
                MODEL.PhaseName.BUILD_AND_UNIT,
                MODEL.PhaseName.ARTIFACT_VERIFY,
                MODEL.PhaseName.SHARED_CONTRACT,
            )
        }
        with tempfile.TemporaryDirectory() as temporary, \
                patch.object(RUNNER, "default_phase_adapters", return_value=passing), \
                patch.object(external, "external_runtime_adapter_from_qualification_inputs", side_effect=factory):
            report = RUNNER.execute_quick_matrix(
                cells, Path(temporary), run_id_factory=lambda: self.run_id,
                provenance_provider=self.provenance,
                frozen_preparation_provider=lambda selected, root, run_id: preparation,
            )
        self.assertEqual(MODEL.Verdict.PASS, report.verdict)
        self.assertEqual(1, len(factory_inputs))
        self.assertEqual(cells, factory_inputs[0][0])
        self.assertIs(preparation, factory_inputs[0][2])
        self.assertEqual(3, len(locks))
        self.assertTrue(all(lock is not None for lock in locks))

    def test_failed_frozen_preparation_prevents_external_runtime_io(self) -> None:
        external = __import__("external_runtime_qualification_adapter")
        failure = RUNNER.FrozenCandidatePreparation(
            "fabric", MODEL.Verdict.FAIL, RUNNER.FROZEN_CANDIDATE_PREPARATION_FAILED,
        )
        passing = {
            phase: self.passing_adapter for phase in (
                MODEL.PhaseName.BUILD_AND_UNIT,
                MODEL.PhaseName.ARTIFACT_VERIFY,
            )
        }
        passing[MODEL.PhaseName.SHARED_CONTRACT] = RUNNER.shared_contract_adapter({"fabric": failure})
        with tempfile.TemporaryDirectory() as temporary, \
                patch.object(RUNNER, "default_phase_adapters", return_value=passing), \
                patch.object(external, "execute_external_runtime_smoke") as runtime:
            report = RUNNER.execute_quick_matrix(
                self.full_loader_triplet("fabric"), Path(temporary), run_id_factory=lambda: self.run_id,
                provenance_provider=self.provenance,
                frozen_preparation_provider=lambda cells, root, run_id: {"fabric": failure},
            )
        runtime.assert_not_called()
        self.assertEqual(MODEL.Verdict.FAIL, report.cells[0].verdict)

    def test_failed_complete_triplet_preflight_skips_all_per_cell_diagnostics_and_writes_reports(self) -> None:
        external = __import__("external_runtime_qualification_adapter")
        cells = self.full_loader_triplet("fabric")
        failure = RUNNER.FrozenCandidatePreparation(
            "fabric", MODEL.Verdict.FAIL, "FROZEN_CANDIDATE_PREPARATION_FAILED:BUILD_FAILED",
        )
        calls = []

        def unexpected_diagnostic(context):
            calls.append(context.phase)
            raise AssertionError("a failed frozen preflight must skip per-cell diagnostics")

        with tempfile.TemporaryDirectory() as temporary, \
                patch.object(RUNNER, "default_phase_adapters", return_value={
                    MODEL.PhaseName.BUILD_AND_UNIT: unexpected_diagnostic,
                    MODEL.PhaseName.ARTIFACT_VERIFY: unexpected_diagnostic,
                }), \
                patch.object(external, "external_runtime_adapter_from_qualification_inputs") as runtime_factory:
            report = RUNNER.execute_quick_matrix(
                cells, Path(temporary), run_id_factory=lambda: self.run_id,
                provenance_provider=self.provenance,
                frozen_preparation_provider=lambda selected, root, run_id: {"fabric": failure},
            )
            report_paths = [cell.paths.evidence_directory / "cell-report.json" for cell in report.cells]
            self.assertTrue(all(path.is_file() for path in report_paths))

        self.assertEqual([], calls)
        runtime_factory.assert_not_called()
        self.assertEqual(MODEL.Verdict.FAIL, report.verdict)
        first = report.cells[0]
        self.assertEqual("FROZEN_CANDIDATE_PREPARATION_FAILED:BUILD_FAILED", next(
            phase.reason for phase in first.phases if phase.phase is MODEL.PhaseName.SHARED_CONTRACT
        ))
        self.assertEqual(RUNNER.FROZEN_PREFLIGHT_ABORTED, next(
            phase.reason for phase in first.phases if phase.phase is MODEL.PhaseName.BUILD_AND_UNIT
        ))
        self.assertEqual(RUNNER.FROZEN_PREFLIGHT_ABORTED, next(
            phase.reason for phase in first.phases if phase.phase is MODEL.PhaseName.ARTIFACT_VERIFY
        ))
        self.assertEqual(RUNNER.CELL_ABORTED_AFTER_FAILURE, next(
            phase.reason for phase in first.phases if phase.phase is MODEL.PhaseName.DEDICATED_SMOKE
        ))
        for cell in report.cells[1:]:
            self.assertEqual(RUNNER.CELL_ABORTED_AFTER_FAILURE, next(
                phase.reason for phase in cell.phases if phase.phase is MODEL.PhaseName.BUILD_AND_UNIT
            ))

    def test_partial_triplet_keeps_per_cell_diagnostics_after_incomplete_preflight(self) -> None:
        calls = []

        def build(context):
            calls.append(context.phase)
            return self.passing_adapter(context)

        incomplete = RUNNER.FrozenCandidatePreparation(
            "fabric", MODEL.Verdict.INCOMPLETE, RUNNER.SHARED_CONTRACT_REQUIRES_FULL_LOADER_TRIPLET,
        )
        with tempfile.TemporaryDirectory() as temporary, patch.object(
                RUNNER, "default_phase_adapters", return_value={MODEL.PhaseName.BUILD_AND_UNIT: build}):
            report = RUNNER.execute_quick_matrix(
                (self.cell,), Path(temporary), run_id_factory=lambda: self.run_id,
                provenance_provider=self.provenance,
                frozen_preparation_provider=lambda selected, root, run_id: {"fabric": incomplete},
            )

        self.assertEqual([MODEL.PhaseName.BUILD_AND_UNIT], calls)
        self.assertEqual(MODEL.Verdict.INCOMPLETE, report.verdict)
        self.assertNotIn(RUNNER.FROZEN_PREFLIGHT_ABORTED, {
            phase.reason for phase in report.cells[0].phases
        })

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

    def test_artifact_adapter_allows_gradles_direct_sources_jar_but_not_a_second_runtime_jar(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = MODEL.QualificationPaths.from_cell(root, self.cell, self.run_id)
            jar = self.write_diagnostic_jar(paths.build_directory)
            jar.with_name("ringworld-qualification-sources.jar").write_bytes(b"normal Gradle sources output")
            result = RUNNER.artifact_verify_adapter(RUNNER.PhaseAdapterContext(
                self.cell, paths, MODEL.PhaseName.ARTIFACT_VERIFY, None, 3,
            ))
            self.assertEqual(MODEL.Verdict.PASS, result.verdict)

    def test_artifact_adapter_rejects_unrelated_or_multiple_sources_jars(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = MODEL.QualificationPaths.from_cell(root, self.cell, self.run_id)
            jar = self.write_diagnostic_jar(paths.build_directory)
            jar.with_name("unrelated-sources.jar").write_bytes(b"unexpected")
            result = RUNNER.artifact_verify_adapter(RUNNER.PhaseAdapterContext(
                self.cell, paths, MODEL.PhaseName.ARTIFACT_VERIFY, None, 3,
            ))
            self.assertEqual(MODEL.Verdict.FAIL, result.verdict)

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
