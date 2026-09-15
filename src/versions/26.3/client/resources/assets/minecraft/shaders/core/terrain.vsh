#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
#include <minecraft:chunksection.glsl>
#else
layout(location = 4) in ivec3 ChunkPosition;
layout(location = 5) in float ChunkVisibility;
#endif
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;


#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler2;
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) out float chunkVisibility;
layout(location = 5) out float ringIntrinsicDistance;

float ring_circumference() {
    return float(RingWorldLayout.y);
}

bool ring_active() {
    return RingWorldLayout.x != 0;
}

// Keep the last live chunks gently atmospheric without raising an obvious
// fog-colour wall at their outer edge. The distant ring now supplies the
// remainder of the handoff through a broad alpha cross-fade.
const float RING_FOG_DISTANCE_SCALE = 1.02;

vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}

// Converts a physical ring-space delta back into the camera's local Minecraft
// axes: circumference (X), local up (Y), and band width (Z).
vec3 camera_local_ring_position(vec3 vertexCanonical, vec3 cameraCanonical) {
    float circumference = ring_circumference();
    float baseRadius = circumference / (2.0 * 3.14159265358979323846);
    float deltaAngle = 6.28318530717958647692
        * (vertexCanonical.x - cameraCanonical.x) / circumference;
    float vertexRadius = baseRadius + RingWorldVertical.x - vertexCanonical.y;
    float cameraRadius = baseRadius + RingWorldVertical.x - cameraCanonical.y;
    return vec3(
        vertexRadius * sin(deltaAngle),
        cameraRadius - vertexRadius * cos(deltaAngle),
        vertexCanonical.z - cameraCanonical.z
    );
}

void main() {
    vec3 vanillaPos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    chunkVisibility = mix(1.0, ChunkVisibility, clamp((length(vanillaPos) - 16.0) / 16.0, 0.0, 1.0));
    if (!ring_active()) {
        gl_Position = ProjMat * ModelViewMat * vec4(vanillaPos, 1.0);
        sphericalVertexDistance = fog_spherical_distance(vanillaPos);
        cylindricalVertexDistance = fog_cylindrical_distance(vanillaPos);
        #ifdef OIT_ALPHA_ONLY
    vertexColor = Color;
#else
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
#endif
        texCoord0 = UV0;
        ringIntrinsicDistance = -1.0;
        return;
    }

    vec3 vertexCanonical = Position + vec3(ChunkPosition);
    // Vanilla's CameraOffset is the translation from the integer camera
    // origin to the real camera (normally the negated fractional position).
    // Therefore camera world-space is block origin minus this offset. Using
    // plus here doubles sub-block movement and snaps every block boundary.
    vec3 cameraCanonical = vec3(CameraBlockPos) - CameraOffset;
    vec3 pos = camera_local_ring_position(vertexCanonical, cameraCanonical);
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    // Chunk loading is measured along the intrinsic surface coordinates, not
    // by the shorter chord through the rendered cylinder. Preserve the curved
    // position for geometry while using the greater of both distances for
    // fog, so the final loaded chunks fully dissolve before their hard edge.
    sphericalVertexDistance = RING_FOG_DISTANCE_SCALE * max(
        fog_spherical_distance(pos),
        fog_spherical_distance(vanillaPos)
    );
    cylindricalVertexDistance = RING_FOG_DISTANCE_SCALE * max(
        fog_cylindrical_distance(pos),
        fog_cylindrical_distance(vanillaPos)
    );
    #ifdef OIT_ALPHA_ONLY
    vertexColor = Color;
#else
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
#endif
    texCoord0 = UV0;
    // Horizontal intrinsic distance matches chunk loading and the textured
    // surface shader. The fragment shader uses it to reveal the already-drawn
    // distant ring beneath only the final live terrain band.
    ringIntrinsicDistance = length(vanillaPos.xz);
}
