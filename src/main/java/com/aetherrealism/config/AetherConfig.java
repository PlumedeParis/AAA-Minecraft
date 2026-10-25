package com.aetherrealism.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every user-facing setting. Plain public fields so Gson can (de)serialize it directly; values are clamped by
 * {@link #validate()} after loading and after every GUI edit. Defaults correspond to the High preset.
 */
public final class AetherConfig {
	/** Bumped when a setting's meaning changes so old files can be migrated. */
	public int configVersion = 1;

	// --- Overview ---
	public boolean masterEnabled = true;
	public Preset preset = Preset.HIGH;
	public boolean showPerformanceMonitor = false;

	// --- Lighting ---
	public boolean dynamicLights = true;
	public boolean dynamicLightsHeldItems = true;
	public boolean dynamicLightsEntities = true;
	public float dynamicLightRadius = 1.0f;
	public float dynamicLightIntensity = 1.0f;
	public int dynamicLightMaxSources = 32;
	public int dynamicLightUpdateInterval = 2;
	public boolean dynamicLightSmoothing = true;
	/** Item id -> light level overrides, e.g. {"minecraft:blaze_rod": 10}. */
	public Map<String, Integer> customItemLights = defaultItemLights();

	public boolean postProcessing = true;
	public float exposure = 0.0f;
	public float contrast = 1.05f;
	public float saturation = 1.08f;
	public float brightness = 0.0f;
	public boolean colorGrading = true;
	public ColorStyle colorStyle = ColorStyle.CINEMATIC;
	public float vignette = 0.25f;
	public boolean bloom = true;
	public float bloomIntensity = 0.35f;
	public float bloomThreshold = 0.78f;
	public Quality bloomQuality = Quality.HIGH;
	public boolean ssao = false;
	public float ssaoStrength = 0.6f;
	public Quality ssaoQuality = Quality.MEDIUM;
	public boolean caveAtmosphere = true;
	public float caveDarkness = 0.25f;
	public boolean nightAtmosphere = true;
	public float nightTint = 0.4f;

	// --- Nature ---
	/** Enables the terrain vertex markers read by the bundled "Aether Realism Shaders" resource pack. */
	public boolean shaderLayer = true;
	public boolean vegetationAnimation = true;
	public boolean swayGrass = true;
	public boolean swayLeaves = true;
	public boolean swayFlowers = true;
	public boolean swayCrops = true;
	public boolean swayUnderwater = true;
	public float windStrength = 1.0f;
	public float grassStrength = 1.0f;
	public float leavesStrength = 1.0f;
	public float flowerStrength = 1.0f;
	public float cropStrength = 1.0f;
	public float underwaterStrength = 1.0f;
	public boolean stormWindResponse = true;
	public boolean windGusts = true;
	public float windSpeed = 1.0f;

	// --- Weather ---
	public boolean enhancedRain = true;
	public boolean rainSplashes = true;
	public float rainSplashDensity = 0.8f;
	public int rainEffectDistance = 18;
	public boolean waterRipples = true;
	public boolean wetSurfaces = true;
	public boolean enhancedSnow = true;
	public float snowDensity = 0.9f;
	public boolean lightningFlashes = true;
	public float lightningFlashIntensity = 1.0f;
	public boolean stormAtmosphere = true;
	public boolean cinematicStorms = false;

	// --- Atmosphere ---
	public boolean atmosphericFog = true;
	public float fogDensity = 1.0f;
	public float fogDistance = 1.0f;
	public boolean biomeFog = true;
	public float forestFog = 1.0f;
	public float mountainFog = 1.0f;
	public float plainsFog = 1.0f;
	public float oceanFog = 1.0f;
	public float swampFog = 1.0f;
	public boolean heightFog = true;
	public float heightFogDensity = 1.0f;
	public int heightFogLevel = 0; // offset from sea level
	public boolean volumetricFog = false;
	public float volumetricStrength = 1.0f;
	public Quality volumetricQuality = Quality.MEDIUM;
	public boolean godRays = true;
	public float godRayStrength = 0.6f;
	public Quality godRayQuality = Quality.MEDIUM;
	public boolean caveFog = true;
	public float caveFogDensity = 1.0f;
	public boolean underwaterEffects = true;
	public float underwaterVisibility = 1.0f;
	public float fogTint = 0.5f;

	// --- Water ---
	public boolean waterWaves = true;
	public float waterWaveHeight = 1.0f;
	public boolean waterShimmer = true;
	public boolean waterCaustics = true;
	public float causticStrength = 0.6f;
	public boolean underwaterDistortion = true;
	public float distortionStrength = 0.5f;
	public boolean waterDroplets = true;

	// --- Particles ---
	public boolean fallingLeaves = true;
	public boolean fireflies = true;
	public boolean dust = true;
	public boolean pollen = true;
	public boolean caveDust = true;
	public boolean windDebris = false;
	public boolean enhancedFire = true;
	public boolean glowParticles = true;
	public float particleDensity = 1.0f;
	public int particleDistance = 28;
	public int maxAmbientParticles = 400;

	// --- Sky ---
	public boolean sunsetGlow = true;
	public float sunsetIntensity = 1.0f;
	public boolean skyScattering = true;
	public boolean moonlight = true;
	public boolean netherAtmosphere = true;
	public boolean endAtmosphere = true;

	// --- Performance ---
	public int effectDistance = 64;
	public boolean lowPerformanceMode = false;
	public boolean frustumCulling = true;
	public int distantUpdateInterval = 4;

	// --- Advanced ---
	public boolean disableWithShaderPacks = true;
	public boolean debugLogging = false;
	public boolean experimentalFeatures = false;

	private static Map<String, Integer> defaultItemLights() {
		Map<String, Integer> map = new LinkedHashMap<>();
		map.put("minecraft:lava_bucket", 15);
		map.put("minecraft:blaze_rod", 10);
		map.put("minecraft:blaze_powder", 8);
		map.put("minecraft:glowstone_dust", 8);
		map.put("minecraft:glow_berries", 6);
		map.put("minecraft:glow_ink_sac", 6);
		map.put("minecraft:nether_star", 12);
		map.put("minecraft:spectral_arrow", 8);
		map.put("minecraft:fire_charge", 10);
		map.put("minecraft:magma_cream", 6);
		map.put("minecraft:amethyst_shard", 4);
		map.put("minecraft:prismarine_crystals", 6);
		map.put("minecraft:echo_shard", 4);
		map.put("minecraft:experience_bottle", 6);
		return map;
	}

	public AetherConfig copy() {
		AetherConfig copy = ConfigManager.GSON.fromJson(ConfigManager.GSON.toJsonTree(this), AetherConfig.class);
		copy.validate();
		return copy;
	}

	/** Clamps every value into its valid range and repairs missing objects. Returns true if anything changed. */
	public boolean validate() {
		boolean changed = false;
		AetherConfig d = DEFAULTS;

		if (preset == null) { preset = d.preset; changed = true; }
		if (colorStyle == null) { colorStyle = d.colorStyle; changed = true; }
		if (bloomQuality == null) { bloomQuality = d.bloomQuality; changed = true; }
		if (ssaoQuality == null) { ssaoQuality = d.ssaoQuality; changed = true; }
		if (volumetricQuality == null) { volumetricQuality = d.volumetricQuality; changed = true; }
		if (godRayQuality == null) { godRayQuality = d.godRayQuality; changed = true; }
		if (customItemLights == null) { customItemLights = defaultItemLights(); changed = true; }
		changed |= customItemLights.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
		customItemLights.replaceAll((k, v) -> Math.max(0, Math.min(15, v)));

		float before;
		int beforeI;
		before = dynamicLightRadius; dynamicLightRadius = clamp(dynamicLightRadius, 0.5f, 1.5f); changed |= before != dynamicLightRadius;
		before = dynamicLightIntensity; dynamicLightIntensity = clamp(dynamicLightIntensity, 0.0f, 1.5f); changed |= before != dynamicLightIntensity;
		beforeI = dynamicLightMaxSources; dynamicLightMaxSources = clamp(dynamicLightMaxSources, 1, 128); changed |= beforeI != dynamicLightMaxSources;
		beforeI = dynamicLightUpdateInterval; dynamicLightUpdateInterval = clamp(dynamicLightUpdateInterval, 1, 10); changed |= beforeI != dynamicLightUpdateInterval;

		exposure = clamp(exposure, -2.0f, 2.0f);
		contrast = clamp(contrast, 0.5f, 1.5f);
		saturation = clamp(saturation, 0.0f, 2.0f);
		brightness = clamp(brightness, -0.5f, 0.5f);
		vignette = clamp(vignette, 0.0f, 1.0f);
		bloomIntensity = clamp(bloomIntensity, 0.0f, 1.5f);
		bloomThreshold = clamp(bloomThreshold, 0.4f, 1.0f);
		ssaoStrength = clamp(ssaoStrength, 0.0f, 1.5f);
		caveDarkness = clamp(caveDarkness, 0.0f, 1.0f);
		nightTint = clamp(nightTint, 0.0f, 1.0f);

		windStrength = clamp(windStrength, 0.0f, 2.0f);
		grassStrength = clamp(grassStrength, 0.0f, 2.0f);
		leavesStrength = clamp(leavesStrength, 0.0f, 2.0f);
		flowerStrength = clamp(flowerStrength, 0.0f, 2.0f);
		cropStrength = clamp(cropStrength, 0.0f, 2.0f);
		underwaterStrength = clamp(underwaterStrength, 0.0f, 2.0f);
		windSpeed = clamp(windSpeed, 0.25f, 3.0f);

		rainSplashDensity = clamp(rainSplashDensity, 0.0f, 2.0f);
		rainEffectDistance = clamp(rainEffectDistance, 4, 32);
		snowDensity = clamp(snowDensity, 0.0f, 2.0f);
		lightningFlashIntensity = clamp(lightningFlashIntensity, 0.0f, 1.5f);

		fogDensity = clamp(fogDensity, 0.0f, 3.0f);
		fogDistance = clamp(fogDistance, 0.25f, 2.0f);
		forestFog = clamp(forestFog, 0.0f, 3.0f);
		mountainFog = clamp(mountainFog, 0.0f, 3.0f);
		plainsFog = clamp(plainsFog, 0.0f, 3.0f);
		oceanFog = clamp(oceanFog, 0.0f, 3.0f);
		swampFog = clamp(swampFog, 0.0f, 3.0f);
		heightFogDensity = clamp(heightFogDensity, 0.0f, 3.0f);
		heightFogLevel = clamp(heightFogLevel, -64, 128);
		volumetricStrength = clamp(volumetricStrength, 0.0f, 2.0f);
		godRayStrength = clamp(godRayStrength, 0.0f, 1.5f);
		caveFogDensity = clamp(caveFogDensity, 0.0f, 3.0f);
		underwaterVisibility = clamp(underwaterVisibility, 0.25f, 3.0f);
		fogTint = clamp(fogTint, 0.0f, 1.0f);

		waterWaveHeight = clamp(waterWaveHeight, 0.0f, 2.0f);
		causticStrength = clamp(causticStrength, 0.0f, 1.5f);
		distortionStrength = clamp(distortionStrength, 0.0f, 1.5f);

		particleDensity = clamp(particleDensity, 0.0f, 2.0f);
		particleDistance = clamp(particleDistance, 8, 64);
		maxAmbientParticles = clamp(maxAmbientParticles, 0, 2000);

		sunsetIntensity = clamp(sunsetIntensity, 0.0f, 2.0f);

		effectDistance = clamp(effectDistance, 16, 256);
		distantUpdateInterval = clamp(distantUpdateInterval, 1, 20);

		return changed;
	}

	private static float clamp(float v, float min, float max) {
		if (Float.isNaN(v)) {
			return min;
		}

		return Math.max(min, Math.min(max, v));
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	/** Pristine defaults used for repairing broken values and for "reset" buttons. */
	public static final AetherConfig DEFAULTS = new AetherConfig();
}
