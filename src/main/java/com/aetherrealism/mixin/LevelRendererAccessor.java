package com.aetherrealism.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
	/** Marks one chunk section for re-meshing (same invoker LambDynamicLights uses on 1.21.11). */
	@Invoker("setSectionDirty")
	void aether$setSectionDirty(int x, int y, int z, boolean important);
}
