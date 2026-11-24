package com.recipeagent.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.recipeagent.agent.AgentTool;
import com.recipeagent.agent.ToolArgs;
import com.recipeagent.model.PantryItem;
import com.recipeagent.model.StorageLocation;
import com.recipeagent.pantry.PantryManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Lets the agent read the pantry (F01, F05, F07, F09) and, when the user asks, change it (F03).
 * <p>
 * Actions (JSON args):
 * <ul>
 *   <li>{"action":"list"}: every item with quantity, unit, location and days left</li>
 *   <li>{"action":"expiring","days":3}: items that expire within that many days</li>
 *   <li>{"action":"has","name":"rice"}: whether an item with that name is in stock</li>
 *   <li>{"action":"add","name":"milk","quantity":2,"unit":"L","location":"fridge","expires_in_days":7}:
 *       adds an item, or adds the quantity to an item with the same name</li>
 *   <li>{"action":"update","name":"milk","quantity":1,"best_before":"2026-12-01"}: changes the
 *       fields given ("new_name", "quantity", "unit", "location", "best_before" or "none")</li>
 *   <li>{"action":"remove","name":"milk"}</li>
 * </ul>
 * Changes go through {@link KitchenActions} (the controller), so they are checked, saved and raise
 * the same expiry alerts as the GUI and CLI. Without actions the tool can only read.
 * Results are compact JSON, so the agent can also check them in code.
 */
public class PantryTool implements AgentTool {

    public static final String NAME = "pantry";
    static final int MAX_NAME = 60;

    private final PantryManager pantry;
    private final Clock clock;
    /** How changes are made (through the controller); null = the tool can only read. */
    private final KitchenActions actions;

    public PantryTool(PantryManager pantry) {
        this(pantry, Clock.systemDefaultZone());
    }

    public PantryTool(PantryManager pantry, Clock clock) {
        this(pantry, clock, null);
    }

    /** A pantry tool that can also add, update and remove items through {@code actions}. */
    public PantryTool(PantryManager pantry, Clock clock, KitchenActions actions) {
        if (pantry == null || clock == null) {
            throw new IllegalArgumentException("Pantry and clock are required");
        }
        this.pantry = pantry;
        this.clock = clock;
        this.actions = actions;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        String read = "The user's pantry, fridge and freezer stock. Use action 'list' for all items, "
                + "'expiring' with 'days' for items close to their best-before date, or 'has' with "
                + "'name' to check one item.";
        return actions == null ? read : read + " Only when the user asks: 'add' an item (name, quantity, unit, "
                + "location pantry/fridge/freezer, best_before YYYY-MM-DD or expires_in_days; an item already "
                + "there gets the quantity added), 'update' one (name plus the fields to change: new_name, "
                + "quantity, unit, location, best_before or \"none\"), or 'remove' one by name.";
    }

    @Override
    public String getInputSchema() {
        String actionList = actions == null ? "\"list\",\"expiring\",\"has\""
                : "\"list\",\"expiring\",\"has\",\"add\",\"update\",\"remove\"";
        String write = actions == null ? "" : ","
                + "\"quantity\":{\"type\":\"number\"},"
                + "\"unit\":{\"type\":\"string\"},"
                + "\"location\":{\"type\":\"string\",\"enum\":[\"pantry\",\"fridge\",\"freezer\"]},"
                + "\"best_before\":{\"type\":\"string\",\"description\":\"YYYY-MM-DD, or none\"},"
                + "\"expires_in_days\":{\"type\":\"integer\",\"minimum\":0},"
                + "\"new_name\":{\"type\":\"string\"}";
        return "{\"type\":\"object\",\"properties\":{"
                + "\"action\":{\"type\":\"string\",\"enum\":[" + actionList + "]},"
                + "\"days\":{\"type\":\"integer\",\"minimum\":0},"
                + "\"name\":{\"type\":\"string\"}" + write + "},"
                + "\"required\":[\"action\"]}";
    }

    @Override
    public String execute(String args) {
        ToolArgs a = ToolArgs.parse(args);
        LocalDate today = LocalDate.now(clock);
        String action = a.action("list");
        switch (action) {
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
            case "add":
            case "update":
            case "remove":
                if (actions == null) {
                    return error("this pantry tool can only read");
                }
                try {
                    return action.equals("add") ? add(a, today)
                            : action.equals("update") ? update(a, today) : remove(a);
                } catch (IllegalArgumentException e) {
                    return error(e.getMessage());
                }
            default:
                return error("unknown action '" + action + "' (use list, expiring, has"
                        + (actions == null ? ")" : ", add, update or remove)"));
        }
    }

    private String add(ToolArgs a, LocalDate today) {
        String name = name(a.getString("name", null));
        double qty = a.getDouble("quantity", 1);
        String unit = a.getString("unit", "");
        StorageLocation location = StorageLocation.parse(a.getString("location", "pantry"));
        LocalDate bestBefore = date(a, today, null);
        boolean existed = !pantry.findByName(name).isEmpty();
        actions.addPantryItem(new PantryItem(name, qty, unit, location, bestBefore), true);
        JsonObject o = itemJson(pantry.findByName(name).get(0), today);
        o.addProperty(existed ? "merged" : "added", true);
        o.addProperty("change", "Pantry updated");
        return o.toString();
    }

    private String update(ToolArgs a, LocalDate today) {
        PantryItem item = find(a.getString("name", null));
        String newName = a.has("new_name") ? name(a.getString("new_name", null)) : item.getName();
        double qty = a.has("quantity") ? a.getDouble("quantity", -1) : item.getQuantity();
        String unit = a.has("unit") ? a.getString("unit", "") : item.getUnit();
        StorageLocation location = a.has("location") ? StorageLocation.parse(a.getString("location", null))
                : item.getLocation();
        LocalDate bestBefore = date(a, today, item.getBestBefore());
        PantryItem updated = actions.updatePantryItem(item.getId(), newName, qty, unit, location, bestBefore);
        JsonObject o = itemJson(updated, today);
        o.addProperty("updated", true);
        o.addProperty("change", "Pantry updated");
        return o.toString();
    }

    private String remove(ToolArgs a) {
        PantryItem item = find(a.getString("name", null));
        actions.removePantryItemByName(item.getName());
        JsonObject o = new JsonObject();
        o.addProperty("removed", item.getName());
        o.addProperty("change", "Pantry updated");
        return o.toString();
    }

    private PantryItem find(String name) {
        if (name == null) {
            throw new IllegalArgumentException("which item? give its 'name'");
        }
        List<PantryItem> found = pantry.findByName(name);
        if (found.isEmpty()) {
            throw new IllegalArgumentException("'" + name + "' is not in the pantry");
        }
        return found.get(0);
    }

    private static String name(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("the item needs a 'name'");
        }
        if (name.trim().length() > MAX_NAME) {
            throw new IllegalArgumentException("the name is longer than " + MAX_NAME + " characters");
        }
        return name.trim();
    }

    /** best_before (YYYY-MM-DD or "none") or expires_in_days; {@code fallback} if neither is given. */
    private static LocalDate date(ToolArgs a, LocalDate today, LocalDate fallback) {
        if (a.has("expires_in_days")) {
            int days = a.getInt("expires_in_days", -1);
            if (days < 0) {
                throw new IllegalArgumentException("expires_in_days must be 0 or more");
            }
            return today.plusDays(days);
        }
        if (!a.has("best_before")) {
            return fallback;
        }
        String text = a.getString("best_before", "none");
        if (text.equalsIgnoreCase("none") || text.equalsIgnoreCase("null")) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("best_before must be a date like " + today.plusDays(7));
        }
    }

    private static JsonObject itemsJson(List<PantryItem> items, LocalDate today) {
        JsonArray arr = new JsonArray();
        for (PantryItem i : items) {
            arr.add(itemJson(i, today));
        }
        JsonObject result = new JsonObject();
        result.add("items", arr);
        return result;
    }

    private static JsonObject itemJson(PantryItem i, LocalDate today) {
        JsonObject o = new JsonObject();
        o.addProperty("name", i.getName());
        o.addProperty("quantity", i.getQuantity());
        o.addProperty("unit", i.getUnit());
        o.addProperty("location", i.getLocation().name().toLowerCase(Locale.ROOT));
        if (i.hasBestBefore()) {
            o.addProperty("best_before", i.getBestBefore().toString());
            o.addProperty("days_left", i.daysUntilExpiry(today));
        }
        return o;
    }

    static String error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o.toString();
    }
}
