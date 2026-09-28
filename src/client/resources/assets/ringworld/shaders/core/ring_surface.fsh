#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:ringworld_handoff.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;

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
    float metadata = floor(vertexColor.a * 255.0 + 0.5);
    float pattern = floor(metadata / 32.0);
    float fine = wallHash(vec3(blockX, blockY, depth), 0.0);
    float coarse = wallHash(vec3(floor(blockX / 7.0), floor(blockY / 5.0),
                                     floor(depth / 2.0)), 19.0);
    // The revised Industrial option keeps the established panel layout.
    if (pattern > 5.5) pattern = 3.0;
    if (pattern < 0.5) {
        return mix(fine, coarse, 0.72);
    }
    if (pattern < 1.5) {
        float course = floor(blockY / 2.0);
        float width = 3.0 + floor(wallHash(vec3(course, depth, 0.0), 31.0) * 4.0);
        float offset = floor(wallHash(vec3(course, depth, 1.0), 47.0) * width);
        float brick = floor((blockX + offset) / width);
        return mix(fine, wallHash(vec3(brick, course, depth), 53.0), 0.76);
    }
    if (pattern < 2.5) {
        float section = floor(blockX / 19.0);
        float wave = floor(wallHash(vec3(section, depth, 2.0), 61.0) * 9.0) - 4.0;
        float height = 3.0 + floor(wallHash(vec3(section, floor(blockY / 13.0),
                                                   depth), 67.0) * 7.0);
        float band = floor((blockY + wave) / height);
        return mix(fine, wallHash(vec3(floor(blockX / 11.0), band, depth), 71.0), 0.68);
    }
    if (pattern < 3.5) {
        float panelWidth = 11.0 + floor(wallHash(
                vec3(floor(blockX / 67.0), depth, 3.0), 79.0) * 13.0);
        float panelX = mod(blockX, panelWidth);
        float course = 8.0 + floor(wallHash(vec3(floor(blockX / panelWidth), depth, 5.0),
                                                83.0) * 11.0);
        bool rib = panelX < 1.0 || panelX >= panelWidth - 1.0
                   || mod(blockY, course) < 1.0;
        return rib ? 0.92 : mix(fine, coarse, 0.70);
    }
    if (pattern < 4.5) {
        float vertical = clamp((blockY + 64.0) / 224.0, 0.0, 1.0);
        return vertical * 0.28 + mix(fine, coarse, 0.46) * 0.72;
    }
    // Hybrid: broad weathered clusters broken by occasional structural ribs.
    float selector = wallHash(vec3(floor(blockX / 23.0), floor(blockY / 17.0), depth),
                              97.0);
    float clustered = mix(fine, coarse, 0.66);
    float rib = mod(blockX, 17.0) < 1.0 || mod(blockY, 13.0) < 1.0 ? 0.90 : clustered;
    return selector < 0.28 ? rib : mix(rib, clustered, 0.82);
}

vec3 wallPalette(float roll) {
    if (roll < TextureMat[0].w) return TextureMat[0].rgb;
    if (roll < TextureMat[1].w) return TextureMat[1].rgb;
    if (roll < TextureMat[2].w) return TextureMat[2].rgb;
    if (roll < TextureMat[3].w) return TextureMat[3].rgb;
    return vertexColor.rgb;
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
        vec4 wall = texture(Sampler3, vec2(fract(texCoord0.x), (strip + vertical) / 4.0));
        if (wall.a < 0.5) discard;
        sampled = vec4(wall.rgb, 0.0);
    }

    float circumference = float(RingWorldLayout.y);

    // Real chunks are the only local surface. Cross-fade the visual proxy
    // beneath the outer live-chunk band, but do not make it fully opaque until
    // beyond the nominal loaded range. This turns a missing/streaming edge
    // into a soft continuation and keeps nearby rim walls free of proxy
    // geometry.
    float proxyAlpha = smootherstep(
        RingWorldHandoff.z,
        RingWorldHandoff.w,
        intrinsicDistance
    );
    if (proxyAlpha <= 0.001) {
        discard;
    }

    // A nearly transparent proxy must not occlude fully visible live terrain.
    // Fade its window-space depth from the far plane to the actual surface.
    // Both backend NDC ranges map to [0,1] here; reversed depth has far=0.
#ifdef RINGWORLD_REVERSED_DEPTH
    gl_FragDepth = mix(0.0, gl_FragCoord.z, proxyAlpha);
#else
    gl_FragDepth = mix(1.0, gl_FragCoord.z, proxyAlpha);
#endif

    // Match the final live chunks' atmosphere before their geometry fades.
    // ColorModulator.y carries the celestial rain fade (1 - rain).
    // Add bounded rain haze beyond the live-terrain transition. Full rain
    // retains 65% of the normal reveal instead of erasing the terrain.
    float rain = 1.0 - clamp(ColorModulator.y, 0.0, 1.0);
    float rainDistance = smootherstep(RingWorldDetail.x, RingWorldDetail.y, intrinsicDistance);
    float reveal = ring_handoff_reveal(intrinsicDistance)
                   * (1.0 - 0.35 * rain * rainDistance);

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
    fragColor = vec4(mix(ring_handoff_edge_color(), litTerrain, reveal), proxyAlpha);
}
