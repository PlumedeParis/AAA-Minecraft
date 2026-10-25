#version 330

// Separable Gaussian blur for bloom. The direction comes from the pass's AetherBlur uniform block.

#moj_import <aether_realism:params.glsl>

uniform sampler2D InSampler;

layout(std140) uniform AetherBlur {
    vec2 BlurDirection;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    if (!paramsValid() || P(P_BLOOM) <= 0.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    float quality = P(P_BLOOM_QUALITY);
    int radius = int(mix(4.0, 12.0, quality));
    float spread = mix(3.0, 2.0, quality); // fewer taps cover the same width with bigger steps
    float sigma = float(radius) * 0.5;
    vec3 sum = texture(InSampler, texCoord).rgb;
    float weightSum = 1.0;

    for (int i = 1; i <= 12; i++) {
        if (i > radius) {
            break;
        }

        float w = exp(-float(i * i) / (2.0 * sigma * sigma));
        vec2 offset = BlurDirection * texel * float(i) * spread;
        sum += (texture(InSampler, texCoord + offset).rgb + texture(InSampler, texCoord - offset).rgb) * w;
        weightSum += 2.0 * w;
    }

    fragColor = vec4(sum / weightSum, 1.0);
}
