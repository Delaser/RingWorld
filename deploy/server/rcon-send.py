#!/usr/bin/env python3
"""Send one command to the local RingWorld Minecraft RCON endpoint."""

from __future__ import annotations

import argparse
import os
import socket
import struct
import sys
from pathlib import Path


DEFAULT_SERVER_PROPERTIES = Path("server.properties")


def property_value(server_properties: Path, name: str) -> str:
    prefix = name + "="
    for line in server_properties.read_text(encoding="utf-8").splitlines():
        if line.startswith(prefix):
            return line[len(prefix) :]
    raise RuntimeError(f"missing {name} in {server_properties}")


def packet(request_id: int, packet_type: int, payload: str) -> bytes:
    body = struct.pack("<ii", request_id, packet_type) + payload.encode("utf-8") + b"\0\0"
    return struct.pack("<i", len(body)) + body


def receive_exact(connection: socket.socket, length: int) -> bytes:
    chunks: list[bytes] = []
    remaining = length
    while remaining:
        chunk = connection.recv(remaining)
        if not chunk:
            raise ConnectionError("RCON connection closed unexpectedly")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def receive(connection: socket.socket) -> tuple[int, int, str]:
    length = struct.unpack("<i", receive_exact(connection, 4))[0]
    body = receive_exact(connection, length)
    request_id, packet_type = struct.unpack("<ii", body[:8])
    return request_id, packet_type, body[8:-2].decode("utf-8", errors="replace")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Send commands to the RCON endpoint configured by server.properties")
    parser.add_argument(
        "--server-properties",
        type=Path,
        default=Path(os.environ.get("RINGWORLD_SERVER_PROPERTIES", DEFAULT_SERVER_PROPERTIES)),
        help="server.properties path (default: ./server.properties or RINGWORLD_SERVER_PROPERTIES)")
    parser.add_argument("commands", nargs="+", help="Minecraft commands, or '-' to read stdin")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)

    commands = ([line.rstrip("\n") for line in sys.stdin if line.strip()]
                if args.commands == ["-"] else args.commands)

    port = int(property_value(args.server_properties, "rcon.port"))
    password = property_value(args.server_properties, "rcon.password")
    with socket.create_connection(("127.0.0.1", port), timeout=5.0) as connection:
        connection.sendall(packet(1, 3, password))
        auth_id, _, _ = receive(connection)
        if auth_id == -1:
            raise PermissionError("RCON authentication failed")
        for index, command in enumerate(commands, start=2):
            connection.sendall(packet(index, 2, command))
            response_id, _, response = receive(connection)
            if response_id != index:
                raise RuntimeError("unexpected RCON response")
            if response:
                print(response)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
