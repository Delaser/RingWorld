from __future__ import annotations

import importlib.util
import struct
import tempfile
import unittest
from unittest import mock
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "deploy" / "server" / "rcon-send.py"
SPEC = importlib.util.spec_from_file_location("ringworld_rcon_send", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
RCON = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RCON)


class _ChunkedConnection:
    def __init__(self, payload: bytes, chunk_size: int = 3) -> None:
        self.payload = payload
        self.chunk_size = chunk_size

    def recv(self, length: int) -> bytes:
        count = min(length, self.chunk_size, len(self.payload))
        result = self.payload[:count]
        self.payload = self.payload[count:]
        return result


class ServerRconSendTest(unittest.TestCase):
    def test_cli_uses_environment_default_and_explicit_override(self) -> None:
        with mock.patch.dict(RCON.os.environ, {"RINGWORLD_SERVER_PROPERTIES": "/tmp/from-env"}):
            from_environment = RCON.parse_args(["say hello"])
            explicit = RCON.parse_args([
                "--server-properties", "/tmp/from-flag", "say hello",
            ])
        self.assertEqual(Path("/tmp/from-env"), from_environment.server_properties)
        self.assertEqual(Path("/tmp/from-flag"), explicit.server_properties)
        self.assertEqual(["say hello"], explicit.commands)

    def test_reads_requested_server_properties(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "server.properties"
            path.write_text("rcon.port=25575\nrcon.password=local-test-only\n", encoding="utf-8")
            self.assertEqual("25575", RCON.property_value(path, "rcon.port"))
            self.assertEqual("local-test-only", RCON.property_value(path, "rcon.password"))
            with self.assertRaisesRegex(RuntimeError, "missing motd"):
                RCON.property_value(path, "motd")

    def test_packet_round_trips_through_chunked_receive(self) -> None:
        encoded = RCON.packet(42, 2, "say hello")
        body_length = struct.unpack("<i", encoded[:4])[0]
        self.assertEqual(len(encoded) - 4, body_length)
        self.assertEqual((42, 2, "say hello"), RCON.receive(_ChunkedConnection(encoded)))

    def test_receive_exact_rejects_early_close(self) -> None:
        with self.assertRaisesRegex(ConnectionError, "closed unexpectedly"):
            RCON.receive_exact(_ChunkedConnection(b"x"), 2)


if __name__ == "__main__":
    unittest.main()
