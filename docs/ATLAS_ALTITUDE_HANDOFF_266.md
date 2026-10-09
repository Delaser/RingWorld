# Altitude-aware terrain handoff — #266

Tracks [issue #266](https://github.com/Delaser/RingWorld/issues/266).
The wall closure repair is already merged through #265. This is a separate
coverage correction, not a change to wall dimensions or terrain generation.

## Failure and fix

The owner's complete 2,048 × 128 study showed a sky-coloured hole when looking
down from X1192.649 / Y234.67834 / Z0.181 at render distance ten chunks.
Vanilla `SectionOcclusionGraph.getRelativeFrom` rejects neighbouring sections
whose vertical section distance from the camera exceeds the configured view
distance. At camera section Y14, sections below Y4 (block Y64) are excluded.
High hills remain drawn while lower terrain beneath the player disappears.

The old Atlas alpha mask used only intrinsic horizontal distance. Directly
below the camera it suppressed the replacement even though native sections
were outside the vertical range. The Atlas was 100% complete; there was no
missing world data or failed download.

Both shaders now use coverage distance
`sqrt(horizontalIntrinsicDistance² + canonicalHeightSeparation²)` for their
coordinated handoff. The proxy derives world Y from cylinder radius and the
shared physical centre, and reads the camera's exact Y from integer origin
minus fractional camera offset. Live terrain uses the equivalent canonical
camera-relative vertex length. Periodic horizontal distance still wraps at
the X seam. New varyings match both shader resource ABIs, including the 26.3
OIT terrain passes. Visual profile version is now seven.

Coverage windows remain unchanged: proxy 0.68–0.98 of effective view distance,
live terrain 0.90–1.02. At ten chunks, the proxy is fully visible by 156.8
blocks of separation. The reported missing low terrain is over 171 blocks
below the player. At zero height separation, coverage is exactly the previous
horizontal handoff.

Material/detail/reveal distances remain horizontal. Existing live dithering,
smooth proxy alpha and depth writes, forward/reversed depth handling, curved
positions, finite Atlas sampling and native section budgets are retained.
No extra geometry, world setting, chunk loading or distant underside is added.
Non-RingWorld dimensions keep the existing native path.

## Verification

The static source-ABI regression checks both shader pairs and the 26.3 passes,
preserves horizontal material distance and periodic wrapping, and tests the
nearest vertically excluded section faces at 2/6/10/28-chunk distances for
cameras above and below the terrain. It covers the reported gap and the
zero-height control.

All six source build/test cells pass. Each native run compiles the selected
shader ABI, captures six settled views and exits normally (36 captures total).

| Minecraft | Fabric Java cases | NeoForge Java cases | Native captures per loader |
| --- | ---: | ---: | --- |
| 26.1.2 (26.1 ABI) | 457 | 457 | Six; normal exit 0 |
| 26.2 | 457 | 457 | Six; normal exit 0 |
| 26.3 | 460 | 460 | Six; normal exit 0 |

All Java cases have zero failures/errors/skips. Reviewed Medium images from
all six cells restore the lake/terrain coverage with equivalent appearance.
Ground-height controls retain detailed live blocks. All screenshot paths,
Java totals, runtime exits and external authentication errors are recorded in
`logs/atlas-altitude-handoff/results.json`. No shader compilation or RingWorld
surface-build errors occurred. Native captures do not establish frame-pacing
or authenticated multiplayer qualification.

The complete static contract suite passes 355 tests. Its initial local invocation
omitted the workflow's `PYTHONPATH=scripts`, causing four import errors; the
corrected environment passes without source changes. The initial 26.1.2
NeoForge capture was stopped before world entry because its disposable save
was copied to the root rather than `neoforge/run-client`. The next attempt
stopped at vanilla first-start accessibility onboarding. Copying the muted,
onboarding-complete test options before launch repairs the fixture setup.
Those aborted attempts are retained in separate logs and are not runtime passes.
The completed 26.2 Fabric run logs a development-profile certificate fetch
HTTP 401. This does not interrupt the six captures or normal shutdown and is
retained explicitly in the result report; it is not a rendering failure or
evidence of authenticated multiplayer coverage. The completed 26.3 Fabric run
also logs a timeout fetching Minecraft Services public keys. This external
network error is separately recorded; shader compilation, six captures and
normal shutdown all complete.

The opt-in copied-world fixture uses `ringworld.captureAtlasWalls=true` with
`ringworld.captureAtlasAltitude=true` and `ringworld.backgroundTestWindow=true`.
It opens only the disposable `Atlas Wall Review` save, mutes sound, uses ten
chunks, and captures the reported Y234.67834 view at Low/Medium/High, followed
by Medium at Y185, Y130 and Y340. All use the same X/Z and downward camera
orientation. The original save is preserved. For disposable NeoForge runs,
`config/fml.toml` disables the separate early splash, following the #264 test
convention. Each fixture exits normally after six captures.

Local evidence: `logs/atlas-altitude-handoff/`. The 26.2/26.3 runs reuse the
ignored isolated build/runtime directories under
`dist/qualification/wall-264-top-anchored/`; retained #264 screenshots/logs
remain separate. These are source-development checks, not a frozen release
qualification or a complete multiplayer/dimension lifecycle run.

The [before/after gallery](media/atlas-altitude-handoff/index.html) retains the
owner's original screenshot and the repaired Medium capture. The camera angle
is slightly different and the native fixture hides HUD/clouds; the comparison
demonstrates restored coverage rather than identical screenshot pixels.
