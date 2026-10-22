package com.recipeagent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @TempDir
    Path tmp;

    private Path writeEnv(String... lines) throws IOException {
        Path f = tmp.resolve(".env");
        Files.write(f, List.of(lines));
        return f;
    }

    @Test
    void readsValuesFromEnvFile() throws IOException {
        Path f = writeEnv("# comment", "", "LLM_API_KEY=abc123", "LLM_MODEL=\"some-model\"",
                "LLM_DAILY_LIMIT=20", "DATA_DIR='mydata'");
        AppConfig c = AppConfig.load(f, Map.of());
        assertEquals("abc123", c.getApiKey().orElseThrow());
        assertEquals("some-model", c.getModel());
        assertEquals(20, c.getDailyLimit());
        assertEquals(Path.of("mydata"), c.getDataDir());
    }

    @Test
    void environmentOverridesFile() throws IOException {
        Path f = writeEnv("LLM_API_KEY=fromFile", "LLM_MODEL=fileModel");
        AppConfig c = AppConfig.load(f, Map.of("LLM_API_KEY", "fromEnv"));
        assertEquals("fromEnv", c.getApiKey().orElseThrow());
        assertEquals("fileModel", c.getModel());
    }

    @Test
    void missingFileUsesDefaults() {
        AppConfig c = AppConfig.load(tmp.resolve("does-not-exist"), Map.of());
        assertFalse(c.hasApiKey());
        assertEquals(AppConfig.DEFAULT_MODEL, c.getModel());
        assertEquals(AppConfig.DEFAULT_DAILY_LIMIT, c.getDailyLimit());
        assertEquals(Path.of(AppConfig.DEFAULT_DATA_DIR), c.getDataDir());
    }

    @Test
    void placeholderKeyCountsAsMissing() throws IOException {
        Path f = writeEnv("LLM_API_KEY=" + AppConfig.PLACEHOLDER_KEY);
        assertFalse(AppConfig.load(f, Map.of()).hasApiKey());
    }

    @Test
    void invalidDailyLimitFallsBackToDefault() throws IOException {
        assertEquals(AppConfig.DEFAULT_DAILY_LIMIT,
                AppConfig.load(writeEnv("LLM_DAILY_LIMIT=abc"), Map.of()).getDailyLimit());
        assertEquals(AppConfig.DEFAULT_DAILY_LIMIT,
                AppConfig.load(writeEnv("LLM_DAILY_LIMIT=0"), Map.of()).getDailyLimit());
    }

    @Test
    void toStringNeverShowsKey() throws IOException {
        AppConfig c = AppConfig.load(writeEnv("LLM_API_KEY=secret-value"), Map.of());
        assertFalse(c.toString().contains("secret-value"));
        assertTrue(c.toString().contains("apiKey=set"));
    }
}
