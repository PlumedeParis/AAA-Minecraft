package com.aetherrealism.atmosphere;

import com.aetherrealism.compat.CompatManager;
import com.aetherrealism.config.AetherConfig;
import com.aetherrealism.config.ConfigManager;
import com.aetherrealism.render.PostParams;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * The environment model behind fog, sky and post-processing. Each tick it samples the camera's surroundings
 * (dimension, biome family, altitude, sky exposure, weather, time of day, lightning) into smoothed factors;
 * per frame those factors shape vanilla's fog uniforms and the post-processing parameters.
 *
 * <p>All factors are eased exponentially, so walking from a plains into a swamp or into a cave fades the
 * atmosphere over a few seconds instead of switching abruptly.
 */
public final class AtmosphereController {
	public static final AtmosphereController INSTANCE = new AtmosphereController();

	public enum Dimension { OVERWORLD, NETHER, END, OTHER }

	private Dimension dimension = Dimension.OVERWORLD;
	// Smoothed 0..1 factors
	private float forest, swamp, ocean, mountain, plains, snowy;
	private float cave, rain, thunder, mist, valley;
	private float flash;
	private float flashTarget;
	private int flashFlickers;
	private final IntSet seenBolts = new IntOpenHashSet();
	private boolean initialized;

	// Final fog color of the last frame, reused by the post shaders.
	private float fogR = 0.7f, fogG = 0.8f, fogB = 1.0f;
	private float seaLevel = 63.0f;

	private AtmosphereController() {
	}

	public void reset() {
		initialized = false;
		seenBolts.clear();
		flash = 0;
		flashTarget = 0;
		flashFlickers = 0;
		cave = rain = thunder = mist = valley = 0;
	}

	public Dimension dimension() {
		return dimension;
	}

	public void tick(Minecraft client) {
		ClientLevel level = client.level;
		Camera camera = client.gameRenderer.getMainCamera();
		BlockPos pos = BlockPos.containing(camera.position());
		AetherConfig c = ConfigManager.get();

		if (level.dimension() == Level.OVERWORLD) {
			dimension = Dimension.OVERWORLD;
		} else if (level.dimension() == Level.NETHER) {
			dimension = Dimension.NETHER;
		} else if (level.dimension() == Level.END) {
			dimension = Dimension.END;
		} else {
			dimension = Dimension.OTHER;
		}

		seaLevel = level.getSeaLevel();
		float ease = initialized ? 0.04f : 1.0f;
		initialized = true;

		// Biome family
		String biome = biomePath(level.getBiome(pos));
		forest = approach(forest, has(biome, "forest", "jungle", "taiga", "grove", "birch", "cherry") ? 1 : 0, ease);
		swamp = approach(swamp, has(biome, "swamp", "mangrove") ? 1 : 0, ease);
		ocean = approach(ocean, has(biome, "ocean", "beach", "river") ? 1 : 0, ease);
		mountain = approach(mountain, has(biome, "peaks", "slopes", "windswept", "hills", "mountain", "stony") ? 1 : 0, ease);
		plains = approach(plains, has(biome, "plains", "savanna", "meadow", "desert") ? 1 : 0, ease);
		snowy = approach(snowy, has(biome, "snowy", "frozen", "ice", "grove", "jagged", "peaks") ? 1 : 0, ease);

		// Underground: little sky light and below the surface.
		int skyLight = level.getBrightness(LightLayer.SKY, pos);
		boolean underground = dimension == Dimension.OVERWORLD && skyLight <= 3 && pos.getY() < seaLevel + 8;
		cave = approach(cave, underground ? 1 : 0, ease * 1.5f);

		// Valleys: camera well below the surrounding terrain is mist-prone at dawn.
		valley = approach(valley, pos.getY() < seaLevel + 6 ? 1 : 0, ease);

		rain = approach(rain, level.getRainLevel(1.0f), ease * 2.0f);
		thunder = approach(thunder, level.getThunderLevel(1.0f), ease * 2.0f);

		// Morning mist: the first part of the day, fading as the sun climbs.
		float daylight = daylight(camera, 1.0f);
		float sunX = sunDirX(camera, 1.0f);
		boolean morning = sunX > 0.0f; // the sun rises in the east (+X)
		float mistTarget = morning ? Mth.clamp(1.0f - Math.abs(daylight) * 2.5f, 0.0f, 1.0f) : 0.0f;
		mist = approach(mist, mistTarget, ease);

		tickLightning(client, level, c);
	}

	private void tickLightning(Minecraft client, ClientLevel level, AetherConfig c) {
		if (c.lightningFlashes && dimension == Dimension.OVERWORLD) {
			for (Entity entity : level.entitiesForRendering()) {
				if (entity.getType() == EntityType.LIGHTNING_BOLT && seenBolts.add(entity.getId())) {
					double dist = Math.sqrt(entity.distanceToSqr(client.gameRenderer.getMainCamera().position()));
					float strength = (float) Mth.clamp(1.2 - dist / 400.0, 0.2, 1.0);
					flashTarget = Math.max(flashTarget, strength);
					flashFlickers = 2 + level.getRandom().nextInt(3);
				}
			}

			if (seenBolts.size() > 512) {
				seenBolts.clear();
			}
		}

		// Flicker: a few sharp peaks, then decay.
		if (flashFlickers > 0 && level.getRandom().nextFloat() < 0.45f) {
			flash = flashTarget * (0.6f + 0.4f * level.getRandom().nextFloat());
			flashFlickers--;
		} else {
			flash *= 0.55f;
			flashTarget *= 0.9f;
		}
	}

	private static float approach(float current, float target, float rate) {
		return current + (target - current) * Mth.clamp(rate, 0.0f, 1.0f);
	}

	private static boolean has(String s, String... needles) {
		for (String n : needles) {
			if (s.contains(n)) {
				return true;
			}
		}

		return false;
	}

	private static String biomePath(Holder<Biome> holder) {
		return holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
	}

	private static float sunAngleRadians(Camera camera, float partial) {
		Float angle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partial);
		return angle == null ? 0.0f : (float) Math.toRadians(angle);
	}

	/** Cosine of the sun's angle from the zenith: 1 at noon, 0 at the horizon, -1 at midnight. */
	public static float daylight(Camera camera, float partial) {
		return Mth.cos(sunAngleRadians(camera, partial));
	}

	private static float sunDirX(Camera camera, float partial) {
		return -Mth.sin(sunAngleRadians(camera, partial));
	}

	private boolean blinded(Minecraft client) {
		return client.player != null && (client.player.hasEffect(MobEffects.BLINDNESS) || client.player.hasEffect(MobEffects.DARKNESS));
	}

	/**
	 * Rewrites the arguments of {@code FogRenderer.updateBuffer}: 2 = fog color, 3/4 = environmental start/end,
	 * 5/6 = render-distance start/end.
	 */
	public void adjustFog(Args args) {
		AetherConfig c = ConfigManager.get();
		Minecraft client = Minecraft.getInstance();
		Vector4f color = args.get(2);
		float envStart = args.get(3);
		float envEnd = args.get(4);
		float rdStart = args.get(5);
		float rdEnd = args.get(6);

		if (color != null) {
			fogR = color.x;
			fogG = color.y;
			fogB = color.z;
		}

		if (!c.masterEnabled || CompatManager.suspendedByShaderPack() || client.level == null || rdEnd > 1.0e6f || blinded(client)) {
			return;
		}

		FogType fluid = client.gameRenderer.getMainCamera().getFluidInCamera();

		if (fluid == FogType.WATER) {
			if (c.underwaterEffects) {
				args.set(3, envStart * c.underwaterVisibility);
				args.set(4, envEnd * c.underwaterVisibility);
			}

			return;
		}

		if (fluid != FogType.NONE || !c.atmosphericFog) {
			return;
		}

		float density = densityFactor(c);

		if (density > 0.001f) {
			float visibility = Math.max(20.0f, rdEnd * 1.6f / (1.0f + 2.8f * density) * c.fogDistance);
			float newEnd = Math.min(envEnd, visibility);
			float newStart = Math.min(envStart, newEnd * (0.12f - 0.1f * Math.min(1.0f, density)));
			args.set(3, newStart);
			args.set(4, newEnd);
		}

		// Long, soft horizon gradient so terrain never pops out of a hard fog wall.
		float softStart = rdEnd - (rdEnd - rdStart) * (1.0f + 1.6f * c.fogDensity);
		args.set(5, Math.max(0.0f, Math.min(rdStart, softStart)));

		if (color != null && c.fogTint > 0.0f) {
			tintFog(color, c, client);
			fogR = color.x;
			fogG = color.y;
			fogB = color.z;
		}
	}

	/** Combined 0..~2 fog density from weather, biome, caves, mist and dimension. */
	private float densityFactor(AetherConfig c) {
		float d = 0.0f;

		switch (dimension) {
			case OVERWORLD -> {
				if (c.stormAtmosphere) {
					d += rain * 0.45f + thunder * 0.45f;

					if (c.cinematicStorms) {
						d += thunder * 0.4f;
					}
				}

				if (c.enhancedSnow) {
					d += rain * snowy * 0.6f * c.snowDensity; // blizzards
				}

				if (c.biomeFog) {
					d += forest * 0.22f * c.forestFog;
					d += swamp * 0.5f * c.swampFog;
					d += ocean * 0.15f * c.oceanFog;
					d += plains * 0.04f * c.plainsFog;
					d += mountain * 0.2f * c.mountainFog;
				}

				d += mist * (0.25f + valley * 0.35f);

				if (c.caveFog) {
					d += cave * 0.6f * c.caveFogDensity;
				}
			}
			case NETHER -> d += c.netherAtmosphere ? 0.15f : 0.0f;
			case END -> d += c.endAtmosphere ? 0.1f : 0.0f;
			default -> {
			}
		}

		return d * c.fogDensity;
	}

	private void tintFog(Vector4f color, AetherConfig c, Minecraft client) {
		if (dimension != Dimension.OVERWORLD) {
			return;
		}

		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float day = daylight(client.gameRenderer.getMainCamera(), partial);
		float lum = color.x * 0.3f + color.y * 0.59f + color.z * 0.11f;
		float r = color.x, g = color.y, b = color.z;
		float amount = c.fogTint;

		// Storms: desaturate and darken.
		float storm = Math.max(rain * 0.6f, thunder);
		r = Mth.lerp(storm * 0.6f * amount, r, lum * 0.8f);
		g = Mth.lerp(storm * 0.6f * amount, g, lum * 0.82f);
		b = Mth.lerp(storm * 0.6f * amount, b, lum * 0.9f);

		// Golden hour: warm the fog while the sun is near the horizon.
		float golden = Mth.clamp(1.0f - Math.abs(day) * 3.0f, 0.0f, 1.0f) * (1.0f - storm) * (c.sunsetGlow ? c.sunsetIntensity : 0.0f);
		r = Mth.lerp(golden * 0.35f * amount, r, Math.min(1.0f, lum * 1.6f + 0.1f));
		g = Mth.lerp(golden * 0.35f * amount, g, lum * 1.05f);
		b = Mth.lerp(golden * 0.35f * amount, b, lum * 0.7f);

		// Caves: fade toward a dark, slightly blue haze.
		r = Mth.lerp(cave * 0.7f * amount, r, 0.035f);
		g = Mth.lerp(cave * 0.7f * amount, g, 0.04f);
		b = Mth.lerp(cave * 0.7f * amount, b, 0.055f);

		// Flash briefly brightens the fog too.
		float f = flash * c.lightningFlashIntensity * 0.5f;
		color.set(Math.min(1.0f, r + f), Math.min(1.0f, g + f), Math.min(1.0f, b + f * 1.1f), color.w);
	}

	public void fillPostParams(float[] p, AetherConfig c, float partial) {
		Minecraft client = Minecraft.getInstance();

		if (client.level == null) {
			return;
		}

		Camera camera = client.gameRenderer.getMainCamera();
		float angle = sunAngleRadians(camera, partial);
		float sx = -Mth.sin(angle);
		float sy = Mth.cos(angle);
		boolean overworld = dimension == Dimension.OVERWORLD;
		boolean underwater = camera.getFluidInCamera() == FogType.WATER;
		boolean night = sy < 0.0f;

		// At night the moon (opposite the sun) drives scattering and rays.
		p[PostParams.SUN_DIR_X] = night && c.moonlight ? -sx : sx;
		p[PostParams.SUN_DIR_Y] = night && c.moonlight ? -sy : sy;
		p[PostParams.SUN_DIR_Z] = 0.0f;
		p[PostParams.DAYLIGHT] = sy;
		p[PostParams.DIMENSION] = dimension.ordinal();

		p[PostParams.FOG_R] = fogR;
		p[PostParams.FOG_G] = fogG;
		p[PostParams.FOG_B] = fogB;

		float storm = Math.max(rain * 0.7f, thunder);
		float daySide = Mth.clamp(sy * 4.0f, 0.0f, 1.0f);
		float moonSide = c.moonlight ? Mth.clamp(-sy * 4.0f, 0.0f, 1.0f) * 0.35f : 0.0f;
		float celestial = (daySide + moonSide) * (1.0f - storm * 0.8f) * (1.0f - cave);

		if (overworld && !underwater && c.heightFog && c.atmosphericFog) {
			float density = 0.0025f * c.heightFogDensity * (1.0f + rain * 1.5f + mist * 2.5f + swamp * 1.5f + forest * 0.5f);
			p[PostParams.HEIGHT_FOG] = density * (1.0f - cave);
			p[PostParams.HEIGHT_FOG_BASE] = seaLevel + c.heightFogLevel + 2.0f;
			p[PostParams.HEIGHT_FOG_FALLOFF] = 0.045f;
		}

		if (!underwater && c.volumetricFog && (overworld || dimension == Dimension.OTHER)) {
			p[PostParams.VOLUMETRIC] = c.volumetricStrength * (0.35f + mist * 0.6f + rain * 0.3f) * Math.max(celestial, 0.15f);
		}

		if (!underwater && c.godRays && overworld) {
			p[PostParams.GODRAYS] = c.godRayStrength * celestial;
		}

		p[PostParams.FLASH] = c.lightningFlashes ? flash * c.lightningFlashIntensity : 0.0f;

		if (underwater && c.underwaterEffects) {
			p[PostParams.UNDERWATER] = 1.0f;
			p[PostParams.CAUSTICS] = c.waterCaustics ? c.causticStrength * Math.max(0.15f, daySide) : 0.0f;
			p[PostParams.DISTORTION] = c.underwaterDistortion ? c.distortionStrength : 0.0f;
		}

		if (overworld && c.wetSurfaces) {
			p[PostParams.WETNESS] = rain * (1.0f - cave) * 0.6f;
		}

		if (overworld && c.sunsetGlow) {
			float horizon = Mth.clamp(1.0f - Math.abs(sy) * 3.5f, 0.0f, 1.0f);
			p[PostParams.SUNSET] = c.sunsetIntensity * horizon * (1.0f - storm);
		}

		if (overworld && c.nightAtmosphere) {
			p[PostParams.NIGHT_TINT] = c.nightTint * Mth.clamp(-sy * 3.0f, 0.0f, 1.0f);
		}

		if (c.caveAtmosphere) {
			p[PostParams.CAVE] = c.caveDarkness * cave;
		}

		p[PostParams.SKY_SCATTER] = overworld && c.skyScattering ? 1.0f - storm * 0.7f : 0.0f;

		if (dimension == Dimension.NETHER && !c.netherAtmosphere) {
			p[PostParams.SKY_SCATTER] = 0.0f;
		}
	}
}
