// Requires the shared globals and fog uniforms. Both sides of the handoff
// use the same atmospheric tint and reveal curve; no extra texture fetches.
float ring_handoff_smootherstep(float start, float end, float value) {
    float t = clamp((value - start) / max(0.0001, end - start), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

vec3 ring_handoff_edge_color() {
    // Backdrop id shares the previously unused fourth Atlas-light component.
    if (RingWorldAtlasLight.w > 1.5) return vec3(1.0, 1.0, 3.0) / 255.0;
    if (RingWorldAtlasLight.w > 0.5) return vec3(5.0, 8.0, 16.0) / 255.0;
    return FogColor.rgb;
}

float ring_handoff_reveal(float distance) {
    float detail = ring_handoff_smootherstep(RingWorldDetail.x, RingWorldDetail.y, distance);
    float reveal = mix(RingWorldDetail.z, RingWorldDetail.w, detail);
    float fraction = clamp(distance / (float(RingWorldLayout.y) * 0.5), 0.0, 1.0);
    float haze = mix(RingWorldAtmosphere.x, RingWorldAtmosphere.y,
                     pow(fraction, RingWorldAtmosphere.z));
    return reveal * (1.0 - haze);
}
