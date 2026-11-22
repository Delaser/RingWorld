#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <ringworld:render_uniforms.glsl>
#include <ringworld:handoff.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in float intrinsicDistance;
layout(location = 6) in float handoffDistance;
layout(location = 3) in float intrinsicHeight;
layout(location = 4) in float intrinsicWidth;
layout(location = 5) flat in vec3 depthMapping;

layout(location = 0) out vec4 fragColor;

float smootherstep(float edge0, float edge1, float value) {
    float t = clamp((value - edge0) / max(0.0001, edge1 - edge0), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

// Clamp each mip to its own wall strip, including the one-row final mip.
// Repeat U in the sampler: fract(U) before derivatives creates a seam LOD spike.
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
    bool water = surfaceUv.x < 0.0;
    if (water) surfaceUv.x = -1.0 - surfaceUv.x;
    bool replacement = surfaceUv.x >= 8.0;
    bool covered = surfaceUv.x >= 4.0 && !replacement;
    // Retain the distant single surface outside the transition neighbourhood.
    // Near coverage continues to use the regular native-to-Atlas fade below.
    if (covered || replacement) {
        // Scale the width from the near edge, leaving the native handoff fixed.
        float outer = mix(RingWorldHandoff.z, RingWorldHandoff.y * 1.4, ColorModulator.x);
        bool useLayers = handoffDistance >= RingWorldHandoff.z && handoffDistance < outer;
        if (replacement != useLayers) discard;
        surfaceUv.x -= replacement ? 8.0 : 4.0;
    }
    bool side = surfaceUv.x >= 2.0;
    if (side) surfaceUv.x -= 2.0;
    vec4 previous = texture(Sampler1, surfaceUv);
    vec4 current = texture(Sampler0, surfaceUv);
    vec4 sampled = mix(previous, current, clamp(ColorModulator.z, 0.0, 1.0));
    if (side) sampled.rgb = vertexColor.rgb * 0.85;
    if (replacement) sampled = vec4(vertexColor.rgb, 0.0);
    bool rimBridge = texCoord0.y < 0.0 || texCoord0.y > 1.0;
    if (rimBridge) {
        float worldY = RingWorldVertical.w - intrinsicHeight;
        float bottomY = RingWorldVertical.y - float(RingWorldLayout.w);
        float vertical = clamp((worldY - bottomY) / max(1.0, float(RingWorldLayout.w)), 0.0, 0.999999);
        // Inner V markers are -1 / 2; outer/top marker is shared.
        float strip = intrinsicWidth < 0.0 ? 0.0 : 1.0;
        if (texCoord0.y < -1.5 || texCoord0.y > 2.5) strip += 2.0;
        vec4 wall = filteredWall(vec2(texCoord0.x, (strip + vertical) / 4.0), strip);
        // Decay is a closed height field; never punch holes in wall faces.
        sampled = vec4(wall.rgb, 0.0);
    }

    float circumference = float(RingWorldLayout.y);

    // Real chunks are the local surface within horizontal AND vertical range.
    // Cross-fade the visual proxy
    // beneath the outer live-chunk band, but do not make it fully opaque until
    // beyond the nominal loaded range. This turns a missing/streaming edge
    // into a soft continuation and keeps nearby rim walls free of proxy
    // geometry.
    float proxyAlpha = smootherstep(
        RingWorldHandoff.z,
        RingWorldHandoff.w,
        handoffDistance
    );
    // Fill missing destination chunks behind native geometry during a teleport.
    // Only colour coverage changes. Near proxy fragments still write far depth.
    float coverageAlpha = mix(proxyAlpha, 1.0, RingWorldRender.z);
    // Opaque backing only where native geometry leaves the bed uncovered.
    // Its depth is behind the live range, so loaded coral and sand still win.
    float waterY = RingWorldVertical.w - intrinsicHeight;
    bool waterBacking = water && proxyAlpha < 1.0
            && TextureMat[3].y > waterY;
    if (waterBacking) coverageAlpha = 1.0;
    if (coverageAlpha <= 0.001) {
        discard;
    }

    // A nearly transparent proxy must not occlude fully visible live terrain.
    // Reconstruct depth before the vertex clamp, including triangles that
    // straddle its boundary. Join the native curve to an ordered infinite
    // tail exactly where clamping starts: no plateau and no distance jump.
    float nativeDepth = depthMapping.x + depthMapping.y * gl_FragCoord.w;
    float tailOffset = depthMapping.z * gl_FragCoord.w;
    // Both backend NDC ranges map to [0,1] here; reversed depth has far=0.
#ifdef RINGWORLD_REVERSED_DEPTH
    float surfaceDepth = max(nativeDepth, tailOffset);
    gl_FragDepth = mix(0.0, surfaceDepth, proxyAlpha);
#else
    float surfaceDepth = min(nativeDepth, 1.0 + tailOffset);
    gl_FragDepth = mix(1.0, surfaceDepth, proxyAlpha);
#endif

    if (waterBacking) {
        // Move along this view ray to just beyond the native fade, keeping the
        // physical water silhouette. Use the same ordered far tail on both APIs.
        float inverseW = gl_FragCoord.w * min(1.0, handoffDistance / (1.05 * RingWorldHandoff.y));
        float backNative = depthMapping.x + depthMapping.y * inverseW;
#ifdef RINGWORLD_REVERSED_DEPTH
        float backDepth = max(backNative, depthMapping.z * inverseW);
#else
        float backDepth = min(backNative, 1.0 + depthMapping.z * inverseW);
#endif
        gl_FragDepth = mix(backDepth, surfaceDepth, proxyAlpha);
    }

    // Match the final live chunks' atmosphere before their geometry fades.
    // ColorModulator.y carries the celestial rain fade (1 - rain).
    // Add bounded rain haze beyond the live-terrain transition. Full rain
    // retains 65% of the normal reveal instead of erasing the terrain.
    float rain = 1.0 - clamp(ColorModulator.y, 0.0, 1.0);
    float reveal = ring_handoff_reveal(intrinsicDistance, rain);

    // ColorModulator.w is the incomplete-Atlas generation haze. It begins
    // dense, clears with authoritative coverage, and reaches exactly zero for
    // a complete ring. The renderer interpolates it alongside texture morphs.
    reveal *= 1.0 - clamp(ColorModulator.w, 0.0, 1.0);

    // Atlas samples are exposed top surfaces, so use the same lightmap texel
    // as a real face with maximum sky light and no block light. This carries
    // Minecraft's current RGB sky tint and all client lightmap effects into
    // the visual LOD without pretending that the static atlas contains lamps.
    const vec2 fullSkyNoBlockLight = vec2(0.5 / 16.0, 15.5 / 16.0);
    vec3 surfaceLight = texture(Sampler2, fullSkyNoBlockLight).rgb;
    vec3 litTerrain = sampled.rgb * surfaceLight;
    if (waterBacking) litTerrain *= ring_handoff_water_backing(handoffDistance);
    // Surface texture alpha is a separate server-authored exposed block-light
    // level. Reveal it only when the live lightmap says environmental daylight
    // is low; daytime terrain RGB and nearby real lighting remain untouched.
    float skyBrightness = max(surfaceLight.r, max(surfaceLight.g, surfaceLight.b));
    float nightVisibility = 1.0 - smootherstep(0.38, 0.78, skyBrightness);
    // Do not turn every interpolated neighbour into a broad glowing patch.
    // Keep only the bright core of the authored light sample, then compress
    // it again so distant torches read as restrained pinpricks.
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
    // The incomplete texture is deliberately opaque: real generated colours
    // flavour nearby unknown cells, then each published revision cross-fades
    // into the next instead of exposing a hard tile update.
    vec3 terrain = mix(ring_handoff_edge_color(FogColor.rgb), litTerrain, reveal);
    float fog = ring_handoff_environment(handoffDistance, FogEnvironmentalStart, FogEnvironmentalEnd);
    fragColor = vec4(mix(terrain, FogColor.rgb, fog * FogColor.a), coverageAlpha);
}
