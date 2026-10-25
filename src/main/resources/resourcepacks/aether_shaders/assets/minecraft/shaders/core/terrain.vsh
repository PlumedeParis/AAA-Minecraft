#version 330

// Aether Realism terrain vertex shader.
// Identical to vanilla 1.21.11 core/terrain.vsh, plus wind and wave displacement for vertices that the mod
// marked through their color alpha: alpha = 255 - k, where
//   k  1..63 : foliage, category = k / 16 (0 grass, 1 leaves, 2 flowers/crops, 3 underwater), weight = k % 16
//   k 64..79 : water with ripple shimmer, weight = k - 64 (0 = anchored vertex)
//   k 80..95 : water without shimmer,     weight = k - 80
// Unmarked geometry (alpha 255) is processed exactly like vanilla.

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
out float aetherKind;
out vec3 aetherWorldPos;

const float TAU = 6.28318530718;

vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}

// GameTime is the world time modulo one day, mapped to [0, 1). Using integer cycle counts per day keeps every
// animation continuous when it wraps.
float cycle(float cyclesPerDay, float phase) {
    return sin(TAU * cyclesPerDay * GameTime + phase);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 windOffset(int category, float weight, vec3 world) {
    // Slowly wandering wind direction (a few full turns per day) and travelling gusts.
    float angle = 1.1 * cycle(3.0, 0.0) + 0.6 * cycle(11.0, 1.7);
    vec2 dir = vec2(cos(angle), sin(angle));
    float along = dot(world.xz, dir);
    float gust = 0.55 + 0.45 * cycle(240.0, -along * 0.045) * cycle(97.0, along * 0.02 + 1.3);
    float phase = along * 0.35 + world.y * 0.15 + hash12(floor(world.xz)) * 1.5;

    if (category == 0) {
        // Grass & ferns: bend along the wind, small cross-wind flutter.
        float bend = cycle(900.0, phase) * 0.6 + cycle(1700.0, phase * 1.7) * 0.25 + 0.35;
        float flutter = cycle(2600.0, phase * 2.3) * 0.25;
        vec2 xz = dir * bend * gust + vec2(-dir.y, dir.x) * flutter;
        return vec3(xz.x, -0.15 * abs(bend) * gust, xz.y) * 0.16 * weight;
    }

    if (category == 1) {
        // Leaves: per-vertex 3D flutter so canopies shimmer instead of moving as one block.
        float h = hash12(world.xz * 3.17 + world.y);
        vec3 f = vec3(cycle(2100.0, phase + h * TAU), cycle(1500.0, phase * 1.3 + h * 4.0), cycle(2400.0, phase * 0.7 + h * 2.0));
        return (f * vec3(1.0, 0.45, 1.0) + vec3(dir.x, 0.0, dir.y) * 0.35) * 0.055 * weight * gust;
    }

    if (category == 2) {
        // Flowers & crops: stiffer stems, slightly faster nodding.
        float bend = cycle(1100.0, phase) * 0.7 + cycle(2300.0, phase * 1.9) * 0.2 + 0.2;
        vec2 xz = dir * bend * gust;
        return vec3(xz.x, 0.0, xz.y) * 0.09 * weight;
    }

    // Underwater plants: slow current independent of wind.
    float sway = cycle(420.0, world.x * 0.4 + world.y * 0.3) + 0.5 * cycle(700.0, world.z * 0.5);
    return vec3(sway, 0.0, cycle(360.0, world.z * 0.35 + world.y * 0.2)) * 0.12 * weight;
}

float waveHeight(vec3 world) {
    float w = cycle(1300.0, world.x * 0.55 + world.z * 0.25)
            + 0.6 * cycle(1900.0, -world.z * 0.7 + world.x * 0.15)
            + 0.3 * cycle(3100.0, (world.x + world.z) * 1.3);
    return w * 0.035;
}

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    vec3 world = Position + vec3(ChunkPosition);
    vec4 color = Color;
    aetherKind = 0.0;

    int k = 255 - int(Color.a * 255.0 + 0.5);

    if (k > 0 && k < 96) {
        color.a = 1.0;
        // Fade animation out with distance so far-away terrain stays stable and cheap to look at.
        float dist = length(pos);
        float fade = 1.0 - smoothstep(FogRenderDistanceEnd * 0.45, FogRenderDistanceEnd * 0.7, dist);

        if (k >= 64) {
            int base = k >= 80 ? 80 : 64;
            aetherKind = base == 64 ? 1.0 : 0.0;
            float weight = float(k - base) / 15.0;
            pos.y += waveHeight(world) * weight * fade * 2.0;
        } else {
            int category = k / 16;
            float weight = float(k - category * 16) / 15.0;
            pos += windOffset(category, weight * 2.0, world) * fade;
        }
    }

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    aetherWorldPos = world;
}
