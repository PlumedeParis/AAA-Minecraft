package com.aetherrealism.particles;

import com.aetherrealism.atmosphere.AtmosphereController;
import com.aetherrealism.compat.CompatManager;
import com.aetherrealism.config.AetherConfig;
import com.aetherrealism.config.ConfigManager;
import com.aetherrealism.vegetation.WindSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Environmental particles: falling leaves, fireflies, sunlit dust, pollen, cave dust, windblown debris, extra
 * smoke and embers around fire, sparkles near glowing blocks, and water droplets after swimming. Every spawn is
 * distance-limited, frustum-tested and counted against the shared {@link ParticleBudget}. Only vanilla particle
 * types are used, so resource packs re-texture them and no registry entries are added.
 */
public final class AmbientParticleSystem {
	public static final AmbientParticleSystem INSTANCE = new AmbientParticleSystem();

	private static final Set<String> FIRE_BLOCKS = Set.of("campfire", "soul_campfire", "fire", "soul_fire", "torch",
			"wall_torch", "soul_torch", "soul_wall_torch", "lantern", "soul_lantern", "lava", "magma_block", "furnace",
			"blast_furnace", "smoker", "copper_torch", "copper_wall_torch");
	private static final Set<String> GLOW_BLOCKS = Set.of("amethyst_cluster", "large_amethyst_bud", "glowstone",
			"sea_lantern", "shroomlight", "end_rod", "beacon", "glow_lichen", "crying_obsidian", "respawn_anchor",
			"enchanting_table", "ochre_froglight", "verdant_froglight", "pearlescent_froglight", "sculk_catalyst",
			"budding_amethyst", "conduit", "end_portal_frame");

	private static final DustParticleOptions SUN_DUST = new DustParticleOptions(0xFFF4E2, 0.45f);
	private static final DustParticleOptions POLLEN = new DustParticleOptions(0xF2D45C, 0.35f);
	private static final DustParticleOptions CAVE_DUST = new DustParticleOptions(0x9A9184, 0.4f);
	private static final DustParticleOptions DEBRIS_DIRT = new DustParticleOptions(0x7A5C3A, 0.6f);
	private static final DustParticleOptions DEBRIS_GRASS = new DustParticleOptions(0x5E8C31, 0.55f);

	private boolean wasInWater;
	private int dripTicks;
	private int tickCount;

	private AmbientParticleSystem() {
	}

	public void reset() {
		ParticleBudget.reset();
		wasInWater = false;
		dripTicks = 0;
	}

	public void tick(Minecraft client) {
		ParticleBudget.beginTick();
		AetherConfig c = ConfigManager.get();

		if (!c.masterEnabled || CompatManager.suspendedByShaderPack() || client.isPaused()) {
			return;
		}

		float density = ParticleBudget.densityScale(client, c);

		if (density <= 0.0f) {
			return;
		}

		tickCount++;
		ClientLevel level = client.level;
		Camera camera = client.gameRenderer.getMainCamera();
		RandomSource random = level.getRandom();
		AtmosphereController.Dimension dim = AtmosphereController.INSTANCE.dimension();
		float daylight = AtmosphereController.daylight(camera, 1.0f);
		float rain = level.getRainLevel(1.0f);
		Vec3 cam = camera.position();
		int radius = c.particleDistance;
		WindSystem wind = WindSystem.INSTANCE;

		// Sample random blocks around the camera; each sample may trigger at most one effect.
		int samples = Math.round(40 * density);

		for (int i = 0; i < samples && ParticleBudget.hasRoom(c); i++) {
			int x = (int) Math.floor(cam.x) + random.nextInt(radius * 2 + 1) - radius;
			int y = (int) Math.floor(cam.y) + random.nextInt(radius + 1) - radius / 2;
			int z = (int) Math.floor(cam.z) + random.nextInt(radius * 2 + 1) - radius;
			BlockPos pos = new BlockPos(x, y, z);

			if (!level.isLoaded(pos)) {
				continue;
			}

			BlockState state = level.getBlockState(pos);
			Block block = state.getBlock();

			if (c.fallingLeaves && block instanceof LeavesBlock && random.nextFloat() < 0.08f + wind.strength() * 0.05f) {
				BlockPos below = pos.below();

				if (level.getBlockState(below).isAir() && ParticleBudget.visible(camera, c, x + 0.5, y, z + 0.5)) {
					int color = client.getBlockColors().getColor(state, level, pos, 0);
					String id = BuiltInRegistries.BLOCK.getKey(block).getPath();

					if (id.equals("cherry_leaves")) {
						ParticleBudget.spawn(client, c, ParticleTypes.CHERRY_LEAVES, x + random.nextDouble(), y - 0.05, z + random.nextDouble(), 0, 0, 0);
					} else if (id.equals("pale_oak_leaves")) {
						ParticleBudget.spawn(client, c, ParticleTypes.PALE_OAK_LEAVES, x + random.nextDouble(), y - 0.05, z + random.nextDouble(), 0, 0, 0);
					} else {
						int tint = color == -1 ? 0x48B518 : color;
						ParticleBudget.spawn(client, c, ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF000000 | tint),
								x + random.nextDouble(), y - 0.05, z + random.nextDouble(), 0, 0, 0);
					}
				}

				continue;
			}

			String path = null;

			if (c.enhancedFire || c.glowParticles) {
				path = BuiltInRegistries.BLOCK.getKey(block).getPath();
			}

			if (c.enhancedFire && path != null && FIRE_BLOCKS.contains(path) && random.nextFloat() < 0.35f) {
				spawnFire(client, c, path, x, y, z, random, wind);
				continue;
			}

			if (c.glowParticles && path != null && GLOW_BLOCKS.contains(path) && random.nextFloat() < 0.25f
					&& ParticleBudget.visible(camera, c, x + 0.5, y + 0.5, z + 0.5)) {
				double px = x + 0.5 + (random.nextDouble() - 0.5) * 1.6;
				double py = y + 0.5 + (random.nextDouble() - 0.5) * 1.6;
				double pz = z + 0.5 + (random.nextDouble() - 0.5) * 1.6;
				ParticleBudget.spawn(client, c, random.nextBoolean() ? ParticleTypes.END_ROD : ParticleTypes.GLOW,
						px, py, pz, 0, 0.005, 0);
			}
		}

		if (dim == AtmosphereController.Dimension.OVERWORLD) {
			spawnOpenAir(client, c, level, camera, random, density, daylight, rain, wind);
		}

		if (c.caveDust && random.nextFloat() < 0.6f * density) {
			BlockPos p = randomAirNear(level, cam, random, 10);

			if (p != null && level.getBrightness(LightLayer.SKY, p) <= 2 && p.getY() < level.getSeaLevel()
					&& ParticleBudget.visible(camera, c, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)) {
				ParticleBudget.spawn(client, c, CAVE_DUST, p.getX() + random.nextDouble(), p.getY() + random.nextDouble(),
						p.getZ() + random.nextDouble(), 0, -0.002, 0);
			}
		}

		tickDroplets(client, c, random);
	}

	private void spawnOpenAir(Minecraft client, AetherConfig c, ClientLevel level, Camera camera, RandomSource random,
			float density, float daylight, float rain, WindSystem wind) {
		Vec3 cam = camera.position();

		// Fireflies: calm, dry nights near vegetation or water.
		if (c.fireflies && daylight < -0.05f && rain < 0.1f && random.nextFloat() < 0.5f * density) {
			BlockPos p = randomAirNear(level, cam, random, 14);

			if (p != null && level.getBrightness(LightLayer.SKY, p) >= 8) {
				BlockState below = level.getBlockState(p.below());
				String id = BuiltInRegistries.BLOCK.getKey(below.getBlock()).getPath();
				boolean nearPlants = id.contains("grass") || id.contains("fern") || id.contains("flower") || id.contains("bush")
						|| level.getFluidState(p.below()).is(FluidTags.WATER);

				if (nearPlants && ParticleBudget.visible(camera, c, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)) {
					ParticleBudget.spawn(client, c, ParticleTypes.FIREFLY, p.getX() + random.nextDouble(),
							p.getY() + 0.3 + random.nextDouble(), p.getZ() + random.nextDouble(), 0, 0, 0);
				}
			}
		}

		// Dust motes in sunlight and pollen drifting on the wind.
		if (daylight > 0.15f && rain < 0.2f) {
			if (c.dust && random.nextFloat() < 0.6f * density) {
				spawnDrifting(client, c, level, camera, random, SUN_DUST, 8, wind, 0.02);
			}

			if (c.pollen && random.nextFloat() < 0.5f * density) {
				spawnDrifting(client, c, level, camera, random, POLLEN, 12, wind, 0.04);
			}
		}

		// Windblown debris when it is genuinely windy.
		if (c.windDebris && wind.strength() > 0.9f && random.nextFloat() < 0.5f * density * (wind.strength() - 0.8f)) {
			spawnDrifting(client, c, level, camera, random, random.nextBoolean() ? DEBRIS_DIRT : DEBRIS_GRASS, 14, wind, 0.25);
		}
	}

	private void spawnDrifting(Minecraft client, AetherConfig c, ClientLevel level, Camera camera, RandomSource random,
			DustParticleOptions options, int radius, WindSystem wind, double windFactor) {
		BlockPos p = randomAirNear(level, camera.position(), random, radius);

		if (p == null || level.getBrightness(LightLayer.SKY, p) < 12) {
			return;
		}

		double px = p.getX() + random.nextDouble();
		double py = p.getY() + random.nextDouble();
		double pz = p.getZ() + random.nextDouble();

		if (ParticleBudget.visible(camera, c, px, py, pz)) {
			ParticleBudget.spawn(client, c, options, px, py, pz, wind.windX() * windFactor, 0.004, wind.windZ() * windFactor);
		}
	}

	private void spawnFire(Minecraft client, AetherConfig c, String path, int x, int y, int z, RandomSource random, WindSystem wind) {
		double cx = x + 0.5;
		double cz = z + 0.5;
		double drift = 0.012;

		if (path.contains("campfire")) {
			ParticleBudget.spawn(client, c, ParticleTypes.CAMPFIRE_COSY_SMOKE, cx + random.nextGaussian() * 0.15, y + 0.8,
					cz + random.nextGaussian() * 0.15, wind.windX() * drift, 0.06, wind.windZ() * drift);

			if (random.nextFloat() < 0.4f) {
				ParticleBudget.spawn(client, c, ParticleTypes.LAVA, cx, y + 0.5, cz, 0, 0, 0);
			}
		} else if (path.contains("torch")) {
			// Flicker: an occasional extra flame and a thin smoke wisp.
			ParticleBudget.spawn(client, c, path.contains("soul") ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMALL_FLAME,
					cx + random.nextGaussian() * 0.03, y + 0.72, cz + random.nextGaussian() * 0.03, 0, 0.004, 0);

			if (random.nextFloat() < 0.3f) {
				ParticleBudget.spawn(client, c, ParticleTypes.SMOKE, cx, y + 0.85, cz, wind.windX() * drift * 0.5, 0.01, wind.windZ() * drift * 0.5);
			}
		} else if (path.equals("lava")) {
			if (random.nextFloat() < 0.15f) {
				ParticleBudget.spawn(client, c, ParticleTypes.SMOKE, cx + random.nextGaussian() * 0.4, y + 1.0,
						cz + random.nextGaussian() * 0.4, wind.windX() * drift, 0.03, wind.windZ() * drift);
			}
		} else if (path.contains("fire")) {
			ParticleBudget.spawn(client, c, ParticleTypes.LARGE_SMOKE, cx + random.nextGaussian() * 0.3, y + 0.9,
					cz + random.nextGaussian() * 0.3, wind.windX() * drift, 0.04, wind.windZ() * drift);
		} else if (path.contains("lantern")) {
			if (random.nextFloat() < 0.3f) {
				ParticleBudget.spawn(client, c, ParticleTypes.SMALL_FLAME, cx, y + 0.45, cz, 0, 0.002, 0);
			}
		} else if (path.equals("magma_block")) {
			ParticleBudget.spawn(client, c, ParticleTypes.WHITE_ASH, cx + random.nextGaussian() * 0.4, y + 1.05,
					cz + random.nextGaussian() * 0.4, 0, 0.01, 0);
		}
	}

	/** Water drips from the player's head and clothes for a few seconds after leaving water. */
	private void tickDroplets(Minecraft client, AetherConfig c, RandomSource random) {
		if (!c.waterDroplets || client.player == null) {
			return;
		}

		boolean inWater = client.player.isInWater();

		if (wasInWater && !inWater) {
			dripTicks = 80;
		}

		wasInWater = inWater;

		if (dripTicks > 0) {
			dripTicks--;

			if (random.nextFloat() < dripTicks / 80.0f) {
				var p = client.player;
				ParticleBudget.spawn(client, c, ParticleTypes.DRIPPING_WATER,
						p.getX() + random.nextGaussian() * 0.25, p.getY() + p.getBbHeight() * random.nextFloat(),
						p.getZ() + random.nextGaussian() * 0.25, 0, 0, 0);
			}
		}
	}

	static BlockPos randomAirNear(ClientLevel level, Vec3 cam, RandomSource random, int radius) {
		int x = (int) Math.floor(cam.x) + random.nextInt(radius * 2 + 1) - radius;
		int y = (int) Math.floor(cam.y) + random.nextInt(radius + 1) - radius / 3;
		int z = (int) Math.floor(cam.z) + random.nextInt(radius * 2 + 1) - radius;
		BlockPos pos = new BlockPos(x, y, z);

		if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
			return null;
		}

		return pos;
	}
}
