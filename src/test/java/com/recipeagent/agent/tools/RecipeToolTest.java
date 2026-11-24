package com.recipeagent.agent.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.recipeagent.history.RecipeHistory;
import com.recipeagent.model.Ingredient;
import com.recipeagent.model.Recipe;
import com.recipeagent.model.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The chat's recipes tool: list and save (F08, F09). */
class RecipeToolTest {

    private static final String FRIED_RICE = "{\"action\":\"save\",\"title\":\"Egg Fried Rice\","
            + "\"cook_time_minutes\":20,\"portions\":2,"
            + "\"ingredients\":[{\"name\":\"rice\",\"quantity\":2,\"unit\":\"cup\"},{\"name\":\"eggs\",\"quantity\":2}],"
            + "\"steps\":[\"Scramble the eggs\",\"Fry the rice with the eggs\"]}";

    private final RecipeHistory history = new RecipeHistory();
    private final UserProfile profile = new UserProfile();
    private final RecipeTool tool = new RecipeTool(history, profile);

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void savesARecipeTheAgentSuggested() {
        JsonObject o = json(tool.execute(FRIED_RICE));
        assertTrue(o.get("saved").getAsBoolean());
        assertEquals(1, history.getAll().size());
        Recipe r = history.getAll().get(0);
        assertEquals("Egg Fried Rice", r.getTitle());
        assertEquals(20, r.getCookTimeMinutes());
        assertEquals(2, r.getPortions());
        assertEquals(List.of("rice", "eggs"), r.getIngredients().stream().map(Ingredient::getName).toList());
        assertEquals("cup", r.getIngredients().get(0).getUnit());
        assertEquals(2, r.getSteps().size());
        assertEquals(0, r.getTimesCooked());
    }

    @Test
    void refusesARecipeWithAnAllergen() {
        profile.setAllergies(List.of("eggs"));
        JsonObject o = json(tool.execute(FRIED_RICE));
        assertTrue(o.get("error").getAsString().contains("allergic to eggs"), o.toString());
        assertTrue(history.getAll().isEmpty());
    }

    @Test
    void refusesTheSameTitleTwice() {
        tool.execute(FRIED_RICE);
        JsonObject o = json(tool.execute(FRIED_RICE.replace("Egg Fried Rice", "egg fried rice")));
        assertTrue(o.get("error").getAsString().contains("already saved"));
        assertEquals(1, history.getAll().size());
    }

    @Test
    void refusesAnIncompleteRecipe() {
        assertTrue(json(tool.execute("{\"action\":\"save\",\"title\":\"Toast\"}")).has("error"));
        assertTrue(json(tool.execute(FRIED_RICE.replace("\"steps\":[\"Scramble the eggs\",\"Fry the rice with the eggs\"]",
                "\"steps\":[]"))).get("error").getAsString().contains("no steps"));
        assertTrue(json(tool.execute(FRIED_RICE.replace("\"portions\":2", "\"portions\":0"))).has("error"));
        assertTrue(history.getAll().isEmpty());
    }

    @Test
    void listsSavedRecipes() {
        assertEquals(0, json(tool.execute("list")).get("total").getAsInt());
        tool.execute(FRIED_RICE);
        history.rate(history.getAll().get(0).getId(), 4);
        JsonObject o = json(tool.execute("{\"action\":\"list\"}"));
        assertEquals(1, o.get("total").getAsInt());
        JsonObject first = o.getAsJsonArray("recipes").get(0).getAsJsonObject();
        assertEquals("Egg Fried Rice", first.get("title").getAsString());
        assertEquals(4, first.get("rating").getAsInt());
        assertTrue(json(tool.execute("{\"action\":\"cook\"}")).has("error"));
    }
}
