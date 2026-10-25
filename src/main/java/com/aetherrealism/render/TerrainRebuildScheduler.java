package com.aetherrealism.render;

import com.aetherrealism.config.AetherConfig;
import com.aetherrealism.mixin.LevelRendererAccessor;
import com.aetherrealism.vegetation.FoliageSettings;
import com.aetherrealism.water.WaterRenderHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;

import java.util.ArrayDeque;
import java.util.Objects;

/**
 * Terrain meshes bake the foliage/water vertex markers, so changing those settings (or the storm wind level)
 * requires re-meshing. Settings changes trigger one full rebuild; storm transitions re-mesh the sections closest
 * to the player gradually so there is no frame hitch.
 */
public final class TerrainRebuildScheduler {
	private static final ArrayDeque<long[]> QUEUE = new ArrayDeque<>();
	private static final int SECTIONS_PER_TICK = 24;
	private static boolean fullRebuildPending;

	private TerrainRebuildScheduler() {
	}

	public static void onConfigChanged(AetherConfig previous, AetherConfig next) {
		FoliageSettings.refresh();
		WaterRenderHandler.refresh();

		if (!Objects.equals(markerKey(previous), markerKey(next))) {
			fullRebuildPending = true;
		}
	}

	/** Every setting that is baked into terrain vertices. */
	private static String markerKey(AetherConfig c) {
		return c.masterEnabled + "|" + c.shaderLayer + "|" + c.vegetationAnimation + "|" + c.swayGrass + c.swayLeaves
				+ c.swayFlowers + c.swayCrops + c.swayUnderwater + "|" + c.windStrength + "|" + c.grassStrength + "|"
				+ c.leavesStrength + "|" + c.flowerStrength + "|" + c.cropStrength + "|" + c.underwaterStrength + "|"
				+ c.waterWaves + "|" + c.waterWaveHeight + "|" + c.waterShimmer + "|" + c.stormWindResponse;
	}

	/** Called when the pack selection or a baked environmental factor (storm level) changed. */
	public static void requestFullRebuild() {
		fullRebuildPending = true;
	}

	/** Re-meshes the sections around the player, nearest first, a few per tick. */
	public static void requestNearbyRebuild(Minecraft client, int radiusChunks) {
		if (client.player == null) {
			return;
		}

		QUEUE.clear();
		SectionPos center = SectionPos.of(client.player.blockPosition());
		int minY = client.level.getMinSectionY();
		int maxY = client.level.getMaxSectionY();

		for (int r = 0; r <= radiusChunks; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}

					for (int sy = Math.max(minY, center.y() - 4); sy <= Math.min(maxY, center.y() + 4); sy++) {
						QUEUE.add(new long[]{center.x() + dx, sy, center.z() + dz});
					}
				}
			}
		}
	}

	public static void tick(Minecraft client) {
		if (fullRebuildPending) {
			fullRebuildPending = false;
			QUEUE.clear();
			client.levelRenderer.allChanged();
			return;
		}

		LevelRendererAccessor accessor = (LevelRendererAccessor) client.levelRenderer;

		for (int i = 0; i < SECTIONS_PER_TICK && !QUEUE.isEmpty(); i++) {
			long[] s = QUEUE.poll();
			accessor.aether$setSectionDirty((int) s[0], (int) s[1], (int) s[2], false);
		}
	}

	public static void reset() {
		QUEUE.clear();
		fullRebuildPending = false;
	}
}
