package com.recipeagent.model;

/** An item on the shopping list (F02). {@code onSale} is set by the flyer sale check. */
public class CartItem {

    private String name;
    private double quantity;
    private boolean onSale;

    public CartItem(String name, double quantity) {
        this.name = Validation.requireText(name, "Item name");
        this.quantity = requirePositive(quantity);
        this.onSale = false;
    }

    public String getName() {
        return name;
    }

    /** True if this item has the same name as {@code otherName}, ignoring case and outer spaces. */
    public boolean hasName(String otherName) {
        return otherName != null && name.equalsIgnoreCase(otherName.trim());
    }

    public double getQuantity() {
        return quantity;
    }

    /** Sets the quantity; a cart item must have more than zero. */
    public void setQuantity(double quantity) {
        this.quantity = requirePositive(quantity);
    }

    private static double requirePositive(double quantity) {
        Validation.requireNonNegative(quantity, "Quantity");
        if (quantity == 0) {
            throw new IllegalArgumentException("Quantity must be more than zero");
        }
        return quantity;
    }

    /** Adds to the quantity, e.g. when the same item is added to the cart twice. */
    public void addQuantity(double extra) {
        setQuantity(quantity + Validation.requireNonNegative(extra, "Extra quantity"));
    }

    public boolean isOnSale() {
        return onSale;
    }

    public void setOnSale(boolean onSale) {
        this.onSale = onSale;
    }

    @Override
    public String toString() {
        return name + " x" + quantity + (onSale ? " (on sale)" : "");
    }
}
