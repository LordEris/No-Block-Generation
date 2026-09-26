#!/usr/bin/env python3
"""Sends one command to the smoke-test server over RCON and prints the reply."""
import socket
import struct
import sys

HOST, PORT, PASSWORD = "127.0.0.1", 25575, "smoke-test"


def main():
    command = " ".join(sys.argv[1:])
    with socket.create_connection((HOST, PORT), timeout=60) as sock:
        def send(request_id, kind, payload):
            body = struct.pack("<ii", request_id, kind) + payload.encode("utf-8") + b"\x00\x00"
            sock.sendall(struct.pack("<i", len(body)) + body)

        def receive_exactly(size):
            data = b""
            while len(data) < size:
                chunk = sock.recv(size - len(data))
                if not chunk:
                    raise ConnectionError("RCON connection closed")
                data += chunk
            return data

        def receive():
            (length,) = struct.unpack("<i", receive_exactly(4))
            data = receive_exactly(length)
            request_id, _ = struct.unpack("<ii", data[:8])
            return request_id, data[8:-2].decode("utf-8", "replace")

        send(1, 3, PASSWORD)
        if receive()[0] == -1:
            sys.exit("RCON authentication failed")
        send(2, 2, command)
        print(receive()[1])


if __name__ == "__main__":
    main()
