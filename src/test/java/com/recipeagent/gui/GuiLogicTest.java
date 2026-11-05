package com.recipeagent.gui;

import com.recipeagent.model.IngredientStatus;
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
        m.setRecipes(List.of(plain, r));                                  // cooked recipe comes first
        assertEquals("Curry", m.getValueAt(0, HistoryTableModel.TITLE_COLUMN));
        assertEquals(Boolean.TRUE, m.getValueAt(0, HistoryTableModel.FAV_COLUMN));
        assertEquals(4, ((Integer) m.getValueAt(0, HistoryTableModel.RATING_COLUMN)).intValue());
        assertEquals(1, ((Integer) m.getValueAt(0, HistoryTableModel.COOKED_COLUMN)).intValue());
        assertEquals(LocalDate.of(2026, 11, 1), m.getValueAt(0, HistoryTableModel.LAST_COOKED_COLUMN));
        assertEquals(30, ((Integer) m.getValueAt(0, HistoryTableModel.TIME_COLUMN)).intValue());
        assertEquals(Boolean.FALSE, m.getValueAt(1, HistoryTableModel.FAV_COLUMN));
        assertEquals(0, ((Integer) m.getValueAt(1, HistoryTableModel.RATING_COLUMN)).intValue());
        assertNull(m.getValueAt(1, HistoryTableModel.LAST_COOKED_COLUMN));
        assertEquals(LocalDate.class, m.getColumnClass(HistoryTableModel.LAST_COOKED_COLUMN));
        assertEquals(Boolean.class, m.getColumnClass(HistoryTableModel.FAV_COLUMN));
        assertEquals("Never", HistoryTableModel.lastCooked(null));
        assertEquals("Nov 1, 2026", HistoryTableModel.lastCooked(LocalDate.of(2026, 11, 1)));
    }

    @Test
    void historyIsMostRecentlyCookedFirstThenNewestSaved() {
        com.recipeagent.model.Recipe a = recipe("A");
        com.recipeagent.model.Recipe b = recipe("B");
        com.recipeagent.model.Recipe c = recipe("C");
        com.recipeagent.model.Recipe d = recipe("D");
        b.recordCooked(LocalDate.of(2026, 10, 1));
        d.recordCooked(LocalDate.of(2026, 11, 2));
        List<String> titles = HistoryTableModel.recentFirst(List.of(a, b, c, d)).stream()
                .map(com.recipeagent.model.Recipe::getTitle).toList();
        assertEquals(List.of("D", "B", "C", "A"), titles);   // saved order is A, B, C, D
    }

    @Test
    void historyFooterTotals() {
        com.recipeagent.model.Recipe a = recipe("A");
        com.recipeagent.model.Recipe b = recipe("B");
        a.setRating(5);
        b.setRating(4);
        a.setFavourite(true);
        a.recordCooked(LocalDate.of(2026, 11, 1));
        a.recordCooked(LocalDate.of(2026, 11, 3));
        assertEquals("2 recipes · 1 favourite · cooked 2 times · average rating 4.5",
                HistoryTableModel.stats(List.of(a, b)));
        assertEquals("3 recipes · 1 favourite · cooked 2 times · average rating 4.5",   // unrated not averaged
                HistoryTableModel.stats(List.of(a, b, recipe("C"))));
        assertEquals("1 recipe · 0 favourites · cooked 0 times", HistoryTableModel.stats(List.of(recipe("X"))));
        assertEquals("0 recipes · 0 favourites · cooked 0 times", HistoryTableModel.stats(List.of()));
    }

    @Test
    void historyStarsAndEmptyStates() {
        assertEquals(0, HistoryPanel.starAt(5));                         // left padding
        assertEquals(1, HistoryPanel.starAt(HistoryPanel.STAR_LEFT));
        assertEquals(1, HistoryPanel.starAt(HistoryPanel.STAR_LEFT + HistoryPanel.STAR_SIZE));
        assertEquals(0, HistoryPanel.starAt(HistoryPanel.STAR_LEFT + HistoryPanel.STAR_SIZE + 2));   // the gap
        assertEquals(3, HistoryPanel.starAt(HistoryPanel.STAR_LEFT + 2 * HistoryPanel.STAR_STEP + 5));
        assertEquals(5, HistoryPanel.starAt(HistoryPanel.STAR_LEFT + 4 * HistoryPanel.STAR_STEP));
        assertEquals(0, HistoryPanel.starAt(HistoryPanel.STAR_LEFT + 5 * HistoryPanel.STAR_STEP));

        assertEquals("No saved recipes yet", HistoryPanel.emptyTitle(true, "", false));
        assertEquals("No recipes match \"tofu\"", HistoryPanel.emptyTitle(false, " tofu ", false));
        assertEquals("No recipes match \"tofu\" in your favourites", HistoryPanel.emptyTitle(false, "tofu", true));
        assertEquals("No favourites yet", HistoryPanel.emptyTitle(false, "", true));
    }

    @Test
    void cartTableAndMessages() {
        com.recipeagent.model.CartItem milk = new com.recipeagent.model.CartItem("milk", 2, "L",
                com.recipeagent.model.CartSource.CHAT);
        com.recipeagent.model.CartItem eggs = new com.recipeagent.model.CartItem("eggs", 12);
        eggs.setOnSale(true);
        CartTableModel m = new CartTableModel();
        m.setItems(List.of(milk, eggs));
        assertEquals(2, m.getRowCount());
        assertEquals("milk", m.getValueAt(0, 0));
        assertEquals("L", m.getValueAt(0, 2));
        assertEquals(com.recipeagent.model.CartSource.CHAT, m.getValueAt(0, CartTableModel.SOURCE_COLUMN));
        assertEquals(com.recipeagent.model.CartSource.MANUAL, m.getValueAt(1, CartTableModel.SOURCE_COLUMN));
        assertEquals(Boolean.TRUE, m.getValueAt(1, CartTableModel.SALE_COLUMN));
        assertEquals("2 items · 1 on sale", CartTableModel.summary(List.of(milk, eggs)));
        assertEquals("1 item", CartTableModel.summary(List.of(milk)));
        assertEquals("2 L", CartTableModel.amount(milk));
        assertEquals("12", CartTableModel.amount(eggs));
        assertEquals("Added milk.", CartPanel.addedMessage(milk, false));
        assertEquals("milk was already in your cart; it now has 2 L.", CartPanel.addedMessage(milk, true));
        assertEquals("", CartPanel.UNITS[0]);                            // no unit by default
        assertEquals("pcs", CartPanel.UNITS[1]);
        m.setItems(null);
        assertEquals(0, m.getRowCount());
    }

    private static com.recipeagent.model.Recipe recipe(String title) {
        return new com.recipeagent.model.Recipe(title,
                List.of(new com.recipeagent.model.Ingredient("rice", 1, "")), List.of("Cook"), 10, 1);
    }

    @Test
    void recipeStepsAreNumbered() {
        assertEquals("1. Chop\n\n2. Boil", RecipePanel.formatSteps(List.of("Chop", "Boil")));
        assertEquals("", RecipePanel.formatSteps(List.of()));
    }

    @Test
    void recipeCardSummaryAndChips() {
        List<IngredientStatus> st = List.of(IngredientStatus.IN_PANTRY, IngredientStatus.USE_SOON,
                IngredientStatus.USE_SOON, IngredientStatus.MISSING, IngredientStatus.NOT_ENOUGH);
        assertEquals("3 in pantry · 2 missing", RecipePanel.summary(st));
        assertEquals("1 in pantry · 0 missing · 1 basic",
                RecipePanel.summary(List.of(IngredientStatus.EXPIRED, IngredientStatus.BASIC)));
        assertEquals("Uses 2 expiring items", RecipePanel.expiringText(st));
        assertEquals("Uses 1 expiring item", RecipePanel.expiringText(List.of(IngredientStatus.USE_SOON)));
        assertNull(RecipePanel.expiringText(List.of(IngredientStatus.IN_PANTRY)));

        assertEquals("In pantry", RecipePanel.statusLabel(IngredientStatus.IN_PANTRY));
        assertEquals("Use soon", RecipePanel.statusLabel(IngredientStatus.USE_SOON));
        assertEquals("Not enough", RecipePanel.statusLabel(IngredientStatus.NOT_ENOUGH));
        assertEquals("Missing", RecipePanel.statusLabel(IngredientStatus.MISSING));
        assertEquals("Expired", RecipePanel.statusLabel(IngredientStatus.EXPIRED));
        assertEquals("Basic", RecipePanel.statusLabel(IngredientStatus.BASIC));

        assertTrue(RecipePanel.canSubstitute(IngredientStatus.MISSING));
        assertTrue(RecipePanel.canSubstitute(IngredientStatus.NOT_ENOUGH));
        assertTrue(RecipePanel.canSubstitute(IngredientStatus.EXPIRED));
        assertFalse(RecipePanel.canSubstitute(IngredientStatus.IN_PANTRY));
        assertFalse(RecipePanel.canSubstitute(IngredientStatus.BASIC));
    }

    @Test
    void recipeMetaLine() {
        com.recipeagent.model.Recipe r = new com.recipeagent.model.Recipe("Soup",
                List.of(new com.recipeagent.model.Ingredient("leek", 1, "")), List.of("Boil"), 25, 1);
        assertEquals("25 min · 1 portion", RecipePanel.meta(r));
        r.recordCooked(LocalDate.of(2026, 11, 1));
        assertEquals("25 min · 1 portion · Cooked once", RecipePanel.meta(r));
        r.recordCooked(LocalDate.of(2026, 11, 2));
        assertEquals("25 min · 1 portion · Cooked 2 times", RecipePanel.meta(r));
    }

    @Test
    void recipeMessages() {
        assertEquals("Thinking of a recipe…", RecipePanel.loadingText(List.of()));
        assertEquals("Finding a recipe that uses milk, spinach…", RecipePanel.loadingText(List.of("milk", "spinach")));
        assertEquals("You're missing 1 ingredient", RecipePanel.missingTitle(1));
        assertEquals("You're missing 3 ingredients", RecipePanel.missingTitle(3));
        assertEquals("Marked as cooked and taken out of your pantry. Click Undo if this was a mistake.",
                RecipePanel.cookedMessage(List.of()));
        assertTrue(RecipePanel.cookedMessage(List.of("basil", "mint")).contains("skipped: basil, mint."));
    }

    @Test
    void missingDialogRowsSayWhy() {
        List<com.recipeagent.model.Ingredient> recipe = List.of(
                new com.recipeagent.model.Ingredient("Eggs", 3, ""),
                new com.recipeagent.model.Ingredient("soy sauce", 2, "tbsp"));
        java.util.Map<String, IngredientStatus> byName = RecipePanel.statusByName(recipe,
                List.of(IngredientStatus.NOT_ENOUGH, IngredientStatus.MISSING));
        List<String[]> rows = RecipePanel.missingRows(List.of(
                new com.recipeagent.model.Ingredient("eggs", 1, ""),
                new com.recipeagent.model.Ingredient("soy sauce", 2, "tbsp")), byName);
        assertArrayEquals(new String[]{"eggs", "1", "Not enough in pantry"}, rows.get(0));
        assertArrayEquals(new String[]{"soy sauce", "2 tbsp", "Not in pantry"}, rows.get(1));
    }

    @Test
    void substituteDialogPicksOneFromThePantryFirst() {
        com.recipeagent.model.Substitution a = new com.recipeagent.model.Substitution("yogurt", "", false);
        com.recipeagent.model.Substitution b = new com.recipeagent.model.Substitution("milk + lemon", "", true);
        assertEquals(1, RecipePanel.defaultChoice(List.of(a, b)));
        assertEquals(0, RecipePanel.defaultChoice(List.of(a)));
    }

    @Test
    void cancelledBackgroundTaskIgnoresTheResult() throws Exception {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch idle = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean gotResult = new java.util.concurrent.atomic.AtomicBoolean();
        Background.Task[] task = new Background.Task[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> task[0] = Background.run(
                busy -> {
                    if (!busy) {
                        idle.countDown();
                    }
                },
                () -> {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "late answer";
                },
                r -> gotResult.set(true),
                ex -> gotResult.set(true)));
        javax.swing.SwingUtilities.invokeAndWait(() -> task[0].cancel());
        assertTrue(idle.await(5, java.util.concurrent.TimeUnit.SECONDS));   // no longer busy
        assertTrue(task[0].isCancelled());
        release.countDown();
        Thread.sleep(200);
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        assertFalse(gotResult.get());
    }

    private static String message(Runnable r) {
        return assertThrows(IllegalArgumentException.class, r::run).getMessage();
    }
}
