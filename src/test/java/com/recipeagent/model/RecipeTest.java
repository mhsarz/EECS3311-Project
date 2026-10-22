package com.recipeagent.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeTest {

    private Recipe friedRice() {
        return new Recipe("Egg Fried Rice",
                List.of(new Ingredient("rice", 2, "cup"), new Ingredient("egg", 2, "")),
                List.of("Cook rice", "Fry with eggs"), 20, 2);
    }

    @Test
    void newRecipeIsUnratedAndNotFavourite() {
        Recipe r = friedRice();
        assertNotNull(r.getId());
        assertFalse(r.isRated());
        assertFalse(r.isFavourite());
        assertEquals(2, r.getIngredients().size());
    }

    @Test
    void rejectsInvalidRecipe() {
        assertThrows(IllegalArgumentException.class,
                () -> new Recipe(" ", List.of(), List.of(), 20, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new Recipe("Soup", List.of(), List.of(), 0, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new Recipe("Soup", List.of(), List.of(), 20, 0));
    }

    @Test
    void ratingMustBeOneToFive() {
        Recipe r = friedRice();
        r.setRating(4);
        assertEquals(4, r.getRating());
        assertThrows(IllegalArgumentException.class, () -> r.setRating(0));
        assertThrows(IllegalArgumentException.class, () -> r.setRating(6));
        assertEquals(4, r.getRating());
    }

    @Test
    void toggleFavouriteFlipsFlag() {
        Recipe r = friedRice();
        assertTrue(r.toggleFavourite());
        assertFalse(r.toggleFavourite());
    }

    @Test
    void searchMatchesTitleOrIngredient() {
        Recipe r = friedRice();
        assertTrue(r.matches("fried"));
        assertTrue(r.matches("EGG"));
        assertFalse(r.matches("chicken"));
    }

    @Test
    void listsCannotBeChangedFromOutside() {
        List<String> steps = new ArrayList<>(List.of("Boil"));
        Recipe r = new Recipe("Pasta", List.of(), steps, 10, 1);
        steps.add("Hacked");
        assertEquals(1, r.getSteps().size());
        assertThrows(UnsupportedOperationException.class, () -> r.getSteps().add("x"));
    }
}
