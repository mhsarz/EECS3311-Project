package com.recipeagent.pantry;

import com.recipeagent.model.Ingredient;
import com.recipeagent.model.IngredientCheck;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.Recipe;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the food the user has at home (F03). Item names are unique (ignoring case),
 * so adding an item with an existing name is refused and the caller can merge instead.
 * Also provides expiring items (F05), deducting and restoring quantities for
 * Mark as Cooked and Undo (F06), and comparing a recipe with the stock (F11).
 * Saving to disk is done by the caller through JsonStorage.
 */
public class PantryManager {

    /** Returned by {@link #deduct} when no pantry item has the given name. */
    public static final double NOT_IN_PANTRY = -1;

    private final List<PantryItem> items;

    public PantryManager() {
        this(null);
    }

    /** Starts with previously saved items (may be null). */
    public PantryManager(List<PantryItem> initialItems) {
        this.items = new ArrayList<>();
        if (initialItems != null) {
            for (PantryItem item : initialItems) {
                if (item == null) {
                    continue;
                }
                if (item.getId() == null || item.getName() == null || item.getLocation() == null) {
                    System.err.println("Warning: skipped a saved pantry item with a missing id, name or location");
                    continue;
                }
                if (items.stream().noneMatch(i -> i.hasName(item.getName()))) {
                    items.add(item);
                }
            }
        }
    }

    /**
     * Adds a new item.
     *
     * @return true if added; false if an item with the same name already exists
     * @throws IllegalArgumentException if the item is null or its quantity is zero
     */
    public boolean addItem(PantryItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null");
        }
        if (item.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be more than zero");
        }
        if (!findByName(item.getName()).isEmpty()) {
            return false;
        }
        items.add(item);
        return true;
    }

    /**
     * Adds {@code qty} to the existing item with this id (the "merge" choice in F03).
     *
     * @throws IllegalArgumentException if no item has this id or qty is not positive
     */
    public void mergeQuantity(String id, double qty) {
        if (Double.isNaN(qty) || qty <= 0) {
            throw new IllegalArgumentException("Quantity to merge must be more than zero");
        }
        PantryItem item = findById(id).orElseThrow(
                () -> new IllegalArgumentException("No pantry item with id " + id));
        item.setQuantity(item.getQuantity() + qty);
    }

    /** Removes the item with this id. Returns true if something was removed. */
    public boolean removeItem(String id) {
        return id != null && items.removeIf(i -> i.getId().equals(id));
    }

    /** Removes the item with this name, ignoring case (CLI: pantry remove rice). */
    public boolean removeByName(String name) {
        return name != null && items.removeIf(i -> i.hasName(name));
    }

    /** Items whose name equals {@code name}, ignoring case. Empty list if none. */
    public List<PantryItem> findByName(String name) {
        List<PantryItem> result = new ArrayList<>();
        for (PantryItem i : items) {
            if (i.hasName(name)) {
                result.add(i);
            }
        }
        return result;
    }

    public Optional<PantryItem> findById(String id) {
        for (PantryItem i : items) {
            if (i.getId().equals(id)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    /** All items, read-only. */
    public List<PantryItem> getAll() {
        return Collections.unmodifiableList(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** Items that expire within {@code days} days from today, soonest first (F05). */
    public List<PantryItem> getExpiringItems(int days) {
        return getExpiringItems(days, LocalDate.now());
    }

    /** Items that expire within {@code days} days of {@code today}, soonest first. */
    public List<PantryItem> getExpiringItems(int days, LocalDate today) {
        if (days < 0) {
            throw new IllegalArgumentException("Days must be zero or more");
        }
        List<PantryItem> result = new ArrayList<>();
        for (PantryItem i : items) {
            if (i.isExpiringWithin(days, today)) {
                result.add(i);
            }
        }
        result.sort(Comparator.comparing(PantryItem::getBestBefore));
        return result;
    }

    /**
     * Uses up {@code qty} of the named item (F06). If the pantry has less than that,
     * the quantity becomes zero.
     *
     * @return the quantity before deducting, or {@link #NOT_IN_PANTRY} if no item has this name
     */
    public double deduct(String name, double qty) {
        if (Double.isNaN(qty) || qty < 0) {
            throw new IllegalArgumentException("Quantity to deduct must be zero or more");
        }
        List<PantryItem> found = findByName(name);
        if (found.isEmpty()) {
            return NOT_IN_PANTRY;
        }
        PantryItem item = found.get(0);
        double previous = item.getQuantity();
        item.setQuantity(Math.max(0, previous - qty));
        return previous;
    }

    /**
     * Puts quantities back after an undo (F06). Keys are item names, values the old
     * quantities. Names that are no longer in the pantry are skipped.
     */
    public void restore(Map<String, Double> snapshot) {
        if (snapshot == null) {
            return;
        }
        for (Map.Entry<String, Double> e : snapshot.entrySet()) {
            Double qty = e.getValue();
            if (qty == null || qty < 0) {
                continue;
            }
            for (PantryItem item : findByName(e.getKey())) {
                item.setQuantity(qty);
            }
        }
    }

    /**
     * Compares a recipe with the pantry by exact name and quantity (F11).
     * An ingredient is missing if no item has its name, or if there is not enough of it;
     * in the second case the missing quantity is the shortfall.
     * Ingredient names that differ (e.g. "scallions" vs "green onions") are left for the
     * agent's name matching.
     */
    public IngredientCheck checkRecipe(Recipe recipe) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe must not be null");
        }
        List<Ingredient> missing = new ArrayList<>();
        for (Ingredient ing : recipe.getIngredients()) {
            List<PantryItem> found = findByName(ing.getName());
            if (found.isEmpty()) {
                missing.add(ing);
                continue;
            }
            double have = found.get(0).getQuantity();
            if (have < ing.getQuantity()) {
                missing.add(new Ingredient(ing.getName(), ing.getQuantity() - have, ing.getUnit()));
            }
        }
        return new IngredientCheck(recipe.getId(), missing);
    }

    /** Names of the recipe's ingredients that have no pantry item with exactly that name. */
    public List<String> unmatchedNames(Recipe recipe) {
        List<String> result = new ArrayList<>();
        for (Ingredient ing : recipe.getIngredients()) {
            if (findByName(ing.getName()).isEmpty()) {
                result.add(ing.getName());
            }
        }
        return result;
    }
}
