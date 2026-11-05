package com.recipeagent.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CartAndFlyerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @Test
    void cartItemStartsNotOnSale() {
        CartItem milk = new CartItem(" milk ", 2);
        assertEquals("milk", milk.getName());
        assertEquals(2, milk.getQuantity());
        assertFalse(milk.isOnSale());
        milk.setOnSale(true);
        assertTrue(milk.isOnSale());
    }

    @Test
    void cartItemRejectsEmptyNameAndZeroQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new CartItem("", 1));
        assertThrows(IllegalArgumentException.class, () -> new CartItem("milk", 0));
        assertThrows(IllegalArgumentException.class, () -> new CartItem("milk", -2));
    }

    @Test
    void cartItemAddsQuantityAndMatchesName() {
        CartItem eggs = new CartItem("Eggs", 1);
        eggs.addQuantity(2);
        assertEquals(3, eggs.getQuantity());
        assertTrue(eggs.hasName("eggs "));
        assertThrows(IllegalArgumentException.class, () -> eggs.addQuantity(-1));
    }

    @Test
    void flyerItemFormatsPrice() {
        FlyerItem item = new FlyerItem("2% Milk 4L", 5.5);
        assertEquals("$5.50", item.formattedPrice());
        assertThrows(IllegalArgumentException.class, () -> new FlyerItem("Milk", -1));
    }

    @Test
    void flyerIsCurrentUntilEndDate() {
        Flyer flyer = new Flyer("No Frills", TODAY, List.of(new FlyerItem("Bread", 2.0)));
        assertTrue(flyer.isCurrent(TODAY));
        assertFalse(flyer.isCurrent(TODAY.plusDays(1)));
        assertTrue(new Flyer("Metro", null, null).isCurrent(TODAY));
    }

    @Test
    void flyerItemsAreProtected() {
        Flyer flyer = new Flyer("Metro", TODAY, null);
        assertTrue(flyer.isEmpty());
        flyer.addItem(new FlyerItem("Apples", 3.99));
        assertEquals(1, flyer.getItems().size());
        assertThrows(UnsupportedOperationException.class,
                () -> flyer.getItems().add(new FlyerItem("x", 1)));
        assertThrows(IllegalArgumentException.class, () -> flyer.addItem(null));
        assertThrows(IllegalArgumentException.class, () -> new Flyer(" ", TODAY, null));
    }

    @Test
    void cartItemHasOptionalUnitAndSource() {
        CartItem plain = new CartItem("bread", 1);
        assertEquals("", plain.getUnit());
        assertEquals(CartSource.MANUAL, plain.getSource());
        assertEquals("bread x1.0", plain.toString());
        CartItem milk = new CartItem("milk", 2, " L ", CartSource.CHAT);
        assertEquals("L", milk.getUnit());
        assertEquals(CartSource.CHAT, milk.getSource());
        assertEquals("milk 2.0 L", milk.toString());
        assertEquals(CartSource.MANUAL, new CartItem("x", 1, null, null).getSource());
        assertEquals("Chat", CartSource.CHAT.getLabel());
        assertEquals("Recipe", CartSource.RECIPE.getLabel());
        assertEquals("You", CartSource.MANUAL.getLabel());
    }

    @Test
    void cartItemSavedBeforeUnitsLoadsWithDefaults() {
        CartItem old = new com.google.gson.Gson().fromJson("{\"name\":\"milk\",\"quantity\":2}", CartItem.class);
        assertEquals("", old.getUnit());
        assertEquals(CartSource.MANUAL, old.getSource());
    }

    @Test
    void cartItemRemembersItsSale() {
        CartItem milk = new CartItem("milk", 1);
        milk.setSale("No Frills", new FlyerItem("2% Milk 4L", 5.49));
        assertTrue(milk.isOnSale());
        assertEquals("No Frills", milk.getSaleStore());
        assertEquals("2% Milk 4L", milk.getSaleItemName());
        assertEquals(5.49, milk.getSalePrice().doubleValue());
        milk.clearSale();
        assertFalse(milk.isOnSale());
        assertNull(milk.getSaleStore());
        assertNull(milk.getSalePrice());
        milk.setOnSale(true);                                   // old style flag: no details
        assertNull(milk.getSaleItemName());
        assertThrows(IllegalArgumentException.class, () -> milk.setSale(" ", new FlyerItem("x", 1)));
        assertThrows(IllegalArgumentException.class, () -> milk.setSale("Metro", null));
        SaleMatch m = new SaleMatch("milk", new FlyerItem("2% Milk 4L", 5.49), "Metro");
        assertEquals("milk -> 2% Milk 4L - $5.49 at Metro", m.toString());
    }

    @Test
    void ingredientCheckCanReplaceAMissingAmount() {
        IngredientCheck check = new IngredientCheck("r1", List.of(new Ingredient("scallions", 3, "")));
        check.replaceMissing("Scallions", new Ingredient("scallions", 1, ""));
        assertEquals(1, check.getMissing().get(0).getQuantity());
        assertEquals(1, check.getMissing().size());
    }
}
