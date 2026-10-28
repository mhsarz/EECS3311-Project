package com.recipeagent.command;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Runs commands and keeps them so the latest can be undone (F06).
 * Only the most recent {@code maxSize} commands are kept.
 */
public class CommandHistory {

    public static final int DEFAULT_MAX_SIZE = 20;

    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final int maxSize;

    public CommandHistory() {
        this(DEFAULT_MAX_SIZE);
    }

    public CommandHistory(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be more than zero");
        }
        this.maxSize = maxSize;
    }

    /**
     * Executes the command and remembers it for undo. If execute() throws, the
     * command is not remembered and the exception is passed on.
     */
    public void run(Command c) {
        if (c == null) {
            throw new IllegalArgumentException("Command must not be null");
        }
        c.execute();
        undoStack.push(c);
        if (undoStack.size() > maxSize) {
            undoStack.removeLast();
        }
    }

    /**
     * Undoes the most recent command.
     *
     * @return the command that was undone, or empty if there is nothing to undo
     */
    public Optional<Command> undo() {
        Command c = undoStack.poll();
        if (c == null) {
            return Optional.empty();
        }
        c.undo();
        return Optional.of(c);
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /** Description of the command that undo() would reverse, if any. */
    public Optional<String> peekDescription() {
        Command c = undoStack.peek();
        return c == null ? Optional.empty() : Optional.of(c.getDescription());
    }

    public int size() {
        return undoStack.size();
    }

    public void clear() {
        undoStack.clear();
    }
}
