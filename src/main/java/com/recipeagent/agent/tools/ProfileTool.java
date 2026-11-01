package com.recipeagent.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.recipeagent.agent.AgentTool;
import com.recipeagent.agent.ToolArgs;
import com.recipeagent.model.UserProfile;

import java.util.List;

/**
 * Lets the agent read and save the user's preference profile (F01, F04, F07, F09).
 * <ul>
 *   <li>{"action":"get"}: diet, allergies, dislikes and default portions</li>
 *   <li>{"action":"save","diet":"vegetarian","allergies":["peanuts"],"dislikes":[],
 *       "default_portions":2}: used at the end of the profile questionnaire (F04).
 *       Only the fields given are changed.</li>
 * </ul>
 * The tool changes the same UserProfile object the rest of the app uses; saving it to
 * disk is done by the caller.
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
        return "Read or save the user's food preferences: diet, allergies, dislikes and default "
                + "portions. Use action 'get' to read. Use action 'save' only at the end of the "
                + "profile set-up questions, with the fields the user told you.";
    }

    @Override
    public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"action\":{\"type\":\"string\",\"enum\":[\"get\",\"save\"]},"
                + "\"diet\":{\"type\":\"string\"},"
                + "\"allergies\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"dislikes\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"default_portions\":{\"type\":\"integer\",\"minimum\":1}},"
                + "\"required\":[\"action\"]}";
    }

    @Override
    public String execute(String args) {
        ToolArgs a = ToolArgs.parse(args);
        String action = a.action("get");
        switch (action) {
            case "get":
                return profileJson().toString();
            case "save":
                return save(a);
            default:
                return PantryTool.error("unknown action '" + action + "' (use get or save)");
        }
    }

    private String save(ToolArgs a) {
        // Check everything first, so a bad value does not leave the profile half-changed.
        int portions = profile.getDefaultPortions();
        if (a.has("default_portions")) {
            portions = a.getInt("default_portions", -1);
            if (portions <= 0) {
                return PantryTool.error("default_portions must be a whole number above zero");
            }
        }
        if (a.has("diet")) {
            profile.setDiet(a.getString("diet", UserProfile.NO_DIET));
        }
        List<String> allergies = a.getStringList("allergies");
        if (allergies != null) {
            profile.setAllergies(allergies);
        }
        List<String> dislikes = a.getStringList("dislikes");
        if (dislikes != null) {
            profile.setDislikes(dislikes);
        }
        profile.setDefaultPortions(portions);
        JsonObject o = profileJson();
        o.addProperty("saved", true);
        return o.toString();
    }

    private JsonObject profileJson() {
        JsonObject o = new JsonObject();
        o.addProperty("diet", profile.getDiet());
        o.add("allergies", toArray(profile.getAllergies()));
        o.add("dislikes", toArray(profile.getDislikes()));
        o.addProperty("default_portions", profile.getDefaultPortions());
        return o;
    }

    private static JsonArray toArray(List<String> values) {
        JsonArray arr = new JsonArray();
        for (String v : values) {
            arr.add(v);
        }
        return arr;
    }
}
