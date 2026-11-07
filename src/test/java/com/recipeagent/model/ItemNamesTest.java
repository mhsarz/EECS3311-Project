package com.recipeagent.model;

import com.recipeagent.cart.ShoppingCart;
import com.recipeagent.pantry.PantryManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Singular and plural names are the same item in the pantry and the cart (F03, F02, F11). */
class ItemNamesTest {

    @Test
    void pluralsCaseAndSpacesDoNotMakeANewItem() {
        assertTrue(ItemNames.same("Eggs", "egg"));
        assertTrue(ItemNames.same("tomatoes", "Tomato"));
        assertTrue(ItemNames.same("blueberries", "blueberry"));
        assertTrue(ItemNames.same("peaches", "peach"));
        assertTrue(ItemNames.same(" Green   Onions ", "green onion"));
        assertTrue(ItemNames.same("carrots", "Carrots"));
        assertFalse(ItemNames.same("eggs", "egg noodles"));
        assertFalse(ItemNames.same("rice", "rice krispies"));
        assertFalse(ItemNames.same("milk", null));
        assertEquals("hummus", ItemNames.key("Hummus"));                    // not "hummu"
        assertEquals("glass", ItemNames.key("glass"));
        assertEquals("pea", ItemNames.key("peas"));
        assertEquals("box", ItemNames.key("boxes"));
    }

    @Test
    void cartAndPantryMergePlurals() {
        ShoppingCart cart = new ShoppingCart();
        cart.add(new CartItem("egg", 12, "pcs", CartSource.MANUAL));
        cart.add(new CartItem("Eggs", 6));
        assertEquals(1, cart.getItems().size());
        assertEquals(18, cart.getItems().get(0).getQuantity());
        assertTrue(cart.remove("eggs"));

        PantryManager pantry = new PantryManager();
        assertTrue(pantry.addItem(new PantryItem("tomatoes", 3, "", StorageLocation.PANTRY, null)));
        assertFalse(pantry.addItem(new PantryItem("Tomato", 1, "", StorageLocation.PANTRY, null)));
        assertEquals(1, pantry.findByName("tomato").size());
    }

    @Test
    void savedDuplicatesFromBeforeAreKept() {
        PantryManager pantry = new PantryManager(List.of(
                new PantryItem("egg", 6, "pcs", StorageLocation.FRIDGE, null),
                new PantryItem("eggs", 12, "", StorageLocation.FRIDGE, null)));
        assertEquals(2, pantry.getAll().size());                             // nothing is dropped
        ShoppingCart cart = new ShoppingCart(List.of(new CartItem("egg", 12), new CartItem("eggs", 24)));
        assertEquals(1, cart.getItems().size());                             // the cart merges them
        assertEquals(36, cart.getItems().get(0).getQuantity());
    }
}
