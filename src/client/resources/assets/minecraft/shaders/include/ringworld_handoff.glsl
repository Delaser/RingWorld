// Minecraft 1.21.1 splits chunk terrain across several core programs. Keep
// the visual handoff policy in one adapter include so every layer converges
// on the same tone before its live coverage disappears into the Atlas.
uniform vec4 RingWorldHandoff;
uniform vec4 RingWorldDetail;
uniform vec4 RingWorldAtmosphere;

float ringSmootherstep(float edge0, float edge1, float value) {
    float t = clamp((value - edge0) / max(0.0001, edge1 - edge0), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

float ringDitherThreshold(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

float ringLiveCoverageFade(float intrinsicDistance) {
    return ringSmootherstep(
        RingWorldHandoff.x, RingWorldHandoff.y, intrinsicDistance);
}

float ringTerrainReveal(float intrinsicDistance, float circumference) {
    float detail = ringSmootherstep(
        RingWorldDetail.x, RingWorldDetail.y, intrinsicDistance);
    float reveal = mix(RingWorldDetail.z, RingWorldDetail.w, detail);
    float farFraction = clamp(
        intrinsicDistance / (circumference * 0.5), 0.0, 1.0);
    float haze = mix(
        RingWorldAtmosphere.x,
        RingWorldAtmosphere.y,
        pow(farFraction, RingWorldAtmosphere.z)
    );
    return reveal * (1.0 - haze);
}

vec3 ringProxyTone(vec3 liveTerrain, float intrinsicDistance,
                   float circumference, vec3 fogColor) {
    return mix(fogColor, liveTerrain,
        ringTerrainReveal(intrinsicDistance, circumference));
}
