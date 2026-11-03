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
                new PantryItem("rice", 2, "kg", StorageLocation.PANTRY, null)), today);

        assertEquals(2, model.getRowCount());
        assertEquals(6, model.getColumnCount());
        assertEquals("Item", model.getColumnName(0));
        assertEquals("milk", model.getValueAt(0, 0));
        assertEquals("Fridge", model.getValueAt(0, 3));
        assertEquals("2026-11-05", model.getValueAt(0, 4));
        assertEquals(2, model.getValueAt(0, PantryTableModel.DAYS_LEFT_COLUMN));
        assertNull(model.getValueAt(1, PantryTableModel.DAYS_LEFT_COLUMN));
        assertEquals("", model.getValueAt(1, 4));
        assertEquals("rice", model.getItemAt(1).getName());
    }

    private static String message(Runnable r) {
        return assertThrows(IllegalArgumentException.class, r::run).getMessage();
    }
}
