# 26.2 qualification checkpoint — 2026-08-27

Publication, merging and live-server changes remain on hold. Runtime work uses
disposable worlds; the live demo world is untouched. The static workflow passes
**339 tests** after the fixture and composite-review corrections.

## Current result

- Paired quick `20260827T103705Z-a0890015e3e3` passes both loaders from clean,
  pushed source `1cfac9b10648054e46f4303f6e8b87df9b9bcdba`.
- Nightly `20260827T104447Z-6af7691cc891` retains **16 PASS, 2 FAIL,
  2 INCOMPLETE**. Both curved-object source-ABI captures fail; their dependent
  production-render checks were not executed. All other eight checks per
  loader pass, including raids, maps/compasses and production-world lifecycle.
- The capture failure is a fixture chart-coordinate assumption, not evidence
  of missing terrain: after spawning near the end of the ring, canonical
  X=0.5 arrives as presentation X=2048.5. Raw-X stage guards and block probes
  never accepted the correct client chart. Fix `5467f20` uses periodic target
  distance and nearest-image probes, preserves the timeout/render-readiness
  gate, and adds structured timeout diagnostics.
- Next: rerun only curved objects and production rendering on both loaders,
  then rehash and review composite coverage. No repaired result is claimed yet.

The earlier boat-fixture metadata failure was fixed by validating actual
factory-created entities. Targeted Fabric multiplayer
`20260827T103208Z-ab11a8f68225` passes with the same Fabric frozen hash as the
paired quick. Historical failed attempts remain unchanged.

## Candidate identity

The retained frozen candidates remain unchanged:

| Loader | Frozen SHA-256 |
| --- | --- |
| Fabric | `7cf2a56aea4946f27b953df65f296abc2f72c0379d7da52562bd10a499273a01` |
| NeoForge | `1d3070be8be479b6438254e033ed3afe905e5d04da632dab5a20e395f4e3bd5a` |

The later capture fix is **source-ABI fixture evidence**, not a claim that the
unchanged frozen jars contain that fix. Production-render repairs must use the
above exact hashes and original quick evidence. The generic repair-review mode
in `review_composite_nightly_evidence.py` records separate aggregate/source
identities, requires complete manifest coverage, rejects replacing an existing
PASS, and rehashes terminals and retained artifacts. Its output is explicitly
composite review, never a monolithic all-PASS execution or upload authorization.

## Local packages

Refreshed stages are under
`dist/qualified-release/fixture-fix-20260827/1.1.0+mc26.2`; six client/server
review archives are under `dist/qualified-package-review/fixture-fix-20260827`.
Metadata-only equivalence, checksums, nested MPL licensing, source links and
exact runtime pins pass. The staged release hashes are:

- Fabric: `dbc4d0ff170a8b3850c85edf859865e8ce10a12a7296a6f83dd324a193138949`.
- NeoForge: `15e2d2c9e84ed9a421351bce34ad5f24dfafe2349772cbe42bf34b8cda1ce0a5`.

Four publication plans validate without tokens, network calls or host changes.
Actual publication requires explicit owner approval and a clean checkout
matching the staged binary source revision; a later fixture/docs checkout must
not bypass that boundary.

26.2 server-overlay smoke roots are prepared on localhost ports 26563/26564,
but have not launched. Packaged graphical clients are not yet qualified.
Never copy a normal Prism account/token store into qualification state; a fresh
authenticated disposable profile and real Windows confirmation remain separate
owner gates.

## Remaining work

1. Complete and review the four targeted nightly repair checks.
2. Complete copied-world upgrades: 26.1 and 26.1.1 Fabric source fixtures pass;
   four further source fixtures and all eight upgrade routes remain.
3. Run isolated server-overlay smokes; prepare authenticated macOS and Windows
   package review without treating archive tests as gameplay evidence.
4. Finalize documentation and the owner handoff; **stop before publication**.

The earlier 26.1.x 60-slot review remains separately documented in
[the composite review](QUALIFICATION_REVIEW_2026-08-27.md). Do not rerun or
relabel those unchanged passing results as part of the 26.2 fixture repair.
