package com.aetherrealism.gui;

/** Settings categories, in sidebar order. Icons are font glyphs so they scale with the GUI and need no textures. */
public enum Category {
	OVERVIEW("Overview", "◉", "Master switch, current preset and a summary of active effects."),
	LIGHTING("Lighting", "☀", "Dynamic lights, ambient lighting, bloom, exposure and color grading."),
	NATURE("Nature", "❀", "Moving grass, leaves, flowers, crops and wind."),
	WEATHER("Weather", "☂", "Rain, snow, storms, splashes and lightning."),
	ATMOSPHERE("Atmosphere", "☁", "Fog, mist, light scattering and god rays."),
	WATER("Water", "≈", "Waves, ripples, caustics and underwater effects."),
	PARTICLES("Particles", "✦", "Falling leaves, fireflies, dust and other ambient particles."),
	SKY("Sky", "☾", "Sunrise, sunset, night and per-dimension atmosphere."),
	PERFORMANCE("Performance", "⚡", "Effect distances, particle limits and optimizations."),
	PRESETS("Presets", "★", "One-click graphics configurations."),
	ADVANCED("Advanced", "⚙", "Compatibility, diagnostics and experimental features.");

	public final String displayName;
	public final String icon;
	public final String description;

	Category(String displayName, String icon, String description) {
		this.displayName = displayName;
		this.icon = icon;
		this.description = description;
	}
}
