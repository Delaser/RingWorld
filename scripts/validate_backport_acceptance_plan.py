#!/usr/bin/env python3
"""Validate planning coverage only; never launch Minecraft or certify a build."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PLAN = ROOT / 'config/backport-1.4-acceptance-plan.json'
CORE_FIXTURES = frozenset({
    'creation-ui', 'worldgen', 'atlas-recovery', 'atlas-ui', 'multiplayer',
    'raid', 'map-compass', 'production-lifecycle', 'curved-objects', 'production-render',
})
EXTENSIONS = frozenset({
    'atlas-concurrency-checkpoints', 'scheduled-save-lighting',
    'exterior-building-visibility', 'projection-sky-wall-water',
    'version-upgrade-rejection', 'memory-performance-soak', 'render-core-isolation',
})
CELLS = {
    '1.21.1-fabric': ('1.21.1', 'fabric', 21),
    '1.21.1-neoforge': ('1.21.1', 'neoforge', 21),
    '1.20.1-fabric': ('1.20.1', 'fabric', 17),
    '1.20.1-forge': ('1.20.1', 'forge', 17),
}
PRIMARY_FEATURE_GATES = {
    'F01': {'atlas-recovery', 'atlas-concurrency-checkpoints'},
    'F02': {'atlas-concurrency-checkpoints', 'multiplayer'},
    'F03': {'atlas-concurrency-checkpoints'},
    'F04': {'scheduled-save-lighting', 'memory-performance-soak'},
    'F05': {'scheduled-save-lighting', 'memory-performance-soak'},
    'F06': {'atlas-ui', 'exterior-building-visibility'},
    'F07': {'exterior-building-visibility', 'multiplayer'},
    'F08': {'projection-sky-wall-water', 'curved-objects'},
    'F09': {'projection-sky-wall-water', 'production-render'},
    'F10': {'projection-sky-wall-water', 'production-render'},
    'F11': {'projection-sky-wall-water', 'production-render'},
    'F12': {'atlas-concurrency-checkpoints', 'projection-sky-wall-water'},
    'F13': {'render-core-isolation'},
    'F14': {'projection-sky-wall-water'},
    'F15': {'creation-ui', 'render-core-isolation'},
    'F16': CORE_FIXTURES | {'version-upgrade-rejection'},
}


class PlanError(ValueError):
    pass


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise PlanError(message)


def _exact_unique_list(value: object, expected: frozenset[str], label: str) -> None:
    _require(isinstance(value, list) and all(isinstance(v, str) for v in value),
             f'{label} must be a list of names')
    _require(len(value) == len(set(value)) and set(value) == expected,
             f'{label} has missing, duplicate or unknown entries')


def validate(plan: dict) -> dict:
    _require(isinstance(plan, dict), 'plan must be an object')
    _require(type(plan.get('schemaVersion')) is int and plan['schemaVersion'] == 1
             and plan.get('kind') == 'planning-only',
             'expected planning-only schema 1')
    _require(plan.get('release') == '1.4', 'unexpected release')
    _require(plan.get('upstreamRevision') == '1533878fe3a6eab7562830b3f39cbd7caae6d46a',
             'plan must target the published replacement source')
    _require(bool(re.fullmatch('[0-9a-f]{40}', str(plan.get('baselineRevision', '')))),
             'baseline revision must be immutable')
    _require(plan.get('executionReady') is False and plan.get('qualificationStatus') == 'NOT_RUN',
             'planning validation cannot claim runtime readiness or qualification')
    _exact_unique_list(plan.get('coreFixtures'), CORE_FIXTURES, 'core fixtures')
    _exact_unique_list(plan.get('requiredExtensions'), EXTENSIONS, 'extensions')
    cells = plan.get('cells')
    _require(isinstance(cells, list) and all(isinstance(c, dict) for c in cells), 'cells must be objects')
    ids = [cell.get('id') for cell in cells]
    _require(all(isinstance(v, str) for v in ids), 'cell IDs must be names')
    _require(len(ids) == len(set(ids)) and set(ids) == set(CELLS),
             'expected the four exact Minecraft/loader cells')
    for cell in cells:
        _require(type(cell.get('javaMajor')) is int
                 and (cell.get('minecraft'), cell.get('loader'), cell.get('javaMajor')) == CELLS[cell['id']],
                 f'wrong game, loader or Java for {cell["id"]}')
        _require(cell.get('status') == 'NOT_RUN' and cell.get('runtimeManifest') is None,
                 'this plan cannot manufacture runtime results or executable manifests')
    contracts = plan.get('contracts', {})
    _require(isinstance(contracts, dict), 'contracts must be an object')
    for key, value in {'settingsFormat': 5, 'terrainNoiseMapping': 4,
                       'atlasFormat': 11, 'atlasSourceStep': 1,
                       'maximumAtlasCells': 16_777_216}.items():
        _require(type(contracts.get(key)) is int and contracts[key] == value,
                 f'wrong data contract: {key}')
    for key in ('preserveLocalRecaptureFairness', 'requireNonblockingChunkAcquisition',
                'requireExactPackagedCandidates', 'requireIndependentDiskEvidence',
                'requireAtlasContentIdentity',
                'sourceChangesInvalidateAcceptance'):
        _require(contracts.get(key) is True, f'required safety contract missing: {key}')
    for key in ('crossMinecraftConnections', 'saveDowngradeSupported'):
        _require(contracts.get(key) is False, f'unsupported claim: {key}')
    coverage = plan.get('featureGates', {})
    _require(isinstance(coverage, dict) and set(coverage) == {f'F{i:02}' for i in range(1, 17)},
             'every audited feature F01–F16 requires gates')
    known = CORE_FIXTURES | EXTENSIONS
    for feature, gates in coverage.items():
        _require(isinstance(gates, list) and bool(gates)
                 and all(isinstance(g, str) and g in known for g in gates)
                 and len(gates) == len(set(gates)), f'invalid gates for {feature}')
        _require(PRIMARY_FEATURE_GATES[feature] <= set(gates),
                 f'primary behavioral coverage missing for {feature}')
    _require(CORE_FIXTURES <= set(coverage['F16']), 'existing behavior must retain every core fixture')
    _require(set().union(*(set(v) for v in coverage.values())) == known,
             'some required gate has no audited feature')
    perf = plan.get('performance', {})
    _require(isinstance(perf, dict), 'performance must be an object')
    for key, minimum in {'pairedRepeatsMinimum': 3, 'checkpointPairsMinimum': 6,
                         'soakMinutesMinimum': 60, 'disconnectReopensMinimum': 10}.items():
        _require(type(perf.get(key)) is int and perf[key] >= minimum,
                 f'performance coverage reduced: {key}')
    _require(perf.get('requireRawMeasurements') is True and perf.get('requireHealthyClients') is True,
             'performance results require raw measurements and healthy clients')
    for key, maximum in {'ordinaryMeanTickMsBelow': 50, 'ordinaryP95TickMsMaximum': 50,
                         'p95RegressionPercentMaximum': 10, 'p99RegressionPercentMaximum': 15,
                         'recurringWarmCheckpointStepMsBlocker': 100}.items():
        _require(type(perf.get(key)) in (int, float) and 0 < perf[key] <= maximum,
                 f'performance limit weakened: {key}')
    _require(perf.get('largeServerHeapMiB') == 4096 and perf.get('largeServerClients') == 3,
             'preserve the controlled four-GiB three-client workload')
    compatibility = plan.get('compatibility', {})
    _require(isinstance(compatibility, dict), 'compatibility must be an object')
    _require(compatibility.get('releaseRenderer') == 'vanilla'
             and compatibility.get('sodiumClaim') == 'pending-target-specific-validation'
             and compatibility.get('forgeRendererAlternativesClaim') == 'not-qualified'
             and compatibility.get('shaderPacksClaim') == 'not-qualified'
             and compatibility.get('createFlywheelClaim') == 'benched',
             'unqualified compatibility claim')
    _require(plan.get('platforms') == {'primaryFullMatrix': 'windows-x64',
             'linuxDedicated': 'NOT_RUN', 'macosGraphical': 'NOT_RUN'},
             'do not claim unexecuted platform coverage')
    _require(isinstance(plan.get('blockers'), list) and bool(plan['blockers']),
             'unimplemented execution prerequisites must stay visible')
    return {'status': 'PLAN_VALID_NOT_QUALIFIED', 'cells': len(cells),
            'coreFixtureKeys': len(cells) * len(CORE_FIXTURES),
            'extensionKeys': len(cells) * len(EXTENSIONS),
            'expectedCellFamilyKeys': len(cells) * len(known),
            'runtimeExecutionPerformed': False, 'executionReady': False}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, default=DEFAULT_PLAN)
    parser.add_argument('--require-executable', action='store_true')
    args = parser.parse_args()
    try:
        result = validate(json.loads(args.plan.read_text(encoding='utf-8')))
        if args.require_executable:
            raise PlanError('Execution is not implemented: pins, adapters and production inputs are pending')
    except (OSError, ValueError, TypeError, KeyError) as error:
        print(json.dumps({'status': 'INVALID_OR_NOT_EXECUTABLE', 'error': str(error)}))
        return 2
    print(json.dumps(result, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
