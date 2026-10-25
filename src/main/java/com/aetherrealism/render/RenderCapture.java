package com.aetherrealism.render;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Per-frame copies of the matrices vanilla passes to {@code LevelRenderer.renderLevel}. Minecraft renders the
 * world camera-relative, so {@code projection * view} maps camera-relative world positions to clip space and its
 * inverse lets the post shaders reconstruct positions from the depth buffer.
 */
public final class RenderCapture {
	public static final Matrix4f PROJECTION = new Matrix4f();
	public static final Matrix4f VIEW = new Matrix4f();
	public static final Matrix4f VIEW_PROJECTION = new Matrix4f();
	public static final Matrix4f INVERSE_VIEW_PROJECTION = new Matrix4f();
	public static Vec3 cameraPos = Vec3.ZERO;
	public static float partialTick;
	public static boolean valid;

	private RenderCapture() {
	}

	public static void capture(Matrix4f view, Matrix4f projection, Vec3 camera, float partial) {
		VIEW.set(view);
		PROJECTION.set(projection);
		projection.mul(view, VIEW_PROJECTION);
		VIEW_PROJECTION.invert(INVERSE_VIEW_PROJECTION);
		cameraPos = camera;
		partialTick = partial;
		valid = true;
	}
}
