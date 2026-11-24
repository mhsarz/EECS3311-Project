package com.recipeagent.controller;

import com.recipeagent.agent.AgentAction;
import com.recipeagent.agent.AgentTool;
import com.recipeagent.llm.LLMClient;
import com.recipeagent.llm.LLMResponse;
import com.recipeagent.model.CartItem;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.Recipe;
import com.recipeagent.model.StorageLocation;
import com.recipeagent.storage.JsonStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The chat agent doing tasks end to end with the pantry and recipes tools (as the standalone CLI
 * does): the changes go through the controller, are saved and can be undone.
 */
class AgentTaskToolsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tmp;

    static final String RECIPE = "{\"title\":\"Scallion Rice\",\"cook_time_minutes\":20,\"portions\":2,"
            + "\"ingredients\":[{\"name\":\"rice\",\"quantity\":1,\"unit\":\"kg\"},{\"name\":\"scallions\",\"quantity\":1},"
            + "{\"name\":\"soy sauce\",\"quantity\":2,\"unit\":\"tbsp\"}],\"steps\":[\"Cook the rice\",\"Add scallions\"]}";

    /** Answers by prompt: the chat gets the scripted replies, the other AI features fixed answers. */
    private static class RoutingLLM implements LLMClient {
        final Deque<LLMResponse> chat = new ArrayDeque<>();
        int recipeCalls;

        RoutingLLM tool(String name, String args) {
            chat.add(LLMResponse.toolCall(name, args));
            return this;
        }

        RoutingLLM text(String s) {
            chat.add(LLMResponse.text(s));
            return this;
        }

        @Override
        public LLMResponse generate(String prompt, List<AgentTool> tools) {
            if (prompt.contains("You are the assistant inside a recipe and grocery app")) {
                return chat.isEmpty() ? LLMResponse.text("") : chat.poll();
            }
            if (prompt.contains("\"pantry_item\"")) {
                return LLMResponse.text("{\"matches\":[]}");
            }
            if (prompt.contains("\"substitutes\"")) {
                return LLMResponse.text("{\"substitutes\":[{\"substitute\":\"salt\",\"note\":\"a pinch\","
                        + "\"pantry_items\":[\"salt\"]}]}");
            }
            if (prompt.contains("\"cook_time_minutes\"")) {
                recipeCalls++;
                return LLMResponse.text(RECIPE);
            }
            return LLMResponse.text("");
        }
    }

    private AgentController controller(LLMClient llm) {
        return new AgentController(llm, 100, new JsonStorage(tmp), CLOCK);
    }

    private static double qty(AgentController c, String name) {
        return c.getPantryItems().stream().filter(i -> i.hasName(name)).findFirst().orElseThrow().getQuantity();
    }

    private static List<String> changes(AgentController c) {
        return c.getLastChatActions().stream().map(AgentAction::change).filter(Objects::nonNull).toList();
    }

    @Test
    void agentAddsUpdatesAndRemovesPantryItems() {
        RoutingLLM llm = new RoutingLLM()
                .tool("pantry", "{\"action\":\"add\",\"name\":\"milk\",\"quantity\":2,\"unit\":\"L\",\"location\":\"fridge\",\"expires_in_days\":5}")
                .tool("pantry", "{\"action\":\"add\",\"name\":\"eggs\",\"quantity\":12}")
                .text("Added milk and eggs.")
                .tool("pantry", "{\"action\":\"update\",\"name\":\"egg\",\"quantity\":6,\"best_before\":\"2026-12-01\"}")
                .tool("pantry", "{\"action\":\"remove\",\"name\":\"milk\"}")
                .text("Done.");
        AgentController c = controller(llm);

        c.chat("I bought 2 L of milk (good for 5 days) and a dozen eggs");
        assertEquals(List.of("Pantry updated", "Pantry updated"), changes(c));
        PantryItem milk = c.getPantryItems().stream().filter(i -> i.hasName("milk")).findFirst().orElseThrow();
        assertEquals(StorageLocation.FRIDGE, milk.getLocation());
        assertEquals(LocalDate.of(2026, 11, 29), milk.getBestBefore());
        assertEquals(12, qty(c, "eggs"));

        c.chat("I used 6 eggs, they last until Dec 1, and the milk is finished");
        assertEquals(6, qty(c, "eggs"));
        assertEquals(LocalDate.of(2026, 12, 1), c.getPantryItems().get(0).getBestBefore());
        assertEquals(1, controller(null).getPantryItems().size());        // saved to disk
    }

    @Test
    void pantryMistakesComeBackAsErrors() {
        RoutingLLM llm = new RoutingLLM()
                .tool("pantry", "{\"action\":\"remove\",\"name\":\"caviar\"}")
                .tool("pantry", "{\"action\":\"add\",\"name\":\"rice\",\"location\":\"garage\"}")
                .tool("pantry", "{\"action\":\"add\",\"name\":\"rice\",\"best_before\":\"soon\"}")
                .text("I could not do that.");
        AgentController c = controller(llm);
        c.chat("remove caviar and add rice");
        List<AgentAction> acts = c.getLastChatActions();
        assertTrue(acts.get(0).result().contains("not in the pantry"));
        assertTrue(acts.get(1).result().contains("Invalid location"));
        assertTrue(acts.get(2).result().contains("best_before must be a date"));
        assertTrue(c.getPantryItems().isEmpty());
        assertTrue(changes(c).isEmpty());
    }

    @Test
    void agentGeneratesChecksCooksUndoesAndRates() {
        RoutingLLM llm = new RoutingLLM()
                .tool("recipes", "{\"action\":\"generate\",\"minutes\":30,\"portions\":2}")
                .tool("recipes", "{\"action\":\"check\",\"add_missing\":true}")
                .text("Here is Scallion Rice; I added the missing items to your cart.")
                .tool("recipes", "{\"action\":\"cook\"}")
                .text("Cooked.")
                .tool("recipes", "{\"action\":\"undo\"}")
                .text("Undone.")
                .tool("recipes", "{\"action\":\"rate\",\"recipe\":\"scallion\",\"stars\":4}")
                .tool("recipes", "{\"action\":\"favourite\"}")
                .text("Rated and saved.");
        AgentController c = controller(llm);
        c.addPantryItem(new PantryItem("rice", 2, "kg", StorageLocation.PANTRY, null), false);

        c.chat("Make me a dinner for 2 in 30 minutes and put what I'm missing in my cart");
        assertEquals(1, llm.recipeCalls);
        assertEquals(List.of("scallions", "soy sauce"), c.getCartItems().stream().map(CartItem::getName).toList());
        assertEquals(List.of("Cart updated"), changes(c));
        assertTrue(c.getLastChatActions().get(0).result().contains("\"status\":\"in_pantry\""));

        c.chat("I cooked it");
        assertEquals(1, qty(c, "rice"));
        assertEquals(1, c.getHistory().size());                          // cooking keeps the recipe
        assertEquals(List.of("Marked as cooked"), changes(c));

        c.chat("undo that");
        assertEquals(2, qty(c, "rice"));

        c.chat("give it 4 stars and make it a favourite");
        Recipe r = controller(null).getHistory().get(0);                // saved to disk
        assertEquals("Scallion Rice", r.getTitle());
        assertEquals(4, r.getRating());
        assertTrue(r.isFavourite());
    }

    @Test
    void agentUsesASubstitute() {
        RoutingLLM llm = new RoutingLLM()
                .tool("recipes", "{\"action\":\"generate\"}")
                .tool("recipes", "{\"action\":\"use_substitute\",\"ingredient\":\"soy sauce\",\"option\":1}")
                .tool("recipes", "{\"action\":\"substitute\",\"ingredient\":\"soy\"}")
                .tool("recipes", "{\"action\":\"use_substitute\",\"ingredient\":\"soy sauce\",\"option\":1}")
                .text("I used salt instead of soy sauce.");
        AgentController c = controller(llm);
        c.addPantryItem(new PantryItem("rice", 2, "kg", StorageLocation.PANTRY, null), false);
        c.chat("make a recipe and replace the soy sauce");
        List<AgentAction> acts = c.getLastChatActions();
        assertTrue(acts.get(1).result().contains("call 'substitute' for soy sauce first"));
        assertTrue(acts.get(2).result().contains("\"substitute\":\"salt\""));
        assertTrue(acts.get(3).result().contains("soy sauce with salt"), acts.get(3).result());
        assertEquals(List.of("Recipe changed"), changes(c));
    }

    @Test
    void recipeActionsNeedARecipe() {
        RoutingLLM llm = new RoutingLLM()
                .tool("recipes", "{\"action\":\"cook\"}")
                .tool("recipes", "{\"action\":\"undo\"}")
                .tool("recipes", "{\"action\":\"show\",\"recipe\":\"lasagna\"}")
                .text("You have no recipe yet.");
        AgentController c = controller(llm);
        c.chat("I cooked it");
        List<AgentAction> acts = c.getLastChatActions();
        assertTrue(acts.get(0).result().contains("no current recipe"));
        assertTrue(acts.get(1).result().contains("nothing to undo"));
        assertTrue(acts.get(2).result().contains("no saved recipe called 'lasagna'"));
        assertTrue(c.getHistory().isEmpty());
    }

    @Test
    void agentSavesTheCurrentRecipeOnlyOnce() {
        RoutingLLM llm = new RoutingLLM()
                .tool("recipes", "{\"action\":\"generate\",\"minutes\":20}")
                .tool("recipes", "{\"action\":\"save\"}")
                .tool("recipes", "{\"action\":\"save\"}")
                .text("Saved.");
        AgentController c = controller(llm);
        c.addPantryItem(new PantryItem("rice", 2, "kg", StorageLocation.PANTRY, null), false);
        c.chat("make something and save it");
        assertEquals(1, c.getHistory().size());
        assertTrue(c.getLastChatActions().get(2).result().contains("already saved"));
        assertEquals(List.of("Recipe saved"), changes(c));
    }
}
