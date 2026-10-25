package com.aetherrealism.gui;

/** Colors and metrics of the settings UI (ARGB; alpha is required by the 1.21 GUI renderer). */
public final class Theme {
	public static final int BACKGROUND = 0xF00B0D10;
	public static final int PANEL = 0xFF14181D;
	public static final int PANEL_ALT = 0xFF181D23;
	public static final int SIDEBAR = 0xFF101317;
	public static final int HEADER = 0xFF0E1115;
	public static final int ROW_HOVER = 0xFF1E252D;
	public static final int ROW_SELECTED = 0xFF233040;
	public static final int BORDER = 0xFF2A323C;
	public static final int ACCENT = 0xFF4DA3FF;
	public static final int ACCENT_DIM = 0xFF2D5E94;
	public static final int TEXT = 0xFFE8ECF1;
	public static final int TEXT_DIM = 0xFF98A3B0;
	public static final int TEXT_MUTED = 0xFF66717D;
	public static final int TRACK = 0xFF2B333D;
	public static final int KNOB = 0xFFF2F5F8;
	public static final int WARNING = 0xFFFFB547;
	public static final int DANGER = 0xFFFF6B6B;
	public static final int SUCCESS = 0xFF5BD68A;
	public static final int TOOLTIP_BG = 0xF5181D24;

	public static final int HEADER_HEIGHT = 34;
	public static final int FOOTER_HEIGHT = 30;
	public static final int ROW_HEIGHT = 24;
	public static final int PADDING = 8;

	private Theme() {
	}

	/** Linear blend between two ARGB colors. */
	public static int mix(int a, int b, float t) {
		t = Math.max(0.0f, Math.min(1.0f, t));
		int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
				| ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}
}
