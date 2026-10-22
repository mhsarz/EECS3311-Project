package com.recipeagent.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the user asks for when generating a recipe (F01).
 * Invalid values are rejected here, before any LLM call is made.
 */
public class RecipeRequest {

    private final int maxMinutes;
    private final int portions;
    private final List<String> requiredItems;
    private final List<String> favourites;
    private boolean allowMissing;

    public RecipeRequest(int maxMinutes, int portions) {
        this.maxMinutes = Validation.requirePositive(maxMinutes, "Maximum cooking time");
        this.portions = Validation.requirePositive(portions, "Portions");
        this.requiredItems = new ArrayList<>();
        this.favourites = new ArrayList<>();
        this.allowMissing = false;
    }

    public int getMaxMinutes() {
        return maxMinutes;
    }

    public int getPortions() {
        return portions;
    }

    /** Ingredients the recipe must use, e.g. expiring items from F05. */
    public List<String> getRequiredItems() {
        return Collections.unmodifiableList(requiredItems);
    }

    public void addRequiredItem(String name) {
        requiredItems.add(Validation.requireText(name, "Required item"));
    }

    /** Titles of the user's top-rated recipes, added by AgentController from F08. */
    public List<String> getFavourites() {
        return Collections.unmodifiableList(favourites);
    }

    public void setFavourites(List<String> titles) {
        favourites.clear();
        if (titles != null) {
            for (String t : titles) {
                if (t != null && !t.isBlank()) {
                    favourites.add(t.trim());
                }
            }
        }
    }

    /** If true, the recipe may use ingredients that are not in the pantry. */
    public boolean isAllowMissing() {
        return allowMissing;
    }

    public void setAllowMissing(boolean allowMissing) {
        this.allowMissing = allowMissing;
    }
}
