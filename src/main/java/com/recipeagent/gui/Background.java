package com.recipeagent.gui;

import javax.swing.SwingWorker;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs slow work (AI calls) off the Swing event thread so the window stays responsive,
 * then hands the result or the error back on the event thread.
 */
final class Background {

    private Background() {
    }

    /**
     * @param busy    called with true before the work starts and false when it ends
     * @param task    the slow work
     * @param onDone  receives the result
     * @param onError receives the exception the task threw
     */
    static <T> void run(Consumer<Boolean> busy, Supplier<T> task, Consumer<T> onDone,
                        Consumer<RuntimeException> onError) {
        busy.accept(true);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() {
                return task.get();
            }

            @Override
            protected void done() {
                busy.accept(false);
                try {
                    onDone.accept(get());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    onError.accept(cause instanceof RuntimeException
                            ? (RuntimeException) cause : new RuntimeException(cause));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }
}
