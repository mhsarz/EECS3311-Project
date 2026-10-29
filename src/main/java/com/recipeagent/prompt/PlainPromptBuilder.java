package com.recipeagent.prompt;

/**
 * Builder pattern: a second <b>ConcreteBuilder</b> that writes the same parts as simple
 * "Label: value" lines, for models that do not use XML-style tags. It shows that the
 * Director's steps stay the same while the representation changes.
 */
public class PlainPromptBuilder extends PromptBuilder {

    @Override
    public void buildTask(String task) {
        current().setTask(task);
    }

    @Override
    public void buildContext(String label, String value) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Context label must not be empty");
        }
        String body = (value == null || value.isBlank()) ? "(none)" : value.trim();
        current().addContext(label.trim() + ":\n" + body);
    }

    @Override
    public void buildFormat(String schema) {
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("Format schema must not be empty");
        }
        current().setFormat("Answer format (JSON only):\n" + schema.trim());
    }
}
