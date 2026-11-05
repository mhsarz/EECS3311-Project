package com.recipeagent.model;

/**
 * A shopping-list item that is on sale this week (F02): the cart item's name, the flyer
 * item it matched (e.g. "milk" matched "2% Milk 4L") and the store.
 */
public final class SaleMatch {

    private final String cartItem;
    private final FlyerItem flyerItem;
    private final String store;

    public SaleMatch(String cartItem, FlyerItem flyerItem, String store) {
        this.cartItem = Validation.requireText(cartItem, "Cart item");
        if (flyerItem == null) {
            throw new IllegalArgumentException("Flyer item must not be null");
        }
        this.flyerItem = flyerItem;
        this.store = Validation.requireText(store, "Store");
    }

    public String getCartItem() {
        return cartItem;
    }

    public FlyerItem getFlyerItem() {
        return flyerItem;
    }

    public String getStore() {
        return store;
    }

    @Override
    public String toString() {
        return cartItem + " -> " + flyerItem + " at " + store;
    }
}
