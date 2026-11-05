package com.recipeagent.gui;

import com.recipeagent.model.PantryItem;
import com.recipeagent.model.StorageLocation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the GUI's input checks and table data. These run without a screen. */
class GuiLogicTest {

    @Test
    void parsesQuantities() {
        assertEquals(2.0, InputParser.quantity(" 2 "));
        assertEquals(1.5, InputParser.quantity("1,5"));
        assertEquals("Quantity is required.", message(() -> InputParser.quantity("")));
        assertEquals("Quantity must be a number, like 2 or 1.5.", message(() -> InputParser.quantity("two")));
        assertEquals("Quantity must be more than zero.", message(() -> InputParser.quantity("0")));
        assertEquals("Quantity must be more than zero.", message(() -> InputParser.quantity("-3")));
    }

    @Test
    void parsesOptionalDates() {
        assertNull(InputParser.optionalDate("  "));
        assertEquals(LocalDate.of(2026, 11, 20), InputParser.optionalDate("2026-11-20"));
        assertTrue(message(() -> InputParser.optionalDate("20/11/2026")).contains("2026-11-20"));
        assertTrue(message(() -> InputParser.optionalDate("2026-02-30")).contains("year-month-day"));
    }

    @Test
    void parsesNamesAndWholeNumbers() {
        assertEquals("rice", InputParser.name(" rice ", "Name"));
        assertEquals("Name is required.", message(() -> InputParser.name(" ", "Name")));
        assertEquals(30, InputParser.wholeNumber("30", "Minutes", 1, 600));
        assertEquals("Minutes must be between 1 and 600.", message(() -> InputParser.wholeNumber("0", "Minutes", 1, 600)));
        assertEquals("Minutes must be a whole number.", message(() -> InputParser.wholeNumber("2.5", "Minutes", 1, 600)));
    }

    @Test
    void formatsQuantitiesWithoutUselessDecimals() {
        assertEquals("2", InputParser.formatQuantity(2.0));
        assertEquals("1.5", InputParser.formatQuantity(1.5));
    }

    @Test
    void tableShowsOneRowPerItemWithDaysLeft() {
        LocalDate today = LocalDate.of(2026, 11, 3);
        PantryTableModel model = new PantryTableModel();
        model.setItems(List.of(
                new PantryItem("milk", 1, "L", StorageLocation.FRIDGE, today.plusDays(2)),
                new PantryItem("rice", 2, "kg", StorageLocation.PANTRY, null),
                new PantryItem("yogurt", 1, "", StorageLocation.FRIDGE, today.minusDays(1)),
                new PantryItem("cheese", 1, "", StorageLocation.FRIDGE, today.plusDays(9))), today);

        assertEquals(4, model.getRowCount());
        assertEquals(6, model.getColumnCount());
        assertEquals("Qty", model.getColumnName(1));
        assertEquals("milk", model.getValueAt(0, 0));
        assertEquals("Fridge", model.getValueAt(0, 3));
        assertEquals(LocalDate.of(2026, 11, 5), model.getValueAt(0, PantryTableModel.BEST_BEFORE_COLUMN));
        assertEquals(2, model.getValueAt(0, PantryTableModel.DAYS_LEFT_COLUMN));
        assertNull(model.getValueAt(1, PantryTableModel.DAYS_LEFT_COLUMN));
        assertNull(model.getValueAt(1, PantryTableModel.BEST_BEFORE_COLUMN));
        assertEquals(LocalDate.class, model.getColumnClass(PantryTableModel.BEST_BEFORE_COLUMN));

        assertEquals(PantryTableModel.Status.USE_SOON, model.getStatusAt(0));
        assertEquals(PantryTableModel.Status.NO_DATE, model.getStatusAt(1));
        assertEquals(PantryTableModel.Status.EXPIRED, model.getStatusAt(2));
        assertEquals(PantryTableModel.Status.FRESH, model.getStatusAt(3));
    }

    @Test
    void daysLeftChipsReadLikeTheDesign() {
        assertEquals("1 day · Use soon", PantryTableModel.daysLabel(1));
        assertEquals("3 days · Use soon", PantryTableModel.daysLabel(3));
        assertEquals("Today · Use soon", PantryTableModel.daysLabel(0));
        assertEquals("Expired · 2 days", PantryTableModel.daysLabel(-2));
        assertEquals("Expired · 1 day", PantryTableModel.daysLabel(-1));
        assertEquals("7 days", PantryTableModel.daysLabel(7));
        assertEquals("", PantryTableModel.daysLabel(null));
        assertEquals("Nov 4, 2026", PantryTableModel.formatDate(LocalDate.of(2026, 11, 4)));
        assertEquals("", PantryTableModel.formatDate(null));
    }

    @Test
    void addFormShowsOneClearMessage() {
        PantryPanel.FormCheck both = PantryPanel.checkForm(" ", "0");
        assertTrue(both.nameBad());
        assertTrue(both.qtyBad());
        assertEquals("Enter an item name and a quantity greater than 0.", both.message());
        assertEquals("Enter an item name.", PantryPanel.checkForm("", "2").message());
        assertEquals("Enter a quantity greater than 0, like 2 or 1.5.", PantryPanel.checkForm("milk", "abc").message());
        PantryPanel.FormCheck ok = PantryPanel.checkForm("milk", "1,5");
        assertNull(ok.message());
        assertFalse(ok.nameBad() || ok.qtyBad());
    }

    @Test
    void mergeDialogShowsTheNewTotal() {
        PantryItem inPantry = new PantryItem("milk", 1, "L", StorageLocation.FRIDGE, null);
        PantryItem adding = new PantryItem("Milk", 1.5, "L", StorageLocation.FRIDGE, null);
        assertArrayEquals(new String[]{"In pantry: 1 L", "Adding: 1.5 L", "Total: 2.5 L"},
                PantryPanel.mergeSummary(inPantry, adding));
        assertNull(PantryPanel.unitWarning(inPantry, adding));
        PantryItem inMl = new PantryItem("milk", 250, "ml", StorageLocation.FRIDGE, null);
        assertEquals("Units differ (L vs ml). The amounts are added as numbers, in L.",
                PantryPanel.unitWarning(inPantry, inMl));
    }

    @Test
    void ingredientAmountsReadNaturally() {
        assertEquals("1 cup", IngredientTableModel.amount(new com.recipeagent.model.Ingredient("rice", 1, "cup")));
        assertEquals("2", IngredientTableModel.amount(new com.recipeagent.model.Ingredient("eggs", 2, "")));
        assertEquals("to taste", IngredientTableModel.amount(new com.recipeagent.model.Ingredient("salt", 0, "")));
        IngredientTableModel m = new IngredientTableModel();
        m.setIngredients(List.of(new com.recipeagent.model.Ingredient("rice", 1.5, "cup")));
        assertEquals("rice", m.getValueAt(0, 0));
        assertEquals("1.5 cup", m.getValueAt(0, 1));
        m.setIngredients(null);
        assertEquals(0, m.getRowCount());
    }

    @Test
    void historyTableShowsRatingFavouriteAndCooks() {
        com.recipeagent.model.Recipe r = new com.recipeagent.model.Recipe("Curry",
                List.of(new com.recipeagent.model.Ingredient("rice", 1, "")), List.of("Cook"), 30, 2);
        r.setRating(4);
        r.setFavourite(true);
        r.recordCooked(LocalDate.of(2026, 11, 1));
        com.recipeagent.model.Recipe plain = new com.recipeagent.model.Recipe("Toast",
                List.of(new com.recipeagent.model.Ingredient("bread", 1, "")), List.of("Toast"), 5, 1);

        HistoryTableModel m = new HistoryTableModel();
        m.setRecipes(List.of(r, plain));
        assertEquals("★★★★☆", m.getValueAt(0, 1));
        assertEquals("♥", m.getValueAt(0, 2));
        assertEquals(1, m.getValueAt(0, 3));
        assertEquals("2026-11-01", m.getValueAt(0, 4));
        assertEquals(30, m.getValueAt(0, 5));
        assertEquals("not rated", m.getValueAt(1, 1));
        assertEquals("", m.getValueAt(1, 2));
        assertEquals("never", m.getValueAt(1, 4));

        m.setFancySymbols(false);                    // for fonts without star/heart symbols
        assertEquals("4/5", m.getValueAt(0, 1));
        assertEquals("yes", m.getValueAt(0, 2));
    }

    @Test
    void recipeTextForTheScreenAndDialogs() {
        assertEquals("1. Chop\n\n2. Boil", RecipePanel.formatSteps(List.of("Chop", "Boil")));
        assertEquals("", RecipePanel.formatSteps(List.of()));

        String subs = RecipePanel.formatSubstitutes("buttermilk", List.of(
                new com.recipeagent.model.Substitution("milk with lemon juice", "1 cup milk + 1 tbsp lemon", true),
                new com.recipeagent.model.Substitution("plain yogurt", "", false)));
        assertEquals("Instead of buttermilk you can use:\n"
                + "\n1. milk with lemon juice  (in your pantry)\n    1 cup milk + 1 tbsp lemon"
                + "\n2. plain yogurt", subs);

        String missing = RecipePanel.formatMissing(List.of(
                new com.recipeagent.model.Ingredient("soy sauce", 2, "tbsp"),
                new com.recipeagent.model.Ingredient("pepper", 0, "")));
        assertEquals("You are missing:\n\n•  soy sauce — 2 tbsp\n•  pepper — to taste", missing);
    }

    private static String message(Runnable r) {
        return assertThrows(IllegalArgumentException.class, r::run).getMessage();
    }
}
