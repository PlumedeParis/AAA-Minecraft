package com.aetherrealism.gui;

/** Expected performance cost of a setting, shown as a colored badge and in tooltips. */
public enum Impact {
	NONE("No performance impact", Theme.TEXT_MUTED),
	LOW("Low performance impact", Theme.SUCCESS),
	MEDIUM("Medium performance impact", 0xFFE8D25A),
	HIGH("High performance impact", Theme.WARNING),
	VERY_HIGH("Very high performance impact", Theme.DANGER);

	public final String text;
	public final int color;

	Impact(String text, int color) {
		this.text = text;
		this.color = color;
	}
}
