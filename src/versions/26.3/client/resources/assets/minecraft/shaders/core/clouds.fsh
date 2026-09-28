#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:oit.glsl>
#include <minecraft:globals.glsl>

layout(location = 0) in float vertexDistance;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in float ringIntrinsicWorldZ;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

void main() {
    vec4 color = vertexColor;
    if (RingWorldLayout.x != 0
            && (ringIntrinsicWorldZ < RingWorldAtmosphere2.z
                || ringIntrinsicWorldZ > RingWorldAtmosphere2.w)) {
        discard;
    }
#ifndef OIT_DEPTH_BOUNDS
    color.a *= 1.0f - linear_fog_value(vertexDistance, 0, FogCloudsEnd);
#endif
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
#ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
#endif
    fragColor = color;
#endif
}
