package com.recipeagent.command;

import com.recipeagent.history.RecipeHistory;
import com.recipeagent.model.Ingredient;
import com.recipeagent.model.Recipe;
import com.recipeagent.pantry.PantryManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mark as Cooked (F06): deducts each ingredient from the pantry, remembering the old
 * quantities in a snapshot, and adds a cook record to the recipe history.
 * {@link #undo()} restores the pantry from the snapshot and removes the cook record.
 * If the recipe was not saved yet, executing saves it and undoing removes it again.
 */
public class CookRecipeCommand implements Command {

    private final Recipe recipe;
    private final PantryManager pantry;
    private final RecipeHistory history;
    private final LocalDate date;
    /** Pantry names for ingredients that are in the pantry under another name (F11), lower-case keys. */
    private final Map<String, String> pantryNames = new LinkedHashMap<>();

    /** Old quantity of each deducted ingredient, keyed by name. */
    private final Map<String, Double> snapshot = new LinkedHashMap<>();
    /** Ingredients that were not in the pantry and so were skipped. */
    private final List<String> skipped = new ArrayList<>();
    private boolean addedToHistory;
    private boolean executed;

    public CookRecipeCommand(Recipe recipe, PantryManager pantry, RecipeHistory history) {
        this(recipe, pantry, history, LocalDate.now());
    }

    /** Same as above with a fixed cook date, for tests. */
    public CookRecipeCommand(Recipe recipe, PantryManager pantry, RecipeHistory history,
                             LocalDate date) {
        this(recipe, pantry, history, date, Map.of());
    }

    /**
     * @param pantryNames ingredient name -> the pantry item it is stored as (e.g. "scallions"
     *                    -> "green onions"), so the right pantry item is used up
     */
    public CookRecipeCommand(Recipe recipe, PantryManager pantry, RecipeHistory history,
                             LocalDate date, Map<String, String> pantryNames) {
        if (recipe == null || pantry == null || history == null || date == null) {
            throw new IllegalArgumentException("Recipe, pantry, history and date are required");
        }
        this.recipe = recipe;
        this.pantry = pantry;
        this.history = history;
        this.date = date;
        if (pantryNames != null) {
            pantryNames.forEach((k, v) -> {
                if (k != null && v != null) {
                    this.pantryNames.put(k.trim().toLowerCase(Locale.ROOT), v);
                }
            });
        }
    }

    /**
     * Deducts the ingredients and records the cook.
     *
     * @throws IllegalStateException if this command was already executed and not undone
     */
    @Override
    public void execute() {
        if (executed) {
            throw new IllegalStateException("Recipe already marked as cooked");
        }
        snapshot.clear();
        skipped.clear();
        for (Ingredient ing : recipe.getIngredients()) {
            String name = pantryNames.getOrDefault(ing.getName().trim().toLowerCase(Locale.ROOT), ing.getName());
            String key = name.toLowerCase(Locale.ROOT);
            double previous = pantry.deduct(name, ing.getQuantity());
            if (previous == PantryManager.NOT_IN_PANTRY) {
                skipped.add(ing.getName());
            } else {
                // If the recipe lists an ingredient twice, keep the quantity from before the first deduction.
                snapshot.putIfAbsent(key, previous);
            }
        }
        addedToHistory = history.save(recipe);
        history.recordCooked(recipe.getId(), date);
        executed = true;
    }

    /**
     * Restores the pantry quantities and removes the cook record.
     *
     * @throws IllegalStateException if the command has not been executed
     */
    @Override
    public void undo() {
        if (!executed) {
            throw new IllegalStateException("Nothing to undo: recipe was not marked as cooked");
        }
        pantry.restore(snapshot);
        history.undoCooked(recipe.getId());
        if (addedToHistory) {
            history.remove(recipe.getId());
        }
        executed = false;
    }

    @Override
    public String getDescription() {
        return "Cooked " + recipe.getTitle();
    }

    public Recipe getRecipe() {
        return recipe;
    }

    /** Quantities before cooking, keyed by lower-case ingredient name (read-only). */
    public Map<String, Double> getSnapshot() {
        return Collections.unmodifiableMap(snapshot);
    }

    /** Ingredient names that were not in the pantry (read-only). */
    public List<String> getSkipped() {
        return Collections.unmodifiableList(skipped);
    }

    public boolean isExecuted() {
        return executed;
    }
}
