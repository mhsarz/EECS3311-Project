package com.recipeagent.storage;

import com.recipeagent.cart.ShoppingCart;
import com.recipeagent.history.RecipeHistory;
import com.recipeagent.model.CartItem;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.Recipe;
import com.recipeagent.pantry.PantryManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Saved files can be edited by hand (for example sample data). Entries with missing
 * fields must not crash the app: optional lists are treated as empty, and entries
 * without their required fields are skipped.
 */
class DamagedDataTest {

    @TempDir
    Path tmp;

    @Test
    void recipeWithoutListsLoadsAndCanBeSearched() throws IOException {
        Files.writeString(tmp.resolve("history.json"),
                "[{\"id\":\"r1\",\"title\":\"Toast\",\"cookTimeMinutes\":5,\"portions\":1}]");
        RecipeHistory h = new RecipeHistory(new JsonStorage(tmp).loadList("history", Recipe.class));
        Recipe r = h.findById("r1").orElseThrow();
        assertTrue(r.getIngredients().isEmpty());
        assertTrue(r.getSteps().isEmpty());
        assertEquals(0, r.getTimesCooked());
        assertFalse(r.matches("bread"));
        assertTrue(r.matches("toast"));
    }

    @Test
    void recipeWithoutIdIsSkipped() throws IOException {
        Files.writeString(tmp.resolve("history.json"),
                "[{\"title\":\"No id\",\"cookTimeMinutes\":5,\"portions\":1},"
                        + "{\"id\":\"r2\",\"title\":\"Soup\",\"cookTimeMinutes\":20,\"portions\":2}]");
        RecipeHistory h = new RecipeHistory(new JsonStorage(tmp).loadList("history", Recipe.class));
        assertEquals(1, h.getAll().size());
        assertTrue(h.contains("r2"));
    }

    @Test
    void pantryItemWithoutLocationOrNameIsSkipped() throws IOException {
        Files.writeString(tmp.resolve("pantry.json"),
                "[{\"id\":\"1\",\"name\":\"milk\",\"quantity\":1,\"unit\":\"L\"},"
                        + "{\"id\":\"2\",\"quantity\":1,\"location\":\"PANTRY\"},"
                        + "{\"id\":\"3\",\"name\":\"rice\",\"quantity\":2,\"unit\":\"kg\",\"location\":\"pantry\"}]");
        PantryManager pm = new PantryManager(new JsonStorage(tmp).loadList("pantry", PantryItem.class));
        assertEquals(1, pm.getAll().size());
        assertEquals("rice", pm.getAll().get(0).getName());
    }

    @Test
    void cartItemWithoutNameIsSkipped() throws IOException {
        Files.writeString(tmp.resolve("cart.json"),
                "[{\"quantity\":1},{\"name\":\"eggs\",\"quantity\":12}]");
        ShoppingCart cart = new ShoppingCart(new JsonStorage(tmp).loadList("cart", CartItem.class));
        assertEquals(1, cart.size());
        assertTrue(cart.contains("eggs"));
    }
}
