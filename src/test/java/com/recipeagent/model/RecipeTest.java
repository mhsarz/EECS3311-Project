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

    @Test
    void replaceIngredientKeepsTheAmountAndRenamesItInTheSteps() {
        Recipe r = new Recipe("Pancakes",
                List.of(new Ingredient("buttermilk", 1, "cup"), new Ingredient("flour", 2, "cup")),
                List.of("Mix Buttermilk and flour", "Add more buttermilk if thick", "Serve buttermilks"), 20, 2);
        assertTrue(r.replaceIngredient(" buttermilk ", "milk with lemon"));
        assertEquals(new Ingredient("milk with lemon", 1, "cup"), r.getIngredients().get(0));
        assertEquals("Mix milk with lemon and flour", r.getSteps().get(0));
        assertEquals("Add more milk with lemon if thick", r.getSteps().get(1));
        assertEquals("Serve buttermilks", r.getSteps().get(2));       // whole words only
    }

    @Test
    void replaceIngredientMergesWithTheSameIngredientAndUnit() {
        Recipe r = new Recipe("Rice", List.of(new Ingredient("brown rice", 1, "cup"), new Ingredient("rice", 2, "cup")),
                List.of("Cook"), 20, 2);
        assertTrue(r.replaceIngredient("brown rice", "Rice"));
        assertEquals(List.of(new Ingredient("rice", 3, "cup")), r.getIngredients());
    }

    @Test
    void replaceIngredientThatIsNotThere() {
        Recipe r = friedRice();
        assertFalse(r.replaceIngredient("tofu", "tempeh"));
        assertFalse(r.replaceIngredient(null, "tempeh"));
        assertThrows(IllegalArgumentException.class, () -> r.replaceIngredient("egg", " "));
        assertTrue(r.replaceIngredient("egg", "Egg"));                 // same name: nothing to change
        assertEquals("egg", r.getIngredients().get(1).getName());
        assertTrue(r.replaceIngredient("egg", "tofu"));
        assertEquals("Fry with eggs", r.getSteps().get(1));            // "eggs" is not the word "egg"
    }

    @Test
    void substituteRemembersThePantryItemsItIsMadeFrom() {
        Recipe r = new Recipe("Pancakes", List.of(new Ingredient("buttermilk", 1, "cup")), List.of("Mix"), 20, 2);
        assertEquals(List.of(), r.getSubstituteParts("buttermilk"));
        r.replaceIngredient("buttermilk", "Milk with lemon", List.of("milk", " lemon ", " "));
        assertEquals(List.of("milk", "lemon"), r.getSubstituteParts("milk with LEMON"));
        r.replaceIngredient("milk with lemon", "yogurt");                // replaced again: forget the old parts
        assertEquals(List.of(), r.getSubstituteParts("milk with lemon"));
        assertEquals(List.of(), r.getSubstituteParts("yogurt"));
        assertEquals(List.of(), r.getSubstituteParts(null));
    }
}
