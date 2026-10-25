package com.aetherrealism.vegetation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Animation categories. The ordinal is encoded into terrain vertices (see {@link FoliageSettings}) and must match
 * the category decoding in the bundled {@code terrain.vsh}.
 */
public enum FoliageCategory {
	/** Grass, ferns, bushes: tops bend with the wind. */
	GRASS,
	/** Leaves: whole quads flutter slightly. */
	LEAVES,
	/** Flowers, crops, saplings: stiffer stems, tops sway. */
	FLOWER_CROP,
	/** Seagrass, kelp, coral fans: slow current-driven motion. */
	UNDERWATER;

	private static final Set<String> GRASS_IDS = Set.of(
			"short_grass", "tall_grass", "fern", "large_fern", "bush", "dead_bush", "short_dry_grass", "tall_dry_grass",
			"firefly_bush", "sweet_berry_bush", "nether_sprouts", "warped_roots", "crimson_roots", "hanging_roots",
			"leaf_litter", "pink_petals", "wildflowers", "sugar_cane", "small_dripleaf", "azalea", "flowering_azalea");
	private static final Set<String> UNDERWATER_IDS = Set.of(
			"seagrass", "tall_seagrass", "kelp", "kelp_plant");

	/** Which category a block state belongs to, or null if it should not move. */
	public static FoliageCategory of(BlockState state) {
		Block block = state.getBlock();
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath();

		if (block instanceof LeavesBlock || path.endsWith("_leaves")) {
			return LEAVES;
		}

		if (UNDERWATER_IDS.contains(path) || path.endsWith("_coral_fan") || path.endsWith("_coral")) {
			return path.startsWith("dead_") ? null : UNDERWATER;
		}

		if (GRASS_IDS.contains(path)) {
			return GRASS;
		}

		if (block instanceof FlowerBlock || block instanceof CropBlock || block instanceof SaplingBlock
				|| path.endsWith("_tulip") || path.endsWith("_sapling") || path.endsWith("_propagule")) {
			return FLOWER_CROP;
		}

		if (block instanceof DoublePlantBlock) {
			// Sunflowers, lilacs, peonies, rose bushes, pitcher plants...
			return FLOWER_CROP;
		}

		return null;
	}
}
