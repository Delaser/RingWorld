"""Host-specific launch and timeout primitives for release qualification."""

from __future__ import annotations

import os
from pathlib import Path
import signal
import subprocess
import sys


def is_windows() -> bool:
    return sys.platform == "win32"


def gradle_argv(repository: Path) -> tuple[str, ...]:
    if is_windows():
        # Run the checked-in wrapper directly. Avoid cmd.exe/batch expansion
        # of spaces, percent signs and metacharacters in paths/properties.
        return ("java", "-classpath", str(repository / "gradle/wrapper/gradle-wrapper.jar"),
                "org.gradle.wrapper.GradleWrapperMain")
    return (str(repository / "gradlew"),)


def neoforge_windows_argv(version: str) -> tuple[str, ...]:
    return ("java", "@user_jvm_args.txt",
            f"@libraries/net/neoforged/neoforge/{version}/win_args.txt", "nogui")


def terminate_process_tree(process: subprocess.Popen, *, timeout: int = 5) -> None:
    if process.poll() is not None:
        return
    if is_windows():
        # /T includes Gradle's Java/Minecraft children; terminating only the
        # parent leaves locks, ports and log pipes held after a timeout.
        system_root = Path(os.environ.get("SystemRoot", r"C:\Windows"))
        try:
            subprocess.run(
                [str(system_root / "System32/taskkill.exe"), "/PID", str(process.pid), "/T", "/F"],
                check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, timeout=timeout,
            )
        except subprocess.CalledProcessError as error:
            # The process may exit between poll() and taskkill. Windows returns
            # 128 when the PID is no longer present; confirm that race before
            # accepting it. A live process or another cleanup error still fails.
            if error.returncode != 128 or process.poll() is None:
                raise
        process.wait(timeout=timeout)
        return
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=timeout)
