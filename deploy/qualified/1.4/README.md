# RingWorld 1.4 — qualified, not uploaded

Qualification completed on 10 October 2026. Six standalone runtime JARs are
prepared from merged source `6ee5bd56c1de8044c658d80d5a0c0f4a9d6c4085`.
**Automated release gates: PASS. Owner visual confirmation: pending. No upload,
host promotion, release tag or optional launcher bundle was produced.**

## Qualification

- 452 Python tests run: 450 passed; two Windows-only platform skips on macOS.
- All nine PR #277 checks passed, including all six loader/version source builds.
- All ten quick runtime cells passed. The 26.1.x pair was tested unchanged on
  26.1, 26.1.1 and 26.1.2.
- All 100 full-suite fixture invocations passed: 60 for 26.1.x, 20 for 26.2,
  and 20 for 26.3. Each group is a complete monolithic PASS, with zero retries
  or composite repairs. Native clients ran hidden and muted, serially on macOS
  with Java 25. Saved-world upgrade testing remains outside the agreed scope.
- Final audit rehashed 110 terminal reports and 770 retained artifacts, including
  all 160 production screenshots. Frozen/public byte-equivalence, loader metadata,
  source identity, sizes and final SHA-256 checks passed for all six JARs.
- All six CurseForge publication dry runs passed without credentials, network
  submission or upload.

| Group | Quick run | Full-suite run | Fixtures |
| --- | --- | --- | ---: |
| 26.1.x | `20261009T213747Z-11504c0f250e` | `20261009T220439Z-e823396385c1` | 60 |
| 26.2 | `20261009T215051Z-d01081307ef4` | `20261010T014508Z-fad933b0bf26` | 20 |
| 26.3 | `20261009T215726Z-c02b814905a9` | `20261010T025819Z-e0fcb6e3b5ca` | 20 |

Full-suite reports are retained at
`dist/qualification/nightly-matrix/<run>/terminal.json`. Their SHA-256 values:

- 26.1.x: `a6d0b477033d6ab4f0d6611de236dc4025143ffd16e63a470306b8b4c1ce1737`
- 26.2: `a7ccc1169114a89964813d490497ee0fbddce25039b3260615c1e4c49432ec36`
- 26.3: `73daec0d31238f38cedfea40a722ad86dd2a3f37e9c07c7ecaa2689dec5014c5`

## Prepared files

All six files are collected in `dist/qualified-release/final-1.4/jars/`, alongside
`SHA256SUMS.txt`. Canonical per-loader stages, changelogs, inventories and
provenance remain under `dist/qualified-release/final-1.4/1.4.0+mc<version>/`.
These generated files and native evidence are retained locally and ignored by Git.

| Minecraft | Loader | File | Bytes | MiB | SHA-256 |
| --- | --- | --- | ---: | ---: | --- |
| 26.1–26.1.2 | fabric | `ringworld-1.4.0+mc26.1.jar` | 3424081 | 3.27 | `05285000cee3be4b67246223f2733e7057a0f7392da539709136487e0570629c` |
| 26.1–26.1.2 | neoforge | `ringworld-neoforge-1.4.0+mc26.1.jar` | 3412587 | 3.25 | `06edfefd2c4cfa0b0eb594663217901e32c09f977a4d2d270cb1966587bd4cf9` |
| 26.2 | fabric | `ringworld-1.4.0+mc26.2.jar` | 3423930 | 3.27 | `a10f378c77b9ce8de4c155da80dfb11ef6d9690451092bbbaa88a7da5a1fdee8` |
| 26.2 | neoforge | `ringworld-neoforge-1.4.0+mc26.2.jar` | 3412434 | 3.25 | `7ba1d5926a5300cf9a6258d8a2e2fb2bcd2c74cee35b13156d4ac7dc4820b3c8` |
| 26.3 | fabric | `ringworld-1.4.0+mc26.3.jar` | 3429343 | 3.27 | `3c7778d101e62ebec1fdf6409deca53042076b894d683f7da5547c27ef4ad747` |
| 26.3 | neoforge | `ringworld-neoforge-1.4.0+mc26.3.jar` | 3417858 | 3.26 | `e3d32e323c53999d5b9a1248c4d09441c8f47b933c2b2e614475f1ee33367206` |

Fabric requires Fabric API; NeoForge has no external mod dependency. The
explicit release configs retain the published 1.3 rollback source and hashes.

## Changes and retained evidence

The export audit and test-speed improvements are merged in PRs #275 and #276.
Weather batching removes thirty client startups and thirty production-world
copies across ten runtime cells; no percentage speedup is claimed. All weather
conditions, per-view frame metrics, independent lifecycle/recovery runs and
settling delays remain required. See [test setup](../../../docs/RELEASE_TEST_SPEED.md).

The first full run exposed an obsolete floating-gold-block Atlas UI marker.
PR #277 changes that revision proof to stone, which the floating-platform
selector must retain. Hidden Fabric and NeoForge clients passed the corrected
placement/removal/handshake/disconnect path before integration. All candidates
were then rebuilt and fully requalified from the merged source above. The
stopped attempt remains under `logs/release-1.4/attempt-46650a2/`; it is not used
as final release evidence.

Final status and the independent retained-file audit are in
`logs/release-1.4/STATUS.json` and `logs/release-1.4/FINAL-EVIDENCE-AUDIT.json`.
The [patch notes draft](../../../docs/RELEASE_1_4_PATCH_NOTES_DRAFT.md) and three
staged changelogs describe the release. Wall preview PNGs remain repository assets;
production screenshots and logs remain local evidence.

## Next gate

The owner will perform visual confirmation before authorizing any upload.
Use the canonical per-loader stages with the qualified publisher, following
[JAR export](../../../docs/JAR_EXPORT.md). Publication requires the clean pushed
`codex/release-1.4-final-candidate` checkout at the frozen source commit above.
This qualification record is a later documentation-only change; it does not
change or rebuild the tested JARs.
