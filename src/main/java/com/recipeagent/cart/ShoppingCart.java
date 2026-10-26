package com.recipeagent.cart;

import com.recipeagent.model.CartItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The user's shopping list (F02). Item names are unique (ignoring case): adding an
 * item that is already in the cart increases its quantity instead of adding a second line.
 * The sale check (matching cart items to flyers) is done by the agent; this class only
 * stores the result through {@link #markOnSale}.
 */
public class ShoppingCart {

    private final List<CartItem> items;

    public ShoppingCart() {
        this(null);
    }

    /** Starts with previously saved items (may be null). Duplicate names are merged. */
    public ShoppingCart(List<CartItem> initialItems) {
        this.items = new ArrayList<>();
        if (initialItems != null) {
            for (CartItem item : initialItems) {
                if (item != null) {
                    addOrMerge(item);
                }
            }
        }
    }

    /**
     * Adds an item, or adds its quantity to the existing item with the same name.
     *
     * @return the item now in the cart (the existing one if it was merged)
     */
    public CartItem add(CartItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Cart item must not be null");
        }
        return addOrMerge(item);
    }

    private CartItem addOrMerge(CartItem item) {
        for (CartItem existing : items) {
            if (existing.hasName(item.getName())) {
                existing.addQuantity(item.getQuantity());
                return existing;
            }
        }
        items.add(item);
        return item;
    }

    /** Removes the item with this name, ignoring case. Returns true if it was in the cart. */
    public boolean remove(String name) {
        return name != null && items.removeIf(i -> i.hasName(name));
    }

    public Optional<CartItem> find(String name) {
        for (CartItem i : items) {
            if (i.hasName(name)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    public boolean contains(String name) {
        return find(name).isPresent();
    }

    /** All items, read-only, in the order they were added. */
    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }

    /**
     * Sets the on-sale flag from the latest sale check: items named in {@code namesOnSale}
     * are marked on sale and every other item is cleared.
     */
    public void markOnSale(Collection<String> namesOnSale) {
        for (CartItem item : items) {
            boolean onSale = false;
            if (namesOnSale != null) {
                for (String n : namesOnSale) {
                    if (item.hasName(n)) {
                        onSale = true;
                        break;
                    }
                }
            }
            item.setOnSale(onSale);
        }
    }

    /** Removes everything from the cart. */
    public void clear() {
        items.clear();
    }
}
