#!/usr/bin/env python3
"""Serial Windows 1.21.1 packaged-client diagnostics, never a release verdict.

Uses the same native fixtures/verifiers as the development tasks, launching the
actual runtime jar on an inventory-verified official classpath. Final acceptance
also requires server, extension, performance, source/publication and review gates.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass, field
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import zipfile

try:
    from scripts.run_backport_exterior_diagnostics import ROOT, copy_mods, copy_tree, digest, require, runtime_inventory, save_json
    from scripts.verify_backport_frame_evidence import verify as verify_frames
except ModuleNotFoundError:
    from run_backport_exterior_diagnostics import ROOT, copy_mods, copy_tree, digest, require, runtime_inventory, save_json
    from verify_backport_frame_evidence import verify as verify_frames


@dataclass
class Case:
    name: str
    folder: str
    flag: str
    marker: str
    captures: int
    prepare: str | None = None
    verifier: str | None = None
    master: str | None = None
    world: str = 'Input'
    properties: dict = field(default_factory=dict)
    gradle_properties: dict = field(default_factory=dict)
    raw_frames: int = 0


def cases() -> list[Case]:
    result = [
        Case('creation-ui', 'run-creation-ui', 'creationUiTest', '[creation-ui-test] PASS', 26,
             'prepareCreationUiRun', 'verifyCreationUiClient'),
        Case('atlas-ui', 'run-atlas-ui', 'atlasUiTest', '[atlas-ui-test] PASS', 21,
             'prepareAtlasUiRun', 'verifyAtlasUiClient'),
        Case('map-compass', 'run-map-compass-capture', 'captureMapCompass', '[map-compass-capture] PASS', 8,
             'prepareMapCompassCaptureRun', 'verifyMapCompassCaptureClient'),
        Case('curved-objects', 'run-curved-object-capture', 'curvedObjectCapture',
             '[curved-object-capture] result=PASS, captures complete', 2,
             'prepareCurvedObjectCapture', 'verifyCurvedObjectCaptureClient'),
        Case('underwater', 'run-underwater', 'captureUnderwater', '[underwater-capture] PASS:', 7),
        Case('horizon', 'run-horizon', 'captureHorizon', '[horizon-test] CAPTURE COMPLETE:', 16),
        Case('floating-atlas', 'run-floating-atlas', 'captureFloatingAtlas', '[floating-atlas] NATIVE PASS:', 5,
             master='small', world='RingWorld Floating Atlas Review'),
        Case('production-lifecycle', 'run-production-lifecycle', 'productionLifecycleTest',
             '[production-lifecycle] result=true', 0, verifier='verifyProductionLifecycleClient',
             master='medium', properties={'productionLifecycleWorld': 'Input'},
             gradle_properties={'ProductionLifecycleDestination': 'Input'}),
        Case('layout-switch', 'run-layout-switch', 'layoutSwitchTest', '[layout-switch] result=true', 0,
             verifier='verifyLayoutSwitchClient', master='small', world='First',
             properties={'layoutSwitchFirst': 'First', 'layoutSwitchSecond': 'Second',
                         'layoutSwitchExpectation': 'different-layout'},
             gradle_properties={'LayoutSwitchFirstDestination': 'First', 'LayoutSwitchSecondDestination': 'Second',
                                'LayoutSwitchExpectation': 'different-layout'}),
    ]
    for name, extra, count in [('walls', {}, 3), ('altitude', {'captureAtlasAltitude': 'true'}, 6),
                                ('clouds', {'captureCloudAltitude': 'true'}, 6)]:
        result.append(Case(name, 'run-' + name, 'captureAtlasWalls', '[atlas-wall-review] CAPTURE COMPLETE:',
                           count, master='small', world='Atlas Wall Review', properties=extra))
    for environment in ('noon', 'dusk', 'night', 'rain'):
        result.append(Case('production-' + environment, 'run-production-projection', 'captureRingProjection',
                           '[projection-capture] result=true, captures complete', 3,
                           verifier='verifyProductionProjectionClient', master='medium',
                           properties={'projectionWorld': 'Input', 'projectionEnvironment': environment,
                                       'projectionViewDistanceChunks': '16', 'projectionGraphicsMode': 'fancy'},
                           gradle_properties={'ProjectionEnvironment': environment,
                                              'ProjectionViewDistanceChunks': '16', 'ProjectionGraphicsMode': 'fancy'},
                           raw_frames=3))
    for distance in (6, 12, 28, 16):
        production = distance == 16
        result.append(Case(('production' if production else 'small') + '-seam-rims-' + str(distance),
                           'run-production-visual-parity', 'captureRingVisualParity',
                           '[visual-parity-capture] result=true, captures complete', 4,
                           verifier='verifyProductionVisualParityClient', master='medium' if production else 'small',
                           properties={'visualParityWorld': 'Input', 'visualParityViewDistanceChunks': str(distance)},
                           gradle_properties={'VisualParityViewDistanceChunks': str(distance)}, raw_frames=1))
    result.append(Case('foliage', 'run-handoff-foliage-capture', 'captureRingHandoffFoliage',
                       '[handoff-foliage-capture] result=true, captures complete', 8,
                       verifier='verifyHandoffFoliageCaptureClient', master='medium',
                       properties={'handoffFoliageWorld': 'Input'}, raw_frames=1))
    result.append(Case('streaming', 'run-production-streaming-projection', 'captureRingProjection',
                       '[projection-capture] result=true, streaming fallback capture complete', 1,
                       verifier='verifyProductionStreamingProjectionClient', master='medium',
                       properties={'projectionWorld': 'Input', 'projectionStreamingEdge': 'true',
                                   'projectionViewDistanceChunks': '16'}, raw_frames=1))
    for environment, distance in [('noon', 16), ('rain', 28)]:
        result.append(Case('fabulous-' + environment, 'run-production-projection', 'captureRingProjection',
                           '[projection-capture] result=true, captures complete', 3,
                           verifier='verifyProductionProjectionClient', master='medium',
                           properties={'projectionWorld': 'Input', 'projectionEnvironment': environment,
                                       'projectionViewDistanceChunks': str(distance), 'projectionGraphicsMode': 'fabulous'},
                           gradle_properties={'ProjectionEnvironment': environment,
                                              'ProjectionViewDistanceChunks': str(distance), 'ProjectionGraphicsMode': 'fabulous'},
                           raw_frames=3))
    return result


def inventory(path: Path) -> list[dict]:
    return [{'path': p.relative_to(path).as_posix(), 'bytes': p.stat().st_size, 'sha256': digest(p)}
            for p in sorted(path.rglob('*')) if p.is_file()]


def required_native_captures(case: Case) -> set[str]:
    if case.name == 'underwater':
        return {'underwater-' + name + '.png' for name in
                ('above', 'submerged', 'surface', 'resurfaced', 'reload', 'reload-resurfaced', 'camera-only')}
    if case.name == 'horizon':
        return {'horizon-' + phase + '-' + height + '.png'
                for phase in ('sunset', 'sunrise', 'day', 'night', 'rain', 'thunder')
                for height in ('ground', 'elevated')} | {
                    'horizon-' + name + '.png' for name in
                    ('after-reload', 'nether-control', 'end-control', 'overworld-return')}
    if case.name == 'floating-atlas':
        return {'floating-atlas-' + name + '.png' for name in ('baseline', 'near', 'low', 'medium', 'high')}
    if case.name == 'walls':
        return {'atlas-walls-' + name + '.png' for name in ('low', 'medium', 'high')}
    if case.name == 'altitude':
        return {'atlas-altitude-' + name + '.png' for name in
                ('high-low', 'high-medium', 'high-high', 'lower-medium', 'ground-medium', 'above-build-medium')}
    if case.name == 'clouds':
        return {'cloud-altitude-' + mode + '-' + height + '.png'
                for mode in ('fancy', 'fast') for height in ('below', 'inside', 'above')}
    return set()


def clean_source(source: str) -> None:
    require(subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip() == source,
            'Source checkpoint changed during diagnostics')
    require(not subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip(),
            'Tracked diagnostic source became dirty')


def main() -> None:
    from PIL import Image
    # The historical independent NBT parser is a standalone script module.
    import sys
    sys.path.insert(0, str(ROOT / 'scripts'))
    from verify_backport_atlas_v11 import verify as verify_atlas
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--loader', choices=('fabric', 'neoforge'), required=True)
    for key in ('candidate', 'client-seed', 'java-home', 'small', 'medium', 'output'):
        parser.add_argument('--' + key, type=Path, required=True)
    parser.add_argument('--case', action='append', choices=[case.name for case in cases()])
    parser.add_argument('--wait-for-file', type=Path)
    parser.add_argument('--retire-successful-worlds', action='store_true')
    args = parser.parse_args()
    output = args.output.resolve()
    require(output.is_relative_to(ROOT / 'dist/qualification') and not output.exists(),
            'Use a fresh output below dist/qualification')
    source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    clean_source(source)
    seed = args.client_seed.resolve(); runtime_inventory(seed)
    java_home = args.java_home.resolve(); java = java_home / 'bin/java.exe'
    java_version = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True).stderr
    require('version "21.' in java_version, 'This adapter requires Java 21')
    with zipfile.ZipFile(args.candidate) as jar:
        require(jar.read('LICENSE-RINGWORLD.txt') == (ROOT / 'LICENSE').read_bytes(), 'Candidate MPL mismatch')
        identity = dict(line.split('=', 1) for line in jar.read('ringworld-build.properties').decode('utf-8').splitlines()
                        if '=' in line and not line.startswith('#'))
    label = identity['releaseLabel'] + ' · ' + identity['artifactVersion']
    output.mkdir(parents=True)
    candidate = output / args.candidate.name; shutil.copy2(args.candidate, candidate)
    sha = digest(candidate)
    subprocess.run(['git', 'archive', '--format=zip', '--output=' + str(output / 'source.zip'), source], cwd=ROOT, check=True)
    save_json(output / 'identity.json', {'diagnosticOnly': True, 'qualificationStatus': 'NOT_QUALIFIED',
        'sourceCommit': source, 'candidateSha256': sha, 'buildIdentity': identity, 'loader': args.loader,
        'minecraft': '1.21.1', 'javaVersion': java_version, 'runtimeClasspath': runtime_inventory(seed)})
    if args.wait_for_file is not None:
        print('WAIT scheduling prerequisite', args.wait_for_file, flush=True)
        deadline = time.monotonic() + 4 * 60 * 60
        while not args.wait_for_file.is_file():
            require(time.monotonic() < deadline, 'Scheduling prerequisite timed out')
            time.sleep(1)
    clean_source(source)
    masters = {'small': args.small.resolve(), 'medium': args.medium.resolve()}
    originals = {key: inventory(path) for key, path in masters.items()}
    save_json(output / 'input-inventories.json', originals)
    environment = os.environ.copy(); environment.pop('JAVA_TOOL_OPTIONS', None)
    environment['JAVA_HOME'] = str(java_home)
    environment['PATH'] = str(java_home / 'bin') + os.pathsep + environment['PATH']
    rows = []
    for case in cases():
        if args.case and case.name not in args.case: continue
        clean_source(source); require(digest(candidate) == sha, 'Frozen candidate changed')
        phase = output / case.name; phase.mkdir()
        runtime = phase / 'run' / case.folder
        prefix = ':' if args.loader == 'fabric' else ':neoforge:'
        properties = ['-PringQualificationRoot=' + str(output), '-PringQualificationCell=' + case.name]
        properties.extend('-P' + ('ring' if args.loader == 'fabric' else 'ringNeoForge') + key + '=' + value
                          for key, value in case.gradle_properties.items())
        def gradle(task: str, filename: str) -> None:
            clean_source(source)
            if args.loader == 'neoforge': task = task.replace('prepare', 'prepareNeoForge', 1).replace('verify', 'verifyNeoForge', 1)
            with (phase / filename).open('x', encoding='utf-8') as log:
                result = subprocess.run([str(ROOT / 'gradlew.bat'), prefix + task, *properties,
                    '--no-daemon', '--no-parallel', '--console=plain'], cwd=ROOT, env=environment,
                    stdout=log, stderr=subprocess.STDOUT, timeout=600)
            require(result.returncode == 0, 'Native verifier/prepare failed: ' + task)
        child = None
        started = time.monotonic()
        try:
            print('START', args.loader, case.name, flush=True)
            if case.prepare: gradle(case.prepare, 'prepare.log')
            runtime.mkdir(parents=True, exist_ok=True)
            (runtime / 'config').mkdir(exist_ok=True)
            config = runtime / 'config/ringworld.properties'
            if not config.exists():
                config.write_text(('widthBlocks=416\n' if case.name == 'horizon' else 'widthBlocks=128\n')
                                  + 'circumferenceBlocks=2048\nwallHeightBlocks=160\n'
                                  'testMode=false\ntestViewDistanceChunks=8\npregenerateTerrainAtlas=true\n')
            copy_mods(seed, runtime / 'mods', candidate, args.loader)
            if case.master:
                (runtime / 'saves').mkdir()
                copy_tree(masters[case.master], runtime / 'saves' / case.world)
                if case.name == 'layout-switch': copy_tree(masters['medium'], runtime / 'saves' / 'Second')
            lines = [line for line in (seed / 'java-args.txt').read_text(encoding='utf-8').splitlines()
                     if not line.strip('"').startswith('-Dringworld.')]
            flags = {case.flag: 'true', 'rawFrameEvidence': 'true', **case.properties}
            if case.name in ('creation-ui', 'atlas-ui'):
                flags[case.flag.removesuffix('Test') + 'ExpectedBuildLabel'] = label
            if case.name == 'atlas-ui': flags['projectionGpuProbe'] = 'true'
            lines[1:1] = ['"-Dringworld.' + key + '=' + value + '"' for key, value in flags.items()]
            dimensions = ('1920', '1080') if case.name in ('creation-ui', 'atlas-ui') else ('1280', '720')
            username = {'creation-ui': 'CreationUiTester', 'atlas-ui': 'AtlasUiTester',
                        'map-compass': 'MapCompassTester', 'curved-objects': 'CurvedObjects'}.get(case.name, 'BackportTester')
            for option, value in {'--gameDir': str(runtime).replace('\\', '/'), '--username': username,
                                  '--width': dimensions[0], '--height': dimensions[1]}.items():
                lines[lines.index('"' + option + '"') + 1] = '"' + value + '"'
            (runtime / 'java-args.txt').write_text('\n'.join(lines), encoding='utf-8')
            runtime_inventory(seed)
            with (phase / 'process.log').open('x', encoding='utf-8') as log:
                child = subprocess.Popen([str(java), '@java-args.txt'], cwd=runtime, env=environment,
                                         stdout=log, stderr=subprocess.STDOUT)
                save_json(phase / 'process.json', {'parentPid': os.getpid(), 'pid': child.pid, 'runtime': str(runtime)})
                require(child.wait(timeout=1800) == 0, 'Packaged client failed or crashed')
            text = (phase / 'process.log').read_text(encoding='utf-8', errors='replace')
            require(case.marker in text and 'Exception in server tick loop' not in text
                    and 'Mixin apply failed' not in text and 'OutOfMemoryError' not in text
                    and '] FAIL:' not in text and 'result=false' not in text and 'result=FAIL' not in text,
                    'Missing native completion or explicit product failure')
            if case.verifier: gradle(case.verifier, 'verification.log')
            screenshots = sorted((runtime / 'screenshots').glob('*.png'))
            require(len(screenshots) >= case.captures, 'Missing screenshots')
            require(required_native_captures(case) <= {p.name for p in screenshots},
                    'Missing a specific native control capture')
            pngs = []
            for path in screenshots:
                with Image.open(path) as image:
                    image.load(); require(16 <= image.width <= 16384 and 16 <= image.height <= 16384, 'Invalid capture dimensions')
                    pngs.append({'path': path.relative_to(output).as_posix(), 'sha256': digest(path), 'size': image.size})
            frames = [{'path': path.relative_to(output).as_posix(), **verify_frames(path)}
                      for path in sorted((runtime / 'evidence').glob('*.rwfr'))]
            require(len(frames) == case.raw_frames, 'Missing or unexpected raw frame windows')
            clean_source(source)
            worlds = []
            for world in sorted((runtime / 'saves').glob('*')):
                if not (world / 'level.dat').is_file(): continue
                cache = world / 'data/ringworld/terrain-atlas.rwat.gz'
                disk = verify_atlas(world / 'data/ringworld/settings.dat', cache, require_complete=False) if cache.exists() else None
                if case.master or case.name == 'atlas-ui': require(disk is not None and disk['status'] == 'PASS', 'Missing complete saved Atlas')
                files = inventory(world)
                receipt = phase / ('world-' + str(len(worlds)) + '-inventory.json'); save_json(receipt, files)
                worlds.append({'world': world.name, 'disk': disk, 'inventorySha256': digest(receipt)})
                if args.retire_successful_worlds:
                    require(world.resolve().is_relative_to(output) and not world.is_symlink(), 'Unsafe disposable-world retirement')
                    retained = phase / 'persisted-data' / world.name
                    for file in world.rglob('*'):
                        if file.is_file() and (file.suffix == '.dat' or file.name == 'terrain-atlas.rwat.gz'):
                            target = retained / file.relative_to(world); target.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(file, target)
                    shutil.rmtree(('\\\\?\\' if os.name == 'nt' else '') + str(world.resolve()))
            clean_source(source)
            record = {'status': 'PASS', 'diagnosticOnly': True, 'case': case.name,
                      'candidateSha256': sha, 'sourceCommit': source, 'elapsedSeconds': time.monotonic() - started,
                      'processLogSha256': digest(phase / 'process.log'), 'captures': pngs,
                      'frames': frames, 'worlds': worlds}
            save_json(phase / 'terminal.json', record); rows.append(record)
            print('PASS', args.loader, case.name, flush=True)
        except BaseException as failure:
            save_json(phase / 'FAILED.json', {'status': 'FAIL', 'diagnosticOnly': True, 'error': repr(failure)})
            raise
        finally:
            if child is not None and child.poll() is None:
                child.terminate()
                try: child.wait(timeout=10)
                except subprocess.TimeoutExpired: child.kill(); child.wait(timeout=10)
    require(all(inventory(masters[key]) == value for key, value in originals.items()), 'An original production input changed')
    save_json(output / 'results.json', {'diagnosticOnly': True, 'qualificationStatus': 'NOT_QUALIFIED', 'cases': rows})


if __name__ == '__main__': main()
