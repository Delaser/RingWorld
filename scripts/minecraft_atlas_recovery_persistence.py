#!/usr/bin/env python3
"""Bounded independent parsers for RingWorld Atlas-recovery evidence files."""

from __future__ import annotations

import gzip
import hashlib
from io import BytesIO
from pathlib import Path
import struct
from typing import Any

from minecraft_atlas_recovery_qualification import (
    ATLAS_FORMAT_VERSION,
    ATLAS_SAMPLE_STEP_BLOCKS,
    AtlasCacheObservation,
    EXPECTED_ATLAS_COLUMNS,
    EXPECTED_ATLAS_ROWS,
    PersistedRingSettingsObservation,
)
from minecraft_qualification_model import InvocationError


MAX_SETTINGS_COMPRESSED_BYTES = 1 << 20
MAX_SETTINGS_UNCOMPRESSED_BYTES = 4 << 20
MAX_ATLAS_COMPRESSED_BYTES = 16 << 20
MAX_ATLAS_UNCOMPRESSED_BYTES = 32 << 20
ATLAS_MAGIC = 0x52574154


class _NbtReader:
    """Small bounded big-endian NBT reader; sufficient for saved settings."""

    def __init__(self, data: bytes) -> None:
        self.data = data
        self.offset = 0

    def read(self, size: int) -> bytes:
        if size < 0 or self.offset + size > len(self.data):
            raise InvocationError("saved settings NBT is truncated")
        value = self.data[self.offset:self.offset + size]
        self.offset += size
        return value

    def number(self, code: str) -> int | float:
        return struct.unpack(">" + code, self.read(struct.calcsize(">" + code)))[0]

    def string(self) -> str:
        length = int(self.number("H"))
        try:
            return self.read(length).decode("utf-8")
        except UnicodeDecodeError as error:
            raise InvocationError("saved settings NBT contains invalid UTF-8") from error

    def payload(self, tag: int, depth: int = 0) -> Any:
        if depth > 32:
            raise InvocationError("saved settings NBT nesting is too deep")
        if tag == 1:
            return self.number("b")
        if tag == 2:
            return self.number("h")
        if tag == 3:
            return self.number("i")
        if tag == 4:
            return self.number("q")
        if tag == 5:
            return self.number("f")
        if tag == 6:
            return self.number("d")
        if tag == 7:
            length = int(self.number("i"))
            return self.read(length)
        if tag == 8:
            return self.string()
        if tag == 9:
            child, length = self.read(1)[0], int(self.number("i"))
            if length < 0 or length > 1_000_000:
                raise InvocationError("saved settings NBT list length is invalid")
            return [self.payload(child, depth + 1) for _ in range(length)]
        if tag == 10:
            compound: dict[str, Any] = {}
            while True:
                child = self.read(1)[0]
                if child == 0:
                    return compound
                name = self.string()
                if name in compound:
                    raise InvocationError("saved settings NBT duplicates a field")
                compound[name] = self.payload(child, depth + 1)
        if tag == 11:
            length = int(self.number("i"))
            if length < 0 or length > 1_000_000:
                raise InvocationError("saved settings NBT int-array length is invalid")
            return [self.number("i") for _ in range(length)]
        if tag == 12:
            length = int(self.number("i"))
            if length < 0 or length > 1_000_000:
                raise InvocationError("saved settings NBT long-array length is invalid")
            return [self.number("q") for _ in range(length)]
        raise InvocationError(f"saved settings NBT uses unsupported tag {tag}")


def _decompress(raw: bytes, *, compressed_limit: int, uncompressed_limit: int, label: str) -> bytes:
    if not isinstance(raw, bytes) or not raw or len(raw) > compressed_limit:
        raise InvocationError(f"{label} has an invalid compressed size")
    try:
        with gzip.GzipFile(fileobj=BytesIO(raw), mode="rb") as stream:
            value = stream.read(uncompressed_limit + 1)
    except (OSError, EOFError) as error:
        raise InvocationError(f"{label} is not a valid gzip stream") from error
    if len(value) > uncompressed_limit:
        raise InvocationError(f"{label} exceeds its uncompressed size limit")
    return value


def parse_persisted_ring_settings(raw: bytes, path: Path) -> PersistedRingSettingsObservation:
    """Decode the dimension-owned ``settings.dat`` from its exact bytes."""
    data = _decompress(
        raw, compressed_limit=MAX_SETTINGS_COMPRESSED_BYTES,
        uncompressed_limit=MAX_SETTINGS_UNCOMPRESSED_BYTES, label="saved settings",
    )
    reader = _NbtReader(data)
    if reader.read(1)[0] != 10:
        raise InvocationError("saved settings NBT root is not a compound")
    reader.string()  # root name
    root = reader.payload(10)
    if reader.offset != len(data):
        raise InvocationError("saved settings NBT has trailing bytes")
    values = root.get("data")
    if not isinstance(values, dict):
        raise InvocationError("saved settings NBT has no data compound")

    def integer(name: str, *, default: int | None = None) -> int:
        value = values.get(name, default)
        if not isinstance(value, int) or isinstance(value, bool):
            raise InvocationError(f"saved settings field {name} is missing or not an integer")
        return value

    return PersistedRingSettingsObservation(
        integer("width"), integer("circumference"), integer("seed"), integer("wallHeight"),
        integer("surfaceReferenceY", default=64), integer("terrainNoiseMapping", default=1),
        integer("format"), path, hashlib.sha256(raw).hexdigest(),
    )


def parse_ring_terrain_atlas(raw: bytes, path: Path) -> AtlasCacheObservation:
    """Decode an entire Atlas-v6 file and independently count cells/chunks."""
    data = _decompress(
        raw, compressed_limit=MAX_ATLAS_COMPRESSED_BYTES,
        uncompressed_limit=MAX_ATLAS_UNCOMPRESSED_BYTES, label="terrain Atlas",
    )
    header_format = ">IIQIIIIIQ"
    header_size = struct.calcsize(header_format)
    if len(data) < header_size:
        raise InvocationError("terrain Atlas is truncated before its header")
    magic, version, world_hash, width, circumference, sample_step, columns, rows, revision = struct.unpack(
        header_format, data[:header_size],
    )
    if magic != ATLAS_MAGIC or version != ATLAS_FORMAT_VERSION:
        raise InvocationError("terrain Atlas has the wrong magic or format")
    if (width, circumference, sample_step, columns, rows) != (
            416, 2_048, ATLAS_SAMPLE_STEP_BLOCKS, EXPECTED_ATLAS_COLUMNS, EXPECTED_ATLAS_ROWS):
        raise InvocationError("terrain Atlas has the wrong safe-small geometry")
    expected_size = header_size + columns * rows * 7
    if len(data) != expected_size:
        raise InvocationError("terrain Atlas payload is truncated or has trailing bytes")
    present: list[bool] = []
    offset = header_size
    for _ in range(columns * rows):
        flag = data[offset]
        if flag not in (0, 1):
            raise InvocationError("terrain Atlas has an invalid presence flag")
        present.append(flag == 1)
        offset += 7  # boolean + signed height short + RGB int
    present_cells = sum(present)
    present_chunks = 0
    samples_per_chunk = 16 // sample_step
    for chunk_x in range(circumference // 16):
        for chunk_z in range(width // 16):
            first_x, first_z = chunk_x * samples_per_chunk, chunk_z * samples_per_chunk
            if all(present[(first_z + dz) * columns + first_x + dx]
                   for dz in range(samples_per_chunk) for dx in range(samples_per_chunk)):
                present_chunks += 1
    return AtlasCacheObservation(
        version, str(world_hash), width, circumference, sample_step, columns, rows,
        revision, present_cells, present_chunks, path, hashlib.sha256(raw).hexdigest(),
    )
