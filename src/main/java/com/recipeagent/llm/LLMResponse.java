package com.recipeagent.llm;

/**
 * What the model replied: either text, or a request to call a tool (or both, when the
 * model explains what it is about to do). Also carries the token usage of the call.
 */
public class LLMResponse {

    private final String text;
    private final String toolName;
    private final String toolArgs;
    private final String stopReason;
    private final int inputTokens;
    private final int outputTokens;

    /**
     * @param text       the model's text ("" if none)
     * @param toolName   tool to call, or null if the model did not ask for one
     * @param toolArgs   tool input as a JSON object string, or null
     * @param stopReason why the model stopped, e.g. "end_turn", "tool_use", "max_tokens"
     */
    public LLMResponse(String text, String toolName, String toolArgs, String stopReason,
                       int inputTokens, int outputTokens) {
        this.text = text == null ? "" : text;
        this.toolName = (toolName == null || toolName.isBlank()) ? null : toolName;
        this.toolArgs = this.toolName == null ? null : (toolArgs == null ? "{}" : toolArgs);
        this.stopReason = stopReason == null ? "" : stopReason;
        this.inputTokens = Math.max(0, inputTokens);
        this.outputTokens = Math.max(0, outputTokens);
    }

    /** A plain text answer (used by tests and fake clients). */
    public static LLMResponse text(String text) {
        return new LLMResponse(text, null, null, "end_turn", 0, 0);
    }

    /** A tool request (used by tests and fake clients). */
    public static LLMResponse toolCall(String toolName, String toolArgs) {
        return new LLMResponse("", toolName, toolArgs, "tool_use", 0, 0);
    }

    public String getText() {
        return text;
    }

    public boolean hasToolCall() {
        return toolName != null;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolArgs() {
        return toolArgs;
    }

    public String getStopReason() {
        return stopReason;
    }

    /** True if the answer was cut off because it reached the token limit. */
    public boolean isTruncated() {
        return "max_tokens".equals(stopReason);
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    @Override
    public String toString() {
        return hasToolCall() ? "tool call " + toolName + " " + toolArgs : "text: " + text;
    }
}
