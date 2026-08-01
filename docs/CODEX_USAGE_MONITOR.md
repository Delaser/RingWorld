# Codex usage pause monitor

The primary RingWorld development machine monitors the ChatGPT-backed Codex
weekly allowance through the supported local Codex app-server. The monitor
never opens `~/.codex/auth.json`, reads tokens, or prints credentials.

RingWorld has one allowance guardrail: at **20% remaining or below**, the
monitor reports `PAUSE` and all RingWorld work pauses. Do not dispatch new
tasks at or below that threshold. Active work stops at the next safe handoff;
do not resume below the threshold without explicit owner authorization. Above
20%, the monitor reports `OK` and normal operation may continue.

The app-server reports `usedPercent`, `windowDurationMins`, and `resetsAt`.
The monitor accepts only the exact 10,080-minute weekly window and fails
visibly instead of treating a shorter window as the weekly allowance. If more
than one weekly bucket is returned, the most depleted bucket controls.

## One-shot check

From the repository root:

```sh
python3 scripts/codex_usage_monitor.py
```

At the threshold, the concise text output includes:

```text
state PAUSE — PAUSE ALL RINGWORLD WORK.
```

The generated machine-readable status is:

```text
.codex-tmp/codex-usage-status.json
```

`.codex-tmp/` is ignored. The status contains percentages, window length,
local observation/reset timestamps, and state, but no authentication material.

Unit tests:

```sh
python3 scripts/test_codex_usage_monitor.py
```

## macOS background check

Install a per-user LaunchAgent that checks every five minutes and sends a
notification when the state changes to `PAUSE` or `ERROR`:

```sh
python3 scripts/codex_usage_monitor.py --install-launch-agent
```

It installs:

```text
~/Library/LaunchAgents/com.andwhatnotstudio.ringworld-codex-usage.plist
```

Because macOS background agents cannot execute files from a protected
`Documents` folder without interactive privacy approval, installation copies
the monitor only to:

```text
~/Library/Application Support/RingWorld/codex_usage_monitor.py
```

Its background status and logs live beside that runtime copy. The tracked
source remains under `scripts/`, and reinstalling refreshes the runtime copy.
Remove it with:

```sh
python3 scripts/codex_usage_monitor.py --uninstall-launch-agent
```

The background status is advisory. Before a substantial task and after a long
tool-heavy milestone, the primary agent must run a fresh one-shot check. A
background process cannot interrupt a ChatGPT turn already in flight.

The secondary agent is on a separate ChatGPT plan and must check its own
allowance on its dedicated PC; the primary machine's result does not represent
the secondary account.
