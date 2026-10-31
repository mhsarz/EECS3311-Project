package com.recipeagent.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.recipeagent.agent.AgentTool;
import com.recipeagent.agent.ToolArgs;
import com.recipeagent.model.PantryItem;
import com.recipeagent.pantry.PantryManager;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Lets the agent read the pantry (F01, F05, F07, F09). Read-only: changing stock is done
 * by deterministic code (F03, F06), never directly by the LLM.
 * <p>
 * Actions (JSON args):
 * <ul>
 *   <li>{"action":"list"}: every item with quantity, unit, location and days left</li>
 *   <li>{"action":"expiring","days":3}: items that expire within that many days</li>
 *   <li>{"action":"has","name":"rice"}: whether an item with that exact name is in stock</li>
 * </ul>
 * Results are compact JSON, so the agent can also check them in code.
 */
public class PantryTool implements AgentTool {

    public static final String NAME = "pantry";

    private final PantryManager pantry;
    private final Clock clock;

    public PantryTool(PantryManager pantry) {
        this(pantry, Clock.systemDefaultZone());
    }

    public PantryTool(PantryManager pantry, Clock clock) {
        if (pantry == null || clock == null) {
            throw new IllegalArgumentException("Pantry and clock are required");
        }
        this.pantry = pantry;
        this.clock = clock;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Read the user's pantry, fridge and freezer stock. Use action 'list' for all items, "
                + "'expiring' with 'days' for items close to their best-before date, or 'has' with "
                + "'name' to check one item.";
    }

    @Override
    public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"action\":{\"type\":\"string\",\"enum\":[\"list\",\"expiring\",\"has\"]},"
                + "\"days\":{\"type\":\"integer\",\"minimum\":0},"
                + "\"name\":{\"type\":\"string\"}},"
                + "\"required\":[\"action\"]}";
    }

    @Override
    public String execute(String args) {
        ToolArgs a = ToolArgs.parse(args);
        LocalDate today = LocalDate.now(clock);
        switch (a.action("list")) {
            case "list":
                return itemsJson(pantry.getAll(), today).toString();
            case "expiring":
                int days = Math.max(0, a.getInt("days", 3));
                return itemsJson(pantry.getExpiringItems(days, today), today).toString();
            case "has":
                String name = a.getString("name", null);
                if (name == null) {
                    return error("'has' needs a 'name'");
                }
                JsonObject has = new JsonObject();
                has.addProperty("name", name);
                has.addProperty("in_pantry", !pantry.findByName(name).isEmpty());
                return has.toString();
            default:
                return error("unknown action '" + a.action("") + "' (use list, expiring or has)");
        }
    }

    private static JsonObject itemsJson(List<PantryItem> items, LocalDate today) {
        JsonArray arr = new JsonArray();
        for (PantryItem i : items) {
            JsonObject o = new JsonObject();
            o.addProperty("name", i.getName());
            o.addProperty("quantity", i.getQuantity());
            o.addProperty("unit", i.getUnit());
            o.addProperty("location", i.getLocation().name().toLowerCase(java.util.Locale.ROOT));
            if (i.hasBestBefore()) {
                o.addProperty("best_before", i.getBestBefore().toString());
                o.addProperty("days_left", i.daysUntilExpiry(today));
            }
            arr.add(o);
        }
        JsonObject result = new JsonObject();
        result.add("items", arr);
        return result;
    }

    static String error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o.toString();
    }
}
