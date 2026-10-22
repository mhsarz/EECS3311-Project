package com.recipeagent.model;

import java.util.Objects;

/** One ingredient line of a recipe, e.g. 2 cup rice. */
public class Ingredient {

    private String name;
    private double quantity;
    private String unit;

    public Ingredient(String name, double quantity, String unit) {
        this.name = Validation.requireText(name, "Ingredient name");
        this.quantity = Validation.requireNonNegative(quantity, "Ingredient quantity");
        this.unit = Validation.textOrEmpty(unit);
    }

    public String getName() {
        return name;
    }

    public double getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ingredient)) {
            return false;
        }
        Ingredient other = (Ingredient) o;
        return Double.compare(quantity, other.quantity) == 0
                && name.equalsIgnoreCase(other.name)
                && unit.equalsIgnoreCase(other.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name.toLowerCase(), quantity, unit.toLowerCase());
    }

    @Override
    public String toString() {
        return (quantity + " " + unit).trim() + " " + name;
    }
}
