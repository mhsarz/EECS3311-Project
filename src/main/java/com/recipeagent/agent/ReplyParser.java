package com.recipeagent.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.recipeagent.model.Ingredient;
import com.recipeagent.model.Recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the LLM's JSON replies into domain objects. Replies are checked strictly:
 * anything that does not fit is reported as a {@link ReplyException} with a short
 * reason, which the agent can send back to the LLM when it retries.
 */
final class ReplyParser {

    private ReplyParser() {
    }

    /** A reply that could not be used, with the reason. */
    static class ReplyException extends Exception {
        private static final long serialVersionUID = 1L;

        ReplyException(String message) {
            super(message);
        }
    }

    /**
     * Finds the JSON object in a reply. Models sometimes wrap JSON in ```json fences or add
     * a sentence before it, so this takes the text from the first '{' to the last '}'.
     */
    static JsonObject extractJson(String text) throws ReplyException {
        if (text == null) {
            throw new ReplyException("the reply was empty");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ReplyException("the reply did not contain a JSON object");
        }
        try {
            JsonElement el = JsonParser.parseString(text.substring(start, end + 1));
            if (!el.isJsonObject()) {
                throw new ReplyException("the reply was not a JSON object");
            }
            return el.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new ReplyException("the JSON in the reply was not valid");
        }
    }

    /** Reads a recipe in the format asked for by RecipeAgent.RECIPE_FORMAT. */
    static Recipe toRecipe(JsonObject o) throws ReplyException {
        String title = requiredString(o, "title");
        int minutes = requiredInt(o, "cook_time_minutes");
        int portions = requiredInt(o, "portions");

        JsonArray ingArray = requiredArray(o, "ingredients");
        List<Ingredient> ingredients = new ArrayList<>();
        for (JsonElement el : ingArray) {
            if (!el.isJsonObject()) {
                throw new ReplyException("each ingredient must be an object");
            }
            JsonObject ing = el.getAsJsonObject();
            String name = requiredString(ing, "name");
            double qty = optionalDouble(ing, "quantity", 0);
            String unit = optionalString(ing, "unit", "");
            try {
                ingredients.add(new Ingredient(name, qty, unit));
            } catch (IllegalArgumentException e) {
                throw new ReplyException("ingredient '" + name + "': " + e.getMessage());
            }
        }
        if (ingredients.isEmpty()) {
            throw new ReplyException("the recipe has no ingredients");
        }

        List<String> steps = new ArrayList<>();
        for (JsonElement el : requiredArray(o, "steps")) {
            if (el.isJsonPrimitive() && !el.getAsString().isBlank()) {
                steps.add(el.getAsString().trim());
            }
        }
        if (steps.isEmpty()) {
            throw new ReplyException("the recipe has no steps");
        }

        try {
            return new Recipe(title, ingredients, steps, minutes, portions);
        } catch (IllegalArgumentException e) {
            throw new ReplyException(e.getMessage());
        }
    }

    static String requiredString(JsonObject o, String key) throws ReplyException {
        String s = optionalString(o, key, null);
        if (s == null) {
            throw new ReplyException("'" + key + "' is missing");
        }
        return s;
    }

    static String optionalString(JsonObject o, String key, String fallback) {
        JsonElement el = o.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return fallback;
        }
        String s = el.getAsString().trim();
        return s.isEmpty() ? fallback : s;
    }

    static int requiredInt(JsonObject o, String key) throws ReplyException {
        JsonElement el = o.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            throw new ReplyException("'" + key + "' is missing");
        }
        try {
            return (int) Math.round(el.getAsDouble());
        } catch (NumberFormatException e) {
            throw new ReplyException("'" + key + "' must be a number");
        }
    }

    static double optionalDouble(JsonObject o, String key, double fallback) {
        JsonElement el = o.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return el.getAsDouble();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static JsonArray requiredArray(JsonObject o, String key) throws ReplyException {
        JsonElement el = o.get(key);
        if (el == null || !el.isJsonArray()) {
            throw new ReplyException("'" + key + "' must be a list");
        }
        return el.getAsJsonArray();
    }
}
