package com.recipeagent.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application settings, read from a local .env file and from environment variables.
 * Environment variables take priority over the .env file, so a key can also be
 * supplied without any file on disk. The real API key is never printed.
 */
public final class AppConfig {

    public static final String KEY_API_KEY = "LLM_API_KEY";
    public static final String KEY_MODEL = "LLM_MODEL";
    public static final String KEY_DAILY_LIMIT = "LLM_DAILY_LIMIT";
    public static final String KEY_DATA_DIR = "DATA_DIR";

    public static final String DEFAULT_MODEL = "claude-opus-5-5";
    public static final int DEFAULT_DAILY_LIMIT = 100;
    public static final String DEFAULT_DATA_DIR = "data";

    /** Placeholder value used in .env.example; treated as "no key". */
    static final String PLACEHOLDER_KEY = "YOUR_LOCAL_API_KEY_HERE";

    private final Map<String, String> values;

    private AppConfig(Map<String, String> values) {
        this.values = values;
    }

    /** Loads ./.env (if present) and the process environment. */
    public static AppConfig load() {
        return load(Path.of(".env"), System.getenv());
    }

    /**
     * Loads settings from the given .env file (may be missing) and environment map.
     * Values in {@code env} override values from the file.
     */
    public static AppConfig load(Path envFile, Map<String, String> env) {
        Map<String, String> merged = new HashMap<>();
        if (envFile != null && Files.isRegularFile(envFile)) {
            try {
                merged.putAll(parse(Files.readAllLines(envFile, StandardCharsets.UTF_8)));
            } catch (IOException e) {
                System.err.println("Warning: could not read " + envFile + ": " + e.getMessage());
            }
        }
        for (String key : List.of(KEY_API_KEY, KEY_MODEL, KEY_DAILY_LIMIT, KEY_DATA_DIR)) {
            String v = env.get(key);
            if (v != null && !v.isBlank()) {
                merged.put(key, v.trim());
            }
        }
        return new AppConfig(merged);
    }

    /**
     * Parses KEY=VALUE lines. Blank lines and lines starting with # are ignored.
     * A UTF-8 byte order mark (added by some Windows editors) is removed first,
     * otherwise the first key would not be recognized.
     */
    static Map<String, String> parse(List<String> lines) {
        Map<String, String> result = new HashMap<>();
        for (String raw : lines) {
            String line = raw.replace("﻿", "").trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            result.put(key, value);
        }
        return result;
    }

    /** The API key, or empty if it is missing or still the placeholder. */
    public Optional<String> getApiKey() {
        String key = values.get(KEY_API_KEY);
        if (key == null || key.isBlank() || key.equals(PLACEHOLDER_KEY)) {
            return Optional.empty();
        }
        return Optional.of(key);
    }

    public boolean hasApiKey() {
        return getApiKey().isPresent();
    }

    public String getModel() {
        String m = values.get(KEY_MODEL);
        return (m == null || m.isBlank()) ? DEFAULT_MODEL : m;
    }

    /** Daily LLM call limit; falls back to the default if missing, invalid, or not positive. */
    public int getDailyLimit() {
        String v = values.get(KEY_DAILY_LIMIT);
        if (v == null) {
            return DEFAULT_DAILY_LIMIT;
        }
        try {
            int n = Integer.parseInt(v.trim());
            return n > 0 ? n : DEFAULT_DAILY_LIMIT;
        } catch (NumberFormatException e) {
            return DEFAULT_DAILY_LIMIT;
        }
    }

    public Path getDataDir() {
        String d = values.get(KEY_DATA_DIR);
        return Path.of((d == null || d.isBlank()) ? DEFAULT_DATA_DIR : d);
    }

    /** Safe summary for logs: shows whether a key is set, never the key itself. */
    @Override
    public String toString() {
        return "AppConfig{apiKey=" + (hasApiKey() ? "set" : "missing")
                + ", model=" + getModel()
                + ", dailyLimit=" + getDailyLimit()
                + ", dataDir=" + getDataDir() + "}";
    }
}
