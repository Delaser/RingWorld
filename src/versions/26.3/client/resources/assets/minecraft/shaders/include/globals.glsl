#ifndef MINECRAFT_GLOBALS_GLSL
#define MINECRAFT_GLOBALS_GLSL

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    float GlintAlpha;
    vec3 CameraOffset;
    float GameTime;
    vec2 ScreenSize;
    int MenuBlurRadius;
    int UseRgss;

    // RingWorld immutable layout and per-frame presentation profile. The
    // activation flag is zero outside the negotiated Overworld.
    ivec4 RingWorldLayout;
    vec4 RingWorldVertical;
    vec4 RingWorldRender;
    vec4 RingWorldHandoff;
    vec4 RingWorldDetail;
    vec4 RingWorldAtmosphere;
    vec4 RingWorldAtmosphere2;
    // mode (0 midpoint, 1 gamma), falloff exponent, peak strength, reserved
    vec4 RingWorldAtlasLight;
    // Current block-atlas bounds for water only; no colour-based material guesses.
    vec4 RingWorldWaterStill;
    vec4 RingWorldWaterFlow;
};

#endif
