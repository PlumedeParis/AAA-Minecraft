package com.aetherrealism.vegetation;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadTransform;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Delegating block model that tags each emitted vertex with its sway weight. The weight depends on the vertex
 * height inside the block so stems stay anchored to the ground while tops move; double-tall plants continue the
 * gradient across both halves. Leaves get a uniform weight (the shader adds per-vertex phase noise).
 */
public final class SwayBlockStateModel extends WrapperBlockStateModel {
	private final QuadTransform transform;

	public SwayBlockStateModel(BlockStateModel wrapped, FoliageCategory category, boolean upperHalf, boolean lowerHalf) {
		super(wrapped);
		this.transform = quad -> mark(quad, category, upperHalf, lowerHalf);
	}

	private static boolean mark(MutableQuadView quad, FoliageCategory category, boolean upperHalf, boolean lowerHalf) {
		FoliageSettings settings = FoliageSettings.get();

		if (!settings.enabled || settings.strength(category) <= 0.0f) {
			return true;
		}

		for (int i = 0; i < 4; i++) {
			float y = Math.max(0.0f, Math.min(1.0f, quad.y(i)));
			float weight = switch (category) {
				case LEAVES -> 0.55f;
				case UNDERWATER -> upperHalf ? 0.5f + 0.5f * y : (lowerHalf ? 0.5f * y : y);
				default -> upperHalf ? 0.5f + 0.5f * y : (lowerHalf ? 0.5f * y : y);
			};
			quad.color(i, settings.mark(quad.color(i), category, weight));
		}

		return true;
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter blockView, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
		if (!FoliageSettings.get().enabled) {
			super.emitQuads(emitter, blockView, pos, state, random, cullTest);
			return;
		}

		emitter.pushTransform(transform);

		try {
			super.emitQuads(emitter, blockView, pos, state, random, cullTest);
		} finally {
			emitter.popTransform();
		}
	}
}
