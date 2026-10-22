package com.recipeagent.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeRequestAndProfileTest {

    @Test
    void requestRejectsZeroPortionsOrTime() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeRequest(30, 0));
        assertThrows(IllegalArgumentException.class, () -> new RecipeRequest(0, 2));
    }

    @Test
    void requestHoldsRequiredItemsAndFavourites() {
        RecipeRequest req = new RecipeRequest(30, 2);
        req.addRequiredItem("spinach");
        req.setFavourites(Arrays.asList("Pasta", " ", null, "Soup"));
        assertEquals(List.of("spinach"), req.getRequiredItems());
        assertEquals(List.of("Pasta", "Soup"), req.getFavourites());
        assertFalse(req.isAllowMissing());
    }

    @Test
    void newProfileHasDefaults() {
        UserProfile p = new UserProfile();
        assertEquals(UserProfile.NO_DIET, p.getDiet());
        assertTrue(p.getAllergies().isEmpty());
        assertEquals(UserProfile.DEFAULT_PORTIONS, p.getDefaultPortions());
    }

    @Test
    void profileNormalizesAndDeduplicates() {
        UserProfile p = new UserProfile();
        p.setDiet(" Vegetarian ");
        p.addAllergy("Peanuts");
        p.addAllergy("peanuts ");
        p.setDislikes(Arrays.asList("Olives", "", null, "olives"));
        assertEquals("vegetarian", p.getDiet());
        assertEquals(List.of("peanuts"), p.getAllergies());
        assertEquals(List.of("olives"), p.getDislikes());
        assertTrue(p.isAllergicTo("PEANUTS"));
        assertFalse(p.isAllergicTo("milk"));
    }

    @Test
    void profileRejectsZeroPortions() {
        assertThrows(IllegalArgumentException.class, () -> new UserProfile().setDefaultPortions(0));
    }
}
