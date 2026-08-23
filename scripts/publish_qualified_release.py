#!/usr/bin/env python3
"""Dry-run-first Modrinth/CurseForge publisher for one qualified stage file."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import secrets
import stat
from typing import Any, Mapping
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from stage_modrinth_release import VerificationError, current_public_source


MARKER = ".ringworld-qualified-stage"
HOSTS = ("modrinth", "curseforge")
LOADERS = ("fabric", "neoforge")
TOKEN_ENV = {"modrinth": "MODRINTH_TOKEN", "curseforge": "CURSEFORGE_API_TOKEN"}
ENDPOINT = {
    "modrinth": "https://api.modrinth.com/v2/version",
    "curseforge": "https://minecraft.curseforge.com/api/projects/1645598/upload-file",
}


class PublishPlanError(ValueError):
    """The stage, authorization, or response is unsafe or inconsistent."""


def _regular(path: Path, label: str) -> Path:
    try:
        info = path.lstat()
    except OSError as error:
        raise PublishPlanError(f"{label} is unavailable") from error
    if not stat.S_ISREG(info.st_mode) or path.is_symlink():
        raise PublishPlanError(f"{label} must be a non-symlink regular file")
    return path


def _json(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(_regular(path, label).read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise PublishPlanError(f"{label} is invalid") from error
    if not isinstance(value, dict):
        raise PublishPlanError(f"{label} must be an object")
    return value


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def publication_plan(stage: Path, host: str, loader: str) -> dict[str, Any]:
    if host not in HOSTS or loader not in LOADERS:
        raise PublishPlanError("host/loader is unsupported")
    stage = stage.resolve(strict=True)
    if not _regular(stage / MARKER, "qualified stage marker").read_text() == "generated\n":
        raise PublishPlanError("qualified stage marker is invalid")
    folder = stage / loader
    manifest = _json(folder / "STAGING-MANIFEST.json", "staging manifest")
    if manifest.get("loader") != loader or manifest.get("publication_action") != "manual_owner_authorization_required":
        raise PublishPlanError("staging manifest loader/authority is invalid")
    upload_name = manifest.get("upload_file")
    if not isinstance(upload_name, str) or Path(upload_name).name != upload_name or not upload_name.endswith(".jar"):
        raise PublishPlanError("staging manifest upload file is unsafe")
    jar = _regular(folder / upload_name, "staged runtime jar")
    jars = tuple(folder.glob("*.jar"))
    if jars != (jar,):
        raise PublishPlanError("loader stage must contain exactly one runtime jar")
    hashes = manifest.get("hashes")
    if not isinstance(hashes, Mapping) or hashes.get("sha256") != _sha256(jar):
        raise PublishPlanError("staged runtime jar hash does not match its manifest")
    source = manifest.get("source")
    if not isinstance(source, Mapping) or not isinstance(source.get("revision"), str) \
            or source.get("url") != f"https://github.com/Delaser/RingWorld/commit/{source.get('revision')}":
        raise PublishPlanError("staging manifest source is invalid")
    metadata_name = "MODRINTH-VERSION.json" if host == "modrinth" else "CURSEFORGE-UPLOAD.json"
    metadata = _json(folder / metadata_name, f"{host} metadata")
    if metadata.get("loader") not in (None, loader):
        raise PublishPlanError(f"{host} metadata loader mismatch")
    if host == "modrinth":
        data = dict(metadata)
        data.update({"file_parts": ["file"], "primary_file": "file", "status": "unlisted",
                     "requested_status": "unlisted"})
    else:
        relations = metadata.get("relations")
        if not isinstance(relations, list):
            raise PublishPlanError("curseforge relations must be a list")
        projects = []
        for item in relations:
            if not isinstance(item, Mapping) or not isinstance(item.get("project_id"), int) \
                    or not isinstance(item.get("slug"), str) or not item["slug"] \
                    or not isinstance(item.get("relation_type"), str):
                raise PublishPlanError("curseforge relation is invalid")
            projects.append({"projectID": item["project_id"], "slug": item["slug"],
                             "type": item["relation_type"]})
        data = {
            "changelog": metadata["changelog"], "changelogType": "markdown",
            "displayName": metadata["display_name"],
            "gameVersionNames": ["Client", "Server", *manifest["game_versions"],
                                 "Fabric" if loader == "fabric" else "NeoForge"],
            "releaseType": metadata["release_type"],
            "isMarkedForManualRelease": True,
        }
        if projects:
            data["relations"] = {"projects": projects}
    return {
        "format": 1, "dry_run": True, "host": host, "loader": loader,
        "method": "POST", "endpoint": ENDPOINT[host], "token_environment": TOKEN_ENV[host],
        "source_revision": source["revision"], "jar_path": str(jar),
        "jar_sha256": hashes["sha256"], "metadata": data,
        "multipart_fields": ["data" if host == "modrinth" else "metadata", "file"],
        "authority": "fresh_owner_authorization_required",
    }


def _authorization(path: Path, plan: Mapping[str, Any]) -> None:
    record = _json(path, "owner authorization")
    expected = {"format", "action", "host", "loader", "source_revision", "jar_sha256", "expires_utc"}
    if set(record) != expected or record.get("format") != 1 \
            or record.get("action") != "publish-qualified-release" \
            or any(record.get(key) != plan.get(key) for key in ("host", "loader", "source_revision", "jar_sha256")):
        raise PublishPlanError("owner authorization does not bind this exact publication")
    try:
        expiry = datetime.fromisoformat(str(record["expires_utc"]).replace("Z", "+00:00"))
    except ValueError as error:
        raise PublishPlanError("owner authorization expiry is invalid") from error
    now = datetime.now(timezone.utc)
    if expiry <= now or (expiry - now).total_seconds() > 3600:
        raise PublishPlanError("owner authorization must be unexpired and no more than one hour ahead")


def _multipart(field: str, metadata: Mapping[str, Any], jar: Path) -> tuple[bytes, str]:
    boundary = "RingWorld-" + secrets.token_hex(16)
    line = b"\r\n"
    chunks = [
        f"--{boundary}".encode(),
        f'Content-Disposition: form-data; name="{field}"'.encode(),
        b"Content-Type: application/json", b"", json.dumps(metadata, separators=(",", ":")).encode(),
        f"--{boundary}".encode(),
        f'Content-Disposition: form-data; name="file"; filename="{jar.name}"'.encode(),
        b"Content-Type: application/java-archive", b"", jar.read_bytes(),
        f"--{boundary}--".encode(), b"",
    ]
    return line.join(chunks), f"multipart/form-data; boundary={boundary}"


def execute_publication(
    plan: Mapping[str, Any], authorization: Path, *, token: str,
    opener=urlopen,
) -> dict[str, Any]:
    _authorization(authorization, plan)
    if not token or any(character.isspace() for character in token):
        raise PublishPlanError("host token is missing or malformed")
    jar = _regular(Path(str(plan["jar_path"])), "staged runtime jar")
    if _sha256(jar) != plan["jar_sha256"]:
        raise PublishPlanError("staged runtime jar changed after planning")
    field = "data" if plan["host"] == "modrinth" else "metadata"
    body, content_type = _multipart(field, plan["metadata"], jar)
    headers = {"Content-Type": content_type, "User-Agent": "RingWorld-release-automation/1.0"}
    headers["Authorization" if plan["host"] == "modrinth" else "X-Api-Token"] = token
    request = Request(str(plan["endpoint"]), data=body, headers=headers, method="POST")
    try:
        with opener(request, timeout=120) as response:
            if response.status not in (200, 201):
                raise PublishPlanError(f"{plan['host']} returned HTTP {response.status}")
            result = json.loads(response.read().decode("utf-8"))
    except (HTTPError, URLError, OSError, UnicodeError, json.JSONDecodeError) as error:
        raise PublishPlanError(f"{plan['host']} publication failed without a valid response") from error
    if not isinstance(result, dict) or not isinstance(result.get("id"), (str, int)):
        raise PublishPlanError(f"{plan['host']} response has no version/file id")
    return {"host": plan["host"], "loader": plan["loader"], "id": str(result["id"]),
            "jar_sha256": plan["jar_sha256"], "raw_response": result}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", type=Path, required=True)
    parser.add_argument("--host", choices=HOSTS, required=True)
    parser.add_argument("--loader", choices=LOADERS, required=True)
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--authorization-file", type=Path)
    args = parser.parse_args()
    try:
        plan = publication_plan(args.stage, args.host, args.loader)
        if not args.execute:
            print(json.dumps(plan, indent=2))
            print("DRY RUN: no token read and no network or host mutation performed.")
            return 0
        if args.authorization_file is None:
            raise PublishPlanError("--execute requires --authorization-file")
        source = current_public_source(Path.cwd())
        if source["revision"] != plan["source_revision"]:
            raise PublishPlanError("current clean pushed source differs from the staged source")
        token = os.environ.get(str(plan["token_environment"]), "")
        result = execute_publication(plan, args.authorization_file, token=token)
        print(json.dumps(result, indent=2))
        print("Submission created; no prior host file was edited or deleted.")
        return 0
    except (OSError, ValueError, VerificationError) as error:
        print(f"FAIL {error}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
