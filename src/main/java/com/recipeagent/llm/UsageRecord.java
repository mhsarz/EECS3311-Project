package com.recipeagent.llm;

/** Today's LLM usage, saved by LLMClientProxy so the daily limit survives a restart. */
class UsageRecord {

    String date;
    int calls;
    long inputTokens;
    long outputTokens;

    UsageRecord(String date, int calls, long inputTokens, long outputTokens) {
        this.date = date;
        this.calls = calls;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }
}
