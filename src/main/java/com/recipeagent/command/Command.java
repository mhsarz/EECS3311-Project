package com.recipeagent.command;

/** An action that can be undone (F06). Run through {@link CommandHistory}. */
public interface Command {

    /** Performs the action. */
    void execute();

    /** Reverses everything {@link #execute()} changed. */
    void undo();

    /** Short text for messages such as "Undid: Cooked Egg Fried Rice". */
    default String getDescription() {
        return getClass().getSimpleName();
    }
}
