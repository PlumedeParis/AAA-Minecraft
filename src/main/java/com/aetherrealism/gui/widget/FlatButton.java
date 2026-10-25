package com.aetherrealism.gui.widget;

import com.aetherrealism.gui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;

/** Dark flat button with a hover fade and an optional accent/danger style. */
public class FlatButton extends BaseWidget {
	public enum Style { NORMAL, PRIMARY, DANGER }

	private final String text;
	private final Runnable onPress;
	private final Style style;
	private BooleanSupplier active = () -> true;
	private float hover;

	public FlatButton(String text, Style style, Runnable onPress) {
		super(0, 0, 80, 20);
		this.text = text;
		this.style = style;
		this.onPress = onPress;
	}

	public FlatButton activeWhen(BooleanSupplier active) {
		this.active = active;
		return this;
	}

	public int preferredWidth() {
		return font.width(text) + 20;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		boolean enabled = active.getAsBoolean();
		boolean hovered = enabled && isMouseOver(mouseX, mouseY);
		hover += ((hovered || focused ? 1.0f : 0.0f) - hover) * 0.3f;

		int base = switch (style) {
			case PRIMARY -> Theme.ACCENT_DIM;
			case DANGER -> 0xFF5A2626;
			default -> Theme.PANEL_ALT;
		};
		int hot = switch (style) {
			case PRIMARY -> Theme.ACCENT;
			case DANGER -> 0xFF8A3333;
			default -> Theme.ROW_HOVER;
		};

		g.fill(x, y, x + width, y + height, enabled ? Theme.mix(base, hot, hover) : Theme.PANEL);
		border(g, x, y, x + width, y + height, focused ? Theme.ACCENT : Theme.BORDER);
		String label = fit(text, width - 8);
		int color = enabled ? Theme.TEXT : Theme.TEXT_MUTED;
		g.drawString(font, label, x + (width - font.width(label)) / 2, y + (height - font.lineHeight) / 2 + 1, color, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && isMouseOver(event.x(), event.y()) && active.getAsBoolean()) {
			playClick();
			onPress.run();
			return true;
		}

		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (focused && active.getAsBoolean() && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_SPACE
				|| event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
			playClick();
			onPress.run();
			return true;
		}

		return false;
	}

	@Override
	protected Component narrationTitle() {
		return Component.literal(text);
	}
}
