package com.recipeagent.gui;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Turns text typed into the GUI forms into values, with messages a user can understand.
 * Kept separate from the Swing code so it can be tested without a screen.
 */
public final class InputParser {

    private InputParser() {
    }

    /** A required name, trimmed. */
    public static String name(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return text.trim();
    }

    /** A quantity above zero, e.g. "2" or "1.5" (a comma is also accepted as decimal point). */
    public static double quantity(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Quantity is required.");
        }
        double q;
        try {
            q = Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Quantity must be a number, like 2 or 1.5.");
        }
        if (Double.isNaN(q) || Double.isInfinite(q) || q <= 0) {
            throw new IllegalArgumentException("Quantity must be more than zero.");
        }
        return q;
    }

    /** An optional date in YYYY-MM-DD form; blank means no date (returns null). */
    public static LocalDate optionalDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Best-before date must look like 2026-11-20 (year-month-day).");
        }
    }

    /** A whole number within [min, max], e.g. minutes or portions. */
    public static int wholeNumber(String text, String field, int min, int max) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        int n;
        try {
            n = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a whole number.");
        }
        if (n < min || n > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max + ".");
        }
        return n;
    }

    /** Formats a quantity without a useless ".0" (2.0 -> "2", 1.5 -> "1.5"). */
    public static String formatQuantity(double q) {
        if (q == Math.rint(q) && Math.abs(q) < 1e9) {
            return String.valueOf((long) q);
        }
        return String.valueOf(q);
    }
}
