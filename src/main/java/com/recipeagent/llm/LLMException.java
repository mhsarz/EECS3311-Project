package com.recipeagent.llm;

/** A call to the language model failed (network error, API error, bad reply). */
public class LLMException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LLMException(String message) {
        super(message);
    }

    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
