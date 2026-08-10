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
each generated `PROJECT_DESCRIPTION.md` and loader-specific `CHANGELOG.md`
contains the same exact immutable commit URL derived from that checkout. The
manifests repeat the provenance for local package assembly, but are not the
recipient-facing source route. Only each staged `ringworld-*.jar` is a
potential upload file. The checksums, manifests, and Markdown are review
material and must not be uploaded as extra version files. Staging never
uploads, publishes, changes a listing, or accepts credentials.

`version.artifact_version` names and validates the jar bytes. The separate
`version.version_number` is the public Modrinth identifier and must be the
current loader-labelled 1.0 value. This prevents Fabric, NeoForge, and older
alpha files from sharing an ambiguous hosted identifier.
Generated optional-package provenance retains both identities rather than
relabelling the shared runtime jar.

Read [`docs/MODRINTH_RELEASE.md`](../../docs/MODRINTH_RELEASE.md) before a
separately authorized manual release action.
