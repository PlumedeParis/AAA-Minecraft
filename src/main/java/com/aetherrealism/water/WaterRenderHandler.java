package com.aetherrealism.water;

import com.aetherrealism.AetherRealismClient;
import com.aetherrealism.compat.CompatManager;
import com.aetherrealism.config.AetherConfig;
import com.aetherrealism.config.ConfigManager;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * Wraps the registered water renderer so water vertices carry a marker for the bundled terrain shader: the
 * shader animates the surface with waves and adds ripple shimmer. Rendering itself is still done by the
 * original handler (vanilla or another mod's), so textures, tint and resource packs are untouched.
 */
public final class WaterRenderHandler implements FluidRenderHandler {
	private static volatile int waveLevel;
	private static volatile int markerBase = 64;
	private static volatile boolean marking;

	private final FluidRenderHandler delegate;
	private final ThreadLocal<WaterMarkingVertexConsumer> consumers = ThreadLocal.withInitial(WaterMarkingVertexConsumer::new);

	private WaterRenderHandler(FluidRenderHandler delegate) {
		this.delegate = delegate;
	}

	public static void install() {
		FluidRenderHandler original = FluidRenderHandlerRegistry.INSTANCE.get(Fluids.WATER);

		if (original == null) {
			AetherRealismClient.LOGGER.warn("No water render handler registered; water waves are unavailable");
			return;
		}

		if (original instanceof WaterRenderHandler) {
			return;
		}

		FluidRenderHandlerRegistry.INSTANCE.register(Fluids.WATER, Fluids.FLOWING_WATER, new WaterRenderHandler(original));
		refresh();
	}

	public static void refresh() {
		AetherConfig c = ConfigManager.get();
		marking = CompatManager.terrainMarkersAllowed(c) && (c.waterWaves || c.waterShimmer);
		waveLevel = c.waterWaves ? Math.round(Math.min(1.0f, c.waterWaveHeight * 0.5f) * 15.0f) : 0;
		markerBase = c.waterShimmer ? 64 : 80;
	}

	@Override
	public TextureAtlasSprite[] getFluidSprites(@Nullable BlockAndTintGetter view, @Nullable BlockPos pos, FluidState state) {
		return delegate.getFluidSprites(view, pos, state);
	}

	@Override
	public int getFluidColor(@Nullable BlockAndTintGetter view, @Nullable BlockPos pos, FluidState state) {
		return delegate.getFluidColor(view, pos, state);
	}

	@Override
	public void renderFluid(BlockPos pos, BlockAndTintGetter world, VertexConsumer vertexConsumer, BlockState blockState, FluidState fluidState) {
		if (!marking) {
			delegate.renderFluid(pos, world, vertexConsumer, blockState, fluidState);
			return;
		}

		WaterMarkingVertexConsumer marker = consumers.get();
		marker.begin(vertexConsumer, markerBase, waveLevel);

		try {
			delegate.renderFluid(pos, world, marker, blockState, fluidState);
		} finally {
			marker.end();
		}
	}

	@Override
	public void reloadTextures(TextureAtlas textureAtlas) {
		delegate.reloadTextures(textureAtlas);
	}
}
