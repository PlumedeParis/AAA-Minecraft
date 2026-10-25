package com.aetherrealism.render;

import com.aetherrealism.AetherRealismClient;
import com.aetherrealism.ErrorReporter;
import com.aetherrealism.atmosphere.AtmosphereController;
import com.aetherrealism.compat.CompatManager;
import com.aetherrealism.config.AetherConfig;
import com.aetherrealism.config.ConfigManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import java.util.Set;

/**
 * Drives the {@code aether_realism:aether} post chain (SSAO, height/volumetric fog, sky scattering, god rays,
 * bloom, color grading, underwater effects, lightning flashes).
 *
 * <p>Vanilla post chains only support uniform values fixed in JSON, so live parameters are transported through a
 * 64x1 RGBA8 texture: every texel holds the raw IEEE-754 bits of one float, decoded exactly in
 * {@code shaders/include/params.glsl} with {@code uintBitsToFloat}. The texture is registered in the texture
 * manager so the chain's texture input picks it up.
 */
public final class PostEffectManager {
	public static final PostEffectManager INSTANCE = new PostEffectManager();
	public static final Identifier CHAIN_ID = AetherRealismClient.id("aether");
	private static final Set<Identifier> EXTERNAL_TARGETS = Set.of(Identifier.withDefaultNamespace("main"));

	private final float[] params = new float[PostParams.COUNT];
	private DynamicTexture texture;
	private DynamicTexture textureAlias;
	private boolean chainUnavailable;
	private long lastFailureLog;

	private PostEffectManager() {
	}

	public void init(Minecraft client) {
		try {
			texture = new DynamicTexture(() -> "Aether Realism parameters", PostParams.COUNT, 1, true);
			textureAlias = new DynamicTexture(() -> "Aether Realism parameters (alias)", PostParams.COUNT, 1, true);
			// The post-chain loader resolves texture inputs either by their raw id or under textures/effect/;
			// register both so the dynamic texture is found regardless.
			client.getTextureManager().register(AetherRealismClient.id("params"), texture);
			client.getTextureManager().register(AetherRealismClient.id("textures/effect/params.png"), textureAlias);
		} catch (Exception e) {
			chainUnavailable = true;
			AetherRealismClient.LOGGER.error("Could not create the post-processing parameter texture; post effects disabled", e);
		}
	}

	public void close() {
		// Textures are owned and closed by the TextureManager.
		texture = null;
		textureAlias = null;
	}

	public boolean isAvailable() {
		return !chainUnavailable && texture != null;
	}

	public boolean shouldRun(AetherConfig c) {
		return c.masterEnabled && c.postProcessing && isAvailable() && !CompatManager.suspendedByShaderPack()
				&& RenderCapture.valid;
	}

	/** Called right after the level has been rendered (before the hand and GUI). */
	public void apply(GraphicsResourceAllocator allocator) {
		AetherConfig c = ConfigManager.get();

		if (!shouldRun(c)) {
			return;
		}

		Minecraft client = Minecraft.getInstance();

		try {
			fillParams(c, client);
			upload(texture);
			upload(textureAlias);

			PostChain chain = client.getShaderManager().getPostChain(CHAIN_ID, EXTERNAL_TARGETS);

			if (chain == null) {
				logUnavailable();
				return;
			}

			chain.process(client.getMainRenderTarget(), allocator);
		} catch (Exception e) {
			ErrorReporter.report("post-processing", e);
		}
	}

	private void logUnavailable() {
		long now = System.currentTimeMillis();

		if (now - lastFailureLog > 60_000L) {
			lastFailureLog = now;
			AetherRealismClient.LOGGER.warn("The aether_realism:aether post chain failed to load (see earlier shader errors); post effects are skipped");
		}
	}

	private void upload(DynamicTexture tex) {
		if (tex == null) {
			return;
		}

		NativeImage image = tex.getPixels();

		if (image == null) {
			return;
		}

		for (int i = 0; i < PostParams.COUNT; i++) {
			int bits = Float.floatToRawIntBits(params[i]);
			int r = (bits >>> 24) & 0xFF;
			int g = (bits >>> 16) & 0xFF;
			int b = (bits >>> 8) & 0xFF;
			int a = bits & 0xFF;
			image.setPixel(i, 0, (a << 24) | (r << 16) | (g << 8) | b);
		}

		tex.upload();
	}

	private void fillParams(AetherConfig c, Minecraft client) {
		float[] p = params;
		java.util.Arrays.fill(p, 0.0f);

		Matrix4f inv = RenderCapture.INVERSE_VIEW_PROJECTION;
		p[PostParams.INV_VP + 0] = inv.m00();
		p[PostParams.INV_VP + 1] = inv.m01();
		p[PostParams.INV_VP + 2] = inv.m02();
		p[PostParams.INV_VP + 3] = inv.m03();
		p[PostParams.INV_VP + 4] = inv.m10();
		p[PostParams.INV_VP + 5] = inv.m11();
		p[PostParams.INV_VP + 6] = inv.m12();
		p[PostParams.INV_VP + 7] = inv.m13();
		p[PostParams.INV_VP + 8] = inv.m20();
		p[PostParams.INV_VP + 9] = inv.m21();
		p[PostParams.INV_VP + 10] = inv.m22();
		p[PostParams.INV_VP + 11] = inv.m23();
		p[PostParams.INV_VP + 12] = inv.m30();
		p[PostParams.INV_VP + 13] = inv.m31();
		p[PostParams.INV_VP + 14] = inv.m32();
		p[PostParams.INV_VP + 15] = inv.m33();

		var cam = RenderCapture.cameraPos;
		p[PostParams.CAMERA_X] = (float) (cam.x % 4096.0);
		p[PostParams.CAMERA_Y] = (float) cam.y;
		p[PostParams.CAMERA_Z] = (float) (cam.z % 4096.0);
		p[PostParams.TIME] = (float) ((System.nanoTime() / 1.0e9) % 3600.0);

		AtmosphereController atmo = AtmosphereController.INSTANCE;
		atmo.fillPostParams(p, c, RenderCapture.partialTick);

		// Projected sun position in UV space for the radial god-ray blur.
		float sx = p[PostParams.SUN_DIR_X];
		float sy = p[PostParams.SUN_DIR_Y];
		float sz = p[PostParams.SUN_DIR_Z];
		org.joml.Vector4f clip = new org.joml.Vector4f(sx * 1000.0f, sy * 1000.0f, sz * 1000.0f, 1.0f);
		RenderCapture.VIEW_PROJECTION.transform(clip);

		if (clip.w > 0.0f) {
			p[PostParams.SUN_SCREEN_X] = (clip.x / clip.w) * 0.5f + 0.5f;
			p[PostParams.SUN_SCREEN_Y] = (clip.y / clip.w) * 0.5f + 0.5f;
			p[PostParams.SUN_ON_SCREEN] = 1.0f;
		} else {
			p[PostParams.SUN_ON_SCREEN] = 0.0f;
		}

		p[PostParams.EXPOSURE] = c.exposure;
		p[PostParams.CONTRAST] = c.contrast;
		p[PostParams.SATURATION] = c.saturation;
		p[PostParams.BRIGHTNESS] = c.brightness;
		p[PostParams.GRADING] = c.colorGrading ? 1.0f : 0.0f;
		p[PostParams.TINT_R] = c.colorGrading ? c.colorStyle.r : 1.0f;
		p[PostParams.TINT_G] = c.colorGrading ? c.colorStyle.g : 1.0f;
		p[PostParams.TINT_B] = c.colorGrading ? c.colorStyle.b : 1.0f;
		p[PostParams.SPLIT_TONING] = c.colorGrading ? c.colorStyle.splitToning : 0.0f;
		p[PostParams.VIBRANCE] = c.colorGrading && c.colorStyle == com.aetherrealism.config.ColorStyle.VIBRANT ? 0.35f : 0.0f;
		p[PostParams.VIGNETTE] = c.vignette;

		p[PostParams.BLOOM] = c.bloom ? c.bloomIntensity : 0.0f;
		p[PostParams.BLOOM_THRESHOLD] = c.bloomThreshold;
		p[PostParams.BLOOM_QUALITY] = c.bloomQuality.factor;

		p[PostParams.SSAO] = c.ssao && c.experimentalFeatures ? c.ssaoStrength : 0.0f;
		p[PostParams.SSAO_QUALITY] = c.ssaoQuality.factor;

		p[PostParams.GODRAY_QUALITY] = c.godRayQuality.factor;
		p[PostParams.VOLUMETRIC_QUALITY] = c.volumetricQuality.factor;
		p[PostParams.LOW_PERF] = c.lowPerformanceMode ? 1.0f : 0.0f;
	}
}
