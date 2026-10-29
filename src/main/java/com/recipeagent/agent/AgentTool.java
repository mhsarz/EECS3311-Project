package com.recipeagent.agent;

/**
 * An action the agent can ask to run, such as reading the pantry or adding to the cart.
 * The tools wrap deterministic code; the LLM only decides which one to call and with
 * which arguments (F09). Concrete tools (PantryTool, CartTool, FlyerTool, ProfileTool)
 * are added with the chat loop.
 */
public interface AgentTool {

    /** Name the LLM uses to call the tool, e.g. "cart". Letters, digits, '_' or '-'. */
    String getName();

    /** One or two sentences telling the LLM what the tool does and when to use it. */
    String getDescription();

    /**
     * JSON Schema of the tool's input object, sent to the LLM with the tool list.
     * The default accepts an empty object.
     */
    default String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    /**
     * Runs the tool.
     *
     * @param args the input chosen by the LLM, as a JSON object string
     * @return a short text result that is sent back to the LLM
     */
    String execute(String args);
}
