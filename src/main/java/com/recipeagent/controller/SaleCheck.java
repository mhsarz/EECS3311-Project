package com.recipeagent.controller;

import com.recipeagent.model.SaleMatch;

import java.util.List;

/**
 * The result of checking cart items against this week's flyers (F02): which items were
 * checked, which are on sale, and, if the check could not run, why (no flyers, AI unavailable).
 */
public final class SaleCheck {

    private final List<String> checked;
    private final List<SaleMatch> matches;
    private final String skippedReason;

    /** @param skippedReason why the check did not run, or null if it ran */
    public SaleCheck(List<String> checked, List<SaleMatch> matches, String skippedReason) {
        this.checked = List.copyOf(checked);
        this.matches = List.copyOf(matches);
        this.skippedReason = skippedReason;
    }

    static SaleCheck skipped(List<String> checked, String reason) {
        return new SaleCheck(checked, List.of(), reason);
    }

    /** The cart item names that were checked. */
    public List<String> getChecked() {
        return checked;
    }

    /** The items found on sale (empty if none, or if the check was skipped). */
    public List<SaleMatch> getMatches() {
        return matches;
    }

    /** True if the check ran (it is skipped when there are no flyers or no AI). */
    public boolean wasRun() {
        return skippedReason == null;
    }

    /** Why the check was skipped, or null if it ran. */
    public String getSkippedReason() {
        return skippedReason;
    }
}
