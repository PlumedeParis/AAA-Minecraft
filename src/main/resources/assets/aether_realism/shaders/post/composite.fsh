#version 330

// Final pass: adds bloom and god rays, then exposure, lightning flash, color grading, night tone and vignette.

#moj_import <aether_realism:params.glsl>

uniform sampler2D InSampler;
uniform sampler2D BloomSampler;
uniform sampler2D RaysSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;

    if (!paramsValid()) {
        fragColor = vec4(color, 1.0);
        return;
    }

    float bloom = P(P_BLOOM);

    if (bloom > 0.0) {
        color += texture(BloomSampler, texCoord).rgb * bloom;
    }

    if (P(P_GODRAYS) > 0.0) {
        color += texture(RaysSampler, texCoord).rgb;
    }

    // Exposure and lightning flash.
    color *= exp2(P(P_EXPOSURE));
    float flash = P(P_FLASH);

    if (flash > 0.0) {
        color = color * (1.0 + flash * 1.4) + vec3(0.55, 0.6, 0.75) * flash * 0.25;
    }

    // Night: gentle blue tone and shadow lift so nights feel moonlit rather than flat.
    float night = P(P_NIGHT_TINT);

    if (night > 0.0) {
        float lum = luminance(color);
        vec3 moonlit = mix(vec3(lum), color, 0.6) * vec3(0.82, 0.92, 1.18);
        color = mix(color, moonlit, night * 0.6);
        color += vec3(0.004, 0.006, 0.012) * night;
    }

    // Underwater: absorb red first.
    if (P(P_UNDERWATER) > 0.5) {
        color *= vec3(0.86, 0.97, 1.02);
    }

    // Soft shoulder so bloom and flashes roll off instead of clipping harshly.
    color = color / (1.0 + max(color - 1.0, 0.0) * 0.6);

    // Grading
    color += P(P_BRIGHTNESS);
    color = (color - 0.5) * P(P_CONTRAST) + 0.5;
    float lum = luminance(color);
    color = mix(vec3(lum), color, P(P_SATURATION));

    if (P(P_GRADING) > 0.5) {
        color *= vec3(P(P_TINT_R), P(P_TINT_G), P(P_TINT_B));
        float split = P(P_SPLIT_TONING);

        if (split > 0.0) {
            float l = clamp(luminance(color), 0.0, 1.0);
            vec3 shadows = vec3(0.92, 1.0, 1.08);   // teal shadows
            vec3 highlights = vec3(1.06, 1.0, 0.92); // warm highlights
            color *= mix(vec3(1.0), mix(shadows, highlights, smoothstep(0.2, 0.8, l)), split);
        }

        float vibrance = P(P_VIBRANCE);

        if (vibrance > 0.0) {
            float maxC = max(color.r, max(color.g, color.b));
            float minC = min(color.r, min(color.g, color.b));
            float sat = maxC - minC;
            color = mix(vec3(luminance(color)), color, 1.0 + vibrance * (1.0 - sat));
        }
    }

    float vignette = P(P_VIGNETTE);

    if (vignette > 0.0) {
        vec2 d = texCoord - 0.5;
        float v = smoothstep(0.85, 0.25, length(d * vec2(1.1, 1.0)));
        color *= mix(1.0, v, vignette * 0.75);
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
