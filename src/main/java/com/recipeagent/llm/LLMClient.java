package com.recipeagent.llm;

import com.recipeagent.agent.AgentTool;

import java.util.List;

/**
 * The simple interface the agent uses to talk to a language model.
 * <p>
 * Adapter pattern: this is the <b>Target</b>. ClaudeClient adapts the Anthropic
 * Messages API to it.<br>
 * Proxy pattern: this is the <b>Subject</b>. LLMClientProxy and ClaudeClient both
 * implement it, so RecipeAgent cannot tell which one it is using.
 */
public interface LLMClient {

    /**
     * Sends a prompt and the tools the model may call.
     *
     * @param prompt the full prompt text (must not be empty)
     * @param tools  tools the model may ask to call; may be empty
     * @return the model's text answer or a request to call one tool
     * @throws LLMException if the call fails
     */
    LLMResponse generate(String prompt, List<AgentTool> tools);
}
