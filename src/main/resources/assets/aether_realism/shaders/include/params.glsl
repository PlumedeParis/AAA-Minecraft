// Aether Realism post-processing parameters.
// The mod writes the raw IEEE-754 bits of each float into one RGBA8 texel of a 64x1 texture; this decodes them
// exactly. Indices must match com.aetherrealism.render.PostParams.

uniform sampler2D ParamsSampler;

float P(int index) {
    vec4 t = texelFetch(ParamsSampler, ivec2(index, 0), 0);
    uvec4 b = uvec4(t * 255.0 + 0.5);
    uint bits = (b.r << 24u) | (b.g << 16u) | (b.b << 8u) | b.a;
    return uintBitsToFloat(bits);
}

#define P_INV_VP 0
#define P_CAMERA_X 16
#define P_CAMERA_Y 17
#define P_CAMERA_Z 18
#define P_TIME 19
#define P_SUN_DIR_X 20
#define P_SUN_DIR_Y 21
#define P_SUN_DIR_Z 22
#define P_DAYLIGHT 23
#define P_FOG_R 24
#define P_FOG_G 25
#define P_FOG_B 26
#define P_HEIGHT_FOG 27
#define P_HEIGHT_FOG_BASE 28
#define P_HEIGHT_FOG_FALLOFF 29
#define P_VOLUMETRIC 30
#define P_GODRAYS 31
#define P_GODRAY_QUALITY 32
#define P_SUN_SCREEN_X 33
#define P_SUN_SCREEN_Y 34
#define P_SUN_ON_SCREEN 35
#define P_BLOOM 36
#define P_BLOOM_THRESHOLD 37
#define P_BLOOM_QUALITY 38
#define P_EXPOSURE 39
#define P_CONTRAST 40
#define P_SATURATION 41
#define P_BRIGHTNESS 42
#define P_GRADING 43
#define P_TINT_R 44
#define P_TINT_G 45
#define P_TINT_B 46
#define P_SPLIT_TONING 47
#define P_VIGNETTE 48
#define P_FLASH 49
#define P_UNDERWATER 50
#define P_CAUSTICS 51
#define P_DISTORTION 52
#define P_SSAO 53
#define P_SSAO_QUALITY 54
#define P_WETNESS 55
#define P_SUNSET 56
#define P_NIGHT_TINT 57
#define P_CAVE 58
#define P_DIMENSION 59
#define P_SKY_SCATTER 60
#define P_VOLUMETRIC_QUALITY 61
#define P_VIBRANCE 62
#define P_LOW_PERF 63

// Contrast is always >= 0.5 when the mod has written parameters; anything else means the texture is not ours.
bool paramsValid() {
    float c = P(P_CONTRAST);
    return c >= 0.25 && c <= 4.0;
}

mat4 inverseViewProjection() {
    return mat4(
        P(0), P(1), P(2), P(3),
        P(4), P(5), P(6), P(7),
        P(8), P(9), P(10), P(11),
        P(12), P(13), P(14), P(15));
}

// Camera-relative world position of a screen pixel.
vec3 reconstructPosition(mat4 invVP, vec2 uv, float depth) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 p = invVP * ndc;
    return p.xyz / p.w;
}

vec3 sunDirection() {
    return normalize(vec3(P(P_SUN_DIR_X), P(P_SUN_DIR_Y), P(P_SUN_DIR_Z)) + vec3(0.0, 1e-4, 0.0));
}

float luminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float valueNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i);
    float n100 = hash13(i + vec3(1, 0, 0));
    float n010 = hash13(i + vec3(0, 1, 0));
    float n110 = hash13(i + vec3(1, 1, 0));
    float n001 = hash13(i + vec3(0, 0, 1));
    float n101 = hash13(i + vec3(1, 0, 1));
    float n011 = hash13(i + vec3(0, 1, 1));
    float n111 = hash13(i + vec3(1, 1, 1));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}
