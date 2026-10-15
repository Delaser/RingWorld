#!/usr/bin/env python3
"""Bounded packaged 1.21.1 exterior fixtures; never final qualification/publication.

Seeds are reviewed official runtime installations. Their immutable classpath
inventory is rehashed, and copied mods contain only the chosen candidate and
Fabric API. Original seed mods and worlds are never changed. The full backport
acceptance coordinator must separately bind build provenance and all families.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FAILURE = re.compile(r'Exception in server tick loop|Failed to save chunk|OutOfMemoryError|'
                     r'\[(?:outside-building|off-ring)[^\]]*\].*result=false|'
                     r'Mixin apply failed|InjectionError|Outside building watchdog')


def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def save_json(path: Path, value: object) -> None:
    with path.open('x', encoding='utf-8') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def verify_log(text: str, markers: list[str], label: str) -> None:
    require(not FAILURE.search(text), f'{label}: product failure in raw process log')
    position = 0
    for marker in markers:
        index = text.find(marker, position)
        require(index >= 0, f'{label}: missing or unordered marker {marker}')
        position = index + len(marker)


def runtime_inventory(seed: Path) -> list[dict]:
    inventory = json.loads((seed / 'inventory.json').read_text(encoding='utf-8'))['classpath']
    paths = {str(Path(item['path']).resolve()).lower(): item for item in inventory}
    require(len(paths) == len(inventory), 'Duplicate runtime inventory paths')
    arguments = [line.strip('"') for line in (seed / 'java-args.txt').read_text(encoding='utf-8').splitlines()]
    used = []
    for flag in ('-cp', '-p'):
        if flag in arguments:
            used.extend(arguments[arguments.index(flag) + 1].split(';'))
    require({str(Path(p).resolve()).lower() for p in used} == set(paths),
            'Runtime inventory differs from client class/module path')
    for item in inventory:
        path = Path(item['path'])
        require(path.is_file() and digest(path) == item['sha256'], f'Runtime dependency changed: {path}')
    return inventory


def copy_tree(source: Path, destination: Path) -> None:
    # Python/Win32 otherwise rejects the official NeoForge library paths.
    prefix = '\\\\?\\' if os.name == 'nt' else ''
    shutil.copytree(prefix + str(source.resolve()), prefix + str(destination.resolve()))


def copy_mods(seed: Path, destination: Path, candidate: Path, loader: str) -> None:
    copy_tree(seed / 'mods', destination)
    old = list(destination.glob('ringworld*.jar'))
    require(len(old) == 1, 'Expected exactly one old RingWorld seed mod')
    old[0].unlink()
    require(all(p.name.startswith('fabric-api-') for p in destination.iterdir()) if loader == 'fabric'
            else not any(destination.iterdir()), 'Unreviewed mod in runtime seed')
    shutil.copy2(candidate, destination / candidate.name)


def client_arguments(seed: Path, client: Path, role: str, flags: list[str], port: int) -> None:
    lines = [line for line in (seed / 'java-args.txt').read_text(encoding='utf-8').splitlines()
             if not line.strip('"').startswith('-Dringworld.')]
    for option, value in {'--gameDir': str(client).replace('\\', '/'), '--username': 'RingTester' + role,
                          '--width': '1280', '--height': '720'}.items():
        lines[lines.index('"' + option + '"') + 1] = '"' + value + '"'
    lines[1:1] = ['"' + flag + '"' for flag in flags + [
        '-Dringworld.multiplayerTestRole=' + role, '-Dringworld.multiplayerTestPort=' + str(port)]]
    (client / 'java-args.txt').write_text('\n'.join(lines), encoding='utf-8')


def wait_marker(child: subprocess.Popen, path: Path, marker: str, seconds: int) -> None:
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        text = path.read_text(encoding='utf-8', errors='replace')
        require(not FAILURE.search(text), f'Product failure: {path}')
        if marker in text:
            return
        require(child.poll() is None, f'Process exited before {marker}: {path}')
        time.sleep(1)
    raise TimeoutError(f'Missing {marker}: {path}')


def run(args: argparse.Namespace) -> None:
    # Pillow is a host prerequisite, not a game/runtime dependency.
    from PIL import Image
    source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    require(not subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip(),
            'Diagnostic packaged runs require a clean committed source checkpoint')
    candidate, java_home, seed, server_seed, output = (
        getattr(args, key).resolve() for key in ('candidate', 'java_home', 'client_seed', 'server_seed', 'output'))
    require(not output.exists(), 'Output must be exclusive and fresh')
    require(not (server_seed / 'world').exists() and not (server_seed / 'mods').exists(),
            'Server seed contains a mutable world/mods installation')
    require('eula=true' in args.eula.read_text(), 'Missing previously accepted EULA')
    java = java_home / 'bin/java.exe'
    version = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True)
    require('version "21.' in version.stderr, 'This native adapter requires Java 21')
    sha = digest(candidate)
    with zipfile.ZipFile(candidate) as jar:
        require(jar.read('LICENSE-RINGWORLD.txt') == (ROOT / 'LICENSE').read_bytes(), 'Candidate MPL mismatch')
        require('dev/ringworld/server/RingOutsideBuildingTest.class' in jar.namelist(), 'Missing fixture candidate')
    dependencies = runtime_inventory(seed)
    output.mkdir(parents=True)
    save_json(output / 'identity.json', {'diagnosticOnly': True, 'qualificationStatus': 'NOT_QUALIFIED',
        'sourceCommit': source, 'loader': args.loader, 'minecraft': '1.21.1', 'candidateSha256': sha,
        'candidate': str(candidate), 'javaVersion': version.stderr, 'runtimeClasspath': dependencies})
    subprocess.run(['git', 'archive', '--format=zip', '--output=' + str(output / 'source.zip'), source], cwd=ROOT, check=True)
    runtime = output / 'server'
    copy_tree(server_seed, runtime)
    copy_mods(seed, runtime / 'mods', candidate, args.loader)
    shutil.copy2(args.eula, runtime / 'eula.txt')
    (runtime / 'config').mkdir(exist_ok=True)
    (runtime / 'config/ringworld.properties').write_text('widthBlocks=128\ncircumferenceBlocks=2048\nwallHeightBlocks=160\ntestMode=false\npregenerateTerrainAtlas=false\n')
    launch = ['-jar', 'fabric-server-launch.jar', 'nogui'] if args.loader == 'fabric' else [
        '@libraries/net/neoforged/neoforge/21.1.239/win_args.txt', 'nogui']
    environment = os.environ.copy()
    environment['JAVA_HOME'] = str(java_home)
    environment['PATH'] = str(java_home / 'bin') + os.pathsep + environment['PATH']
    environment.pop('JAVA_TOOL_OPTIONS', None)
    phases = []
    for name in ('placement', 'reopen', 'visibility'):
        phase = output / name
        phase.mkdir()
        if name == 'visibility':
            (runtime / 'world').rename(output / 'placement-world')
        (runtime / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=' + str(args.port) +
            '\nonline-mode=false\ngamemode=creative\ndifficulty=normal\nlevel-name=world\nlevel-seed=ringworld-exterior-1.4\nview-distance=6\nsimulation-distance=5\n')
        flags = ['-Dringworld.offRingVisibilityTest=true'] if name == 'visibility' else [
            '-Dringworld.outsideBuildingTest=true'] + (['-Dringworld.outsideBuildingResume=true'] if name == 'reopen' else [])
        children, handles = [], []
        started = time.monotonic()
        try:
            require(digest(candidate) == sha, 'Candidate changed')
            runtime_inventory(seed)
            server_log = phase / 'server-process.log'
            handle = server_log.open('x', encoding='utf-8'); handles.append(handle)
            server = subprocess.Popen([str(java), '-Xmx4G', '-Dringworld.multiplayerTest=true', *flags, *launch],
                cwd=runtime, env=environment, stdin=subprocess.PIPE, stdout=handle, stderr=subprocess.STDOUT, text=True)
            children.append(server)
            wait_marker(server, server_log, 'Done (', 600)
            clients = []
            for role in ('A', 'B'):
                directory = phase / ('client-' + role.lower()); directory.mkdir()
                copy_mods(seed, directory / 'mods', candidate, args.loader)
                copy_tree(seed / 'config', directory / 'config')
                client_arguments(seed, directory, role, flags, args.port)
                handle = (phase / ('client-' + role.lower() + '-process.log')).open('x', encoding='utf-8'); handles.append(handle)
                child = subprocess.Popen([str(java), '@java-args.txt'], cwd=directory, env=environment,
                    stdout=handle, stderr=subprocess.STDOUT)
                children.append(child); clients.append((role, child, directory))
                time.sleep(2)
            save_json(phase / 'processes.json', {'parentPid': os.getpid(), 'children': [p.pid for p in children]})
            require(server.wait(timeout=600) == 0, 'Dedicated server exit failed')
            server_text = server_log.read_text(encoding='utf-8', errors='replace')
            prefix = 'off-ring' if name == 'visibility' else 'outside-building'
            count = 7 if name == 'visibility' else 2 if name == 'reopen' else 6
            final = '[off-ring] result=true all 7 visibility stages and vanilla void damage passed' if name == 'visibility' else (
                '[outside-building] result=true resume=' + str(name == 'reopen').lower())
            verify_log(server_text, [f'[{prefix}] stage={i} result=true' for i in range(count)] + [
                final, 'All dimensions are saved'], name + ' server')
            if name == 'placement':
                require('survivalInventory=true bedFootAndHead=true' in server_text, 'Missing native survival/bed rejection')
            captures = []
            for role, child, directory in clients:
                require(child.wait(timeout=90) == 0, 'Client did not exit normally')
                text = (phase / ('client-' + role.lower() + '-process.log')).read_text(encoding='utf-8', errors='replace')
                verify_log(text, [f'[{prefix}:{role}] stage={i} result=true' for i in range(count)], role)
                names = [f'off-ring-{role}-{i}.png' for i in range(count)] if name == 'visibility' else [
                    f'outside-{role}-' + ('reopen-' if name == 'reopen' else '') + f'{i}.png' for i in range(count)]
                if name == 'placement': names.append(f'outside-{role}-world-menu.png')
                for filename in names:
                    path = directory / 'screenshots' / filename
                    with Image.open(path) as image:
                        image.load(); require(image.size == (1280, 720), f'Wrong capture dimensions: {path}')
                    captures.append({'path': str(path.relative_to(output)), 'sha256': digest(path)})
            record = {'status': 'PASS', 'diagnosticOnly': True, 'phase': name, 'elapsedSeconds': time.monotonic() - started,
                      'candidateSha256': sha, 'sourceCommit': source, 'captures': captures,
                      'logs': [{'path': str(p.relative_to(output)), 'sha256': digest(p)} for p in phase.glob('*-process.log')]}
            save_json(phase / 'terminal.json', record); phases.append(record)
            print('PASS', args.loader, name, flush=True)
        except BaseException as failure:
            save_json(phase / 'FAILED.json', {'status': 'FAIL', 'error': repr(failure), 'diagnosticOnly': True})
            raise
        finally:
            for child in children:
                if child.poll() is None and child is children[0]:
                    try: child.stdin.write('stop\n'); child.stdin.flush(); child.wait(timeout=60)
                    except (OSError, subprocess.TimeoutExpired): pass
                if child.poll() is None:
                    child.terminate()
                    try: child.wait(timeout=10)
                    except subprocess.TimeoutExpired: child.kill(); child.wait(timeout=10)
            for handle in handles: handle.close()
    require(digest(candidate) == sha, 'Candidate changed after fixture')
    save_json(output / 'results.json', {'diagnosticOnly': True, 'qualificationStatus': 'NOT_QUALIFIED', 'phases': phases})


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--loader', choices=('fabric', 'neoforge'), required=True)
    for option in ('candidate', 'java-home', 'client-seed', 'server-seed', 'eula', 'output'):
        parser.add_argument('--' + option, type=Path, required=True)
    parser.add_argument('--port', type=int, default=25568)
    args = parser.parse_args()
    require(1024 <= args.port <= 65535, 'Invalid isolated port')
    run(args)


if __name__ == '__main__':
    main()
