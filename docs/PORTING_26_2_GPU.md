# 26.2 GPU adapter boundary

`RingSurfaceTextureRenderer` owns RingWorld's atlas snapshots, asynchronous
pixel preparation, mesh selection, morph state, and cylindrical transforms.
The narrow versioned `RingSurfaceGpu` helper owns only GPU ABI calls:
pipeline layout, vertex-buffer construction, texture allocation/upload, draw
submission, and `writeBuffer` for the shared Globals mixin.

The 26.1 helper preserves the existing command path. The 26.2 helper uses the
new GPU ABI: `GpuFormat.RGBA8_UNORM`, `PrimitiveTopology.TRIANGLES`, explicit
vertex binding, bind-group layouts, vertex-buffer slices, four-argument draw,
`Optional<Vector4fc>` render-pass clears, and explicit command submission.
Its pipeline inherits `Globals`, `MATRICES_PROJECTION`, and `Sampler0` from
`GUI_TEXTURED_SNIPPET`; it adds only fog and Samplers 1–2. The inherited
`MATRICES_PROJECTION` layout already contains `DynamicTransforms`; adding a
separate layout for it fails shader linking with a duplicate bind name.
The 26.2 direct draw call is `(vertexCount, instanceCount, firstVertex,
firstInstance)`, so the ring submits `(vertexCount, 1, 0, 0)`.

This adapter is a source-ABI preparation step only. It is not runtime
qualification evidence for 26.2.

26.2 also moved section-neighbour readiness from `RenderSection` to
`SectionUpdateTracker`. Its version-owned mixin preserves the finite-Z rim
exemption only for exterior section positions; ordinary missing neighbours
still require vanilla full-chunk and lighting readiness.
