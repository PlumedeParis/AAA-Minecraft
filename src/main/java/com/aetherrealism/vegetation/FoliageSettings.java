package com.aetherrealism.vegetation;

import com.aetherrealism.compat.CompatManager;
import com.aetherrealism.config.AetherConfig;
import com.aetherrealism.config.ConfigManager;

/**
 * Immutable snapshot of the foliage settings read from chunk-meshing threads.
 *
 * <h2>Vertex marker encoding</h2>
 * The terrain shader has no per-block attribute, so the animation data is carried in the vertex color alpha of
 * cutout geometry, where vanilla always writes 255. A marked vertex has {@code alpha = 255 - k} with
 * {@code k = category * 16 + weight} ({@code weight} 1..15 = amplitude). The bundled {@code terrain.vsh} decodes
 * {@code k}, displaces the vertex and restores alpha to 1. Water uses {@code k = 64 + weight} (see the water
 * package). Without the shader pack the slightly lower alpha is invisible on cutout geometry.
 */
public final class FoliageSettings {
	private static volatile FoliageSettings current = new FoliageSettings(ConfigManager.get(), 1.0f);
	private static volatile float stormFactor = 1.0f;

	public final boolean enabled;
	private final float[] strength = new float[FoliageCategory.values().length];

	private FoliageSettings(AetherConfig c, float storm) {
		boolean on = c.vegetationAnimation && CompatManager.terrainMarkersAllowed(c);
		this.enabled = on;
		float wind = c.windStrength * storm;
		strength[FoliageCategory.GRASS.ordinal()] = on && c.swayGrass ? c.grassStrength * wind : 0.0f;
		strength[FoliageCategory.LEAVES.ordinal()] = on && c.swayLeaves ? c.leavesStrength * wind : 0.0f;
		strength[FoliageCategory.FLOWER_CROP.ordinal()] = on && (c.swayFlowers || c.swayCrops)
				? Math.max(c.swayFlowers ? c.flowerStrength : 0.0f, c.swayCrops ? c.cropStrength : 0.0f) * wind : 0.0f;
		// Underwater motion is driven by currents, not wind, so storms do not affect it.
		strength[FoliageCategory.UNDERWATER.ordinal()] = on && c.swayUnderwater ? c.underwaterStrength : 0.0f;
	}

	public static FoliageSettings get() {
		return current;
	}

	public static void refresh() {
		current = new FoliageSettings(ConfigManager.get(), stormFactor);
	}

	public static void setStormFactor(float factor) {
		stormFactor = factor;
		refresh();
	}

	public float strength(FoliageCategory category) {
		return strength[category.ordinal()];
	}

	/**
	 * Returns the ARGB color with the marker for a vertex whose normalized sway weight is {@code weight}
	 * (0 = anchored, 1 = full amplitude), or the unchanged color if it should stay static.
	 */
	public int mark(int argb, FoliageCategory category, float weight) {
		float amount = weight * strength[category.ordinal()];
		int w = Math.round(Math.min(1.0f, amount * 0.5f) * 15.0f);

		if (w <= 0) {
			return argb;
		}

		int k = category.ordinal() * 16 + w;
		int alpha = 255 - k;
		return (argb & 0x00FFFFFF) | (alpha << 24);
	}
}
