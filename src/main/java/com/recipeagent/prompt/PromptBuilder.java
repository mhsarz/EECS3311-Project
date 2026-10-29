package com.recipeagent.prompt;

/**
 * Builds a {@link Prompt} step by step.
 * <p>
 * Builder pattern: this is the abstract <b>Builder</b>. It declares the building steps
 * (task, context, format) and holds the product being built. RecipeAgent is the
 * <b>Director</b>: for each of its prompts (recipe, substitutes, sales, flyer, name
 * matching, chat) it calls only the steps it needs, through this abstract type.
 * A <b>ConcreteBuilder</b> decides how each part is written, so a different model's
 * prompt style is a new subclass with no change to RecipeAgent.
 * <p>
 * Usage: {@code createNewPrompt(); buildTask(..); buildContext(..)...; buildFormat(..); getPrompt()}.
 */
public abstract class PromptBuilder {

    /** The prompt being built; null until {@link #createNewPrompt()} is called. */
    protected Prompt prompt;

    /** Starts a new, empty prompt (any previous one is discarded). */
    public void createNewPrompt() {
        prompt = new Prompt();
    }

    /**
     * Returns the prompt built so far.
     *
     * @throws IllegalStateException if createNewPrompt() was not called
     */
    public Prompt getPrompt() {
        return current();
    }

    /** Sets what the LLM should do, e.g. "Create one recipe ...". */
    public abstract void buildTask(String task);

    /**
     * Adds one piece of context, e.g. label "pantry" with the list of items.
     * An empty value is written as "(none)", so the LLM can tell it was checked.
     */
    public abstract void buildContext(String label, String value);

    /** Sets the required reply format, e.g. a JSON schema. */
    public abstract void buildFormat(String schema);

    /** The prompt being built, for use by subclasses. */
    protected Prompt current() {
        if (prompt == null) {
            throw new IllegalStateException("Call createNewPrompt() before building a prompt");
        }
        return prompt;
    }
}
