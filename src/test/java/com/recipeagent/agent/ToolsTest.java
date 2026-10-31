package com.recipeagent.agent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.recipeagent.agent.tools.PantryTool;
import com.recipeagent.agent.tools.ProfileTool;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.StorageLocation;
import com.recipeagent.model.UserProfile;
import com.recipeagent.pantry.PantryManager;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ToolsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);

    private PantryTool pantryTool() {
        PantryManager pm = new PantryManager();
        pm.addItem(new PantryItem("rice", 2, "kg", StorageLocation.PANTRY, null));
        pm.addItem(new PantryItem("milk", 1, "L", StorageLocation.FRIDGE, LocalDate.of(2026, 11, 2)));
        return new PantryTool(pm, CLOCK);
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void pantryListGivesItemsWithDaysLeft() {
        JsonObject o = json(pantryTool().execute("{\"action\":\"list\"}"));
        assertEquals(2, o.getAsJsonArray("items").size());
        JsonObject milk = o.getAsJsonArray("items").get(1).getAsJsonObject();
        assertEquals("milk", milk.get("name").getAsString());
        assertEquals("fridge", milk.get("location").getAsString());
        assertEquals(2, milk.get("days_left").getAsInt());
        assertFalse(o.getAsJsonArray("items").get(0).getAsJsonObject().has("days_left"));
    }

    @Test
    void pantryAcceptsPlainActionWord() {
        assertEquals(2, json(pantryTool().execute("list")).getAsJsonArray("items").size());
        assertEquals(2, json(pantryTool().execute("")).getAsJsonArray("items").size());
    }

    @Test
    void pantryExpiringUsesDays() {
        assertEquals(1, json(pantryTool().execute("{\"action\":\"expiring\",\"days\":3}")).getAsJsonArray("items").size());
        assertEquals(0, json(pantryTool().execute("{\"action\":\"expiring\",\"days\":1}")).getAsJsonArray("items").size());
    }

    @Test
    void pantryHasChecksExactName() {
        assertTrue(json(pantryTool().execute("{\"action\":\"has\",\"name\":\"Milk\"}")).get("in_pantry").getAsBoolean());
        assertFalse(json(pantryTool().execute("{\"action\":\"has\",\"name\":\"oat milk\"}")).get("in_pantry").getAsBoolean());
        assertTrue(json(pantryTool().execute("{\"action\":\"has\"}")).has("error"));
    }

    @Test
    void unknownActionReturnsErrorInsteadOfThrowing() {
        JsonObject o = json(pantryTool().execute("{\"action\":\"delete\"}"));
        assertTrue(o.get("error").getAsString().contains("unknown action 'delete'"));
    }

    @Test
    void profileGetReturnsPreferences() {
        UserProfile p = new UserProfile();
        p.setDiet("vegan");
        p.addAllergy("soy");
        p.addDislike("olives");
        ProfileTool tool = new ProfileTool(p);
        JsonObject o = json(tool.execute("get"));
        assertEquals("vegan", o.get("diet").getAsString());
        assertEquals("soy", o.getAsJsonArray("allergies").get(0).getAsString());
        assertEquals("olives", o.getAsJsonArray("dislikes").get(0).getAsString());
        assertEquals(2, o.get("default_portions").getAsInt());
        assertTrue(json(tool.execute("{\"action\":\"save\"}")).has("error"));
    }

    @Test
    void toolsDescribeThemselvesForTheLLM() {
        assertEquals("pantry", pantryTool().getName());
        assertTrue(JsonParser.parseString(pantryTool().getInputSchema()).isJsonObject());
        ProfileTool profileTool = new ProfileTool(new UserProfile());
        assertEquals("profile", profileTool.getName());
        assertFalse(profileTool.getDescription().isBlank());
        assertThrows(IllegalArgumentException.class, () -> new ProfileTool(null));
        assertThrows(IllegalArgumentException.class, () -> new PantryTool(null));
    }

    @Test
    void toolArgsReadValuesSafely() {
        ToolArgs a = ToolArgs.parse("{\"action\":\"LIST\",\"days\":\"x\",\"name\":\" \"}");
        assertEquals("list", a.action("none"));
        assertEquals(3, a.getInt("days", 3));
        assertEquals("fallback", a.getString("name", "fallback"));
        assertEquals("{broken", ToolArgs.parse("{broken").getString("action", null));
    }
}
