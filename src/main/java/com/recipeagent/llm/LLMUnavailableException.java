package com.recipeagent.llm;

/**
 * The single "LLM unavailable" error that every AI feature handles the same way.
 * Thrown by LLMClientProxy when there is no API key, the daily limit is reached,
 * or the real call failed for any reason.
 */
public class LLMUnavailableException extends LLMException {

    private static final long serialVersionUID = 1L;

    public LLMUnavailableException(String message) {
        super(message);
    }

    public LLMUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
