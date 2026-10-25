package com.aetherrealism.gui.widget;

import com.aetherrealism.gui.Theme;
import net.minecraft.client.gui.GuiGraphics;

/** Animated pill-shaped on/off switch, drawn by its owner (used inside rows and in the header). */
public final class ToggleSwitch {
	private float position;

	/** Draws the switch and advances its knob animation toward {@code on}. */
	public void render(GuiGraphics g, int x, int y, int w, int h, boolean on, boolean enabled, boolean hovered) {
		position += ((on ? 1.0f : 0.0f) - position) * 0.35f;

		if (Math.abs(position - (on ? 1.0f : 0.0f)) < 0.01f) {
			position = on ? 1.0f : 0.0f;
		}

		int track = enabled ? Theme.mix(Theme.TRACK, Theme.ACCENT, position) : Theme.PANEL_ALT;

		if (hovered && enabled) {
			track = Theme.mix(track, 0xFFFFFFFF, 0.08f);
		}

		g.fill(x + 1, y, x + w - 1, y + h, track);
		g.fill(x, y + 1, x + w, y + h - 1, track);

		int knob = h - 4;
		int kx = x + 2 + Math.round((w - knob - 4) * position);
		g.fill(kx, y + 2, kx + knob, y + 2 + knob, enabled ? Theme.KNOB : Theme.TEXT_MUTED);
	}

	public void snap(boolean on) {
		position = on ? 1.0f : 0.0f;
	}
}
