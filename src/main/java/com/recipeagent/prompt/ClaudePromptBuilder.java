package com.recipeagent.prompt;

import java.util.Locale;

/**
 * Builder pattern: <b>ConcreteBuilder</b> that writes prompts in the style Claude handles
 * best: each piece of context goes inside its own XML-style tag (for example
 * {@code <pantry> ... </pantry>}), and the reply format asks for JSON only.
 * <p>
 * Context values often contain user or flyer text. A value that contains the closing tag
 * of its own section has that tag removed, so the text cannot "close" the section early
 * and pretend to be instructions.
 */
public class ClaudePromptBuilder extends PromptBuilder {

    @Override
    public void buildTask(String task) {
        current().setTask(task);
    }

    @Override
    public void buildContext(String label, String value) {
        String tag = toTag(label);
        String body = (value == null || value.isBlank()) ? "(none)" : value.trim();
        body = body.replace("</" + tag + ">", "");
        current().addContext("<" + tag + ">\n" + body + "\n</" + tag + ">");
    }

    @Override
    public void buildFormat(String schema) {
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("Format schema must not be empty");
        }
        current().setFormat("Reply with only one JSON object that matches this format. "
                + "Do not add any text before or after the JSON.\n"
                + "<format>\n" + schema.trim() + "\n</format>");
    }

    /** Turns a label such as "Top-rated recipes" into a tag such as "top_rated_recipes". */
    static String toTag(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Context label must not be empty");
        }
        String tag = label.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        tag = tag.replaceAll("^_+|_+$", "");
        if (tag.isEmpty()) {
            throw new IllegalArgumentException("Context label must contain a letter or digit");
        }
        return tag;
    }
}
