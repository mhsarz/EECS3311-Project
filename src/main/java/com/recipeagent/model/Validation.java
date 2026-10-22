package com.recipeagent.model;

/** Small shared checks used by the model classes. */
final class Validation {

    private Validation() {
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return value.trim();
    }

    static double requireNonNegative(double value, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            throw new IllegalArgumentException(field + " must be zero or more, got " + value);
        }
        return value;
    }

    static int requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be more than zero, got " + value);
        }
        return value;
    }

    static String textOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
