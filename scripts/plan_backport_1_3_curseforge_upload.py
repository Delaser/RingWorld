#!/usr/bin/env python3
"""Verify the matched 1.3 stages and print upload plans; no token or network."""
from pathlib import Path
import json

try:
    from scripts import stage_modrinth_release as core
    from scripts.stage_backport_1_3_release import VERSION, LOADERS, release_profile
except ModuleNotFoundError:
    import stage_modrinth_release as core
    from stage_backport_1_3_release import VERSION, LOADERS, release_profile


def publication_plans(root: Path) -> list[dict]:
    source = core.current_public_source(root)
    plans = []
    with release_profile():
        for loader in LOADERS:
            folder = root / 'dist/backport' / VERSION / loader
            manifest = core.read_json(folder / 'STAGING-MANIFEST.json')
            if (manifest.get('loader') != loader or manifest.get('version') != VERSION
                    or manifest.get('source') != source
                    or manifest.get('game_version') != '1.21.1'
                    or manifest.get('upload_file_only') is not True):
                raise core.VerificationError('Release stage identity/provenance mismatch')
            name = manifest.get('upload_file')
            if not isinstance(name, str) or Path(name).name != name:
                raise core.VerificationError('Unsafe runtime jar filename')
            jar = folder / name
            if jar.is_symlink() or not jar.is_file() or sorted(folder.glob('*.jar')) != [jar]:
                raise core.VerificationError('Stage must contain exactly one regular runtime jar')
            if core.digest(jar, 'sha256') != manifest['hashes']['sha256']:
                raise core.VerificationError('Staged jar hash changed')
            core.validate_runtime_jar(jar, manifest['release_config'],
                                      (root / 'LICENSE').read_bytes(), loader=loader)
            changelog = (folder / 'CHANGELOG.md').read_text(encoding='utf-8')
            if changelog.count(source['url']) != 1 or '{{' in changelog:
                raise core.VerificationError('Changelog must carry exact corresponding source')
            metadata = {
                'changelog': changelog, 'changelogType': 'markdown',
                'displayName': manifest['public_name'],
                'gameVersionNames': ['Client', 'Server', '1.21.1', 'Java 21',
                                     'Fabric' if loader == 'fabric' else 'NeoForge'],
                'releaseType': 'release', 'isMarkedForManualRelease': False,
            }
            if loader == 'fabric':
                metadata['relations'] = {'projects': [{'projectID': 306612,
                    'slug': 'fabric-api', 'type': 'requiredDependency'}]}
            plans.append({'format': 1, 'dryRun': True, 'loader': loader,
                'endpoint': 'https://minecraft.curseforge.com/api/projects/1645598/upload-file',
                'method': 'POST', 'jarPath': str(jar), 'jarSha256': manifest['hashes']['sha256'],
                'source': source, 'metadata': metadata})
    return plans


if __name__ == '__main__':
    print(json.dumps(publication_plans(Path.cwd()), indent=2))
