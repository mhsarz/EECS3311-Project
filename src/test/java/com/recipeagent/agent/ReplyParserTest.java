package com.recipeagent.agent;

import com.google.gson.JsonObject;
import com.recipeagent.llm.LLMResponse;
import com.recipeagent.model.Recipe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReplyParserTest {

    @Test
    void extractsJsonFromSurroundingText() throws Exception {
        JsonObject o = ReplyParser.extractJson("Sure!\n```json\n{\"a\": 1}\n```\nEnjoy.");
        assertEquals(1, o.get("a").getAsInt());
    }

    @Test
    void rejectsRepliesWithoutValidJson() {
        assertThrows(ReplyParser.ReplyException.class, () -> ReplyParser.extractJson((String) null));
        assertThrows(ReplyParser.ReplyException.class, () -> ReplyParser.extractJson("no json here"));
        assertThrows(ReplyParser.ReplyException.class, () -> ReplyParser.extractJson("{\"a\": }"));
    }

    @Test
    void cutOffReplySaysItWasTooLong() throws Exception {
        LLMResponse cut = new LLMResponse("{\"title\": \"Sou", null, null, "max_tokens", 10, 8000);
        ReplyParser.ReplyException e = assertThrows(ReplyParser.ReplyException.class,
                () -> ReplyParser.extractJson(cut));
        assertTrue(e.getMessage().contains("cut off"));
        LLMResponse bad = new LLMResponse("no json", null, null, "end_turn", 10, 5);
        e = assertThrows(ReplyParser.ReplyException.class, () -> ReplyParser.extractJson(bad));
        assertFalse(e.getMessage().contains("cut off"));
        LLMResponse ok = new LLMResponse("{\"a\": 1}", null, null, "max_tokens", 10, 5);
        assertEquals(1, ReplyParser.extractJson(ok).get("a").getAsInt());
    }

    @Test
    void readsRecipeAcceptingNumbersAsText() throws Exception {
        Recipe r = ReplyParser.toRecipe(ReplyParser.extractJson("{\"title\":\"Soup\","
                + "\"cook_time_minutes\":\"20\",\"portions\":2.0,"
                + "\"ingredients\":[{\"name\":\"carrot\",\"quantity\":\"3\"},{\"name\":\"salt\"}],"
                + "\"steps\":[\"Chop\",\"\",\"Boil\"]}"));
        assertEquals(20, r.getCookTimeMinutes());
        assertEquals(2, r.getPortions());
        assertEquals(3, r.getIngredients().get(0).getQuantity());
        assertEquals(0, r.getIngredients().get(1).getQuantity());   // "to taste"
        assertEquals("", r.getIngredients().get(1).getUnit());
        assertEquals(2, r.getSteps().size());                        // blank step dropped
    }

    @Test
    void reportsWhatIsWrongWithRecipe() {
        assertEquals("'title' is missing", reason("{\"cook_time_minutes\":10,\"portions\":1,"
                + "\"ingredients\":[{\"name\":\"x\"}],\"steps\":[\"a\"]}"));
        assertEquals("the recipe has no ingredients", reason("{\"title\":\"t\",\"cook_time_minutes\":10,"
                + "\"portions\":1,\"ingredients\":[],\"steps\":[\"a\"]}"));
        assertEquals("the recipe has no steps", reason("{\"title\":\"t\",\"cook_time_minutes\":10,"
                + "\"portions\":1,\"ingredients\":[{\"name\":\"x\"}],\"steps\":[]}"));
        assertEquals("'ingredients' must be a list", reason("{\"title\":\"t\",\"cook_time_minutes\":10,"
                + "\"portions\":1,\"ingredients\":\"rice\",\"steps\":[\"a\"]}"));
        assertTrue(reason("{\"title\":\"t\",\"cook_time_minutes\":0,\"portions\":1,"
                + "\"ingredients\":[{\"name\":\"x\"}],\"steps\":[\"a\"]}").contains("Cooking time"));
        assertTrue(reason("{\"title\":\"t\",\"cook_time_minutes\":5,\"portions\":1,"
                + "\"ingredients\":[{\"name\":\"x\",\"quantity\":-1}],\"steps\":[\"a\"]}").contains("ingredient 'x'"));
    }

    private static String reason(String json) {
        ReplyParser.ReplyException e = assertThrows(ReplyParser.ReplyException.class,
                () -> ReplyParser.toRecipe(ReplyParser.extractJson(json)));
        return e.getMessage();
    }
}
