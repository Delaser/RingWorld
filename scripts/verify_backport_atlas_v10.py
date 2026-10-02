#!/usr/bin/env python3
"""Independent, bounded disk verification for the 1.21.1 format-10 candidate."""
import argparse
import gzip
import hashlib
import json
import struct
from pathlib import Path
from minecraft_atlas_recovery_persistence import _NbtReader

MASK = (1 << 64) - 1


def mix(value):
    value &= MASK
    value ^= value >> 30
    value = value * 0xBF58476D1CE4E5B9 & MASK
    value ^= value >> 27
    value = value * 0x94D049BB133111EB & MASK
    return value ^ (value >> 31)


def identity(settings):
    # Minecraft's optional-field codecs omit values equal to their defaults.
    wall = settings.get("wallStyle", {"thickness": 5, "palette": 0, "pattern": 0,
                                     "decay": 0, "format": 1})
    generation = settings.get("generation", {"atlas_fidelity": 1, "layout": 0,
                                            "continuous_river": 0, "more_structures": 0, "format": 1})
    value = 0x9E3779B97F4A7C15 ^ settings["seed"]
    fields = [(4, 0), (settings["width"], 0), (settings["circumference"], 1),
              (settings["wallHeight"], 17), (settings.get("surfaceReferenceY", 64), 33),
              (settings["terrainNoiseMapping"], 49), (settings["format"], 41),
              (wall["thickness"], 9), (wall["palette"], 13), (wall["pattern"], 21),
              (wall["decay"], 29), (wall["format"], 37),
              (generation["atlas_fidelity"], 11), (generation["layout"], 19)]
    for field, shift in fields:
        value = mix(value ^ ((field & 0xFFFFFFFF) << shift))
    value = mix(value ^ (0x52A17E2D if generation["continuous_river"] else 0))
    value = mix(value ^ (0x6D31904B if generation["more_structures"] else 0))
    value = mix(value ^ (generation["format"] << 45))
    fingerprint = mix(value ^ (3 << 25))
    return fingerprint, mix(mix(fingerprint ^ (10 << 32)) ^ 1)


def verify(settings_path, atlas_path):
    with gzip.open(settings_path, "rb") as stream:
        data = stream.read(4 * 1024 * 1024 + 1)
    if len(data) > 4 * 1024 * 1024:
        raise ValueError("settings exceed the bounded NBT budget")
    reader = _NbtReader(data)
    if reader.read(1) != b"\x0a":
        raise ValueError("settings root must be a compound")
    reader.string()
    settings = reader.payload(10)["data"]
    if reader.offset != len(data) or settings["format"] != 5:
        raise ValueError("trailing settings bytes or unexpected settings format")
    fingerprint, expected_hash = identity(settings)
    present = water_cells = light_cells = 0
    with gzip.open(atlas_path, "rb") as stream:
        header = stream.read(44)
        if len(header) != 44:
            raise ValueError("truncated Atlas header")
        magic, version, world_hash, width, circumference, step, columns, rows, revision = struct.unpack(
            ">IIQiiiiiQ", header)
        if (magic, version, world_hash, width, circumference, step, columns, rows) != (
                0x52574154, 10, expected_hash, settings["width"], settings["circumference"],
                1, settings["circumference"], settings["width"]):
            raise ValueError("Atlas does not match format 10 and immutable saved settings")
        cells = columns * rows
        if (width < 128 or circumference < 1024 or width % 16 or circumference % 16
                or not 0 < cells <= 16_777_216 or revision >= 1 << 63):
            raise ValueError("invalid Atlas budget or revision")
        remaining = cells
        while remaining:
            count = min(4096, remaining)
            payload = stream.read(count * 12)
            if len(payload) != count * 12:
                raise ValueError("truncated Atlas cells")
            for exists, height, color, packed, side_color in struct.iter_unpack(">BhIBI", payload):
                if exists not in (0, 1) or color > 0xFFFFFF or side_color > 0xFFFFFF:
                    raise ValueError("invalid Atlas presence or RGB value")
                present += exists
                water_cells += bool(exists and packed >> 4)
                light_cells += bool(exists and packed & 15)
            remaining -= count
        if stream.read(1):
            raise ValueError("trailing Atlas bytes")
    if present != cells:
        raise ValueError("Atlas is not durably complete")
    return {"schemaVersion": 1, "status": "PASS", "settings": settings,
            "layoutFingerprint": str(fingerprint), "worldHash": str(world_hash),
            "atlasFormat": version, "sampleStep": step, "cells": cells,
            "presentCells": present, "waterCells": water_cells, "litCells": light_cells,
            "revision": revision, "atlasSha256": hashlib.sha256(atlas_path.read_bytes()).hexdigest(),
            "settingsSha256": hashlib.sha256(settings_path.read_bytes()).hexdigest()}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("settings", type=Path)
    parser.add_argument("atlas", type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.settings, args.atlas), indent=2))
