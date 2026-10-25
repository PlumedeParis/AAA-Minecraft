#version 330

// Scene pass: underwater refraction and caustics, screen-space ambient occlusion, height / volumetric-style fog
// with sun-phase scattering, sky scattering and sunset glow, wet-weather look and cave tone.

#moj_import <aether_realism:params.glsl>

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

in vec2 texCoord;

out vec4 fragColor;

const float TAU = 6.28318530718;

float ssao(mat4 invVP, vec2 uv, vec3 pos, float strength, float quality) {
    vec2 texel = 1.0 / vec2(textureSize(DepthSampler, 0));
    float dist = length(pos);
    float radius = clamp(18.0 / max(dist, 1.0), 2.0, 24.0);
    int samples = int(mix(6.0, 16.0, quality));
    float occlusion = 0.0;
    float angleOffset = hash13(vec3(gl_FragCoord.xy, 1.0)) * TAU;

    for (int i = 0; i < 16; i++) {
        if (i >= samples) {
            break;
        }

        float a = angleOffset + float(i) * 2.39996; // golden angle spiral
        float r = radius * sqrt((float(i) + 0.5) / float(samples));
        vec2 suv = uv + vec2(cos(a), sin(a)) * r * texel;
        float sd = texture(DepthSampler, suv).r;

        if (sd >= 0.99999) {
            continue;
        }

        vec3 sp = reconstructPosition(invVP, suv, sd);
        float sdist = length(sp);
        float diff = dist - sdist; // positive: neighbour is in front of us
        occlusion += smoothstep(0.05, 0.4, diff) * (1.0 - smoothstep(1.0, 3.0, diff));
    }

    return clamp(1.0 - occlusion / float(samples) * strength, 0.0, 1.0);
}

// Optical depth of exponential height fog along the ray camera -> pos (closed form).
float heightFogOpticalDepth(vec3 rayDir, float rayLength, float camY, float base, float density, float falloff) {
    float h0 = camY - base;
    float dy = rayDir.y * rayLength;
    float start = density * exp(-falloff * h0);

    if (abs(dy) < 0.01) {
        return start * rayLength;
    }

    return start * rayLength * (1.0 - exp(-falloff * dy)) / (falloff * dy);
}

float phaseHG(float cosTheta, float g) {
    float g2 = g * g;
    return (1.0 - g2) / (4.0 * 3.14159 * pow(max(1.0 + g2 - 2.0 * g * cosTheta, 1e-4), 1.5));
}

void main() {
    if (!paramsValid()) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    vec2 uv = texCoord;
    float time = P(P_TIME);
    float underwater = P(P_UNDERWATER);

    if (underwater > 0.5 && P(P_DISTORTION) > 0.0) {
        float d = P(P_DISTORTION) * 0.0035;
        uv += vec2(sin(uv.y * 24.0 + time * 1.7), cos(uv.x * 19.0 + time * 1.3)) * d;
        uv = clamp(uv, vec2(0.001), vec2(0.999));
    }

    vec3 color = texture(InSampler, uv).rgb;
    float depth = texture(DepthSampler, uv).r;
    bool sky = depth >= 0.99999;
    mat4 invVP = inverseViewProjection();
    vec3 pos = reconstructPosition(invVP, uv, sky ? 0.9999 : depth);
    vec3 dir = normalize(pos);
    float dist = sky ? 512.0 : length(pos);
    vec3 cameraPos = vec3(P(P_CAMERA_X), P(P_CAMERA_Y), P(P_CAMERA_Z));
    vec3 worldPos = cameraPos + dir * dist;
    vec3 sunDir = sunDirection();
    float daylight = P(P_DAYLIGHT);
    vec3 fogColor = vec3(P(P_FOG_R), P(P_FOG_G), P(P_FOG_B));
    float sunCos = dot(dir, sunDir);
    // Warm sunlight near the horizon, white at noon, cool moonlight at night.
    vec3 sunColor = daylight > 0.0 ? mix(vec3(1.0, 0.62, 0.35), vec3(1.0, 0.97, 0.9), clamp(daylight * 2.5, 0.0, 1.0))
                                   : vec3(0.55, 0.65, 0.9) * 0.6;

    // Ambient occlusion
    if (!sky && P(P_SSAO) > 0.0 && dist < 96.0) {
        color *= ssao(invVP, uv, pos, P(P_SSAO), P(P_SSAO_QUALITY));
    }

    // Caustics: dancing light on surfaces seen underwater.
    if (!sky && underwater > 0.5 && P(P_CAUSTICS) > 0.0) {
        vec2 p = worldPos.xz * 0.9 + worldPos.y * 0.3;
        float c1 = sin(p.x * 2.1 + time * 1.4) + sin(p.y * 1.7 - time * 1.1) + sin((p.x + p.y) * 1.3 + time * 0.9);
        float c2 = sin(p.x * 3.3 - time * 1.8) * sin(p.y * 2.9 + time * 1.6);
        float caustic = pow(clamp((c1 / 3.0) * 0.5 + 0.5 + c2 * 0.25, 0.0, 1.0), 4.0);
        float fade = exp(-dist * 0.06);
        color += color * caustic * P(P_CAUSTICS) * 1.4 * fade;
    }

    // Height fog / volumetric-style fog
    float heightDensity = P(P_HEIGHT_FOG);
    float volumetric = P(P_VOLUMETRIC);

    if ((heightDensity > 0.0 || volumetric > 0.0) && underwater < 0.5) {
        float base = P(P_HEIGHT_FOG_BASE);
        float falloff = max(P(P_HEIGHT_FOG_FALLOFF), 0.005);
        float camY = P(P_CAMERA_Y);
        float rayLen = min(dist, 384.0);
        float optical = heightFogOpticalDepth(dir, rayLen, camY, base, max(heightDensity, 0.002), falloff);
        float inscatter = 0.0;

        if (volumetric > 0.0) {
            // Ray-march drifting 3D noise so the fog rolls and thins instead of being a uniform layer.
            float quality = P(P_VOLUMETRIC_QUALITY);
            int steps = int(mix(8.0, 32.0, quality));
            float stepLen = min(rayLen, 160.0) / float(steps);
            float jitter = hash13(vec3(gl_FragCoord.xy, time));
            float noiseOptical = 0.0;
            vec3 drift = vec3(time * 0.6, 0.0, time * 0.35);

            for (int i = 0; i < 32; i++) {
                if (i >= steps) {
                    break;
                }

                float t = (float(i) + jitter) * stepLen;
                vec3 wp = cameraPos + dir * t;
                float h = wp.y - base;
                float n = valueNoise(wp * 0.045 + drift * 0.05) * 0.65 + valueNoise(wp * 0.11 - drift * 0.08) * 0.35;
                float d = exp(-falloff * max(h, -20.0)) * (0.4 + 1.2 * n) * 0.005;
                noiseOptical += d * stepLen;
            }

            optical += noiseOptical * volumetric;
            inscatter = phaseHG(sunCos, 0.6) * 4.0 * volumetric;
        }

        float fogAmount = 1.0 - exp(-optical);

        if (sky) {
            fogAmount *= 0.6; // keep the sky readable
        }

        vec3 litFog = fogColor * (1.0 + inscatter * 0.5) + sunColor * inscatter * 0.35 * clamp(abs(daylight) * 3.0, 0.0, 1.0);
        color = mix(color, litFog, clamp(fogAmount, 0.0, 0.92));
    }

    // Sky scattering: halo around the sun and a bright horizon haze; warm glow at sunrise / sunset.
    if (sky && P(P_SKY_SCATTER) > 0.0 && P(P_DIMENSION) < 0.5) {
        float halo = pow(max(sunCos, 0.0), 48.0) * 0.35 + pow(max(sunCos, 0.0), 6.0) * 0.12;
        float horizon = pow(1.0 - abs(dir.y), 6.0) * 0.08;
        color += (sunColor * halo + fogColor * horizon) * P(P_SKY_SCATTER) * clamp(abs(daylight) * 4.0, 0.0, 1.0);
    }

    float sunset = P(P_SUNSET);

    if (sunset > 0.0) {
        float towardSun = pow(max(dot(normalize(dir.xz + 1e-4), normalize(sunDir.xz + 1e-4)), 0.0), 3.0);
        float band = exp(-abs(dir.y - 0.02) * 9.0);
        vec3 glow = vec3(1.0, 0.45, 0.18) * band * (0.25 + 0.75 * towardSun);
        color += glow * sunset * (sky ? 0.35 : 0.08);
    }

    // Wet look in rain: darker, slightly more contrasty surfaces with faint sky reflection on upward faces.
    float wet = P(P_WETNESS);

    if (!sky && wet > 0.0) {
        float lum = luminance(color);
        color = mix(color, color * color * 1.6, wet * 0.25);
        color *= 1.0 - wet * 0.12;
        color += fogColor * wet * 0.04 * smoothstep(0.2, 0.6, lum);
    }

    // Caves: deeper shadows and a cold tone.
    float cave = P(P_CAVE);

    if (cave > 0.0) {
        float lum = luminance(color);
        float shadow = 1.0 - smoothstep(0.0, 0.35, lum);
        color *= 1.0 - cave * 0.5 * shadow;
        color = mix(color, color * vec3(0.9, 0.95, 1.08), cave * 0.5);
    }

    fragColor = vec4(color, 1.0);
}
