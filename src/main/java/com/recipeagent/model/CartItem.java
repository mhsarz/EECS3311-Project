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
    private String saleStore;
    private String saleItem;
    private Double salePrice;

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

    /** Sets or clears the on-sale flag; clearing it also forgets which sale it was. */
    public void setOnSale(boolean onSale) {
        this.onSale = onSale;
        if (!onSale) {
            saleStore = null;
            saleItem = null;
            salePrice = null;
        }
    }

    /** Marks this item as on sale at {@code store} as {@code flyerItem} (F02 sale check). */
    public void setSale(String store, FlyerItem flyerItem) {
        if (flyerItem == null) {
            throw new IllegalArgumentException("Flyer item must not be null");
        }
        this.saleStore = Validation.requireText(store, "Store");
        this.saleItem = flyerItem.getName();
        this.salePrice = flyerItem.getSalePrice();
        this.onSale = true;
    }

    public void clearSale() {
        setOnSale(false);
    }

    /** The store with the sale, or null if not on sale (or found before stores were kept). */
    public String getSaleStore() {
        return onSale ? saleStore : null;
    }

    /** The flyer item it matched, e.g. "2% Milk 4L", or null. */
    public String getSaleItemName() {
        return onSale ? saleItem : null;
    }

    /** The sale price, or null. */
    public Double getSalePrice() {
        return onSale ? salePrice : null;
    }

    @Override
    public String toString() {
        String amount = getUnit().isEmpty() ? " x" + quantity : " " + quantity + " " + getUnit();
        return name + amount + (onSale ? " (on sale)" : "");
    }
}
