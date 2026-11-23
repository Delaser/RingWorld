# Compatibility contract

## Compatibility priorities from CurseForge (22 November 2026)

The [RingWorld comments](https://www.curseforge.com/minecraft/mc-mods/ringworld/comments) contain 54 posts, including replies. Several independent players describe incorrect shader-pack output. Others ask how to use Sodium, report invisible nearby walls, or ask whether a second distant-terrain renderer can coexist with the Ring Atlas.

| Priority | Player evidence | Test or engineering consequence |
|---|---|---|
| Terrain and shader packs | Reports name Iris, Photon, MakeUp Ultra Fast and Derivative. One 26.2 Sodium report distinguishes invisible **loaded** walls from visible **distant** walls. | First compare the same rim and seam on stock rendering and Sodium alone. Then add Iris with packs disabled and enable one pack at a time. Check real blocks, Atlas alignment, water, shadows and reload separately. |
| External distant terrain | One player asks about Voxy and Distant Horizons. | Check which renderer draws the distant surface, which projection it uses, and whether both write depth over the same terrain. The question alone establishes demand. |
| Audio and world generation | One player names Sounds without a version or symptom. Another reports playing successfully with Lost Cities and Oh The Biomes We've Gone. | Obtain a minimal audio reproduction before changing audio hooks. Check content generation and seam continuity on representative worlds before declaring support. |
| Older game versions | Several players request 1.20.1. A 1.21.1 user reports a NeoForge startup crash with Sodium 0.8.13. | Track the backport independently. [Issue #322](https://github.com/Delaser/RingWorld/issues/322) identifies an injection into a replaced section-rendering method. |

The explosion crash reported with RingWorld alone is tracked in [#301](https://github.com/Delaser/RingWorld/issues/301). The [compatibility umbrella](https://github.com/Delaser/RingWorld/issues/98) covers broader interoperability.

For new work, keep projection, fog, handoff, water and visibility decisions shared. Compose with existing shader reads and preserve the active provider's material. Use a renderer-specific adapter only to translate an actual difference in inputs or pass lifecycle. The Iris interface exposes separate geometry and shadow passes. A candidate should project the same canonical position in both, keep block coordinates available for material sampling, and compare shadow alignment under motion. [Geometry passes](https://shaders.properties/current/reference/programs/gbuffers/) · [Coordinate spaces](https://shaders.properties/current/how-to/coordinate_spaces/)

The November investigation traced the Atlas from its sky draw to the finished frame. The frame captures below distinguish a valid sky target from the separate buffers a deferred shader pack uses to light terrain.

### Development probes

The shader-loading hook now operates on the source or reader returned by the active resource loader. The 26.2 path passes unrelated readers through by identity without consuming or closing them. The terrain adapter applies projection after the renderer decodes the position, preserving the renderer’s arithmetic. Comparing actual Sodium JAR resources with the pre-change adapter produced byte-identical vertex and fragment sources for the tested 0.8.9 and 0.9.2 builds. Sodium 0.9.3 subtracts a camera offset at that step. A regression test reproduces the old rejection and checks that the offset is retained and the projection runs before optional fog.

Tests used an Apple M2 with macOS OpenGL, Fabric, seed 259 and a disposable 2,048 × 128 ring.

| Combination | Observation |
|---|---|
| Sodium 0.8.9 on 26.1.1 and Sodium 0.9.2 on 26.2 and 26.3 | Entered the ring, captured six water/surface views and recovered after a resource reload. |
| Sodium 0.9.2 on 26.2, with and without Iris 1.11.4 installed, packs off | Both completed the reload test. The above-water views showed the same curved ring. |
| Sodium 0.9.3 on 26.3 | The pre-change adapter rejected the shipped vertex shader. The revised client loaded the ring, crossed the seam and captured the curved arc and both visible loaded rims. |
| Stock renderer versus Sodium 0.9.2 on 26.2 | On copies of the same completed save, both traversed the wrap seam and drew both loaded rim walls. |
| Iris 1.11.4, Sodium 0.9.2 and MakeUp Ultra Fast 9.6a on 26.2 | The pack loaded, local walls rendered, and the camera crossed the seam. In the forward view, the opposite arc disappeared. Compare the [Sodium control](media/sodium-compatibility/26.2-sodium-seam.jpg) and [active pack](media/sodium-compatibility/26.2-makeup-seam.jpg). |
| Iris 1.11.4, Sodium 0.9.2 and Photon 1.4 on 26.2 | Iris disabled the pack when its deferred4 program needed 17 fragment samplers on a driver exposing 16. The same error occurred with Fabric explicitly disabling RingWorld. An active-pack comparison requires a driver that can link this program. |

### What happens to the far arc with a shader pack

A frame capture on the 26.2 seam isolated two separate problems. At the Atlas draw, Minecraft’s color and depth overrides were both unset. RingWorld drew into the main color and depth textures. The surrounding pack sky used a forward depth comparison, while the stock 26.2 Atlas pipeline used reversed depth. Switching to forward depth alone still left the arc missing. A diagnostic draw that always passed depth produced a clearly visible opposite arc in the main color texture. The finished frame from that run lost the arc and acquired holes in the nearby terrain. Generation, download and mesh construction had all completed.

Two experiments presented the same Atlas through a sky pass the pack recognizes. Both recovered the arc. The celestial pass lit it like the sun, and the alpha-blended End sky pass also turned the surrounding sky black. These findings rule out a depth-only change and a sky-sprite overlay as fixes for this pack. A terrain integration needs to supply color and per-pixel depth to the active scene before its fog, clouds and water are composed. The next acceptance views are the distant arc, a cloud crossing it, and a nearby water or terrain silhouette while moving. [Atlas color immediately after the diagnostic draw](media/sodium-compatibility/26.2-atlas-draw.png) · [Finished frame from the same diagnostic run](media/sodium-compatibility/26.2-atlas-finished.jpg) · [Celestial pass experiment](media/sodium-compatibility/26.2-atlas-celestial.jpg)

A third 26.2 probe submitted the curved mesh with Minecraft’s ordinary solid-block vertex format and material. The pack drew parts of the mesh alongside its terrain, but the far surface vanished into the sky and strips of wall projected above the loaded rims. The probe used a radial normal, full skylight and an opaque Atlas texture. It lacked the production wall material, distance handoff and far-depth encoding. Reversing triangle winding in that same probe changed which rim faces appeared and caused the Atlas to cover nearby trees and terrain. The production Atlas has two-sided faces and suppresses its coverage inside the real chunk range. Both properties need to survive a move to ordinary terrain rendering. The remaining distance question is where the pack clips or fogs an opaque curved patch with a known depth. The solid-block and sky-sprite experiments are absent from the development build. [Solid-block experiment](media/sodium-compatibility/26.2-atlas-solid-block-trial.jpg) · [Reversed winding](media/sodium-compatibility/26.2-atlas-winding-trial.jpg) · [Iris geometry pass reference](https://shaders.properties/current/reference/programs/gbuffers/)

### Shared pipeline ownership

RingWorld adapts terrain and cloud shaders as a matched vertex/fragment pair. A mod can reuse either vanilla stage with its own other stage and keep its original bindings. A mod can also reuse the builder after creating a curved pipeline. The finished pipeline receives RingWorld’s bindings without modifying the builder. On 26.3 a pipeline with additional programmable stages also retains its own layout.

On 26.2 a native test mod built both mixed-stage permutations and then reused the same builder for stock and custom materials. It verified the shader IDs and binding counts of each finished pipeline.

The Atlas now takes its color and depth from the sky renderer’s destination. Minecraft 26.1.x uses the main target for its sky. On 26.2 and 26.3 the renderer accepts a target at construction, which can differ from the main framebuffer. A native test mod supplied a second render target on 26.2 and observed that exact object at the Atlas GPU draw. A deferred shader pack can still composite from separately managed attachments.

## Renderer interoperability research (22 November 2026)

Reviewed against RingWorld `1b6c74c`, the installed Iris 1.11.4 API, Sodium 0.9.2/0.9.3 bytecode and the MakeUp Ultra Fast 9.6a pack. The captures above are previous native tests. This review inspected source and shipped artifacts.

| Renderer or mod | What its interface actually supplies | Consequence for RingWorld |
|---|---|---|
| Distant Horizons and Iris | Dedicated distant terrain, water and shadow programs, depth textures, and a projection with its own near and far planes. DH exposes depth direction and range per frame and lets an integrator schedule transparent geometry separately. [Iris pack interface](https://shaders.properties/current/reference/mod-support/distant_horizons/) · [DH render API](https://distant-horizons-team.gitlab.io/distant-horizons/com/seibel/distanthorizons/api/interfaces/render/IDhApiRenderProxy.html) | A depth sample needs the projection that produced it. Keep the stock ordered far-depth encoding for the stock pass. An external pass needs comparable physical distance or a distant projection it can reconstruct. |
| Voxy | Its pack integration selects separate opaque and translucent draw targets and exposes corresponding depth samplers. [Target selection](https://github.com/MCRcortex/voxy/blob/5bf33350a1cc7bb7e2b5d947b236c56d878b8a7d/src/main/java/me/cortex/voxy/client/iris/IrisVoxyRenderPipelineData.java) · [Depth bindings](https://github.com/MCRcortex/voxy/blob/5bf33350a1cc7bb7e2b5d947b236c56d878b8a7d/src/main/java/me/cortex/voxy/client/iris/VoxySamplers.java) | Give a cooperating renderer distinct terrain and water inputs. Reserve RingWorld's distant surface for a single owner in a ring view. Other dimensions can retain their own LOD renderer. |
| Colorwheel and Flywheel | Colorwheel exposes shader programs for Flywheel's material system. Packs can implement them explicitly. [Project design](https://github.com/djefrey/Colorwheel#difference-with-iris-flywheel-compat) | Preserve the pack's material and lighting decisions. A growing list of edits to individual pack shaders would create a separate maintenance burden for each pack. |
| Immersive Portals | Recursive rendering can draw another world with a transformed camera, clipped by stencil or through a separate framebuffer. [Rendering implementation](https://qouteall.fun/immptl/wiki/Implementation-Details) | Each draw needs the viewed level, camera and immersion state. Closing a temporary renderer should release its own resources while the live world's Atlas stays resident. |
| No Mine's Sky | Its author reports a curved planet LOD disappearing under Complementary even after targeting the framebuffer active after sky. Their request for a general Iris LOD provider was still open with no replies on review. [Iris issue 3455](https://github.com/IrisShaders/Iris/issues/3455) | This reproduces the symptom independently. The precise operation that loses RingWorld pixels still needs its own frame trace. |

**What we can use today.** Iris 1.11.4 can assign an ordinary terrain program and a shadow program, register a shadow callback, and report reversed depth. Its public API has no entry for publishing an independent far projection and depth pair. The DH implementation binds those through DH events. MakeUp 9.6a already ships Overworld DH terrain and water shaders plus Voxy configuration. Its deferred shader samples scene color and depth and classifies samples close to the far plane as sky in several branches. The presence of those files does not route RingWorld draws through them or prove which branch erased the arc. [Iris public API](https://github.com/IrisShaders/Iris/blob/4ce5bb6ddd4b964b2b63442ebfca7e101ff128d0/common/src/api/java/net/irisshaders/iris/api/v0/IrisApi.java) · [Iris DH event bindings](https://github.com/IrisShaders/Iris/blob/4ce5bb6ddd4b964b2b63442ebfca7e101ff128d0/common/src/main/java/net/irisshaders/iris/compat/dh/LodRendererEvents.java) · [MakeUp source](https://github.com/javiergcim/MakeUpUltraFast)

### Active sky views and Sodium bindings

The sky render state captures its own level, eye, fluid, sky profile and sun. Its frame-graph callback installs that snapshot for the duration of the draw and restores the outer view in a finally block. A different dimension gets its own sky. An underwater camera skips the Atlas even while another camera is above water. The Atlas shader receives the extracted eye in the existing dynamic transform upload. A secondary dry view has a separate, lazily allocated 192-byte RingWorld uniform buffer. The two buffers independently retain their last successful contents. When the camera, weather, lighting and remaining uniform values stay the same, the next frame skips the GPU write and command submission. Only the main view advances the cold-teleport tracker. Atlas pages belong to the client session and survive disposal of a temporary sky renderer. A completed in-world resource reload invalidates the Atlas materials so the next draw rebuilds with the newly loaded pack.

On 26.2 and 26.3 the optional Sodium adapter decorates the binding-layout builder and adds RingWorld bindings after Sodium writes its original uniform slice. A second test mod wrapped both original operations. On Fabric 26.2 it observed 2,621 original `u_Globals` writes followed by 2,621 RingWorld bindings at the checkpoint. The NeoForge 26.3 probe verified the first 1,000 pairs. Both built the layout once with the provider and RingWorld bindings present. The Fabric probe also drew a second dry camera to an offscreen target, verified an underwater camera issued zero Atlas draws, checked a secondary Nether level, and confirmed GPU texture identity survived closing a temporary sky renderer. A subsequent in-world resource reload replaced the Atlas texture and rebuilt its materials. The view handling and shader decisions are shared by Fabric and NeoForge. Both loaders built and passed their Gradle checks for 26.1, 26.1.1, 26.1.2, 26.2 and 26.3. Native clients covered the three rendering ABIs on Fabric 26.1.1, Fabric 26.2 and NeoForge 26.3.

### MakeUp frame trace

On Fabric 26.2, Sodium 0.9.2 and Iris 1.11.4 with MakeUp Ultra Fast 9.6a, a diagnostic shader colored a strip of the actual curved surface magenta. It preserved the production mesh, depth test, depth output and draw order. The strip covers blocks within 18 blocks of the width center and beyond 165 blocks of intrinsic distance. The camera was at (2, 121.62, 0.5), with 12 chunks of render distance. A test mod read both Iris scene-color textures, Minecraft main color and the shared depth texture at successive boundaries of one frame. Counts below use every other pixel in each direction (854 × 480 samples).

| Boundary | Marker in Minecraft main color | Marker in either Iris colortex1 texture |
| --- | ---: | ---: |
| Before the Atlas draw | 0 | 0 |
| Immediately after the Atlas draw | 10,154 | 0 |
| End of opaque geometry, after deferred lighting, and after composite | 10,154 at each boundary | 0 |
| After Iris’s final pass | 0 | 0 |

At sample (432, 90), the Atlas changed depth from 0 to approximately 0.000157. Iris used that same depth texture as `depthtex0`. The sample kept its Atlas depth through final presentation. Both copies of `colortex1` were byte-identical across the Atlas draw. MakeUp’s `deferred_fragment.glsl` reads color from `colortex1` and depth from `depthtex0`. The Minecraft main color image stayed byte-identical from the Atlas draw through the composite pass. `IrisRenderingPipeline.finalizeLevelRendering` then called `FinalPassRenderer.renderFinalPass`, which drew into main color and replaced it with the pack’s scene. The marked pixel became RGB (65, 97, 136). [See the same-frame captures](media/sodium-compatibility/26.2-makeup-frame-trace.jpg).

The follow-up opaque-material experiment below tested a single patch in the pack’s terrain color and depth targets, including occlusion by a nearby oak. The diagnostic marker and GPU readbacks are confined to the test build.

### Frame-time check

Paired production-path runs used the same pre-generated 2,048 × 128 ring, camera motion across the wrap, 12-chunk render distance and Apple M2 OpenGL backend. VSync was off and the frame limit was set to 260 FPS. The baseline came from commit `8849740`. The table compares the last baseline lap with the final build on the same route. The measurement includes settling after crossing the wrap, so sample counts vary. Neither build had the diagnostic shader or framebuffer readback enabled.

| Minecraft 26.2 Fabric | Before, mean ms/frame | After, mean ms/frame | Frames sampled, before / after | Frames over 50 ms, before / after |
| --- | ---: | ---: | ---: | ---: |
| Stock rendering | 8.06 | 7.21 | 886 / 989 | 0 / 0 |
| Sodium 0.9.2 | 6.30 | 4.54 | 1,134 / 1,576 | 0 / 0 |
| Sodium + Iris + MakeUp | 16.90 | 13.10 | 422 / 545 | 0 / 0 |

Earlier runs with the view fix alone varied in both directions. Two fixed-camera comparisons prompted the upload change. The final build keeps the primary Atlas draw count and skips submitting unchanged uniform data. Motion, teleport coverage, rain and settings changes still update their actual packed values. These measurements cover the tested route on this Mac. The Sodium captures kept the same terrain silhouette at both rims and across the wrap. A repeat with clouds disabled isolated a nearby cloud that appeared over terrain in one seam-join capture. At that pose the mean absolute RGB differences from the baseline were 0.10, 0.18 and 0.33 on a 0–255 scale.

On NeoForge 26.3 with Sodium 0.9.2, a test mod held the camera at the seam-join pose, warmed up for 150 frames, and sampled 600 frames. The trace counted actual writes to the RingWorld uniform buffer. The baseline wrote it 600 times. The updated renderer wrote it zero times during the stationary sample and used the primary view throughout.

| NeoForge 26.3 + Sodium | Before | After |
| --- | ---: | ---: |
| Moving seam route, average ms/frame | 5.90 | 5.97 |
| Stationary, average ms/frame | 5.85 | 5.22 |
| Stationary, 95th percentile ms/frame | 13.31 | 10.23 |
| CPU time submitting the Atlas draw, median ms | 0.910 | 0.025 |

A second run of the updated build measured 6.05 ms moving and 5.85 ms stationary, with stationary p95 at 12.87 ms. It again skipped all 600 stationary writes. Neither updated NeoForge run recorded a motion frame over 50 ms. These are elapsed frame times and CPU submission times. This driver supplied no GPU timer-query data.

### Automatic shader-pack support

The development build detects active Iris shader packs on the client. `/ringworld shadersupport` reports the preference and pack status. `/ringworld shadersupport off` bypasses the Atlas attachment and local terrain projection immediately. `/ringworld shadersupport on` restores automatic detection. The setting persists between sessions. Each multiplayer client controls its own preference and the shader pack retains its own settings.

Use `/ringworld atlascontrast` to see the current shader pack’s setting.
`/ringworld atlascontrast on` reduces clear-day Atlas haze and deepens midtones at full strength (100%).
`/ringworld atlascontrast off` restores the default look (0%). Use any whole percentage from 0 to 100,
such as `/ringworld atlascontrast 50` for half strength. Each client remembers the value
for each shader pack and applies it immediately while shader support is enabled.

Both loaders reuse the normal Atlas mesh, textures, water and depth. On the OpenGL backend, the main view draws the Atlas into the pack’s scene color after deferred lighting and before translucent terrain. Iris resolves the first color output of the pack’s translucent terrain program. The adapter uses the existing opaque path if that target is unavailable. It restores the previous attachment and framebuffer before returning. Nearby terrain and shadow programs use the shared ring projection. Iris is a compile-only dependency and remains optional for players.

#### Sildur’s black ring and Complementary’s border fog

[Matched interior views before and after](../experiments/opaque-atlas-patch/deferred-lighting-comparison.jpg)

With Sildur’s Vibrant v2.02 Extreme and Fog off, a pixel at (854, 225) on the top of the outer wall already had RGB (0.367, 0.387, 0.445) in the opaque albedo texture. Its encoded normal was (0, 0) and its lightmap buffer still held the clear value. The pack decodes that normal by taking a square root whose argument becomes −1. Both copies of its scene color were black after deferred lighting at this pixel. In a diagnostic run on the original draw path, changing only the decoded normal in a small rectangle brought that presented wall pixel from RGB (0, 0, 0) to (160, 142, 139). An adjacent untreated wall pixel stayed black. The distributed adapter contains no shader-pack edits. With Complementary Reimagined r5.10, deferred border fog instead mixed the distant wall into the sky color.

The detailed Atlas already supplies its own surface shading. RingWorld schedules that one draw with its original geometry, projection and frame uniforms at the end of Iris’s deferred lighting. The live depth attachment still occludes the Atlas behind nearer opaque blocks. The pack subsequently renders translucent terrain and post-processing. A fixed-camera test on Complementary placed a red concrete panel and lime stained glass in front of the arc. The concrete stayed opaque and the Atlas remained visible through the tinted glass. This uses neither pack names nor pack-specific definitions of albedo, material, normal or lightmap buffers. Pack effects already composited into the sky, including volumetric clouds, can appear behind the distant Atlas. [Iris deferred pass ordering](https://shaders.properties/current/reference/programs/deferred/)

With default settings the corrected arc was also visible in Complementary Unbound, Sildur’s Vibrant Extreme, BSL and MakeUp. Native smoke tests exercised the three rendering ABIs and the NeoForge client toggle.

Paired frame intervals on Minecraft 26.2 Fabric, Iris 1.11.4, Sodium 0.9.2, Apple M2 OpenGL, 12 chunks. In each of four fixed views the client alternated previous, changed, changed, previous for 180 frames each. Values are the average of the two window means, in milliseconds. This test includes all pack effects.

| Pack and setting | Interior | Outside rim | Full loop | Shore |
| --- | ---: | ---: | ---: | ---: |
| Sildur’s Vibrant Extreme, Fog off | 26.40 → 29.00 | 20.95 → 21.56 | 18.95 → 20.13 | 26.07 → 26.59 |
| Complementary Reimagined, default | 20.12 → 23.01 | 17.39 → 16.75 | 16.84 → 16.68 | 32.96 → 34.57 |

An extra early color/depth draw was also tested on the corrected Complementary interior. Alternating four 180-frame windows averaged 22.10 ms for one Atlas draw and 23.97 ms for two. The shared adapter issues one draw pass.

#### Wall flicker and fog at the live terrain boundary

The opening interior shot exposes two narrow strips where the generated walls and the Atlas describe the same surface. At a stationary camera, shader-pack terrain jitter changes which surface passes the depth test. The loaded wall receives the pack’s border fog during lighting, while the Atlas is drawn afterward. Alternating between those colors makes the depth conflict especially visible. Disabling temporal anti-aliasing on the original Complementary path also stopped the flashing, but left a bright fog stripe. The revised path keeps temporal anti-aliasing enabled. The shader-off control is stable. Sampling the distant wall from outside the ring misses this overlap.

The post-lighting draw now blends the Atlas color over matching opaque terrain using the existing distance fade. Its depth test gives the proxy a 0.75-block offset toward the camera along the view ray, tapering to zero between 1.5 and 2 times the handoff range. Screen positions stay fixed. The same calculation feeds both forward and reversed depth and the ordered far tail. Water backing and the separate replacement layer retain their own depth handling. The stock draw uses its original depth. This needs no extra render pass, texture lookup or change to a pack’s fog and anti-aliasing settings.

In the 26.2 Fabric interior at 12 chunks, a 4,442-pixel mask selects the two visibly flashing strips in the original Complementary capture. Across 38 consecutive stationary frame pairs, the fraction jumping by more than 24 grayscale levels fell from 30.91% to zero with Complementary Reimagined and from 49.52% to zero with MakeUp. The average brightness difference from the adjacent distant wall fell from 49.9 to 2.8 and 31.3 to 2.2 levels respectively. Four alternating 180-frame windows in the Complementary interior measured mean frame intervals of 23.05 / 21.50 / 20.15 / 20.70 ms (previous, revised, revised, previous). This check covers one Apple M2 OpenGL scene. [Matched view and enlarged seam](media/sodium-compatibility/shader-wall-overlap.jpg)

The seven matched Fabric recordings each include four 150-frame shots. Both stationary holds in the opening shot had zero above-threshold seam changes after the fix for Complementary Reimagined, Complementary Unbound, BSL, MakeUp and Sildur’s Vibrant Extreme (default and Fog off). The shader-off control measured zero throughout both holds before and after. Sildur's default preset already had low seam brightness contrast (3.9 before and 4.2 afterward). Nearby red concrete still occluded the ring and lime stained glass transmitted its color in the Complementary foreground fixture. NeoForge 26.2 presented all four matching views with Complementary active. Gradle checks passed on both loaders for 26.1, 26.1.1, 26.1.2, 26.2 and 26.3.

#### Earlier opaque-pass baseline for the five most-downloaded CurseForge shaders, 22 November 2026

Default pack settings, Minecraft 26.2 Fabric, Iris 1.11.4, Sodium 0.9.2, Apple M2 OpenGL, 12 chunks, VSync off. Each camera used the same completed 2,048 × 128 ring. The table gives mean frame intervals over 180 settled frames per view. The ranking is by lifetime downloads on [CurseForge](https://www.curseforge.com/minecraft/search?class=shaders&page=1&pageSize=20&sortBy=total+downloads).

| Pack | Inside | Outside rim | Full loop | Waterline | Default appearance |
| --- | ---: | ---: | ---: | ---: | --- |
| Complementary Reimagined r5.10 | 19.52 ms | 16.06 ms | 15.62 ms | 28.04 ms | Border Fog obscures the distant arc. |
| Complementary Unbound r5.10 | 22.83 ms | 22.78 ms | 19.46 ms | 36.53 ms | Border Fog obscures the distant arc. |
| BSL v10.1.8 | 19.17 ms | 20.06 ms | 16.67 ms | 31.81 ms | Detailed interior and shore. Clouds and haze obscure parts of the exterior. |
| MakeUp Ultra Fast 9.6a | 10.81 ms | 9.55 ms | 9.45 ms | 15.56 ms | Detailed arc, walls and water across all four views. |
| Sildur’s Vibrant v2.02 Extreme | 25.72 ms | 28.65 ms | 25.00 ms | 34.72 ms | The far side becomes a blue silhouette. |

The same MakeUp setup also rendered the normal mesh on NeoForge 26.2. Fabric native checks exercised the three renderer ABIs on 26.1.2, 26.2 and 26.3, with an additional 26.2 run without Iris or Sodium. Gradle checks passed for both loaders on all five supported 26.x versions.

The stock captures use the same renderer with Iris present and its shader pack disabled. The exterior and full-loop frames were pixel-identical to the previous shader-off control. In a separate test, disabling the pack’s **Border Fog** restored visible terrain and wall materials in both Complementary editions. In Sildur’s Vibrant Extreme, disabling Fog changed the blue silhouette to black. Its distant surface still lacks visible color and detail. The pack still controls clouds, lighting and atmospheric haze. Default presets and adjusted presets are separate results. These captures cover the stated OpenGL driver and pack versions.

#### Cost of the attachment switch

Four consecutive 360-frame windows at the same MakeUp waterline alternated per-draw, per-pass, per-pass, per-draw. Mean frame intervals were 16.31, 15.39, 15.12 and 15.12 ms respectively. Successful color target switches fell from approximately two to one per frame. Averaging the two windows per mode gives 15.71 ms before and 15.25 ms after. The individual windows overlap. Per-pass switching removes redundant driver work, while the frame-time gain varies.

### Curved Atlas terrain through a shader pack

The [dual-loader MC 26.2 experiment and finished-frame comparisons](../experiments/opaque-atlas-patch/README.md) submit one physical Atlas patch through an Iris-assigned block material. MakeUp Ultra Fast and Sildur’s Enhanced Default preserve the patch and a real foreground oak occludes it. Sildur’s Vibrant receives the albedo and then fogs it away in deferred shading. Restricting the optical range change to the Atlas draw preserves the pack’s fog for ordinary terrain. The whole-ring follow-up displays the completed Atlas and both textured rims inside MakeUp on both loaders. A near overlap closes the open sky seen with a hard cutoff at the view distance. Color, water, moving-view performance and terrain detail still differ from the ordinary ring. The add-on is separate from shipped jars. A subsequent MakeUp trial draws the existing detailed Atlas mesh into the opaque pass with its normal wall and water resources. Interior, exterior, full-loop and waterline views are compared at identical camera positions. [Detailed mesh, water and timings](../experiments/opaque-atlas-patch/README.md#stock-atlas-mesh-and-water-through-makeup) [Whole-ring comparison](../experiments/opaque-atlas-patch/full-ring-comparison.jpg)

### Further interoperability work

1. **Expand only after the patch survives and occludes correctly.** Feed the existing curved mesh, side colors, normals, light, water coverage and real-to-Atlas handoff into that path. Preserve canonical block coordinates for sampling, curved positions for drawing, and previous-frame transforms across the wrap for temporal effects. Check water in front of and behind a rim, fog and clouds crossing the arc, moving shadows, and reload. Compare stock captures and frame time against the existing renderer. Keep full-ring shader support experimental until a second independent pack passes. Treat cooperation with external LOD renderers as a separate ownership test.

## Compatibility work based on 1.4 (13 October 2026)

`codex/mod-compatibility-core`, based on `57efcb0`, preserves RingWorld's accepted
visuals while reducing ownership of Minecraft's shared shader state. The active
Windows 1.4 qualification and its frozen candidates remain unchanged.

RingWorld owns an independent 192-byte uniform block on `GameRenderer`, with
matching allocation, per-frame updates and shutdown cleanup. Minecraft retains
its normal `Globals` include and publisher. Terrain/cloud entry points use the
RingWorld namespace, selected by matching stock pipeline builders; unrelated
mod shaders retain their own assets and bindings. Incremental packaging removes
obsolete stock-namespace RingWorld overrides.

Vanilla terrain, clouds and Atlas reuse the same projection/handoff/material
GLSL; CPU visibility uses immutable `RingRenderView` snapshots. Optional renderer
adapters may translate vertex formats, binding and visibility hooks. They must
preserve the same geometry, altitude-aware depth handoff, water, atmosphere and
wall alignment. A flat fallback or disabled Atlas is not compatibility.

Sodium adapters now pass native development validation on all ten supported
Minecraft/loader cells, using 0.8.9 for 26.1/26.1.1 and matching 0.9.2 builds for
26.1.2/26.2/26.3. Corrected natural-world captures close the flat/live-versus-curved
Atlas gaps. Additional checks cover correction commands, reloads, objects,
weather/time, both other dimensions, exterior walls and two-client visibility.
See the [cause, implementation and validation record](SODIUM_COMPATIBILITY_2026_10_13.md)
for exact scope, evidence and retained failed diagnostics.

Older OpenGL adapters restore indexed/range and generic buffer bindings; newer
adapters declare the stock and private buffers through RenderPass. Visibility
uses shared curved bounds and refreshes its graph when stationary correction
settings change. Shader composition uses Sodium's original runtime resources;
no Sodium implementation or dependency is bundled. Unknown source anchors fail
clearly rather than silently drawing flat terrain. Optional mixins are absent
without Sodium and do not initialize foreign classes during discovery.

Development runs can load a clean mod directory through
`-PringCompatibilityModsDir=PATH_TO_MOD_JARS` on either loader. This does not
bundle dependencies and is rejected in formal vanilla release qualification.
Keep source/world/mod hashes with diagnostic captures. Native validation used
macOS OpenGL; it does not certify every driver, future Sodium build, shader pack
or modpack. Other replacement renderers still need narrow adapters to the same
contract. Content and texture-pack compatibility must be tested, not inferred.

The following older release records remain historical qualification evidence.

RingWorld is an engine-level mod, not an ordinary content mod. Both the server
and every client need the matching game version, loader and RingWorld build.
Compatibility contract version 1 remains the shared contract in the 1.1
Fabric and NeoForge releases for 26.1, 26.1.1, 26.1.2 and 26.2.

One unchanged jar per loader covers the three qualified 26.1.x patches;
26.2 has separate jars. Exact dependencies are in the [README](../README.md)
and host records are in the [1.1 release record](RELEASE_1_1_PUBLICATION_2026-08-27.md).
Later stable versions use the same fail-closed intake described in
[`MINECRAFT_VERSION_SUPPORT_PLAN.md`](MINECRAFT_VERSION_SUPPORT_PLAN.md).

## Supported baseline

Version 1.3 and later accept newer NeoForge builds above each jar's minimum,
while keeping its Minecraft version limits. Exact tested loader pins remain in
qualification records; accepting a loader version is not evidence it was tested.
Published 1.2 jars still have their old upper caps, including an exact
`26.3.0.7-beta` requirement for Minecraft 26.3.

The release gate covers this exact foundation:

| Component | Supported contract |
| --- | --- |
| Minecraft | Java Edition 26.1, 26.1.1, 26.1.2, 26.2 or 26.3; matching jar required |
| Java | 25 |
| Loader | Fabric Loader 0.19.3 or NeoForge at/above the matching JAR minimum; tested pins remain in qualification records |
| API | Matching Fabric API on Fabric only |
| Renderer | Vanilla OpenGL; the draft Sodium adapter has the native development coverage above; experimental Vulkan is not qualified |
| Server | Integrated and dedicated servers with RingWorld installed |
| Client | Matching RingWorld settings/atlas channel generations and format |

Content mods that only add blocks, items, recipes, sounds, mobs, or data-pack
content are the lowest-risk category, but they are not certified merely by
belonging to that category. Mods must still respect the finite Z band, the
periodic canonical X plane, and RingWorld's world-generation ownership.

Texture-only resource packs are expected to load, but the distant atlas uses
sampled map/biome colour rather than every pack's live block textures. Shader
packs are unsupported because RingWorld supplies its own terrain and cloud
shader contract.

## Known unsupported combinations

These remain unsupported by this source branch. Published JARs retain their
original compatibility inventories.
The loader probe logs a warning for shader-pack conflicts and an error for the other listed conflicts. Loading continues. For Iris, compare the same view with the pack on and off.

| Mod / family | Mod ID | Conflict |
| --- | --- | --- |
| Iris with an active shader pack | `iris` | Development builds detect active packs. Appearance depends on pack fog and compositing. See [tested packs and commands](#automatic-shader-pack-support). With packs off, Iris 1.11.4 passed the 26.2 surface and reload probe. |
| VulkanMod | `vulkanmod` | Replaces the OpenGL renderer and shader contract |
| Canvas Renderer | `canvas` | Replaces Fabric's vanilla-compatible renderer path |
| Distant Horizons | `distanthorizons` | Adds an independent flat-world distant LOD. An opt-in [Fabric 26.2 development trial](../experiments/dh-atlas-api/README.md) reads the DH cache into RingWorld’s curved mesh. |
| Bobby | `bobby` | Retains client chunks outside the transient presentation chart |
| Immersive Portals | `imm_ptl_core` | Rewrites world views and entity/chunk relationships |
| Gravity Changer | `gravity_changer` | Changes the vanilla `-Y` intrinsic gravity contract |
| C2ME | `c2me` | Rewrites chunk and world-generation internals |
| OptiFabric | `optifabric` | Replaces terrain and shader internals |
| Nvidium | `nvidium` | Replaces Sodium terrain with a separate unsupported rendering pipeline |

The Java inventory is authoritative for early detection:
`RingCompatibilityContract.VERSION == 1`. Adding a new entry does not break
the API version. Changing the meaning of a published status, coordinate
domain, or method does.

## Public read-only API

`RingWorldApi.API_VERSION == 1` exposes:

```java
boolean isRingWorld(ServerLevel world)
RingWorldSettings settings(ServerLevel world)
RingGeometry geometry(ServerLevel world)
Vec3 canonicalPosition(ServerLevel world, Vec3 intrinsicPosition)
Vec3 nearestPresentationPosition(ServerLevel world, Vec3 canonicalPosition,
                                 double referencePresentationX)
Vec3 physicalPosition(ServerLevel world, Vec3 intrinsicPosition)
RingPhysicalPose physicalPose(ServerLevel world, Vec3 intrinsicPosition,
                              float yawDegrees, float pitchDegrees)
```

The API never mutates settings, world state, entities, or a client's chart.
Canonical conversion is appropriate when data enters authoritative storage.
Nearest-presentation conversion is for observer-local display only and must
never be persisted. Physical pose is a render/visualization result containing
the physical position, circumference tangent, local up, width direction, and
view direction; it is not a request to replace vanilla gravity or physics.

Third-party custom payloads remain opaque to RingWorld. A compatible mod owns
its packet conversion and must use canonical values at server ownership
boundaries and nearest images for observer relationships. The current API has
no event that grants another mod ownership of RingWorld's client chunk chart,
shader globals, saved settings, or topology folding.

## Loader boundary

The compatibility catalog, pose math, cost model, settings, and coordinate
rules are loader-neutral. Fabric and NeoForge use narrow discovery adapters
that obtain loaded mod IDs and log matching contract entries without changing
the public contract or coordinate semantics. Any later loader or Minecraft
version remains unsupported until its own runtime and multiplayer gates pass.

## Reporting an unlisted conflict

Include the RingWorld version, Minecraft version, loader and API versions,
complete mod list, relevant `latest.log` section, and whether the failure also
occurs with only RingWorld and Fabric API. An unlisted combination is
**untested**, not automatically supported.

## Validation

- The 2026-08-31 audited source passes 377 Java unit and parameterized cases
  per loader with zero failures, errors, or skips. Coverage fixes the
  compatibility/API versions, exact mod-ID matching, immutable inventory,
  cardinal physical-pose basis, measured production reference, checked
  maximum scaling, and exact atlas transport bounds.
- A real Fabric 0.19.3 client with the resolved Fabric API baseline reached the
  main menu, initialized the vanilla/Indigo renderer, and loaded all RingWorld
  resources and shaders without a false compatibility match or crash.
- Positive conflict detection is a pure deterministic test. The release gate
  does not install unsupported third-party mods merely to force their startup
  paths.

### Historical initial diagnostics, 13 October 2026

The following intermediate statuses are superseded by the completed development
validation linked above. Original failed evidence is retained.

On unchanged 1.4 source `57efcb0`, 19 of 20 planned no-mod/Sodium startup and
capture cases completed, including the separately banked 26.1.2 Fabric pair.
26.3 NeoForge with Sodium timed out at approximately 90% client Atlas transfer;
it did not report a shader or startup crash. Retain that failure and retry it
serially. Completed captures still require visual review. Matched 26.1 and
26.3 Fabric seam images confirm a flat/live terrain and curved Atlas mismatch,
with visible sky gaps at the handoff. Both rims render in the current default
natural Medium world; the historical #249 missing-wall claim is not reproduced
by this case. The adapter and private entry-point routing need native checks
before any support status changes.

The initial adapter/native control pair also exceeded the old visual-parity
2,400-client-tick Atlas limit. The parity fixture now uses the existing lifecycle
6,000-tick cold-load allowance on both resource ABIs. A Medium Atlas alone needs
2,048 server ticks at the ordinary tile budget, before startup and tick delays.
Completion, rendered-section, pose and screenshot checks are unchanged; this
changes only the opt-in fixture deadline, not game behaviour or the active frozen
Windows qualification. Preserve the original failed runs.

Native follow-up: the first corrected 26.1.2 Fabric control/Sodium pair completes
and the large sky gaps at the seam are closed. The newer 26.2/26.3 no-mod controls
also complete. Their Sodium startup checks identified the moved occlusion policy:
the modern adapter modifies the boolean passed to `prepareRenderTrees`, which
the worker captures; the older adapter retains `shouldUseOcclusionCulling`. A
shared visibility-demand hook marks only the visibility graph dirty when correction mode or
distance changes, covering stationary-camera command changes without remeshing.
These repairs require repeat native checks; earlier failed evidence is retained.

The projection-change check runs before `needsUpdate` returns: older Sodium
skips its update method entirely when the graph is clean, so checking inside
update would miss a command issued with a stationary camera. The shared demand
hook covers both old and asynchronous renderers without new meshes. The first
corrected 26.2 and 26.3 Fabric seam/rim capture runs now complete as well; wider
loader coverage and native correction/reload checks remain in progress.
