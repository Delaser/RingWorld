#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

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
    vec4 previous = texture(Sampler1, texCoord0);
    vec4 current = texture(Sampler0, texCoord0);
    vec4 sampled = mix(previous, current, clamp(ColorModulator.z, 0.0, 1.0));
    bool rimBridge = texCoord0.y < 0.0 || texCoord0.y > 1.0;
    if (rimBridge) {
        float blockX = floor(mod(texCoord0.x * float(RingWorldLayout.y),
                                 float(RingWorldLayout.y)));
        float blockY = floor(intrinsicHeight);
        float halfWidth = float(RingWorldLayout.z) * 0.5;
        float wallDepth = max(0.0, halfWidth - abs(intrinsicWidth));
        float roll = wallRoll(blockX, blockY, floor(wallDepth));
        float textureNoise = 0.88 + 0.12 * wallHash(
                vec3(blockX + 31.0, blockY - 17.0, wallDepth), 109.0);
        sampled = vec4(wallPalette(roll) * textureNoise, 0.0);
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

    // Begin revealing the atlas underneath the final live chunks. Those chunks
    // normally overwrite it, while a streaming gap exposes a fogged but
    // recognizable continuation instead of a flat-colour belt. At the nominal
    // edge over half of the terrain signal remains visible. The fog-colour
    // component therefore sits low on the surface instead of forming a bright
    // band above the last chunks.
    float terrainDetail = smootherstep(
        RingWorldDetail.x,
        RingWorldDetail.y,
        intrinsicDistance
    );
    float reveal = mix(RingWorldDetail.z, RingWorldDetail.w, terrainDetail)
                   * clamp(ColorModulator.y, 0.0, 1.0);

    // A small amount of haze remains around the complete ring to suppress
    // mip/colour aliasing without turning the far side into a flat sky ribbon.
    float farFraction = clamp(intrinsicDistance / (circumference * 0.5), 0.0, 1.0);
    float distanceHaze = mix(
        RingWorldAtmosphere.x,
        RingWorldAtmosphere.y,
        pow(farFraction, RingWorldAtmosphere.z)
    );
    reveal *= 1.0 - distanceHaze;

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
    // The normal fog UBO remains atmosphere-coloured even when the selected
    // backdrop is Night or Void. Using it at the proxy boundary creates a
    // conspicuous pale outline around the ring. ColorModulator.x carries the
    // saved backdrop id, so dark modes blend to their actual sky colour.
    vec3 edgeColor = FogColor.rgb;
    if (ColorModulator.x > 1.5) {
        edgeColor = vec3(1.0 / 255.0, 1.0 / 255.0, 3.0 / 255.0);
    } else if (ColorModulator.x > 0.5) {
        edgeColor = vec3(5.0 / 255.0, 8.0 / 255.0, 16.0 / 255.0);
    }
    fragColor = vec4(mix(edgeColor, litTerrain, reveal), proxyAlpha);
}
