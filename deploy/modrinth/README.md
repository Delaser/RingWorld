# Historical Modrinth 1.0 staging inputs

The release JSON/changelog templates in this directory belong to the retained
1.0 clean-build workflow. They are not current matrix-release defaults.
`scripts/stage_modrinth_release.py` still uses them for that historical path;
its shared verification helpers are used by newer tools.

For a new supported-version release, follow
[the current JAR export runbook](../../docs/JAR_EXPORT.md) and explicitly select
new release config/notes with `stage_qualified_release.py`. Only the qualified
runtime JAR is uploaded; metadata, manifests and source archives are review or
source-delivery material. Modrinth publication remains deferred until authorized.

This directory contains no credentials or upload implementation.
