package com.recipeagent.cart;

import com.recipeagent.model.CartItem;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShoppingCartTest {

    @Test
    void addsItemsInOrder() {
        ShoppingCart cart = new ShoppingCart();
        assertTrue(cart.isEmpty());
        cart.add(new CartItem("milk", 2));
        cart.add(new CartItem("eggs", 12));
        assertEquals(2, cart.size());
        assertEquals("milk", cart.getItems().get(0).getName());
    }

    @Test
    void addingSameNameMergesQuantity() {
        ShoppingCart cart = new ShoppingCart();
        CartItem first = cart.add(new CartItem("Milk", 1));
        CartItem merged = cart.add(new CartItem("milk ", 2));
        assertSame(first, merged);
        assertEquals(1, cart.size());
        assertEquals(3, cart.find("MILK").orElseThrow().getQuantity());
    }

    @Test
    void rejectsNullItem() {
        assertThrows(IllegalArgumentException.class, () -> new ShoppingCart().add(null));
    }

    @Test
    void removesByNameIgnoringCase() {
        ShoppingCart cart = new ShoppingCart();
        cart.add(new CartItem("Bread", 1));
        assertTrue(cart.contains("bread"));
        assertTrue(cart.remove("BREAD"));
        assertFalse(cart.remove("bread"));
        assertFalse(cart.remove(null));
        assertTrue(cart.isEmpty());
    }

    @Test
    void markOnSaleSetsAndClearsFlags() {
        ShoppingCart cart = new ShoppingCart();
        cart.add(new CartItem("milk", 1));
        cart.add(new CartItem("eggs", 1));

        cart.markOnSale(List.of("Milk"));
        assertTrue(cart.find("milk").orElseThrow().isOnSale());
        assertFalse(cart.find("eggs").orElseThrow().isOnSale());

        cart.markOnSale(List.of("eggs"));
        assertFalse(cart.find("milk").orElseThrow().isOnSale());
        assertTrue(cart.find("eggs").orElseThrow().isOnSale());

        cart.markOnSale(null);
        assertFalse(cart.find("eggs").orElseThrow().isOnSale());
    }

    @Test
    void initialItemsMergeDuplicatesAndSkipNulls() {
        ShoppingCart cart = new ShoppingCart(Arrays.asList(
                new CartItem("rice", 1), null, new CartItem("Rice", 2)));
        assertEquals(1, cart.size());
        assertEquals(3, cart.getItems().get(0).getQuantity());
        assertThrows(UnsupportedOperationException.class,
                () -> cart.getItems().add(new CartItem("x", 1)));
    }

    @Test
    void clearEmptiesCart() {
        ShoppingCart cart = new ShoppingCart();
        cart.add(new CartItem("milk", 1));
        cart.clear();
        assertTrue(cart.isEmpty());
    }
}
