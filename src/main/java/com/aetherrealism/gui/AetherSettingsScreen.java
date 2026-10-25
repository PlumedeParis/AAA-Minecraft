package com.aetherrealism.gui;

import com.aetherrealism.config.AetherConfig;
import com.aetherrealism.config.ConfigManager;
import com.aetherrealism.config.Preset;
import com.aetherrealism.gui.widget.CategoryList;
import com.aetherrealism.gui.widget.FlatButton;
import com.aetherrealism.gui.widget.SettingsList;
import com.aetherrealism.gui.widget.ToggleSwitch;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Aether Realism graphics settings screen.
 *
 * <p>Edits go to a staged copy of the config. <b>Apply</b> commits and saves it (effects update immediately; only
 * terrain-baked settings re-mesh chunks), <b>Cancel</b> discards it, <b>Done</b> applies and closes. Enabling an
 * expensive effect asks for confirmation first; editing a quality setting switches the preset label to Custom.
 */
public class AetherSettingsScreen extends Screen implements SettingsList.Host {
	private final @Nullable Screen parent;
	private AetherConfig working;
	private Category category = Category.OVERVIEW;
	private String query = "";
	private float savedScroll;

	private CategoryList categoryList;
	private SettingsList settingsList;
	private EditBox search;
	private final ToggleSwitch masterSwitch = new ToggleSwitch();
	private int masterX, masterY, masterW, masterH;

	public AetherSettingsScreen(@Nullable Screen parent) {
		super(Component.literal("Aether Realism - Video Enhancements"));
		this.parent = parent;
		this.working = ConfigManager.get().copy();
		this.masterSwitch.snap(working.masterEnabled);
	}

	@Override
	public AetherConfig working() {
		return working;
	}

	private boolean dirtyCache;

	/** Whether the staged config differs from the saved one (recomputed once per frame). */
	private boolean dirty() {
		return dirtyCache;
	}

	private boolean computeDirty() {
		return !ConfigManager.GSON.toJson(working).equals(ConfigManager.GSON.toJson(ConfigManager.get()));
	}

	// ------------------------------------------------------------------ layout

	@Override
	protected void init() {
		// init() runs again on resize; keep the user's place in the list.
		if (settingsList != null) {
			savedScroll = settingsList.scroll();
		}

		int sidebarW = width < 420 ? 30 : Math.max(96, Math.min(150, (int) (width * 0.2f)));
		int top = Theme.HEADER_HEIGHT;
		int bottom = height - Theme.FOOTER_HEIGHT;
		int searchH = 18;

		search = new EditBox(font, 6, top + 6, sidebarW - 12, searchH, Component.literal("Search settings"));
		search.setMaxLength(64);
		search.setValue(query);
		search.setHint(Component.literal(width < 420 ? "⌕" : "Search..."));
		search.setResponder(this::onSearch);
		addRenderableWidget(search);

		categoryList = new CategoryList(() -> category, this::selectCategory);
		categoryList.setBounds(0, top + searchH + 12, sidebarW, bottom - top - searchH - 12);
		categoryList.setCompact(sidebarW < 60);
		addRenderableWidget(categoryList);

		settingsList = new SettingsList(this);
		settingsList.setBounds(sidebarW, top, width - sidebarW, bottom - top);
		addRenderableWidget(settingsList);
		refreshRows(false);
		settingsList.setScroll(savedScroll);

		// Footer buttons, right-aligned; collapse labels on narrow screens.
		boolean narrow = width < 520;
		List<FlatButton> right = List.of(
				new FlatButton("Done", FlatButton.Style.PRIMARY, this::applyAndClose),
				new FlatButton("Apply", FlatButton.Style.NORMAL, this::apply).activeWhen(this::dirty),
				new FlatButton("Cancel", FlatButton.Style.NORMAL, this::onClose));
		List<FlatButton> left = List.of(
				new FlatButton(narrow ? "Reset" : "Reset category", FlatButton.Style.NORMAL, this::resetCategory),
				new FlatButton(narrow ? "Defaults" : "Reset all...", FlatButton.Style.DANGER, this::confirmResetAll));

		int by = height - Theme.FOOTER_HEIGHT + 5;
		int bx = width - 6;

		for (FlatButton b : right) {
			int bw = Math.max(52, b.preferredWidth());
			bx -= bw;
			b.setBounds(bx, by, bw, 20);
			bx -= 4;
			addRenderableWidget(b);
		}

		int lx = 6;

		for (FlatButton b : left) {
			int bw = Math.max(52, b.preferredWidth());

			if (lx + bw > bx - 4) {
				break; // never overlap the right-hand group
			}

			b.setBounds(lx, by, bw, 20);
			lx += bw + 4;
			addRenderableWidget(b);
		}

		masterW = 30;
		masterH = 14;
		masterX = width - masterW - 10;
		masterY = (Theme.HEADER_HEIGHT - masterH) / 2;
	}

	private void selectCategory(Category next) {
		if (!query.isEmpty()) {
			query = "";
			search.setValue("");
		}

		category = next;
		refreshRows(true);
	}

	private void onSearch(String text) {
		query = text.trim();
		refreshRows(true);
	}

	private void refreshRows(boolean resetScroll) {
		List<Setting> rows;

		if (query.isEmpty()) {
			rows = SettingsRegistry.forCategory(category);
			settingsList.setEmptyMessage("");
		} else {
			rows = new ArrayList<>();

			for (Setting s : SettingsRegistry.all()) {
				if (!(s instanceof Setting.Header) && s.matches(query)) {
					rows.add(s);
				}
			}

			settingsList.setEmptyMessage("No settings match \"" + query + "\"");
		}

		settingsList.setRows(rows, !query.isEmpty(), resetScroll);
	}

	// ------------------------------------------------------------------ editing

	@Override
	public void edit(Setting setting, boolean enabling, Runnable change) {
		if (enabling && setting.expensive) {
			minecraft.setScreen(new ConfirmScreen(confirmed -> {
				if (confirmed) {
					applyEdit(setting, change);
				}

				minecraft.setScreen(this);
			}, Component.literal("Enable " + setting.label + "?"),
					Component.literal(setting.impact.text + ". " + setting.description
							+ " You can turn it off again at any time, or press F9 to disable all effects."),
					Component.literal("Enable"), Component.literal("Cancel")));
			return;
		}

		applyEdit(setting, change);
	}

	private void applyEdit(Setting setting, Runnable change) {
		Preset before = working.preset;
		change.run();
		working.validate();

		if (!setting.presetNeutral && working.preset == before && before != Preset.CUSTOM) {
			working.preset = Preset.CUSTOM;
		}

		onWorkingChanged(false);
	}

	/** Called after the working copy changed outside a normal row edit (presets, resets). */
	public void onWorkingChanged(boolean resetScroll) {
		if (resetScroll) {
			refreshRows(true);
		}
	}

	@Override
	public void runAction(Setting.Action action) {
		action.action.accept(this, working);
	}

	private void apply() {
		ConfigManager.commit(working.copy());
	}

	private void applyAndClose() {
		if (computeDirty()) {
			apply();
		}

		minecraft.setScreen(parent);
	}

	private void resetCategory() {
		for (Setting s : SettingsRegistry.forCategory(category)) {
			if (s.isValue() && !(s instanceof Setting.Choice<?> ch && ch.label.equals("Graphics preset"))) {
				s.copy(AetherConfig.DEFAULTS, working);
			}
		}

		if (category != Category.OVERVIEW && category != Category.ADVANCED) {
			working.preset = Preset.CUSTOM;
		}

		working.validate();
	}

	public void confirmResetAll() {
		minecraft.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				working = new AetherConfig();
				masterSwitch.snap(working.masterEnabled);
			}

			minecraft.setScreen(this);
		}, Component.literal("Reset all Aether Realism settings?"),
				Component.literal("Every setting returns to its default (High preset). Press Apply or Done to save."),
				Component.literal("Reset"), Component.literal("Cancel")));
	}

	@Override
	public void onClose() {
		// Cancel: the staged copy is simply dropped.
		minecraft.setScreen(parent);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (settingsList.hasOpenDropdown() && !settingsList.isMouseOver(event.x(), event.y())) {
			settingsList.closeDropdown();
			return true;
		}

		if (event.button() == 0 && event.x() >= masterX && event.x() < masterX + masterW
				&& event.y() >= masterY && event.y() < masterY + masterH) {
			Setting master = SettingsRegistry.forCategory(Category.OVERVIEW).stream()
					.filter(s -> s instanceof Setting.Toggle && s.label.equals("Master effects")).findFirst().orElse(null);

			if (master != null) {
				boolean next = !working.masterEnabled;
				edit(master, next, () -> working.masterEnabled = next);
				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		boolean ctrl = (event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;

		if (ctrl && event.key() == GLFW.GLFW_KEY_F) {
			setFocused(search);
			return true;
		}

		if (ctrl && event.key() == GLFW.GLFW_KEY_S) {
			apply();
			return true;
		}

		if (event.key() == GLFW.GLFW_KEY_ESCAPE && settingsList.hasOpenDropdown()) {
			settingsList.closeDropdown();
			return true;
		}

		if (!search.isFocused()) {
			if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN && ctrl) {
				Category[] all = Category.values();
				selectCategory(all[(category.ordinal() + 1) % all.length]);
				return true;
			}

			if (event.key() == GLFW.GLFW_KEY_PAGE_UP && ctrl) {
				Category[] all = Category.values();
				selectCategory(all[(category.ordinal() - 1 + all.length) % all.length]);
				return true;
			}
		}

		if (search.isFocused() && (event.key() == GLFW.GLFW_KEY_DOWN || event.key() == GLFW.GLFW_KEY_ENTER)) {
			setFocused(settingsList);
			return true;
		}

		return super.keyPressed(event);
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// Opaque dark background without the vanilla blur, so the panel stays crisp.
		g.fill(0, 0, width, height, Theme.BACKGROUND);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		dirtyCache = computeDirty();

		// Header
		g.fill(0, 0, width, Theme.HEADER_HEIGHT, Theme.HEADER);
		g.fill(0, Theme.HEADER_HEIGHT - 1, width, Theme.HEADER_HEIGHT, Theme.BORDER);
		String title = width < 460 ? "AETHER REALISM" : "AETHER REALISM — VIDEO ENHANCEMENTS";
		g.drawString(font, title, 10, 8, Theme.TEXT, false);
		String sub = "Preset: " + working.preset.displayName + (dirty() ? "   • unsaved changes" : "");
		g.drawString(font, sub, 10, 20, dirty() ? Theme.WARNING : Theme.TEXT_DIM, false);

		String masterLabel = "Master effects  " + (working.masterEnabled ? "ON" : "OFF");
		int labelX = masterX - 8 - font.width(masterLabel);

		if (labelX > font.width(title) + 20) {
			g.drawString(font, masterLabel, labelX, masterY + 3, working.masterEnabled ? Theme.TEXT : Theme.DANGER, false);
		}

		boolean masterHover = mouseX >= masterX && mouseX < masterX + masterW && mouseY >= masterY && mouseY < masterY + masterH;
		masterSwitch.render(g, masterX, masterY, masterW, masterH, working.masterEnabled, true, masterHover);

		// Search area background under the sidebar
		int sidebarW = categoryList.width();
		g.fill(0, Theme.HEADER_HEIGHT, sidebarW, Theme.HEADER_HEIGHT + 30, Theme.SIDEBAR);

		// Footer
		g.fill(0, height - Theme.FOOTER_HEIGHT, width, height, Theme.HEADER);
		g.fill(0, height - Theme.FOOTER_HEIGHT, width, height - Theme.FOOTER_HEIGHT + 1, Theme.BORDER);

		super.render(g, mouseX, mouseY, delta);

		// Overlays: dropdowns and tooltips on top of everything.
		g.nextStratum();
		settingsList.renderOverlay(g, mouseX, mouseY);
		renderTooltip(g, mouseX, mouseY);
	}

	private void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
		List<String> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		Setting hovered = settingsList.hoveredSetting(mouseX, mouseY);

		if (hovered != null && !(hovered instanceof Setting.Header) && !hovered.description.isEmpty()) {
			lines.add(hovered.label);
			colors.add(Theme.ACCENT);
			addWrapped(lines, colors, hovered.description, Theme.TEXT);

			if (hovered.isValue() || hovered.impact != Impact.NONE) {
				lines.add(hovered.impact.text);
				colors.add(hovered.impact.color);
			}

			if (hovered.experimental) {
				lines.add("Experimental: may change or cause visual artifacts.");
				colors.add(Theme.WARNING);
			}

			if (hovered.expensive) {
				lines.add("Asks for confirmation before enabling.");
				colors.add(Theme.TEXT_DIM);
			}

			if (hovered.note != null) {
				addWrapped(lines, colors, hovered.note, Theme.WARNING);
			}

			if (!hovered.isEnabled(working) && hovered.isValue()) {
				lines.add("Disabled: enable the parent option first.");
				colors.add(Theme.TEXT_MUTED);
			}
		} else {
			Category cat = categoryList.hoveredCategory(mouseX, mouseY);

			if (cat != null) {
				lines.add(cat.displayName);
				colors.add(Theme.ACCENT);
				addWrapped(lines, colors, cat.description, Theme.TEXT);
			} else if (mouseX >= masterX - 4 && mouseX < masterX + masterW && mouseY >= masterY && mouseY < masterY + masterH) {
				lines.add("Master effects");
				colors.add(Theme.ACCENT);
				addWrapped(lines, colors, "Turns every Aether Realism effect on or off. F9 toggles it in game.", Theme.TEXT);
			}
		}

		if (lines.isEmpty()) {
			return;
		}

		int w = 0;

		for (String line : lines) {
			w = Math.max(w, font.width(line));
		}

		int h = lines.size() * (font.lineHeight + 2) + 6;
		int tx = mouseX + 12;
		int ty = mouseY + 10;

		if (tx + w + 10 > width) {
			tx = Math.max(2, mouseX - w - 16);
		}

		if (ty + h > height) {
			ty = Math.max(2, height - h - 2);
		}

		g.fill(tx - 4, ty - 4, tx + w + 6, ty + h - 2, Theme.TOOLTIP_BG);
		g.fill(tx - 4, ty - 4, tx + w + 6, ty - 3, Theme.ACCENT_DIM);

		for (int i = 0; i < lines.size(); i++) {
			g.drawString(font, lines.get(i), tx + 1, ty + i * (font.lineHeight + 2), colors.get(i), false);
		}
	}

	private void addWrapped(List<String> lines, List<Integer> colors, String text, int color) {
		int maxWidth = Math.min(260, Math.max(120, width / 2 - 20));

		for (FormattedCharSequence seq : font.split(Component.literal(text), maxWidth)) {
			StringBuilder sb = new StringBuilder();
			seq.accept((index, style, codePoint) -> {
				sb.appendCodePoint(codePoint);
				return true;
			});
			lines.add(sb.toString());
			colors.add(color);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	public String toString() {
		return "AetherSettingsScreen[" + category.name().toLowerCase(Locale.ROOT) + "]";
	}
}
