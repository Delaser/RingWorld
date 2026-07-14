#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

// RingWorld writes the negotiated circumference into the otherwise
// menu-only Globals field each in-world frame. Keeping this in the existing
// UBO avoids a second terrain pipeline while retaining per-world geometry.
float ring_circumference() {
    return float((MenuBlurRadius & 0x7ffffff0) >> 4);
}

bool ring_active() {
    return MenuBlurRadius < 0;
}

const float SURFACE_Y = 64.0;
const float RING_FOG_DISTANCE_SCALE = 1.08;

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
    float vertexRadius = baseRadius + SURFACE_Y - vertexCanonical.y;
    float cameraRadius = baseRadius + SURFACE_Y - cameraCanonical.y;
    return vec3(
        vertexRadius * sin(deltaAngle),
        cameraRadius - vertexRadius * cos(deltaAngle),
        vertexCanonical.z - cameraCanonical.z
    );
}

void main() {
    vec3 vanillaPos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    if (!ring_active()) {
        gl_Position = ProjMat * ModelViewMat * vec4(vanillaPos, 1.0);
        sphericalVertexDistance = fog_spherical_distance(vanillaPos);
        cylindricalVertexDistance = fog_cylindrical_distance(vanillaPos);
        vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
        texCoord0 = UV0;
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
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
