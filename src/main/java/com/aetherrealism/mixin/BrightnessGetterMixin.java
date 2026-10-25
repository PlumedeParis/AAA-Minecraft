package com.aetherrealism.mixin;

import com.aetherrealism.lighting.DynamicLightManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the block-light component of the packed lightmap coordinate used while meshing terrain, which is what
 * makes dynamic lights actually illuminate the world. Targets the lambda behind
 * {@code LevelRenderer.BrightnessGetter.DEFAULT}: {@code lambda$static$0} in the Mojang-mapped dev environment,
 * {@code method_68890} in production (the same target LambDynamicLights uses on 1.21.11).
 */
@Mixin(value = LevelRenderer.BrightnessGetter.class, priority = 900)
public interface BrightnessGetterMixin {
	@SuppressWarnings("UnresolvedMixinReference")
	@Inject(method = {"method_68890", "lambda$static$0"}, at = @At("TAIL"), cancellable = true, remap = false, require = 0)
	private static void aether$applyDynamicLight(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		DynamicLightManager manager = DynamicLightManager.INSTANCE;

		if (!manager.hasActiveLights()) {
			return;
		}

		int packed = cir.getReturnValueI();
		int lit = manager.applyToPackedLight(level, pos, packed);

		if (lit != packed) {
			cir.setReturnValue(lit);
		}
	}
}
