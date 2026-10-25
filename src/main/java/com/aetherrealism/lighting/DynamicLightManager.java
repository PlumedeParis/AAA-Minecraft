package com.aetherrealism.lighting;

import com.aetherrealism.compat.CompatManager;
import com.aetherrealism.config.AetherConfig;
import com.aetherrealism.config.ConfigManager;
import com.aetherrealism.mixin.LevelRendererAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * World-space dynamic lighting for moving light sources.
 *
 * <p>Every tick the manager collects light-emitting entities (held torches/lanterns, dropped glowing items,
 * burning mobs, blazes, lit TNT...), keeps the closest {@code maxSources}, and publishes an immutable snapshot.
 * The snapshot raises the block-light component returned by vanilla's light lookup (see
 * {@code BrightnessGetterMixin}), and chunk sections around sources that moved are re-meshed so the extra light
 * becomes visible on terrain. Light falls off continuously with distance (not in whole-block steps), which keeps
 * moving lights smooth. Light color is limited to vanilla's single block-light color: per-light colors would need
 * a custom lighting pipeline that the vanilla renderer does not have.
 */
public final class DynamicLightManager {
	public static final DynamicLightManager INSTANCE = new DynamicLightManager();

	/** Immutable light snapshot read from chunk-meshing threads. */
	private record Light(double x, double y, double z, float level, float radius) {
	}

	private record Tracked(long sectionKey, int level, double x, double y, double z) {
	}

	private volatile Light[] lights = new Light[0];
	private volatile float intensity = 1.0f;
	private final Int2ObjectOpenHashMap<Tracked> tracked = new Int2ObjectOpenHashMap<>();
	private int tickCounter;
	private int activeCount;

	private DynamicLightManager() {
	}

	public boolean hasActiveLights() {
		return lights.length > 0;
	}

	public int activeCount() {
		return activeCount;
	}

	public void reset() {
		lights = new Light[0];
		tracked.clear();
		activeCount = 0;
	}

	public void onConfigChanged(AetherConfig previous, AetherConfig next) {
		boolean wasOn = previous.masterEnabled && previous.dynamicLights;
		boolean isOn = next.masterEnabled && next.dynamicLights;

		if (wasOn && !isOn) {
			clearAndRefresh(Minecraft.getInstance());
		}
	}

	private void clearAndRefresh(Minecraft client) {
		Light[] old = lights;
		lights = new Light[0];
		activeCount = 0;

		if (client.levelRenderer != null) {
			for (Light light : old) {
				markDirty(client, light.x, light.y, light.z, light.radius);
			}
		}

		tracked.clear();
	}

	public void tick(Minecraft client) {
		AetherConfig c = ConfigManager.get();
		ClientLevel level = client.level;

		if (level == null || !c.masterEnabled || !c.dynamicLights || CompatManager.suspendedByShaderPack()) {
			if (lights.length > 0) {
				clearAndRefresh(client);
			}

			return;
		}

		tickCounter++;
		intensity = c.dynamicLightIntensity;
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		double maxDistSq = (double) c.effectDistance * c.effectDistance;
		List<Candidate> candidates = new ArrayList<>();

		for (Entity entity : level.entitiesForRendering()) {
			int lum = luminance(entity, c);

			if (lum <= 0) {
				continue;
			}

			double distSq = entity.distanceToSqr(camera);

			if (distSq > maxDistSq) {
				continue;
			}

			candidates.add(new Candidate(entity, lum, distSq));
		}

		candidates.sort(Comparator.comparingDouble(Candidate::distSq));
		int max = Math.min(candidates.size(), c.dynamicLightMaxSources);
		Light[] next = new Light[max];
		IntSet seen = new IntOpenHashSet();
		boolean rebuildTick = tickCounter % c.dynamicLightUpdateInterval == 0;

		for (int i = 0; i < max; i++) {
			Candidate cand = candidates.get(i);
			Entity e = cand.entity;
			double x = e.getX();
			double y = e.getY() + e.getBbHeight() * 0.6;
			double z = e.getZ();
			float radius = cand.luminance * c.dynamicLightRadius;
			next[i] = new Light(x, y, z, cand.luminance, radius);
			seen.add(e.getId());

			if (!rebuildTick) {
				continue;
			}

			// Distant sources refresh less often.
			if (cand.distSq > 24 * 24 && (tickCounter / c.dynamicLightUpdateInterval) % c.distantUpdateInterval != 0) {
				continue;
			}

			Tracked prev = tracked.get(e.getId());
			long sectionKey = SectionPos.asLong(BlockPos.containing(x, y, z));
			// Re-mesh when the source changed level or moved far enough for the falloff to visibly shift.
			double moveThreshold = c.dynamicLightSmoothing ? 0.35 : 1.0;

			if (prev == null || prev.level != cand.luminance
					|| sq(prev.x - x) + sq(prev.y - y) + sq(prev.z - z) > moveThreshold * moveThreshold) {
				if (prev != null) {
					markDirty(client, prev.x, prev.y, prev.z, prev.level * c.dynamicLightRadius);
				}

				markDirty(client, x, y, z, radius);
				tracked.put(e.getId(), new Tracked(sectionKey, cand.luminance, x, y, z));
			}
		}

		if (rebuildTick) {
			// Sources that disappeared or fell out of the budget: refresh where they were.
			var it = tracked.int2ObjectEntrySet().iterator();

			while (it.hasNext()) {
				var entry = it.next();

				if (!seen.contains(entry.getIntKey())) {
					Tracked t = entry.getValue();
					markDirty(client, t.x, t.y, t.z, t.level * c.dynamicLightRadius);
					it.remove();
				}
			}
		}

		lights = next;
		activeCount = max;
	}

	private record Candidate(Entity entity, int luminance, double distSq) {
	}

	private static double sq(double v) {
		return v * v;
	}

	private void markDirty(Minecraft client, double x, double y, double z, float radius) {
		LevelRendererAccessor accessor = (LevelRendererAccessor) client.levelRenderer;
		int r = (int) Math.ceil(radius) + 1;
		int minX = SectionPos.blockToSectionCoord((int) Math.floor(x) - r);
		int maxX = SectionPos.blockToSectionCoord((int) Math.floor(x) + r);
		int minY = SectionPos.blockToSectionCoord((int) Math.floor(y) - r);
		int maxY = SectionPos.blockToSectionCoord((int) Math.floor(y) + r);
		int minZ = SectionPos.blockToSectionCoord((int) Math.floor(z) - r);
		int maxZ = SectionPos.blockToSectionCoord((int) Math.floor(z) + r);

		for (int sx = minX; sx <= maxX; sx++) {
			for (int sy = minY; sy <= maxY; sy++) {
				for (int sz = minZ; sz <= maxZ; sz++) {
					accessor.aether$setSectionDirty(sx, sy, sz, false);
				}
			}
		}
	}

	/** Continuous light level (0..15) contributed by dynamic sources at a point. Thread-safe. */
	public float levelAt(double px, double py, double pz) {
		Light[] snapshot = lights;
		float best = 0.0f;

		for (Light light : snapshot) {
			double dx = light.x - px;
			double dy = light.y - py;
			double dz = light.z - pz;
			double distSq = dx * dx + dy * dy + dz * dz;

			if (distSq >= light.radius * light.radius) {
				continue;
			}

			float v = (float) (light.level * (1.0 - Math.sqrt(distSq) / light.radius));

			if (v > best) {
				best = v;
			}
		}

		return Math.min(15.0f, best * intensity);
	}

	/** Raises the block-light half of a packed lightmap coordinate. Called from meshing threads. */
	public int applyToPackedLight(BlockAndTintGetter level, BlockPos pos, int packed) {
		float dyn = levelAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

		if (dyn <= 0.0f) {
			return packed;
		}

		if (level.getBlockState(pos).isSolidRender()) {
			return packed;
		}

		int blockCoord = packed & 0xFFFF;
		int dynCoord = Math.min(240, (int) (dyn * 16.0f));

		if (dynCoord <= blockCoord) {
			return packed;
		}

		return (packed & 0xFFFF0000) | dynCoord;
	}

	public int lightLevelForEntity(Entity entity, BlockPos pos) {
		AetherConfig c = ConfigManager.get();
		int own = c.dynamicLightsEntities || entity == Minecraft.getInstance().player ? luminance(entity, c) : 0;
		float around = levelAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		return Math.max(own, (int) around);
	}

	/** How much light an entity emits right now, honoring the per-category toggles. */
	public static int luminance(Entity entity, AetherConfig c) {
		int lum = 0;

		if (c.dynamicLightsHeldItems && entity instanceof LivingEntity living) {
			lum = Math.max(lum, itemLuminance(living.getMainHandItem(), c));
			lum = Math.max(lum, itemLuminance(living.getOffhandItem(), c));
		}

		if (!c.dynamicLightsEntities) {
			return lum;
		}

		if (entity.isOnFire()) {
			lum = Math.max(lum, 14);
		}

		if (entity instanceof ItemEntity item) {
			lum = Math.max(lum, itemLuminance(item.getItem(), c));
		} else if (entity instanceof FallingBlockEntity falling) {
			lum = Math.max(lum, falling.getBlockState().getLightEmission());
		}

		Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		Integer typeLight = ENTITY_LIGHTS.get(typeId.toString());

		if (typeLight != null) {
			lum = Math.max(lum, typeLight);
		}

		return lum;
	}

	private static final Map<String, Integer> ENTITY_LIGHTS = Map.ofEntries(
			Map.entry("minecraft:blaze", 10),
			Map.entry("minecraft:magma_cube", 8),
			Map.entry("minecraft:glow_squid", 6),
			Map.entry("minecraft:tnt", 10),
			Map.entry("minecraft:firework_rocket", 12),
			Map.entry("minecraft:lightning_bolt", 15),
			Map.entry("minecraft:experience_orb", 6),
			Map.entry("minecraft:spectral_arrow", 8),
			Map.entry("minecraft:end_crystal", 12),
			Map.entry("minecraft:allay", 6),
			Map.entry("minecraft:fireball", 12),
			Map.entry("minecraft:small_fireball", 10),
			Map.entry("minecraft:dragon_fireball", 10),
			Map.entry("minecraft:wither_skull", 6),
			Map.entry("minecraft:glow_item_frame", 8)
	);

	public static int itemLuminance(ItemStack stack, AetherConfig c) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}

		Integer custom = c.customItemLights.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());

		if (custom != null) {
			return custom;
		}

		if (stack.getItem() instanceof BlockItem blockItem) {
			return blockItem.getBlock().defaultBlockState().getLightEmission();
		}

		return 0;
	}
}
