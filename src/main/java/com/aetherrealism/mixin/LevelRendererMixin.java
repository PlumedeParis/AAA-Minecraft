package com.aetherrealism.mixin;

import com.aetherrealism.ErrorReporter;
import com.aetherrealism.performance.PerformanceMonitor;
import com.aetherrealism.render.PostEffectManager;
import com.aetherrealism.render.RenderCapture;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the camera matrices for the post shaders and runs the post chain once the level (including
 * translucent geometry, weather and particles) has been drawn, but before the first-person hand and the GUI.
 * The signature matches Fabric API's own 1.21.11 hook on the same method.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void aether$captureMatrices(GraphicsResourceAllocator allocator, DeltaTracker tickCounter, boolean renderBlockOutline,
			Camera camera, Matrix4f viewMatrix, Matrix4f projectionMatrix, Matrix4f cullProjectionMatrix,
			GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
		try {
			RenderCapture.capture(viewMatrix, projectionMatrix, camera.position(), tickCounter.getGameTimeDeltaPartialTick(false));
			PerformanceMonitor.INSTANCE.onFrame();
		} catch (Exception e) {
			ErrorReporter.report("matrix capture", e);
		}
	}

	@Inject(method = "renderLevel", at = @At("RETURN"))
	private void aether$applyPostEffects(GraphicsResourceAllocator allocator, DeltaTracker tickCounter, boolean renderBlockOutline,
			Camera camera, Matrix4f viewMatrix, Matrix4f projectionMatrix, Matrix4f cullProjectionMatrix,
			GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
		PostEffectManager.INSTANCE.apply(allocator);
	}
}
