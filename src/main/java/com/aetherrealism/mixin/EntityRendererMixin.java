package com.aetherrealism.mixin;

import com.aetherrealism.lighting.DynamicLightManager;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lights entities standing near dynamic light sources (and light-emitting entities themselves). */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {
	@Inject(method = "getBlockLightLevel", at = @At("RETURN"), cancellable = true)
	private void aether$entityDynamicLight(T entity, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		DynamicLightManager manager = DynamicLightManager.INSTANCE;

		if (!manager.hasActiveLights()) {
			return;
		}

		int vanilla = cir.getReturnValueI();
		int dynamic = manager.lightLevelForEntity(entity, pos);

		if (dynamic > vanilla) {
			cir.setReturnValue(dynamic);
		}
	}
}
