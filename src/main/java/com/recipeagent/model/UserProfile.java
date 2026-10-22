package com.recipeagent.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The user's food preferences (F04). A new profile has no restrictions and
 * 2 default portions, so recipes still work before the user sets one up.
 */
public class UserProfile {

    public static final String NO_DIET = "none";
    public static final int DEFAULT_PORTIONS = 2;

    private String diet;
    private List<String> allergies;
    private List<String> dislikes;
    private int defaultPortions;

    public UserProfile() {
        this.diet = NO_DIET;
        this.allergies = new ArrayList<>();
        this.dislikes = new ArrayList<>();
        this.defaultPortions = DEFAULT_PORTIONS;
    }

    public String getDiet() {
        return diet;
    }

    /** Sets the diet, e.g. "vegetarian". Empty or null means no diet. */
    public void setDiet(String diet) {
        this.diet = (diet == null || diet.isBlank()) ? NO_DIET : diet.trim().toLowerCase(Locale.ROOT);
    }

    public List<String> getAllergies() {
        return Collections.unmodifiableList(allergies);
    }

    public void setAllergies(List<String> allergies) {
        this.allergies = cleanList(allergies);
    }

    public void addAllergy(String allergy) {
        addUnique(allergies, allergy);
    }

    public List<String> getDislikes() {
        return Collections.unmodifiableList(dislikes);
    }

    public void setDislikes(List<String> dislikes) {
        this.dislikes = cleanList(dislikes);
    }

    public void addDislike(String dislike) {
        addUnique(dislikes, dislike);
    }

    public int getDefaultPortions() {
        return defaultPortions;
    }

    public void setDefaultPortions(int portions) {
        this.defaultPortions = Validation.requirePositive(portions, "Default portions");
    }

    /** True if the user listed {@code ingredient} as an allergy, ignoring case. */
    public boolean isAllergicTo(String ingredient) {
        if (ingredient == null) {
            return false;
        }
        String i = ingredient.trim().toLowerCase(Locale.ROOT);
        return allergies.contains(i);
    }

    private static List<String> cleanList(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            for (String v : values) {
                addUnique(result, v);
            }
        }
        return result;
    }

    private static void addUnique(List<String> list, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (!list.contains(v)) {
            list.add(v);
        }
    }

    @Override
    public String toString() {
        return "diet=" + diet + ", allergies=" + allergies + ", dislikes=" + dislikes
                + ", defaultPortions=" + defaultPortions;
    }
}
