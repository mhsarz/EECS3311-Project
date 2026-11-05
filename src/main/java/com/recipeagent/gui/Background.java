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

    /** A running task that the user can cancel (the Cancel button while a recipe is generated). */
    interface Task {
        /**
         * Stops waiting for the result (call on the Swing event thread): {@code busy(false)} is
         * called right away and neither onDone nor onError will be. The AI call itself is not
         * interrupted (that could leave a file half written); it finishes in the background and
         * its answer is ignored. Does nothing if the task already finished.
         */
        void cancel();

        boolean isCancelled();
    }

    /**
     * @param busy    called with true before the work starts and false when it ends
     * @param task    the slow work
     * @param onDone  receives the result
     * @param onError receives the exception the task threw
     */
    static <T> Task run(Consumer<Boolean> busy, Supplier<T> task, Consumer<T> onDone,
                        Consumer<RuntimeException> onError) {
        busy.accept(true);
        // Our own flag: SwingWorker may call done() only when the task really ends, even after
        // cancel, so cancel() ends the busy state itself and done() then does nothing.
        // Both flags are only touched on the event thread.
        boolean[] cancelled = {false};
        boolean[] finished = {false};
        SwingWorker<T, Void> worker = new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() {
                return task.get();
            }

            @Override
            protected void done() {
                if (cancelled[0]) {
                    return;
                }
                finished[0] = true;
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
        };
        worker.execute();
        return new Task() {
            @Override
            public void cancel() {
                if (cancelled[0] || finished[0]) {
                    return;
                }
                cancelled[0] = true;
                worker.cancel(false);
                busy.accept(false);
            }

            @Override
            public boolean isCancelled() {
                return cancelled[0];
            }
        };
    }
}
