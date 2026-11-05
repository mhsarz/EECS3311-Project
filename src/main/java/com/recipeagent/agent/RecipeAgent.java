package com.recipeagent.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.recipeagent.agent.tools.PantryTool;
import com.recipeagent.agent.tools.ProfileTool;
import com.recipeagent.llm.LLMClient;
import com.recipeagent.llm.LLMException;
import com.recipeagent.llm.LLMResponse;
import com.recipeagent.llm.LLMUnavailableException;
import com.recipeagent.model.Ingredient;
import com.recipeagent.model.Recipe;
import com.recipeagent.model.RecipeRequest;
import com.recipeagent.model.Substitution;
import com.recipeagent.prompt.Prompt;
import com.recipeagent.prompt.PromptBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The AI agent. It gathers context through its tools, builds prompts, calls the LLM,
 * and checks every reply in code before it reaches the user.
 * <p>
 * Builder pattern: this is the <b>Director</b>. It holds a {@link PromptBuilder}
 * (aggregation) and, for each kind of prompt, calls only the building steps it needs.
 * <p>
 * Agent behaviour: the LLM's answer is not trusted blindly. A recipe that is too long,
 * has the wrong number of portions, misses a required item or contains an allergen is
 * rejected, and the agent asks the LLM again once, telling it what was wrong.
 * Substitutes that contain allergens are dropped, and "in stock" is checked against the
 * real pantry instead of taking the LLM's word for it.
 * <p>
 * In chat (F09) the agent runs a tool-use loop: the LLM plans which tools to call, the
 * agent runs them and sends the results back, up to a limit per message, and the
 * conversation is remembered for the session.
 * <p>
 * The public methods are synchronized: the GUI runs LLM calls in background threads, and
 * the agent shares one prompt builder and one conversation, so two requests at the same
 * time (e.g. Generate Recipe while a chat answer is coming) must not mix their prompts.
 */
public class RecipeAgent {

    /** How many times one request is tried in total (first try plus one corrected retry). */
    static final int MAX_ATTEMPTS = 2;
    static final int MAX_SUBSTITUTES = 3;

    static final String RECIPE_FORMAT = "{\"title\": string, \"cook_time_minutes\": integer, "
            + "\"portions\": integer, \"ingredients\": [{\"name\": string, \"quantity\": number, "
            + "\"unit\": string}], \"steps\": [string]}";

    static final String SUBSTITUTE_FORMAT = "{\"substitutes\": [{\"substitute\": string, "
            + "\"note\": string, \"pantry_items\": [string]}]}";

    /**
     * Basics a recipe may use without them being in the pantry. The recipe prompt tells the
     * LLM so, and the missing-ingredient check (F11) does not count them as missing.
     */
    public static final List<String> BASIC_STAPLES = List.of("salt", "pepper", "black pepper",
            "cooking oil", "oil", "water");

    /** True if the ingredient is one of the {@link #BASIC_STAPLES} (ignoring case). */
    public static boolean isBasicStaple(String ingredient) {
        return ingredient != null && BASIC_STAPLES.contains(ingredient.trim().toLowerCase(Locale.ROOT));
    }

    /** Most tool calls the agent may make while answering one chat message (F09). */
    public static final int DEFAULT_MAX_TOOL_CALLS = 5;
    /** How many earlier chat lines are included in the prompt. */
    static final int HISTORY_IN_PROMPT = 30;
    /**
     * Tool results from earlier messages are shortened to this length in the prompt.
     * Results from the message being answered are always sent in full.
     */
    static final int MAX_OLD_RESULT_IN_PROMPT = 300;
    /** The conversation memory keeps at most this many lines (oldest are dropped). */
    static final int MAX_HISTORY_LINES = 200;
    static final String TOOL_LINE_PREFIX = "Tool ";

    /** Message the interface sends when the user clicks Help Me Set Up (F04). */
    public static final String PROFILE_SETUP_MESSAGE =
            "Please help me set up my food preference profile.";

    private final LLMClient llm;
    private final PromptBuilder builder;
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private final List<String> chatHistory = new ArrayList<>();
    private final int maxToolCalls;
    private List<AgentAction> lastActions = List.of();

    /**
     * @param llm     the LLM client (normally an LLMClientProxy)
     * @param builder how prompts are written (normally a ClaudePromptBuilder)
     * @param tools   the tools the agent can use; must include "pantry" and "profile"
     */
    public RecipeAgent(LLMClient llm, PromptBuilder builder, List<AgentTool> tools) {
        this(llm, builder, tools, DEFAULT_MAX_TOOL_CALLS);
    }

    /** Same as above with a different limit on tool calls per chat message. */
    public RecipeAgent(LLMClient llm, PromptBuilder builder, List<AgentTool> tools, int maxToolCalls) {
        if (llm == null || builder == null || tools == null) {
            throw new IllegalArgumentException("LLM client, prompt builder and tools are required");
        }
        if (maxToolCalls <= 0) {
            throw new IllegalArgumentException("maxToolCalls must be more than zero");
        }
        this.llm = llm;
        this.builder = builder;
        this.maxToolCalls = maxToolCalls;
        for (AgentTool t : tools) {
            if (t != null) {
                this.tools.put(t.getName(), t);
            }
        }
        requireTool(PantryTool.NAME);
        requireTool(ProfileTool.NAME);
    }

    /** The tools the agent can use, by name (read-only). */
    public Map<String, AgentTool> getTools() {
        return Collections.unmodifiableMap(tools);
    }

    // ------------------------------------------------------------------ F01

    /**
     * Generates one recipe that fits the request, the pantry and the profile (F01).
     *
     * @throws PantryEmptyException   if the pantry is empty and missing ingredients are not allowed
     * @throws LLMUnavailableException if the LLM cannot be used
     * @throws LLMException            if the LLM's replies could not be used after a retry
     */
    public synchronized Recipe generateRecipe(RecipeRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Recipe request must not be null");
        }
        String pantryJson = requireTool(PantryTool.NAME).execute("{\"action\":\"list\"}");
        String profileJson = requireTool(ProfileTool.NAME).execute("{\"action\":\"get\"}");
        if (!req.isAllowMissing() && pantryIsEmpty(pantryJson)) {
            throw new PantryEmptyException();
        }
        List<String> allergies = jsonStrings(profileJson, "allergies");

        String problem = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Prompt prompt = buildRecipePrompt(req, pantryJson, profileJson, problem);
            LLMResponse reply = llm.generate(prompt.toText(), List.of());
            try {
                Recipe recipe = ReplyParser.toRecipe(ReplyParser.extractJson(reply.getText()));
                checkRecipe(recipe, req, allergies);
                return recipe;
            } catch (ReplyParser.ReplyException e) {
                problem = e.getMessage();
            }
        }
        throw new LLMException("The AI could not produce a suitable recipe (" + problem + "). Please try again.");
    }

    private Prompt buildRecipePrompt(RecipeRequest req, String pantryJson, String profileJson,
                                     String problem) {
        builder.createNewPrompt();
        StringBuilder task = new StringBuilder()
                .append("You are a home-cooking assistant. Create one recipe that takes at most ")
                .append(req.getMaxMinutes()).append(" minutes in total and makes exactly ")
                .append(req.getPortions()).append(" portions.\n")
                .append("- Prefer ingredients from the pantry, and use items with few days_left first.\n")
                .append("- For an ingredient that is in the pantry, use exactly the pantry's name and unit, ")
                .append("so the stock can be updated after cooking.\n")
                .append("- Follow the diet in the profile, never use an ingredient the user is allergic to, ")
                .append("and avoid the dislikes.\n");
        if (req.isAllowMissing()) {
            task.append("- You may use ingredients that are not in the pantry; keep them few and common.\n");
        } else {
            task.append("- Use only pantry ingredients, plus these basics: ")
                    .append(String.join(", ", BASIC_STAPLES)).append(".\n");
        }
        if (!req.getRequiredItems().isEmpty()) {
            task.append("- The recipe must use: ").append(String.join(", ", req.getRequiredItems())).append(".\n");
        }
        if (!req.getFavourites().isEmpty()) {
            task.append("- The user rated the recipes in top_rated_recipes highly; a similar style is welcome.\n");
        }
        builder.buildTask(task.toString());
        builder.buildContext("pantry", pantryJson);
        builder.buildContext("profile", profileJson);
        if (!req.getFavourites().isEmpty()) {
            builder.buildContext("top_rated_recipes", String.join("\n", req.getFavourites()));
        }
        if (problem != null) {
            builder.buildContext("problem_with_previous_answer",
                    "Your previous answer could not be used because " + problem + ". Fix this.");
        }
        builder.buildFormat(RECIPE_FORMAT);
        return builder.getPrompt();
    }

    /** Checks the LLM's recipe against the request; throws with a reason if it does not fit. */
    static void checkRecipe(Recipe r, RecipeRequest req, List<String> allergies)
            throws ReplyParser.ReplyException {
        if (r.getCookTimeMinutes() > req.getMaxMinutes()) {
            throw new ReplyParser.ReplyException("it takes " + r.getCookTimeMinutes()
                    + " minutes but the limit is " + req.getMaxMinutes());
        }
        if (r.getPortions() != req.getPortions()) {
            throw new ReplyParser.ReplyException("it makes " + r.getPortions()
                    + " portions but " + req.getPortions() + " were asked for");
        }
        for (String required : req.getRequiredItems()) {
            boolean used = r.getIngredients().stream()
                    .anyMatch(i -> lower(i.getName()).contains(lower(required)));
            if (!used) {
                throw new ReplyParser.ReplyException("it does not use the required item '" + required + "'");
            }
        }
        for (Ingredient i : r.getIngredients()) {
            String allergen = findAllergen(i.getName(), allergies);
            if (allergen != null) {
                throw new ReplyParser.ReplyException("it contains " + i.getName()
                        + ", but the user is allergic to " + allergen);
            }
        }
    }

    // ------------------------------------------------------------------ F07

    /**
     * Suggests up to 3 replacements for one ingredient of a recipe (F07). An empty list
     * means no good substitute was found (the interface can then offer to add the item
     * to the cart).
     *
     * @throws IllegalArgumentException if the ingredient is not in the recipe
     * @throws LLMUnavailableException  if the LLM cannot be used
     * @throws LLMException             if the LLM's replies could not be used after a retry
     */
    public synchronized List<Substitution> suggestSubstitutes(Recipe recipe, String ingredient) {
        if (recipe == null || ingredient == null || ingredient.isBlank()) {
            throw new IllegalArgumentException("Recipe and ingredient are required");
        }
        Ingredient target = recipe.getIngredients().stream()
                .filter(i -> i.getName().equalsIgnoreCase(ingredient.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "'" + ingredient + "' is not an ingredient of " + recipe.getTitle()));

        AgentTool pantryTool = requireTool(PantryTool.NAME);
        String pantryJson = pantryTool.execute("{\"action\":\"list\"}");
        String profileJson = requireTool(ProfileTool.NAME).execute("{\"action\":\"get\"}");
        List<String> allergies = jsonStrings(profileJson, "allergies");

        String problem = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Prompt prompt = buildSubstitutePrompt(recipe, target, pantryJson, profileJson, problem);
            LLMResponse reply = llm.generate(prompt.toText(), List.of());
            try {
                return toSubstitutions(ReplyParser.extractJson(reply.getText()), allergies, pantryTool);
            } catch (ReplyParser.ReplyException e) {
                problem = e.getMessage();
            }
        }
        throw new LLMException("The AI could not suggest substitutes (" + problem + "). Please try again.");
    }

    private Prompt buildSubstitutePrompt(Recipe recipe, Ingredient target, String pantryJson,
                                         String profileJson, String problem) {
        builder.createNewPrompt();
        builder.buildTask("You are a home-cooking assistant. The user is missing \""
                + target.getName() + "\" for the recipe below. Suggest 1 to 3 substitutes that "
                + "work in this recipe, best first.\n"
                + "- Prefer substitutes made from items in the pantry, and list those pantry item "
                + "names exactly as written in pantry_items.\n"
                + "- Never suggest anything the user is allergic to, and follow their diet.\n"
                + "- In note, say how much to use and any change to the steps, in one sentence.\n"
                + "- If there is no reasonable substitute, return an empty substitutes list.");
        StringBuilder r = new StringBuilder(recipe.getTitle()).append("\nIngredients:");
        for (Ingredient i : recipe.getIngredients()) {
            r.append("\n- ").append(i);
        }
        r.append("\nSteps:");
        int n = 1;
        for (String s : recipe.getSteps()) {
            r.append("\n").append(n++).append(". ").append(s);
        }
        builder.buildContext("recipe", r.toString());
        builder.buildContext("missing_ingredient", target.toString());
        builder.buildContext("pantry", pantryJson);
        builder.buildContext("profile", profileJson);
        if (problem != null) {
            builder.buildContext("problem_with_previous_answer",
                    "Your previous answer could not be used because " + problem + ". Fix this.");
        }
        builder.buildFormat(SUBSTITUTE_FORMAT);
        return builder.getPrompt();
    }

    /**
     * Reads the substitutes, drops any that contain an allergen, and marks a substitute as
     * in stock only if every pantry item it names is really in the pantry.
     */
    static List<Substitution> toSubstitutions(JsonObject o, List<String> allergies, AgentTool pantryTool)
            throws ReplyParser.ReplyException {
        JsonArray arr = ReplyParser.requiredArray(o, "substitutes");
        List<Substitution> result = new ArrayList<>();
        for (JsonElement el : arr) {
            if (result.size() == MAX_SUBSTITUTES) {
                break;
            }
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject s = el.getAsJsonObject();
            String name = ReplyParser.optionalString(s, "substitute", null);
            if (name == null || findAllergen(name, allergies) != null) {
                continue;
            }
            String note = ReplyParser.optionalString(s, "note", "");
            List<String> uses = new ArrayList<>();
            if (s.has("pantry_items") && s.get("pantry_items").isJsonArray()) {
                for (JsonElement p : s.getAsJsonArray("pantry_items")) {
                    if (p.isJsonPrimitive() && !p.getAsString().isBlank()) {
                        uses.add(p.getAsString().trim());
                    }
                }
            }
            boolean inStock = !uses.isEmpty();
            for (String item : uses) {
                String answer = pantryTool.execute(hasArgs(item));
                if (!answer.contains("\"in_pantry\":true")) {
                    inStock = false;
                }
            }
            result.add(new Substitution(name, note, inStock, uses));
        }
        return result;
    }

    // ------------------------------------------------------------------ F09 / F04

    /**
     * Answers a chat message (F09). The LLM decides which tools to use; the agent runs
     * each requested tool and sends the result back, until the LLM gives a final answer.
     * At most {@code maxToolCalls} tools are run per message; after that the LLM is asked
     * to answer with what it found so far. The conversation is remembered for the session,
     * which is also how the profile questionnaire (F04) works: the LLM asks one question
     * per message and finally saves the profile through the profile tool.
     *
     * @return the agent's answer; the tool calls it made are in {@link #getLastActions()}
     * @throws LLMUnavailableException if the LLM cannot be used
     */
    public synchronized String chat(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message must not be empty");
        }
        chatHistory.add("User: " + message.trim());
        int turnStart = chatHistory.size() - 1;
        List<AgentAction> actions = new ArrayList<>();
        List<AgentTool> toolList = new ArrayList<>(tools.values());
        boolean answered = false;
        try {
            while (true) {
                boolean limitReached = actions.size() >= maxToolCalls;
                Prompt prompt = buildChatPrompt(limitReached, turnStart);
                LLMResponse reply = llm.generate(prompt.toText(), limitReached ? List.of() : toolList);

                if (reply.hasToolCall() && !limitReached) {
                    AgentAction action = runTool(reply.getToolName(), reply.getToolArgs());
                    actions.add(action);
                    chatHistory.add(TOOL_LINE_PREFIX + action.tool() + " " + action.args() + " -> "
                            + action.result());
                    continue;
                }
                String answer = reply.getText().isBlank()
                        ? "Sorry, I could not come up with an answer. Please try asking another way."
                        : reply.getText().trim();
                chatHistory.add("Assistant: " + answer);
                answered = true;
                return answer;
            }
        } finally {
            if (!answered) {
                // Keep the conversation readable for the next message.
                chatHistory.add("Assistant: (no answer, the AI was unavailable)");
            }
            // Even if the LLM fails part-way, report the changes that were already made.
            lastActions = Collections.unmodifiableList(actions);
            trimHistory();
        }
    }

    /** Starts the profile questionnaire (F04); returns the agent's first question. */
    public synchronized String startProfileSetup() {
        return chat(PROFILE_SETUP_MESSAGE);
    }

    /** Tool calls made while answering the most recent chat message. */
    public synchronized List<AgentAction> getLastActions() {
        return lastActions;
    }

    /** A copy of the conversation so far, oldest first. */
    public synchronized List<String> getChatHistory() {
        return List.copyOf(chatHistory);
    }

    /** Forgets the conversation, e.g. when the user starts a new chat. */
    public synchronized void clearChat() {
        chatHistory.clear();
        lastActions = List.of();
    }

    private void trimHistory() {
        int extra = chatHistory.size() - MAX_HISTORY_LINES;
        if (extra > 0) {
            chatHistory.subList(0, extra).clear();
        }
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    /**
     * Builds the chat prompt. Lines from {@code turnStart} on belong to the message being
     * answered and are sent in full; long tool results from earlier messages are shortened.
     */
    private Prompt buildChatPrompt(boolean limitReached, int turnStart) {
        builder.createNewPrompt();
        builder.buildTask("You are the assistant inside a recipe and grocery app for students and busy "
                + "people. Help with cooking ideas, the pantry, expiring food and the shopping cart.\n"
                + "- Use the tools to look things up or make changes instead of guessing. You can call "
                + "one tool at a time, and at most " + maxToolCalls + " per user message.\n"
                + "- Only add to or remove from the cart, or save the profile, when the user asks for it.\n"
                + "- If the request is unclear, ask one short clarifying question instead of guessing.\n"
                + "- Answer in short, plain sentences (no JSON). Say what you changed, if anything.\n"
                + "- Profile set-up: when the user asks for help setting up their profile, ask one "
                + "question per message (diet, allergies, dislikes, usual number of portions, and a "
                + "few more if useful; about 6 in total). When you have enough answers, save them with "
                + "the profile tool (action 'save') and tell the user what you saved.");
        int from = Math.max(0, Math.min(turnStart, chatHistory.size() - HISTORY_IN_PROMPT));
        List<String> lines = new ArrayList<>();
        for (int i = from; i < chatHistory.size(); i++) {
            String line = chatHistory.get(i);
            if (i < turnStart && line.startsWith(TOOL_LINE_PREFIX)) {
                line = shorten(line, MAX_OLD_RESULT_IN_PROMPT);
            }
            lines.add(line);
        }
        builder.buildContext("conversation", String.join("\n", lines));
        if (limitReached) {
            builder.buildContext("note", "You have used all " + maxToolCalls + " tool calls for this "
                    + "message. Do not ask for more tools. Answer now with what you have found so far.");
        }
        return builder.getPrompt();
    }

    /** Runs one tool for the LLM. Errors are returned as text so the LLM can react to them. */
    private AgentAction runTool(String name, String args) {
        AgentTool tool = tools.get(name);
        String result;
        if (tool == null) {
            result = errorJson("there is no tool named '" + name + "'");
        } else {
            try {
                result = tool.execute(args);
            } catch (RuntimeException e) {
                result = errorJson("the tool failed: " + e.getMessage());
            }
            if (result == null) {
                result = "{}";
            }
        }
        return new AgentAction(name, args == null ? "{}" : args, result);
    }

    private static String errorJson(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o.toString();
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    // ------------------------------------------------------------------ helpers

    private AgentTool requireTool(String name) {
        AgentTool t = tools.get(name);
        if (t == null) {
            throw new IllegalStateException("RecipeAgent needs a '" + name + "' tool");
        }
        return t;
    }

    private static String hasArgs(String name) {
        JsonObject a = new JsonObject();
        a.addProperty("action", "has");
        a.addProperty("name", name);
        return a.toString();
    }

    static boolean pantryIsEmpty(String pantryJson) {
        try {
            JsonObject o = ReplyParser.extractJson(pantryJson);
            return !o.has("items") || !o.get("items").isJsonArray() || o.getAsJsonArray("items").isEmpty();
        } catch (ReplyParser.ReplyException e) {
            return true;
        }
    }

    /** Reads a list of strings from a tool's JSON result; empty if missing. */
    static List<String> jsonStrings(String json, String key) {
        List<String> result = new ArrayList<>();
        try {
            JsonObject o = ReplyParser.extractJson(json);
            if (o.has(key) && o.get(key).isJsonArray()) {
                for (JsonElement el : o.getAsJsonArray(key)) {
                    if (el.isJsonPrimitive()) {
                        result.add(el.getAsString());
                    }
                }
            }
        } catch (ReplyParser.ReplyException e) {
            // no list
        }
        return result;
    }

    /**
     * Returns the allergy that {@code text} contains, or null. Matching ignores case and a
     * plural ending, so "peanuts" also catches "peanut butter". It errs on the side of caution.
     */
    static String findAllergen(String text, List<String> allergies) {
        if (text == null || allergies == null) {
            return null;
        }
        String t = lower(text);
        for (String allergy : allergies) {
            String a = lower(allergy);
            if (a.isEmpty()) {
                continue;
            }
            String stem = a.endsWith("es") && a.length() > 4 ? a.substring(0, a.length() - 2)
                    : a.endsWith("s") && a.length() > 3 ? a.substring(0, a.length() - 1) : a;
            if (t.contains(a) || t.contains(stem)) {
                return allergy;
            }
        }
        return null;
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
