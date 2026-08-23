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
    float proxyAlpha = smootherstep(
        RingWorldHandoff.z, RingWorldHandoff.w, intrinsicDistance
    );
    if (proxyAlpha <= 0.001) discard;

    float terrainDetail = smootherstep(
        RingWorldDetail.x, RingWorldDetail.y, intrinsicDistance
    );
    float reveal = mix(RingWorldDetail.z, RingWorldDetail.w, terrainDetail)
                   * clamp(ColorModulator.y, 0.0, 1.0);
    float farFraction = clamp(intrinsicDistance / (circumference * 0.5), 0.0, 1.0);
    float distanceHaze = mix(
        RingWorldAtmosphere.x,
        RingWorldAtmosphere.y,
        pow(farFraction, RingWorldAtmosphere.z)
    );
    reveal *= 1.0 - distanceHaze;
    reveal *= 1.0 - clamp(ColorModulator.w, 0.0, 1.0);

    const vec2 fullSkyNoBlockLight = vec2(0.5 / 16.0, 15.5 / 16.0);
    vec3 surfaceLight = texture(Sampler2, fullSkyNoBlockLight).rgb;
    vec3 litTerrain = sampled.rgb * surfaceLight;
    fragColor = vec4(mix(FogColor.rgb, litTerrain, reveal), proxyAlpha * sampled.a);
}
