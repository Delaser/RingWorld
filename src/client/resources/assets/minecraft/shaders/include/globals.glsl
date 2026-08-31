#version 330

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
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
};
