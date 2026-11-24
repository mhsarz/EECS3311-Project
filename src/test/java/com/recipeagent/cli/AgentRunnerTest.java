package com.recipeagent.cli;

import com.recipeagent.agent.AgentTool;
import com.recipeagent.controller.AgentController;
import com.recipeagent.llm.LLMClient;
import com.recipeagent.llm.LLMResponse;
import com.recipeagent.model.CartItem;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.StorageLocation;
import com.recipeagent.storage.JsonStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The standalone agent mode (-input / -stdin / -task), with a scripted AI. */
class AgentRunnerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tmp;

    private static class ScriptedLLM implements LLMClient {
        final Deque<LLMResponse> replies = new ArrayDeque<>();
        final List<String> prompts = new ArrayList<>();

        ScriptedLLM text(String s) {
            replies.add(LLMResponse.text(s));
            return this;
        }

        ScriptedLLM tool(String name, String args) {
            replies.add(LLMResponse.toolCall(name, args));
            return this;
        }

        @Override
        public LLMResponse generate(String prompt, List<AgentTool> tools) {
            prompts.add(prompt);
            return replies.isEmpty() ? LLMResponse.text("") : replies.poll();
        }
    }

    private AgentController controller(LLMClient llm) {
        return new AgentController(llm, 100, new JsonStorage(tmp.resolve("data")), CLOCK);
    }

    private String output;

    private int run(AgentController c, String stdin, String... args) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        int code = new AgentRunner(c, new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)), out)
                .run(AgentRunner.parse(args));
        output = bytes.toString(StandardCharsets.UTF_8).replace(System.lineSeparator(), "\n");
        return code;
    }

    @Test
    void readsOptionsWithOneOrTwoDashes() {
        AgentRunner.Options o = AgentRunner.parse(new String[]{"-LLMAPI", "-input", "task.txt"});
        assertEquals("claude", o.llmApi);
        assertEquals(Path.of("task.txt"), o.input);
        assertFalse(o.stdin);

        o = AgentRunner.parse(new String[]{"--llm-api=Claude", "--task", "What is expiring?", "--output", "a.txt",
                "--model", "claude-haiku-5-5", "--data", "demo"});
        assertEquals("claude", o.llmApi);
        assertEquals("What is expiring?", o.task);
        assertEquals(Path.of("a.txt"), o.output);
        assertEquals("claude-haiku-5-5", o.model);
        assertEquals("demo", o.dataDir);

        assertTrue(AgentRunner.parse(new String[]{"-stdin"}).stdin);
        assertTrue(AgentRunner.parse(new String[]{"-input", "-"}).stdin);
        assertTrue(AgentRunner.parse(new String[]{"-input", "x.txt", "-commands"}).commands);
        assertTrue(AgentRunner.parse(new String[]{"-help"}).help);
    }

    @Test
    void rejectsBadOptions() {
        assertThrows(IllegalArgumentException.class, () -> AgentRunner.parse(new String[]{"-LLMAPI", "openai", "-stdin"}));
        assertThrows(IllegalArgumentException.class, () -> AgentRunner.parse(new String[]{"-LLMAPI"}));      // no task
        assertThrows(IllegalArgumentException.class, () -> AgentRunner.parse(new String[]{"-input", "a.txt", "-stdin"}));
        assertThrows(IllegalArgumentException.class, () -> AgentRunner.parse(new String[]{"-input"}));
        assertThrows(IllegalArgumentException.class, () -> AgentRunner.parse(new String[]{"-colour", "red", "-stdin"}));
        assertThrows(IllegalArgumentException.class, () -> AgentRunner.parse(new String[]{"task.txt"}));
    }

    @Test
    void launchPrintsUsageForBadOptionsAndHelp() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = AgentRunner.launch(new String[]{"-input"}, com.recipeagent.config.AppConfig.load(tmp.resolve(".env"),
                java.util.Map.of()), new ByteArrayInputStream(new byte[0]), new PrintStream(out), new PrintStream(err));
        assertEquals(2, code);
        assertTrue(err.toString().contains("-input needs a value"));
        assertTrue(err.toString().contains("Usage:"));

        code = AgentRunner.launch(new String[]{"-help"}, com.recipeagent.config.AppConfig.load(tmp.resolve(".env"),
                java.util.Map.of()), new ByteArrayInputStream(new byte[0]), new PrintStream(out), new PrintStream(err));
        assertEquals(0, code);
        assertTrue(out.toString().contains("-LLMAPI NAME"));
    }

    @Test
    void splitsTasksAtDashLinesAndSkipsNotes() {
        List<String> tasks = AgentRunner.splitTasks("# my tasks\nWhat expires soon?\n\n---\nAdd milk\nto my cart.\n---\n\n");
        assertEquals(List.of("What expires soon?", "Add milk to my cart."), tasks);
        assertEquals(List.of("What can I cook tonight?"), AgentRunner.splitTasks("What\r\ncan\r\nI\r\ncook\r\ntonight?\r\n"));
        assertEquals(List.of(), AgentRunner.splitTasks("  \n# only a note\n"));
        assertEquals(List.of("one"), AgentRunner.splitTasks("﻿one".substring(1)));
    }

    @Test
    void runsTheAgentOnATaskFileAndShowsItsSteps() throws Exception {
        ScriptedLLM llm = new ScriptedLLM()
                .tool("pantry", "{\"action\":\"expiring\"}")
                .tool("cart", "{\"action\":\"add\",\"name\":\"rice\",\"quantity\":1,\"unit\":\"kg\"}")
                .text("Make spinach fried rice tonight. I added rice to your cart.");
        AgentController c = controller(llm);
        c.addPantryItem(new PantryItem("spinach", 1, "bag", StorageLocation.FRIDGE, LocalDate.of(2026, 11, 25)), false);
        Path task = tmp.resolve("task.txt");
        Files.writeString(task, "Plan a dinner with what expires soon and add anything missing to my cart.");
        Path answer = tmp.resolve("answer.txt");

        int code = run(c, "", "-LLMAPI", "claude", "-input", task.toString(), "-output", answer.toString());

        assertEquals(0, code, output);
        assertTrue(output.contains("1 task from " + task), output);
        assertTrue(output.contains("Task: Plan a dinner with what expires soon"));
        assertTrue(output.contains("1. used the pantry tool {\"action\":\"expiring\"}"), output);
        assertTrue(output.contains("2. used the cart tool"), output);
        assertTrue(output.contains("Agent: Make spinach fried rice tonight."));
        assertTrue(output.contains("Changed: Cart updated"), output);
        assertTrue(output.endsWith("Done.\n"), output);
        assertTrue(llm.prompts.get(0).contains("Plan a dinner"));
        // the agent really changed the cart, and it is saved
        assertTrue(c.getCartItems().stream().anyMatch(i -> i.hasName("rice")));
        assertTrue(Files.readString(answer).contains("Make spinach fried rice tonight."));
    }

    @Test
    void jsonOutputListsTasksStepsAndChanges() {
        ScriptedLLM llm = new ScriptedLLM()
                .tool("cart", "{\"action\":\"add\",\"name\":\"milk\",\"quantity\":2}")
                .text("I added 2 milk to your cart.");
        AgentController c = controller(llm);

        int code = run(c, "", "-task", "Add 2 milk to my cart", "-json");

        assertEquals(0, code, output);
        com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(output).getAsJsonObject();
        assertTrue(o.get("ok").getAsBoolean());
        com.google.gson.JsonObject t = o.getAsJsonArray("tasks").get(0).getAsJsonObject();
        assertEquals("Add 2 milk to my cart", t.get("task").getAsString());
        assertEquals("I added 2 milk to your cart.", t.get("answer").getAsString());
        com.google.gson.JsonObject step = t.getAsJsonArray("steps").get(0).getAsJsonObject();
        assertEquals("cart", step.get("tool").getAsString());
        assertEquals("add", step.getAsJsonObject("args").get("action").getAsString());
        assertEquals("Cart updated", t.getAsJsonArray("changes").get(0).getAsString());
        // the agent is told nobody can answer follow-up questions
        assertTrue(llm.prompts.get(0).contains("nobody can answer questions"));
    }

    @Test
    void jsonOutputWithoutAKey() {
        int code = run(controller(null), "", "-task", "What can I cook?", "-json");
        assertEquals(1, code);
        com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(output).getAsJsonObject();
        assertFalse(o.get("ok").getAsBoolean());
        assertTrue(o.get("error").getAsString().contains("LLM_API_KEY"));
    }

    @Test
    void readsSeveralTasksFromStandardInput() {
        ScriptedLLM llm = new ScriptedLLM().text("Your cart is empty.").text("Added eggs.");
        AgentController c = controller(llm);

        int code = run(c, "What is in my cart?\n---\nAdd a dozen eggs.\n", "-stdin");

        assertEquals(0, code, output);
        assertTrue(output.contains("2 tasks from standard input"));
        assertTrue(output.contains("Task 1: What is in my cart?"));
        assertTrue(output.contains("Task 2: Add a dozen eggs."));
        assertTrue(output.contains("Steps: answered without using a tool"));
        assertTrue(output.contains("Agent: Added eggs."));
    }

    @Test
    void withoutAKeyItSaysSoAndFails() {
        AgentController c = controller(null);
        int code = run(c, "", "-task", "What can I cook?");
        assertEquals(1, code);
        assertTrue(output.contains("Set LLM_API_KEY in .env"), output);
    }

    @Test
    void missingFileFails() {
        int code = run(controller(null), "", "-input", tmp.resolve("nope.txt").toString());
        assertEquals(1, code);
        assertTrue(output.contains("file not found"));
    }

    @Test
    void commandsModeRunsCliCommandsFromStandardInputWithoutAi() {
        AgentController c = controller(null);
        int code = run(c, "pantry add rice --qty 2 --unit kg\ncart add milk --qty 1 --unit L\npantry list\n",
                "-stdin", "-commands");
        assertEquals(0, code, output);
        assertTrue(output.contains("smart-pantry> pantry add rice --qty 2 --unit kg"));
        assertTrue(output.contains("Ran 3 commands from standard input."), output);
        assertEquals(1, c.getPantryItems().size());
        assertEquals(List.of("milk"), c.getCartItems().stream().map(CartItem::getName).toList());

        code = run(c, "pantry remove nothing-here\n", "-stdin", "-commands");
        assertEquals(1, code);
    }
}
