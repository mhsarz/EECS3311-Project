package com.recipeagent.agent.tools;

import com.recipeagent.model.CartItem;
import com.recipeagent.model.IngredientCheck;
import com.recipeagent.model.IngredientStatus;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.Recipe;
import com.recipeagent.model.RecipeRequest;
import com.recipeagent.model.StorageLocation;
import com.recipeagent.model.Substitution;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The app actions the chat agent's tools may run on the user's behalf (F03, F01, F06, F07,
 * F08, F11). AgentController implements it, so a change made by the agent goes through the
 * same code as the GUI and CLI: it is saved, raises the same alerts and can be undone. The
 * tools only see this narrow interface, not the whole facade.
 */
public interface KitchenActions {

    LocalDate today();

    /** Adds a pantry item, or adds its quantity to an item with the same name if {@code merge}. */
    boolean addPantryItem(PantryItem item, boolean merge);

    PantryItem updatePantryItem(String id, String name, double quantity, String unit,
                                StorageLocation location, LocalDate bestBefore);

    boolean removePantryItemByName(String name);

    List<PantryItem> getItemsToUseSoon();

    /** F01: generates a recipe (a separate AI call). */
    Recipe generateRecipe(RecipeRequest req);

    List<IngredientStatus> getIngredientStatuses(Recipe recipe);

    /** F11: what is missing, with names matched to the pantry. */
    IngredientCheck checkIngredients(Recipe recipe);

    List<CartItem> addToCart(List<CartItem> items);

    /** F07: substitutes for one ingredient (a separate AI call). */
    List<Substitution> suggestSubstitutes(Recipe recipe, String ingredient);

    boolean useSubstitute(Recipe recipe, String ingredient, Substitution substitute);

    /** F06: takes the ingredients out of the pantry; returns the ingredients not in the pantry. */
    List<String> markCooked(Recipe recipe);

    /** Undoes the last Mark as Cooked; returns what was undone. */
    Optional<String> undo();

    boolean saveRecipe(Recipe recipe);

    void rateRecipe(String id, int stars);

    boolean toggleFavourite(String id);
}
