package com.aetherrealism;

import com.aetherrealism.atmosphere.AtmosphereController;
import com.aetherrealism.compat.CompatManager;
import com.aetherrealism.config.ConfigManager;
import com.aetherrealism.gui.AetherSettingsScreen;
import com.aetherrealism.lighting.DynamicLightManager;
import com.aetherrealism.particles.AmbientParticleSystem;
import com.aetherrealism.performance.PerformanceMonitor;
import com.aetherrealism.render.PostEffectManager;
import com.aetherrealism.render.TerrainRebuildScheduler;
import com.aetherrealism.vegetation.FoliageModelPlugin;
import com.aetherrealism.vegetation.WindSystem;
import com.aetherrealism.water.WaterRenderHandler;
import com.aetherrealism.weather.WeatherEffects;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint. Wires the config, GUI, key bindings and every effect module to Fabric's lifecycle events.
 * All features are client-side only; nothing is registered on the server or in synced registries.
 */
public final class AetherRealismClient implements ClientModInitializer {
	public static final String MOD_ID = "aether_realism";
	public static final Logger LOGGER = LoggerFactory.getLogger("Aether Realism");

	private static KeyMapping openSettingsKey;
	private static KeyMapping toggleEffectsKey;
	private static KeyMapping toggleMonitorKey;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		ConfigManager.load();

		ModContainer container = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();
		boolean packRegistered = ResourceLoader.registerBuiltinPack(id("aether_shaders"), container,
				Component.literal("Aether Realism Shaders"), PackActivationType.DEFAULT_ENABLED);

		if (!packRegistered) {
			LOGGER.warn("Could not register the built-in shader layer pack; foliage and water animation will be unavailable");
		}

		ModelLoadingPlugin.register(new FoliageModelPlugin());
		registerKeys();
		registerEvents();

		ConfigManager.addListener(TerrainRebuildScheduler::onConfigChanged);
		ConfigManager.addListener((previous, next) -> DynamicLightManager.INSTANCE.onConfigChanged(previous, next));

		LOGGER.info("Aether Realism initialized ({})", CompatManager.describe());
	}

	private static void registerKeys() {
		KeyMapping.Category category = KeyMapping.Category.register(id("main"));
		openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.aether_realism.open_settings",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F10, category));
		toggleEffectsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.aether_realism.toggle_effects",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, category));
		toggleMonitorKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.aether_realism.toggle_monitor",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
	}

	private static void registerEvents() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			PostEffectManager.INSTANCE.init(client);
			WaterRenderHandler.install();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> PostEffectManager.INSTANCE.close());

		ClientTickEvents.END_CLIENT_TICK.register(AetherRealismClient::onEndTick);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetWorldState());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetWorldState());

		HudElementRegistry.addLast(id("performance_monitor"), PerformanceMonitor.INSTANCE::renderHud);

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof VideoSettingsScreen) {
				Button button = Button.builder(Component.literal("Aether Realism..."),
								b -> client.setScreen(new AetherSettingsScreen(screen)))
						.bounds(scaledWidth - 126, 6, 120, 20)
						.tooltip(Tooltip.create(Component.literal("Open the Aether Realism visual settings")))
						.build();
				Screens.getButtons(screen).add(button);
			}
		});
	}

	private static void onEndTick(Minecraft client) {
		while (openSettingsKey.consumeClick()) {
			if (client.screen == null) {
				client.setScreen(new AetherSettingsScreen(null));
			}
		}

		while (toggleEffectsKey.consumeClick()) {
			ConfigManager.toggleMaster();
			showStatus(client, "Aether Realism effects " + (ConfigManager.get().masterEnabled ? "ON" : "OFF"));
		}

		while (toggleMonitorKey.consumeClick()) {
			var next = ConfigManager.get().copy();
			next.showPerformanceMonitor = !next.showPerformanceMonitor;
			ConfigManager.commit(next);
		}

		CompatManager.tick(client);
		PerformanceMonitor.INSTANCE.tick();

		if (client.level == null || client.player == null) {
			return;
		}

		try {
			WindSystem.INSTANCE.tick(client);
			AtmosphereController.INSTANCE.tick(client);
			DynamicLightManager.INSTANCE.tick(client);
			WeatherEffects.INSTANCE.tick(client);
			AmbientParticleSystem.INSTANCE.tick(client);
			TerrainRebuildScheduler.tick(client);
		} catch (Exception e) {
			// One bad tick must never crash the game; log it once per distinct message.
			ErrorReporter.report("tick", e);
		}
	}

	private static void showStatus(Minecraft client, String message) {
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal(message), true);
		}
	}

	/** Clears per-world state so nothing leaks between worlds or servers. */
	private static void resetWorldState() {
		DynamicLightManager.INSTANCE.reset();
		WeatherEffects.INSTANCE.reset();
		AmbientParticleSystem.INSTANCE.reset();
		AtmosphereController.INSTANCE.reset();
		WindSystem.INSTANCE.reset();
		TerrainRebuildScheduler.reset();
	}
}
