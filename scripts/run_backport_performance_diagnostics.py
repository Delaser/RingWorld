#!/usr/bin/env python3
"""Collect real three-client measurements; this is never a release verdict.

Run this serially on an otherwise idle graphical Windows worker. The first
short run validates instrumentation; paired baselines and a 60-minute soak
are separately required by the acceptance plan. Only disposable world copies
are opened. RCON is bound to loopback and used for health/pose observations.
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import struct
import subprocess
import time

try:
    from .run_backport_exterior_diagnostics import (ROOT, copy_tree, copy_mods, client_arguments,
        digest, require, runtime_inventory, save_json, wait_marker)
    from .run_backport_client_diagnostics import clean_source, inventory, normalize_profile_options
    from .verify_backport_tick_evidence import rows as tick_rows, summarize
    from .verify_backport_frame_evidence import clock_anchor, clock_correlation, runtime_rows, verify_runtime_sequence
except ImportError:
    from run_backport_exterior_diagnostics import (ROOT, copy_tree, copy_mods, client_arguments,
        digest, require, runtime_inventory, save_json, wait_marker)
    from run_backport_client_diagnostics import clean_source, inventory, normalize_profile_options
    from verify_backport_tick_evidence import rows as tick_rows, summarize
    from verify_backport_frame_evidence import clock_anchor, clock_correlation, runtime_rows, verify_runtime_sequence

FAILURE = re.compile(r'Exception in server tick loop|Failed to save chunk|OutOfMemoryError|'
                     r'Mixin apply failed|InjectionError|\[runtime-frame-evidence\] FAIL|'
                     r'Could not retain every runtime frame|server tick evidence capacity exceeded')


class Rcon:
    def __init__(self, port: int, password: str):
        self.connection = socket.create_connection(('127.0.0.1', port), timeout=10)
        self.ordinal = 1
        self.send(3, password)
        identity, kind, _ = self.receive()
        require((identity, kind) == (1, 2), 'RCON authentication failed')

    def exact(self, count: int) -> bytes:
        result = bytearray()
        while len(result) < count:
            part = self.connection.recv(count - len(result))
            if not part: raise ConnectionError('RCON closed unexpectedly')
            result.extend(part)
        return bytes(result)

    def send(self, kind: int, command: str) -> None:
        body = struct.pack('<ii', self.ordinal, kind) + command.encode('utf-8') + b'\0\0'
        self.connection.sendall(struct.pack('<i', len(body)) + body)

    def receive(self) -> tuple[int, int, str]:
        size, = struct.unpack('<i', self.exact(4))
        require(10 <= size <= 4096, 'RCON response exceeds bounded command budget')
        body = self.exact(size)
        require(body[-2:] == b'\0\0', 'Malformed RCON terminator')
        return (*struct.unpack('<ii', body[:8]), body[8:-2].decode('utf-8', errors='strict'))

    def command(self, command: str) -> str:
        self.ordinal += 1; self.send(2, command)
        identity, kind, response = self.receive()
        require((identity, kind) == (self.ordinal, 0), 'RCON response identity differs')
        return response

    def close(self): self.connection.close()


def epoch_nanos(metadata: dict, monotonic: int, end_metadata: dict | None = None) -> int:
    if end_metadata is not None:
        first, first_wall, last, last_wall = clock_correlation(metadata, end_metadata)
        require(first <= monotonic <= last, 'Sample lies outside its clock calibration')
        return first_wall + (monotonic - first) * (last_wall - first_wall) // (last - first)
    anchor, epoch, _ = clock_anchor(metadata)
    return epoch + monotonic - anchor


def measured_frames(paths: list[Path], started: int, ended: int, completion: dict | None = None) -> list[int]:
    selected = []
    for path in paths:
        metadata, durations = runtime_rows(path)
        monotonic = metadata['firstStartNanoTime']
        for duration in durations:
            begin = epoch_nanos(metadata, monotonic, completion)
            end = epoch_nanos(metadata, monotonic + duration, completion)
            if begin >= started and end <= ended: selected.append(duration)
            monotonic += duration
    return selected


def main() -> None:
    from verify_backport_atlas_v11 import verify as verify_atlas
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--loader', choices=['fabric', 'neoforge'], required=True)
    for name in ['candidate', 'client-seed', 'server-seed', 'java-home', 'world', 'eula', 'output']:
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--warmup-seconds', type=int, default=120)
    parser.add_argument('--seconds', type=int, default=120)
    parser.add_argument('--server-port', type=int, default=25574)
    parser.add_argument('--rcon-port', type=int, default=25575)
    args = parser.parse_args()
    require(60 <= args.warmup_seconds <= 600 and 60 <= args.seconds <= 3600, 'Invalid timing budget')
    require(1024 < args.server_port < 65536 and 1024 < args.rcon_port < 65536
            and args.server_port != args.rcon_port, 'Invalid loopback ports')
    source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    clean_source(source)
    require(subprocess.check_output(['git', 'rev-parse', '@{upstream}'], cwd=ROOT, text=True).strip() == source,
            'Performance diagnostics require pushed source')
    candidate, seed, server_seed, master, output, jdk = (getattr(args, name).resolve() for name in
        ['candidate', 'client_seed', 'server_seed', 'world', 'output', 'java_home'])
    require(output.is_relative_to(ROOT / 'dist/qualification') and not output.exists(), 'Fresh contained output required')
    require(not (server_seed / 'world').exists() and not (server_seed / 'mods').exists(), 'Mutable server seed')
    require('eula=true' in args.eula.read_text(), 'Missing accepted EULA')
    java = jdk / 'bin/java.exe'
    version = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True).stderr
    require('version "21.' in version, 'Java 21 required')
    dependencies = runtime_inventory(seed)
    original = inventory(master); sha = digest(candidate)
    disk = verify_atlas(master / 'data/ringworld/settings.dat', master / 'data/ringworld/terrain-atlas.rwat.gz')
    output.mkdir(parents=True)
    save_json(output / 'identity.json', {'sourceCommit': source, 'candidateSha256': sha,
        'loader': args.loader, 'javaVersion': version, 'runtimeClasspath': dependencies,
        'diagnosticOnly': True, 'qualificationStatus': 'NOT_QUALIFIED', 'inputAtlas': disk,
        'serverHeapGiB': 4, 'clientHeapGiB': 2, 'clients': 3, 'viewDistance': 28, 'simulationDistance': 8})
    save_json(output / 'input-inventory.json', original)
    subprocess.run(['git', 'archive', '--format=zip', '--output=' + str(output / 'source.zip'), source], cwd=ROOT, check=True)
    runtime = output / 'server'; copy_tree(server_seed, runtime)
    copy_mods(seed, runtime / 'mods', candidate, args.loader); copy_tree(master, runtime / 'world')
    shutil.copy2(args.eula, runtime / 'eula.txt')
    (runtime / 'config').mkdir(exist_ok=True)
    (runtime / 'config/ringworld.properties').write_text('testMode=false\npregenerateTerrainAtlas=false\n')
    password = secrets.token_hex(24)
    (runtime / 'server.properties').write_text('server-ip=127.0.0.1\nonline-mode=false\nlevel-name=world\n'
        'gamemode=creative\nforce-gamemode=true\ndifficulty=normal\nview-distance=28\nsimulation-distance=8\n'
        f'server-port={args.server_port}\nenable-rcon=true\nrcon.port={args.rcon_port}\nrcon.password={password}\n')
    launch = ['-jar', 'fabric-server-launch.jar', 'nogui'] if args.loader == 'fabric' else [
        '@libraries/net/neoforged/neoforge/21.1.239/win_args.txt', 'nogui']
    server_args = [str(java), '-Xms1G', '-Xmx4G', '-XX:+UseG1GC', '-XX:MaxGCPauseMillis=200',
        '-XX:+ParallelRefProcEnabled', '-Xlog:gc*,safepoint:file=gc.log:time,uptime,level,tags',
        '-Dringworld.measureServerTicks=true', '-Dringworld.tickEvidenceRunId=three-client', *launch]
    env = os.environ.copy(); env.pop('JAVA_TOOL_OPTIONS', None)
    env['JAVA_HOME'] = str(jdk); env['PATH'] = str(jdk / 'bin') + os.pathsep + env['PATH']
    children, handles, logs = [], [], []
    controller = None; health = []
    try:
        log_path = output / 'server-process.log'; handle = log_path.open('x', encoding='utf-8'); handles.append(handle)
        server = subprocess.Popen(server_args, cwd=runtime, env=env, stdin=subprocess.PIPE,
            stdout=handle, stderr=subprocess.STDOUT, text=True); children.append(server); logs.append(log_path)
        save_json(output / 'server-process.json', {'pid': server.pid, 'parentPid': os.getpid(), 'args': server_args})
        wait_marker(server, log_path, 'RCON running on', 600)
        controller = Rcon(args.rcon_port, password)
        for role in 'ABC':
            client = output / ('client-' + role); client.mkdir(); normalize_profile_options(client)
            copy_mods(seed, client / 'mods', candidate, args.loader)
            client_arguments(seed, client, role, ['-Dringworld.performanceClient=true',
                '-Dringworld.runtimeFrameEvidence=true', '-Dringworld.multiplayerTestViewDistanceChunks=28'], args.server_port)
            lines = (client / 'java-args.txt').read_text(encoding='utf-8').splitlines()
            lines = [line for line in lines if not line.strip('"').startswith(('-Xmx', '-Xms'))]
            lines[0:0] = ['"-Xms512M"', '"-Xmx2G"', '"-Xlog:gc*,safepoint:file=gc.log:time,uptime,level,tags"']
            (client / 'java-args.txt').write_text('\n'.join(lines), encoding='utf-8')
            log_path = output / ('client-' + role + '-process.log')
            handle = log_path.open('x', encoding='utf-8'); handles.append(handle); logs.append(log_path)
            child = subprocess.Popen([str(java), '@java-args.txt'], cwd=client, env=env, stdout=handle, stderr=subprocess.STDOUT)
            children.append(child)
            save_json(client / 'process.json', {'pid': child.pid, 'parentPid': os.getpid()})
            wait_marker(child, log_path, '[runtime-frame-evidence] START', 300)
            response = controller.command('tp RingTester' + role + ' 64 180 64 0 20')
            require('Teleported' in response, 'Cannot position live client')
        print('START three healthy real clients', args.loader, flush=True)
        started = ended = None
        for phase, duration in [('warmup', args.warmup_seconds), ('measured', args.seconds)]:
            if phase == 'measured': started = time.time_ns(); print('START measured window', args.loader, flush=True)
            deadline = time.monotonic() + duration
            while time.monotonic() < deadline:
                require(all(child.poll() is None for child in children), 'Unexpected server/client exit')
                for path in logs:
                    require(not FAILURE.search(path.read_text(encoding='utf-8', errors='replace')), 'Native failure: ' + str(path))
                listing = controller.command('list')
                require('There are 3 of a max of' in listing and all('RingTester' + role in listing for role in 'ABC'),
                        'Three real clients must remain connected')
                entry = {'epochNanos': time.time_ns(), 'phase': phase, 'players': listing, 'observations': []}
                for role in 'ABC':
                    player = 'RingTester' + role
                    life = controller.command('data get entity ' + player + ' Health')
                    require(re.search(r': 20(?:\.0)?f$', life) is not None, 'Client is unhealthy: ' + life)
                    pose = controller.command('data get entity ' + player + ' Pos')
                    entry['observations'].append({'player': player, 'health': life, 'position': pose})
                health.append(entry)
                time.sleep(min(5, max(0, deadline - time.monotonic())))
            if phase == 'measured': ended = time.time_ns()
        save_json(output / 'measurement-window.json', {'startEpochNanos': started, 'endEpochNanos': ended,
            'clockModel': 'affine native start/end UTC anchors; raw durations stay monotonic',
            'maximumClockRateErrorPpm': 500, 'additiveClockToleranceNanos': 5_000_000})
        save_json(output / 'health.json', health)
        for role in 'ABC': controller.command('kick RingTester' + role + ' Measurement complete')
        for child in children[1:]: require(child.wait(timeout=90) == 0, 'Client did not close normally')
        server.stdin.write('stop\n'); server.stdin.flush()
        require(server.wait(timeout=120) == 0, 'Server did not save and close normally')
        require('All dimensions are saved' in logs[0].read_text(encoding='utf-8', errors='replace'), 'Missing normal save')
        raw_ticks = list((runtime / 'world/ringworld-evidence').glob('ticks-three-client-*.rwtk'))
        require(len(raw_ticks) == 1, 'Missing inclusive server evidence')
        metadata, samples = tick_rows(raw_ticks[0]); end_anchor = metadata['endClockAnchor']
        selected = [row for row in samples if started <= epoch_nanos(metadata, row[3], end_anchor)
                    and epoch_nanos(metadata, row[3] + sum(row[4:7]), end_anchor) <= ended]
        require(len(selected) >= args.seconds * 10 and all(row[1] == 3 for row in selected), 'Missing healthy measured ticks')
        frames = {}
        for role in 'ABC':
            directory = output / ('client-' + role) / 'evidence/runtime-frames'
            paths = sorted(directory.glob('*.rwfr'), key=lambda path: int(path.stem.rsplit('-', 1)[1]))
            completion_path = directory / ('frames-runtime-' + role + '-complete.json')
            stream = verify_runtime_sequence(paths, completion_path)
            values = measured_frames(paths, started, ended, json.loads(completion_path.read_text(encoding='utf-8')))
            require(len(values) >= args.seconds * 10, 'Missing measured graphical frames')
            frames[role] = {'stream': stream, 'measured': summarize(values)}
        require(inventory(master) == original and digest(candidate) == sha, 'Immutable input changed')
        clean_source(source)
        saved = verify_atlas(runtime / 'world/data/ringworld/settings.dat', runtime / 'world/data/ringworld/terrain-atlas.rwat.gz')
        save_json(output / 'terminal.json', {'status': 'COLLECTED', 'diagnosticOnly': True,
            'qualificationStatus': 'NOT_QUALIFIED', 'sourceCommit': source, 'candidateSha256': sha,
            'measuredStartEpochNanos': started, 'measuredEndEpochNanos': ended, 'durationSeconds': args.seconds,
            'serverWork': summarize([row[4] + row[5] for row in selected]), 'clientFrames': frames,
            'inputAtlas': disk, 'savedAtlas': saved, 'healthSha256': digest(output / 'health.json')})
        print('COLLECTED three-client window', args.loader, flush=True)
    except BaseException as failure:
        save_json(output / 'FAILED.json', {'status': 'FAIL', 'diagnosticOnly': True, 'error': repr(failure)})
        save_json(output / 'partial-health.json', health)
        raise
    finally:
        if controller is not None: controller.close()
        # Normal stop first, then terminate only processes created by this run.
        if children and children[0].poll() is None:
            try:
                children[0].stdin.write('stop\n'); children[0].stdin.flush(); children[0].wait(timeout=60)
            except (OSError, subprocess.TimeoutExpired): pass
        for child in children:
            if child.poll() is None:
                child.terminate()
                try: child.wait(timeout=10)
                except subprocess.TimeoutExpired: child.kill(); child.wait(timeout=10)
        for handle in handles: handle.close()


if __name__ == '__main__': main()
