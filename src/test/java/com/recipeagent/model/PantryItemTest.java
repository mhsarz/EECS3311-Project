package com.recipeagent.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PantryItemTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Test
    void createsItemWithGeneratedId() {
        PantryItem item = new PantryItem(" rice ", 2, "kg", StorageLocation.PANTRY, null);
        assertNotNull(item.getId());
        assertEquals("rice", item.getName());
        assertEquals(2, item.getQuantity());
        assertFalse(item.hasBestBefore());
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new PantryItem("", 1, "kg", StorageLocation.PANTRY, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PantryItem("rice", -1, "kg", StorageLocation.PANTRY, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PantryItem("rice", 1, "kg", null, null));
        PantryItem item = new PantryItem("rice", 1, "kg", StorageLocation.PANTRY, null);
        assertThrows(IllegalArgumentException.class, () -> item.setQuantity(Double.NaN));
    }

    @Test
    void parsesLocationIgnoringCase() {
        assertEquals(StorageLocation.FRIDGE, StorageLocation.parse(" fridge "));
        assertThrows(IllegalArgumentException.class, () -> StorageLocation.parse("garage"));
        assertThrows(IllegalArgumentException.class, () -> StorageLocation.parse(""));
    }

    @Test
    void computesDaysUntilExpiry() {
        PantryItem milk = new PantryItem("milk", 1, "L", StorageLocation.FRIDGE, TODAY.plusDays(3));
        PantryItem old = new PantryItem("bread", 1, "", StorageLocation.PANTRY, TODAY.minusDays(1));
        PantryItem salt = new PantryItem("salt", 1, "kg", StorageLocation.PANTRY, null);

        assertEquals(3, milk.daysUntilExpiry(TODAY));
        assertEquals(-1, old.daysUntilExpiry(TODAY));
        assertEquals(Integer.MAX_VALUE, salt.daysUntilExpiry(TODAY));

        assertTrue(milk.isExpiringWithin(3, TODAY));
        assertFalse(milk.isExpiringWithin(2, TODAY));
        assertTrue(old.isExpiringWithin(3, TODAY));
        assertFalse(salt.isExpiringWithin(3, TODAY));
    }

    @Test
    void matchesNameIgnoringCase() {
        PantryItem item = new PantryItem("Green Onions", 1, "", StorageLocation.FRIDGE, null);
        assertTrue(item.hasName("green onions "));
        assertFalse(item.hasName("onions"));
        assertFalse(item.hasName(null));
    }
}
