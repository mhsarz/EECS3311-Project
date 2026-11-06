package com.recipeagent.agent;

import com.recipeagent.agent.tools.CartTool;
import com.recipeagent.agent.tools.PantryTool;
import com.recipeagent.agent.tools.ProfileTool;
import com.recipeagent.cart.ShoppingCart;
import com.recipeagent.llm.LLMClient;
import com.recipeagent.llm.LLMResponse;
import com.recipeagent.llm.LLMUnavailableException;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.StorageLocation;
import com.recipeagent.model.UserProfile;
import com.recipeagent.pantry.PantryManager;
import com.recipeagent.prompt.ClaudePromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatTest {

    /** Plays the LLM: returns scripted responses and records each prompt and tool list. */
    private static class ScriptedLLM implements LLMClient {
        final Deque<LLMResponse> replies = new ArrayDeque<>();
        final List<String> prompts = new ArrayList<>();
        final List<Integer> toolCounts = new ArrayList<>();
        int failOnCall = -1;

        ScriptedLLM then(LLMResponse r) {
            replies.add(r);
            return this;
        }

        @Override
        public LLMResponse generate(String prompt, List<AgentTool> tools) {
            prompts.add(prompt);
            toolCounts.add(tools == null ? 0 : tools.size());
            if (prompts.size() == failOnCall) {
                throw new LLMUnavailableException("LLM unavailable: network down");
            }
            return replies.isEmpty() ? LLMResponse.text("") : replies.poll();
        }
    }

    private final ShoppingCart cart = new ShoppingCart();
    private final UserProfile profile = new UserProfile();
    private final PantryManager pantry = new PantryManager();

    private RecipeAgent agent(ScriptedLLM llm) {
        pantry.addItem(new PantryItem("chicken", 500, "g", StorageLocation.FRIDGE, null));
        return new RecipeAgent(llm, new ClaudePromptBuilder(), List.of(
                new PantryTool(pantry), new ProfileTool(profile), new CartTool(cart)));
    }

    @Test
    void answersDirectlyWhenNoToolIsNeeded() {
        ScriptedLLM llm = new ScriptedLLM().then(LLMResponse.text("Hi! How can I help?"));
        RecipeAgent a = agent(llm);

        assertEquals("Hi! How can I help?", a.chat("hello"));
        assertTrue(a.getLastActions().isEmpty());
        assertEquals(1, llm.prompts.size());
        assertEquals(3, llm.toolCounts.get(0).intValue());          // all three tools were offered
        assertTrue(llm.prompts.get(0).contains("<conversation>\nUser: hello\n</conversation>"));
    }

    @Test
    void runsRequestedToolAndSendsResultBack() {
        ScriptedLLM llm = new ScriptedLLM()
                .then(LLMResponse.toolCall("cart", "{\"action\":\"add\",\"name\":\"eggs\",\"quantity\":12}"))
                .then(LLMResponse.text("I added 12 eggs to your cart."));
        RecipeAgent a = agent(llm);

        String answer = a.chat("Add a dozen eggs to my cart");

        assertEquals("I added 12 eggs to your cart.", answer);
        assertEquals(12, cart.find("eggs").orElseThrow().getQuantity());
        assertEquals(1, a.getLastActions().size());
        assertTrue(a.getLastActions().get(0).succeededWith("cart"));
        assertEquals(2, llm.prompts.size());
        assertTrue(llm.prompts.get(1).contains("Tool cart {\"action\":\"add\""));
        assertTrue(llm.prompts.get(1).contains("\"added\":\"eggs\""));
    }

    @Test
    void chainsSeveralTools() {
        ScriptedLLM llm = new ScriptedLLM()
                .then(LLMResponse.toolCall("pantry", "{\"action\":\"has\",\"name\":\"rice\"}"))
                .then(LLMResponse.toolCall("cart", "{\"action\":\"add\",\"name\":\"rice\"}"))
                .then(LLMResponse.text("You had no rice, so I added it to your cart."));
        RecipeAgent a = agent(llm);

        a.chat("I want to make chicken and rice. Add whatever I'm missing.");

        assertEquals(List.of("pantry", "cart"), a.getLastActions().stream().map(AgentAction::tool).toList());
        assertTrue(a.getLastActions().get(0).result().contains("\"in_pantry\":false"));
        assertTrue(cart.contains("rice"));
    }

    @Test
    void stopsAfterToolLimitAndAsksForAnswer() {
        ScriptedLLM llm = new ScriptedLLM();
        for (int i = 0; i < 5; i++) {
            llm.then(LLMResponse.toolCall("pantry", "list"));
        }
        llm.then(LLMResponse.toolCall("pantry", "list"));   // would be a 6th call: ignored
        RecipeAgent a = agent(llm);

        String answer = a.chat("Check everything");

        assertEquals(5, a.getLastActions().size());
        assertEquals(6, llm.prompts.size());
        assertEquals(0, llm.toolCounts.get(5).intValue());           // no tools offered on the last call
        assertTrue(llm.prompts.get(5).contains("used all 5 tool calls"));
        assertTrue(answer.startsWith("Sorry"));           // the scripted LLM gave no text
    }

    @Test
    void unknownToolOrBadArgsAreReportedToTheLLM() {
        ScriptedLLM llm = new ScriptedLLM()
                .then(LLMResponse.toolCall("oven", "{}"))
                .then(LLMResponse.toolCall("cart", "{\"action\":\"add\"}"))
                .then(LLMResponse.text("Which item should I add?"));
        RecipeAgent a = agent(llm);

        assertEquals("Which item should I add?", a.chat("add something"));
        assertTrue(a.getLastActions().get(0).result().contains("no tool named 'oven'"));
        assertFalse(a.getLastActions().get(1).succeededWith("cart"));
        assertTrue(cart.isEmpty());
    }

    @Test
    void remembersConversationAcrossMessages() {
        ScriptedLLM llm = new ScriptedLLM()
                .then(LLMResponse.text("What do you have in the fridge?"))
                .then(LLMResponse.text("Try a chicken stir fry."));
        RecipeAgent a = agent(llm);
        a.chat("What can I cook tonight?");
        a.chat("Chicken");

        String second = llm.prompts.get(1);
        assertTrue(second.contains("User: What can I cook tonight?\nAssistant: What do you have in the fridge?\nUser: Chicken"));
        assertEquals(4, a.getChatHistory().size());

        a.clearChat();
        assertTrue(a.getChatHistory().isEmpty());
    }

    @Test
    void profileQuestionnaireSavesProfileAtTheEnd() {
        ScriptedLLM llm = new ScriptedLLM()
                .then(LLMResponse.text("Do you follow a diet, like vegetarian or halal?"))
                .then(LLMResponse.text("Any food allergies?"))
                .then(LLMResponse.toolCall("profile",
                        "{\"action\":\"save\",\"diet\":\"vegetarian\",\"allergies\":[\"peanuts\"]}"))
                .then(LLMResponse.text("Saved: vegetarian, allergic to peanuts."));
        RecipeAgent a = agent(llm);

        assertEquals("Do you follow a diet, like vegetarian or halal?", a.startProfileSetup());
        assertTrue(llm.prompts.get(0).contains(RecipeAgent.PROFILE_SETUP_MESSAGE));
        assertTrue(llm.prompts.get(0).contains("diet, allergies, dislikes and the usual number of portions (4 questions)"));
        assertTrue(llm.prompts.get(0).contains("no Markdown"));
        assertTrue(llm.prompts.get(0).contains("ask one question per message"));
        a.chat("Vegetarian");
        String last = a.chat("Peanuts");

        assertEquals("Saved: vegetarian, allergic to peanuts.", last);
        assertTrue(a.getLastActions().get(0).succeededWith("profile"));
        assertEquals("vegetarian", profile.getDiet());
        assertEquals(List.of("peanuts"), profile.getAllergies());
    }

    @Test
    void llmFailureMidwayStillReportsActionsDone() {
        ScriptedLLM llm = new ScriptedLLM()
                .then(LLMResponse.toolCall("cart", "{\"action\":\"add\",\"name\":\"milk\"}"));
        llm.failOnCall = 2;
        RecipeAgent a = agent(llm);

        assertThrows(LLMUnavailableException.class, () -> a.chat("add milk"));
        assertTrue(cart.contains("milk"));
        assertEquals(1, a.getLastActions().size());
    }

    @Test
    void rejectsEmptyMessageAndBadLimit() {
        RecipeAgent a = agent(new ScriptedLLM());
        assertThrows(IllegalArgumentException.class, () -> a.chat(" "));
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeAgent(new ScriptedLLM(), new ClaudePromptBuilder(),
                        List.of(new PantryTool(pantry), new ProfileTool(profile)), 0));
        assertEquals(RecipeAgent.DEFAULT_MAX_TOOL_CALLS, a.getMaxToolCalls());
    }

    @Test
    void fullToolResultIsSentInSameMessageAndShortenedLater() {
        for (String n : List.of("rice", "eggs", "spinach", "onions", "garlic", "tomatoes", "carrots")) {
            pantry.addItem(new PantryItem(n, 1, "kg", StorageLocation.PANTRY, null));
        }
        ScriptedLLM llm = new ScriptedLLM()
                .then(LLMResponse.toolCall("pantry", "list"))
                .then(LLMResponse.text("You could make a vegetable fried rice."))
                .then(LLMResponse.text("You're welcome!"));
        RecipeAgent a = agent(llm);

        a.chat("What can I make with what I have?");
        assertTrue(a.getLastActions().get(0).result().length() > RecipeAgent.MAX_OLD_RESULT_IN_PROMPT);
        String sameMessage = llm.prompts.get(1);
        assertTrue(sameMessage.contains("\"name\":\"carrots\""));    // the last item is not cut off

        a.chat("Thanks");
        String nextMessage = llm.prompts.get(2);
        assertFalse(nextMessage.contains("\"name\":\"carrots\""));   // old result shortened
        assertTrue(nextMessage.contains("Tool pantry list -> "));
        assertTrue(a.getChatHistory().get(1).contains("\"name\":\"carrots\""));  // kept in full in memory
    }

    @Test
    void failedMessageIsMarkedSoTheConversationStaysReadable() {
        ScriptedLLM llm = new ScriptedLLM().then(LLMResponse.text("unused"));
        llm.failOnCall = 1;
        RecipeAgent a = agent(llm);
        assertThrows(LLMUnavailableException.class, () -> a.chat("What's expiring?"));
        assertEquals(List.of("User: What's expiring?", "Assistant: (no answer, the AI was unavailable)"),
                a.getChatHistory());
    }

    @Test
    void conversationMemoryIsLimited() {
        ScriptedLLM llm = new ScriptedLLM();
        RecipeAgent a = agent(llm);
        for (int i = 0; i < 150; i++) {
            a.chat("message " + i);
        }
        List<String> history = a.getChatHistory();
        assertEquals(RecipeAgent.MAX_HISTORY_LINES, history.size());
        assertEquals("User: message 149", history.get(history.size() - 2));
        assertThrows(UnsupportedOperationException.class, () -> history.add("x"));
    }

    @Test
    void requestsFromTwoThreadsDoNotOverlap() throws Exception {
        java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger maxInFlight = new java.util.concurrent.atomic.AtomicInteger();
        LLMClient slow = (prompt, tools) -> {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            inFlight.decrementAndGet();
            return LLMResponse.text("ok");
        };
        RecipeAgent a = agent(new ScriptedLLM());
        RecipeAgent shared = new RecipeAgent(slow, new ClaudePromptBuilder(), List.copyOf(a.getTools().values()));

        Thread t1 = new Thread(() -> shared.chat("first"));
        Thread t2 = new Thread(() -> shared.chat("second"));
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertEquals(1, maxInFlight.get());
        assertEquals(4, shared.getChatHistory().size());
    }
}
