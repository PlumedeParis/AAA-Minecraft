package com.aetherrealism.render;

/**
 * Indices into the post-processing parameter texture. Must stay in sync with
 * {@code assets/aether_realism/shaders/include/params.glsl}.
 */
public final class PostParams {
	public static final int COUNT = 64;

	public static final int INV_VP = 0;            // 16 floats, column-major
	public static final int CAMERA_X = 16;
	public static final int CAMERA_Y = 17;
	public static final int CAMERA_Z = 18;
	public static final int TIME = 19;
	public static final int SUN_DIR_X = 20;
	public static final int SUN_DIR_Y = 21;
	public static final int SUN_DIR_Z = 22;
	public static final int DAYLIGHT = 23;         // -1 midnight .. 1 noon
	public static final int FOG_R = 24;
	public static final int FOG_G = 25;
	public static final int FOG_B = 26;
	public static final int HEIGHT_FOG = 27;       // density, 0 = off
	public static final int HEIGHT_FOG_BASE = 28;  // world Y
	public static final int HEIGHT_FOG_FALLOFF = 29;
	public static final int VOLUMETRIC = 30;       // in-scattering strength
	public static final int GODRAYS = 31;
	public static final int GODRAY_QUALITY = 32;
	public static final int SUN_SCREEN_X = 33;
	public static final int SUN_SCREEN_Y = 34;
	public static final int SUN_ON_SCREEN = 35;
	public static final int BLOOM = 36;
	public static final int BLOOM_THRESHOLD = 37;
	public static final int BLOOM_QUALITY = 38;
	public static final int EXPOSURE = 39;
	public static final int CONTRAST = 40;
	public static final int SATURATION = 41;
	public static final int BRIGHTNESS = 42;
	public static final int GRADING = 43;
	public static final int TINT_R = 44;
	public static final int TINT_G = 45;
	public static final int TINT_B = 46;
	public static final int SPLIT_TONING = 47;
	public static final int VIGNETTE = 48;
	public static final int FLASH = 49;
	public static final int UNDERWATER = 50;
	public static final int CAUSTICS = 51;
	public static final int DISTORTION = 52;
	public static final int SSAO = 53;
	public static final int SSAO_QUALITY = 54;
	public static final int WETNESS = 55;
	public static final int SUNSET = 56;
	public static final int NIGHT_TINT = 57;
	public static final int CAVE = 58;
	public static final int DIMENSION = 59;        // 0 overworld, 1 nether, 2 end, 3 other
	public static final int SKY_SCATTER = 60;
	public static final int VOLUMETRIC_QUALITY = 61;
	public static final int VIBRANCE = 62;
	public static final int LOW_PERF = 63;

	private PostParams() {
	}
}
