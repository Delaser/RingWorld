# RingWorld 1.3 supported-version parity

**Release gate, 30 September 2026.** The owner stopped the initial release
tests when 26.1/26.2 were found to lack 26.3 rendering work. All 1.3 fixes,
performance improvements and features must have equivalent player-facing
behaviour on Minecraft 26.1–26.1.2, 26.2 and 26.3, on Fabric and NeoForge.
Future supported 26.x versions inherit the same requirement. No 1.3 jar has
been uploaded. Six final JARs are staged and the full automated fixture
coverage is complete; upload waits for the owner's visual confirmation.

| 1.3 change | 26.1/26.1.2 and 26.2 implementation | 26.3 implementation | Final automated evidence |
| --- | --- | --- | --- |
| Creation/editor, seed Use/Apply, local Low/Medium/High and block outline | Shared UI and geometry correction | Shared UI with version-owned API adapters | Source/unit checks and nightly creation/Atlas UI fixtures pass on all ten version/loader cells. |
| Ordered terrain fade and quiet Atlas cache snapshot | Shared | Shared | Source checks, Atlas recovery and production rendering pass; owner visual review remains. |
| Distant terrain depth ordering | Shared old-GLSL shader reconstructs depth before far clamp | 26.3 shader uses the same depth curve | Source contract and production-render fixtures pass; owner motion review remains. |
| Wall mip filtering and GPU upload reuse | Shared renderer plus old-API GPU samplers | 26.3 renderer and GPU adapter | Production-render and reload fixtures pass; owner wall-motion review remains. |
| Block-texture-derived Atlas wall palette | Shared model/sprite palette adapter and resource-reload cache | 26.3 model/sprite palette adapter | Production-render resource-pack capture/reload passes on every cell. |
| Authored Atlas water, live-water fade and continuous land haze | Shared Atlas data, shader and texture worker | Shared Atlas data with 26.3 shader/worker adapter | Production day/dusk/night/rain captures pass; owner seam review remains. |
| NeoForge minimum-only loader metadata | Shared packaging | Shared packaging | Six staged hashes/metadata pass; exact NeoForge JARs start and stop on newer loaders 26.1.2.112, 26.2.0.88 and 26.3.0.37-beta. |

Two 26.3 fixes have no matching failure mechanism in the older rendering APIs:
the null sky-colour guard protects a nullable 26.3 sky vector, whereas the
26.1/26.2 sky colour is a primitive integer; the dedicated pipeline-compilation
worker protects 26.3's asynchronous `PipelineCache`, which is absent from the
26.1/26.2 client jars. The shared sky and reload paths passed older-version
nightly runs; the owner still needs to review their appearance.

Fabric and NeoForge sources compile and pass unit suites on 26.1, 26.1.1,
26.1.2, 26.2 and 26.3; all ten version-source contracts pass. Fresh quick
qualification passed for all six release JARs. The 26.2 and 26.3 nightly
matrices each passed 20/20 in one run. The 26.1.x matrix has 60/60 **reviewed
composite coverage**: 42 original passes plus 18 passing targeted repairs.
It is not a monolithic nightly PASS. Four original lifecycle failures were
caused by a survival-mode test player falling from an airborne saved position;
one multiplayer fixture completed its game scenario but Gradle failed while
writing a report directory. The repairs used the same frozen JARs and source
commit; lifecycle repairs used a copied creative-mode test world. See the
[release record](../deploy/qualified/1.3/README.md) for run IDs and hashes.
Before upload, the owner will review the staged visuals, especially motion at
the depth handoff, wall filtering and the land/water seam.
