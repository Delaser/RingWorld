#ifndef RINGWORLD_RENDER_UNIFORMS_GLSL
#define RINGWORLD_RENDER_UNIFORMS_GLSL
layout(std140) uniform RingWorld {
    ivec4 RingWorldLayout;
    vec4 RingWorldVertical;
    vec4 RingWorldRender;
    vec4 RingWorldHandoff;
    vec4 RingWorldDetail;
    vec4 RingWorldAtmosphere;
    vec4 RingWorldAtmosphere2;
    // mode (0 midpoint, 1 gamma), falloff exponent, peak strength, reserved
    vec4 RingWorldAtlasLight;
    vec4 RingWorldWaterStill;
    vec4 RingWorldWaterFlow;
    // enabled, correction distance, blend end, safe visual reference radius
    vec4 RingWorldDistortion;
};
#endif
