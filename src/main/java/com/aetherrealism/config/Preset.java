package com.aetherrealism.config;

/**
 * One-click graphics configurations. {@link #CUSTOM} is never applied; it is the label shown once the user
 * edits any individual setting after picking a preset.
 */
public enum Preset {
	POTATO("Potato", "Minimal extra effects for weak hardware."),
	PERFORMANCE("Performance", "Essential improvements, lightweight fog and lighting."),
	BALANCED("Balanced", "A good mix of quality and performance."),
	HIGH("High", "High-quality environment, enhanced lighting and water."),
	ULTRA("Ultra", "Maximum quality, long effect distances, dense particles."),
	CINEMATIC("Cinematic", "Everything on, including the most expensive effects. Powerful GPUs only."),
	CUSTOM("Custom", "Individually configured settings.");

	public final String displayName;
	public final String description;

	Preset(String displayName, String description) {
		this.displayName = displayName;
		this.description = description;
	}

	/**
	 * Writes every preset-controlled value into {@code c}. Settings that are personal preferences rather than
	 * quality knobs (master switch, performance monitor, color style) are left untouched.
	 */
	public void applyTo(AetherConfig c) {
		if (this == CUSTOM) {
			return;
		}

		int tier = ordinal(); // 0 = potato ... 5 = cinematic

		// Lighting
		c.dynamicLights = tier >= 1;
		c.dynamicLightsHeldItems = tier >= 1;
		c.dynamicLightsEntities = tier >= 2;
		c.dynamicLightMaxSources = pick(tier, 4, 8, 16, 32, 64, 96);
		c.dynamicLightUpdateInterval = pick(tier, 6, 4, 3, 2, 1, 1);
		c.dynamicLightRadius = pick(tier, 0.75f, 0.85f, 1.0f, 1.0f, 1.15f, 1.25f);
		c.dynamicLightSmoothing = tier >= 2;

		c.postProcessing = tier >= 1;
		c.colorGrading = tier >= 1;
		c.bloom = tier >= 2;
		c.bloomIntensity = pick(tier, 0.0f, 0.0f, 0.25f, 0.35f, 0.45f, 0.55f);
		c.bloomQuality = pickEnum(tier, Quality.LOW, Quality.LOW, Quality.MEDIUM, Quality.HIGH, Quality.HIGH, Quality.ULTRA);
		c.ssao = tier >= 4;
		c.ssaoQuality = pickEnum(tier, Quality.LOW, Quality.LOW, Quality.LOW, Quality.MEDIUM, Quality.HIGH, Quality.ULTRA);
		c.caveAtmosphere = tier >= 1;
		c.nightAtmosphere = tier >= 1;
		c.vignette = pick(tier, 0.0f, 0.1f, 0.2f, 0.25f, 0.3f, 0.35f);

		// Nature
		c.shaderLayer = tier >= 1;
		c.vegetationAnimation = tier >= 1;
		c.swayGrass = tier >= 1;
		c.swayLeaves = tier >= 2;
		c.swayFlowers = tier >= 2;
		c.swayCrops = tier >= 2;
		c.swayUnderwater = tier >= 3;
		c.stormWindResponse = tier >= 3;
		c.windGusts = tier >= 2;

		// Weather
		c.enhancedRain = tier >= 1;
		c.rainSplashes = tier >= 1;
		c.rainSplashDensity = pick(tier, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f, 1.2f);
		c.rainEffectDistance = pick(tier, 8, 10, 14, 18, 24, 28);
		c.waterRipples = tier >= 2;
		c.wetSurfaces = tier >= 2;
		c.enhancedSnow = tier >= 1;
		c.snowDensity = pick(tier, 0.3f, 0.5f, 0.7f, 0.9f, 1.1f, 1.3f);
		c.lightningFlashes = tier >= 1;
		c.stormAtmosphere = tier >= 2;
		c.cinematicStorms = tier >= 5;

		// Atmosphere
		c.atmosphericFog = tier >= 1;
		c.biomeFog = tier >= 2;
		c.heightFog = tier >= 2;
		c.volumetricFog = tier >= 4;
		c.volumetricQuality = pickEnum(tier, Quality.LOW, Quality.LOW, Quality.LOW, Quality.MEDIUM, Quality.HIGH, Quality.ULTRA);
		c.godRays = tier >= 3;
		c.godRayQuality = pickEnum(tier, Quality.LOW, Quality.LOW, Quality.MEDIUM, Quality.MEDIUM, Quality.HIGH, Quality.ULTRA);
		c.caveFog = tier >= 2;
		c.underwaterEffects = tier >= 1;

		// Water
		c.waterWaves = tier >= 1;
		c.waterWaveHeight = pick(tier, 0.3f, 0.5f, 0.8f, 1.0f, 1.0f, 1.2f);
		c.waterShimmer = tier >= 2;
		c.waterCaustics = tier >= 3;
		c.underwaterDistortion = tier >= 2;
		c.waterDroplets = tier >= 3;

		// Particles
		c.fallingLeaves = tier >= 1;
		c.fireflies = tier >= 2;
		c.dust = tier >= 3;
		c.pollen = tier >= 3;
		c.caveDust = tier >= 2;
		c.windDebris = tier >= 4;
		c.enhancedFire = tier >= 2;
		c.glowParticles = tier >= 3;
		c.particleDensity = pick(tier, 0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f);
		c.particleDistance = pick(tier, 12, 16, 20, 28, 36, 40);
		c.maxAmbientParticles = pick(tier, 60, 120, 250, 400, 700, 1000);

		// Sky
		c.sunsetGlow = tier >= 1;
		c.skyScattering = tier >= 3;
		c.moonlight = tier >= 2;
		c.netherAtmosphere = tier >= 2;
		c.endAtmosphere = tier >= 2;

		// Performance
		c.effectDistance = pick(tier, 24, 32, 48, 64, 96, 128);
		c.lowPerformanceMode = tier == 0;
	}

	private static int pick(int tier, int... values) {
		return values[Math.min(tier, values.length - 1)];
	}

	private static float pick(int tier, float... values) {
		return values[Math.min(tier, values.length - 1)];
	}

	@SafeVarargs
	private static <T> T pickEnum(int tier, T... values) {
		return values[Math.min(tier, values.length - 1)];
	}
}
