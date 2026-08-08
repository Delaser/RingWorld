#!/usr/bin/env python3
"""Fail-closed local staging for manually uploaded Fabric or NeoForge jars.

This module has no network client and accepts no credential or upload option.
It validates runtime jars and writes local review material only.
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
    from scripts.verify_distribution_license import (
        EXPECTED_IDENTIFIER, VerificationError, read_jar_metadata, verify_jar,
    )
except ModuleNotFoundError:
    from verify_distribution_license import (
        EXPECTED_IDENTIFIER, VerificationError, read_jar_metadata, verify_jar,
    )


MARKER = ".ringworld-modrinth-stage"
SOURCE_SUFFIXES = {".java", ".groovy", ".kt", ".kts", ".scala"}
SENSITIVE_BASENAMES = {
    ".env", ".npmrc", ".pypirc", "accounts.json", "auth.json", "authorization.json",
    "cookie.txt", "cookies.txt", "credential.json", "credentials.json", "eula.txt",
    "level.dat", "ops.json", "options.txt", "server.properties", "servers.dat",
    "session.lock", "token.txt", "tokens.json", "usercache.json", "whitelist.json",
}
SENSITIVE_TOP_LEVEL = {".minecraft", "logs", "run", "saves"}
SENSITIVE_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx"}
PRIVATE_KEY_PATTERN = re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")
SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
FULL_COMMIT_SHA_TEXT_PATTERN = re.compile(r"\b[0-9a-f]{40}\b", re.IGNORECASE)
SHORT_COMMIT_SHA_LABELLED_PATTERN = re.compile(
    r"\b(?:commit|revision|sha)(?:\s+(?:id|hash))?\s*"
    r"(?::|=|#|-|\bis\b)?\s*[`'\"]*([0-9a-f]{7,39})\b",
    re.IGNORECASE,
)
GITHUB_COMMIT_PREFIX = "https://github.com/Delaser/RingWorld/commit/"
PUBLIC_REPOSITORY = "https://github.com/Delaser/RingWorld"
GITHUB_REVISION_URL_PATTERN = re.compile(
    r"https?://github\.com/delaser/ringworld/(?:commit|tree|blob)/[^\s)\]}>]+",
    re.IGNORECASE,
)
SOURCE_URL_PLACEHOLDER = "{{RINGWORLD_CORRESPONDING_SOURCE_URL}}"
COMPATIBILITY_API_VERSION = 1
REQUIRED_BUILD_JAVA = 25
JAVA_VERSION_PATTERN = re.compile(r'\b(?:java|openjdk) version "(?:1\.)?(\d+)')
LOADERS = {"fabric", "neoforge"}
SHARED_CRITICAL_ENTRIES = (
    "ringworld.mixins.json",
    "ringworld.client.mixins.json",
    "dev/ringworld/world/RingWorldSettings.class",
    "dev/ringworld/world/RingGeometry.class",
    "dev/ringworld/api/RingCompatibilityContract.class",
    "dev/ringworld/api/RingPhysicalPose.class",
    "dev/ringworld/api/RingWorldApi.class",
    "dev/ringworld/net/RingAtlasPregenerationControlPayload.class",
    "dev/ringworld/net/RingAtlasPregenerationStatusPayload.class",
    "dev/ringworld/net/RingAtlasPregenerationStatusRequestPayload.class",
    "dev/ringworld/net/RingMultiplayerTestPayload.class",
    "dev/ringworld/net/RingSettingsAckPayload.class",
    "dev/ringworld/net/RingSettingsPayload.class",
    "dev/ringworld/net/RingTerrainAtlasMetadataPayload.class",
    "dev/ringworld/net/RingTerrainAtlasRequestPayload.class",
    "dev/ringworld/net/RingTerrainAtlasRevisionPayload.class",
    "dev/ringworld/net/RingTerrainAtlasTilePayload.class",
)
SHARED_PREFIXES = (
    "dev/ringworld/api/",
    "dev/ringworld/net/",
    "assets/minecraft/shaders/",
)
FABRIC_JAR = Path("build/libs/ringworld-0.2.0+mc26.1.2.jar")
NEOFORGE_JAR = Path("neoforge/build/libs/ringworld-neoforge-0.2.0+mc26.1.2.jar")
FABRIC_CONFIG = Path("deploy/modrinth/release.json")
NEOFORGE_CONFIG = Path("deploy/modrinth/release-neoforge.json")
DESCRIPTION = Path("deploy/modrinth/project-description.md")
FABRIC_CHANGELOG = Path("deploy/modrinth/version-changelog.md")
NEOFORGE_CHANGELOG = Path("deploy/modrinth/version-changelog-neoforge.md")
LICENSE_PATH = Path("LICENSE")


def read_json(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise VerificationError(f"{path}: expected a JSON object")
    return value


def require_equal(label: str, actual: object, expected: object) -> None:
    if actual != expected:
        raise VerificationError(f"{label}: expected {expected!r}, found {actual!r}")


def loader_from_config(config: dict, expected_loader: str | None = None) -> str:
    version = config.get("version")
    if not isinstance(version, dict):
        raise VerificationError("release.json: version object is required")
    loaders = version.get("loaders")
    if not isinstance(loaders, list) or len(loaders) != 1 or loaders[0] not in LOADERS:
        raise VerificationError("version.loaders must name exactly one supported loader")
    loader = loaders[0]
    if expected_loader is not None and loader != expected_loader:
        raise VerificationError(f"release config is for {loader}, not requested loader {expected_loader}")
    return loader


def validate_release_config(config: dict, expected_loader: str | None = None) -> str:
    project, version, source = (config.get(key) for key in ("project", "version", "source"))
    if not all(isinstance(section, dict) for section in (project, version, source)):
        raise VerificationError("release.json: project, version, and source objects are required")
    loader = loader_from_config(config, expected_loader)
    require_equal("project.project_type", project.get("project_type"), "mod")
    require_equal("project.client_side", project.get("client_side"), "required")
    require_equal("project.server_side", project.get("server_side"), "required")
    require_equal("project.license_id", project.get("license_id"), EXPECTED_IDENTIFIER)
    require_equal("version.version_type", version.get("version_type"), "alpha")
    require_equal("version.environment", version.get("environment"), "client_and_server")
    require_equal("version.featured", version.get("featured"), False)
    require_equal("source.repository", source.get("repository"), PUBLIC_REPOSITORY)
    platform = config.get(loader)
    if not isinstance(platform, dict):
        raise VerificationError(f"release.json: {loader} object is required")
    if "minecraft" not in platform:
        raise VerificationError(f"release.json: {loader}.minecraft is required")
    require_equal("Modrinth game_versions", version.get("game_versions"), [platform["minecraft"]])
    if loader == "fabric":
        dependencies = version.get("dependencies")
        expected = {"project_id": "P7dR8mSH", "dependency_type": "required"}
        if not isinstance(dependencies, list) or expected not in dependencies:
            raise VerificationError("version.dependencies must require Fabric API P7dR8mSH")
        for field in ("mod_id", "author", "homepage", "environment", "fabric_loader", "minecraft", "java", "fabric_api"):
            if field not in platform:
                raise VerificationError(f"release.json: fabric.{field} is required")
    else:
        if version.get("dependencies") != []:
            raise VerificationError("NeoForge Modrinth metadata must declare no external dependencies")
        for field in ("mod_id", "author", "homepage", "minecraft", "neoforge"):
            if field not in platform:
                raise VerificationError(f"release.json: neoforge.{field} is required")
    return loader


def command_output(arguments: list[str], root: Path) -> str:
    return subprocess.run(arguments, cwd=root, check=True, text=True, capture_output=True).stdout.strip()


def java_major(version_output: str) -> int:
    match = JAVA_VERSION_PATTERN.search(version_output)
    if match is None:
        raise VerificationError(f"could not identify the active Java version from: {version_output.strip()!r}")
    return int(match.group(1))


def require_build_java(runner=subprocess.run) -> str:
    try:
        result = runner(["java", "-version"], text=True, capture_output=True, check=False)
    except OSError as exc:
        raise VerificationError(
            "Java 25 is required for --build, but java could not be started; set JAVA_HOME and PATH to a JDK 25 installation"
        ) from exc
    output = "\n".join(part for part in (result.stdout, result.stderr) if part).strip()
    if result.returncode != 0:
        raise VerificationError(
            "Java 25 is required for --build, but java -version failed; set JAVA_HOME and PATH to a JDK 25 installation"
        )
    if java_major(output) != REQUIRED_BUILD_JAVA:
        first_line = output.splitlines()[0] if output else "unknown Java"
        raise VerificationError(
            f"Java 25 is required for --build; active runtime is {first_line}. Set JAVA_HOME and put $JAVA_HOME/bin first on PATH."
        )
    return output.splitlines()[0]


def dual_build_command() -> list[str]:
    return ["./gradlew", "clean", ":neoforge:clean", "test", "build", ":neoforge:test", ":neoforge:build", "--console=plain"]


def run_dual_build() -> None:
    """Build both candidates from one clean source checkout before staging either."""
    subprocess.run(dual_build_command(), check=True)


def current_public_source(root: Path, runner=command_output) -> dict:
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


def render_public_release_text(path: Path, source: dict, *, label: str) -> str:
    """Render one upload-adjacent public text with the verified source URL.

    The source manifest is local operator evidence, so it cannot be the only
    way a recipient learns where to obtain the corresponding source.  Require
    every staged public text to carry the one immutable link itself.
    """
    validate_source_descriptor(source)
    template = path.read_text(encoding="utf-8")
    if not template.strip():
        raise VerificationError(f"{label} must not be empty")
    placeholder_count = template.count(SOURCE_URL_PLACEHOLDER)
    if placeholder_count != 1:
        raise VerificationError(
            f"{label} must contain exactly one {SOURCE_URL_PLACEHOLDER} placeholder"
        )
    if (GITHUB_REVISION_URL_PATTERN.search(template)
            or FULL_COMMIT_SHA_TEXT_PATTERN.search(template)
            or SHORT_COMMIT_SHA_LABELLED_PATTERN.search(template)):
        raise VerificationError(
            f"{label} must not hard-code a GitHub revision URL or SHA; use {SOURCE_URL_PLACEHOLDER}"
        )
    rendered = template.replace(SOURCE_URL_PLACEHOLDER, source["url"])
    if SOURCE_URL_PLACEHOLDER in rendered or rendered.count(source["url"]) != 1:
        raise VerificationError(f"{label} did not render one verified corresponding-source URL")
    return rendered


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


def ringworld_mod(metadata: dict) -> dict:
    mods = metadata.get("mods")
    if not isinstance(mods, list):
        raise VerificationError("neoforge.mods.toml mods array is required")
    matches = [mod for mod in mods if isinstance(mod, dict) and mod.get("modId") == "ringworld"]
    if len(matches) != 1:
        raise VerificationError("neoforge.mods.toml must declare exactly one ringworld mod")
    return matches[0]


def required_neoforge_dependency(metadata: dict, mod_id: str) -> dict:
    dependencies = metadata.get("dependencies")
    if not isinstance(dependencies, dict):
        raise VerificationError("neoforge.mods.toml dependencies are required")
    values = dependencies.get("ringworld")
    if not isinstance(values, list):
        raise VerificationError("neoforge.mods.toml ringworld dependencies are required")
    matches = [entry for entry in values if isinstance(entry, dict) and entry.get("modId") == mod_id]
    if len(matches) != 1:
        raise VerificationError(f"neoforge.mods.toml must declare one {mod_id} dependency")
    return matches[0]


def validate_runtime_jar(jar_path: Path, config: dict, expected_license: bytes, *, loader: str) -> dict:
    if jar_path.name.endswith(("-sources.jar", "-dev.jar", "-javadoc.jar")):
        raise VerificationError(f"refusing non-runtime artifact: {jar_path.name}")
    if jar_path.suffix != ".jar":
        raise VerificationError(f"expected a .jar: {jar_path}")
    with zipfile.ZipFile(jar_path) as archive:
        verify_jar(archive, str(jar_path), expected_license, loader=loader)
        names = archive.namelist()
        validate_archive_paths(names)
        if not any(name.endswith(".class") for name in names):
            raise VerificationError("runtime jar contains no compiled classes")
        for required in ("ringworld.mixins.json", "ringworld.client.mixins.json"):
            if required not in names:
                raise VerificationError(f"runtime jar missing {required}")
        _, metadata = read_jar_metadata(archive, str(jar_path), loader=loader)
        for name in names:
            if not name.endswith("/") and PRIVATE_KEY_PATTERN.search(archive.read(name)):
                raise VerificationError(f"runtime jar contains private-key material: {name}")
    version, platform = config["version"], config[loader]
    expected_filename = (
        f"ringworld-{version['version_number']}.jar" if loader == "fabric"
        else f"ringworld-neoforge-{version['version_number']}.jar"
    )
    require_equal("runtime jar filename", jar_path.name, expected_filename)
    if loader == "fabric":
        require_equal("fabric.mod.json id", metadata.get("id"), platform["mod_id"])
        require_equal("fabric.mod.json version", metadata.get("version"), version["version_number"])
        require_equal("fabric.mod.json authors", metadata.get("authors"), [platform["author"]])
        contact = metadata.get("contact")
        if not isinstance(contact, dict):
            raise VerificationError("fabric.mod.json contact object is required")
        require_equal("fabric.mod.json contact.homepage", contact.get("homepage"), platform["homepage"])
        require_equal("fabric.mod.json environment", metadata.get("environment"), platform["environment"])
        custom = metadata.get("custom")
        if not isinstance(custom, dict):
            raise VerificationError("fabric.mod.json custom object is required")
        require_equal("fabric.mod.json custom.ringworld:compatibility_api",
                      custom.get("ringworld:compatibility_api"), COMPATIBILITY_API_VERSION)
        depends = metadata.get("depends")
        if not isinstance(depends, dict):
            raise VerificationError("fabric.mod.json depends object is required")
        for key, config_key in (("fabricloader", "fabric_loader"), ("minecraft", "minecraft"),
                                ("java", "java"), ("fabric-api", "fabric_api")):
            require_equal(f"fabric.mod.json depends.{key}", depends.get(key), platform[config_key])
        return {"id": metadata["id"], "version": metadata["version"]}
    mod = ringworld_mod(metadata)
    require_equal("neoforge.mods.toml modId", mod.get("modId"), platform["mod_id"])
    require_equal("neoforge.mods.toml version", mod.get("version"), version["version_number"])
    require_equal("neoforge.mods.toml authors", mod.get("authors"), platform["author"])
    require_equal("neoforge.mods.toml displayURL", mod.get("displayURL"), platform["homepage"])
    neo_dependency = required_neoforge_dependency(metadata, "neoforge")
    require_equal("neoforge.mods.toml neoforge versionRange", neo_dependency.get("versionRange"), platform["neoforge"])
    require_equal("neoforge dependency type", neo_dependency.get("type"), "required")
    minecraft_dependency = required_neoforge_dependency(metadata, "minecraft")
    require_equal("neoforge.mods.toml minecraft versionRange", minecraft_dependency.get("versionRange"),
                  f"[{platform['minecraft']}]")
    return {"id": mod["modId"], "version": mod["version"]}


def shared_contract_entries(fabric: zipfile.ZipFile, neoforge: zipfile.ZipFile) -> tuple[str, ...]:
    """Return the complete shared runtime contract that must match byte-for-byte."""
    entries = set(SHARED_CRITICAL_ENTRIES)
    for archive in (fabric, neoforge):
        for name in archive.namelist():
            if name.endswith("/"):
                continue
            if name.startswith("assets/minecraft/shaders/"):
                entries.add(name)
            elif name.startswith("dev/ringworld/api/") and name.endswith(".class"):
                entries.add(name)
            elif name.startswith("dev/ringworld/net/") and name.endswith(".class") \
                    and name != "dev/ringworld/net/RingWorldNetworking.class":
                entries.add(name)
    return tuple(sorted(entries))


def validate_candidate_pair(
    fabric_jar: Path, fabric_config_path: Path, neoforge_jar: Path,
    neoforge_config_path: Path, license_path: Path,
) -> None:
    """Reject dual artifacts that do not carry the same shared RingWorld contract."""
    expected_license = license_path.read_bytes()
    fabric_config = read_json(fabric_config_path)
    neoforge_config = read_json(neoforge_config_path)
    require_equal("Fabric release config loader", validate_release_config(fabric_config, "fabric"), "fabric")
    require_equal("NeoForge release config loader", validate_release_config(neoforge_config, "neoforge"), "neoforge")
    fabric_metadata = validate_runtime_jar(fabric_jar, fabric_config, expected_license, loader="fabric")
    neoforge_metadata = validate_runtime_jar(neoforge_jar, neoforge_config, expected_license, loader="neoforge")
    require_equal("candidate mod version", fabric_metadata["version"], neoforge_metadata["version"])
    require_equal("candidate Minecraft version", fabric_config["fabric"]["minecraft"],
                  neoforge_config["neoforge"]["minecraft"])
    with zipfile.ZipFile(fabric_jar) as fabric, zipfile.ZipFile(neoforge_jar) as neoforge:
        fabric_names, neoforge_names = set(fabric.namelist()), set(neoforge.namelist())
        for name in shared_contract_entries(fabric, neoforge):
            if name not in fabric_names:
                raise VerificationError(f"Fabric candidate is missing shared contract entry {name}")
            if name not in neoforge_names:
                raise VerificationError(f"NeoForge candidate is missing shared contract entry {name}")
            if fabric.read(name) != neoforge.read(name):
                raise VerificationError(f"shared contract entry differs between candidates: {name}")


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


def stage_release(
    jar_path: Path, config_path: Path, description_path: Path, changelog_path: Path,
    license_path: Path, output_root: Path, source: dict, *, loader: str | None = None,
) -> Path:
    config = read_json(config_path)
    resolved_loader = validate_release_config(config, loader)
    validate_source_descriptor(source)
    metadata = validate_runtime_jar(jar_path, config, license_path.read_bytes(), loader=resolved_loader)
    description = render_public_release_text(
        description_path, source, label="project description",
    )
    changelog = render_public_release_text(
        changelog_path, source, label=f"{resolved_loader} changelog",
    )
    target = output_root / config["version"]["version_number"] / resolved_loader
    target.parent.mkdir(parents=True, exist_ok=True)
    remove_recognized_stage(target)
    temporary = Path(tempfile.mkdtemp(prefix=f".{resolved_loader}-stage-", dir=target.parent))
    try:
        staged_jar = temporary / jar_path.name
        shutil.copy2(jar_path, staged_jar)
        sha256, sha512 = digest(staged_jar, "sha256"), digest(staged_jar, "sha512")
        manifest = {
            # This is also the provenance record consumed by optional package
            # assembly.  Keep the validated config itself, rather than merely
            # its filename or digest: a later tool can re-run the exact
            # loader-aware runtime contract against the staged bytes.
            "format": 2,
            "generated": True, "upload_file": staged_jar.name, "upload_file_only": True,
            "size": staged_jar.stat().st_size, "hashes": {"sha256": sha256, "sha512": sha512},
            "mod_id": metadata["id"], "version": metadata["version"], "loader": resolved_loader,
            "game_version": config[resolved_loader]["minecraft"],
            "environment": config["version"]["environment"], "source": source,
            "release_config": config,
            "publication_action": "manual_owner_authorization_required",
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
    parser.add_argument("--loader", choices=("fabric", "neoforge", "both"), default="both")
    parser.add_argument(
        "--build", action="store_true",
        help="deprecated compatibility flag; the Java 25 dual build gate always runs before staging",
    )
    parser.add_argument("--output-root", type=Path, default=Path("dist/modrinth"))
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        # Never stage a cached artifact.  Both loader candidates are always
        # rebuilt from one clean, pushed source revision, even when the caller
        # requests just one of them.
        source = current_public_source(Path.cwd())
        require_build_java()
        run_dual_build()
        if current_public_source(Path.cwd()) != source:
            raise VerificationError("source changed during the dual build gate")
        requested = ("fabric", "neoforge") if args.loader == "both" else (args.loader,)
        validate_candidate_pair(
            FABRIC_JAR, FABRIC_CONFIG, NEOFORGE_JAR, NEOFORGE_CONFIG, LICENSE_PATH,
        )
        candidates = {
            "fabric": (FABRIC_JAR, FABRIC_CONFIG, FABRIC_CHANGELOG),
            "neoforge": (NEOFORGE_JAR, NEOFORGE_CONFIG, NEOFORGE_CHANGELOG),
        }
        staged = []
        for loader in requested:
            jar, config, changelog = candidates[loader]
            staged.append(stage_release(jar, config, DESCRIPTION, changelog, LICENSE_PATH,
                                        args.output_root, source, loader=loader))
    except (OSError, ValueError, subprocess.CalledProcessError, zipfile.BadZipFile, VerificationError) as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1
    for target in staged:
        print(f"PASS staged review directory at {target}")
    print("No upload, token, or Modrinth mutation was performed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
