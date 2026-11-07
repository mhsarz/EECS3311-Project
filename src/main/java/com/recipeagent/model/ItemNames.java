package com.recipeagent.model;

import java.util.Locale;

/**
 * Compares food names the way people write them: case, extra spaces and a plural last word
 * don't make a different item, so "Eggs" in the cart is the same as "egg", and "tomatoes"
 * the same as "Tomato". Used by the pantry and the cart (F03, F02) and so also when a recipe
 * is checked against the pantry (F11).
 */
public final class ItemNames {

    private ItemNames() {
    }

    /** True if the two names are the same item ("Eggs" and "egg", "green onions" and "Green Onion"). */
    public static boolean same(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return key(a).equals(key(b));
    }

    /** The form names are compared in: lower case, single spaces, last word made singular. */
    public static String key(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        int space = n.lastIndexOf(' ');
        return n.substring(0, space + 1) + singular(n.substring(space + 1));
    }

    /**
     * A simple English singular for food words: berries -> berry, tomatoes -> tomato,
     * peaches -> peach, boxes -> box, eggs -> egg. Short words and words ending in "ss" or
     * "us" are left alone (glass, hummus, asparagus).
     */
    static String singular(String w) {
        if (w.length() <= 3 || w.endsWith("ss") || w.endsWith("us") || w.endsWith("is")) {
            return w;
        }
        if (w.endsWith("ies") && w.length() > 4) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.endsWith("oes") || w.endsWith("ches") || w.endsWith("shes") || w.endsWith("xes")
                || w.endsWith("sses") || w.endsWith("zes")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("s")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }
}
