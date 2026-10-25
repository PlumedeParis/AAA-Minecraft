package com.aetherrealism.vegetation;

import com.aetherrealism.config.AetherConfig;
import com.aetherrealism.config.ConfigManager;
import com.aetherrealism.render.TerrainRebuildScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * CPU-side wind model shared by particles and weather. Direction and strength wander slowly using layered
 * sines, gusts rise and fall smoothly, and storms strengthen everything. The terrain shader runs an equivalent
 * model on the GPU; storm strength reaches it by re-meshing nearby terrain when the storm level changes.
 */
public final class WindSystem {
	public static final WindSystem INSTANCE = new WindSystem();

	private double time;
	private float direction;
	private float strength = 0.4f;
	private float gust;
	private int bakedStormLevel = -1;

	private WindSystem() {
	}

	public void reset() {
		time = 0;
		gust = 0;
		bakedStormLevel = -1;
		FoliageSettings.setStormFactor(1.0f);
	}

	public void tick(Minecraft client) {
		AetherConfig c = ConfigManager.get();
		ClientLevel level = client.level;
		time += 0.05 * c.windSpeed;

		float rain = level.getRainLevel(1.0f);
		float thunder = level.getThunderLevel(1.0f);
		float storm = rain * 0.6f + thunder * 0.9f;

		direction = (float) (Math.sin(time * 0.013) * 1.6 + Math.sin(time * 0.0047 + 1.3) * 2.2);
		float base = 0.35f + 0.15f * (float) Math.sin(time * 0.021);
		float targetGust = 0.0f;

		if (c.windGusts) {
			double g = Math.sin(time * 0.31) * Math.sin(time * 0.077 + 2.0);
			targetGust = (float) Math.max(0.0, g) * (0.5f + storm);
		}

		gust = Mth.lerp(0.08f, gust, targetGust);
		strength = Mth.clamp((base + gust + storm * 0.8f) * c.windStrength, 0.0f, 3.0f);

		// Bake storm strength into foliage markers in three coarse levels so storms visibly whip vegetation.
		int stormLevel = !c.stormWindResponse ? 0 : thunder > 0.5f ? 2 : rain > 0.5f ? 1 : 0;

		if (stormLevel != bakedStormLevel) {
			boolean first = bakedStormLevel < 0;
			bakedStormLevel = stormLevel;
			FoliageSettings.setStormFactor(1.0f + stormLevel * 0.45f);

			if (!first && FoliageSettings.get().enabled) {
				TerrainRebuildScheduler.requestNearbyRebuild(client, Math.min(12, client.options.getEffectiveRenderDistance()));
			}
		}
	}

	/** Horizontal wind direction in radians. */
	public float direction() {
		return direction;
	}

	/** Current wind strength, roughly 0 (calm) .. 3 (severe storm). */
	public float strength() {
		return strength;
	}

	public float windX() {
		return Mth.cos(direction) * strength;
	}

	public float windZ() {
		return Mth.sin(direction) * strength;
	}
}
