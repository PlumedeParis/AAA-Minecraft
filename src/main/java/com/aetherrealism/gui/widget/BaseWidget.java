package com.aetherrealism.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/** Minimal widget base implementing the vanilla GUI interfaces directly (same approach as Sodium's options GUI). */
public abstract class BaseWidget implements Renderable, GuiEventListener, NarratableEntry {
	protected final Font font = Minecraft.getInstance().font;
	protected int x;
	protected int y;
	protected int width;
	protected int height;
	protected boolean focused;

	protected BaseWidget(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	@Override
	public ScreenRectangle getRectangle() {
		return new ScreenRectangle(x, y, width, height);
	}

	@Override
	public boolean isFocused() {
		return focused;
	}

	@Override
	public void setFocused(boolean focused) {
		this.focused = focused;
	}

	@Override
	public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
		return !this.isFocused() ? ComponentPath.leaf(this) : null;
	}

	@Override
	public NarrationPriority narrationPriority() {
		return focused ? NarrationPriority.FOCUSED : NarrationPriority.NONE;
	}

	@Override
	public void updateNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, narrationTitle());
	}

	protected Component narrationTitle() {
		return Component.empty();
	}

	protected static void playClick() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
	}

	protected static void border(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
		g.fill(x1, y1, x2, y1 + 1, color);
		g.fill(x1, y2 - 1, x2, y2, color);
		g.fill(x1, y1, x1 + 1, y2, color);
		g.fill(x2 - 1, y1, x2, y2, color);
	}

	/** Trims text with an ellipsis so it never overflows its column. */
	protected String fit(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}

		String ellipsis = "...";
		int target = maxWidth - font.width(ellipsis);

		if (target <= 0) {
			return "";
		}

		int end = text.length();

		while (end > 0 && font.width(text.substring(0, end)) > target) {
			end--;
		}

		return text.substring(0, end).trim() + ellipsis;
	}
}
