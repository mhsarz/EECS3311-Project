package com.recipeagent.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** A food item the user has at home (F03), with an optional best-before date (F05). */
public class PantryItem {

    private String id;
    private String name;
    private double quantity;
    private String unit;
    private StorageLocation location;
    private LocalDate bestBefore;

    /** Creates a new item with a generated id. {@code bestBefore} may be null. */
    public PantryItem(String name, double quantity, String unit,
                      StorageLocation location, LocalDate bestBefore) {
        this(UUID.randomUUID().toString(), name, quantity, unit, location, bestBefore);
    }

    /** Creates an item with a known id, e.g. when loading from storage. */
    public PantryItem(String id, String name, double quantity, String unit,
                      StorageLocation location, LocalDate bestBefore) {
        this.id = Validation.requireText(id, "Item id");
        this.name = Validation.requireText(name, "Item name");
        this.quantity = Validation.requireNonNegative(quantity, "Quantity");
        this.unit = Validation.textOrEmpty(unit);
        if (location == null) {
            throw new IllegalArgumentException("Location is required (PANTRY, FRIDGE or FREEZER)");
        }
        this.location = location;
        this.bestBefore = bestBefore;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getQuantity() {
        return quantity;
    }

    /** Sets the quantity; used by merge (F03), deduct and restore (F06). */
    public void setQuantity(double quantity) {
        this.quantity = Validation.requireNonNegative(quantity, "Quantity");
    }

    public String getUnit() {
        return unit;
    }

    public StorageLocation getLocation() {
        return location;
    }

    public LocalDate getBestBefore() {
        return bestBefore;
    }

    public boolean hasBestBefore() {
        return bestBefore != null;
    }

    /** True if this item has the same name as {@code otherName}, ignoring case and spaces. */
    public boolean hasName(String otherName) {
        return otherName != null && name.equalsIgnoreCase(otherName.trim());
    }

    /** Days from today until the best-before date (negative if already past). */
    public int daysUntilExpiry() {
        return daysUntilExpiry(LocalDate.now());
    }

    /**
     * Days from {@code today} until the best-before date (negative if already past).
     * Items without a date return {@link Integer#MAX_VALUE}, so they never count as expiring.
     */
    public int daysUntilExpiry(LocalDate today) {
        if (bestBefore == null) {
            return Integer.MAX_VALUE;
        }
        return (int) ChronoUnit.DAYS.between(today, bestBefore);
    }

    /** True if the item has a date and expires within {@code days} days of {@code today}. */
    public boolean isExpiringWithin(int days, LocalDate today) {
        return bestBefore != null && daysUntilExpiry(today) <= days;
    }

    @Override
    public String toString() {
        String s = name + " - " + (quantity + " " + unit).trim() + " (" + location + ")";
        return bestBefore == null ? s : s + ", best before " + bestBefore;
    }
}
