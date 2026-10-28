package com.recipeagent.command;

import com.recipeagent.history.RecipeHistory;
import com.recipeagent.model.Ingredient;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.Recipe;
import com.recipeagent.model.StorageLocation;
import com.recipeagent.pantry.PantryManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CookRecipeCommandTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private PantryManager pantry() {
        PantryManager pm = new PantryManager();
        pm.addItem(new PantryItem("rice", 3, "cup", StorageLocation.PANTRY, null));
        pm.addItem(new PantryItem("eggs", 1, "", StorageLocation.FRIDGE, null));
        return pm;
    }

    private Recipe friedRice() {
        return new Recipe("Egg Fried Rice", List.of(
                new Ingredient("rice", 2, "cup"),
                new Ingredient("Eggs", 2, ""),
                new Ingredient("soy sauce", 1, "tbsp")), List.of("Cook"), 20, 2);
    }

    private double qty(PantryManager pm, String name) {
        return pm.findByName(name).get(0).getQuantity();
    }

    @Test
    void executeDeductsAndRecordsCook() {
        PantryManager pm = pantry();
        RecipeHistory h = new RecipeHistory();
        Recipe r = friedRice();
        CookRecipeCommand cmd = new CookRecipeCommand(r, pm, h, TODAY);

        cmd.execute();

        assertEquals(1, qty(pm, "rice"));
        assertEquals(0, qty(pm, "eggs"));            // had 1, recipe used 2: set to zero
        assertEquals(List.of("soy sauce"), cmd.getSkipped());
        assertEquals(3.0, (double) cmd.getSnapshot().get("rice"));
        assertEquals(1.0, (double) cmd.getSnapshot().get("eggs"));
        assertTrue(h.contains(r.getId()));
        assertEquals(1, r.getTimesCooked());
        assertEquals(TODAY, r.getLastCooked());
        assertTrue(cmd.isExecuted());
    }

    @Test
    void undoRestoresPantryAndRemovesUnsavedRecipe() {
        PantryManager pm = pantry();
        RecipeHistory h = new RecipeHistory();
        Recipe r = friedRice();
        CookRecipeCommand cmd = new CookRecipeCommand(r, pm, h, TODAY);
        cmd.execute();

        cmd.undo();

        assertEquals(3, qty(pm, "rice"));
        assertEquals(1, qty(pm, "eggs"));
        assertEquals(0, r.getTimesCooked());
        assertFalse(h.contains(r.getId()));
        assertFalse(cmd.isExecuted());
    }

    @Test
    void undoKeepsRecipeThatWasAlreadySaved() {
        PantryManager pm = pantry();
        RecipeHistory h = new RecipeHistory();
        Recipe r = friedRice();
        h.save(r);
        h.recordCooked(r.getId(), TODAY.minusDays(7));
        CookRecipeCommand cmd = new CookRecipeCommand(r, pm, h, TODAY);

        cmd.execute();
        assertEquals(2, r.getTimesCooked());
        cmd.undo();

        assertTrue(h.contains(r.getId()));
        assertEquals(1, r.getTimesCooked());
        assertEquals(TODAY.minusDays(7), r.getLastCooked());
    }

    @Test
    void repeatedIngredientRestoresOriginalQuantity() {
        PantryManager pm = pantry();
        Recipe r = new Recipe("Rice bowl", List.of(
                new Ingredient("rice", 1, "cup"), new Ingredient("rice", 1, "cup")),
                List.of("Cook"), 10, 1);
        CookRecipeCommand cmd = new CookRecipeCommand(r, pm, new RecipeHistory(), TODAY);
        cmd.execute();
        assertEquals(1, qty(pm, "rice"));
        cmd.undo();
        assertEquals(3, qty(pm, "rice"));
    }

    @Test
    void cannotExecuteTwiceOrUndoBeforeExecute() {
        CookRecipeCommand cmd = new CookRecipeCommand(friedRice(), pantry(), new RecipeHistory(), TODAY);
        assertThrows(IllegalStateException.class, cmd::undo);
        cmd.execute();
        assertThrows(IllegalStateException.class, cmd::execute);
        cmd.undo();
        cmd.execute();      // can be done again after undo
        assertTrue(cmd.isExecuted());
    }

    @Test
    void requiresAllParts() {
        assertThrows(IllegalArgumentException.class,
                () -> new CookRecipeCommand(null, pantry(), new RecipeHistory(), TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> new CookRecipeCommand(friedRice(), null, new RecipeHistory(), TODAY));
        assertEquals("Cooked Egg Fried Rice",
                new CookRecipeCommand(friedRice(), pantry(), new RecipeHistory()).getDescription());
    }
}
