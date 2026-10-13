"""Provision and verify immutable NeoForm intermediate cache acceleration inputs.

Only derived Minecraft setup artifacts are copied; never worlds, RingWorld
classes, credentials, locks or writable state shared between test processes.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import shutil
from typing import Sequence

INDEX = 'neoform-seed.json'
NAME = re.compile(r'[A-Za-z0-9_-]+\.(?:jar|zip|txt)')
MAX_BYTES = 8 * 1024**3


class NeoFormSeedError(ValueError):
    pass


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()


def verified_entries(root: Path) -> tuple[tuple[Path, str], ...]:
    if root.is_symlink() or not root.is_dir():
        raise NeoFormSeedError('NeoForm seed root is not a plain directory')
    index = root / INDEX
    if not index.exists() and not index.is_symlink():
        return ()
    if index.is_symlink() or not index.is_file() or index.stat().st_size > 2 * 1024**2:
        raise NeoFormSeedError('NeoForm seed index is not a plain file')
    data = json.loads(index.read_text(encoding='utf-8'))
    if not isinstance(data, dict):
        raise NeoFormSeedError('NeoForm seed inventory is invalid')
    entries = data.get('files')
    if data.get('format') != 1 or not isinstance(entries, list) or not 1 <= len(entries) <= 2000:
        raise NeoFormSeedError('NeoForm seed inventory is invalid')
    files, names, total = [], set(), 0
    base = root / 'neoformruntime' / 'intermediate_results'
    if base.parent.is_symlink() or base.is_symlink():
        raise NeoFormSeedError('NeoForm seed directory is a symlink')
    for item in entries:
        if not isinstance(item, dict):
            raise NeoFormSeedError('NeoForm seed entry is invalid')
        name = item.get('name')
        size = item.get('bytes')
        expected = item.get('sha256')
        if (not isinstance(name, str) or not NAME.fullmatch(name) or name in names
                or not isinstance(size, int) or isinstance(size, bool) or size < 0
                or not isinstance(expected, str) or not re.fullmatch('[0-9a-f]{64}', expected)):
            raise NeoFormSeedError('NeoForm seed entry is invalid')
        total += size
        if total > MAX_BYTES:
            raise NeoFormSeedError('NeoForm seed exceeds size limit')
        path = base / name
        if path.is_symlink() or not path.is_file() or path.stat().st_size != size or digest(path) != expected:
            raise NeoFormSeedError('NeoForm seed content hash mismatch')
        files.append((path, expected))
        names.add(name)
    return tuple(files)


def verified_files(root: Path) -> tuple[Path, ...]:
    return tuple(path for path, _ in verified_entries(root))


def provision(source: Path, root: Path) -> None:
    """Snapshot a stopped, trusted worker cache beside an existing Loom seed."""
    provision_many((source,), root)


def provision_many(sources: Sequence[Path], root: Path) -> None:
    """Combine stopped per-version caches; refuse stale metadata and collisions."""
    if (not sources or root.is_symlink() or not root.is_dir()
            or any(source.is_symlink() or not source.is_dir() for source in sources)):
        raise NeoFormSeedError('cache and seed must be plain directories')
    if any(path.exists() or path.is_symlink() for path in (root / INDEX, root / 'neoformruntime')):
        raise NeoFormSeedError('refusing to overwrite an existing NeoForm seed')
    entries, inputs, total = {}, {}, 0
    for source in sources:
        artifacts = source / 'artifacts'
        if artifacts.is_symlink():
            raise NeoFormSeedError('NeoForm artifacts directory is a symlink')
        for metadata in artifacts.glob('minecraft_*_version_manifest.json'):
            version = metadata.name.removeprefix('minecraft_').removesuffix('_version_manifest.json')
            if version in {'.', '..'} or not re.fullmatch(r'[A-Za-z0-9._-]+', version):
                raise NeoFormSeedError('NeoForm metadata version is invalid')
            expected = root / version / 'mojang_minecraft_info.json'
            if expected.exists() or expected.is_symlink():
                if (metadata.is_symlink() or not metadata.is_file()
                        or expected.parent.is_symlink() or expected.is_symlink() or not expected.is_file()):
                    raise NeoFormSeedError('NeoForm source metadata is not a plain file')
                if digest(metadata) != digest(expected):
                    raise NeoFormSeedError(
                        f'NeoForm source metadata differs from Loom seed for {version}; '
                        'warm a fresh isolated worker with the current Loom seed before snapshotting')
        base = source / 'intermediate_results'
        if base.is_symlink() or not base.is_dir():
            raise NeoFormSeedError('missing plain intermediate_results directory')
        for path in sorted(base.iterdir()):
            if path.is_symlink() or not path.is_file() or not NAME.fullmatch(path.name):
                raise NeoFormSeedError('unexpected intermediate cache entry')
            item = {'name': path.name, 'bytes': path.stat().st_size, 'sha256': digest(path)}
            if path.name in entries:
                if entries[path.name] != item:
                    raise NeoFormSeedError('conflicting NeoForm intermediate cache entries')
                continue
            total += item['bytes']
            if total > MAX_BYTES or len(entries) >= 2000:
                raise NeoFormSeedError('NeoForm cache exceeds snapshot limits')
            entries[path.name], inputs[path.name] = item, path
    if not entries:
        raise NeoFormSeedError('NeoForm cache is empty')
    target = root / 'neoformruntime' / 'intermediate_results'
    target.mkdir(parents=True)
    inventory = [entries[name] for name in sorted(entries)]
    for item in inventory:
        shutil.copyfile(inputs[item['name']], target / item['name'])
        if digest(target / item['name']) != item['sha256']:
            raise NeoFormSeedError('NeoForm cache changed while snapshotting')
    with (root / INDEX).open('x', encoding='utf-8') as stream:
        json.dump({'format': 1, 'files': inventory}, stream, sort_keys=True)
    verified_files(root)


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-cache', type=Path, action='append', required=True,
                        help='Stopped trusted NeoForm cache; repeat for supported versions')
    parser.add_argument('--loom-seed', type=Path, required=True)
    args = parser.parse_args()
    provision_many(args.source_cache, args.loom_seed)
