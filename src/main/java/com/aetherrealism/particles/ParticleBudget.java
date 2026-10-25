package com.aetherrealism.particles;

import com.aetherrealism.config.AetherConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.phys.Vec3;

/**
 * Shared spawn budget for every particle Aether Realism adds. Tracks spawns over a sliding two-second window
 * as an estimate of how many of our particles are alive, enforces the configured cap, scales density with the
 * vanilla particle setting and low-performance mode, and skips spawns outside the view cone (cheap frustum test).
 */
public final class ParticleBudget {
	private static final int WINDOW = 40;
	private static final int[] SPAWNS = new int[WINDOW];
	private static int slot;
	private static int alive;
	private static int spawnedThisTick;

	private ParticleBudget() {
	}

	public static void beginTick() {
		slot = (slot + 1) % WINDOW;
		alive -= SPAWNS[slot];
		SPAWNS[slot] = 0;
		spawnedThisTick = 0;
	}

	public static void reset() {
		java.util.Arrays.fill(SPAWNS, 0);
		alive = 0;
	}

	/** Estimated number of live particles spawned by this mod. */
	public static int alive() {
		return Math.max(0, alive);
	}

	public static int spawnedThisTick() {
		return spawnedThisTick;
	}

	/** Global multiplier for spawn counts; 0 disables ambient particles entirely. */
	public static float densityScale(Minecraft client, AetherConfig c) {
		ParticleStatus status = client.options.particles().get();

		if (status == ParticleStatus.MINIMAL) {
			return 0.0f;
		}

		float scale = c.particleDensity;

		if (status == ParticleStatus.DECREASED) {
			scale *= 0.5f;
		}

		if (c.lowPerformanceMode) {
			scale *= 0.4f;
		}

		return scale;
	}

	public static boolean hasRoom(AetherConfig c) {
		return alive() < c.maxAmbientParticles;
	}

	/** Frustum-ish visibility test: inside a cone slightly wider than the view, or very close to the camera. */
	public static boolean visible(Camera camera, AetherConfig c, double x, double y, double z) {
		if (!c.frustumCulling) {
			return true;
		}

		Vec3 cam = camera.position();
		double dx = x - cam.x;
		double dy = y - cam.y;
		double dz = z - cam.z;
		double distSq = dx * dx + dy * dy + dz * dz;

		if (distSq < 9.0) {
			return true;
		}

		var forward = camera.forwardVector();
		double dot = (dx * forward.x() + dy * forward.y() + dz * forward.z()) / Math.sqrt(distSq);
		return dot > 0.2; // ~78 degrees half-angle covers wide FOVs
	}

	public static boolean spawn(Minecraft client, AetherConfig c, ParticleOptions options, double x, double y, double z,
			double vx, double vy, double vz) {
		if (!hasRoom(c) || client.level == null) {
			return false;
		}

		client.level.addParticle(options, x, y, z, vx, vy, vz);
		SPAWNS[slot]++;
		alive++;
		spawnedThisTick++;
		return true;
	}
}
