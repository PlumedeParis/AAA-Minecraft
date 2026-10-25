package com.aetherrealism.config;

/** Quality levels for effects with a sample-count or resolution trade-off. */
public enum Quality {
	LOW("Low", 0.25f),
	MEDIUM("Medium", 0.5f),
	HIGH("High", 0.75f),
	ULTRA("Ultra", 1.0f);

	public final String displayName;
	/** Normalized 0..1 factor the shaders turn into sample counts. */
	public final float factor;

	Quality(String displayName, float factor) {
		this.displayName = displayName;
		this.factor = factor;
	}
}
