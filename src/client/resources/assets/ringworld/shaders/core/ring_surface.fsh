#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec4 ColorModulator;
uniform vec4 FogColor;
uniform ivec4 RingWorldLayout;
uniform vec4 RingWorldVertical;
uniform vec4 RingWorldHandoff;
uniform vec4 RingWorldDetail;
uniform vec4 RingWorldAtmosphere;
uniform vec2 RingWorldLegacyStreaming;
uniform vec4 RingWorldAtlasLight;
uniform sampler2D Sampler3;

in vec2 texCoord0;
in vec4 vertexColor;
in float intrinsicDistance;
in float intrinsicHeight;
in float intrinsicWidth;
flat in vec3 depthMapping;

out vec4 fragColor;

float smootherstep(float edge0, float edge1, float value) {
    float t = clamp((value - edge0) / max(0.0001, edge1 - edge0), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

// Clamp each mip to its own wall strip, including the one-row final mip.
// Repeat U in the sampler: fract(U) before derivatives creates a seam spike.
vec4 wallMip(vec2 uv, float strip, float level) {
    float halfTexel = 0.5 / float(textureSize(Sampler3, int(level)).y);
    uv.y = clamp(uv.y, strip * 0.25 + halfTexel, (strip + 1.0) * 0.25 - halfTexel);
    return textureLod(Sampler3, uv, level);
}

vec4 filteredWall(vec2 uv, float strip) {
    vec2 size = vec2(textureSize(Sampler3, 0));
    vec2 dx = dFdx(uv) * size;
    vec2 dy = dFdy(uv) * size;
    float level = clamp(0.5 * log2(max(1.0, max(dot(dx, dx), dot(dy, dy)))),
                        0.0, log2(size.y * 0.25));
    float low = floor(level);
    float high = ceil(level);
    return mix(wallMip(uv, strip, low), wallMip(uv, strip, high), fract(level));
}

void main() {
    vec2 surfaceUv = texCoord0;
    if (surfaceUv.x >= 2.0) surfaceUv.x -= 2.0;
    vec4 previous = texture(Sampler1, surfaceUv);
    vec4 current = texture(Sampler0, surfaceUv);
    vec4 sampled = mix(previous, current, clamp(ColorModulator.z, 0.0, 1.0));
    if (texCoord0.x >= 2.0) sampled.rgb = vertexColor.rgb * 0.85;
    bool rimBridge = texCoord0.y < 0.0 || texCoord0.y > 1.0;
    if (rimBridge) {
        float worldY = RingWorldVertical.w - intrinsicHeight;
        float bottomY = RingWorldVertical.y - float(RingWorldLayout.w);
        float vertical = clamp((worldY - bottomY) / max(1.0, float(RingWorldLayout.w)), 0.0, 0.999999);
        // Inner V markers are -1 / 2; outer/top marker is shared.
        float strip = intrinsicWidth < 0.0 ? 0.0 : 1.0;
        if (texCoord0.y < -1.5 || texCoord0.y > 2.5) strip += 2.0;
        vec4 wall = filteredWall(vec2(texCoord0.x, (strip + vertical) / 4.0), strip);
        if (wall.a < 0.5) discard;
        sampled = vec4(wall.rgb, 0.0);
    }

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

    float nativeDepth = depthMapping.x + depthMapping.y * gl_FragCoord.w;
    float surfaceDepth = min(nativeDepth, 1.0 + depthMapping.z * gl_FragCoord.w);
    gl_FragDepth = mix(1.0, surfaceDepth, proxyAlpha);
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
    reveal *= 1.0 - distanceHaze * terrainDetail;
    reveal *= 1.0 - clamp(ColorModulator.w, 0.0, 1.0);

    // Match 1.21.1 light.glsl and the lightmap's linear sampler exactly.
    // UV2=(0,240) is clamped after division by 256, leaving Y=15/16 halfway
    // between lightmap rows 14 and 15 rather than sampling row 15's centre.
    const vec2 fullSkyNoBlockLight = vec2(0.5 / 16.0, 15.0 / 16.0);
    vec3 surfaceLight = texture(Sampler2, fullSkyNoBlockLight).rgb;
    vec3 litTerrain = sampled.rgb * surfaceLight;
    // Texture alpha is independent server-authored exposed block light, not
    // terrain opacity. Reveal its warm contribution only as daylight falls.
    float skyBrightness = max(surfaceLight.r, max(surfaceLight.g, surfaceLight.b));
    float nightVisibility = 1.0 - smootherstep(0.38, 0.78, skyBrightness);
    float authoredLight = clamp(sampled.a, 0.0, 1.0);
    bool gammaLightProfile = RingWorldAtlasLight.x > 0.5;
    float lightCore = gammaLightProfile
        ? authoredLight
        : smootherstep(0.24, 0.84, authoredLight);
    float lightFalloff = gammaLightProfile ? RingWorldAtlasLight.y : 1.35;
    float artificialLight = pow(lightCore, lightFalloff) * nightVisibility;
    vec3 lampColor = vec3(1.00, 0.63, 0.28);
    float lightPeak = gammaLightProfile
        ? RingWorldAtlasLight.z
        : (0.42 + 0.24 * nightVisibility);
    litTerrain += lampColor * artificialLight * lightPeak;
    // Dark profiles use exact backdrop RGB at the proxy boundary instead of
    // the atmosphere-coloured vanilla fog that would leave a pale outline.
    // Atmosphere retains the live FogColor supplied every frame.
    vec3 edgeColor = FogColor.rgb;
    if (ColorModulator.x > 1.5) {
        edgeColor = vec3(1.0 / 255.0, 1.0 / 255.0, 3.0 / 255.0);
    } else if (ColorModulator.x > 0.5) {
        edgeColor = vec3(5.0 / 255.0, 8.0 / 255.0, 16.0 / 255.0);
    }
    fragColor = vec4(mix(edgeColor, litTerrain, reveal), proxyAlpha);
}
