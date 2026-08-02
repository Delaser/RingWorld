# Modrinth staging

This directory is versioned release metadata only. It has no token, upload
client, project mutation, or Modrinth API request.

From the repository root, build and stage in one command:

```sh
python3 scripts/stage_modrinth_release.py --loader both --build
```

The command runs the Java 25 Fabric and NeoForge test/build gate, validates one
runtime jar for each loader, and writes separate ignored review directories
under `dist/modrinth/<version>/fabric/` and `.../neoforge/`. Use
`--loader fabric` or `--loader neoforge` for one explicit loader. Run it only
from a clean, pushed public branch commit with the standard HTTPS `origin`:
both manifests record the same checkout `HEAD`, rather than a circular hash
embedded in source. Only each staged `ringworld-*.jar` is a potential upload
file. The checksums, manifests, and Markdown are review material and must not
be uploaded as extra version files. Staging never uploads, publishes, changes
a listing, or accepts credentials.

Read [`docs/MODRINTH_RELEASE.md`](../../docs/MODRINTH_RELEASE.md) before a
separately authorized manual release action.
