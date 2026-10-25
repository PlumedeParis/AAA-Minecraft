#version 330

// Screen-space god rays: radial blur toward the sun (or moon) of the visible bright sky. Terrain, trees and
// fog occlude the light because only sky pixels (depth = far plane) contribute.

#moj_import <aether_realism:params.glsl>

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

in vec2 texCoord;

out vec4 fragColor;

float skyLight(vec2 uv) {
    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) {
        return 0.0;
    }

    float depth = texture(DepthSampler, uv).r;

    if (depth < 0.99999) {
        return 0.0;
    }

    vec3 c = texture(InSampler, uv).rgb;
    return smoothstep(0.35, 1.0, luminance(c));
}

void main() {
    float strength = paramsValid() ? P(P_GODRAYS) : 0.0;

    if (strength <= 0.0 || P(P_SUN_ON_SCREEN) < 0.5) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec2 sunUV = vec2(P(P_SUN_SCREEN_X), P(P_SUN_SCREEN_Y));
    // Fade as the sun leaves the screen so rays never pop.
    vec2 overshoot = max(abs(sunUV - 0.5) - 0.5, 0.0);
    float edgeFade = 1.0 - smoothstep(0.0, 0.6, length(overshoot));

    if (edgeFade <= 0.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    float quality = P(P_GODRAY_QUALITY);
    int samples = int(mix(16.0, 64.0, quality));
    vec2 delta = (texCoord - sunUV) / float(samples) * 0.85;
    vec2 uv = texCoord;
    float jitter = hash13(vec3(gl_FragCoord.xy, P(P_TIME)));
    uv -= delta * jitter;
    float decay = 1.0;
    float accum = 0.0;

    for (int i = 0; i < 64; i++) {
        if (i >= samples) {
            break;
        }

        uv -= delta;
        accum += skyLight(uv) * decay;
        decay *= 0.965;
    }

    accum /= float(samples);
    float rays = accum * strength * edgeFade * 2.2;
    float daylight = P(P_DAYLIGHT);
    vec3 tint = daylight > 0.0 ? mix(vec3(1.0, 0.7, 0.45), vec3(1.0, 0.96, 0.88), clamp(daylight * 2.5, 0.0, 1.0))
                               : vec3(0.6, 0.7, 1.0);
    fragColor = vec4(tint * rays, 1.0);
}
