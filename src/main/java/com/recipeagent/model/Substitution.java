package com.recipeagent.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One suggested replacement for a missing ingredient (F07). */
public class Substitution {

    private String substitute;
    private String note;
    private boolean inStock;
    private List<String> pantryItems;

    public Substitution(String substitute, String note, boolean inStock) {
        this(substitute, note, inStock, List.of());
    }

    /**
     * @param pantryItems names of the pantry items the substitute is made from, e.g. "milk"
     *                    and "lemon" for "milk with lemon juice" (may be empty)
     */
    public Substitution(String substitute, String note, boolean inStock, List<String> pantryItems) {
        this.substitute = Validation.requireText(substitute, "Substitute");
        this.note = Validation.textOrEmpty(note);
        this.inStock = inStock;
        this.pantryItems = new ArrayList<>(pantryItems == null ? List.of() : pantryItems);
    }

    /** The pantry items this substitute is made from (empty if it is something to buy). */
    public List<String> getPantryItems() {
        return pantryItems == null ? List.of() : Collections.unmodifiableList(pantryItems);
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
