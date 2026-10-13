"""Portable descriptive timing reports; never a qualification verdict or gate."""
from __future__ import annotations

from datetime import datetime, timedelta
import math
from pathlib import Path
import re
from typing import Any, Mapping, Sequence


def seconds(value: Any) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value < 0:
        return 0.0
    return float(value)


def span(commands: Sequence[Mapping[str, Any]]) -> float:
    """Union process intervals: two concurrent clients must not count twice."""
    intervals = []
    for command in commands:
        try:
            start = datetime.fromisoformat(command['started_at']).timestamp()
        except (KeyError, ValueError, TypeError):
            continue
        intervals.append((start, start + seconds(command.get('elapsed_seconds'))))
    end, total = float('-inf'), 0.0
    for start, stop in sorted(intervals):
        total += max(0.0, stop - max(start, end))
        end = max(end, stop)
    return total


def game_milestones(path: Path) -> dict[str, float]:
    """Observe first Atlas wait from bounded local game logs, without summing it."""
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 16 * 1024**2:
        return {}
    atlas_start = None
    previous, day = 0.0, 0.0
    for line in path.read_text(encoding='utf-8', errors='replace').splitlines():
        clock = re.match(r'\[(?:\d{2}[A-Za-z]{3}\d{4} )?(\d{2}):(\d{2}):(\d{2})(?:\.(\d{3}))?\]', line)
        if not clock:
            continue
        h, m, s = map(int, clock.group(1, 2, 3))
        value = h * 3600 + m * 60 + s + int(clock.group(4) or 0) / 1000
        if value + day < previous - 12 * 3600:
            day += 86400
        value += day
        previous = value
        if atlas_start is None and 'terrain atlas ready:' in line:
            atlas_start = value
        if atlas_start is not None and 'Textured ring surface ready:' in line and 'mesh=terrain-height' in line:
            return {'first_atlas_ready_to_detailed_seconds': max(0.0, value - atlas_start)}
    return {}


def startup_observation(command: Mapping[str, Any], root: Path) -> dict[str, Any]:
    value = command.get('output', command.get('stdout'))
    if not isinstance(value, str) or not isinstance(command.get('started_at_local'), str):
        return {}
    path = Path(value)
    if not path.is_absolute():
        path = root / path
    if (path.is_symlink() or not path.is_file()
            or not path.resolve().is_relative_to((root / 'dist/qualification').resolve())
            or path.stat().st_size > 16 * 1024**2):
        return {}
    try:
        start = datetime.fromisoformat(command['started_at_local'])
        if start.tzinfo is None:
            return {}
        with path.open(encoding='utf-8', errors='replace') as stream:
            text = stream.read(16 * 1024**2)
        for line in text.splitlines():
            if not ('(Minecraft)' in line or '[minecraft/' in line or '[net.minecraft.' in line):
                continue
            clock = re.match(r'\[(?:(\d{2})([A-Za-z]{3})(\d{4}) )?(\d{2}):(\d{2}):(\d{2})(?:\.(\d{3}))?\]', line)
            if not clock:
                continue
            day, month, year, h, m, s, millis = clock.groups()
            stamp = start.replace(hour=int(h), minute=int(m), second=int(s), microsecond=int(millis or 0) * 1000)
            if year:
                month_index = ('Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec').index(month) + 1
                stamp = stamp.replace(year=int(year), month=month_index, day=int(day))
            elif stamp < start - timedelta(hours=12):
                stamp += timedelta(days=1)
            delay = (stamp - start).total_seconds()
            if 0 <= delay <= seconds(command.get('elapsed_seconds')):
                return {'name': command.get('name'), 'first_game_log_delay_seconds': delay,
                        'after_first_game_log_seconds': seconds(command.get('elapsed_seconds')) - delay}
            return {}
    except (OSError, ValueError):
        return {}
    return {}


def fixture_timing(row: Mapping[str, Any], root: Path) -> dict[str, Any]:
    attempts = row.get('attempts') or [{'command': row.get('command', {})}]
    outer = sum(seconds(a.get('command', {}).get('elapsed_seconds')) for a in attempts)
    if row.get('reused_from'):
        passed = row.get('verdict') == 'PASS'
        return {'reused': passed, 'blocked_prior_failure': not passed, 'executed_seconds': 0.0,
                'prior_command_seconds_skipped': outer if passed else 0.0}
    child = row.get('child', {})
    if not isinstance(child, Mapping):
        child = {}
    commands = child.get('commands', [])
    if not isinstance(commands, list):
        commands = []
    if isinstance(child.get('command'), Mapping):
        commands = [*commands, child['command']]
    buckets: dict[str, list] = {'setup_assets': [], 'runtime_processes': [], 'verification': []}
    commands = [command for command in commands if isinstance(command, Mapping)]
    for command in commands:
        name = str(command.get('name', ''))
        if name in {'prepare', 'assets', 'prepare-and-assets'}:
            bucket = 'setup_assets'
        elif name == 'verify':
            bucket = 'verification'
        else:
            bucket = 'runtime_processes'
        buckets[bucket].append(command)
    waits = sum(bool(a.get('retry_scheduled')) for a in attempts) * 120
    measured = span(commands)
    logs = child.get('game_logs', [])
    if isinstance(child.get('game_log'), Mapping):
        logs = [*logs, child['game_log']]
    milestones = []
    logs = logs if isinstance(logs, list) else []
    for log in logs[:8]:
        if not isinstance(log, Mapping) or not isinstance(log.get('path'), str):
            continue
        path = Path(log['path'])
        if not path.is_absolute():
            path = root / path
        if path.resolve().is_relative_to((root / 'dist/qualification').resolve()):
            observed = game_milestones(path)
            if observed:
                milestones.append({'path': str(path), **observed})
    startup = [value for c in buckets['runtime_processes'] if (value := startup_observation(c, root))]
    return {'observed_startup_intervals': startup, 'reused': False, 'executed_seconds': outer + waits,
            'attempt_seconds': outer, 'retry_cooldown_seconds': waits,
            **{name + '_seconds': span(values) for name, values in buckets.items()},
            'unattributed_seconds': max(0.0, outer - measured),
            'configured_multiplayer_settle_seconds': row.get('post_prepare_settle_seconds', 0),
            'configured_raid_phase_settle_seconds': row.get('phase_settle_seconds', 0),
            'observed_game_milestones': milestones,
            'attempt_count': len(attempts)}


def summary(results: Sequence[Mapping[str, Any]], root: Path) -> dict[str, Any]:
    rows = [{'cell': row['cell'], 'fixture': row['fixture'], 'verdict': row['verdict'],
             **fixture_timing(row, root)} for row in results]
    return {'format': 1, 'fixtures': rows,
            'executed_seconds': sum(row['executed_seconds'] for row in rows),
            'reused_fixture_count': sum(row['reused'] for row in rows),
            'prior_command_seconds_skipped': sum(row.get('prior_command_seconds_skipped', 0) for row in rows),
            'limitations': ['Descriptive timings only; not qualification or measured cache speedup.',
                           'Runtime process time includes its Gradle startup; concurrent processes use interval unions.',
                           'First-game-log intervals include wrapper/setup and early game initialization, not pure game startup.',
                           'Unattributed time includes copying, verification overhead, intentional waits and unavailable failure details.',
                           'Configured settle values and observed Atlas intervals are not additional additive wall-time buckets.']}


def markdown(report: Mapping[str, Any]) -> str:
    lines = ['# Qualification timing', '', '| Cell | Fixture | Verdict | Executed seconds | Reused |',
             '| --- | --- | --- | ---: | --- |']
    for row in report['fixtures']:
        lines.append(f"| {row['cell']} | {row['fixture']} | {row['verdict']} | {row['executed_seconds']:.2f} | {row['reused']} |")
    return '\n'.join(lines) + '\n\n' + '\n'.join(report['limitations']) + '\n'
