#!/usr/bin/env python3
"""Build reproducible, credential-free optional RingWorld packages locally.

The ordinary install path remains the standalone Modrinth jar. This tool only
assembles optional Prism client bundles and a dedicated-server overlay. It has
no upload, deployment, service-control, or live-world capability.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from typing import Any, Mapping

try:
    from verify_distribution_license import (
        EXPECTED_IDENTIFIER,
        RUNTIME_DIRECTORIES,
        RUNTIME_PATH_PARTS,
        VerificationError,
        verify_artifact,
    )
    from stage_modrinth_release import (
        validate_release_config,
        validate_runtime_jar,
        validate_source_descriptor,
    )
    from minecraft_support_contract import contract_from_manifest
    from run_minecraft_qualification import load_manifest
    from release_candidate_equivalence import _verify_release_metadata
    from prism_neoforge_component import ComponentError, component_from_installer
except ModuleNotFoundError:
    from scripts.verify_distribution_license import (
        EXPECTED_IDENTIFIER,
        RUNTIME_DIRECTORIES,
        RUNTIME_PATH_PARTS,
        VerificationError,
        verify_artifact,
    )
    from scripts.stage_modrinth_release import (
        validate_release_config,
        validate_runtime_jar,
        validate_source_descriptor,
    )
    from scripts.minecraft_support_contract import contract_from_manifest
    from scripts.run_minecraft_qualification import load_manifest
    from scripts.release_candidate_equivalence import _verify_release_metadata
    from scripts.prism_neoforge_component import ComponentError, component_from_installer


SOURCE_SUFFIXES = ("-sources.jar", ".java", ".kt", ".groovy")
FIXED_ZIP_TIME = (2026, 1, 1, 0, 0, 0)
SOURCE_URL = "https://github.com/Delaser/RingWorld"
MINECRAFT_VERSION = "26.1.2"
FABRIC_LOADER_VERSION = "0.19.3"
FABRIC_API_VERSION = "0.155.2+26.1.2"
NEOFORGE_VERSION = "26.1.2.87"
STAGING_MANIFEST_NAME = "STAGING-MANIFEST.json"
STAGING_MARKER_NAME = ".ringworld-modrinth-stage"
STAGING_MANIFEST_FORMAT = 2
QUALIFIED_STAGING_MARKER_NAME = ".ringworld-qualified-stage"
QUALIFIED_STAGING_MANIFEST_FORMAT = 1
PRECONFIGURED_SERVER_NAME = "RingWorld Test Server"
PRECONFIGURED_SERVER_ADDRESS = "andwhatnotstudio.com:25565"

LOADER_SPECS = {
    "fabric": {
        "display": "Fabric",
        "ringworld_prefix": "ringworld-",
        "component": "net.fabricmc.fabric-loader",
        "component_version": FABRIC_LOADER_VERSION,
        "default_instance_template": Path("deploy/client/instance"),
        "requires_fabric_api": True,
        "server_runtime": "Fabric",
    },
    "neoforge": {
        "display": "NeoForge",
        "ringworld_prefix": "ringworld-neoforge-",
        "component": "net.neoforged",
        "component_version": NEOFORGE_VERSION,
        "default_instance_template": Path("deploy/client/instance-neoforge"),
        "requires_fabric_api": False,
        "server_runtime": "NeoForge",
    },
}


class PackageError(RuntimeError):
    pass


@dataclass(frozen=True)
class PackagePins:
    minecraft: str
    fabric_loader: str | None
    fabric_api: str | None
    fabric_api_sha256: str | None
    neoforge: str | None
    managed_profile: bool = False
    neoforge_installer_sha256: str | None = None


LEGACY_PINS = PackagePins(MINECRAFT_VERSION, FABRIC_LOADER_VERSION, FABRIC_API_VERSION, None, NEOFORGE_VERSION)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def reject_unsafe_path(path: Path, *, label: str) -> None:
    normalized = Path(path.as_posix().replace("\\", "/"))
    if normalized.is_absolute() or re.match(r"^[A-Za-z]:", normalized.as_posix()) \
            or ".." in normalized.parts:
        raise PackageError(f"{label}: unsafe path {path}")
    parts = {part.lower() for part in normalized.parts}
    if parts & RUNTIME_PATH_PARTS:
        raise PackageError(f"{label}: forbidden credential/runtime path {path}")
    if parts & RUNTIME_DIRECTORIES:
        raise PackageError(f"{label}: forbidden runtime directory {path}")
    if path.name.lower().endswith(SOURCE_SUFFIXES):
        raise PackageError(f"{label}: source artifact is not distributable: {path}")


def validate_tree(root: Path, *, label: str) -> None:
    if not root.is_dir():
        raise PackageError(f"{label}: expected directory: {root}")
    for path in root.rglob("*"):
        relative = path.relative_to(root)
        if path.is_symlink():
            raise PackageError(f"{label}: symlinks are not allowed: {relative}")
        reject_unsafe_path(relative, label=label)


def deterministic_zip(source: Path, destination: Path) -> None:
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(source.rglob("*"), key=lambda item: item.as_posix()):
            if not path.is_file():
                continue
            relative = path.relative_to(source).as_posix()
            info = zipfile.ZipInfo(relative, FIXED_ZIP_TIME)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            mode = 0o755 if path.stat().st_mode & 0o111 else 0o644
            info.external_attr = mode << 16
            archive.writestr(info, path.read_bytes())


def read_fabric_metadata(jar: Path, *, label: str) -> dict[str, object]:
    if not jar.is_file() or jar.is_symlink():
        raise PackageError(f"{label}: expected regular jar: {jar}")
    reject_unsafe_path(Path(jar.name), label=label)
    try:
        with zipfile.ZipFile(jar) as archive:
            return json.loads(archive.read("fabric.mod.json"))
    except (KeyError, ValueError, zipfile.BadZipFile) as exc:
        raise PackageError(f"{label}: invalid fabric.mod.json in {jar}") from exc


def _qualified_pins(manifest_path: Path, runtime_cell: str, loader: str, staged_versions: object) -> PackagePins:
    """Derive one explicit package runtime profile from the reviewed matrix."""
    if not isinstance(staged_versions, list) or not all(isinstance(item, str) for item in staged_versions):
        raise PackageError("qualified staging manifest has invalid game_versions")
    try:
        manifest = load_manifest(manifest_path)
        contract = contract_from_manifest(manifest)
    except (OSError, ValueError) as exc:
        raise PackageError(f"qualified package manifest is invalid: {exc}") from exc
    if staged_versions != list(contract.versions):
        raise PackageError("qualified staging game versions do not match reviewed manifest")
    cells = manifest.get("cells")
    matches = [cell for cell in cells if isinstance(cell, Mapping) and cell.get("id") == runtime_cell] \
        if isinstance(cells, list) else []
    if len(matches) != 1 or matches[0].get("loader") != loader:
        raise PackageError("qualified runtime cell is missing or belongs to another loader")
    cell = matches[0]
    minecraft = cell.get("minecraft")
    dependencies = cell.get("dependencies")
    if not isinstance(minecraft, Mapping) or not isinstance(minecraft.get("version"), str) \
            or not isinstance(dependencies, list) or minecraft["version"] not in staged_versions:
        raise PackageError("qualified runtime cell has invalid pins")
    by_name = {item.get("name"): item for item in dependencies if isinstance(item, Mapping)}
    if len(by_name) != len(dependencies):
        raise PackageError("qualified runtime cell has duplicate or malformed dependencies")
    def dependency(name: str) -> Mapping[str, Any]:
        value = by_name.get(name)
        if not isinstance(value, Mapping) or not isinstance(value.get("version"), str):
            raise PackageError(f"qualified runtime cell is missing {name} pin")
        return value
    if loader == "fabric":
        fabric_loader, fabric_api = dependency("Fabric Loader"), dependency("Fabric API")
        checksum = fabric_api.get("checksum")
        if not isinstance(checksum, Mapping) or checksum.get("algorithm") != "sha256" \
                or not isinstance(checksum.get("value"), str) \
                or re.fullmatch(r"[0-9a-f]{64}", checksum["value"]) is None:
            raise PackageError("qualified Fabric API pin has invalid SHA-256")
        return PackagePins(minecraft["version"], fabric_loader["version"], fabric_api["version"],
                           checksum["value"], None, managed_profile=True)
    neoforge = dependency("NeoForge")
    installer = cell.get("runtime_install", {})
    checksum = installer.get("checksum", {})
    if installer.get("version") != neoforge["version"] or checksum.get("algorithm") != "sha256":
        raise PackageError("qualified NeoForge installer does not match loader pin")
    return PackagePins(minecraft["version"], None, None, None, neoforge["version"], managed_profile=True,
                       neoforge_installer_sha256=checksum.get("value"))


def load_staged_release(
    manifest_path: Path, *, loader: str, expected_license: bytes,
    qualification_manifest: Path | None = None, runtime_cell: str | None = None,
) -> tuple[Path, str, str, str, str, PackagePins]:
    """Return the exact staged jar, artifact/public versions, name, and source revision.

    Optional bundles are deliberately downstream of the local Modrinth review
    stage.  They must not be able to relabel an arbitrary jar with a caller
    supplied commit id, or quietly use an old build output.
    """
    if manifest_path.name != STAGING_MANIFEST_NAME or not manifest_path.is_file() \
            or manifest_path.is_symlink():
        raise PackageError(f"expected a regular {STAGING_MANIFEST_NAME}: {manifest_path}")
    stage = manifest_path.parent
    try:
        staged = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise PackageError(f"invalid staging manifest: {manifest_path}") from exc
    if not isinstance(staged, dict):
        raise PackageError("staging manifest must be a JSON object")
    qualified = staged.get("format") == QUALIFIED_STAGING_MANIFEST_FORMAT
    marker_name = QUALIFIED_STAGING_MARKER_NAME if qualified else STAGING_MARKER_NAME
    marker = stage.parent / marker_name if qualified else stage / marker_name
    if not marker.is_file() or marker.is_symlink() or marker.read_text(encoding="utf-8") != "generated\n":
        raise PackageError("staging manifest is not in a generated RingWorld review directory")
    if staged.get("generated") is not True or staged.get("upload_file_only") is not True:
        raise PackageError("staging manifest is not a current generated runtime-artifact record")
    if staged.get("loader") != loader:
        raise PackageError(f"staging manifest is for {staged.get('loader')!r}, not {loader}")
    source = staged.get("source")
    if not isinstance(source, dict):
        raise PackageError("staging manifest is missing its validated source")
    try:
        validate_source_descriptor(source)
    except VerificationError as exc:
        raise PackageError(str(exc)) from exc
    config = staged.get("release_config")
    if qualified:
        if qualification_manifest is None or runtime_cell is None:
            raise PackageError("qualified staging requires --qualification-manifest and --runtime-cell")
        pins = _qualified_pins(qualification_manifest, runtime_cell, loader, staged.get("game_versions"))
        artifact, label = staged.get("artifact_version"), staged.get("release_label")
        if not isinstance(artifact, str) or not isinstance(label, str):
            raise PackageError("qualified staging manifest has invalid public identity")
    else:
        if staged.get("format") != STAGING_MANIFEST_FORMAT or not isinstance(config, dict):
            raise PackageError("staging manifest is not a current generated runtime-artifact record")
        try:
            validate_release_config(config, loader)
        except VerificationError as exc:
            raise PackageError(str(exc)) from exc
        pins, artifact, label = LEGACY_PINS, config["version"].get("artifact_version"), None
    filename = staged.get("upload_file")
    if not isinstance(filename, str) or not filename or Path(filename).name != filename \
            or filename in {".", ".."}:
        raise PackageError("staging manifest has an unsafe upload_file")
    release_jar = stage / filename
    if not release_jar.is_file() or release_jar.is_symlink():
        raise PackageError(f"staged runtime jar is missing: {release_jar}")
    hashes = staged.get("hashes")
    if not isinstance(hashes, dict):
        raise PackageError("staging manifest hashes are required")
    for algorithm, expected_length in (("sha256", 64), ("sha512", 128)):
        expected = hashes.get(algorithm)
        if not isinstance(expected, str) or not re.fullmatch(rf"[0-9a-f]{{{expected_length}}}", expected):
            raise PackageError(f"staging manifest has invalid {algorithm}")
        actual = hashlib.new(algorithm, release_jar.read_bytes()).hexdigest()
        if actual != expected:
            raise PackageError(f"staged runtime jar {algorithm} does not match staging manifest")
    if staged.get("size") != release_jar.stat().st_size:
        raise PackageError("staged runtime jar size does not match staging manifest")
    try:
        if qualified:
            contract = contract_from_manifest(load_manifest(qualification_manifest))
            _verify_release_metadata(release_jar, loader, expected_license, artifact, label, contract)
            runtime = {"id": "ringworld", "version": artifact}
        else:
            runtime = validate_runtime_jar(release_jar, config, expected_license, loader=loader)
    except (VerificationError, ValueError) as exc:
        raise PackageError(str(exc)) from exc
    version = artifact
    if not isinstance(version, str) or not re.fullmatch(r"[0-9A-Za-z.+_-]+", version):
        raise PackageError("staging release config has an unsafe artifact_version")
    if qualified:
        public_version = f"{artifact.split('+mc', 1)[0]}-{loader}+mc{pins.minecraft}"
        public_name = f"RingWorld {label} for Minecraft {pins.minecraft} ({LOADER_SPECS[loader]['display']})"
        return release_jar, version, public_version, public_name, source["revision"], pins
    expected_fields = {
        "mod_id": runtime["id"],
        "version": runtime["version"],
        "game_version": config[loader]["minecraft"],
        "environment": config["version"]["environment"],
        "public_version": config["version"]["version_number"],
        "public_name": config["version"]["name"],
    }
    for key, expected in expected_fields.items():
        if staged.get(key) != expected:
            raise PackageError(f"staging manifest {key} does not match its validated runtime artifact")
    return (
        release_jar,
        version,
        config["version"]["version_number"],
        config["version"]["name"],
        source["revision"], LEGACY_PINS,
    )


def validate_inputs(
    loader: str,
    release_jar: Path,
    fabric_api: Path | None,
    instance_template: Path,
    license_file: Path,
    pins: PackagePins,
) -> bytes:
    spec = LOADER_SPECS[loader]
    expected_license = license_file.read_bytes()
    if not expected_license:
        raise PackageError(f"empty licence file: {license_file}")
    if not release_jar.name.startswith(spec["ringworld_prefix"]):
        raise PackageError(
            f"{spec['display']} RingWorld jar filename must start with "
            f"{spec['ringworld_prefix']}"
        )
    if spec["requires_fabric_api"]:
        if pins.fabric_api is None:
            raise PackageError("qualified Fabric package is missing Fabric API pin")
        if fabric_api is None:
            raise PackageError("Fabric packages require --fabric-api")
        fabric_metadata = read_fabric_metadata(fabric_api, label="Fabric API jar")
        if not fabric_api.name.startswith("fabric-api-"):
            raise PackageError("Fabric API jar filename must start with fabric-api-")
        if fabric_metadata.get("id") != "fabric-api":
            raise PackageError("Fabric API input does not declare id fabric-api")
        if fabric_metadata.get("version") != pins.fabric_api:
            raise PackageError(
                f"Fabric API version {fabric_metadata.get('version')!r} does not match "
                f"{pins.fabric_api!r}"
            )
        if pins.fabric_api_sha256 is not None and sha256(fabric_api) != pins.fabric_api_sha256:
            raise PackageError("Fabric API SHA-256 does not match qualified runtime pin")
    elif fabric_api is not None:
        raise PackageError("NeoForge packages must not include --fabric-api")
    validate_tree(instance_template, label="instance template")
    if any((instance_template / name).exists() for name in
           ("patches/net.neoforged.json", "ringworld-managed-neoforge-patch.txt")):
        raise PackageError("NeoForge component overrides must be generated from --neoforge-installer")
    required = (
        instance_template / "mmc-pack.json",
        instance_template / "instance.cfg",
        instance_template / ".minecraft" / "config" / "ringworld.properties",
    )
    missing = [str(path.relative_to(instance_template)) for path in required if not path.is_file()]
    if missing:
        raise PackageError("instance template is missing " + ", ".join(missing))
    try:
        pack = json.loads((instance_template / "mmc-pack.json").read_text(encoding="utf-8"))
        raw_components = pack["components"]
        if not isinstance(raw_components, list) or any(
                not isinstance(component, dict) or not isinstance(component.get("uid"), str)
                or not isinstance(component.get("version"), str) for component in raw_components):
            raise ValueError
        component_ids = [component["uid"] for component in raw_components]
        if len(component_ids) != len(set(component_ids)):
            raise PackageError("instance template has duplicate Prism component UID")
        opposite = "net.neoforged" if loader == "fabric" else "net.fabricmc.fabric-loader"
        if opposite in component_ids:
            raise PackageError("instance template contains the opposite loader component")
        components = {component["uid"]: component["version"] for component in raw_components}
    except (KeyError, TypeError, ValueError) as exc:
        raise PackageError("instance template has invalid mmc-pack.json") from exc
    expected_components = {
        "net.minecraft": pins.minecraft,
        spec["component"]: pins.fabric_loader if loader == "fabric" else pins.neoforge,
    }
    for component, expected in expected_components.items():
        if not isinstance(expected, str):
            raise PackageError(f"qualified {loader} package is missing its selected loader pin")
        if component not in components or (not pins.managed_profile and components[component] != expected):
            raise PackageError(
                f"instance template {component} version {components.get(component)!r} "
                f"does not match {expected!r}"
            )
    instance_config = (instance_template / "instance.cfg").read_text(encoding="utf-8")
    required_settings = (
        "AutomaticJava=true",
        "OverrideJavaLocation=false",
        "JoinServerOnLaunch=false",
    )
    for setting in required_settings:
        if not re.search(rf"(?m)^{re.escape(setting)}$", instance_config):
            raise PackageError(f"instance template must declare {setting}")
    for name in ("LICENSE", "LICENSE-RINGWORLD.txt"):
        candidate = instance_template / name
        if candidate.is_file() and candidate.read_bytes() != expected_license:
            raise PackageError(f"instance template contains a stale {name}")
    mods = instance_template / ".minecraft" / "mods"
    if mods.exists() and (list(mods.glob("ringworld-*.jar")) or list(mods.glob("fabric-api-*.jar"))):
        raise PackageError("instance template contains a managed RingWorld or Fabric API jar")
    return expected_license


def apply_runtime_profile(instance: Path, *, loader: str, pins: PackagePins) -> None:
    """Rewrite only managed Prism component versions after template validation."""
    pack_path = instance / "mmc-pack.json"
    try:
        pack = json.loads(pack_path.read_text(encoding="utf-8"))
        components = pack["components"]
        if not isinstance(components, list):
            raise ValueError
    except (KeyError, TypeError, ValueError) as exc:
        raise PackageError("instance template has invalid mmc-pack.json") from exc
    expected = {
        "net.minecraft": pins.minecraft,
        "net.fabricmc.fabric-loader" if loader == "fabric" else "net.neoforged":
            pins.fabric_loader if loader == "fabric" else pins.neoforge,
    }
    if not all(isinstance(value, str) for value in expected.values()):
        raise PackageError(f"qualified {loader} package is missing its selected loader pin")
    seen: set[str] = set()
    all_uids: set[str] = set()
    opposite = "net.neoforged" if loader == "fabric" else "net.fabricmc.fabric-loader"
    for component in components:
        if not isinstance(component, dict) or not isinstance(component.get("uid"), str):
            raise PackageError("instance template has invalid component entry")
        uid = component["uid"]
        if uid in all_uids:
            raise PackageError("instance template has duplicate Prism component UID")
        if uid == opposite:
            raise PackageError("instance template contains the opposite loader component")
        all_uids.add(uid)
        if uid in expected:
            component["version"] = expected[uid]
            seen.add(uid)
    if seen != set(expected):
        raise PackageError("instance template cannot receive selected runtime profile")
    pack_path.write_text(json.dumps(pack, indent=2) + "\n", encoding="utf-8")


def render_runtime_text(path: Path, *, pins: PackagePins, loader: str, version: str) -> None:
    """Keep copied operator text accurate without altering checked-in templates."""
    if not path.is_file():
        return
    text = path.read_text(encoding="utf-8")
    replacements = {
        "{{MINECRAFT_VERSION}}": pins.minecraft,
        "{{RINGWORLD_VERSION}}": version,
        "{{FABRIC_LOADER_VERSION}}": pins.fabric_loader,
        "{{FABRIC_API_VERSION}}": pins.fabric_api,
        "{{NEOFORGE_VERSION}}": pins.neoforge,
    }
    for token, value in replacements.items():
        if token in text:
            if value is None:
                raise PackageError(f"{path.name} requires unavailable runtime value {token}")
            text = text.replace(token, value)
    # Retain compatibility with historical checked-in templates and locally
    # staged package inputs that predate explicit runtime tokens.
    text = text.replace("Minecraft 26.1.2", f"Minecraft {pins.minecraft}")
    if loader == "fabric" and pins.fabric_api is not None:
        text = text.replace("Fabric API 0.155.2+26.1.2", f"Fabric API {pins.fabric_api}")
    if loader == "neoforge" and pins.neoforge is not None:
        text = text.replace("NeoForge 26.1.2.87", f"NeoForge {pins.neoforge}")
    text = text.replace("RingWorld 1.0.0+mc26.1.2", f"RingWorld {version}")
    if "{{" in text or "}}" in text:
        raise PackageError(f"{path.name} contains an unresolved runtime template token")
    path.write_text(text, encoding="utf-8")


def manifest(
    *, loader: str, kind: str, version: str, source_revision: str,
    release_jar: Path, fabric_api: Path | None, pins: PackagePins,
) -> dict[str, object]:
    result: dict[str, object] = {
        "format": 2,
        "kind": kind,
        "license": EXPECTED_IDENTIFIER,
        "version": version,
        "minecraft": pins.minecraft,
        "loader": loader,
        "sourceRevision": source_revision,
        "sourceUrl": f"{SOURCE_URL}/tree/{source_revision}",
        "ringworldJar": release_jar.name,
        "ringworldSha256": sha256(release_jar),
        "preconfiguredServer": {
            "name": PRECONFIGURED_SERVER_NAME,
            "address": PRECONFIGURED_SERVER_ADDRESS,
            "autoJoin": False,
        },
    }
    if fabric_api is not None:
        result["fabricApiJar"] = fabric_api.name
        result["fabricApiSha256"] = sha256(fabric_api)
    return result


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def nbt_string(value: str) -> bytes:
    encoded = value.encode("utf-8")
    if len(encoded) > 0xFFFF:
        raise PackageError("preconfigured server field is too long")
    return struct.pack(">H", len(encoded)) + encoded


def write_preconfigured_server_list(destination: Path) -> None:
    """Write a deterministic Minecraft servers.dat containing the public demo.

    This is intentionally generated instead of copied from a used client
    profile. It contains only the public server name/address, never account,
    history, resource-pack, or other player runtime data. Launchers copy it
    only while creating their managed instance, so an existing user's server
    list is never replaced.
    """
    server = (
        b"\x08" + nbt_string("name") + nbt_string(PRECONFIGURED_SERVER_NAME)
        + b"\x08" + nbt_string("ip") + nbt_string(PRECONFIGURED_SERVER_ADDRESS)
        + b"\x00"
    )
    payload = (
        b"\x0a\x00\x00"  # unnamed root compound
        + b"\x09" + nbt_string("servers") + b"\x0a" + struct.pack(">i", 1)
        + server
        + b"\x00"
    )
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(gzip.compress(payload, compresslevel=9, mtime=0))


def populate_instance(
    instance_template: Path, destination: Path, release_jar: Path, fabric_api: Path | None,
    *, loader: str, pins: PackagePins, version: str,
) -> None:
    shutil.copytree(instance_template, destination)
    apply_runtime_profile(destination, loader=loader, pins=pins)
    mods = destination / ".minecraft" / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    shutil.copy2(release_jar, mods / release_jar.name)
    if fabric_api is not None:
        shutil.copy2(fabric_api, mods / fabric_api.name)
    write_preconfigured_server_list(destination / ".minecraft" / "servers.dat")


def add_client_package(
    staging: Path,
    *,
    loader: str,
    platform: str,
    instance_template: Path,
    release_jar: Path,
    fabric_api: Path | None,
    license_file: Path,
    expected_license: bytes,
    launcher_dir: Path,
    version: str,
    source_revision: str,
    pins: PackagePins,
    neoforge_component: dict | None = None,
) -> Path:
    archive_name = f"RingWorld-{version}-{LOADER_SPECS[loader]['display']}-Client-{platform}.zip"
    with tempfile.TemporaryDirectory(prefix="ringworld-client-") as directory:
        root = Path(directory) / "bundle"
        root.mkdir()
        shutil.copy2(license_file, root / "LICENSE")
        # This marker is consumed from archive bytes and must not vary with the
        # host platform's text newline translation.
        (root / "RINGWORLD-LOADER.txt").write_bytes(loader.encode("ascii") + b"\n")
        launchers = {
            "macOS-universal": ("Launch RingWorld.command", "launch-ringworld.sh"),
            "Windows": ("Launch RingWorld.bat",),
        }[platform]
        for launcher in launchers:
            source = launcher_dir / launcher
            if not source.is_file():
                raise PackageError(f"missing launcher template: {source}")
            shutil.copy2(source, root / launcher)
        instance = root / "instance"
        populate_instance(instance_template, instance, release_jar, fabric_api,
                          loader=loader, pins=pins, version=version)
        if neoforge_component is not None:
            (instance / "patches").mkdir(exist_ok=True)
            write_json(instance / "patches/net.neoforged.json", neoforge_component)
            (instance / "ringworld-managed-neoforge-patch.txt").write_bytes(b"RingWorld managed NeoForge component\n")
        for launcher in launchers:
            render_runtime_text(root / launcher, pins=pins, loader=loader, version=version)

        nested_root = Path(directory) / "prism-instance"
        shutil.copytree(instance, nested_root)
        shutil.copy2(license_file, nested_root / "LICENSE")
        deterministic_zip(nested_root, root / "RingWorld-Prism-Instance.zip")

        package_manifest = manifest(
            loader=loader, kind=f"client-{platform}", version=version, source_revision=source_revision,
            release_jar=release_jar, fabric_api=fabric_api, pins=pins,
        )
        if neoforge_component is not None:
            package_manifest["prismComponent"] = {
                "path": "patches/net.neoforged.json",
                "sha256": sha256(instance / "patches/net.neoforged.json"),
                "installerSha256": pins.neoforge_installer_sha256,
                "source": "official NeoForge installer; Prism native custom component",
            }
        write_json(root / "PACKAGE-MANIFEST.json", package_manifest)
        (root / "README-FIRST.txt").write_text(
            f"RingWorld optional {LOADER_SPECS[loader]['display']} Prism client bundle.\n"
            "Run the platform launcher, or import RingWorld-Prism-Instance.zip into Prism Launcher.\n"
            f"{PRECONFIGURED_SERVER_NAME} ({PRECONFIGURED_SERVER_ADDRESS}) is already in the server list.\n"
            "This bundle does not auto-join a server. Existing accounts, saves, settings, and server list are preserved.\n"
            f"Source: {package_manifest['sourceUrl']}\n",
            encoding="utf-8",
        )
        output = staging / archive_name
        deterministic_zip(root, output)
    verify_artifact(output, expected_license, kind="client")
    return output


def add_server_package(
    staging: Path,
    *,
    loader: str,
    server_template: Path,
    release_jar: Path,
    fabric_api: Path | None,
    license_file: Path,
    expected_license: bytes,
    version: str,
    source_revision: str,
    pins: PackagePins,
) -> Path:
    validate_tree(server_template, label="server template")
    archive_name = f"RingWorld-{version}-{LOADER_SPECS[loader]['display']}-Server-Overlay.zip"
    with tempfile.TemporaryDirectory(prefix="ringworld-server-") as directory:
        root = Path(directory) / "server-overlay"
        shutil.copytree(server_template, root)
        loader_deployment = root / f"DEPLOYMENT-{loader}.md"
        if not loader_deployment.is_file():
            raise PackageError(f"server template is missing {loader_deployment.name}")
        shutil.copy2(loader_deployment, root / "DEPLOYMENT.md")
        for candidate in root.glob("DEPLOYMENT-*.md"):
            candidate.unlink()
        loader_service = root / f"ringworld-{loader}.service"
        if not loader_service.is_file():
            raise PackageError(f"server template is missing {loader_service.name}")
        shutil.copy2(loader_service, root / "ringworld.service")
        for candidate in root.glob("ringworld-*.service"):
            candidate.unlink()
        properties = root / "server.properties.example"
        if properties.is_file():
            properties.write_text(
                properties.read_text(encoding="utf-8").replace(
                    "RingWorld — Fabric 26.1.2",
                    f"RingWorld — {LOADER_SPECS[loader]['server_runtime']} {pins.minecraft}",
                ),
                encoding="utf-8",
            )
        for text_path in (root / "DEPLOYMENT.md", root / "ringworld.service"):
            render_runtime_text(text_path, pins=pins, loader=loader, version=version)
        shutil.copy2(license_file, root / "LICENSE")
        mods = root / "mods"
        mods.mkdir(exist_ok=True)
        shutil.copy2(release_jar, mods / release_jar.name)
        if fabric_api is not None:
            shutil.copy2(fabric_api, mods / fabric_api.name)
        package_manifest = manifest(
            loader=loader, kind="server-overlay", version=version, source_revision=source_revision,
            release_jar=release_jar, fabric_api=fabric_api, pins=pins,
        )
        package_manifest["installNote"] = (
            "Overlay only; obtain Minecraft and "
            f"{LOADER_SPECS[loader]['server_runtime']} server components from official sources."
        )
        write_json(root / "PACKAGE-MANIFEST.json", package_manifest)
        output = staging / archive_name
        deterministic_zip(root, output)
    verify_artifact(output, expected_license, kind="server")
    return output


def build_packages(args: argparse.Namespace) -> tuple[Path, ...]:
    expected_license = args.license.read_bytes()
    if not expected_license:
        raise PackageError(f"empty licence file: {args.license}")
    (
        args.jar,
        args.version,
        args.public_version,
        args.public_name,
        args.source_revision,
        pins,
    ) = load_staged_release(
        args.stage_manifest, loader=args.loader, expected_license=expected_license,
        qualification_manifest=args.qualification_manifest, runtime_cell=args.runtime_cell,
    )
    expected_license = validate_inputs(
        args.loader, args.jar, args.fabric_api, args.instance_template, args.license, pins,
    )
    validate_tree(args.launcher_dir, label="launcher templates")
    neoforge_component = None
    installer = getattr(args, "neoforge_installer", None)
    if installer is not None:
        if args.loader != "neoforge" or not pins.managed_profile:
            raise PackageError("--neoforge-installer requires a qualified NeoForge stage/runtime cell")
        try:
            neoforge_component = component_from_installer(
                installer, minecraft=pins.minecraft, version=pins.neoforge,
                expected_sha256=pins.neoforge_installer_sha256,
            )
        except ComponentError as exc:
            raise PackageError(str(exc)) from exc
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="ringworld-release-", dir=args.output.parent) as directory:
        staging = Path(directory) / "staging"
        staging.mkdir()
        universal = add_client_package(
            staging, loader=args.loader, platform="macOS-universal", instance_template=args.instance_template,
            release_jar=args.jar, fabric_api=args.fabric_api, license_file=args.license,
            expected_license=expected_license, launcher_dir=args.launcher_dir,
            version=args.version, source_revision=args.source_revision,
            pins=pins,
            neoforge_component=neoforge_component,
        )
        windows = add_client_package(
            staging, loader=args.loader, platform="Windows", instance_template=args.instance_template,
            release_jar=args.jar, fabric_api=args.fabric_api, license_file=args.license,
            expected_license=expected_license, launcher_dir=args.launcher_dir,
            version=args.version, source_revision=args.source_revision,
            pins=pins,
            neoforge_component=neoforge_component,
        )
        server = add_server_package(
            staging, loader=args.loader, server_template=args.server_template, release_jar=args.jar,
            fabric_api=args.fabric_api, license_file=args.license,
            expected_license=expected_license, version=args.version,
            source_revision=args.source_revision, pins=pins,
        )
        artifacts = (universal, windows, server)
        checksums = "".join(f"{sha256(path)}  {path.name}\n" for path in artifacts)
        (staging / "SHA256SUMS.txt").write_text(checksums, encoding="utf-8")
        write_json(
            staging / "RELEASE-MANIFEST.json",
            {
                "format": 1,
                "license": EXPECTED_IDENTIFIER,
                "loader": args.loader,
                "artifactVersion": args.version,
                "publicVersion": args.public_version,
                "publicName": args.public_name,
                "sourceRevision": args.source_revision,
                "sourceUrl": f"{SOURCE_URL}/tree/{args.source_revision}",
                "artifacts": [
                    {"name": path.name, "sha256": sha256(path)} for path in artifacts
                ],
            },
        )
        staging.rename(args.output)
    return tuple(args.output / path.name for path in artifacts)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--loader", choices=tuple(LOADER_SPECS), required=True)
    parser.add_argument(
        "--stage-manifest", required=True, type=Path,
        help="generated format-2 legacy or format-1 qualified STAGING-MANIFEST.json",
    )
    parser.add_argument("--qualification-manifest", type=Path,
                        help="reviewed matrix required only for format-1 qualified staging")
    parser.add_argument("--runtime-cell", help="exact matrix cell used for optional package runtime pins")
    parser.add_argument("--fabric-api", type=Path)
    parser.add_argument("--neoforge-installer", type=Path,
                        help="optional exact pinned official installer: embed Prism component metadata, not binaries")
    parser.add_argument(
        "--instance-template", type=Path
    )
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--license", type=Path, default=Path("LICENSE"))
    parser.add_argument("--launcher-dir", type=Path, default=Path("deploy/client"))
    parser.add_argument("--server-template", type=Path, default=Path("deploy/server"))
    args = parser.parse_args()
    if args.instance_template is None:
        args.instance_template = LOADER_SPECS[args.loader]["default_instance_template"]
    if args.output.exists():
        print(f"FAIL output directory already exists: {args.output}", file=sys.stderr)
        return 1
    try:
        artifacts = build_packages(args)
    except (OSError, PackageError, VerificationError, zipfile.BadZipFile) as exc:
        shutil.rmtree(args.output, ignore_errors=True)
        print(f"FAIL {exc}", file=sys.stderr)
        return 1
    for artifact in artifacts:
        print(f"PASS {artifact} {sha256(artifact)}")
    print(f"PASS checksums {args.output / 'SHA256SUMS.txt'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
