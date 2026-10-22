package com.recipeagent.model;

import java.util.Locale;

/** One sale item extracted from a store flyer (F10), e.g. "2% Milk 4L" at $5.49. */
public class FlyerItem {

    private String name;
    private double salePrice;

    public FlyerItem(String name, double salePrice) {
        this.name = Validation.requireText(name, "Flyer item name");
        this.salePrice = Validation.requireNonNegative(salePrice, "Sale price");
    }

    public String getName() {
        return name;
    }

    public double getSalePrice() {
        return salePrice;
    }

    /** Price formatted for display, e.g. "$5.49". */
    public String formattedPrice() {
        return String.format(Locale.CANADA, "$%.2f", salePrice);
    }

    @Override
    public String toString() {
        return name + " - " + formattedPrice();
    }
}
