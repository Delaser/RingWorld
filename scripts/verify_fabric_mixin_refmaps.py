#!/usr/bin/env python3
"""Verify each packaged Fabric Mixin config selects its compiled mapping table."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def require(condition, message):
    if not condition: raise ValueError(message)


def verify(path: Path) -> dict:
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        require(len(names) == len(set(names)), 'Duplicate archive entries')
        def read(name):
            require(name in names and jar.getinfo(name).file_size <= 4 * 1024 * 1024,
                    'Missing or oversized Mixin metadata: ' + name)
            return json.loads(jar.read(name))
        metadata = read('fabric.mod.json')
        require(metadata.get('id') == 'ringworld', 'Expected a RingWorld Fabric jar')
        maps = {name: read(name) for name in names if name.endswith('refmap.json')}
        available = {}
        for document in maps.values():
            for owner, values in document.get('mappings', {}).items():
                require(owner not in available or available[owner] == values,
                        'Conflicting generated mappings for ' + owner)
                available[owner] = values
        reports = []
        selected_owners = set()
        for entry in metadata['mixins']:
            name = entry if isinstance(entry, str) else entry['config']
            config = read(name)
            require(config.get('required') is True and config.get('injectors', {}).get('defaultRequire') == 1,
                    'Required Mixin policy weakened: ' + name)
            refmap = config.get('refmap')
            require(refmap in maps, 'Mixin config points to a missing mapping table: ' + name)
            selected = maps[refmap].get('mappings', {})
            owners = [config['package'].replace('.', '/') + '/' + simple for side in ('mixins', 'client', 'server')
                      for simple in config.get(side, [])]
            require(owners, 'Empty Mixin config: ' + name)
            for owner in owners:
                require(owner + '.class' in names, 'Missing configured Mixin class: ' + owner)
                if owner in available:
                    require(selected.get(owner) == available[owner],
                            name + ' selects ' + refmap + ' without the compiled mappings for ' + owner)
                    selected_owners.add(owner)
            reports.append({'config': name, 'refmap': refmap,
                            'mappedClasses': sum(owner in available for owner in owners)})
        # These required native method/string targets reproduced the packaged
        # startup failure. Missing AP output is itself an error, not identity mapping.
        for owner, selector in [('dev/ringworld/client/mixin/LivingEntitySleepingPositionMixin', 'getSleepingPos'),
                                ('dev/ringworld/client/mixin/MinecraftMixin', 'clearClientLevel')]:
            require(owner in selected_owners and selector in available[owner],
                    'Missing required native client mapping: ' + owner + ':' + selector)
    return {'status': 'MAPPINGS_VALID_NOT_RUNTIME_QUALIFIED', 'jarSha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'configs': reports}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    print(json.dumps(verify(parser.parse_args().jar), indent=2))
