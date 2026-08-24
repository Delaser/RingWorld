#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec4 ColorModulator;
uniform vec4 FogColor;
uniform ivec4 RingWorldLayout;
uniform vec4 RingWorldHandoff;
uniform vec4 RingWorldDetail;
uniform vec4 RingWorldAtmosphere;
uniform vec2 RingWorldLegacyStreaming;

in vec2 texCoord0;
in vec4 vertexColor;
in float intrinsicDistance;
in float intrinsicHeight;

out vec4 fragColor;

float smootherstep(float edge0, float edge1, float value) {
    float t = clamp((value - edge0) / max(0.0001, edge1 - edge0), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

float wallHash(vec2 block) {
    return fract(sin(dot(block, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec4 previous = texture(Sampler1, texCoord0);
    vec4 current = texture(Sampler0, texCoord0);
    vec4 sampled = mix(previous, current, clamp(ColorModulator.z, 0.0, 1.0))
                   * vertexColor;
    bool rimBridge = texCoord0.y < 0.0 || texCoord0.y > 1.0;
    if (rimBridge) {
        float blockX = floor(mod(texCoord0.x * float(RingWorldLayout.y),
                                 float(RingWorldLayout.y)));
        float blockY = floor(intrinsicHeight);
        float moss = step(0.70, wallHash(vec2(blockX, blockY)));
        float textureNoise = 0.82 + 0.18 * wallHash(vec2(blockX + 31.0, blockY - 17.0));
        vec3 cobble = vec3(0.40, 0.42, 0.40);
        vec3 mossy = vec3(0.30, 0.40, 0.30);
        sampled = vec4(mix(cobble, mossy, moss) * textureNoise, 1.0);
    }
    if (sampled.a == 0.0) discard;

    float circumference = float(RingWorldLayout.y);
    // The legacy pre-terrain compositor must supply an opaque two-layer
    // underlay before live terrain starts fading. Keep Handoff.w's shared
    // proxy-end meaning intact and localize this 1.21.1 adapter here.
    float sharedLiveFadeStart = RingWorldHandoff.x;
    float legacyProxyFadeStart = max(
        0.0,
        RingWorldHandoff.z - (sharedLiveFadeStart - RingWorldHandoff.z)
    );
    float proxyAlpha = smootherstep(
        legacyProxyFadeStart, RingWorldHandoff.z, intrinsicDistance
    );
    // During initial chunk/section streaming, keep an opaque Atlas underlay
    // beneath every positive distance. A proven complete finite-band window
    // publishes a step at V, where Experiment 19 is already opaque, so this
    // max becomes an exact visual no-op.
    float streamingProxyAlpha = smootherstep(
        RingWorldLegacyStreaming.x,
        RingWorldLegacyStreaming.y,
        intrinsicDistance
    );
    proxyAlpha = max(proxyAlpha, streamingProxyAlpha);
    if (proxyAlpha <= 0.001) discard;

    float terrainDetail = smootherstep(
        RingWorldDetail.x, RingWorldDetail.y, intrinsicDistance);
    float reveal = mix(RingWorldDetail.z, RingWorldDetail.w, terrainDetail)
                   * clamp(ColorModulator.y, 0.0, 1.0);
    float handoffEnvelope = 1.0 - smootherstep(
        RingWorldHandoff.z, RingWorldDetail.y, intrinsicDistance
    );
    float handoffRevealFloor = mix(
        RingWorldDetail.w, 1.0, handoffEnvelope
    ) * clamp(ColorModulator.y, 0.0, 1.0);
    reveal = max(reveal, handoffRevealFloor);
    float farFraction = clamp(intrinsicDistance / (circumference * 0.5), 0.0, 1.0);
    float distanceHaze = mix(
        RingWorldAtmosphere.x,
        RingWorldAtmosphere.y,
        pow(farFraction, RingWorldAtmosphere.z)
    );
    reveal *= 1.0 - distanceHaze;
    reveal *= 1.0 - clamp(ColorModulator.w, 0.0, 1.0);

    // Match 1.21.1 light.glsl and the lightmap's linear sampler exactly.
    // UV2=(0,240) is clamped after division by 256, leaving Y=15/16 halfway
    // between lightmap rows 14 and 15 rather than sampling row 15's centre.
    const vec2 fullSkyNoBlockLight = vec2(0.5 / 16.0, 15.0 / 16.0);
    vec3 surfaceLight = texture(Sampler2, fullSkyNoBlockLight).rgb;
    vec3 litTerrain = sampled.rgb * surfaceLight;
    fragColor = vec4(
        mix(FogColor.rgb, litTerrain, reveal), proxyAlpha * sampled.a);
}
