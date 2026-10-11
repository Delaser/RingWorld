"""Host launch contracts, argument preservation and Windows tree cleanup."""

import json
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import Mock, patch

import qualification_host as host


class QualificationHostTest(unittest.TestCase):
    def test_posix_keeps_existing_wrapper(self):
        with patch.object(host, "is_windows", return_value=False):
            self.assertEqual(host.gradle_argv(Path("/checkout")), ("/checkout/gradlew",))

    def test_windows_uses_reviewed_wrapper_without_command_interpreter(self):
        root = Path("checkout space %PATH% & Unicode \u2713")
        with patch.object(host, "is_windows", return_value=True):
            command = host.gradle_argv(root)
        self.assertEqual(command, ("java", "-classpath",
                         str(root / "gradle/wrapper/gradle-wrapper.jar"),
                         "org.gradle.wrapper.GradleWrapperMain"))
        arguments = (*command, "-Pfixture=space %PATH% & literal")
        # Exercise the real host's subprocess argument roundtrip, including
        # Windows CI, without needing Gradle downloads or a game runtime.
        result = subprocess.run([sys.executable, "-c",
            "import json,sys; print(json.dumps(sys.argv[1:]))", *arguments],
            check=True, capture_output=True, text=True)
        self.assertEqual(json.loads(result.stdout), list(arguments))

    def test_neoforge_windows_uses_installed_argument_files(self):
        self.assertEqual(host.neoforge_windows_argv("26.3.0.7-beta"), (
            "java", "@user_jvm_args.txt",
            "@libraries/net/neoforged/neoforge/26.3.0.7-beta/win_args.txt", "nogui"))

    def test_windows_timeout_kills_descendants_and_reaps_parent(self):
        process = Mock(pid=1234)
        process.poll.return_value = None
        with patch.object(host, "is_windows", return_value=True), \
                patch.object(host.subprocess, "run") as run:
            host.terminate_process_tree(process)
        self.assertEqual(run.call_args.args[0][1:], ["/PID", "1234", "/T", "/F"])
        self.assertTrue(run.call_args.kwargs["check"])
        process.wait.assert_called_once_with(timeout=5)

    def test_failed_windows_tree_cleanup_is_not_reported_as_success(self):
        process = Mock(pid=1234)
        process.poll.return_value = None
        with patch.object(host, "is_windows", return_value=True), \
                patch.object(host.subprocess, "run",
                             side_effect=subprocess.CalledProcessError(1, "taskkill")):
            with self.assertRaises(subprocess.CalledProcessError):
                host.terminate_process_tree(process)
        process.wait.assert_not_called()


if __name__ == "__main__":
    unittest.main()
