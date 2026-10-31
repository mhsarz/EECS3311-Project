package com.recipeagent.agent;

/**
 * F01 was asked for a recipe that only uses pantry ingredients, but the pantry is empty.
 * The interface should ask the user to add items first, or to allow missing ingredients.
 */
public class PantryEmptyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PantryEmptyException() {
        super("The pantry is empty. Add some items first, or allow recipes with ingredients you don't have.");
    }
}
