# RingWorld 1.3 supported-version parity

**Release gate, 29 September 2026.** The owner stopped the initial release
tests when 26.1/26.2 were found to lack 26.3 rendering work. All 1.3 fixes,
performance improvements and features must have equivalent player-facing
behaviour on Minecraft 26.1–26.1.2, 26.2 and 26.3, on Fabric and NeoForge.
Future supported 26.x versions inherit the same requirement. No 1.3 jar has
been uploaded. The interrupted test run cannot qualify the changed source.
The owner authorized a fresh full suite and six local jars; upload waits for
visual confirmation.

| 1.3 change | 26.1/26.1.2 and 26.2 implementation | 26.3 implementation | Current evidence |
| --- | --- | --- | --- |
| Creation/editor, seed Use/Apply, local Low/Medium/High and block outline | Shared UI and geometry correction | Shared UI with version-owned API adapters | Six source builds/tests passed before parity repair; recheck final source |
| Ordered terrain fade and quiet Atlas cache snapshot | Shared | Shared | Source tests pass; runtime recheck pending |
| Distant terrain depth ordering | Shared old-GLSL shader reconstructs depth before far clamp | 26.3 shader uses the same depth curve | Source contract passes; older runtime motion review pending |
| Wall mip filtering and GPU upload reuse | Shared renderer plus old-API GPU samplers | 26.3 renderer and GPU adapter | Older source compiles; wall motion and upload review pending |
| Block-texture-derived Atlas wall palette | Shared model/sprite palette adapter and resource-reload cache | 26.3 model/sprite palette adapter | Older source compiles; resource-pack/reload review pending |
| Authored Atlas water, live-water fade and continuous land haze | Shared Atlas data, shader and texture worker | Shared Atlas data with 26.3 shader/worker adapter | Older source compiles; day/rain/water review pending |
| NeoForge minimum-only loader metadata | Shared packaging | Shared packaging | Packaging contract already passed; final jar review pending |

Two 26.3 fixes have no matching failure mechanism in the older rendering APIs:
the null sky-colour guard protects a nullable 26.3 sky vector, whereas the
26.1/26.2 sky colour is a primitive integer; the dedicated pipeline-compilation
worker protects 26.3's asynchronous `PipelineCache`, which is absent from the
26.1/26.2 client jars. The common sky appearance, reload behaviour and smooth
startup still need older-version runtime checks.

Current development checks: Fabric and NeoForge client sources compile for
26.1, 26.1.1, 26.1.2, 26.2 and 26.3; the ten version-source contracts pass.
Fabric and NeoForge unit suites pass on all five listed Minecraft versions.
Hidden-window
26.1.2 and 26.2 Fabric/NeoForge Atlas UI/world runs each reached a
complete 2,048×128 Atlas, rendered the ported surface shader and wall texture,
and disconnected normally. Their logs and world captures are retained in
ignored `logs/1.3-parity/` by version and loader.
These are development checks only.
Before 1.3 publication, verify shader load and motion at the depth handoff,
wall filtering and texture-pack reload, land/water transitions, and final jars
on both loaders for each supported version line; then run fresh qualification.
