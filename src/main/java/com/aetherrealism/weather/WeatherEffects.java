package com.aetherrealism.weather;

import com.aetherrealism.atmosphere.AtmosphereController;
import com.aetherrealism.compat.CompatManager;
import com.aetherrealism.config.AetherConfig;
import com.aetherrealism.config.ConfigManager;
import com.aetherrealism.particles.ParticleBudget;
import com.aetherrealism.vegetation.WindSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Weather additions on top of vanilla rain and snow (which keep working unchanged): splashes where drops land,
 * ripples on water, drips from leaf canopies, wind-driven snowflakes and blizzard flurries, and a soft
 * rain-on-leaves ambience. Fog, sky darkening and lightning flashes are handled by the atmosphere and
 * post-processing modules.
 */
public final class WeatherEffects {
	public static final WeatherEffects INSTANCE = new WeatherEffects();

	private int canopySoundCooldown;

	private WeatherEffects() {
	}

	public void reset() {
		canopySoundCooldown = 0;
	}

	public void tick(Minecraft client) {
		AetherConfig c = ConfigManager.get();
		ClientLevel level = client.level;

		if (!c.masterEnabled || CompatManager.suspendedByShaderPack() || client.isPaused()
				|| AtmosphereController.INSTANCE.dimension() != AtmosphereController.Dimension.OVERWORLD) {
			return;
		}

		float rain = level.getRainLevel(1.0f);

		if (rain < 0.05f) {
			return;
		}

		float density = ParticleBudget.densityScale(client, c);

		if (density <= 0.0f) {
			return;
		}

		Camera camera = client.gameRenderer.getMainCamera();
		RandomSource random = level.getRandom();
		Vec3 cam = camera.position();
		int radius = c.rainEffectDistance;
		boolean snowing = isSnowing(level, BlockPos.containing(cam));
		WindSystem wind = WindSystem.INSTANCE;

		if (snowing) {
			if (c.enhancedSnow) {
				spawnSnow(client, c, level, camera, random, rain * density * c.snowDensity, wind);
			}

			return;
		}

		if (c.enhancedRain && c.rainSplashes) {
			int count = Math.round(14 * rain * c.rainSplashDensity * density);

			for (int i = 0; i < count && ParticleBudget.hasRoom(c); i++) {
				int x = (int) Math.floor(cam.x) + random.nextInt(radius * 2 + 1) - radius;
				int z = (int) Math.floor(cam.z) + random.nextInt(radius * 2 + 1) - radius;
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));

				if (Math.abs(top.getY() - cam.y) > radius || isSnowing(level, top)) {
					continue;
				}

				double px = x + random.nextDouble();
				double pz = z + random.nextDouble();
				double py = top.getY() + 0.02;

				if (!ParticleBudget.visible(camera, c, px, py, pz)) {
					continue;
				}

				BlockPos surface = top.below();

				if (level.getFluidState(surface).is(FluidTags.WATER)) {
					py = surface.getY() + 0.9;

					if (c.waterRipples) {
						// Rings on the surface: a splash plus a short-lived bubble pop.
						ParticleBudget.spawn(client, c, ParticleTypes.SPLASH, px, py, pz, 0, 0, 0);
						ParticleBudget.spawn(client, c, ParticleTypes.BUBBLE_POP, px, py + 0.05, pz, 0, 0, 0);
					}
				} else {
					BlockState landed = level.getBlockState(surface);
					ParticleBudget.spawn(client, c, random.nextFloat() < 0.6f ? ParticleTypes.RAIN : ParticleTypes.SPLASH,
							px, py + landed.getShape(level, surface).max(net.minecraft.core.Direction.Axis.Y) - 1.0 + 0.02, pz, 0, 0, 0);
				}
			}
		}

		if (c.enhancedRain) {
			spawnCanopyDrips(client, c, level, camera, random, rain * density);
		}
	}

	private boolean isSnowing(ClientLevel level, BlockPos pos) {
		// Snow falls where the biome is cold at that height; vanilla's own check lives on Biome.
		return level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel());
	}

	private void spawnSnow(Minecraft client, AetherConfig c, ClientLevel level, Camera camera, RandomSource random,
			float amount, WindSystem wind) {
		Vec3 cam = camera.position();
		int count = Math.round(10 * amount * (1.0f + wind.strength() * 0.5f));
		double windScale = 0.06;

		for (int i = 0; i < count && ParticleBudget.hasRoom(c); i++) {
			double px = cam.x + (random.nextDouble() - 0.5) * 24;
			double py = cam.y + random.nextDouble() * 10 - 2;
			double pz = cam.z + (random.nextDouble() - 0.5) * 24;
			BlockPos p = BlockPos.containing(px, py, pz);

			if (!level.getBlockState(p).isAir() || level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, p).getY() > py) {
				continue;
			}

			if (ParticleBudget.visible(camera, c, px, py, pz)) {
				ParticleBudget.spawn(client, c, ParticleTypes.SNOWFLAKE, px, py, pz,
						wind.windX() * windScale, -0.04 - random.nextDouble() * 0.03, wind.windZ() * windScale);
			}
		}
	}

	/** Water dripping off leaves above the player, plus a muffled rain-on-leaves sound under dense canopy. */
	private void spawnCanopyDrips(Minecraft client, AetherConfig c, ClientLevel level, Camera camera, RandomSource random, float amount) {
		Vec3 cam = camera.position();
		int leavesAbove = 0;

		for (int i = 0; i < Math.round(8 * amount); i++) {
			int x = (int) Math.floor(cam.x) + random.nextInt(13) - 6;
			int z = (int) Math.floor(cam.z) + random.nextInt(13) - 6;
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z)).below();
			BlockState state = level.getBlockState(top);

			if (state.getBlock() instanceof LeavesBlock && top.getY() > cam.y) {
				leavesAbove++;
				BlockPos under = top.below();

				if (level.getBlockState(under).isAir() && ParticleBudget.visible(camera, c, x + 0.5, top.getY(), z + 0.5)) {
					ParticleBudget.spawn(client, c, ParticleTypes.DRIPPING_WATER, x + random.nextDouble(), top.getY() - 0.05,
							z + random.nextDouble(), 0, 0, 0);
				}
			}
		}

		if (canopySoundCooldown > 0) {
			canopySoundCooldown--;
		} else if (leavesAbove >= 3 && amount > 0.3f) {
			canopySoundCooldown = 30 + random.nextInt(30);
			level.playLocalSound(cam.x, cam.y + 3, cam.z, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER,
					0.08f * amount, 0.6f + random.nextFloat() * 0.1f, false);
		}
	}
}
