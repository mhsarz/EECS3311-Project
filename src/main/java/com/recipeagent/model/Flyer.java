package com.recipeagent.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A store's weekly flyer (F10): the store, the last day of the sale, and the sale items. */
public class Flyer {

    private String store;
    private LocalDate endDate;
    private List<FlyerItem> items;

    /** {@code endDate} may be null if the flyer text did not include one. */
    public Flyer(String store, LocalDate endDate, List<FlyerItem> items) {
        this.store = Validation.requireText(store, "Store");
        this.endDate = endDate;
        this.items = new ArrayList<>(items == null ? List.of() : items);
    }

    public String getStore() {
        return store;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public List<FlyerItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void addItem(FlyerItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Flyer item must not be null");
        }
        items.add(item);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * True if the sale is still running on {@code today}.
     * A flyer without an end date is treated as current.
     */
    public boolean isCurrent(LocalDate today) {
        return endDate == null || !today.isAfter(endDate);
    }

    @Override
    public String toString() {
        return store + " flyer (" + items.size() + " items"
                + (endDate == null ? "" : ", ends " + endDate) + ")";
    }
}
