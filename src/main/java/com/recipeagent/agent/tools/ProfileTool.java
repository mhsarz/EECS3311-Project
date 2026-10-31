package com.recipeagent.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.recipeagent.agent.AgentTool;
import com.recipeagent.agent.ToolArgs;
import com.recipeagent.model.UserProfile;

/**
 * Lets the agent read the user's preference profile (F01, F07, F09).
 * Action {"action":"get"} returns diet, allergies, dislikes and default portions as JSON.
 * Saving the profile from the questionnaire (F04) is added with the chat loop.
 */
public class ProfileTool implements AgentTool {

    public static final String NAME = "profile";

    private final UserProfile profile;

    /** Uses the same UserProfile object the rest of the app uses. */
    public ProfileTool(UserProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("Profile is required");
        }
        this.profile = profile;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Read the user's food preferences: diet, allergies, dislikes and default portions. "
                + "Use action 'get'.";
    }

    @Override
    public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"action\":{\"type\":\"string\",\"enum\":[\"get\"]}},"
                + "\"required\":[\"action\"]}";
    }

    @Override
    public String execute(String args) {
        String action = ToolArgs.parse(args).action("get");
        if (!action.equals("get")) {
            return PantryTool.error("unknown action '" + action + "' (use get)");
        }
        JsonObject o = new JsonObject();
        o.addProperty("diet", profile.getDiet());
        o.add("allergies", toArray(profile.getAllergies()));
        o.add("dislikes", toArray(profile.getDislikes()));
        o.addProperty("default_portions", profile.getDefaultPortions());
        return o.toString();
    }

    private static JsonArray toArray(java.util.List<String> values) {
        JsonArray arr = new JsonArray();
        for (String v : values) {
            arr.add(v);
        }
        return arr;
    }
}
