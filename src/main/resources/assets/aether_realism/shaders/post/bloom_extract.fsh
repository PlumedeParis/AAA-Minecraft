#version 330

// Bloom bright pass with a soft knee. Only genuinely bright pixels (light sources, lava, the sun) contribute,
// so the rest of the image stays sharp.

#moj_import <aether_realism:params.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    if (!paramsValid() || P(P_BLOOM) <= 0.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    // 4-tap box prefilter to reduce sparkle from single bright pixels.
    vec3 c = texture(InSampler, texCoord + texel * vec2(-0.5, -0.5)).rgb
           + texture(InSampler, texCoord + texel * vec2(0.5, -0.5)).rgb
           + texture(InSampler, texCoord + texel * vec2(-0.5, 0.5)).rgb
           + texture(InSampler, texCoord + texel * vec2(0.5, 0.5)).rgb;
    c *= 0.25;

    float threshold = P(P_BLOOM_THRESHOLD);
    float knee = 0.15;
    float brightness = max(c.r, max(c.g, c.b));
    float soft = clamp(brightness - threshold + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee + 1e-4);
    float contribution = max(soft, brightness - threshold) / max(brightness, 1e-4);

    fragColor = vec4(c * contribution, 1.0);
}
