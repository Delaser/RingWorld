#!/usr/bin/env python3
"""Pure filesystem/process tests for the qualification executor primitives."""

from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import time
import unittest
import zipfile

from minecraft_qualification_executor import (
    EvidenceError,
    LockError,
    MAX_LOG_BYTES,
    PackageVerificationError,
    QualificationLock,
    create_contained_directories,
    exact_runtime_jar,
    execute_command,
    inspect_runtime_jar,
    new_run_id,
    sanitized_environment,
    verify_pinned_file,
    write_terminal_report,
)
from minecraft_qualification_model import CommandRecord, PhaseName, QualificationPaths


ROOT = Path(__file__).resolve().parents[1]


def canonical_license_bytes() -> bytes:
    """Mirror the repository's LF-pinned executable licence on every host."""
    return (ROOT / "LICENSE").read_text(encoding="utf-8").replace("\r\n", "\n").encode("utf-8")


def paths_at(root: Path) -> QualificationPaths:
    cell = root / "dist" / "qualification" / "run" / "fabric-26.1"
    return QualificationPaths(
        repository_root=root,
        run_id="20260812T120000Z-0123456789ab",
        cell_id="fabric-26.1",
        run_root=cell.parent,
        cell_root=cell,
        gradle_home=cell / "gradle-home",
        run_directory=cell / "run",
        cache_directory=cell / "cache",
        build_directory=cell / "build",
        evidence_directory=cell / "evidence",
        logs_directory=cell / "logs",
        world_directory=cell / "world",
        lock_path=cell / ".qualification.lock",
    )


class QualificationExecutorTest(unittest.TestCase):
    def test_run_id_is_utc_and_unique(self) -> None:
        fixed = datetime(2026, 8, 12, 12, 34, 56, tzinfo=timezone.utc)
        first, second = new_run_id(fixed), new_run_id(fixed)
        self.assertRegex(first, r"^20260812T123456Z-[0-9a-f]{12}$")
        self.assertNotEqual(first, second)

    def test_lock_uses_a_held_os_lock_and_stale_metadata_is_advisory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "lock"
            run_id = "20260812T120000Z-0123456789ab"
            lock = QualificationLock.acquire(path, run_id, hostname="test-host", pid=12345)
            self.assertTrue(path.exists())
            lock.release()
            self.assertTrue(path.exists())
            path.write_text(json.dumps({"format": 1, "pid": 999999999, "hostname": "test-host", "run_id": run_id}), encoding="utf-8")
            reacquired = QualificationLock.acquire(path, run_id, hostname="test-host")
            reacquired.release()

    def test_lock_does_not_parse_or_reclaim_stale_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "lock"
            run_id = "20260812T120000Z-0123456789ab"
            path.write_text("not-json", encoding="utf-8")
            lock = QualificationLock.acquire(path, run_id, hostname="local")
            lock.release()
            self.assertIn('"run_id": "20260812T120000Z-0123456789ab"', path.read_text(encoding="utf-8"))

    def test_directories_and_sanitized_environment(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            paths = paths_at(Path(temporary))
            create_contained_directories(paths)
            self.assertTrue(paths.evidence_directory.is_dir())
            environment = sanitized_environment(
                (("JAVA_HOME", "/jdk"),),
                inherited={"GITHUB_TOKEN": "secret", "PATH": "/bin", "JAVA_HOME": "/old", "ODD": "no"},
            )
            self.assertEqual(environment, {"PATH": "/bin", "JAVA_HOME": "/jdk"})
            cache_environment = sanitized_environment(
                (("GRADLE_USER_HOME", "/cell/gradle-home"), ("GRADLE_RO_DEP_CACHE", "/worker/cache")),
                inherited={"GRADLE_RO_DEP_CACHE": "/untrusted/inherited", "PATH": "/bin"},
            )
            self.assertEqual(cache_environment, {
                "PATH": "/bin", "GRADLE_USER_HOME": "/cell/gradle-home", "GRADLE_RO_DEP_CACHE": "/worker/cache",
            })
            with self.assertRaises(Exception):
                sanitized_environment((("API_TOKEN", "nope"),), inherited={})

    def test_lock_and_contained_paths_work_with_windows_safe_spacing_and_unicode(self) -> None:
        """Exercise the real host Path/lock backend, including Windows CI."""
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "qualification space \u2713"
            paths = paths_at(root)
            create_contained_directories(paths)
            self.assertTrue(paths.logs_directory.is_dir())
            self.assertIn("qualification space \u2713", str(paths.cell_root))
            with QualificationLock.acquire(paths.lock_path, "20260812T120000Z-0123456789ab"):
                with self.assertRaises(LockError):
                    QualificationLock.acquire(paths.lock_path, "20260812T120001Z-0123456789ab")

    def test_subprocess_logs_success_failure_and_timeout(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = paths_at(root)
            passing = CommandRecord(
                PhaseName.BUILD_AND_UNIT, (sys.executable, "-c", "print('good')"), root, (), 5,
            )
            result = execute_command(passing, paths, ordinal=1)
            self.assertEqual("PASS", result.verdict.value)
            self.assertIn("good", Path(result.stdout_log).read_text(encoding="utf-8"))
            failing = CommandRecord(
                PhaseName.DEDICATED_SMOKE, (sys.executable, "-c", "raise SystemExit(9)"), root, (), 5,
            )
            result = execute_command(failing, paths, ordinal=2)
            self.assertEqual("FAIL", result.verdict.value)
            self.assertEqual("EXIT_9", result.reason)
            timeout = CommandRecord(
                PhaseName.DEDICATED_SMOKE, (sys.executable, "-c", "import time; time.sleep(1)"), root, (), 1,
            )
            result = execute_command(timeout, paths, ordinal=3)
            self.assertEqual("FAIL", result.verdict.value)
            self.assertEqual("TIMEOUT_AFTER_1_SECONDS", result.reason)

    def test_subprocess_logs_are_bounded_and_redacted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = paths_at(root)
            command = CommandRecord(
                PhaseName.BUILD_AND_UNIT,
                (sys.executable, "-c", "print('token=hunter2'); print('x' * (3 * 1024 * 1024))"),
                root,
                (),
                5,
            )
            result = execute_command(command, paths, ordinal=1)
            text = Path(result.stdout_log).read_text(encoding="utf-8")
            self.assertNotIn("hunter2", text)
            self.assertIn("<redacted>", text)
            self.assertLessEqual(Path(result.stdout_log).stat().st_size, MAX_LOG_BYTES)

    @unittest.skipIf(os.name == "nt", "process-group probe uses POSIX signal semantics")
    def test_timeout_terminates_descendant_process_group(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = paths_at(root)
            command = CommandRecord(
                PhaseName.DEDICATED_SMOKE,
                (
                    sys.executable,
                    "-c",
                    "import subprocess,sys,time; child=subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)']); print(child.pid, flush=True); time.sleep(30)",
                ),
                root,
                (),
                1,
            )
            result = execute_command(command, paths, ordinal=1)
            self.assertEqual("TIMEOUT_AFTER_1_SECONDS", result.reason)
            child_pid = int(Path(result.stdout_log).read_text(encoding="utf-8").strip())
            for _ in range(50):
                try:
                    os.kill(child_pid, 0)
                except ProcessLookupError:
                    break
                time.sleep(0.1)
            else:
                self.fail("timeout left the descendant process alive")

    def test_terminal_reports_are_immutable(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            evidence = Path(temporary) / "evidence"
            first = write_terminal_report(evidence, {"verdict": "PASS"}, "# PASS\n")
            self.assertTrue(first[0].is_file())
            with self.assertRaises(EvidenceError):
                write_terminal_report(evidence, {"verdict": "PASS"}, "# PASS\n")

    def test_pinned_file_hashes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "input.bin"
            path.write_bytes(b"ringworld")
            sha1 = hashlib.sha1(b"ringworld").hexdigest()
            sha256 = hashlib.sha256(b"ringworld").hexdigest()
            self.assertTrue(verify_pinned_file(path, "sha1", sha1).verified)
            self.assertTrue(verify_pinned_file(path, "sha256", sha256).verified)
            self.assertFalse(verify_pinned_file(path, "sha1", "0" * 40).verified)

    def test_exact_runtime_jar_rejects_sources_and_ambiguity(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with self.assertRaises(PackageVerificationError):
                exact_runtime_jar(root)
            (root / "ringworld.jar").write_bytes(b"jar")
            self.assertEqual(root / "ringworld.jar", exact_runtime_jar(root))
            (root / "ringworld-sources.jar").write_bytes(b"jar")
            with self.assertRaises(PackageVerificationError):
                exact_runtime_jar(root)

    def test_fabric_jar_inspection(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / "ringworld.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("LICENSE-RINGWORLD.txt", canonical_license_bytes())
                archive.writestr("ringworld-build.properties", "artifactVersion=0.0.0-qualification+mc26.1\nreleaseLabel=qualification-26.1-fabric\n")
                archive.writestr(
                    "fabric.mod.json",
                    json.dumps({
                        "id": "ringworld", "version": "0.0.0-qualification+mc26.1",
                        "license": "MPL-2.0", "depends": {"minecraft": "26.1"},
                    }),
                )
            inspected = inspect_runtime_jar(
                jar, loader="fabric", minecraft_version="26.1", diagnostic_version="0.0.0-qualification+mc26.1",
            )
            self.assertEqual("fabric.mod.json", inspected.metadata_entry)
            with zipfile.ZipFile(jar, "a") as archive:
                archive.writestr("META-INF/old.txt", "MIT")
            with self.assertRaises(PackageVerificationError):
                inspect_runtime_jar(jar, loader="fabric", minecraft_version="26.1", diagnostic_version="0.0.0-qualification+mc26.1")

    def test_neoforge_jar_inspection(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / "ringworld.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("LICENSE-RINGWORLD.txt", canonical_license_bytes())
                archive.writestr("ringworld-build.properties", "artifactVersion=0.0.0-qualification+mc26.1\nreleaseLabel=qualification-26.1-neoforge\n")
                archive.writestr(
                    "META-INF/neoforge.mods.toml",
                    'license="MPL-2.0"\n[[mods]]\nmodId="ringworld"\nversion="0.0.0-qualification+mc26.1"\n[[dependencies.ringworld]]\nmodId="minecraft"\nversionRange="[26.1]"\n',
                )
            inspected = inspect_runtime_jar(
                jar, loader="neoforge", minecraft_version="26.1", diagnostic_version="0.0.0-qualification+mc26.1",
            )
            self.assertEqual("META-INF/neoforge.mods.toml", inspected.metadata_entry)

    def test_jar_inspection_rejects_modified_mpl_text_even_when_label_remains(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / "ringworld.jar"
            altered = (ROOT / "LICENSE").read_text(encoding="utf-8") + "\nMPL-2.0\n"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("LICENSE-RINGWORLD.txt", altered)
                archive.writestr("ringworld-build.properties", "artifactVersion=0.0.0-qualification+mc26.1\nreleaseLabel=qualification-26.1-fabric\n")
                archive.writestr("fabric.mod.json", json.dumps({
                    "id": "ringworld", "version": "0.0.0-qualification+mc26.1",
                    "license": "MPL-2.0", "depends": {"minecraft": "26.1"},
                }))
            with self.assertRaises(PackageVerificationError):
                inspect_runtime_jar(
                    jar, loader="fabric", minecraft_version="26.1",
                    diagnostic_version="0.0.0-qualification+mc26.1",
                )

    def test_jar_inspection_rejects_duplicate_and_ambiguous_descriptors(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / "ringworld.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("LICENSE-RINGWORLD.txt", "MPL-2.0\n")
                archive.writestr("ringworld-build.properties", "artifactVersion=0.0.0-qualification+mc26.1\nreleaseLabel=qualification-26.1-fabric\n")
                archive.writestr("fabric.mod.json", json.dumps({"id": "ringworld", "version": "0.0.0-qualification+mc26.1", "license": "MPL-2.0", "depends": {"minecraft": "126.1"}}))
            with self.assertRaises(PackageVerificationError):
                inspect_runtime_jar(jar, loader="fabric", minecraft_version="26.1", diagnostic_version="0.0.0-qualification+mc26.1")
            with zipfile.ZipFile(jar, "a") as archive:
                archive.writestr("fabric.mod.json", "{}")
            with self.assertRaises(PackageVerificationError):
                inspect_runtime_jar(jar, loader="fabric", minecraft_version="26.1", diagnostic_version="0.0.0-qualification+mc26.1")


if __name__ == "__main__":
    unittest.main()
