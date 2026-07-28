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
out float ringIntrinsicDistance;

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
    if (!ring_active()) {
        gl_Position = ProjMat * ModelViewMat * vec4(vanillaPos, 1.0);
        sphericalVertexDistance = fog_spherical_distance(vanillaPos);
        cylindricalVertexDistance = fog_cylindrical_distance(vanillaPos);
        vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
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
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    // Horizontal intrinsic distance matches chunk loading and the textured
    // surface shader. The fragment shader uses it to reveal the already-drawn
    // distant ring beneath only the final live terrain band.
    ringIntrinsicDistance = length(vanillaPos.xz);
}
