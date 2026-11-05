package com.recipeagent.model;

/**
 * An item on the shopping list (F02), with an optional unit and where it was added from.
 * {@code onSale} is set by the flyer sale check.
 */
public class CartItem {

    private String name;
    private double quantity;
    private String unit;
    private CartSource source;
    private boolean onSale;

    /** An item typed in by the user, with no unit. */
    public CartItem(String name, double quantity) {
        this(name, quantity, "", CartSource.MANUAL);
    }

    /** @param source where it came from (null means typed in by the user) */
    public CartItem(String name, double quantity, String unit, CartSource source) {
        this.name = Validation.requireText(name, "Item name");
        this.quantity = requirePositive(quantity);
        this.unit = Validation.textOrEmpty(unit);
        this.source = source == null ? CartSource.MANUAL : source;
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

    /** The unit, e.g. "L" or "tbsp"; empty if none. */
    public String getUnit() {
        return unit == null ? "" : unit;
    }

    public void setUnit(String unit) {
        this.unit = Validation.textOrEmpty(unit);
    }

    /** Where the item was added from; older saved carts have none, which means the user. */
    public CartSource getSource() {
        return source == null ? CartSource.MANUAL : source;
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
        String amount = getUnit().isEmpty() ? " x" + quantity : " " + quantity + " " + getUnit();
        return name + amount + (onSale ? " (on sale)" : "");
    }
}
