// Requires render_uniforms.glsl and handoff.glsl. Shared by material adapters.
float ring_material_smootherstep(float edge0, float edge1, float value) {
    float t = clamp((value - edge0) / max(0.0001, edge1 - edge0), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

// Ordered coverage gives every 8x8 pixel area an even fade, without the
// irregular speckle of a screen-space hash. Keep the pattern fixed in screen
// space so motion does not introduce a new random threshold each frame.
float ring_material_dither_threshold(vec2 pixel) {
    ivec2 cell = ivec2(pixel) & ivec2(7);
    int rank = 0;
    for (int bit = 0; bit < 3; ++bit) {
        int x = (cell.x >> bit) & 1;
        int y = (cell.y >> bit) & 1;
        int pair = ((x ^ y) << 1) | y;
        rank |= pair << (4 - 2 * bit);
    }
    return (float(rank) + 0.5) / 64.0;
}

bool ring_material_water_sprite(vec2 uv, vec4 bounds) {
    return bounds.x < bounds.z && bounds.y < bounds.w
        && all(greaterThanEqual(uv, bounds.xy)) && all(lessThanEqual(uv, bounds.zw));
}


#ifndef OIT_ALPHA_ONLY
uniform sampler2D RingWorldSurface;
#endif
vec4 ring_material_coverage(vec4 color, vec4 vertexColor, vec2 texCoord0,
        float ringIntrinsicDistance, float ringHandoffDistance) {
    if ((RingWorldLayout.x != 0) && ringIntrinsicDistance >= 0.0) {
        float proxyReveal = ring_material_smootherstep(
            RingWorldHandoff.x,
            RingWorldHandoff.y,
            ringHandoffDistance
        );
        if (ring_material_dither_threshold(gl_FragCoord.xy) < proxyReveal) {
            discard;
        }
        if (ring_material_water_sprite(texCoord0, RingWorldWaterStill)
                || ring_material_water_sprite(texCoord0, RingWorldWaterFlow)) {
            // Seal translucent water over the same 3D band in which the Atlas appears.
            float waterMatch = ring_material_smootherstep(RingWorldHandoff.z, RingWorldHandoff.x,
                                            ringHandoffDistance);
            // The Atlas captures the bed from above. At grazing angles its ledges
            // appear on the water plane, so retain the live bed's parallax longer.
            // Fully seal at the original distance, before live coverage drops.
            if (RingWorldWaterChart.z > 0.5) {
                float horizontalFraction = ringIntrinsicDistance / max(ringHandoffDistance, 0.001);
                float grazing = smoothstep(0.70, 0.97, horizontalFraction);
                waterMatch = mix(waterMatch, waterMatch * waterMatch * waterMatch, grazing);
            }
            float matchedAlpha = mix(color.a, 1.0, waterMatch);
            vec3 atlasWater = vertexColor.rgb * 0.58;
            float waterPeak = max(atlasWater.r, max(atlasWater.g, atlasWater.b));
            atlasWater = mix(atlasWater, vec3(waterPeak), 0.06) * 0.89;
#ifndef OIT_ALPHA_ONLY
            if (waterMatch > 0.0 && RingWorldWaterChart.z > 0.5) {
                vec3 skyLight = texelFetch(RING_WATER_LIGHTMAP, ivec2(0, 15), 0).rgb;
                // Water and land can share a fragment quad. An implicit LOD inside
                // this material branch produced striped water on the OpenGL backend.
                vec3 captured = textureLod(RingWorldSurface, ringWaterUv, 0.0).rgb * skyLight;
                // Keep the matched biome tint for deep water. Larger differences,
                // such as bed reflection, follow the captured surface colour.
                vec3 delta = abs(captured - atlasWater) / max(skyLight, vec3(0.05));
                float bedMatch = smoothstep(0.0, 0.065, max(delta.r, max(delta.g, delta.b)));
                atlasWater = mix(atlasWater, captured, bedMatch);
            }
#endif
            // Interpolate the contribution to the final pixel as opacity rises.
            color.rgb = mix(color.rgb, atlasWater, waterMatch / max(matchedAlpha, 0.00001));
            color.a = matchedAlpha;
        }
    }
    return color;
}

vec4 ring_material_atmosphere(vec4 color, float distance, vec3 fogColor) {
    if (RingWorldLayout.x != 0 && distance >= 0.0) {
        color.rgb = mix(ring_handoff_edge_color(fogColor), color.rgb, ring_handoff_reveal(distance, RingWorldAtmosphere2.y));
    }
    return color;
}
