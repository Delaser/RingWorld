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


def settings_bytes(seed: int = 155_088_888, *, width=416, circumference=2048,
                   format_version=3, wall=None, generation=None) -> bytes:
    fields = b"".join((
        b"\x03" + _name("width") + struct.pack(">i", width),
        b"\x03" + _name("circumference") + struct.pack(">i", circumference),
        b"\x04" + _name("seed") + struct.pack(">q", seed),
        b"\x03" + _name("wallHeight") + struct.pack(">i", 160),
        b"\x03" + _name("terrainNoiseMapping") + struct.pack(">i", 4),
        b"\x03" + _name("format") + struct.pack(">i", format_version),
    ))
    for name, values in (("wallStyle", wall), ("generation", generation)):
        if values is not None:
            fields += b"\x0a" + _name(name)
            for key, value in values.items():
                tag, code = (b"\x01", ">b") if isinstance(value, bool) else (b"\x03", ">i")
                fields += tag + _name(key) + struct.pack(code, value)
            fields += b"\x00"
    nbt = b"\x0a\x00\x00" + b"\x0a" + _name("data") + fields + b"\x00" + b"\x00"
    return gzip.compress(nbt, mtime=0)


def atlas_bytes(world_hash: int, present_cells: int, *, trailing: bytes = b"") -> bytes:
    columns, rows = 2048, 416
    header = struct.pack(">IIQIIIIIQ", 0x52574154, 9, world_hash, 416, 2_048, 1, columns, rows, 7)
    cells = b"".join((b"\x01" if index < present_cells else b"\x00") + struct.pack(">hIBI", 64, 0, 0, 0x334455)
                     for index in range(columns * rows))
    return gzip.compress(header + cells + trailing, mtime=0)


class AtlasRecoveryPersistenceTest(unittest.TestCase):
    def test_settings_decode_and_java_unsigned_hash_parity(self) -> None:
        settings = parse_persisted_ring_settings(settings_bytes(), Path("/world/settings.dat"))
        self.assertEqual(64, settings.surface_reference_y)  # optional persisted default
        self.assertEqual("16011387810716297352", layout_fingerprint(settings))
        self.assertEqual("5082858087917131076", atlas_world_hash(settings))

    def test_current_options_match_real_java_prewarm_identity(self) -> None:
        # Cross-checked by RingTerrainAtlasTest.fixedMasterIdentityMatchesQualificationReader.
        raw = settings_bytes(-7617918596273893784, width=256, circumference=16384,
                format_version=5,
                wall=dict(thickness=7, palette=4, pattern=3, decay=10, format=1),
                generation=dict(atlas_fidelity=3, layout=0, continuous_river=False,
                                more_structures=False, format=1))
        settings = parse_persisted_ring_settings(raw, Path("/world/settings.dat"))
        self.assertEqual((7, 4, 3, 10, 3), (settings.wall_thickness,
                settings.wall_palette, settings.wall_pattern, settings.wall_decay,
                settings.atlas_fidelity))
        self.assertEqual("6214264662786933286", layout_fingerprint(settings))
        self.assertEqual("3372587833368448249", atlas_world_hash(settings))

    def test_rejects_unknown_or_incomplete_saved_options(self) -> None:
        for wall in (dict(thickness=7),
                     dict(thickness=7, palette=10, pattern=3, decay=10, format=1)):
            with self.subTest(wall=wall), self.assertRaises(InvocationError):
                parse_persisted_ring_settings(settings_bytes(wall=wall), Path("/settings"))
        with self.assertRaises(InvocationError):
            parse_persisted_ring_settings(settings_bytes(generation=dict(
                    atlas_fidelity=5, layout=0, continuous_river=False,
                    more_structures=False, format=1)), Path("/settings"))

    def test_atlas_header_presence_and_complete_chunk_counts(self) -> None:
        raw = atlas_bytes(8_665_210_144_080_158_345, 8)
        atlas = parse_ring_terrain_atlas(raw, Path("/world/terrain-atlas.rwat.gz"))
        self.assertEqual(8, atlas.present_cells)
        self.assertEqual(0, atlas.present_chunks)
        complete = parse_ring_terrain_atlas(
            atlas_bytes(8_665_210_144_080_158_345, 851_968), Path("/world/terrain-atlas.rwat.gz"),
        )
        self.assertEqual(851_968, complete.present_cells)
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

    def test_rejects_obsolete_atlas_and_invalid_block_light(self) -> None:
        data = bytearray(gzip.decompress(atlas_bytes(1, 4)))
        data[4:8] = struct.pack(">I", 6)
        with self.assertRaises(InvocationError):
            parse_ring_terrain_atlas(gzip.compress(data), Path("/atlas"))
        data[4:8] = struct.pack(">I", 9)
        data[44 + 7] = 16
        with self.assertRaises(InvocationError):
            parse_ring_terrain_atlas(gzip.compress(data), Path("/atlas"))


if __name__ == "__main__":
    unittest.main()
