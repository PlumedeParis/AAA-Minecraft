package com.aetherrealism.water;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Pass-through vertex consumer that rewrites the vertex alpha of water geometry into the water marker
 * ({@code alpha = 255 - (base + w)}, base 64 with ripple shimmer or 80 without): {@code w} is the wave amplitude for surface vertices and 0 for vertices on a
 * block boundary (bottoms of side faces, full-height water), which must stay put to avoid cracks.
 */
final class WaterMarkingVertexConsumer implements VertexConsumer {
	private VertexConsumer delegate;
	private int base;
	private int waveLevel;
	private float lastY;

	void begin(VertexConsumer delegate, int base, int waveLevel) {
		this.delegate = delegate;
		this.base = base;
		this.waveLevel = waveLevel;
	}

	void end() {
		this.delegate = null;
	}

	private int markerAlpha(float y) {
		float frac = y - (float) Math.floor(y);
		boolean surface = frac > 0.001f && frac < 0.999f;
		int w = surface ? waveLevel : 0;
		return 255 - (base + w);
	}

	@Override
	public VertexConsumer addVertex(float x, float y, float z) {
		lastY = y;
		delegate.addVertex(x, y, z);
		return this;
	}

	@Override
	public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
		int marked = (color & 0x00FFFFFF) | (markerAlpha(y) << 24);
		delegate.addVertex(x, y, z, marked, u, v, overlay, light, nx, ny, nz);
	}

	@Override
	public VertexConsumer setColor(int red, int green, int blue, int alpha) {
		delegate.setColor(red, green, blue, markerAlpha(lastY));
		return this;
	}

	@Override
	public VertexConsumer setColor(float red, float green, float blue, float alpha) {
		delegate.setColor(red, green, blue, markerAlpha(lastY) / 255.0f);
		return this;
	}

	@Override
	public VertexConsumer setColor(int argb) {
		delegate.setColor((argb & 0x00FFFFFF) | (markerAlpha(lastY) << 24));
		return this;
	}

	@Override
	public VertexConsumer setUv(float u, float v) {
		delegate.setUv(u, v);
		return this;
	}

	@Override
	public VertexConsumer setUv1(int u, int v) {
		delegate.setUv1(u, v);
		return this;
	}

	@Override
	public VertexConsumer setOverlay(int uv) {
		delegate.setOverlay(uv);
		return this;
	}

	@Override
	public VertexConsumer setUv2(int u, int v) {
		delegate.setUv2(u, v);
		return this;
	}

	@Override
	public VertexConsumer setLight(int uv) {
		delegate.setLight(uv);
		return this;
	}

	@Override
	public VertexConsumer setNormal(float x, float y, float z) {
		delegate.setNormal(x, y, z);
		return this;
	}

	@Override
	public VertexConsumer setLineWidth(float width) {
		delegate.setLineWidth(width);
		return this;
	}
}
