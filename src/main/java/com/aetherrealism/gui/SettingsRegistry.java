package com.aetherrealism.gui;

import com.aetherrealism.compat.CompatManager;
import com.aetherrealism.config.AetherConfig;
import com.aetherrealism.config.ColorStyle;
import com.aetherrealism.config.ConfigManager;
import com.aetherrealism.config.Preset;
import com.aetherrealism.config.Quality;
import com.aetherrealism.render.PostEffectManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Every row of the settings screen, grouped by category. */
public final class SettingsRegistry {
	private static final List<Setting> ALL = new ArrayList<>();

	private SettingsRegistry() {
	}

	public static List<Setting> all() {
		if (ALL.isEmpty()) {
			build();
		}

		return Collections.unmodifiableList(ALL);
	}

	public static List<Setting> forCategory(Category category) {
		List<Setting> list = new ArrayList<>();

		for (Setting s : all()) {
			if (s.category == category) {
				list.add(s);
			}
		}

		return list;
	}

	// --- builders ---

	private static Setting.Toggle toggle(Category cat, String label, String desc, Function<AetherConfig, Boolean> get,
			BiConsumer<AetherConfig, Boolean> set) {
		Setting.Toggle t = new Setting.Toggle(cat, label, desc, get, set);
		ALL.add(t);
		return t;
	}

	private static Setting.Slider slider(Category cat, String label, String desc, float min, float max, float step,
			Function<AetherConfig, Float> get, BiConsumer<AetherConfig, Float> set, Function<Float, String> fmt) {
		Setting.Slider s = new Setting.Slider(cat, label, desc, min, max, step, get, set, fmt);
		ALL.add(s);
		return s;
	}

	private static <E> Setting.Choice<E> choice(Category cat, String label, String desc, E[] values, Function<E, String> names,
			Function<AetherConfig, E> get, BiConsumer<AetherConfig, E> set) {
		Setting.Choice<E> s = new Setting.Choice<>(cat, label, desc, values, names, get, set);
		ALL.add(s);
		return s;
	}

	private static Setting.Choice<Quality> quality(Category cat, String label, String desc, Function<AetherConfig, Quality> get,
			BiConsumer<AetherConfig, Quality> set) {
		return choice(cat, label, desc, Quality.values(), q -> q.displayName, get, set);
	}

	private static void header(Category cat, String label) {
		ALL.add(new Setting.Header(cat, label, () -> ""));
	}

	private static void header(Category cat, String label, String right) {
		ALL.add(new Setting.Header(cat, label, () -> right));
	}

	private static void info(Category cat, String label, String desc, Function<AetherConfig, String> value) {
		ALL.add(new Setting.Info(cat, label, desc, value, c -> Theme.TEXT_DIM));
	}

	private static void infoColored(Category cat, String label, String desc, Function<AetherConfig, String> value,
			Function<AetherConfig, Integer> color) {
		ALL.add(new Setting.Info(cat, label, desc, value, color));
	}

	private static void action(Category cat, String label, String desc, String button,
			BiConsumer<AetherSettingsScreen, AetherConfig> action) {
		ALL.add(new Setting.Action(cat, label, desc, button, action));
	}

	private static String onOff(boolean v) {
		return v ? "ON" : "OFF";
	}

	private static void summary(String label, Function<AetherConfig, Boolean> on, Function<AetherConfig, String> quality) {
		infoColored(Category.OVERVIEW, label, "Summary of the staged settings.",
				c -> String.format("%-3s  |  %s", onOff(c.masterEnabled && on.apply(c)), quality.apply(c)),
				c -> c.masterEnabled && on.apply(c) ? Theme.SUCCESS : Theme.TEXT_MUTED);
	}

	private static String tier(float v, float low, float high) {
		return v < low ? "Low" : v < high ? "Medium" : v < high * 1.5f ? "High" : "Ultra";
	}

	private static void build() {
		Category o = Category.OVERVIEW;
		header(o, "Aether Realism");
		toggle(o, "Master effects", "Turns every Aether Realism effect on or off at once. Bound to F9 by default for instant recovery.",
				c -> c.masterEnabled, (c, v) -> c.masterEnabled = v).neutral();
		choice(o, "Graphics preset", "Selecting a preset immediately configures every quality setting. Editing any setting afterwards switches to Custom.",
				Preset.values(), p -> p.displayName, c -> c.preset, (c, p) -> {
					p.applyTo(c);
					c.preset = p;
				}).neutral();
		toggle(o, "Performance monitor", "Shows FPS, frame time, active particles, dynamic lights and the preset in the top-left corner.",
				c -> c.showPerformanceMonitor, (c, v) -> c.showPerformanceMonitor = v).neutral();

		header(o, "Feature", "Enabled  |  Quality");
		summary("Dynamic Lighting", c -> c.dynamicLights, c -> tier(c.dynamicLightMaxSources, 16, 48));
		summary("Moving Grass", c -> c.vegetationAnimation && c.swayGrass, c -> tier(c.grassStrength * c.windStrength, 0.6f, 1.1f));
		summary("Moving Leaves", c -> c.vegetationAnimation && c.swayLeaves, c -> tier(c.leavesStrength * c.windStrength, 0.6f, 1.1f));
		summary("Wind Simulation", c -> c.vegetationAnimation, c -> c.windGusts ? "Gusts" : "Steady");
		summary("Enhanced Rain", c -> c.enhancedRain, c -> tier(c.rainSplashDensity, 0.5f, 0.9f));
		summary("Atmospheric Fog", c -> c.atmosphericFog, c -> c.heightFog ? "Height + biome" : "Distance");
		summary("Volumetric Fog", c -> c.postProcessing && c.volumetricFog, c -> c.volumetricQuality.displayName);
		summary("God Rays", c -> c.postProcessing && c.godRays, c -> c.godRayQuality.displayName);
		summary("Bloom", c -> c.postProcessing && c.bloom, c -> c.bloomQuality.displayName);
		summary("Water Waves", c -> c.waterWaves, c -> tier(c.waterWaveHeight, 0.5f, 0.9f));
		summary("Falling Leaves", c -> c.fallingLeaves, c -> tier(c.particleDensity, 0.5f, 0.9f));
		summary("Fireflies", c -> c.fireflies, c -> tier(c.particleDensity, 0.5f, 0.9f));
		summary("Water Reflections", c -> false, c -> "Needs a shader pack");

		header(o, "Status");
		infoColored(o, "Terrain shader layer", "Foliage and water animation come from the bundled 'Aether Realism Shaders' resource pack.",
				c -> CompatManager.sodiumLoaded() ? "Unavailable with Sodium"
						: CompatManager.shaderLayerPackSelected() ? "Pack enabled" : "Pack disabled in Resource Packs",
				c -> !CompatManager.sodiumLoaded() && CompatManager.shaderLayerPackSelected() ? Theme.SUCCESS : Theme.WARNING);
		infoColored(o, "Post-processing", "Fog scattering, god rays, bloom and color grading.",
				c -> CompatManager.suspendedByShaderPack() ? "Paused (shader pack active)"
						: PostEffectManager.INSTANCE.isAvailable() ? "Available" : "Unavailable (see log)",
				c -> PostEffectManager.INSTANCE.isAvailable() && !CompatManager.suspendedByShaderPack() ? Theme.SUCCESS : Theme.WARNING);
		info(o, "Other mods", "Detected rendering mods.", c -> CompatManager.describe());
		infoColored(o, "Safe mode", "Started with -Daether_realism.safe=true: effects are off until you apply settings.",
				c -> ConfigManager.isSafeMode() ? "ACTIVE" : "off", c -> ConfigManager.isSafeMode() ? Theme.WARNING : Theme.TEXT_MUTED);

		buildLighting();
		buildNature();
		buildWeather();
		buildAtmosphere();
		buildWater();
		buildParticles();
		buildSky();
		buildPerformance();
		buildPresets();
		buildAdvanced();
	}

	private static void buildLighting() {
		Category l = Category.LIGHTING;
		header(l, "Dynamic lights");
		toggle(l, "Dynamic lighting", "Moving light sources light up terrain and entities around them. Terrain is re-meshed near moving lights.",
				c -> c.dynamicLights, (c, v) -> c.dynamicLights = v).impact(Impact.MEDIUM)
				.note("Under Sodium the terrain part may not apply; entity lighting still works.");
		toggle(l, "Held light sources", "Torches, lanterns, glowstone and other luminous items held by players and mobs emit light.",
				c -> c.dynamicLightsHeldItems, (c, v) -> c.dynamicLightsHeldItems = v).impact(Impact.LOW).enabledWhen(c -> c.dynamicLights);
		toggle(l, "Entity light sources", "Burning entities, dropped glowing items, blazes, lit TNT, fireworks and similar emit light.",
				c -> c.dynamicLightsEntities, (c, v) -> c.dynamicLightsEntities = v).impact(Impact.LOW).enabledWhen(c -> c.dynamicLights);
		slider(l, "Light radius", "How far dynamic light reaches, relative to the source's light level.", 0.5f, 1.5f, 0.05f,
				c -> c.dynamicLightRadius, (c, v) -> c.dynamicLightRadius = v, Setting.Slider.percent()).impact(Impact.LOW).enabledWhen(c -> c.dynamicLights);
		slider(l, "Light intensity", "Brightness of dynamic light.", 0.0f, 1.5f, 0.05f,
				c -> c.dynamicLightIntensity, (c, v) -> c.dynamicLightIntensity = v, Setting.Slider.percent()).enabledWhen(c -> c.dynamicLights);
		slider(l, "Max dynamic lights", "Only the closest sources are kept. Each one causes nearby terrain to be re-meshed while it moves.",
				1, 128, 1, c -> (float) c.dynamicLightMaxSources, (c, v) -> c.dynamicLightMaxSources = Math.round(v),
				Setting.Slider.integer("")).impact(Impact.MEDIUM).enabledWhen(c -> c.dynamicLights);
		slider(l, "Light update interval", "Ticks between terrain light refreshes. Lower is smoother but costs more CPU.",
				1, 10, 1, c -> (float) c.dynamicLightUpdateInterval, (c, v) -> c.dynamicLightUpdateInterval = Math.round(v),
				Setting.Slider.integer(" ticks")).impact(Impact.MEDIUM).enabledWhen(c -> c.dynamicLights);
		toggle(l, "Smooth light movement", "Refresh light as soon as a source moves a fraction of a block instead of a whole block.",
				c -> c.dynamicLightSmoothing, (c, v) -> c.dynamicLightSmoothing = v).impact(Impact.MEDIUM).enabledWhen(c -> c.dynamicLights);
		info(l, "Light colors", "Vanilla terrain has a single block-light color, so dynamic lights use Minecraft's warm torch light. Per-light colors would need a shader pack.",
				c -> "Vanilla warm light");

		header(l, "Image");
		toggle(l, "Post-processing", "Master switch for all screen-space effects (grading, bloom, fog scattering, god rays, SSAO).",
				c -> c.postProcessing, (c, v) -> c.postProcessing = v).impact(Impact.MEDIUM);
		slider(l, "Exposure", "Overall exposure in stops.", -2.0f, 2.0f, 0.05f, c -> c.exposure, (c, v) -> c.exposure = v,
				v -> String.format("%+.2f EV", v)).enabledWhen(c -> c.postProcessing);
		slider(l, "Brightness", "Lifts or lowers the whole image.", -0.5f, 0.5f, 0.01f, c -> c.brightness, (c, v) -> c.brightness = v,
				v -> String.format("%+.2f", v)).enabledWhen(c -> c.postProcessing);
		slider(l, "Contrast", "Contrast around mid-grey.", 0.5f, 1.5f, 0.01f, c -> c.contrast, (c, v) -> c.contrast = v,
				Setting.Slider.percent()).enabledWhen(c -> c.postProcessing);
		slider(l, "Saturation", "Color intensity.", 0.0f, 2.0f, 0.01f, c -> c.saturation, (c, v) -> c.saturation = v,
				Setting.Slider.percent()).enabledWhen(c -> c.postProcessing);
		toggle(l, "Cinematic color grading", "Applies the selected color style (tint and split toning).",
				c -> c.colorGrading, (c, v) -> c.colorGrading = v).impact(Impact.LOW).enabledWhen(c -> c.postProcessing);
		choice(l, "Color style", "The look applied by color grading.", ColorStyle.values(), s -> s.displayName,
				c -> c.colorStyle, (c, v) -> c.colorStyle = v).enabledWhen(c -> c.postProcessing && c.colorGrading);
		slider(l, "Vignette", "Darkens the screen edges.", 0.0f, 1.0f, 0.01f, c -> c.vignette, (c, v) -> c.vignette = v,
				Setting.Slider.percent()).enabledWhen(c -> c.postProcessing);

		header(l, "Glow");
		toggle(l, "Bloom", "Soft glow around bright light sources, lava and the sun. Thresholded so the image never turns blurry.",
				c -> c.bloom, (c, v) -> c.bloom = v).impact(Impact.MEDIUM).enabledWhen(c -> c.postProcessing);
		slider(l, "Glow intensity", "Strength of bloom.", 0.0f, 1.5f, 0.01f, c -> c.bloomIntensity, (c, v) -> c.bloomIntensity = v,
				Setting.Slider.percent()).enabledWhen(c -> c.postProcessing && c.bloom);
		slider(l, "Glow threshold", "Only pixels brighter than this glow.", 0.4f, 1.0f, 0.01f, c -> c.bloomThreshold,
				(c, v) -> c.bloomThreshold = v, Setting.Slider.percent()).enabledWhen(c -> c.postProcessing && c.bloom);
		quality(l, "Bloom quality", "Blur sample count.", c -> c.bloomQuality, (c, v) -> c.bloomQuality = v)
				.impact(Impact.MEDIUM).enabledWhen(c -> c.postProcessing && c.bloom);

		header(l, "Ambient light");
		toggle(l, "Screen-space ambient occlusion", "Darkens creases and corners using the depth buffer, on top of vanilla smooth lighting.",
				c -> c.ssao, (c, v) -> c.ssao = v).impact(Impact.HIGH).expensive().experimental()
				.enabledWhen(c -> c.postProcessing && c.experimentalFeatures).note("Requires Advanced > Experimental features.");
		slider(l, "AO strength", "Darkness of ambient occlusion.", 0.0f, 1.5f, 0.01f, c -> c.ssaoStrength, (c, v) -> c.ssaoStrength = v,
				Setting.Slider.percent()).enabledWhen(c -> c.postProcessing && c.ssao);
		quality(l, "AO quality", "Number of depth samples per pixel.", c -> c.ssaoQuality, (c, v) -> c.ssaoQuality = v)
				.impact(Impact.HIGH).enabledWhen(c -> c.postProcessing && c.ssao);
		toggle(l, "Cave atmosphere", "Deeper shadows and a cold tone underground.", c -> c.caveAtmosphere, (c, v) -> c.caveAtmosphere = v)
				.impact(Impact.LOW).enabledWhen(c -> c.postProcessing);
		slider(l, "Cave darkness", "How much darker caves become.", 0.0f, 1.0f, 0.01f, c -> c.caveDarkness, (c, v) -> c.caveDarkness = v,
				Setting.Slider.percent()).enabledWhen(c -> c.postProcessing && c.caveAtmosphere);
	}

	private static void buildNature() {
		Category n = Category.NATURE;
		header(n, "Vegetation");
		toggle(n, "Terrain shader layer", "Marks foliage and water so the bundled 'Aether Realism Shaders' resource pack can animate them. Disable the pack in Resource Packs if another pack replaces terrain shaders.",
				c -> c.shaderLayer, (c, v) -> c.shaderLayer = v).impact(Impact.LOW)
				.note("Not available with Sodium or while an Iris shader pack is active.");
		toggle(n, "Animated vegetation", "Plants and leaves move in the wind (vertex animation on the GPU).",
				c -> c.vegetationAnimation, (c, v) -> c.vegetationAnimation = v).impact(Impact.LOW).enabledWhen(c -> c.shaderLayer);
		toggle(n, "Grass and ferns", "Grass, ferns, bushes and similar plants bend with the wind; roots stay anchored.",
				c -> c.swayGrass, (c, v) -> c.swayGrass = v).enabledWhen(c -> c.shaderLayer && c.vegetationAnimation);
		slider(n, "Grass motion", "Amplitude for grass and ferns.", 0.0f, 2.0f, 0.05f, c -> c.grassStrength, (c, v) -> c.grassStrength = v,
				Setting.Slider.percent()).enabledWhen(c -> c.shaderLayer && c.vegetationAnimation && c.swayGrass);
		toggle(n, "Leaves", "Tree leaves flutter with per-vertex variation.", c -> c.swayLeaves, (c, v) -> c.swayLeaves = v)
				.enabledWhen(c -> c.shaderLayer && c.vegetationAnimation);
		slider(n, "Leaf motion", "Amplitude for leaves.", 0.0f, 2.0f, 0.05f, c -> c.leavesStrength, (c, v) -> c.leavesStrength = v,
				Setting.Slider.percent()).enabledWhen(c -> c.shaderLayer && c.vegetationAnimation && c.swayLeaves);
		toggle(n, "Flowers", "Flowers and tall flowers sway.", c -> c.swayFlowers, (c, v) -> c.swayFlowers = v)
				.enabledWhen(c -> c.shaderLayer && c.vegetationAnimation);
		slider(n, "Flower motion", "Amplitude for flowers.", 0.0f, 2.0f, 0.05f, c -> c.flowerStrength, (c, v) -> c.flowerStrength = v,
				Setting.Slider.percent()).enabledWhen(c -> c.shaderLayer && c.vegetationAnimation && c.swayFlowers);
		toggle(n, "Crops and saplings", "Wheat, carrots, saplings and similar plants sway.", c -> c.swayCrops, (c, v) -> c.swayCrops = v)
				.enabledWhen(c -> c.shaderLayer && c.vegetationAnimation);
		slider(n, "Crop motion", "Amplitude for crops.", 0.0f, 2.0f, 0.05f, c -> c.cropStrength, (c, v) -> c.cropStrength = v,
				Setting.Slider.percent()).enabledWhen(c -> c.shaderLayer && c.vegetationAnimation && c.swayCrops);
		toggle(n, "Underwater plants", "Seagrass, kelp and coral fans drift with a slow current.", c -> c.swayUnderwater,
				(c, v) -> c.swayUnderwater = v).enabledWhen(c -> c.shaderLayer && c.vegetationAnimation);
		slider(n, "Underwater motion", "Amplitude for underwater plants.", 0.0f, 2.0f, 0.05f, c -> c.underwaterStrength,
				(c, v) -> c.underwaterStrength = v, Setting.Slider.percent())
				.enabledWhen(c -> c.shaderLayer && c.vegetationAnimation && c.swayUnderwater);

		header(n, "Wind");
		slider(n, "Wind strength", "Overall wind amplitude for vegetation, particles and snow.", 0.0f, 2.0f, 0.05f,
				c -> c.windStrength, (c, v) -> c.windStrength = v, Setting.Slider.percent());
		slider(n, "Wind speed", "How fast the wind changes direction and strength (particles and weather). Vegetation sway speed is fixed in the shader.",
				0.25f, 3.0f, 0.05f, c -> c.windSpeed, (c, v) -> c.windSpeed = v, Setting.Slider.percent());
		toggle(n, "Wind gusts", "Occasional smooth gusts that push particles and snow harder.", c -> c.windGusts, (c, v) -> c.windGusts = v);
		toggle(n, "Storm wind", "Vegetation moves harder in rain and thunderstorms (nearby terrain is re-meshed gradually when a storm starts or ends).",
				c -> c.stormWindResponse, (c, v) -> c.stormWindResponse = v).impact(Impact.LOW)
				.enabledWhen(c -> c.shaderLayer && c.vegetationAnimation);
		info(n, "Wind direction", "Direction wanders slowly and is computed on the GPU; it is not user-fixed because the vanilla terrain shader has no input for live parameters.",
				c -> "Dynamic");
		info(n, "Distance quality", "Vegetation animation fades out between 45% and 70% of your render distance.",
				c -> "Automatic");
	}

	private static void buildWeather() {
		Category w = Category.WEATHER;
		header(w, "Rain");
		toggle(w, "Enhanced rain", "Extra rain detail: splashes, canopy drips and ambience. Vanilla rain keeps working unchanged.",
				c -> c.enhancedRain, (c, v) -> c.enhancedRain = v).impact(Impact.LOW);
		toggle(w, "Rain splashes", "Drops splash where they land.", c -> c.rainSplashes, (c, v) -> c.rainSplashes = v)
				.impact(Impact.LOW).enabledWhen(c -> c.enhancedRain);
		slider(w, "Splash density", "Number of splashes.", 0.0f, 2.0f, 0.05f, c -> c.rainSplashDensity, (c, v) -> c.rainSplashDensity = v,
				Setting.Slider.percent()).impact(Impact.LOW).enabledWhen(c -> c.enhancedRain && c.rainSplashes);
		slider(w, "Rain effect distance", "How far from you splashes appear.", 4, 32, 1, c -> (float) c.rainEffectDistance,
				(c, v) -> c.rainEffectDistance = Math.round(v), Setting.Slider.integer(" blocks")).enabledWhen(c -> c.enhancedRain);
		toggle(w, "Water ripples", "Rain hitting water makes ripples.", c -> c.waterRipples, (c, v) -> c.waterRipples = v)
				.enabledWhen(c -> c.enhancedRain);
		toggle(w, "Wet surfaces", "The world looks darker and glossier while it rains (screen-space; there are no puddle decals).",
				c -> c.wetSurfaces, (c, v) -> c.wetSurfaces = v).enabledWhen(c -> c.postProcessing);

		header(w, "Snow");
		toggle(w, "Enhanced snow", "Wind-driven snowflakes and blizzard visibility.", c -> c.enhancedSnow, (c, v) -> c.enhancedSnow = v)
				.impact(Impact.LOW);
		slider(w, "Snow density", "Number of extra snowflakes and blizzard thickness.", 0.0f, 2.0f, 0.05f, c -> c.snowDensity,
				(c, v) -> c.snowDensity = v, Setting.Slider.percent()).enabledWhen(c -> c.enhancedSnow);

		header(w, "Storms");
		toggle(w, "Lightning flashes", "Lightning briefly lights up the scene and the fog.", c -> c.lightningFlashes,
				(c, v) -> c.lightningFlashes = v);
		slider(w, "Flash intensity", "Brightness of lightning flashes.", 0.0f, 1.5f, 0.05f, c -> c.lightningFlashIntensity,
				(c, v) -> c.lightningFlashIntensity = v, Setting.Slider.percent()).enabledWhen(c -> c.lightningFlashes);
		toggle(w, "Storm atmosphere", "Thicker fog and a darker, desaturated look in rain and storms.", c -> c.stormAtmosphere,
				(c, v) -> c.stormAtmosphere = v);
		toggle(w, "Cinematic storms", "Even denser fog during thunderstorms.", c -> c.cinematicStorms, (c, v) -> c.cinematicStorms = v)
				.enabledWhen(c -> c.stormAtmosphere);
	}

	private static void buildAtmosphere() {
		Category a = Category.ATMOSPHERE;
		header(a, "Fog");
		toggle(a, "Atmospheric fog", "Distance fog shaped by weather, biome, caves and time of day, with soft horizon transitions.",
				c -> c.atmosphericFog, (c, v) -> c.atmosphericFog = v).impact(Impact.NONE);
		slider(a, "Fog density", "Overall fog thickness.", 0.0f, 3.0f, 0.05f, c -> c.fogDensity, (c, v) -> c.fogDensity = v,
				Setting.Slider.percent()).enabledWhen(c -> c.atmosphericFog);
		slider(a, "Fog distance", "Scales how far you can see through fog.", 0.25f, 2.0f, 0.05f, c -> c.fogDistance,
				(c, v) -> c.fogDistance = v, Setting.Slider.percent()).enabledWhen(c -> c.atmosphericFog);
		slider(a, "Fog color tint", "How strongly weather, sunsets and caves tint the fog color.", 0.0f, 1.0f, 0.05f, c -> c.fogTint,
				(c, v) -> c.fogTint = v, Setting.Slider.percent()).enabledWhen(c -> c.atmosphericFog);
		toggle(a, "Biome fog", "Forests, swamps, oceans and mountains each have their own fog.", c -> c.biomeFog, (c, v) -> c.biomeFog = v)
				.enabledWhen(c -> c.atmosphericFog);
		slider(a, "Forest fog", "Fog in forests, jungles and taigas.", 0.0f, 3.0f, 0.05f, c -> c.forestFog, (c, v) -> c.forestFog = v,
				Setting.Slider.percent()).enabledWhen(c -> c.atmosphericFog && c.biomeFog);
		slider(a, "Mountain fog", "Fog in peaks, slopes and windswept hills.", 0.0f, 3.0f, 0.05f, c -> c.mountainFog,
				(c, v) -> c.mountainFog = v, Setting.Slider.percent()).enabledWhen(c -> c.atmosphericFog && c.biomeFog);
		slider(a, "Plains fog", "Fog in plains, savannas and deserts.", 0.0f, 3.0f, 0.05f, c -> c.plainsFog, (c, v) -> c.plainsFog = v,
				Setting.Slider.percent()).enabledWhen(c -> c.atmosphericFog && c.biomeFog);
		slider(a, "Ocean fog", "Sea mist over oceans, beaches and rivers.", 0.0f, 3.0f, 0.05f, c -> c.oceanFog, (c, v) -> c.oceanFog = v,
				Setting.Slider.percent()).enabledWhen(c -> c.atmosphericFog && c.biomeFog);
		slider(a, "Swamp fog", "Fog in swamps and mangroves.", 0.0f, 3.0f, 0.05f, c -> c.swampFog, (c, v) -> c.swampFog = v,
				Setting.Slider.percent()).enabledWhen(c -> c.atmosphericFog && c.biomeFog);
		toggle(a, "Cave fog", "A dark haze underground.", c -> c.caveFog, (c, v) -> c.caveFog = v).enabledWhen(c -> c.atmosphericFog);
		slider(a, "Cave fog density", "Thickness of cave fog.", 0.0f, 3.0f, 0.05f, c -> c.caveFogDensity, (c, v) -> c.caveFogDensity = v,
				Setting.Slider.percent()).enabledWhen(c -> c.atmosphericFog && c.caveFog);

		header(a, "Height fog and mist");
		toggle(a, "Height fog", "Exponential fog that pools in valleys and over water, thickest at dawn and in rain (screen-space, from the depth buffer).",
				c -> c.heightFog, (c, v) -> c.heightFog = v).impact(Impact.LOW).enabledWhen(c -> c.postProcessing && c.atmosphericFog);
		slider(a, "Height fog density", "Thickness of low-lying fog.", 0.0f, 3.0f, 0.05f, c -> c.heightFogDensity,
				(c, v) -> c.heightFogDensity = v, Setting.Slider.percent()).enabledWhen(c -> c.postProcessing && c.heightFog);
		slider(a, "Height fog level", "Fog layer height relative to sea level.", -64, 128, 1, c -> (float) c.heightFogLevel,
				(c, v) -> c.heightFogLevel = Math.round(v), v -> String.format("%+d blocks", Math.round(v)))
				.enabledWhen(c -> c.postProcessing && c.heightFog);

		header(a, "Light scattering");
		toggle(a, "Volumetric-style fog", "Ray-marches the depth buffer so fog glows toward the sun and moon. This is a screen-space approximation, not true volumetrics.",
				c -> c.volumetricFog, (c, v) -> c.volumetricFog = v).impact(Impact.VERY_HIGH).expensive().enabledWhen(c -> c.postProcessing);
		slider(a, "Scattering strength", "Brightness of in-scattered light.", 0.0f, 2.0f, 0.05f, c -> c.volumetricStrength,
				(c, v) -> c.volumetricStrength = v, Setting.Slider.percent()).enabledWhen(c -> c.postProcessing && c.volumetricFog);
		quality(a, "Scattering quality", "Ray-march steps per pixel.", c -> c.volumetricQuality, (c, v) -> c.volumetricQuality = v)
				.impact(Impact.VERY_HIGH).enabledWhen(c -> c.postProcessing && c.volumetricFog);
		toggle(a, "God rays", "Light shafts from the sun (or moon) where it is visible between terrain, trees and clouds of fog.",
				c -> c.godRays, (c, v) -> c.godRays = v).impact(Impact.HIGH).expensive().enabledWhen(c -> c.postProcessing);
		slider(a, "God ray strength", "Brightness of the light shafts.", 0.0f, 1.5f, 0.05f, c -> c.godRayStrength,
				(c, v) -> c.godRayStrength = v, Setting.Slider.percent()).enabledWhen(c -> c.postProcessing && c.godRays);
		quality(a, "God ray quality", "Radial blur samples.", c -> c.godRayQuality, (c, v) -> c.godRayQuality = v)
				.impact(Impact.HIGH).enabledWhen(c -> c.postProcessing && c.godRays);

		header(a, "Underwater");
		toggle(a, "Underwater effects", "Underwater visibility control plus the water category's caustics and distortion.",
				c -> c.underwaterEffects, (c, v) -> c.underwaterEffects = v);
		slider(a, "Underwater visibility", "Multiplier for vanilla underwater view distance.", 0.25f, 3.0f, 0.05f,
				c -> c.underwaterVisibility, (c, v) -> c.underwaterVisibility = v, Setting.Slider.percent())
				.enabledWhen(c -> c.underwaterEffects);
	}

	private static void buildWater() {
		Category w = Category.WATER;
		header(w, "Surface");
		toggle(w, "Water waves", "The water surface moves in layered waves. Water seams stay closed because only surface vertices move.",
				c -> c.waterWaves, (c, v) -> c.waterWaves = v).impact(Impact.LOW).enabledWhen(c -> c.shaderLayer)
				.note("Requires the terrain shader layer.");
		slider(w, "Wave height", "Wave amplitude.", 0.0f, 2.0f, 0.05f, c -> c.waterWaveHeight, (c, v) -> c.waterWaveHeight = v,
				Setting.Slider.percent()).enabledWhen(c -> c.shaderLayer && c.waterWaves);
		toggle(w, "Ripple shimmer", "Animated ripple highlights and slightly clearer water.", c -> c.waterShimmer,
				(c, v) -> c.waterShimmer = v).impact(Impact.LOW).enabledWhen(c -> c.shaderLayer);
		info(w, "Reflections", "Screen-space reflections need to know which pixels are water, which the vanilla pipeline does not expose to post-processing. Use an Iris shader pack for reflections.",
				c -> "Not available");

		header(w, "Underwater");
		toggle(w, "Caustics", "Dancing light patterns on surfaces while you are underwater.", c -> c.waterCaustics,
				(c, v) -> c.waterCaustics = v).impact(Impact.LOW).enabledWhen(c -> c.postProcessing && c.underwaterEffects);
		slider(w, "Caustic strength", "Brightness of caustics.", 0.0f, 1.5f, 0.05f, c -> c.causticStrength, (c, v) -> c.causticStrength = v,
				Setting.Slider.percent()).enabledWhen(c -> c.postProcessing && c.waterCaustics);
		toggle(w, "Underwater distortion", "Gentle refraction wobble while submerged.", c -> c.underwaterDistortion,
				(c, v) -> c.underwaterDistortion = v).impact(Impact.LOW).enabledWhen(c -> c.postProcessing && c.underwaterEffects);
		slider(w, "Distortion strength", "Amount of wobble.", 0.0f, 1.5f, 0.05f, c -> c.distortionStrength,
				(c, v) -> c.distortionStrength = v, Setting.Slider.percent()).enabledWhen(c -> c.postProcessing && c.underwaterDistortion);
		toggle(w, "Water droplets", "Water drips off you for a few seconds after leaving water.", c -> c.waterDroplets,
				(c, v) -> c.waterDroplets = v);
	}

	private static void buildParticles() {
		Category p = Category.PARTICLES;
		header(p, "Ambient particles");
		toggle(p, "Falling leaves", "Leaves drift down from trees, tinted to match the foliage.", c -> c.fallingLeaves,
				(c, v) -> c.fallingLeaves = v).impact(Impact.LOW);
		toggle(p, "Fireflies", "Fireflies over grass and water on calm nights.", c -> c.fireflies, (c, v) -> c.fireflies = v).impact(Impact.LOW);
		toggle(p, "Floating dust", "Dust motes catching the sunlight.", c -> c.dust, (c, v) -> c.dust = v).impact(Impact.LOW);
		toggle(p, "Pollen", "Pollen drifting on the wind on sunny days.", c -> c.pollen, (c, v) -> c.pollen = v).impact(Impact.LOW);
		toggle(p, "Cave dust", "Slow dust in dark caves.", c -> c.caveDust, (c, v) -> c.caveDust = v).impact(Impact.LOW);
		toggle(p, "Windblown debris", "Dirt and grass bits carried by strong wind.", c -> c.windDebris, (c, v) -> c.windDebris = v)
				.impact(Impact.LOW);
		toggle(p, "Fire and smoke", "Extra embers, flickering flames and wind-drifted smoke around fires, torches and lava.",
				c -> c.enhancedFire, (c, v) -> c.enhancedFire = v).impact(Impact.LOW);
		toggle(p, "Glowing sparkles", "Subtle sparkles near amethyst, glowstone, froglights and other glowing blocks.",
				c -> c.glowParticles, (c, v) -> c.glowParticles = v).impact(Impact.LOW);
		slider(p, "Particle density", "Multiplier for every ambient particle type. Also follows the vanilla Particles setting.",
				0.0f, 2.0f, 0.05f, c -> c.particleDensity, (c, v) -> c.particleDensity = v, Setting.Slider.percent()).impact(Impact.MEDIUM);
	}

	private static void buildSky() {
		Category s = Category.SKY;
		header(s, "Day and night");
		toggle(s, "Sunrise and sunset glow", "Warm horizon glow and golden-hour fog around sunrise and sunset.", c -> c.sunsetGlow,
				(c, v) -> c.sunsetGlow = v).enabledWhen(c -> c.postProcessing);
		slider(s, "Sunset intensity", "Strength of the golden-hour look.", 0.0f, 2.0f, 0.05f, c -> c.sunsetIntensity,
				(c, v) -> c.sunsetIntensity = v, Setting.Slider.percent()).enabledWhen(c -> c.postProcessing && c.sunsetGlow);
		toggle(s, "Sky scattering", "A soft halo around the sun and a brighter horizon haze.", c -> c.skyScattering,
				(c, v) -> c.skyScattering = v).impact(Impact.LOW).enabledWhen(c -> c.postProcessing);
		toggle(s, "Night atmosphere", "Cool blue night tone with gentle shadow lift.", c -> c.nightAtmosphere,
				(c, v) -> c.nightAtmosphere = v).enabledWhen(c -> c.postProcessing);
		slider(s, "Night tint", "Strength of the night tone.", 0.0f, 1.0f, 0.05f, c -> c.nightTint, (c, v) -> c.nightTint = v,
				Setting.Slider.percent()).enabledWhen(c -> c.postProcessing && c.nightAtmosphere);
		toggle(s, "Moonlight", "At night the moon drives light scattering and god rays.", c -> c.moonlight, (c, v) -> c.moonlight = v)
				.enabledWhen(c -> c.postProcessing);
		info(s, "Clouds and stars", "Clouds and stars are vanilla, tinted by the grading and fog.", c -> "Vanilla");

		header(s, "Dimensions");
		toggle(s, "Nether atmosphere", "Slightly thicker Nether haze. Overworld effects (weather, sun, god rays) never run in the Nether.",
				c -> c.netherAtmosphere, (c, v) -> c.netherAtmosphere = v);
		toggle(s, "End atmosphere", "Subtle End haze.", c -> c.endAtmosphere, (c, v) -> c.endAtmosphere = v);
	}

	private static void buildPerformance() {
		Category p = Category.PERFORMANCE;
		header(p, "Optimization");
		toggle(p, "Low-performance mode", "Cuts particle spawns, uses the cheapest post-processing paths and shortens effect distances.",
				c -> c.lowPerformanceMode, (c, v) -> c.lowPerformanceMode = v);
		toggle(p, "Frustum culling", "Skips particles that would spawn outside your view.", c -> c.frustumCulling, (c, v) -> c.frustumCulling = v);
		slider(p, "Effect distance", "Maximum distance for dynamic lights and other per-entity effects.", 16, 256, 8,
				c -> (float) c.effectDistance, (c, v) -> c.effectDistance = Math.round(v), Setting.Slider.integer(" blocks")).impact(Impact.MEDIUM);
		slider(p, "Distant light updates", "Dynamic lights farther than 24 blocks refresh only every N light updates.", 1, 20, 1,
				c -> (float) c.distantUpdateInterval, (c, v) -> c.distantUpdateInterval = Math.round(v), Setting.Slider.integer("x"))
				.impact(Impact.LOW);

		header(p, "Limits");
		slider(p, "Max ambient particles", "Upper bound on live particles spawned by Aether Realism.", 0, 2000, 10,
				c -> (float) c.maxAmbientParticles, (c, v) -> c.maxAmbientParticles = Math.round(v), Setting.Slider.integer(""))
				.impact(Impact.MEDIUM);
		slider(p, "Particle distance", "Radius around you where ambient particles spawn.", 8, 64, 1, c -> (float) c.particleDistance,
				(c, v) -> c.particleDistance = Math.round(v), Setting.Slider.integer(" blocks")).impact(Impact.MEDIUM);
		slider(p, "Max dynamic lights", "Only the closest sources are kept.", 1, 128, 1, c -> (float) c.dynamicLightMaxSources,
				(c, v) -> c.dynamicLightMaxSources = Math.round(v), Setting.Slider.integer("")).impact(Impact.MEDIUM);
		quality(p, "Bloom quality", "Blur sample count.", c -> c.bloomQuality, (c, v) -> c.bloomQuality = v).impact(Impact.MEDIUM);
		quality(p, "God ray quality", "Radial blur samples.", c -> c.godRayQuality, (c, v) -> c.godRayQuality = v).impact(Impact.HIGH);
		quality(p, "Scattering quality", "Ray-march steps.", c -> c.volumetricQuality, (c, v) -> c.volumetricQuality = v).impact(Impact.VERY_HIGH);
		quality(p, "AO quality", "Depth samples.", c -> c.ssaoQuality, (c, v) -> c.ssaoQuality = v).impact(Impact.HIGH);
	}

	private static void buildPresets() {
		Category p = Category.PRESETS;
		header(p, "Graphics presets");
		info(p, "Current preset", "Changes to individual settings switch the preset to Custom.", c -> c.preset.displayName);

		for (Preset preset : Preset.values()) {
			if (preset == Preset.CUSTOM) {
				continue;
			}

			action(p, preset.displayName, preset.description, "Apply", (screen, c) -> {
				preset.applyTo(c);
				c.preset = preset;
				screen.onWorkingChanged(false);
			});
		}
	}

	private static void buildAdvanced() {
		Category a = Category.ADVANCED;
		header(a, "Compatibility");
		toggle(a, "Pause with shader packs", "Suspend Aether Realism's fog, post-processing and terrain markers while an Iris shader pack is active.",
				c -> c.disableWithShaderPacks, (c, v) -> c.disableWithShaderPacks = v).neutral();
		info(a, "Detected mods", "Rendering mods found at startup.", c -> CompatManager.describe());

		header(a, "Diagnostics");
		toggle(a, "Debug logging", "Log extra details about effect state (for bug reports).", c -> c.debugLogging, (c, v) -> c.debugLogging = v).neutral();
		toggle(a, "Experimental features", "Unlocks options marked EXPERIMENTAL (currently: screen-space ambient occlusion).",
				c -> c.experimentalFeatures, (c, v) -> c.experimentalFeatures = v).neutral().experimental();
		info(a, "Config file", "Delete this file to reset everything; a malformed file is backed up and replaced with defaults.",
				c -> "config/aether_realism.json");
		info(a, "Custom item lights", "Edit \"customItemLights\" in the config file to set the light level of any item.",
				c -> c.customItemLights.size() + " entries");

		header(a, "Recovery");
		action(a, "Reload resources", "Reloads resource packs and shaders (F3+T).", "Reload", (screen, c) -> Minecraft.getInstance().reloadResourcePacks());
		action(a, "Reset everything", "Restore every setting to its default value.", "Reset all...", (screen, c) -> screen.confirmResetAll());
	}
}
