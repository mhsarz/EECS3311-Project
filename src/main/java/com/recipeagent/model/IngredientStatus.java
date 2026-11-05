package com.recipeagent.model;

/**
 * Where a recipe ingredient stands compared with the pantry (F01, F11), shown as a chip next
 * to each ingredient in the Recipe tab.
 */
public enum IngredientStatus {
    /** In the pantry, enough of it, not close to its date. */
    IN_PANTRY,
    /** In the pantry and expires within a few days: a good reason to cook this now. */
    USE_SOON,
    /** In the pantry but past its best-before date. */
    EXPIRED,
    /** In the pantry, but less than the recipe needs. */
    NOT_ENOUGH,
    /** Not in the pantry. */
    MISSING,
    /** A basic such as salt or water that is assumed to be at home. */
    BASIC;

    /** True if the user needs to buy it (it is in the missing list of the ingredient check). */
    public boolean needsBuying() {
        return this == MISSING || this == NOT_ENOUGH;
    }
}
