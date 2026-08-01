# Modrinth staging

This directory is versioned release metadata only. It has no token, upload
client, project mutation, or Modrinth API request.

From the repository root, build and stage in one command:

```sh
python3 scripts/stage_modrinth_release.py --build
```

The command runs the Java 25 Gradle test/build gate, validates exactly one
runtime jar, and writes an ignored review directory under `dist/modrinth/`.
Run it only from a clean, pushed public branch commit with the standard HTTPS
`origin`: the manifest records that checkout's `HEAD`, rather than a circular
hash embedded in source.
Run it only from a clean, pushed public branch commit: the manifest records
that checkout's `HEAD`, rather than a circular hash embedded in source.
Only the staged `ringworld-*.jar` is a potential upload file. The checksum,
manifest, and Markdown are review material and must not be uploaded as extra
version files. Staging never uploads, publishes, changes a listing, or accepts
credentials.

Read [`docs/MODRINTH_RELEASE.md`](../../docs/MODRINTH_RELEASE.md) before a
separately authorized manual release action.
