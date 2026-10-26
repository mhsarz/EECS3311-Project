package com.recipeagent.history;

import com.recipeagent.model.Recipe;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Saved recipes with ratings, favourites and cook records (F08).
 * {@link #getTopRated(int)} feeds the user's best recipes back into recipe generation (F01),
 * and {@link #recordCooked(String)} keeps a record each time a recipe is cooked (F06).
 */
public class RecipeHistory {

    private final List<Recipe> recipes;

    public RecipeHistory() {
        this(null);
    }

    /** Starts with previously saved recipes (may be null). Duplicate ids are skipped. */
    public RecipeHistory(List<Recipe> initialRecipes) {
        this.recipes = new ArrayList<>();
        if (initialRecipes != null) {
            for (Recipe r : initialRecipes) {
                if (r != null && recipes.stream().noneMatch(x -> x.getId().equals(r.getId()))) {
                    recipes.add(r);
                }
            }
        }
    }

    /**
     * Saves a recipe.
     *
     * @return true if it was added; false if a recipe with the same id is already saved
     */
    public boolean save(Recipe r) {
        if (r == null) {
            throw new IllegalArgumentException("Recipe must not be null");
        }
        if (contains(r.getId())) {
            return false;
        }
        recipes.add(r);
        return true;
    }

    public boolean contains(String id) {
        return findById(id).isPresent();
    }

    public Optional<Recipe> findById(String id) {
        for (Recipe r : recipes) {
            if (r.getId().equals(id)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** Removes a saved recipe. Returns true if it was in the history. */
    public boolean remove(String id) {
        return id != null && recipes.removeIf(r -> r.getId().equals(id));
    }

    /**
     * Gives a saved recipe 1-5 stars.
     *
     * @throws IllegalArgumentException if no recipe has this id or stars is outside 1-5
     */
    public void rate(String id, int stars) {
        require(id).setRating(stars);
    }

    /**
     * Flips the favourite flag of a saved recipe.
     *
     * @return the new value
     * @throws IllegalArgumentException if no recipe has this id
     */
    public boolean toggleFavourite(String id) {
        return require(id).toggleFavourite();
    }

    /**
     * Recipes whose title or an ingredient contains {@code query}, ignoring case.
     * An empty query returns every saved recipe.
     */
    public List<Recipe> search(String query) {
        List<Recipe> result = new ArrayList<>();
        for (Recipe r : recipes) {
            if (r.matches(query)) {
                result.add(r);
            }
        }
        return result;
    }

    /**
     * The user's best recipes, at most {@code n}: only recipes that are rated or marked
     * favourite, ordered by rating (highest first), then favourites first, then how often
     * they were cooked.
     */
    public List<Recipe> getTopRated(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be zero or more");
        }
        List<Recipe> candidates = new ArrayList<>();
        for (Recipe r : recipes) {
            if (r.isRated() || r.isFavourite()) {
                candidates.add(r);
            }
        }
        candidates.sort(Comparator.comparingInt(Recipe::getRating).reversed()
                .thenComparing(Recipe::isFavourite, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingInt(Recipe::getTimesCooked).reversed()));
        return new ArrayList<>(candidates.subList(0, Math.min(n, candidates.size())));
    }

    /** Titles of {@link #getTopRated(int)}, as added to a RecipeRequest in F01. */
    public List<String> getTopRatedTitles(int n) {
        List<String> titles = new ArrayList<>();
        for (Recipe r : getTopRated(n)) {
            titles.add(r.getTitle());
        }
        return titles;
    }

    /**
     * Records that a saved recipe was cooked today (F06).
     *
     * @return false if no saved recipe has this id
     */
    public boolean recordCooked(String id) {
        return recordCooked(id, LocalDate.now());
    }

    public boolean recordCooked(String id, LocalDate date) {
        Optional<Recipe> r = findById(id);
        r.ifPresent(recipe -> recipe.recordCooked(date));
        return r.isPresent();
    }

    /** Removes the latest cook record of a recipe, used by Undo (F06). */
    public boolean undoCooked(String id) {
        return findById(id).map(Recipe::removeLastCooked).orElse(false);
    }

    /** All saved recipes, read-only, oldest first. */
    public List<Recipe> getAll() {
        return Collections.unmodifiableList(recipes);
    }

    public boolean isEmpty() {
        return recipes.isEmpty();
    }

    private Recipe require(String id) {
        return findById(id).orElseThrow(
                () -> new IllegalArgumentException("No saved recipe with id " + id));
    }
}
