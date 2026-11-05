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
        assertTrue(json(tool.execute("{\"action\":\"delete\"}")).has("error"));
    }

    @Test
    void profileSaveChangesOnlyGivenFields() {
        UserProfile p = new UserProfile();
        p.addDislike("olives");
        ProfileTool tool = new ProfileTool(p);
        JsonObject o = json(tool.execute("{\"action\":\"save\",\"diet\":\"Vegetarian\","
                + "\"allergies\":[\"Peanuts\",\" \"],\"default_portions\":3}"));
        assertTrue(o.get("saved").getAsBoolean());
        assertEquals("vegetarian", p.getDiet());
        assertEquals(java.util.List.of("peanuts"), p.getAllergies());
        assertEquals(java.util.List.of("olives"), p.getDislikes());   // not given, so unchanged
        assertEquals(3, p.getDefaultPortions());
        tool.execute("{\"action\":\"save\",\"allergies\":\"shellfish\"}");
        assertEquals(java.util.List.of("shellfish"), p.getAllergies());
    }

    @Test
    void profileSaveRejectsBadPortionsWithoutChangingAnything() {
        UserProfile p = new UserProfile();
        ProfileTool tool = new ProfileTool(p);
        JsonObject o = json(tool.execute("{\"action\":\"save\",\"diet\":\"vegan\",\"default_portions\":0}"));
        assertTrue(o.has("error"));
        assertEquals(UserProfile.NO_DIET, p.getDiet());
        assertEquals(UserProfile.DEFAULT_PORTIONS, p.getDefaultPortions());
    }

    @Test
    void cartToolAddsListsAndRemoves() {
        com.recipeagent.cart.ShoppingCart cart = new com.recipeagent.cart.ShoppingCart();
        com.recipeagent.agent.tools.CartTool tool = new com.recipeagent.agent.tools.CartTool(cart);

        JsonObject added = json(tool.execute("{\"action\":\"add\",\"name\":\"eggs\",\"quantity\":12}"));
        assertEquals("eggs", added.get("added").getAsString());
        assertEquals(12, added.get("quantity_in_cart").getAsInt());
        json(tool.execute("{\"action\":\"add\",\"name\":\"Eggs\"}"));
        assertEquals(13, cart.find("eggs").orElseThrow().getQuantity());

        JsonObject list = json(tool.execute("list"));
        assertEquals(1, list.getAsJsonArray("items").size());
        assertFalse(list.getAsJsonArray("items").get(0).getAsJsonObject().get("on_sale").getAsBoolean());

        assertTrue(json(tool.execute("{\"action\":\"remove\",\"name\":\"EGGS\"}")).get("removed").getAsBoolean());
        assertFalse(json(tool.execute("{\"action\":\"remove\",\"name\":\"eggs\"}")).get("removed").getAsBoolean());
        assertTrue(cart.isEmpty());
    }

    @Test
    void cartToolAddsWithUnitAndMarksItAsFromChat() {
        com.recipeagent.cart.ShoppingCart cart = new com.recipeagent.cart.ShoppingCart();
        com.recipeagent.agent.tools.CartTool tool = new com.recipeagent.agent.tools.CartTool(cart);
        JsonObject added = json(tool.execute("{\"action\":\"add\",\"name\":\"milk\",\"quantity\":2,\"unit\":\"L\"}"));
        assertEquals("L", added.get("unit").getAsString());
        com.recipeagent.model.CartItem milk = cart.find("milk").orElseThrow();
        assertEquals("L", milk.getUnit());
        assertEquals(com.recipeagent.model.CartSource.CHAT, milk.getSource());
        JsonObject item = json(tool.execute("list")).getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals("L", item.get("unit").getAsString());
        assertTrue(tool.getInputSchema().contains("\"unit\""));
    }

    @Test
    void cartToolReportsBadInputAsError() {
        com.recipeagent.agent.tools.CartTool tool =
                new com.recipeagent.agent.tools.CartTool(new com.recipeagent.cart.ShoppingCart());
        assertTrue(json(tool.execute("{\"action\":\"add\"}")).has("error"));
        assertTrue(json(tool.execute("{\"action\":\"add\",\"name\":\"milk\",\"quantity\":-2}")).has("error"));
        assertTrue(json(tool.execute("{\"action\":\"remove\"}")).has("error"));
        assertTrue(json(tool.execute("{\"action\":\"buy\"}")).has("error"));
        assertTrue(JsonParser.parseString(tool.getInputSchema()).isJsonObject());
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
