package com.recipeagent.agent;

/**
 * One tool call the agent made while answering a chat message (F09), e.g.
 * tool "cart", args {"action":"add","name":"eggs"}, result {"added":"eggs",...}.
 * The interface can show these as "actions taken", and AgentController uses them to
 * know what to save (for example the cart or the profile).
 */
public record AgentAction(String tool, String args, String result) {

    /** True if this call used the given tool and the result was not an error. */
    public boolean succeededWith(String toolName) {
        return tool.equals(toolName) && !result.contains("\"error\"");
    }

    /**
     * What this call changed, for the note under the agent's answer ("Pantry updated",
     * "Marked as cooked"), or null if it only read something. Tools put it in a "change" field.
     */
    public String change() {
        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(result);
            if (el.isJsonObject() && el.getAsJsonObject().has("change")
                    && el.getAsJsonObject().get("change").isJsonPrimitive()) {
                return el.getAsJsonObject().get("change").getAsString();
            }
        } catch (RuntimeException e) {
            // not JSON: nothing changed that we know of
        }
        return null;
    }
}
