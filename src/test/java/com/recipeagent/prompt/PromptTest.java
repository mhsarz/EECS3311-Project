package com.recipeagent.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptTest {

    @Test
    void toTextJoinsTaskContextAndFormatInOrder() {
        Prompt p = new Prompt();
        p.setFormat("FORMAT");
        p.addContext("CONTEXT 1");
        p.setTask("TASK");
        p.addContext("CONTEXT 2");
        assertEquals("TASK\n\nCONTEXT 1\n\nCONTEXT 2\n\nFORMAT", p.toText());
    }

    @Test
    void formatIsOptional() {
        Prompt p = new Prompt();
        p.setTask("Say hello");
        assertFalse(p.hasFormat());
        assertEquals("Say hello", p.toText());
    }

    @Test
    void refusesPromptWithoutTask() {
        Prompt p = new Prompt();
        p.addContext("pantry: rice");
        assertFalse(p.hasTask());
        IllegalStateException e = assertThrows(IllegalStateException.class, p::toText);
        assertTrue(e.getMessage().contains("no task"));
    }

    @Test
    void rejectsEmptyParts() {
        Prompt p = new Prompt();
        assertThrows(IllegalArgumentException.class, () -> p.setTask(" "));
        assertThrows(IllegalArgumentException.class, () -> p.addContext(null));
        assertThrows(IllegalArgumentException.class, () -> p.setFormat(""));
    }

    @Test
    void contextListIsReadOnly() {
        Prompt p = new Prompt();
        p.addContext("a");
        assertThrows(UnsupportedOperationException.class, () -> p.getContext().add("b"));
    }
}
