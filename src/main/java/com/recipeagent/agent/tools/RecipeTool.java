package com.recipeagent.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.recipeagent.agent.AgentTool;
import com.recipeagent.agent.RecipeAgent;
import com.recipeagent.agent.ToolArgs;
import com.recipeagent.history.RecipeHistory;
import com.recipeagent.model.Ingredient;
import com.recipeagent.model.Recipe;
import com.recipeagent.model.UserProfile;

import java.util.List;

/**
 * Lets the chat agent read and add to the user's saved recipes (F08, F09).
 * <ul>
 *   <li>{"action":"list"}: the saved recipes with rating, favourite and times cooked</li>
 *   <li>{"action":"save","title":"Egg Fried Rice","cook_time_minutes":20,"portions":2,
 *       "ingredients":[{"name":"rice","quantity":2,"unit":"cup"}],"steps":["..."]}:
 *       saves a recipe the agent suggested in the chat, when the user asks for it</li>
 * </ul>
 * A recipe is checked before it is saved: the same format as a generated recipe (F01),
 * none of the user's allergies, and no saved recipe with the same title. The tool changes
 * the same RecipeHistory object the rest of the app uses; saving it to disk is done by the
 * caller (AgentController).
 */
public class RecipeTool implements AgentTool {

    public static final String NAME = "recipes";
    static final int MAX_LISTED = 30;
    static final int MAX_INGREDIENTS = 40;
    static final int MAX_STEPS = 30;
    static final int MAX_TITLE = 100;

    private final RecipeHistory history;
    private final UserProfile profile;

    public RecipeTool(RecipeHistory history, UserProfile profile) {
        if (history == null || profile == null) {
            throw new IllegalArgumentException("History and profile are required");
        }
        this.history = history;
        this.profile = profile;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "The user's saved recipes (their recipe history). Use action 'list' to see them. Use action "
                + "'save' only when the user asks you to save a recipe you gave them, with the whole recipe: "
                + "title, cook_time_minutes, portions, ingredients (name, quantity, unit) and steps.";
    }

    @Override
    public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"action\":{\"type\":\"string\",\"enum\":[\"list\",\"save\"]},"
                + "\"title\":{\"type\":\"string\"},"
                + "\"cook_time_minutes\":{\"type\":\"integer\",\"minimum\":1},"
                + "\"portions\":{\"type\":\"integer\",\"minimum\":1},"
                + "\"ingredients\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
                + "\"name\":{\"type\":\"string\"},\"quantity\":{\"type\":\"number\"},\"unit\":{\"type\":\"string\"}},"
                + "\"required\":[\"name\"]}},"
                + "\"steps\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},"
                + "\"required\":[\"action\"]}";
    }

    @Override
    public String execute(String args) {
        ToolArgs a = ToolArgs.parse(args);
        String action = a.action("list");
        switch (action) {
            case "list":
                return list();
            case "save":
                return save(a);
            default:
                return PantryTool.error("unknown action '" + action + "' (use list or save)");
        }
    }

    private String list() {
        List<Recipe> all = history.getAll();
        JsonArray arr = new JsonArray();
        for (Recipe r : all.subList(0, Math.min(MAX_LISTED, all.size()))) {
            JsonObject o = new JsonObject();
            o.addProperty("title", r.getTitle());
            o.addProperty("rating", r.getRating());
            o.addProperty("favourite", r.isFavourite());
            o.addProperty("times_cooked", r.getTimesCooked());
            arr.add(o);
        }
        JsonObject o = new JsonObject();
        o.add("recipes", arr);
        o.addProperty("total", all.size());
        return o.toString();
    }

    private String save(ToolArgs a) {
        Recipe r;
        try {
            r = a.toRecipe();
        } catch (IllegalArgumentException e) {
            return PantryTool.error("not saved: " + e.getMessage());
        }
        if (r.getTitle().length() > MAX_TITLE) {
            return PantryTool.error("not saved: the title is longer than " + MAX_TITLE + " characters");
        }
        if (r.getIngredients().size() > MAX_INGREDIENTS || r.getSteps().size() > MAX_STEPS) {
            return PantryTool.error("not saved: at most " + MAX_INGREDIENTS + " ingredients and "
                    + MAX_STEPS + " steps");
        }
        for (Ingredient i : r.getIngredients()) {
            String allergen = RecipeAgent.findAllergen(i.getName(), profile.getAllergies());
            if (allergen != null) {
                return PantryTool.error("not saved: it contains " + i.getName()
                        + ", and the user is allergic to " + allergen);
            }
        }
        boolean sameTitle = history.getAll().stream().anyMatch(s -> s.getTitle().equalsIgnoreCase(r.getTitle()));
        if (sameTitle) {
            return PantryTool.error("not saved: a recipe called '" + r.getTitle() + "' is already saved");
        }
        history.save(r);
        JsonObject o = new JsonObject();
        o.addProperty("saved", true);
        o.addProperty("title", r.getTitle());
        o.addProperty("ingredients", r.getIngredients().size());
        o.addProperty("steps", r.getSteps().size());
        return o.toString();
    }
}
