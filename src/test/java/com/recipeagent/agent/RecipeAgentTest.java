package com.recipeagent.agent;

import com.recipeagent.agent.tools.PantryTool;
import com.recipeagent.agent.tools.ProfileTool;
import com.recipeagent.llm.LLMClient;
import com.recipeagent.llm.LLMException;
import com.recipeagent.llm.LLMResponse;
import com.recipeagent.llm.LLMUnavailableException;
import com.recipeagent.model.Ingredient;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.Recipe;
import com.recipeagent.model.RecipeRequest;
import com.recipeagent.model.StorageLocation;
import com.recipeagent.model.Substitution;
import com.recipeagent.model.UserProfile;
import com.recipeagent.pantry.PantryManager;
import com.recipeagent.prompt.ClaudePromptBuilder;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeAgentTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);

    /** Returns scripted replies in order and remembers every prompt it was sent. */
    private static class ScriptedLLM implements LLMClient {
        final Deque<String> replies = new ArrayDeque<>();
        final List<String> prompts = new ArrayList<>();
        RuntimeException failWith;

        ScriptedLLM reply(String text) {
            replies.add(text);
            return this;
        }

        @Override
        public LLMResponse generate(String prompt, List<AgentTool> tools) {
            prompts.add(prompt);
            if (failWith != null) {
                throw failWith;
            }
            return LLMResponse.text(replies.isEmpty() ? "" : replies.poll());
        }
    }

    private PantryManager pantry;
    private UserProfile profile;

    private RecipeAgent agent(ScriptedLLM llm) {
        return new RecipeAgent(llm, new ClaudePromptBuilder(),
                List.of(new PantryTool(pantry, CLOCK), new ProfileTool(profile)));
    }

    private void stockPantry() {
        pantry = new PantryManager();
        pantry.addItem(new PantryItem("rice", 2, "cup", StorageLocation.PANTRY, null));
        pantry.addItem(new PantryItem("eggs", 6, "", StorageLocation.FRIDGE, null));
        pantry.addItem(new PantryItem("spinach", 1, "bag", StorageLocation.FRIDGE,
                java.time.LocalDate.of(2026, 11, 1)));
        profile = new UserProfile();
        profile.setDiet("vegetarian");
        profile.addAllergy("peanuts");
    }

    private static String recipeJson(int minutes, int portions, String extraIngredient) {
        return "{\"title\":\"Spinach Egg Fried Rice\",\"cook_time_minutes\":" + minutes
                + ",\"portions\":" + portions + ",\"ingredients\":["
                + "{\"name\":\"rice\",\"quantity\":1,\"unit\":\"cup\"},"
                + "{\"name\":\"eggs\",\"quantity\":2,\"unit\":\"\"},"
                + "{\"name\":\"spinach\",\"quantity\":1,\"unit\":\"bag\"}"
                + (extraIngredient == null ? "" : ",{\"name\":\"" + extraIngredient + "\",\"quantity\":1,\"unit\":\"tbsp\"}")
                + "],\"steps\":[\"Cook rice\",\"Scramble eggs\",\"Mix with spinach\"]}";
    }

    // ------------------------------------------------------------- F01

    @Test
    void generatesRecipeFromPantryProfileAndFavourites() {
        stockPantry();
        ScriptedLLM llm = new ScriptedLLM().reply(recipeJson(25, 2, null));
        RecipeRequest req = new RecipeRequest(30, 2);
        req.addRequiredItem("spinach");
        req.setFavourites(List.of("Veggie Curry"));

        Recipe r = agent(llm).generateRecipe(req);

        assertEquals("Spinach Egg Fried Rice", r.getTitle());
        assertEquals(25, r.getCookTimeMinutes());
        assertEquals(2, r.getPortions());
        assertEquals(3, r.getIngredients().size());
        assertEquals(new Ingredient("rice", 1, "cup"), r.getIngredients().get(0));
        assertEquals(3, r.getSteps().size());

        assertEquals(1, llm.prompts.size());
        String p = llm.prompts.get(0);
        assertTrue(p.contains("at most 30 minutes"));
        assertTrue(p.contains("exactly 2 portions"));
        assertTrue(p.contains("must use: spinach"));
        assertTrue(p.contains("<pantry>"));
        assertTrue(p.contains("\"name\":\"spinach\""));
        assertTrue(p.contains("\"days_left\":1"));
        assertTrue(p.contains("\"allergies\":[\"peanuts\"]"));
        assertTrue(p.contains("<top_rated_recipes>\nVeggie Curry\n</top_rated_recipes>"));
        assertTrue(p.contains("Use only pantry ingredients"));
        assertTrue(p.contains("<format>"));
    }

    @Test
    void acceptsJsonWrappedInCodeFence() {
        stockPantry();
        ScriptedLLM llm = new ScriptedLLM().reply("Here you go:\n```json\n" + recipeJson(20, 2, null) + "\n```");
        assertEquals("Spinach Egg Fried Rice", agent(llm).generateRecipe(new RecipeRequest(30, 2)).getTitle());
    }

    @Test
    void retriesOnceWhenRecipeIsTooLong() {
        stockPantry();
        ScriptedLLM llm = new ScriptedLLM()
                .reply(recipeJson(45, 2, null))
                .reply(recipeJson(28, 2, null));

        Recipe r = agent(llm).generateRecipe(new RecipeRequest(30, 2));

        assertEquals(28, r.getCookTimeMinutes());
        assertEquals(2, llm.prompts.size());
        assertFalse(llm.prompts.get(0).contains("problem_with_previous_answer"));
        assertTrue(llm.prompts.get(1).contains("it takes 45 minutes but the limit is 30"));
    }

    @Test
    void retriesWhenRecipeContainsAllergen() {
        stockPantry();
        ScriptedLLM llm = new ScriptedLLM()
                .reply(recipeJson(20, 2, "peanut butter"))
                .reply(recipeJson(20, 2, "soy sauce"));

        Recipe r = agent(llm).generateRecipe(new RecipeRequest(30, 2));

        assertEquals("soy sauce", r.getIngredients().get(3).getName());
        assertTrue(llm.prompts.get(1).contains("allergic to peanuts"));
    }

    @Test
    void retriesOnWrongPortionsOrMissingRequiredItem() {
        stockPantry();
        RecipeRequest req = new RecipeRequest(30, 2);
        req.addRequiredItem("tofu");
        ScriptedLLM llm = new ScriptedLLM().reply(recipeJson(20, 4, null)).reply(recipeJson(20, 2, null));

        LLMException e = assertThrows(LLMException.class, () -> agent(llm).generateRecipe(req));
        assertEquals(2, llm.prompts.size());
        assertTrue(llm.prompts.get(1).contains("makes 4 portions but 2 were asked for"));
        assertTrue(e.getMessage().contains("required item 'tofu'"));
    }

    @Test
    void givesUpAfterTwoUnusableReplies() {
        stockPantry();
        ScriptedLLM llm = new ScriptedLLM().reply("Sorry, I can't.").reply("{\"title\":\"x\"}");
        LLMException e = assertThrows(LLMException.class,
                () -> agent(llm).generateRecipe(new RecipeRequest(30, 2)));
        assertFalse(e instanceof LLMUnavailableException);
        assertEquals(2, llm.prompts.size());
        assertTrue(llm.prompts.get(1).contains("did not contain a JSON object"));
    }

    @Test
    void emptyPantryIsRejectedBeforeCallingLLM() {
        pantry = new PantryManager();
        profile = new UserProfile();
        ScriptedLLM llm = new ScriptedLLM().reply(recipeJson(20, 2, null));

        assertThrows(PantryEmptyException.class, () -> agent(llm).generateRecipe(new RecipeRequest(30, 2)));
        assertTrue(llm.prompts.isEmpty());

        RecipeRequest allowMissing = new RecipeRequest(30, 2);
        allowMissing.setAllowMissing(true);
        assertNotNull(agent(llm).generateRecipe(allowMissing));
        assertTrue(llm.prompts.get(0).contains("You may use ingredients that are not in the pantry"));
    }

    @Test
    void llmUnavailableIsPassedOnWithoutRetry() {
        stockPantry();
        ScriptedLLM llm = new ScriptedLLM();
        llm.failWith = new LLMUnavailableException("No LLM API key is set.");
        assertThrows(LLMUnavailableException.class, () -> agent(llm).generateRecipe(new RecipeRequest(30, 2)));
        assertEquals(1, llm.prompts.size());
    }

    @Test
    void requiresPantryAndProfileTools() {
        assertThrows(IllegalStateException.class,
                () -> new RecipeAgent(new ScriptedLLM(), new ClaudePromptBuilder(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeAgent(null, new ClaudePromptBuilder(), List.of()));
    }

    // ------------------------------------------------------------- F07

    private Recipe pancakes() {
        return new Recipe("Pancakes", List.of(
                new Ingredient("flour", 1, "cup"),
                new Ingredient("buttermilk", 1, "cup"),
                new Ingredient("eggs", 1, "")), List.of("Mix", "Fry"), 20, 2);
    }

    @Test
    void suggestsSubstitutesAndChecksStockInCode() {
        stockPantry();
        pantry.addItem(new PantryItem("milk", 1, "L", StorageLocation.FRIDGE, null));
        pantry.addItem(new PantryItem("lemon", 1, "", StorageLocation.FRIDGE, null));
        ScriptedLLM llm = new ScriptedLLM().reply("{\"substitutes\":["
                + "{\"substitute\":\"milk with lemon juice\",\"note\":\"1 cup milk + 1 tbsp lemon juice, rest 5 min\",\"pantry_items\":[\"milk\",\"lemon\"]},"
                + "{\"substitute\":\"plain yogurt\",\"note\":\"thin with water\",\"pantry_items\":[\"yogurt\"]},"
                + "{\"substitute\":\"oat milk\",\"note\":\"less tangy\",\"pantry_items\":[]}]}");

        List<Substitution> subs = agent(llm).suggestSubstitutes(pancakes(), "Buttermilk");

        assertEquals(3, subs.size());
        assertEquals("milk with lemon juice", subs.get(0).getSubstitute());
        assertTrue(subs.get(0).isInStock());
        assertFalse(subs.get(1).isInStock());   // LLM named "yogurt", which is not in the pantry
        assertFalse(subs.get(2).isInStock());
        String p = llm.prompts.get(0);
        assertTrue(p.contains("missing \"buttermilk\""));
        assertTrue(p.contains("1. Mix"));
        assertTrue(p.contains("<missing_ingredient>"));
    }

    @Test
    void dropsAllergenSubstitutesAndKeepsAtMostThree() {
        stockPantry();
        ScriptedLLM llm = new ScriptedLLM().reply("{\"substitutes\":["
                + "{\"substitute\":\"peanut milk\",\"note\":\"x\"},"
                + "{\"substitute\":\"A\",\"note\":\"\"},{\"substitute\":\"B\"},"
                + "{\"substitute\":\"C\"},{\"substitute\":\"D\"}]}");
        List<Substitution> subs = agent(llm).suggestSubstitutes(pancakes(), "buttermilk");
        assertEquals(List.of("A", "B", "C"), subs.stream().map(Substitution::getSubstitute).toList());
    }

    @Test
    void noSubstituteGivesEmptyList() {
        stockPantry();
        ScriptedLLM llm = new ScriptedLLM().reply("{\"substitutes\":[]}");
        assertTrue(agent(llm).suggestSubstitutes(pancakes(), "eggs").isEmpty());
    }

    @Test
    void substituteForUnknownIngredientIsRejected() {
        stockPantry();
        ScriptedLLM llm = new ScriptedLLM();
        assertThrows(IllegalArgumentException.class, () -> agent(llm).suggestSubstitutes(pancakes(), "chocolate"));
        assertTrue(llm.prompts.isEmpty());
    }

    @Test
    void substituteRetriesOnBadReply() {
        stockPantry();
        ScriptedLLM llm = new ScriptedLLM().reply("{\"options\":[]}").reply("{\"substitutes\":[{\"substitute\":\"yogurt\"}]}");
        assertEquals(1, agent(llm).suggestSubstitutes(pancakes(), "buttermilk").size());
        assertTrue(llm.prompts.get(1).contains("'substitutes' must be a list"));
    }

    // ------------------------------------------------------------- helpers

    @Test
    void allergenMatchingIgnoresCaseAndPlural() {
        List<String> allergies = List.of("peanuts", "tomatoes", "fish");
        assertEquals("peanuts", RecipeAgent.findAllergen("Peanut Butter", allergies));
        assertEquals("tomatoes", RecipeAgent.findAllergen("cherry tomato", allergies));
        assertEquals("fish", RecipeAgent.findAllergen("Fish sauce", allergies));
        assertNull(RecipeAgent.findAllergen("rice", allergies));
        assertNull(RecipeAgent.findAllergen("rice", null));
    }
}
