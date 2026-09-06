#!/usr/bin/env python3
"""Start, tick, save and stop already prepared disposable development servers.

Does not prepare worlds, accept the EULA, install mods or establish gameplay
qualification. Records the exact installed mod hashes with each result.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import selectors
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]


def run(directory: Path, timeout: int = 180) -> dict:
    directory = directory.resolve()
    allowed = (ROOT / 'dist' / name for name in ('backlog-runtime', 'backlog-compatibility'))
    if not any(directory != root and directory.is_relative_to(root) for root in allowed):
        raise ValueError('expected an explicitly prepared disposable backlog runtime')
    eula = directory / 'eula.txt'
    if not eula.is_file() or 'eula=true' not in [line.strip() for line in eula.read_text().splitlines()]:
        raise ValueError('missing retained EULA acceptance')
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    if (directory / 'fabric-server-launch.jar').is_file():
        command = [java, '-Xmx2G', '-jar', 'fabric-server-launch.jar', 'nogui']
    else:
        arguments = list((directory / 'libraries/net/neoforged/neoforge').glob('*/unix_args.txt'))
        if len(arguments) != 1:
            raise ValueError('ambiguous NeoForge runtime')
        command = [java, '-Xmx2G', '@' + str(arguments[0]), 'nogui']
    hashes = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
              for p in sorted((directory / 'mods').glob('*.jar'))}
    started = time.monotonic()
    ready_at = None
    stopped = False
    timed_out = False
    output = bytearray()
    with subprocess.Popen(command, cwd=directory, stdin=subprocess.PIPE,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT) as process:
        with selectors.DefaultSelector() as selector, (directory / 'development-smoke.log').open('wb') as log:
            selector.register(process.stdout, selectors.EVENT_READ)
            while selector.get_map():
                now = time.monotonic()
                if now - started > timeout and process.poll() is None:
                    timed_out = True
                    process.terminate()
                    try:
                        process.wait(timeout=30)
                    except subprocess.TimeoutExpired:
                        process.kill()
                if ready_at is not None and now - ready_at >= 15 and not stopped and process.poll() is None:
                    process.stdin.write(b'save-all flush\nstop\n')
                    process.stdin.flush()
                    stopped = True
                for key, _ in selector.select(1):
                    data = os.read(key.fd, 65536)
                    if not data:
                        selector.unregister(key.fileobj)
                        continue
                    log.write(data)
                    log.flush()
                    output.extend(data)
                    if ready_at is None and b'Done (' in output:
                        ready_at = time.monotonic()
            exit_code = process.wait()
    result = {'directory': str(directory), 'mods_sha256': hashes,
              'ready': ready_at is not None, 'stop_requested': stopped,
              'timed_out': timed_out, 'exit': exit_code,
              'seconds': round(time.monotonic() - started, 2),
              'pass': ready_at is not None and stopped and not timed_out and exit_code == 0}
    (directory / 'development-smoke.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result), flush=True)
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directories', type=Path, nargs='+')
    args = parser.parse_args()
    results = [run(directory) for directory in args.directories]
    return 0 if all(result['pass'] for result in results) else 1


if __name__ == '__main__':
    raise SystemExit(main())
