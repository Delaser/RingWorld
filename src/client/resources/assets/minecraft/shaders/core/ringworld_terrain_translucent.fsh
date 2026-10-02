#version 150

#moj_import <fog.glsl>
#moj_import <ringworld_handoff.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform ivec4 RingWorldLayout;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in float ringIntrinsicDistance;
out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    float coverageFade = 0.0;
    if (RingWorldLayout.x != 0 && ringIntrinsicDistance >= 0.0) {
        coverageFade = ringLiveCoverageFade(ringIntrinsicDistance);
        if (ringWaterSprite(texCoord0, RingWorldWaterStill)
                || ringWaterSprite(texCoord0, RingWorldWaterFlow)) {
            float matchWater = ringSmootherstep(RingWorldHandoff.z, RingWorldHandoff.x, ringIntrinsicDistance);
            color.a = mix(color.a, 1.0, matchWater);
            vec3 atlasWater = vertexColor.rgb * 0.58;
            float peak = max(atlasWater.r, max(atlasWater.g, atlasWater.b));
            atlasWater = mix(atlasWater, vec3(peak), 0.12) * 1.15;
            color.rgb = mix(color.rgb, atlasWater, matchWater);
        }
    }
    vec4 fogged = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
    if (coverageFade > 0.0) {
        vec3 proxyTone = ringProxyTone(color.rgb, ringIntrinsicDistance,
            float(RingWorldLayout.y), FogColor.rgb);
        fogged.rgb = mix(fogged.rgb, proxyTone,
            ringToneConvergence(coverageFade));
        fogged.a *= 1.0 - coverageFade;
        if (fogged.a <= 0.001) discard;
    }
    fragColor = fogged;
}
