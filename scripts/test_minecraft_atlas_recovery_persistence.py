#!/usr/bin/env python3
"""Pure parser tests for persisted Atlas-recovery evidence."""

from __future__ import annotations

import gzip
from io import BytesIO
from pathlib import Path
import struct
import unittest

from minecraft_atlas_recovery_persistence import parse_persisted_ring_settings, parse_ring_terrain_atlas
from minecraft_atlas_recovery_qualification import atlas_world_hash, layout_fingerprint
from minecraft_qualification_model import InvocationError


def _name(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def settings_bytes(seed: int = 155_088_888) -> bytes:
    fields = b"".join((
        b"\x03" + _name("width") + struct.pack(">i", 416),
        b"\x03" + _name("circumference") + struct.pack(">i", 2_048),
        b"\x04" + _name("seed") + struct.pack(">q", seed),
        b"\x03" + _name("wallHeight") + struct.pack(">i", 160),
        b"\x03" + _name("terrainNoiseMapping") + struct.pack(">i", 4),
        b"\x03" + _name("format") + struct.pack(">i", 3),
    ))
    nbt = b"\x0a\x00\x00" + b"\x0a" + _name("data") + fields + b"\x00" + b"\x00"
    return gzip.compress(nbt, mtime=0)


def atlas_bytes(world_hash: int, present_cells: int, *, trailing: bytes = b"") -> bytes:
    columns, rows = 256, 52
    header = struct.pack(">IIQIIIIIQ", 0x52574154, 6, world_hash, 416, 2_048, 8, columns, rows, 7)
    cells = b"".join((b"\x01" if index < present_cells else b"\x00") + b"\x00\x40\x00\x00\x00\x00"
                     for index in range(columns * rows))
    return gzip.compress(header + cells + trailing, mtime=0)


class AtlasRecoveryPersistenceTest(unittest.TestCase):
    def test_settings_decode_and_java_unsigned_hash_parity(self) -> None:
        settings = parse_persisted_ring_settings(settings_bytes(), Path("/world/settings.dat"))
        self.assertEqual(64, settings.surface_reference_y)  # optional persisted default
        self.assertEqual("4064118068185880929", layout_fingerprint(settings))
        self.assertEqual("8665210144080158345", atlas_world_hash(settings))

    def test_atlas_header_presence_and_complete_chunk_counts(self) -> None:
        raw = atlas_bytes(8_665_210_144_080_158_345, 8)
        atlas = parse_ring_terrain_atlas(raw, Path("/world/terrain-atlas.rwat.gz"))
        self.assertEqual(8, atlas.present_cells)
        self.assertEqual(0, atlas.present_chunks)
        complete = parse_ring_terrain_atlas(
            atlas_bytes(8_665_210_144_080_158_345, 13_312), Path("/world/terrain-atlas.rwat.gz"),
        )
        self.assertEqual(13_312, complete.present_cells)
        self.assertEqual(3_328, complete.present_chunks)

    def test_rejects_truncated_trailing_bad_presence_and_bad_nbt(self) -> None:
        good = atlas_bytes(1, 4)
        with self.assertRaises(InvocationError):
            parse_ring_terrain_atlas(good[:-4], Path("/atlas"))
        with self.assertRaises(InvocationError):
            parse_ring_terrain_atlas(atlas_bytes(1, 4, trailing=b"x"), Path("/atlas"))
        uncompressed = bytearray(gzip.decompress(good))
        uncompressed[44] = 2
        with self.assertRaises(InvocationError):
            parse_ring_terrain_atlas(gzip.compress(bytes(uncompressed), mtime=0), Path("/atlas"))
        with self.assertRaises(InvocationError):
            parse_persisted_ring_settings(gzip.compress(b"\x00", mtime=0), Path("/settings"))


if __name__ == "__main__":
    unittest.main()
