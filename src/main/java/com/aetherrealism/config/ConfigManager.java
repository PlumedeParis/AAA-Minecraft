package com.aetherrealism.config;

import com.aetherrealism.AetherRealismClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Loads, validates and persists {@link AetherConfig} at {@code config/aether_realism.json}.
 *
 * <p>The active config is replaced atomically on {@link #commit(AetherConfig)}; listeners receive the previous and
 * new config so subsystems can decide whether they need to rebuild anything (e.g. terrain meshes).
 */
public final class ConfigManager {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
	private static final String FILE_NAME = "aether_realism.json";

	private static volatile AetherConfig current = new AetherConfig();
	private static final List<BiConsumer<AetherConfig, AetherConfig>> LISTENERS = new ArrayList<>();
	private static boolean safeMode;

	private ConfigManager() {
	}

	public static AetherConfig get() {
		return current;
	}

	public static boolean isSafeMode() {
		return safeMode;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static void addListener(BiConsumer<AetherConfig, AetherConfig> listener) {
		LISTENERS.add(listener);
	}

	public static void load() {
		// -Daether_realism.safe=true starts with every effect disabled, without touching the saved file.
		safeMode = Boolean.getBoolean("aether_realism.safe");
		Path file = path();
		AetherConfig loaded = null;

		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				JsonElement json = JsonParser.parseReader(reader);
				loaded = GSON.fromJson(json, AetherConfig.class);
			} catch (Exception e) {
				AetherRealismClient.LOGGER.error("Aether Realism config is malformed; backing it up and using defaults", e);
				backupBroken(file);
			}
		}

		if (loaded == null) {
			loaded = new AetherConfig();
		}

		loaded.validate();
		current = loaded;
		save();

		if (safeMode) {
			AetherConfig safe = loaded.copy();
			safe.masterEnabled = false;
			current = safe;
			AetherRealismClient.LOGGER.warn("Aether Realism started in safe mode: all effects are disabled for this session");
		}
	}

	private static void backupBroken(Path file) {
		try {
			Files.copy(file, file.resolveSibling(FILE_NAME + ".broken"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			AetherRealismClient.LOGGER.warn("Could not back up broken config", e);
		}
	}

	public static void save() {
		Path file = path();

		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(FILE_NAME + ".tmp");

			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(current, writer);
			}

			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			AetherRealismClient.LOGGER.error("Could not save Aether Realism config", e);
		}
	}

	/** Makes {@code next} the active config, persists it and notifies listeners. */
	public static void commit(AetherConfig next) {
		next.validate();
		AetherConfig previous = current;
		current = next;
		safeMode = false;
		save();

		for (BiConsumer<AetherConfig, AetherConfig> listener : LISTENERS) {
			try {
				listener.accept(previous, next);
			} catch (Exception e) {
				AetherRealismClient.LOGGER.error("Config listener failed", e);
			}
		}
	}

	/** Instantly flips the master switch (panic key). */
	public static void toggleMaster() {
		AetherConfig next = current.copy();
		next.masterEnabled = !next.masterEnabled;
		commit(next);
	}
}
