#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform ivec4 RingWorldLayout;
uniform vec4 RingWorldHandoff;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in float ringIntrinsicDistance;
out vec4 fragColor;

float smootherstep(float edge0, float edge1, float value) {
    float t = clamp((value - edge0) / max(0.0001, edge1 - edge0), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

float ringDitherThreshold(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (RingWorldLayout.x != 0 && ringIntrinsicDistance >= 0.0) {
        float proxyReveal = smootherstep(
            RingWorldHandoff.x, RingWorldHandoff.y, ringIntrinsicDistance);
        if (ringDitherThreshold(gl_FragCoord.xy) < proxyReveal) discard;
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
