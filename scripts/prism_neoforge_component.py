"""Create a portable Prism component from a hash-pinned official installer.

No network I/O, installer execution, or bundled Minecraft binaries. The shape
follows Prism's documented implementation in meta/run/generate_neoforge.py;
patches/net.neoforged.json is Prism's native custom-component mechanism.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import zipfile


class ComponentError(ValueError):
    pass


WRAPPER = {
    "name": "io.github.zekerzhayard:ForgeWrapper:prism-2026-08-01",
    "downloads": {"artifact": {
        "url": "https://files.prismlauncher.org/maven/io/github/zekerzhayard/ForgeWrapper/"
               "prism-2026-08-01/ForgeWrapper-prism-2026-08-01.jar",
        "sha1": "852b7e59748da1512d40e38407eadb1f0031a996",
        "size": 29800,
    }},
}


def component_from_installer(path: Path, *, minecraft: str, version: str,
                             expected_sha256: str) -> dict:
    """Use only the selected qualification cell's exact installer bytes."""
    if not re.fullmatch(r"[0-9.]+(?:-beta)?", version) or not re.fullmatch(r"[0-9.]+", minecraft):
        raise ComponentError("invalid Minecraft/NeoForge version")
    if not isinstance(expected_sha256, str) or not re.fullmatch(r"[0-9a-f]{64}", expected_sha256):
        raise ComponentError("official NeoForge installer needs a reviewed SHA-256 pin")
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 64 * 1024 * 1024:
        raise ComponentError("expected a bounded regular NeoForge installer")
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != expected_sha256:
        raise ComponentError("NeoForge installer SHA-256 does not match qualified runtime pin")
    try:
        with zipfile.ZipFile(path) as archive:
            values = []
            for name in ("install_profile.json", "version.json"):
                if archive.namelist().count(name) != 1 or archive.getinfo(name).file_size > 2 * 1024 * 1024:
                    raise ComponentError("missing, duplicate or oversized installer metadata")
                values.append(json.loads(archive.read(name)))
        profile, runtime = values
        identity = "neoforge-" + version
        if (profile["minecraft"] != minecraft or profile["version"] != identity
                or profile["json"] != "/version.json" or runtime["id"] != identity
                or runtime["inheritsFrom"] != minecraft):
            raise ComponentError("installer Minecraft/NeoForge identity differs from package pins")
        arguments = runtime["arguments"]["game"]
        if not isinstance(arguments, list) or not all(isinstance(arg, str) and not any(c.isspace() for c in arg)
                                                      for arg in arguments):
            raise ComponentError("unsupported NeoForge game argument structure")
        for flag, value in (("--fml.neoForgeVersion", version), ("--fml.mcVersion", minecraft)):
            if arguments.count(flag) != 1 or arguments[arguments.index(flag) + 1] != value:
                raise ComponentError("installer launch arguments differ from package pins")

        def libraries(document: dict) -> list:
            result = []
            seen = set()
            if not isinstance(document["libraries"], list):
                raise ComponentError("invalid NeoForge library list")
            for library in document["libraries"]:
                name = library["name"]
                if not isinstance(name, str) or name in seen:
                    raise ComponentError("invalid or duplicate NeoForge library")
                seen.add(name)
                if name.startswith("org.apache.logging.log4j:"):
                    continue  # The Minecraft component owns Log4j, as in Prism's generator.
                artifact = library["downloads"]["artifact"]
                if (not re.fullmatch(r"[0-9a-f]{40}", artifact["sha1"])
                        or type(artifact["size"]) is not int or artifact["size"] <= 0
                        or not artifact["url"].startswith(("https://maven.neoforged.net/releases/",
                                                           "https://libraries.minecraft.net/"))):
                    raise ComponentError("NeoForge library lacks an official hash-bound download")
                result.append(library)
            return result

        installer = {"name": f"net.neoforged:neoforge:{version}:installer",
                     "downloads": {"artifact": {
                         "url": f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{version}/"
                                f"neoforge-{version}-installer.jar",
                         "sha1": hashlib.sha1(data).hexdigest(), "size": len(data)}}}
        return {
            "formatVersion": 1, "name": "NeoForge", "uid": "net.neoforged", "version": version,
            "order": 5, "releaseTime": runtime["releaseTime"],
            "requires": [{"uid": "net.minecraft", "equals": minecraft}],
            "mainClass": "io.github.zekerzhayard.forgewrapper.installer.Main",
            "minecraftArguments": "--username ${auth_player_name} --version ${version_name} "
                                  "--gameDir ${game_directory} --assetsDir ${assets_root} "
                                  "--assetIndex ${assets_index_name} --uuid ${auth_uuid} "
                                  "--accessToken ${auth_access_token} --userType ${user_type} "
                                  "--versionType ${version_type} " + " ".join(arguments),
            "libraries": [WRAPPER, *libraries(runtime)],
            "mavenFiles": [installer, *libraries(profile)],
        }
    except (KeyError, IndexError, TypeError, json.JSONDecodeError, zipfile.BadZipFile) as exc:
        raise ComponentError("malformed or unsupported NeoForge installer metadata") from exc
