# RingWorld 1.3 release preparation

Six local JARs are staged under `dist/qualified-release/final-1.3/` from
source commit `f1b243af48f44452179f7a109877799584c8f47d`. They cover
Minecraft 26.1–26.1.2, 26.2 and 26.3 on Fabric and NeoForge. CurseForge
upload waits for the owner's visual confirmation. No 1.3 file has been
uploaded. Modrinth, optional launcher bundles and saved-world upgrades are
outside this release scope.

All six staged JARs passed hash, staging-manifest, source-link and metadata
checks. The Python release suite passed 439 tests with two expected platform
skips; Fabric and NeoForge build/unit checks passed for all five listed
Minecraft versions. Fresh frozen-candidate quick qualification passed for
26.1.x (`20260929T220403Z-6008662a741e`), 26.2
(`20260929T221843Z-8f8fa35ec37f`) and 26.3
(`20260929T223545Z-5112093d09ec`).

The 26.2 nightly matrix passed all 20 fixtures in one run
(`20260930T071340Z-83fb16714794`); 26.3 likewise passed 20/20
(`20260929T224354Z-b07720a411bb`). For 26.1.x, the original 60-fixture
matrix recorded 42 passes and stopped affected cells after five failures.
Four lifecycle failures came from its survival-mode test player falling from
an airborne saved position; the fifth was a Gradle report-directory error
after the multiplayer game scenario completed. A copied source world with
creative mode enabled and five targeted reruns passed all 18 affected or
skipped fixtures on the same source commit and frozen JARs. The repository's
composite validator reviewed the resulting 60/60 passing fixture coverage at
`dist/qualification/release-1.3-composite-26.1.json`. This is **reviewed composite
coverage, not a monolithic nightly PASS**; preserve the original failure and
repair evidence when reviewing the release.

The exact staged NeoForge JARs also passed dedicated-server bootstrap, ready,
save and normal-stop smokes on newer official loaders: 26.1.2.112,
26.2.0.88 and 26.3.0.37-beta. Results and logs are retained under
`dist/qualification/release-1.3-newloader-smoke/`.

| Minecraft | Fabric SHA-256 | NeoForge SHA-256 |
| --- | --- | --- |
| 26.1–26.1.2 | `d47b92b43de08f08726c72f8c9c2dd56d63e9d757f260c45be7993f0a27ae0af` | `7d21ff3807cce833a8dd048475a41a4638c0c2704bb8247fd565e68e0493d69a` |
| 26.2 | `00c960be8b0a006ea8d55e5185af6ccfe9d391cdac7fd7a55ddcfe7adae85e01` | `c2b3a1a57741f3f2f1e35ccc702ce8d3cf2913cd61b3e76b46e20913f4d6b91a` |
| 26.3 | `aa3df8882554b8b15f1abfa6334ff3970649314308f0e4cf2b658af91dc03db5` | `9779daf3080132eafc45781ac7eadf1bff2136996b435e7e38324725476df83a` |

The [parity record](../../../docs/RELEASE_1_3_PARITY.md) explains which
changes were ported across version adapters. The
[patch notes](../../../docs/RELEASE_1_3_PATCH_NOTES_DRAFT.md) and per-version
changelogs describe changes since 1.2. After visual sign-off, check these
same six hashes during CurseForge upload and verify the hosted downloads.
