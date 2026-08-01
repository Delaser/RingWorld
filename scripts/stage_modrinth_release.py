#!/usr/bin/env python3
"""Fail-closed local staging for a manually uploaded RingWorld Modrinth jar.

This module deliberately has no network client and accepts no credential or
token option. It only validates a runtime jar and writes review material.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

try:
    from scripts.verify_distribution_license import EXPECTED_IDENTIFIER, VerificationError, verify_jar
except ModuleNotFoundError:
    from verify_distribution_license import EXPECTED_IDENTIFIER, VerificationError, verify_jar


MARKER = ".ringworld-modrinth-stage"
SOURCE_SUFFIXES = {".java", ".groovy", ".kt", ".kts", ".scala"}
SENSITIVE_BASENAMES = {
    ".env", ".npmrc", ".pypirc", "accounts.json", "auth.json",
    "authorization.json", "cookie.txt", "cookies.txt", "credential.json",
    "credentials.json", "eula.txt", "level.dat", "ops.json", "options.txt",
    "server.properties", "servers.dat", "session.lock", "token.txt",
    "tokens.json", "usercache.json", "whitelist.json",
}
SENSITIVE_TOP_LEVEL = {".minecraft", "logs", "run", "saves"}
SENSITIVE_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx"}
PRIVATE_KEY_PATTERN = re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")
SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
GITHUB_COMMIT_PREFIX = "https://github.com/Delaser/RingWorld/commit/"
PUBLIC_REPOSITORY = "https://github.com/Delaser/RingWorld"
COMPATIBILITY_API_VERSION = 1


def read_json(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise VerificationError(f"{path}: expected a JSON object")
    return value


def require_equal(label: str, actual: object, expected: object) -> None:
    if actual != expected:
        raise VerificationError(f"{label}: expected {expected!r}, found {actual!r}")


def validate_release_config(config: dict) -> None:
    project, version, fabric, source = (config.get(key) for key in ("project", "version", "fabric", "source"))
    if not all(isinstance(section, dict) for section in (project, version, fabric, source)):
        raise VerificationError("release.json: project, version, fabric, and source objects are required")
    require_equal("project.project_type", project.get("project_type"), "mod")
    require_equal("project.client_side", project.get("client_side"), "required")
    require_equal("project.server_side", project.get("server_side"), "required")
    require_equal("project.license_id", project.get("license_id"), EXPECTED_IDENTIFIER)
    require_equal("version.version_type", version.get("version_type"), "alpha")
    require_equal("version.loaders", version.get("loaders"), ["fabric"])
    require_equal("version.environment", version.get("environment"), "client_and_server")
    require_equal("version.featured", version.get("featured"), False)
    dependencies = version.get("dependencies")
    if not isinstance(dependencies, list) or {"project_id": "P7dR8mSH", "dependency_type": "required"} not in dependencies:
        raise VerificationError("version.dependencies must require Fabric API P7dR8mSH")
    require_equal("source.repository", source.get("repository"), PUBLIC_REPOSITORY)


def command_output(arguments: list[str], root: Path) -> str:
    return subprocess.run(
        arguments, cwd=root, check=True, text=True, capture_output=True
    ).stdout.strip()


def current_public_source(root: Path, runner=command_output) -> dict:
    """Return the exact clean public Git revision that owns a staged binary.

    A commit cannot safely contain its own object ID in release.json: changing
    that field changes the commit ID. The ignored staging directory is instead
    produced *after* a clean public branch commit exists, and this function
    records that commit in the manifest.
    """
    try:
        revision = runner(["git", "rev-parse", "--verify", "HEAD"], root)
        remote = runner(["git", "remote", "get-url", "origin"], root)
        branch = runner(["git", "symbolic-ref", "--quiet", "--short", "HEAD"], root)
        upstream = runner(["git", "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}"], root)
        upstream_revision = runner(["git", "rev-parse", "--verify", "@{upstream}"], root)
        dirty = runner(["git", "status", "--porcelain", "--untracked-files=all"], root)
    except (OSError, subprocess.CalledProcessError) as exc:
        raise VerificationError("staging requires a clean checkout of the public Git repository") from exc
    if not SHA_PATTERN.fullmatch(revision):
        raise VerificationError("Git HEAD must be a full 40-character commit SHA")
    if remote != f"{PUBLIC_REPOSITORY}.git":
        raise VerificationError(f"origin must be {PUBLIC_REPOSITORY}, found {remote!r}")
    if not branch or upstream != f"origin/{branch}" or upstream_revision != revision:
        raise VerificationError("Git HEAD must equal its pushed origin upstream")
    if dirty:
        raise VerificationError("refusing to stage from a dirty source checkout")
    return {"revision": revision, "url": f"{GITHUB_COMMIT_PREFIX}{revision}"}


def validate_source_descriptor(source: dict) -> None:
    revision, url = source.get("revision"), source.get("url")
    if not isinstance(revision, str) or not SHA_PATTERN.fullmatch(revision):
        raise VerificationError("source revision must be a full 40-character lowercase commit SHA")
    require_equal("source URL", url, f"{GITHUB_COMMIT_PREFIX}{revision}")


def validate_archive_paths(names: list[str]) -> None:
    if len(names) != len(set(names)):
        raise VerificationError("runtime jar contains duplicate archive entries")
    for name in names:
        path = PurePosixPath(name)
        if name.startswith("/") or ".." in path.parts:
            raise VerificationError(f"runtime jar contains unsafe archive path: {name}")
        lowered_parts, basename = [part.lower() for part in path.parts], path.name.lower()
        if path.suffix.lower() in SOURCE_SUFFIXES:
            raise VerificationError(f"runtime jar contains source file: {name}")
        if (basename in SENSITIVE_BASENAMES or basename.startswith(("secret.", "token."))
                or path.suffix.lower() in SENSITIVE_SUFFIXES
                or (lowered_parts and lowered_parts[0] in SENSITIVE_TOP_LEVEL)):
            raise VerificationError(f"runtime jar contains sensitive runtime file: {name}")


def validate_runtime_jar(jar_path: Path, config: dict, expected_license: bytes) -> dict:
    if jar_path.name.endswith(("-sources.jar", "-dev.jar", "-javadoc.jar")):
        raise VerificationError(f"refusing non-runtime artifact: {jar_path.name}")
    if jar_path.suffix != ".jar":
        raise VerificationError(f"expected a .jar: {jar_path}")
    with zipfile.ZipFile(jar_path) as archive:
        verify_jar(archive, str(jar_path), expected_license)
        names = archive.namelist()
        validate_archive_paths(names)
        if not any(name.endswith(".class") for name in names):
            raise VerificationError("runtime jar contains no compiled classes")
        for required in ("ringworld.mixins.json", "ringworld.client.mixins.json"):
            if required not in names:
                raise VerificationError(f"runtime jar missing {required}")
        try:
            metadata = json.loads(archive.read("fabric.mod.json"))
        except (KeyError, json.JSONDecodeError) as exc:
            raise VerificationError("invalid fabric.mod.json") from exc
        for name in names:
            if not name.endswith("/") and PRIVATE_KEY_PATTERN.search(archive.read(name)):
                raise VerificationError(f"runtime jar contains private-key material: {name}")
    version, fabric = config["version"], config["fabric"]
    require_equal("fabric.mod.json id", metadata.get("id"), fabric["mod_id"])
    require_equal("fabric.mod.json version", metadata.get("version"), version["version_number"])
    require_equal("fabric.mod.json authors", metadata.get("authors"), [fabric["author"]])
    contact = metadata.get("contact")
    if not isinstance(contact, dict):
        raise VerificationError("fabric.mod.json contact object is required")
    require_equal("fabric.mod.json contact.homepage", contact.get("homepage"), fabric["homepage"])
    require_equal("fabric.mod.json environment", metadata.get("environment"), fabric["environment"])
    custom = metadata.get("custom")
    if not isinstance(custom, dict):
        raise VerificationError("fabric.mod.json custom object is required")
    require_equal("fabric.mod.json custom.ringworld:compatibility_api",
                  custom.get("ringworld:compatibility_api"), COMPATIBILITY_API_VERSION)
    depends = metadata.get("depends")
    if not isinstance(depends, dict):
        raise VerificationError("fabric.mod.json depends object is required")
    for key, config_key in (("fabricloader", "fabric_loader"), ("minecraft", "minecraft"), ("java", "java"), ("fabric-api", "fabric_api")):
        require_equal(f"fabric.mod.json depends.{key}", depends.get(key), fabric[config_key])
    require_equal("Modrinth game_versions", version.get("game_versions"), [fabric["minecraft"]])
    require_equal("runtime jar filename", jar_path.name, f"ringworld-{version['version_number']}.jar")
    return metadata


def digest(path: Path, algorithm: str) -> str:
    hasher = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(block)
    return hasher.hexdigest()


def remove_recognized_stage(path: Path) -> None:
    if path.exists():
        marker = path / MARKER
        if not marker.is_file() or marker.read_text(encoding="utf-8") != "generated\n":
            raise VerificationError(f"refusing to replace unrecognized directory: {path}")
        shutil.rmtree(path)


def stage_release(jar_path: Path, config_path: Path, description_path: Path, changelog_path: Path, license_path: Path, output_root: Path, source: dict) -> Path:
    config = read_json(config_path)
    validate_release_config(config)
    validate_source_descriptor(source)
    metadata = validate_runtime_jar(jar_path, config, license_path.read_bytes())
    description, changelog = description_path.read_text(encoding="utf-8"), changelog_path.read_text(encoding="utf-8")
    if not description.strip() or not changelog.strip():
        raise VerificationError("project description and changelog must not be empty")
    target = output_root / config["version"]["version_number"] / "fabric"
    target.parent.mkdir(parents=True, exist_ok=True)
    remove_recognized_stage(target)
    temporary = Path(tempfile.mkdtemp(prefix=".fabric-stage-", dir=target.parent))
    try:
        staged_jar = temporary / jar_path.name
        shutil.copy2(jar_path, staged_jar)
        sha256, sha512 = digest(staged_jar, "sha256"), digest(staged_jar, "sha512")
        manifest = {
            "generated": True, "upload_file": staged_jar.name, "upload_file_only": True,
            "size": staged_jar.stat().st_size, "hashes": {"sha256": sha256, "sha512": sha512},
            "mod_id": metadata["id"], "version": metadata["version"], "loader": "fabric",
            "game_version": config["fabric"]["minecraft"], "environment": config["version"]["environment"],
            "source": source, "publication_action": "manual_owner_authorization_required"
        }
        (temporary / "STAGING-MANIFEST.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        (temporary / "SHA256SUMS.txt").write_text(f"{sha256}  {staged_jar.name}\n", encoding="utf-8")
        (temporary / "PROJECT_DESCRIPTION.md").write_text(description, encoding="utf-8")
        (temporary / "CHANGELOG.md").write_text(changelog, encoding="utf-8")
        (temporary / MARKER).write_text("generated\n", encoding="utf-8")
        temporary.replace(target)
    except Exception:
        shutil.rmtree(temporary, ignore_errors=True)
        raise
    return target


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--build", action="store_true", help="run the Java 25 Gradle test/build gate first")
    parser.add_argument("--config", type=Path, default=Path("deploy/modrinth/release.json"))
    parser.add_argument("--description", type=Path, default=Path("deploy/modrinth/project-description.md"))
    parser.add_argument("--changelog", type=Path, default=Path("deploy/modrinth/version-changelog.md"))
    parser.add_argument("--license", type=Path, default=Path("LICENSE"))
    parser.add_argument("--output-root", type=Path, default=Path("dist/modrinth"))
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        if args.build:
            subprocess.run(["./gradlew", "clean", "test", "build", "--console=plain"], check=True)
        jar = args.jar or Path("build/libs/ringworld-0.2.0+mc26.1.2.jar")
        source = current_public_source(Path.cwd())
        target = stage_release(jar, args.config, args.description, args.changelog, args.license, args.output_root, source)
    except (OSError, ValueError, subprocess.CalledProcessError, zipfile.BadZipFile, VerificationError) as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1
    print(f"PASS staged review directory at {target}")
    print("No upload, token, or Modrinth mutation was performed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
