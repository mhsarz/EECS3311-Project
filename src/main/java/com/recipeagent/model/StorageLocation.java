package com.recipeagent.model;

import java.util.Locale;

/** Where a pantry item is kept (F03). */
public enum StorageLocation {
    PANTRY, FRIDGE, FREEZER;

    /**
     * Parses user input such as "fridge" or " Freezer ".
     *
     * @throws IllegalArgumentException if the text is not PANTRY, FRIDGE or FREEZER
     */
    public static StorageLocation parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Location is required (PANTRY, FRIDGE or FREEZER)");
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid location '" + text + "' (must be PANTRY, FRIDGE or FREEZER)");
        }
    }
}
