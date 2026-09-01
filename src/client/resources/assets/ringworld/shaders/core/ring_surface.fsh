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
uniform mat4 RingWorldWallPalette;
uniform vec4 RingWorldWallStyle;
uniform vec4 RingWorldAtlasLight;

in vec2 texCoord0;
in vec4 vertexColor;
in float intrinsicDistance;
in float intrinsicHeight;
in float intrinsicWidth;

out vec4 fragColor;

float smootherstep(float edge0, float edge1, float value) {
    float t = clamp((value - edge0) / max(0.0001, edge1 - edge0), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

float wallHash(vec3 block, float salt) {
    float metadata = floor(vertexColor.a * 255.0 + 0.5);
    float seed = mod(metadata, 32.0);
    return fract(sin(dot(block, vec3(12.9898, 78.233, 37.719))
                     + salt + seed * 11.173) * 43758.5453);
}

float wallRoll(float blockX, float blockY, float depth) {
    float pattern = floor(vertexColor.a * 255.0 / 32.0 + 0.001);
    float fine = wallHash(vec3(blockX, blockY, depth), 0.0);
    float coarse = wallHash(vec3(floor(blockX / 7.0), floor(blockY / 5.0),
                                 floor(depth / 2.0)), 19.0);
    if (pattern < 0.5) return mix(fine, coarse, 0.72);
    if (pattern < 1.5) return mix(fine, wallHash(vec3(floor(blockX / 5.0),
                                                     floor(blockY / 2.0), depth), 53.0), 0.76);
    if (pattern < 2.5) return mix(fine, wallHash(vec3(floor(blockX / 11.0),
                                                     floor(blockY / 6.0), depth), 71.0), 0.68);
    if (pattern < 3.5) {
        bool rib = mod(blockX, 17.0) < 1.0 || mod(blockY, 13.0) < 1.0;
        return rib ? 0.92 : mix(fine, coarse, 0.70);
    }
    if (pattern < 4.5) return clamp((blockY + 64.0) / 224.0, 0.0, 1.0) * 0.28
            + mix(fine, coarse, 0.46) * 0.72;
    return mix(fine, coarse, 0.66);
}

vec3 wallPalette(float roll) {
    if (roll < RingWorldWallPalette[0].w) return RingWorldWallPalette[0].rgb;
    if (roll < RingWorldWallPalette[1].w) return RingWorldWallPalette[1].rgb;
    if (roll < RingWorldWallPalette[2].w) return RingWorldWallPalette[2].rgb;
    if (roll < RingWorldWallPalette[3].w) return RingWorldWallPalette[3].rgb;
    return vertexColor.rgb;
}

void main() {
    vec4 previous = texture(Sampler1, texCoord0);
    vec4 current = texture(Sampler0, texCoord0);
    vec4 sampled = mix(previous, current, clamp(ColorModulator.z, 0.0, 1.0));
    bool rimBridge = texCoord0.y < 0.0 || texCoord0.y > 1.0;
    if (rimBridge) {
        float blockX = floor(mod(texCoord0.x * float(RingWorldLayout.y),
                                 float(RingWorldLayout.y)));
        float blockY = floor(intrinsicHeight);
        float halfWidth = float(RingWorldLayout.z) * 0.5;
        float depth = max(0.0, halfWidth - abs(intrinsicWidth));
        float roll = wallRoll(blockX, blockY, floor(depth));
        float weather = step(1.0 - RingWorldWallStyle.x,
                wallHash(vec3(blockX, blockY, depth), 101.0));
        float textureNoise = 0.88 + 0.12 * wallHash(
                vec3(blockX + 31.0, blockY - 17.0, depth), 109.0);
        vec3 styled = wallPalette(roll);
        styled = mix(styled, styled * vec3(0.72, 0.86, 0.72), weather * 0.35);
        sampled = vec4(styled * textureNoise, 0.0);
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
