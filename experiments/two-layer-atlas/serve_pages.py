"""Prepare immutable localhost test payloads and print the manifest digest."""
import gzip
import hashlib
import io
from pathlib import Path
import sys
import zipfile

archive, dest = Path(sys.argv[1]), Path(sys.argv[2])
dest.mkdir(exist_ok=True)
with zipfile.ZipFile(archive) as source:
    def write(data):
        # Keep the gzip OS byte and timestamp deterministic across Python versions.
        buffer = io.BytesIO()
        with gzip.GzipFile(filename="", mode="wb", fileobj=buffer, compresslevel=1, mtime=0) as gz:
            gz.write(data)
        payload = buffer.getvalue()
        digest = hashlib.sha256(payload).digest()
        (dest / digest.hex()).write_bytes(payload)
        return digest
    pages = sorted((int(key) for key in source.namelist() if key != "meta"))
    assert pages == list(range(len(pages)))
    print(write(source.read("meta") + b"".join(write(source.read(str(i))) for i in pages)).hex())
