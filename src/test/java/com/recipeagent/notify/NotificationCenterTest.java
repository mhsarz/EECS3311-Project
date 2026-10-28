package com.recipeagent.notify;

import com.recipeagent.model.Notification;
import com.recipeagent.model.NotificationType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NotificationCenterTest {

    private static final Notification SALE =
            new Notification(NotificationType.SALE, "milk is on sale");

    @Test
    void getInstanceAlwaysReturnsSameObject() {
        assertSame(NotificationCenter.getInstance(), NotificationCenter.getInstance());
    }

    @Test
    void constructorIsPrivate() {
        Constructor<?>[] ctors = NotificationCenter.class.getDeclaredConstructors();
        assertEquals(1, ctors.length);
        assertTrue(Modifier.isPrivate(ctors[0].getModifiers()));
    }

    @Test
    void everySubscriberReceivesPublishedNotification() {
        NotificationCenter nc = NotificationCenter.getInstance();
        List<String> gui = new ArrayList<>();
        List<String> cli = new ArrayList<>();
        NotificationListener g = n -> gui.add(n.getMessage());
        NotificationListener c = n -> cli.add(n.getMessage());
        nc.subscribe(g);
        nc.subscribe(c);
        try {
            // A listener that got the hub from a separate getInstance() call still receives it.
            NotificationCenter.getInstance().publish(SALE);
            assertEquals(List.of("milk is on sale"), gui);
            assertEquals(List.of("milk is on sale"), cli);
        } finally {
            nc.unsubscribe(g);
            nc.unsubscribe(c);
        }
    }

    @Test
    void subscribingTwiceDeliversOnce() {
        NotificationCenter nc = NotificationCenter.getInstance();
        List<String> got = new ArrayList<>();
        NotificationListener l = n -> got.add(n.getMessage());
        nc.subscribe(l);
        nc.subscribe(l);
        try {
            nc.publish(SALE);
            assertEquals(1, got.size());
        } finally {
            nc.unsubscribe(l);
        }
        assertFalse(nc.unsubscribe(l));
    }

    @Test
    void failingListenerDoesNotStopOthers() {
        NotificationCenter nc = NotificationCenter.getInstance();
        List<String> got = new ArrayList<>();
        NotificationListener bad = n -> {
            throw new IllegalStateException("broken window");
        };
        NotificationListener good = n -> got.add(n.getMessage());
        nc.subscribe(bad);
        nc.subscribe(good);
        try {
            int before = nc.getListenerCount();
            assertEquals(before - 1, nc.publish(SALE));
            assertEquals(1, got.size());
        } finally {
            nc.unsubscribe(bad);
            nc.unsubscribe(good);
        }
    }

    @Test
    void rejectsNulls() {
        NotificationCenter nc = NotificationCenter.getInstance();
        assertThrows(IllegalArgumentException.class, () -> nc.subscribe(null));
        assertThrows(IllegalArgumentException.class, () -> nc.publish(null));
    }

    @Test
    void getInstanceIsSafeFromManyThreads() throws InterruptedException {
        Set<NotificationCenter> seen = Collections.newSetFromMap(new ConcurrentHashMap<>());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 50; i++) {
            pool.submit(() -> {
                start.await();
                seen.add(NotificationCenter.getInstance());
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(1, seen.size());
    }
}
