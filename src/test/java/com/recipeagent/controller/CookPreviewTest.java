package com.recipeagent.controller;

import com.recipeagent.model.Ingredient;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.Recipe;
import com.recipeagent.model.StorageLocation;
import com.recipeagent.storage.JsonStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The "reduce these ingredients?" preview and the first-cook rating question (F06, F08). */
class CookPreviewTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tmp;

    private AgentController controller() {
        AgentController c = new AgentController(null, 100, new JsonStorage(tmp), CLOCK);
        c.addPantryItem(new PantryItem("chicken breast", 500, "g", StorageLocation.FRIDGE, null), false);
        c.addPantryItem(new PantryItem("rice", 2, "kg", StorageLocation.PANTRY, null), false);
        c.addPantryItem(new PantryItem("salt", 1, "box", StorageLocation.PANTRY, null), false);
        return c;
    }

    private static Recipe recipe() {
        return new Recipe("Chicken Rice", List.of(new Ingredient("Chicken Breast", 400, "g"),
                new Ingredient("rice", 1.5, "kg"), new Ingredient("rice", 1, "kg"),
                new Ingredient("parmesan", 50, "g"), new Ingredient("salt", 0, "")), List.of("Cook"), 30, 2);
    }

    @Test
    void previewShowsWhatWouldBeTakenWithoutChangingThePantry() {
        AgentController c = controller();
        CookPreview p = c.previewCooked(recipe());

        assertEquals(5, p.getLines().size());
        assertEquals(4, p.getUsed().size());
        assertEquals(List.of("parmesan"), p.getSkipped());
        assertEquals("chicken breast: 400 g (you have 500 g, 100 g left)", p.getLines().get(0).describe());
        assertEquals("rice: 1.5 kg (you have 2 kg, 0.5 kg left)", p.getLines().get(1).describe());
        assertEquals("rice: 1 kg (you have 0.5 kg, none left)", p.getLines().get(2).describe());   // listed twice
        assertEquals("salt: to taste (nothing taken)", p.getLines().get(4).describe());
        assertEquals(500, c.getPantryItems().get(0).getQuantity());                               // unchanged

        String q = p.question("Chicken Rice");
        assertTrue(q.startsWith("You are cooking \"Chicken Rice\". Reduce these ingredients in your pantry?\n"));
        assertTrue(q.contains("\n  chicken breast: 400 g (you have 500 g, 100 g left)"));
        assertTrue(q.endsWith("Not in your pantry, so nothing is taken: parmesan"));
    }

    @Test
    void previewWhenNothingIsInThePantry() {
        AgentController c = new AgentController(null, 100, new JsonStorage(tmp), CLOCK);
        CookPreview p = c.previewCooked(recipe());
        assertTrue(p.getUsed().isEmpty());
        String q = p.question("Chicken Rice");
        assertTrue(q.contains("None of its ingredients are in your pantry"));
        assertTrue(q.endsWith("Mark it as cooked anyway?"));
        assertFalse(q.contains("Not in your pantry, so nothing is taken"));
        assertThrows(IllegalArgumentException.class, () -> c.previewCooked(null));
    }

    @Test
    void previewMatchesWhatMarkAsCookedDoes() {
        AgentController c = controller();
        Recipe r = recipe();
        CookPreview p = c.previewCooked(r);
        assertEquals(p.getSkipped(), c.markCooked(r));
        assertEquals(100, c.getPantryItems().get(0).getQuantity());
        assertEquals(0, c.getPantryItems().get(1).getQuantity());
    }

    @Test
    void ratingIsAskedOnlyTheFirstTime() {
        AgentController c = controller();
        Recipe fresh = recipe();
        assertTrue(c.isFirstCook(fresh));                // not in the history yet
        c.markCooked(fresh);
        assertFalse(c.isFirstCook(fresh));               // cooked once (rating skipped)

        Recipe saved = new Recipe("Soup", List.of(new Ingredient("rice", 1, "kg")), List.of("Boil"), 20, 2);
        c.saveRecipe(saved);
        assertTrue(c.isFirstCook(saved));                // saved but never cooked or rated
        c.rateRecipe(saved.getId(), 4);
        assertFalse(c.isFirstCook(saved));               // already rated in History
        assertFalse(c.isFirstCook(null));
    }

    @Test
    void saysWhenTheUnitsDiffer() {
        CookPreview.Line l = new CookPreview.Line("rice", "rice", 1, "cup", 500, 499, "g");
        assertEquals("rice: 1 cup (you have 500 g, 499 g left; the units differ)", l.describe());
        assertFalse(new CookPreview.Line("eggs", "eggs", 2, "", 6, 4, "pcs").unitsDiffer());
        assertFalse(new CookPreview.Line("milk", "milk", 1, "L", 2, 1, "l").unitsDiffer());
    }

    @Test
    void amountsAreShort() {
        assertEquals("2 kg", CookPreview.amount(2.0, "kg"));
        assertEquals("0.33 L", CookPreview.amount(1 / 3.0, "L"));
        assertEquals("3", CookPreview.amount(3, ""));
        assertEquals("1.5", CookPreview.amount(1.5, null));
    }
}
