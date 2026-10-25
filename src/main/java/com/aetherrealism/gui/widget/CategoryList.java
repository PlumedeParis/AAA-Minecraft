package com.aetherrealism.gui.widget;

import com.aetherrealism.gui.Category;
import com.aetherrealism.gui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Sidebar with the settings categories. Collapses to icons only on narrow screens. */
public class CategoryList extends BaseWidget {
	private static final int ENTRY_HEIGHT = 20;
	private final Supplier<Category> selected;
	private final Consumer<Category> onSelect;
	private final float[] hover = new float[Category.values().length];
	private boolean compact;

	public CategoryList(Supplier<Category> selected, Consumer<Category> onSelect) {
		super(0, 0, 0, 0);
		this.selected = selected;
		this.onSelect = onSelect;
	}

	public void setCompact(boolean compact) {
		this.compact = compact;
	}

	public Category hoveredCategory(double mouseX, double mouseY) {
		if (!isMouseOver(mouseX, mouseY)) {
			return null;
		}

		int index = (int) ((mouseY - y - 4) / ENTRY_HEIGHT);
		Category[] all = Category.values();
		return index >= 0 && index < all.length ? all[index] : null;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		g.fill(x, y, x + width, y + height, Theme.SIDEBAR);
		g.fill(x + width - 1, y, x + width, y + height, Theme.BORDER);
		g.enableScissor(x, y, x + width, y + height);
		Category[] all = Category.values();
		Category hovered = hoveredCategory(mouseX, mouseY);

		for (int i = 0; i < all.length; i++) {
			Category cat = all[i];
			int ey = y + 4 + i * ENTRY_HEIGHT;
			boolean isSelected = cat == selected.get();
			hover[i] += ((cat == hovered ? 1.0f : 0.0f) - hover[i]) * 0.3f;

			if (isSelected) {
				g.fill(x, ey, x + width - 1, ey + ENTRY_HEIGHT, Theme.ROW_SELECTED);
				g.fill(x, ey, x + 2, ey + ENTRY_HEIGHT, Theme.ACCENT);
			} else if (hover[i] > 0.02f) {
				g.fill(x, ey, x + width - 1, ey + ENTRY_HEIGHT, Theme.mix(Theme.SIDEBAR, Theme.ROW_HOVER, hover[i]));
			}

			int textY = ey + (ENTRY_HEIGHT - font.lineHeight) / 2 + 1;
			int iconColor = isSelected ? Theme.ACCENT : Theme.TEXT_DIM;

			if (compact) {
				g.drawString(font, cat.icon, x + (width - font.width(cat.icon)) / 2, textY, iconColor, false);
			} else {
				g.drawString(font, cat.icon, x + 8, textY, iconColor, false);
				g.drawString(font, fit(cat.displayName, width - 30), x + 22, textY, isSelected ? Theme.TEXT : Theme.TEXT_DIM, false);
			}
		}

		if (focused) {
			border(g, x, y, x + width, y + height, Theme.ACCENT_DIM);
		}

		g.disableScissor();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Category cat = event.button() == 0 ? hoveredCategory(event.x(), event.y()) : null;

		if (cat != null) {
			playClick();
			onSelect.accept(cat);
			return true;
		}

		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!focused) {
			return false;
		}

		Category[] all = Category.values();
		int index = selected.get().ordinal();

		if (event.key() == GLFW.GLFW_KEY_DOWN) {
			onSelect.accept(all[(index + 1) % all.length]);
			return true;
		}

		if (event.key() == GLFW.GLFW_KEY_UP) {
			onSelect.accept(all[(index - 1 + all.length) % all.length]);
			return true;
		}

		return false;
	}

	@Override
	protected Component narrationTitle() {
		return Component.literal("Category: " + selected.get().displayName);
	}
}
