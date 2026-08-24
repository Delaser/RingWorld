// Minecraft 1.21.1 splits chunk terrain across several core programs. Keep
// the visual handoff policy in one adapter include so every layer converges
// on the same tone before its live coverage disappears into the Atlas.
uniform vec4 RingWorldHandoff;
uniform vec4 RingWorldDetail;
uniform vec4 RingWorldAtmosphere;
uniform float RingWorldLegacyProxyRevealScale;
uniform float RingWorldLegacyProxyDrawn;

float ringSmootherstep(float edge0, float edge1, float value) {
    float t = clamp((value - edge0) / max(0.0001, edge1 - edge0), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

float ringLiveCoverageFade(float intrinsicDistance) {
    // Handoff.x remains the shared live-fade start. The 1.21.1 pre-terrain
    // compositor deliberately begins live coverage at the earlier proxy
    // start so both layers overlap continuously before the shared endpoint.
    float legacyLiveFadeStart = RingWorldHandoff.z;
    return ringSmootherstep(
        legacyLiveFadeStart, RingWorldHandoff.y, intrinsicDistance)
        * clamp(RingWorldLegacyProxyDrawn, 0.0, 1.0);
}

// Let live terrain approach the proxy-like tone before its alpha falls away.
// The finite-slope quadratic most effectively suppresses the low-frequency
// live/Atlas mismatch while still reaching the exact endpoints.
float ringToneConvergence(float coverageFade) {
    float remaining = 1.0 - clamp(coverageFade, 0.0, 1.0);
    return 1.0 - remaining * remaining;
}

float ringTerrainReveal(float intrinsicDistance, float circumference) {
    float detail = ringSmootherstep(
        RingWorldDetail.x, RingWorldDetail.y, intrinsicDistance);
    float reveal = mix(RingWorldDetail.z, RingWorldDetail.w, detail);
    float legacyLiveFadeStart = RingWorldHandoff.z;
    float handoffEnvelope = 1.0 - ringSmootherstep(
        legacyLiveFadeStart, RingWorldDetail.y, intrinsicDistance);
    reveal = max(reveal, mix(RingWorldDetail.w, 1.0, handoffEnvelope));
    float farFraction = clamp(
        intrinsicDistance / (circumference * 0.5), 0.0, 1.0);
    float haze = mix(
        RingWorldAtmosphere.x,
        RingWorldAtmosphere.y,
        pow(farFraction, RingWorldAtmosphere.z)
    );
    return reveal * (1.0 - haze)
        * clamp(RingWorldLegacyProxyRevealScale, 0.0, 1.0);
}

vec3 ringProxyTone(vec3 liveTerrain, float intrinsicDistance,
                   float circumference, vec3 fogColor) {
    return mix(fogColor, liveTerrain,
        ringTerrainReveal(intrinsicDistance, circumference));
}
