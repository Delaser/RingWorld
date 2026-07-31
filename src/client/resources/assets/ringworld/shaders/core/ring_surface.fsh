#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

in vec2 texCoord0;
in vec4 vertexColor;
in float intrinsicDistance;

out vec4 fragColor;

float smootherstep(float edge0, float edge1, float value) {
    float t = clamp((value - edge0) / max(0.0001, edge1 - edge0), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

void main() {
    vec4 sampled = texture(Sampler0, texCoord0) * vertexColor;
    if (sampled.a == 0.0) {
        discard;
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

    // Atlas samples are exposed top surfaces, so use the same lightmap texel
    // as a real face with maximum sky light and no block light. This carries
    // Minecraft's current RGB sky tint and all client lightmap effects into
    // the visual LOD without pretending that the static atlas contains lamps.
    const vec2 fullSkyNoBlockLight = vec2(0.5 / 16.0, 15.5 / 16.0);
    vec3 surfaceLight = texture(Sampler2, fullSkyNoBlockLight).rgb;
    vec3 litTerrain = sampled.rgb * surfaceLight;
    fragColor = vec4(mix(FogColor.rgb, litTerrain, reveal), proxyAlpha);
}
