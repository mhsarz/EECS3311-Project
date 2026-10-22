package com.recipeagent.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckAndNotificationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @Test
    void ingredientCheckTracksMissingItems() {
        IngredientCheck check = new IngredientCheck("r1", List.of(
                new Ingredient("scallions", 2, ""), new Ingredient("rice", 1, "cup")));
        assertFalse(check.allInStock());
        assertEquals(List.of("scallions", "rice"), check.getMissingNames());

        assertTrue(check.markFound("Scallions"));
        assertFalse(check.markFound("chicken"));
        assertEquals(List.of("rice"), check.getMissingNames());

        check.markFound("rice");
        assertTrue(check.allInStock());
    }

    @Test
    void emptyCheckMeansAllInStock() {
        assertTrue(new IngredientCheck("r1", null).allInStock());
    }

    @Test
    void substitutionKeepsNoteAndStock() {
        Substitution s = new Substitution("milk + lemon juice", "1 cup milk, 1 tbsp lemon", false);
        assertFalse(s.isInStock());
        s.setInStock(true);
        assertTrue(s.toString().contains("(in stock)"));
        assertEquals("", new Substitution("yogurt", null, true).getNote());
        assertThrows(IllegalArgumentException.class, () -> new Substitution("", "x", true));
    }

    @Test
    void saleNotificationNamesItemStoreAndPrice() {
        Notification n = Notification.sale(new CartItem("milk", 1),
                new FlyerItem("2% Milk 4L", 5.49), "No Frills");
        assertEquals(NotificationType.SALE, n.getType());
        assertEquals("milk is on sale at No Frills: 2% Milk 4L for $5.49", n.getMessage());
    }

    @Test
    void expiryNotificationDescribesDaysLeft() {
        assertEquals("spinach (fridge) expires in 2 days", expiryMessage(2));
        assertEquals("spinach (fridge) expires tomorrow", expiryMessage(1));
        assertEquals("spinach (fridge) expires today", expiryMessage(0));
        assertEquals("spinach (fridge) expired 1 day ago", expiryMessage(-1));
        assertEquals("spinach (fridge) expired 3 days ago", expiryMessage(-3));
    }

    @Test
    void expiryNotificationNeedsDate() {
        PantryItem salt = new PantryItem("salt", 1, "kg", StorageLocation.PANTRY, null);
        assertThrows(IllegalArgumentException.class, () -> Notification.expiry(salt, TODAY));
    }

    @Test
    void notificationRejectsMissingParts() {
        assertThrows(IllegalArgumentException.class, () -> new Notification(null, "hi"));
        assertThrows(IllegalArgumentException.class,
                () -> new Notification(NotificationType.SALE, " "));
    }

    private String expiryMessage(int daysLeft) {
        PantryItem spinach = new PantryItem("spinach", 1, "bag",
                StorageLocation.FRIDGE, TODAY.plusDays(daysLeft));
        Notification n = Notification.expiry(spinach, TODAY);
        assertEquals(NotificationType.EXPIRY, n.getType());
        return n.getMessage();
    }
}
