package com.recipeagent.history;

import com.recipeagent.model.Ingredient;
import com.recipeagent.model.Recipe;
import com.recipeagent.storage.JsonStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeHistoryTest {

    @TempDir
    Path tmp;

    private static Recipe recipe(String title, String ingredient) {
        return new Recipe(title, List.of(new Ingredient(ingredient, 1, "")),
                List.of("Cook it"), 20, 2);
    }

    @Test
    void savesWithoutDuplicates() {
        RecipeHistory h = new RecipeHistory();
        Recipe r = recipe("Chicken Curry", "chicken");
        assertTrue(h.save(r));
        assertFalse(h.save(r));
        assertEquals(1, h.getAll().size());
        assertThrows(IllegalArgumentException.class, () -> h.save(null));
    }

    @Test
    void ratesAndRejectsBadRatingOrId() {
        RecipeHistory h = new RecipeHistory();
        Recipe r = recipe("Soup", "carrot");
        h.save(r);
        h.rate(r.getId(), 5);
        assertEquals(5, h.findById(r.getId()).orElseThrow().getRating());
        assertThrows(IllegalArgumentException.class, () -> h.rate(r.getId(), 6));
        assertThrows(IllegalArgumentException.class, () -> h.rate("missing", 3));
    }

    @Test
    void togglesFavourite() {
        RecipeHistory h = new RecipeHistory();
        Recipe r = recipe("Soup", "carrot");
        h.save(r);
        assertTrue(h.toggleFavourite(r.getId()));
        assertFalse(h.toggleFavourite(r.getId()));
        assertThrows(IllegalArgumentException.class, () -> h.toggleFavourite("missing"));
    }

    @Test
    void searchesByTitleOrIngredient() {
        RecipeHistory h = new RecipeHistory();
        h.save(recipe("Chicken Curry", "chicken"));
        h.save(recipe("Veggie Stir Fry", "broccoli"));
        assertEquals(1, h.search("curry").size());
        assertEquals(1, h.search("BROCCOLI").size());
        assertTrue(h.search("pizza").isEmpty());
        assertEquals(2, h.search("").size());
    }

    @Test
    void topRatedOrdersByRatingThenFavouriteThenCooks() {
        RecipeHistory h = new RecipeHistory();
        Recipe a = recipe("A", "x");
        Recipe b = recipe("B", "x");
        Recipe c = recipe("C", "x");
        Recipe d = recipe("D", "x");
        Recipe unrated = recipe("Unrated", "x");
        for (Recipe r : List.of(a, b, c, d, unrated)) {
            h.save(r);
        }
        h.rate(a.getId(), 3);
        h.rate(b.getId(), 5);
        h.rate(c.getId(), 5);
        h.toggleFavourite(c.getId());
        h.toggleFavourite(d.getId());
        h.rate(a.getId(), 3);

        assertEquals(List.of("C", "B", "A", "D"), h.getTopRatedTitles(10));
        assertEquals(List.of("C", "B"), h.getTopRatedTitles(2));
        assertTrue(h.getTopRated(0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> h.getTopRated(-1));
    }

    @Test
    void topRatedBreaksTiesByTimesCooked() {
        RecipeHistory h = new RecipeHistory();
        Recipe a = recipe("A", "x");
        Recipe b = recipe("B", "x");
        h.save(a);
        h.save(b);
        h.rate(a.getId(), 4);
        h.rate(b.getId(), 4);
        h.recordCooked(b.getId());
        assertEquals(List.of("B", "A"), h.getTopRatedTitles(5));
    }

    @Test
    void recordsAndUndoesCooking() {
        RecipeHistory h = new RecipeHistory();
        Recipe r = recipe("Pasta", "pasta");
        h.save(r);
        LocalDate oct20 = LocalDate.of(2026, 10, 20);
        LocalDate oct26 = LocalDate.of(2026, 10, 26);

        assertTrue(h.recordCooked(r.getId(), oct20));
        assertTrue(h.recordCooked(r.getId(), oct26));
        assertEquals(2, r.getTimesCooked());
        assertEquals(oct26, r.getLastCooked());

        assertTrue(h.undoCooked(r.getId()));
        assertEquals(1, r.getTimesCooked());
        assertEquals(oct20, r.getLastCooked());

        assertTrue(h.undoCooked(r.getId()));
        assertFalse(h.undoCooked(r.getId()));
        assertNull(r.getLastCooked());

        assertFalse(h.recordCooked("missing"));
        assertFalse(h.undoCooked("missing"));
    }

    @Test
    void removesRecipe() {
        RecipeHistory h = new RecipeHistory();
        Recipe r = recipe("Pasta", "pasta");
        h.save(r);
        assertTrue(h.remove(r.getId()));
        assertFalse(h.remove(r.getId()));
        assertTrue(h.isEmpty());
    }

    @Test
    void survivesSaveAndLoad() {
        RecipeHistory h = new RecipeHistory();
        Recipe r = recipe("Pasta", "pasta");
        h.save(r);
        h.rate(r.getId(), 4);
        h.toggleFavourite(r.getId());
        h.recordCooked(r.getId(), LocalDate.of(2026, 10, 26));

        JsonStorage storage = new JsonStorage(tmp);
        storage.save("history", h.getAll());
        RecipeHistory loaded = new RecipeHistory(storage.loadList("history", Recipe.class));

        Recipe back = loaded.findById(r.getId()).orElseThrow();
        assertEquals(4, back.getRating());
        assertTrue(back.isFavourite());
        assertEquals(1, back.getTimesCooked());
        assertEquals("pasta", back.getIngredients().get(0).getName());
    }
}
