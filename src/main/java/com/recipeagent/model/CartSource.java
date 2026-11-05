package com.recipeagent.model;

/** Where a cart item came from (F02), shown in the Cart tab, e.g. "added from Chat". */
public enum CartSource {
    /** Typed in the Cart tab (or the CLI). */
    MANUAL("You"),
    /** Added from a recipe's missing ingredients (F11). */
    RECIPE("Recipe"),
    /** Added by the agent in the chat (F09). */
    CHAT("Chat");

    private final String label;

    CartSource(String label) {
        this.label = label;
    }

    /** "You", "Recipe" or "Chat". */
    public String getLabel() {
        return label;
    }
}
