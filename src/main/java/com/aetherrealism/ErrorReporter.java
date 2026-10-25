package com.aetherrealism;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Logs each distinct failure once instead of flooding the console every frame or tick. */
public final class ErrorReporter {
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

	private ErrorReporter() {
	}

	public static void report(String area, Throwable t) {
		String key = area + ":" + t.getClass().getName() + ":" + t.getMessage();

		if (SEEN.size() < 256 && SEEN.add(key)) {
			AetherRealismClient.LOGGER.error("Aether Realism error in {} (further identical errors are suppressed)", area, t);
		}
	}
}
