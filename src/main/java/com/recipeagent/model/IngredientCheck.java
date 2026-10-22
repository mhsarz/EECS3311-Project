package com.recipeagent.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of comparing a recipe with the pantry (F11): the ingredients that are missing.
 * PantryManager fills it with exact-name results; name matching by the agent can then
 * remove ingredients that turn out to be in stock under another name.
 */
public class IngredientCheck {

    private final String recipeId;
    private final List<Ingredient> missing;

    public IngredientCheck(String recipeId, List<Ingredient> missing) {
        this.recipeId = Validation.requireText(recipeId, "Recipe id");
        this.missing = new ArrayList<>(missing == null ? List.of() : missing);
    }

    public String getRecipeId() {
        return recipeId;
    }

    public List<Ingredient> getMissing() {
        return Collections.unmodifiableList(missing);
    }

    /** Names of the missing ingredients, in recipe order. */
    public List<String> getMissingNames() {
        List<String> names = new ArrayList<>();
        for (Ingredient i : missing) {
            names.add(i.getName());
        }
        return names;
    }

    public boolean allInStock() {
        return missing.isEmpty();
    }

    /**
     * Marks an ingredient as found (e.g. "scallions" matched to "green onions").
     *
     * @return true if it was in the missing list
     */
    public boolean markFound(String ingredientName) {
        if (ingredientName == null) {
            return false;
        }
        String n = ingredientName.trim();
        return missing.removeIf(i -> i.getName().equalsIgnoreCase(n));
    }

    @Override
    public String toString() {
        return allInStock() ? "All ingredients in stock" : "Missing: " + getMissingNames();
    }
}
