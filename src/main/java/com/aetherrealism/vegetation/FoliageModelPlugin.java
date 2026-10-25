package com.aetherrealism.vegetation;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Wraps the baked models of plants, leaves and underwater vegetation so their quads get animation markers.
 * Uses Fabric's model-loading and renderer APIs, which Indigo (and Sodium's FRAPI support) honor.
 */
public final class FoliageModelPlugin implements ModelLoadingPlugin {
	@Override
	public void initialize(Context pluginContext) {
		pluginContext.modifyBlockModelAfterBake().register((model, context) -> {
			BlockState state = context.state();
			FoliageCategory category = FoliageCategory.of(state);

			if (category == null) {
				return model;
			}

			boolean upperHalf = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
					&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
			boolean lowerHalf = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && !upperHalf;
			return wrap(model, category, upperHalf, lowerHalf);
		});
	}

	private static BlockStateModel wrap(BlockStateModel model, FoliageCategory category, boolean upperHalf, boolean lowerHalf) {
		return new SwayBlockStateModel(model, category, upperHalf, lowerHalf);
	}
}
