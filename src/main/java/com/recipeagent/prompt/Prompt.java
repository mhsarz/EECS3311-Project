package com.recipeagent.prompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A finished prompt: the task, any number of context lines, and an optional reply format.
 * <p>
 * Builder pattern: this is the <b>Product</b>. A PromptBuilder fills it in step by step;
 * the builder decides how each part is written, and {@link #toText()} joins them in a
 * fixed order (task, context, format). A prompt without a task is refused, so a forgotten
 * step is caught here instead of showing up later as a bad LLM reply.
 */
public class Prompt {

    private String task;
    private final List<String> context = new ArrayList<>();
    private String format;

    public void setTask(String t) {
        if (t == null || t.isBlank()) {
            throw new IllegalArgumentException("Task must not be empty");
        }
        this.task = t.trim();
    }

    public void addContext(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Context line must not be empty");
        }
        context.add(line.trim());
    }

    public void setFormat(String f) {
        if (f == null || f.isBlank()) {
            throw new IllegalArgumentException("Format must not be empty");
        }
        this.format = f.trim();
    }

    public String getTask() {
        return task;
    }

    public boolean hasTask() {
        return task != null;
    }

    public List<String> getContext() {
        return Collections.unmodifiableList(context);
    }

    public String getFormat() {
        return format;
    }

    public boolean hasFormat() {
        return format != null;
    }

    /**
     * The text sent to the LLM.
     *
     * @throws IllegalStateException if no task was set
     */
    public String toText() {
        if (task == null) {
            throw new IllegalStateException("Prompt has no task: call buildTask() before toText()");
        }
        StringBuilder sb = new StringBuilder(task);
        for (String line : context) {
            sb.append("\n\n").append(line);
        }
        if (format != null) {
            sb.append("\n\n").append(format);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return hasTask() ? toText() : "(prompt without task)";
    }
}
