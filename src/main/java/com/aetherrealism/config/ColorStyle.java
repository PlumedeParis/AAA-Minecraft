package com.aetherrealism.config;

/** Color grading looks. The tint values are multiplied into the final image by the composite shader. */
public enum ColorStyle {
	NATURAL("Natural", 1.00f, 1.00f, 1.00f, 0.0f),
	CINEMATIC("Cinematic", 1.04f, 1.00f, 0.97f, 0.35f),
	WARM("Warm", 1.06f, 1.01f, 0.92f, 0.0f),
	COOL("Cool", 0.94f, 1.00f, 1.07f, 0.0f),
	VIBRANT("Vibrant", 1.00f, 1.02f, 1.00f, 0.0f);

	public final String displayName;
	public final float r;
	public final float g;
	public final float b;
	/** Strength of the teal-shadows / warm-highlights split toning. */
	public final float splitToning;

	ColorStyle(String displayName, float r, float g, float b, float splitToning) {
		this.displayName = displayName;
		this.r = r;
		this.g = g;
		this.b = b;
		this.splitToning = splitToning;
	}
}
