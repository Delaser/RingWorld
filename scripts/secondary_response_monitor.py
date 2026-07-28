#!/usr/bin/env python3
"""Poll RingWorld GitHub issues for protocol-prefixed secondary-agent replies."""

import argparse
import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import sys
import time
from typing import Any, Dict, List, Optional, Tuple


DEFAULT_REPOSITORY = "Delaser/RingWorld"
DEFAULT_INTERVAL_SECONDS = 600
LAUNCH_AGENT_LABEL = "com.andwhatnotstudio.ringworld-secondary-responses"
SECONDARY_PREFIX = "[SECONDARY]"


class MonitorError(RuntimeError):
    """A safe, user-facing monitoring failure."""


def application_support_path() -> Path:
    return Path.home() / "Library" / "Application Support" / "RingWorld"


def default_status_path() -> Path:
    return application_support_path() / "secondary-response-status.json"


def launch_agent_path() -> Path:
    return (
        Path.home()
        / "Library"
        / "LaunchAgents"
        / "{}.plist".format(LAUNCH_AGENT_LABEL)
    )


def find_gh_binary(explicit: Optional[str] = None) -> str:
    candidates = [
        explicit,
        os.environ.get("GH_BIN"),
        shutil.which("gh"),
        "/opt/homebrew/bin/gh",
        "/usr/local/bin/gh",
    ]
    for candidate in candidates:
        if candidate and Path(candidate).is_file() and os.access(candidate, os.X_OK):
            return str(Path(candidate).resolve())
    raise MonitorError("GitHub CLI not found; install gh or pass --gh-bin.")


def github_environment() -> Dict[str, str]:
    environment = os.environ.copy()
    # Prefer the authenticated gh keyring entry. Do not make the monitor depend
    # on a token inherited from an interactive shell or desktop process.
    environment.pop("GH_TOKEN", None)
    environment.pop("GITHUB_TOKEN", None)
    environment["GH_PAGER"] = "cat"
    return environment


def fetch_issue_comments(gh_binary: str, repository: str) -> List[Dict[str, Any]]:
    endpoint = (
        "repos/{}/issues/comments?sort=created&direction=asc&per_page=100".format(
            repository
        )
    )
    completed = subprocess.run(
        [gh_binary, "api", "--paginate", "--slurp", endpoint],
        check=False,
        capture_output=True,
        text=True,
        env=github_environment(),
        timeout=60,
    )
    if completed.returncode != 0:
        detail = completed.stderr.strip().splitlines()
        safe_detail = detail[-1] if detail else "unknown gh error"
        raise MonitorError("GitHub issue query failed: {}".format(safe_detail))
    try:
        pages = json.loads(completed.stdout)
    except json.JSONDecodeError as exc:
        raise MonitorError("GitHub issue query returned invalid JSON.") from exc

    comments: List[Dict[str, Any]] = []
    if isinstance(pages, list):
        for page in pages:
            if isinstance(page, list):
                comments.extend(item for item in page if isinstance(item, dict))
            elif isinstance(page, dict):
                comments.append(page)
    return comments


def secondary_comments(comments: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    selected = []
    for comment in comments:
        body = comment.get("body")
        comment_id = comment.get("id")
        if not isinstance(body, str) or not body.lstrip().startswith(SECONDARY_PREFIX):
            continue
        if not isinstance(comment_id, int):
            continue
        issue_url = str(comment.get("issue_url") or "")
        try:
            issue_number = int(issue_url.rstrip("/").rsplit("/", 1)[-1])
        except (ValueError, IndexError):
            issue_number = None
        first_line = body.strip().splitlines()[0] if body.strip() else SECONDARY_PREFIX
        selected.append(
            {
                "id": comment_id,
                "issueNumber": issue_number,
                "createdAt": comment.get("created_at"),
                "updatedAt": comment.get("updated_at"),
                "url": comment.get("html_url"),
                "summary": first_line[:500],
            }
        )
    return sorted(selected, key=lambda item: item["id"])


def read_status(path: Path) -> Optional[Dict[str, Any]]:
    try:
        status = json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, OSError, json.JSONDecodeError):
        return None
    return status if isinstance(status, dict) else None


def write_status(path: Path, status: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(status, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    temporary.replace(path)


def build_polled_status(
    comments: List[Dict[str, Any]],
    previous: Optional[Dict[str, Any]],
    repository: str,
) -> Tuple[Dict[str, Any], bool]:
    latest_id = comments[-1]["id"] if comments else 0
    if previous is None or "lastAcknowledgedCommentId" not in previous:
        acknowledged_id = latest_id
        notified_id = latest_id
        is_initial_baseline = True
    else:
        acknowledged_id = int(previous.get("lastAcknowledgedCommentId") or 0)
        notified_id = int(previous.get("lastNotifiedCommentId") or 0)
        is_initial_baseline = False

    pending = [item for item in comments if item["id"] > acknowledged_id]
    has_new_notification = latest_id > notified_id and bool(pending)
    status = {
        "observedAt": int(time.time()),
        "repository": repository,
        "state": "RESPONSE_PENDING" if pending else "WAITING",
        "initialBaseline": is_initial_baseline,
        "lastPolledCommentId": latest_id,
        "lastAcknowledgedCommentId": acknowledged_id,
        "lastNotifiedCommentId": latest_id if has_new_notification else notified_id,
        "latestResponse": comments[-1] if comments else None,
        "pendingResponses": pending[-20:],
    }
    return status, has_new_notification


def acknowledge(status: Dict[str, Any]) -> Dict[str, Any]:
    latest_id = int(status.get("lastPolledCommentId") or 0)
    updated = dict(status)
    updated.update(
        {
            "observedAt": int(time.time()),
            "state": "WAITING",
            "lastAcknowledgedCommentId": latest_id,
            "lastNotifiedCommentId": max(
                latest_id, int(status.get("lastNotifiedCommentId") or 0)
            ),
            "pendingResponses": [],
        }
    )
    return updated


def notify_macos(title: str, body: str) -> None:
    if sys.platform != "darwin":
        return
    subprocess.run(
        [
            "/usr/bin/osascript",
            "-e",
            "on run argv",
            "-e",
            "display notification (item 2 of argv) with title (item 1 of argv)",
            "-e",
            "end run",
            title,
            body,
        ],
        check=False,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def format_status(status: Dict[str, Any]) -> str:
    pending = status.get("pendingResponses") or []
    latest = status.get("latestResponse")
    if pending:
        newest = pending[-1]
        return (
            "Secondary response pending: issue #{issue}, {summary} ({url})"
        ).format(
            issue=newest.get("issueNumber") or "?",
            summary=newest.get("summary") or SECONDARY_PREFIX,
            url=newest.get("url") or "no URL",
        )
    if isinstance(latest, dict):
        return (
            "Secondary monitor: waiting; latest response was issue #{issue} "
            "at {created}."
        ).format(
            issue=latest.get("issueNumber") or "?",
            created=latest.get("createdAt") or "unknown time",
        )
    return "Secondary monitor: waiting; no protocol-prefixed response found."


def error_status(
    repository: str,
    message: str,
    previous: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    # Preserve acknowledgement cursors across a temporary GitHub/keychain
    # failure so recovery cannot surface the entire historical backlog.
    status = dict(previous or {})
    status.update(
        {
            "observedAt": int(time.time()),
            "repository": repository,
            "state": "ERROR",
            "error": message,
        }
    )
    return status


def run_check(args: argparse.Namespace) -> int:
    status_path = Path(args.status_file).expanduser().resolve()
    previous = read_status(status_path)
    try:
        comments = secondary_comments(
            fetch_issue_comments(find_gh_binary(args.gh_bin), args.repository)
        )
        status, should_notify = build_polled_status(
            comments, previous, args.repository
        )
        message = format_status(status)
        exit_code = 10 if status["state"] == "RESPONSE_PENDING" else 0
    except (MonitorError, OSError, subprocess.SubprocessError) as exc:
        status = error_status(args.repository, str(exc), previous)
        should_notify = previous is None or previous.get("state") != "ERROR"
        message = "Secondary response monitor error: {}".format(exc)
        exit_code = 2

    write_status(status_path, status)
    if not args.quiet:
        print(message)
    if args.json:
        print(json.dumps(status, indent=2, sort_keys=True))
    if args.notify and should_notify:
        notify_macos("RingWorld secondary agent", message)
    return 0 if args.launch_agent_run else exit_code


def run_acknowledge(args: argparse.Namespace) -> int:
    status_path = Path(args.status_file).expanduser().resolve()
    status = read_status(status_path)
    if status is None:
        raise MonitorError("No secondary response status exists to acknowledge.")
    updated = acknowledge(status)
    write_status(status_path, updated)
    print("Acknowledged secondary responses through comment {}.".format(
        updated["lastAcknowledgedCommentId"]
    ))
    return 0


def install_launch_agent(args: argparse.Namespace) -> int:
    if sys.platform != "darwin":
        raise MonitorError("The bundled background installer supports macOS only.")
    runtime_dir = application_support_path()
    runtime_dir.mkdir(parents=True, exist_ok=True)
    installed_script = runtime_dir / "secondary_response_monitor.py"
    shutil.copy2(str(Path(__file__).resolve()), str(installed_script))
    status_path = default_status_path()
    plist_path = launch_agent_path()
    plist_path.parent.mkdir(parents=True, exist_ok=True)
    interpreter = (
        "/usr/bin/python3"
        if Path("/usr/bin/python3").is_file()
        else sys.executable
    )
    plist = {
        "Label": LAUNCH_AGENT_LABEL,
        "ProgramArguments": [
            interpreter,
            str(installed_script),
            "--quiet",
            "--notify",
            "--launch-agent-run",
            "--repository",
            args.repository,
            "--status-file",
            str(status_path),
        ],
        "WorkingDirectory": str(runtime_dir),
        "RunAtLoad": True,
        "StartInterval": int(args.interval),
        "ProcessType": "Background",
        "StandardOutPath": str(runtime_dir / "secondary-response-monitor.out.log"),
        "StandardErrorPath": str(runtime_dir / "secondary-response-monitor.err.log"),
    }
    with plist_path.open("wb") as handle:
        plistlib.dump(plist, handle)

    domain = "gui/{}".format(os.getuid())
    subprocess.run(
        ["/bin/launchctl", "bootout", domain, str(plist_path)],
        check=False,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    subprocess.run(
        ["/bin/launchctl", "bootstrap", domain, str(plist_path)], check=True
    )
    print(
        "Installed {} (checks every {} seconds; status {}).".format(
            plist_path, args.interval, status_path
        )
    )
    return 0


def uninstall_launch_agent() -> int:
    if sys.platform != "darwin":
        raise MonitorError("The bundled background installer supports macOS only.")
    plist_path = launch_agent_path()
    domain = "gui/{}".format(os.getuid())
    subprocess.run(
        ["/bin/launchctl", "bootout", domain, str(plist_path)],
        check=False,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    try:
        plist_path.unlink()
    except FileNotFoundError:
        pass
    try:
        (application_support_path() / "secondary_response_monitor.py").unlink()
    except FileNotFoundError:
        pass
    print("Removed {}.".format(LAUNCH_AGENT_LABEL))
    return 0


def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Poll RingWorld issue comments for replies using the existing "
            "[SECONDARY] coordination prefix."
        )
    )
    parser.add_argument("--repository", default=DEFAULT_REPOSITORY)
    parser.add_argument("--gh-bin", help="Explicit GitHub CLI executable path.")
    parser.add_argument("--status-file", default=str(default_status_path()))
    parser.add_argument(
        "--interval", type=int, default=DEFAULT_INTERVAL_SECONDS
    )
    parser.add_argument("--notify", action="store_true")
    parser.add_argument("--quiet", action="store_true")
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--launch-agent-run", action="store_true", help=argparse.SUPPRESS)
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument("--ack", action="store_true")
    actions.add_argument("--install-launch-agent", action="store_true")
    actions.add_argument("--uninstall-launch-agent", action="store_true")
    args = parser.parse_args(argv)
    if args.interval < 60:
        parser.error("--interval must be at least 60 seconds")
    if "/" not in args.repository:
        parser.error("--repository must use owner/name form")
    return args


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)
    try:
        if args.install_launch_agent:
            return install_launch_agent(args)
        if args.uninstall_launch_agent:
            return uninstall_launch_agent()
        if args.ack:
            return run_acknowledge(args)
        return run_check(args)
    except (MonitorError, OSError, subprocess.SubprocessError) as exc:
        print("Secondary response monitor error: {}".format(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
