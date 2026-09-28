# Project description rewrite — 2026-09-07

Owner authorized continuing past the weekly quota pause to complete this rewrite.

`project-description.md` is the publish-ready shared page copy. `summary.txt` replaces the short project tagline. The CurseForge copy is mirrored in `deploy/curseforge/project-description.md`. The Modrinth staging template retains its required immutable source URL placeholder; use the shared page copy for a project-wide description, or render the template through the existing staging process for a particular release. Never paste the unresolved placeholder into a listing.

Reviewed the live CurseForge page at https://www.curseforge.com/minecraft/mc-mods/ringworld. Its requirements paragraph still named only 26.1.2, while its current 1.1 files cover 26.1–26.1.2 and 26.2. Version claims were cross-checked with `docs/RELEASE_1_1_PUBLICATION_2026-08-27.md`.

Modrinth's public project API returned 404 and its project page returned an error 500 in the browser. The session was not signed in. Its rewrite uses the existing tracked description template, not a verified live-page export.

The rewrite removes renderer implementation notes, repeated compatibility cautions and promotional qualifiers. It retains world sizes, immutable dimensions, Atlas generation costs and controls, installation requirements, multiplayer requirements, compatibility guidance, source and licence links. Unreleased wall, lighting and LOD changes are excluded.

Published the approved full description to CurseForge project 1645598 on 2026-09-07 after explicit owner authorization. The author dashboard reported “Changes saved successfully”; a fresh public-page load verified the complete replacement, headings, lists, version requirements and links. The short tagline, gallery, release files and changelogs were unchanged. Modrinth remains unpublished.
