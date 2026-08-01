#!/usr/bin/env python3
"""Build reproducible, credential-free optional RingWorld packages locally.

The ordinary install path remains the standalone Modrinth jar. This tool only
assembles optional Prism client bundles and a dedicated-server overlay. It has
no upload, deployment, service-control, or live-world capability.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
import tempfile
import zipfile

from verify_distribution_license import (
    EXPECTED_IDENTIFIER,
    RUNTIME_DIRECTORIES,
    RUNTIME_PATH_PARTS,
    VerificationError,
    verify_artifact,
)


SOURCE_SUFFIXES = ("-sources.jar", ".java", ".kt", ".groovy")
FIXED_ZIP_TIME = (2026, 1, 1, 0, 0, 0)
SOURCE_URL = "https://github.com/Delaser/RingWorld"
MINECRAFT_VERSION = "26.1.2"
FABRIC_LOADER_VERSION = "0.19.3"
FABRIC_API_VERSION = "0.155.2+26.1.2"
COMPATIBILITY_API_VERSION = 1


class PackageError(RuntimeError):
    pass


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


def validate_inputs(
    release_jar: Path,
    fabric_api: Path,
    instance_template: Path,
    license_file: Path,
    version: str,
    source_revision: str,
) -> bytes:
    if not re.fullmatch(r"[0-9A-Za-z.+_-]+", version):
        raise PackageError("version contains unsafe filename characters")
    if not re.fullmatch(r"[0-9a-f]{40}", source_revision):
        raise PackageError("source revision must be a full 40-character lowercase commit id")
    expected_license = license_file.read_bytes()
    if not expected_license:
        raise PackageError(f"empty licence file: {license_file}")
    try:
        verify_artifact(release_jar, expected_license, kind="jar")
    except VerificationError as exc:
        raise PackageError(str(exc)) from exc
    release_metadata = read_fabric_metadata(release_jar, label="RingWorld jar")
    if not release_jar.name.startswith("ringworld-"):
        raise PackageError("RingWorld jar filename must start with ringworld-")
    if release_metadata.get("id") != "ringworld":
        raise PackageError("release jar is not the RingWorld mod")
    if release_metadata.get("version") != version:
        raise PackageError(
            f"release jar version {release_metadata.get('version')!r} does not match {version!r}"
        )
    custom = release_metadata.get("custom")
    if not isinstance(custom, dict) or custom.get("ringworld:compatibility_api") \
            != COMPATIBILITY_API_VERSION:
        raise PackageError(
            "RingWorld jar does not advertise compatibility API version "
            f"{COMPATIBILITY_API_VERSION}"
        )
    fabric_metadata = read_fabric_metadata(fabric_api, label="Fabric API jar")
    if not fabric_api.name.startswith("fabric-api-"):
        raise PackageError("Fabric API jar filename must start with fabric-api-")
    if fabric_metadata.get("id") != "fabric-api":
        raise PackageError("Fabric API input does not declare id fabric-api")
    if fabric_metadata.get("version") != FABRIC_API_VERSION:
        raise PackageError(
            f"Fabric API version {fabric_metadata.get('version')!r} does not match "
            f"{FABRIC_API_VERSION!r}"
        )
    validate_tree(instance_template, label="instance template")
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
        components = {
            component["uid"]: component["version"] for component in pack["components"]
        }
    except (KeyError, TypeError, ValueError) as exc:
        raise PackageError("instance template has invalid mmc-pack.json") from exc
    expected_components = {
        "net.minecraft": MINECRAFT_VERSION,
        "net.fabricmc.fabric-loader": FABRIC_LOADER_VERSION,
    }
    for component, expected in expected_components.items():
        if components.get(component) != expected:
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


def manifest(
    *, kind: str, version: str, source_revision: str,
    release_jar: Path, fabric_api: Path,
) -> dict[str, object]:
    return {
        "format": 2,
        "kind": kind,
        "license": EXPECTED_IDENTIFIER,
        "version": version,
        "minecraft": MINECRAFT_VERSION,
        "loader": "fabric",
        "sourceRevision": source_revision,
        "sourceUrl": f"{SOURCE_URL}/tree/{source_revision}",
        "ringworldJar": release_jar.name,
        "ringworldSha256": sha256(release_jar),
        "fabricApiJar": fabric_api.name,
        "fabricApiSha256": sha256(fabric_api),
    }


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def populate_instance(
    instance_template: Path, destination: Path, release_jar: Path, fabric_api: Path,
) -> None:
    shutil.copytree(instance_template, destination)
    mods = destination / ".minecraft" / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    shutil.copy2(release_jar, mods / release_jar.name)
    shutil.copy2(fabric_api, mods / fabric_api.name)


def add_client_package(
    staging: Path,
    *,
    platform: str,
    instance_template: Path,
    release_jar: Path,
    fabric_api: Path,
    license_file: Path,
    expected_license: bytes,
    launcher_dir: Path,
    version: str,
    source_revision: str,
) -> Path:
    archive_name = f"RingWorld-{version}-Client-{platform}.zip"
    with tempfile.TemporaryDirectory(prefix="ringworld-client-") as directory:
        root = Path(directory) / "bundle"
        root.mkdir()
        shutil.copy2(license_file, root / "LICENSE")
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
        populate_instance(instance_template, instance, release_jar, fabric_api)

        nested_root = Path(directory) / "prism-instance"
        shutil.copytree(instance, nested_root)
        shutil.copy2(license_file, nested_root / "LICENSE")
        deterministic_zip(nested_root, root / "RingWorld-Prism-Instance.zip")

        package_manifest = manifest(
            kind=f"client-{platform}", version=version, source_revision=source_revision,
            release_jar=release_jar, fabric_api=fabric_api,
        )
        write_json(root / "PACKAGE-MANIFEST.json", package_manifest)
        (root / "README-FIRST.txt").write_text(
            "RingWorld optional Prism client bundle.\n"
            "Run the platform launcher, or import RingWorld-Prism-Instance.zip into Prism Launcher.\n"
            "This bundle does not auto-join a server. Existing accounts, saves and settings are preserved.\n"
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
    server_template: Path,
    release_jar: Path,
    fabric_api: Path,
    license_file: Path,
    expected_license: bytes,
    version: str,
    source_revision: str,
) -> Path:
    validate_tree(server_template, label="server template")
    archive_name = f"RingWorld-{version}-Server-Overlay.zip"
    with tempfile.TemporaryDirectory(prefix="ringworld-server-") as directory:
        root = Path(directory) / "server-overlay"
        shutil.copytree(server_template, root)
        shutil.copy2(license_file, root / "LICENSE")
        mods = root / "mods"
        mods.mkdir(exist_ok=True)
        shutil.copy2(release_jar, mods / release_jar.name)
        shutil.copy2(fabric_api, mods / fabric_api.name)
        package_manifest = manifest(
            kind="server-overlay", version=version, source_revision=source_revision,
            release_jar=release_jar, fabric_api=fabric_api,
        )
        package_manifest["installNote"] = (
            "Overlay only; obtain Minecraft and Fabric server components from official sources."
        )
        write_json(root / "PACKAGE-MANIFEST.json", package_manifest)
        output = staging / archive_name
        deterministic_zip(root, output)
    verify_artifact(output, expected_license, kind="server")
    return output


def build_packages(args: argparse.Namespace) -> tuple[Path, ...]:
    expected_license = validate_inputs(
        args.jar, args.fabric_api, args.instance_template, args.license,
        args.version, args.source_revision,
    )
    validate_tree(args.launcher_dir, label="launcher templates")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="ringworld-release-", dir=args.output.parent) as directory:
        staging = Path(directory) / "staging"
        staging.mkdir()
        universal = add_client_package(
            staging, platform="macOS-universal", instance_template=args.instance_template,
            release_jar=args.jar, fabric_api=args.fabric_api, license_file=args.license,
            expected_license=expected_license, launcher_dir=args.launcher_dir,
            version=args.version, source_revision=args.source_revision,
        )
        windows = add_client_package(
            staging, platform="Windows", instance_template=args.instance_template,
            release_jar=args.jar, fabric_api=args.fabric_api, license_file=args.license,
            expected_license=expected_license, launcher_dir=args.launcher_dir,
            version=args.version, source_revision=args.source_revision,
        )
        server = add_server_package(
            staging, server_template=args.server_template, release_jar=args.jar,
            fabric_api=args.fabric_api, license_file=args.license,
            expected_license=expected_license, version=args.version,
            source_revision=args.source_revision,
        )
        artifacts = (universal, windows, server)
        checksums = "".join(f"{sha256(path)}  {path.name}\n" for path in artifacts)
        (staging / "SHA256SUMS.txt").write_text(checksums, encoding="utf-8")
        write_json(
            staging / "RELEASE-MANIFEST.json",
            {
                "format": 1,
                "license": EXPECTED_IDENTIFIER,
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
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--fabric-api", required=True, type=Path)
    parser.add_argument(
        "--instance-template", type=Path, default=Path("deploy/client/instance")
    )
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--version", required=True)
    parser.add_argument("--source-revision", required=True)
    parser.add_argument("--license", type=Path, default=Path("LICENSE"))
    parser.add_argument("--launcher-dir", type=Path, default=Path("deploy/client"))
    parser.add_argument("--server-template", type=Path, default=Path("deploy/server"))
    args = parser.parse_args()
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
