package com.aetherrealism.mixin;

import com.aetherrealism.ErrorReporter;
import com.aetherrealism.atmosphere.AtmosphereController;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Adjusts the fog uniform block right before vanilla writes it. Arguments of {@code updateBuffer}: buffer, offset,
 * fog color, environmental start/end, render-distance start/end, sky end, clouds end (same call Sodium hooks on
 * 1.21.11).
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@ModifyArgs(method = "setupFog", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
	private void aether$adjustFog(Args args) {
		try {
			AtmosphereController.INSTANCE.adjustFog(args);
		} catch (Exception e) {
			ErrorReporter.report("fog", e);
		}
	}
}
