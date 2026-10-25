package com.aetherrealism.performance;

import com.aetherrealism.compat.CompatManager;
import com.aetherrealism.config.AetherConfig;
import com.aetherrealism.config.ConfigManager;
import com.aetherrealism.lighting.DynamicLightManager;
import com.aetherrealism.particles.ParticleBudget;
import com.aetherrealism.render.PostEffectManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Optional HUD overlay with FPS, frame time (average and worst over the last second), particle and light counts
 * and the active preset. Frame times are measured between consecutive world renders with no allocation.
 */
public final class PerformanceMonitor {
	public static final PerformanceMonitor INSTANCE = new PerformanceMonitor();

	private long lastFrameNanos;
	private double frameMsAccum;
	private double worstMsWindow;
	private int framesWindow;
	private long windowStart;
	private double avgFrameMs;
	private double worstFrameMs;

	private PerformanceMonitor() {
	}

	public void onFrame() {
		long now = System.nanoTime();

		if (lastFrameNanos != 0) {
			double ms = (now - lastFrameNanos) / 1.0e6;
			frameMsAccum += ms;
			worstMsWindow = Math.max(worstMsWindow, ms);
			framesWindow++;
		}

		lastFrameNanos = now;

		if (now - windowStart > 1_000_000_000L) {
			avgFrameMs = framesWindow > 0 ? frameMsAccum / framesWindow : 0.0;
			worstFrameMs = worstMsWindow;
			frameMsAccum = 0;
			worstMsWindow = 0;
			framesWindow = 0;
			windowStart = now;
		}
	}

	public void tick() {
	}

	public void renderHud(GuiGraphics graphics, DeltaTracker deltaTracker) {
		AetherConfig c = ConfigManager.get();
		Minecraft client = Minecraft.getInstance();

		if (!c.showPerformanceMonitor || client.options.hideGui) {
			return;
		}

		Font font = client.font;
		String[] lines = {
				"Aether Realism" + (c.masterEnabled ? "" : " (effects OFF)"),
				"FPS: " + client.getFps(),
				String.format("Frame: %.1f ms avg / %.1f ms worst", avgFrameMs, worstFrameMs),
				"Ambient particles: " + ParticleBudget.alive() + " / " + c.maxAmbientParticles,
				"Dynamic lights: " + DynamicLightManager.INSTANCE.activeCount() + " / " + c.dynamicLightMaxSources,
				"Preset: " + c.preset.displayName,
				"Post FX: " + (PostEffectManager.INSTANCE.shouldRun(c) ? "on" : "off")
						+ (CompatManager.irisShaderPackInUse() ? " (shader pack active)" : "")
		};

		int width = 0;

		for (String line : lines) {
			width = Math.max(width, font.width(line));
		}

		int x = 4;
		int y = 4;
		int h = lines.length * (font.lineHeight + 2) + 4;
		graphics.fill(x - 2, y - 2, x + width + 4, y + h - 2, 0xA0101418);
		graphics.fill(x - 2, y - 2, x - 1, y + h - 2, 0xFF6FB7FF);

		for (int i = 0; i < lines.length; i++) {
			int color = i == 0 ? 0xFF8FC9FF : 0xFFE6E6E6;
			graphics.drawString(font, lines[i], x + 2, y + i * (font.lineHeight + 2), color, false);
		}
	}
}
