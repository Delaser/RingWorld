"""Hash-checked Mojang asset seeding for isolated Gradle homes.

The seed is only an acceleration input.  Callers still run Gradle online and
must validate the external cache path with their qualification policy.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import shutil
from typing import Callable, Optional, Sequence


class LoomSeedError(RuntimeError):
    """The optional cache seed cannot safely be used."""


CacheValidator = Callable[[Optional[Path], Path], Optional[Path]]


def _file_hash(path: Path, algorithm: str) -> str:
    digest = hashlib.new(algorithm)
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def validated_loom_seed(cache: Path | None, repository_root: Path, version: str,
                        *, cache_validator: CacheValidator) -> tuple[Path, ...]:
    """Return only independently hash-checked Mojang files from ``cache``."""
    if cache is None:
        return ()
    resolved = cache_validator(cache, repository_root)
    if resolved is None:
        raise LoomSeedError("Loom seed cache was not accepted")
    manifest = resolved / "mojang_versions_manifest.json"
    version_directory = resolved / version
    metadata = version_directory / "mojang_minecraft_info.json"
    required = (manifest, metadata, version_directory / "minecraft-client.jar",
                version_directory / "minecraft-server.jar")
    if any(path.is_symlink() or not path.is_file() for path in required):
        raise LoomSeedError("Loom seed is missing a plain required file")
    try:
        manifest_data = json.loads(manifest.read_text(encoding="utf-8"))
        entry = next(item for item in manifest_data["versions"] if item["id"] == version)
        if _file_hash(metadata, "sha1") != entry["sha1"]:
            raise LoomSeedError("Loom seed version metadata SHA-1 mismatch")
        metadata_data = json.loads(metadata.read_text(encoding="utf-8"))
        for side in ("client", "server"):
            path = version_directory / f"minecraft-{side}.jar"
            expected = metadata_data["downloads"][side]
            if path.stat().st_size != int(expected["size"]) or _file_hash(path, "sha1") != expected["sha1"]:
                raise LoomSeedError(f"Loom seed {side} jar identity mismatch")
        asset = metadata_data["assetIndex"]
        asset_index = resolved / "assets/indexes" / f"{version}-{asset['id']}.json"
        if asset_index.is_symlink() or not asset_index.is_file():
            raise LoomSeedError("Loom seed asset index is missing")
        if asset_index.stat().st_size != int(asset["size"]) or _file_hash(asset_index, "sha1") != asset["sha1"]:
            raise LoomSeedError("Loom seed asset index identity mismatch")
        asset_data = json.loads(asset_index.read_text(encoding="utf-8"))
        asset_files: list[Path] = [asset_index]
        for item in asset_data["objects"].values():
            asset_hash = item["hash"]
            if not isinstance(asset_hash, str) or not re.fullmatch(r"[0-9a-f]{40}", asset_hash):
                raise LoomSeedError("Loom seed asset hash is malformed")
            asset_file = resolved / "assets/objects" / asset_hash[:2] / asset_hash
            if asset_file.is_symlink() or not asset_file.is_file():
                raise LoomSeedError("Loom seed asset object is missing")
            if asset_file.stat().st_size != int(item["size"]) or _file_hash(asset_file, "sha1") != asset_hash:
                raise LoomSeedError("Loom seed asset object identity mismatch")
            asset_files.append(asset_file)
    except LoomSeedError:
        raise
    except (KeyError, StopIteration, TypeError, ValueError, json.JSONDecodeError) as error:
        raise LoomSeedError("Loom seed metadata is malformed") from error
    return (*required, *asset_files)


def stage_loom_seed(files: Sequence[Path], gradle_home: Path, version: str,
                    loader: str = "fabric") -> None:
    """Copy only validated seed files into the selected loader's cache layout."""
    if loader not in {"fabric", "neoforge"}:
        raise LoomSeedError("unsupported loader")
    if not files:
        return
    if loader == "neoforge":
        if len(files) < 5:
            raise LoomSeedError("Loom seed has no validated asset index")
        manifest, metadata, client, server, asset_index, *asset_objects = files
        version_name = f"minecraft_{version}"
        expected_sources = (
            (manifest, "mojang_versions_manifest.json"),
            (metadata, "mojang_minecraft_info.json"),
            (client, "minecraft-client.jar"),
            (server, "minecraft-server.jar"),
        )
        if any(source.name != expected_name for source, expected_name in expected_sources):
            raise LoomSeedError("Loom seed Mojang file layout is invalid")
        expected_prefix = f"{version}-"
        if (asset_index.parent.name != "indexes" or asset_index.parent.parent.name != "assets"
                or not asset_index.name.startswith(expected_prefix)
                or not asset_index.name.endswith(".json")):
            raise LoomSeedError("Loom seed asset index layout is invalid")
        asset_id = asset_index.name.removeprefix(expected_prefix).removesuffix(".json")
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", asset_id):
            raise LoomSeedError("Loom seed asset index filename is unsafe")
        source_assets = asset_index.parent.parent
        runtime_cache = gradle_home / "caches/neoformruntime"
        artifacts = runtime_cache / "artifacts"
        for source, target_name in (
            (manifest, "minecraft_launcher_manifest.json"),
            (metadata, f"{version_name}_version_manifest.json"),
            (client, f"{version_name}_client.jar"),
            (server, f"{version_name}_server.jar"),
        ):
            artifacts.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, artifacts / target_name)
        destination = runtime_cache / "assets"
        target_index = destination / "indexes" / f"{asset_id}.json"
        target_index.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(asset_index, target_index)
        for source in asset_objects:
            try:
                relative = source.relative_to(source_assets / "objects")
            except ValueError as error:
                raise LoomSeedError("Loom seed asset object layout is invalid") from error
            target = destination / "objects" / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
        return
    source_root = files[0].parent
    destination = gradle_home / "caches/fabric-loom"
    for source in files:
        relative = source.relative_to(source_root)
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
