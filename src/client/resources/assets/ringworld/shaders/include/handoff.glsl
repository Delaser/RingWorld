// Requires the shared globals and fog uniforms. Both sides of the handoff
// use the same atmospheric tint and reveal curve; no extra texture fetches.
float ring_handoff_smootherstep(float start, float end, float value) {
    float t = clamp((value - start) / max(0.0001, end - start), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

vec3 ring_handoff_edge_color(vec3 fogColor) {
    // Backdrop id shares the previously unused fourth Atlas-light component.
    if (RingWorldAtlasLight.w > 1.5) return vec3(1.0, 1.0, 3.0) / 255.0;
    if (RingWorldAtlasLight.w > 0.5) return vec3(5.0, 8.0, 16.0) / 255.0;
    return fogColor;
}

float ring_handoff_reveal(float distance, float rain) {
    float detail = ring_handoff_smootherstep(RingWorldDetail.x, RingWorldDetail.y, distance);
    float reveal = mix(RingWorldDetail.z, RingWorldDetail.w, detail);
    float fraction = clamp(distance / (float(RingWorldLayout.y) * 0.5), 0.0, 1.0);
    float haze = mix(RingWorldAtmosphere.x, RingWorldAtmosphere.y,
                     pow(fraction, RingWorldAtmosphere.z));
    // Introduce far haze continuously instead of a pale near-Atlas belt.
    return reveal * (1.0 - haze * detail) * (1.0 - 0.35 * rain * detail);
}

// A live water surface already supplies the biome tint. In the overlap, the
// Atlas colour behind it represents the submerged bed. Restore the surface
// colour before the live surface dithers away, or immediately for a teleport.
float ring_handoff_water_backing(float distance) {
    float restoreStart = mix(RingWorldHandoff.z, RingWorldHandoff.x, 0.8);
    float restore = smoothstep(restoreStart, RingWorldHandoff.x, distance);
    return mix(0.30, 1.0, max(restore, RingWorldRender.z));
}

// Carry the last live blocks' environmental fog onto the first Atlas faces.
// Cap at the native range, then hand it back to the ring's long-distance haze.
float ring_handoff_environment(float distance, float fogStart, float fogEnd) {
    float edge = RingWorldHandoff.y;
    float nativeDistance = 1.02 * min(distance, edge);
    float fog = clamp((nativeDistance - fogStart) / max(0.0001, fogEnd - fogStart), 0.0, 1.0);
    return fog * (1.0 - smoothstep(edge, 2.0 * edge, distance));
}

// A deferred pack has already shaded the opaque chunks when the Atlas is drawn.
// Blend matching opaque surfaces over the pack's distance fog. A sub-block
// offset along the view ray breaks depth ties with temporally jittered terrain.
// The screen position and alpha stay physical; restore exact depth past the overlap.
float ring_handoff_overlay_inverse_w(float inverseW, float distance) {
    float range = RingWorldHandoff.y;
    float offset = 0.75 * (1.0 - smoothstep(1.5 * range, 2.0 * range, distance));
    return inverseW / max(0.5, 1.0 - offset * inverseW);
}
