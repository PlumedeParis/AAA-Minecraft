package com.aetherrealism.gui.widget;

import com.aetherrealism.config.AetherConfig;
import com.aetherrealism.gui.Setting;
import com.aetherrealism.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scrollable list of setting rows with inline controls: toggle switches, sliders, dropdowns, buttons and
 * read-only values. Supports mouse, wheel, drag and full keyboard control (arrows, Enter/Space, Shift for big
 * slider steps, Page Up/Down, Home/End).
 */
public class SettingsList extends BaseWidget {
	/** Callbacks into the owning screen. */
	public interface Host {
		AetherConfig working();

		/** Applies {@code change} to the working config, handling expensive-effect confirmation and the preset label. */
		void edit(Setting setting, boolean enabling, Runnable change);

		void runAction(Setting.Action action);
	}

	private static final int ROW = Theme.ROW_HEIGHT;
	private static final int SCROLLBAR = 4;

	private final Host host;
	private List<Setting> rows = new ArrayList<>();
	private boolean showCategory;
	private float scroll;
	private float targetScroll;
	private int selected = -1;
	private Setting.Slider dragging;
	private Setting.Choice<?> dropdown;
	private int dropX, dropY, dropW, dropH;
	private final Map<Setting, ToggleSwitch> switches = new IdentityHashMap<>();
	private String emptyMessage = "";

	public SettingsList(Host host) {
		super(0, 0, 0, 0);
		this.host = host;
	}

	public void setRows(List<Setting> rows, boolean showCategory, boolean resetScroll) {
		this.rows = rows;
		this.showCategory = showCategory;
		this.dropdown = null;
		this.dragging = null;

		if (resetScroll) {
			scroll = targetScroll = 0;
			selected = -1;
		}

		clampScroll();
	}

	public void setEmptyMessage(String message) {
		this.emptyMessage = message;
	}

	public boolean hasOpenDropdown() {
		return dropdown != null;
	}

	public void closeDropdown() {
		dropdown = null;
	}

	public float scroll() {
		return targetScroll;
	}

	public void setScroll(float value) {
		scroll = targetScroll = value;
		clampScroll();
	}

	private int contentHeight() {
		return rows.size() * ROW + 6;
	}

	private void clampScroll() {
		float max = Math.max(0, contentHeight() - height);
		targetScroll = Math.max(0, Math.min(max, targetScroll));
		scroll = Math.max(0, Math.min(max, scroll));
	}

	private int controlWidth() {
		return Math.max(90, Math.min(170, (int) (width * 0.42f)));
	}

	private int controlX() {
		return x + width - controlWidth() - 8 - SCROLLBAR;
	}

	private int rowTop(int index) {
		return y + 3 + index * ROW - Math.round(scroll);
	}

	private int rowAt(double mouseY) {
		int index = (int) Math.floor((mouseY - y - 3 + scroll) / ROW);
		return index >= 0 && index < rows.size() ? index : -1;
	}

	/** Setting under the mouse for tooltips, or null. */
	public Setting hoveredSetting(double mouseX, double mouseY) {
		if (dropdown != null || dragging != null || !super.isMouseOver(mouseX, mouseY)) {
			return null;
		}

		int index = rowAt(mouseY);
		return index >= 0 ? rows.get(index) : null;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		if (dropdown != null && mouseX >= dropX && mouseX < dropX + dropW && mouseY >= dropY && mouseY < dropY + dropH) {
			return true;
		}

		return super.isMouseOver(mouseX, mouseY);
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		scroll += (targetScroll - scroll) * 0.45f;

		if (Math.abs(targetScroll - scroll) < 0.5f) {
			scroll = targetScroll;
		}

		AetherConfig c = host.working();
		g.fill(x, y, x + width, y + height, Theme.PANEL);
		g.enableScissor(x, y, x + width, y + height);

		if (rows.isEmpty()) {
			g.drawString(font, emptyMessage, x + (width - font.width(emptyMessage)) / 2, y + 20, Theme.TEXT_DIM, false);
		}

		int hoveredIndex = dropdown == null && super.isMouseOver(mouseX, mouseY) ? rowAt(mouseY) : -1;

		for (int i = 0; i < rows.size(); i++) {
			int top = rowTop(i);

			if (top + ROW < y || top > y + height) {
				continue;
			}

			renderRow(g, c, rows.get(i), i, top, i == hoveredIndex, mouseX, mouseY);
		}

		g.disableScissor();
		renderScrollbar(g);

		if (focused && dropdown == null) {
			border(g, x, y, x + width, y + height, Theme.ACCENT_DIM);
		}
	}

	private void renderScrollbar(GuiGraphics g) {
		int content = contentHeight();

		if (content <= height) {
			return;
		}

		int trackX = x + width - SCROLLBAR - 2;
		g.fill(trackX, y + 2, trackX + SCROLLBAR, y + height - 2, Theme.PANEL_ALT);
		int thumbH = Math.max(16, (int) ((float) height * height / content));
		int maxScroll = content - height;
		int thumbY = y + 2 + (int) ((height - 4 - thumbH) * (scroll / maxScroll));
		g.fill(trackX, thumbY, trackX + SCROLLBAR, thumbY + thumbH, Theme.BORDER);
	}

	private void renderRow(GuiGraphics g, AetherConfig c, Setting s, int index, int top, boolean hovered, int mouseX, int mouseY) {
		int left = x + 10;
		int right = x + width - 8 - SCROLLBAR;
		int textY = top + (ROW - font.lineHeight) / 2 + 1;

		if (s instanceof Setting.Header header) {
			g.drawString(font, fit(header.label.toUpperCase(java.util.Locale.ROOT), width / 2), left, textY + 2, Theme.ACCENT, false);
			String r = header.right.get();

			if (!r.isEmpty()) {
				g.drawString(font, r, right - font.width(r), textY + 2, Theme.TEXT_MUTED, false);
			}

			g.fill(left, top + ROW - 2, right, top + ROW - 1, Theme.BORDER);
			return;
		}

		boolean enabled = s.isEnabled(c);

		if (index == selected && focused) {
			g.fill(x + 2, top, right + 2, top + ROW, Theme.ROW_SELECTED);
		} else if (hovered) {
			g.fill(x + 2, top, right + 2, top + ROW, Theme.ROW_HOVER);
		}

		// Impact dot.
		if (s.impact != com.aetherrealism.gui.Impact.NONE) {
			g.fill(x + 4, top + ROW / 2 - 2, x + 7, top + ROW / 2 + 1, s.impact.color);
		}

		int cx = controlX();
		int cw = controlWidth();
		String label = (showCategory ? s.category.displayName + " › " : "") + s.label;
		String badge = s.experimental ? " EXPERIMENTAL" : "";

		if (s instanceof Setting.Info info) {
			String value = info.value.apply(c);
			int valueMax = Math.max(60, (right - left) - Math.min(font.width(label) + 12, (right - left) / 2));
			String shown = fit(value, valueMax);
			int labelMax = right - left - font.width(shown) - 12;
			g.drawString(font, fit(label, labelMax), left, textY, Theme.TEXT_DIM, false);
			g.drawString(font, shown, right - font.width(shown), textY, info.color.apply(c), false);
			return;
		}

		int labelMax = cx - left - 8 - (badge.isEmpty() ? 0 : font.width(badge));
		String fitted = fit(label, labelMax);
		g.drawString(font, fitted, left, textY, enabled ? Theme.TEXT : Theme.TEXT_MUTED, false);

		if (!badge.isEmpty()) {
			g.drawString(font, badge, left + font.width(fitted), textY, Theme.WARNING, false);
		}

		int controlY = top + 4;
		int controlH = ROW - 8;
		boolean controlHovered = hovered && mouseX >= cx && mouseX < cx + cw;

		switch (s) {
			case Setting.Toggle t -> {
				boolean on = t.get(c);
				ToggleSwitch sw = switches.computeIfAbsent(t, k -> {
					ToggleSwitch created = new ToggleSwitch();
					created.snap(on);
					return created;
				});
				int sw_w = 26;
				String state = on ? "ON" : "OFF";
				g.drawString(font, state, cx + cw - sw_w - 6 - font.width(state), textY, enabled ? (on ? Theme.TEXT : Theme.TEXT_DIM) : Theme.TEXT_MUTED, false);
				sw.render(g, cx + cw - sw_w, controlY + 1, sw_w, controlH - 2, on, enabled, hovered);
			}
			case Setting.Slider sl -> {
				String value = sl.format(c);
				int valueW = Math.max(38, font.width(value) + 4);
				int trackX = cx;
				int trackW = cw - valueW - 6;
				int trackY = top + ROW / 2 - 1;
				float t = Math.max(0, Math.min(1, sl.normalized(c)));
				g.fill(trackX, trackY, trackX + trackW, trackY + 3, Theme.TRACK);
				g.fill(trackX, trackY, trackX + Math.round(trackW * t), trackY + 3, enabled ? Theme.ACCENT : Theme.TEXT_MUTED);
				int knobX = trackX + Math.round(trackW * t) - 3;
				int knobColor = !enabled ? Theme.TEXT_MUTED : (controlHovered || dragging == sl ? 0xFFFFFFFF : Theme.KNOB);
				g.fill(knobX, trackY - 4, knobX + 6, trackY + 7, knobColor);
				g.drawString(font, value, cx + cw - font.width(value), textY, enabled ? Theme.TEXT : Theme.TEXT_MUTED, false);
			}
			case Setting.Choice<?> ch -> {
				int bg = enabled ? (controlHovered || dropdown == ch ? Theme.ROW_HOVER : Theme.PANEL_ALT) : Theme.PANEL;
				g.fill(cx, controlY, cx + cw, controlY + controlH, bg);
				border(g, cx, controlY, cx + cw, controlY + controlH, dropdown == ch ? Theme.ACCENT : Theme.BORDER);
				String value = fit(ch.display(c), cw - 18);
				g.drawString(font, value, cx + 5, textY, enabled ? Theme.TEXT : Theme.TEXT_MUTED, false);
				g.drawString(font, "▾", cx + cw - 10, textY, Theme.TEXT_DIM, false);
			}
			case Setting.Action a -> {
				int bw = Math.min(cw, font.width(a.buttonText) + 20);
				int bx = cx + cw - bw;
				g.fill(bx, controlY, bx + bw, controlY + controlH, controlHovered ? Theme.ACCENT : Theme.ACCENT_DIM);
				g.drawString(font, fit(a.buttonText, bw - 6), bx + (bw - Math.min(bw - 6, font.width(a.buttonText))) / 2, textY, Theme.TEXT, false);
			}
			default -> {
			}
		}
	}

	/** Dropdown popups are drawn after everything else so they overlap neighbouring rows and the footer. */
	public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
		if (dropdown == null) {
			return;
		}

		AetherConfig c = host.working();
		int current = dropdown.index(c);
		g.fill(dropX - 1, dropY - 1, dropX + dropW + 1, dropY + dropH + 1, Theme.ACCENT_DIM);
		g.fill(dropX, dropY, dropX + dropW, dropY + dropH, Theme.PANEL_ALT);

		for (int i = 0; i < dropdown.values.length; i++) {
			int oy = dropY + i * 16;
			boolean hovered = mouseX >= dropX && mouseX < dropX + dropW && mouseY >= oy && mouseY < oy + 16;

			if (hovered) {
				g.fill(dropX, oy, dropX + dropW, oy + 16, Theme.ROW_HOVER);
			}

			String name = optionName(dropdown, i);
			g.drawString(font, fit(name, dropW - 16), dropX + 6, oy + 4, i == current ? Theme.ACCENT : Theme.TEXT, false);

			if (i == current) {
				g.drawString(font, "✓", dropX + dropW - 11, oy + 4, Theme.ACCENT, false);
			}
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static String optionName(Setting.Choice choice, int i) {
		return (String) choice.names.apply(choice.values[i]);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();

		if (dropdown != null) {
			if (mx >= dropX && mx < dropX + dropW && my >= dropY && my < dropY + dropH) {
				int option = (int) ((my - dropY) / 16);
				Setting.Choice<?> ch = dropdown;
				dropdown = null;
				playClick();
				host.edit(ch, false, () -> ch.setIndex(host.working(), option));
			} else {
				dropdown = null;
			}

			return true;
		}

		if (event.button() != 0 || !super.isMouseOver(mx, my)) {
			return false;
		}

		// Scrollbar: jump to the clicked position.
		if (mx >= x + width - SCROLLBAR - 3 && contentHeight() > height) {
			float rel = (float) ((my - y) / height);
			targetScroll = rel * (contentHeight() - height);
			clampScroll();
			return true;
		}

		int index = rowAt(my);

		if (index < 0) {
			return false;
		}

		selected = index;
		return activate(rows.get(index), mx, false);
	}

	/** Performs the primary interaction for a row (mouse click, Enter or Space). */
	private boolean activate(Setting s, double mx, boolean keyboard) {
		AetherConfig c = host.working();

		if (!s.isEnabled(c) && s.isValue()) {
			return true;
		}

		int cx = controlX();
		int cw = controlWidth();

		switch (s) {
			case Setting.Toggle t -> {
				boolean next = !t.get(c);
				playClick();
				host.edit(t, next, () -> t.set(host.working(), next));
				return true;
			}
			case Setting.Slider sl -> {
				if (keyboard) {
					return true;
				}

				String value = sl.format(c);
				int valueW = Math.max(38, font.width(value) + 4);
				int trackW = cw - valueW - 6;

				if (mx >= cx - 4 && mx <= cx + trackW + 4) {
					dragging = sl;
					setSliderFromMouse(sl, mx);
				}

				return true;
			}
			case Setting.Choice<?> ch -> {
				playClick();
				openDropdown(ch);
				return true;
			}
			case Setting.Action a -> {
				playClick();
				host.runAction(a);
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	private void openDropdown(Setting.Choice<?> ch) {
		int index = rows.indexOf(ch);

		if (index < 0) {
			return;
		}

		dropdown = ch;
		dropW = controlWidth();
		dropX = controlX();
		dropH = ch.values.length * 16;
		int below = rowTop(index) + ROW - 3;
		int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		dropY = below + dropH > screenH - 4 ? rowTop(index) + 3 - dropH : below;
	}

	private void setSliderFromMouse(Setting.Slider sl, double mx) {
		AetherConfig c = host.working();
		String value = sl.format(c);
		int valueW = Math.max(38, font.width(value) + 4);
		int trackW = controlWidth() - valueW - 6;
		float t = (float) ((mx - controlX()) / Math.max(1, trackW));
		t = Math.max(0, Math.min(1, t));
		float v = sl.min + t * (sl.max - sl.min);
		host.edit(sl, false, () -> sl.set(host.working(), v));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (dragging != null && event.button() == 0) {
			setSliderFromMouse(dragging, event.x());
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null && event.button() == 0) {
			dragging = null;
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (dropdown != null) {
			dropdown = null;
		}

		targetScroll -= (float) (verticalAmount * ROW * 1.5);
		clampScroll();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!focused) {
			return false;
		}

		int key = event.key();
		boolean shift = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;

		if (dropdown != null) {
			AetherConfig c = host.working();
			Setting.Choice<?> ch = dropdown;

			if (key == GLFW.GLFW_KEY_ESCAPE) {
				dropdown = null;
				return true;
			}

			if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
				int next = ch.index(c) + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1);
				host.edit(ch, false, () -> ch.setIndex(host.working(), next));
				return true;
			}

			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_KP_ENTER) {
				dropdown = null;
				return true;
			}
		}

		switch (key) {
			case GLFW.GLFW_KEY_DOWN -> {
				moveSelection(1);
				return true;
			}
			case GLFW.GLFW_KEY_UP -> {
				moveSelection(-1);
				return true;
			}
			case GLFW.GLFW_KEY_PAGE_DOWN -> {
				moveSelection(Math.max(1, height / ROW - 1));
				return true;
			}
			case GLFW.GLFW_KEY_PAGE_UP -> {
				moveSelection(-Math.max(1, height / ROW - 1));
				return true;
			}
			case GLFW.GLFW_KEY_HOME -> {
				selected = -1;
				moveSelection(1);
				return true;
			}
			case GLFW.GLFW_KEY_END -> {
				selected = rows.size();
				moveSelection(-1);
				return true;
			}
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
				return adjust(key == GLFW.GLFW_KEY_RIGHT ? 1 : -1, shift);
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
				if (selected >= 0 && selected < rows.size()) {
					return activate(rows.get(selected), 0, true);
				}

				return false;
			}
			default -> {
				return false;
			}
		}
	}

	private boolean adjust(int direction, boolean big) {
		if (selected < 0 || selected >= rows.size()) {
			return false;
		}

		Setting s = rows.get(selected);
		AetherConfig c = host.working();

		if (!s.isEnabled(c)) {
			return true;
		}

		switch (s) {
			case Setting.Toggle t -> {
				boolean next = direction > 0;

				if (t.get(c) != next) {
					host.edit(t, next, () -> t.set(host.working(), next));
				}
			}
			case Setting.Slider sl -> {
				float step = sl.step * (big ? 10 : 1);
				float v = sl.get(c) + direction * step;
				host.edit(sl, false, () -> sl.set(host.working(), v));
			}
			case Setting.Choice<?> ch -> {
				int next = ch.index(c) + direction;
				host.edit(ch, false, () -> ch.setIndex(host.working(), next));
			}
			default -> {
			}
		}

		return true;
	}

	private void moveSelection(int delta) {
		if (rows.isEmpty()) {
			return;
		}

		int index = selected;
		int step = delta > 0 ? 1 : -1;
		int remaining = Math.abs(delta);

		while (remaining > 0) {
			int next = index + step;

			while (next >= 0 && next < rows.size() && rows.get(next) instanceof Setting.Header) {
				next += step;
			}

			if (next < 0 || next >= rows.size()) {
				break;
			}

			index = next;
			remaining--;
		}

		if (index < 0 || index >= rows.size()) {
			return;
		}

		selected = index;
		int top = 3 + index * ROW;

		if (top < targetScroll) {
			targetScroll = top - 3;
		} else if (top + ROW > targetScroll + height) {
			targetScroll = top + ROW - height + 3;
		}

		clampScroll();
	}

	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);

		if (focused && selected < 0) {
			moveSelection(1);
		}
	}

	@Override
	protected Component narrationTitle() {
		if (selected >= 0 && selected < rows.size()) {
			Setting s = rows.get(selected);
			return Component.literal(s.label + ". " + s.description);
		}

		return Component.literal("Settings list");
	}
}
