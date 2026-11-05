package com.recipeagent.controller;

import com.recipeagent.model.Flyer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of Refresh Flyers or Upload Flyer (F10): the flyers read, a message for each
 * store that failed, whether uploading files would help, and the sale check that followed (F02).
 */
public final class FlyerRefresh {

    private final List<Flyer> updated;
    private final Map<String, String> problems;
    private final boolean uploadSuggested;
    private final SaleCheck sales;

    FlyerRefresh(List<Flyer> updated, Map<String, String> problems, boolean uploadSuggested, SaleCheck sales) {
        this.updated = List.copyOf(updated);
        this.problems = Collections.unmodifiableMap(new LinkedHashMap<>(problems));
        this.uploadSuggested = uploadSuggested;
        this.sales = sales;
    }

    public List<Flyer> getUpdated() {
        return updated;
    }

    /** Store -> what went wrong, for stores whose flyer could not be read. */
    public Map<String, String> getProblems() {
        return problems;
    }

    /** True if a store's online flyer was not available, so uploading the file is the way (UC10 1a). */
    public boolean isUploadSuggested() {
        return uploadSuggested;
    }

    /** The sale check run on the cart after the flyers were read. */
    public SaleCheck getSales() {
        return sales;
    }
}
