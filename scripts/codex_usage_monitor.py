#!/usr/bin/env python3
"""Monitor the ChatGPT-backed Codex weekly quota without reading credentials."""

import argparse
import datetime as dt
import json
import os
from pathlib import Path
import plistlib
import selectors
import shutil
import subprocess
import sys
import time
from typing import Any, Dict, Iterable, List, Optional, Tuple


WEEKLY_WINDOW_MINUTES = 7 * 24 * 60
DEFAULT_PAUSE_THRESHOLD_PERCENT = 5.0
DEFAULT_INTERVAL_SECONDS = 300
LAUNCH_AGENT_LABEL = "com.andwhatnotstudio.ringworld-codex-usage"


class MonitorError(RuntimeError):
    """A safe, user-facing monitoring failure."""


def find_codex_binary(explicit: Optional[str] = None) -> str:
    candidates = [
        explicit,
        os.environ.get("CODEX_BIN"),
        shutil.which("codex"),
        "/Applications/ChatGPT.app/Contents/Resources/codex",
        str(Path.home() / ".local/bin/codex"),
    ]
    for candidate in candidates:
        if candidate and Path(candidate).is_file() and os.access(candidate, os.X_OK):
            return str(Path(candidate).resolve())
    raise MonitorError("Codex executable not found; set CODEX_BIN or pass --codex-bin.")


def send_message(process: subprocess.Popen, message: Dict[str, Any]) -> None:
    assert process.stdin is not None
    process.stdin.write(json.dumps(message, separators=(",", ":")) + "\n")
    process.stdin.flush()


def read_response(
    process: subprocess.Popen, request_id: int, timeout_seconds: float
) -> Dict[str, Any]:
    assert process.stdout is not None
    selector = selectors.DefaultSelector()
    selector.register(process.stdout, selectors.EVENT_READ)
    deadline = time.monotonic() + timeout_seconds
    try:
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise MonitorError("Timed out waiting for the Codex usage response.")
            if not selector.select(remaining):
                continue
            line = process.stdout.readline()
            if not line:
                raise MonitorError("Codex app-server ended before returning usage data.")
            try:
                message = json.loads(line)
            except json.JSONDecodeError:
                continue
            if message.get("id") != request_id:
                continue
            if "error" in message:
                error = message["error"]
                detail = error.get("message", "unknown app-server error") if isinstance(error, dict) else "unknown app-server error"
                raise MonitorError("Codex usage query failed: {}".format(detail))
            result = message.get("result")
            if not isinstance(result, dict):
                raise MonitorError("Codex usage query returned no result object.")
            return result
    finally:
        selector.close()


def query_rate_limits(codex_binary: str, timeout_seconds: float = 30.0) -> Dict[str, Any]:
    environment = os.environ.copy()
    # Query the signed-in ChatGPT allowance only; never select an API-key path.
    for variable in ("OPENAI_API_KEY", "AZURE_OPENAI_API_KEY", "OPENAI_ORG_ID", "OPENAI_PROJECT_ID"):
        environment.pop(variable, None)
    process = subprocess.Popen(
        [codex_binary, "app-server"], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, text=True, bufsize=1, env=environment,
    )
    try:
        send_message(process, {"method": "initialize", "id": 1, "params": {"clientInfo": {"name": "ringworld_usage_monitor", "title": "RingWorld Usage Monitor", "version": "1.0.0"}}})
        read_response(process, 1, timeout_seconds)
        send_message(process, {"method": "initialized", "params": {}})
        send_message(process, {"method": "account/rateLimits/read", "id": 2})
        return read_response(process, 2, timeout_seconds)
    finally:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)


def iter_limit_windows(result: Dict[str, Any]) -> Iterable[Dict[str, Any]]:
    buckets = result.get("rateLimitsByLimitId")
    bucket_items = buckets.items() if isinstance(buckets, dict) and buckets else []
    if not bucket_items:
        fallback = result.get("rateLimits")
        bucket_items = [("default", fallback)] if isinstance(fallback, dict) else []
    for bucket_key, bucket in bucket_items:
        if not isinstance(bucket, dict):
            continue
        limit_id = str(bucket.get("limitId") or bucket_key)
        for slot in ("primary", "secondary"):
            window = bucket.get(slot)
            if not isinstance(window, dict):
                continue
            duration, used, reset = window.get("windowDurationMins"), window.get("usedPercent"), window.get("resetsAt")
            if not isinstance(duration, (int, float)) or not isinstance(used, (int, float)):
                continue
            yield {"limitId": limit_id, "limitName": bucket.get("limitName"), "slot": slot,
                   "windowDurationMins": int(duration), "usedPercent": float(used),
                   "resetsAt": int(reset) if isinstance(reset, (int, float)) else None,
                   "planType": bucket.get("planType")}


def select_weekly_window(result: Dict[str, Any], weekly_window_minutes: int = WEEKLY_WINDOW_MINUTES) -> Dict[str, Any]:
    windows = list(iter_limit_windows(result))
    weekly = [window for window in windows if window["windowDurationMins"] == weekly_window_minutes]
    if not weekly:
        durations = sorted({window["windowDurationMins"] for window in windows})
        available = ", ".join(str(value) for value in durations) or "none"
        raise MonitorError("No {}-minute weekly quota window was returned; available window durations: {}.".format(weekly_window_minutes, available))
    return max(weekly, key=lambda window: window["usedPercent"])


def classify_remaining(remaining_percent: float, pause_threshold_percent: float) -> str:
    return "PAUSE" if remaining_percent <= pause_threshold_percent else "OK"


def build_status(window: Dict[str, Any], pause_threshold_percent: float) -> Dict[str, Any]:
    used = max(0.0, min(100.0, float(window["usedPercent"])))
    remaining = 100.0 - used
    return {
        "observedAt": int(time.time()), "state": classify_remaining(remaining, pause_threshold_percent),
        "limitId": window["limitId"], "limitName": window.get("limitName"), "slot": window["slot"],
        "windowDurationMins": window["windowDurationMins"], "usedPercent": used,
        "remainingPercent": remaining, "resetsAt": window.get("resetsAt"),
        "planType": window.get("planType"), "pauseThresholdPercent": pause_threshold_percent,
    }


def format_timestamp(timestamp: Optional[int]) -> str:
    if timestamp is None:
        return "unknown"
    return dt.datetime.fromtimestamp(timestamp).astimezone().strftime("%Y-%m-%d %H:%M %Z")


def format_status(status: Dict[str, Any]) -> str:
    state = status["state"]
    policy = "normal operation" if state == "OK" else "PAUSE ALL RINGWORLD WORK"
    return ("Codex weekly quota: {remaining:g}% remaining ({used:g}% used); resets {reset}; "
            "state {state} — {policy}.").format(remaining=status["remainingPercent"], used=status["usedPercent"], reset=format_timestamp(status.get("resetsAt")), state=state, policy=policy)


def read_previous_state(path: Path) -> Optional[str]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, OSError, json.JSONDecodeError):
        return None
    state = data.get("state")
    return str(state) if state else None


def write_status(path: Path, status: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(status, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(path)


def notify_macos(title: str, body: str) -> None:
    if sys.platform != "darwin":
        return
    subprocess.run(["/usr/bin/osascript", "-e", "on run argv", "-e", "display notification (item 2 of argv) with title (item 1 of argv)", "-e", "end run", title, body], check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def error_status(message: str, pause_threshold: float) -> Dict[str, Any]:
    return {"observedAt": int(time.time()), "state": "ERROR", "error": message, "pauseThresholdPercent": pause_threshold}


def run_check(args: argparse.Namespace) -> Tuple[Dict[str, Any], int]:
    status_path = Path(args.status_file).expanduser().resolve()
    previous_state = read_previous_state(status_path)
    try:
        result = query_rate_limits(find_codex_binary(args.codex_bin), args.timeout)
        status = build_status(select_weekly_window(result, args.weekly_window_minutes), args.pause_threshold)
        exit_code = {"OK": 0, "PAUSE": 20}[status["state"]]
        message = format_status(status)
    except MonitorError as exc:
        status, exit_code = error_status(str(exc), args.pause_threshold), 2
        message = "Codex weekly quota monitor error: {}".format(exc)
    write_status(status_path, status)
    if not args.quiet:
        print(message)
    if args.json:
        print(json.dumps(status, indent=2, sort_keys=True))
    if args.notify and status["state"] != "OK" and status["state"] != previous_state:
        notify_macos("RingWorld usage pause", message)
    return status, exit_code


def launch_agent_path() -> Path:
    return Path.home() / "Library" / "LaunchAgents" / "{}.plist".format(LAUNCH_AGENT_LABEL)


def application_support_path() -> Path:
    return Path.home() / "Library" / "Application Support" / "RingWorld"


def install_launch_agent(args: argparse.Namespace) -> int:
    if sys.platform != "darwin":
        raise MonitorError("The bundled background installer supports macOS only.")
    source_script, runtime_dir = Path(__file__).resolve(), application_support_path()
    runtime_dir.mkdir(parents=True, exist_ok=True)
    installed_script = runtime_dir / "codex_usage_monitor.py"
    shutil.copy2(str(source_script), str(installed_script))
    status_path, plist_path = runtime_dir / "codex-usage-status.json", launch_agent_path()
    plist_path.parent.mkdir(parents=True, exist_ok=True)
    interpreter = "/usr/bin/python3" if Path("/usr/bin/python3").is_file() else sys.executable
    plist = {"Label": LAUNCH_AGENT_LABEL, "ProgramArguments": [interpreter, str(installed_script), "--quiet", "--notify", "--launch-agent-run", "--status-file", str(status_path), "--pause-threshold", str(args.pause_threshold), "--weekly-window-minutes", str(args.weekly_window_minutes)], "WorkingDirectory": str(runtime_dir), "RunAtLoad": True, "StartInterval": int(args.interval), "ProcessType": "Background", "StandardOutPath": str(runtime_dir / "codex-usage-monitor.out.log"), "StandardErrorPath": str(runtime_dir / "codex-usage-monitor.err.log")}
    with plist_path.open("wb") as handle:
        plistlib.dump(plist, handle)
    domain = "gui/{}".format(os.getuid())
    subprocess.run(["/bin/launchctl", "bootout", domain, str(plist_path)], check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(["/bin/launchctl", "bootstrap", domain, str(plist_path)], check=True)
    print("Installed {} (checks every {} seconds; status {}).".format(plist_path, args.interval, status_path))
    return 0


def uninstall_launch_agent() -> int:
    if sys.platform != "darwin":
        raise MonitorError("The bundled background installer supports macOS only.")
    plist_path, domain = launch_agent_path(), "gui/{}".format(os.getuid())
    subprocess.run(["/bin/launchctl", "bootout", domain, str(plist_path)], check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for path in (plist_path, application_support_path() / "codex_usage_monitor.py"):
        try:
            path.unlink()
        except FileNotFoundError:
            pass
    print("Removed {}.".format(LAUNCH_AGENT_LABEL))
    return 0


def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    repo_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description="Read the ChatGPT-backed Codex weekly quota through the supported local app-server account endpoint.")
    parser.add_argument("--codex-bin", help="Explicit Codex executable path.")
    parser.add_argument("--status-file", default=str(repo_root / ".codex-tmp/codex-usage-status.json"), help="Machine-readable status output (default: project .codex-tmp).")
    parser.add_argument("--pause-threshold", type=float, default=DEFAULT_PAUSE_THRESHOLD_PERCENT, help="Remaining percentage at or below which RingWorld work pauses.")
    parser.add_argument("--weekly-window-minutes", type=int, default=WEEKLY_WINDOW_MINUTES, help="Expected weekly quota window duration.")
    parser.add_argument("--timeout", type=float, default=30.0, help="App-server response timeout.")
    parser.add_argument("--interval", type=int, default=DEFAULT_INTERVAL_SECONDS, help="Seconds between watch/LaunchAgent checks.")
    parser.add_argument("--watch", action="store_true", help="Check continuously.")
    parser.add_argument("--notify", action="store_true", help="Notify on PAUSE or ERROR.")
    parser.add_argument("--quiet", action="store_true", help="Suppress text status.")
    parser.add_argument("--json", action="store_true", help="Also print status JSON.")
    parser.add_argument("--launch-agent-run", action="store_true", help=argparse.SUPPRESS)
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument("--install-launch-agent", action="store_true", help="Install the five-minute macOS background monitor.")
    actions.add_argument("--uninstall-launch-agent", action="store_true", help="Remove the macOS background monitor.")
    args = parser.parse_args(argv)
    if not 0 <= args.pause_threshold <= 100:
        parser.error("--pause-threshold must be between 0 and 100")
    if args.interval < 60:
        parser.error("--interval must be at least 60 seconds")
    if args.weekly_window_minutes <= 0 or args.timeout <= 0:
        parser.error("window duration and timeout must be positive")
    return args


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)
    try:
        if args.install_launch_agent:
            return install_launch_agent(args)
        if args.uninstall_launch_agent:
            return uninstall_launch_agent()
        while True:
            _, exit_code = run_check(args)
            if not args.watch:
                return 0 if args.launch_agent_run else exit_code
            time.sleep(args.interval)
    except (MonitorError, OSError, subprocess.SubprocessError) as exc:
        print("Codex usage monitor error: {}".format(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
