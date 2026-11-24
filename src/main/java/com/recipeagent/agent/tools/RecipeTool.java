package com.recipeagent.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.recipeagent.agent.AgentTool;
import com.recipeagent.agent.RecipeAgent;
import com.recipeagent.agent.ToolArgs;
import com.recipeagent.history.RecipeHistory;
import com.recipeagent.llm.LLMUnavailableException;
import com.recipeagent.model.CartItem;
import com.recipeagent.model.CartSource;
import com.recipeagent.model.Ingredient;
import com.recipeagent.model.IngredientCheck;
import com.recipeagent.model.IngredientStatus;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.Recipe;
import com.recipeagent.model.RecipeRequest;
import com.recipeagent.model.Substitution;
import com.recipeagent.model.UserProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Lets the chat agent work with recipes the way the Recipe and History tabs do
 * (F01, F06, F07, F08, F11). Every action except list, show and save needs {@link KitchenActions}.
 * <ul>
 *   <li>{"action":"list"}: the saved recipes with rating, favourite and times cooked</li>
 *   <li>{"action":"show","recipe":"Egg Fried Rice"}: one recipe in full</li>
 *   <li>{"action":"generate","minutes":30,"portions":2,"use":["spinach"],"use_expiring":true,
 *       "allow_missing":false}: a new recipe from the recipe generator (F01)</li>
 *   <li>{"action":"save"} saves the current recipe; with title, cook_time_minutes, portions,
 *       ingredients and steps it saves a recipe the agent wrote itself (F08)</li>
 *   <li>{"action":"check","recipe":"current","add_missing":true}: what is missing (F11)</li>
 *   <li>{"action":"substitute","ingredient":"buttermilk"} and then
 *       {"action":"use_substitute","ingredient":"buttermilk","option":1} (F07)</li>
 *   <li>{"action":"cook"} marks a recipe as cooked and takes its ingredients out of the pantry,
 *       {"action":"undo"} reverses it (F06)</li>
 *   <li>{"action":"rate","stars":4} and {"action":"favourite","favourite":true} (F08)</li>
 * </ul>
 * "recipe" is a saved recipe's title (or part of it), or "current" (the default): the recipe
 * generated, shown or saved last. A recipe is checked before it is saved (format, the user's
 * allergies, no saved recipe with the same title). Results that change something carry a
 * "change" text that the GUI and CLI show under the answer.
 */
public class RecipeTool implements AgentTool {

    public static final String NAME = "recipes";
    static final int MAX_LISTED = 30;
    static final int MAX_INGREDIENTS = 40;
    static final int MAX_STEPS = 30;
    static final int MAX_TITLE = 100;
    static final int MAX_MINUTES = 600;
    static final int MAX_PORTIONS = 20;

    private final RecipeHistory history;
    private final UserProfile profile;
    private final KitchenActions actions;
    /** The recipe the conversation is about: generated, shown or saved last. */
    private Recipe current;
    /** The options from the last 'substitute' call, and which recipe and ingredient they are for. */
    private List<Substitution> lastOptions = List.of();
    private String lastOptionsFor;

    /** A tool that can list and save recipes only. */
    public RecipeTool(RecipeHistory history, UserProfile profile) {
        this(history, profile, null);
    }

    public RecipeTool(RecipeHistory history, UserProfile profile, KitchenActions actions) {
        if (history == null || profile == null) {
            throw new IllegalArgumentException("History and profile are required");
        }
        this.history = history;
        this.profile = profile;
        this.actions = actions;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        String base = "Recipes: the user's saved recipe history and the recipe you are working on. 'list' the "
                + "saved recipes, 'show' one, or 'save' one (the current recipe, or a recipe you wrote: title, "
                + "cook_time_minutes, portions, ingredients with name, quantity and unit, and steps).";
        if (actions == null) {
            return base;
        }
        return base + " 'generate' makes a full recipe with the app's recipe generator (minutes, portions, "
                + "use: items it must use, use_expiring, allow_missing); prefer it to writing a recipe yourself. "
                + "'check' lists the ingredients missing from the pantry (add_missing: true also adds them to "
                + "the cart). 'substitute' with an ingredient finds replacements; 'use_substitute' with the "
                + "option number puts one into the recipe. 'cook' marks the recipe as cooked and takes its "
                + "ingredients out of the pantry; 'undo' reverses the last cook. 'rate' (stars 1-5) and "
                + "'favourite' (true/false). Name a saved recipe in 'recipe' by its title, or leave it out for the "
                + "current recipe. Only change anything when the user asks.";
    }

    @Override
    public String getInputSchema() {
        String actionList = actions == null ? "\"list\",\"show\",\"save\""
                : "\"list\",\"show\",\"save\",\"generate\",\"check\",\"substitute\",\"use_substitute\","
                + "\"cook\",\"undo\",\"rate\",\"favourite\"";
        return "{\"type\":\"object\",\"properties\":{"
                + "\"action\":{\"type\":\"string\",\"enum\":[" + actionList + "]},"
                + "\"recipe\":{\"type\":\"string\",\"description\":\"a saved recipe's title, or current\"},"
                + "\"minutes\":{\"type\":\"integer\",\"minimum\":1},"
                + "\"portions\":{\"type\":\"integer\",\"minimum\":1},"
                + "\"use\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"use_expiring\":{\"type\":\"boolean\"},"
                + "\"allow_missing\":{\"type\":\"boolean\"},"
                + "\"add_missing\":{\"type\":\"boolean\"},"
                + "\"ingredient\":{\"type\":\"string\"},"
                + "\"option\":{\"type\":\"integer\",\"minimum\":1},"
                + "\"stars\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":5},"
                + "\"favourite\":{\"type\":\"boolean\"},"
                + "\"title\":{\"type\":\"string\"},"
                + "\"cook_time_minutes\":{\"type\":\"integer\",\"minimum\":1},"
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
        try {
            switch (action) {
                case "list":
                    return list();
                case "show":
                    current = find(a);
                    return recipeJson(current).toString();
                case "save":
                    return save(a);
                case "generate":
                case "check":
                case "substitute":
                case "use_substitute":
                case "cook":
                case "undo":
                case "rate":
                case "favourite":
                    if (actions == null) {
                        return PantryTool.error("this recipes tool can only list, show and save");
                    }
                    return run(action, a);
                default:
                    return PantryTool.error("unknown action '" + action + "'");
            }
        } catch (LLMUnavailableException e) {
            throw e;                                              // the chat reports an unavailable AI
        } catch (RuntimeException e) {
            // includes LLMException from a generator call: the agent can tell the user
            return PantryTool.error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private String run(String action, ToolArgs a) {
        switch (action) {
            case "generate":
                return generate(a);
            case "check":
                return check(a);
            case "substitute":
                return substitute(a);
            case "use_substitute":
                return useSubstitute(a);
            case "cook":
                return cook(a);
            case "undo":
                return undo();
            case "rate":
                return rate(a);
            default:
                return favourite(a);
        }
    }

    // ------------------------------------------------------------ list, show, save

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
        if (current != null) {
            o.addProperty("current", current.getTitle());
        }
        return o.toString();
    }

    private String save(ToolArgs a) {
        Recipe r;
        if (a.has("title") || a.has("ingredients")) {
            try {
                r = a.toRecipe();
            } catch (IllegalArgumentException e) {
                return PantryTool.error("not saved: " + e.getMessage());
            }
            String problem = problemWith(r);
            if (problem != null) {
                return PantryTool.error("not saved: " + problem);
            }
        } else {
            r = find(a);
            if (history.contains(r.getId())) {
                return PantryTool.error("'" + r.getTitle() + "' is already saved");
            }
            String allergen = allergenIn(r);
            if (allergen != null) {
                return PantryTool.error("not saved: " + allergen);
            }
        }
        if (actions != null) {
            actions.saveRecipe(r);
        } else {
            history.save(r);
        }
        current = r;
        JsonObject o = new JsonObject();
        o.addProperty("saved", true);
        o.addProperty("title", r.getTitle());
        o.addProperty("ingredients", r.getIngredients().size());
        o.addProperty("steps", r.getSteps().size());
        o.addProperty("change", "Recipe saved");
        return o.toString();
    }

    /** Why a recipe the agent wrote cannot be saved, or null. */
    private String problemWith(Recipe r) {
        if (r.getTitle().length() > MAX_TITLE) {
            return "the title is longer than " + MAX_TITLE + " characters";
        }
        if (r.getIngredients().size() > MAX_INGREDIENTS || r.getSteps().size() > MAX_STEPS) {
            return "at most " + MAX_INGREDIENTS + " ingredients and " + MAX_STEPS + " steps";
        }
        String allergen = allergenIn(r);
        if (allergen != null) {
            return allergen;
        }
        boolean sameTitle = history.getAll().stream().anyMatch(s -> s.getTitle().equalsIgnoreCase(r.getTitle()));
        return sameTitle ? "a recipe called '" + r.getTitle() + "' is already saved" : null;
    }

    private String allergenIn(Recipe r) {
        for (Ingredient i : r.getIngredients()) {
            String allergen = RecipeAgent.findAllergen(i.getName(), profile.getAllergies());
            if (allergen != null) {
                return "it contains " + i.getName() + ", and the user is allergic to " + allergen;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ actions through the controller

    private String generate(ToolArgs a) {
        int minutes = a.getInt("minutes", 30);
        int portions = a.getInt("portions", profile.getDefaultPortions());
        if (minutes < 1 || minutes > MAX_MINUTES || portions < 1 || portions > MAX_PORTIONS) {
            return PantryTool.error("minutes must be 1-" + MAX_MINUTES + " and portions 1-" + MAX_PORTIONS);
        }
        RecipeRequest req = new RecipeRequest(minutes, portions);
        List<String> use = a.getStringList("use");
        if (use != null) {
            use.forEach(req::addRequiredItem);
        }
        if (a.getBoolean("use_expiring", false)) {
            for (PantryItem i : actions.getItemsToUseSoon()) {
                if (!req.getRequiredItems().contains(i.getName())) {
                    req.addRequiredItem(i.getName());
                }
            }
        }
        req.setAllowMissing(a.getBoolean("allow_missing", false));
        current = actions.generateRecipe(req);
        JsonObject o = recipeJson(current);
        o.addProperty("note", "Not saved yet. Use 'save' if the user wants to keep it.");
        return o.toString();
    }

    private String check(ToolArgs a) {
        Recipe r = find(a);
        IngredientCheck check = actions.checkIngredients(r);
        JsonObject o = new JsonObject();
        o.addProperty("recipe", r.getTitle());
        o.addProperty("all_in_stock", check.allInStock());
        JsonArray missing = new JsonArray();
        for (Ingredient i : check.getMissing()) {
            missing.add(ingredientJson(i, null));
        }
        o.add("missing", missing);
        if (a.getBoolean("add_missing", false) && !check.allInStock()) {
            List<CartItem> items = new ArrayList<>();
            for (Ingredient i : check.getMissing()) {
                items.add(new CartItem(i.getName(), i.getQuantity() > 0 ? i.getQuantity() : 1,
                        i.getQuantity() > 0 ? i.getUnit() : "", CartSource.RECIPE));
            }
            actions.addToCart(items);
            o.addProperty("added_to_cart", true);
            o.addProperty("change", "Cart updated");
        }
        return o.toString();
    }

    private String substitute(ToolArgs a) {
        Recipe r = find(a);
        Ingredient ing = ingredient(r, a.getString("ingredient", null));
        lastOptions = actions.suggestSubstitutes(r, ing.getName());
        lastOptionsFor = r.getId() + "|" + ing.getName().toLowerCase(Locale.ROOT);
        JsonObject o = new JsonObject();
        o.addProperty("recipe", r.getTitle());
        o.addProperty("ingredient", ing.getName());
        JsonArray arr = new JsonArray();
        for (int k = 0; k < lastOptions.size(); k++) {
            Substitution s = lastOptions.get(k);
            JsonObject so = new JsonObject();
            so.addProperty("option", k + 1);
            so.addProperty("substitute", s.getSubstitute());
            so.addProperty("note", s.getNote());
            so.addProperty("in_pantry", s.isInStock());
            arr.add(so);
        }
        o.add("options", arr);
        if (lastOptions.isEmpty()) {
            o.addProperty("note", "No good substitute; offer to add " + ing.getName() + " to the cart.");
        }
        return o.toString();
    }

    private String useSubstitute(ToolArgs a) {
        Recipe r = find(a);
        Ingredient ing = ingredient(r, a.getString("ingredient", null));
        String key = r.getId() + "|" + ing.getName().toLowerCase(Locale.ROOT);
        if (!key.equals(lastOptionsFor) || lastOptions.isEmpty()) {
            return PantryTool.error("call 'substitute' for " + ing.getName() + " first");
        }
        int option = a.getInt("option", 0);
        if (option < 1 || option > lastOptions.size()) {
            return PantryTool.error("option must be 1-" + lastOptions.size());
        }
        Substitution s = lastOptions.get(option - 1);
        actions.useSubstitute(r, ing.getName(), s);
        lastOptions = List.of();
        lastOptionsFor = null;
        JsonObject o = recipeJson(r);
        o.addProperty("replaced", ing.getName() + " with " + s.getSubstitute());
        o.addProperty("change", "Recipe changed");
        return o.toString();
    }

    private String cook(ToolArgs a) {
        Recipe r = find(a);
        List<String> skipped = actions.markCooked(r);
        JsonObject o = new JsonObject();
        o.addProperty("cooked", r.getTitle());
        o.addProperty("times_cooked", r.getTimesCooked());
        JsonArray s = new JsonArray();
        skipped.forEach(s::add);
        o.add("not_in_pantry", s);
        o.addProperty("note", "The ingredients were taken out of the pantry. 'undo' reverses it.");
        o.addProperty("change", "Marked as cooked");
        return o.toString();
    }

    private String undo() {
        Optional<String> done = actions.undo();
        if (done.isEmpty()) {
            return PantryTool.error("there is nothing to undo in this session");
        }
        JsonObject o = new JsonObject();
        o.addProperty("undone", done.get());
        o.addProperty("change", "Undone");
        return o.toString();
    }

    private String rate(ToolArgs a) {
        Recipe r = find(a);
        int stars = a.getInt("stars", 0);
        if (stars < 1 || stars > 5) {
            return PantryTool.error("stars must be 1-5");
        }
        boolean newlySaved = keep(r);
        actions.rateRecipe(r.getId(), stars);
        JsonObject o = new JsonObject();
        o.addProperty("recipe", r.getTitle());
        o.addProperty("rating", stars);
        o.addProperty("saved_to_history", newlySaved);
        o.addProperty("change", "Rated");
        return o.toString();
    }

    private String favourite(ToolArgs a) {
        Recipe r = find(a);
        boolean want = a.getBoolean("favourite", true);
        boolean newlySaved = keep(r);
        if (r.isFavourite() != want) {
            actions.toggleFavourite(r.getId());
        }
        JsonObject o = new JsonObject();
        o.addProperty("recipe", r.getTitle());
        o.addProperty("favourite", want);
        o.addProperty("saved_to_history", newlySaved);
        o.addProperty("change", want ? "Added to favourites" : "Removed from favourites");
        return o.toString();
    }

    /** Ratings and favourites are kept in the history, so an unsaved recipe is saved first. */
    private boolean keep(Recipe r) {
        if (history.contains(r.getId())) {
            return false;
        }
        String allergen = allergenIn(r);
        if (allergen != null) {
            throw new IllegalArgumentException("not saved: " + allergen);
        }
        actions.saveRecipe(r);
        return true;
    }

    // ------------------------------------------------------------ finding things

    /** The recipe named in "recipe" (title or part of it), or the current one. */
    private Recipe find(ToolArgs a) {
        String wanted = a.getString("recipe", "current").trim();
        String w = wanted.toLowerCase(Locale.ROOT);
        if (w.equals("current") || w.equals("last") || w.equals("this") || w.equals("it")) {
            if (current == null) {
                throw new IllegalArgumentException("there is no current recipe yet: 'generate' one, or name a "
                        + "saved recipe in 'recipe' (see 'list')");
            }
            return current;
        }
        if (current != null && current.getTitle().equalsIgnoreCase(wanted)) {
            return current;
        }
        List<Recipe> all = history.getAll();
        for (Recipe r : all) {
            if (r.getTitle().equalsIgnoreCase(wanted)) {
                current = r;
                return r;
            }
        }
        List<Recipe> partial = new ArrayList<>(all.stream()
                .filter(r -> r.getTitle().toLowerCase(Locale.ROOT).contains(w)).toList());
        if (current != null && !history.contains(current.getId())
                && current.getTitle().toLowerCase(Locale.ROOT).contains(w)) {
            partial.add(current);                                 // the unsaved recipe being discussed
        }
        if (partial.size() == 1) {
            current = partial.get(0);
            return current;
        }
        if (partial.isEmpty()) {
            throw new IllegalArgumentException("no saved recipe called '" + wanted + "' (see 'list')");
        }
        List<String> titles = partial.stream().map(Recipe::getTitle).limit(5).toList();
        throw new IllegalArgumentException("'" + wanted + "' matches several recipes: " + String.join("; ", titles));
    }

    private static Ingredient ingredient(Recipe r, String name) {
        if (name == null) {
            throw new IllegalArgumentException("which ingredient? give 'ingredient'");
        }
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (Ingredient i : r.getIngredients()) {
            if (i.getName().equalsIgnoreCase(n)) {
                return i;
            }
        }
        List<Ingredient> partial = r.getIngredients().stream()
                .filter(i -> i.getName().toLowerCase(Locale.ROOT).contains(n)).toList();
        if (partial.size() == 1) {
            return partial.get(0);
        }
        throw new IllegalArgumentException("'" + name + "' is not one ingredient of " + r.getTitle());
    }

    private JsonObject recipeJson(Recipe r) {
        JsonObject o = new JsonObject();
        o.addProperty("title", r.getTitle());
        o.addProperty("cook_time_minutes", r.getCookTimeMinutes());
        o.addProperty("portions", r.getPortions());
        List<IngredientStatus> statuses = actions == null ? List.of() : actions.getIngredientStatuses(r);
        JsonArray ings = new JsonArray();
        for (int k = 0; k < r.getIngredients().size(); k++) {
            ings.add(ingredientJson(r.getIngredients().get(k), k < statuses.size() ? statuses.get(k) : null));
        }
        o.add("ingredients", ings);
        JsonArray steps = new JsonArray();
        r.getSteps().forEach(steps::add);
        o.add("steps", steps);
        o.addProperty("saved", history.contains(r.getId()));
        o.addProperty("rating", r.getRating());
        o.addProperty("favourite", r.isFavourite());
        o.addProperty("times_cooked", r.getTimesCooked());
        return o;
    }

    private static JsonObject ingredientJson(Ingredient i, IngredientStatus status) {
        JsonObject o = new JsonObject();
        o.addProperty("name", i.getName());
        o.addProperty("quantity", i.getQuantity());
        o.addProperty("unit", i.getUnit());
        if (status != null) {
            o.addProperty("status", status.name().toLowerCase(Locale.ROOT));
        }
        return o;
    }
}
