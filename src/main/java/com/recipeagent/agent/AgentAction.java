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
}
