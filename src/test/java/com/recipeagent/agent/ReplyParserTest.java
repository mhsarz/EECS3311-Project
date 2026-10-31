package com.recipeagent.agent;

import com.google.gson.JsonObject;
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
        assertThrows(ReplyParser.ReplyException.class, () -> ReplyParser.extractJson(null));
        assertThrows(ReplyParser.ReplyException.class, () -> ReplyParser.extractJson("no json here"));
        assertThrows(ReplyParser.ReplyException.class, () -> ReplyParser.extractJson("{\"a\": }"));
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
