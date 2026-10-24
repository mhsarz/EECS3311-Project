package com.recipeagent.pantry;

import com.recipeagent.model.Ingredient;
import com.recipeagent.model.IngredientCheck;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.Recipe;
import com.recipeagent.model.StorageLocation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PantryManagerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

    private static PantryItem item(String name, double qty, LocalDate bestBefore) {
        return new PantryItem(name, qty, "", StorageLocation.PANTRY, bestBefore);
    }

    @Test
    void addsNewItem() {
        PantryManager pm = new PantryManager();
        assertTrue(pm.isEmpty());
        assertTrue(pm.addItem(item("rice", 2, null)));
        assertEquals(1, pm.getAll().size());
    }

    @Test
    void refusesDuplicateNameIgnoringCase() {
        PantryManager pm = new PantryManager();
        pm.addItem(item("Rice", 2, null));
        assertFalse(pm.addItem(item("rice ", 1, null)));
        assertEquals(1, pm.getAll().size());
    }

    @Test
    void rejectsZeroQuantityAndNull() {
        PantryManager pm = new PantryManager();
        assertThrows(IllegalArgumentException.class, () -> pm.addItem(item("rice", 0, null)));
        assertThrows(IllegalArgumentException.class, () -> pm.addItem(null));
    }

    @Test
    void mergeAddsToExistingQuantity() {
        PantryManager pm = new PantryManager();
        PantryItem rice = item("rice", 2, null);
        pm.addItem(rice);
        pm.mergeQuantity(rice.getId(), 1.5);
        assertEquals(3.5, pm.findByName("rice").get(0).getQuantity());
        assertThrows(IllegalArgumentException.class, () -> pm.mergeQuantity("nope", 1));
        assertThrows(IllegalArgumentException.class, () -> pm.mergeQuantity(rice.getId(), 0));
    }

    @Test
    void removesByIdAndByName() {
        PantryManager pm = new PantryManager();
        PantryItem rice = item("rice", 2, null);
        pm.addItem(rice);
        pm.addItem(item("pasta", 1, null));
        assertTrue(pm.removeItem(rice.getId()));
        assertFalse(pm.removeItem(rice.getId()));
        assertTrue(pm.removeByName("PASTA"));
        assertTrue(pm.isEmpty());
    }

    @Test
    void initialItemsSkipDuplicatesAndNulls() {
        PantryManager pm = new PantryManager(java.util.Arrays.asList(
                item("milk", 1, null), null, item("Milk", 2, null)));
        assertEquals(1, pm.getAll().size());
        assertThrows(UnsupportedOperationException.class,
                () -> pm.getAll().add(item("x", 1, null)));
    }

    @Test
    void findsExpiringItemsSoonestFirst() {
        PantryManager pm = new PantryManager();
        pm.addItem(item("yogurt", 1, TODAY.plusDays(3)));
        pm.addItem(item("milk", 1, TODAY.plusDays(1)));
        pm.addItem(item("cheese", 1, TODAY.plusDays(10)));
        pm.addItem(item("salt", 1, null));
        pm.addItem(item("bread", 1, TODAY.minusDays(2)));

        List<PantryItem> expiring = pm.getExpiringItems(3, TODAY);
        assertEquals(List.of("bread", "milk", "yogurt"),
                expiring.stream().map(PantryItem::getName).toList());
        assertTrue(pm.getExpiringItems(0, TODAY.minusDays(30)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> pm.getExpiringItems(-1, TODAY));
    }

    @Test
    void deductReturnsPreviousAndStopsAtZero() {
        PantryManager pm = new PantryManager();
        pm.addItem(item("eggs", 6, null));
        assertEquals(6, pm.deduct("Eggs", 2));
        assertEquals(4, pm.findByName("eggs").get(0).getQuantity());
        assertEquals(4, pm.deduct("eggs", 10));
        assertEquals(0, pm.findByName("eggs").get(0).getQuantity());
        assertEquals(PantryManager.NOT_IN_PANTRY, pm.deduct("butter", 1));
    }

    @Test
    void restorePutsQuantitiesBack() {
        PantryManager pm = new PantryManager();
        pm.addItem(item("eggs", 6, null));
        pm.addItem(item("rice", 2, null));
        double eggs = pm.deduct("eggs", 2);
        double rice = pm.deduct("rice", 5);

        pm.restore(Map.of("eggs", eggs, "rice", rice, "butter", 1.0));
        assertEquals(6, pm.findByName("eggs").get(0).getQuantity());
        assertEquals(2, pm.findByName("rice").get(0).getQuantity());
        assertTrue(pm.findByName("butter").isEmpty());
        pm.restore(null);
    }

    @Test
    void checkRecipeListsMissingAndShortfall() {
        PantryManager pm = new PantryManager();
        pm.addItem(item("rice", 1, null));
        pm.addItem(item("eggs", 6, null));
        pm.addItem(item("green onions", 1, null));
        Recipe r = new Recipe("Fried rice", List.of(
                new Ingredient("rice", 3, "cup"),
                new Ingredient("eggs", 2, ""),
                new Ingredient("scallions", 2, ""),
                new Ingredient("soy sauce", 1, "tbsp")), List.of("Cook"), 20, 2);

        IngredientCheck check = pm.checkRecipe(r);
        assertEquals(List.of("rice", "scallions", "soy sauce"), check.getMissingNames());
        assertEquals(2, check.getMissing().get(0).getQuantity());
        assertEquals(List.of("scallions", "soy sauce"), pm.unmatchedNames(r));
    }

    @Test
    void checkRecipeAllInStock() {
        PantryManager pm = new PantryManager();
        pm.addItem(item("pasta", 2, null));
        Recipe r = new Recipe("Pasta", List.of(new Ingredient("pasta", 1, "")),
                List.of("Boil"), 10, 1);
        assertTrue(pm.checkRecipe(r).allInStock());
        assertThrows(IllegalArgumentException.class, () -> pm.checkRecipe(null));
    }
}
