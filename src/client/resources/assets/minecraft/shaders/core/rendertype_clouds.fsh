#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform ivec4 RingWorldLayout;
uniform vec4 RingWorldAtmosphere2;

in vec2 texCoord0;
in float vertexDistance;
in vec4 vertexColor;
in float ringIntrinsicWorldZ;
out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) discard;
    if (RingWorldLayout.x != 0
            && (ringIntrinsicWorldZ < RingWorldAtmosphere2.z
                || ringIntrinsicWorldZ > RingWorldAtmosphere2.w)) {
        discard;
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
