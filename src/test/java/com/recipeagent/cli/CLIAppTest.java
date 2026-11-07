package com.recipeagent.cli;

import com.recipeagent.agent.AgentTool;
import com.recipeagent.controller.AgentController;
import com.recipeagent.llm.LLMClient;
import com.recipeagent.llm.LLMResponse;
import com.recipeagent.model.Recipe;
import com.recipeagent.storage.JsonStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
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

/** The CLI (all features from a terminal), with a scripted AI and a temporary data folder. */
class CLIAppTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tmp;

    private static class ScriptedLLM implements LLMClient {
        final Deque<LLMResponse> replies = new ArrayDeque<>();
        final List<String> prompts = new ArrayList<>();

        ScriptedLLM text(String s) {
            replies.add(LLMResponse.text(s));
            return this;
        }

        @Override
        public LLMResponse generate(String prompt, List<AgentTool> tools) {
            prompts.add(prompt);
            return replies.isEmpty() ? LLMResponse.text("") : replies.poll();
        }
    }

    private AgentController controller(LLMClient llm) {
        return new AgentController(llm, 100, new JsonStorage(tmp), CLOCK);
    }

    /** Runs the CLI with these arguments (no arguments = the shell, fed {@code input}); returns the output. */
    private static String run(AgentController c, String input, String... args) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        int code = new CLIApp(c, new BufferedReader(new StringReader(input)), out).run(args);
        String text = bytes.toString(StandardCharsets.UTF_8).replace(System.lineSeparator(), "\n");
        return code + "|" + text;
    }

    private static final String RECIPE = "{\"title\":\"Scallion Rice\",\"cook_time_minutes\":20,\"portions\":2,"
            + "\"ingredients\":[{\"name\":\"rice\",\"quantity\":1,\"unit\":\"kg\"},{\"name\":\"scallions\",\"quantity\":1},"
            + "{\"name\":\"soy sauce\",\"quantity\":2,\"unit\":\"tbsp\"}],\"steps\":[\"Cook the rice\",\"Add scallions\"]}";

    @Test
    void pantryCommandsWorkWithoutAi() {
        AgentController c = controller(null);
        String add = run(c, "", "pantry", "add", "green", "onions", "--qty", "2", "--unit", "bunch",
                "--location", "fridge", "--expires", "2026-11-06");
        assertTrue(add.startsWith("0|"));
        assertTrue(add.contains("[ALERT] Expiring: green onions (fridge) expires tomorrow"));
        assertTrue(add.contains("Added green onions (2 bunch, fridge, best before Nov 6, 2026)."));

        String dup = run(c, "", "pantry", "add", "Green Onions");
        assertTrue(dup.contains("already in your pantry. Nothing was added. Use --merge"));
        assertTrue(run(c, "", "pantry", "add", "green onions", "--merge").contains("Merged: green onions is now 3 bunch."));

        String list = run(c, "", "pantry", "list");
        assertTrue(list.contains("green onions  3    bunch  Fridge    Nov 6, 2026  1 day - use soon"));
        assertTrue(run(c, "", "pantry", "edit", "green onions", "--expires", "none").contains("(3 bunch, fridge)."));
        assertTrue(run(c, "", "pantry", "expiring").contains("Nothing in your pantry expires"));
        assertTrue(run(c, "", "pantry", "remove", "green onions").contains("Removed green onions."));
        assertTrue(run(c, "", "pantry", "list").contains("Your pantry is empty."));

        assertTrue(run(c, "", "pantry", "add", "rice", "--qty", "0").startsWith("1|Error: Quantity must be more than zero."));
        assertTrue(run(c, "", "pantry", "add", "rice", "--expires", "12/11/2026").contains("Dates are year-month-day"));
        assertTrue(run(c, "", "pantry", "add", "rice", "--location", "attic").contains("pantry, fridge or freezer"));
        assertTrue(run(c, "", "pantry", "add", "rice", "--qyt", "2").contains("Unknown option --qyt"));
        assertTrue(run(c, "", "pantry").contains("Usage:"));
        assertTrue(run(c, "", "nonsense").startsWith("1|Error: Unknown command 'nonsense'."));
    }

    @Test
    void recipeFlowInTheShellWithUndo() {
        ScriptedLLM llm = new ScriptedLLM().text(RECIPE)
                .text("{\"matches\":[{\"ingredient\":\"scallions\",\"pantry_item\":\"green onions\"}]}")
                .text("{\"substitutes\":[{\"substitute\":\"tamari\",\"note\":\"same amount\",\"pantry_items\":[]}]}");
        AgentController c = controller(llm);
        run(c, "", "pantry", "add", "rice", "--qty", "2", "--unit", "kg");
        run(c, "", "pantry", "add", "green onions", "--qty", "2");

        String out = run(c, String.join("\n",
                "recipe generate --time 20",
                "recipe check last",
                "y",
                "recipe substitute last soy sauce",
                "1",
                "recipe save",
                "recipe cooked last",
                "y",
                "",
                "undo",
                "exit"));
        assertTrue(out.contains("Scallion Rice\n20 min, 2 portions"));
        assertTrue(out.contains("  - rice: 1 kg  [in pantry]"));
        assertTrue(out.contains("(not saved yet; use 'last' in commands)"));
        assertTrue(out.contains("soy sauce  2 tbsp         Not in pantry"));        // scallions matched by the AI
        assertTrue(out.contains("Added to your cart: soy sauce."));
        assertTrue(out.contains("1. tamari\n     same amount"));
        assertTrue(out.contains("Using tamari instead of soy sauce"));
        assertTrue(out.contains("Saved \"Scallion Rice\" to your history"));
        assertTrue(out.contains("You are cooking \"Scallion Rice\". Reduce these ingredients in your pantry?"));
        assertTrue(out.contains("  rice: 1 kg (you have 2 kg, 1 kg left)"));
        assertTrue(out.contains("Marked \"Scallion Rice\" as cooked."));
        assertTrue(out.contains("Rate it 1-5, or press Enter to skip for now: Skipped. Rate it later with: history rate"));
        assertTrue(out.contains("Undone: Cooked Scallion Rice. Your pantry is back as it was."));
        assertTrue(out.contains("Bye. Everything is saved."));
        assertTrue(llm.prompts.get(0).contains("at most 20 minutes"));
        assertEquals(1, c.getCartItems().size());
        assertEquals("tamari", c.getHistory().get(0).getIngredients().get(2).getName());

        String shown = run(c, "", "recipe", "show", CLIApp.shortId(c.getHistory().get(0)));
        assertTrue(shown.contains("  - scallions: 1 (your green onions)  [in pantry]"));
    }

    @Test
    void oneShotGenerateCanSaveAndOtherCommandsUseTheId() {
        ScriptedLLM llm = new ScriptedLLM().text(RECIPE);
        AgentController c = controller(llm);
        run(c, "", "pantry", "add", "rice", "--qty", "2");
        String generated = run(c, "", "recipe", "generate", "--save");
        assertTrue(generated.contains("Saved to your history (id "));
        assertFalse(generated.contains("not saved yet"));
        Recipe r = c.getHistory().get(0);
        String id = CLIApp.shortId(r);

        assertTrue(run(c, "", "history", "rate", id, "5").contains("Rated \"Scallion Rice\" 5 of 5."));
        assertTrue(run(c, "", "history", "fav", id.substring(0, 4)).contains("is now a favourite."));
        String list = run(c, "", "history", "list", "--favourites");
        assertTrue(list.contains(id + "  Scallion Rice  5/5     yes  0       never        20 min"));
        assertTrue(run(c, "", "history", "search", "scallion").contains("Scallion Rice"));
        assertTrue(run(c, "", "history", "search", "tofu").contains("No recipes match \"tofu\""));
        assertTrue(run(c, "", "history", "rate", id, "9").contains("between 1 and 5"));
        assertTrue(run(c, "", "history", "rate", "abc", "4").contains("at least the first 4 characters"));
        assertTrue(run(c, "", "recipe", "check", "last").contains("No recipe yet"));     // a new run has no "last"
        assertTrue(run(c, "", "recipe", "cooked", id).contains("Undo works in the interactive CLI"));
        assertTrue(run(c, "", "undo").contains("Undone: Cooked Scallion Rice."));   // same controller = same session here
        assertTrue(run(c, "", "undo").contains("There is nothing to undo. (Undo works within one interactive session.)"));
        assertTrue(run(c, "", "history", "remove", id).contains("Removed \"Scallion Rice\""));
    }

    @Test
    void cartAndFlyersWithSaleAlerts() throws Exception {
        ScriptedLLM llm = new ScriptedLLM()
                .text("{\"end_date\":\"2026-11-11\",\"items\":[{\"name\":\"2% Milk 4L\",\"price\":5.49}]}")
                .text("{\"matches\":[{\"cart_item\":\"milk\",\"flyer_item_id\":1}]}");
        AgentController c = controller(llm);
        assertTrue(run(c, "", "cart", "add", "milk", "--qty", "2", "--unit", "L")
                .contains("(No flyers for this week yet, so sales were not checked.)"));
        Path file = tmp.resolve("nofrills.txt");
        Files.writeString(file, "No Frills: 2% Milk 4L $5.49");

        String up = run(c, "", "flyers", "upload", file.toString(), "--store", "No Frills");
        assertTrue(up.contains("Read 1 sale item from No Frills (until Nov 11, 2026)."));
        assertTrue(up.contains("[ALERT] On sale: milk is on sale at No Frills: 2% Milk 4L for $5.49"));
        assertTrue(run(c, "", "cart", "list").contains("milk  2    L     You         No Frills $5.49"));
        assertTrue(run(c, "", "flyers", "list", "--search", "MILK").contains("No Frills  2% Milk 4L  $5.49  Nov 11, 2026  milk"));
        assertTrue(run(c, "", "flyers", "stores").contains("[x] No Frills"));
        assertTrue(run(c, "", "flyers", "upload", file.toString()).contains("Add --store"));
        assertTrue(run(c, "", "flyers", "upload", tmp.resolve("nope.txt").toString(), "--store", "Metro")
                .contains("Error: The file nope.txt was not found."));
        assertTrue(run(c, "", "flyers", "pick", "Metro", "Costco").contains("Picked: Metro, Costco."));
        assertTrue(run(c, "", "flyers", "unpick", "Walmart").contains("Unpicked: Walmart."));
        assertTrue(run(c, "", "flyers", "unpick", "Zellers").contains("not in your store list"));
        assertTrue(run(c, "", "cart", "remove", "milk").contains("Removed milk"));
        assertTrue(run(c, "", "cart", "clear").contains("Your cart is empty."));
    }

    @Test
    void chatAndProfileSetup() {
        ScriptedLLM llm = new ScriptedLLM();
        llm.replies.add(LLMResponse.toolCall("cart", "{\"action\":\"add\",\"name\":\"eggs\",\"quantity\":12}"));
        llm.text("Added 12 eggs.");
        AgentController c = controller(llm);
        String one = run(c, "", "chat", "add a dozen eggs");
        assertTrue(one.contains("agent> Added 12 eggs.\n       (cart updated)"));

        llm.text("Do you follow a diet?");
        llm.replies.add(LLMResponse.toolCall("profile", "{\"action\":\"save\",\"diet\":\"halal\"}"));
        llm.text("Saved your profile.");
        String setup = run(c, "halal\nexit\nexit\n", new String[0]);
        assertFalse(setup.contains("agent>"));                               // the shell does nothing until asked
        String session = run(c, "profile setup\nhalal\nexit\nprofile show\nexit\n");
        assertTrue(session.contains("agent> Do you follow a diet?"));
        assertTrue(session.contains("agent> Saved your profile.\n       (profile saved)"));
        assertTrue(session.contains("Diet:             halal"));

        AgentController noAi = controller(null);
        assertTrue(run(noAi, "", "profile", "setup").contains("profile setup needs the AI"));
        assertTrue(run(noAi, "", "chat", "hi").contains("AI unavailable"));
        String set = run(noAi, "", "profile", "set", "--diet", "vegan", "--allergy", "peanuts,sesame", "--portions", "4");
        assertTrue(set.contains("Allergies:        peanuts, sesame"));
        assertTrue(run(noAi, "", "profile", "set", "--allergy", "none").contains("Allergies:        none"));
        assertTrue(run(noAi, "", "profile", "set", "--portions", "0").contains("between 1 and 50"));
    }

    @Test
    void helpAndStatus() {
        AgentController c = controller(null);
        String help = run(c, "", "help");
        for (String cmd : List.of("pantry add", "recipe generate", "recipe check", "recipe substitute", "recipe cooked",
                "cart add", "flyers upload", "history rate", "profile set", "chat", "undo")) {
            assertTrue(help.contains(cmd), cmd);
        }
        assertTrue(run(c, "", "help", "cart").startsWith("0|cart add"));
        assertTrue(run(c, "", "status").contains("Pantry: 0 items, cart: 0 items, history: 0 recipes"));
    }

    @Test
    void textHelpers() {
        assertEquals("Expired 1 day ago", CLIApp.daysLabel(-1));
        assertEquals("Today - use soon", CLIApp.daysLabel(0));
        assertEquals("3 days - use soon", CLIApp.daysLabel(3));
        assertEquals("4 days", CLIApp.daysLabel(4));
        assertEquals("1.5 kg", CLIApp.amount(1.5, "kg"));
        assertEquals("2", CLIApp.amount(2, ""));
        assertEquals("$3.50", CLIApp.money(3.5));
        assertEquals("1 item", CLIApp.plural(1, "item"));
        assertEquals(LocalDate.of(2026, 11, 20), CLIApp.date("2026-11-20"));
        assertNull(CLIApp.date("none"));
        LocalDate today = LocalDate.of(2026, 11, 5);
        assertEquals(LocalDate.of(2026, 11, 8), CLIApp.date("+3", today));
        assertEquals(today, CLIApp.date("Today", today));
        assertEquals(LocalDate.of(2026, 11, 3), CLIApp.date("-2", today));
        assertEquals(LocalDate.of(2026, 11, 6), CLIApp.date("tomorrow", today));
        assertEquals(LocalDate.of(2027, 1, 1), CLIApp.date("2027-01-01", today));
        assertThrows(IllegalArgumentException.class, () -> CLIApp.date("next week", today));
    }

    @Test
    void runsCommandFiles() throws Exception {
        AgentController c = controller(null);
        Path file = tmp.resolve("cmds.txt");
        Files.writeString(file, "\uFEFF# Sample pantry\n\npantry add milk --qty 1 --unit L --expires +2\n"
                + "pantry add milk\nchat\npantry list\n", StandardCharsets.UTF_8);
        String out = run(c, "should not be read", "run", file.toString());
        assertTrue(out.startsWith("1|"));                       // the chat line did not work
        assertTrue(out.contains("# Sample pantry\n"));
        assertTrue(out.contains(CLIApp.PROMPT + "pantry add milk --qty 1 --unit L --expires +2\n"));
        assertTrue(out.contains("best before Nov 7, 2026"));
        assertTrue(out.contains("Nothing was added"));          // no question asked in a file
        assertTrue(out.contains("In a command file, write the message"));
        assertTrue(out.contains("Ran 4 commands from " + file + "; 1 did not work."));

        Path ok = tmp.resolve("ok.txt");
        Files.writeString(ok, "pantry list\nexit\npantry remove milk\n", StandardCharsets.UTF_8);
        String done = run(c, "", "run", ok.toString());
        assertTrue(done.startsWith("0|"));
        assertTrue(done.contains("Ran 1 command from"));
        assertTrue(run(c, "", "run", tmp.resolve("missing.txt").toString()).contains("File not found"));
        assertTrue(run(c, "", "run").contains("Which file?"));
    }

    @Test
    void findsIngredientsByPartOfTheName() {
        Recipe r = new Recipe("Salad", List.of(new com.recipeagent.model.Ingredient("baby spinach", 100, "g"),
                new com.recipeagent.model.Ingredient("red onion", 1, ""),
                new com.recipeagent.model.Ingredient("onion", 1, "")), List.of("Mix"), 10, 2);
        assertEquals("baby spinach", CLIApp.findIngredient(r, "Spinach").getName());
        assertEquals("onion", CLIApp.findIngredient(r, "onion").getName());   // exact name first
        assertEquals("red onion", CLIApp.findIngredient(r, "red").getName());
        assertThrows(IllegalArgumentException.class, () -> CLIApp.findIngredient(r, "kale"));
        assertEquals("red onion", CLIApp.findIngredient(r, "2").getName());               // by number
        assertTrue(assertThrows(IllegalArgumentException.class, () -> CLIApp.findIngredient(r, "9"))
                .getMessage().contains("from 1 to 3"));
        assertEquals("onion", CLIApp.findIngredient(r, "Onions").getName());             // plural
        Recipe two = new Recipe("Soup", List.of(new com.recipeagent.model.Ingredient("green peas", 1, "cup"),
                new com.recipeagent.model.Ingredient("frozen peas", 1, "cup")), List.of("Boil"), 10, 2);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> CLIApp.findIngredient(two, "peas"));
        assertTrue(e.getMessage().contains("Which one? green peas, frozen peas"));
    }

    @Test
    void flyersUploadTakesAPdf() throws Exception {
        List<Integer> pdfCalls = new ArrayList<>();
        ScriptedLLM llm = new ScriptedLLM() {
            @Override
            public LLMResponse generate(String prompt, List<AgentTool> tools, List<com.recipeagent.llm.Attachment> files) {
                pdfCalls.add(files.size());
                return generate(prompt, tools);
            }
        };
        llm.text("{\"end_date\":null,\"items\":[{\"name\":\"Butter 454 g\",\"price\":4.79}]}");
        AgentController c = controller(llm);
        Path pdf = tmp.resolve("food basics.pdf");
        Files.write(pdf, "%PDF-1.7 pictures of the flyer".getBytes(StandardCharsets.US_ASCII));
        String out = run(c, "", "flyers", "upload", pdf.toString(), "--store", "Food Basics");
        assertTrue(out.contains("Reading the Food Basics flyer (PDF, this can take a minute)..."));
        assertTrue(out.contains("Read 1 sale item from Food Basics."));
        assertEquals(List.of(1), pdfCalls);
        assertTrue(CLIApp.HELP.get("flyers").contains("flyers upload ./nofrills.pdf"));
    }

    @Test
    void chatAnswersLoseMarkdown() {
        assertEquals("Saved:\n- Diet: none\n- Portions: 2", CLIApp.plainText("# Saved:\n- **Diet:** none\n* **Portions:** 2"));
    }

    @Test
    void useExpiringWithNothingExpiringStillGivesARecipe() {
        ScriptedLLM llm = new ScriptedLLM().text(RECIPE);
        AgentController c = controller(llm);
        run(c, "", "pantry", "add", "rice", "--qty", "2", "--unit", "kg");
        String out = run(c, "", "recipe", "generate", "--use-expiring");
        assertTrue(out.startsWith("0|"));
        assertTrue(out.contains("Nothing in your pantry is about to expire, so any pantry food can be used."));
        assertTrue(out.contains("Scallion Rice"));
    }
}
