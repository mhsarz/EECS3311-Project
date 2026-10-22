package com.recipeagent.model;

import java.time.LocalDate;
import java.util.Locale;

/** An alert shown in the GUI and printed in the CLI (F02 sale alerts, F05 expiry alerts). */
public class Notification {

    private final NotificationType type;
    private final String message;

    public Notification(NotificationType type, String message) {
        if (type == null) {
            throw new IllegalArgumentException("Notification type must not be null");
        }
        this.type = type;
        this.message = Validation.requireText(message, "Notification message");
    }

    /** Sale alert for a cart item that matched a flyer item (F02). */
    public static Notification sale(CartItem cartItem, FlyerItem flyerItem, String store) {
        String msg = cartItem.getName() + " is on sale at " + Validation.requireText(store, "Store")
                + ": " + flyerItem.getName() + " for " + flyerItem.formattedPrice();
        return new Notification(NotificationType.SALE, msg);
    }

    /** Expiry alert for a pantry item close to its best-before date (F05). */
    public static Notification expiry(PantryItem item, LocalDate today) {
        if (!item.hasBestBefore()) {
            throw new IllegalArgumentException(item.getName() + " has no best-before date");
        }
        int days = item.daysUntilExpiry(today);
        String when;
        if (days < 0) {
            when = "expired " + (-days) + (days == -1 ? " day ago" : " days ago");
        } else if (days == 0) {
            when = "expires today";
        } else if (days == 1) {
            when = "expires tomorrow";
        } else {
            when = "expires in " + days + " days";
        }
        return new Notification(NotificationType.EXPIRY,
                item.getName() + " (" + item.getLocation().name().toLowerCase(Locale.ROOT) + ") " + when);
    }

    public NotificationType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "[" + type + "] " + message;
    }
}
