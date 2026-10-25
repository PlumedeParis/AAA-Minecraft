package com.aetherrealism.compat;

import com.aetherrealism.AetherRealismClient;
import com.aetherrealism.config.AetherConfig;
import com.aetherrealism.config.ConfigManager;
import com.aetherrealism.render.TerrainRebuildScheduler;
import com.aetherrealism.vegetation.FoliageSettings;
import com.aetherrealism.water.WaterRenderHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;

/**
 * Detects other rendering mods and decides which of our features may run alongside them.
 *
 * <ul>
 *     <li>Sodium replaces terrain meshing and terrain shaders, so the vertex-marker shader layer (foliage sway,
 *     water waves) cannot work there and is switched off. Dynamic block lighting depends on vanilla's light
 *     lookup and is reported as "may not apply to terrain" under Sodium.</li>
 *     <li>An active Iris shader pack replaces the whole world pipeline; our post-processing, fog edits and shader
 *     layer are suspended while one is in use (configurable).</li>
 * </ul>
 */
public final class CompatManager {
	public static final String SHADER_PACK_ID = AetherRealismClient.MOD_ID + ":aether_shaders";

	private static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");
	private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");

	private static Method irisGetInstance;
	private static Method irisIsShaderPackInUse;
	private static boolean irisReflectionFailed;

	private static boolean shaderPackInUse;
	private static boolean shaderLayerPackSelected = true;

	private CompatManager() {
	}

	public static boolean sodiumLoaded() {
		return SODIUM;
	}

	public static boolean irisLoaded() {
		return IRIS;
	}

	/** True while an Iris shader pack is active (refreshed once per client tick). */
	public static boolean irisShaderPackInUse() {
		return shaderPackInUse;
	}

	/** True if the bundled "Aether Realism Shaders" resource pack is enabled. */
	public static boolean shaderLayerPackSelected() {
		return shaderLayerPackSelected;
	}

	/** Whether our own world-rendering changes should be suspended because a shader pack took over. */
	public static boolean suspendedByShaderPack() {
		return shaderPackInUse && ConfigManager.get().disableWithShaderPacks;
	}

	/** Whether terrain should carry the vertex markers interpreted by our terrain shader. */
	public static boolean terrainMarkersAllowed(AetherConfig c) {
		return c.masterEnabled && c.shaderLayer && !SODIUM && !suspendedByShaderPack() && shaderLayerPackSelected;
	}

	public static void tick(Minecraft client) {
		boolean previousPack = shaderPackInUse;
		boolean previousLayer = shaderLayerPackSelected;
		shaderPackInUse = queryIris();

		try {
			shaderLayerPackSelected = client.getResourcePackRepository().getSelectedIds().contains(SHADER_PACK_ID);
		} catch (Exception e) {
			shaderLayerPackSelected = true;
		}

		if (previousPack != shaderPackInUse || previousLayer != shaderLayerPackSelected) {
			// Terrain markers depend on both; re-mesh so they appear or disappear.
			FoliageSettings.refresh();
			WaterRenderHandler.refresh();

			if (client.level != null) {
				TerrainRebuildScheduler.requestFullRebuild();
			}
		}
	}

	private static boolean queryIris() {
		if (!IRIS || irisReflectionFailed) {
			return false;
		}

		try {
			if (irisGetInstance == null) {
				Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				irisGetInstance = api.getMethod("getInstance");
				irisIsShaderPackInUse = api.getMethod("isShaderPackInUse");
			}

			Object instance = irisGetInstance.invoke(null);
			return (Boolean) irisIsShaderPackInUse.invoke(instance);
		} catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
			irisReflectionFailed = true;
			AetherRealismClient.LOGGER.warn("Iris is installed but its API could not be queried; assuming no shader pack", e);
			return false;
		}
	}

	public static String describe() {
		StringBuilder sb = new StringBuilder();
		sb.append("Sodium: ").append(SODIUM ? "detected (terrain shader layer disabled)" : "not installed");
		sb.append(" | Iris: ").append(IRIS ? (shaderPackInUse ? "shader pack active" : "installed, no shader pack") : "not installed");
		return sb.toString();
	}
}
