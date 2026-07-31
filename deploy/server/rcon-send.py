#!/usr/bin/env python3
"""Send one command to the local RingWorld Minecraft RCON endpoint."""

from __future__ import annotations

import socket
import struct
import sys
from pathlib import Path


SERVER_PROPERTIES = Path("/opt/ringworld-server/server.properties")


def property_value(name: str) -> str:
    prefix = name + "="
    for line in SERVER_PROPERTIES.read_text(encoding="utf-8").splitlines():
        if line.startswith(prefix):
            return line[len(prefix) :]
    raise RuntimeError(f"missing {name} in {SERVER_PROPERTIES}")


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


def main() -> int:
    if len(sys.argv) < 2:
        print(f"usage: {sys.argv[0]} '<minecraft command>' [...] | -", file=sys.stderr)
        return 2

    commands = ([line.rstrip("\n") for line in sys.stdin if line.strip()]
                if sys.argv[1:] == ["-"] else sys.argv[1:])

    port = int(property_value("rcon.port"))
    password = property_value("rcon.password")
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
