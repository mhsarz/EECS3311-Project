package com.recipeagent.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Reads the arguments the LLM (or the agent itself) passes to a tool.
 * Arguments are normally a JSON object such as {"action":"list"}; a plain word such as
 * "list" is also accepted and treated as {"action":"list"}.
 */
public final class ToolArgs {

    private final JsonObject json;

    private ToolArgs(JsonObject json) {
        this.json = json;
    }

    public static ToolArgs parse(String args) {
        if (args == null || args.isBlank()) {
            return new ToolArgs(new JsonObject());
        }
        String text = args.trim();
        if (text.startsWith("{")) {
            try {
                JsonElement el = JsonParser.parseString(text);
                if (el.isJsonObject()) {
                    return new ToolArgs(el.getAsJsonObject());
                }
            } catch (JsonParseException e) {
                // fall through: treat as a plain action word
            }
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("action", text);
        return new ToolArgs(obj);
    }

    /** A string argument, or {@code fallback} if missing or not a string. */
    public String getString(String key, String fallback) {
        JsonElement el = json.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return fallback;
        }
        String s = el.getAsString().trim();
        return s.isEmpty() ? fallback : s;
    }

    /** An integer argument, or {@code fallback} if missing or not a number. */
    public int getInt(String key, int fallback) {
        JsonElement el = json.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return el.getAsInt();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** A number argument, or {@code fallback} if missing or not a number. */
    public double getDouble(String key, double fallback) {
        JsonElement el = json.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return el.getAsDouble();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** True if the argument is present (even if empty). */
    public boolean has(String key) {
        return json.has(key) && !json.get(key).isJsonNull();
    }

    /**
     * A list of strings, or null if the argument is missing. A single string is
     * treated as a one-item list; blank entries are skipped.
     */
    public java.util.List<String> getStringList(String key) {
        JsonElement el = json.get(key);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        java.util.List<String> result = new java.util.ArrayList<>();
        if (el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                if (item.isJsonPrimitive() && !item.getAsString().isBlank()) {
                    result.add(item.getAsString().trim());
                }
            }
        } else if (el.isJsonPrimitive() && !el.getAsString().isBlank()) {
            result.add(el.getAsString().trim());
        }
        return result;
    }

    /** The "action" argument in lower case, or {@code fallback}. */
    public String action(String fallback) {
        return getString("action", fallback).toLowerCase(java.util.Locale.ROOT);
    }

    public JsonObject raw() {
        return json;
    }
}
