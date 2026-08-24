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
    if (color.a < 0.5) discard;
    float coverageFade = 0.0;
    bool ringHandoffActive =
        RingWorldLayout.x != 0 && ringIntrinsicDistance >= 0.0;
    if (ringHandoffActive) {
        coverageFade = ringLiveCoverageFade(ringIntrinsicDistance);
    }
    vec4 fogged = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
    if (ringHandoffActive) {
        if (coverageFade > 0.0) {
            vec3 proxyTone = ringProxyTone(color.rgb, ringIntrinsicDistance,
                float(RingWorldLayout.y), FogColor.rgb);
            fogged.rgb = mix(fogged.rgb, proxyTone,
                ringToneConvergence(coverageFade));
        }
        // The original alpha cutoff above defines visible texels. This render
        // type is otherwise opaque, so do not turn mipped sprite-edge alpha
        // translucent while RingWorld's blend adapter is active.
        fogged.a = 1.0 - coverageFade;
        if (fogged.a <= 0.001) discard;
    }
    fragColor = fogged;
}
