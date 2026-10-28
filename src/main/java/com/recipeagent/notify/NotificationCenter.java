package com.recipeagent.notify;

import com.recipeagent.model.Notification;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Singleton hub for alerts (F02 sale alerts, F05 expiry alerts).
 * <p>
 * AgentController publishes to it, and MainWindow and CLIApp subscribe to it. They must
 * all use the same instance, otherwise alerts would be published to an object nobody
 * listens to and silently disappear. The private constructor makes it impossible to
 * create a second instance, and {@link #getInstance()} gives every layer the same one.
 * <p>
 * getInstance() is synchronized because the GUI thread and the daily expiry check can
 * call it at the same time. The listener list is a CopyOnWriteArrayList, so publishing
 * while another thread subscribes is safe.
 */
public final class NotificationCenter {

    private static NotificationCenter instance;

    private final List<NotificationListener> listeners = new CopyOnWriteArrayList<>();

    private NotificationCenter() {
    }

    /** Returns the one shared instance, creating it on first use. */
    public static synchronized NotificationCenter getInstance() {
        if (instance == null) {
            instance = new NotificationCenter();
        }
        return instance;
    }

    /** Adds a listener. Subscribing the same listener twice has no effect. */
    public void subscribe(NotificationListener l) {
        if (l == null) {
            throw new IllegalArgumentException("Listener must not be null");
        }
        if (!listeners.contains(l)) {
            listeners.add(l);
        }
    }

    /** Removes a listener. Returns true if it was subscribed. */
    public boolean unsubscribe(NotificationListener l) {
        return listeners.remove(l);
    }

    /**
     * Sends the notification to every listener. If one listener throws, the error is
     * reported and the remaining listeners still receive the notification.
     *
     * @return how many listeners received it without error
     */
    public int publish(Notification n) {
        if (n == null) {
            throw new IllegalArgumentException("Notification must not be null");
        }
        int delivered = 0;
        for (NotificationListener l : listeners) {
            try {
                l.onNotify(n);
                delivered++;
            } catch (RuntimeException e) {
                System.err.println("Notification listener failed: " + e.getMessage());
            }
        }
        return delivered;
    }

    public int getListenerCount() {
        return listeners.size();
    }
}
