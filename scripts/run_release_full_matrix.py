#!/usr/bin/env python3
"""Run all three full qualification groups serially, retaining every failure.

No publication, source edits, quick rebuilding, parallel clients or automatic
assertion waivers. A failed group does not prevent the other groups from running.
"""
from __future__ import annotations
import argparse
import json
from typing import Callable

from minecraft_qualification_executor import new_run_id, write_terminal_report
import run_minecraft_nightly_matrix as nightly

GROUPS = (
    ('26.1.x', 'config/minecraft-version-matrix.json', 'quick_26_1'),
    ('26.2', 'config/minecraft-version-matrix-26.2.json', 'quick_26_2'),
    ('26.3', 'config/minecraft-version-matrix-26.3.json', 'quick_26_3'),
)


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    for _, _, name in GROUPS:
        result.add_argument('--' + name.replace('_', '-'), required=True)
    result.add_argument('--production-world', required=True)
    for option in ('gradle-dependency-cache', 'gradle-distribution-zip', 'gradle-loom-cache'):
        result.add_argument('--' + option)
    result.add_argument('--execute', action='store_true')
    return result


def run_group(arguments: argparse.Namespace, manifest: str, quick_id: str) -> dict:
    child = nightly.parser().parse_args([
        '--manifest', manifest, '--quick-run-id', quick_id,
        '--production-world', arguments.production_world,
        *(['--execute'] if arguments.execute else []),
        *[value for name in ('gradle_dependency_cache', 'gradle_distribution_zip', 'gradle_loom_cache')
          if getattr(arguments, name, None) for value in ('--' + name.replace('_', '-'), getattr(arguments, name))],
    ])
    planned = nightly.plan(child)
    return nightly.execute(child, planned) if arguments.execute else planned


def run_groups(arguments: argparse.Namespace, *, executor: Callable = run_group,
               checkpoint: Callable | None = None) -> dict:
    results = []
    for label, manifest, quick in GROUPS:
        try:
            report = executor(arguments, manifest, getattr(arguments, quick))
            results.append({'group': label, 'report': report, 'verdict': report.get('verdict', 'INCOMPLETE')})
        except (nightly.QualificationExecutionError, OSError, ValueError) as error:
            results.append({'group': label, 'verdict': 'FAIL', 'reason': str(error)})
        if checkpoint is not None:
            checkpoint({'format': 1, 'mode': 'EXECUTED', 'verdict': 'INCOMPLETE',
                        'groups': list(results)}, len(results))
    commits = {r['report'].get('started_source_commit') for r in results if 'report' in r}
    same_source = len(commits) == 1 and None not in commits
    return {'format': 1, 'mode': 'EXECUTED' if arguments.execute else 'DRY_RUN',
            'verdict': ('PASS' if arguments.execute and same_source
                        and all(r['verdict'] == 'PASS' for r in results)
                        else 'FAIL' if arguments.execute else 'INCOMPLETE'), 'groups': results}


def main() -> int:
    arguments = parser().parse_args()
    run_id = new_run_id()
    evidence = nightly.ROOT / 'dist/qualification/release-full' / run_id

    def checkpoint(report: dict, count: int) -> None:
        write_terminal_report(evidence, {**report, 'run_id': run_id},
                              '# Full release matrix\n\nVerdict: INCOMPLETE\n',
                              stem=f'checkpoint-{count:02d}')

    result = run_groups(arguments, checkpoint=checkpoint if arguments.execute else None)
    if arguments.execute:
        result['run_id'] = run_id
        write_terminal_report(evidence,
                              result, '# Full release matrix\n\nVerdict: ' + result['verdict'] + '\n')
    print(json.dumps(result, sort_keys=True))
    return 0 if not arguments.execute or result['verdict'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
