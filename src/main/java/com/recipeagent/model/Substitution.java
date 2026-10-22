package com.recipeagent.model;

/** One suggested replacement for a missing ingredient (F07). */
public class Substitution {

    private String substitute;
    private String note;
    private boolean inStock;

    public Substitution(String substitute, String note, boolean inStock) {
        this.substitute = Validation.requireText(substitute, "Substitute");
        this.note = Validation.textOrEmpty(note);
        this.inStock = inStock;
    }

    public String getSubstitute() {
        return substitute;
    }

    /** Short explanation from the agent, e.g. "1 cup milk + 1 tbsp lemon juice". */
    public String getNote() {
        return note;
    }

    /** True if the substitute is already in the user's pantry. */
    public boolean isInStock() {
        return inStock;
    }

    /** Sets the in-stock flag after checking the pantry, instead of trusting the LLM. */
    public void setInStock(boolean inStock) {
        this.inStock = inStock;
    }

    @Override
    public String toString() {
        String s = substitute + (inStock ? " (in stock)" : "");
        return note.isEmpty() ? s : s + ": " + note;
    }
}
