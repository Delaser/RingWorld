# Active work list — 7 September 2026

Owner requested completion of the combined local/GitHub backlog. River work (#159), distant underside mesh and release/26.3 preparation are excluded. No release, publication or main-branch merge is implied. Record evidence and genuine external blockers per item rather than treating builds as full gameplay qualification.

- [x] Checkpoint and push the accepted seam, wall, underside and exterior changes.
- [x] Implement three client LOD levels: Low = previous Low, Medium = previous High, High = previous Max.
- [x] Profile walking/Atlas loading and new wall-texture upload/memory costs; address actionable stutter sources (#242).
- [ ] Validate new generation across seeds, dimensions, decay, both wall faces, underside and save/reload; include structure-density counts/balance (#160) and implemented layout validation (#161), excluding river and Climate Tour.
- [x] Validate Atlas recovery and multiplayer/cache revision behavior (#242).
- [x] Reconcile PR #245 with the accepted local work and retained qualification evidence; identify review gaps without publishing or merging.
- [x] Audit the third-party compatibility handoff and execute an explicit representative compatibility matrix (#98); record exact supported/unsupported versions.
- [x] Investigate/build a universal Fabric + NeoForge jar and run loader smoke tests (#144), without publishing it.
- [x] Final reviewed checkpoint and concise remaining-risk report.

The previous ambiguous default-on toggle request needs identification before changing an unrelated setting; it is not counted as implemented.

Initial checkpoint: `2a6ebcf` on `codex/atlas-fidelity-gallery`. Final work is committed on the same branch and reconciled into PR #245; unresolved acceptance items below remain open.

Live evidence and discovered regressions: [BACKLOG_VALIDATION_2026-09-07.md](BACKLOG_VALIDATION_2026-09-07.md).

Compatibility matrix is bounded to the versions in the evidence report; Create and renderer replacements remain unsupported/unqualified.

New findings requiring follow-up (not silently counted as passed):

- [ ] Production Archipelago smooth-join acceptance: reproducible failing seed; nearby reference cut also fails. Retain strict gate until generation/acceptance review resolves the cause.
- [ ] Whole-ring structure-density balance; current actual start/loot counts cover the seam strip.
- [ ] Remaining production frame outliers and large GPU uploads: starvation fixed, but the movement sample still has two frames over 50 ms. Completed one-block production multiplayer/cache coverage remains a separate gate.

Create has no official release for the tested Minecraft lines. Packaged universal-client and distribution-host qualification remain deferred development gates; no release is being prepared.
