package com.aetherrealism.gui;

import com.aetherrealism.config.AetherConfig;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Describes one row in the settings screen: how to read and write it on a (staged) config, how expensive it is and
 * what to tell the user about it. Rows operate on whatever {@link AetherConfig} the screen passes in, so the same
 * definitions drive editing, per-category reset and search.
 */
public abstract sealed class Setting permits Setting.Toggle, Setting.Slider, Setting.Choice, Setting.Action, Setting.Info, Setting.Header {
	public final Category category;
	public final String label;
	public final String description;
	public Impact impact = Impact.NONE;
	/** Ask for confirmation before enabling. */
	public boolean expensive;
	public boolean experimental;
	/** Controls whose effect is only partial or that rely on another component (shown in the tooltip). */
	public String note;
	/** Settings that are not quality knobs keep the preset label when changed. */
	public boolean presetNeutral;
	/** When present and false, the row is drawn disabled. */
	public Predicate<AetherConfig> enabledWhen;

	protected Setting(Category category, String label, String description) {
		this.category = category;
		this.label = label;
		this.description = description;
	}

	public Setting impact(Impact impact) {
		this.impact = impact;
		return this;
	}

	public Setting expensive() {
		this.expensive = true;
		return this;
	}

	public Setting experimental() {
		this.experimental = true;
		return this;
	}

	public Setting note(String note) {
		this.note = note;
		return this;
	}

	public Setting neutral() {
		this.presetNeutral = true;
		return this;
	}

	public Setting enabledWhen(Predicate<AetherConfig> predicate) {
		this.enabledWhen = predicate;
		return this;
	}

	public boolean isEnabled(AetherConfig c) {
		return enabledWhen == null || enabledWhen.test(c);
	}

	public boolean matches(String query) {
		String q = query.toLowerCase(Locale.ROOT);
		return label.toLowerCase(Locale.ROOT).contains(q) || description.toLowerCase(Locale.ROOT).contains(q)
				|| category.displayName.toLowerCase(Locale.ROOT).contains(q);
	}

	/** Copies this setting's value from {@code source} into {@code target}. */
	public abstract void copy(AetherConfig source, AetherConfig target);

	/** True if this row is an interactive value (not a header, info line or button). */
	public boolean isValue() {
		return true;
	}

	public static final class Toggle extends Setting {
		public final Function<AetherConfig, Boolean> getter;
		public final BiConsumer<AetherConfig, Boolean> setter;

		public Toggle(Category category, String label, String description, Function<AetherConfig, Boolean> getter,
				BiConsumer<AetherConfig, Boolean> setter) {
			super(category, label, description);
			this.getter = getter;
			this.setter = setter;
		}

		public boolean get(AetherConfig c) {
			return getter.apply(c);
		}

		public void set(AetherConfig c, boolean value) {
			setter.accept(c, value);
		}

		@Override
		public void copy(AetherConfig source, AetherConfig target) {
			set(target, get(source));
		}
	}

	public static final class Slider extends Setting {
		public final float min;
		public final float max;
		public final float step;
		public final Function<AetherConfig, Float> getter;
		public final BiConsumer<AetherConfig, Float> setter;
		public final Function<Float, String> formatter;

		public Slider(Category category, String label, String description, float min, float max, float step,
				Function<AetherConfig, Float> getter, BiConsumer<AetherConfig, Float> setter, Function<Float, String> formatter) {
			super(category, label, description);
			this.min = min;
			this.max = max;
			this.step = step;
			this.getter = getter;
			this.setter = setter;
			this.formatter = formatter;
		}

		public float get(AetherConfig c) {
			return getter.apply(c);
		}

		public void set(AetherConfig c, float value) {
			float snapped = Math.round((value - min) / step) * step + min;
			setter.accept(c, Math.max(min, Math.min(max, snapped)));
		}

		public float normalized(AetherConfig c) {
			return (get(c) - min) / (max - min);
		}

		public String format(AetherConfig c) {
			return formatter.apply(get(c));
		}

		@Override
		public void copy(AetherConfig source, AetherConfig target) {
			setter.accept(target, get(source));
		}

		public static Function<Float, String> percent() {
			return v -> Math.round(v * 100) + "%";
		}

		public static Function<Float, String> decimal(int places) {
			return v -> String.format(Locale.ROOT, "%." + places + "f", v);
		}

		public static Function<Float, String> integer(String suffix) {
			return v -> Math.round(v) + suffix;
		}
	}

	public static final class Choice<E> extends Setting {
		public final E[] values;
		public final Function<E, String> names;
		public final Function<AetherConfig, E> getter;
		public final BiConsumer<AetherConfig, E> setter;

		public Choice(Category category, String label, String description, E[] values, Function<E, String> names,
				Function<AetherConfig, E> getter, BiConsumer<AetherConfig, E> setter) {
			super(category, label, description);
			this.values = values;
			this.names = names;
			this.getter = getter;
			this.setter = setter;
		}

		public E get(AetherConfig c) {
			return getter.apply(c);
		}

		public int index(AetherConfig c) {
			E v = get(c);

			for (int i = 0; i < values.length; i++) {
				if (values[i] == v) {
					return i;
				}
			}

			return 0;
		}

		public void setIndex(AetherConfig c, int index) {
			setter.accept(c, values[Math.floorMod(index, values.length)]);
		}

		public String display(AetherConfig c) {
			return names.apply(get(c));
		}

		@Override
		public void copy(AetherConfig source, AetherConfig target) {
			setter.accept(target, get(source));
		}
	}

	/** A button row, e.g. "Apply preset" or "Open resource packs". */
	public static final class Action extends Setting {
		public final String buttonText;
		public final BiConsumer<AetherSettingsScreen, AetherConfig> action;

		public Action(Category category, String label, String description, String buttonText,
				BiConsumer<AetherSettingsScreen, AetherConfig> action) {
			super(category, label, description);
			this.buttonText = buttonText;
			this.action = action;
		}

		@Override
		public void copy(AetherConfig source, AetherConfig target) {
		}

		@Override
		public boolean isValue() {
			return false;
		}
	}

	/** A read-only line, e.g. a summary table row or compatibility status. */
	public static final class Info extends Setting {
		public final Function<AetherConfig, String> value;
		public final Function<AetherConfig, Integer> color;

		public Info(Category category, String label, String description, Function<AetherConfig, String> value,
				Function<AetherConfig, Integer> color) {
			super(category, label, description);
			this.value = value;
			this.color = color;
		}

		@Override
		public void copy(AetherConfig source, AetherConfig target) {
		}

		@Override
		public boolean isValue() {
			return false;
		}
	}

	/** A section heading inside a category. */
	public static final class Header extends Setting {
		public final Supplier<String> right;

		public Header(Category category, String label, Supplier<String> right) {
			super(category, label, "");
			this.right = right;
		}

		@Override
		public void copy(AetherConfig source, AetherConfig target) {
		}

		@Override
		public boolean isValue() {
			return false;
		}
	}
}
