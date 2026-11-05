package com.recipeagent.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandLineTest {

    @Test
    void splitsWordsAndKeepsQuotedTextTogether() {
        assertEquals(List.of("flyers", "upload", "./my flyer.txt", "--store", "No Frills"),
                CommandLine.split("flyers upload './my flyer.txt' --store \"No Frills\""));
        assertEquals(List.of("a", ""), CommandLine.split("a \"\""));
        assertEquals(List.of(), CommandLine.split("   "));
        assertEquals(List.of(), CommandLine.split(null));
    }

    @Test
    void readsWordsOptionsAndFlags() {
        CommandLine c = CommandLine.parse("Pantry add green onions --qty 2 --unit=bunch --merge --allergy a,b --allergy c");
        assertEquals("pantry", c.word(0));
        assertEquals("", c.word(9));
        assertEquals("green onions", c.rest(2));
        assertEquals("2", c.option("qty", "1"));
        assertEquals("bunch", c.option("unit", ""));
        assertTrue(c.has("merge"));
        assertEquals("", c.option("merge", "x"));                          // a flag has no value
        assertEquals("1", c.option("missing", "1"));
        assertEquals(List.of("a", "b", "c"), c.optionList("allergy"));
        assertEquals(List.of("--merge", "--allergy"), c.unknownOptions("qty", "unit"));
        assertEquals("", c.rest(7));
    }

    @Test
    void tablePadsColumnsAndCutsLongText() {
        String t = new TextTable("Item", "Qty").row("milk", 2).row("x".repeat(50), null).render();
        String[] lines = t.split(System.lineSeparator());
        assertEquals("Item" + " ".repeat(38) + "Qty", lines[0]);
        assertTrue(lines[1].startsWith("----"));
        assertEquals("milk" + " ".repeat(38) + "2", lines[2]);
        assertEquals("x".repeat(37) + "...", lines[3]);                     // no trailing spaces
    }
}
