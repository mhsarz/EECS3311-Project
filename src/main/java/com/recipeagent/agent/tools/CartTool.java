package com.recipeagent.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.recipeagent.agent.AgentTool;
import com.recipeagent.agent.ToolArgs;
import com.recipeagent.cart.ShoppingCart;
import com.recipeagent.model.CartItem;

/**
 * Lets the agent read and change the shopping cart from the chat (F09), e.g.
 * "Add eggs to my cart".
 * <p>
 * Actions (JSON args):
 * <ul>
 *   <li>{"action":"list"}: every item with quantity and whether it is on sale</li>
 *   <li>{"action":"add","name":"eggs","quantity":12}: adds the item (quantity defaults to 1)</li>
 *   <li>{"action":"remove","name":"eggs"}: removes the item</li>
 * </ul>
 */
public class CartTool implements AgentTool {

    public static final String NAME = "cart";

    private final ShoppingCart cart;

    public CartTool(ShoppingCart cart) {
        if (cart == null) {
            throw new IllegalArgumentException("Cart is required");
        }
        this.cart = cart;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Read or change the user's shopping cart. Use action 'list' to see it, 'add' with "
                + "'name' and optional 'quantity' to add an item, or 'remove' with 'name'. "
                + "Only add or remove items when the user asks.";
    }

    @Override
    public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"action\":{\"type\":\"string\",\"enum\":[\"list\",\"add\",\"remove\"]},"
                + "\"name\":{\"type\":\"string\"},"
                + "\"quantity\":{\"type\":\"number\",\"exclusiveMinimum\":0}},"
                + "\"required\":[\"action\"]}";
    }

    @Override
    public String execute(String args) {
        ToolArgs a = ToolArgs.parse(args);
        String action = a.action("list");
        switch (action) {
            case "list":
                return listJson().toString();
            case "add": {
                String name = a.getString("name", null);
                if (name == null) {
                    return PantryTool.error("'add' needs a 'name'");
                }
                double qty = a.getDouble("quantity", 1);
                CartItem added;
                try {
                    added = cart.add(new CartItem(name, qty));
                } catch (IllegalArgumentException e) {
                    return PantryTool.error(e.getMessage());
                }
                JsonObject o = new JsonObject();
                o.addProperty("added", added.getName());
                o.addProperty("quantity_in_cart", added.getQuantity());
                return o.toString();
            }
            case "remove": {
                String name = a.getString("name", null);
                if (name == null) {
                    return PantryTool.error("'remove' needs a 'name'");
                }
                JsonObject o = new JsonObject();
                o.addProperty("name", name);
                o.addProperty("removed", cart.remove(name));
                return o.toString();
            }
            default:
                return PantryTool.error("unknown action '" + action + "' (use list, add or remove)");
        }
    }

    private JsonObject listJson() {
        JsonArray arr = new JsonArray();
        for (CartItem i : cart.getItems()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", i.getName());
            o.addProperty("quantity", i.getQuantity());
            o.addProperty("on_sale", i.isOnSale());
            arr.add(o);
        }
        JsonObject result = new JsonObject();
        result.add("items", arr);
        return result;
    }
}
