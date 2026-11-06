package com.recipeagent.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What Mark as Cooked (F06) would take out of the pantry, worked out without changing
 * anything, so the GUI and CLI can ask "reduce these ingredients?" first. It uses the same
 * pantry names as Mark as Cooked, including names the agent matched (F11).
 */
public final class CookPreview {

    /**
     * One recipe ingredient.
     *
     * @param ingredient the name in the recipe
     * @param pantryItem the pantry item it uses, or null if it is not in the pantry
     * @param amount     how much the recipe uses (0 = to taste)
     * @param unit       the recipe's unit
     * @param before     the pantry quantity before (0 if not in the pantry)
     * @param after      the pantry quantity after cooking (never below 0)
     * @param pantryUnit the pantry item's unit ("" if not in the pantry)
     */
    public record Line(String ingredient, String pantryItem, double amount, String unit,
                       double before, double after, String pantryUnit) {

        public boolean inPantry() {
            return pantryItem != null;
        }

        /**
         * "chicken breast: 400 g (you have 500 g, 100 g left)",
         * "green onions (for scallions): 1 bunch (you have 1 bunch, none left)",
         * "salt: to taste (nothing taken)",
         * "rice: 1 cup (you have 500 g, 499 g left; the units differ)".
         */
        public String describe() {
            String name = pantryItem.equalsIgnoreCase(ingredient) ? pantryItem
                    : pantryItem + " (for " + ingredient + ")";
            if (amount <= 0) {
                return name + ": to taste (nothing taken)";
            }
            String left = after <= 0 ? "none left" : CookPreview.amount(after, pantryUnit) + " left";
            // There is no unit conversion (see README), so say so when the units differ.
            String units = unitsDiffer() ? "; the units differ" : "";
            return name + ": " + CookPreview.amount(amount, unit) + " (you have "
                    + CookPreview.amount(before, pantryUnit) + ", " + left + units + ")";
        }

        /** True if both units are given and are not the same (ignoring case). */
        public boolean unitsDiffer() {
            return unit != null && pantryUnit != null && !unit.isBlank() && !pantryUnit.isBlank()
                    && !unit.trim().equalsIgnoreCase(pantryUnit.trim());
        }
    }

    private final List<Line> lines;

    public CookPreview(List<Line> lines) {
        this.lines = List.copyOf(lines);
    }

    /** One line per recipe ingredient, in recipe order. */
    public List<Line> getLines() {
        return lines;
    }

    /** The ingredients that are in the pantry, so their amounts are taken out. */
    public List<Line> getUsed() {
        return lines.stream().filter(Line::inPantry).toList();
    }

    /** Ingredient names that are not in the pantry, so nothing is taken for them. */
    public List<String> getSkipped() {
        List<String> names = new ArrayList<>();
        for (Line l : lines) {
            if (!l.inPantry()) {
                names.add(l.ingredient());
            }
        }
        return names;
    }

    /**
     * The question shown before Mark as Cooked, e.g.
     * <pre>
     * You are cooking "Fried Rice". Reduce these ingredients in your pantry?
     *
     *   rice: 1 kg (you have 2 kg, 1 kg left)
     *
     * Not in your pantry, so nothing is taken: soy sauce
     * </pre>
     */
    public String question(String recipeTitle) {
        StringBuilder sb = new StringBuilder("You are cooking \"" + recipeTitle + "\".");
        List<Line> used = getUsed();
        if (used.isEmpty()) {
            sb.append("\nNone of its ingredients are in your pantry, so nothing will be taken out.")
                    .append("\nMark it as cooked anyway?");
        } else {
            sb.append(" Reduce these ingredients in your pantry?\n");
            for (Line l : used) {
                sb.append("\n  ").append(l.describe());
            }
        }
        List<String> skipped = getSkipped();
        if (!skipped.isEmpty() && !used.isEmpty()) {
            sb.append("\n\nNot in your pantry, so nothing is taken: ").append(String.join(", ", skipped));
        }
        return sb.toString();
    }

    /** "400 g", "2", "1.5 L". */
    static String amount(double q, String unit) {
        String n = q == Math.rint(q) && Math.abs(q) < 1e9 ? String.valueOf((long) q)
                : String.format(Locale.ROOT, "%.2f", q).replaceAll("0+$", "").replaceAll("\\.$", "");
        return (n + " " + (unit == null ? "" : unit)).trim();
    }
}
