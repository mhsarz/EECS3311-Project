package com.recipeagent.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.recipeagent.agent.AgentTool;
import com.recipeagent.agent.ToolArgs;
import com.recipeagent.flyer.FlyerService;
import com.recipeagent.model.Flyer;
import com.recipeagent.model.FlyerItem;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Lets the agent look at this week's flyers in the chat (F09, F10), e.g. "What's on sale
 * at No Frills?" Read only: flyers are refreshed or uploaded in the Flyers tab.
 * <ul>
 *   <li>{"action":"list"}: current sale items of every store</li>
 *   <li>{"action":"list","store":"No Frills","search":"chicken"}: only that store and/or
 *       items whose name contains the search word</li>
 * </ul>
 */
public class FlyerTool implements AgentTool {

    public static final String NAME = "flyers";
    /** Most sale items returned in one call, so the answer stays short. */
    static final int MAX_ITEMS = 60;

    private final FlyerService flyers;
    private final Clock clock;

    public FlyerTool(FlyerService flyers, Clock clock) {
        if (flyers == null || clock == null) {
            throw new IllegalArgumentException("Flyer service and clock are required");
        }
        this.flyers = flyers;
        this.clock = clock;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Read this week's store flyers (sale items with prices). Use action 'list', with an "
                + "optional 'store' and an optional 'search' word. Read only.";
    }

    @Override
    public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"action\":{\"type\":\"string\",\"enum\":[\"list\"]},"
                + "\"store\":{\"type\":\"string\"},"
                + "\"search\":{\"type\":\"string\"}},"
                + "\"required\":[\"action\"]}";
    }

    @Override
    public String execute(String args) {
        ToolArgs a = ToolArgs.parse(args);
        String action = a.action("list");
        if (!action.equals("list")) {
            return PantryTool.error("unknown action '" + action + "' (use list)");
        }
        String store = a.getString("store", null);
        String search = a.getString("search", null);
        String word = search == null ? null : search.trim().toLowerCase(Locale.ROOT);
        JsonArray result = new JsonArray();
        int count = 0;
        boolean cut = false;
        LocalDate today = LocalDate.now(clock);
        for (Flyer f : flyers.getCurrentFlyers(today)) {
            if (store != null && !f.getStore().equalsIgnoreCase(store.trim())) {
                continue;
            }
            JsonArray items = new JsonArray();
            for (FlyerItem i : f.getItems()) {
                if (word != null && !i.getName().toLowerCase(Locale.ROOT).contains(word)) {
                    continue;
                }
                if (count == MAX_ITEMS) {
                    cut = true;
                    break;
                }
                JsonObject o = new JsonObject();
                o.addProperty("name", i.getName());
                o.addProperty("price", i.getSalePrice());
                items.add(o);
                count++;
            }
            JsonObject fo = new JsonObject();
            fo.addProperty("store", f.getStore());
            fo.addProperty("ends", f.getEndDate() == null ? "unknown" : f.getEndDate().toString());
            fo.add("items", items);
            result.add(fo);
        }
        JsonObject o = new JsonObject();
        o.add("flyers", result);
        if (result.isEmpty()) {
            o.addProperty("note", "No current flyers" + (store == null ? "" : " for " + store)
                    + ". The user can refresh or upload flyers in the Flyers tab.");
        } else if (cut) {
            o.addProperty("note", "Only the first " + MAX_ITEMS + " items are shown; use 'search' to narrow it.");
        }
        return o.toString();
    }
}
