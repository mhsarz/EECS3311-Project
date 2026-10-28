package com.recipeagent.command;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CommandHistoryTest {

    /** Records calls so the tests can check the order of execute and undo. */
    private static class FakeCommand implements Command {
        private final String name;
        private final List<String> log;

        FakeCommand(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        @Override
        public void execute() {
            log.add("do " + name);
        }

        @Override
        public void undo() {
            log.add("undo " + name);
        }

        @Override
        public String getDescription() {
            return name;
        }
    }

    @Test
    void runExecutesAndUndoReversesLatestFirst() {
        List<String> log = new ArrayList<>();
        CommandHistory h = new CommandHistory();
        h.run(new FakeCommand("A", log));
        h.run(new FakeCommand("B", log));
        assertEquals(Optional.of("B"), h.peekDescription());

        assertEquals("B", h.undo().orElseThrow().getDescription());
        assertEquals("A", h.undo().orElseThrow().getDescription());
        assertEquals(List.of("do A", "do B", "undo B", "undo A"), log);
    }

    @Test
    void undoWithNothingReturnsEmpty() {
        CommandHistory h = new CommandHistory();
        assertFalse(h.canUndo());
        assertTrue(h.undo().isEmpty());
        assertTrue(h.peekDescription().isEmpty());
    }

    @Test
    void failedCommandIsNotRemembered() {
        CommandHistory h = new CommandHistory();
        Command failing = new Command() {
            @Override
            public void execute() {
                throw new IllegalStateException("boom");
            }

            @Override
            public void undo() {
                fail("undo should not be called");
            }
        };
        assertThrows(IllegalStateException.class, () -> h.run(failing));
        assertFalse(h.canUndo());
    }

    @Test
    void keepsOnlyMostRecentCommands() {
        List<String> log = new ArrayList<>();
        CommandHistory h = new CommandHistory(2);
        h.run(new FakeCommand("A", log));
        h.run(new FakeCommand("B", log));
        h.run(new FakeCommand("C", log));
        assertEquals(2, h.size());
        h.undo();
        h.undo();
        assertTrue(h.undo().isEmpty());
        assertFalse(log.contains("undo A"));
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> new CommandHistory(0));
        assertThrows(IllegalArgumentException.class, () -> new CommandHistory().run(null));
    }

    @Test
    void clearForgetsCommands() {
        CommandHistory h = new CommandHistory();
        h.run(new FakeCommand("A", new ArrayList<>()));
        h.clear();
        assertFalse(h.canUndo());
    }
}
