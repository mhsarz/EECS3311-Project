package com.recipeagent.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.recipeagent.agent.tools.PantryTool;
import com.recipeagent.agent.tools.ProfileTool;
import com.recipeagent.llm.LLMClient;
import com.recipeagent.llm.LLMException;
import com.recipeagent.llm.LLMResponse;
import com.recipeagent.llm.LLMUnavailableException;
import com.recipeagent.model.Ingredient;
import com.recipeagent.model.Recipe;
import com.recipeagent.model.RecipeRequest;
import com.recipeagent.model.Substitution;
import com.recipeagent.prompt.Prompt;
import com.recipeagent.prompt.PromptBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The AI agent. It gathers context through its tools, builds prompts, calls the LLM,
 * and checks every reply in code before it reaches the user.
 * <p>
 * Builder pattern: this is the <b>Director</b>. It holds a {@link PromptBuilder}
 * (aggregation) and, for each kind of prompt, calls only the building steps it needs.
 * <p>
 * Agent behaviour: the LLM's answer is not trusted blindly. A recipe that is too long,
 * has the wrong number of portions, misses a required item or contains an allergen is
 * rejected, and the agent asks the LLM again once, telling it what was wrong.
 * Substitutes that contain allergens are dropped, and "in stock" is checked against the
 * real pantry instead of taking the LLM's word for it.
 */
public class RecipeAgent {

    /** How many times one request is tried in total (first try plus one corrected retry). */
    static final int MAX_ATTEMPTS = 2;
    static final int MAX_SUBSTITUTES = 3;

    static final String RECIPE_FORMAT = "{\"title\": string, \"cook_time_minutes\": integer, "
            + "\"portions\": integer, \"ingredients\": [{\"name\": string, \"quantity\": number, "
            + "\"unit\": string}], \"steps\": [string]}";

    static final String SUBSTITUTE_FORMAT = "{\"substitutes\": [{\"substitute\": string, "
            + "\"note\": string, \"pantry_items\": [string]}]}";

    private final LLMClient llm;
    private final PromptBuilder builder;
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    /**
     * @param llm     the LLM client (normally an LLMClientProxy)
     * @param builder how prompts are written (normally a ClaudePromptBuilder)
     * @param tools   the tools the agent can use; must include "pantry" and "profile"
     */
    public RecipeAgent(LLMClient llm, PromptBuilder builder, List<AgentTool> tools) {
        if (llm == null || builder == null || tools == null) {
            throw new IllegalArgumentException("LLM client, prompt builder and tools are required");
        }
        this.llm = llm;
        this.builder = builder;
        for (AgentTool t : tools) {
            if (t != null) {
                this.tools.put(t.getName(), t);
            }
        }
        requireTool(PantryTool.NAME);
        requireTool(ProfileTool.NAME);
    }

    /** The tools the agent can use, by name (read-only). */
    public Map<String, AgentTool> getTools() {
        return Collections.unmodifiableMap(tools);
    }

    // ------------------------------------------------------------------ F01

    /**
     * Generates one recipe that fits the request, the pantry and the profile (F01).
     *
     * @throws PantryEmptyException   if the pantry is empty and missing ingredients are not allowed
     * @throws LLMUnavailableException if the LLM cannot be used
     * @throws LLMException            if the LLM's replies could not be used after a retry
     */
    public Recipe generateRecipe(RecipeRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Recipe request must not be null");
        }
        String pantryJson = requireTool(PantryTool.NAME).execute("{\"action\":\"list\"}");
        String profileJson = requireTool(ProfileTool.NAME).execute("{\"action\":\"get\"}");
        if (!req.isAllowMissing() && pantryIsEmpty(pantryJson)) {
            throw new PantryEmptyException();
        }
        List<String> allergies = jsonStrings(profileJson, "allergies");

        String problem = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Prompt prompt = buildRecipePrompt(req, pantryJson, profileJson, problem);
            LLMResponse reply = llm.generate(prompt.toText(), List.of());
            try {
                Recipe recipe = ReplyParser.toRecipe(ReplyParser.extractJson(reply.getText()));
                checkRecipe(recipe, req, allergies);
                return recipe;
            } catch (ReplyParser.ReplyException e) {
                problem = e.getMessage();
            }
        }
        throw new LLMException("The AI could not produce a suitable recipe (" + problem + "). Please try again.");
    }

    private Prompt buildRecipePrompt(RecipeRequest req, String pantryJson, String profileJson,
                                     String problem) {
        builder.createNewPrompt();
        StringBuilder task = new StringBuilder()
                .append("You are a home-cooking assistant. Create one recipe that takes at most ")
                .append(req.getMaxMinutes()).append(" minutes in total and makes exactly ")
                .append(req.getPortions()).append(" portions.\n")
                .append("- Prefer ingredients from the pantry, and use items with few days_left first.\n")
                .append("- For an ingredient that is in the pantry, use exactly the pantry's name and unit, ")
                .append("so the stock can be updated after cooking.\n")
                .append("- Follow the diet in the profile, never use an ingredient the user is allergic to, ")
                .append("and avoid the dislikes.\n");
        if (req.isAllowMissing()) {
            task.append("- You may use ingredients that are not in the pantry; keep them few and common.\n");
        } else {
            task.append("- Use only pantry ingredients, plus salt, pepper, cooking oil and water.\n");
        }
        if (!req.getRequiredItems().isEmpty()) {
            task.append("- The recipe must use: ").append(String.join(", ", req.getRequiredItems())).append(".\n");
        }
        if (!req.getFavourites().isEmpty()) {
            task.append("- The user rated the recipes in top_rated_recipes highly; a similar style is welcome.\n");
        }
        builder.buildTask(task.toString());
        builder.buildContext("pantry", pantryJson);
        builder.buildContext("profile", profileJson);
        if (!req.getFavourites().isEmpty()) {
            builder.buildContext("top_rated_recipes", String.join("\n", req.getFavourites()));
        }
        if (problem != null) {
            builder.buildContext("problem_with_previous_answer",
                    "Your previous answer could not be used because " + problem + ". Fix this.");
        }
        builder.buildFormat(RECIPE_FORMAT);
        return builder.getPrompt();
    }

    /** Checks the LLM's recipe against the request; throws with a reason if it does not fit. */
    static void checkRecipe(Recipe r, RecipeRequest req, List<String> allergies)
            throws ReplyParser.ReplyException {
        if (r.getCookTimeMinutes() > req.getMaxMinutes()) {
            throw new ReplyParser.ReplyException("it takes " + r.getCookTimeMinutes()
                    + " minutes but the limit is " + req.getMaxMinutes());
        }
        if (r.getPortions() != req.getPortions()) {
            throw new ReplyParser.ReplyException("it makes " + r.getPortions()
                    + " portions but " + req.getPortions() + " were asked for");
        }
        for (String required : req.getRequiredItems()) {
            boolean used = r.getIngredients().stream()
                    .anyMatch(i -> lower(i.getName()).contains(lower(required)));
            if (!used) {
                throw new ReplyParser.ReplyException("it does not use the required item '" + required + "'");
            }
        }
        for (Ingredient i : r.getIngredients()) {
            String allergen = findAllergen(i.getName(), allergies);
            if (allergen != null) {
                throw new ReplyParser.ReplyException("it contains " + i.getName()
                        + ", but the user is allergic to " + allergen);
            }
        }
    }

    // ------------------------------------------------------------------ F07

    /**
     * Suggests up to 3 replacements for one ingredient of a recipe (F07). An empty list
     * means no good substitute was found (the interface can then offer to add the item
     * to the cart).
     *
     * @throws IllegalArgumentException if the ingredient is not in the recipe
     * @throws LLMUnavailableException  if the LLM cannot be used
     * @throws LLMException             if the LLM's replies could not be used after a retry
     */
    public List<Substitution> suggestSubstitutes(Recipe recipe, String ingredient) {
        if (recipe == null || ingredient == null || ingredient.isBlank()) {
            throw new IllegalArgumentException("Recipe and ingredient are required");
        }
        Ingredient target = recipe.getIngredients().stream()
                .filter(i -> i.getName().equalsIgnoreCase(ingredient.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "'" + ingredient + "' is not an ingredient of " + recipe.getTitle()));

        AgentTool pantryTool = requireTool(PantryTool.NAME);
        String pantryJson = pantryTool.execute("{\"action\":\"list\"}");
        String profileJson = requireTool(ProfileTool.NAME).execute("{\"action\":\"get\"}");
        List<String> allergies = jsonStrings(profileJson, "allergies");

        String problem = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Prompt prompt = buildSubstitutePrompt(recipe, target, pantryJson, profileJson, problem);
            LLMResponse reply = llm.generate(prompt.toText(), List.of());
            try {
                return toSubstitutions(ReplyParser.extractJson(reply.getText()), allergies, pantryTool);
            } catch (ReplyParser.ReplyException e) {
                problem = e.getMessage();
            }
        }
        throw new LLMException("The AI could not suggest substitutes (" + problem + "). Please try again.");
    }

    private Prompt buildSubstitutePrompt(Recipe recipe, Ingredient target, String pantryJson,
                                         String profileJson, String problem) {
        builder.createNewPrompt();
        builder.buildTask("You are a home-cooking assistant. The user is missing \""
                + target.getName() + "\" for the recipe below. Suggest 1 to 3 substitutes that "
                + "work in this recipe, best first.\n"
                + "- Prefer substitutes made from items in the pantry, and list those pantry item "
                + "names exactly as written in pantry_items.\n"
                + "- Never suggest anything the user is allergic to, and follow their diet.\n"
                + "- In note, say how much to use and any change to the steps, in one sentence.\n"
                + "- If there is no reasonable substitute, return an empty substitutes list.");
        StringBuilder r = new StringBuilder(recipe.getTitle()).append("\nIngredients:");
        for (Ingredient i : recipe.getIngredients()) {
            r.append("\n- ").append(i);
        }
        r.append("\nSteps:");
        int n = 1;
        for (String s : recipe.getSteps()) {
            r.append("\n").append(n++).append(". ").append(s);
        }
        builder.buildContext("recipe", r.toString());
        builder.buildContext("missing_ingredient", target.toString());
        builder.buildContext("pantry", pantryJson);
        builder.buildContext("profile", profileJson);
        if (problem != null) {
            builder.buildContext("problem_with_previous_answer",
                    "Your previous answer could not be used because " + problem + ". Fix this.");
        }
        builder.buildFormat(SUBSTITUTE_FORMAT);
        return builder.getPrompt();
    }

    /**
     * Reads the substitutes, drops any that contain an allergen, and marks a substitute as
     * in stock only if every pantry item it names is really in the pantry.
     */
    static List<Substitution> toSubstitutions(JsonObject o, List<String> allergies, AgentTool pantryTool)
            throws ReplyParser.ReplyException {
        JsonArray arr = ReplyParser.requiredArray(o, "substitutes");
        List<Substitution> result = new ArrayList<>();
        for (JsonElement el : arr) {
            if (result.size() == MAX_SUBSTITUTES) {
                break;
            }
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject s = el.getAsJsonObject();
            String name = ReplyParser.optionalString(s, "substitute", null);
            if (name == null || findAllergen(name, allergies) != null) {
                continue;
            }
            String note = ReplyParser.optionalString(s, "note", "");
            List<String> uses = new ArrayList<>();
            if (s.has("pantry_items") && s.get("pantry_items").isJsonArray()) {
                for (JsonElement p : s.getAsJsonArray("pantry_items")) {
                    if (p.isJsonPrimitive() && !p.getAsString().isBlank()) {
                        uses.add(p.getAsString().trim());
                    }
                }
            }
            boolean inStock = !uses.isEmpty();
            for (String item : uses) {
                String answer = pantryTool.execute(hasArgs(item));
                if (!answer.contains("\"in_pantry\":true")) {
                    inStock = false;
                }
            }
            result.add(new Substitution(name, note, inStock));
        }
        return result;
    }

    // ------------------------------------------------------------------ helpers

    private AgentTool requireTool(String name) {
        AgentTool t = tools.get(name);
        if (t == null) {
            throw new IllegalStateException("RecipeAgent needs a '" + name + "' tool");
        }
        return t;
    }

    private static String hasArgs(String name) {
        JsonObject a = new JsonObject();
        a.addProperty("action", "has");
        a.addProperty("name", name);
        return a.toString();
    }

    static boolean pantryIsEmpty(String pantryJson) {
        try {
            JsonObject o = ReplyParser.extractJson(pantryJson);
            return !o.has("items") || !o.get("items").isJsonArray() || o.getAsJsonArray("items").isEmpty();
        } catch (ReplyParser.ReplyException e) {
            return true;
        }
    }

    /** Reads a list of strings from a tool's JSON result; empty if missing. */
    static List<String> jsonStrings(String json, String key) {
        List<String> result = new ArrayList<>();
        try {
            JsonObject o = ReplyParser.extractJson(json);
            if (o.has(key) && o.get(key).isJsonArray()) {
                for (JsonElement el : o.getAsJsonArray(key)) {
                    if (el.isJsonPrimitive()) {
                        result.add(el.getAsString());
                    }
                }
            }
        } catch (ReplyParser.ReplyException e) {
            // no list
        }
        return result;
    }

    /**
     * Returns the allergy that {@code text} contains, or null. Matching ignores case and a
     * plural ending, so "peanuts" also catches "peanut butter". It errs on the side of caution.
     */
    static String findAllergen(String text, List<String> allergies) {
        if (text == null || allergies == null) {
            return null;
        }
        String t = lower(text);
        for (String allergy : allergies) {
            String a = lower(allergy);
            if (a.isEmpty()) {
                continue;
            }
            String stem = a.endsWith("es") && a.length() > 4 ? a.substring(0, a.length() - 2)
                    : a.endsWith("s") && a.length() > 3 ? a.substring(0, a.length() - 1) : a;
            if (t.contains(a) || t.contains(stem)) {
                return allergy;
            }
        }
        return null;
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
